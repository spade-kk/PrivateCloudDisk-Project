// Package capability contains Runtime Agent's authenticated client for the
// Workflow Capability Hub. It is deliberately not reachable from the plugin
// container; only internal/uds invokes it after session authentication.
package capability

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/uds"
)

const maxHubResponseBytes = 2 * 1024 * 1024

type Client struct {
	baseURL string
	token   string
	http    *http.Client
}

func New(baseURL, token string, timeout time.Duration) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"), token: token,
		http: &http.Client{Timeout: timeout},
	}
}

// Invoke maps server-injected session identity to the Capability Hub's
// internal invocation contract. No plugin-supplied user_id / space_id crosses
// this boundary.
func (c *Client) Invoke(ctx context.Context, request uds.Invocation) (uds.InvocationResult, error) {
	if c.baseURL == "" || c.token == "" {
		return uds.InvocationResult{}, fmt.Errorf("Capability Hub client is not configured")
	}
	payload := map[string]interface{}{
		"capabilityKey":       request.CapabilityKey,
		"executionId":         request.ExecutionID,
		"stepId":              request.StepID,
		"attempt":             0,
		"userId":              request.UserID,
		"spaceId":             request.SpaceID,
		"input":               request.Parameters,
		"declaredPermissions": request.DeclaredPermissions,
		// CF-PLUGIN-UDS-002: This is a trusted installation/space snapshot,
		// not a copy of the manifest declaration. Capability Hub applies the
		// final declared ∩ granted intersection again.
		"grantedPermissions": request.GrantedPermissions,
		"traceId":            request.RequestID,
		"idempotencyKey":     request.ExecutionID + ":" + request.StepID + ":" + request.RequestID,
	}
	body, err := json.Marshal(payload)
	if err != nil {
		return uds.InvocationResult{}, err
	}
	httpRequest, err := http.NewRequestWithContext(ctx, http.MethodPost, c.baseURL+"/internal/v1/capabilities/invoke", bytes.NewReader(body))
	if err != nil {
		return uds.InvocationResult{}, err
	}
	httpRequest.Header.Set("Content-Type", "application/json")
	httpRequest.Header.Set("X-PCD-Service-Token", c.token)
	response, err := c.http.Do(httpRequest)
	if err != nil {
		return uds.InvocationResult{}, err
	}
	defer response.Body.Close()
	encoded, err := io.ReadAll(io.LimitReader(response.Body, maxHubResponseBytes+1))
	if err != nil {
		return uds.InvocationResult{}, err
	}
	if len(encoded) > maxHubResponseBytes {
		return uds.InvocationResult{}, fmt.Errorf("Capability Hub response exceeded size limit")
	}
	if response.StatusCode < 200 || response.StatusCode >= 300 {
		return uds.InvocationResult{}, fmt.Errorf("Capability Hub returned HTTP %d", response.StatusCode)
	}
	var envelope struct {
		Code    string `json:"code"`
		Message string `json:"message"`
		Data    struct {
			Success      bool                   `json:"success"`
			Output       map[string]interface{} `json:"output"`
			ErrorCode    string                 `json:"errorCode"`
			ErrorSummary string                 `json:"errorSummary"`
			Retryable    bool                   `json:"retryable"`
		} `json:"data"`
	}
	if err := json.Unmarshal(encoded, &envelope); err != nil {
		return uds.InvocationResult{}, fmt.Errorf("decode Capability Hub response: %w", err)
	}
	if envelope.Code != "" && envelope.Code != "OK" && envelope.Code != "SUCCESS" {
		return uds.InvocationResult{ErrorCode: envelope.Code, Message: envelope.Message}, nil
	}
	if !envelope.Data.Success {
		code := envelope.Data.ErrorCode
		if code == "" {
			code = "CAPABILITY_FAILED"
		}
		message := envelope.Data.ErrorSummary
		if message == "" {
			message = envelope.Message
		}
		return uds.InvocationResult{ErrorCode: code, Message: message, Retryable: envelope.Data.Retryable}, nil
	}
	return uds.InvocationResult{Output: envelope.Data.Output}, nil
}
