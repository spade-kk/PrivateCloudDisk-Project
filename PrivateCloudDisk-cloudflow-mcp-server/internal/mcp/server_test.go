package mcp

import (
	"bytes"
	"encoding/json"
	"io"
	"log/slog"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"

	"privateclouddisk/cloudflow-mcp-server/internal/config"
	"privateclouddisk/cloudflow-mcp-server/internal/identity"
	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

const testSecret = "0123456789abcdef0123456789abcdef"
const testUser = "00000000-0000-0000-0000-000000000001"
const testSpace = "00000000-0000-0000-0000-000000000002"

func TestMCPInitializeListAndCallStayWithinHubBoundary(t *testing.T) {
	var state struct {
		sync.Mutex
		invocation model.HubInvocationRequest
		audits     int
	}
	hubServer := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Header.Get("X-PCD-Service-Token") != "internal-token" {
			http.Error(response, "bad service token", http.StatusUnauthorized)
			return
		}
		response.Header().Set("Content-Type", "application/json")
		switch request.URL.Path {
		case "/internal/v1/capabilities/mcp/tools":
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[model.HubToolListResponse]{Code: "OK", Data: model.HubToolListResponse{Capabilities: []model.CapabilityRow{{
				CapabilityKey: "api:file.list", Status: "ACTIVE", Description: "List files", InputSchemaJSON: `{"type":"object","required":["space_id","keyword"],"properties":{"space_id":{"type":"string"},"keyword":{"type":"string"}}}`,
			}}}})
		case "/internal/v1/capabilities/mcp/invoke":
			var invocation model.HubInvocationRequest
			_ = json.NewDecoder(request.Body).Decode(&invocation)
			state.Lock()
			state.invocation = invocation
			state.Unlock()
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[model.HubCapabilityResult]{Code: "OK", Data: model.HubCapabilityResult{Success: true, Output: map[string]any{"items": []any{"roadmap.md"}}}})
		case "/internal/v1/capabilities/mcp/audit":
			state.Lock()
			state.audits++
			state.Unlock()
			_ = json.NewEncoder(response).Encode(model.APIEnvelope[map[string]any]{Code: "OK", Data: map[string]any{"accepted": true}})
		default:
			http.NotFound(response, request)
		}
	}))
	defer hubServer.Close()
	cfg := config.Config{
		CapabilityHubURL: hubServer.URL, InternalServiceToken: "internal-token", IdentitySharedSecret: testSecret,
		IdentityMaxAge: 2 * time.Minute, RequestTimeout: 2 * time.Second, AuditTimeout: time.Second,
		ToolListCacheTTL: time.Minute, SessionTTL: time.Minute, MaxBodyBytes: 1024 * 1024,
		MaxConcurrentRequests: 8, RequestsPerMinutePerUser: 50, MetricsEnabled: true, Version: "test",
	}
	server := httptest.NewServer(New(cfg, slog.New(slog.NewTextHandler(io.Discard, nil))).Handler())
	defer server.Close()

	initialize := performRPC(t, server.URL, "init-1", "", map[string]any{
		"jsonrpc": "2.0", "id": 1, "method": "initialize",
		"params": map[string]any{"protocolVersion": model.ProtocolVersion, "capabilities": map[string]any{}, "clientInfo": map[string]any{"name": "test-agent", "version": "1"}},
	})
	sessionID := initialize.Header.Get(mcpSessionHeader)
	if sessionID == "" {
		t.Fatal("initialize did not issue Mcp-Session-Id")
	}

	list := performRPC(t, server.URL, "list-1", sessionID, map[string]any{"jsonrpc": "2.0", "id": 2, "method": "tools/list", "params": map[string]any{}})
	var listResult struct {
		Result model.ToolListResult `json:"result"`
	}
	decodeBody(t, list, &listResult)
	if len(listResult.Result.Tools) != 1 || listResult.Result.Tools[0].Name != "cloudflow.file.list" {
		t.Fatalf("unexpected tools: %#v", listResult.Result.Tools)
	}

	call := performRPC(t, server.URL, "call-1", sessionID, map[string]any{
		"jsonrpc": "2.0", "id": 3, "method": "tools/call", "params": map[string]any{
			"name": "cloudflow.file.list", "arguments": map[string]any{"keyword": "roadmap", "space_id": "forged", "tenant_id": "forged"},
		},
	})
	var callResult struct {
		Result model.ToolCallResult `json:"result"`
	}
	decodeBody(t, call, &callResult)
	if callResult.Result.IsError || len(callResult.Result.Content) != 1 {
		t.Fatalf("unexpected call result: %#v", callResult)
	}
	state.Lock()
	invocation := state.invocation
	audits := state.audits
	state.Unlock()
	if invocation.Input["space_id"] != nil || invocation.Input["tenant_id"] != nil {
		t.Fatalf("MCP server must strip forged context: %#v", invocation.Input)
	}
	if invocation.Input["keyword"] != "roadmap" || invocation.SpaceID != testSpace || invocation.UserID != testUser {
		t.Fatalf("trusted context was not propagated correctly: %#v", invocation)
	}
	if audits < 2 {
		t.Fatalf("initialize and tools/list should be protocol-audited, got %d", audits)
	}
}

