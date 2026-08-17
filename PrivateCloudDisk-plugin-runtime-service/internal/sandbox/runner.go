package sandbox

import (
	"archive/zip"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"strings"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/broker"
	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/pkgclient"
)

type Runner struct {
	Config   config.Config
	Packages *pkgclient.Client
	Broker   *broker.Client
}

type sandboxResult struct {
	Success  bool                   `json:"success"`
	Modified bool                   `json:"modified"`
	Error    string                 `json:"error"`
	Output   map[string]interface{} `json:"output"`
}

var safeID = regexp.MustCompile(`^[0-9A-Za-z_-]{1,128}$`)

func (r *Runner) Execute(parent context.Context, request model.PreprocessChainRequest) model.RuntimeChainResult {
	if !safeID.MatchString(request.ExecutionID) {
		return failed("RUNTIME_REQUEST_INVALID", "execution_id 格式无效", 0)
	}
	data := eventData(request.Event)
	gateID := stringValue(data["gate_id"])
	if !safeID.MatchString(gateID) || request.ContentLeaseRef == "" {
		return failed("RUNTIME_REQUEST_INVALID", "Gate 或内容 Lease 缺失", 0)
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
		ctx,
		gateID,
		request.ExecutionID,
		request.ContentLeaseRef,
		int(timeout.Seconds()),
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

	currentInput := input
	modified := false
	completed := 0
	for index, entrypoint := range request.Entrypoints {
		if ctx.Err() != nil {
			return model.RuntimeChainResult{
				Status: "timeout", ContentModified: false,
				CompletedEntrypoints: completed,
				FailureCode:          "PLUGIN_RUNTIME_TIMEOUT",
				FailureSummary:       "插件执行超过预处理截止时间",
			}
		}
		if entrypoint.Runtime != "PYTHON_3_11" ||
			!contains(entrypoint.Permissions, "file.content.write_pre_activation") {
			return failed("RUNTIME_POLICY_REJECTED", "入口运行时或权限不符合预处理策略", completed)
		}
		stepRoot := filepath.Join(root, fmt.Sprintf("step-%03d", index))
		packagePath := filepath.Join(stepRoot, "plugin.pcdpkg")
		pluginRoot := filepath.Join(stepRoot, "plugin")
		workRoot := filepath.Join(stepRoot, "work")
		contextRoot := filepath.Join(stepRoot, "context")
		if err := os.MkdirAll(stepRoot, 0o700); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建步骤工作区", completed)
		}
		if err := r.Packages.Download(ctx, entrypoint.VersionID, packagePath); err != nil {
			return failed("PLUGIN_PACKAGE_FETCH_FAILED", summarize(err), completed)
		}
		if err := extractPackage(packagePath, pluginRoot, r.Config.PackageMaxBytes); err != nil {
			return failed("PLUGIN_PACKAGE_INVALID", summarize(err), completed)
		}
		if err := os.MkdirAll(workRoot, 0o770); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱输出目录", completed)
		}
		if err := os.MkdirAll(contextRoot, 0o700); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱上下文目录", completed)
		}
		contextPath := filepath.Join(contextRoot, "context.json")
		contextPayload := map[string]interface{}{
			"execution_id":    request.ExecutionID,
			"gate_id":         gateID,
			"plugin_id":       entrypoint.PluginID,
			"version_id":      entrypoint.VersionID,
			"installation_id": entrypoint.InstallationID,
			"permissions":     entrypoint.Permissions,
			"config":          entrypoint.Config,
			"event":           request.Event,
		}
		contextBytes, _ := json.Marshal(contextPayload)
		if err := os.WriteFile(contextPath, contextBytes, 0o400); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法写入沙箱上下文", completed)
		}
		result, logs, err := r.runContainer(
			ctx, request.ExecutionID, index, pluginRoot, currentInput,
			workRoot, contextRoot, entrypoint,
		)
		if err != nil {
			if ctx.Err() != nil {
				return model.RuntimeChainResult{
					Status: "timeout", CompletedEntrypoints: completed,
					FailureCode:    "PLUGIN_RUNTIME_TIMEOUT",
					FailureSummary: "插件执行超时，容器已强制终止",
				}
			}
			return failed("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, logs), completed)
		}
		if !result.Success {
			return failed("PLUGIN_EXECUTION_FAILED", sanitize(result.Error), completed)
		}
		completed++
		output := filepath.Join(workRoot, "output.bin")
		if result.Modified {
			stat, statErr := os.Stat(output)
			if statErr != nil || stat.Size() <= 0 || stat.Size() > r.Config.CandidateMaxBytes {
				return failed("PLUGIN_OUTPUT_INVALID", "插件声明修改但输出内容无效", completed)
			}
			currentInput = output
			modified = true
		}
	}
	if !modified {
		return model.RuntimeChainResult{
			Status: "success", ContentModified: false,
			CompletedEntrypoints: completed,
		}
	}
	candidate, err := r.Broker.Upload(
		ctx, gateID, request.ExecutionID, exchanged.ExecutionLease, currentInput,
	)
	if err != nil {
		return failed("CANDIDATE_COMMIT_FAILED", summarize(err), completed)
	}
	size := candidate.Size
	return model.RuntimeChainResult{
		Status: "success", ContentModified: true,
		CandidateID: candidate.ID, CandidateChecksum: candidate.Checksum,
		CandidateSize: &size, CompletedEntrypoints: completed,
	}
}

