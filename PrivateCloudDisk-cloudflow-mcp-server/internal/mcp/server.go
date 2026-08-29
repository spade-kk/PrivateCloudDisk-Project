package mcp

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"

	"privateclouddisk/cloudflow-mcp-server/internal/adapter"
	"privateclouddisk/cloudflow-mcp-server/internal/audit"
	"privateclouddisk/cloudflow-mcp-server/internal/cache"
	"privateclouddisk/cloudflow-mcp-server/internal/config"
	"privateclouddisk/cloudflow-mcp-server/internal/hub"
	"privateclouddisk/cloudflow-mcp-server/internal/identity"
	"privateclouddisk/cloudflow-mcp-server/internal/model"
	"privateclouddisk/cloudflow-mcp-server/internal/ratelimit"
)

const (
	mcpSessionHeader = "Mcp-Session-Id"
	maxToolPageSize  = 25
	// Scan size is deliberately equal to the public page size.  It prevents an
	// export-policy filtered page from skipping valid tools that appear after a
	// truncated internal candidate batch.
	hubScanPageSize = 25
)

type Server struct {
	cfg           config.Config
	hub           *hub.Client
	verify        *identity.Verifier
	audit         *audit.Recorder
	sessions      *sessions
	cache         *cache.TTLCache[model.HubToolListResponse]
	limiter       *ratelimit.FixedWindow
	metrics       *Metrics
	logger        *slog.Logger
	sem           chan struct{}
	cancellations sync.Map
}

func New(cfg config.Config, logger *slog.Logger) *Server {
	hubClient := hub.New(cfg.CapabilityHubURL, cfg.InternalServiceToken, cfg.RequestTimeout)
	return &Server{
		cfg: cfg, hub: hubClient, verify: identity.NewVerifier(cfg.IdentitySharedSecret, cfg.IdentityMaxAge),
		audit: audit.New(hubClient, cfg.AuditTimeout, logger), sessions: newSessions(cfg.SessionTTL),
		cache: cache.New[model.HubToolListResponse](), limiter: ratelimit.New(cfg.RequestsPerMinutePerUser, time.Minute),
		metrics: NewMetrics(), logger: logger, sem: make(chan struct{}, cfg.MaxConcurrentRequests),
	}
}

func (server *Server) Handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /health/live", server.live)
	mux.HandleFunc("GET /health/ready", server.ready)
	if server.cfg.MetricsEnabled {
		mux.Handle("GET /metrics", server.metrics)
	}
	mux.HandleFunc("GET /.well-known/oauth-protected-resource/mcp", server.protectedResourceMetadata)
	mux.HandleFunc("GET /mcp", server.mcp)
	mux.HandleFunc("POST /mcp", server.mcp)
	return server.securityHeaders(mux)
}

func (server *Server) live(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]string{"status": "UP"})
}

func (server *Server) ready(response http.ResponseWriter, _ *http.Request) {
	// Hub is intentionally not synchronously probed: a transient downstream call
	// must not make a healthy adapter pod disappear from load balancing. Tool
	// calls still surface the Hub outage as a stable, retryable MCP failure.
	writeJSON(response, http.StatusOK, map[string]string{"status": "UP"})
}

func (server *Server) protectedResourceMetadata(response http.ResponseWriter, request *http.Request) {
	base := publicBaseURL(request)
	writeJSON(response, http.StatusOK, map[string]any{
		"resource":                 base + "/api/v1/mcp",
		"authorization_servers":    server.cfg.OAuthAuthorizationServers,
		"bearer_methods_supported": []string{"header"},
		"resource_documentation":   base + "/docs/cloudflow-mcp",
	})
}

