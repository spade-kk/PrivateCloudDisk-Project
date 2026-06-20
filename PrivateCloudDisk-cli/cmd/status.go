package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/privateclouddisk/cli/task"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(statusCmd)
}

var (
	statusJSON bool
)

var statusCmd = &cobra.Command{
	Use:   "status",
	Short: "显示 CLI 安装状态和系统信息",
	Long: `显示 PrivateCloudDisk CLI 的安装状态、版本信息、配置摘要和系统环境。

包括:
  安装信息: 二进制路径、版本、构建信息
  认证状态: 登录用户、Token 有效期
  服务连接: 服务端连通性、延迟
  配置摘要: 分片大小、并发数等
  任务统计: 上传/下载任务状态
  系统环境: OS、架构、Go 版本

示例:
  pcd status                          # 完整状态
  pcd status --json                   # JSON 格式输出`,
	RunE: runStatus,
}

func init() {
	statusCmd.Flags().BoolVar(&statusJSON, "json", false, "以 JSON 格式输出")
}

// StatusInfo 完整状态信息
type StatusInfo struct {
	Version        VersionInfo       `json:"version"`
	Installation   InstallationInfo  `json:"installation"`
	Authentication AuthStatusInfo    `json:"authentication"`
	Server         ServerStatusInfo  `json:"server"`
	Configuration  ConfigSummaryInfo `json:"configuration"`
	Tasks          TaskStatusInfo    `json:"tasks"`
	System         SystemInfo        `json:"system"`
}

type VersionInfo struct {
	Version   string `json:"version"`
	Commit    string `json:"commit"`
	BuildTime string `json:"build_time"`
}

type InstallationInfo struct {
	BinaryPath string `json:"binary_path"`
	ConfigDir  string `json:"config_dir"`
	ConfigFile string `json:"config_file"`
	TokenFile  string `json:"token_file"`
	TaskDBFile string `json:"task_db_file"`
	LogDir     string `json:"log_dir"`
}

type AuthStatusInfo struct {
	LoggedIn     bool   `json:"logged_in"`
	Account      string `json:"account,omitempty"`
	UserID       string `json:"user_id,omitempty"`
	UserName     string `json:"user_name,omitempty"`
	TokenExpires string `json:"token_expires,omitempty"`
	TokenValid   bool   `json:"token_valid"`
}

type ServerStatusInfo struct {
	Endpoint   string `json:"endpoint"`
	Reachable  bool   `json:"reachable"`
	LatencyMs  int64  `json:"latency_ms,omitempty"`
	ServerName string `json:"server_name,omitempty"`
}

type ConfigSummaryInfo struct {
	ChunkSizeMB     int  `json:"chunk_size_mb"`
	MaxConcurrency  int  `json:"max_concurrency"`
	MaxRetries      int  `json:"max_retries"`
	RetryBackoffMs  int  `json:"retry_backoff_ms"`
	DownloadWorkers int  `json:"download_workers"`
	SyncIntervalS   int  `json:"sync_interval_s"`
	DryRun          bool `json:"dry_run"`
}

type TaskStatusInfo struct {
	Total     int `json:"total"`
	Pending   int `json:"pending"`
	Running   int `json:"running"`
	Completed int `json:"completed"`
	Failed    int `json:"failed"`
}

type SystemInfo struct {
	OS        string `json:"os"`
	Arch      string `json:"arch"`
	GoVersion string `json:"go_version"`
	Hostname  string `json:"hostname"`
	CPUCores  int    `json:"cpu_cores"`
}

