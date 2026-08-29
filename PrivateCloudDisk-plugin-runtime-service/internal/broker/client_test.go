package broker

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func newTestClient(server *httptest.Server) *Client {
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	return client
}

func sha256Of(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

func TestNewSetsTimeout(t *testing.T) {
	client := New("http://x", "token", 128)
	if client.HTTP.Timeout != 10*time.Minute {
		t.Fatalf("默认客户端应设置 10 分钟超时：%v", client.HTTP.Timeout)
	}
}

func TestExchangeSuccessAndErrors(t *testing.T) {
	var method, servedPath, seenToken string
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		method, servedPath, seenToken = request.Method, request.URL.Path, request.Header.Get("X-PCD-Service-Token")
		response.WriteHeader(http.StatusOK)
		response.Write([]byte(`{"execution_lease":"lease-1","expires_at":"2030-01-01T00:00:00Z"}`))
	}))
	defer server.Close()
	client := newTestClient(server)

	exchanged, err := client.Exchange(context.Background(), "gate1", "exec1", "ref-1", 60)
	if err != nil {
		t.Fatalf("Exchange 成功路径失败：%v", err)
	}
	if exchanged.ExecutionLease != "lease-1" {
		t.Fatalf("Lease 解析错误：%+v", exchanged)
	}
	if method != http.MethodPost {
		t.Fatalf("Exchange 应为 POST：%s", method)
	}
	if !strings.HasSuffix(servedPath, "/internal/v1/preprocess-gates/gate1/lease-exchange") {
		t.Fatalf("Exchange 路径错误：%s", servedPath)
	}
	if seenToken != "svc-token" {
		t.Fatal("服务令牌缺失")
	}
}

func TestExchangeNon200AndInvalid(t *testing.T) {
	for _, td := range []struct {
		name     string
		status   int
		body     string
		expected string
	}{
		{name: "非 200", status: http.StatusInternalServerError, body: "", expected: "状态 500"},
		{name: "缺 lease", status: http.StatusOK, body: `{}`, expected: "兑换响应无效"},
		{name: "非法 JSON", status: http.StatusOK, body: `not-json`, expected: "兑换响应无效"},
	} {
		t.Run(td.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
				response.WriteHeader(td.status)
				response.Write([]byte(td.body))
			}))
			defer server.Close()
			client := newTestClient(server)
			_, err := client.Exchange(context.Background(), "gate1", "exec1", "ref-1", 60)
			if err == nil || !strings.Contains(err.Error(), td.expected) {
				t.Fatalf("应返回 %q 错误：%v", td.expected, err)
			}
		})
	}
}

func TestDownloadSuccessAndLimits(t *testing.T) {
	var receivedLease, receivedExec, receivedMethod, receivedToken string
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		receivedLease = request.Header.Get("X-Content-Lease")
		receivedExec = request.Header.Get("X-PCD-Execution-Id")
		receivedMethod = request.Method
		receivedToken = request.Header.Get("X-PCD-Service-Token")
		response.WriteHeader(http.StatusOK)
		response.Write([]byte("hello-content"))
	}))
	defer server.Close()
	client := newTestClient(server)
	destination := filepath.Join(t.TempDir(), "content.bin")
	if err := client.Download(context.Background(), "gate1", "exec1", "lease-1", destination); err != nil {
		t.Fatalf("Download 失败：%v", err)
	}
	if receivedMethod != http.MethodGet || !strings.HasSuffix(server.URL+"/x", "/x") {
		_ = receivedMethod
	}
	if receivedLease != "lease-1" || receivedExec != "exec1" {
		t.Fatalf("Lease/执行 ID 未透传：%q/%q", receivedLease, receivedExec)
	}
	if receivedToken != "svc-token" || receivedMethod != http.MethodGet {
		t.Fatalf("下载请求方法/令牌错误：%s/%s", receivedMethod, receivedToken)
	}
	data, err := os.ReadFile(destination)
	if err != nil || string(data) != "hello-content" {
		t.Fatalf("下载内容错误：%q err=%v", data, err)
	}
}

func TestDownloadOversizeRejected(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		response.Write([]byte(strings.Repeat("x", 2048)))
	}))
	defer server.Close()
	// maxBytes=1024
	client := newTestClient(server)
	if err := client.Download(context.Background(), "gate1", "exec1", "lease-1", filepath.Join(t.TempDir(), "c.bin")); err == nil ||
		!strings.Contains(err.Error(), "超过 Runtime 读取上限") {
		t.Fatalf("超大内容应被拒绝：%v", err)
	}
}