func (server *Server) mcp(response http.ResponseWriter, request *http.Request) {
	traceContext, span := otel.Tracer("cloudflow-mcp-server").Start(request.Context(), "mcp.request")
	defer span.End()
	request = request.WithContext(traceContext)
	span.SetAttributes(
		attribute.String("http.request.method", request.Method),
		attribute.String("url.path", request.URL.Path),
	)
	started := time.Now()
	done := server.metrics.Begin()
	methodLabel, resultLabel := "transport", "error"
	defer func() { done(methodLabel, resultLabel, time.Since(started).Milliseconds()) }()
	if !server.tryAcquire(response) {
		resultLabel = "capacity"
		return
	}
	defer func() { <-server.sem }()

	principal, err := server.verify.Verify(request)
	if err != nil {
		server.unauthorized(response, request)
		server.logger.Warn("MCP request rejected before identity", "path", request.URL.Path, "error", err)
		resultLabel = "unauthorized"
		return
	}
	if !server.limiter.Allow(principal.UserID, time.Now()) {
		server.writeHTTPError(response, http.StatusTooManyRequests, "MCP request rate limit exceeded")
		server.audit.Record(principal, "transport.rate_limited", map[string]any{"path": request.URL.Path}, false, "MCP-RATE-LIMIT", time.Since(started))
		resultLabel = "rate_limited"
		return
	}
	if request.Method == http.MethodGet {
		span.SetAttributes(attribute.String("mcp.transport", "sse"))
		methodLabel, resultLabel = server.serveSSE(response, request, principal, started)
		return
	}
	methodLabel, resultLabel = server.servePost(response, request, principal, started)
	span.SetAttributes(attribute.String("mcp.method", methodLabel), attribute.String("mcp.result", resultLabel))
}

func (server *Server) servePost(response http.ResponseWriter, request *http.Request, principal identity.Identity, started time.Time) (string, string) {
	if !acceptsMCPResponse(request.Header.Get("Accept")) {
		server.writeHTTPError(response, http.StatusNotAcceptable, "Accept must include application/json and text/event-stream")
		server.audit.Record(principal, "transport.invalid_accept", map[string]any{}, false, "MCP-ACCEPT", time.Since(started))
		return "transport", "invalid_accept"
	}
	if !strings.HasPrefix(strings.ToLower(request.Header.Get("Content-Type")), "application/json") {
		server.writeHTTPError(response, http.StatusUnsupportedMediaType, "Content-Type must be application/json")
		return "transport", "invalid_content_type"
	}
	request.Body = http.MaxBytesReader(response, request.Body, server.cfg.MaxBodyBytes)
	body, err := io.ReadAll(request.Body)
	if err != nil {
		server.writeHTTPError(response, http.StatusRequestEntityTooLarge, "MCP request body is too large")
		return "transport", "body_too_large"
	}
	var rpc model.JSONRPCRequest
	if err := json.Unmarshal(body, &rpc); err != nil || rpc.JSONRPC != "2.0" || strings.TrimSpace(rpc.Method) == "" {
		server.writeRPC(response, request, model.Error(json.RawMessage("null"), -32600, "Invalid Request", nil), false)
		server.audit.Record(principal, "rpc.invalid", map[string]any{}, false, "MCP-INVALID-REQUEST", time.Since(started))
		return "invalid", "invalid_request"
	}
	method := rpc.Method
	if method != "initialize" && server.requireSession(response, request, principal) {
		return method, "invalid_session"
	}
	result, notification, status := server.dispatch(request.Context(), request, principal, rpc, started)
	if notification {
		response.WriteHeader(http.StatusAccepted)
		return method, "accepted"
	}
	server.writeRPC(response, request, result, prefersSSE(request.Header.Get("Accept")))
	if result.Error != nil {
		return method, "rpc_error"
	}
	return method, status
}

func (server *Server) dispatch(ctx context.Context, request *http.Request, principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) (model.JSONRPCResponse, bool, string) {
	switch rpc.Method {
	case "initialize":
		return server.initialize(request, principal, rpc, started), false, "ok"
	case "notifications/initialized":
		server.audit.Record(principal, rpc.Method, map[string]any{}, true, "OK", time.Since(started))
		return model.JSONRPCResponse{}, true, "accepted"
	case "notifications/cancelled":
		server.cancel(request, rpc)
		server.audit.Record(principal, rpc.Method, map[string]any{}, true, "OK", time.Since(started))
		return model.JSONRPCResponse{}, true, "accepted"
	case "ping":
		server.audit.Record(principal, rpc.Method, map[string]any{}, true, "OK", time.Since(started))
		return model.Result(rpc.ID, map[string]any{}), false, "ok"
	case "tools/list":
		return server.listTools(ctx, principal, rpc, started), false, "ok"
	case "tools/call":
		return server.callTool(ctx, principal, request.Header.Get(mcpSessionHeader), rpc, started), false, "ok"
	case "resources/list":
		return server.listResources(principal, rpc, started), false, "ok"
	case "resources/read":
		return server.readResource(principal, rpc, started), false, "ok"
	case "prompts/list":
		return server.listPrompts(principal, rpc, started), false, "ok"
	case "prompts/get":
		return server.getPrompt(principal, rpc, started), false, "ok"
	default:
		server.audit.Record(principal, rpc.Method, map[string]any{}, false, "MCP-METHOD-NOT-FOUND", time.Since(started))
		return model.Error(rpc.ID, -32601, "Method not found", nil), false, "method_not_found"
	}
}