func runStatus(cmd *cobra.Command, args []string) error {
	info := &StatusInfo{}

	// ============================================================
	// 1. 版本信息
	// ============================================================
	info.Version = VersionInfo{
		Version:   version,
		Commit:    commit,
		BuildTime: buildTime,
	}

	// ============================================================
	// 2. 安装信息
	// ============================================================
	binaryPath, _ := os.Executable()
	binaryPath, _ = filepath.EvalSymlinks(binaryPath)
	info.Installation = InstallationInfo{
		BinaryPath: binaryPath,
		ConfigDir:  config.ConfigDir(),
		ConfigFile: config.ConfigFile(),
		TokenFile:  config.TokenFile(),
		TaskDBFile: config.TaskDBFile(),
		LogDir:     config.LogDir(),
	}

	// ============================================================
	// 3. 认证状态
	// ============================================================
	info.Authentication = AuthStatusInfo{}
	auth, authErr := config.LoadAuth()
	if authErr == nil && auth != nil {
		info.Authentication.LoggedIn = true
		info.Authentication.Account = auth.Account
		info.Authentication.UserID = auth.UserID
		info.Authentication.TokenExpires = auth.ExpiresAt.Format("2006-01-02 15:04:05")
		info.Authentication.TokenValid = time.Now().Before(auth.ExpiresAt)
	}

	// ============================================================
	// 4. 服务端状态
	// ============================================================
	cfg, err := config.LoadConfig()
	if err == nil {
		info.Server.Endpoint = cfg.Endpoint

		// 尝试连接服务端
		client := api.NewClient(cfg)
		if auth != nil && auth.Token != "" {
			client.SetToken(auth.Token)
		}

		start := time.Now()
		profile, err := client.GetUserProfile()
		elapsed := time.Since(start)

		if err == nil && profile != nil {
			info.Server.Reachable = true
			info.Server.LatencyMs = elapsed.Milliseconds()
			info.Server.ServerName = "PrivateCloudDisk"
			info.Authentication.UserName = profile.Name
		} else {
			// 尝试健康检查（使用任意 GET 端点探测连通性）
			var healthResp map[string]interface{}
			start = time.Now()
			healthErr := client.Get("/business/users/me", &healthResp)
			elapsed = time.Since(start)
			info.Server.Reachable = (healthErr == nil)
			info.Server.LatencyMs = elapsed.Milliseconds()
		}

		// 配置摘要
		info.Configuration = ConfigSummaryInfo{
			ChunkSizeMB:     cfg.ChunkSizeMB,
			MaxConcurrency:  cfg.MaxConcurrency,
			MaxRetries:      cfg.MaxRetries,
			RetryBackoffMs:  cfg.RetryBackoffMs,
			DownloadWorkers: cfg.DownloadWorkers,
			SyncIntervalS:   cfg.SyncIntervalS,
			DryRun:          cfg.DryRun,
		}
	}

	// ============================================================
	// 5. 任务统计
	// ============================================================
	info.Tasks = TaskStatusInfo{}
	taskMgr, err := task.NewManager()
	if err == nil {
		summary, err := taskMgr.GetSummary()
		if err == nil && summary != nil {
			info.Tasks = TaskStatusInfo{
				Total:     summary.Total,
				Pending:   summary.Pending,
				Running:   summary.Running,
				Completed: summary.Completed,
				Failed:    summary.Failed,
			}
		}
		_ = taskMgr.Close()
	}

	// ============================================================
	// 6. 系统信息
	// ============================================================
	hostname, _ := os.Hostname()
	info.System = SystemInfo{
		OS:        runtime.GOOS,
		Arch:      runtime.GOARCH,
		GoVersion: runtime.Version(),
		Hostname:  hostname,
		CPUCores:  runtime.NumCPU(),
	}

	// ============================================================
	// 输出
	// ============================================================
	if statusJSON {
		return printStatusJSON(info)
	}
	return printStatusText(info)
}

// printStatusText 文本格式输出
func printStatusText(info *StatusInfo) error {
	// 顶部横幅
	fmt.Println("╔══════════════════════════════════════════════════════════════════╗")
	fmt.Println("║           PrivateCloudDisk CLI - 状态诊断                        ║")
	fmt.Println("╚══════════════════════════════════════════════════════════════════╝")
	fmt.Println()

	// ---- 版本 ----
	fmt.Println("━ 版本信息")
	fmt.Printf("  版本:       %s\n", info.Version.Version)
	fmt.Printf("  Commit:     %s\n", info.Version.Commit)
	fmt.Printf("  构建时间:   %s\n", info.Version.BuildTime)
	fmt.Println()

	// ---- 安装 ----
	fmt.Println("━ 安装信息")
	fmt.Printf("  二进制路径: %s\n", info.Installation.BinaryPath)
	fmt.Printf("  配置目录:   %s\n", info.Installation.ConfigDir)
	fmt.Printf("  配置文件:   %s\n", info.Installation.ConfigFile)
	fmt.Printf("  Token 文件: %s\n", info.Installation.TokenFile)
	fmt.Printf("  任务数据库: %s\n", info.Installation.TaskDBFile)
	fmt.Printf("  日志目录:   %s\n", info.Installation.LogDir)
	fmt.Println()

	// ---- 认证 ----
	fmt.Println("━ 认证状态")
	if info.Authentication.LoggedIn {
		validIcon := "[有效]"
		if !info.Authentication.TokenValid {
			validIcon = "[已过期]"
		}
		fmt.Printf("  状态:       已登录 %s\n", validIcon)
		if info.Authentication.UserName != "" {
			fmt.Printf("  用户:       %s\n", info.Authentication.UserName)
		}
		fmt.Printf("  账号:       %s\n", info.Authentication.Account)
		fmt.Printf("  User ID:    %s\n", info.Authentication.UserID)
		fmt.Printf("  Token 过期: %s\n", info.Authentication.TokenExpires)
	} else {
		fmt.Println("  状态:       未登录")
		fmt.Println("  提示:       使用 pcd login 登录")
	}
	fmt.Println()

	// ---- 服务端 ----
	fmt.Println("━ 服务端连接")
	fmt.Printf("  地址:       %s\n", info.Server.Endpoint)
	if info.Server.Reachable {
		fmt.Printf("  状态:       [可达]\n")
		fmt.Printf("  延迟:       %d ms\n", info.Server.LatencyMs)
		if info.Server.ServerName != "" {
			fmt.Printf("  服务名称:   %s\n", info.Server.ServerName)
		}
	} else {
		fmt.Println("  状态:       [不可达]")
	}
	fmt.Println()

	// ---- 配置 ----
	fmt.Println("━ 配置摘要")
	fmt.Printf("  分片大小:     %d MB\n", info.Configuration.ChunkSizeMB)
	fmt.Printf("  上传并发:     %d\n", info.Configuration.MaxConcurrency)
	fmt.Printf("  下载并发:     %d\n", info.Configuration.DownloadWorkers)
	fmt.Printf("  最大重试:     %d\n", info.Configuration.MaxRetries)
	fmt.Printf("  重试退避:     %d ms\n", info.Configuration.RetryBackoffMs)
	fmt.Printf("  同步间隔:     %d s\n", info.Configuration.SyncIntervalS)
	fmt.Printf("  模拟运行:     %v\n", info.Configuration.DryRun)
	fmt.Println()

	// ---- 任务 ----
	fmt.Println("━ 任务统计")
	fmt.Printf("  总计:     %d\n", info.Tasks.Total)
	fmt.Printf("  运行中:   %d\n", info.Tasks.Running)
	fmt.Printf("  已完成:   %d\n", info.Tasks.Completed)
	fmt.Printf("  失败:     %d\n", info.Tasks.Failed)
	fmt.Printf("  待处理:   %d\n", info.Tasks.Pending)
	fmt.Println()

	// ---- 系统 ----
	fmt.Println("━ 系统环境")
	fmt.Printf("  操作系统:   %s\n", info.System.OS)
	fmt.Printf("  架构:       %s\n", info.System.Arch)
	fmt.Printf("  Go 版本:    %s\n", info.System.GoVersion)
	fmt.Printf("  主机名:     %s\n", info.System.Hostname)
	fmt.Printf("  CPU 核心:   %d\n", info.System.CPUCores)
	fmt.Println()

	// 底部提示
	if !info.Authentication.LoggedIn {
		fmt.Println("提示: 运行 pcd login 以登录")
	}
	if info.Tasks.Failed > 0 {
		fmt.Printf("提示: 运行 pcd task retry 重试 %d 个失败任务\n", info.Tasks.Failed)
	}
	if !info.Server.Reachable {
		fmt.Println("提示: 运行 pcd config set endpoint <url> 修改服务地址")
	}

	return nil
}

