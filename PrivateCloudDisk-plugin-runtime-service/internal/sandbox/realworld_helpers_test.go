package sandbox

// realworld 夹具共享 helper（单元与集成测试共用，无 build tag）。

import (
	"os"
	"path/filepath"
	"testing"
)

// realworldDir 返回 testdata/plugins/realworld 下插件目录。
func realworldDir(id string) string {
	return filepath.Join("..", "..", "testdata", "plugins", "realworld", id)
}

// realworldZipBytes 把 realworld 插件目录打成内存 .pcdpkg（保留 manifest.yaml + src/ 结构）。
func realworldZipBytes(t *testing.T, id string) []byte {
	t.Helper()
	return realworldPcdpkgBytes(t, id)
}

// realworldModuleBytes 只打包 manifest.yaml + 指定执行模块（多入口包可精确控制包内容）。
// modules 缺省时打包全部 src/ 下 .py。
func realworldModuleBytes(t *testing.T, id string, modules ...string) []byte {
	t.Helper()
	dir := realworldDir(id)
	manifest, err := os.ReadFile(filepath.Join(dir, "manifest.yaml"))
	if err != nil {
		t.Fatalf("读取 %s/manifest.yaml：%v", id, err)
	}
	extra := map[string][]byte{}
	if len(modules) == 0 {
		entries, err := os.ReadDir(filepath.Join(dir, "src"))
		if err != nil {
			t.Fatalf("读取 %s/src：%v", id, err)
		}
		for _, entry := range entries {
			if entry.IsDir() || filepath.Ext(entry.Name()) != ".py" {
				continue
			}
			modules = append(modules, entry.Name())
		}
	}
	for _, module := range modules {
		content, err := os.ReadFile(filepath.Join(dir, "src", filepath.Base(module)))
		if err != nil {
			t.Fatalf("读取 %s/src/%s：%v", id, module, err)
		}
		extra["src/"+filepath.Base(module)] = content
	}
	return pcdpkgBytes(t, string(manifest), extra)
}
