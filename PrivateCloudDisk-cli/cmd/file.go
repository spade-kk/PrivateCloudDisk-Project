package cmd

import (
	"fmt"
	"strings"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/privateclouddisk/cli/utils"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(lsCmd)
	rootCmd.AddCommand(mkdirCmd)
	rootCmd.AddCommand(rmCmd)
	rootCmd.AddCommand(mvCmd)
	rootCmd.AddCommand(renameCmd)
	rootCmd.AddCommand(statCmd)
	rootCmd.AddCommand(cpCmd)
}

var lsLongFlag bool

var lsCmd = &cobra.Command{
	Use:   "ls [path|node-id]",
	Short: "列出目录内容",
	Long: `列出指定目录下的文件和子目录。

示例:
  pcd ls                          # 列出根目录
  pcd ls /documents               # 列出指定路径
  pcd ls <node-uuid>              # 列出指定节点 ID
  pcd ls -l                       # 详细列表模式`,
	RunE: runLs,
}

func init() {
	lsCmd.Flags().BoolVarP(&lsLongFlag, "long", "l", false, "详细列表模式")
}

func runLs(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	var nodeID string
	if len(args) > 0 {
		// 尝试作为路径解析
		path := args[0]
		if strings.HasPrefix(path, "/") || !strings.Contains(path, "-") {
			nid, err := findNodeByPath(client, path)
			if err != nil {
				// 尝试作为 UUID 直接使用
				nodeID = path
			} else {
				nodeID = nid
			}
		} else {
			nodeID = path
		}
	} else {
		root, err := client.GetRootNode()
		if err != nil {
			return err
		}
		nodeID = root.NodeID
	}

	entries, err := client.ListChildren(nodeID)
	if err != nil {
		return fmt.Errorf("列出目录失败: %w", err)
	}

	if len(entries) == 0 {
		fmt.Println("(空目录)")
		return nil
	}

	if lsLongFlag {
		fmt.Printf("%-38s %-6s %-12s %-30s\n", "ID", "类型", "大小", "名称")
		fmt.Println(strings.Repeat("-", 90))
		for _, entry := range entries {
			typeStr := entry.NodeType
			if typeStr == "FOLDER" {
				typeStr = "📁 DIR"
			} else {
				typeStr = utils.GetFileIcon(entry.NodeName) + " FILE"
			}
			sizeStr := "-"
			if entry.NodeType != "FOLDER" {
				sizeStr = utils.FormatSize(entry.NodeSize)
			}
			fmt.Printf("%-38s %-6s %-12s %-30s\n",
				entry.NodeID, typeStr, sizeStr, utils.TruncateString(entry.NodeName, 28))
		}
	} else {
		for _, entry := range entries {
			icon := "📁"
			if entry.NodeType != "FOLDER" {
				icon = utils.GetFileIcon(entry.NodeName)
			}
			fmt.Printf("%s %s\n", icon, entry.NodeName)
		}
	}

	return nil
}

var mkdirCmd = &cobra.Command{
	Use:   "mkdir <folder-name> [parent-path|parent-node-id]",
	Short: "创建文件夹",
	Long: `在指定目录下创建文件夹。

示例:
  pcd mkdir projects              # 在根目录创建
  pcd mkdir reports /documents    # 在 /documents 下创建
  pcd mkdir data <node-uuid>      # 在指定节点下创建`,
	Args: cobra.MinimumNArgs(1),
	RunE: runMkdir,
}

func runMkdir(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	folderName := args[0]

	var parentNodeID string
	if len(args) > 1 {
		parentNodeID = args[1]
		// 尝试作为路径解析
		if strings.HasPrefix(parentNodeID, "/") {
			nid, err := findNodeByPath(client, parentNodeID)
			if err != nil {
				return err
			}
			parentNodeID = nid
		}
	} else {
		root, err := client.GetRootNode()
		if err != nil {
			return err
		}
		parentNodeID = root.NodeID
	}

	if err := client.CreateFolder(parentNodeID, folderName); err != nil {
		return err
	}

	fmt.Printf("文件夹已创建: %s\n", folderName)
	return nil
}

var rmRecursiveFlag bool

var rmCmd = &cobra.Command{
	Use:   "rm <node-id|file-id>",
	Short: "删除文件或文件夹",
	Long: `删除文件或文件夹。

示例:
  pcd rm <node-uuid>              # 删除文件夹
  pcd rm <file-uuid>              # 删除文件
  pcd rm -r <node-uuid>           # 递归删除`,
	Args: cobra.MinimumNArgs(1),
	RunE: runRm,
}

func init() {
	rmCmd.Flags().BoolVarP(&rmRecursiveFlag, "recursive", "r", false, "递归删除")
}

