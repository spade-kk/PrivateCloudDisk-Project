package utils

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// ComputeSHA256 计算文件 SHA256 哈希
func ComputeSHA256(filePath string) (string, error) {
	file, err := os.Open(filePath)
	if err != nil {
		return "", err
	}
	defer file.Close()

	hasher := sha256.New()
	if _, err := io.Copy(hasher, file); err != nil {
		return "", err
	}

	return hex.EncodeToString(hasher.Sum(nil)), nil
}

// ComputeSHA256Bytes 计算字节数组 SHA256 哈希
func ComputeSHA256Bytes(data []byte) string {
	hash := sha256.Sum256(data)
	return hex.EncodeToString(hash[:])
}

// FormatSize 格式化文件大小
func FormatSize(size int64) string {
	if size <= 0 {
		return "0 B"
	}
	units := []string{"B", "KB", "MB", "GB", "TB", "PB"}
	unitIndex := 0
	fsize := float64(size)
	for fsize >= 1024 && unitIndex < len(units)-1 {
		fsize /= 1024
		unitIndex++
	}
	if unitIndex == 0 {
		return fmt.Sprintf("%d B", size)
	}
	return fmt.Sprintf("%.2f %s", fsize, units[unitIndex])
}

// FormatDuration 格式化时间间隔
func FormatDuration(d time.Duration) string {
	if d < time.Second {
		return fmt.Sprintf("%dms", d.Milliseconds())
	}
	if d < time.Minute {
		return fmt.Sprintf("%.1fs", d.Seconds())
	}
	if d < time.Hour {
		m := int(d.Minutes())
		s := int(d.Seconds()) % 60
		return fmt.Sprintf("%dm%ds", m, s)
	}
	h := int(d.Hours())
	m := int(d.Minutes()) % 60
	return fmt.Sprintf("%dh%dm", h, m)
}

// GetFileType 根据文件扩展名获取文件类型
func GetFileType(fileName string) string {
	ext := strings.ToLower(filepath.Ext(fileName))
	switch ext {
	case ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg", ".ico":
		return "image/" + strings.TrimPrefix(ext, ".")
	case ".mp4", ".avi", ".mkv", ".mov", ".wmv", ".flv", ".webm":
		return "video/" + strings.TrimPrefix(ext, ".")
	case ".mp3", ".wav", ".flac", ".aac", ".ogg", ".wma":
		return "audio/" + strings.TrimPrefix(ext, ".")
	case ".pdf":
		return "application/pdf"
	case ".doc", ".docx":
		return "application/msword"
	case ".xls", ".xlsx":
		return "application/vnd.ms-excel"
	case ".ppt", ".pptx":
		return "application/vnd.ms-powerpoint"
	case ".zip", ".rar", ".7z", ".tar", ".gz":
		return "application/zip"
	case ".txt", ".md", ".log", ".csv":
		return "text/plain"
	case ".json":
		return "application/json"
	case ".xml", ".html", ".htm":
		return "text/" + strings.TrimPrefix(ext, ".")
	case ".go", ".java", ".py", ".js", ".ts", ".c", ".cpp", ".rs":
		return "text/x-source"
	default:
		return "application/octet-stream"
	}
}

// IsTextFile 判断是否为文本文件
func IsTextFile(fileType string) bool {
	return strings.HasPrefix(fileType, "text/") ||
		fileType == "application/json" ||
		fileType == "application/xml"
}

// GetFileIcon 根据文件名获取文件图标
func GetFileIcon(name string) string {
	ext := strings.ToLower(filepath.Ext(name))
	switch ext {
	case ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".svg":
		return "\U0001F5BC" // 🖼
	case ".mp4", ".avi", ".mkv", ".mov", ".webm":
		return "\U0001F3AC" // 🎬
	case ".mp3", ".wav", ".flac", ".aac":
		return "\U0001F3B5" // 🎵
	case ".pdf":
		return "\U0001F4D5" // 📕
	case ".doc", ".docx":
		return "\U0001F4DD" // 📝
	case ".xls", ".xlsx":
		return "\U0001F4CA" // 📊
	case ".zip", ".rar", ".7z", ".tar", ".gz":
		return "\U0001F4E6" // 📦
	case ".go", ".java", ".py", ".js", ".ts", ".c", ".cpp", ".rs":
		return "\U0001F4BB" // 💻
	default:
		return "\U0001F4C4" // 📄
	}
}

// NewByteReader 将字节数组包装为 io.Reader
func NewByteReader(data []byte) io.Reader {
	return bytes.NewReader(data)
}

// TruncateString 截断字符串
func TruncateString(s string, maxLen int) string {
	if len(s) <= maxLen {
		return s
	}
	return s[:maxLen-3] + "..."
}

// PadRight 右侧填充
func PadRight(s string, length int) string {
	if len(s) >= length {
		return s
	}
	return s + strings.Repeat(" ", length-len(s))
}

// AskYesNo 询问用户是/否
func AskYesNo(prompt string) bool {
	fmt.Printf("%s [y/N]: ", prompt)
	var response string
	fmt.Scanln(&response)
	response = strings.ToLower(strings.TrimSpace(response))
	return response == "y" || response == "yes"
}

// ResolvePath 解析路径，支持 ~ 展开
func ResolvePath(p string) string {
	if strings.HasPrefix(p, "~") {
		home, _ := os.UserHomeDir()
		return filepath.Join(home, p[1:])
	}
	abs, _ := filepath.Abs(p)
	return abs
}