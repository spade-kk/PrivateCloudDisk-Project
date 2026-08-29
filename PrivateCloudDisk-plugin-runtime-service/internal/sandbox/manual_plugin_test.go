//go:build integration

package sandbox

// 手动调试/教学测试单元（禁止重复实现，全部复用现有模块）：
//
//   - 插件来源通过环境变量指定：插件项目目录（含 manifest.yaml + src/ + schemas/，
//     未打包的云插件工程，可带 input.json / input.txt）、或已打包的 .pcdpkg 文件、
//     或单个 python 入口文件（快速验证受限运行时 runner.py / restricted.py）。
//   - 目录/PCDPKG/SRC 三种来源一律先经 internal/package 模块校验：
//       * manifest.go 的 ParseManifestBytes —— 校验插件清单 YAML 规范（§7.2）。
//       * parse.go 的 Parse —— 校验插件目录/包结构（§7.1 禁止清单 + 受限 ZIP）。
//   - 容器运行复用 runner.go（Runner.Execute / ExecutePostAvailable /
//     ExecuteCapability），与 integration_docker_test.go / integration_realworld_test.go
//     同一 harness（integrationConfig / newTestRunner / fakeBroker / fakePackages /
//     capabilityRelay / preprocessRequest / assertNoResidual）。
//   - 插件输出、日志、异常一律 t.Logf 打印（运行时请加 -v），不写数据库、不落盘。
//   - runner.go 的 Execute/ExecutePostAvailable/ExecuteCapability 返回结果模型现携带
//     logs（脱敏容器 stdout/stderr，保留换行）与 output（入口函数返回值）；本单元会
//     用 dumpLogs 把 logs 单独打印成段（====> [容器日志]），独立于 struct/JSON，
//     便于直接观察插件 print / pycloud.log / runner.py、restricted.py 输出。
//
// 用法（脚本包装见 scripts/run_manual_plugin.sh）：
//
//	PCD_DEBUG_PLUGIN_DIR=/path/to/plugin-dir \
//	  go test -tags=integration -v -run '^TestManualPluginDriver$' ./internal/sandbox/

import (
	"archive/zip"
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"privateclouddisk/plugin-runtime-service/internal/model"
	pkg "privateclouddisk/plugin-runtime-service/internal/package"
	"privateclouddisk/plugin-runtime-service/internal/uds"
)

// 环境变量开关（脚本 scripts/run_manual_plugin.sh 会按 CLI 参数映射）。
const (
	envPluginDir         = "PCD_DEBUG_PLUGIN_DIR"         // 插件项目目录（未打包）
	envPluginPkg         = "PCD_DEBUG_PLUGIN_PKG"         // 已打包 .pcdpkg 文件
	envPluginSrc         = "PCD_DEBUG_PLUGIN_SRC"         // 单个 python 入口文件（受限层探针）
	envInputJSON         = "PCD_DEBUG_INPUT"              // 输入参数/内容（JSON 字符串）
	envInputFile         = "PCD_DEBUG_INPUT_FILE"         // 输入文件路径（内容模式原样注入）
	envEvent             = "PCD_DEBUG_EVENT"              // pcd.file.content.ready.v1 | pcd.file.available.v1
	envCapability        = "PCD_DEBUG_CAPABILITY"         // 切换到 ExecuteCapability（manifest exports.name）
	envTimeout           = "PCD_DEBUG_TIMEOUT"            // 覆盖容器执行超时（秒）
	envMemoryMB          = "PCD_DEBUG_MEMORY_MB"          // 覆盖容器内存（MiB）
	envNoRestricted      = "PCD_DEBUG_DISABLE_RESTRICTED" // 关停受限 Python 层（探针夹具用）
	envVerifyDigest      = "PCD_DEBUG_VERIFY_DIGEST"      // 开启镜像摘要门禁（需 PLUGIN_SANDBOX_IMAGE_DIGEST）
	envRestrictedProbe   = "PCD_DEBUG_RESTRICTED_PROBE"   // restricted.py 宿主机探针（无需 Docker）
	envRestrictedSnippet = "PCD_DEBUG_RESTRICTED_SNIPPET" // 自定义受限探针源码
)

