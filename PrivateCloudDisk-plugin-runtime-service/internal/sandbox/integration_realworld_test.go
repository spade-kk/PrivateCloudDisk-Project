//go:build integration

package sandbox

// 真实场景 Python 云插件测试（需求一/四/五/六/八）。
//
// 复用 integration_docker_test.go 的 harness：integrationConfig / integrationRunner /
// newTestRunner / directEntry / preprocessRequest / capabilityRequest / assertNoResidual。
// 沙箱 --network none；插件能力调用仅走 /runtime/runtime.sock 的独占 UDS protobuf RPC。
// 宿主侧 capabilityRelay 实现 Runtime Agent 的 UDS -> Capability Hub 接口，不轮询插件工作目录。
// 基线 = testdata/expected/*.golden（scripts/gen_baselines.py 离线生成），
// 断言真实 Docker 输出与基线逐字节一致（需求三 3.20/八 8.30）。
//
// 注意：require 的 ExecutionID 必须匹配 safeID（字母/数字/-/_），时间戳退化处理。

import (
	"context"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"sync"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// --------------------------------------------------------------------------- 夹具与输入

// inputFor 返回 testdata/input/<id>.* 样例输入内容。
func inputFor(t *testing.T, id string) []byte {
	t.Helper()
	base := filepath.Join("..", "..", "testdata", "input", id)
	for _, ext := range []string{".txt", ".json", ".csv"} {
		if content, err := os.ReadFile(base + ext); err == nil {
			return content
		}
	}
	t.Fatalf("realworld 插件 %s 缺少样例输入", id)
	return nil
}

// goldenFor 返回 <plugin>.golden 期望输出。
func goldenFor(t *testing.T, id string) []byte {
	t.Helper()
	content, err := os.ReadFile(filepath.Join("..", "..", "testdata", "expected", id+".golden"))
	if err != nil {
		t.Fatalf("读取基线 %s.golden：%v", id, err)
	}
	return content
}

// goldenMode 读取 .golden.mode：output-bin / return-json / chain。
func goldenMode(t *testing.T, id string) string {
	t.Helper()
	content, err := os.ReadFile(filepath.Join("..", "..", "testdata", "expected", id+".golden.mode"))
	if err != nil {
		t.Fatalf("读取基线模式 %s.golden.mode：%v", id, err)
	}
	return strings.TrimSpace(string(content))
}

// uploadContent 返回 fakeBroker 在 Upload 时同步捕获的唯一候选内容（Execute 链末入口输出）。
// Execute 结束后工作区会被 RemoveAll 清理，故必须在 Upload 时捕获而不是事后读源文件。
func uploadContent(t *testing.T, brokerClient *fakeBroker) []byte {
	t.Helper()
	brokerClient.mu.Lock()
	defer brokerClient.mu.Unlock()
	if len(brokerClient.uploadContents) != 1 {
		t.Fatalf("应恰好捕获 1 份上传内容，实际 %d（sources=%v）",
			len(brokerClient.uploadContents), brokerClient.uploadSources)
	}
	return brokerClient.uploadContents[0]
}

// --------------------------------------------------------------------------- 能力网关 relay

type capabilityCall struct {
	ID            string
	CapabilityKey string
	Parameters    map[string]any
	UserID        string
	SpaceID       string
}

// capabilityRelay is a trusted test-only Capability Hub fake. CF-PLUGIN-UDS-001
// keeps identity from the Runtime session instead of any plugin request payload.
type capabilityRelay struct {
	mu       sync.Mutex
	calls    []capabilityCall
	handlers map[string]func(params map[string]any) (map[string]any, string, string)
}

func newCapabilityRelay() *capabilityRelay {
	return &capabilityRelay{
		handlers: map[string]func(map[string]any) (map[string]any, string, string){},
	}
}

func (r *capabilityRelay) handle(key string, fn func(map[string]any) (map[string]any, string, string)) {
	r.mu.Lock()
	defer r.mu.Unlock()
	r.handlers[key] = fn
}

func (r *capabilityRelay) Invoke(_ context.Context, request uds.Invocation) (uds.InvocationResult, error) {
	r.mu.Lock()
	handler := r.handlers[request.CapabilityKey]
	r.calls = append(r.calls, capabilityCall{
		ID: request.RequestID, CapabilityKey: request.CapabilityKey, Parameters: request.Parameters,
		UserID: request.UserID, SpaceID: request.SpaceID,
	})
	r.mu.Unlock()
	if handler == nil {
		return uds.InvocationResult{ErrorCode: "CAPABILITY_UNKNOWN", Message: "能力未在测试网关注册"}, nil
	}
	output, code, message := handler(request.Parameters)
	if code != "" {
		return uds.InvocationResult{ErrorCode: code, Message: message}, nil
	}
	return uds.InvocationResult{Output: output}, nil
}

func (r *capabilityRelay) callsSnapshot() []capabilityCall {
	r.mu.Lock()
	defer r.mu.Unlock()
	out := make([]capabilityCall, len(r.calls))
	copy(out, r.calls)
	return out
}

// --------------------------------------------------------------------------- 执行 Runner 构造

// realworldRunner 构造 Execute/ExecuteCapability 用 Runner：插件包来自 realworld 目录，
// 输入来自 testdata/input，能力 relay 挂载在 cfg.WorkRoot。
func realworldRunner(t *testing.T, cfg config.Config, id string, modules ...string) (*Runner, *fakeBroker, *capabilityRelay) {
	t.Helper()
	if len(modules) == 0 {
		modules = []string{"main.py"}
	}
	packages := &fakePackages{zips: map[string][]byte{"v1": realworldModuleBytes(t, id, modules...)}}
	brokerClient := &fakeBroker{}
	brokerClient.downloadFn = func(ctxArg context.Context, gateID, executionID, lease, destination string) error {
		return os.WriteFile(destination, inputFor(t, id), 0o400)
	}
	relay := newCapabilityRelay()
	runner := newTestRunner(t, cfg, brokerClient, packages)
	_ = runner.Sessions.Close()
	sessions, err := uds.NewManager(uds.Config{
		RootDir: runner.Config.SocketRoot, GroupID: os.Getgid(), MaxFrameBytes: runner.Config.SocketMaxFrameBytes,
		MaxConnectionsPerPeer: runner.Config.SocketMaxConnections, RequestsPerSecond: runner.Config.SocketRequestsPerSec,
		RequestBurst: runner.Config.SocketRequestBurst, RequestTimeout: runner.Config.SocketRequestTimeout,
	}, relay)
	if err != nil { t.Fatalf("创建 UDS Relay 会话管理器失败：%v", err) }
	runner.Sessions = sessions
	t.Cleanup(func() { _ = sessions.Close() })
	return runner, brokerClient, relay
}

// executeEntries 构造 Execute 链入口（manifest 驱动：请求只表达事件，模块/函数来自 manifest）。
func executeEntries(modules ...string) []model.Entrypoint {
	return []model.Entrypoint{{
		PluginID: "plugin-1", VersionID: "v1", Runtime: "PYTHON_3_11",
		Event: EventContentReady, Permissions: []string{"file.content.read_staging", "file.content.write_pre_activation"},
	}}
}

// capabilityPermissionEntry 构造 ExecuteCapability 入口：Capability 名来自 manifest exports.name。
func capabilityPermissionEntry(capability string, permissions ...string) model.Entrypoint {
	merged := append([]string{}, permissions...)
	return model.Entrypoint{
		PluginID: "plugin-1", VersionID: "v1", Runtime: "PYTHON_3_11",
		Capability: capability, Permissions: merged,
	}
}

// assertOutputBinGolden 断言 Execute 上传源内容与 golden 逐字节一致（需求三 3.20）。
func assertOutputBinGolden(t *testing.T, result model.RuntimeChainResult, brokerClient *fakeBroker, id string) {
	t.Helper()
	if result.Status != "success" || !result.ContentModified {
		t.Fatalf("%s 应修改并提交候选：%+v", id, result)
	}
	actual := uploadContent(t, brokerClient)
	expected := goldenFor(t, id)
	if !reflect.DeepEqual(actual, expected) {
		t.Fatalf("%s 输出与基线不一致：\n--- got (%d) ---\n%s\n--- want (%d) ---\n%s",
			id, len(actual), string(actual), len(expected), string(expected))
	}
}

// assertReturnJSONGolden 断言 ExecuteCapability Output 与基线 JSON 相等（键序无关）。
func assertReturnJSONGolden(t *testing.T, result model.CapabilityExecutionResult, id string) {
	t.Helper()
	if result.Status != "success" {
		t.Fatalf("%s 能力执行应成功：%+v", id, result)
	}
	var want map[string]any
	if err := json.Unmarshal(goldenFor(t, id), &want); err != nil {
		t.Fatalf("解析基线 %s.golden：%v", id, err)
	}
	if !reflect.DeepEqual(result.Output, want) {
		t.Fatalf("%s 输出与基线不一致：got=%#v want=%#v", id, result.Output, want)
	}
}

// realExecID 生成合法 execution_id。
func realExecID(id string, n int) string {
	return fmt.Sprintf("real_%s_%d", strings.ReplaceAll(id, "_", ""), n)
}

// --------------------------------------------------------------------------- Execute 修改链（需求五 5.5-5.8 / 八）

func TestRealWorldTextStats(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "text_stats")
	executionID := realExecID("text_stats", 1)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	assertOutputBinGolden(t, result, broker, "text_stats")
	assertNoResidual(t, executionID)
}

