package sandbox

// manifest 驱动执行逻辑单元测试（需求四 4.x/6.1-6.25/7.x）。
// 不依赖 Docker：所有到达 runContainer 的路径都因 docker 缺失返回 PLUGIN_EXECUTION_FAILED，
// 用于验证下载、manifest 解析、事件/能力规划、权限门禁与错误映射。

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
)

// preEntrypoint 构造 manifest 驱动的事件入口（请求只表达事件 + 版本，module/function 由 manifest 提供）。
func preEntrypoint(versionID string, permissions ...string) model.Entrypoint {
	return model.Entrypoint{
		PluginID:    "00000000-0000-0000-0000-000000000001",
		VersionID:   versionID,
		Runtime:     "PYTHON_3_11",
		Event:       EventContentReady,
		Permissions: permissions,
		Config:      map[string]interface{}{"k": "v"},
	}
}

// postAvailableEntrypoint 构造激活后事件入口（file.available）。
func postAvailableEntrypoint(versionID string, permissions ...string) model.Entrypoint {
	entry := preEntrypoint(versionID, permissions...)
	entry.Event = EventContentAvailable
	return entry
}

// capabilityEntrypoint 构造能力调用入口。
func capabilityEntrypoint(versionID, capability string, permissions ...string) model.Entrypoint {
	return model.Entrypoint{
		PluginID:    "00000000-0000-0000-0000-000000000001",
		VersionID:   versionID,
		Runtime:     "PYTHON_3_11",
		Capability:  capability,
		Permissions: permissions,
		Config:      map[string]interface{}{"k": "v"},
	}
}

func preprocessRequest(executionID string, entrypoints ...model.Entrypoint) model.PreprocessChainRequest {
	return model.PreprocessChainRequest{
		ExecutionID:     executionID,
		Event:           map[string]interface{}{"data": map[string]interface{}{"gate_id": "gate1"}},
		Entrypoints:     entrypoints,
		DeadlineAt:      time.Now().Add(60 * time.Second).Format(time.RFC3339Nano),
		ContentLeaseRef: "lease-ref-1",
	}
}

func postAvailableRequest(executionID string, entrypoints ...model.Entrypoint) model.PostAvailableChainRequest {
	return model.PostAvailableChainRequest{
		ExecutionID: executionID,
		Event: map[string]interface{}{
			"data":          map[string]interface{}{"file_id": "00000000-0000-0000-0000-000000000010"},
			"actor_user_id": "user-1",
			"space_id":      "space-1",
		},
		Entrypoints: entrypoints,
	}
}

func capabilityRequest(executionID string, entrypoint model.Entrypoint) model.CapabilityExecutionRequest {
	return model.CapabilityExecutionRequest{
		ExecutionID: executionID,
		StepID:      "step_1",
		UserID:      "user-1",
		SpaceID:     "space-1",
		Input:       map[string]interface{}{"q": "x"},
		Entrypoint:  entrypoint,
	}
}

// emptyRunner 返回未注入任何依赖的最小 Runner（用于 Pre-拒路径）。
func emptyRunner(t *testing.T) *Runner {
	return newTestRunner(t, config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}, nil, nil)
}

// TestExecuteRejectsLegacyEntrypoint 4.19/4.18：旧式 module/function 必须拒绝并提示 manifest 驱动。
func TestExecuteRejectsLegacyEntrypoint(t *testing.T) {
	runner := emptyRunner(t)
	entry := model.Entrypoint{
		VersionID: "v1", Runtime: "PYTHON_3_11",
		ModulePath: "main.py", FunctionName: "main",
		Permissions: []string{"file.content.write_pre_activation"},
	}
	result := runner.Execute(context.Background(), preprocessRequest("exec_1", entry))
	if result.FailureCode != "MANIFEST_DRIVEN_REQUIRED" {
		t.Fatalf("旧式入口应返回 MANIFEST_DRIVEN_REQUIRED，实际=%+v", result)
	}
}

