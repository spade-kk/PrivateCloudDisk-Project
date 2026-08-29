package api

import (
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/hex"
	"encoding/json"
	"errors"
	"io"
	"log/slog"
	"net/http"
	"os"
	"os/exec"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/sandbox"
	"privateclouddisk/plugin-runtime-service/internal/validation"
)

type Server struct {
	Config    config.Config
	Validator validation.Validator
	Runner    *sandbox.Runner
	Slots     chan struct{}
	active    atomic.Int64
	testMu    sync.RWMutex
	testTasks map[string]*testTask
}

type testTask struct {
	status model.TestExecutionStatus
	cancel context.CancelFunc
}

func (server *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health/live", server.live)
	mux.HandleFunc("GET /health/ready", server.ready)
	mux.HandleFunc("GET /internal/v1/health/capacity", server.auth(server.capacity))
	mux.HandleFunc("GET /internal/v1/metrics/uds", server.auth(server.udsMetrics))
	mux.HandleFunc("POST /internal/v1/validation/python", server.auth(server.validatePython))
	mux.HandleFunc("POST /internal/v1/validation/javascript", server.auth(server.validateJavaScript))
	mux.HandleFunc("POST /internal/v1/executions/preprocess-chain", server.auth(server.executePreprocess))
	mux.HandleFunc("POST /internal/v1/executions/post-available-chain", server.auth(server.executePostAvailable))
	mux.HandleFunc("POST /internal/v1/executions/capability", server.auth(server.executeCapability))
	// [PLUGIN-TEST-001] 测试执行异步进入 Runtime Sandbox，不阻塞 HTTP 请求。
	mux.HandleFunc("POST /internal/v1/test-executions", server.auth(server.createTestExecution))
	mux.HandleFunc("GET /internal/v1/test-executions/{executionID}", server.auth(server.getTestExecution))
	mux.HandleFunc("POST /internal/v1/test-executions/{executionID}/cancel", server.auth(server.cancelTestExecution))
	return securityHeaders(mux)
}

func (server *Server) live(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]interface{}{"status": "UP"})
}

func (server *Server) ready(response http.ResponseWriter, _ *http.Request) {
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Second)
	defer cancel()
	if err := exec.CommandContext(ctx, server.Config.DockerBinary, "version", "--format", "{{.Client.Version}}").Run(); err != nil {
		writeJSON(response, http.StatusServiceUnavailable, map[string]interface{}{
			"status": "DOWN", "reason": "沙箱容器客户端不可用",
		})
		return
	}
	writeJSON(response, http.StatusOK, map[string]interface{}{"status": "UP"})
}

func (server *Server) capacity(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]interface{}{
		"active":    server.active.Load(),
		"capacity":  cap(server.Slots),
		"available": len(server.Slots),
	})
}

// udsMetrics exposes only aggregate Runtime-owned UDS indicators to trusted
// operators. It must not disclose socket paths, tokens, plugin IDs, tenant
// identifiers or request parameters.
func (server *Server) udsMetrics(response http.ResponseWriter, _ *http.Request) {
	if server.Runner == nil || server.Runner.Sessions == nil {
		writeJSON(response, http.StatusServiceUnavailable, map[string]interface{}{
			"status": "DOWN", "reason": "Unix Socket session manager unavailable",
		})
		return
	}
	writeJSON(response, http.StatusOK, server.Runner.Sessions.Stats())
}

func (server *Server) validatePython(response http.ResponseWriter, request *http.Request) {
	var payload model.ValidationRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	writeJSON(response, http.StatusOK, server.Validator.Python(payload))
}

func (server *Server) validateJavaScript(response http.ResponseWriter, request *http.Request) {
	var payload model.ValidationRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	writeJSON(response, http.StatusOK, server.Validator.JavaScript(payload))
}

func (server *Server) executePreprocess(response http.ResponseWriter, request *http.Request) {
	var payload model.PreprocessChainRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	select {
	case server.Slots <- struct{}{}:
		server.active.Add(1)
		defer func() {
			<-server.Slots
			server.active.Add(-1)
		}()
	default:
		writeJSON(response, http.StatusTooManyRequests, model.RuntimeChainResult{
			Status: "failed", FailureCode: "RUNTIME_CAPACITY_EXHAUSTED",
			FailureSummary: "当前沙箱执行容量已满，请稍后重试",
		})
		return
	}
	result := server.Runner.Execute(request.Context(), payload)
	writeJSON(response, http.StatusOK, result)
}

func (server *Server) executePostAvailable(response http.ResponseWriter, request *http.Request) {
	var payload model.PostAvailableChainRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	select {
	case server.Slots <- struct{}{}:
		server.active.Add(1)
		defer func() {
			<-server.Slots
			server.active.Add(-1)
		}()
	default:
		writeJSON(response, http.StatusTooManyRequests, model.RuntimeChainResult{
			Status: "failed", FailureCode: "RUNTIME_CAPACITY_EXHAUSTED",
			FailureSummary: "当前沙箱执行容量已满，请稍后重试",
		})
		return
	}
	writeJSON(response, http.StatusOK, server.Runner.ExecutePostAvailable(request.Context(), payload))
}

