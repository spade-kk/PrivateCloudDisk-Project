package storage

import (
	"bytes"
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

type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

func New(baseURL, token string) *Client {
	transport := &http.Transport{MaxIdleConns: 64, MaxIdleConnsPerHost: 32, IdleConnTimeout: 90 * time.Second}
	return &Client{baseURL: strings.TrimRight(baseURL, "/"), token: token,
		http: &http.Client{Transport: transport, Timeout: 15 * time.Minute}}
}

func (c *Client) objectURL(algorithm, hash string) string {
	return fmt.Sprintf("%s/internal/v1/git/objects/%s/%s", c.baseURL, algorithm, hash)
}

func (c *Client) Exists(ctx context.Context, algorithm, hash string) (bool, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodHead, c.objectURL(algorithm, hash), nil)
	if err != nil {
		return false, fmt.Errorf("storage HEAD request: %w", err)
	}
	req.Header.Set("X-PCD-Service-Token", c.token)
	res, err := c.http.Do(req)
	if err != nil {
		return false, err
	}
	defer res.Body.Close()
	if res.StatusCode == http.StatusNotFound {
		return false, nil
	}
	if res.StatusCode != http.StatusOK {
		return false, storageResponseError(res, "HEAD")
	}
	return true, nil
}

func (c *Client) Put(ctx context.Context, algorithm, hash string, compressed []byte) error {
	digest := sha256.Sum256(compressed)
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, c.objectURL(algorithm, hash), bytes.NewReader(compressed))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("X-PCD-Service-Token", c.token)
	req.Header.Set("X-Content-SHA256", hex.EncodeToString(digest[:]))
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
		return fmt.Errorf("storage PUT returned %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
	}
	return nil
}

func (c *Client) PutFile(ctx context.Context, algorithm, hash, sourcePath string) error {
	file, err := os.Open(sourcePath)
	if err != nil {
		return err
	}
	digest := sha256.New()
	if _, err := io.Copy(digest, file); err != nil {
		file.Close()
		return err
	}
	if _, err := file.Seek(0, io.SeekStart); err != nil {
		file.Close()
		return err
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPut, c.objectURL(algorithm, hash), file)
	if err != nil {
		file.Close()
		return err
	}
	req.Header.Set("Content-Type", "application/octet-stream")
	req.Header.Set("X-PCD-Service-Token", c.token)
	req.Header.Set("X-Content-SHA256", hex.EncodeToString(digest.Sum(nil)))
	res, err := c.http.Do(req)
	file.Close()
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(res.Body, 4096))
		return fmt.Errorf("storage PUT returned %d: %s", res.StatusCode, strings.TrimSpace(string(body)))
	}
	return nil
}

func (c *Client) Download(ctx context.Context, algorithm, hash, destination string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, c.objectURL(algorithm, hash), nil)
	if err != nil {
		return fmt.Errorf("storage GET request: %w", err)
	}
	req.Header.Set("X-PCD-Service-Token", c.token)
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusOK {
		return storageResponseError(res, "GET")
	}
	if err := os.MkdirAll(filepath.Dir(destination), 0o750); err != nil {
		return err
	}
	temporary := destination + ".tmp"
	file, err := os.OpenFile(temporary, os.O_CREATE|os.O_TRUNC|os.O_WRONLY, 0o640)
	if err != nil {
		return err
	}
	_, copyErr := io.Copy(file, res.Body)
	closeErr := file.Close()
	if copyErr != nil {
		os.Remove(temporary)
		return copyErr
	}
	if closeErr != nil {
		os.Remove(temporary)
		return closeErr
	}
	return os.Rename(temporary, destination)
}

func (c *Client) Delete(ctx context.Context, algorithm, hash string) error {
	req, err := http.NewRequestWithContext(ctx, http.MethodDelete, c.objectURL(algorithm, hash), nil)
	if err != nil {
		return fmt.Errorf("storage DELETE request: %w", err)
	}
	req.Header.Set("X-PCD-Service-Token", c.token)
	res, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer res.Body.Close()
	if res.StatusCode != http.StatusNoContent && res.StatusCode != http.StatusNotFound {
		return storageResponseError(res, "DELETE")
	}
	return nil
}

// storageResponseError 保留 Storage Broker 返回的 detail，避免 Git 协议层只能看到一个
// 无法定位根因的 503。响应体最多读取 4 KiB，不会把大响应带入日志或错误链。
func storageResponseError(response *http.Response, operation string) error {
	body, readErr := io.ReadAll(io.LimitReader(response.Body, 4096))
	detail := strings.TrimSpace(string(body))
	if detail != "" {
		return fmt.Errorf("storage %s returned %d: %s", operation, response.StatusCode, detail)
	}
	if readErr != nil {
		return fmt.Errorf("storage %s returned %d (read error: %w)", operation, response.StatusCode, readErr)
	}
	return fmt.Errorf("storage %s returned %d", operation, response.StatusCode)
}