func TestRealWorldJsonCleaner(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "json_cleaner")
	executionID := realExecID("json_cleaner", 2)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	assertOutputBinGolden(t, result, broker, "json_cleaner")
	assertNoResidual(t, executionID)
}

func TestRealWorldCsvReport(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "csv_report")
	executionID := realExecID("csv_report", 3)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	assertOutputBinGolden(t, result, broker, "csv_report")
	assertNoResidual(t, executionID)
}

func TestRealWorldExcelParse(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "excel_parse")
	executionID := realExecID("excel_parse", 4)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	assertOutputBinGolden(t, result, broker, "excel_parse")
	assertNoResidual(t, executionID)
}

func TestRealWorldContentReverse(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "content_reverse")
	executionID := realExecID("content_reverse", 5)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	assertOutputBinGolden(t, result, broker, "content_reverse")
	assertNoResidual(t, executionID)
}

// TestRealWorldExcelGenerate 6.9/8.13-8.14：能力网关 mock 生成 CSV 报表并写回候选。
func TestRealWorldExcelGenerate(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, relay := realworldRunner(t, cfg, "excel_generate")
	relay.handle("api.file.generate_excel", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{
			"content_type": "text/csv",
			"content":      "product,amount,price\ndisk,3,99.5\nssd,2,299.0\n",
		}, "", ""
	})
	// 插件需要平台能力调用权限。
	entries := executeEntries("main.py")
	entries[0].Permissions = append(entries[0].Permissions, "platform.capability.invoke")
	executionID := realExecID("excel_generate", 6)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, entries...))
	assertOutputBinGolden(t, result, broker, "excel_generate")
	calls := relay.callsSnapshot()
	if len(calls) != 1 || calls[0].CapabilityKey != "api.file.generate_excel" {
		t.Fatalf("能力网关调用记录异常：%+v", calls)
	}
	assertNoResidual(t, executionID)
}

