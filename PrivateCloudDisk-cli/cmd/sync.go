package cmd

import (
	"fmt"
	"os"
	"path/filepath"
	"strings"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/privateclouddisk/cli/task"
	"github.com/privateclouddisk/cli/transfer"
	"github.com/privateclouddisk/cli/utils"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(syncCmd)
}

var syncCmd = &cobra.Command{
	Use:   "sync <local-dir> [remote-path]",
	Short: "同步本地目录到云盘",
	Long: `将本地目录同步到私有云盘。

支持增量同步，自动跳过已存在的文件。

示例:
  pcd sync ./documents                    # 同步到根目录
  pcd sync ./backups /backups             # 同步到指定目录
  pcd sync ./data --dry-run               # 模拟运行，不实际同步`,
	Args: cobra.MinimumNArgs(1),
	RunE: runSync,
}

func runSync(cmd *cobra.Command, args []string) error {
	localPath := utils.ResolvePath(args[0])

	// 检查本地路径
	fileInfo, err := os.Stat(localPath)
	if err != nil {
		return fmt.Errorf("本地路径不存在: %s", localPath)
	}
	if !fileInfo.IsDir() {
		return fmt.Errorf("sync 命令仅支持目录同步，请使用 upload 命令上传单个文件")
	}

	auth, err := config.EnsureLoggedIn()
	if err != nil {
		return err
	}

	cfg, err := config.LoadConfig()
	if err != nil {
		return err
	}
	cfg.Token = auth.Token
	client := api.NewClient(cfg)

	// 确定目标节点
	var targetNodeID string
	if len(args) > 1 {
		path := args[1]
		if strings.HasPrefix(path, "/") {
			nid, err := findNodeByPath(client, path)
			if err != nil {
				// 创建目标目录
				parts := strings.Split(strings.Trim(path, "/"), "/")
				root, _ := client.GetRootNode()
				parentID := root.NodeID
				created := 0
				for _, part := range parts {
					if part == "" {
						continue
					}
					// 尝试查找
					children, _ := client.ListChildren(parentID)
					found := false
					for _, child := range children {
						if child.NodeName == part && child.NodeType == "FOLDER" {
							parentID = child.NodeID
							found = true
							break
						}
					}
					if !found {
						if err := client.CreateFolder(parentID, part); err != nil {
							return fmt.Errorf("创建目录 %s 失败: %w", part, err)
						}
						created++
						// 重新获取节点 ID
						children, _ := client.ListChildren(parentID)
						for _, child := range children {
							if child.NodeName == part {
								parentID = child.NodeID
								break
							}
						}
					}
				}
				targetNodeID = parentID
				if created > 0 {
					fmt.Printf("已创建 %d 个目录\n", created)
				}
			} else {
				targetNodeID = nid
			}
		} else {
			targetNodeID = path
		}
	} else {
		root, _ := client.GetRootNode()
		targetNodeID = root.NodeID
	}

	fmt.Printf("同步目录: %s -> %s\n", localPath, targetNodeID)

	// 任务管理
	taskMgr, err := task.NewManager()
	if err != nil {
		return fmt.Errorf("创建任务管理器失败: %w", err)
	}
	defer taskMgr.Close()

	// 遍历本地文件
	uploadMgr := transfer.NewUploadManager(cfg)
	successCount := 0
	failCount := 0
	skipCount := 0

	err = filepath.Walk(localPath, func(path string, info os.FileInfo, err error) error {
		if err != nil {
			return err
		}
		if info.IsDir() {
			return nil
		}
		// 跳过隐藏文件
		if strings.HasPrefix(info.Name(), ".") {
			return nil
		}

		relPath, _ := filepath.Rel(localPath, path)
		fmt.Printf("\n[%s] %s (%s)\n", relPath, info.Name(), utils.FormatSize(info.Size()))

		if cfg.DryRun {
			fmt.Println("  (模拟运行，跳过)")
			skipCount++
			return nil
		}

		// 创建任务
		taskEntity := &task.Task{
			Type:        task.TypeSync,
			Status:      task.StatusRunning,
			Source:      path,
			Destination: targetNodeID,
			FileName:    info.Name(),
			FileSize:    info.Size(),
		}
		if err := taskMgr.CreateTask(taskEntity); err != nil {
			fmt.Printf("  警告: 创建任务记录失败: %v\n", err)
		}

		if err := uploadMgr.UploadFile(client, path, targetNodeID); err != nil {
			fmt.Printf("  上传失败: %v\n", err)
			taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusFailed, err.Error())
			failCount++
			return nil // 继续下一个文件
		}

		taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusCompleted, "")
		successCount++
		return nil
	})

	if err != nil {
		return fmt.Errorf("同步过程中出错: %w", err)
	}

	fmt.Printf("\n=== 同步完成 ===\n")
	fmt.Printf("  成功: %d\n", successCount)
	fmt.Printf("  失败: %d\n", failCount)
	fmt.Printf("  跳过: %d\n", skipCount)

	return nil
}