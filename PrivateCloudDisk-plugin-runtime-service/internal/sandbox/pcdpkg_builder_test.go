package sandbox

// .pcdpkg 测试构造器：为单元/集成测试生成受约束 ZIP（manifest.yaml + src/），
// 与 internal/package 的 Parse 校验保持对齐。

import (
	"archive/zip"
	"bytes"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// manifestYAML 依据入口信息生成受约束 manifest.yaml 文本。
func manifestYAML(pluginID, version, event, module, function string, permissions, eventPermissions []string) string {
	permBlock := "permissions:\n"
	for _, permission := range permissions {
		permBlock += "  - " + permission + "\n"
	}
	eventPerm := "    permissions:\n"
	for _, permission := range eventPermissions {
		eventPerm += "      - " + permission + "\n"
	}
	return fmt.Sprintf(`manifest_version: 1
plugin:
  id: %s
  name: fixture-plugin
  type: CLOUD_PLUGIN
  version: %s
runtime:
  language: python
  version: "3.11"
%sentrypoints:
  events:
  - event: %s
    module: %s
    function: %s
    priority: 10
%s
`, pluginID, version, permBlock, event, module, function, eventPerm)
}

// fixtureManifestID 返回固定 UUID 形态的插件 ID（纯小写十六进制，供 fixture 包复用）。
func fixtureManifestID(slug string, index int) string {
	hex := fmt.Sprintf("%032x", index)
	_ = slug // 保留参数签名以便调用方表达语义；UUID 部分固定为纯 hex。
	return hex[0:8] + "-" + hex[8:12] + "-" + hex[12:16] + "-" + hex[16:20] + "-" + hex[20:32]
}

// pcdpkgBytes 把 manifest + 附加文件打包成内存 .pcdpkg（受约束结构）。
func pcdpkgBytes(t *testing.T, manifest string, extra map[string][]byte) []byte {
	t.Helper()
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	writeEntry := func(name string, content []byte) {
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
	writeEntry("manifest.yaml", []byte(manifest))
	for name, content := range extra {
		writeEntry(name, content)
	}
	writeEntry("README.md", []byte("# fixture\n"))
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}

// fixturePcdpkgBytes 打包一个单入口脚本为 .pcdpkg（src/<module>）。
func fixturePcdpkgBytes(t *testing.T, versionID, module, function string, permissions []string, content []byte) []byte {
	t.Helper()
	if content == nil {
		content = []byte("def " + function + "(context):\n    return {}\n")
	}
	manifest := manifestYAML(fixtureManifestID("fixture", 1), "1.0.0",
		EventContentReady, "src/"+module, function, permissions, permissions)
	return pcdpkgBytes(t, manifest, map[string][]byte{"src/" + module: content})
}

// fixturePcdpkgBytesEvent 打包单入口脚本为 .pcdpkg，指定事件类型。
func fixturePcdpkgBytesEvent(t *testing.T, event, module, function string, permissions []string, content []byte) []byte {
	t.Helper()
	if content == nil {
		content = []byte("def " + function + "(context):\n    return {}\n")
	}
	manifest := manifestYAML(fixtureManifestID("fixture", 1), "1.0.0",
		event, "src/"+module, function, permissions, permissions)
	return pcdpkgBytes(t, manifest, map[string][]byte{"src/" + module: content})
}

// fixtureCapabilityPcdpkgBytes 打包一个含能力导出的 .pcdpkg（exports[0]）。
func fixtureCapabilityPcdpkgBytes(t *testing.T, versionID, module, function, capability string, permissions []string, content []byte) []byte {
	t.Helper()
	return fixtureCapabilityPcdpkgBytesPerms(t, versionID, module, function, capability, permissions, []string{"file.content.read"}, content)
}

// fixtureCapabilityPcdpkgBytesPerms 打包能力导出包，可定制 export 权限（冻结/越权用例）。
func fixtureCapabilityPcdpkgBytesPerms(t *testing.T, versionID, module, function, capability string, globalPerms, exportPerms []string, content []byte) []byte {
	t.Helper()
	if content == nil {
		content = []byte("def " + function + "(input_data):\n    return {}\n")
	}
	base := manifestYAML(fixtureManifestID("fixture", 2), "1.0.0",
		EventContentReady, "src/"+module, function, globalPerms, globalPerms)
	manifest := base + fmt.Sprintf(`exports:
  - name: %s
    module: src/%s
    function: %s
    permissions:
`, capability, module, function)
	for _, permission := range exportPerms {
		manifest += "      - " + permission + "\n"
	}
	return pcdpkgBytes(t, manifest, map[string][]byte{
		"src/" + module:                        content,
		"schemas/capability." + capability + ".input.json": []byte(`{"type":"object"}`),
		"schemas/capability." + capability + ".output.json": []byte(`{"type":"object"}`),
	})
}

// realworldPcdpkgBytes 把 realworld 插件目录打包为受约束 .pcdpkg（保持 src/ 结构）。
func realworldPcdpkgBytes(t *testing.T, id string) []byte {
	t.Helper()
	root := realworldDir(id)
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	walkErr := filepath.WalkDir(root, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() {
			return nil
		}
		rel, relErr := filepath.Rel(root, path)
		if relErr != nil {
			return relErr
		}
		rel = filepath.ToSlash(rel)
		if strings.HasPrefix(rel, ".") || strings.HasPrefix(rel, "..") {
			return nil
		}
		// 样例输入文件（input.*）与旧 plugin.yaml 是开发期/迁移产物，不进入发布包。
		if strings.HasPrefix(rel, "input.") || rel == "plugin.yaml" {
			return nil
		}
		content, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		header := &zip.FileHeader{Name: rel, Method: zip.Deflate}
		header.SetMode(0o400)
		fileWriter, createErr := writer.CreateHeader(header)
		if createErr != nil {
			return createErr
		}
		_, writeErr := fileWriter.Write(content)
		return writeErr
	})
	if walkErr != nil {
		t.Fatal(walkErr)
	}
	if err := writer.Close(); err != nil {
		t.Fatal(err)
	}
	return buffer.Bytes()
}