func (server *Server) initialize(request *http.Request, principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	var params model.InitializeParams
	if err := decodeParams(rpc.Params, &params); err != nil || params.ProtocolVersion == "" {
		server.audit.Record(principal, "initialize", map[string]any{}, false, "MCP-INVALID-PARAMS", time.Since(started))
		return model.Error(rpc.ID, -32602, "Invalid initialize parameters", nil)
	}
	if params.ProtocolVersion != model.ProtocolVersion {
		server.audit.Record(principal, "initialize", map[string]any{"protocolVersion": params.ProtocolVersion}, false, "MCP-PROTOCOL-UNSUPPORTED", time.Since(started))
		return model.Error(rpc.ID, -32602, "Unsupported protocol version", map[string]string{"supported": model.ProtocolVersion})
	}
	sessionID, err := server.sessions.Create(principal, time.Now())
	if err != nil {
		return model.Error(rpc.ID, -32603, "Unable to create MCP session", nil)
	}
	request.Header.Set(mcpSessionHeader, sessionID) // response writer receives this in writeRPC via context header helper below.
	server.audit.Record(principal, "initialize", map[string]any{"client": truncate(params.ClientInfo.Name, 64)}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, map[string]any{
		"protocolVersion": model.ProtocolVersion,
		"capabilities": map[string]any{
			"tools":     map[string]any{"listChanged": true},
			"resources": map[string]any{"listChanged": false},
			"prompts":   map[string]any{"listChanged": false},
		},
		"serverInfo":   map[string]string{"name": "cloudflow-mcp-server", "version": server.cfg.Version},
		"instructions": "CloudFlow MCP tools operate only on the authenticated user's authorized tenant and space. Do not provide identity, tenant, or permission arguments.",
	})
}

func (server *Server) listTools(ctx context.Context, principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	var params model.ToolListParams
	if err := decodeParams(rpc.Params, &params); err != nil {
		return model.Error(rpc.ID, -32602, "Invalid tools/list parameters", nil)
	}
	offset, err := decodeCursor(params.Cursor)
	if err != nil {
		return model.Error(rpc.ID, -32602, "Invalid tools/list cursor", nil)
	}
	tools, next, err := server.visibleTools(ctx, principal, offset)
	if err != nil {
		server.audit.Record(principal, "tools/list", map[string]any{"offset": offset}, false, "MCP-HUB-UNAVAILABLE", time.Since(started))
		return model.Error(rpc.ID, -32603, "Tool discovery is temporarily unavailable", nil)
	}
	result := model.ToolListResult{Tools: tools}
	if next != nil {
		result.NextCursor = encodeCursor(*next)
	}
	server.audit.Record(principal, "tools/list", map[string]any{"offset": offset, "returned": len(tools)}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, result)
}

