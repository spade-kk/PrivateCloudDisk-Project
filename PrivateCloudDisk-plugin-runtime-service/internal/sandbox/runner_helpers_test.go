package sandbox

import (
	"context"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"privateclouddisk/plugin-runtime-service/internal/model"
)

func TestSbxHostnameDeterministicAndDistinct(t *testing.T) {
	// 2.17：相同输入 → 相同输出与长度约束。
	same := sbxHostname("exec_1", 0, 0)
	if same != sbxHostname("exec_1", 0, 0) {
		t.Fatal("相同输入应生成相同主机名（2.17）")
	}
	if len(same) <= 0 || len(same) > 63 {
		t.Fatalf("主机名长度越界：%q (%d)", same, len(same))
	}
	if !strings.HasPrefix(same, "pcd-sbx-") {
		t.Fatalf("主机名缺少前缀：%q", same)
	}
	// 不同输入 → 不同主机名。
	others := []string{
		sbxHostname("exec_2", 0, 0),
		sbxHostname("exec_1", 1, 0),
		sbxHostname("exec_1", 0, 1),
	}
	for _, other := range others {
		if other == same {
			t.Fatalf("不同输入生成了相同主机名 %q", same)
		}
	}
}

func TestMinMaxInt(t *testing.T) {
	if minInt(1, 2) != 1 || minInt(3, 3) != 3 || minInt(-1, 2) != -1 {
		t.Fatal("minInt 语义错误（2.18）")
	}
	if maxInt(1, 2) != 2 || maxInt(3, 3) != 3 || maxInt(-1, -2) != -1 {
		t.Fatal("maxInt 语义错误（2.18）")
	}
}

func TestContains(t *testing.T) {
	if !contains([]string{"a", "b"}, "b") {
		t.Fatal("contains 应命中（2.19）")
	}
	if contains([]string{"a"}, "b") || contains(nil, "a") {
		t.Fatal("contains 不应误报（2.19）")
	}
}

func TestEventDataAndStringValue(t *testing.T) {
	// 2.20/7.18：嵌套 map 提取。
	data := map[string]interface{}{
		"data": map[string]interface{}{"gate_id": "gate-1"},
	}
	if got := eventData(data); got["gate_id"] != "gate-1" {
		t.Fatalf("eventData 提取失败：%v", got)
	}
	if got := eventData(map[string]interface{}{}); got == nil {
		t.Fatal("eventData 应返回空 map 而非 nil")
	}
	// 7.19：stringValue 各种取值。
	if stringValue(nil) != "" || stringValue("x") != "x" || stringValue(42) != "42" {
		t.Fatal("stringValue 转换错误（2.20/7.19）")
	}
}

func TestOutcome(t *testing.T) {
	if outcome(nil) != "success" || outcome(context.DeadlineExceeded) != "failed" {
		t.Fatal("outcome 语义错误（2.21）")
	}
}

func TestFailedSummariesAreSanitized(t *testing.T) {
	// 2.22：failed / capabilityFailed 必须脱敏摘要。
	result := failed("PLUGIN_EXECUTION_FAILED", "读取 /etc/passwd 失败 容器 62be2a75d53130c0b76b96a7eddf6fa343777d39a5c49859d266ec7a9e1e5fcf", 2)
	if result.FailureCode != "PLUGIN_EXECUTION_FAILED" || result.CompletedEntrypoints != 2 {
		t.Fatalf("failed 字段错误：%+v", result)
	}
	if strings.Contains(result.FailureSummary, "/etc/passwd") ||
		strings.Contains(result.FailureSummary, "62be2a75d531") {
		t.Fatalf("failed 摘要未脱敏：%q", result.FailureSummary)
	}
	capability := capabilityFailed("PLUGIN_PACKAGE_FETCH_FAILED", "下载 /var/lib/pcd-runtime/x 失败")
	if capability.Status != "failed" || capability.FailureCode != "PLUGIN_PACKAGE_FETCH_FAILED" {
		t.Fatalf("capabilityFailed 字段错误：%+v", capability)
	}
	if strings.Contains(capability.FailureSummary, "/var/lib/pcd-runtime") {
		t.Fatalf("capabilityFailed 摘要未脱敏：%q", capability.FailureSummary)
	}
}

