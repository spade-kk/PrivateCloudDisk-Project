package pkg

// 受约束 ZIP 解压与安全校验（设计文档 7.1 禁止清单 + 36.6 结构）。
//
// 运行层（Go）在解压时强制：路径穿越/符号链接/硬链接/设备文件、文件数<=1000、
// 解压后<=20MiB、单脚本<=1MiB/<=5000 行、敏感文件（.env/私钥/二进制可执行文件/动态库）、
// 顶层目录白名单（src/schemas/assets/README/LICENSE）。AST 节点上限（20,000）
// 属发布门禁（validator/validate_python.py）执行，运行层用源码字节数与行数兜底。

import (
	"archive/zip"
	"bytes"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
)

// Options 是解析配置；零值使用设计文档 MVP 上限（7.1）。
type Options struct {
	// MaxExpandedBytes 解压后总大小上限，默认 20 MiB（可配置）。
	MaxExpandedBytes int64
	// MaxFiles 文件数上限，默认 1000。
	MaxFiles int
	// MaxScriptBytes 单脚本字节上限，默认 1 MiB。
	MaxScriptBytes int64
	// MaxScriptLines 单脚本行数上限，默认 5000。
	MaxScriptLines int
}

func (o *Options) applyDefaults() {
	if o.MaxExpandedBytes <= 0 {
		o.MaxExpandedBytes = 20 * 1024 * 1024
	}
	if o.MaxFiles <= 0 {
		o.MaxFiles = 1000
	}
	if o.MaxScriptBytes <= 0 {
		o.MaxScriptBytes = 1024 * 1024
	}
	if o.MaxScriptLines <= 0 {
		o.MaxScriptLines = 5000
	}
}

// Parsed 是解析后的插件包（3.24：返回结构化对象，不散落路径）。
type Parsed struct {
	Manifest *Manifest
	// Root 是解压根目录；SrcRoot/SchemaRoot/AssetRoot 供挂载使用。
	Root       string
	SrcRoot    string
	SchemaDirs []string
	AssetRoot  string
	// Modules 记录 src/ 下的 .py 模块（相对包根），供 AST/存在性校验与挂载决策。
	Modules []string
	// FileCount 与 TotalBytes 供审计与测试断言。
	FileCount  int
	TotalBytes int64
}

// sensitiveFileRaises 判断相对路径是否为敏感文件（3.22/7.1）。
func isSensitivePath(rel string) bool {
	base := strings.ToLower(filepath.Base(rel))
	lower := strings.ToLower(rel)
	for _, forbidden := range []string{".env", ".pem", ".key", ".so", ".dll", ".dylib", ".exe", ".bin"} {
		if strings.HasSuffix(lower, forbidden) {
			return true
		}
	}
	if strings.HasPrefix(base, "id_rsa") || strings.HasPrefix(base, "id_ed25519") {
		return true
	}
	return false
}

// isBinaryMagic 通过魔数识别可执行/动态库（ELF/Mach-O/PE 与脚本 shebang 之外）。
func hasExecutableMagic(header []byte) bool {
	if len(header) < 4 {
		return false
	}
	// ELF
	if bytes.Equal(header[:4], []byte{0x7f, 'E', 'L', 'F'}) {
		return true
	}
	// PE (MZ)
	if header[0] == 'M' && header[1] == 'Z' {
		return true
	}
	// Mach-O (32/64 little/big endian)
	if bytes.Equal(header[:4], []byte{0xfe, 0xed, 0xfa, 0xce}) ||
		bytes.Equal(header[:4], []byte{0xce, 0xfa, 0xed, 0xfe}) ||
		bytes.Equal(header[:4], []byte{0xfe, 0xed, 0xfa, 0xcf}) ||
		bytes.Equal(header[:4], []byte{0xcf, 0xfa, 0xed, 0xfe}) {
		return true
	}
	return false
}

// allowedTopLevel 约束包的顶层目录/文件（7.1/3.4）。
func allowedTopLevel(name string) bool {
	switch name {
	case "src", "schemas", "assets", "README.md", "LICENSE", "LICENSE.txt", "manifest.yaml":
		return true
	}
	return false
}

