package mcp

import (
	"context"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"privateclouddisk/cloudflow-mcp-server/internal/audit"
	"privateclouddisk/cloudflow-mcp-server/internal/config"
	"privateclouddisk/cloudflow-mcp-server/internal/hub"
	"privateclouddisk/cloudflow-mcp-server/internal/identity"
	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

func testServer() *Server {
	return New(config.Config{
		CapabilityHubURL: "http://127.0.0.1:1", InternalServiceToken: "internal-token", IdentitySharedSecret: testSecret,
		IdentityMaxAge: time.Minute, RequestTimeout: time.Second, AuditTimeout: 5 * time.Millisecond,
		ToolListCacheTTL: time.Minute, SessionTTL: time.Minute, MaxBodyBytes: 1024, MaxConcurrentRequests: 2,
		RequestsPerMinutePerUser: 20, MetricsEnabled: true, Version: "test",
	}, slog.New(slog.NewTextHandler(io.Discard, nil)))
}

func testPrincipal() identity.Identity {
	return identity.Identity{UserID: testUser, SpaceID: testSpace, TenantID: "tenant-a", RequestID: "request-a", AgentID: "test-agent"}
}

func TestStaticMCPResourcesPromptsAndUtilityContracts(t *testing.T) {
	server := testServer()
	principal := testPrincipal()

	resources := server.listResources(principal, model.JSONRPCRequest{ID: json.RawMessage("1"), Method: "resources/list"}, time.Now())
	resourceList := resources.Result.(map[string]any)["resources"].([]map[string]any)
	if len(resourceList) != 1 || resourceList[0]["uri"] != "cloudflow://server/policy" {
		t.Fatalf("unexpected static resources: %#v", resourceList)
	}

	read := server.readResource(principal, model.JSONRPCRequest{ID: json.RawMessage("2"), Params: json.RawMessage(`{"uri":"cloudflow://server/policy"}`)}, time.Now())
	if read.Error != nil || !strings.Contains(read.Result.(map[string]any)["contents"].([]map[string]string)[0]["text"], "authenticated user") {
		t.Fatalf("safe policy resource was not returned: %#v", read)
	}
	if result := server.readResource(principal, model.JSONRPCRequest{ID: json.RawMessage("3"), Params: json.RawMessage(`{"uri":"file:///secret"}`)}, time.Now()); result.Error == nil || result.Error.Code != -32602 {
		t.Fatalf("unknown resource must be rejected: %#v", result)
	}

	prompts := server.listPrompts(principal, model.JSONRPCRequest{ID: json.RawMessage("4"), Method: "prompts/list"}, time.Now())
	promptList := prompts.Result.(map[string]any)["prompts"].([]map[string]any)
	if len(promptList) != 1 || promptList[0]["name"] != "safe-file-search" {
		t.Fatalf("unexpected prompts: %#v", promptList)
	}
	prompt := server.getPrompt(principal, model.JSONRPCRequest{ID: json.RawMessage("5"), Params: json.RawMessage(`{"name":"safe-file-search","arguments":{"keyword":"roadmap"}}`)}, time.Now())
	if prompt.Error != nil || !strings.Contains(prompt.Result.(map[string]any)["messages"].([]map[string]any)[0]["content"].(map[string]string)["text"], "roadmap") {
		t.Fatalf("safe prompt was not returned: %#v", prompt)
	}
	if result := server.getPrompt(principal, model.JSONRPCRequest{ID: json.RawMessage("6"), Params: json.RawMessage(`{"name":"other"}`)}, time.Now()); result.Error == nil {
		t.Fatalf("unknown prompt must be rejected: %#v", result)
	}

	if offset, err := decodeCursor(encodeCursor(42)); err != nil || offset != 42 {
		t.Fatalf("opaque cursor must round trip: offset=%d err=%v", offset, err)
	}
	for _, invalid := range []string{"%%", encodeCursor(-1), encodeCursor(1_000_001)} {
		if _, err := decodeCursor(invalid); err == nil {
			t.Fatalf("invalid cursor %q was accepted", invalid)
		}
	}
	if !prefersSSE("text/event-stream, application/json") || prefersSSE("application/json, text/event-stream") || !acceptsMCPResponse("application/json, text/event-stream") {
		t.Fatal("transport negotiation does not preserve required preference semantics")
	}
	if got := safeToolError("", ""); !strings.Contains(got, "CLOUDFLOW_TOOL_FAILED") || truncate("line\nvalue", 50) != "line value" {
		t.Fatalf("safe error/truncation contracts changed: %q", got)
	}
}

func TestHealthMetadataMetricsAndSSELifecycle(t *testing.T) {
	server := testServer()
	handler := server.Handler()
	for _, path := range []string{"/health/live", "/health/ready", "/.well-known/oauth-protected-resource/mcp"} {
		request := httptest.NewRequest(http.MethodGet, "https://gateway.example"+path, nil)
		request.Header.Set("X-Forwarded-Host", "gateway.example")
		recorder := httptest.NewRecorder()
		handler.ServeHTTP(recorder, request)
		if recorder.Code != http.StatusOK || recorder.Header().Get("X-Content-Type-Options") != "nosniff" {
			t.Fatalf("%s should be a protected public surface: code=%d headers=%v", path, recorder.Code, recorder.Header())
		}
	}

	metricsDone := server.metrics.Begin()
	metricsDone("tools/list", "ok", 5)
	metrics := httptest.NewRecorder()
	server.metrics.ServeHTTP(metrics, httptest.NewRequest(http.MethodGet, "/metrics", nil))
	if !strings.Contains(metrics.Body.String(), `cloudflow_mcp_requests_total{method="tools/list",result="ok"} 1`) {
		t.Fatalf("metrics must use only fixed safe labels: %s", metrics.Body.String())
	}

	principal := testPrincipal()
	sessionID, err := server.sessions.Create(principal, time.Now())
	if err != nil {
		t.Fatal(err)
	}
	requestContext, cancel := context.WithCancel(context.Background())
	request := httptest.NewRequest(http.MethodGet, "http://mcp.internal/mcp", nil).WithContext(requestContext)
	request.Header.Set(mcpSessionHeader, sessionID)
	recorder := httptest.NewRecorder()
	go func() {
		time.Sleep(5 * time.Millisecond)
		cancel()
	}()
	method, result := server.serveSSE(recorder, request, principal, time.Now())
	if method != "sse" || result != "closed" || !strings.Contains(recorder.Body.String(), "id: request-a") {
		t.Fatalf("SSE lifecycle must close on request cancellation: method=%s result=%s body=%q", method, result, recorder.Body.String())
	}
	if err := server.sessions.Require(sessionID, identity.Identity{UserID: "other"}, time.Now()); err == nil {
		t.Fatal("session must not be reusable by another principal")
	}
	server.sessions.RemoveExpired(time.Now().Add(2 * time.Minute))
	if err := server.sessions.Require(sessionID, principal, time.Now().Add(2*time.Minute)); err == nil {
		t.Fatal("expired session must be evicted")
	}
}

func TestServePostTransportErrorsCancellationAndCapacity(t *testing.T) {
	server := testServer()
	principal := testPrincipal()

	for _, testCase := range []struct {
		name        string
		accept      string
		contentType string
		body        string
		status      int
	}{
		{name: "accept", accept: "application/json", contentType: "application/json", body: `{}`, status: http.StatusNotAcceptable},
		{name: "content", accept: "application/json, text/event-stream", contentType: "text/plain", body: `{}`, status: http.StatusUnsupportedMediaType},
	} {
		t.Run(testCase.name, func(t *testing.T) {
			request := httptest.NewRequest(http.MethodPost, "http://mcp.internal/mcp", strings.NewReader(testCase.body))
			request.Header.Set("Accept", testCase.accept)
			request.Header.Set("Content-Type", testCase.contentType)
			recorder := httptest.NewRecorder()
			server.servePost(recorder, request, principal, time.Now())
			if recorder.Code != testCase.status {
				t.Fatalf("expected %d, got %d", testCase.status, recorder.Code)
			}
		})
	}

	key := cancellationKey(principal, "session", json.RawMessage("9"))
	cancelled := make(chan struct{})
	server.cancellations.Store(key, context.CancelFunc(func() { close(cancelled) }))
	request := signedRequest(t, http.MethodPost, "/mcp", "cancel-request", "session", `{"jsonrpc":"2.0","method":"notifications/cancelled","params":{"requestId":9}}`)
	request.Header.Set(mcpSessionHeader, "session")
	server.cancel(request, model.JSONRPCRequest{Params: json.RawMessage(`{"requestId":9}`)})
	select {
	case <-cancelled:
	case <-time.After(time.Second):
		t.Fatal("valid cancellation notification did not cancel the matching request")
	}

	server.sem <- struct{}{}
	server.sem <- struct{}{}
	recorder := httptest.NewRecorder()
	if server.tryAcquire(recorder) || recorder.Code != http.StatusServiceUnavailable {
		t.Fatal("capacity guard must reject a saturated MCP process")
	}
	<-server.sem
	<-server.sem
}

func signedRequest(t *testing.T, method, path, requestID, sessionID, body string) *http.Request {
	t.Helper()
	request := httptest.NewRequest(method, "http://mcp.internal"+path, strings.NewReader(body))
	now := time.Now().UTC()
	timestamp := identity.CanonicalTimestamp(now)
	request.Header.Set(identity.HeaderUserID, testUser)
	request.Header.Set(identity.HeaderTenantID, "tenant-a")
	request.Header.Set(identity.HeaderSpaceID, testSpace)
	request.Header.Set(identity.HeaderRequestID, requestID)
	request.Header.Set(identity.HeaderTimestamp, timestamp)
	request.Header.Set(identity.HeaderSignature, identity.SignForGateway(testSecret, method, path, requestID, timestamp, testUser, "tenant-a", testSpace))
	if sessionID != "" {
		request.Header.Set(mcpSessionHeader, sessionID)
	}
	return request
}

func TestDispatchInitializationToolPagingAndHubToolFailure(t *testing.T) {
	var toolRequests atomic.Int32
	var invokeFailure atomic.Bool
	hubServer := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set("Content-Type", "application/json")
		switch request.URL.Path {
		case "/internal/v1/capabilities/mcp/audit":
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[map[string]any]{Code: "OK", Data: map[string]any{"accepted": true}})
		case "/internal/v1/capabilities/mcp/tools":
			toolRequests.Add(1)
			rows := make([]model.CapabilityRow, 0, 25)
			for index := 0; index < 25; index++ {
				rows = append(rows, model.CapabilityRow{CapabilityKey: "api:file.list", Status: "ACTIVE", Description: "list", InputSchemaJSON: `{"type":"object"}`})
			}
			next := 25
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[model.HubToolListResponse]{Code: "OK", Data: model.HubToolListResponse{Capabilities: rows, NextOffset: &next}})
		case "/internal/v1/capabilities/mcp/invoke":
			if invokeFailure.Load() {
				_ = json.NewEncoder(response).Encode(model.APIEnvelope[model.HubCapabilityResult]{Code: "OK", Data: model.HubCapabilityResult{Success: false, ErrorCode: "WF-CAPABILITY-FORBIDDEN", ErrorSummary: "permission denied"}})
				return
			}
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[model.HubCapabilityResult]{Code: "OK", Data: model.HubCapabilityResult{Success: true, Output: map[string]any{"items": []any{}}}})
		default:
			http.NotFound(response, request)
		}
	}))
	defer hubServer.Close()
	server := testServer()
	server.hub = hub.New(hubServer.URL, "internal-token", time.Second)
	server.audit = audit.New(server.hub, time.Second, slog.New(slog.NewTextHandler(io.Discard, nil)))
	principal := testPrincipal()
	request := httptest.NewRequest(http.MethodPost, "http://mcp.internal/mcp", nil)

	if result, _, _ := server.dispatch(context.Background(), request, principal, model.JSONRPCRequest{ID: json.RawMessage("1"), Method: "initialize", Params: json.RawMessage(`{}`)}, time.Now()); result.Error == nil {
		t.Fatal("initialize without protocol version must fail")
	}
	initialize := model.JSONRPCRequest{ID: json.RawMessage("2"), Method: "initialize", Params: json.RawMessage(`{"protocolVersion":"2025-11-25","clientInfo":{"name":"agent"}}`)}
	if result, _, _ := server.dispatch(context.Background(), request, principal, initialize, time.Now()); result.Error != nil || request.Header.Get(mcpSessionHeader) == "" {
		t.Fatalf("valid initialize must return a session: %#v", result)
	}
	if result, notification, status := server.dispatch(context.Background(), request, principal, model.JSONRPCRequest{Method: "notifications/initialized"}, time.Now()); !notification || status != "accepted" || result.Error != nil {
		t.Fatalf("initialized notification contract changed: %#v notification=%v status=%s", result, notification, status)
	}
	if result, _, status := server.dispatch(context.Background(), request, principal, model.JSONRPCRequest{ID: json.RawMessage("3"), Method: "unknown"}, time.Now()); result.Error == nil || status != "method_not_found" {
		t.Fatalf("unknown method must be a JSON-RPC method-not-found error: %#v", result)
	}
	if result := server.listTools(context.Background(), principal, model.JSONRPCRequest{ID: json.RawMessage("4"), Params: json.RawMessage(`{"cursor":"bad%"}`)}, time.Now()); result.Error == nil {
		t.Fatal("malformed tool cursor must fail without querying Hub")
	}

	listed := server.listTools(context.Background(), principal, model.JSONRPCRequest{ID: json.RawMessage("5"), Params: json.RawMessage(`{}`)}, time.Now())
	if listed.Error != nil || listed.Result.(model.ToolListResult).NextCursor == "" || len(listed.Result.(model.ToolListResult).Tools) != 25 {
		t.Fatalf("full tools page must stay cursor-resumable: %#v", listed)
	}
	_ = server.listTools(context.Background(), principal, model.JSONRPCRequest{ID: json.RawMessage("6"), Params: json.RawMessage(`{}`)}, time.Now())
	if toolRequests.Load() != 1 {
		t.Fatalf("filtered discovery cache should avoid duplicate Hub calls, got %d", toolRequests.Load())
	}

	if result := server.callTool(context.Background(), principal, "session", model.JSONRPCRequest{ID: json.RawMessage("7"), Params: json.RawMessage(`{"name":"cloudflow.admin.delete"}`)}, time.Now()); result.Error == nil {
		t.Fatal("unexported tool must fail before Hub invocation")
	}
	invokeFailure.Store(true)
	called := server.callTool(context.Background(), principal, "session", model.JSONRPCRequest{ID: json.RawMessage("8"), Params: json.RawMessage(`{"name":"cloudflow.file.list","arguments":{}}`)}, time.Now())
	if called.Error != nil || !called.Result.(model.ToolCallResult).IsError {
		t.Fatalf("Hub business failure must remain an MCP tool error result: %#v", called)
	}
}

