package cmd

import (
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"

	"github.com/privateclouddisk/cli/config"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(uninstallCmd)
}

var (
	uninstallForce   bool
	uninstallKeepConfig bool
)

var uninstallCmd = &cobra.Command{
	Use:   "uninstall",
	Short: "卸载 PrivateCloudDisk CLI",
	Long: `完整卸载 PrivateCloudDisk CLI 客户端。

此命令将执行以下操作:
  1. 删除 CLI 二进制文件
  2. 清除配置文件 (~/.cloud-cli/)
  3. 清除认证 Token
  4. 清除任务数据库 (SQLite)
  5. 清除 Shell 补全脚本
  6. 清除 PATH 环境变量配置

示例:
  pcd uninstall                      # 交互式卸载（需确认）
  pcd uninstall --force              # 强制卸载（跳过确认）
  pcd uninstall --keep-config        # 保留配置文件`,
	RunE: runUninstall,
}

func init() {
	uninstallCmd.Flags().BoolVarP(&uninstallForce, "force", "f", false, "强制卸载，跳过确认")
	uninstallCmd.Flags().BoolVar(&uninstallKeepConfig, "keep-config", false, "保留用户配置文件")
}

func runUninstall(cmd *cobra.Command, args []string) error {
	// ============================================================
	// 1. 收集卸载信息
	// ============================================================
	fmt.Println("╔══════════════════════════════════════════════════════════╗")
	fmt.Println("║       PrivateCloudDisk CLI - 企业级卸载程序              ║")
	fmt.Println("╚══════════════════════════════════════════════════════════╝")
	fmt.Println()

	items := collectUninstallItems()

	// 显示将删除的内容
	fmt.Println("以下内容将被删除:\n")
	for _, item := range items {
		status := "[存在]"
		if _, err := os.Stat(item.Path); os.IsNotExist(err) {
			status = "[不存在]"
		}
		fmt.Printf("  %s %s\n", status, item.Description)
	}
	fmt.Println()

	// ============================================================
	// 2. 确认
	// ============================================================
	if !uninstallForce {
		fmt.Print("确认卸载? 输入 'yes' 继续: ")
		var response string
		fmt.Scanln(&response)
		if strings.ToLower(strings.TrimSpace(response)) != "yes" {
			fmt.Println("已取消卸载")
			return nil
		}
		fmt.Println()
	}

	// ============================================================
	// 3. 执行删除
	// ============================================================
	successCount := 0
	failCount := 0

	for _, item := range items {
		if item.Skip {
			fmt.Printf("  ⏭ 跳过: %s\n", item.Description)
			continue
		}

		fmt.Printf("  🗑 删除: %s... ", item.Description)

		if _, err := os.Stat(item.Path); os.IsNotExist(err) {
			fmt.Println("不存在，跳过")
			successCount++
			continue
		}

		var err error
		if item.IsDir {
			err = os.RemoveAll(item.Path)
		} else {
			err = os.Remove(item.Path)
		}

		if err != nil {
			fmt.Printf("失败: %v\n", err)
			failCount++
		} else {
			fmt.Println("完成")
			successCount++
		}
	}

	// ============================================================
	// 4. 清理 Shell 配置
	// ============================================================
	if !uninstallKeepConfig {
		fmt.Println()
		fmt.Println("正在清理 Shell 配置...")
		cleanShellConfigs()
	}

	// ============================================================
	// 5. 结果
	// ============================================================
	fmt.Println()
	fmt.Println("╔══════════════════════════════════════════════════════════╗")
	fmt.Printf("║  卸载完成: 成功 %d, 失败 %d                              ║\n", successCount, failCount)
	fmt.Println("╚══════════════════════════════════════════════════════════╝")
	fmt.Println()

	if failCount > 0 {
		fmt.Println("部分文件可能因权限不足无法删除，请手动清理。")
	}

	if uninstallKeepConfig {
		fmt.Printf("配置文件保留在: %s\n", config.ConfigDir())
	}

	fmt.Println("感谢使用 PrivateCloudDisk CLI。")
	return nil
}

// UninstallItem 待卸载项
type UninstallItem struct {
	Path        string
	Description string
	IsDir       bool
	Skip        bool
}

