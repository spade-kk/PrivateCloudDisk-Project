package sandbox

// .pcdpkg 受约束 ZIP 运行层校验（原 extractPackage 已重构为 internal/package.Parse）。
// 此处验证：正常包可解析且文件只读；禁止项（路径穿越/符号链接/设备/敏感文件/资源超限）
// 在 Runner 运行层同样被拒绝，错误不泄露内部路径。

import (
	"archive/zip"
	"bytes"
	"fmt"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	pkg "privateclouddisk/plugin-runtime-service/internal/package"
)

const sandboxValidManifest = `manifest_version: 1
plugin:
  id: 8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7
  name: demo
  type: CLOUD_PLUGIN
  version: 1.0.0
runtime:
  language: python
  version: "3.11"
permissions:
  - file.content.read_staging
  - file.content.write_pre_activation
entrypoints:
  events:
  - event: pcd.file.content.ready.v1
    module: src/main.py
    function: main
    permissions:
      - file.content.read_staging
      - file.content.write_pre_activation
`

// pcdpkgZipBytes 构造单入口 .pcdpkg 内存字节。
func pcdpkgZipBytes(t *testing.T, extra map[string][]byte, modes map[string]os.FileMode, mainContent []byte) []byte {
	t.Helper()
	entries := map[string][]byte{
		"manifest.yaml": []byte(sandboxValidManifest),
		"src/main.py":   []byte("def main(context):\n    return {}\n"),
	}
	if mainContent != nil {
		entries["src/main.py"] = mainContent
	}
	for name, content := range extra {
		entries[name] = content
	}
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
	return buffer.Bytes()
}

func parsePcdpkg(t *testing.T, entries map[string][]byte, modes map[string]os.FileMode) (*pkg.Parsed, error) {
	t.Helper()
	root := t.TempDir()
	archive := filepath.Join(root, "plugin.pcdpkg")
	if err := os.WriteFile(archive, pcdpkgZipBytes(t, entries, modes, nil), 0o600); err != nil {
		t.Fatal(err)
	}
	return pkg.Parse(archive, filepath.Join(root, "out"), pkg.Options{})
}

// TestSandboxParseNormalPcdpkg 正常 .pcdpkg：manifest 解析、文件只读、目录 0700（3.1/3.14/3.24）。
func TestSandboxParseNormalPcdpkg(t *testing.T) {
	parsed, err := parsePcdpkg(t, nil, nil)
	if err != nil {
		t.Fatalf("合法 .pcdpkg 解析失败：%v", err)
	}
	if parsed.Manifest.Plugin.ID != "8ae47c8d-41c5-4b9d-87e7-2f93b74d34d7" {
		t.Fatalf("manifest plugin.id 错误：%q", parsed.Manifest.Plugin.ID)
	}
	if len(parsed.Modules) != 1 || parsed.Modules[0] != "src/main.py" {
		t.Fatalf("Modules 元数据错误：%v", parsed.Modules)
	}
	stat, err := os.Stat(filepath.Join(parsed.Root, "src", "main.py"))
	if err != nil {
		t.Fatal(err)
	}
	if stat.Mode().Perm()&0o222 != 0 {
		t.Fatalf("解压文件应只读 0400，实际 0o%o", stat.Mode().Perm())
	}
}

