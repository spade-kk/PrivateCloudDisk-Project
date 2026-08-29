package uds

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/sanitize"
)

// Invoker is the trusted Runtime Agent -> Capability Hub boundary. The plugin
// never supplies identity fields in this invocation: those come exclusively
// from the UDS session that created the container.
type Invoker interface {
	Invoke(ctx context.Context, request Invocation) (InvocationResult, error)
}

type Invocation struct {
	RequestID        string
	CapabilityKey    string
	Parameters       map[string]interface{}
	PluginInstanceID string
	PluginID         string
	VersionID        string
	InstallationID   string
	UserID           string
	SpaceID          string
	ExecutionID      string
	StepID           string
	// ParentAuditID is created by the trusted execution envelope. It is never
	// supplied by SDK wire data and lets the control plane attach child calls to
	// its stable execution root or workflow step.
	ParentAuditID       string
	DeclaredPermissions []string
	// GrantedPermissions is a server-side snapshot from Plugin Service's
	// installation/space authorization decision. It must never be inferred
	// from the manifest declaration or from data sent by plugin code.
	GrantedPermissions []string
}

type InvocationResult struct {
	Output    map[string]interface{}
	ErrorCode string
	Message   string
	Retryable bool
}

// SessionContext is immutable server-controlled identity data. Do not add a
// plugin-provided field here; doing so would regress tenant isolation.
type SessionContext struct {
	PluginID       string
	VersionID      string
	InstallationID string
	UserID         string
	SpaceID        string
	ExecutionID    string
	StepID         string
	// ParentAuditID is created by the trusted execution envelope, not by the
	// plugin wire request. Keeping it on the immutable session context preserves
	// the event/workflow audit hierarchy without allowing a tenant to forge it.
	ParentAuditID       string
	DeclaredPermissions []string
	GrantedPermissions  []string
}

type Config struct {
	RootDir               string
	GroupID               int
	MaxFrameBytes         int
	MaxConnectionsPerPeer int
	RequestsPerSecond     int
	RequestBurst          int
	RequestTimeout        time.Duration
}

// Manager is a single Runtime Agent process component. Each created Session
// owns one listener and independent identity/token/limiter/audit buffer.
type Manager struct {
	config         Config
	invoker        Invoker
	mu             sync.RWMutex
	sessions       map[string]*Session
	closed         bool
	requestCount   atomic.Uint64
	failedRequests atomic.Uint64
}

// Stats is a small, allocation-free snapshot intended for the protected
// Runtime health/metrics endpoint. It contains no socket paths, tokens or
// tenant identifiers, so operators can observe load without broadening the
// plugin attack surface.
type Stats struct {
	Sessions       int     `json:"sessions"`
	Connections    int     `json:"connections"`
	Requests       uint64  `json:"requests"`
	FailedRequests uint64  `json:"failed_requests"`
	ErrorRate      float64 `json:"error_rate"`
}

type Session struct {
	ID         string
	SocketPath string
	Token      string
	context    SessionContext
	manager    *Manager
	listener   *net.UnixListener

	mu          sync.Mutex
	connections map[net.Conn]struct{}
	audits      []model.RuntimeAuditRecord
	auditIndex  map[string]int
	closed      bool
	tokens      float64
	lastRefill  time.Time
	done        chan struct{}
	wg          sync.WaitGroup
}

var capabilityKey = regexp.MustCompile(`^[a-z][a-z0-9_.:-]{0,255}$`)

func NewManager(config Config, invoker Invoker) (*Manager, error) {
	if invoker == nil {
		return nil, errors.New("Runtime UDS Manager requires a Capability Hub invoker")
	}
	if !filepath.IsAbs(config.RootDir) || config.MaxFrameBytes < 1024 || config.MaxConnectionsPerPeer < 1 ||
		config.RequestsPerSecond < 1 || config.RequestBurst < 1 || config.RequestTimeout <= 0 {
		return nil, errors.New("Runtime UDS Manager configuration is invalid")
	}
	if err := os.MkdirAll(config.RootDir, 0o700); err != nil {
		return nil, fmt.Errorf("create runtime socket directory: %w", err)
	}
	if err := os.Chmod(config.RootDir, 0o700); err != nil {
		return nil, fmt.Errorf("secure runtime socket directory: %w", err)
	}
	if err := cleanupStaleSockets(config.RootDir); err != nil {
		return nil, err
	}
	return &Manager{config: config, invoker: invoker, sessions: map[string]*Session{}}, nil
}