// TestExecuteRejectsPolicyViolation 6.4/6.5：manifest 事件缺预激活写权限或运行时不受支持 → RUNTIME_POLICY_REJECTED。
func TestExecuteRejectsPolicyViolation(t *testing.T) {
	packages := &fakePackages{zips: map[string][]byte{}}
	cfg := config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}
	runner := newTestRunner(t, cfg, nil, packages)

	t.Run("缺少写权限", func(t *testing.T) {
		packages.zips["v1"] = fixturePcdpkgBytes(t, "v1", "main.py", "main",
			[]string{"file.content.read"}, []byte("def main(context):\n    return {}\n"))
		result := runner.Execute(context.Background(), preprocessRequest("exec_1", preEntrypoint("v1", "file.content.read")))
		if result.Status != "failed" || result.FailureCode != "RUNTIME_POLICY_REJECTED" {
			t.Fatalf("应返回 RUNTIME_POLICY_REJECTED，实际=%+v", result)
		}
	})

	t.Run("运行时不受支持", func(t *testing.T) {
		entry := preEntrypoint("v1", "file.content.write_pre_activation")
		entry.Runtime = "JAVASCRIPT_20"
		result := runner.Execute(context.Background(), preprocessRequest("exec_1", entry))
		if result.Status != "failed" || result.FailureCode != "RUNTIME_POLICY_REJECTED" {
			t.Fatalf("应返回 RUNTIME_POLICY_REJECTED，实际=%+v", result)
		}
	})
}

// TestExecuteRejectsInvalidIDs 6.19/6.7：execution_id / gate_id / lease 非法 → RUNTIME_REQUEST_INVALID。
func TestExecuteRejectsInvalidIDs(t *testing.T) {
	runner := emptyRunner(t)
	entry := preEntrypoint("v1", "file.content.write_pre_activation")

	bad := preprocessRequest("../bad", entry)
	if result := runner.Execute(context.Background(), bad); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("非法 execution_id 应被拒绝：%+v", result)
	}
	bad = preprocessRequest("exec_1", entry)
	bad.Event = map[string]interface{}{"data": map[string]interface{}{"gate_id": "bad gate!"}}
	if result := runner.Execute(context.Background(), bad); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("非法 gate_id 应被拒绝：%+v", result)
	}
	bad.Event = map[string]interface{}{"data": map[string]interface{}{"gate_id": "gate1"}}
	bad.ContentLeaseRef = ""
	if result := runner.Execute(context.Background(), bad); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("缺 content lease ref 应被拒绝：%+v", result)
	}
}

// TestExecuteTimeoutByDeadline 6.6：截止时间已过 → 直接超时（不触碰 Docker）。
func TestExecuteTimeoutByDeadline(t *testing.T) {
	runner := emptyRunner(t)
	request := preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	request.DeadlineAt = time.Now().Add(-time.Second).Format(time.RFC3339Nano)
	result := runner.Execute(context.Background(), request)
	if result.Status != "timeout" || result.FailureCode != "PLUGIN_RUNTIME_TIMEOUT" {
		t.Fatalf("过期截止时间应直接超时：%+v", result)
	}
}