// TestSandboxParseSecurityRejects 运行层禁止项表驱动（3.2-3.7/3.16-3.22/7.2-7.7）。
func TestSandboxParseSecurityRejects(t *testing.T) {
	tooMany := map[string][]byte{}
	for index := 0; index < 1001; index++ {
		tooMany["src/f"+fmt.Sprintf("%05d", index)] = []byte("x")
	}
	cases := []struct {
		name    string
		extra   map[string][]byte
		modes   map[string]os.FileMode
		options pkg.Options
		want    string // ErrorKind
	}{
		{"路径穿越", map[string][]byte{"../evil.py": []byte("x")}, nil, pkg.Options{}, "PATH_ESCAPE_REJECTED"},
		{"绝对路径", map[string][]byte{"/etc/passwd": []byte("x")}, nil, pkg.Options{}, "PATH_ESCAPE_REJECTED"},
		{"src内穿越", map[string][]byte{"src/../../evil.py": []byte("x")}, nil, pkg.Options{}, "PATH_ESCAPE_REJECTED"},
		{"符号链接", map[string][]byte{"src/link.py": []byte("x")}, map[string]os.FileMode{"src/link.py": os.ModeSymlink | 0o777}, pkg.Options{}, "PACKAGE_SECURITY_INVALID"},
		{"设备文件", map[string][]byte{"src/dev.py": []byte("x")}, map[string]os.FileMode{"src/dev.py": os.ModeDevice | 0o666}, pkg.Options{}, "PACKAGE_SECURITY_INVALID"},
		{".env 敏感文件", map[string][]byte{"src/.env": []byte("SECRET=1")}, nil, pkg.Options{}, "SENSITIVE_FILE_REJECTED"},
		{"私钥 .pem", map[string][]byte{"src/id.pem": []byte("PRIVATE")}, nil, pkg.Options{}, "SENSITIVE_FILE_REJECTED"},
		{"动态库魔数", map[string][]byte{"assets/lib.so": []byte{0x7f, 'E', 'L', 'F', 2, 1, 1}}, nil, pkg.Options{}, "SENSITIVE_FILE_REJECTED"},
		{"可执行权限位", map[string][]byte{"src/run.sh": []byte("echo x")}, map[string]os.FileMode{"src/run.sh": 0o755}, pkg.Options{}, "SENSITIVE_FILE_REJECTED"},
		{"解压体积超限", nil, nil, pkg.Options{MaxExpandedBytes: 64}, "PACKAGE_RESOURCE_LIMIT"},
		{"单脚本超大小", nil, nil, pkg.Options{MaxScriptBytes: 8}, "PACKAGE_RESOURCE_LIMIT"},
		{"单脚本超行数", nil, nil, pkg.Options{MaxScriptLines: 2}, "PACKAGE_RESOURCE_LIMIT"},
		{"文件数超限", tooMany, nil, pkg.Options{}, "PACKAGE_RESOURCE_LIMIT"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var mainContent []byte
			if tc.name == "单脚本超行数" {
				mainContent = []byte(strings.Repeat("x\n", 50))
			}
			if tc.name == "单脚本超大小" {
				mainContent = bytes.Repeat([]byte("y"), 4096)
			}
			root := t.TempDir()
			archive := filepath.Join(root, "p.pcdpkg")
			content := pcdpkgZipBytes(t, tc.extra, tc.modes, mainContent)
			if err := os.WriteFile(archive, content, 0o600); err != nil {
				t.Fatal(err)
			}
			_, err := pkg.Parse(archive, filepath.Join(root, "out"), tc.options)
			if err == nil {
				t.Fatalf("%s 应解析失败", tc.name)
			}
			var parseErr *pkg.ParseError
			if !errors.As(err, &parseErr) {
				t.Fatalf("%s 应为 ParseError：%v", tc.name, err)
			}
			if string(parseErr.Kind) != tc.want {
				t.Fatalf("%s kind=%s want=%s（err=%v）", tc.name, parseErr.Kind, tc.want, err)
			}
			if strings.Contains(err.Error(), "pcd-runtime") || strings.Contains(err.Error(), "TempDir") {
				t.Fatalf("%s 错误不得泄露内部路径：%v", tc.name, err)
			}
		})
	}
}

// TestSandboxParseInvalidManifest 缺 manifest / 缺 src / 非法 manifest（7.2/3.2/3.3）。
func TestSandboxParseInvalidManifest(t *testing.T) {
	root := t.TempDir()
	archive := filepath.Join(root, "p.pcdpkg")
	if err := os.WriteFile(archive, pcdpkgZipBytes(t, map[string][]byte{}, nil, nil), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := pkg.Parse(archive, filepath.Join(root, "out"), pkg.Options{})
	if err != nil {
		t.Fatalf("含 manifest 的合法包应解析成功：%v", err)
	}

	// 缺 manifest.yaml。
	noManifest := pcdpkgZipBytes(t, nil, nil, nil)
	if err := os.WriteFile(archive, noManifest, 0o600); err != nil {
		t.Fatal(err)
	}
	// 用 zip 重写：去掉 manifest.yaml。
	entries := map[string][]byte{"src/main.py": []byte("def main(context):\n    return {}\n")}
	zb := bytes.NewBuffer(nil)
	w := zip.NewWriter(zb)
	for name, content := range entries {
		entry, _ := w.Create(name)
		_, _ = entry.Write(content)
	}
	_ = w.Close()
	if err := os.WriteFile(archive, zb.Bytes(), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err = pkg.Parse(archive, filepath.Join(root, "out2"), pkg.Options{})
	var parseErr *pkg.ParseError
	if !errors.As(err, &parseErr) || parseErr.Kind != pkg.ErrManifestMissing {
		t.Fatalf("缺 manifest.yaml 应报 MANIFEST_MISSING：%v", err)
	}
}
