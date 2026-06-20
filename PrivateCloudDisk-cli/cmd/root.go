package cmd

import (
	"fmt"
	"os"

	"github.com/spf13/cobra"
)

var (
	rootCmd = &cobra.Command{
		Use:   "pcd",
		Short: "PrivateCloudDisk CLI - 私有云盘命令行客户端",
		Long: `PrivateCloudDisk CLI (pcd) 是一个企业级私有云盘命令行客户端。
支持文件上传/下载、目录管理、全文搜索、文件同步等功能。

使用 "pcd [command] --help" 查看命令详情。`,
		Version: "",
		Run: func(cmd *cobra.Command, args []string) {
			cmd.Help()
		},
	}

	version, commit, buildTime string
)

// SetVersion 设置版本信息
func SetVersion(v, c, bt string) {
	version = v
	commit = c
	buildTime = bt
	rootCmd.Version = fmt.Sprintf("%s (commit: %s, built: %s)", version, commit, buildTime)
}

// Execute 执行根命令
func Execute() error {
	rootCmd.CompletionOptions.DisableDefaultCmd = true
	return rootCmd.Execute()
}

func init() {
	rootCmd.SetFlagErrorFunc(func(cmd *cobra.Command, err error) error {
		cmd.Println(err)
		cmd.Println(cmd.UsageString())
		os.Exit(1)
		return nil
	})
}