// printStatusJSON JSON 格式输出
func printStatusJSON(info *StatusInfo) error {
	output := fmt.Sprintf(`{
  "version": {
    "version": %q,
    "commit": %q,
    "build_time": %q
  },
  "installation": {
    "binary_path": %q,
    "config_dir": %q,
    "config_file": %q,
    "token_file": %q,
    "task_db_file": %q,
    "log_dir": %q
  },
  "authentication": {
    "logged_in": %v,
    "account": %q,
    "user_id": %q,
    "user_name": %q,
    "token_expires": %q,
    "token_valid": %v
  },
  "server": {
    "endpoint": %q,
    "reachable": %v,
    "latency_ms": %d,
    "server_name": %q
  },
  "configuration": {
    "chunk_size_mb": %d,
    "max_concurrency": %d,
    "max_retries": %d,
    "retry_backoff_ms": %d,
    "download_workers": %d,
    "sync_interval_s": %d,
    "dry_run": %v
  },
  "tasks": {
    "total": %d,
    "pending": %d,
    "running": %d,
    "completed": %d,
    "failed": %d
  },
  "system": {
    "os": %q,
    "arch": %q,
    "go_version": %q,
    "hostname": %q,
    "cpu_cores": %d
  }
}`,
		info.Version.Version, info.Version.Commit, info.Version.BuildTime,
		info.Installation.BinaryPath, info.Installation.ConfigDir,
		info.Installation.ConfigFile, info.Installation.TokenFile,
		info.Installation.TaskDBFile, info.Installation.LogDir,
		info.Authentication.LoggedIn, info.Authentication.Account,
		info.Authentication.UserID, info.Authentication.UserName,
		info.Authentication.TokenExpires, info.Authentication.TokenValid,
		info.Server.Endpoint, info.Server.Reachable, info.Server.LatencyMs,
		info.Server.ServerName,
		info.Configuration.ChunkSizeMB, info.Configuration.MaxConcurrency,
		info.Configuration.MaxRetries, info.Configuration.RetryBackoffMs,
		info.Configuration.DownloadWorkers, info.Configuration.SyncIntervalS,
		info.Configuration.DryRun,
		info.Tasks.Total, info.Tasks.Pending, info.Tasks.Running,
		info.Tasks.Completed, info.Tasks.Failed,
		info.System.OS, info.System.Arch, info.System.GoVersion,
		info.System.Hostname, info.System.CPUCores,
	)
	fmt.Println(output)
	return nil
}

// statusBar 打印进度条
func statusBar(label string, current, total int64) string {
	pct := float64(current) / float64(total) * 100
	width := 30
	filled := int(pct / 100 * float64(width))
	bar := strings.Repeat("=", filled) + strings.Repeat("-", width-filled)
	return fmt.Sprintf("  %s [%s] %.1f%%", label, bar, pct)
}

// statusSeparator 打印分隔线
func statusSeparator() string {
	return strings.Repeat("-", 60)
}