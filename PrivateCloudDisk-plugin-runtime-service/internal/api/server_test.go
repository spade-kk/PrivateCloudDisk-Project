package api

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"privateclouddisk/plugin-runtime-service/internal/config"
)

func TestInternalEndpointRejectsMissingServiceToken(t *testing.T) {
	server := &Server{
		Config: config.Config{InternalServiceToken: "test-secret"},
		Slots:  make(chan struct{}, 1),
	}
	request := httptest.NewRequest(http.MethodGet, "/internal/v1/health/capacity", nil)
	response := httptest.NewRecorder()

	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusUnauthorized {
		t.Fatalf("缺少内部凭证应返回 401，实际为 %d", response.Code)
	}
}

func TestCapacityEndpointAcceptsServiceToken(t *testing.T) {
	server := &Server{
		Config: config.Config{InternalServiceToken: "test-secret"},
		Slots:  make(chan struct{}, 2),
	}
	request := httptest.NewRequest(http.MethodGet, "/internal/v1/health/capacity", nil)
	request.Header.Set("X-PCD-Service-Token", "test-secret")
	response := httptest.NewRecorder()

	server.Handler().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("有效内部凭证应通过，实际为 %d", response.Code)
	}
}

func TestTestExecutionEndpointIsAsynchronousAndProtected(t *testing.T) {
	server := &Server{Config: config.Config{InternalServiceToken: "test-secret", ExecutionTimeout: 1}, Slots: make(chan struct{}, 1)}
	request := httptest.NewRequest(http.MethodPost, "/internal/v1/test-executions", strings.NewReader(`{"plugin_id":"p","version_id":"v","test_entrypoint":"test_main","user_id":"u"}`))
	request.Header.Set("X-PCD-Service-Token", "test-secret")
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	server.Handler().ServeHTTP(response, request)
	if response.Code != http.StatusAccepted {
		t.Fatalf("测试执行创建应返回 202，实际为 %d: %s", response.Code, response.Body.String())
	}
	body, _ := io.ReadAll(response.Body)
	if !strings.Contains(string(body), `"status":"PENDING"`) {
		t.Fatalf("测试执行响应缺少 PENDING 状态: %s", body)
	}
}
