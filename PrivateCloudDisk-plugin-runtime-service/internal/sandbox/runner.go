package sandbox

import (
	"context"
	"crypto/md5"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"sort"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/audit"
	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/package"
	"privateclouddisk/plugin-runtime-service/internal/sanitize"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// BrokerClient 是 Runner 依赖的 Storage Broker 内容面接口；生产实现为 *broker.Client，
// 测试中可注入 httptest 驱动的假实现（6.8/7.5）。
type BrokerClient interface {
	// Exchange 把 MQ 中的一次性引用换成绑定 execution_id 的短期执行 Lease。
	Exchange(ctx context.Context, gateID, executionID, contentLeaseRef string, ttlSeconds int) (broker.ExchangedLease, error)
	// Download 读取预处理内容到 destination。
	Download(ctx context.Context, gateID, executionID, contentLease, destination string) error
	// DownloadActive 读取已激活最终内容（激活后路径）。
	DownloadActive(ctx context.Context, fileID, executionID, actorUserID, spaceID, destination string) error
	// Upload 提交候选内容并返回封存后的 Candidate。
	Upload(ctx context.Context, gateID, executionID, contentLease, source string) (broker.Candidate, error)
}

// PackageClient 是 Runner 依赖的插件包下载接口；生产实现为 *pkgclient.Client，
// 只接受 .pcdpkg（DownloadPcdpkg 强制扩展名/魔数/大小/哈希）。旧 Download 已废弃（2.19）。
type PackageClient interface {
	DownloadPcdpkg(ctx context.Context, versionID, destination string) error
}

// 预激活/激活 事件类型（设计文档 7.2 约定）。
const (
	EventContentReady     = "pcd.file.content.ready.v1"
	EventContentAvailable = "pcd.file.available.v1"
)

// legacyEntryError 说明旧的 module/function 驱动方式已被 manifest 取代（4.19）。
const manifestDrivenMsg = "外部入口已废弃：module/function 必须由 .pcdpkg manifest.yaml 提供"

type Runner struct {
	Config   config.Config
	Packages PackageClient
	Broker   BrokerClient
	Audit    *audit.Sink
	// Sessions owns one authenticated Unix socket per sandbox container instance.
	// [CF-PLUGIN-UDS-001] It replaces the former shared work-directory file relay.
	Sessions *uds.Manager
	// digestChecked 记录镜像摘要是否已在本进程内校验通过（2.24/3.5）。
	digestChecked atomic.Bool
	// digestMu 让摘要校验成为原子一次操作：并发调用只执行一次 docker inspect（4.8）。
	digestMu sync.Mutex
}

type sandboxResult struct {
	Success  bool                   `json:"success"`
	Modified bool                   `json:"modified"`
	Error    string                 `json:"error"`
	Output   map[string]interface{} `json:"output"`
}

// execLimits 是单入口生效的执行限制（manifest limits ∩ 全局上限）。
type execLimits struct {
	timeout     time.Duration
	memoryBytes int64
}

// manifestStep 是从 .pcdpkg manifest 解析出的一个可执行步骤（4.3/4.4）。
type manifestStep struct {
	module      string // 相对包根，如 src/main.py
	function    string
	permissions []string
	event       string
	capability  string
	priority    int
}

var safeID = regexp.MustCompile(`^[0-9A-Za-z_-]{1,128}$`)

// maxResultLogs 是结果模型 Logs 字段的单条日志上限（字节），脱敏后应用；
// 与容器侧 LogLimitBytes 截断构成双层防护，防止日志放大。
const maxResultLogs = 64 * 1024

// ---------------------------------------------------------------------------
// manifest 驱动解析

// hasLegacyEntrypoint 检测旧式 module/function 驱动请求（4.19 拒绝）。
func hasLegacyEntrypoint(entrypoints []model.Entrypoint) bool {
	for _, entry := range entrypoints {
		if entry.ModulePath != "" || entry.FunctionName != "" {
			return true
		}
	}
	return false
}

// requestedEvents 提取请求指定的事件类型集合。
func requestedEvents(entrypoints []model.Entrypoint) []string {
	out := []string{}
	for _, entry := range entrypoints {
		if entry.Event != "" && !contains(out, entry.Event) {
			out = append(out, entry.Event)
		}
	}
	return out
}

// resolveEventPlan 从 manifest 选择并排序事件入口（4.6/4.16：manifest 顺序、priority 升序）。
func resolveEventPlan(manifest *pkg.Manifest, wanted []string) []manifestStep {
	steps := []manifestStep{}
	for _, event := range manifest.Entrypoints.Events {
		if len(wanted) > 0 && !contains(wanted, event.Event) {
			continue
		}
		steps = append(steps, manifestStep{
			module: event.Module, function: event.Function,
			permissions: event.Permissions, event: event.Event, priority: event.Priority,
		})
	}
	// 4.16：多入口链按 priority 升序执行（稳定排序保持 manifest 声明顺序）。
	sort.SliceStable(steps, func(i, j int) bool { return steps[i].priority < steps[j].priority })
	return steps
}

// resolveExportStep 从 manifest exports 匹配能力（4.7）。
func resolveExportStep(manifest *pkg.Manifest, capability string) (manifestStep, bool) {
	export, ok := manifest.ExportByName(capability)
	if !ok {
		return manifestStep{}, false
	}
	return manifestStep{
		module: export.Module, function: export.Function,
		permissions: export.Permissions, capability: export.Name,
	}, true
}

// applyLimits 用 manifest limits 收敛全局限制（只降不升，4.10）。
func (r *Runner) applyLimits(manifest *pkg.Manifest) execLimits {
	limits := execLimits{timeout: r.Config.ExecutionTimeout, memoryBytes: r.Config.MemoryBytes}
	if manifest.Limits.TimeoutSeconds > 0 {
		manifestTimeout := time.Duration(manifest.Limits.TimeoutSeconds) * time.Second
		if manifestTimeout < limits.timeout {
			limits.timeout = manifestTimeout
		}
	}
	if manifest.Limits.MemoryMB > 0 {
		manifestMemory := int64(manifest.Limits.MemoryMB) * 1024 * 1024
		if manifestMemory < limits.memoryBytes {
			limits.memoryBytes = manifestMemory
		}
	}
	return limits
}

// loadPackage 下载并解析 .pcdpkg 到 pluginRoot（4.15）。错误统一为结构化包错误。
func (r *Runner) loadPackage(ctx context.Context, stepRoot, versionID string) (*pkg.Parsed, string, error) {
	packagePath := filepath.Join(stepRoot, "plugin.pcdpkg")
	pluginRoot := filepath.Join(stepRoot, "plugin")
	if err := os.MkdirAll(stepRoot, 0o700); err != nil {
		return nil, "", err
	}
	if err := r.Packages.DownloadPcdpkg(ctx, versionID, packagePath); err != nil {
		return nil, "", err
	}
	parsed, err := pkg.Parse(packagePath, pluginRoot, pkg.Options{
		MaxExpandedBytes: r.Config.PackageMaxBytes,
	})
	if err != nil {
		return nil, "", err
	}
	return parsed, pluginRoot, nil
}

// ---------------------------------------------------------------------------

func (r *Runner) Execute(parent context.Context, request model.PreprocessChainRequest) model.RuntimeChainResult {
	if !safeID.MatchString(request.ExecutionID) {
		return failed("RUNTIME_REQUEST_INVALID", "execution_id 格式无效", 0)
	}
	data := eventData(request.Event)
	gateID := stringValue(data["gate_id"])
	if !safeID.MatchString(gateID) || request.ContentLeaseRef == "" {
		return failed("RUNTIME_REQUEST_INVALID", "Gate 或内容 Lease 缺失", 0)
	}
	if r.Broker == nil {
		return failed("RUNTIME_CONFIG_INVALID", "内容 Broker 客户端未配置", 0)
	}
	if r.Packages == nil {
		return failed("RUNTIME_CONFIG_INVALID", "插件包客户端未配置", 0)
	}
	if hasLegacyEntrypoint(request.Entrypoints) {
		return failed("MANIFEST_DRIVEN_REQUIRED", manifestDrivenMsg, 0)
	}
	// 6.5：请求声明的运行时（若提供）必须与 manifest 允许的 PYTHON_3_11 一致。
	for _, entry := range request.Entrypoints {
		if entry.Runtime != "" && entry.Runtime != "PYTHON_3_11" {
			return failed("RUNTIME_POLICY_REJECTED", "插件运行时不受支持", 0)
		}
	}
	deadline, err := time.Parse(time.RFC3339Nano, request.DeadlineAt)
	if err != nil || !deadline.After(time.Now()) {
		return model.RuntimeChainResult{
			Status: "timeout", FailureCode: "PLUGIN_RUNTIME_TIMEOUT",
			FailureSummary: "预处理执行截止时间已到",
		}
	}
	timeout := r.Config.ExecutionTimeout
	if remaining := time.Until(deadline); remaining < timeout {
		timeout = remaining
	}
	ctx, cancel := context.WithTimeout(parent, timeout)
	defer cancel()
	exchanged, err := r.Broker.Exchange(
		ctx, gateID, request.ExecutionID, request.ContentLeaseRef, int(timeout.Seconds()),
	)
	if err != nil {
		return failed("CONTENT_LEASE_EXCHANGE_FAILED", summarize(err), 0)
	}

	root := filepath.Join(r.Config.WorkRoot, request.ExecutionID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		return failed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离工作区", 0)
	}
	defer os.RemoveAll(root)
	input := filepath.Join(root, "input-0.bin")
	if err := r.Broker.Download(
		ctx, gateID, request.ExecutionID, exchanged.ExecutionLease, input,
	); err != nil {
		return failed("CONTENT_LEASE_READ_FAILED", summarize(err), 0)
	}

	// 首个步骤：下载并解析插件包，解析事件计划（用于执行与 limits）。
	first := request.Entrypoints
	if len(first) == 0 {
		first = []model.Entrypoint{model.Entrypoint{Event: EventContentReady}}
	}
	wanted := requestedEvents(first)
	if len(wanted) == 0 {
		wanted = []string{EventContentReady}
	}
	stepRoot := filepath.Join(root, "step-000")
	parsed, pluginRoot, pkgErr := r.loadPackage(ctx, stepRoot, first[0].VersionID)
	if pkgErr != nil {
		return failed(execFailedCode(pkgErr), summarize(pkgErr), 0)
	}
	plan := resolveEventPlan(parsed.Manifest, wanted)
	if len(plan) == 0 {
		return failed("ENTRYPOINT_MISSING", "manifest 未声明匹配的事件入口", 0)
	}
	limits := r.applyLimits(parsed.Manifest)

	currentInput := input
	modified := false
	completed := 0
	var logsBuilder strings.Builder
	var lastOutput map[string]interface{}
	var auditTrails []model.RuntimeAuditRecord
	for index, step := range plan {
		if ctx.Err() != nil {
			return model.RuntimeChainResult{
				Status: "timeout", ContentModified: false,
				CompletedEntrypoints: completed,
				FailureCode:          "PLUGIN_RUNTIME_TIMEOUT",
				FailureSummary:       "插件执行超过预处理截止时间",
				Logs:                 asLogs(logsBuilder.String()),
				AuditTrails:          auditTrails,
			}
		}
		if !contains(step.permissions, "file.content.write_pre_activation") {
			return failed("RUNTIME_POLICY_REJECTED", "事件入口缺少预激活写权限", completed)
		}
		stepResult, stepLogs, stepAudits, err := r.runPlanStep(ctx, request.ExecutionID, index, root, pluginRoot,
			currentInput, step, first[0], parsed.Manifest, limits, request.Event, false)
		auditTrails = append(auditTrails, stepAudits...)
		if err != nil {
			if ctx.Err() != nil {
				return model.RuntimeChainResult{
					Status: "timeout", CompletedEntrypoints: completed,
					FailureCode: "PLUGIN_RUNTIME_TIMEOUT", FailureSummary: "插件执行超时，容器已强制终止",
					Logs:        asLogs(joinLogs(logsBuilder.String(), stepLogs)),
					AuditTrails: auditTrails,
				}
			}
			return withAuditTrails(failedWithLogs("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, stepLogs), completed,
				[]string{logsBuilder.String(), stepLogs}), auditTrails)
		}
		if !stepResult.Success {
			return withAuditTrails(failedWithLogs("PLUGIN_EXECUTION_FAILED", redact(stepResult.Error), completed,
				[]string{logsBuilder.String(), stepLogs}), auditTrails)
		}
		completed++
		if stepLogs != "" {
			logsBuilder.WriteString(stepLogs)
			logsBuilder.WriteString("\n")
		}
		if stepResult.Output != nil {
			lastOutput = stepResult.Output
		}
		output := filepath.Join(root, fmt.Sprintf("step-%03d", index), "work", "output.bin")
		if stepResult.Modified {
			stat, statErr := os.Stat(output)
			if statErr != nil || stat.Size() <= 0 || stat.Size() > r.Config.CandidateMaxBytes {
				return withAuditTrails(failedWithLogs("PLUGIN_OUTPUT_INVALID", "插件声明修改但输出内容无效", completed,
					[]string{logsBuilder.String()}), auditTrails)
			}
			currentInput = output
			modified = true
		}
	}
	if !modified {
		return model.RuntimeChainResult{
			Status: "success", ContentModified: false,
			CompletedEntrypoints: completed, Output: lastOutput, Logs: asLogs(logsBuilder.String()), AuditTrails: auditTrails,
		}
	}
	candidate, err := r.Broker.Upload(
		ctx, gateID, request.ExecutionID, exchanged.ExecutionLease, currentInput,
	)
	if err != nil {
		return withAuditTrails(failedWithLogs("CANDIDATE_COMMIT_FAILED", summarize(err), completed,
			[]string{logsBuilder.String()}), auditTrails)
	}
	size := candidate.Size
	return model.RuntimeChainResult{
		Status: "success", ContentModified: true,
		CandidateID: candidate.ID, CandidateChecksum: candidate.Checksum,
		CandidateSize: &size, CompletedEntrypoints: completed,
		Output: lastOutput, Logs: asLogs(logsBuilder.String()), AuditTrails: auditTrails,
	}
}

// joinLogs 把既有累计日志与当前步骤日志拼接为单条文本（供结果 Logs 字段）。
func joinLogs(acc, current string) string {
	if acc == "" {
		return current
	}
	if current == "" {
		return acc
	}
	return acc + "\n" + current
}

// runPlanStep 为 manifest 解析出的单入口创建步骤目录并执行容器。
// contentFrozen=true 表示激活后路径（禁止写回）。
func (r *Runner) runPlanStep(
	ctx context.Context, executionID string, index int, stepsRoot, pluginRoot string,
	input string, step manifestStep, request model.Entrypoint, manifest *pkg.Manifest,
	limits execLimits, eventDataValue map[string]interface{}, contentFrozen bool,
) (sandboxResult, string, []model.RuntimeAuditRecord, error) {
	stepRoot := filepath.Join(stepsRoot, fmt.Sprintf("step-%03d", index))
	workRoot := filepath.Join(stepRoot, "work")
	contextRoot := filepath.Join(stepRoot, "context")
	if err := os.MkdirAll(workRoot, 0o770); err != nil {
		return sandboxResult{}, "", nil, errors.New("无法创建沙箱输出目录")
	}
	if err := os.MkdirAll(contextRoot, 0o700); err != nil {
		return sandboxResult{}, "", nil, errors.New("无法创建沙箱上下文目录")
	}
	// 4.15：确认 manifest 模块存在于解压树。
	modulePath := filepath.Join(pluginRoot, filepath.FromSlash(step.module))
	if _, err := os.Stat(modulePath); err != nil {
		return sandboxResult{}, "", nil, fmt.Errorf("manifest 入口模块缺失：%s", step.module)
	}
	contextPayload := map[string]interface{}{
		"execution_id":     executionID,
		"plugin_id":        manifest.Plugin.ID,
		"plugin_name":      manifest.Plugin.Name,
		"plugin_type":      manifest.Plugin.Type,
		"version_id":       manifest.Plugin.Version,
		"installation_id":  request.InstallationID,
		"manifest_version": manifest.ManifestVersion,
		"permissions":      step.permissions,
		"config":           request.Config,
		"event":            eventDataValue,
		"event_type":       step.event,
		"content_frozen":   contentFrozen,
	}
	contextBytes, _ := json.Marshal(contextPayload)
	if err := os.WriteFile(
		filepath.Join(contextRoot, "context.json"), contextBytes, 0o400,
	); err != nil {
		return sandboxResult{}, "", nil, errors.New("无法写入沙箱上下文")
	}
	entry := model.Entrypoint{
		PluginID: manifest.Plugin.ID, VersionID: manifest.Plugin.Version,
		InstallationID: request.InstallationID, Event: step.event,
		Permissions: step.permissions,
	}
	if r.Sessions == nil {
		return sandboxResult{}, "", nil, errors.New("RUNTIME_SOCKET_UNAVAILABLE: Unix Socket session manager is not configured")
	}
	identity := uds.SessionContext{
		PluginID: manifest.Plugin.ID, VersionID: manifest.Plugin.Version, InstallationID: request.InstallationID,
		UserID:  eventIdentity(eventDataValue, "actor_user_id", "user_id"),
		SpaceID: eventIdentity(eventDataValue, "space_id"), ExecutionID: executionID,
		StepID: fmt.Sprintf("%03d", index), ParentAuditID: executionAuditRootID(executionID),
		DeclaredPermissions: append([]string(nil), step.permissions...),
		// Entry point permissions originate from Plugin Service's match result,
		// where manifest permissions and installation grants have already been
		// intersected. Preserve that trusted grant snapshot separately from the
		// manifest declaration for Capability Hub's final intersection.
		GrantedPermissions: append([]string(nil), request.Permissions...),
	}
	session, err := r.Sessions.CreateSession(identity)
	if err != nil {
		return sandboxResult{}, "", nil, fmt.Errorf("RUNTIME_SOCKET_UNAVAILABLE: %w", err)
	}
	defer session.Close()
	result, logs, err := r.runContainerWithSession(ctx, executionID, index, pluginRoot, input,
		workRoot, contextRoot, entry, step.module, step.function, limits, session)
	return result, logs, session.AuditTrails(), err
}

// execFailedCode 把 .pcdpkg 解析错误映射到统一失败码。
func execFailedCode(err error) string {
	var parseErr *pkg.ParseError
	if errors.As(err, &parseErr) {
		switch parseErr.Kind {
		case pkg.ErrManifestMissing, pkg.ErrManifestInvalid, pkg.ErrManifestVersion,
			pkg.ErrPluginID, pkg.ErrPluginType, pkg.ErrPluginVersion, pkg.ErrRuntime,
			pkg.ErrEntrypoint, pkg.ErrExport, pkg.ErrPermission, pkg.ErrLimit, pkg.ErrStructure:
			return "PLUGIN_PACKAGE_INVALID"
		case pkg.ErrSecurity, pkg.ErrSensitiveFile, pkg.ErrPathEscape:
			return "PLUGIN_PACKAGE_REJECTED"
		case pkg.ErrResourceLimit:
			return "PLUGIN_PACKAGE_EXCEEDED"
		}
	}
	if errors.Is(err, context.DeadlineExceeded) {
		return "PLUGIN_RUNTIME_TIMEOUT"
	}
	return "PLUGIN_PACKAGE_FETCH_FAILED"
}

func (r *Runner) ExecutePostAvailable(
	parent context.Context,
	request model.PostAvailableChainRequest,
) model.RuntimeChainResult {
	if !safeID.MatchString(request.ExecutionID) {
		return failed("RUNTIME_REQUEST_INVALID", "execution_id 格式无效", 0)
	}
	data := eventData(request.Event)
	fileID := stringValue(data["file_id"])
	actorUserID := stringValue(request.Event["actor_user_id"])
	spaceID := stringValue(request.Event["space_id"])
	if !safeID.MatchString(strings.ReplaceAll(fileID, "-", "")) || actorUserID == "" {
		return failed("RUNTIME_REQUEST_INVALID", "file.available 执行上下文无效", 0)
	}
	if r.Broker == nil {
		return failed("RUNTIME_CONFIG_INVALID", "内容 Broker 客户端未配置", 0)
	}
	if r.Packages == nil {
		return failed("RUNTIME_CONFIG_INVALID", "插件包客户端未配置", 0)
	}
	if hasLegacyEntrypoint(request.Entrypoints) {
		return failed("MANIFEST_DRIVEN_REQUIRED", manifestDrivenMsg, 0)
	}
	ctx, cancel := context.WithTimeout(parent, r.Config.ExecutionTimeout)
	defer cancel()

	root := filepath.Join(r.Config.WorkRoot, request.ExecutionID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		return failed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离工作区", 0)
	}
	defer os.RemoveAll(root)
	input := filepath.Join(root, "active-content.bin")

	first := request.Entrypoints
	if len(first) == 0 {
		first = []model.Entrypoint{model.Entrypoint{Event: EventContentAvailable}}
	}
	wanted := requestedEvents(first)
	if len(wanted) == 0 {
		wanted = []string{EventContentAvailable}
	}
	stepRoot := filepath.Join(root, "step-000")
	parsed, pluginRoot, pkgErr := r.loadPackage(ctx, stepRoot, first[0].VersionID)
	if pkgErr != nil {
		return failed(execFailedCode(pkgErr), summarize(pkgErr), 0)
	}
	plan := resolveEventPlan(parsed.Manifest, wanted)
	if len(plan) == 0 {
		return failed("ENTRYPOINT_MISSING", "manifest 未声明匹配的激活后事件入口", 0)
	}
	limits := r.applyLimits(parsed.Manifest)

	// 激活后是否需要读取最终内容：任一入口具备 file.content.read。
	requiresRead := false
	for _, step := range plan {
		if contains(step.permissions, "file.content.read") {
			requiresRead = true
		}
	}
	if requiresRead {
		if err := r.Broker.DownloadActive(
			ctx, fileID, request.ExecutionID, actorUserID, spaceID, input,
		); err != nil {
			return failed("ACTIVE_CONTENT_READ_FAILED", summarize(err), 0)
		}
	} else if err := os.WriteFile(input, []byte{}, 0o400); err != nil {
		return failed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离输入", 0)
	}

	completed := 0
	var logsBuilder strings.Builder
	var lastOutput map[string]interface{}
	var auditTrails []model.RuntimeAuditRecord
	for index, step := range plan {
		if ctx.Err() != nil {
			return model.RuntimeChainResult{
				Status: "timeout", CompletedEntrypoints: completed,
				FailureCode: "PLUGIN_RUNTIME_TIMEOUT", FailureSummary: "激活后插件执行超时",
				Logs: asLogs(logsBuilder.String()),
			}
		}
		if contains(step.permissions, "file.content.write_pre_activation") {
			return failed("CONTENT_FROZEN", "file.available 入口禁止修改文件内容", completed)
		}
		stepResult, stepLogs, stepAudits, err := r.runPlanStep(ctx, request.ExecutionID, index, root, pluginRoot,
			input, step, first[0], parsed.Manifest, limits, request.Event, true)
		auditTrails = append(auditTrails, stepAudits...)
		if err != nil {
			if ctx.Err() != nil {
				return model.RuntimeChainResult{
					Status: "timeout", CompletedEntrypoints: completed,
					FailureCode: "PLUGIN_RUNTIME_TIMEOUT", FailureSummary: "激活后插件执行超时",
					Logs: asLogs(joinLogs(logsBuilder.String(), stepLogs)), AuditTrails: auditTrails,
				}
			}
			return withAuditTrails(failedWithLogs("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, stepLogs), completed,
				[]string{logsBuilder.String(), stepLogs}), auditTrails)
		}
		if !stepResult.Success {
			return withAuditTrails(failedWithLogs("PLUGIN_EXECUTION_FAILED", redact(stepResult.Error), completed,
				[]string{logsBuilder.String(), stepLogs}), auditTrails)
		}
		if stepResult.Modified {
			return withAuditTrails(failedWithLogs("CONTENT_FROZEN", "激活后插件产生了非法内容写入", completed,
				[]string{logsBuilder.String(), stepLogs}), auditTrails)
		}
		completed++
		if stepLogs != "" {
			logsBuilder.WriteString(stepLogs)
			logsBuilder.WriteString("\n")
		}
		if stepResult.Output != nil {
			lastOutput = stepResult.Output
		}
	}
	return model.RuntimeChainResult{
		Status: "success", ContentModified: false, CompletedEntrypoints: completed,
		Output: lastOutput, Logs: asLogs(logsBuilder.String()), AuditTrails: auditTrails,
	}
}

// ExecuteCapability 在同一 gVisor/容器安全边界中执行云插件导出的能力函数（4.7）。
func (r *Runner) ExecuteCapability(
	parent context.Context,
	request model.CapabilityExecutionRequest,
) model.CapabilityExecutionResult {
	if !safeID.MatchString(request.ExecutionID) || !safeID.MatchString(request.StepID) {
		return capabilityFailed("RUNTIME_REQUEST_INVALID", "执行或步骤标识格式无效")
	}
	entrypoint := request.Entrypoint
	if entrypoint.ModulePath != "" || entrypoint.FunctionName != "" {
		return capabilityFailed("MANIFEST_DRIVEN_REQUIRED", manifestDrivenMsg)
	}
	if entrypoint.Capability == "" {
		return capabilityFailed("RUNTIME_POLICY_REJECTED", "能力调用必须指定 manifest exports 中的能力名")
	}
	if r.Packages == nil {
		return capabilityFailed("RUNTIME_CONFIG_INVALID", "插件包客户端未配置")
	}
	ctx, cancel := context.WithTimeout(parent, r.Config.ExecutionTimeout)
	defer cancel()
	root := filepath.Join(r.Config.WorkRoot, request.ExecutionID+"-"+request.StepID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离工作区")
	}
	defer os.RemoveAll(root)
	stepRoot := filepath.Join(root, "step-000")
	parsed, pluginRoot, pkgErr := r.loadPackage(ctx, stepRoot, entrypoint.VersionID)
	if pkgErr != nil {
		return capabilityFailed(execFailedCode(pkgErr), summarize(pkgErr))
	}
	step, ok := resolveExportStep(parsed.Manifest, entrypoint.Capability)
	if !ok {
		return capabilityFailed("EXPORT_NOT_FOUND", "manifest 未声明该能力导出")
	}
	if contains(step.permissions, "file.content.write_pre_activation") {
		return capabilityFailed("CONTENT_FROZEN", "工作流能力禁止使用预激活文件写权限")
	}
	limits := r.applyLimits(parsed.Manifest)

	inputPath := filepath.Join(root, "empty-input.bin")
	if err := os.WriteFile(inputPath, []byte{}, 0o400); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离输入")
	}
	contextPayload := map[string]interface{}{
		"execution_id":    request.ExecutionID,
		"step_id":         request.StepID,
		"user_id":         request.UserID,
		"space_id":        request.SpaceID,
		"plugin_id":       parsed.Manifest.Plugin.ID,
		"version_id":      parsed.Manifest.Plugin.Version,
		"installation_id": entrypoint.InstallationID,
		"capability":      step.capability,
		"permissions":     step.permissions,
		"config":          entrypoint.Config,
		"input":           request.Input,
		"invocation":      "workflow_capability",
		"content_frozen":  true,
	}
	contextBytes, _ := json.Marshal(contextPayload)
	if err := os.MkdirAll(filepath.Join(stepRoot, "context"), 0o700); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱上下文目录")
	}
	if err := os.MkdirAll(filepath.Join(stepRoot, "work"), 0o770); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱输出目录")
	}
	if err := os.WriteFile(
		filepath.Join(stepRoot, "context", "context.json"), contextBytes, 0o400,
	); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法写入沙箱上下文")
	}
	entry := model.Entrypoint{
		PluginID: parsed.Manifest.Plugin.ID, VersionID: parsed.Manifest.Plugin.Version,
		InstallationID: entrypoint.InstallationID, Capability: step.capability,
		Permissions: step.permissions,
	}
	// 校验 manifest 模块存在（4.15）。
	modulePath := filepath.Join(pluginRoot, filepath.FromSlash(step.module))
	if _, err := os.Stat(modulePath); err != nil {
		return capabilityFailed("PLUGIN_PACKAGE_INVALID", "manifest 能力模块缺失")
	}
	if r.Sessions == nil {
		return capabilityFailed("RUNTIME_SOCKET_UNAVAILABLE", "Unix Socket session manager is not configured")
	}
	session, sessionErr := r.Sessions.CreateSession(uds.SessionContext{
		PluginID: parsed.Manifest.Plugin.ID, VersionID: parsed.Manifest.Plugin.Version,
		InstallationID: entrypoint.InstallationID, UserID: request.UserID, SpaceID: request.SpaceID,
		ExecutionID: request.ExecutionID, StepID: request.StepID,
		// CloudFlow's workflow step identifier is already the stable parent edge
		// for a plugin capability invocation; do not accept a parent ID from SDK.
		ParentAuditID:       request.StepID,
		DeclaredPermissions: append([]string(nil), step.permissions...),
		// ExecuteCapability is an authenticated internal endpoint. Its entrypoint
		// permission snapshot is supplied by the trusted caller; the plugin never
		// sees or controls it.
		GrantedPermissions: append([]string(nil), entrypoint.Permissions...),
	})
	if sessionErr != nil {
		return capabilityFailed("RUNTIME_SOCKET_UNAVAILABLE", summarize(sessionErr))
	}
	defer session.Close()
	result, logs, err := r.runContainerWithSession(ctx, request.ExecutionID, 0, pluginRoot,
		inputPath, filepath.Join(stepRoot, "work"), filepath.Join(stepRoot, "context"),
		entry, step.module, step.function, limits, session)
	if err != nil {
		if ctx.Err() != nil {
			return withCapabilityAuditTrails(capabilityFailed("PLUGIN_RUNTIME_TIMEOUT", "插件能力执行超时，容器已强制终止", logs), session.AuditTrails())
		}
		return withCapabilityAuditTrails(capabilityFailed("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, logs), logs), session.AuditTrails())
	}
	if !result.Success {
		return withCapabilityAuditTrails(capabilityFailed("PLUGIN_EXECUTION_FAILED", redact(result.Error), logs), session.AuditTrails())
	}
	if result.Modified {
		return withCapabilityAuditTrails(capabilityFailed("CONTENT_FROZEN", "工作流能力不得直接写入预激活文件输出", logs), session.AuditTrails())
	}
	if result.Output == nil {
		result.Output = map[string]interface{}{}
	}
	return withCapabilityAuditTrails(model.CapabilityExecutionResult{
		Status: "success", Output: result.Output, Logs: asLogs(logs),
	}, session.AuditTrails())
}

// ---------------------------------------------------------------------------

// runContainer 在独立容器内运行插件步骤，并保证：镜像摘要门禁（2.24/3.5/8.5）、
// 幂等重试上限（8.13）、失败强制清理（2.19）、执行审计（7.19/8.2）。
func (r *Runner) runContainer(
	ctx context.Context,
	executionID string,
	step int,
	pluginRoot, inputPath, workRoot, contextRoot string,
	entrypoint model.Entrypoint,
	module, function string,
	limits execLimits,
) (sandboxResult, string, error) {
	return r.runContainerWithSession(ctx, executionID, step, pluginRoot, inputPath, workRoot, contextRoot,
		entrypoint, module, function, limits, nil)
}

// runContainerWithSession keeps the container runner reusable for test probes
// while production execution always receives a server-created UDS session.
func (r *Runner) runContainerWithSession(
	ctx context.Context,
	executionID string,
	step int,
	pluginRoot, inputPath, workRoot, contextRoot string,
	entrypoint model.Entrypoint,
	module, function string,
	limits execLimits,
	session *uds.Session,
) (sandboxResult, string, error) {
	if err := r.verifySandboxImageDigestOnce(ctx); err != nil {
		r.audit("container_digest_rejected", executionID, entrypoint, "failed", err)
		return sandboxResult{}, "", err
	}
	attempts := 1 + maxInt(0, minInt(r.Config.MaxExecutionRetries, 3))
	var lastErr error
	var logs string
	for attempt := 0; attempt < attempts; attempt++ {
		if ctx.Err() != nil {
			return sandboxResult{}, logs, ctx.Err()
		}
		args := r.containerArgsWithSession(executionID, step, attempt, pluginRoot, inputPath, workRoot, contextRoot,
			entrypoint, module, function, limits, session)
		containerName := args[3]
		buffer := NewLimitedBuffer(r.Config.LogLimitBytes)
		command := exec.CommandContext(ctx, r.Config.DockerBinary, args...)
		command.Stdout = buffer
		command.Stderr = buffer
		r.audit("container_started", executionID, entrypoint, "started", nil)
		err := command.Run()
		if err == nil {
			result, parseErr, _ := readSandboxResult(workRoot)
			r.audit("container_finished", executionID, entrypoint, outcome(parseErr), parseErr)
			// 成功路径同样返回容器 stdout/stderr（插件 print / pycloud.log /
			// runner.py、restricted.py 输出），而不仅是 result.json；否则插件日志被丢弃。
			return result, buffer.String(), parseErr
		}
		lastErr = err
		logs = buffer.String()
		r.audit("container_failed", executionID, entrypoint, "failed", err)
		// CommandContext 只终止 docker CLI；额外删除可避免失联容器继续运行（2.19）。
		forceRemoveContainer(r.Config.DockerBinary, containerName)
		if attempt+1 < attempts {
			continue
		}
	}
	return sandboxResult{}, logs, lastErr
}

// containerArgs 纯函数：构造 docker run 参数，便于单元测试镜像摘要/命名空间/资源门禁。
func (r *Runner) containerArgs(
	executionID string,
	step, attempt int,
	pluginRoot, inputPath, workRoot, contextRoot string,
	entrypoint model.Entrypoint,
	module, function string,
	limits execLimits,
) []string {
	return r.containerArgsWithSession(executionID, step, attempt, pluginRoot, inputPath, workRoot, contextRoot,
		entrypoint, module, function, limits, nil)
}

// containerArgsWithSession injects the per-instance socket mount and runner
// argv credentials. The token is deliberately absent from environment/context
// mounts, preventing accidental shell/log/context exposure.
func (r *Runner) containerArgsWithSession(
	executionID string,
	step, attempt int,
	pluginRoot, inputPath, workRoot, contextRoot string,
	entrypoint model.Entrypoint,
	module, function string,
	limits execLimits,
	session *uds.Session,
) []string {
	containerName := fmt.Sprintf("pcd-plugin-%s-%03d-%d", strings.ToLower(executionID), step, attempt)
	hostname := sbxHostname(executionID, step, attempt)
	memory := r.Config.MemoryBytes
	if limits.memoryBytes > 0 && limits.memoryBytes < memory {
		memory = limits.memoryBytes
	}
	args := []string{
		"run", "--rm", "--name", containerName,
		// 追踪与清理标签（2.18）。
		"--label", "plugin-execution-id=" + executionID,
		"--label", "plugin-step-id=" + fmt.Sprintf("%03d", step),
		"--label", "plugin-version-id=" + entrypoint.VersionID,
		"--label", "pcd-platform=sandbox",
		// 不与宿主共享任何命名空间（3.1/3.7/3.19）。
		"--hostname", hostname,
		"--runtime", r.Config.SandboxRuntime,
		"--network", r.Config.SandboxNetwork,
		"--read-only",
		"--ipc=private", "--cgroupns=private",
		// 资源边界（2.2/2.3/2.5/2.6；manifest limits.memory_mb 覆盖）。
		"--cpus", r.Config.CPUs,
		"--memory", fmt.Sprintf("%d", memory),
		"--memory-swap", fmt.Sprintf("%d", memory),
		"--pids-limit", fmt.Sprintf("%d", r.Config.PidsLimit),
		"--ulimit", "nofile=128:128",
		"--stop-timeout", "2",
		"--cap-drop", "ALL",
		"--security-opt", "no-new-privileges",
		"--user", r.Config.SandboxUser,
		"--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=16777216",
		"--tmpfs", "/dev/shm:rw,noexec,nosuid,nodev,size=4194304",
		"--mount", "type=bind,src=" + pluginRoot + ",dst=/workspace/plugin,readonly",
		"--mount", "type=bind,src=" + inputPath + ",dst=/workspace/input/content.bin,readonly",
		"--mount", "type=bind,src=" + workRoot + ",dst=/workspace/work",
		"--mount", "type=bind,src=" + contextRoot + ",dst=/workspace/context,readonly",
		"-e", "PCD_MODULE_PATH=/workspace/plugin/" + module,
		"-e", "PCD_FUNCTION_NAME=" + function,
		"-e", "PCD_CONTEXT_PATH=/workspace/context/context.json",
		// 受限 Python 层开关：默认开启；生产模式由 config.Load() 强制开启。
		"-e", fmt.Sprintf("PCD_RESTRICTED_PYTHON=%d", boolInt(!r.Config.DisableRestrictedPython)),
		r.Config.SandboxImage,
	}
	if session != nil {
		imageIndex := len(args) - 1
		args = append(args[:imageIndex], append([]string{
			"--mount", "type=bind,src=" + session.SocketPath + ",dst=/runtime/runtime.sock,readonly",
		}, args[imageIndex:]...)...)
		args = append(args, "--pcd-instance-id", session.ID, "--pcd-instance-token", session.Token)
	}
	if r.Config.UserNamespaceRemap {
		args = append(args[:len(args)-1], "--userns-remap", "default", args[len(args)-1])
	}
	if r.Config.SeccompProfile != "" {
		args = append(args[:len(args)-1], "--security-opt", "seccomp="+r.Config.SeccompProfile, args[len(args)-1])
	}
	if r.Config.AppArmorProfile != "" {
		args = append(args[:len(args)-1], "--security-opt", "apparmor="+r.Config.AppArmorProfile, args[len(args)-1])
	}
	return args
}

// VerifyImageDigest 供启动门禁与运行前强制校验镜像摘要（2.24/8.5）。
func (r *Runner) VerifyImageDigest(ctx context.Context) error {
	return r.verifySandboxImageDigestOnce(ctx)
}

// verifySandboxImageDigestOnce 校验镜像 RepoDigests 包含配置的 sha256 摘要（2.24/8.5），每个进程只查一次。
func (r *Runner) verifySandboxImageDigestOnce(ctx context.Context) error {
	if !r.Config.RequireSandboxDigest {
		return nil
	}
	digest := r.Config.SandboxImageDigest
	if !strings.HasPrefix(digest, "sha256:") {
		return fmt.Errorf("镜像摘要配置无效，已拒绝启动沙箱")
	}
	r.digestMu.Lock()
	defer r.digestMu.Unlock()
	if r.digestChecked.Load() {
		return nil
	}
	limit, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	output, err := exec.CommandContext(
		limit, r.Config.DockerBinary,
		"image", "inspect", "--format", "{{.RepoDigests}}", r.Config.SandboxImage,
	).Output()
	if err != nil {
		return fmt.Errorf("镜像摘要校验失败：无法检查镜像")
	}
	if !strings.Contains(string(output), digest) {
		return fmt.Errorf("镜像不匹配配置摘要 %s，已拒绝启动沙箱", digest)
	}
	r.digestChecked.Store(true)
	return nil
}

func forceRemoveContainer(dockerBinary, containerName string) {
	cleanup, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	_ = exec.CommandContext(cleanup, dockerBinary, "rm", "-f", containerName).Run()
}

func readSandboxResult(workRoot string) (sandboxResult, error, string) {
	const maxResultBytes = 1 * 1024 * 1024
	path := filepath.Join(workRoot, "result.json")
	file, err := os.Open(path)
	if err != nil {
		return sandboxResult{}, fmt.Errorf("沙箱未生成 result.json（%w）", err), ""
	}
	defer file.Close()
	limited := io.LimitReader(file, maxResultBytes+1)
	data, err := io.ReadAll(limited)
	if err != nil {
		return sandboxResult{}, err, ""
	}
	if len(data) > maxResultBytes {
		return sandboxResult{}, errors.New("沙箱结果超过 1 MiB 上限"), ""
	}
	var result sandboxResult
	if err := json.Unmarshal(data, &result); err != nil {
		return sandboxResult{}, fmt.Errorf("result.json 格式无效：%v", err), string(data)
	}
	return result, nil, string(data)
}

func sbxHostname(executionID string, step, attempt int) string {
	sum := sha256.Sum256([]byte(fmt.Sprintf("%s-%d-%d", executionID, step, attempt)))
	return fmt.Sprintf("pcd-sbx-%s", hex.EncodeToString(sum[:6]))
}

func (r *Runner) audit(event, executionID string, entrypoint model.Entrypoint, outcome string, detail error) {
	if r.Audit == nil {
		return
	}
	var raw json.RawMessage
	if detail != nil {
		raw, _ = json.Marshal(map[string]interface{}{"summary": redact(detail.Error())})
	}
	r.Audit.Write(audit.Event{
		Event: event, Outcome: outcome, ExecutionID: executionID,
		StepID: "", PluginID: entrypoint.PluginID, VersionID: entrypoint.VersionID,
		UserID: "", SpaceID: "",
		Detail: raw,
	})
}

func outcome(err error) string {
	if err == nil {
		return "success"
	}
	return "failed"
}

func minInt(a, b int) int {
	if a < b {
		return a
	}
	return b
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func eventData(event map[string]interface{}) map[string]interface{} {
	data, _ := event["data"].(map[string]interface{})
	if data == nil {
		return map[string]interface{}{}
	}
	return data
}

func stringValue(value interface{}) string {
	if value == nil {
		return ""
	}
	if text, ok := value.(string); ok {
		return text
	}
	return fmt.Sprintf("%v", value)
}

// eventIdentity extracts only upstream-trusted event context for the UDS
// session. [CF-PLUGIN-UDS-001] Values are never accepted from pycloud requests.
func eventIdentity(event map[string]interface{}, keys ...string) string {
	for _, key := range keys {
		if value := stringValue(event[key]); value != "" {
			return value
		}
		if nested := eventData(event); nested != nil {
			if value := stringValue(nested[key]); value != "" {
				return value
			}
		}
	}
	return ""
}

func contains(values []string, expected string) bool {
	for _, value := range values {
		if value == expected {
			return true
		}
	}
	return false
}

func failed(code, summary string, completed int) model.RuntimeChainResult {
	return failedWithLogs(code, summary, completed, nil)
}

// failedWithLogs 在 failed 基础上把容器日志一并放入结果模型的 Logs 字段，
// 让 Execute / ExecutePostAvailable 的失败返回值也能携带可观测的容器输出。
// FailureSummary 语义与既有行为保持一致（不重复拼接日志）。
func failedWithLogs(code, summary string, completed int, logs []string) model.RuntimeChainResult {
	result := model.RuntimeChainResult{
		Status: "failed", FailureCode: code, FailureSummary: summarize(errors.New(summary)),
		CompletedEntrypoints: completed,
	}
	if len(logs) > 0 {
		result.Logs = asLogs(strings.Join(logs, "\n"))
	}
	return result
}

// withAuditTrails 把由受控 SDK 通道写出的能力事实附加到链路结果。
// [PLUGIN-EXEC-OBS-001] 即使容器步骤随后失败，已发生的能力调用也必须可审计，
// 因此不能只在最终成功路径附加审计记录。
func withAuditTrails(result model.RuntimeChainResult, trails []model.RuntimeAuditRecord) model.RuntimeChainResult {
	result.AuditTrails = trails
	return result
}

// withCapabilityAuditTrails mirrors chain execution: both request shapes can
// host pycloud calls, so neither may silently discard the Agent-owned audit
// facts before a trusted Automation/Workflow caller persists them.
func withCapabilityAuditTrails(result model.CapabilityExecutionResult, trails []model.RuntimeAuditRecord) model.CapabilityExecutionResult {
	result.AuditTrails = trails
	return result
}

// executionAuditRootID deliberately mirrors Java UUID.nameUUIDFromBytes over
// `<execution_id>:runtime-root` (MD5 UUID v3). Automation uses the same stable
// root for its execution observation, so child UDS audit facts retain their
// tree relation without the Runtime accepting a caller-controlled audit ID.
func executionAuditRootID(executionID string) string {
	digest := md5.Sum([]byte(executionID + ":runtime-root"))
	digest[6] = (digest[6] & 0x0f) | 0x30
	digest[8] = (digest[8] & 0x3f) | 0x80
	encoded := hex.EncodeToString(digest[:])
	return fmt.Sprintf("%s-%s-%s-%s-%s", encoded[0:8], encoded[8:12], encoded[12:16], encoded[16:20], encoded[20:32])
}

func capabilityFailed(code, summary string, logs ...string) model.CapabilityExecutionResult {
	result := model.CapabilityExecutionResult{
		Status: "failed", FailureCode: code, FailureSummary: summarize(errors.New(summary)),
	}
	if len(logs) > 0 {
		result.Logs = asLogs(strings.Join(logs, "\n"))
	}
	return result
}

// asLogs 把容器日志安全化为可放入结果模型的文本：脱敏 + 保留换行 + 长度上限。
// 与 summarizeWithLogs 不同，这里不压平为单行，便于调试时观察多行输出。
func asLogs(logs string) string {
	if logs == "" {
		return ""
	}
	clean := sanitize.Sanitize(logs)
	if len(clean) > maxResultLogs {
		clean = clean[:maxResultLogs]
	}
	return clean
}

func summarize(err error) string {
	if err == nil {
		return ""
	}
	return sanitize.Summary(sanitize.Sanitize(err.Error()), 1000)
}

func summarizeWithLogs(err error, logs string) string {
	if err == nil {
		return ""
	}
	summary := sanitize.Summary(sanitize.Sanitize(err.Error()), 1000)
	if logs != "" {
		summary += "：" + sanitize.Summary(sanitize.Sanitize(logs), 400)
	}
	return summary
}

func redact(value string) string {
	return sanitize.Summary(sanitize.Sanitize(value), 1000)
}

// boolInt 把布尔值转为 1/0，用于注入容器环境变量开关。
func boolInt(value bool) int {
	if value {
		return 1
	}
	return 0
}