// TestExecuteBrokerFailure 6.20：Exchange/Download 失败对应错误码。
func TestExecuteBrokerFailure(t *testing.T) {
	brokerClient := &fakeBroker{
		exchangeFn: func(ctx context.Context, gateID, executionID, ref string, ttl int) (broker.ExchangedLease, error) {
			return broker.ExchangedLease{}, errors.New("broker down")
		},
	}
	cfg := config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}
	runner := newTestRunner(t, cfg, brokerClient, nil)
	result := runner.Execute(context.Background(), preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation")))
	if result.FailureCode != "CONTENT_LEASE_EXCHANGE_FAILED" {
		t.Fatalf("Exchange 失败应映射 CONTENT_LEASE_EXCHANGE_FAILED，实际=%+v", result)
	}

	brokerClient.exchangeFn = nil
	brokerClient.downloadFn = func(ctx context.Context, gateID, executionID, lease, destination string) error {
		return errors.New("read failed")
	}
	result = runner.Execute(context.Background(), preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation")))
	if result.FailureCode != "CONTENT_LEASE_READ_FAILED" {
		t.Fatalf("Download 失败应映射 CONTENT_LEASE_READ_FAILED，实际=%+v", result)
	}
}

// TestExecutePackageFailure 6.21/6.22：包下载/解压失败对应错误码，且工作区被清理（6.25）。
func TestExecutePackageFailure(t *testing.T) {
	workRoot := t.TempDir()
	packages := &fakePackages{zips: map[string][]byte{}}
	cfg := config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: workRoot}
	runner := newTestRunner(t, cfg, nil, packages)
	request := preprocessRequest("exec_cleanup", preEntrypoint("v1", "file.content.write_pre_activation"))

	packages.downloadFn = func(ctx context.Context, versionID, destination string) error {
		return errors.New("package service down")
	}
	if result := runner.Execute(context.Background(), request); result.FailureCode != "PLUGIN_PACKAGE_FETCH_FAILED" {
		t.Fatalf("包下载失败应映射 PLUGIN_PACKAGE_FETCH_FAILED，实际=%+v", result)
	}
	if _, err := os.Stat(filepath.Join(workRoot, "exec_cleanup")); !os.IsNotExist(err) {
		t.Fatalf("失败路径应清理隔离工作区（6.25），残留: %v", err)
	}

	packages.downloadFn = nil
	packages.zips["v1"] = []byte("this is not a zip")
	if result := runner.Execute(context.Background(), request); result.FailureCode != "PLUGIN_PACKAGE_INVALID" {
		t.Fatalf("坏包应映射 PLUGIN_PACKAGE_INVALID，实际=%+v", result)
	}
	if _, err := os.Stat(filepath.Join(workRoot, "exec_cleanup")); !os.IsNotExist(err) {
		t.Fatalf("坏包路径也应清理隔离工作区（6.25）")
	}
}

// TestExecuteNilDependencies 7.15/7.16：Broker 未注入时显式拒绝，不 panic。
func TestExecuteNilDependencies(t *testing.T) {
	runner := &Runner{Config: config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}}
	request := preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation"))
	if result := runner.Execute(context.Background(), request); result.FailureCode != "RUNTIME_CONFIG_INVALID" {
		t.Fatalf("Broker 缺失应显式拒绝：%+v", result)
	}
}

// TestExecuteManifestDrivenPackageFailure 4.15/7.13：坏包经 loadPackage → 结构化错误码。
func TestExecuteManifestDrivenPackageFailure(t *testing.T) {
	packages := &fakePackages{zips: map[string][]byte{
		// 缺 src 的包 → PACKAGE_STRUCTURE_INVALID → PLUGIN_PACKAGE_INVALID。
		"v1": pcdpkgBytes(t, manifestYAML(fixtureManifestID("fixture", 3), "1.0.0",
			EventContentReady, "src/main.py", "main",
			[]string{"file.content.read_staging", "file.content.write_pre_activation"},
			[]string{"file.content.read_staging", "file.content.write_pre_activation"}),
			map[string][]byte{}),
	}}
	runner := newTestRunner(t, config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}, nil, packages)
	result := runner.Execute(context.Background(), preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation")))
	if result.FailureCode != "PLUGIN_PACKAGE_INVALID" {
		t.Fatalf("缺 src 应映射 PLUGIN_PACKAGE_INVALID，实际=%+v", result)
	}
}