// TestRealWorldMultiEntryChain 1.7/8.27/5.9：多入口顺序执行，末入口修改生效。
func TestRealWorldMultiEntryChain(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, broker, _ := realworldRunner(t, cfg, "multi_entry_pkg", "step_a.py", "step_b.py")
	executionID := realExecID("multi", 7)
	request := preprocessRequest(executionID, executeEntries("step_a.py", "step_b.py")...)
	result := runner.Execute(context.Background(), request)
	if result.Status != "success" || !result.ContentModified || result.CompletedEntrypoints != 2 {
		t.Fatalf("多入口链结果异常：%+v", result)
	}
	actual := uploadContent(t, broker)
	if string(actual) != string(goldenFor(t, "multi_entry_pkg")) {
		t.Fatalf("多入口链输出与基线不一致：got=%q", string(actual))
	}
	assertNoResidual(t, executionID)
}

// --------------------------------------------------------------------------- ExecuteCapability（需求一 1.4 / 八 8.16-8.17, 8.24）

func TestRealWorldCapabilityReport(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, _, _ := realworldRunner(t, cfg, "capability_report")
	entrypoint := capabilityPermissionEntry("generate_report", "file.content.read")
	executionID := realExecID("cap_report", 8)
	request := capabilityRequest(executionID, entrypoint)
	request.Input = map[string]interface{}{"text": "privacy cloud disk\nhello world\nprivacy first\n"}
	result := runner.ExecuteCapability(context.Background(), request)
	assertReturnJSONGolden(t, result, "capability_report")
	assertNoResidual(t, executionID)
}

// TestRealWorldCapabilityUserInfo 6.5/8.24：mock 网关返回脱敏用户信息且传递用户上下文。
func TestRealWorldCapabilityUserInfo(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, _, relay := realworldRunner(t, cfg, "capability_user_info")
	relay.handle("api.user.info", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{
			"user_id":  "user-1",
			"nickname": "u***r",
			"email":    "***@example.com",
		}, "", ""
	})
	// manifest 驱动的能力导出名为 read_file（见 manifest.yaml exports）。
	entrypoint := capabilityPermissionEntry("user_info", "file.content.read", "platform.capability.invoke")
	executionID := realExecID("cap_user", 9)
	request := capabilityRequest(executionID, entrypoint)
	result := runner.ExecuteCapability(context.Background(), request)
	assertReturnJSONGolden(t, result, "capability_user_info")
	calls := relay.callsSnapshot()
	if len(calls) != 1 || calls[0].CapabilityKey != "api.user.info" || calls[0].UserID != "user-1" || calls[0].SpaceID != "space-1" {
		t.Fatalf("用户信息能力上下文未透传：%+v", calls)
	}
	assertNoResidual(t, executionID)
}

