package uds

import (
	"bufio"
	"context"
	"encoding/json"
	"fmt"
	"net"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

type fakeInvoker struct {
	mu       sync.Mutex
	calls    []Invocation
	result   InvocationResult
	blocking <-chan struct{}
}

func (f *fakeInvoker) Invoke(ctx context.Context, request Invocation) (InvocationResult, error) {
	if f.blocking != nil {
		select {
		case <-f.blocking:
		case <-ctx.Done():
			return InvocationResult{}, ctx.Err()
		}
	}
	f.mu.Lock()
	f.calls = append(f.calls, request)
	f.mu.Unlock()
	return f.result, nil
}

func testManager(t *testing.T, invoker *fakeInvoker) *Manager {
	t.Helper()
	root, err := os.MkdirTemp("/tmp", "pcd-uds-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(root) })
	manager, err := NewManager(Config{
		RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024,
		MaxConnectionsPerPeer: 32, RequestsPerSecond: 1000, RequestBurst: 1000,
		RequestTimeout: time.Second,
	}, invoker)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = manager.Close() })
	return manager
}

func testSession(t *testing.T, manager *Manager) *Session {
	t.Helper()
	session, err := manager.CreateSession(SessionContext{
		PluginID: "plugin-1", VersionID: "v1", InstallationID: "install-1", UserID: "user-1",
		SpaceID: "space-1", ExecutionID: "execution-1", StepID: "step-1",
		ParentAuditID:       "runtime-root-1",
		DeclaredPermissions: []string{"platform.capability.invoke"},
		GrantedPermissions:  []string{"platform.capability.invoke"},
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = session.Close() })
	return session
}

func invokeSocket(t *testing.T, socketPath string, request CapabilityRequest) CapabilityResponse {
	t.Helper()
	connection, err := net.DialTimeout("unix", socketPath, time.Second)
	if err != nil {
		t.Fatal(err)
	}
	defer connection.Close()
	if err := WriteRequest(connection, request, 64*1024); err != nil {
		t.Fatal(err)
	}
	response, err := ReadResponse(bufio.NewReader(connection), 64*1024)
	if err != nil {
		t.Fatal(err)
	}
	return response
}

