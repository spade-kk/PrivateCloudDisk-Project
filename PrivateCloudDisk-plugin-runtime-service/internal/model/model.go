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

// Entrypoint 描述一次执行意图（插件包分发统一改造后）：
// Runner 不再信任 ModulePath/FunctionName（旧字段保留 JSON 兼容但会被拒绝），
// 从 .pcdpkg manifest.yaml 解析真实 module/function/permissions。
//   - Event:      触发入口的事件类型（pcd.file.content.ready.v1 / pcd.file.available.v1）。
//   - Capability: ExecuteCapability 要调用的能力名（manifest exports.name）。
type Entrypoint struct {
	InstallationID string `json:"installation_id"`
	PluginID       string `json:"plugin_id"`
	VersionID      string `json:"version_id"`
	Event          string `json:"event,omitempty"`
	Capability     string `json:"capability,omitempty"`
	// ModulePath/FunctionName 已废弃：外部若继续传入，Runner 必须拒绝并提示 manifest 驱动。
	ModulePath   string                 `json:"module_path,omitempty"`
	FunctionName string                 `json:"function_name,omitempty"`
	Runtime      string                 `json:"runtime,omitempty"`
	Priority     int                    `json:"priority,omitempty"`
	Permissions  []string               `json:"permissions,omitempty"`
	Config       map[string]interface{} `json:"config"`
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
	// Output 携带最后一个已执行入口函数的序列化返回值（若无则为 nil）。
	// Logs 携带容器 stdout/stderr 的脱敏文本（插件 print / pycloud.log / runner.py /
	// restricted.py 输出与退出信息），便于调试与审计；两者均仅用于可观测性，
	// 不影响候选内容提交语义。
	Output map[string]interface{} `json:"output,omitempty"`
	Logs   string                 `json:"logs,omitempty"`
	// AuditTrails 只包含 Runtime Agent 在实例级 UDS 请求入口/响应出口记录的调用事实；
	// [CF-PLUGIN-UDS-001] 不从 SDK 文件或插件 stdout 推断，防止用户脚本伪造审计。
	// Automation 会将其绑定到插件中心稳定 execution_id。
	AuditTrails []RuntimeAuditRecord `json:"audit_trails,omitempty"`
}

// RuntimeAuditRecord 是 Runtime 与 Automation 之间的最小审计契约。
// 参数在 Runtime Agent 写入时脱敏，再在 Plugin Service 持久化前做第二次脱敏；SDK
// 不再被信任为审计事实来源。
type RuntimeAuditRecord struct {
	AuditID         string                 `json:"audit_id,omitempty"`
	ParentAuditID   string                 `json:"parent_audit_id,omitempty"`
	CapabilityKey   string                 `json:"capability_key"`
	CapabilityType  string                 `json:"capability_type"`
	SummaryTemplate string                 `json:"summary_template,omitempty"`
	TargetContext   map[string]interface{} `json:"target_context,omitempty"`
	InputParams     map[string]interface{} `json:"input_params,omitempty"`
	OutputResult    map[string]interface{} `json:"output_result,omitempty"`
	Status          string                 `json:"status"`
	DurationMs      int64                  `json:"duration_ms,omitempty"`
	RetryCount      int                    `json:"retry_count,omitempty"`
	ErrorCode       string                 `json:"error_code,omitempty"`
	ErrorSummary    string                 `json:"error_summary,omitempty"`
	Timestamp       string                 `json:"timestamp,omitempty"`
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
	// Logs 携带容器 stdout/stderr 的脱敏文本（与 RuntimeChainResult.Logs 语义一致）。
	Logs string `json:"logs,omitempty"`
	// AuditTrails 使工作流/插件能力调用也能拿到 Runtime Agent 生成的可信事实。
	// [CF-PLUGIN-UDS-004] 原有行为仅在链式执行结果中返回审计，导出能力执行会丢失
	// 已发生的调用；新行为在所有已创建 Session 的成功/失败结果中保留同一份脱敏记录。
	AuditTrails []RuntimeAuditRecord `json:"audit_trails,omitempty"`
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
