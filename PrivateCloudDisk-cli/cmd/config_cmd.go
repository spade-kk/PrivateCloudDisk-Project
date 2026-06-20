package cmd

import (
	"fmt"

	"github.com/privateclouddisk/cli/config"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(configCmd)
	configCmd.AddCommand(configShowCmd)
	configCmd.AddCommand(configSetCmd)
	configCmd.AddCommand(configResetCmd)
}

var configCmd = &cobra.Command{
	Use:   "config",
	Short: "管理配置",
	Long:  "查看和修改 CLI 客户端配置。",
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Help()
	},
}

var configShowCmd = &cobra.Command{
	Use:   "show",
	Short: "显示当前配置",
	Long:  "显示所有配置项及其当前值。",
	RunE:  runConfigShow,
}

func runConfigShow(cmd *cobra.Command, args []string) error {
	cfg, err := config.LoadConfig()
	if err != nil {
		return err
	}

	fmt.Println("=== 当前配置 ===")
	fmt.Printf("  服务地址:         %s\n", cfg.Endpoint)
	fmt.Printf("  分片大小:         %d MB\n", cfg.ChunkSizeMB)
	fmt.Printf("  最大并发数:       %d\n", cfg.MaxConcurrency)
	fmt.Printf("  最大重试次数:     %d\n", cfg.MaxRetries)
	fmt.Printf("  重试退避时间:     %d ms\n", cfg.RetryBackoffMs)
	fmt.Printf("  下载并发数:       %d\n", cfg.DownloadWorkers)
	fmt.Printf("  同步间隔:         %d s\n", cfg.SyncIntervalS)
	fmt.Printf("  模拟运行:         %v\n", cfg.DryRun)

	fmt.Println("\n=== 认证状态 ===")
	auth, err := config.LoadAuth()
	if err != nil {
		fmt.Println("  未登录")
	} else {
		fmt.Printf("  已登录: %s\n", auth.Account)
		fmt.Printf("  登录时间: %s\n", auth.LoginAt.Format("2006-01-02 15:04:05"))
		fmt.Printf("  Token 过期: %s\n", auth.ExpiresAt.Format("2006-01-02 15:04:05"))
		tokenDisplay := auth.Token[:20] + "..." + auth.Token[len(auth.Token)-10:]
		fmt.Printf("  Token: %s\n", tokenDisplay)
	}

	fmt.Println("\n=== 文件路径 ===")
	fmt.Printf("  配置目录: %s\n", config.ConfigDir())
	fmt.Printf("  配置文件: %s\n", config.ConfigFile())
	fmt.Printf("  认证文件: %s\n", config.TokenFile())
	fmt.Printf("  任务数据库: %s\n", config.TaskDBFile())

	return nil
}

var configSetCmd = &cobra.Command{
	Use:   "set <key> <value>",
	Short: "设置配置项",
	Long: `设置指定配置项的值。

可配置项:
  endpoint         服务地址
  chunk_size_mb    分片大小 (MB)
  max_concurrency  最大并发数
  max_retries      最大重试次数
  retry_backoff_ms 重试退避时间 (ms)
  download_workers 下载并发数
  sync_interval_s  同步间隔 (s)

示例:
  pcd config set endpoint https://disk.example.com/api
  pcd config set max_concurrency 8
  pcd config set chunk_size_mb 16`,
	Args: cobra.ExactArgs(2),
	RunE: runConfigSet,
}

func runConfigSet(cmd *cobra.Command, args []string) error {
	key := args[0]
	value := args[1]

	cfg, err := config.LoadConfig()
	if err != nil {
		return err
	}

	switch key {
	case "endpoint":
		cfg.Endpoint = value
	case "chunk_size_mb":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.ChunkSizeMB = v
	case "max_concurrency":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.MaxConcurrency = v
	case "max_retries":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.MaxRetries = v
	case "retry_backoff_ms":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.RetryBackoffMs = v
	case "download_workers":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.DownloadWorkers = v
	case "sync_interval_s":
		var v int
		fmt.Sscanf(value, "%d", &v)
		cfg.SyncIntervalS = v
	default:
		return fmt.Errorf("未知配置项: %s", key)
	}

	if err := config.SaveConfig(cfg); err != nil {
		return err
	}

	fmt.Printf("配置已更新: %s = %s\n", key, value)
	return nil
}

var configResetCmd = &cobra.Command{
	Use:   "reset",
	Short: "重置为默认配置",
	Long:  "将所有配置项重置为默认值。",
	RunE:  runConfigReset,
}

func runConfigReset(cmd *cobra.Command, args []string) error {
	cfg := config.DefaultConfig()
	if err := config.SaveConfig(cfg); err != nil {
		return err
	}
	fmt.Println("配置已重置为默认值")
	return nil
}