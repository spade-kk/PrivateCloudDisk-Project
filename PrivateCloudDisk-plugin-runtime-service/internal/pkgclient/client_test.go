package pkgclient

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"
)

func sha256Of(data []byte) string {
	sum := sha256.Sum256(data)
	return hex.EncodeToString(sum[:])
}

// zipBytes 构造一个最小合法 ZIP 包（含一个虚拟入口），用于 pkgclient 魔数校验。
func zipBytes() []byte {
	var buffer bytes.Buffer
	writer := zip.NewWriter(&buffer)
	f, _ := writer.Create("src/main.py")
	f.Write([]byte("# test"))
	writer.Close()
	return buffer.Bytes()
}

func TestNewSetsTimeout(t *testing.T) {
	client := New("http://x", "token", 128)
	if client.HTTP.Timeout != 30*time.Second {
		t.Fatalf("默认客户端应设置 30 秒超时：%v", client.HTTP.Timeout)
	}
}

func TestDownloadRejectsNonPcdpkgDestination(t *testing.T) {
	client := New("http://x", "svc-token", 1024)
	err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.zip"))
	if err == nil || !strings.Contains(err.Error(), "必须使用 .pcdpkg") {
		t.Fatalf("应拒绝非 .pcdpkg 目标：%v", err)
	}
}

func TestDownloadSuccess(t *testing.T) {
	content := zipBytes()
	checksum := sha256Of(content)
	var servedPath, servedToken string
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		servedPath = request.URL.Path
		servedToken = request.Header.Get("X-PCD-Service-Token")
		response.Header().Set("X-PCD-Package-SHA256", checksum)
		response.Header().Set("Content-Type", "application/zip")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()

	destination := filepath.Join(t.TempDir(), "pkg.pcdpkg")
	if err := client.Download(context.Background(), "version-1", destination); err != nil {
		t.Fatalf("Download 失败：%v", err)
	}
	if !strings.HasSuffix(servedPath, "/internal/v1/packages/version-1") {
		t.Fatalf("路径错误：%s", servedPath)
	}
	if servedToken != "svc-token" {
		t.Fatalf("服务令牌缺失：%q", servedToken)
	}
	data, err := os.ReadFile(destination)
	if err != nil || !bytes.Equal(data, content) {
		t.Fatalf("包内容错误：err=%v", err)
	}
}

func TestDownloadErrors(t *testing.T) {
	checksum := sha256Of([]byte("data"))
	for _, td := range []struct {
		name       string
		status     int
		hashHeader string
		body       string
		expected   string
	}{
		{name: "服务错误", status: http.StatusInternalServerError, hashHeader: checksum, body: "", expected: "状态 500"},
		{name: "缺哈希头", status: http.StatusOK, hashHeader: "", body: "data", expected: "缺少有效 SHA256 头"},
		{name: "非法哈希", status: http.StatusOK, hashHeader: "nothex", body: "data", expected: "缺少有效 SHA256 头"},
	} {
		t.Run(td.name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
				if td.hashHeader != "" {
					response.Header().Set("X-PCD-Package-SHA256", td.hashHeader)
				}
				response.WriteHeader(td.status)
				response.Write([]byte(td.body))
			}))
			defer server.Close()
			client := New(server.URL, "svc-token", 1024)
			client.HTTP = server.Client()
			err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg"))
			if err == nil || !strings.Contains(err.Error(), td.expected) {
				t.Fatalf("应返回 %q 错误：%v", td.expected, err)
			}
		})
	}
}

func TestDownloadContentTypeRejected(t *testing.T) {
	content := zipBytes()
	checksum := sha256Of(content)
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("X-PCD-Package-SHA256", checksum)
		response.Header().Set("Content-Type", "text/html")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg"))
	if err == nil || !strings.Contains(err.Error(), "不是 ZIP") {
		t.Fatalf("非 ZIP Content-Type 应被拒绝：%v", err)
	}
}

func TestDownloadHashMismatch(t *testing.T) {
	content := zipBytes()
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("X-PCD-Package-SHA256", strings.Repeat("ab", 32))
		response.Header().Set("Content-Type", "application/zip")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg"))
	if err == nil || !strings.Contains(err.Error(), "哈希不一致") {
		t.Fatalf("哈希不一致应被拒绝：%v", err)
	}
}

func TestDownloadOversize(t *testing.T) {
	content := bytes.Repeat([]byte("PK\x03\x04padding"), 512)
	checksum := sha256Of(content)
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("X-PCD-Package-SHA256", checksum)
		response.Header().Set("Content-Type", "application/zip")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg"))
	if err == nil || !strings.Contains(err.Error(), "超过包大小上限") {
		t.Fatalf("超大包应被拒绝：%v", err)
	}
}

func TestDownloadExistingDestinationFails(t *testing.T) {
	content := zipBytes()
	checksum := sha256Of(content)
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("X-PCD-Package-SHA256", checksum)
		response.Header().Set("Content-Type", "application/zip")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	destination := filepath.Join(t.TempDir(), "pkg.pcdpkg")
	if err := os.WriteFile(destination, []byte("old"), 0o600); err != nil {
		t.Fatal(err)
	}
	if err := client.Download(context.Background(), "v1", destination); err == nil {
		t.Fatal("目标已存在应失败（O_EXCL）")
	}
}

func TestDownloadContextCancel(t *testing.T) {
	client := New("http://127.0.0.1:1", "svc-token", 1024)
	ctx, cancel := context.WithCancel(context.Background())
	cancel()
	if err := client.Download(ctx, "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg")); err == nil {
		t.Fatal("取消上下文应终止下载")
	}
}

func TestDownloadRetriesThenSucceeds(t *testing.T) {
	content := zipBytes()
	checksum := sha256Of(content)
	attempts := 0
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		attempts++
		if attempts < 3 {
			response.WriteHeader(http.StatusInternalServerError)
			return
		}
		response.Header().Set("X-PCD-Package-SHA256", checksum)
		response.Header().Set("Content-Type", "application/zip")
		response.WriteHeader(http.StatusOK)
		response.Write(content)
	}))
	defer server.Close()
	client := New(server.URL, "svc-token", 1024)
	client.HTTP = server.Client()
	client.retries = 3
	client.retryDelay = 10 * time.Millisecond
	if err := client.Download(context.Background(), "v1", filepath.Join(t.TempDir(), "pkg.pcdpkg")); err != nil {
		t.Fatalf("重试后应成功：%v", err)
	}
	if attempts != 3 {
		t.Fatalf("期望 3 次尝试，实际 %d", attempts)
	}
}
