package cmd

import (
	"fmt"

	"github.com/privateclouddisk/cli/task"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(taskCmd)
	taskCmd.AddCommand(taskListCmd)
	taskCmd.AddCommand(taskShowCmd)
	taskCmd.AddCommand(taskDeleteCmd)
	taskCmd.AddCommand(taskCleanCmd)
	taskCmd.AddCommand(taskRetryCmd)
}

var taskCmd = &cobra.Command{
	Use:   "task",
	Short: "管理任务",
	Long:  "查看和管理上传/下载任务。",
	Run: func(cmd *cobra.Command, args []string) {
		cmd.Help()
	},
}

var (
	taskFilterType   string
	taskFilterStatus string
	taskLimit        int
)

var taskListCmd = &cobra.Command{
	Use:   "list",
	Short: "列出所有任务",
	Long: `列出所有上传/下载任务。

示例:
  pcd task list                       # 列出所有任务
  pcd task list --type UPLOAD         # 仅列出上传任务
  pcd task list --status FAILED       # 仅列出失败任务
  pcd task list --limit 10            # 限制数量`,
	RunE: runTaskList,
}

func init() {
	taskListCmd.Flags().StringVar(&taskFilterType, "type", "", "任务类型 (UPLOAD/DOWNLOAD/SYNC)")
	taskListCmd.Flags().StringVar(&taskFilterStatus, "status", "", "任务状态 (PENDING/RUNNING/COMPLETED/FAILED)")
	taskListCmd.Flags().IntVar(&taskLimit, "limit", 50, "显示数量")
}

func runTaskList(cmd *cobra.Command, args []string) error {
	mgr, err := task.NewManager()
	if err != nil {
		return err
	}
	defer mgr.Close()

	// 显示摘要
	summary, err := mgr.GetSummary()
	if err == nil {
		fmt.Printf("任务统计: 总计 %d | 运行中 %d | 已完成 %d | 失败 %d\n\n",
			summary.Total, summary.Running, summary.Completed, summary.Failed)
	}

	filter := task.TaskFilter{
		Type:   task.TaskType(taskFilterType),
		Status: task.TaskStatus(taskFilterStatus),
		Limit:  taskLimit,
	}

	return mgr.PrintTaskList(filter)
}

var taskShowCmd = &cobra.Command{
	Use:   "show <task-id>",
	Short: "查看任务详情",
	Long:  "查看指定任务的详细信息。",
	Args:  cobra.ExactArgs(1),
	RunE:  runTaskShow,
}

func runTaskShow(cmd *cobra.Command, args []string) error {
	var id int64
	if _, err := fmt.Sscanf(args[0], "%d", &id); err != nil {
		return fmt.Errorf("无效的任务 ID: %s", args[0])
	}

	mgr, err := task.NewManager()
	if err != nil {
		return err
	}
	defer mgr.Close()

	t, err := mgr.GetTask(id)
	if err != nil {
		return err
	}
	if t == nil {
		return fmt.Errorf("任务不存在: %d", id)
	}

	fmt.Println("=== 任务详情 ===")
	fmt.Printf("  ID:         %d\n", t.ID)
	fmt.Printf("  类型:       %s\n", t.Type)
	fmt.Printf("  状态:       %s\n", t.Status)
	fmt.Printf("  文件名:     %s\n", t.FileName)
	fmt.Printf("  文件大小:   %s\n", formatTaskSize(t.FileSize))
	fmt.Printf("  来源:       %s\n", t.Source)
	fmt.Printf("  目标:       %s\n", t.Destination)
	fmt.Printf("  进度:       %d/%d 分片 (%.1f%%)\n",
		t.DoneChunks, t.TotalChunks,
		float64(t.Progress)/float64(t.FileSize)*100)
	fmt.Printf("  上传会话:   %s\n", t.UploadsID)
	fmt.Printf("  文件ID:     %s\n", t.FileID)
	if t.Error != "" {
		fmt.Printf("  错误:       %s\n", t.Error)
	}
	fmt.Printf("  创建时间:   %s\n", t.CreatedAt.Format("2006-01-02 15:04:05"))
	if t.CompletedAt != nil {
		fmt.Printf("  完成时间:   %s\n", t.CompletedAt.Format("2006-01-02 15:04:05"))
	}

	return nil
}

var taskDeleteCmd = &cobra.Command{
	Use:   "delete <task-id>",
	Short: "删除任务",
	Long:  "删除指定的任务记录。",
	Args:  cobra.ExactArgs(1),
	RunE:  runTaskDelete,
}

func runTaskDelete(cmd *cobra.Command, args []string) error {
	var id int64
	if _, err := fmt.Sscanf(args[0], "%d", &id); err != nil {
		return fmt.Errorf("无效的任务 ID: %s", args[0])
	}

	mgr, err := task.NewManager()
	if err != nil {
		return err
	}
	defer mgr.Close()

	if err := mgr.DeleteTask(id); err != nil {
		return err
	}

	fmt.Printf("任务已删除: %d\n", id)
	return nil
}

var taskCleanCmd = &cobra.Command{
	Use:   "clean",
	Short: "清理已完成/失败的任务",
	Long:  "清理所有已完成和失败的任务记录。",
	RunE:  runTaskClean,
}

func runTaskClean(cmd *cobra.Command, args []string) error {
	mgr, err := task.NewManager()
	if err != nil {
		return err
	}
	defer mgr.Close()

	n1, err := mgr.CleanCompleted()
	if err != nil {
		return err
	}
	n2, err := mgr.CleanFailed()
	if err != nil {
		return err
	}

	fmt.Printf("已清理: %d 个已完成任务, %d 个失败任务\n", n1, n2)
	return nil
}

var taskRetryCmd = &cobra.Command{
	Use:   "retry",
	Short: "重试所有失败任务",
	Long:  "将所有失败任务重置为待处理状态，以便重新执行。",
	RunE:  runTaskRetry,
}

func runTaskRetry(cmd *cobra.Command, args []string) error {
	mgr, err := task.NewManager()
	if err != nil {
		return err
	}
	defer mgr.Close()

	n, err := mgr.RetryFailed()
	if err != nil {
		return err
	}

	fmt.Printf("已重置 %d 个失败任务\n", n)
	return nil
}

func formatTaskSize(size int64) string {
	if size == 0 {
		return "-"
	}
	units := []string{"B", "KB", "MB", "GB", "TB"}
	unitIndex := 0
	fsize := float64(size)
	for fsize >= 1024 && unitIndex < len(units)-1 {
		fsize /= 1024
		unitIndex++
	}
	return fmt.Sprintf("%.1f%s", fsize, units[unitIndex])
}