func (server *Server) executeCapability(response http.ResponseWriter, request *http.Request) {
	var payload model.CapabilityExecutionRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	select {
	case server.Slots <- struct{}{}:
		server.active.Add(1)
		defer func() {
			<-server.Slots
			server.active.Add(-1)
		}()
	default:
		writeJSON(response, http.StatusTooManyRequests, model.CapabilityExecutionResult{
			Status: "failed", FailureCode: "RUNTIME_CAPACITY_EXHAUSTED",
			FailureSummary: "当前沙箱执行容量已满，请稍后重试",
		})
		return
	}
	writeJSON(response, http.StatusOK, server.Runner.ExecuteCapability(request.Context(), payload))
}

func (server *Server) createTestExecution(response http.ResponseWriter, request *http.Request) {
	var payload model.TestExecutionRequest
	if !decodeJSON(response, request, &payload, 2*1024*1024) {
		return
	}
	if payload.PluginID == "" || payload.VersionID == "" || payload.TestEntrypoint == "" || payload.UserID == "" {
		writeJSON(response, http.StatusUnprocessableEntity, map[string]interface{}{
			"code": "PLUGIN-TEST-REQUEST-INVALID", "message": "插件、版本、用户和测试入口不能为空",
		})
		return
	}
	if payload.ScriptEntry == "" {
		payload.ScriptEntry = "src/main.py"
	}
	if payload.ExecutionID == "" {
		payload.ExecutionID = newExecutionID()
	}
	server.testMu.Lock()
	if server.testTasks == nil {
		server.testTasks = make(map[string]*testTask)
	}
	if _, exists := server.testTasks[payload.ExecutionID]; exists {
		server.testMu.Unlock()
		writeJSON(response, http.StatusConflict, map[string]interface{}{
			"code": "PLUGIN-TEST-IDEMPOTENCY-CONFLICT", "message": "测试任务已存在",
		})
		return
	}
	ctx, cancel := context.WithCancel(context.Background())
	server.testTasks[payload.ExecutionID] = &testTask{
		status: model.TestExecutionStatus{ExecutionID: payload.ExecutionID, Status: "PENDING"},
		cancel: cancel,
	}
	server.testMu.Unlock()
	go server.runTestExecution(ctx, payload)
	writeJSON(response, http.StatusAccepted, model.TestExecutionAccepted{
		ExecutionID: payload.ExecutionID, Status: "PENDING",
	})
}

func (server *Server) runTestExecution(parent context.Context, payload model.TestExecutionRequest) {
	server.updateTestStatus(payload.ExecutionID, func(status *model.TestExecutionStatus) {
		status.Status = "RUNNING"
		status.StartedAt = time.Now().UTC().Format(time.RFC3339Nano)
	})
	ctx, cancel := context.WithTimeout(parent, server.Config.ExecutionTimeout)
	defer cancel()
	select {
	case server.Slots <- struct{}{}:
		server.active.Add(1)
		defer func() { <-server.Slots; server.active.Add(-1) }()
	default:
		server.updateTestStatus(payload.ExecutionID, func(status *model.TestExecutionStatus) {
			status.Status = "FAILED"
			status.ErrorCode = "RUNTIME_CAPACITY_EXHAUSTED"
			status.ErrorSummary = "当前沙箱执行容量已满，请稍后重试"
			status.EndedAt = time.Now().UTC().Format(time.RFC3339Nano)
		})
		return
	}
	if server.Runner == nil {
		server.updateTestStatus(payload.ExecutionID, func(status *model.TestExecutionStatus) {
			status.Status = "FAILED"
			status.ErrorCode = "RUNTIME_NOT_READY"
			status.ErrorSummary = "Runtime 执行器未初始化"
			status.EndedAt = time.Now().UTC().Format(time.RFC3339Nano)
		})
		return
	}
	result := server.Runner.ExecuteCapability(ctx, model.CapabilityExecutionRequest{
		ExecutionID: payload.ExecutionID,
		StepID:      "test",
		UserID:      payload.UserID,
		SpaceID:     payload.SpaceID,
		Input:       payload.Parameters,
		Entrypoint: model.Entrypoint{
			PluginID:     payload.PluginID,
			VersionID:    payload.VersionID,
			Runtime:      "PYTHON_3_11",
			ModulePath:   payload.ScriptEntry,
			FunctionName: payload.TestEntrypoint,
			Permissions:  []string{"file.content.read", "plugin.log.write"},
		},
	})
	status := "FAILED"
	if result.Status == "success" {
		status = "SUCCESS"
	} else if result.FailureCode == "PLUGIN_RUNTIME_TIMEOUT" {
		status = "TIMEOUT"
	} else if ctx.Err() != nil {
		status = "CANCELLED"
	}
	server.updateTestStatus(payload.ExecutionID, func(value *model.TestExecutionStatus) {
		// [PLUGIN-TEST-002] 取消请求与容器完成可能并发到达；CANCELLED 是终态，
		// 不能被稍后返回的成功结果覆盖，避免前端看到与用户操作相反的状态。
		if value.Status == "CANCELLED" {
			return
		}
		value.Status = status
		value.Result = result.Output
		value.ErrorCode = result.FailureCode
		value.ErrorSummary = result.FailureSummary
		value.EndedAt = time.Now().UTC().Format(time.RFC3339Nano)
	})
}