// genExecID 生成 safeID 合法的 execution_id（字母数字_/-，≤128）。
func genExecID() string {
	return fmt.Sprintf("manual_%d", time.Now().Unix())
}

// resolvePathArg 把用户传入的路径归一化为绝对路径：go test 的 cwd 是包目录
// （internal/sandbox），仓库根相对路径（脚本场景 cwd=$ROOT）会因 cwd 变化而失效，
// 因此做"包目录绝对化 → 仓库根(../../)相对化"两级查找。
func resolvePathArg(p string) string {
	if p == "" {
		return ""
	}
	if filepath.IsAbs(p) {
		return p
	}
	if _, err := os.Stat(p); err == nil && !filepath.IsAbs(p) {
		abs, _ := filepath.Abs(p)
		return abs
	}
	root, _ := filepath.Abs(filepath.Join("..", ".."))
	candidate := filepath.Join(root, p)
	if _, err := os.Stat(candidate); err == nil {
		abs, _ := filepath.Abs(candidate)
		return abs
	}
	abs, _ := filepath.Abs(p)
	return abs
}

// --- 插件包构造（复用 internal/package 校验；此处只做"目录/单文件 -> 内存 zip"打包） ----

// packageDirPcdpkgBytes 把插件工程目录打成受约束 .pcdpkg 内存 zip，行为与
// scripts/package_test_plugins.sh / realworldPcdpkgBytes 一致：每个文件只写一次
// （manifest.yaml / src/ / schemas/ / assets/ / README.md / LICENSE），
// 剔除 input.*、点文件、__pycache__、旧 plugin.yaml。结构/安全校验仍由后续
// pkg.Parse 完成，这里只做打包、不重复实现校验逻辑。
func packageDirPcdpkgBytes(t *testing.T, dir string) []byte {
	t.Helper()
	root, err := filepath.Abs(dir)
	if err != nil {
		t.Fatalf("解析插件目录 %q：%v", dir, err)
	}
	if _, err := os.Stat(filepath.Join(root, "manifest.yaml")); err != nil {
		t.Fatalf("插件目录缺少 manifest.yaml（%s）：%v", root, err)
	}
	if _, err := os.Stat(filepath.Join(root, "src")); err != nil {
		t.Fatalf("插件目录缺少 src/（%s）：%v", root, err)
	}
	buffer := &bytes.Buffer{}
	writer := zip.NewWriter(buffer)
	walkErr := filepath.WalkDir(root, func(path string, entry os.DirEntry, wErr error) error {
		if wErr != nil {
			return wErr
		}
		if entry.IsDir() {
			return nil
		}
		rel, relErr := filepath.Rel(root, path)
		if relErr != nil {
			return relErr
		}
		rel = filepath.ToSlash(rel)
		// 与打包脚本一致：input.* / 点文件 / __pycache__ / 旧 plugin.yaml / *.pyc 不进包。
		if strings.HasPrefix(rel, ".") ||
			strings.HasPrefix(rel, "__pycache__/") ||
			strings.HasPrefix(rel, "input.") ||
			rel == "plugin.yaml" ||
			strings.HasSuffix(rel, ".pyc") {
			return nil
		}
		content, readErr := os.ReadFile(path)
		if readErr != nil {
			return readErr
		}
		header := &zip.FileHeader{Name: rel, Method: zip.Deflate}
		header.SetMode(0o400)
		entryWriter, createErr := writer.CreateHeader(header)
		if createErr != nil {
			return createErr
		}
		_, writeErr := entryWriter.Write(content)
		return writeErr
	})
	if walkErr != nil {
		t.Fatalf("遍历插件目录 %s：%v", root, walkErr)
	}
	if err := writer.Close(); err != nil {
		t.Fatalf("关闭临时 .pcdpkg：%v", err)
	}
	return buffer.Bytes()
}