// TestRealWorldPathEscape 7.4/8.21：能力网关拒绝非白名单路径，插件返回 blocked。
func TestRealWorldPathEscape(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, _, relay := realworldRunner(t, cfg, "path_escape")
	relay.handle("api.file.content.get", func(params map[string]any) (map[string]any, string, string) {
		if path, _ := params["path"].(string); strings.HasPrefix(path, "/etc/") {
			return nil, "CAPABILITY_FORBIDDEN", "路径不在可访问白名单"
		}
		return map[string]any{"name": "file.txt"}, "", ""
	})
	entrypoint := capabilityPermissionEntry("read_file", "file.content.read_staging", "platform.capability.invoke")
	executionID := realExecID("escape", 10)
	request := capabilityRequest(executionID, entrypoint)
	result := runner.ExecuteCapability(context.Background(), request)
	assertReturnJSONGolden(t, result, "path_escape")
	calls := relay.callsSnapshot()
	if len(calls) != 1 || !strings.HasPrefix(fmtValue(calls[0].Parameters["path"]), "/") {
		t.Fatalf("路径逃逸调用记录异常：%+v", calls)
	}
	assertNoResidual(t, executionID)
}

func fmtValue(value interface{}) string {
	if text, ok := value.(string); ok {
		return text
	}
	return ""
}

// --------------------------------------------------------------------------- 异常/失败语义（需求五 5.4/5.10/5.11 / 八 8.18-8.19, 8.25）

func TestRealWorldTimeoutSim(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.ExecutionTimeout = 4 * time.Second
	runner, _, _ := realworldRunner(t, cfg, "timeout_sim")
	executionID := realExecID("timeout", 11)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	if result.Status != "timeout" || result.FailureCode != "PLUGIN_RUNTIME_TIMEOUT" {
		t.Fatalf("超时插件应返回 timeout：%+v", result)
	}
	assertNoResidual(t, executionID)
}

func TestRealWorldResourceHog(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	cfg.MemoryBytes = 256 * 1024 * 1024
	runner, _, _ := realworldRunner(t, cfg, "resource_hog")
	executionID := realExecID("hog", 12)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	if result.Status == "success" {
		t.Fatalf("资源耗尽应失败：%+v", result)
	}
	if result.FailureSummary != "" && strings.Contains(result.FailureSummary, "/Users/") {
		t.Fatalf("失败摘要泄露宿主路径：%s", result.FailureSummary)
	}
	assertNoResidual(t, executionID)
}

func TestRealWorldInvalidOutput(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	runner, _, _ := realworldRunner(t, cfg, "invalid_output")
	executionID := realExecID("invalid", 13)
	result := runner.Execute(context.Background(), preprocessRequest(executionID, executeEntries("main.py")...))
	if result.Status != "failed" || result.FailureCode != "PLUGIN_EXECUTION_FAILED" {
		t.Fatalf("无效输出应返回 PLUGIN_EXECUTION_FAILED：%+v", result)
	}
	if result.FailureSummary != "" && strings.Contains(result.FailureSummary, "/") {
		t.Fatalf("无效输出摘要泄露路径：%s", result.FailureSummary)
	}
	assertNoResidual(t, executionID)
}

// --------------------------------------------------------------------------- 并发隔离（需求一 1.12 / 五 5.27）

func TestRealWorldConcurrentIsolation(t *testing.T) {
	dockerAvailable(t)
	cfg := integrationConfig(t)
	const instances = 3
	var wg sync.WaitGroup
	results := make([]model.RuntimeChainResult, instances)
	brokers := make([]*fakeBroker, instances)
	for index := 0; index < instances; index++ {
		wg.Add(1)
		go func(n int) {
			defer wg.Done()
			runner, broker, _ := realworldRunner(t, cfg, "content_reverse")
			results[n] = runner.Execute(context.Background(),
				preprocessRequest(realExecID("conc", n+20), executeEntries("main.py")...))
			brokers[n] = broker
		}(index)
	}
	wg.Wait()
	expected := goldenFor(t, "content_reverse")
	for index := 0; index < instances; index++ {
		if results[index].Status != "success" || !results[index].ContentModified {
			t.Fatalf("并发实例 %d 失败：%+v", index, results[index])
		}
		actual := uploadContent(t, brokers[index])
		if !reflect.DeepEqual(actual, expected) {
			t.Fatalf("并发实例 %d 输出不一致", index)
		}
	}
}