func (server *Server) callTool(ctx context.Context, principal identity.Identity, sessionID string, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	var params model.ToolCallParams
	if err := decodeParams(rpc.Params, &params); err != nil || strings.TrimSpace(params.Name) == "" {
		return model.Error(rpc.ID, -32602, "Invalid tools/call parameters", nil)
	}
	binding, ok := adapter.BindingForTool(params.Name)
	if !ok {
		return model.Error(rpc.ID, -32602, "Unknown or unavailable tool", nil)
	}
	key := cancellationKey(principal, sessionID, rpc.ID)
	callContext, cancel := context.WithTimeout(ctx, server.cfg.RequestTimeout)
	server.cancellations.Store(key, cancel)
	defer func() { cancel(); server.cancellations.Delete(key) }()
	arguments := adapter.SanitizeArguments(params.Arguments)
	idempotencyKey := idempotencyKey(principal, sessionID, rpc.ID, binding.CapabilityKey)
	result, err := server.hub.Invoke(callContext, model.HubInvocationRequest{
		CapabilityKey: binding.CapabilityKey, UserID: principal.UserID, TenantID: principal.TenantID, SpaceID: principal.SpaceID,
		Input: arguments, TraceID: principal.RequestID, IdempotencyKey: idempotencyKey, AgentID: principal.AgentID,
	}, principal.RequestID)
	if err != nil {
		return model.Error(rpc.ID, -32603, "Tool execution is temporarily unavailable", nil)
	}
	if !result.Success {
		return model.Result(rpc.ID, model.ToolCallResult{
			Content: []model.TextContent{{Type: "text", Text: safeToolError(result.ErrorCode, result.ErrorSummary)}},
			IsError: true,
		})
	}
	encoded, err := json.Marshal(result.Output)
	if err != nil {
		return model.Error(rpc.ID, -32603, "Tool result cannot be serialized", nil)
	}
	if len(encoded) > 512*1024 {
		return model.Error(rpc.ID, -32603, "Tool result exceeds MCP response limit", nil)
	}
	return model.Result(rpc.ID, model.ToolCallResult{
		Content: []model.TextContent{{Type: "text", Text: string(encoded)}}, StructuredContent: result.Output,
	})
}

func (server *Server) visibleTools(ctx context.Context, principal identity.Identity, offset int) ([]model.Tool, *int, error) {
	tools := make([]model.Tool, 0, maxToolPageSize)
	nextOffset := offset
	for scans := 0; scans < 20 && len(tools) < maxToolPageSize; scans++ {
		page, err := server.fetchToolPage(ctx, principal, nextOffset)
		if err != nil {
			return nil, nil, err
		}
		tools = append(tools, adapter.ToTools(page.Capabilities)...)
		if len(tools) >= maxToolPageSize {
			tools = tools[:maxToolPageSize]
			if page.NextOffset != nil {
				return tools, page.NextOffset, nil
			}
			return tools, nil, nil
		}
		if page.NextOffset == nil {
			return tools, nil, nil
		}
		nextOffset = *page.NextOffset
	}
	// A malformed registry with thousands of non-exportable entries must not
	// make a single discovery request unbounded.  Cursor remains resumable.
	return tools, &nextOffset, nil
}

func (server *Server) fetchToolPage(ctx context.Context, principal identity.Identity, offset int) (model.HubToolListResponse, error) {
	cacheKey := fmt.Sprintf("%s|%s|%s|%d", principal.UserID, principal.TenantID, principal.SpaceID, offset)
	if cached, ok := server.cache.Get(cacheKey, time.Now()); ok {
		return cached, nil
	}
	page, err := server.hub.ListTools(ctx, model.HubToolListRequest{
		UserID: principal.UserID, TenantID: principal.TenantID, SpaceID: principal.SpaceID, Offset: offset, Limit: hubScanPageSize,
	}, principal.RequestID)
	if err == nil {
		server.cache.Put(cacheKey, page, server.cfg.ToolListCacheTTL, time.Now())
	}
	return page, err
}

func (server *Server) listResources(principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	server.audit.Record(principal, rpc.Method, map[string]any{}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, map[string]any{"resources": []map[string]any{
		{"uri": "cloudflow://server/policy", "name": "CloudFlow MCP policy", "mimeType": "text/markdown", "description": "Security and capability-export policy."},
	}})
}

func (server *Server) readResource(principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	var params struct {
		URI string `json:"uri"`
	}
	if err := decodeParams(rpc.Params, &params); err != nil || params.URI != "cloudflow://server/policy" {
		return model.Error(rpc.ID, -32602, "Unknown resource", nil)
	}
	server.audit.Record(principal, rpc.Method, map[string]any{"uri": params.URI}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, map[string]any{"contents": []map[string]string{{
		"uri": params.URI, "mimeType": "text/markdown", "text": "# CloudFlow MCP policy\n\nTools are filtered per authenticated user, tenant and space. Identity and permission parameters are server-managed and must never be supplied by an Agent.",
	}}})
}