// packageSrcPcdpkgBytes 把单个 python 入口文件包成 .pcdpkg：临时合成最小 manifest
// （函数固定 main、事件 ready、预激活读写权限 + 能力导出探测），便于快速试验
// runner.py / restricted.py 对任意用户代码的行为。
func packageSrcPcdpkgBytes(t *testing.T, srcPath string) []byte {
	t.Helper()
	src, err := os.ReadFile(srcPath)
	if err != nil {
		t.Fatalf("读取插件源码 %s：%v", srcPath, err)
	}
	base := "main.py"
	manifest := fmt.Sprintf(`manifest_version: 1
plugin:
  id: %s
  name: manual-src-probe
  type: CLOUD_PLUGIN
  version: 1.0.0
runtime:
  language: python
  version: "3.11"
permissions:
  - file.content.read_staging
  - file.content.write_pre_activation
  - file.content.read
  - platform.capability.invoke
entrypoints:
  events:
  - event: pcd.file.content.ready.v1
    module: src/%s
    function: main
    priority: 10
    permissions:
      - file.content.read_staging
      - file.content.write_pre_activation
exports:
  - name: run
    module: src/%s
    function: main
    permissions:
      - file.content.read
      - platform.capability.invoke
`, fixtureManifestID("manual-src", 3), base, base)
	return pcdpkgBytes(t, manifest, map[string][]byte{"src/" + base: src})
}

// --- 输入解析 ----

// resolveInput 从 环境变量/目录内 input.* 解析插件输入：
//   - capability 模式：必须是合法 JSON 对象（非 JSON 或不含对象即报错并终止）。
//   - 内容模式（execute/post_available）：原样字节注入，插件经 pycloud.file.read 读取。
//
// 优先级：PCD_DEBUG_INPUT_FILE > PCD_DEBUG_INPUT > <dir>/input.json > <dir>/input.txt > 空。
func resolveInput(t *testing.T, pluginDir string, capabilityMode bool) (content []byte, params map[string]any) {
	t.Helper()
	var raw []byte
	switch {
	case os.Getenv(envInputFile) != "":
		path := resolvePathArg(os.Getenv(envInputFile))
		data, err := os.ReadFile(path)
		if err != nil {
			t.Fatalf("读取 PCD_DEBUG_INPUT_FILE=%s：%v", path, err)
		}
		raw = data
	case os.Getenv(envInputJSON) != "":
		raw = []byte(os.Getenv(envInputJSON))
	case pluginDir != "":
		for _, name := range []string{"input.json", "input.txt"} {
			if data, err := os.ReadFile(filepath.Join(pluginDir, name)); err == nil {
				raw = data
				break
			}
		}
	}
	if capabilityMode {
		if len(raw) == 0 {
			t.Logf("等待能力输入：未提供 PCD_DEBUG_INPUT/INPUT_FILE/input.json，注入空参数")
			params = map[string]any{}
			return nil, params
		}
		if err := json.Unmarshal(raw, &params); err != nil || params == nil {
			t.Fatalf("能力输入参数必须是合法 JSON 对象，当前内容无法解析（%v）：%s",
				err, truncate(string(raw), 200))
		}
		return nil, params
	}
	t.Logf("注入内容输入（%d 字节）：%s", len(raw), truncate(string(raw), 120))
	return raw, nil
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n] + "...(已截断)"
}

// dumpResult 打印 Runner 返回结果（结构体 + JSON 两种形态，便于对照字段）。
func dumpResult(t *testing.T, name string, value any) {
	t.Helper()
	pretty, _ := json.MarshalIndent(value, "", "  ")
	t.Logf("==> %s 结果 struct: %#v", name, value)
	t.Logf("==> %s 结果 JSON :\n%s", name, string(pretty))
}

// dumpLogs 把容器 stdout/stderr 日志单独打印成段（与 struct/JSON 分开），
// 便于直接观察插件 print / pycloud.log / runner.py、restricted.py 输出。
// 日志为 runner.go 结果模型 Logs 字段（已脱敏，保留换行）。
func dumpLogs(t *testing.T, mode, logs string) {
	t.Helper()
	t.Logf("====> [容器日志] %s", mode)
	if logs == "" {
		t.Logf("      <空>（容器未输出或日志为空）")
		return
	}
	for _, line := range strings.Split(logs, "\n") {
		t.Logf("      %s", line)
	}
	t.Logf("====> [容器日志结束] %s", mode)
}

