// Package sanitize 提供输出脱敏过滤器（需求六 6.1-6.25）。
//
// 所有错误信息、堆栈、日志在返回客户端前必须经过 Sanitize，移除绝对路径、容器 ID、
// 内部主机、内网 IP、变量内存地址等敏感信息；生产环境强制开启，--debug 仅开发可用。
package sanitize

import (
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
)

var (
	mu        sync.RWMutex
	debugMode = false
)

// SetDebug 仅允许在非生产环境开启完整错误输出（6.10）；生产强制关闭。
func SetDebug(debug bool) {
	mu.Lock()
	defer mu.Unlock()
	debugMode = debug
}

// Debug 返回是否处于完整错误输出模式。
func Debug() bool {
	mu.RLock()
	defer mu.RUnlock()
	return debugMode
}

var (
	// 绝对 POSIX 路径：至少两级目录，如 /etc/passwd、/var/lib/pcd-runtime/work。
	posixPath = regexp.MustCompile(`/(?:[A-Za-z0-9._~-]+/)+[A-Za-z0-9._~-]+`)
	// Windows 盘符路径。
	winPath = regexp.MustCompile(`[A-Za-z]:\\(?:[^\\s\\r\\n"']*\\?)+`)
	// 32/64 位十六进制标识（容器 ID、摘要、校验和）。
	longHex = regexp.MustCompile(`\b(?:sha256:)?[0-9a-fA-F]{32}(?:[0-9a-fA-F]{32})?\b`)
	// IPv4 地址（内网/环回/保留段也一并覆盖）。
	ipv4 = regexp.MustCompile(`\b(?:\d{1,3}\.){3}\d{1,3}\b`)
	// 内存地址/指针。
	memAddr = regexp.MustCompile(`\b0x[0-9a-fA-F]{4,}\b`)
	// 内部主机名（.internal/.local/.lan 或 host-xxxx）。
	internalHost = regexp.MustCompile(`\b[A-Za-z0-9][A-Za-z0-9.-]{0,127}\.(?:internal|local|lan)\b`)
	hostPrefix   = regexp.MustCompile(`\bhost-[0-9a-f]{8}\b`)
	// 凭据形键值（token/password/secret/key）。
	credential = regexp.MustCompile(`(?i)\b(?:token|password|secret|api[_-]?key)\s*=\s*[^\s,;]+`)
	// traceback 中的 "File \"/path/...\", line N"。
	fileLine = regexp.MustCompile(`File\s+"[^"]*"`)
	// 堆栈帧行首中的路径（含 at /path/...）。
	atFrame = regexp.MustCompile(`(?i)\bat\s+[A-Za-z0-9_./\\:-]*`)
)

// extra 允许部署侧以正则追加规则（6.14），由 Config 注入。
var extraMu sync.RWMutex
var extra []*regexp.Regexp

// Extend 追加自定义脱敏规则（正则）；非法表达式会被忽略。
func Extend(patterns []string) {
	extraMu.Lock()
	defer extraMu.Unlock()
	for _, pattern := range patterns {
		if compiled, err := regexp.Compile(pattern); err == nil {
			extra = append(extra, compiled)
		}
	}
}

// Sanitize 对文本执行脱敏：路径、主机、IP、地址、凭据、容器标识一律打码。
func Sanitize(value string) string {
	if value == "" {
		return ""
	}
	result := value
	result = strings.ReplaceAll(result, os.TempDir(), "[tmp]")
	if home, err := os.UserHomeDir(); err == nil && home != "" {
		result = strings.ReplaceAll(result, home, "[home]")
	}
	result = posixPath.ReplaceAllString(result, "[path]")
	result = winPath.ReplaceAllString(result, "[path]")
	result = internalHost.ReplaceAllString(result, "[host]")
	result = hostPrefix.ReplaceAllString(result, "[host]")
	result = longHex.ReplaceAllString(result, "[id]")
	result = ipv4.ReplaceAllString(result, "[ip]")
	result = memAddr.ReplaceAllString(result, "[addr]")
	result = credential.ReplaceAllString(result, "$1=[redacted]")
	extraMu.RLock()
	defer extraMu.RUnlock()
	for _, pattern := range extra {
		result = pattern.ReplaceAllString(result, "[redacted]")
	}
	return strings.TrimSpace(result)
}

// Error 对 error 文本脱敏，截断到 max。
func Error(err error, max int) string {
	if err == nil {
		return ""
	}
	return Summary(Sanitize(err.Error()), max)
}

// Summary 把多行压平成单行并截断（保持可读、防日志放大）。
func Summary(value string, max int) string {
	value = strings.ReplaceAll(value, "\n", " ")
	value = strings.ReplaceAll(value, "\r", " ")
	if max > 0 && len(value) > max {
		return value[:max]
	}
	return value
}

// Traceback 对堆栈文本进行专门脱敏：保留异常类型与首行描述，抹除文件路径与帧路径。
func Traceback(value string, max int) string {
	if value == "" {
		return ""
	}
	lines := strings.Split(value, "\n")
	kept := make([]string, 0, 8)
	for _, line := range lines {
		clean := strings.TrimSpace(line)
		if clean == "" {
			continue
		}
		if strings.HasPrefix(clean, "Traceback ") || strings.HasPrefix(clean, "  File \"") ||
			strings.HasPrefix(clean, "File \"") {
			continue
		}
		if strings.HasPrefix(clean, "    ") || strings.HasPrefix(clean, "  ") {
			continue
		}
		clean = fileLine.ReplaceAllString(clean, `File "[file]"`)
		clean = atFrame.ReplaceAllString(clean, "at [frame]")
		clean = Sanitize(clean)
		kept = append(kept, clean)
		if len(kept) >= 4 {
			break
		}
	}
	result := strings.Join(kept, " | ")
	if max > 0 && len(result) > max {
		return result[:max]
	}
	return result
}

// RawJSON 对已序列化的 JSON 缓冲执行文本级脱敏，保留 JSON 结构与可读性。
func RawJSON(value []byte) []byte {
	if len(value) == 0 {
		return value
	}
	return []byte(Sanitize(string(value)))
}

// Path 返回只含文件名的安全展示，移除目录前缀（6.3）。
func Path(value string) string {
	if value == "" {
		return ""
	}
	return filepath.Base(value)
}