func (server *Server) listPrompts(principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	server.audit.Record(principal, rpc.Method, map[string]any{}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, map[string]any{"prompts": []map[string]any{{
		"name": "safe-file-search", "title": "安全文件搜索", "description": "在当前授权空间内搜索文件的安全提示模板。",
		"arguments": []map[string]any{{"name": "keyword", "description": "搜索关键词", "required": true}},
	}}})
}

func (server *Server) getPrompt(principal identity.Identity, rpc model.JSONRPCRequest, started time.Time) model.JSONRPCResponse {
	var params struct {
		Name      string            `json:"name"`
		Arguments map[string]string `json:"arguments"`
	}
	if err := decodeParams(rpc.Params, &params); err != nil || params.Name != "safe-file-search" || strings.TrimSpace(params.Arguments["keyword"]) == "" {
		return model.Error(rpc.ID, -32602, "Unknown prompt or invalid arguments", nil)
	}
	server.audit.Record(principal, rpc.Method, map[string]any{"name": params.Name}, true, "OK", time.Since(started))
	return model.Result(rpc.ID, map[string]any{"description": "Search current authorized space only", "messages": []map[string]any{{
		"role": "user", "content": map[string]string{"type": "text", "text": "Search my currently authorized CloudFlow space for: " + params.Arguments["keyword"]},
	}}})
}

func (server *Server) cancel(request *http.Request, rpc model.JSONRPCRequest) {
	var params struct {
		RequestID json.RawMessage `json:"requestId"`
	}
	if decodeParams(rpc.Params, &params) != nil || len(params.RequestID) == 0 {
		return
	}
	principal, err := server.verify.Verify(request)
	if err != nil {
		return
	}
	key := cancellationKey(principal, request.Header.Get(mcpSessionHeader), params.RequestID)
	if cancel, ok := server.cancellations.Load(key); ok {
		cancel.(context.CancelFunc)()
	}
}

func (server *Server) serveSSE(response http.ResponseWriter, request *http.Request, principal identity.Identity, started time.Time) (string, string) {
	if request.Header.Get(mcpSessionHeader) == "" || server.requireSession(response, request, principal) {
		return "sse", "invalid_session"
	}
	flusher, ok := response.(http.Flusher)
	if !ok {
		server.writeHTTPError(response, http.StatusInternalServerError, "SSE is unavailable")
		return "sse", "unavailable"
	}
	response.Header().Set("Content-Type", "text/event-stream")
	response.Header().Set("Cache-Control", "no-cache, no-transform")
	response.Header().Set("Connection", "keep-alive")
	_, _ = fmt.Fprintf(response, "id: %s\ndata:\n\n", principal.RequestID)
	flusher.Flush()
	server.audit.Record(principal, "transport.sse", map[string]any{}, true, "OK", time.Since(started))
	ticker := time.NewTicker(15 * time.Second)
	defer ticker.Stop()
	for {
		select {
		case <-request.Context().Done():
			return "sse", "closed"
		case <-ticker.C:
			_, _ = fmt.Fprint(response, ": keepalive\n\n")
			flusher.Flush()
		}
	}
}

func (server *Server) requireSession(response http.ResponseWriter, request *http.Request, principal identity.Identity) bool {
	if err := server.sessions.Require(request.Header.Get(mcpSessionHeader), principal, time.Now()); err != nil {
		server.writeHTTPError(response, http.StatusBadRequest, "MCP session is missing, expired, or does not match this identity")
		return true
	}
	return false
}

func (server *Server) writeRPC(response http.ResponseWriter, request *http.Request, rpc model.JSONRPCResponse, sse bool) {
	if request.Header.Get(mcpSessionHeader) != "" {
		response.Header().Set(mcpSessionHeader, request.Header.Get(mcpSessionHeader))
	}
	if sse {
		encoded, _ := json.Marshal(rpc)
		response.Header().Set("Content-Type", "text/event-stream")
		response.Header().Set("Cache-Control", "no-cache, no-transform")
		// Keep one SSE event frame intact. A blank line terminates an event, so
		// emitting it between id/data and event/data would silently produce an
		// empty event in strict MCP clients.
		_, _ = fmt.Fprintf(response, "id: response-%d\nevent: message\ndata: %s\n\n", time.Now().UnixNano(), encoded)
		if flusher, ok := response.(http.Flusher); ok {
			flusher.Flush()
		}
		return
	}
	writeJSON(response, http.StatusOK, rpc)
}