// collectUninstallItems 收集所有待卸载的路径
func collectUninstallItems() []UninstallItem {
	var items []UninstallItem

	// 1. 二进制文件
	binaryPath := findBinaryPath()
	if binaryPath != "" {
		items = append(items, UninstallItem{
			Path:        binaryPath,
			Description: fmt.Sprintf("CLI 二进制文件 (%s)", binaryPath),
			IsDir:       false,
		})
	}

	// 2. 配置目录
	items = append(items, UninstallItem{
		Path:        config.ConfigDir(),
		Description: fmt.Sprintf("配置目录 (%s)", config.ConfigDir()),
		IsDir:       true,
		Skip:        uninstallKeepConfig,
	})

	// 3. 配置文件
	items = append(items, UninstallItem{
		Path:        config.ConfigFile(),
		Description: fmt.Sprintf("配置文件 (%s)", config.ConfigFile()),
		IsDir:       false,
		Skip:        uninstallKeepConfig,
	})

	// 4. Token 文件
	items = append(items, UninstallItem{
		Path:        config.TokenFile(),
		Description: fmt.Sprintf("认证 Token (%s)", config.TokenFile()),
		IsDir:       false,
		Skip:        uninstallKeepConfig,
	})

	// 5. 任务数据库
	items = append(items, UninstallItem{
		Path:        config.TaskDBFile(),
		Description: fmt.Sprintf("任务数据库 (%s)", config.TaskDBFile()),
		IsDir:       false,
		Skip:        uninstallKeepConfig,
	})

	// 6. 日志目录
	items = append(items, UninstallItem{
		Path:        config.LogDir(),
		Description: fmt.Sprintf("日志目录 (%s)", config.LogDir()),
		IsDir:       true,
		Skip:        uninstallKeepConfig,
	})

	// 7. Shell 补全脚本
	completionDirs := getCompletionPaths()
	for _, cp := range completionDirs {
		items = append(items, UninstallItem{
			Path:        cp,
			Description: fmt.Sprintf("Shell 补全 (%s)", cp),
			IsDir:       false,
			Skip:        uninstallKeepConfig,
		})
	}

	return items
}

// findBinaryPath 查找当前运行的二进制文件路径
func findBinaryPath() string {
	// 1. 尝试获取当前进程路径
	execPath, err := os.Executable()
	if err == nil {
		resolved, err := filepath.EvalSymlinks(execPath)
		if err == nil {
			return resolved
		}
		return execPath
	}

	// 2. 常见安装路径
	commonPaths := []string{
		"/usr/local/bin/pcd",
		"/usr/bin/pcd",
		"/opt/homebrew/bin/pcd",
		"/opt/pcd/bin/pcd",
	}

	// 3. GOPATH/bin
	gopath := os.Getenv("GOPATH")
	if gopath == "" {
		home, _ := os.UserHomeDir()
		gopath = filepath.Join(home, "go")
	}
	commonPaths = append(commonPaths, filepath.Join(gopath, "bin", "pcd"))

	// 4. GOBIN
	gobin := os.Getenv("GOBIN")
	if gobin != "" {
		commonPaths = append(commonPaths, filepath.Join(gobin, "pcd"))
	}

	// 5. PATH 中搜索
	pathEnv := os.Getenv("PATH")
	for _, dir := range filepath.SplitList(pathEnv) {
		candidate := filepath.Join(dir, "pcd")
		if runtime.GOOS == "windows" {
			candidate += ".exe"
		}
		commonPaths = append(commonPaths, candidate)
	}

	for _, p := range commonPaths {
		if _, err := os.Stat(p); err == nil {
			return p
		}
		// Windows 下尝试 .exe
		if runtime.GOOS == "windows" && !strings.HasSuffix(p, ".exe") {
			exePath := p + ".exe"
			if _, err := os.Stat(exePath); err == nil {
				return exePath
			}
		}
	}

	return ""
}

// getCompletionPaths 获取补全脚本安装路径
func getCompletionPaths() []string {
	var paths []string
	home, _ := os.UserHomeDir()

	switch runtime.GOOS {
	case "darwin":
		// macOS: Homebrew 补全路径
		paths = append(paths,
			filepath.Join(home, ".zshrc"),
			filepath.Join(home, ".bash_profile"),
			filepath.Join(home, ".bashrc"),
			filepath.Join("/usr/local/etc/bash_completion.d", "pcd"),
			filepath.Join("/opt/homebrew/etc/bash_completion.d", "pcd"),
			filepath.Join("/usr/local/share/zsh/site-functions", "_pcd"),
			filepath.Join("/opt/homebrew/share/zsh/site-functions", "_pcd"),
			filepath.Join(home, ".oh-my-zsh/completions", "_pcd"),
			filepath.Join("/usr/local/share/fish/vendor_completions.d", "pcd.fish"),
			filepath.Join("/opt/homebrew/share/fish/vendor_completions.d", "pcd.fish"),
		)
	case "linux":
		paths = append(paths,
			filepath.Join(home, ".bashrc"),
			filepath.Join(home, ".bash_profile"),
			filepath.Join(home, ".zshrc"),
			filepath.Join("/etc/bash_completion.d", "pcd"),
			filepath.Join("/usr/share/bash-completion/completions", "pcd"),
			filepath.Join("/usr/local/share/bash-completion/completions", "pcd"),
			filepath.Join("/usr/share/zsh/vendor-completions", "_pcd"),
			filepath.Join("/usr/local/share/zsh/site-functions", "_pcd"),
			filepath.Join(home, ".oh-my-zsh/completions", "_pcd"),
			filepath.Join("/usr/share/fish/vendor_completions.d", "pcd.fish"),
		)
	case "windows":
		// Windows: 通常通过 Scoop / Chocolatey 管理，补全在 PowerShell profile
		paths = append(paths,
			filepath.Join(home, "Documents", "WindowsPowerShell", "Microsoft.PowerShell_profile.ps1"),
			filepath.Join(home, "Documents", "PowerShell", "Microsoft.PowerShell_profile.ps1"),
			filepath.Join(home, "Documents", "WindowsPowerShell", "profile.ps1"),
		)
	}

	return paths
}

