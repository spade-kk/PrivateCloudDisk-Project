// Package pkgclient 负责从 Plugin Service 安全下载 .pcdpkg 插件包。
//
// 只做安全下载（2.11）：扩展名 .pcdpkg 校验、ZIP 魔数、大小上限、SHA256 校验、
// 内部服务令牌、隔离临时目录；不解析包内容（解析在 internal/package）。
package pkgclient

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"time"
)

// ErrInvalidPackage 统一的包下载/校验失败错误码（2.6）。
type ErrInvalidPackage struct {
	Reason string
}

func (e *ErrInvalidPackage) Error() string { return "插件包校验失败：" + e.Reason }

type Client struct {
	BaseURL      string
	ServiceToken string
	MaxBytes     int64
	HTTP         *http.Client
	// downloadTimeout 与 retries 可通过构建期覆盖（2.5）。
	downloadTimeout time.Duration
	retries         int
	retryDelay      time.Duration
}

func New(baseURL, token string, maxBytes int64) *Client {
	return &Client{
		BaseURL:         strings.TrimRight(baseURL, "/"),
		ServiceToken:    token,
		MaxBytes:        maxBytes,
		HTTP:            &http.Client{Timeout: 30 * time.Second},
		downloadTimeout: 25 * time.Second,
		retries:         2,
		retryDelay:      200 * time.Millisecond,
	}
}

// DownloadPcdpkg 下载指定版本 .pcdpkg 到 destination（2.1-2.10, 2.12）。
// destination 必须以 .pcdpkg 结尾；下载后校验 ZIP 魔数与响应 SHA256。
func (c *Client) DownloadPcdpkg(ctx context.Context, versionID, destination string) error {
	if filepath.Ext(destination) != ".pcdpkg" {
		return &ErrInvalidPackage{Reason: "目标必须使用 .pcdpkg 扩展名"}
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0o700); err != nil {
		return err
	}
	var lastErr error
	for attempt := 0; attempt <= c.retries; attempt++ {
		attemptCtx, cancel := context.WithTimeout(ctx, c.downloadTimeout)
		err := c.downloadOnce(attemptCtx, versionID, destination)
		cancel()
		if err == nil {
			return nil
		}
		lastErr = err
		if attempt < c.retries {
			time.Sleep(c.retryDelay)
		}
	}
	return lastErr
}

func (c *Client) downloadOnce(ctx context.Context, versionID, destination string) error {
	request, err := http.NewRequestWithContext(
		ctx, http.MethodGet, c.BaseURL+"/internal/v1/packages/"+versionID, nil,
	)
	if err != nil {
		return err
	}
	request.Header.Set("X-PCD-Service-Token", c.ServiceToken)
	response, err := c.HTTP.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("插件包服务返回状态 %d", response.StatusCode)
	}
	expected := strings.ToLower(response.Header.Get("X-PCD-Package-SHA256"))
	if len(expected) != 64 {
		return &ErrInvalidPackage{Reason: "响应缺少有效 SHA256 头"}
	}
	contentType := response.Header.Get("Content-Type")
	if contentType != "" && contentType != "application/zip" && contentType != "application/octet-stream" {
		return &ErrInvalidPackage{Reason: "响应 Content-Type 不是 ZIP: " + contentType}
	}
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	// 任一步失败都要清理残留文件，避免重试时 O_EXCL 撞上"已存在"（2.6/2.5）。
	failed := true
	defer func() {
		output.Close()
		if failed {
			os.Remove(destination)
		}
	}()
	digest := sha256.New()
	reader := io.LimitReader(response.Body, c.MaxBytes+1)
	written, err := io.Copy(io.MultiWriter(output, digest), reader)
	if err != nil {
		return err
	}
	if written > c.MaxBytes {
		return &ErrInvalidPackage{Reason: "超过包大小上限"}
	}
	actual := hex.EncodeToString(digest.Sum(nil))
	if actual != expected {
		return &ErrInvalidPackage{Reason: "传输哈希不一致"}
	}
	if err := output.Sync(); err != nil {
		return err
	}
	// ZIP 魔数校验（2.10）。
	file, err := os.Open(destination)
	if err != nil {
		return err
	}
	defer file.Close()
	header := make([]byte, 4)
	if _, err := io.ReadFull(file, header); err != nil {
		return &ErrInvalidPackage{Reason: "下载文件过小，不是 ZIP"}
	}
	if header[0] != 'P' || header[1] != 'K' || (header[2] != 3 && header[2] != 5 && header[2] != 7) {
		return &ErrInvalidPackage{Reason: "下载内容不是合法 ZIP 魔数"}
	}
	failed = false
	return nil
}

// Download 旧接口：保留以兼容既有调用方，标记 deprecated（2.19）。
// 语义与 DownloadPcdpkg 一致（同样强制 .pcdpkg 与安全校验）。
//
// Deprecated: 请使用 DownloadPcdpkg。
func (c *Client) Download(ctx context.Context, versionID, destination string) error {
	return c.DownloadPcdpkg(ctx, versionID, destination)
}