func (server *Server) getTestExecution(response http.ResponseWriter, request *http.Request) {
	id := request.PathValue("executionID")
	server.testMu.RLock()
	task, ok := server.testTasks[id]
	if ok {
		status := task.status
		server.testMu.RUnlock()
		writeJSON(response, http.StatusOK, status)
		return
	}
	server.testMu.RUnlock()
	writeJSON(response, http.StatusNotFound, map[string]interface{}{
		"code": "PLUGIN-TEST-NOT-FOUND", "message": "测试任务不存在",
	})
}

func (server *Server) cancelTestExecution(response http.ResponseWriter, request *http.Request) {
	id := request.PathValue("executionID")
	server.testMu.Lock()
	task, ok := server.testTasks[id]
	if ok && (task.status.Status == "PENDING" || task.status.Status == "RUNNING") {
		task.cancel()
		task.status.Status = "CANCELLED"
		task.status.EndedAt = time.Now().UTC().Format(time.RFC3339Nano)
	}
	server.testMu.Unlock()
	if !ok {
		writeJSON(response, http.StatusNotFound, map[string]interface{}{
			"code": "PLUGIN-TEST-NOT-FOUND", "message": "测试任务不存在",
		})
		return
	}
	writeJSON(response, http.StatusOK, map[string]interface{}{"cancelled": true})
}

func (server *Server) updateTestStatus(id string, update func(*model.TestExecutionStatus)) {
	server.testMu.Lock()
	defer server.testMu.Unlock()
	if task, ok := server.testTasks[id]; ok {
		update(&task.status)
	}
}

func newExecutionID() string {
	buffer := make([]byte, 16)
	if _, err := rand.Read(buffer); err != nil {
		return "test-" + time.Now().UTC().Format("20060102150405.000000000")
	}
	return "test-" + hex.EncodeToString(buffer)
}

func (server *Server) auth(next http.HandlerFunc) http.HandlerFunc {
	return func(response http.ResponseWriter, request *http.Request) {
		expected := []byte(server.Config.InternalServiceToken)
		presented := []byte(request.Header.Get("X-PCD-Service-Token"))
		if len(expected) == 0 ||
			len(presented) != len(expected) ||
			subtle.ConstantTimeCompare(expected, presented) != 1 {
			writeJSON(response, http.StatusUnauthorized, map[string]interface{}{
				"code": "AUTH-UNAUTHENTICATED", "message": "内部服务认证失败",
			})
			return
		}
		next(response, request)
	}
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		// AUDIT FIX [6.7]: Runtime endpoints are internal, but retain the full
		// browser-safe header baseline so an accidental reverse-proxy exposure
		// cannot be framed, MIME-sniffed, or used to leak referrer information.
		response.Header().Set("X-Content-Type-Options", "nosniff")
		response.Header().Set("Cache-Control", "no-store")
		response.Header().Set("Content-Security-Policy", "default-src 'none'")
		response.Header().Set("X-Frame-Options", "DENY")
		response.Header().Set("Referrer-Policy", "no-referrer")
		response.Header().Set("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
		next.ServeHTTP(response, request)
	})
}

func decodeJSON(
	response http.ResponseWriter,
	request *http.Request,
	target interface{},
	maxBytes int64,
) bool {
	request.Body = http.MaxBytesReader(response, request.Body, maxBytes)
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		writeJSON(response, http.StatusBadRequest, map[string]interface{}{
			"code": "RUNTIME-REQUEST-INVALID", "message": "请求体格式无效",
		})
		return false
	}
	var trailing interface{}
	if err := decoder.Decode(&trailing); !errors.Is(err, io.EOF) {
		writeJSON(response, http.StatusBadRequest, map[string]interface{}{
			"code": "RUNTIME-REQUEST-INVALID", "message": "请求体只能包含一个 JSON 文档",
		})
		return false
	}
	return true
}

func writeJSON(response http.ResponseWriter, status int, payload interface{}) {
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.WriteHeader(status)
	if err := json.NewEncoder(response).Encode(payload); err != nil {
		slog.Error("写入 Runtime 响应失败", "error", sanitizeError(err))
	}
}

func sanitizeError(err error) string {
	if err == nil {
		return ""
	}
	value := strings.ReplaceAll(err.Error(), os.TempDir(), "[tmp]")
	if len(value) > 500 {
		return value[:500]
	}
	return value
}