// cleanShellConfigs 清理 Shell 配置文件中的 pcd 相关配置
func cleanShellConfigs() {
	home, _ := os.UserHomeDir()

	shellRCFiles := []string{
		filepath.Join(home, ".zshrc"),
		filepath.Join(home, ".bashrc"),
		filepath.Join(home, ".bash_profile"),
		filepath.Join(home, ".profile"),
		filepath.Join(home, ".config/fish/config.fish"),
	}

	for _, rcFile := range shellRCFiles {
		cleanShellRCFile(rcFile)
	}

	// 清理 PowerShell profile
	if runtime.GOOS == "windows" {
		psProfiles := []string{
			filepath.Join(home, "Documents", "WindowsPowerShell", "Microsoft.PowerShell_profile.ps1"),
			filepath.Join(home, "Documents", "PowerShell", "Microsoft.PowerShell_profile.ps1"),
		}
		for _, psFile := range psProfiles {
			cleanShellRCFile(psFile)
		}
	}
}

// cleanShellRCFile 清理 Shell 配置文件中的 pcd 相关行
func cleanShellRCFile(filePath string) {
	data, err := os.ReadFile(filePath)
	if err != nil {
		return // 文件不存在，跳过
	}

	lines := strings.Split(string(data), "\n")
	var newLines []string
	removed := false

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)

		// 跳过包含 pcd 的配置行
		if strings.Contains(trimmed, "pcd") &&
			(strings.Contains(trimmed, "completion") ||
				strings.Contains(trimmed, "source") ||
				strings.Contains(trimmed, "PATH") ||
				strings.Contains(trimmed, "PrivateCloudDisk") ||
				strings.HasPrefix(trimmed, "# pcd") ||
				trimmed == "eval \"$(pcd completion bash)\"" ||
				trimmed == "source <(pcd completion bash)" ||
				trimmed == "source <(pcd completion zsh)" ||
				trimmed == "pcd completion fish | source" ||
				trimmed == "Invoke-Expression (& { (pcd completion powershell) })") {
			removed = true
			continue
		}
		newLines = append(newLines, line)
	}

	if removed {
		content := strings.Join(newLines, "\n")
		// 去掉末尾多余空行
		content = strings.TrimRight(content, "\n") + "\n"
		os.WriteFile(filePath, []byte(content), 0644)
		fmt.Printf("  ✓ 已清理: %s\n", filePath)
	}

	// 未找到 pcd 相关行，跳过
}

// ============================================================
// 补充: 清理 Homebrew / Scoop / Chocolatey 安装（可选）
// ============================================================

// uninstallHomebrew 通过 Homebrew 卸载
func uninstallHomebrew() error {
	if runtime.GOOS != "darwin" && runtime.GOOS != "linux" {
		return nil
	}

	// 检查 brew 是否可用
	if _, err := exec.LookPath("brew"); err != nil {
		return nil
	}

	fmt.Println("检测到 Homebrew，尝试通过 brew 卸载...")
	cmd := exec.Command("brew", "uninstall", "pcd", "--force")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}

// uninstallScoop 通过 Scoop 卸载 (Windows)
func uninstallScoop() error {
	if runtime.GOOS != "windows" {
		return nil
	}

	if _, err := exec.LookPath("scoop"); err != nil {
		return nil
	}

	fmt.Println("检测到 Scoop，尝试通过 scoop 卸载...")
	cmd := exec.Command("scoop", "uninstall", "pcd")
	cmd.Stdout = os.Stdout
	cmd.Stderr = os.Stderr
	return cmd.Run()
}