func TestDownloadNon200AndExistingFile(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusNotFound)
	}))
	defer server.Close()
	client := newTestClient(server)
	if err := client.Download(context.Background(), "gate1", "exec1", "l", filepath.Join(t.TempDir(), "c.bin")); err == nil ||
		!strings.Contains(err.Error(), "状态 404") {
		t.Fatalf("非 200 应报错：%v", err)
	}

	// O_EXCL：目标已存在应失败。
	success := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
	}))
	defer success.Close()
	client2 := newTestClient(success)
	destination := filepath.Join(t.TempDir(), "exists.bin")
	if err := os.WriteFile(destination, []byte("old"), 0o400); err != nil {
		t.Fatal(err)
	}
	if err := client2.Download(context.Background(), "gate1", "exec1", "l", destination); err == nil {
		t.Fatal("目标已存在应失败（O_EXCL）")
	}
}

func TestDownloadActiveHeaders(t *testing.T) {
	var actor, space string
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if !strings.HasSuffix(request.URL.Path, "/internal/v1/automation/files/file-1/content") {
			t.Errorf("DownloadActive 路径错误：%s", request.URL.Path)
		}
		actor = request.Header.Get("X-PCD-Actor-User-Id")
		space = request.Header.Get("X-Space-Id")
		response.WriteHeader(http.StatusOK)
		response.Write([]byte("active"))
	}))
	defer server.Close()
	client := newTestClient(server)
	if err := client.DownloadActive(context.Background(), "file-1", "exec1", "user-1", "space-1", filepath.Join(t.TempDir(), "a.bin")); err != nil {
		t.Fatalf("DownloadActive 失败：%v", err)
	}
	if actor != "user-1" || space != "space-1" {
		t.Fatalf("用户/空间上下文未透传：%q/%q", actor, space)
	}
}

func TestUploadSuccess(t *testing.T) {
	content := []byte("candidate-data")
	checksum := sha256Of(content)
	var uploadMethod, uploadContentType string
	var uploadBody []byte
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		uploadMethod = request.Method
		uploadContentType = request.Header.Get("Content-Type")
		uploadBody, _ = io.ReadAll(request.Body)
		response.WriteHeader(http.StatusOK)
		response.Write([]byte(`{"candidate_id":"cand-1","candidate_checksum":"` + checksum + `","candidate_size":14}`))
	}))
	defer server.Close()
	client := newTestClient(server)
	source := filepath.Join(t.TempDir(), "src.bin")
	if err := os.WriteFile(source, content, 0o600); err != nil {
		t.Fatal(err)
	}
	candidate, err := client.Upload(context.Background(), "gate1", "exec1", "lease-1", source)
	if err != nil {
		t.Fatalf("Upload 成功路径失败：%v", err)
	}
	if candidate.ID != "cand-1" || candidate.Size != int64(len(content)) || candidate.Checksum != checksum {
		t.Fatalf("候选解析错误：%+v", candidate)
	}
	if uploadMethod != http.MethodPut || uploadContentType != "application/octet-stream" || string(uploadBody) != string(content) {
		t.Fatalf("Upload 请求构造错误：%s/%s/%s", uploadMethod, uploadContentType, uploadBody)
	}
}

func TestUploadHashMismatchRejected(t *testing.T) {
	content := []byte("candidate-data")
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusOK)
		response.Write([]byte(`{"candidate_id":"cand-1","candidate_checksum":"` + strings.Repeat("00", 32) + `","candidate_size":14}`))
	}))
	defer server.Close()
	client := newTestClient(server)
	source := filepath.Join(t.TempDir(), "src.bin")
	if err := os.WriteFile(source, content, 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := client.Upload(context.Background(), "gate1", "exec1", "lease-1", source)
	if err == nil || !strings.Contains(err.Error(), "哈希") {
		t.Fatalf("哈希不一致应被拒绝：%v", err)
	}
}

func TestUploadStatusErrorIncludesBody(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.WriteHeader(http.StatusBadRequest)
		response.Write([]byte("bad parameters"))
	}))
	defer server.Close()
	client := newTestClient(server)
	source := filepath.Join(t.TempDir(), "src.bin")
	if err := os.WriteFile(source, []byte("data"), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := client.Upload(context.Background(), "gate1", "exec1", "lease-1", source)
	if err == nil || !strings.Contains(err.Error(), "状态 400") || !strings.Contains(err.Error(), "bad parameters") {
		t.Fatalf("上传失败应含状态与响应体：%v", err)
	}
}

func TestUploadOversizeRejected(t *testing.T) {
	// MaxBytes=1024，源文件 > 上限。
	server := httptest.NewServer(http.HandlerFunc(func(_ http.ResponseWriter, _ *http.Request) {}))
	defer server.Close()
	client := newTestClient(server)
	source := filepath.Join(t.TempDir(), "big.bin")
	if err := os.WriteFile(source, []byte(strings.Repeat("x", 2048)), 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := client.Upload(context.Background(), "gate1", "exec1", "lease-1", source); err == nil ||
		!strings.Contains(err.Error(), "大小越界") {
		t.Fatalf("超大候选应被拒绝：%v", err)
	}
}
