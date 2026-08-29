package pkg

import (
	"bytes"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// parseInto 打包并解析到一个新临时目录，返回 Parsed 与错误。
func parseInto(t *testing.T, entries map[string][]byte, modes map[string]os.FileMode, options Options) (*Parsed, error) {
	t.Helper()
	root := t.TempDir()
	archive := filepath.Join(root, "p.pcdpkg")
	writeZip(t, archive, entries, modes)
	return Parse(archive, filepath.Join(root, "out"), options)
}

func TestParseValidPackage(t *testing.T) {
	parsed, err := parseInto(t, validPackageEntries(), nil, Options{})
	if err != nil {
		t.Fatalf("合法 .pcdpkg 解析失败：%v", err)
	}
	if parsed.Manifest == nil || parsed.Manifest.Plugin.ID == "" {
		t.Fatal("Parsed.Manifest 应被填充")
	}
	if parsed.SrcRoot == "" || parsed.Modules == nil {
		t.Fatalf("src 元数据缺失：%+v", parsed)
	}
	found := false
	for _, module := range parsed.Modules {
		if module == "src/main.py" {
			found = true
		}
	}
	if !found {
		t.Fatalf("Modules 应包含 src/main.py：%v", parsed.Modules)
	}
	if len(parsed.SchemaDirs) == 0 {
		t.Fatal("SchemaDirs 应包含 schemas/")
	}
	// 文件必须只读 0400。
	stat, err := os.Stat(filepath.Join(parsed.Root, "src", "main.py"))
	if err != nil {
		t.Fatal(err)
	}
	if stat.Mode().Perm()&0o222 != 0 {
		t.Fatalf("解压文件应只读，实际 0o%o", stat.Mode().Perm())
	}
}

func TestParseRejectsMissingManifest(t *testing.T) {
	entries := validPackageEntries()
	delete(entries, "manifest.yaml")
	_, err := parseInto(t, entries, nil, Options{})
	assertKind(t, err, ErrManifestMissing)
}

func TestParseRejectsMissingSrc(t *testing.T) {
	entries := validPackageEntries()
	delete(entries, "src/main.py")
	_, err := parseInto(t, entries, nil, Options{})
	assertKind(t, err, ErrStructure)
}

func TestParseRejectsBadManifest(t *testing.T) {
	entries := validPackageEntries()
	entries["manifest.yaml"] = []byte("manifest_version: 2")
	_, err := parseInto(t, entries, nil, Options{})
	assertKind(t, err, ErrManifestVersion)
}

func TestParseSecurityTable(t *testing.T) {
	tooMany := map[string][]byte{"manifest.yaml": []byte(validManifest)}
	for index := 0; index < 1000; index++ {
		tooMany["src/f"+fmt.Sprintf("%04d", index)] = []byte("x")
	}
	cases := []struct {
		name    string
		entries map[string][]byte
		modes   map[string]os.FileMode
		options Options
		want    ErrorKind
	}{
		{
			"路径穿越", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "../evil.py": []byte("x")}, nil, Options{}, ErrPathEscape,
		},
		{
			"绝对路径", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "/etc/passwd": []byte("x")}, nil, Options{}, ErrPathEscape,
		},
		{
			"src内穿越", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/../../evil.py": []byte("x")}, nil, Options{}, ErrPathEscape,
		},
		{
			"符号链接", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/link": []byte("x")}, map[string]os.FileMode{"src/link": os.ModeSymlink | 0o777}, Options{}, ErrSecurity,
		},
		{
			"设备文件", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/dev": []byte("x")}, map[string]os.FileMode{"src/dev": os.ModeDevice | 0o666}, Options{}, ErrSecurity,
		},
		{
			"文件数超限", tooMany, nil, Options{}, ErrResourceLimit,
		},
		{
			"解压体积超限", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": bytes.Repeat([]byte("x"), 2048)}, nil, Options{MaxExpandedBytes: 1024}, ErrResourceLimit,
		},
		{
			"单脚本超大小", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": bytes.Repeat([]byte("x"), 3000)}, nil, Options{MaxScriptBytes: 1024}, ErrResourceLimit,
		},
		{
			"单脚本超行数", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte(strings.Repeat("x\n", 200))}, nil, Options{MaxScriptLines: 10}, ErrResourceLimit,
		},
		{
			".env 敏感文件", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/.env": []byte("SECRET=1")}, nil, Options{}, ErrSensitiveFile,
		},
		{
			"私钥文件", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/key.pem": []byte("PRIVATE")}, nil, Options{}, ErrSensitiveFile,
		},
		{
			"id_rsa 私钥", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/id_rsa": []byte("PRIVATE")}, nil, Options{}, ErrSensitiveFile,
		},
		{
			"动态库 .so", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "assets/lib.so": []byte{0x7f, 'E', 'L', 'F', 2, 1, 1}}, nil, Options{}, ErrSensitiveFile,
		},
		{
			"可执行权限位", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "src/run.sh": []byte("echo hi")}, map[string]os.FileMode{"src/run.sh": 0o755}, Options{}, ErrSensitiveFile,
		},
		{
			"非白名单顶层目录", map[string][]byte{"manifest.yaml": []byte(validManifest), "src/main.py": []byte("x"), "lib/evil.py": []byte("x")}, nil, Options{}, ErrStructure,
		},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			_, err := parseInto(t, tc.entries, tc.modes, tc.options)
			assertKind(t, err, tc.want)
		})
	}
}