// cleanupStaleSockets is deliberately narrow: after an Agent crash, only
// socket endpoints created by a prior Agent process are removed. Marker files,
// symlinks and any unexpected filesystem object are left untouched for an
// operator to inspect rather than being deleted by privileged startup code.
func cleanupStaleSockets(root string) error {
	entries, err := os.ReadDir(root)
	if err != nil {
		return fmt.Errorf("inspect runtime socket directory: %w", err)
	}
	for _, entry := range entries {
		if !strings.HasPrefix(entry.Name(), "plugin-") || !strings.HasSuffix(entry.Name(), ".sock") {
			continue
		}
		path := filepath.Join(root, entry.Name())
		info, statErr := os.Lstat(path)
		if statErr != nil {
			return fmt.Errorf("inspect stale plugin socket: %w", statErr)
		}
		if info.Mode()&os.ModeSocket == 0 {
			continue
		}
		if err := os.Remove(path); err != nil {
			return fmt.Errorf("remove stale plugin socket: %w", err)
		}
	}
	return nil
}

// CreateSession must run before Docker starts. Its ID is high entropy and its
// token is passed only as runner.py argv, never via ENV or context.json.
func (m *Manager) CreateSession(identity SessionContext) (*Session, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.closed {
		return nil, errors.New("RUNTIME_SOCKET_UNAVAILABLE: manager is shutting down")
	}
	instanceID, err := randomURLToken(24)
	if err != nil {
		return nil, err
	}
	token, err := randomURLToken(48)
	if err != nil {
		return nil, err
	}
	socketPath := filepath.Join(m.config.RootDir, "plugin-"+instanceID+".sock")
	// Unix-domain socket path limits are OS specific (Linux commonly 108 bytes,
	// Darwin 104). Fail closed instead of falling back to a shared/shortened
	// path, because path identity is part of the tenant-isolation boundary.
	if len(socketPath) >= 100 {
		return nil, fmt.Errorf("RUNTIME_SOCKET_PATH_TOO_LONG: socket root makes instance path too long")
	}
	// A stale endpoint may only be replaced when it is an actual socket. Never
	// unlink an arbitrary file/symlink located in a security-sensitive runtime
	// directory, even if a host misconfiguration created it.
	if info, statErr := os.Lstat(socketPath); statErr == nil {
		if info.Mode()&os.ModeSocket == 0 {
			return nil, fmt.Errorf("RUNTIME_SOCKET_PATH_UNSAFE: stale endpoint is not a socket")
		}
		if err := os.Remove(socketPath); err != nil {
			return nil, fmt.Errorf("remove stale plugin socket: %w", err)
		}
	} else if !os.IsNotExist(statErr) {
		return nil, fmt.Errorf("inspect stale plugin socket: %w", statErr)
	}
	address := &net.UnixAddr{Name: socketPath, Net: "unix"}
	listener, err := net.ListenUnix("unix", address)
	if err != nil {
		return nil, fmt.Errorf("create plugin socket: %w", err)
	}
	// CF-PLUGIN-UDS-001: strict 0660 socket means only Runtime's owner and
	// the configured sandbox group can access the bind-mounted endpoint.
	if m.config.GroupID >= 0 {
		if err := os.Chown(socketPath, -1, m.config.GroupID); err != nil {
			_ = listener.Close()
			_ = os.Remove(socketPath)
			return nil, fmt.Errorf("set plugin socket group: %w", err)
		}
	}
	if err := os.Chmod(socketPath, 0o660); err != nil {
		_ = listener.Close()
		_ = os.Remove(socketPath)
		return nil, fmt.Errorf("set plugin socket mode: %w", err)
	}
	session := &Session{
		ID: instanceID, SocketPath: socketPath, Token: token, context: cloneContext(identity),
		manager: m, listener: listener, connections: map[net.Conn]struct{}{},
		auditIndex: map[string]int{},
		tokens:     float64(m.config.RequestBurst), lastRefill: time.Now(), done: make(chan struct{}),
	}
	m.sessions[session.ID] = session
	session.wg.Add(1)
	go session.acceptLoop()
	return session, nil
}

func (m *Manager) SessionCount() int {
	m.mu.RLock()
	defer m.mu.RUnlock()
	return len(m.sessions)
}

