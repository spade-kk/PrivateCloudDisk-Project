package sanitize

import (
	"errors"
	"strings"
	"testing"
)

func TestSanitizeRedactsAbsolutePosixPath(t *testing.T) {
	output := Sanitize("读取失败 /var/lib/pcd-runtime/work/abc/plugin/main.py")
	if strings.Contains(output, "/var/lib/pcd-runtime") {
		t.Fatalf("绝对路径未脱敏: %s", output)
	}
	if !strings.Contains(output, "[path]") {
		t.Fatalf("绝对路径应替换为 [path]: %s", output)
	}
}

func TestSanitizeRedactsWindowsDrivePath(t *testing.T) {
	output := Sanitize(`写入失败 C:\Users\dev\Temp\out.bin`)
	if strings.Contains(output, `C:\Users`) {
		t.Fatalf("Windows 路径未脱敏: %s", output)
	}
}

func TestSanitizeRedactsContainerIDAndChecksum(t *testing.T) {
	container := "a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef0"
	output := Sanitize("容器 " + container + " 异常退出")
	if strings.Contains(output, container) {
		t.Fatalf("容器 ID 未脱敏: %s", output)
	}
	if !strings.Contains(output, "[id]") {
		t.Fatalf("容器 ID 应替换为 [id]: %s", output)
	}
}

func TestSanitizeRedactsPrivateIPAndMemoryAddress(t *testing.T) {
	output := Sanitize("连接 10.20.0.15:8090 失败，指针 0x7ffc00001234")
	if strings.Contains(output, "10.20.0.15") || strings.Contains(output, "0x7ffc00001234") {
		t.Fatalf("IP 或内存地址未脱敏: %s", output)
	}
}

func TestSanitizeRedactsInternalHostname(t *testing.T) {
	output := Sanitize("无法解析 plugin-service.example.internal")
	if strings.Contains(output, "example.internal") {
		t.Fatalf("内部主机名未脱敏: %s", output)
	}
}

func TestSanitizeRedactsCredentialPairs(t *testing.T) {
	output := Sanitize("token=abc123 secret=s3cr3t 继续")
	if strings.Contains(output, "abc123") || strings.Contains(output, "s3cr3t") {
		t.Fatalf("凭据未脱敏: %s", output)
	}
}

func TestSanitizeErrorTruncatesAndFlattens(t *testing.T) {
	err := errors.New("第一行\n绝对路径 /etc/passwd\n第二行")
	output := Error(err, 64)
	if strings.Contains(output, "\n") {
		t.Fatalf("错误应压平为单行: %q", output)
	}
	if strings.Contains(output, "/etc/passwd") {
		t.Fatalf("错误内绝对路径未脱敏: %q", output)
	}
	if len(output) > 64 {
		t.Fatalf("错误应截断: %d", len(output))
	}
}

func TestTracebackStripsFramesAndFilePaths(t *testing.T) {
	stack := "Traceback (most recent call last):\n" +
		"  File \"/var/lib/pcd-runtime/plugin/main.py\", line 3, in main\n" +
		"    return eval(source)\n" +
		"ZeroDivisionError: division by zero"
	output := Traceback(stack, 512)
	if strings.Contains(output, "/var/lib/pcd-runtime") {
		t.Fatalf("堆栈含绝对路径: %s", output)
	}
	if strings.Contains(output, "Traceback") {
		t.Fatalf("堆栈头帧不应透出: %s", output)
	}
	if !strings.Contains(output, "ZeroDivisionError") {
		t.Fatalf("应保留异常类型: %s", output)
	}
}

func TestExtendAddsCustomRule(t *testing.T) {
	before := Sanitize("敏感词职粉")
	Extend([]string{`职粉`})
	after := Sanitize("敏感词职粉")
	if before == after {
		t.Fatalf("自定义规则未生效: %q vs %q", before, after)
	}
}

func TestSetDebugFlag(t *testing.T) {
	SetDebug(true)
	if !Debug() {
		t.Fatal("debug 标志未生效")
	}
	SetDebug(false)
	if Debug() {
		t.Fatal("debug 标志未能关闭")
	}
}

func TestRawJSONSanitizesAbsolutePaths(t *testing.T) {
	input := []byte(`{"error":"读取 /etc/passwd 与 /var/lib/pcd-runtime/x 失败","ok":true}`)
	output := RawJSON(input)
	if string(output) == string(input) {
		t.Fatalf("RawJSON 未脱敏：%s", output)
	}
	for _, needle := range []string{"/etc/passwd", "/var/lib/pcd-runtime/x"} {
		if strings.Contains(string(output), needle) {
			t.Fatalf("RawJSON 泄露路径 %s：%s", needle, output)
		}
	}
	if !strings.Contains(string(output), `"ok":true`) {
		t.Fatalf("RawJSON 破坏结构：%s", output)
	}
	if got := RawJSON(nil); got != nil {
		t.Fatalf("空输入应原样返回：%q", got)
	}
}

func TestPathReturnsBaseOnly(t *testing.T) {
	for _, td := range []struct{ in, want string }{
		{"", ""},
		{"/var/lib/pcd-runtime/work/xxx/main.py", "main.py"},
		{"plain.txt", "plain.txt"},
	} {
		if got := Path(td.in); got != td.want {
			t.Fatalf("Path(%q)=%q 期望 %q", td.in, got, td.want)
		}
	}
}

func TestExtendConcurrentAndSummaryBoundary(t *testing.T) {
	// 并发追加规则不 panic、不竞态。
	for index := 0; index < 8; index++ {
		func(seed int) {
			Extend([]string{"MYTOKEN-" + "x"})
		}(index)
	}
	sanitized := Sanitize("contains MYTOKEN-xxxxx here")
	if !strings.Contains(sanitized, "[redacted]") {
		t.Fatalf("扩展规则未生效：%v", sanitized)
	}

	long := strings.Repeat("a", 5000)
	if got := Summary(long, 1000); len(got) != 1000 {
		t.Fatalf("Summary 截断错误：len=%d", len(got))
	}
	collapsed := Summary("a\nb\rc\nd", 0)
	if strings.ContainsAny(collapsed, "\n\r") {
		t.Fatalf("Summary 应折叠换行：%q", collapsed)
	}
}

func TestErrorAndTracebackBoundary(t *testing.T) {
	err := &fakeError{Msg: "open /home/user/secret/foo.txt: 权限拒绝 0xc000012345"}
	got := Error(err, 200)
	if strings.Contains(got, "/home/user") || strings.Contains(got, "0xc000012345") {
		t.Fatalf("Error 未脱敏：%s", got)
	}

	tb := "Traceback (most recent call last):\n  File \"/var/lib/y.py\", line 3, in main\n    raise ValueError('bad')\nValueError: bad"
	out := Traceback(tb, 400)
	if strings.Contains(out, "Traceback") || strings.Contains(out, "File \"") || strings.Contains(out, "/var/lib/y.py") {
		t.Fatalf("Traceback 未移除帧与路径：%s", out)
	}
	if !strings.Contains(out, "ValueError") {
		t.Fatalf("Traceback 应保留异常类型：%s", out)
	}
}

type fakeError struct{ Msg string }

func (f *fakeError) Error() string { return f.Msg }
