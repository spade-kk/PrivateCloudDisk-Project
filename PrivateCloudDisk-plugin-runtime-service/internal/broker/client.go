package broker

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"strings"
	"time"
)

type Candidate struct {
	ID       string `json:"candidate_id"`
	Checksum string `json:"candidate_checksum"`
	Size     int64  `json:"candidate_size"`
}

type ExchangedLease struct {
	ExecutionLease string `json:"execution_lease"`
	ExpiresAt      string `json:"expires_at"`
}

type Client struct {
	BaseURL      string
	ServiceToken string
	MaxBytes     int64
	HTTP         *http.Client
}

func New(baseURL, token string, maxBytes int64) *Client {
	return &Client{
		BaseURL:      strings.TrimRight(baseURL, "/"),
		ServiceToken: token,
		MaxBytes:     maxBytes,
		HTTP:         &http.Client{Timeout: 10 * time.Minute},
	}
}

func (c *Client) Download(
	ctx context.Context,
	gateID, executionID, contentLease, destination string,
) error {
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodGet,
		c.BaseURL+"/internal/v1/preprocess-gates/"+gateID+"/content",
		nil,
	)
	if err != nil {
		return err
	}
	c.setHeaders(request, executionID, contentLease)
	response, err := c.HTTP.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("Storage Broker 读取失败，状态 %d", response.StatusCode)
	}
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o400)
	if err != nil {
		return err
	}
	defer output.Close()
	written, err := io.Copy(output, io.LimitReader(response.Body, c.MaxBytes+1))
	if err != nil {
		return err
	}
	if written > c.MaxBytes {
		return errors.New("原始内容超过 Runtime 读取上限")
	}
	return output.Sync()
}

// DownloadActive 读取已激活最终内容；Storage 会再次执行用户、空间和文件归属校验。
func (c *Client) DownloadActive(
	ctx context.Context,
	fileID, executionID, actorUserID, spaceID, destination string,
) error {
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodGet,
		c.BaseURL+"/internal/v1/automation/files/"+fileID+"/content",
		nil,
	)
	if err != nil {
		return err
	}
	request.Header.Set("X-PCD-Service-Token", c.ServiceToken)
	request.Header.Set("X-PCD-Execution-Id", executionID)
	request.Header.Set("X-PCD-Actor-User-Id", actorUserID)
	if spaceID != "" {
		request.Header.Set("X-Space-Id", spaceID)
	}
	response, err := c.HTTP.Do(request)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("Storage Broker 激活内容读取失败，状态 %d", response.StatusCode)
	}
	output, err := os.OpenFile(destination, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o400)
	if err != nil {
		return err
	}
	defer output.Close()
	written, err := io.Copy(output, io.LimitReader(response.Body, c.MaxBytes+1))
	if err != nil {
		return err
	}
	if written > c.MaxBytes {
		return errors.New("激活文件内容超过 Runtime 读取上限")
	}
	return output.Sync()
}

func (c *Client) Upload(
	ctx context.Context,
	gateID, executionID, contentLease, source string,
) (Candidate, error) {
	file, err := os.Open(source)
	if err != nil {
		return Candidate{}, err
	}
	defer file.Close()
	stat, err := file.Stat()
	if err != nil {
		return Candidate{}, err
	}
	if stat.Size() <= 0 || stat.Size() > c.MaxBytes {
		return Candidate{}, errors.New("候选内容大小越界")
	}
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodPut,
		c.BaseURL+"/internal/v1/preprocess-gates/"+gateID+"/candidate",
		file,
	)
	if err != nil {
		return Candidate{}, err
	}
	request.ContentLength = stat.Size()
	request.Header.Set("Content-Type", "application/octet-stream")
	c.setHeaders(request, executionID, contentLease)
	response, err := c.HTTP.Do(request)
	if err != nil {
		return Candidate{}, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(response.Body, 1024))
		return Candidate{}, fmt.Errorf(
			"Storage Broker 候选提交失败，状态 %d: %s",
			response.StatusCode, strings.TrimSpace(string(body)),
		)
	}
	var candidate Candidate
	if json.NewDecoder(io.LimitReader(response.Body, 64*1024)).Decode(&candidate) != nil {
		return Candidate{}, errors.New("Storage Broker 候选响应无效")
	}
	if candidate.ID == "" || len(candidate.Checksum) != 64 || candidate.Size != stat.Size() {
		return Candidate{}, errors.New("Storage Broker 候选响应完整性字段无效")
	}
	actual, err := fileHash(source)
	if err != nil || actual != strings.ToLower(candidate.Checksum) {
		return Candidate{}, errors.New("候选内容哈希与 Broker 响应不一致")
	}
	return candidate, nil
}

// Exchange 把 MQ 中的一次性引用换成绑定 execution_id 的短期执行 Lease。
func (c *Client) Exchange(
	ctx context.Context,
	gateID, executionID, contentLeaseRef string,
	ttlSeconds int,
) (ExchangedLease, error) {
	payload, _ := json.Marshal(map[string]interface{}{
		"execution_id":      executionID,
		"content_lease_ref": contentLeaseRef,
		"ttl_seconds":       ttlSeconds,
	})
	request, err := http.NewRequestWithContext(
		ctx,
		http.MethodPost,
		c.BaseURL+"/internal/v1/preprocess-gates/"+gateID+"/lease-exchange",
		strings.NewReader(string(payload)),
	)
	if err != nil {
		return ExchangedLease{}, err
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("X-PCD-Service-Token", c.ServiceToken)
	response, err := c.HTTP.Do(request)
	if err != nil {
		return ExchangedLease{}, err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return ExchangedLease{}, fmt.Errorf(
			"内容 Lease 兑换失败，状态 %d", response.StatusCode,
		)
	}
	var exchanged ExchangedLease
	if json.NewDecoder(io.LimitReader(response.Body, 64*1024)).Decode(&exchanged) != nil ||
		exchanged.ExecutionLease == "" {
		return ExchangedLease{}, errors.New("内容 Lease 兑换响应无效")
	}
	return exchanged, nil
}

func (c *Client) setHeaders(request *http.Request, executionID, lease string) {
	request.Header.Set("X-PCD-Service-Token", c.ServiceToken)
	request.Header.Set("X-Content-Lease", lease)
	request.Header.Set("X-PCD-Execution-Id", executionID)
}

func fileHash(path string) (string, error) {
	file, err := os.Open(path)
	if err != nil {
		return "", err
	}
	defer file.Close()
	digest := sha256.New()
	if _, err := io.Copy(digest, file); err != nil {
		return "", err
	}
	return hex.EncodeToString(digest.Sum(nil)), nil
}
