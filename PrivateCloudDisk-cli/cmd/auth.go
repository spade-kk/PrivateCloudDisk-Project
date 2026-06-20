package cmd

import (
	"fmt"
	"syscall"
	"time"

	"github.com/privateclouddisk/cli/api"
	"github.com/privateclouddisk/cli/config"
	"github.com/spf13/cobra"
	"golang.org/x/term"
)

func init() {
	rootCmd.AddCommand(loginCmd)
	rootCmd.AddCommand(logoutCmd)
	rootCmd.AddCommand(whoamiCmd)
	rootCmd.AddCommand(registerCmd)
}

var loginCmd = &cobra.Command{
	Use:   "login",
	Short: "登录到私有云盘",
	Long: `登录到 PrivateCloudDisk 私有云盘系统。
支持账号密码登录和手机号密码登录。

示例:
  pcd login                         # 交互式登录
  pcd login -a myaccount            # 指定账号
  pcd login -p 13800138000          # 指定手机号`,
	RunE: runLogin,
}

var (
	loginAccount string
	loginPhone   string
	loginPass    string
)

func init() {
	loginCmd.Flags().StringVarP(&loginAccount, "account", "a", "", "登录账号")
	loginCmd.Flags().StringVarP(&loginPhone, "phone", "p", "", "手机号")
	loginCmd.Flags().StringVarP(&loginPass, "password", "w", "", "密码")
}

func runLogin(cmd *cobra.Command, args []string) error {
	// 加载配置
	cfg, err := config.LoadConfig()
	if err != nil {
		return fmt.Errorf("加载配置失败: %w", err)
	}

	client := api.NewClient(cfg)

	// 获取账号
	account := loginAccount
	phone := loginPhone
	password := loginPass

	if account == "" && phone == "" {
		fmt.Print("请输入账号或手机号: ")
		fmt.Scanln(&account)
		// 简单判断是手机号还是账号
		if len(account) == 11 && account[0] == '1' {
			phone = account
			account = ""
		}
	}

	if password == "" {
		fmt.Print("请输入密码: ")
		bytePassword, err := term.ReadPassword(int(syscall.Stdin))
		if err != nil {
			return fmt.Errorf("读取密码失败: %w", err)
		}
		password = string(bytePassword)
		fmt.Println()
	}

	fmt.Println("正在登录...")

	// 调用登录 API
	token, err := client.Login(account, phone, password)
	if err != nil {
		return err
	}

	// 保存 Token
	auth := &config.AuthData{
		Token:     token,
		Account:   account,
		LoginAt:   time.Now(),
		ExpiresAt: time.Now().Add(24 * time.Hour),
	}

	// 获取用户信息
	client.SetToken(token)
	profile, err := client.GetUserProfile()
	if err == nil {
		auth.UserID = profile.ID
		auth.Account = profile.Account
		fmt.Printf("登录成功! 欢迎 %s (%s)\n", profile.Name, profile.Account)
	} else {
		fmt.Println("登录成功!")
	}

	if err := config.SaveAuth(auth); err != nil {
		return fmt.Errorf("保存认证信息失败: %w", err)
	}

	cfg.Token = token
	cfg.Account = auth.Account
	if err := config.SaveConfig(cfg); err != nil {
		return fmt.Errorf("保存配置失败: %w", err)
	}

	return nil
}

var logoutCmd = &cobra.Command{
	Use:   "logout",
	Short: "退出登录",
	Long:  "清除本地保存的认证信息，退出登录。",
	RunE: func(cmd *cobra.Command, args []string) error {
		if err := config.ClearAuth(); err != nil {
			return fmt.Errorf("退出登录失败: %w", err)
		}
		fmt.Println("已退出登录")
		return nil
	},
}

var whoamiCmd = &cobra.Command{
	Use:   "whoami",
	Short: "查看当前登录用户信息",
	Long:  "显示当前登录用户的详细信息。",
	RunE: func(cmd *cobra.Command, args []string) error {
		client, err := api.NewClientWithAuth()
		if err != nil {
			return err
		}

		profile, err := client.GetUserProfile()
		if err != nil {
			return err
		}

		fmt.Println("=== 用户信息 ===")
		fmt.Printf("  用户ID:   %s\n", profile.ID)
		fmt.Printf("  账号:     %s\n", profile.Account)
		fmt.Printf("  昵称:     %s\n", profile.Name)
		fmt.Printf("  手机号:   %s\n", profile.PhoneNumber)
		fmt.Printf("  邮箱:     %s\n", profile.Email)

		// 显示配额
		quota, err := client.GetQuota()
		if err == nil {
			fmt.Println("\n=== 存储配额 ===")
			fmt.Printf("  总容量:   %s\n", formatBytes(quota.TotalCapacity))
			fmt.Printf("  已用:     %s\n", formatBytes(quota.UsedCapacity))
			fmt.Printf("  文件数:   %d\n", quota.FileCount)
			if quota.TotalCapacity > 0 {
				pct := float64(quota.UsedCapacity) / float64(quota.TotalCapacity) * 100
				fmt.Printf("  使用率:   %.1f%%\n", pct)
			}
		}

		return nil
	},
}

var registerCmd = &cobra.Command{
	Use:   "register",
	Short: "注册新账号",
	Long: `注册 PrivateCloudDisk 私有云盘账号。

示例:
  pcd register -p 13800138000 -n myname -w mypassword -c 123456`,
	RunE: runRegister,
}

var (
	regPhone    string
	regEmail    string
	regPassword string
	regCode     string
	regName     string
)

func init() {
	registerCmd.Flags().StringVarP(&regPhone, "phone", "p", "", "手机号")
	registerCmd.Flags().StringVarP(&regEmail, "email", "e", "", "邮箱")
	registerCmd.Flags().StringVarP(&regPassword, "password", "w", "", "密码")
	registerCmd.Flags().StringVarP(&regCode, "code", "c", "", "验证码")
	registerCmd.Flags().StringVarP(&regName, "name", "n", "", "昵称")
}

func runRegister(cmd *cobra.Command, args []string) error {
	if regPhone == "" && regEmail == "" {
		return fmt.Errorf("请提供手机号或邮箱")
	}
	if regName == "" {
		return fmt.Errorf("请提供昵称")
	}
	if regPassword == "" {
		fmt.Print("请输入密码: ")
		bytePassword, _ := term.ReadPassword(int(syscall.Stdin))
		regPassword = string(bytePassword)
		fmt.Println()
	}
	if regCode == "" {
		fmt.Print("请输入验证码: ")
		fmt.Scanln(&regCode)
	}

	cfg, _ := config.LoadConfig()
	client := api.NewClient(cfg)

	account, err := client.Register(regPhone, regEmail, regPassword, regCode, regName)
	if err != nil {
		return err
	}

	fmt.Printf("注册成功! 账号: %s\n", account)
	fmt.Println("请使用 login 命令登录")
	return nil
}

func formatBytes(size int64) string {
	if size <= 0 || size == 9223372036854775807 { // Long.MAX_VALUE = unlimited
		return "无限"
	}
	units := []string{"B", "KB", "MB", "GB", "TB"}
	unitIndex := 0
	fsize := float64(size)
	for fsize >= 1024 && unitIndex < len(units)-1 {
		fsize /= 1024
		unitIndex++
	}
	return fmt.Sprintf("%.2f %s", fsize, units[unitIndex])
}