func (server *Server) unauthorized(response http.ResponseWriter, request *http.Request) {
	base := publicBaseURL(request)
	// This is the public Gateway route. The server's internal well-known route
	// is deliberately not exposed directly to Agents.
	metadata := base + "/api/v1/.well-known/oauth-protected-resource/mcp"
	response.Header().Set("WWW-Authenticate", `Bearer realm="cloudflow-mcp", resource_metadata="`+metadata+`"`)
	server.writeHTTPError(response, http.StatusUnauthorized, "MCP authentication is required")
}

func (server *Server) tryAcquire(response http.ResponseWriter) bool {
	select {
	case server.sem <- struct{}{}:
		return true
	default:
		server.writeHTTPError(response, http.StatusServiceUnavailable, "MCP server is at capacity")
		return false
	}
}

func (server *Server) writeHTTPError(response http.ResponseWriter, status int, message string) {
	response.Header().Set("Cache-Control", "no-store")
	writeJSON(response, status, map[string]string{"error": message})
}

func (server *Server) securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set("X-Content-Type-Options", "nosniff")
		response.Header().Set("X-Frame-Options", "DENY")
		response.Header().Set("Referrer-Policy", "no-referrer")
		next.ServeHTTP(response, request)
	})
}

func writeJSON(response http.ResponseWriter, status int, value any) {
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.WriteHeader(status)
	_ = json.NewEncoder(response).Encode(value)
}

func decodeParams(raw json.RawMessage, target any) error {
	if len(raw) == 0 || string(raw) == "null" {
		raw = []byte("{}")
	}
	return json.Unmarshal(raw, target)
}

func acceptsMCPResponse(header string) bool {
	value := strings.ToLower(header)
	return strings.Contains(value, "application/json") && strings.Contains(value, "text/event-stream")
}

func prefersSSE(header string) bool {
	items := strings.Split(strings.ToLower(header), ",")
	for _, item := range items {
		if strings.TrimSpace(strings.Split(item, ";")[0]) == "text/event-stream" {
			return true
		}
		if strings.TrimSpace(strings.Split(item, ";")[0]) == "application/json" {
			return false
		}
	}
	return false
}

func publicBaseURL(request *http.Request) string {
	scheme := request.Header.Get("X-Forwarded-Proto")
	if scheme == "" {
		scheme = "https"
	}
	host := request.Header.Get("X-Forwarded-Host")
	if host == "" {
		host = request.Host
	}
	return scheme + "://" + host
}

func encodeCursor(offset int) string {
	return base64.RawURLEncoding.EncodeToString([]byte(strconv.Itoa(offset)))
}

func decodeCursor(value string) (int, error) {
	if value == "" {
		return 0, nil
	}
	decoded, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil {
		return 0, err
	}
	offset, err := strconv.Atoi(string(decoded))
	if err != nil || offset < 0 || offset > 1_000_000 {
		return 0, errors.New("invalid cursor")
	}
	return offset, nil
}

func cancellationKey(principal identity.Identity, sessionID string, requestID json.RawMessage) string {
	return principal.UserID + "|" + principal.TenantID + "|" + principal.SpaceID + "|" + sessionID + "|" + string(requestID)
}

func idempotencyKey(principal identity.Identity, sessionID string, requestID json.RawMessage, capability string) string {
	source := cancellationKey(principal, sessionID, requestID) + "|" + capability
	sum := sha256.Sum256([]byte(source))
	return fmt.Sprintf("mcp-%x", sum[:])
}

func safeToolError(code, summary string) string {
	if code == "" {
		code = "CLOUDFLOW_TOOL_FAILED"
	}
	if summary == "" {
		summary = "The tool could not complete the requested operation."
	}
	return truncate(code, 64) + ": " + truncate(summary, 500)
}

func truncate(value string, limit int) string {
	value = strings.ReplaceAll(strings.ReplaceAll(value, "\r", " "), "\n", " ")
	if len(value) > limit {
		return value[:limit]
	}
	return value
}
