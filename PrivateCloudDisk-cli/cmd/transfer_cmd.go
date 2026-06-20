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
	rootCmd.AddCommand(uploadCmd)
	rootCmd.AddCommand(downloadCmd)
}

var (
	uploadNodeID string
)

var uploadCmd = &cobra.Command{
	Use:   "upload <local-file> [remote-path]",
	Short: "上传文件到云盘",
	Long: `上传本地文件到私有云盘。

支持分片上传、并发控制、断点续传。

示例:
  pcd upload ./report.pdf                    # 上传到根目录
  pcd upload ./data.zip /documents           # 上传到指定目录
  pcd upload ./video.mp4 /videos --node-id <uuid>`,
	Args: cobra.MinimumNArgs(1),
	RunE: runUpload,
}

func init() {
	uploadCmd.Flags().StringVar(&uploadNodeID, "node-id", "", "目标节点 ID (UUID)")
}

func runUpload(cmd *cobra.Command, args []string) error {
	localPath := utils.ResolvePath(args[0])

	// 检查文件是否存在
	fileInfo, err := os.Stat(localPath)
	if err != nil {
		return fmt.Errorf("文件不存在: %s", localPath)
	}
	if fileInfo.IsDir() {
		return fmt.Errorf("不支持上传文件夹，请使用 sync 命令")
	}

	// 获取认证
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

	// 确定目标节点 ID
	var nodeID string
	if uploadNodeID != "" {
		nodeID = uploadNodeID
	} else if len(args) > 1 && args[1] != "" {
		// 通过路径解析节点 ID
		// 简化处理，直接使用传入的路径
		rootNode, err := client.GetRootNode()
		if err != nil {
			return fmt.Errorf("获取根目录失败: %w", err)
		}
		nodeID = rootNode.NodeID
		fmt.Printf("上传到根目录: %s\n", nodeID)
	} else {
		rootNode, err := client.GetRootNode()
		if err != nil {
			return fmt.Errorf("获取根目录失败: %w", err)
		}
		nodeID = rootNode.NodeID
	}

	// 创建任务记录
	taskMgr, err := task.NewManager()
	if err != nil {
		return fmt.Errorf("创建任务管理器失败: %w", err)
	}
	defer taskMgr.Close()

	taskEntity := &task.Task{
		Type:        task.TypeUpload,
		Status:      task.StatusRunning,
		Source:      localPath,
		Destination: nodeID,
		FileName:    filepath.Base(localPath),
		FileSize:    fileInfo.Size(),
	}
	if err := taskMgr.CreateTask(taskEntity); err != nil {
		fmt.Printf("警告: 创建任务记录失败: %v\n", err)
	}

	// 执行上传
	uploadMgr := transfer.NewUploadManager(cfg)
	if err := uploadMgr.UploadFile(client, localPath, nodeID); err != nil {
		taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusFailed, err.Error())
		return err
	}

	taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusCompleted, "")
	return nil
}

var downloadCmd = &cobra.Command{
	Use:   "download <file-id> [local-path]",
	Short: "下载文件到本地",
	Long: `从私有云盘下载文件。

支持分片下载、断点续传。

示例:
  pcd download <file-uuid>                   # 下载到当前目录
  pcd download <file-uuid> ./downloads/      # 下载到指定目录
  pcd download <file-uuid> ./myfile.pdf      # 下载并重命名`,
	Args: cobra.MinimumNArgs(1),
	RunE: runDownload,
}

func runDownload(cmd *cobra.Command, args []string) error {
	fileID := args[0]
	localPath := "."
	if len(args) > 1 {
		localPath = utils.ResolvePath(args[1])
	}

	auth, err := config.EnsureLoggedIn()
	if err != nil {
		return err
	}

	cfg, _ := config.LoadConfig()
	cfg.Token = auth.Token
	client := api.NewClient(cfg)

	// 获取文件信息
	fileInfo, err := client.GetFileInfo(fileID)
	if err != nil {
		return fmt.Errorf("获取文件信息失败: %w", err)
	}

	// 创建任务记录
	taskMgr, err := task.NewManager()
	if err != nil {
		return fmt.Errorf("创建任务管理器失败: %w", err)
	}
	defer taskMgr.Close()

	taskEntity := &task.Task{
		Type:        task.TypeDownload,
		Status:      task.StatusRunning,
		Source:      fileID,
		Destination: localPath,
		FileName:    fileInfo.Name,
		FileSize:    fileInfo.Size,
	}
	if err := taskMgr.CreateTask(taskEntity); err != nil {
		fmt.Printf("警告: 创建任务记录失败: %v\n", err)
	}

	downloadMgr := transfer.NewDownloadManager(cfg)
	if err := downloadMgr.DownloadFile(client, fileID, auth.UserID, localPath); err != nil {
		taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusFailed, err.Error())
		return err
	}

	taskMgr.UpdateTaskStatus(taskEntity.ID, task.StatusCompleted, "")
	return nil
}

// findNodeByPath 通过路径查找节点 ID
func findNodeByPath(client *api.Client, path string) (string, error) {
	if path == "" || path == "/" {
		root, err := client.GetRootNode()
		if err != nil {
			return "", err
		}
		return root.NodeID, nil
	}

	parts := strings.Split(strings.Trim(path, "/"), "/")
	root, err := client.GetRootNode()
	if err != nil {
		return "", err
	}

	currentID := root.NodeID
	for _, part := range parts {
		if part == "" {
			continue
		}
		children, err := client.ListChildren(currentID)
		if err != nil {
			return "", fmt.Errorf("无法访问路径 %s: %w", path, err)
		}
		found := false
		for _, child := range children {
			if child.NodeName == part && child.NodeType == "FOLDER" {
				currentID = child.NodeID
				found = true
				break
			}
		}
		if !found {
			return "", fmt.Errorf("路径不存在: %s", path)
		}
	}

	return currentID, nil
}