// TestPostAvailablePolicyAndReadSkip 6.10/6.12：预激活写被冻结；无读权限不下载激活内容。
func TestPostAvailablePolicyAndReadSkip(t *testing.T) {
	brokerClient := &fakeBroker{}
	cfg := config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}
	packages := &fakePackages{zips: map[string][]byte{
		"v1": fixturePcdpkgBytesEvent(t, EventContentAvailable, "main.py", "main",
			[]string{"file.content.write_pre_activation"}, nil),
		"v2": fixturePcdpkgBytesEvent(t, EventContentAvailable, "main.py", "main",
			[]string{"file.content.write"}, nil),
	}}
	runner := newTestRunner(t, cfg, brokerClient, packages)

	// 6.10：入口权限含预激活写 → CONTENT_FROZEN。
	frozen := runner.ExecutePostAvailable(context.Background(),
		postAvailableRequest("exec_1", postAvailableEntrypoint("v1", "file.content.write_pre_activation")))
	if frozen.FailureCode != "CONTENT_FROZEN" {
		t.Fatalf("含预激活写权限应 CONTENT_FROZEN，实际=%+v", frozen)
	}

	// 6.12：无读权限 → 不调用 DownloadActive，直接进入 runContainer。
	noRead := runner.ExecutePostAvailable(context.Background(),
		postAvailableRequest("exec_2", postAvailableEntrypoint("v2", "file.content.write")))
	if noRead.FailureCode != "PLUGIN_EXECUTION_FAILED" {
		t.Fatalf("无读权限应跳过下载并继续（docker 缺失失败码 PLUGIN_EXECUTION_FAILED），实际=%+v", noRead)
	}
	if _, _, active, _ := brokerClient.calls(); active != 0 {
		t.Fatalf("无读权限不应调用 DownloadActive（6.12）")
	}

	_ = cfg
}

// TestPostAvailableInvalidID 6.19：非法 execution_id / 缺 actor_user_id → RUNTIME_REQUEST_INVALID。
func TestPostAvailableInvalidID(t *testing.T) {
	runner := emptyRunner(t)
	request := postAvailableRequest("bad!!", postAvailableEntrypoint("v1", "file.content.read"))
	if result := runner.ExecutePostAvailable(context.Background(), request); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("非法标识应拒绝：%+v", result)
	}
	request = postAvailableRequest("exec_1", postAvailableEntrypoint("v1", "file.content.read"))
	request.Event = map[string]interface{}{"data": map[string]interface{}{"file_id": "00000000-0000-0000-0000-000000000010"}}
	if result := runner.ExecutePostAvailable(context.Background(), request); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("缺 actor_user_id 应拒绝：%+v", result)
	}
}

// TestCapabilityInvalidEntrypoint 6.17/6.19：能力入口缺能力名或标识非法 → 直接拒绝。
func TestCapabilityInvalidEntrypoint(t *testing.T) {
	runner := emptyRunner(t)
	entry := model.Entrypoint{VersionID: "v1", Runtime: "PYTHON_3_11", Capability: ""}
	if result := runner.ExecuteCapability(context.Background(), capabilityRequest("exec_1", entry)); result.FailureCode != "RUNTIME_POLICY_REJECTED" {
		t.Fatalf("缺 capability 应拒绝：%+v", result)
	}
	capEntry := model.Entrypoint{VersionID: "v1", Runtime: "PYTHON_3_11", ModulePath: "main.py", FunctionName: "main"}
	if result := runner.ExecuteCapability(context.Background(), capabilityRequest("exec_1", capEntry)); result.FailureCode != "MANIFEST_DRIVEN_REQUIRED" {
		t.Fatalf("旧式 module/function 能力入口应拒绝：%+v", result)
	}
	if result := runner.ExecuteCapability(context.Background(), capabilityRequest("bad id", capabilityEntrypoint("v1", "cap"))); result.FailureCode != "RUNTIME_REQUEST_INVALID" {
		t.Fatalf("非法标识应拒绝：%+v", result)
	}
}

