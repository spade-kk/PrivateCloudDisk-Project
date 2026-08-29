package audit

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

func TestSinkWritesSanitizedJSONLine(t *testing.T) {
	directory := t.TempDir()
	path := filepath.Join(directory, "audit.log")
	sink, err := New(path)
	if err != nil {
		t.Fatal(err)
	}
	defer sink.Close()

	sink.Write(Event{
		Event:       "container_finished",
		Outcome:     "success",
		ExecutionID: "00000000-0000-0000-0000-000000000001",
		PluginID:    "plugin-a",
		VersionID:   "version-1",
		Detail:      []byte(`{"error":"读取 /etc/passwd 失败，容器 a1b2c3d4e5f6"}`),
	})

	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	line := strings.TrimSpace(string(raw))
	if !strings.HasPrefix(line, "{") || !strings.HasSuffix(line, "}") {
		t.Fatalf("审计应写入单行 JSON: %q", line)
	}
	if strings.Contains(line, "/etc/passwd") {
		t.Fatalf("审计详情未脱敏: %s", line)
	}
	if !strings.Contains(line, "container_finished") {
		t.Fatalf("审计事件缺失: %s", line)
	}
}

func TestNoopSinkWritesNothing(t *testing.T) {
	sink, err := New("")
	if err != nil {
		t.Fatal(err)
	}
	sink.Write(Event{Event: "noop"})
	if err := sink.Close(); err != nil {
		t.Fatal(err)
	}
}