// Parse 下载后调用：解压 archivePath 到 destination 并解析/校验 manifest。
//  1. 解压前读 manifest.yaml 预解析（尽早拒绝）；2) 依次解压全部条目并施加限制。
func Parse(archivePath, destination string, options Options) (*Parsed, error) {
	options.applyDefaults()
	reader, err := zip.OpenReader(archivePath)
	if err != nil {
		return nil, kindError(ErrStructure, "插件包不是合法 ZIP")
	}
	defer reader.Close()

	// manifest.yaml 必须在包根。
	if !containsEntry(reader.File, "manifest.yaml") {
		return nil, kindError(ErrManifestMissing, "插件包缺少 manifest.yaml")
	}
	if !containsSrc(reader.File) {
		return nil, kindError(ErrStructure, "插件包缺少 src/ 目录")
	}

	manifestData, err := readEntry(reader.File, "manifest.yaml", options.MaxExpandedBytes)
	if err != nil {
		return nil, err
	}
	manifest, err := ParseManifestBytes(manifestData)
	if err != nil {
		return nil, err
	}

	root, err := filepath.Abs(destination)
	if err != nil {
		return nil, err
	}
	if err := os.MkdirAll(root, 0o700); err != nil {
		return nil, err
	}
	if len(reader.File) > options.MaxFiles {
		return nil, kindError(ErrResourceLimit, "插件包文件数超过上限 %d", options.MaxFiles)
	}

	parsed := &Parsed{Manifest: manifest, Root: root}
	var expanded int64
	for _, file := range reader.File {
		if file.FileInfo().IsDir() {
			continue
		}
		clean := filepath.ToSlash(filepath.Clean(filepath.FromSlash(file.Name)))
		if err := validateEntryPath(clean, root); err != nil {
			return nil, err
		}
		// 顶层目录白名单（src/schemas/assets 及其子层）。
		top := strings.SplitN(clean, "/", 2)[0]
		if !allowedTopLevel(top) {
			return nil, kindError(ErrStructure, "顶层目录不在白名单：%s", top)
		}
		if isSensitivePath(clean) {
			return nil, kindError(ErrSensitiveFile, "插件包包含禁用文件类型：%s", filepath.Base(clean))
		}
		// 链接/设备/特殊文件。
		mode := file.Mode()
		if mode&os.ModeSymlink != 0 || mode&os.ModeDevice != 0 || (mode&os.ModeType != 0 && !file.FileInfo().IsDir()) {
			return nil, kindError(ErrSecurity, "插件包包含符号链接或特殊文件")
		}
		source, err := file.Open()
		if err != nil {
			return nil, err
		}
		header := make([]byte, 4)
		n, _ := io.ReadFull(source, header)
		header = header[:n]
		if hasExecutableMagic(header) {
			source.Close()
			return nil, kindError(ErrSensitiveFile, "插件包包含可执行/动态库二进制：%s", filepath.Base(clean))
		}
		if mode&0100 != 0 {
			source.Close()
			return nil, kindError(ErrSensitiveFile, "插件包文件不应带可执行权限：%s", filepath.Base(clean))
		}
		size := int64(file.UncompressedSize64)
		expanded += size
		if expanded > options.MaxExpandedBytes {
			source.Close()
			return nil, kindError(ErrResourceLimit, "插件包解压体积超过上限")
		}
		// 单脚本限制：src/ 下 .py
		if strings.HasPrefix(clean, "src/") && strings.HasSuffix(clean, ".py") {
			if size > options.MaxScriptBytes {
				source.Close()
				return nil, kindError(ErrResourceLimit, "单个脚本超过 %d MiB 上限", options.MaxScriptBytes/1024/1024)
			}
		}
		target := filepath.Join(root, filepath.FromSlash(clean))
		if !strings.HasPrefix(target, root+string(os.PathSeparator)) {
			source.Close()
			return nil, kindError(ErrPathEscape, "插件包解压路径越界")
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o700); err != nil {
			source.Close()
			return nil, err
		}
		output, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o400)
		if err != nil {
			source.Close()
			return nil, err
		}
		// 前面已消费 header 做魔数检测；写回时必须把该 4 字节一并落盘，
		// 否则每个文件都会被截掉开头（历史回归：成功脚本首 4 字节丢失）。
		_, copyErr := io.Copy(
			output,
			io.LimitReader(io.MultiReader(bytes.NewReader(header), source), options.MaxExpandedBytes+1),
		)
		closeErr := output.Close()
		source.Close()
		if copyErr != nil {
			return nil, copyErr
		}
		if closeErr != nil {
			return nil, closeErr
		}
		parsed.FileCount++
		parsed.TotalBytes += size
		if strings.HasPrefix(clean, "src/") && strings.HasSuffix(clean, ".py") {
			parsed.Modules = append(parsed.Modules, clean)
		}
	}

	// 行数限制：解压完成后对 src/ 下 .py 逐行统计。
	if err := enforceLineLimits(root, options.MaxScriptLines); err != nil {
		return nil, err
	}

	// 目录收紧：文件只读 0400，目录 0700（宿主可清理）。
	if err := filepath.WalkDir(root, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() {
			return nil
		}
		return os.Chmod(path, 0o400)
	}); err != nil {
		return nil, err
	}

	parsed.SrcRoot = filepath.Join(root, "src")
	parsed.AssetRoot = filepath.Join(root, "assets")
	if _, err := os.Stat(parsed.AssetRoot); err != nil {
		parsed.AssetRoot = ""
	}
	if schemaRoot := filepath.Join(root, "schemas"); pathExists(schemaRoot) {
		parsed.SchemaDirs = []string{schemaRoot}
	}
	return parsed, nil
}