func TestSummarizeTruncatesAndRedacts(t *testing.T) {
	// 2.23：summarize 长度与脱敏（内部截断 1000 并委托 sanitize）。
	if got := summarize(nil); got != "" {
		t.Fatalf("nil 应返回空串：%q", got)
	}
	long := "很长很长的错误内容" + strings.Repeat("x", 3000)
	if limited := summarize(errors.New(long)); len(limited) > 1000 {
		t.Fatalf("summarize 未截断：len=%d", len(limited))
	}
	redactedErr := summarize(errors.New("/etc/passwd 内网 10.20.0.15"))
	for _, needle := range []string{"/etc/passwd", "10.20.0.15"} {
		if strings.Contains(redactedErr, needle) {
			t.Fatalf("summarize 未脱敏 %q：%q", needle, redactedErr)
		}
	}
	if withLogs := summarizeWithLogs(errors.New("容器退出"), "堆栈 /var/lib/pcd-runtime/work/main.py"); strings.Contains(withLogs, "/var/lib/pcd-runtime") {
		t.Fatalf("summarizeWithLogs 未脱敏日志路径：%q", withLogs)
	}
}

func TestRedactDelegatesToSanitize(t *testing.T) {
	// 2.24：redact 委托 sanitize 并截断。
	output := redact("错误 /etc/passwd 地址 0x7ffc00001234")
	if strings.Contains(output, "/etc/passwd") || strings.Contains(output, "0x7ffc00001234") {
		t.Fatalf("redact 未脱敏：%q", output)
	}
	if redact("") != "" {
		t.Fatal("redact 空串应保持空串")
	}
}

func TestSafeIDRegex(t *testing.T) {
	// 2.25/7.17：合法与非法 execution_id。
	for _, valid := range []string{"exec_0001", "E2e-9", "abc", "0"} {
		if !safeID.MatchString(valid) {
			t.Fatalf("safeID 应接受 %q", valid)
		}
	}
	for _, invalid := range []string{
		"", "has space", "a/b", "..", "x\x00y", strings.Repeat("a", 129), "has|pipe",
	} {
		if safeID.MatchString(invalid) {
			t.Fatalf("safeID 应拒绝 %q", invalid)
		}
	}
}

func TestReadSandboxResultValidation(t *testing.T) {
	root := t.TempDir()
	// 缺文件 → 错误（5.11 前置）。
	if _, err, _ := readSandboxResult(root); err == nil {
		t.Fatal("缺失 result.json 应报错")
	}

	path := filepath.Join(root, "result.json")

	// 超过 1 MiB → 错误。
	if err := os.WriteFile(path, make([]byte, 1024*1024+1), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err, _ := readSandboxResult(root); err == nil {
		t.Fatal("超限 result.json 应报错")
	}
	_ = os.Remove(path)

	// 非法 JSON → 错误（5.12）。
	if err := os.WriteFile(path, []byte("{bad"), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err, _ := readSandboxResult(root); err == nil {
		t.Fatal("非法 JSON 应报错")
	}
	_ = os.Remove(path)

	// 合法 → 正常解析（5.2）。
	payload, _ := json.Marshal(sandboxResult{Success: true, Modified: false, Output: map[string]interface{}{"n": 1}})
	if err := os.WriteFile(path, payload, 0o600); err != nil {
		t.Fatal(err)
	}
	result, err, _ := readSandboxResult(root)
	if err != nil || !result.Success {
		t.Fatalf("合法 result.json 应解析成功：%+v err=%v", result, err)
	}
}

var _ = model.RuntimeChainResult{} // 保持 model 导入被引用