func requestFor(t *testing.T, session *Session) CapabilityRequest {
	t.Helper()
	parameters, err := json.Marshal(map[string]interface{}{
		"path": "/reports/week.md", "token": "must-not-appear",
		"nested": []interface{}{
			map[string]interface{}{"authorization": "Bearer must-not-appear", "path": "/private/host/path"},
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	return CapabilityRequest{RequestID: "request-1", CapabilityKey: "api.file.read", Parameters: parameters, InstanceID: session.ID, Token: []byte(session.Token)}
}

func TestSocketRoundTripBindsIdentityAndRecordsTrustedAudit(t *testing.T) {
	invoker := &fakeInvoker{result: InvocationResult{Output: map[string]interface{}{"ok": true}}}
	session := testSession(t, testManager(t, invoker))
	response := invokeSocket(t, session.SocketPath, requestFor(t, session))
	if response.Status != "SUCCESS" || response.Error != nil {
		t.Fatalf("response=%+v", response)
	}
	invoker.mu.Lock()
	if len(invoker.calls) != 1 || invoker.calls[0].UserID != "user-1" || invoker.calls[0].SpaceID != "space-1" {
		t.Fatalf("trusted context not forwarded: %+v", invoker.calls)
	}
	invoker.mu.Unlock()
	audits := session.AuditTrails()
	if len(audits) != 1 {
		t.Fatalf("agent audit=%+v", audits)
	}
	nested, _ := audits[0].InputParams["nested"].([]interface{})
	if len(nested) != 1 {
		t.Fatalf("nested audit data=%+v", audits[0].InputParams)
	}
	nestedRecord, _ := nested[0].(map[string]interface{})
	if audits[0].Status != "SUCCESS" || audits[0].ParentAuditID != "runtime-root-1" || audits[0].InputParams["token"] != "***" ||
		audits[0].InputParams["path"] != "***" || nestedRecord["authorization"] != "***" || nestedRecord["path"] != "***" {
		t.Fatalf("agent audit=%+v", audits)
	}
	stats := session.manager.Stats()
	if stats.Sessions != 1 || stats.Requests != 1 || stats.FailedRequests != 0 || stats.ErrorRate != 0 {
		t.Fatalf("unexpected UDS statistics: %+v", stats)
	}
}

func TestSocketRejectsSpoofedInstanceAndToken(t *testing.T) {
	session := testSession(t, testManager(t, &fakeInvoker{}))
	request := requestFor(t, session)
	request.InstanceID = "another-instance"
	response := invokeSocket(t, session.SocketPath, request)
	if response.Status != "FAILED" || response.Error == nil || response.Error.Code != "RUNTIME_INSTANCE_AUTH_FAILED" {
		t.Fatalf("instance spoof was accepted: %+v", response)
	}
	request = requestFor(t, session)
	request.Token = []byte("invalid-token-with-enough-length-xxxxxxxxxxxxxxxx")
	response = invokeSocket(t, session.SocketPath, request)
	if response.Error == nil || response.Error.Code != "RUNTIME_INSTANCE_AUTH_FAILED" {
		t.Fatalf("token spoof was accepted: %+v", response)
	}
}

func TestSocketSessionsAreIsolatedAndCleanupSocketFiles(t *testing.T) {
	manager := testManager(t, &fakeInvoker{})
	first := testSession(t, manager)
	second := testSession(t, manager)
	if first.SocketPath == second.SocketPath || first.ID == second.ID {
		t.Fatal("instances must not share sockets or IDs")
	}
	request := requestFor(t, second)
	response := invokeSocket(t, first.SocketPath, request)
	if response.Error == nil || response.Error.Code != "RUNTIME_INSTANCE_AUTH_FAILED" {
		t.Fatalf("cross-session request accepted: %+v", response)
	}
	path := first.SocketPath
	if err := first.Close(); err != nil {
		t.Fatal(err)
	}
	if _, err := os.Stat(path); !os.IsNotExist(err) {
		t.Fatalf("socket file remained after session cleanup: %v", err)
	}
}

// TestManagerMaintainsManyIsolatedSessions is the deterministic multi-instance
// regression test. It deliberately opens a meaningful number of listeners
// without pretending that a developer laptop result proves the 100,000-QPS
// production capacity target (that target is exercised by the benchmark/CI
// performance environment).
func TestManagerMaintainsManyIsolatedSessions(t *testing.T) {
	manager := testManager(t, &fakeInvoker{})
	sessions := make([]*Session, 0, 128)
	paths := map[string]struct{}{}
	for index := 0; index < 128; index++ {
		session := testSession(t, manager)
		if _, exists := paths[session.SocketPath]; exists {
			t.Fatalf("duplicate session socket at index %d: %s", index, session.SocketPath)
		}
		paths[session.SocketPath] = struct{}{}
		sessions = append(sessions, session)
	}
	if stats := manager.Stats(); stats.Sessions != len(sessions) || stats.Connections != 0 {
		t.Fatalf("multi-instance manager statistics=%+v", stats)
	}
}

func TestSocketConcurrentCallsDoNotCrossTenantContext(t *testing.T) {
	invoker := &fakeInvoker{result: InvocationResult{Output: map[string]interface{}{}}}
	session := testSession(t, testManager(t, invoker))
	var group sync.WaitGroup
	for index := 0; index < 16; index++ {
		group.Add(1)
		go func(index int) {
			defer group.Done()
			request := requestFor(t, session)
			request.RequestID = "request-concurrent-" + string(rune('a'+index))
			response := invokeSocket(t, session.SocketPath, request)
			if response.Status != "SUCCESS" {
				t.Errorf("response=%+v", response)
			}
		}(index)
	}
	group.Wait()
	invoker.mu.Lock()
	defer invoker.mu.Unlock()
	if len(invoker.calls) != 16 {
		t.Fatalf("calls=%d", len(invoker.calls))
	}
	for _, call := range invoker.calls {
		if call.UserID != "user-1" || call.SpaceID != "space-1" {
			t.Fatalf("tenant context crossed: %+v", call)
		}
	}
}

func TestSocketRateLimitRejectsExcessRequests(t *testing.T) {
	invoker := &fakeInvoker{}
	root, err := os.MkdirTemp("/tmp", "pcd-uds-")
	if err != nil {
		t.Fatal(err)
	}
	defer os.RemoveAll(root)
	manager, err := NewManager(Config{RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024, MaxConnectionsPerPeer: 2, RequestsPerSecond: 1, RequestBurst: 1, RequestTimeout: time.Second}, invoker)
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	session := testSession(t, manager)
	if response := invokeSocket(t, session.SocketPath, requestFor(t, session)); response.Status != "SUCCESS" {
		t.Fatalf("first response=%+v", response)
	}
	request := requestFor(t, session)
	request.RequestID = "request-2"
	response := invokeSocket(t, session.SocketPath, request)
	if response.Error == nil || response.Error.Code != "RUNTIME_RATE_LIMITED" {
		t.Fatalf("rate limit did not apply: %+v", response)
	}
}

func TestSocketRejectsDeclaredButUnGrantedCapabilityPermission(t *testing.T) {
	manager := testManager(t, &fakeInvoker{})
	session, err := manager.CreateSession(SessionContext{
		PluginID: "plugin-1", VersionID: "v1", InstallationID: "install-1", UserID: "user-1",
		SpaceID: "space-1", ExecutionID: "execution-1", StepID: "step-1",
		DeclaredPermissions: []string{"platform.capability.invoke"},
		GrantedPermissions:  []string{"file.content.read"},
	})
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = session.Close() })
	response := invokeSocket(t, session.SocketPath, requestFor(t, session))
	if response.Error == nil || response.Error.Code != "CAPABILITY_PERMISSION_DENIED" {
		t.Fatalf("missing installation grant was accepted: %+v", response)
	}
}

func TestSocketRejectsOverlongSocketRoot(t *testing.T) {
	rootBase, err := os.MkdirTemp("/tmp", "pcd-uds-overlong-")
	if err != nil {
		t.Fatal(err)
	}
	root := rootBase
	// Deliberately make the final AF_UNIX endpoint exceed the conservative
	// cross-platform limit. The Manager must fail closed rather than inventing
	// a shared fallback path.
	for len(root) < 100 {
		root += "x"
	}
	defer os.RemoveAll(root)
	defer os.RemoveAll(rootBase)
	manager, err := NewManager(Config{RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024, MaxConnectionsPerPeer: 2, RequestsPerSecond: 1, RequestBurst: 1, RequestTimeout: time.Second}, &fakeInvoker{})
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	if _, err := manager.CreateSession(SessionContext{}); err == nil {
		t.Fatal("overlong socket path should fail closed")
	}
}

func TestManagerStartupCleansOnlyStaleSocketEndpoints(t *testing.T) {
	// macOS AF_UNIX paths are short; t.TempDir() includes a long test name.
	root, err := os.MkdirTemp("/tmp", "pcd-uds-stale-")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.RemoveAll(root) })
	stalePath := root + "/plugin-crashed-instance.sock"
	listener, err := net.ListenUnix("unix", &net.UnixAddr{Name: stalePath, Net: "unix"})
	if err != nil {
		t.Fatal(err)
	}
	listener.SetUnlinkOnClose(false)
	if err := listener.Close(); err != nil {
		t.Fatal(err)
	}
	markerPath := root + "/plugin-not-a-socket.sock"
	if err := os.WriteFile(markerPath, []byte("operator-marker"), 0o600); err != nil {
		t.Fatal(err)
	}
	manager, err := NewManager(Config{RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024, MaxConnectionsPerPeer: 2, RequestsPerSecond: 1, RequestBurst: 1, RequestTimeout: time.Second}, &fakeInvoker{})
	if err != nil {
		t.Fatal(err)
	}
	defer manager.Close()
	if _, err := os.Lstat(stalePath); !os.IsNotExist(err) {
		t.Fatalf("stale socket was not removed: %v", err)
	}
	if content, err := os.ReadFile(markerPath); err != nil || string(content) != "operator-marker" {
		t.Fatalf("startup must not delete non-socket marker: content=%q err=%v", content, err)
	}
}