// ExecutePostAvailable 执行激活后入口。该路径只能读取最终内容，任何候选输出都会被拒绝。
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
	ctx, cancel := context.WithTimeout(parent, r.Config.ExecutionTimeout)
	defer cancel()

	root := filepath.Join(r.Config.WorkRoot, request.ExecutionID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		return failed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离工作区", 0)
	}
	defer os.RemoveAll(root)
	input := filepath.Join(root, "active-content.bin")
	requiresRead := false
	for _, entrypoint := range request.Entrypoints {
		if contains(entrypoint.Permissions, "file.content.write_pre_activation") {
			return failed("CONTENT_FROZEN", "file.available 入口禁止修改文件内容", 0)
		}
		requiresRead = requiresRead || contains(entrypoint.Permissions, "file.content.read")
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
	for index, entrypoint := range request.Entrypoints {
		if ctx.Err() != nil {
			return model.RuntimeChainResult{
				Status: "timeout", CompletedEntrypoints: completed,
				FailureCode: "PLUGIN_RUNTIME_TIMEOUT", FailureSummary: "激活后插件执行超时",
			}
		}
		if entrypoint.Runtime != "PYTHON_3_11" {
			return failed("RUNTIME_POLICY_REJECTED", "云插件入口运行时不受支持", completed)
		}
		stepRoot := filepath.Join(root, fmt.Sprintf("step-%03d", index))
		packagePath := filepath.Join(stepRoot, "plugin.pcdpkg")
		pluginRoot := filepath.Join(stepRoot, "plugin")
		workRoot := filepath.Join(stepRoot, "work")
		contextRoot := filepath.Join(stepRoot, "context")
		if err := os.MkdirAll(stepRoot, 0o700); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建步骤工作区", completed)
		}
		if err := r.Packages.Download(ctx, entrypoint.VersionID, packagePath); err != nil {
			return failed("PLUGIN_PACKAGE_FETCH_FAILED", summarize(err), completed)
		}
		if err := extractPackage(packagePath, pluginRoot, r.Config.PackageMaxBytes); err != nil {
			return failed("PLUGIN_PACKAGE_INVALID", summarize(err), completed)
		}
		if err := os.MkdirAll(workRoot, 0o770); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱输出目录", completed)
		}
		if err := os.MkdirAll(contextRoot, 0o700); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法创建沙箱上下文目录", completed)
		}
		contextPayload := map[string]interface{}{
			"execution_id": request.ExecutionID, "plugin_id": entrypoint.PluginID,
			"version_id": entrypoint.VersionID, "installation_id": entrypoint.InstallationID,
			"permissions": entrypoint.Permissions, "config": entrypoint.Config,
			"event": request.Event, "content_frozen": true,
		}
		contextBytes, _ := json.Marshal(contextPayload)
		if err := os.WriteFile(
			filepath.Join(contextRoot, "context.json"), contextBytes, 0o400,
		); err != nil {
			return failed("RUNTIME_WORKSPACE_FAILED", "无法写入沙箱上下文", completed)
		}
		result, logs, err := r.runContainer(
			ctx, request.ExecutionID, index, pluginRoot, input,
			workRoot, contextRoot, entrypoint,
		)
		if err != nil {
			if ctx.Err() != nil {
				return model.RuntimeChainResult{
					Status: "timeout", CompletedEntrypoints: completed,
					FailureCode: "PLUGIN_RUNTIME_TIMEOUT", FailureSummary: "激活后插件执行超时",
				}
			}
			return failed("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, logs), completed)
		}
		if !result.Success {
			return failed("PLUGIN_EXECUTION_FAILED", sanitize(result.Error), completed)
		}
		if result.Modified {
			return failed("CONTENT_FROZEN", "激活后插件产生了非法内容写入", completed)
		}
		completed++
	}
	return model.RuntimeChainResult{
		Status: "success", ContentModified: false, CompletedEntrypoints: completed,
	}
}

