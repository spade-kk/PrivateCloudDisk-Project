package audit

import (
	"context"
	"log/slog"
	"time"

	"privateclouddisk/cloudflow-mcp-server/internal/hub"
	"privateclouddisk/cloudflow-mcp-server/internal/identity"
	"privateclouddisk/cloudflow-mcp-server/internal/model"
)

// Recorder sends protocol-level audit events back to Capability Hub.  The MCP
// process keeps no audit file and has no database access; Hub is the durable
// audit authority and can correlate this record with execution audit rows.
type Recorder struct {
	client  *hub.Client
	timeout time.Duration
	logger  *slog.Logger
}

func New(client *hub.Client, timeout time.Duration, logger *slog.Logger) *Recorder {
	return &Recorder{client: client, timeout: timeout, logger: logger}
}

func (recorder *Recorder) Record(
	identity identity.Identity, method string, summary map[string]any,
	success bool, resultCode string, duration time.Duration,
) {
	ctx, cancel := context.WithTimeout(context.Background(), recorder.timeout)
	defer cancel()
	request := model.HubAuditRequest{
		Method: method, UserID: identity.UserID, TenantID: identity.TenantID, SpaceID: identity.SpaceID,
		TraceID: identity.RequestID, AgentID: identity.AgentID, ParameterSummary: summary,
		Success: success, ResultCode: resultCode, DurationMS: duration.Milliseconds(),
	}
	for attempt := 0; attempt < 3; attempt++ {
		if err := recorder.client.Audit(ctx, request, identity.RequestID); err == nil {
			return
		} else if ctx.Err() == nil {
			recorder.logger.Warn("MCP protocol audit delivery failed", "method", method, "attempt", attempt+1, "error", err)
			select {
			case <-ctx.Done():
				return
			case <-time.After(time.Duration(attempt+1) * 75 * time.Millisecond):
			}
		}
	}
}