func TestSocketRejectsInvalidPayloadWithoutCallingHub(t *testing.T) {
	invoker := &fakeInvoker{}
	session := testSession(t, testManager(t, invoker))
	request := requestFor(t, session)
	request.Parameters = []byte("[]")
	response := invokeSocket(t, session.SocketPath, request)
	if response.Error == nil || response.Error.Code != "CAPABILITY_REQUEST_INVALID" {
		t.Fatalf("invalid parameters accepted: %+v", response)
	}
	invoker.mu.Lock()
	defer invoker.mu.Unlock()
	if len(invoker.calls) != 0 {
		t.Fatalf("hub should not have been called: %+v", invoker.calls)
	}
}

func TestSocketRecordsRunningAuditBeforeHubCompletes(t *testing.T) {
	release := make(chan struct{})
	invoker := &fakeInvoker{result: InvocationResult{Output: map[string]interface{}{}}, blocking: release}
	session := testSession(t, testManager(t, invoker))
	responseDone := make(chan CapabilityResponse, 1)
	go func() { responseDone <- invokeSocket(t, session.SocketPath, requestFor(t, session)) }()
	deadline := time.Now().Add(time.Second)
	for {
		audits := session.AuditTrails()
		if len(audits) == 1 && audits[0].Status == "RUNNING" {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("expected in-flight Agent audit, got %+v", audits)
		}
		time.Sleep(5 * time.Millisecond)
	}
	close(release)
	if response := <-responseDone; response.Status != "SUCCESS" {
		t.Fatalf("response=%+v", response)
	}
	if audits := session.AuditTrails(); len(audits) != 1 || audits[0].Status != "SUCCESS" {
		t.Fatalf("terminal Agent audit not updated: %+v", audits)
	}
}