// ExecuteCapability 在同一 gVisor/容器安全边界中执行云插件导出的能力函数。
// 该入口不接受预激活文件写权限，结构化返回值通过 result.json 回传给 Workflow。
func (r *Runner) ExecuteCapability(
	parent context.Context,
	request model.CapabilityExecutionRequest,
) model.CapabilityExecutionResult {
	if !safeID.MatchString(request.ExecutionID) || !safeID.MatchString(request.StepID) {
		return capabilityFailed("RUNTIME_REQUEST_INVALID", "执行或步骤标识格式无效")
	}
	entrypoint := request.Entrypoint
	if entrypoint.Runtime != "PYTHON_3_11" || entrypoint.VersionID == "" ||
		entrypoint.ModulePath == "" || entrypoint.FunctionName == "" {
		return capabilityFailed("RUNTIME_POLICY_REJECTED", "插件能力入口配置无效")
	}
	if contains(entrypoint.Permissions, "file.content.write_pre_activation") {
		return capabilityFailed("RUNTIME_POLICY_REJECTED", "工作流能力禁止使用预激活文件写权限")
	}
	ctx, cancel := context.WithTimeout(parent, r.Config.ExecutionTimeout)
	defer cancel()
	root := filepath.Join(r.Config.WorkRoot, request.ExecutionID+"-"+request.StepID)
	if err := os.MkdirAll(root, 0o700); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离工作区")
	}
	defer os.RemoveAll(root)
	packagePath := filepath.Join(root, "plugin.pcdpkg")
	pluginRoot := filepath.Join(root, "plugin")
	workRoot := filepath.Join(root, "work")
	contextRoot := filepath.Join(root, "context")
	inputPath := filepath.Join(root, "empty-input.bin")
	if err := r.Packages.Download(ctx, entrypoint.VersionID, packagePath); err != nil {
		return capabilityFailed("PLUGIN_PACKAGE_FETCH_FAILED", summarize(err))
	}
	if err := extractPackage(packagePath, pluginRoot, r.Config.PackageMaxBytes); err != nil {
		return capabilityFailed("PLUGIN_PACKAGE_INVALID", summarize(err))
	}
	if err := os.MkdirAll(workRoot, 0o770); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建输出目录")
	}
	if err := os.MkdirAll(contextRoot, 0o700); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建上下文目录")
	}
	if err := os.WriteFile(inputPath, []byte{}, 0o400); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法创建隔离输入")
	}
	contextPayload := map[string]interface{}{
		"execution_id":    request.ExecutionID,
		"step_id":         request.StepID,
		"user_id":         request.UserID,
		"space_id":        request.SpaceID,
		"plugin_id":       entrypoint.PluginID,
		"version_id":      entrypoint.VersionID,
		"installation_id": entrypoint.InstallationID,
		"permissions":     entrypoint.Permissions,
		"config":          entrypoint.Config,
		"input":           request.Input,
		"invocation":      "workflow_capability",
		"content_frozen":  true,
	}
	contextBytes, _ := json.Marshal(contextPayload)
	if err := os.WriteFile(
		filepath.Join(contextRoot, "context.json"), contextBytes, 0o400,
	); err != nil {
		return capabilityFailed("RUNTIME_WORKSPACE_FAILED", "无法写入沙箱上下文")
	}
	result, logs, err := r.runContainer(
		ctx, request.ExecutionID, 0, pluginRoot, inputPath,
		workRoot, contextRoot, entrypoint,
	)
	if err != nil {
		if ctx.Err() != nil {
			return capabilityFailed("PLUGIN_RUNTIME_TIMEOUT", "插件能力执行超时，容器已强制终止")
		}
		return capabilityFailed("PLUGIN_EXECUTION_FAILED", summarizeWithLogs(err, logs))
	}
	if !result.Success {
		return capabilityFailed("PLUGIN_EXECUTION_FAILED", sanitize(result.Error))
	}
	if result.Modified {
		return capabilityFailed("CONTENT_FROZEN", "工作流能力不得直接写入预激活文件输出")
	}
	if result.Output == nil {
		result.Output = map[string]interface{}{}
	}
	return model.CapabilityExecutionResult{Status: "success", Output: result.Output}
}