func TestParseRejectsDuplicateFilenames(t *testing.T) {
	// O_EXCL：同名条目在解压第二个副本时报错。
	root := t.TempDir()
	archive := filepath.Join(root, "p.pcdpkg")
	zipBytes := duplicateZip(t)
	if err := os.WriteFile(archive, zipBytes, 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Parse(archive, filepath.Join(root, "out"), Options{}); err == nil {
		t.Fatal("重复文件名应触发 O_EXCL 拒绝")
	}
}

func TestParseErrorDoesNotLeakPath(t *testing.T) {
	entries := validPackageEntries()
	entries["manifest.yaml"] = []byte("manifest_version: 2")
	_, err := parseInto(t, entries, nil, Options{})
	var parseErr *ParseError
	if !errors.As(err, &parseErr) {
		t.Fatal("应为 ParseError")
	}
	if strings.Contains(parseErr.Error(), "pcd-runtime") || strings.Contains(parseErr.Error(), "TempDir") {
		t.Fatalf("错误信息不得暴露内部路径：%v", err)
	}
}

// TestParsePreservesFileBytes 回归：魔数检测消费 header 后必须一并写回，
// 否则每个文件都会丢失开头 4 字节（曾导致所有 Docker 沙箱执行失败）。
func TestParsePreservesFileBytes(t *testing.T) {
	source := []byte("\"\"\"成功入口 docstring\"\"\"\nvery = 'ascii-content-α'\n")
	parsed, err := parseInto(t, map[string][]byte{
		"manifest.yaml": []byte(validManifest),
		"src/main.py":   source,
	}, nil, Options{})
	if err != nil {
		t.Fatalf("解析失败：%v", err)
	}
	data, readErr := os.ReadFile(filepath.Join(parsed.Root, "src", "main.py"))
	if readErr != nil {
		t.Fatal(readErr)
	}
	if !bytes.Equal(data, source) {
		t.Fatalf("文件字节被篡改：\ngot  %q\nwant %q", data, source)
	}
}

// TestParsePreservesBinaryFileBytes 二进制（非敏感 UF 判断之外）也应逐字节保留。
func TestParsePreservesBinaryFileBytes(t *testing.T) {
	content := []byte{0x01, 0x02, 0x03, 0x04, 0xff, 0xfe, 0x00, 0x11}
	parsed, err := parseInto(t, map[string][]byte{
		"manifest.yaml":        []byte(validManifest),
		"src/main.py":          []byte("def main(c):\n    return 1\n"),
		"assets/blob.bin.test": content,
	}, nil, Options{})
	if err != nil {
		t.Fatalf("解析失败：%v（assets/blob.bin.test 不在敏感后缀列表）", err)
	}
	data, readErr := os.ReadFile(filepath.Join(parsed.Root, "assets", "blob.bin.test"))
	if readErr != nil {
		t.Fatal(readErr)
	}
	if !bytes.Equal(data, content) {
		t.Fatalf("二进制文件字节被篡改：got %x want %x", data, content)
	}
}
