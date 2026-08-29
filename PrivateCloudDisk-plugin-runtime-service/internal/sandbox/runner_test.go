package sandbox

import (
	"archive/zip"
	"bytes"
	"context"
	"fmt"
	"os"
	"path/filepath"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	pkg "privateclouddisk/plugin-runtime-service/internal/package"
)

// TestParseRejectsTraversal 路径穿越包在运行层被 Parse 拒绝（含 src/ 目录穿越）。
func TestParseRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	archive := filepath.Join(root, "plugin.pcdpkg")
	file, err := os.Create(archive)
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(file)
	for _, name := range []string{"manifest.yaml", "src/main.py", "../escape.py"} {
		content := []byte("def main(context):\n    return {}\n")
		if name == "manifest.yaml" {
			content = []byte(sandboxValidManifest)
		}
		header := &zip.FileHeader{Name: name, Method: zip.Deflate}
		header.SetMode(0o400)
		entry, err := writer.CreateHeader(header)
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write(content); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	_, err = pkg.Parse(archive, filepath.Join(root, "out"), pkg.Options{})
	if err == nil {
		t.Fatal("包含目录穿越的包不应通过 Runtime 二次校验")
	}
	if _, statErr := os.Stat(filepath.Join(root, "escape.py")); !os.IsNotExist(statErr) {
		t.Fatal("目录穿越文件不应写到目标目录之外")
	}
}

// TestCapabilityRejectsPreActivationWritePermission 工作流能力入口声明预激活写权限 → CONTENT_FROZEN。
// 能力权限由 manifest exports 声明（不含 manifest 则无法解析，故用合法 .pcdpkg 前置）。
func TestCapabilityRejectsPreActivationWritePermission(t *testing.T) {
	cfg := config.Config{ExecutionTimeout: 30 * time.Second, WorkRoot: t.TempDir()}
	manifest := manifestYAML(fixtureManifestID("fixture", 9), "1.0.0",
		EventContentReady, "src/main.py", "main", []string{"file.content.write_pre_activation"}, []string{"file.content.write_pre_activation"})
	manifest += `exports:
  - name: generate
    module: src/main.py
    function: generate
    permissions:
      - file.content.write_pre_activation
`
	packages := &fakePackages{zips: map[string][]byte{"v1": pcdpkgBytes(t, manifest, map[string][]byte{
		"src/main.py": []byte("def main(context):\n    return {}\n\ndef generate(input_data):\n    return {}\n"),
	})}}
	runner := newTestRunner(t, cfg, nil, packages)
	entry := model.Entrypoint{
		VersionID:   "v1",
		Runtime:     "PYTHON_3_11",
		Capability:  "generate",
		Permissions: []string{"file.content.write_pre_activation"},
	}
	result := runner.ExecuteCapability(context.Background(), model.CapabilityExecutionRequest{
		ExecutionID: "execution_1",
		StepID:      "step_1",
		UserID:      "00000000-0000-0000-0000-000000000001",
		SpaceID:     "space_1",
		Input:       map[string]interface{}{},
		Entrypoint:  entry,
	})
	if result.Status != "failed" || result.FailureCode != "CONTENT_FROZEN" {
		t.Fatalf("工作流能力必须拒绝预激活写权限，实际=%+v", result)
	}
}

func TestLimitedBufferTruncatesWithoutShortWrite(t *testing.T) {
	buffer := NewLimitedBuffer(4)
	if count, err := buffer.Write([]byte("123456")); err != nil || count != 6 {
		t.Fatalf("写入结果异常: count=%d err=%v", count, err)
	}
	if value := buffer.String(); value[:4] != "1234" {
		t.Fatalf("日志截断结果错误: %q", value)
	}
}

func TestExecutionAuditRootIDMatchesAutomationJavaNameUUID(t *testing.T) {
	// UUID.nameUUIDFromBytes("execution-1:runtime-root".getBytes(UTF_8))
	// computed by Automation Service. Keep the literal to detect any cross-
	// language change that would disconnect Agent child records from the root.
	if actual := executionAuditRootID("execution-1"); actual != "a74761c4-f5d4-38f9-9f34-6da9f2432f5f" {
		t.Fatalf("Automation audit root mismatch: %s", actual)
	}
}

var _ = fmt.Sprintf // 保持 fmt 导入（部分场景扩展时使用）
var _ = bytes.MinRead