func (r *Runner) runContainer(
	ctx context.Context,
	executionID string,
	step int,
	pluginRoot, inputPath, workRoot, contextRoot string,
	entrypoint model.Entrypoint,
) (sandboxResult, string, error) {
	containerName := fmt.Sprintf("pcd-plugin-%s-%03d", strings.ToLower(executionID), step)
	args := []string{
		"run", "--rm", "--name", containerName,
		"--runtime", r.Config.SandboxRuntime,
		"--network", "none",
		"--read-only",
		"--cpus", r.Config.CPUs,
		"--memory", fmt.Sprintf("%d", r.Config.MemoryBytes),
		"--memory-swap", fmt.Sprintf("%d", r.Config.MemoryBytes),
		"--pids-limit", fmt.Sprintf("%d", r.Config.PidsLimit),
		"--cap-drop", "ALL",
		"--security-opt", "no-new-privileges",
		"--user", "65532:65532",
		"--ulimit", "nofile=128:128",
		"--stop-timeout", "2",
		"--tmpfs", "/tmp:rw,noexec,nosuid,nodev,size=16777216",
		"--mount", "type=bind,src=" + pluginRoot + ",dst=/workspace/plugin,readonly",
		"--mount", "type=bind,src=" + inputPath + ",dst=/workspace/input/content.bin,readonly",
		"--mount", "type=bind,src=" + workRoot + ",dst=/workspace/work",
		"--mount", "type=bind,src=" + contextRoot + ",dst=/workspace/context,readonly",
		"-e", "PCD_MODULE_PATH=/workspace/plugin/" + entrypoint.ModulePath,
		"-e", "PCD_FUNCTION_NAME=" + entrypoint.FunctionName,
		"-e", "PCD_CONTEXT_PATH=/workspace/context/context.json",
		r.Config.SandboxImage,
	}
	if r.Config.SeccompProfile != "" {
		args = append(args[:len(args)-1],
			"--security-opt", "seccomp="+r.Config.SeccompProfile,
			args[len(args)-1],
		)
	}
	if r.Config.AppArmorProfile != "" {
		args = append(args[:len(args)-1],
			"--security-opt", "apparmor="+r.Config.AppArmorProfile,
			args[len(args)-1],
		)
	}
	logs := NewLimitedBuffer(r.Config.LogLimitBytes)
	command := exec.CommandContext(ctx, r.Config.DockerBinary, args...)
	command.Stdout = logs
	command.Stderr = logs
	err := command.Run()
	if err != nil {
		// CommandContext 只终止 docker CLI；额外删除可避免失联容器继续运行。
		cleanup, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		_ = exec.CommandContext(cleanup, r.Config.DockerBinary, "rm", "-f", containerName).Run()
		return sandboxResult{}, logs.String(), err
	}
	resultPath := filepath.Join(workRoot, "result.json")
	resultStat, err := os.Stat(resultPath)
	if err != nil || resultStat.Size() <= 0 || resultStat.Size() > 1024*1024 {
		return sandboxResult{}, logs.String(), errors.New("沙箱执行结果缺失或超过 1 MiB")
	}
	resultBytes, err := os.ReadFile(resultPath)
	if err != nil {
		return sandboxResult{}, logs.String(), errors.New("沙箱未生成结构化执行结果")
	}
	var result sandboxResult
	if json.Unmarshal(resultBytes, &result) != nil {
		return sandboxResult{}, logs.String(), errors.New("沙箱执行结果格式无效")
	}
	return result, logs.String(), nil
}

