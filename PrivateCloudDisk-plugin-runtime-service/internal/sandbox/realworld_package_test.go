package sandbox

// realworld 插件包与基线管理测试（需求三 3.9-3.12/3.20/3.30、四 4.22）。
// 不依赖 Docker：验证 .pcdpkg（manifest.yaml+src/）能被 internal/package.Parse
// 安全解析，manifest 声明入口存在，基线模式与 golden 文件完整。

import (
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	pkg "privateclouddisk/plugin-runtime-service/internal/package"
)

func realworldPluginIDs(t *testing.T) []string {
	t.Helper()
	handle, err := os.ReadDir(realworldDir(""))
	if err != nil {
		t.Fatal(err)
	}
	var ids []string
	for _, entry := range handle {
		if entry.IsDir() {
			ids = append(ids, entry.Name())
		}
	}
	return ids
}

// TestRealWorldPackagesExtractRoundTrip 3.11/3.12/3.30：每个 realworld 目录可被打成
// .pcdpkg 且被 Parse 安全解析（无符号链接/特殊文件/路径穿越，文件只读）。
func TestRealWorldPackagesExtractRoundTrip(t *testing.T) {
	for _, id := range realworldPluginIDs(t) {
		t.Run(id, func(t *testing.T) {
			archive := filepath.Join(t.TempDir(), id+".pcdpkg")
			if err := os.WriteFile(archive, realworldPcdpkgBytes(t, id), 0o600); err != nil {
				t.Fatal(err)
			}
			root := filepath.Join(t.TempDir(), "plugin")
			parsed, err := pkg.Parse(archive, root, pkg.Options{MaxExpandedBytes: 20 * 1024 * 1024})
			if err != nil {
				t.Fatalf("Parse 拒绝合法插件包：%v", err)
			}
			if parsed.Manifest == nil || parsed.Manifest.Plugin.ID == "" {
				t.Fatalf("manifest 未解析：%+v", parsed)
			}
			// 每个声明入口模块必须存在于解压树（src/ 下）。
			declared := declaredModules(t, id)
			for _, module := range declared {
				if _, err := os.Stat(filepath.Join(root, module)); err != nil {
					t.Fatalf("manifest 声明入口 %s 不在包内：%v", module, err)
				}
			}
			if len(declared) == 0 {
				t.Fatalf("%s manifest 未声明任何入口", id)
			}
			// Security：入口文件只读。
			info, err := os.Stat(filepath.Join(root, declared[0]))
			if err != nil {
				t.Fatal(err)
			}
			if info.Mode()&0o222 != 0 {
				t.Fatalf("插件入口文件应为只读：%v", info.Mode())
			}
		})
	}
}

// declaredModules 从 manifest.yaml 提取 entrypoints.events[].module 与 exports[].module。
func declaredModules(t *testing.T, id string) []string {
	t.Helper()
	manifestBytes, err := os.ReadFile(filepath.Join(realworldDir(id), "manifest.yaml"))
	if err != nil {
		t.Fatalf("读取 %s/manifest.yaml：%v", id, err)
	}
	manifest, err := pkg.ParseManifestBytes(manifestBytes)
	if err != nil {
		t.Fatalf("%s manifest.yaml 不合法：%v", id, err)
	}
	var modules []string
	for _, event := range manifest.Entrypoints.Events {
		if !contains(modules, event.Module) {
			modules = append(modules, event.Module)
		}
	}
	for _, export := range manifest.Exports {
		if !contains(modules, export.Module) {
			modules = append(modules, export.Module)
		}
	}
	if len(modules) == 0 {
		t.Fatalf("%s/manifest.yaml 未声明任何 .py 入口", id)
	}
	return modules
}

// TestRealWorldGoldensAndModes 3.6/3.20/4.22：基线文件与模式文件成对存在且内容完整。
func TestRealWorldGoldensAndModes(t *testing.T) {
	handle, err := os.ReadDir(filepath.Join("..", "..", "testdata", "expected"))
	if err != nil {
		t.Fatal(err)
	}
	modes := map[string]bool{"output-bin": true, "return-json": true, "chain": true}
	for _, entry := range handle {
		name := entry.Name()
		if !strings.HasSuffix(name, ".golden") {
			continue
		}
		id := strings.TrimSuffix(name, ".golden")
		t.Run(id, func(t *testing.T) {
			golden, err := os.ReadFile(filepath.Join("..", "..", "testdata", "expected", name))
			if err != nil {
				t.Fatal(err)
			}
			if len(golden) == 0 {
				t.Fatalf("基线 %s 为空", name)
			}
			modeBytes, err := os.ReadFile(filepath.Join("..", "..", "testdata", "expected", id+".golden.mode"))
			if err != nil {
				t.Fatalf("基线模式文件缺失：%v", err)
			}
			if !modes[strings.TrimSpace(string(modeBytes))] {
				t.Fatalf("未知基线模式：%q", string(modeBytes))
			}
		})
	}
}

// TestRealWorldSampleInputs 2.24/3.1-3.5：随包样例输入均已在 testdata/input 落盘。
func TestRealWorldSampleInputs(t *testing.T) {
	for _, id := range realworldPluginIDs(t) {
		switch id {
		case "malicious_import", "capability_report", "capability_user_info", "path_escape",
			"timeout_sim", "resource_hog", "invalid_output":
			continue // 无文件输入/仅能力输入
		}
		exts := []string{".txt", ".json", ".csv"}
		found := false
		for _, ext := range exts {
			if _, err := os.Stat(filepath.Join("..", "..", "testdata", "input", id+ext)); err == nil {
				found = true
				break
			}
		}
		if !found {
			t.Fatalf("%s 缺少 testdata/input 样例输入", id)
		}
	}
}

// TestRealWorldManifestsHaveUUIDAndSemver 7.x/6.4：manifest 字段合规。
func TestRealWorldManifestsHaveUUIDAndSemver(t *testing.T) {
	uuidRe := regexp.MustCompile(`^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$`)
	for _, id := range realworldPluginIDs(t) {
		t.Run(id, func(t *testing.T) {
			manifestBytes, err := os.ReadFile(filepath.Join(realworldDir(id), "manifest.yaml"))
			if err != nil {
				t.Fatal(err)
			}
			manifest, err := pkg.ParseManifestBytes(manifestBytes)
			if err != nil {
				t.Fatalf("manifest 解析失败：%v", err)
			}
			if !uuidRe.MatchString(manifest.Plugin.ID) {
				t.Fatalf("plugin.id 非 UUID：%q", manifest.Plugin.ID)
			}
			if !regexp.MustCompile(`^[0-9]+\.[0-9]+\.[0-9]+$`).MatchString(manifest.Plugin.Version) {
				t.Fatalf("plugin.version 非语义化版本：%q", manifest.Plugin.Version)
			}
			if manifest.Runtime.Language != "python" || manifest.Runtime.Version != "3.11" {
				t.Fatalf("runtime 声明不匹配：%+v", manifest.Runtime)
			}
		})
	}
}