// TestCapabilityTimeoutByCancel 6.18：父上下文取消 → PLUGIN_RUNTIME_TIMEOUT，不触碰 Docker。
func TestCapabilityTimeoutByCancel(t *testing.T) {
	packages := &fakePackages{zips: map[string][]byte{
		"v1": fixtureCapabilityPcdpkgBytes(t, "v1", "capability.py", "cap_main", "somecap",
			[]string{"file.content.read"}, nil),
	}}
	runner := newTestRunner(t, config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}, nil, packages)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	result := runner.ExecuteCapability(ctx, capabilityRequest("exec_1", capabilityEntrypoint("v1", "somecap", "file.content.read")))
	if result.Status != "failed" || result.FailureCode != "PLUGIN_RUNTIME_TIMEOUT" {
		t.Fatalf("取消上下文应超时：%+v", result)
	}
}

// TestCapabilityPackageFailureAndCleanup 6.21/6.22：包失败错误码 + 工作区清理（6.25）。
func TestCapabilityPackageFailureAndCleanup(t *testing.T) {
	workRoot := t.TempDir()
	packages := &fakePackages{zips: map[string][]byte{}}
	runner := newTestRunner(t, config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: workRoot}, nil, packages)
	request := capabilityRequest("exec_cap", capabilityEntrypoint("v1", "somecap", "file.content.read"))

	packages.downloadFn = func(ctx context.Context, versionID, destination string) error {
		return errors.New("down")
	}
	if result := runner.ExecuteCapability(context.Background(), request); result.FailureCode != "PLUGIN_PACKAGE_FETCH_FAILED" {
		t.Fatalf("能力包下载失败应映射 PLUGIN_PACKAGE_FETCH_FAILED：%+v", result)
	}
	packages.downloadFn = nil
	packages.zips["v1"] = []byte("garbage")
	if result := runner.ExecuteCapability(context.Background(), request); result.FailureCode != "PLUGIN_PACKAGE_INVALID" {
		t.Fatalf("能力坏包应映射 PLUGIN_PACKAGE_INVALID：%+v", result)
	}
	entries, err := os.ReadDir(workRoot)
	if err != nil {
		t.Fatal(err)
	}
	if len(entries) != 0 {
		t.Fatalf("能力失败路径应清理隔离工作区（6.25），残留 %d 项", len(entries))
	}
}

// TestCapabilityNilPackages 7.15/7.16：包客户端缺失 → 显式拒绝，不 panic。
func TestCapabilityNilPackages(t *testing.T) {
	runner := &Runner{Config: config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}}
	request := capabilityRequest("exec_1", capabilityEntrypoint("v1", "somecap", "file.content.read"))
	if result := runner.ExecuteCapability(context.Background(), request); result.FailureCode != "RUNTIME_CONFIG_INVALID" {
		t.Fatalf("包客户端缺失应显式拒绝：%+v", result)
	}
}

// TestExecuteRedactsHostPaths 6.23：Underlying 错误即使来自宿主路径也在 FailureSummary 中脱敏。
func TestExecuteRedactsHostPaths(t *testing.T) {
	brokerClient := &fakeBroker{
		exchangeFn: func(ctx context.Context, gateID, executionID, ref string, ttl int) (broker.ExchangedLease, error) {
			return broker.ExchangedLease{}, errors.New("连接 /Users/user/Desktop/pcd-runtime/secret 失败")
		},
	}
	runner := newTestRunner(t, config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}, brokerClient, nil)
	result := runner.Execute(context.Background(), preprocessRequest("exec_1", preEntrypoint("v1", "file.content.write_pre_activation")))
	if result.FailureCode != "CONTENT_LEASE_EXCHANGE_FAILED" {
		t.Fatalf("应映射 Exchange 失败：%+v", result)
	}
	if result.FailureSummary == "" || containsDir(result.FailureSummary) {
		t.Fatalf("应有脱敏后的摘要：%q", result.FailureSummary)
	}
}

func containsDir(value string) bool {
	for _, needle := range []string{"/Users/user", "pcd-runtime"} {
		index := 0
		for {
			idx := indexOf(value, needle, index)
			if idx < 0 {
				break
			}
			return true
		}
	}
	return false
}

func indexOf(value, needle string, from int) int {
	for index := from; index+len(needle) <= len(value); index++ {
		if value[index:index+len(needle)] == needle {
			return index
		}
	}
	return -1
}