func extractPackage(archivePath, destination string, maxExpanded int64) error {
	reader, err := zip.OpenReader(archivePath)
	if err != nil {
		return err
	}
	defer reader.Close()
	if len(reader.File) > 256 {
		return errors.New("插件包文件数量超过 Runtime 上限")
	}
	root, err := filepath.Abs(destination)
	if err != nil {
		return err
	}
	if err := os.MkdirAll(root, 0o500); err != nil {
		return err
	}
	var expanded int64
	for _, file := range reader.File {
		clean := filepath.Clean(filepath.FromSlash(file.Name))
		if filepath.IsAbs(clean) || clean == ".." || strings.HasPrefix(clean, ".."+string(os.PathSeparator)) {
			return errors.New("插件包包含路径穿越")
		}
		if file.Mode()&os.ModeSymlink != 0 || file.Mode()&os.ModeType != 0 && !file.FileInfo().IsDir() {
			return errors.New("插件包包含链接或特殊文件")
		}
		target := filepath.Join(root, clean)
		if !strings.HasPrefix(target, root+string(os.PathSeparator)) && target != root {
			return errors.New("插件包解压路径越界")
		}
		if file.FileInfo().IsDir() {
			if err := os.MkdirAll(target, 0o500); err != nil {
				return err
			}
			continue
		}
		expanded += int64(file.UncompressedSize64)
		if expanded > maxExpanded {
			return errors.New("插件包解压体积超过 Runtime 上限")
		}
		if err := os.MkdirAll(filepath.Dir(target), 0o500); err != nil {
			return err
		}
		source, err := file.Open()
		if err != nil {
			return err
		}
		output, err := os.OpenFile(target, os.O_CREATE|os.O_EXCL|os.O_WRONLY, 0o400)
		if err != nil {
			source.Close()
			return err
		}
		_, copyErr := io.Copy(output, io.LimitReader(source, maxExpanded+1))
		closeErr := output.Close()
		source.Close()
		if copyErr != nil {
			return copyErr
		}
		if closeErr != nil {
			return closeErr
		}
	}
	return nil
}

func eventData(event map[string]interface{}) map[string]interface{} {
	if value, ok := event["data"].(map[string]interface{}); ok {
		return value
	}
	return map[string]interface{}{}
}

func stringValue(value interface{}) string {
	if value == nil {
		return ""
	}
	return fmt.Sprint(value)
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
	return model.RuntimeChainResult{
		Status: "failed", CompletedEntrypoints: completed,
		FailureCode: code, FailureSummary: sanitize(summary),
	}
}

func capabilityFailed(code, summary string) model.CapabilityExecutionResult {
	return model.CapabilityExecutionResult{
		Status: "failed", FailureCode: code, FailureSummary: sanitize(summary),
	}
}

func summarize(err error) string {
	if err == nil {
		return ""
	}
	return sanitize(err.Error())
}

func summarizeWithLogs(err error, logs string) string {
	return sanitize(err.Error() + ": " + logs)
}

func sanitize(value string) string {
	value = strings.ReplaceAll(value, "\n", " ")
	value = strings.ReplaceAll(value, "\r", " ")
	value = regexp.MustCompile(`/[A-Za-z0-9._/-]+`).ReplaceAllString(value, "[path]")
	value = strings.TrimSpace(value)
	if len(value) > 1000 {
		return value[:1000]
	}
	return value
}