// --- TestManualPluginDriver：env 驱动，跑一个真实容器 ----

func TestManualPluginDriver(t *testing.T) {
	dir := resolvePathArg(os.Getenv(envPluginDir))
	pkgPath := resolvePathArg(os.Getenv(envPluginPkg))
	srcPath := resolvePathArg(os.Getenv(envPluginSrc))
	if dir == "" && pkgPath == "" && srcPath == "" {
		t.Skip("手动驱动未指定插件来源。设置 PCD_DEBUG_PLUGIN_DIR / PCD_DEBUG_PLUGIN_PKG / " +
			"PCD_DEBUG_PLUGIN_SRC 之一（见 scripts/run_manual_plugin.sh --help）")
	}
	dockerAvailable(t)

	capability := os.Getenv(envCapability)
	event := os.Getenv(envEvent)
	if event == "" {
		if capability != "" {
			event = EventContentReady
		} else {
			event = EventContentReady
		}
	}
	capabilityMode := capability != ""
	postAvailableMode := event == EventContentAvailable

	// 1) 构造受约束 .pcdpkg + 通过 internal/package 双层校验（manifest.go + parse.go）。
	var zipBytes []byte
	var manualDir string
	var parsed *pkg.Parsed
	var manifestJSON string
	switch {
	case dir != "":
		manualDir = dir
		// 1a) manifest.go：先独立校验清单 YAML 规范并打印解析结果。
		manifestData, err := os.ReadFile(filepath.Join(dir, "manifest.yaml"))
		if err != nil {
			t.Fatalf("读取 %s/manifest.yaml：%v", dir, err)
		}
		manifest, err := pkg.ParseManifestBytes(manifestData)
		if err != nil {
			t.Fatalf("manifest.go 校验失败（%v）：%v", dir, err)
		}
		pretty, _ := json.MarshalIndent(manifest, "", "  ")
		manifestJSON = string(pretty)
		t.Logf("==> 插件目录 %s 的 manifest.yaml 经 manifest.go 校验通过：\n%s", dir, manifestJSON)
		zipBytes = packageDirPcdpkgBytes(t, dir)
	case pkgPath != "":
		manualDir = ""
		archive, err := os.ReadFile(pkgPath)
		if err != nil {
			t.Fatalf("读取 .pcdpkg %s：%v", pkgPath, err)
		}
		zipBytes = archive
	default: // srcPath
		manualDir = ""
		zipBytes = packageSrcPcdpkgBytes(t, srcPath)
	}

	// 1b) parse.go：受约束 ZIP + 结构/安全校验（路径穿越/链接/敏感文件/数量/体积/行数等）。
	dest := t.TempDir()
	archivePath := filepath.Join(dest, "plugin.pcdpkg")
	if err := os.WriteFile(archivePath, zipBytes, 0o600); err != nil {
		t.Fatalf("写入临时 .pcdpkg：%v", err)
	}
	parsed, err := pkg.Parse(archivePath, filepath.Join(dest, "plugin"), pkg.Options{})
	if err != nil {
		t.Fatalf("pkg.Parse 校验失败：%v", err)
	}
	t.Logf("==> parse.go 校验通过：plugin=%s v=%s files=%d bytes=%d modules=%v limits=%+v",
		parsed.Manifest.Plugin.ID, parsed.Manifest.Plugin.Version,
		parsed.FileCount, parsed.TotalBytes, parsed.Modules, parsed.Manifest.Limits)
	if manifestJSON == "" {
		pretty, _ := json.MarshalIndent(parsed.Manifest, "", "  ")
		manifestJSON = string(pretty)
	}
	if capabilityMode {
		if _, ok := parsed.Manifest.ExportByName(capability); !ok {
			t.Logf("警告：manifest exports 未声明能力 %q，ExecuteCapability 将返回 EXPORT_NOT_FOUND", capability)
		}
	}

	// 2) 解析输入。
	content, params := resolveInput(t, manualDir, capabilityMode)

	// 3) 构造与集成测试一致的 Runner（fakes + capabilityRelay + 真实 Docker 配置）。
	cfg := integrationConfig(t)
	if secs := os.Getenv(envTimeout); secs != "" {
		var seconds int
		if _, err := fmt.Sscanf(secs, "%d", &seconds); err != nil || seconds <= 0 {
			t.Fatalf("PCD_DEBUG_TIMEOUT 必须是正整数秒")
		}
		cfg.ExecutionTimeout = time.Duration(seconds) * time.Second
	}
	if mb := os.Getenv(envMemoryMB); mb != "" {
		var mib int
		if _, err := fmt.Sscanf(mb, "%d", &mib); err != nil || mib <= 0 {
			t.Fatalf("PCD_DEBUG_MEMORY_MB 必须是正整数")
		}
		cfg.MemoryBytes = int64(mib) * 1024 * 1024
	}
	if strings.EqualFold(os.Getenv(envNoRestricted), "1") || strings.EqualFold(os.Getenv(envNoRestricted), "true") {
		cfg.DisableRestrictedPython = true
		t.Logf("已按 PCD_DEBUG_DISABLE_RESTRICTED 关闭受限 Python 层（探针模式）")
	}
	if strings.EqualFold(os.Getenv(envVerifyDigest), "1") || strings.EqualFold(os.Getenv(envVerifyDigest), "true") {
		cfg.RequireSandboxDigest = true
		cfg.SandboxImageDigest = os.Getenv("PLUGIN_SANDBOX_IMAGE_DIGEST")
		t.Logf("已开启镜像摘要门禁 RequireSandboxDigest（digest=%s）", cfg.SandboxImageDigest)
	}
	t.Logf("==> 沙箱配置：image=%s runtime=%s network=%s timeout=%v memory=%dMiB cpus=%s pids=%d restricted=%v",
		cfg.SandboxImage, cfg.SandboxRuntime, cfg.SandboxNetwork, cfg.ExecutionTimeout,
		cfg.MemoryBytes/1024/1024, cfg.CPUs, cfg.PidsLimit, !cfg.DisableRestrictedPython)

	versionID := "v1"
	packages := &fakePackages{zips: map[string][]byte{versionID: zipBytes}}
	brokerClient := &fakeBroker{
		downloadFn: func(ctx context.Context, gateID, executionID, lease, destination string) error {
			return os.WriteFile(destination, content, 0o400)
		},
		downloadActiveFn: func(ctx context.Context, fileID, executionID, actorUserID, spaceID, destination string) error {
			return os.WriteFile(destination, content, 0o400)
		},
	}
	relay := newCapabilityRelay()
	registerManualRelayDefaults(t, relay)
	runner := newTestRunner(t, cfg, brokerClient, packages)
	_ = runner.Sessions.Close()
	sessions, err := uds.NewManager(uds.Config{
		RootDir: runner.Config.SocketRoot, GroupID: os.Getgid(), MaxFrameBytes: runner.Config.SocketMaxFrameBytes,
		MaxConnectionsPerPeer: runner.Config.SocketMaxConnections, RequestsPerSecond: runner.Config.SocketRequestsPerSec,
		RequestBurst: runner.Config.SocketRequestBurst, RequestTimeout: runner.Config.SocketRequestTimeout,
	}, relay)
	if err != nil {
		t.Fatalf("创建手动调试 UDS 会话管理器失败：%v", err)
	}
	runner.Sessions = sessions
	defer sessions.Close()

	executionID := genExecID()
	mode := "execute"
	if postAvailableMode {
		mode = "post_available"
	}
	if capabilityMode {
		mode = "capability"
	}
	t.Logf("==> execution_id=%s event=%s capability=%q mode=%s", executionID, event, capability, mode)

	// 4) 按模式调用 runner.go 公开方法（与 integration_realworld_test.go 同款入口）。
	var chainResult model.RuntimeChainResult
	var capResult model.CapabilityExecutionResult
	switch {
	case capabilityMode:
		entry := model.Entrypoint{
			PluginID: "plugin-1", VersionID: versionID, Runtime: "PYTHON_3_11",
			Capability: capability, Permissions: []string{"file.content.read", "platform.capability.invoke"},
		}
		capResult = runner.ExecuteCapability(context.Background(), model.CapabilityExecutionRequest{
			ExecutionID: executionID, StepID: "step_1", UserID: "user-1", SpaceID: "space-1",
			Input: params, Entrypoint: entry,
		})
		dumpResult(t, "ExecuteCapability", capResult)
		dumpLogs(t, "ExecuteCapability", capResult.Logs)
	case postAvailableMode:
		entry := model.Entrypoint{
			PluginID: "plugin-1", VersionID: versionID, Runtime: "PYTHON_3_11",
			Event: EventContentAvailable, Permissions: []string{"file.content.read"},
		}
		chainResult = runner.ExecutePostAvailable(context.Background(), postAvailableRequest(executionID, entry))
		dumpResult(t, "ExecutePostAvailable", chainResult)
		dumpLogs(t, "ExecutePostAvailable", chainResult.Logs)
	default:
		entry := executeEntries()
		entry[0].VersionID = versionID
		chainResult = runner.Execute(context.Background(), preprocessRequest(executionID, entry...))
		dumpResult(t, "Execute", chainResult)
		dumpLogs(t, "Execute (预激活内容链)", chainResult.Logs)
	}

	// 5) 打印能力网关收到的调用（观察插件经 pycloud 的受控能力通道）。
	calls := relay.callsSnapshot()
	if len(calls) == 0 {
		t.Logf("==> 本插件未发起任何能力调用")
	} else {
		for i, call := range calls {
			rawParams, _ := json.Marshal(call.Parameters)
			t.Logf("==> 能力调用[%d] key=%s user=%s space=%s params=%s",
				i, call.CapabilityKey, call.UserID, call.SpaceID, string(rawParams))
		}
	}

	// 6) 收尾：容器无残留（--rm + forceRemove 兜底已由 runner.go 保障）。
	assertNoResidual(t, executionID)
	t.Logf("==> 手动驱动完成：容器已清理、无残留")
}

