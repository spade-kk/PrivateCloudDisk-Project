package capability

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// TestInvokeUsesTrustedGrantSnapshot proves that the Agent cannot turn a
// manifest declaration into an installation grant while forwarding to Hub.
func TestInvokeUsesTrustedGrantSnapshot(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		if request.Method != http.MethodPost || request.URL.Path != "/internal/v1/capabilities/invoke" {
			t.Fatalf("unexpected route %s %s", request.Method, request.URL.Path)
		}
		if request.Header.Get("X-PCD-Service-Token") != "runtime-service-token" {
			t.Fatal("runtime service credential was not forwarded")
		}
		var body map[string]interface{}
		if err := json.NewDecoder(request.Body).Decode(&body); err != nil {
			t.Fatal(err)
		}
		declared, _ := body["declaredPermissions"].([]interface{})
		granted, _ := body["grantedPermissions"].([]interface{})
		if len(declared) != 2 || declared[1] != "file.content.write" {
			t.Fatalf("manifest declaration lost: %#v", declared)
		}
		if len(granted) != 1 || granted[0] != "file.content.read" {
			t.Fatalf("runtime self-granted permissions: %#v", granted)
		}
		if body["userId"] != "trusted-user" || body["spaceId"] != "trusted-space" {
			t.Fatalf("session tenant identity was not injected: %#v", body)
		}
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{"code":"OK","data":{"success":true,"output":{"accepted":true}}}`))
	}))
	defer server.Close()

	client := New(server.URL, "runtime-service-token", time.Second)
	result, err := client.Invoke(context.Background(), uds.Invocation{
		RequestID: "request-1", CapabilityKey: "api.file.read", Parameters: map[string]interface{}{"user_id": "plugin-controlled"},
		PluginInstanceID: "instance-1", PluginID: "plugin-1", VersionID: "v1", InstallationID: "install-1",
		UserID: "trusted-user", SpaceID: "trusted-space", ExecutionID: "execution-1", StepID: "step-1",
		DeclaredPermissions: []string{"file.content.read", "file.content.write"},
		GrantedPermissions:  []string{"file.content.read"},
	})
	if err != nil || result.Output["accepted"] != true {
		t.Fatalf("result=%+v err=%v", result, err)
	}
}

func TestInvokeMapsCapabilityFailure(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(response http.ResponseWriter, _ *http.Request) {
		response.Header().Set("Content-Type", "application/json")
		_, _ = response.Write([]byte(`{"code":"OK","data":{"success":false,"errorCode":"WF-CAPABILITY-FORBIDDEN","errorSummary":"权限不足","retryable":false}}`))
	}))
	defer server.Close()
	result, err := New(server.URL, "token", time.Second).Invoke(context.Background(), uds.Invocation{
		RequestID: "r", CapabilityKey: "api.user.info", Parameters: map[string]interface{}{},
		ExecutionID: "e", StepID: "s",
	})
	if err != nil || result.ErrorCode != "WF-CAPABILITY-FORBIDDEN" || result.Retryable {
		t.Fatalf("result=%+v err=%v", result, err)
	}
}
