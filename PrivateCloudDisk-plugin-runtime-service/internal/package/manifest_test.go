package pkg

import (
	"errors"
	"strings"
	"testing"
)

func TestParseManifestValid(t *testing.T) {
	manifest, err := ParseManifestBytes([]byte(validManifest))
	if err != nil {
		t.Fatalf("合法 manifest 解析失败：%v", err)
	}
	if manifest.ManifestVersion != 1 {
		t.Fatalf("manifest_version 应为 1：%d", manifest.ManifestVersion)
	}
	if manifest.Plugin.ID != "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7" {
		t.Fatalf("plugin.id 解析错误：%q", manifest.Plugin.ID)
	}
	if manifest.Plugin.Type != "CLOUD_PLUGIN" || manifest.Plugin.Version != "1.0.0" {
		t.Fatalf("plugin meta 解析错误：%+v", manifest.Plugin)
	}
	if manifest.Runtime.Language != "python" || manifest.Runtime.Version != "3.11" {
		t.Fatalf("runtime 解析错误：%+v", manifest.Runtime)
	}
	if len(manifest.Entrypoints.Events) != 2 {
		t.Fatalf("应解析出 2 个事件入口：%+v", manifest.Entrypoints.Events)
	}
	if manifest.Entrypoints.Events[0].Event != "pcd.file.content.ready.v1" ||
		manifest.Entrypoints.Events[0].Module != "src/main.py" ||
		manifest.Entrypoints.Events[0].Function != "preprocess" ||
		manifest.Entrypoints.Events[0].Priority != 100 {
		t.Fatalf("事件入口字段解析错误：%+v", manifest.Entrypoints.Events[0])
	}
	if manifest.Entrypoints.Events[0].Conditions["mime_types"] == nil {
		t.Fatalf("事件 conditions 未解析：%+v", manifest.Entrypoints.Events[0].Conditions)
	}
	if len(manifest.Exports) != 1 || manifest.Exports[0].Name != "compress" {
		t.Fatalf("exports 解析错误：%+v", manifest.Exports)
	}
	if manifest.Limits.TimeoutSeconds != 120 || manifest.Limits.MemoryMB != 256 {
		t.Fatalf("limits 解析错误：%+v", manifest.Limits)
	}
	if got, ok := manifest.ExportByName("compress"); !ok || got.Function != "compress" {
		t.Fatalf("ExportByName 错误：%+v ok=%v", got, ok)
	}
	if _, ok := manifest.ExportByName("nope"); ok {
		t.Fatal("ExportByName 不应命中不存在的能力")
	}
	if _, ok := manifest.EventByName("pcd.file.content.ready.v1"); !ok {
		t.Fatal("EventByName 应命中")
	}
}

func TestManifestValidationTable(t *testing.T) {
	// 逐条破坏字段，断言对应错误口径。
	cases := []struct {
		name   string
		mutate func(m string) string
		want   ErrorKind
	}{
		{"非法 YAML", func(m string) string { return "key: [unclosed" }, ErrManifestInvalid},
		{"版本非 1", func(m string) string { return strings.Replace(m, "manifest_version: 1", "manifest_version: 2", 1) }, ErrManifestVersion},
		{"缺 id", func(m string) string { return strings.Replace(m, "  id: 8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7", "  id: \"\"", 1) }, ErrPluginID},
		{"非法 UUID", func(m string) string { return strings.Replace(m, "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7", "not-a-uuid", 1) }, ErrPluginID},
		{"类型不支持", func(m string) string { return strings.Replace(m, "type: CLOUD_PLUGIN", "type: JAVA_PLUGIN", 1) }, ErrPluginType},
		{"版本非法", func(m string) string { return strings.Replace(m, "version: 1.0.0", "version: v1", 1) }, ErrPluginVersion},
		{"语言非 python", func(m string) string { return strings.Replace(m, "language: python", "language: javascript", 1) }, ErrRuntime},
		{"运行时版本未允许", func(m string) string { return strings.Replace(m, `version: "3.11"`, `version: "3.9"`, 1) }, ErrRuntime},
		{"module 不在 src", func(m string) string { return strings.Replace(m, "module: src/main.py", "module: main.py", 1) }, ErrEntrypoint},
		{"事件缺 function", func(m string) string { return strings.Replace(m, "    function: preprocess", "    function: \"\"", 1) }, ErrEntrypoint},
		{"事件权限越权", func(m string) string { return strings.Replace(m, "      - file.content.read_staging\n      - file.content.write_pre_activation", "      - file.super.admin", 1) }, ErrPermission},
		{"export module 不在 src", func(m string) string { return strings.Replace(m, "    module: src/main.py\n    function: compress", "    module: lib.py\n    function: compress", 1) }, ErrExport},
		{"export 越权权限", func(m string) string { return strings.Replace(m, `    input_schema: schemas/capability.compress.input.json`, "    permissions:\n      - file.super.admin\n    input_schema: schemas/capability.compress.input.json", 1) }, ErrPermission},
		{"limits 负数", func(m string) string { return strings.Replace(m, "  memory_mb: 256", "  memory_mb: -1", 1) }, ErrLimit},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := ParseManifestBytes([]byte(tc.mutate(validManifest)))
			if err == nil {
				t.Fatalf("%s：应解析失败", tc.name)
			}
			var parseErr *ParseError
			if !errors.As(err, &parseErr) {
				t.Fatalf("%s：应为 ParseError，实际 %T", tc.name, err)
			}
			if parseErr.Kind != tc.want {
				t.Fatalf("%s：kind=%s want=%s（err=%v）", tc.name, parseErr.Kind, tc.want, err)
			}
		})
	}
}

func TestManifestMissingRequired(t *testing.T) {
	for _, name := range []string{"", "GARBAGE", "manifest_version: 1"} {
		_, err := ParseManifestBytes([]byte(name))
		if err == nil {
			t.Fatalf("残缺 manifest %q 应失败", name)
		}
	}
}
