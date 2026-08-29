package model

import (
	"encoding/json"
	"reflect"
	"strings"
	"testing"
)

func TestPreprocessChainRequestRoundTrip(t *testing.T) {
	request := PreprocessChainRequest{
		ExecutionID:     "exec_1",
		ContentLeaseRef: "ref-1",
		DeadlineAt:      "2030-01-01T00:00:00Z",
		Event:           map[string]interface{}{"data": map[string]interface{}{"gate_id": "gate1"}},
		Entrypoints: []Entrypoint{{
			InstallationID: "inst-1", PluginID: "plugin-1", VersionID: "v1",
			Runtime: "PYTHON_3_11", ModulePath: "main.py", FunctionName: "main",
			Priority: 2, Permissions: []string{"file.content.read"},
			Config: map[string]interface{}{"k": "v"},
		}},
	}
	raw, err := json.Marshal(request)
	if err != nil {
		t.Fatalf("序列化失败：%v", err)
	}
	var decoded PreprocessChainRequest
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatalf("反序列化失败：%v", err)
	}
	if !reflect.DeepEqual(request, decoded) {
		t.Fatalf("往返不一致：\n%+v\n%+v", request, decoded)
	}
}

func TestCapabilityResultEmptyOutputOmits(t *testing.T) {
	result := CapabilityExecutionResult{Status: "success", Output: map[string]interface{}{}}
	raw, _ := json.Marshal(result)
	if !reflect.DeepEqual(raw, []byte(`{"status":"success"}`)) {
		t.Fatalf("空 Output 应被 omitempty 省略：%s", raw)
	}
}

func TestRuntimeChainResultFields(t *testing.T) {
	size := int64(10)
	result := RuntimeChainResult{
		Status: "success", ContentModified: true, CandidateID: "c-1",
		CandidateChecksum: "abc", CandidateSize: &size, CompletedEntrypoints: 2,
	}
	raw, err := json.Marshal(result)
	if err != nil {
		t.Fatal(err)
	}
	var decoded map[string]interface{}
	if err := json.Unmarshal(raw, &decoded); err != nil {
		t.Fatal(err)
	}
	if decoded["status"] != "success" || decoded["content_modified"] != true {
		t.Fatalf("字段编码错误：%s", raw)
	}
	var result2 RuntimeChainResult
	failedResult := RuntimeChainResult{Status: "failed", FailureCode: "E1", FailureSummary: "s"}
	raw2, _ := json.Marshal(failedResult)
	if err := json.Unmarshal(raw2, &result2); err != nil {
		t.Fatal(err)
	}
	if result2.FailureCode != "E1" || result2.FailureSummary != "s" {
		t.Fatalf("失败字段往返错误：%s", raw2)
	}
}

func TestRuntimeChainResultCarriesStructuredCapabilityAudit(t *testing.T) {
	result := RuntimeChainResult{Status: "success", AuditTrails: []RuntimeAuditRecord{{
		AuditID: "1", CapabilityKey: "api.user.info", CapabilityType: "PLATFORM_API",
		Status: "SUCCESS", InputParams: map[string]interface{}{"user_id": "u-1"},
	}}}
	raw, err := json.Marshal(result)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), `"audit_trails"`) || !strings.Contains(string(raw), `"api.user.info"`) {
		t.Fatalf("结构化审计未编码：%s", raw)
	}
}

func TestCapabilityExecutionResultCarriesStructuredCapabilityAudit(t *testing.T) {
	result := CapabilityExecutionResult{Status: "success", AuditTrails: []RuntimeAuditRecord{{
		AuditID: "1", CapabilityKey: "api.user.info", CapabilityType: "PLATFORM_API", Status: "SUCCESS",
	}}}
	raw, err := json.Marshal(result)
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(raw), `"audit_trails"`) || !strings.Contains(string(raw), `"api.user.info"`) {
		t.Fatalf("能力执行审计未编码：%s", raw)
	}
}

func TestTestExecutionModels(t *testing.T) {
	accepted := TestExecutionAccepted{ExecutionID: "exec_1", Status: "accepted"}
	raw, _ := json.Marshal(accepted)
	var decoded TestExecutionAccepted
	if err := json.Unmarshal(raw, &decoded); err != nil || decoded.ExecutionID != "exec_1" {
		t.Fatalf("TestExecutionAccepted 往返错误：%s err=%v", raw, err)
	}
	status := TestExecutionStatus{ExecutionID: "e", Status: "running", ErrorCode: "X"}
	raw2, _ := json.Marshal(status)
	if err := json.Unmarshal(raw2, &status); err != nil || status.ErrorCode != "X" {
		t.Fatalf("TestExecutionStatus 往返错误：%s err=%v", raw2, err)
	}
}
