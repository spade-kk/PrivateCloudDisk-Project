package pkgclient

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

type Client struct {
	BaseURL      string
	ServiceToken string
	MaxBytes     int64
	HTTP         *http.Client
}

func New(baseURL, token string, maxBytes int64) *Client {
	return &Client{
		BaseURL: strings.TrimRight(baseURL, "/"), ServiceToken: token, MaxBytes: maxBytes,
		HTTP: &http.Client{Timeout: 30 * time.Second},
	}
}

// Download 从 Plugin Service 获取已发布不可变包，并再次验证响应哈希。
func (c *Client) Download(ctx context.Context, versionID, destination string) error {
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
		return errors.New("插件包响应缺少有效哈希")
	}
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o600)
	if err != nil {
		return err
	}
	defer output.Close()
	digest := sha256.New()
	reader := io.LimitReader(response.Body, c.MaxBytes+1)
	written, err := io.Copy(io.MultiWriter(output, digest), reader)
	if err != nil {
		return err
	}
	if written > c.MaxBytes {
		return errors.New("插件包超过 Runtime 下载上限")
	}
	actual := hex.EncodeToString(digest.Sum(nil))
	if actual != expected {
		return errors.New("插件包传输哈希不一致")
	}
	return output.Sync()
}