func TestMCPRejectsUnsignedRequests(t *testing.T) {
	cfg := config.Config{CapabilityHubURL: "http://127.0.0.1:1", InternalServiceToken: "internal-token", IdentitySharedSecret: testSecret, IdentityMaxAge: time.Minute, RequestTimeout: time.Second, AuditTimeout: time.Second, ToolListCacheTTL: time.Minute, SessionTTL: time.Minute, MaxBodyBytes: 1024, MaxConcurrentRequests: 1, RequestsPerMinutePerUser: 1, Version: "test"}
	server := httptest.NewServer(New(cfg, slog.New(slog.NewTextHandler(io.Discard, nil))).Handler())
	defer server.Close()
	request, _ := http.NewRequest(http.MethodPost, server.URL+"/mcp", bytes.NewBufferString(`{"jsonrpc":"2.0","id":1,"method":"initialize"}`))
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json, text/event-stream")
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	defer response.Body.Close()
	challenge := response.Header.Get("WWW-Authenticate")
	if response.StatusCode != http.StatusUnauthorized || challenge == "" {
		t.Fatalf("unsigned request should receive OAuth discovery challenge, got %d", response.StatusCode)
	}
	if !strings.Contains(challenge, "/api/v1/.well-known/oauth-protected-resource/mcp") {
		t.Fatalf("challenge must point to the public Gateway metadata path, got %q", challenge)
	}
}

func TestMCPJSONRPCResponseUsesSingleValidSSEFrame(t *testing.T) {
	cfg := config.Config{CapabilityHubURL: "http://127.0.0.1:1", InternalServiceToken: "internal-token", IdentitySharedSecret: testSecret, IdentityMaxAge: time.Minute, RequestTimeout: time.Second, AuditTimeout: time.Second, ToolListCacheTTL: time.Minute, SessionTTL: time.Minute, MaxBodyBytes: 1024, MaxConcurrentRequests: 1, RequestsPerMinutePerUser: 10, Version: "test"}
	server := New(cfg, slog.New(slog.NewTextHandler(io.Discard, nil)))
	request := httptest.NewRequest(http.MethodPost, "http://mcp.internal/mcp", nil)
	request.Header.Set(mcpSessionHeader, "session-1")
	request.Header.Set("Accept", "text/event-stream, application/json")
	recorder := httptest.NewRecorder()
	server.writeRPC(recorder, request, model.Result(json.RawMessage("1"), map[string]string{"ok": "true"}), true)
	body := recorder.Body.String()
	if !strings.Contains(body, "event: message\ndata: {") || strings.Contains(body, "data:\n\nevent:") {
		t.Fatalf("response must be one complete SSE message event, got %q", body)
	}
}

func performRPC(t *testing.T, baseURL, requestID, sessionID string, payload map[string]any) *http.Response {
	t.Helper()
	body, _ := json.Marshal(payload)
	request, err := http.NewRequest(http.MethodPost, baseURL+"/mcp", bytes.NewReader(body))
	if err != nil {
		t.Fatal(err)
	}
	now := time.Now().UTC()
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json, text/event-stream")
	request.Header.Set(identity.HeaderUserID, testUser)
	request.Header.Set(identity.HeaderSpaceID, testSpace)
	request.Header.Set(identity.HeaderRequestID, requestID)
	request.Header.Set(identity.HeaderTimestamp, identity.CanonicalTimestamp(now))
	request.Header.Set(identity.HeaderSignature, identity.SignForGateway(testSecret, http.MethodPost, "/mcp", requestID, identity.CanonicalTimestamp(now), testUser, "", testSpace))
	if sessionID != "" {
		request.Header.Set(mcpSessionHeader, sessionID)
	}
	response, err := http.DefaultClient.Do(request)
	if err != nil {
		t.Fatal(err)
	}
	if response.StatusCode != http.StatusOK {
		response.Body.Close()
		t.Fatalf("unexpected HTTP status %d", response.StatusCode)
	}
	return response
}

func decodeBody(t *testing.T, response *http.Response, target any) {
	t.Helper()
	defer response.Body.Close()
	if err := json.NewDecoder(response.Body).Decode(target); err != nil {
		t.Fatal(err)
	}
}
