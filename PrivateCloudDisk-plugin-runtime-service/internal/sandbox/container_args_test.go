package sandbox

import (
	"strings"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/config"
	"privateclouddisk/plugin-runtime-service/internal/model"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// defaultArgsConfig 返回一份安全基线配置，供 containerArgs 用例复用。
func defaultArgsConfig() config.Config {
	return config.Config{
		Environment:    "development",
		WorkRoot:       "/tmp/pcd-runtime/work",
		DockerBinary:   "docker",
		SandboxImage:   "pcd/plugin-sandbox-python:0.1.0",
		SandboxRuntime: "runsc",
		SandboxNetwork: "none",
		SandboxUser:    "65532:65532",
		CPUs:           "1.0",
		MemoryBytes:    512 * 1024 * 1024,
		PidsLimit:      64,
	}
}

var defLimits = func() execLimits {
	return execLimits{timeout: 90 * time.Second, memoryBytes: 512 * 1024 * 1024}
}

// containerArgsTest 便捷包装：固定 module/function/limits，降低用例噪声。
func containerArgsTest(r *Runner, executionID string, step, attempt int, pluginRoot, input, work, context string, entry model.Entrypoint) []string {
	return r.containerArgs(executionID, step, attempt, pluginRoot, input, work, context, entry, "main.py", "main", defLimits())
}

var argsEntrypoint = model.Entrypoint{
	PluginID:     "plugin-demo",
	VersionID:    "version-abc",
	ModulePath:   "main.py",
	FunctionName: "main",
	Permissions:  []string{"file.content.read"},
}

func hasFlag(args []string, flag string) bool {
	for _, arg := range args {
		if arg == flag {
			return true
		}
	}
	return false
}

func hasFlagValue(args []string, flag, value string) bool {
	for index, arg := range args {
		if arg == flag && index+1 < len(args) && args[index+1] == value {
			return true
		}
	}
	return false
}

func mountValue(args []string, needle string) bool {
	for _, arg := range args {
		if strings.HasPrefix(arg, "--mount") {
			continue
		}
		if strings.HasPrefix(arg, "type=bind") && strings.Contains(arg, needle) {
			return true
		}
	}
	return false
}

func rawBinds(args []string) []string {
	var binds []string
	for index, arg := range args {
		if arg == "--mount" && index+1 < len(args) {
			binds = append(binds, args[index+1])
		}
	}
	return binds
}

// TestContainerArgsSecurityBaseline 验证全部必备安全参数（2.1-2.13）。
func TestContainerArgsSecurityBaseline(t *testing.T) {
	runner := &Runner{Config: defaultArgsConfig()}
	args := containerArgsTest(runner, "exec_0001", 0, 0, "/p/plugin", "/p/input.bin", "/p/work", "/p/context", argsEntrypoint)

	// 运行模式与名称/标签
	if !hasFlag(args, "run") || !hasFlag(args, "--rm") {
		t.Fatal("缺少 run/--rm（2.2）")
	}
	if !hasFlagValue(args, "--name", "pcd-plugin-exec_0001-000-0") {
		t.Fatalf("容器名未按约定生成: %v", args)
	}
	for _, label := range []string{"plugin-execution-id=exec_0001", "plugin-step-id=000", "pcd-platform=sandbox"} {
		if !hasArgContaining(args, label) {
			t.Fatalf("缺少追踪标签 %s（2.18）", label)
		}
	}

	// 运行运行时与网络（2.4/2.5）
	if !hasFlagValue(args, "--runtime", "runsc") {
		t.Fatal("缺少 --runtime runsc（2.4）")
	}
	if !hasFlagValue(args, "--network", "none") {
		t.Fatal("缺少 --network none（2.5）")
	}

	// 命名空间与只读（2.6/2.19）。Docker 29.x 起 PID/UTS 私有为默认且拒绝显式
	// --pid=private/--uts=private，故只断言 IPC/CgroupNS 显式私有并确认 PID/UTS 未切到 host。
	for _, flag := range []string{"--read-only", "--ipc=private", "--cgroupns=private"} {
		if !hasFlag(args, flag) {
			t.Fatalf("缺少 %s（2.6/3.19）", flag)
		}
	}
	for _, flag := range []string{"--pid=private", "--uts=private", "--pid=host", "--uts=host"} {
		if hasFlag(args, flag) {
			t.Fatalf("不应出现 %s（Docker 29.x 兼容，PID/UTS 保持默认私有）", flag)
		}
	}

	// 资源边界（2.7/2.8）
	if !hasFlagValue(args, "--cpus", "1.0") {
		t.Fatal("缺少 --cpus（2.7）")
	}
	if !hasFlagValue(args, "--memory", "536870912") {
		t.Fatal("缺少 --memory（2.7）")
	}
	if !hasFlagValue(args, "--memory-swap", "536870912") {
		t.Fatal("缺少 --memory-swap（2.7）")
	}
	if !hasFlagValue(args, "--pids-limit", "64") {
		t.Fatal("缺少 --pids-limit（2.7）")
	}
	if !hasFlagValue(args, "--ulimit", "nofile=128:128") {
		t.Fatal("缺少 nofile ulimit（2.8）")
	}

	// 最小能力与提权（2.9/2.10）
	if !hasFlagValue(args, "--cap-drop", "ALL") {
		t.Fatal("缺少 --cap-drop ALL（2.9）")
	}
	if !hasFlagValue(args, "--security-opt", "no-new-privileges") {
		t.Fatal("缺少 no-new-privileges（2.9）")
	}
	if !hasFlagValue(args, "--user", "65532:65532") {
		t.Fatal("缺少 --user 65532:65532（2.10）")
	}

	// tmpfs（2.11）
	if !hasArgContaining(args, "--tmpfs") {
		t.Fatal("缺少 tmpfs 挂载（2.11）")
	}
	if !hasArgContaining(args, "/tmp:rw,noexec,nosuid,nodev,size=16777216") {
		t.Fatal("缺少 /tmp tmpfs 安全参数（2.11）")
	}
	if !hasArgContaining(args, "/dev/shm:rw,noexec,nosuid,nodev,size=4194304") {
		t.Fatal("缺少 /dev/shm tmpfs 安全参数（2.11）")
	}

	// 挂载（2.12）
	binds := rawBinds(args)
	if !bindHas(binds, "dst=/workspace/plugin,readonly") || !bindHas(binds, "dst=/workspace/plugin") {
		t.Fatalf("插件根目录应只读挂载: %v", binds)
	}
	if !bindHas(binds, "dst=/workspace/input/content.bin,readonly") {
		t.Fatalf("输入文件应只读挂载: %v", binds)
	}
	if !bindHas(binds, "dst=/workspace/work") || bindHas(binds, "dst=/workspace/work,readonly") {
		t.Fatalf("工作目录应可写挂载（无 readonly）: %v", binds)
	}
	if !bindHas(binds, "dst=/workspace/context,readonly") {
		t.Fatalf("上下文目录应只读挂载: %v", binds)
	}

	// 环境变量（2.13）
	if !hasFlagValue(args, "-e", "PCD_MODULE_PATH=/workspace/plugin/main.py") {
		t.Fatal("缺少 PCD_MODULE_PATH（2.13）")
	}
	if !hasFlagValue(args, "-e", "PCD_FUNCTION_NAME=main") {
		t.Fatal("缺少 PCD_FUNCTION_NAME（2.13）")
	}
	if !hasFlagValue(args, "-e", "PCD_CONTEXT_PATH=/workspace/context/context.json") {
		t.Fatal("缺少 PCD_CONTEXT_PATH（2.13）")
	}
	if !hasFlagValue(args, "-e", "PCD_RESTRICTED_PYTHON=1") {
		t.Fatal("默认应注入 PCD_RESTRICTED_PYTHON=1（受限 Python 层，36.x）")
	}

	if args[len(args)-1] != runner.Config.SandboxImage {
		t.Fatalf("镜像必须是参数最后一个: %v", args)
	}
}

func TestContainerArgsHostnameStablePerAttempt(t *testing.T) {
	runner := &Runner{Config: defaultArgsConfig()}
	first := containerArgsTest(runner, "exec_0001", 2, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
	again := containerArgsTest(runner, "exec_0001", 2, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
	otherAttempt := containerArgsTest(runner, "exec_0001", 2, 1, "/p", "/i", "/w", "/c", argsEntrypoint)
	otherExec := containerArgsTest(runner, "exec_0002", 2, 0, "/p", "/i", "/w", "/c", argsEntrypoint)

	hostnameOf := func(args []string) string {
		for index, arg := range args {
			if arg == "--hostname" {
				return args[index+1]
			}
		}
		return ""
	}
	if !strings.HasPrefix(hostnameOf(first), "pcd-sbx-") {
		t.Fatalf("主机名必须带 pcd-sbx- 前缀（2.3）: %q", hostnameOf(first))
	}
	if hostnameOf(first) != hostnameOf(again) {
		t.Fatalf("相同执行/步骤/尝试应生成相同主机名（2.17）")
	}
	if hostnameOf(first) == hostnameOf(otherAttempt) {
		t.Fatal("不同尝试应生成不同主机名（2.17）")
	}
	if hostnameOf(first) == hostnameOf(otherExec) {
		t.Fatal("不同执行应生成不同主机名（2.17）")
	}
}

// TestContainerArgsMountsOnlyTheInstanceSocket verifies the new transport is
// bound to a single sandbox and the credential is argv-only, never env/context.
func TestContainerArgsMountsPerInstanceSocketWithoutTokenEnvironment(t *testing.T) {
	runner := &Runner{Config: defaultArgsConfig()}
	session := &uds.Session{ID: "instance-abcdefghijklmnopqrstuvwxyz", SocketPath: "/run/pcd/plugins/plugin-instance.sock", Token: "opaque-token-not-env"}
	args := runner.containerArgsWithSession("exec_0001", 0, 0, "/p/plugin", "/p/input", "/p/work", "/p/context", argsEntrypoint, "main.py", "main", defLimits(), session)
	if !bindHas(rawBinds(args), "src=/run/pcd/plugins/plugin-instance.sock,dst=/runtime/runtime.sock,readonly") {
		t.Fatalf("missing read-only per-instance Runtime socket mount: %v", rawBinds(args))
	}
	if !hasArgContaining(args, "--pcd-instance-id") || !hasArgContaining(args, session.ID) || !hasArgContaining(args, "--pcd-instance-token") || !hasArgContaining(args, session.Token) {
		t.Fatalf("runner CLI credentials missing: %v", args)
	}
	for index, value := range args {
		if value == "-e" && index+1 < len(args) && strings.Contains(args[index+1], "TOKEN") {
			t.Fatalf("instance token must not be injected by environment: %v", args[index+1])
		}
	}
}

func TestContainerArgsOptionalCapsules(t *testing.T) {
	base := defaultArgsConfig()

	t.Run("userns_remap", func(t *testing.T) {
		cfg := base
		cfg.UserNamespaceRemap = true
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		if !hasFlagValue(args, "--userns-remap", "default") {
			t.Fatalf("userns-remap 未插入（2.14）: %v", args)
		}
	})

	t.Run("seccomp", func(t *testing.T) {
		cfg := base
		cfg.SeccompProfile = "/etc/pcd-plugin-runtime/seccomp.json"
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		if !hasFlagValue(args, "--security-opt", "seccomp=/etc/pcd-plugin-runtime/seccomp.json") {
			t.Fatalf("seccomp 未插入（2.15）: %v", args)
		}
	})

	t.Run("apparmor", func(t *testing.T) {
		cfg := base
		cfg.AppArmorProfile = "pcd-plugin-sandbox"
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		if !hasFlagValue(args, "--security-opt", "apparmor=pcd-plugin-sandbox") {
			t.Fatalf("apparmor 未插入（2.16）: %v", args)
		}
	})

	t.Run("custom_sandbox_user", func(t *testing.T) {
		cfg := base
		cfg.SandboxUser = "1001:1001"
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		if !hasFlagValue(args, "--user", "1001:1001") {
			t.Fatalf("自定义沙箱 uid 未生效（节点 uid 与容器用户一致）: %v", args)
		}
	})

	t.Run("disable_restricted_python", func(t *testing.T) {
		cfg := base
		cfg.DisableRestrictedPython = true
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		if !hasFlagValue(args, "-e", "PCD_RESTRICTED_PYTHON=0") {
			t.Fatalf("关闭受限 Python 层未注入 PCD_RESTRICTED_PYTHON=0: %v", args)
		}
	})

	t.Run("combined_order_before_image", func(t *testing.T) {
		cfg := base
		cfg.UserNamespaceRemap = true
		cfg.SeccompProfile = "/s.json"
		cfg.AppArmorProfile = "pcd-plugin-sandbox"
		args := containerArgsTest(&Runner{Config: cfg}, "e", 0, 0, "/p", "/i", "/w", "/c", argsEntrypoint)
		image := args[len(args)-1]
		if image != cfg.SandboxImage {
			t.Fatalf("镜像应保持末位: %q", image)
		}
		joined := strings.Join(args, " ")
		for _, needle := range []string{"userns-remap default", "seccomp=/s.json", "apparmor=pcd-plugin-sandbox"} {
			if !strings.Contains(joined, needle) {
				t.Fatalf("缺少 %s", needle)
			}
		}
	})
}

func TestContainerArgsRetryOffset(t *testing.T) {
	cfg := defaultArgsConfig()
	cfg.MaxExecutionRetries = 2
	runner := &Runner{Config: cfg}
	args := containerArgsTest(runner, "e", 1, 2, "/p", "/i", "/w", "/c", argsEntrypoint)
	if !hasFlagValue(args, "--name", "pcd-plugin-e-001-2") {
		t.Fatalf("容器名应包含尝试序号: %v", args)
	}
}

func hasArgContaining(args []string, needle string) bool {
	for _, arg := range args {
		if strings.Contains(arg, needle) {
			return true
		}
	}
	return false
}

func bindHas(binds []string, needle string) bool {
	for _, bind := range binds {
		if strings.Contains(bind, needle) {
			return true
		}
	}
	return false
}

func TestRunnerExecutionTimeoutConfig(t *testing.T) {
	cfg := defaultArgsConfig()
	cfg.ExecutionTimeout = 90 * time.Second
	if cfg.ExecutionTimeout.Seconds() != 90 {
		t.Fatalf("执行超时配置解析异常：%v", cfg.ExecutionTimeout)
	}
}

func TestContainerArgsManifestLimitsOverrideMemory(t *testing.T) {
	// 5.9/5.23：manifest limits.memory_mb 取全局配置与 manifest 的较小值。
	cfg := defaultArgsConfig()
	runner := &Runner{Config: cfg}
	smaller := execLimits{memoryBytes: 128 * 1024 * 1024, timeout: 30 * time.Second}
	args := runner.containerArgs("e", 0, 0, "/p/plugin", "/p/i", "/p/w", "/p/c",
		argsEntrypoint, "src/main.py", "main", smaller)
	if !hasFlagValue(args, "--memory", "134217728") {
		t.Fatalf("manifest 较小内存未生效: %v", args)
	}
	if !hasFlagValue(args, "--memory-swap", "134217728") {
		t.Fatalf("memory-swap 应与 memory 一致: %v", args)
	}

	// manifest 声明更大内存时不得突破全局上限（只降不升）。
	bigger := execLimits{memoryBytes: 2 << 30, timeout: 30 * time.Second}
	args = runner.containerArgs("e", 0, 0, "/p/plugin", "/p/i", "/p/w", "/p/c",
		argsEntrypoint, "src/main.py", "main", bigger)
	if !hasFlagValue(args, "--memory", "536870912") {
		t.Fatalf("manifest 更大内存应被全局上限收敛: %v", args)
	}
}

func TestContainerArgsModulePathUsesManifestModule(t *testing.T) {
	// 5.4：PCD_MODULE_PATH 必须使用 manifest 入口的 module 相对路径。
	runner := &Runner{Config: defaultArgsConfig()}
	args := runner.containerArgs("e", 0, 0, "/p/plugin", "/p/i", "/p/w", "/p/c",
		argsEntrypoint, "src/main.py", "preprocess", defLimits())
	if !hasFlagValue(args, "-e", "PCD_MODULE_PATH=/workspace/plugin/src/main.py") {
		t.Fatalf("PCD_MODULE_PATH 应拼接 manifest module: %v", args)
	}
	if !hasFlagValue(args, "-e", "PCD_FUNCTION_NAME=preprocess") {
		t.Fatalf("PCD_FUNCTION_NAME 应使用 manifest function: %v", args)
	}
}