// registerManualRelayDefaults 注册一组"安全回显"能力 mock：让未配 mock 的能力调用
// 不至于让插件天折，同时调用记录会打印。按需在本函数内增删。
func registerManualRelayDefaults(t *testing.T, relay *capabilityRelay) {
	t.Helper()
	relay.handle("api.user.info", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{"user_id": "user-1", "nickname": "u***r", "email": "***@example.com"}, "", ""
	})
	relay.handle("api.file.content.get", func(params map[string]any) (map[string]any, string, string) {
		if path, _ := params["path"].(string); strings.HasPrefix(path, "/etc/") {
			return nil, "CAPABILITY_FORBIDDEN", "路径不在可访问白名单"
		}
		return map[string]any{"name": "file.txt", "content": "mock"}, "", ""
	})
	relay.handle("api.file.generate_excel", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{"content_type": "text/csv", "content": "product,amount,price\ndisk,3,99.5\n"}, "", ""
	})
	relay.handle("api.space.members.list", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{"members": []map[string]any{{"user_id": "user-1"}}}, "", ""
	})
	relay.handle("api.notification.send", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{"sent": true}, "", ""
	})
	relay.handle("api.file.move", func(params map[string]any) (map[string]any, string, string) {
		return map[string]any{"ok": true, "destination": params["destination"]}, "", ""
	})
}

