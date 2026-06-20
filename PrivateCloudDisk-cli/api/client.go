package api

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"

	"github.com/privateclouddisk/cli/config"
)

// Client HTTP API 客户端
type Client struct {
	BaseURL    string
	HTTPClient *http.Client
	Token      string
}

// APIResponse 通用 API 响应
type APIResponse struct {
	Code    int             `json:"code"`
	Message string          `json:"message"`
	Data    json.RawMessage `json:"data"`
}

// NewClient 创建 API 客户端
func NewClient(cfg *config.Config) *Client {
	if cfg == nil {
		cfg = config.DefaultConfig()
	}
	return &Client{
		BaseURL: cfg.Endpoint,
		HTTPClient: &http.Client{
			Timeout: 30 * time.Second,
			Transport: &http.Transport{
				MaxIdleConns:        20,
				MaxIdleConnsPerHost: 10,
				IdleConnTimeout:     90 * time.Second,
			},
		},
		Token: cfg.Token,
	}
}

// NewClientWithAuth 创建带认证的 API 客户端
func NewClientWithAuth() (*Client, error) {
	auth, err := config.EnsureLoggedIn()
	if err != nil {
		return nil, err
	}
	cfg, err := config.LoadConfig()
	if err != nil {
		return nil, err
	}
	cfg.Token = auth.Token
	return NewClient(cfg), nil
}

// SetToken 设置 Token
func (c *Client) SetToken(token string) {
	c.Token = token
}

// doRequest 执行 HTTP 请求
func (c *Client) doRequest(method, path string, body interface{}, result interface{}) error {
	url := c.BaseURL + path

	var bodyReader io.Reader
	if body != nil {
		bodyBytes, err := json.Marshal(body)
		if err != nil {
			return fmt.Errorf("序列化请求体失败: %w", err)
		}
		bodyReader = bytes.NewReader(bodyBytes)
	}

	req, err := http.NewRequest(method, url, bodyReader)
	if err != nil {
		return fmt.Errorf("创建请求失败: %w", err)
	}

	req.Header.Set("Content-Type", "application/json")
	req.Header.Set("Accept", "application/json")
	req.Header.Set("User-Agent", "PrivateCloudDisk-CLI/1.0")
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return fmt.Errorf("请求失败: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return fmt.Errorf("读取响应失败: %w", err)
	}

	if resp.StatusCode != http.StatusOK {
		return fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	var apiResp APIResponse
	if err := json.Unmarshal(respBody, &apiResp); err != nil {
		// 非 JSON 响应，将原始字符串作为结果
		if result != nil {
			if strResult, ok := result.(*string); ok {
				*strResult = string(respBody)
				return nil
			}
		}
		return fmt.Errorf("解析响应失败: %w", err)
	}

	if apiResp.Code != 200 {
		return fmt.Errorf("API 错误 [%d]: %s", apiResp.Code, apiResp.Message)
	}

	if result != nil {
		if apiResp.Data != nil {
			if err := json.Unmarshal(apiResp.Data, result); err != nil {
				return fmt.Errorf("解析响应数据失败: %w", err)
			}
		}
	}

	return nil
}

// Get GET 请求
func (c *Client) Get(path string, result interface{}) error {
	return c.doRequest("GET", path, nil, result)
}

// Post POST 请求
func (c *Client) Post(path string, body interface{}, result interface{}) error {
	return c.doRequest("POST", path, body, result)
}

// Patch PATCH 请求
func (c *Client) Patch(path string, body interface{}, result interface{}) error {
	return c.doRequest("PATCH", path, body, result)
}

// Delete DELETE 请求
func (c *Client) Delete(path string, result interface{}) error {
	return c.doRequest("DELETE", path, nil, result)
}

// Put PUT 请求
func (c *Client) Put(path string, body interface{}, result interface{}) error {
	return c.doRequest("PUT", path, body, result)
}

// StreamUpload 流式上传 (用于文件分片)
func (c *Client) StreamUpload(path string, body io.Reader, contentType string, size int64) ([]byte, error) {
	url := c.BaseURL + path

	req, err := http.NewRequest("POST", url, body)
	if err != nil {
		return nil, fmt.Errorf("创建请求失败: %w", err)
	}

	req.Header.Set("Content-Type", contentType)
	req.Header.Set("User-Agent", "PrivateCloudDisk-CLI/1.0")
	req.ContentLength = size
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("上传请求失败: %w", err)
	}
	defer resp.Body.Close()

	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("读取响应失败: %w", err)
	}

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusCreated {
		return nil, fmt.Errorf("HTTP %d: %s", resp.StatusCode, string(respBody))
	}

	return respBody, nil
}

// StreamDownload 流式下载
func (c *Client) StreamDownload(path string) (io.ReadCloser, int64, error) {
	url := c.BaseURL + path

	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		return nil, 0, fmt.Errorf("创建请求失败: %w", err)
	}

	req.Header.Set("User-Agent", "PrivateCloudDisk-CLI/1.0")
	if c.Token != "" {
		req.Header.Set("Authorization", "Bearer "+c.Token)
	}

	resp, err := c.HTTPClient.Do(req)
	if err != nil {
		return nil, 0, fmt.Errorf("下载请求失败: %w", err)
	}

	if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusPartialContent {
		resp.Body.Close()
		return nil, 0, fmt.Errorf("HTTP %d", resp.StatusCode)
	}

	return resp.Body, resp.ContentLength, nil
}