// Stats returns a consistent-enough operational snapshot for monitoring. The
// request counters are atomically monotonic; session/connection counts are a
// point-in-time view and intentionally do not expose identities.
func (m *Manager) Stats() Stats {
	m.mu.RLock()
	sessions := make([]*Session, 0, len(m.sessions))
	for _, session := range m.sessions {
		sessions = append(sessions, session)
	}
	m.mu.RUnlock()
	connections := 0
	for _, session := range sessions {
		session.mu.Lock()
		connections += len(session.connections)
		session.mu.Unlock()
	}
	requests := m.requestCount.Load()
	failed := m.failedRequests.Load()
	stats := Stats{Sessions: len(sessions), Connections: connections, Requests: requests, FailedRequests: failed}
	if requests != 0 {
		stats.ErrorRate = float64(failed) / float64(requests)
	}
	return stats
}

func (m *Manager) Close() error {
	m.mu.Lock()
	if m.closed {
		m.mu.Unlock()
		return nil
	}
	m.closed = true
	sessions := make([]*Session, 0, len(m.sessions))
	for _, session := range m.sessions {
		sessions = append(sessions, session)
	}
	m.mu.Unlock()
	var first error
	for _, session := range sessions {
		if err := session.Close(); err != nil && first == nil {
			first = err
		}
	}
	return first
}

// Close is idempotent and waits for accept/connection goroutines so socket
// files are not left after crashes, timeout cleanup, or graceful shutdown.
func (s *Session) Close() error {
	s.mu.Lock()
	if s.closed {
		s.mu.Unlock()
		return nil
	}
	s.closed = true
	close(s.done)
	listener := s.listener
	connections := make([]net.Conn, 0, len(s.connections))
	for connection := range s.connections {
		connections = append(connections, connection)
	}
	s.mu.Unlock()
	if listener != nil {
		_ = listener.Close()
	}
	for _, connection := range connections {
		_ = connection.Close()
	}
	s.wg.Wait()
	s.manager.mu.Lock()
	delete(s.manager.sessions, s.ID)
	s.manager.mu.Unlock()
	if err := os.Remove(s.SocketPath); err != nil && !os.IsNotExist(err) {
		return err
	}
	return nil
}

func (s *Session) AuditTrails() []model.RuntimeAuditRecord {
	s.mu.Lock()
	defer s.mu.Unlock()
	result := make([]model.RuntimeAuditRecord, len(s.audits))
	copy(result, s.audits)
	return result
}

func (s *Session) acceptLoop() {
	defer s.wg.Done()
	for {
		connection, err := s.listener.AcceptUnix()
		if err != nil {
			select {
			case <-s.done:
				return
			default:
				continue
			}
		}
		if !s.registerConnection(connection) {
			s.manager.failedRequests.Add(1)
			_ = connection.Close()
			continue
		}
		s.wg.Add(1)
		go s.serveConnection(connection)
	}
}

func (s *Session) registerConnection(connection net.Conn) bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	if s.closed || len(s.connections) >= s.manager.config.MaxConnectionsPerPeer {
		return false
	}
	s.connections[connection] = struct{}{}
	return true
}

func (s *Session) serveConnection(connection *net.UnixConn) {
	defer s.wg.Done()
	defer func() {
		s.mu.Lock()
		delete(s.connections, connection)
		s.mu.Unlock()
		_ = connection.Close()
	}()
	reader := bufio.NewReader(connection)
	for {
		_ = connection.SetReadDeadline(time.Now().Add(s.manager.config.RequestTimeout))
		request, err := ReadRequest(reader, s.manager.config.MaxFrameBytes)
		if err != nil {
			if !errors.Is(err, io.EOF) {
				s.manager.failedRequests.Add(1)
				_ = s.writeError(connection, "", "RUNTIME_SOCKET_PROTOCOL_INVALID", sanitize.Error(err, 300), false)
			}
			return
		}
		response := s.handleRequest(request)
		_ = connection.SetWriteDeadline(time.Now().Add(s.manager.config.RequestTimeout))
		if err := WriteResponse(connection, response, s.manager.config.MaxFrameBytes); err != nil {
			return
		}
	}
}

