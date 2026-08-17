package sandbox

import (
	"archive/zip"
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
)

func TestExtractPackageRejectsTraversal(t *testing.T) {
	root := t.TempDir()
	archive := filepath.Join(root, "plugin.pcdpkg")
	file, err := os.Create(archive)
	if err != nil {
		t.Fatal(err)
	}
	writer := zip.NewWriter(file)
	entry, err := writer.Create("../escape.py")
	if err != nil {
		t.Fatal(err)
	}
	if _, err := entry.Write([]byte("print('bad')")); err != nil {
		t.Fatal(err)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	if err := file.Close(); err != nil {
		t.Fatal(err)
	}

	if err := extractPackage(archive, filepath.Join(root, "out"), 1024); err == nil {
		t.Fatal("包含目录穿越的包不应通过 Runtime 二次校验")
	}
	if _, err := os.Stat(filepath.Join(root, "escape.py")); !os.IsNotExist(err) {
		t.Fatal("目录穿越文件不应写到目标目录之外")
	}
}

func TestCapabilityRejectsPreActivationWritePermission(t *testing.T) {
	runner := &Runner{Config: config.Config{
		ExecutionTimeout: time.Second,
		WorkRoot:         t.TempDir(),
	}}
	result := runner.ExecuteCapability(context.Background(), model.CapabilityExecutionRequest{
		ExecutionID: "execution_1",
		StepID:      "step_1",
		UserID:      "00000000-0000-0000-0000-000000000001",
		Entrypoint: model.Entrypoint{
			VersionID:   "00000000-0000-0000-0000-000000000002",
			Runtime:     "PYTHON_3_11",
			ModulePath:  "main.py",
			FunctionName: "generate",
			Permissions: []string{"file.content.write_pre_activation"},
		},
	})

	if result.Status != "failed" || result.FailureCode != "RUNTIME_POLICY_REJECTED" {
		t.Fatalf("工作流能力必须拒绝预激活写权限，实际结果=%+v", result)
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
