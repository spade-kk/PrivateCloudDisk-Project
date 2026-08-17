package model

type ValidationRequest struct {
	Source      string   `json:"source"`
	Entrypoint  string   `json:"entrypoint"`
	Permissions []string `json:"permissions"`
}

type ValidationResponse struct {
	Valid      bool                     `json:"valid"`
	ErrorType  string                   `json:"error_type,omitempty"`
	Line       int                      `json:"line,omitempty"`
	Column     int                      `json:"column,omitempty"`
	Message    string                   `json:"message,omitempty"`
	Suggestion string                   `json:"suggestion,omitempty"`
	Findings   []map[string]interface{} `json:"findings"`
	Metrics    map[string]interface{}   `json:"metrics"`
}

type Entrypoint struct {
	InstallationID string                 `json:"installation_id"`
	PluginID       string                 `json:"plugin_id"`
	VersionID      string                 `json:"version_id"`
	Runtime        string                 `json:"runtime"`
	ModulePath     string                 `json:"module_path"`
	FunctionName   string                 `json:"function_name"`
	Priority       int                    `json:"priority"`
	Permissions    []string               `json:"permissions"`
	Config         map[string]interface{} `json:"config"`
}

type PreprocessChainRequest struct {
	ExecutionID     string                 `json:"execution_id"`
	Event           map[string]interface{} `json:"event"`
	Entrypoints     []Entrypoint           `json:"entrypoints"`
	DeadlineAt      string                 `json:"deadline_at"`
	ContentLeaseRef string                 `json:"content_lease_ref"`
}

type PostAvailableChainRequest struct {
	ExecutionID string                 `json:"execution_id"`
	Event       map[string]interface{} `json:"event"`
	Entrypoints []Entrypoint           `json:"entrypoints"`
}

type RuntimeChainResult struct {
	Status               string `json:"status"`
	ContentModified      bool   `json:"content_modified"`
	CandidateID          string `json:"candidate_id,omitempty"`
	CandidateChecksum    string `json:"candidate_checksum,omitempty"`
	CandidateSize        *int64 `json:"candidate_size,omitempty"`
	CompletedEntrypoints int    `json:"completed_entrypoints"`
	FailureCode          string `json:"failure_code,omitempty"`
	FailureSummary       string `json:"failure_summary,omitempty"`
}

type CapabilityExecutionRequest struct {
	ExecutionID string                 `json:"execution_id"`
	StepID      string                 `json:"step_id"`
	UserID      string                 `json:"user_id"`
	SpaceID     string                 `json:"space_id"`
	Input       map[string]interface{} `json:"input"`
	Entrypoint  Entrypoint             `json:"entrypoint"`
}

type CapabilityExecutionResult struct {
	Status         string                 `json:"status"`
	Output         map[string]interface{} `json:"output,omitempty"`
	FailureCode    string                 `json:"error_code,omitempty"`
	FailureSummary string                 `json:"error_summary,omitempty"`
}

// TestExecutionRequest 描述开发阶段的异步沙盒测试请求。
// [PLUGIN-TEST-001] 测试执行与正常事件执行共用 Runtime/Agent/Sandbox 安全边界，不能绕过权限校验。
type TestExecutionRequest struct {
	ExecutionID    string                 `json:"execution_id"`
	PluginID       string                 `json:"plugin_id"`
	VersionID      string                 `json:"version_id"`
	ScriptEntry    string                 `json:"script_entry"`
	TestEntrypoint string                 `json:"test_entrypoint"`
	UserID         string                 `json:"user_id"`
	SpaceID        string                 `json:"space_id"`
	Parameters     map[string]interface{} `json:"parameters"`
}

type TestExecutionAccepted struct {
	ExecutionID string `json:"execution_id"`
	Status      string `json:"status"`
}

type TestExecutionStatus struct {
	ExecutionID  string                 `json:"execution_id"`
	Status       string                 `json:"status"`
	Result       map[string]interface{} `json:"result,omitempty"`
	ErrorCode    string                 `json:"error_code,omitempty"`
	ErrorSummary string                 `json:"error_summary,omitempty"`
	StartedAt    string                 `json:"started_at,omitempty"`
	EndedAt      string                 `json:"ended_at,omitempty"`
}