// --- TestManualRestrictedProbe：宿主机直接探针 restricted.py（无需 Docker） ----
//
// 直接用真实模块 sandbox/python/restricted.py 的 exec_plugin / guard_source /
// sys.addaudithook / _AttrGuard 验证受限层对任意用户代码的拦截效果；内置 5 组
// 探针（import os / eval / 双下划线逃逸链 / 经 SDK 句柄触发审计钩子 / 白名单 json）。

func TestManualRestrictedProbe(t *testing.T) {
	probe := os.Getenv(envRestrictedProbe)
	snippet := os.Getenv(envRestrictedSnippet)
	if probe == "" && snippet == "" {
		t.Skip("受限层探针未开启：PCD_DEBUG_RESTRICTED_PROBE=1 或 PCD_DEBUG_RESTRICTED_SNIPPET=<python 源码>")
	}
	python, err := exec.LookPath("python3")
	if err != nil {
		t.Skip("未找到 python3")
	}
	probes := []struct {
		name   string
		source string
		want   string
	}{
		{"import_os",
			"import os\ndef main(context):\n    return {\"uid\": os.getuid()}\n",
			"禁止导入模块"},
		{"eval_builtin",
			"def main(context):\n    return eval(\"1+1\")\n",
			"受限环境禁用内置"},
		{"dunder_escape",
			"def main(context):\n    return ().__class__.__bases__[0].__subclasses__()\n",
			"双下划线逃逸属性"},
		{"audit_os_system_via_sdk",
			"def main(context):\n    import pycloud\n    pycloud.file.os.system(\"id\")\n    return {}\n",
			"审计钩子阻断"},
		{"whitelist_json_math",
			"import json, math\ndef main(context):\n    return json.dumps({\"pi\": math.pi})\n",
			"OK"},
	}
	driver := `import sys, types
import restricted

class _File:
    os = __import__("os")
class _Sdk:
    file = _File()
    def __getattr__(self, name):
        return types.SimpleNamespace()
pycloud = _Sdk()

def run(name, source):
    print("===probe:%s===" % name)
    try:
        out = restricted.exec_plugin(source, "<probe>." + name + ".py", "main", {}, pycloud_module=pycloud)
        print("OUTCOME:OK result=%r" % (out,))
    except restricted.RestrictedError as e:
        print("OUTCOME:INTERCEPTED %s" % (e,))
    except BaseException as e:
        print("OUTCOME:ERROR %s: %s" % (type(e).__name__, e))
`
	var script strings.Builder
	script.WriteString(driver)
	for _, p := range probes {
		if snippet != "" {
			continue // 自定义场景只跑用户源码
		}
		fmt.Fprintf(&script, "run(%q, %s)\n", p.name, jsonQuote(p.source))
	}
	if snippet != "" {
		fmt.Fprintf(&script, "run(%q, %s)\n", "user_snippet", jsonQuote(snippet))
	}
	// go test 的 cwd 是包目录（internal/sandbox），PYTHONPATH 用绝对路径注入 restricted.py。
	pythonDir, absErr := filepath.Abs(filepath.Join("..", "..", "sandbox", "python"))
	if absErr != nil {
		t.Fatalf("解析 sandbox/python 路径：%v", absErr)
	}
	cmd := exec.Command(python, "-c", script.String())
	cmd.Env = append(os.Environ(), "PYTHONPATH="+pythonDir)
	output, err := cmd.CombinedOutput()
	if err != nil {
		t.Logf("受限层探针 python 进程退出码错误：%v（这可能意味着受限层本身抛出了非受限错误）", err)
	}
	t.Logf("==> restricted.py 探针输出：\n%s", string(output))

	if snippet == "" {
		for _, p := range probes {
			if p.want == "OK" {
				// 白名单探针必须走通（回归锚点）。
				if !strings.Contains(string(output), "===probe:"+p.name+"===\nOUTCOME:OK") {
					t.Errorf("白名单探针 %s 应成功，实际未 OK：%s", p.name, string(output))
				}
				continue
			}
			// 恶意探针必须被拦截（拦截是观察对象，不因拦截而失败）。
			if !strings.Contains(string(output), p.want) {
				t.Logf("提示：探针 %s 未命中预期提示 %q —— 受限层行为可能已变化，请人工确认：\n%s",
					p.name, p.want, string(output))
			}
		}
	}
}

// jsonQuote 用 JSON 编码把 python 源码安全嵌入 python -c 字符串字面量。
func jsonQuote(s string) string {
	b, _ := json.Marshal(s)
	return string(b)
}
