// Package model intentionally owns protocol DTOs only.  It must not mirror
// platform database entities: the Hub remains the sole authority for internal
// Capability data and resource access.
package model

import "encoding/json"

const ProtocolVersion = "2025-11-25"

type JSONRPCRequest struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id,omitempty"`
	Method  string          `json:"method"`
	Params  json.RawMessage `json:"params,omitempty"`
}

func (request JSONRPCRequest) IsNotification() bool {
	return len(request.ID) == 0 || string(request.ID) == "null"
}

type JSONRPCResponse struct {
	JSONRPC string          `json:"jsonrpc"`
	ID      json.RawMessage `json:"id"`
	Result  any             `json:"result,omitempty"`
	Error   *JSONRPCError   `json:"error,omitempty"`
}

type JSONRPCError struct {
	Code    int    `json:"code"`
	Message string `json:"message"`
	Data    any    `json:"data,omitempty"`
}

func Result(id json.RawMessage, result any) JSONRPCResponse {
	return JSONRPCResponse{JSONRPC: "2.0", ID: id, Result: result}
}

func Error(id json.RawMessage, code int, message string, data any) JSONRPCResponse {
	return JSONRPCResponse{JSONRPC: "2.0", ID: id, Error: &JSONRPCError{Code: code, Message: message, Data: data}}
}

type InitializeParams struct {
	ProtocolVersion string         `json:"protocolVersion"`
	Capabilities    map[string]any `json:"capabilities"`
	ClientInfo      ClientInfo     `json:"clientInfo"`
}

type ClientInfo struct {
	Name    string `json:"name"`
	Version string `json:"version"`
}

type ToolListParams struct {
	Cursor string `json:"cursor,omitempty"`
}

type ToolCallParams struct {
	Name      string         `json:"name"`
	Arguments map[string]any `json:"arguments"`
}

type Tool struct {
	Name         string         `json:"name"`
	Title        string         `json:"title,omitempty"`
	Description  string         `json:"description"`
	InputSchema  map[string]any `json:"inputSchema"`
	OutputSchema map[string]any `json:"outputSchema,omitempty"`
	Annotations  map[string]any `json:"annotations,omitempty"`
	Execution    map[string]any `json:"execution,omitempty"`
}

type ToolListResult struct {
	Tools      []Tool `json:"tools"`
	NextCursor string `json:"nextCursor,omitempty"`
}

type TextContent struct {
	Type string `json:"type"`
	Text string `json:"text"`
}

type ToolCallResult struct {
	Content           []TextContent  `json:"content"`
	StructuredContent map[string]any `json:"structuredContent,omitempty"`
	IsError           bool           `json:"isError,omitempty"`
}

type CapabilityRow struct {
	CapabilityKey           string `json:"capabilityKey"`
	SourceType              string `json:"sourceType"`
	SourceID                string `json:"sourceId"`
	SourceVersion           string `json:"sourceVersion"`
	DisplayName             string `json:"displayName"`
	Description             string `json:"description"`
	InputSchemaJSON         string `json:"inputSchemaJson"`
	OutputSchemaJSON        string `json:"outputSchemaJson"`
	RequiredPermissionsJSON string `json:"requiredPermissionsJson"`
	AvailabilityPolicyJSON  string `json:"availabilityPolicyJson"`
	Status                  string `json:"status"`
	Revision                int64  `json:"revision"`
}

type HubToolListRequest struct {
	UserID   string `json:"userId"`
	TenantID string `json:"tenantId,omitempty"`
	SpaceID  string `json:"spaceId,omitempty"`
	Offset   int    `json:"offset"`
	Limit    int    `json:"limit"`
}

type HubToolListResponse struct {
	Capabilities []CapabilityRow `json:"capabilities"`
	NextOffset   *int            `json:"nextOffset,omitempty"`
}

type HubInvocationRequest struct {
	CapabilityKey  string         `json:"capabilityKey"`
	UserID         string         `json:"userId"`
	TenantID       string         `json:"tenantId,omitempty"`
	SpaceID        string         `json:"spaceId,omitempty"`
	Input          map[string]any `json:"input"`
	TraceID        string         `json:"traceId"`
	IdempotencyKey string         `json:"idempotencyKey"`
	AgentID        string         `json:"agentId,omitempty"`
}

type HubCapabilityResult struct {
	Success      bool           `json:"success"`
	Output       map[string]any `json:"output"`
	ErrorCode    string         `json:"errorCode"`
	ErrorSummary string         `json:"errorSummary"`
	Retryable    bool           `json:"retryable"`
}

type HubAuditRequest struct {
	Method           string         `json:"method"`
	UserID           string         `json:"userId"`
	TenantID         string         `json:"tenantId,omitempty"`
	SpaceID          string         `json:"spaceId,omitempty"`
	TraceID          string         `json:"traceId"`
	AgentID          string         `json:"agentId,omitempty"`
	ParameterSummary map[string]any `json:"parameterSummary"`
	Success          bool           `json:"success"`
	ResultCode       string         `json:"resultCode"`
	DurationMS       int64          `json:"durationMs"`
}

type APIEnvelope[T any] struct {
	Code      string `json:"code"`
	Message   string `json:"message"`
	Data      T      `json:"data"`
	RequestID string `json:"requestId"`
}
