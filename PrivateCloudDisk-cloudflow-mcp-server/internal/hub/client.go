// Package hub is the only enterprise HTTP client in the MCP server.  Keeping
// the fixed Hub base URL here makes SSRF impossible and makes the architectural
// boundary mechanically auditable: no File/Workflow/Plugin client exists.
package hub

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"

	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

func New(baseURL, token string, timeout time.Duration) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		token:   token,
		http:    &http.Client{Timeout: timeout},
	}
}

func (client *Client) ListTools(
	ctx context.Context, request model.HubToolListRequest, requestID string,
) (model.HubToolListResponse, error) {
	var response model.HubToolListResponse
	if err := client.post(ctx, "/internal/v1/capabilities/mcp/tools", request, requestID, &response); err != nil {
		return model.HubToolListResponse{}, err
	}
	return response, nil
}

func (client *Client) Invoke(
	ctx context.Context, request model.HubInvocationRequest, requestID string,
) (model.HubCapabilityResult, error) {
	var response model.HubCapabilityResult
	if err := client.post(ctx, "/internal/v1/capabilities/mcp/invoke", request, requestID, &response); err != nil {
		return model.HubCapabilityResult{}, err
	}
	return response, nil
}

// Audit is intentionally best effort at the transport level only.  The Hub
// persists tool-execution audit synchronously in invokeMcp; protocol-only
// discovery/prompt/resource audit is retried here but can never reveal result
// data or cause an already completed tool to be re-executed.
func (client *Client) Audit(
	ctx context.Context, request model.HubAuditRequest, requestID string,
) error {
	var ignored map[string]any
	return client.post(ctx, "/internal/v1/capabilities/mcp/audit", request, requestID, &ignored)
}

func (client *Client) post(ctx context.Context, path string, payload any, requestID string, target any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("encode hub request: %w", err)
	}
	request, err := http.NewRequestWithContext(ctx, http.MethodPost, client.baseURL+path, bytes.NewReader(body))
	if err != nil {
		return fmt.Errorf("create hub request: %w", err)
	}
	request.Header.Set("Content-Type", "application/json")
	request.Header.Set("Accept", "application/json")
	request.Header.Set("X-PCD-Service-Token", client.token)
	request.Header.Set("X-Request-Id", requestID)
	// Carry only standard W3C tracing metadata.  User identity remains in the
	// signed, schema-validated request body and never becomes a propagation
	// baggage field.
	otel.GetTextMapPropagator().Inject(ctx, propagation.HeaderCarrier(request.Header))
	response, err := client.http.Do(request)
	if err != nil {
		return fmt.Errorf("capability hub unavailable: %w", err)
	}
	defer response.Body.Close()
	limited := io.LimitReader(response.Body, 2*1024*1024)
	bytes, err := io.ReadAll(limited)
	if err != nil {
		return fmt.Errorf("read hub response: %w", err)
	}
	if response.StatusCode < http.StatusOK || response.StatusCode >= http.StatusMultipleChoices {
		return &StatusError{Status: response.StatusCode, Code: safeEnvelopeCode(bytes)}
	}
	var envelope model.APIEnvelope[json.RawMessage]
	if err := json.Unmarshal(bytes, &envelope); err != nil {
		return fmt.Errorf("decode hub response: %w", err)
	}
	if envelope.Code != "" && envelope.Code != "OK" {
		return &StatusError{Status: response.StatusCode, Code: envelope.Code}
	}
	if len(envelope.Data) == 0 || string(envelope.Data) == "null" {
		return errors.New("capability hub returned an empty response")
	}
	if err := json.Unmarshal(envelope.Data, target); err != nil {
		return fmt.Errorf("decode hub data: %w", err)
	}
	return nil
}

type StatusError struct {
	Status int
	Code   string
}

func (error *StatusError) Error() string {
	if error.Code == "" {
		return fmt.Sprintf("capability hub returned HTTP %d", error.Status)
	}
	return fmt.Sprintf("capability hub returned HTTP %d (%s)", error.Status, error.Code)
}

func safeEnvelopeCode(body []byte) string {
	var value struct {
		Code string `json:"code"`
	}
	_ = json.Unmarshal(body, &value)
	if len(value.Code) > 64 {
		return ""
	}
	return value.Code
}