// BenchmarkSocketParallelRoundTrip is a repeatable baseline for the UDS
// request path. Execute it on a Linux CI worker with an explicit CPU/memory
// envelope; its ns/op result is evidence for capacity planning, not a claim
// that every deployment reaches a fixed QPS number.
func BenchmarkSocketParallelRoundTrip(b *testing.B) {
	// b.TempDir() includes the benchmark name and can exceed Darwin's short
	// AF_UNIX sockaddr limit once the random instance suffix is appended.
	// Production uses the deliberately short /run/pcd/plugins root; mirror that
	// constraint with a short temp root instead of weakening the fail-closed
	// path-length guard in Manager.CreateSession.
	root, err := os.MkdirTemp("/tmp", "pcd-b-")
	if err != nil {
		b.Fatal(err)
	}
	b.Cleanup(func() { _ = os.RemoveAll(root) })
	invoker := &fakeInvoker{result: InvocationResult{Output: map[string]interface{}{"ok": true}}}
	manager, err := NewManager(Config{
		RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024,
		MaxConnectionsPerPeer: 256, RequestsPerSecond: 1_000_000, RequestBurst: 1_000_000,
		RequestTimeout: 5 * time.Second,
	}, invoker)
	if err != nil {
		b.Fatal(err)
	}
	b.Cleanup(func() { _ = manager.Close() })
	session, err := manager.CreateSession(SessionContext{
		PluginID: "plugin-bench", VersionID: "v1", InstallationID: "install-bench", UserID: "user-bench",
		SpaceID: "space-bench", ExecutionID: "execution-bench", StepID: "step-bench",
		DeclaredPermissions: []string{"platform.capability.invoke"}, GrantedPermissions: []string{"platform.capability.invoke"},
	})
	if err != nil {
		b.Fatal(err)
	}
	b.Cleanup(func() { _ = session.Close() })
	parameters, err := json.Marshal(map[string]interface{}{"value": 1})
	if err != nil {
		b.Fatal(err)
	}
	var sequence atomic.Uint64
	b.SetParallelism(8)
	b.ReportAllocs()
	b.ResetTimer()
	b.RunParallel(func(parallel *testing.PB) {
		connection, dialErr := net.DialTimeout("unix", session.SocketPath, time.Second)
		if dialErr != nil {
			b.Error(dialErr)
			return
		}
		defer connection.Close()
		reader := bufio.NewReader(connection)
		for parallel.Next() {
			request := CapabilityRequest{
				RequestID: fmt.Sprintf("bench-%d", sequence.Add(1)), CapabilityKey: "api.file.read",
				Parameters: parameters, InstanceID: session.ID, Token: []byte(session.Token),
			}
			if writeErr := WriteRequest(connection, request, 64*1024); writeErr != nil {
				b.Error(writeErr)
				return
			}
			response, readErr := ReadResponse(reader, 64*1024)
			if readErr != nil || response.Status != "SUCCESS" {
				b.Errorf("response=%+v error=%v", response, readErr)
				return
			}
		}
	})
}

// BenchmarkSessionLifecycleOneThousand measures the Agent's intended
// multi-tenant lifecycle shape: one listener and one opaque endpoint per
// plugin instance. It creates no containers and does not claim an end-to-end
// Docker/Hub result; that remains a Linux integration performance exercise.
func BenchmarkSessionLifecycleOneThousand(b *testing.B) {
	for iteration := 0; iteration < b.N; iteration++ {
		root, err := os.MkdirTemp("/tmp", "pcd-b-")
		if err != nil {
			b.Fatal(err)
		}
		manager, err := NewManager(Config{
			RootDir: root, GroupID: -1, MaxFrameBytes: 64 * 1024,
			MaxConnectionsPerPeer: 16, RequestsPerSecond: 100, RequestBurst: 200,
			RequestTimeout: time.Second,
		}, &fakeInvoker{})
		if err != nil {
			_ = os.RemoveAll(root)
			b.Fatal(err)
		}
		for instance := 0; instance < 1_000; instance++ {
			if _, err := manager.CreateSession(SessionContext{
				PluginID: "plugin-bench", VersionID: "v1", InstallationID: "install-bench", UserID: "user-bench",
				SpaceID: "space-bench", ExecutionID: fmt.Sprintf("execution-%d", instance), StepID: "step-bench",
				DeclaredPermissions: []string{"platform.capability.invoke"}, GrantedPermissions: []string{"platform.capability.invoke"},
			}); err != nil {
				_ = manager.Close()
				_ = os.RemoveAll(root)
				b.Fatalf("create instance %d: %v", instance, err)
			}
		}
		if stats := manager.Stats(); stats.Sessions != 1_000 {
			_ = manager.Close()
			_ = os.RemoveAll(root)
			b.Fatalf("session count=%d", stats.Sessions)
		}
		if err := manager.Close(); err != nil {
			_ = os.RemoveAll(root)
			b.Fatal(err)
		}
		if err := os.RemoveAll(root); err != nil {
			b.Fatal(err)
		}
	}
}

var _ = context.Background