func (s *Session) handleRequest(request CapabilityRequest) (response CapabilityResponse) {
	s.manager.requestCount.Add(1)
	defer func() {
		if response.Status != "SUCCESS" {
			s.manager.failedRequests.Add(1)
		}
	}()
	if request.InstanceID != s.ID || subtle.ConstantTimeCompare(request.Token, []byte(s.Token)) != 1 {
		return responseError(request.RequestID, "RUNTIME_INSTANCE_AUTH_FAILED", "插件实例身份验证失败", false)
	}
	if !capabilityKey.MatchString(request.CapabilityKey) {
		return responseError(request.RequestID, "CAPABILITY_REQUEST_INVALID", "能力键格式无效", false)
	}
	if !hasPermission(s.context.DeclaredPermissions, "platform.capability.invoke") {
		return responseError(request.RequestID, "CAPABILITY_PERMISSION_DENIED", "插件未声明平台能力调用权限", false)
	}
	if !hasPermission(s.context.GrantedPermissions, "platform.capability.invoke") {
		return responseError(request.RequestID, "CAPABILITY_PERMISSION_DENIED", "插件实例未获授平台能力调用权限", false)
	}
	if !s.takeToken() {
		return responseError(request.RequestID, "RUNTIME_RATE_LIMITED", "插件实例能力调用超过速率限制", true)
	}
	parameters := map[string]interface{}{}
	if err := json.Unmarshal(request.Parameters, &parameters); err != nil {
		return responseError(request.RequestID, "CAPABILITY_REQUEST_INVALID", "能力参数必须是 JSON 对象", false)
	}
	started := time.Now()
	// Record the trusted fact at the Agent boundary before invoking Hub. The
	// terminal result below updates this same invocation ID rather than asking
	// untrusted plugin code to manufacture an audit file.
	s.beginAudit(request, parameters, started)
	ctx, cancel := context.WithTimeout(context.Background(), s.manager.config.RequestTimeout)
	defer cancel()
	result, err := s.manager.invoker.Invoke(ctx, Invocation{
		RequestID: request.RequestID, CapabilityKey: request.CapabilityKey, Parameters: parameters,
		PluginInstanceID: s.ID, PluginID: s.context.PluginID, VersionID: s.context.VersionID,
		InstallationID: s.context.InstallationID, UserID: s.context.UserID, SpaceID: s.context.SpaceID,
		ExecutionID: s.context.ExecutionID, StepID: s.context.StepID, ParentAuditID: s.context.ParentAuditID,
		DeclaredPermissions: append([]string(nil), s.context.DeclaredPermissions...),
		GrantedPermissions:  append([]string(nil), s.context.GrantedPermissions...),
	})
	duration := time.Since(started).Milliseconds()
	if err != nil {
		code := "RUNTIME_CAPABILITY_HUB_UNAVAILABLE"
		message := "能力中心暂不可用"
		if errors.Is(ctx.Err(), context.DeadlineExceeded) {
			code, message = "CAPABILITY_TIMEOUT", "能力调用超时"
		}
		s.recordAudit(request, parameters, nil, "FAILED", duration, code, message)
		return responseError(request.RequestID, code, message, true)
	}
	if result.ErrorCode != "" {
		s.recordAudit(request, parameters, nil, "FAILED", duration, result.ErrorCode, result.Message)
		return responseError(request.RequestID, result.ErrorCode, safeMessage(result.Message), result.Retryable)
	}
	output, err := json.Marshal(result.Output)
	if err != nil || len(output) > s.manager.config.MaxFrameBytes/2 {
		s.recordAudit(request, parameters, nil, "FAILED", duration, "CAPABILITY_RESPONSE_TOO_LARGE", "能力结果超过 Socket 消息限制")
		return responseError(request.RequestID, "CAPABILITY_RESPONSE_TOO_LARGE", "能力结果超过 Socket 消息限制", false)
	}
	s.recordAudit(request, parameters, result.Output, "SUCCESS", duration, "", "")
	return CapabilityResponse{RequestID: request.RequestID, Status: "SUCCESS", Result: output}
}

func (s *Session) writeError(connection net.Conn, requestID, code, message string, retryable bool) error {
	return WriteResponse(connection, responseError(requestID, code, message, retryable), s.manager.config.MaxFrameBytes)
}

func responseError(requestID, code, message string, retryable bool) CapabilityResponse {
	return CapabilityResponse{RequestID: requestID, Status: "FAILED", Error: &ErrorInfo{Code: code, Message: safeMessage(message), Retryable: retryable}}
}

func (s *Session) takeToken() bool {
	s.mu.Lock()
	defer s.mu.Unlock()
	now := time.Now()
	elapsed := now.Sub(s.lastRefill).Seconds()
	s.tokens = minFloat(float64(s.manager.config.RequestBurst), s.tokens+elapsed*float64(s.manager.config.RequestsPerSecond))
	s.lastRefill = now
	if s.tokens < 1 {
		return false
	}
	s.tokens--
	return true
}

func (s *Session) recordAudit(request CapabilityRequest, input, output map[string]interface{}, status string, duration int64, errorCode, errorMessage string) {
	record := s.newAuditRecord(request, input, output, status, duration, errorCode, errorMessage)
	s.mu.Lock()
	if index, ok := s.auditIndex[request.RequestID]; ok {
		s.audits[index] = record
	} else {
		s.auditIndex[request.RequestID] = len(s.audits)
		s.audits = append(s.audits, record)
	}
	s.mu.Unlock()
}