func runRm(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	targetID := args[0]

	// 尝试作为文件删除
	if err := client.DeleteFile(targetID); err == nil {
		fmt.Printf("文件已删除: %s\n", targetID)
		return nil
	}

	// 尝试作为节点删除
	if err := client.DeleteNode(targetID); err != nil {
		return fmt.Errorf("删除失败: %w", err)
	}

	fmt.Printf("已删除: %s\n", targetID)
	return nil
}

var mvCmd = &cobra.Command{
	Use:   "mv <source-id> <target-node-id>",
	Short: "移动文件或文件夹",
	Long: `移动文件或文件夹到指定目录。

示例:
  pcd mv <file-uuid> <target-node-uuid>     # 移动文件
  pcd mv <node-uuid> <target-node-uuid>     # 移动文件夹`,
	Args: cobra.ExactArgs(2),
	RunE: runMv,
}

func runMv(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	sourceID := args[0]
	targetID := args[1]

	// 尝试作为文件移动
	if err := client.MoveFile(sourceID, targetID); err == nil {
		fmt.Printf("文件已移动: %s -> %s\n", sourceID, targetID)
		return nil
	}

	// 尝试作为节点移动
	if err := client.MoveNode(sourceID, targetID); err != nil {
		return fmt.Errorf("移动失败: %w", err)
	}

	fmt.Printf("已移动: %s -> %s\n", sourceID, targetID)
	return nil
}

var renameCmd = &cobra.Command{
	Use:   "rename <id> <new-name>",
	Short: "重命名文件或文件夹",
	Long: `重命名文件或文件夹。

示例:
  pcd rename <file-uuid> newname.pdf
  pcd rename <node-uuid> newfolder`,
	Args: cobra.ExactArgs(2),
	RunE: runRename,
}

func runRename(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	targetID := args[0]
	newName := args[1]

	// 尝试作为文件重命名
	if err := client.RenameFile(targetID, newName); err == nil {
		fmt.Printf("文件已重命名: %s\n", newName)
		return nil
	}

	// 尝试作为节点重命名
	if err := client.RenameNode(targetID, newName); err != nil {
		return fmt.Errorf("重命名失败: %w", err)
	}

	fmt.Printf("已重命名: %s\n", newName)
	return nil
}

var statCmd = &cobra.Command{
	Use:   "stat <file-id|node-id>",
	Short: "查看文件或文件夹详情",
	Long:  "查看文件或文件夹的详细信息。",
	Args:  cobra.MinimumNArgs(1),
	RunE:  runStat,
}

func runStat(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	targetID := args[0]

	// 尝试作为文件获取
	fileInfo, err := client.GetFileInfo(targetID)
	if err == nil {
		fmt.Println("=== 文件信息 ===")
		fmt.Printf("  文件ID:   %s\n", fileInfo.ID)
		fmt.Printf("  文件名:   %s\n", fileInfo.Name)
		fmt.Printf("  类型:     %s\n", fileInfo.Type)
		fmt.Printf("  大小:     %s\n", utils.FormatSize(fileInfo.Size))
		fmt.Printf("  上传时间: %s\n", fileInfo.UploadedTime.Format("2006-01-02 15:04:05"))
		fmt.Printf("  分片数:   %d\n", fileInfo.TotalChunks)
		return nil
	}

	// 尝试作为节点获取
	nodeInfo, err := client.GetNode(targetID)
	if err != nil {
		return fmt.Errorf("获取信息失败: %w", err)
	}

	fmt.Println("=== 文件夹信息 ===")
	fmt.Printf("  节点ID:   %s\n", nodeInfo.NodeID)
	fmt.Printf("  名称:     %s\n", nodeInfo.NodeName)
	fmt.Printf("  创建时间: %s\n", nodeInfo.CreateTime)
	fmt.Printf("  状态:     %s\n", nodeInfo.Status)
	return nil
}

var cpCmd = &cobra.Command{
	Use:   "cp <source-file-id> <target-node-id>",
	Short: "复制文件（创建副本）",
	Long:  "在指定目录下创建文件的副本。",
	Args:  cobra.ExactArgs(2),
	RunE:  runCp,
}

func runCp(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	auth, _ := config.EnsureLoggedIn()
	_ = auth

	fileID := args[0]
	targetNodeID := args[1]

	// 获取文件信息
	fileInfo, err := client.GetFileInfo(fileID)
	if err != nil {
		return fmt.Errorf("获取源文件信息失败: %w", err)
	}

	fmt.Printf("正在复制文件: %s (%s)\n", fileInfo.Name, utils.FormatSize(fileInfo.Size))
	fmt.Println("提示: 文件复制需要先下载再上传，请使用 download + upload 命令完成")
	fmt.Printf("  pcd download %s /tmp/copy_temp\n", fileID)
	fmt.Printf("  pcd upload /tmp/copy_temp/%s --node-id %s\n", fileInfo.Name, targetNodeID)

	return nil
}