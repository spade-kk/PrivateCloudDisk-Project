//go:build integration

package sandbox

// Docker 集成测试（需求五/六）：仅在 -tags=integration 且本机 Docker 可用时执行。
// 测试用 development 配置（runc + network=none），生产 runsc/seccomp/AppArmor 门禁
// 仍由 config.Load() 强制；本地 Docker Desktop 未注册 runsc，因此本文件不得默认开启。

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/audit"
	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	pkg "privateclouddisk/plugin-runtime-service/internal/package"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

func dockerAvailable(t *testing.T) {
	t.Helper()
	if testing.Short() {
		t.Skip("集成测试需要 Docker，短模式跳过")
	}
	if _, err := exec.LookPath("docker"); err != nil {
		t.Skip("未找到 docker 二进制")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if err := exec.CommandContext(ctx, "docker", "version", "--format", "{{.Client.Version}}").Run(); err != nil {
		t.Skipf("Docker daemon 不可用：%v", err)
	}
	// 需求 7.4/36.9：UDS 必须由 Linux Docker Engine 在同一内核命名空间中 bind mount。
	// 原有行为：macOS Docker Desktop 下继续执行，会把宿主 /private/tmp 的 socket
	// 翻译到 Linux VM 的 /socket_mnt，导致 socket 端点不可见而报“source path
	// does not exist”。新行为：明确跳过该虚拟化限制；绝不退回文件轮询或共享目录
	// 通信。Linux CI 仍会执行完整的容器挂载、隔离和审计链路测试。
	operatingSystem, err := exec.CommandContext(ctx, "docker", "info", "--format", "{{.OperatingSystem}}").Output()
	if err == nil && strings.Contains(strings.ToLower(string(operatingSystem)), "docker desktop") {
		t.Skip("Docker Desktop 不支持宿主 Unix Socket bind mount；请在 Linux Docker Engine CI 执行 UDS 容器集成测试")
	}
}

func envOr(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}

// integrationConfig 返回 Doker 集成测试可用的 development 沙箱配置。
func integrationConfig(t *testing.T) config.Config {
	t.Helper()
	socketRoot, err := os.MkdirTemp("/tmp", "pcd-integration-uds-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(socketRoot) })
	return config.Config{
		DockerBinary:   envOr("RUNTIME_DOCKER_BINARY", "docker"),
		SandboxImage:   envOr("PLUGIN_SANDBOX_IMAGE", "pcd/plugin-sandbox-python:0.1.2"),
		SandboxRuntime: envOr("PLUGIN_SANDBOX_RUNTIME", "runc"),
		SandboxNetwork: "none",
		// 绑定挂载属主是宿主测试进程 uid：容器用户须与其一致（Linux CI 下不能硬编码 65532）。
		SandboxUser:          fmt.Sprintf("%d:%d", os.Getuid(), os.Getgid()),
		WorkRoot:             t.TempDir(),
		SocketRoot:           socketRoot,
		SocketGroupID:        os.Getgid(),
		SocketMaxFrameBytes:  64 * 1024,
		SocketMaxConnections: 16,
		SocketRequestsPerSec: 1000,
		SocketRequestBurst:   1000,
		SocketRequestTimeout: 5 * time.Second,
		ExecutionTimeout:     60 * time.Second,
		MemoryBytes:          256 * 1024 * 1024,
		CPUs:                 "0.5",
		PidsLimit:            64,
		LogLimitBytes:        1024,
		PackageMaxBytes:      10 * 1024 * 1024,
		CandidateMaxBytes:    10 * 1024 * 1024,
		MaxExecutionRetries:  0,
	}
}

// probeConfig 返回集成测试用的 Docker 隔离探针配置：探针 fixture（hostfs/network/
// rootuser/pids_fork/write_raw_output/context_probe）必须显式使用 os/socket/open 等
// 受限层禁止 API 来验证 Docker 边界，因此关闭受控 Python 层（36.x 业务插件不受影响，
// realworld 测试保持受限模式开启）。
func probeConfig(t *testing.T) config.Config {
	t.Helper()
	cfg := integrationConfig(t)
	cfg.DisableRestrictedPython = true
	return cfg
}

// integrationRunner 构造已注入 fakes 的 Runner：Packages 使用指定夹具 zip，Broker 固定成功。
func integrationRunner(t *testing.T, cfg config.Config, moduleName, versionID string) (*Runner, *fakeBroker, *fakePackages) {
	t.Helper()
	packages := &fakePackages{zips: map[string][]byte{versionID: fixtureZipBytes(t, moduleName)}}
	brokerClient := &fakeBroker{}
	runner := newTestRunner(t, cfg, brokerClient, packages)
	return runner, brokerClient, packages
}

// setupDirectStep 为 runContainer 直测构造宿主侧工作区：解压插件、输入、上下文。
func setupDirectStep(t *testing.T, cfg config.Config, executionID, moduleName string, entry model.Entrypoint) (string, string, string, string) {
	t.Helper()
	root := filepath.Join(cfg.WorkRoot, executionID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		t.Fatal(err)
	}
	stepRoot := filepath.Join(root, "step-000")
	if err := os.MkdirAll(stepRoot, 0o700); err != nil {
		t.Fatal(err)
	}
	pluginRoot := filepath.Join(stepRoot, "plugin")
	workRoot := filepath.Join(stepRoot, "work")
	contextRoot := filepath.Join(stepRoot, "context")
	for _, directory := range []string{workRoot, contextRoot} {
		if err := os.MkdirAll(directory, 0o770); err != nil {
			t.Fatal(err)
		}
	}
	packagePath := filepath.Join(stepRoot, "plugin.pcdpkg")
	if err := os.WriteFile(packagePath, fixtureZipBytes(t, moduleName), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := pkg.Parse(packagePath, pluginRoot, pkg.Options{MaxExpandedBytes: cfg.PackageMaxBytes}); err != nil {
		t.Fatalf("宿主侧 .pcdpkg 解析失败：%v", err)
	}
	inputPath := filepath.Join(stepRoot, "input.bin")
	if err := os.WriteFile(inputPath, []byte("input-data"), 0o400); err != nil {
		t.Fatal(err)
	}
	contextBytes, _ := json.Marshal(map[string]interface{}{
		"execution_id": executionID,
		"input":        map[string]interface{}{"src": "direct"},
		"permissions":  entry.Permissions,
	})
	if err := os.WriteFile(filepath.Join(contextRoot, "context.json"), contextBytes, 0o400); err != nil {
		t.Fatal(err)
	}
	return pluginRoot, inputPath, workRoot, contextRoot
}

func directEntry(moduleName string, permissions ...string) model.Entrypoint {
	return model.Entrypoint{
		PluginID: "plugin-1", VersionID: "v1", Runtime: "PYTHON_3_11",
		ModulePath: moduleName, FunctionName: "main", Permissions: permissions,
	}
}

// runStepDirect 直测 runContainer 便捷包装：module 取 src/<ModulePath>（manifest 相对路径）。
func runStepDirect(runner *Runner, ctx context.Context, executionID string, index int,
	pluginRoot, inputPath, workRoot, contextRoot string, entry model.Entrypoint,
) (sandboxResult, string, error) {
	if runner.Sessions == nil {
		sessions, err := uds.NewManager(uds.Config{
			RootDir: runner.Config.SocketRoot, GroupID: runner.Config.SocketGroupID, MaxFrameBytes: runner.Config.SocketMaxFrameBytes,
			MaxConnectionsPerPeer: runner.Config.SocketMaxConnections, RequestsPerSecond: runner.Config.SocketRequestsPerSec,
			RequestBurst: runner.Config.SocketRequestBurst, RequestTimeout: runner.Config.SocketRequestTimeout,
		}, fakeCapabilityInvoker{})
		if err != nil {
			return sandboxResult{}, "", err
		}
		runner.Sessions = sessions
	}
	session, err := runner.Sessions.CreateSession(uds.SessionContext{
		PluginID: entry.PluginID, VersionID: entry.VersionID, ExecutionID: executionID,
		StepID: fmt.Sprintf("%03d", index), DeclaredPermissions: entry.Permissions,
		GrantedPermissions: entry.Permissions,
	})
	if err != nil {
		return sandboxResult{}, "", err
	}
	defer session.Close()
	return runner.runContainerWithSession(ctx, executionID, index, pluginRoot, inputPath, workRoot, contextRoot,
		entry, "src/"+entry.ModulePath, entry.FunctionName,
		execLimits{timeout: runner.Config.ExecutionTimeout, memoryBytes: runner.Config.MemoryBytes}, session)
}

// assertNoResidual 确认 --rm 与 forceRemove 后执行 ID 对应的容器全部清除（5.21/5.22）。
func assertNoResidual(t *testing.T, executionID string) {
	t.Helper()
	deadline := time.Now().Add(10 * time.Second)
	for time.Now().Before(deadline) {
		output, err := exec.Command(
			"docker", "ps", "-aq", "--filter", "label=plugin-execution-id="+executionID,
		).Output()
		if err == nil && strings.TrimSpace(string(output)) == "" {
			return
		}
		time.Sleep(500 * time.Millisecond)
	}
	t.Fatalf("执行 %s 存在残留容器：%s", executionID, "")
}

// TestIntegrationRunContainerSuccessAndParsing 5.1/5.2：成功执行并解析 result.json。
func TestIntegrationRunContainerSuccessAndParsing(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	executionID := "integ_success"
	entry := directEntry("success.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "success.py", entry)
	runner := &Runner{Config: cfg}

	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("容器执行失败：%v logs=%s", err, logs)
	}
	if !result.Success || result.Modified {
		t.Fatalf("成功入口结果异常：%+v", result)
	}
	output := result.Output
	if output["ok"] != true {
		t.Fatalf("输出缺少 ok=true：%+v", output)
	}
	if output["execution_id"] != executionID {
		t.Fatalf("上下文 execution_id 未透传：%+v", output)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerModifiedAndMounts 5.7/5.14/5.18/5.19/5.20：可写输出目录 + 只读挂载 + 宿主隔离。
func TestIntegrationRunContainerModifiedAndMounts(t *testing.T) {
	dockerAvailable(t)
	cfg := probeConfig(t)

	// 5.7/5.19：工作目录可写，output.bin 被写入 → Modified=true。
	executionID := "integ_write"
	entry := directEntry("write_raw_output.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "write_raw_output.py", entry)
	runner := &Runner{Config: cfg}
	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("写输出入口失败：%v logs=%s", err, logs)
	}
	if !result.Modified {
		t.Fatalf("write_output 应声明 modified：%+v", result)
	}
	if data, readErr := os.ReadFile(filepath.Join(workRoot, "output.bin")); readErr != nil || string(data) != "raw-output" {
		t.Fatalf("输出文件内容错误：%q err=%v", data, readErr)
	}
	assertNoResidual(t, executionID)

	// 5.14/5.18/5.19/5.20：hostfs 探测结果。
	hostExecID := "integ_hostfs"
	hostEntry := directEntry("hostfs.py")
	hPlugin, hInput, hWork, hContext := setupDirectStep(t, cfg, hostExecID, "hostfs.py", hostEntry)
	hRunner := &Runner{Config: cfg}
	hResult, hLogs, hErr := runStepDirect(hRunner, context.Background(), hostExecID, 0,
		hPlugin, hInput, hWork, hContext, hostEntry)
	if hErr != nil {
		t.Fatalf("hostfs 探测失败：%v logs=%s", hErr, hLogs)
	}
	probes := map[string]string{}
	for key, value := range hResult.Output {
		if text, ok := value.(string); ok {
			probes[key] = text
		}
	}
	// 注意：/proc/1/root/etc/hostname 读取的是容器自身 PID 1 的根（容器本地 newsnamespace），
	// 并非宿主泄露，故不在此硬断言；真正宿主专属路径才必须不可读。
	for _, sensitive := range []string{
		"/var/run/docker.sock", "/run/docker.sock", "/var/lib/docker", "/Users",
	} {
		if probes[sensitive] == "readable" {
			t.Fatalf("宿主敏感路径 %s 不应可读：%+v", sensitive, probes)
		}
	}
	for _, mountPoint := range []string{"/workspace/plugin:write", "/workspace/context:write", "/workspace/input:write"} {
		if probes[mountPoint] == "writable" {
			t.Fatalf("只读挂载 %s 不应可写：%+v", mountPoint, probes)
		}
	}
	if probes["/workspace/work:write"] != "writable" {
		t.Fatalf("输出目录应可写：%+v", probes)
	}
	assertNoResidual(t, hostExecID)
}

// TestIntegrationRunContainerContextReadonly 5.20：上下文只读且内容可读。
func TestIntegrationRunContainerContextReadonly(t *testing.T) {
	dockerAvailable(t)
	cfg := probeConfig(t)
	executionID := "integ_context"
	entry := directEntry("context_probe.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "context_probe.py", entry)
	runner := &Runner{Config: cfg}
	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("context 探测失败：%v logs=%s", err, logs)
	}
	if result.Output["context_has_execution_id"] != true {
		t.Fatalf("应能读取 context.json：%+v", result.Output)
	}
	if result.Output["context_read_error"] != nil {
		t.Fatalf("context 读取不应报错：%+v", result.Output)
	}
	if result.Output["context_write"] == "writable" {
		t.Fatalf("context 目录应只读：%+v", result.Output)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerNetworkIsolated 5.15：network=none 下出站不可达。
func TestIntegrationRunContainerNetworkIsolated(t *testing.T) {
	dockerAvailable(t)
	cfg := probeConfig(t)
	executionID := "integ_network"
	entry := directEntry("network.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "network.py", entry)
	runner := &Runner{Config: cfg}
	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("网络探测失败：%v logs=%s", err, logs)
	}
	if result.Output["connect"] == true {
		t.Fatalf("network=none 下不应能出站连接：%+v", result.Output)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerNonRoot 5.17：容器内 uid/euid 必须是 65532。
func TestIntegrationRunContainerNonRoot(t *testing.T) {
	dockerAvailable(t)
	cfg := probeConfig(t)
	executionID := "integ_root"
	entry := directEntry("rootuser.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "rootuser.py", entry)
	runner := &Runner{Config: cfg}
	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("身份探测失败：%v logs=%s", err, logs)
	}
	expectedUID := strings.SplitN(cfg.SandboxUser, ":", 2)[0]
	if fmt.Sprintf("%v", result.Output["uid"]) != expectedUID || fmt.Sprintf("%v", result.Output["euid"]) != expectedUID {
		t.Fatalf("容器应运行在 %s：%+v", cfg.SandboxUser, result.Output)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerPidsLimit 5.16：fork 风暴在 pids-limit 下完成且不拖垮宿主。
func TestIntegrationRunContainerPidsLimit(t *testing.T) {
	dockerAvailable(t)
	cfg := probeConfig(t)
	executionID := "integ_pids"
	entry := directEntry("pids_fork.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "pids_fork.py", entry)
	runner := &Runner{Config: cfg}
	result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err != nil {
		t.Fatalf("fork 风暴入口应受限完成而非失败：%v logs=%s", err, logs)
	}
	if !result.Success {
		t.Fatalf("fork 风暴入口结果异常：%+v", result.Output)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerLogBuffer 5.23：日志风暴被 LimitedBuffer 截断后仍以失败信息返回。
func TestIntegrationRunContainerLogBuffer(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.LogLimitBytes = 512
	executionID := "integ_biglog"
	entry := directEntry("biglog_fail.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "biglog_fail.py", entry)
	runner := &Runner{Config: cfg}
	_, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err == nil {
		t.Fatal("日志风暴后抛出异常应让 docker run 失败")
	}
	// LimitedBuffer.String 在截断时追加提示标记；去掉标记后的内容区必须小于等于上限。
	const marker = "[日志已按平台上限截断]"
	if !strings.Contains(logs, marker) {
		t.Fatalf("日志应带截断标记：%q", logs)
	}
	content := strings.TrimSuffix(strings.TrimSuffix(logs, marker), "\n")
	if len(content) > int(cfg.LogLimitBytes) {
		t.Fatalf("日志未按 LogLimitBytes 截断：len=%d limit=%d", len(content), cfg.LogLimitBytes)
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerLargeResult 5.11：result.json 超过 1 MiB 被拒绝。
func TestIntegrationRunContainerLargeResult(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	executionID := "integ_bigresult"
	entry := directEntry("bigresult.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "bigresult.py", entry)
	runner := &Runner{Config: cfg}
	_, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry)
	if err == nil {
		t.Fatal("超大 result.json 应被拒绝")
	}
	if !strings.Contains(err.Error(), "1 MiB") {
		t.Fatalf("错误应说明体积上限：%v", err)
	}
	_ = logs
	assertNoResidual(t, executionID)
}

// TestIntegrationRunContainerTimeoutKill 5.4/5.21：超时强制终止并清理容器。
func TestIntegrationRunContainerTimeoutKill(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.ExecutionTimeout = 4 * time.Second
	executionID := "integ_timeout"
	entry := directEntry("timeout.py")
	pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, "timeout.py", entry)
	runner := &Runner{Config: cfg}
	ctx, cancel := context.WithTimeout(context.Background(), cfg.ExecutionTimeout)
	defer cancel()
	if _, _, err := runStepDirect(runner, ctx, executionID, 0,
		pluginRoot, inputPath, workRoot, contextRoot, entry); err == nil {
		t.Fatal("超时插件应返回错误")
	}
	assertNoResidual(t, executionID)
}

// TestIntegrationExecuteSuccessChain 6.1/6.2/6.7：无修改成功链，不触发 Upload。
func TestIntegrationExecuteSuccessChain(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, brokerClient, _ := integrationRunner(t, cfg, "success.py", "v1")
	request := preprocessRequest("integ_exec_ok_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.Status != "success" || result.ContentModified {
		t.Fatalf("成功链应无修改：%+v", result)
	}
	if result.CompletedEntrypoints != 1 {
		t.Fatalf("应完成 1 个入口：%+v", result)
	}
	if _, _, _, uploadCalls := brokerClient.calls(); uploadCalls != 0 {
		t.Fatalf("无修改链不应 Upload：%+v", result)
	}
	if _, err := os.Stat(filepath.Join(cfg.WorkRoot, request.ExecutionID)); !os.IsNotExist(err) {
		t.Fatalf("工作区应被清理（6.25）：%v", err)
	}
}

// TestIntegrationExecuteModifiedChain 6.3/6.20：多入口顺序执行，末入口修改生效并提交候选。
func TestIntegrationExecuteModifiedChain(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, brokerClient, _ := integrationRunner(t, cfg, "success.py", "v1")
	runner.Packages.(*fakePackages).zips["v1"] = multiFixtureZipBytes(t, "success.py", "write_output.py")

	request := preprocessRequest("integ_exec_mod_1",
		preEntrypoint("v1", "file.content.write_pre_activation"),
		preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.Status != "success" || !result.ContentModified {
		t.Fatalf("末入口修改应提交候选：%+v", result)
	}
	if result.CompletedEntrypoints != 2 {
		t.Fatalf("应完成 2 个入口：%+v", result)
	}
	if result.CandidateID != "candidate-1" {
		t.Fatalf("候选 ID 缺失：%+v", result)
	}
	_, _, _, uploadCalls := brokerClient.calls()
	brokerClient.mu.Lock()
	sources := append([]string{}, brokerClient.uploadSources...)
	brokerClient.mu.Unlock()
	if uploadCalls != 1 || len(sources) != 1 {
		t.Fatalf("应恰好 Upload 1 次：%+v", sources)
	}
	if !strings.HasSuffix(sources[0], filepath.Join("step-001", "work", "output.bin")) {
		t.Fatalf("Upload 源应为末入口输出：%s", sources[0])
	}

	// 候选提交失败（6.20）。
	brokerClient.uploadFn = func(ctx context.Context, gateID, executionID, lease, source string) (broker.Candidate, error) {
		return broker.Candidate{}, fmt.Errorf("broker commit down")
	}
	request2 := preprocessRequest("integ_exec_mod_2",
		preEntrypoint("v1", "file.content.write_pre_activation"),
		preEntrypoint("v1", "file.content.write_pre_activation"))
	result2 := runner.Execute(context.Background(), request2)
	if result2.FailureCode != "CANDIDATE_COMMIT_FAILED" {
		t.Fatalf("Upload 失败应映射 CANDIDATE_COMMIT_FAILED：%+v", result2)
	}
}

// TestIntegrationRunContainerFailureSanitizedAtExecute 5.5/5.6/6.23：失败入口错误脱敏。
func TestIntegrationRunContainerFailureSanitizedAtExecute(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, _, _ := integrationRunner(t, cfg, "failure.py", "v1")
	request := preprocessRequest("integ_exec_fail_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.Status != "failed" || result.FailureCode != "PLUGIN_EXECUTION_FAILED" {
		t.Fatalf("失败入口应映射 PLUGIN_EXECUTION_FAILED：%+v", result)
	}
	if result.FailureSummary == "" {
		t.Fatal("应返回脱敏后的失败摘要")
	}
	if strings.Contains(result.FailureSummary, "/var/lib/pcd-runtime") {
		t.Fatalf("失败摘要泄露宿主路径：%s", result.FailureSummary)
	}
	if _, err := os.Stat(filepath.Join(cfg.WorkRoot, request.ExecutionID)); !os.IsNotExist(err) {
		t.Fatalf("失败路径也应清理工作区（6.25）：%v", err)
	}
}

// TestIntegrationExecuteRetry 5.9/5.10/5.22：首次 docker run 失败后重试成功，且无残留容器。
func TestIntegrationExecuteRetry(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.MaxExecutionRetries = 1

	realDocker, err := exec.LookPath("docker")
	if err != nil {
		t.Fatal(err)
	}
	counterFile := filepath.Join(t.TempDir(), "run-count")
	script := filepath.Join(t.TempDir(), "docker-wrapper")
	content := "#!/bin/sh\n" +
		"if [ \"$1\" = \"run\" ]; then\n" +
		"  n=$(cat \"" + counterFile + "\" 2>/dev/null || echo 0)\n" +
		"  n=$((n+1))\n" +
		"  echo $n > \"" + counterFile + "\"\n" +
		"  if [ \"$n\" = \"1\" ]; then\n" +
		"    echo deliberate-first-failure >&2\n" +
		"    exit 1\n" +
		"  fi\n" +
		"fi\n" +
		"exec \"" + realDocker + "\" \"$@\"\n"
	if err := os.WriteFile(script, []byte(content), 0o700); err != nil {
		t.Fatal(err)
	}
	cfg.DockerBinary = script

	runner, _, _ := integrationRunner(t, cfg, "success.py", "v1")
	request := preprocessRequest("integ_exec_retry_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.Status != "success" || result.CompletedEntrypoints != 1 {
		t.Fatalf("重试后应成功：%+v", result)
	}
	raw, _ := os.ReadFile(counterFile)
	if strings.TrimSpace(string(raw)) != "2" {
		t.Fatalf("应恰好重试 1 次（共 2 次 run）：%q", raw)
	}
	assertNoResidual(t, request.ExecutionID)
}

// TestIntegrationDigestGateBlocksContainer 5.24：真实 Docker 下摘要门禁拒绝后容器不启动。
func TestIntegrationDigestGateBlocksContainer(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.RequireSandboxDigest = true
	cfg.SandboxImageDigest = "sha256:0000000000000000000000000000000000000000000000000000000000000000"
	runner, _, _ := integrationRunner(t, cfg, "success.py", "v1")
	request := preprocessRequest("integ_digest_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.Status == "success" {
		t.Fatalf("摘要不匹配不应成功：%+v", result)
	}
	assertNoResidual(t, request.ExecutionID)
}

// TestIntegrationRunContainerMissingDocker 5.25：docker 二进制缺失返回失败码。
func TestIntegrationRunContainerMissingDocker(t *testing.T) {
	cfg := integrationConfig(t)
	cfg.DockerBinary = filepath.Join(t.TempDir(), "no-such-docker")
	runner, _, _ := integrationRunner(t, cfg, "success.py", "v1")
	request := preprocessRequest("integ_nodocker_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	result := runner.Execute(context.Background(), request)
	if result.FailureCode != "PLUGIN_EXECUTION_FAILED" {
		t.Fatalf("docker 缺失应映射 PLUGIN_EXECUTION_FAILED：%+v", result)
	}
}

// TestIntegrationPostAvailable 6.9/6.10/6.11/6.12：激活后路径成功、冻结与读权限行为。
func TestIntegrationPostAvailable(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)

	// 成功：manifest 事件为 file.available，权限含 file.content.read（触发 DownloadActive）。
	successPackages := &fakePackages{zips: map[string][]byte{
		"v1": fixturePcdpkgBytesEvent(t, EventContentAvailable, "success.py", "main",
			[]string{"file.content.read"}, nil),
	}}
	successRunner := newTestRunner(t, cfg, &fakeBroker{}, successPackages)
	result := successRunner.ExecutePostAvailable(context.Background(),
		postAvailableRequest("integ_post_1", postAvailableEntrypoint("v1", "file.content.read")))
	if result.Status != "success" || result.ContentModified {
		t.Fatalf("激活后成功链异常：%+v", result)
	}
	if result.CompletedEntrypoints != 1 {
		t.Fatalf("应完成 1 个入口：%+v", result)
	}
	if _, _, activeCalls, _ := successRunner.Broker.(*fakeBroker).calls(); activeCalls != 1 {
		t.Fatalf("有读权限应调用 DownloadActive：%+v", result)
	}
	assertNoResidual(t, "integ_post_1")

	// 6.11：激活后入口权限含预激活写 → CONTENT_FROZEN。
	frozenPackages := &fakePackages{zips: map[string][]byte{
		"v1": fixturePcdpkgBytesEvent(t, EventContentAvailable, "write_raw_output.py", "main",
			[]string{"file.content.write_pre_activation"}, nil),
	}}
	frozenRunner := newTestRunner(t, cfg, &fakeBroker{}, frozenPackages)
	frozen := frozenRunner.ExecutePostAvailable(context.Background(),
		postAvailableRequest("integ_post_2", postAvailableEntrypoint("v1", "file.content.write_pre_activation")))
	if frozen.FailureCode != "CONTENT_FROZEN" {
		t.Fatalf("激活后写内容应 CONTENT_FROZEN：%+v", frozen)
	}
	assertNoResidual(t, "integ_post_2")
}

// TestIntegrationCapability 6.13/6.15/6.16：能力函数成功、空输出与内容冻结。
func TestIntegrationCapability(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)

	// 成功：exports._capability 函数读取 context.input 并按 step_id 回显。
	okContent := []byte("def main(input_data):\n    return {\"status\": \"ok\", \"step_id\": input_data.get(\"step_id\") or \"step_1\"}\n")
	packages := &fakePackages{zips: map[string][]byte{
		"v1": fixtureCapabilityPcdpkgBytes(t, "v1", "capability.py", "main", "_capability",
			[]string{"file.content.read"}, okContent),
	}}
	runner := newTestRunner(t, cfg, &fakeBroker{}, packages)
	result := runner.ExecuteCapability(context.Background(),
		capabilityRequest("integ_cap_1", capabilityEntrypoint("v1", "_capability", "file.content.read")))
	if result.Status != "success" {
		t.Fatalf("能力执行失败：%+v", result)
	}
	if result.Output["status"] != "ok" || result.Output["step_id"] != "step_1" {
		t.Fatalf("能力输出异常：%+v", result.Output)
	}
	assertNoResidual(t, "integ_cap_1")

	// 空输出：函数返回 None → 规范为空对象（6.16）。
	emptyPackages := &fakePackages{zips: map[string][]byte{
		"v1": fixtureCapabilityPcdpkgBytes(t, "v1", "capability_empty.py", "main", "_capability",
			[]string{"file.content.read"}, []byte("def main(input_data):\n    return None\n")),
	}}
	runner2 := newTestRunner(t, cfg, &fakeBroker{}, emptyPackages)
	result2 := runner2.ExecuteCapability(context.Background(),
		capabilityRequest("integ_cap_2", capabilityEntrypoint("v1", "_capability", "file.content.read")))
	if result2.Status != "success" {
		t.Fatalf("空输出能力应成功：%+v", result2)
	}
	if result2.Output == nil || len(result2.Output) != 0 {
		t.Fatalf("空输出应规范为空对象（6.16）：%#v", result2.Output)
	}
	assertNoResidual(t, "integ_cap_2")

	// 6.15：能力导出声明预激活写权限 → CONTENT_FROZEN。
	frozenPackages := &fakePackages{zips: map[string][]byte{
		"v1": fixtureCapabilityPcdpkgBytesPerms(t, "v1", "write_raw_output.py", "main", "_capability",
			[]string{"file.content.read", "file.content.write_pre_activation"},
			[]string{"file.content.write_pre_activation"}, nil),
	}}
	runner3 := newTestRunner(t, cfg, &fakeBroker{}, frozenPackages)
	frozen := runner3.ExecuteCapability(context.Background(),
		capabilityRequest("integ_cap_3", capabilityEntrypoint("v1", "_capability", "file.content.write_pre_activation")))
	if frozen.FailureCode != "CONTENT_FROZEN" {
		t.Fatalf("工作流能力写文件应 CONTENT_FROZEN：%+v", frozen)
	}
	assertNoResidual(t, "integ_cap_3")
}

// TestIntegrationExecuteAuditWritten 6.24/7.24：容器关键事件写入审计台账。
func TestIntegrationExecuteAuditWritten(t *testing.T) {
	dockerAvailable(t)
	auditPath := filepath.Join(t.TempDir(), "audit.log")
	sink, err := audit.New(auditPath)
	if err != nil {
		t.Fatal(err)
	}
	defer sink.Close()
	cfg := integrationConfig(t)
	runner, _, _ := integrationRunner(t, cfg, "success.py", "v1")
	runner.Audit = sink
	request := preprocessRequest("integ_audit_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	if result := runner.Execute(context.Background(), request); result.Status != "success" {
		t.Fatalf("执行失败：%+v", result)
	}
	raw, _ := os.ReadFile(auditPath)
	text := string(raw)
	for _, event := range []string{"container_started", "container_finished"} {
		if !strings.Contains(text, event) {
			t.Fatalf("审计缺少事件 %s：%s", event, text)
		}
	}
}

// TestIntegrationRunContainerRestrictedPythonIntercepts 36.36：受限 Python 层在
// 真实 Docker 沙盒中对恶意插件实施运行时拦截，且错误信息不含宿主路径（脱敏）。
func TestIntegrationRunContainerRestrictedPythonIntercepts(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t) // 受限层默认开启（DisableRestrictedPython=false）
	for _, tc := range []struct {
		module string
		needle string
	}{
		{module: "restricted_import_os.py", needle: "禁止导入模块"},
		{module: "restricted_dunder.py", needle: "受限环境拒绝"},
		{module: "restricted_eval.py", needle: "受限环境禁用内置"},
	} {
		t.Run(tc.module, func(t *testing.T) {
			executionID := "integ_restricted_" + moduleSlug(tc.module)
			entry := directEntry(tc.module)
			pluginRoot, inputPath, workRoot, contextRoot := setupDirectStep(t, cfg, executionID, tc.module, entry)
			runner := &Runner{Config: cfg}
			result, logs, err := runStepDirect(runner, context.Background(), executionID, 0,
				pluginRoot, inputPath, workRoot, contextRoot, entry)
			if err == nil {
				t.Fatalf("恶意插件应被受限层拦截，却成功：%+v", result)
			}
			combined := (result.Error + " " + logs)
			if !strings.Contains(combined, tc.needle) {
				t.Fatalf("拦截原因不匹配 %q：err=%v logs=%s", tc.needle, err, logs)
			}
			// 脱敏：错误/日志不得泄露宿主绝对路径。
			if strings.Contains(combined, "/Users/") || strings.Contains(combined, "/home/") {
				t.Fatalf("脱敏失败：泄漏宿主路径：%s", combined)
			}
			assertNoResidual(t, executionID)
		})
	}
}

// moduleSlug 把夹具名（如 restricted_import_os.py）清洗成合法 execution_id 片段。
func moduleSlug(name string) string {
	replacer := strings.NewReplacer(".", "", "-", "", "_", "")
	return replacer.Replace(name)
}