func TestDispatchStaticMethodsAndDefensiveHelpers(t *testing.T) {
	server := testServer()
	principal := testPrincipal()
	request := httptest.NewRequest(http.MethodPost, "http://mcp.internal/mcp", nil)
	methods := []model.JSONRPCRequest{
		{ID: json.RawMessage("1"), Method: "ping"},
		{ID: json.RawMessage("2"), Method: "resources/list"},
		{ID: json.RawMessage("3"), Method: "resources/read", Params: json.RawMessage(`{"uri":"cloudflow://server/policy"}`)},
		{ID: json.RawMessage("4"), Method: "prompts/list"},
		{ID: json.RawMessage("5"), Method: "prompts/get", Params: json.RawMessage(`{"name":"safe-file-search","arguments":{"keyword":"x"}}`)},
		{Method: "notifications/cancelled", Params: json.RawMessage(`{"requestId":99}`)},
		{ID: json.RawMessage("6"), Method: "tools/call", Params: json.RawMessage(`{}`)},
	}
	for _, rpc := range methods {
		result, _, _ := server.dispatch(context.Background(), request, principal, rpc, time.Now())
		if rpc.Method == "tools/call" && result.Error == nil {
			t.Fatalf("invalid tools/call must fail: %#v", result)
		}
	}
	if err := server.requireSession(httptest.NewRecorder(), httptest.NewRequest(http.MethodPost, "http://mcp.internal/mcp", nil), principal); !err {
		t.Fatal("missing session must be rejected")
	}
	var decoded map[string]string
	if err := decodeParams(json.RawMessage("null"), &decoded); err != nil || decoded == nil {
		t.Fatalf("null params must decode as an empty object: %#v err=%v", decoded, err)
	}
	if sanitizeMetric("") != "unknown" || len(sanitizeMetric(strings.Repeat("x", 100))) != 64 {
		t.Fatal("metric labels must be bounded and non-empty")
	}
	if method, result := splitMetric("orphan"); method != "orphan" || result != "unknown" || max(-1, 0) != 0 {
		t.Fatal("metric helper fallback contract changed")
	}
	if len(truncate(strings.Repeat("x", 8), 3)) != 3 {
		t.Fatal("long log content must be bounded")
	}
}
