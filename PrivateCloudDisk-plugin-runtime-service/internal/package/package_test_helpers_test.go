package pkg

import (
	"archive/zip"
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"testing"
)

// writeZip 构造 zip 并写入 archivePath（含可选 mode 覆盖，用于符号链接/设备/可执行样本）。
func writeZip(t *testing.T, archivePath string, entries map[string][]byte, modes map[string]os.FileMode) {
	t.Helper()
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	for name, content := range entries {
		header := &zip.FileHeader{Name: name, Method: zip.Deflate}
		header.SetMode(0o400)
		if modes != nil {
			if mode, ok := modes[name]; ok {
				header.SetMode(mode)
			}
		}
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
	if err := os.MkdirAll(filepath.Dir(archivePath), 0o700); err != nil {
		t.Fatal(err)
	}
	if err := os.WriteFile(archivePath, buffer.Bytes(), 0o600); err != nil {
		t.Fatal(err)
	}
}

// duplicateZip 构造包含两个同名 manifest.yaml 条目的 zip（触发 O_EXCL 拒绝）。
func duplicateZip(t *testing.T) []byte {
	t.Helper()
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	for len := 0; len < 2; len++ {
		entry, err := writer.Create("manifest.yaml")
		if err != nil {
			t.Fatal(err)
		}
		if _, err := entry.Write([]byte(validManifest)); err != nil {
			t.Fatal(err)
		}
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

// assertKind 断言错误为指定 ErrorKind 的 ParseError。
func assertKind(t *testing.T, err error, want ErrorKind) {
	t.Helper()
	if err == nil {
		t.Fatalf("应解析失败，want kind=%s", want)
	}
	var parseErr *ParseError
	if !errors.As(err, &parseErr) {
		t.Fatalf("应为 ParseError，实际 %T：%v", err, err)
	}
	if parseErr.Kind != want {
		t.Fatalf("kind=%s want=%s（err=%v）", parseErr.Kind, want, err)
	}
}

const validManifest = `manifest_version: 1
plugin:
  id: 8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7
  name: image-compressor
  type: CLOUD_PLUGIN
  version: 1.0.0
runtime:
  language: python
  version: "3.11"
permissions:
  - file.content.read_staging
  - file.content.write_pre_activation
  - file.content.read
  - file.metadata.write
  - file.location.move
  - notification.send
entrypoints:
  events:
  - event: pcd.file.content.ready.v1
    module: src/main.py
    function: preprocess
    priority: 100
    conditions:
      mime_types: ["image/jpeg"]
    permissions:
      - file.content.read_staging
      - file.content.write_pre_activation
  - event: pcd.file.available.v1
    module: src/main.py
    function: after_available
    permissions:
      - file.content.read
      - file.metadata.write
      - file.location.move
      - notification.send
exports:
  - name: compress
    description: 压缩指定图片
    module: src/main.py
    function: compress
    input_schema: schemas/capability.compress.input.json
    output_schema: schemas/capability.compress.output.json
limits:
  timeout_seconds: 120
  memory_mb: 256
`

// validPackageEntries 返回通过全部安全校验的 .pcdpkg 示例内容。
func validPackageEntries() map[string][]byte {
	return map[string][]byte{
		"manifest.yaml":            []byte(validManifest),
		"src/main.py":              []byte("def preprocess(context):\n    return {}\n\ndef after_available(context):\n    return {}\n\ndef compress(input_data):\n    return {}\n"),
		"schemas/config.schema.json": []byte(`{"type":"object"}`),
		"schemas/capability.compress.input.json": []byte(`{"type":"object"}`),
		"schemas/capability.compress.output.json": []byte(`{"type":"object"}`),
		"README.md":                []byte("# demo\n"),
		"LICENSE":                  []byte("MIT\n"),
	}
}