func (s *Session) beginAudit(request CapabilityRequest, input map[string]interface{}, started time.Time) {
	record := s.newAuditRecord(request, input, nil, "RUNNING", 0, "", "")
	record.Timestamp = started.UTC().Format(time.RFC3339Nano)
	s.mu.Lock()
	if _, exists := s.auditIndex[request.RequestID]; !exists {
		s.auditIndex[request.RequestID] = len(s.audits)
		s.audits = append(s.audits, record)
	}
	s.mu.Unlock()
}

func (s *Session) newAuditRecord(request CapabilityRequest, input, output map[string]interface{}, status string, duration int64, errorCode, errorMessage string) model.RuntimeAuditRecord {
	return model.RuntimeAuditRecord{
		// ParentAuditID is supplied by the trusted Runtime execution envelope. It
		// provides the parent edge for event-root or workflow-step calls and is
		// never accepted from SDK wire data.
		AuditID: request.RequestID, ParentAuditID: s.context.ParentAuditID,
		CapabilityKey: request.CapabilityKey, CapabilityType: capabilityType(request.CapabilityKey),
		SummaryTemplate: "platform.capability.invoke", TargetContext: map[string]interface{}{
			"plugin_instance_id": s.ID, "plugin_id": s.context.PluginID, "execution_id": s.context.ExecutionID,
			"step_id": s.context.StepID, "user_id": s.context.UserID, "space_id": s.context.SpaceID,
		},
		InputParams: redactMap(input), OutputResult: redactMap(output), Status: status, DurationMs: duration,
		ErrorCode: errorCode, ErrorSummary: safeMessage(errorMessage), Timestamp: time.Now().UTC().Format(time.RFC3339Nano),
	}
}

func redactMap(value map[string]interface{}) map[string]interface{} {
	if value == nil {
		return nil
	}
	encoded, err := json.Marshal(value)
	if err != nil {
		return map[string]interface{}{"redacted": true}
	}
	var result map[string]interface{}
	// [CF-PLUGIN-UDS-003] The Runtime Agent, rather than untrusted plugin code,
	// produces the persisted audit fact. Preserve JSON shape for the UI, but walk
	// objects and arrays so nested credentials and absolute paths cannot leak to
	// Plugin Service, Automation Service, or an execution-detail response.
	if json.Unmarshal(encoded, &result) != nil {
		return map[string]interface{}{"redacted": true}
	}
	for key, item := range result {
		lower := strings.ToLower(key)
		if strings.Contains(lower, "token") || strings.Contains(lower, "secret") || strings.Contains(lower, "password") || strings.Contains(lower, "authorization") || strings.Contains(lower, "credential") {
			result[key] = "***"
			continue
		}
		result[key] = redactValue(item)
	}
	return result
}

func redactValue(value interface{}) interface{} {
	switch typed := value.(type) {
	case map[string]interface{}:
		return redactMap(typed)
	case []interface{}:
		result := make([]interface{}, len(typed))
		for index, item := range typed {
			result[index] = redactValue(item)
		}
		return result
	case string:
		// Absolute host paths reveal topology. The virtual file handle is resolved
		// by Capability Hub; the execution audit deliberately keeps no raw path.
		if strings.HasPrefix(typed, "/") {
			return "***"
		}
		return typed
	default:
		return value
	}
}

func hasPermission(permissions []string, wanted string) bool {
	for _, permission := range permissions {
		if permission == wanted {
			return true
		}
	}
	return false
}

func capabilityType(key string) string {
	switch {
	case strings.HasPrefix(key, "builtin."):
		return "BUILTIN"
	case strings.HasPrefix(key, "plugin."):
		return "PLUGIN"
	default:
		return "PLATFORM_API"
	}
}

func safeMessage(value string) string { return sanitize.Summary(sanitize.Sanitize(value), 1000) }

func cloneContext(value SessionContext) SessionContext {
	value.DeclaredPermissions = append([]string(nil), value.DeclaredPermissions...)
	value.GrantedPermissions = append([]string(nil), value.GrantedPermissions...)
	return value
}

func randomURLToken(bytes int) (string, error) {
	raw := make([]byte, bytes)
	if _, err := rand.Read(raw); err != nil {
		return "", fmt.Errorf("generate session credential: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

func minFloat(left, right float64) float64 {
	if left < right {
		return left
	}
	return right
}
