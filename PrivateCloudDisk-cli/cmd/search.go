package cmd

import (
	"fmt"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/utils"
	"github.com/spf13/cobra"
)

func init() {
	rootCmd.AddCommand(searchCmd)
	rootCmd.AddCommand(shareCmd)
}

var searchCmd = &cobra.Command{
	Use:   "search <keyword>",
	Short: "全文搜索文件",
	Long: `在私有云盘中进行全文搜索。

示例:
  pcd search "季度报告"              # 搜索文件
  pcd search "会议" --page 2 --size 20`,
	Args: cobra.MinimumNArgs(1),
	RunE: runSearch,
}

var (
	searchPage int
	searchSize int
)

func init() {
	searchCmd.Flags().IntVar(&searchPage, "page", 1, "页码")
	searchCmd.Flags().IntVar(&searchSize, "size", 20, "每页数量")
}

func runSearch(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	keyword := args[0]

	result, err := client.SearchFiles(keyword, searchPage, searchSize)
	if err != nil {
		return err
	}

	if len(result.Items) == 0 {
		fmt.Println("未找到匹配的文件")
		return nil
	}

	fmt.Printf("搜索结果: %s (共 %d 条, 第 %d/%d 页)\n\n",
		keyword, result.Total, result.Page, (result.Total+int64(result.Size)-1)/int64(result.Size))

	fmt.Printf("%-38s %-12s %-30s %-20s\n", "ID", "大小", "名称", "上传时间")
	fmt.Println("---------------------------------------- ------------ ------------------------------ --------------------")
	for _, item := range result.Items {
		fmt.Printf("%-38s %-12s %-30s %-20s\n",
			item.ID,
			utils.FormatSize(item.Size),
			utils.TruncateString(item.Name, 28),
			item.UploadedTime[:10],
		)
	}

	return nil
}

var shareCmd = &cobra.Command{
	Use:   "share <file-id>",
	Short: "创建文件分享链接",
	Long: `为文件创建分享链接。

示例:
  pcd share <file-uuid>                    # 创建公开分享
  pcd share <file-uuid> -w mypassword      # 创建带密码的分享
  pcd share <file-uuid> -e 24              # 24小时后过期`,
	Args: cobra.MinimumNArgs(1),
	RunE: runShare,
}

var (
	sharePassword string
	shareExpireH  int
)

func init() {
	shareCmd.Flags().StringVarP(&sharePassword, "password", "w", "", "分享密码")
	shareCmd.Flags().IntVarP(&shareExpireH, "expire", "e", 0, "过期时间（小时）")
}

func runShare(cmd *cobra.Command, args []string) error {
	client, err := api.NewClientWithAuth()
	if err != nil {
		return err
	}

	fileID := args[0]

	link, err := client.CreateShare(fileID, sharePassword, shareExpireH)
	if err != nil {
		return err
	}

	fmt.Println("=== 分享链接 ===")
	fmt.Printf("  链接:   %s\n", link.ShareURL)
	if sharePassword != "" {
		fmt.Printf("  密码:   %s\n", sharePassword)
	}
	if shareExpireH > 0 {
		fmt.Printf("  过期:   %d 小时后\n", shareExpireH)
	}
	fmt.Printf("  路径:   %s\n", link.SharePath)

	return nil
}