func enforceLineLimits(root string, maxLines int) error {
	src := filepath.Join(root, "src")
	return filepath.WalkDir(src, func(path string, entry os.DirEntry, walkErr error) error {
		if walkErr != nil {
			return walkErr
		}
		if entry.IsDir() || filepath.Ext(path) != ".py" {
			return nil
		}
		data, err := os.ReadFile(path)
		if err != nil {
			return err
		}
		if strings.Count(string(data), "\n")+1 > maxLines {
			return kindError(ErrResourceLimit, "单个脚本超过 %d 行上限", maxLines)
		}
		return nil
	})
}

func validateEntryPath(clean, root string) error {
	if strings.HasPrefix(clean, "/") {
		return kindError(ErrPathEscape, "插件包包含绝对路径")
	}
	if clean == ".." || strings.HasPrefix(clean, "../") {
		return kindError(ErrPathEscape, "插件包包含路径穿越")
	}
	return nil
}

func containsEntry(files []*zip.File, name string) bool {
	for _, file := range files {
		clean := filepath.ToSlash(filepath.Clean(filepath.FromSlash(file.Name)))
		if clean == strings.TrimSuffix(name, "/") || clean == strings.TrimSuffix(name, "/")+"/" {
			return true
		}
	}
	return false
}

// containsSrc 判断包内是否含 src/（显式目录项或以 src/ 开头的文件项）。
func containsSrc(files []*zip.File) bool {
	for _, file := range files {
		clean := filepath.ToSlash(filepath.Clean(filepath.FromSlash(file.Name)))
		if clean == "src" || clean == "src/" || strings.HasPrefix(clean, "src/") {
			return true
		}
	}
	return false
}

func readEntry(files []*zip.File, name string, limit int64) ([]byte, error) {
	for _, file := range files {
		clean := filepath.ToSlash(filepath.Clean(filepath.FromSlash(file.Name)))
		if clean != name || file.FileInfo().IsDir() {
			continue
		}
		source, err := file.Open()
		if err != nil {
			return nil, err
		}
		defer source.Close()
		data, err := io.ReadAll(io.LimitReader(source, limit+1))
		if err != nil {
			return nil, err
		}
		if int64(len(data)) > limit {
			return nil, kindError(ErrResourceLimit, "manifest.yaml 超过大小上限")
		}
		return data, nil
	}
	return nil, fmt.Errorf("插件包缺少 %s", name)
}

func pathExists(path string) bool {
	_, err := os.Stat(path)
	return err == nil
}
