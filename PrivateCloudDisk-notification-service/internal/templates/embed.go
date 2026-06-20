// Package templates 嵌入邮件模板文件，提供从 Spring Boot 迁移的完整 HTML 邮件模板。
// 使用 Go 1.16+ embed 特性将模板文件编译进二进制，无需外部依赖。
package templates

import (
	"embed"
	"fmt"
	"strings"
)

//go:embed email/*.html
var emailTemplates embed.FS

// EmailTemplate 邮件模板定义
type EmailTemplate struct {
	Code            string // 模板唯一标识
	Name            string // 模板名称
	Channel         string // 渠道
	Lang            string // 语言
	Title           string // 邮件标题
	Body            string // 纯文本正文
	HTMLBody        string // HTML 正文
	VariablesJSON   string // 变量定义 JSON
}

// 模板默认变量定义
var (
	// WelcomeEmailVars 欢迎邮件模板变量
	WelcomeEmailVars = []map[string]interface{}{
		{"name": "Username", "type": "string", "required": true, "desc": "用户名"},
		{"name": "Email", "type": "string", "required": true, "desc": "用户邮箱"},
		{"name": "RegisterTime", "type": "string", "required": true, "desc": "注册时间"},
		{"name": "LoginUrl", "type": "string", "required": true, "desc": "登录页面URL"},
		{"name": "CurrentYear", "type": "string", "required": true, "desc": "当前年份"},
	}

	// VerificationEmailVars 验证码邮件模板变量（亮色版）
	VerificationEmailVars = []map[string]interface{}{
		{"name": "Username", "type": "string", "required": true, "desc": "用户名"},
		{"name": "PurposeText", "type": "string", "required": true, "desc": "操作用途文本"},
		{"name": "VerificationCode", "type": "string", "required": true, "desc": "验证码"},
		{"name": "ExpireMinutes", "type": "int", "required": true, "desc": "过期分钟数"},
		{"name": "Email", "type": "string", "required": true, "desc": "收件人邮箱"},
		{"name": "CurrentYear", "type": "string", "required": true, "desc": "当前年份"},
		{"name": "SupportEmail", "type": "string", "required": true, "desc": "支持邮箱地址"},
	}

	// VerificationEmailDarkVars 验证码邮件模板变量（暗色版）
	VerificationEmailDarkVars = []map[string]interface{}{
		{"name": "Username", "type": "string", "required": true, "desc": "用户名"},
		{"name": "PurposeText", "type": "string", "required": true, "desc": "操作用途文本"},
		{"name": "VerificationCode", "type": "string", "required": true, "desc": "验证码"},
		{"name": "ExpireMinutes", "type": "int", "required": true, "desc": "过期分钟数"},
		{"name": "HelpUrl", "type": "string", "required": true, "desc": "帮助中心URL"},
	}
)

// GetEmbeddedTemplates 获取所有嵌入的邮件模板
func GetEmbeddedTemplates() []EmailTemplate {
	jsonWelcome, _ := toJSONString(WelcomeEmailVars)
	jsonVerify, _ := toJSONString(VerificationEmailVars)
	jsonVerifyDark, _ := toJSONString(VerificationEmailDarkVars)

	return []EmailTemplate{
		{
			Code:          "welcome_email",
			Name:          "注册欢迎邮件",
			Channel:       "email",
			Lang:          "zh-CN",
			Title:         "欢迎使用私有云网盘",
			Body:          "欢迎加入私有云网盘，您的账号已成功创建。",
			HTMLBody:      readEmbeddedFile("email/welcome-email.html"),
			VariablesJSON: jsonWelcome,
		},
		{
			Code:          "verification_email",
			Name:          "验证码邮件（亮色版）",
			Channel:       "email",
			Lang:          "zh-CN",
			Title:         "验证码 - 私有云网盘",
			Body:          "您正在进行安全验证操作，验证码：{{.VerificationCode}}，有效期 {{.ExpireMinutes}} 分钟。",
			HTMLBody:      readEmbeddedFile("email/verification-email.html"),
			VariablesJSON: jsonVerify,
		},
		{
			Code:          "verification_email_dark",
			Name:          "验证码邮件（暗色安全版）",
			Channel:       "email",
			Lang:          "zh-CN",
			Title:         "安全验证码 - 私有云网盘",
			Body:          "您正在进行敏感安全操作，验证码：{{.VerificationCode}}，有效期 {{.ExpireMinutes}} 分钟。",
			HTMLBody:      readEmbeddedFile("email/verification-email-dark.html"),
			VariablesJSON: jsonVerifyDark,
		},
	}
}

// readEmbeddedFile 读取嵌入的模板文件，失败时返回空字符串
func readEmbeddedFile(path string) string {
	data, err := emailTemplates.ReadFile(path)
	if err != nil {
		return ""
	}
	return strings.TrimSpace(string(data))
}

// toJSONString 将变量定义转换为 JSON 字符串
func toJSONString(vars []map[string]interface{}) (string, error) {
	var parts []string
	for _, v := range vars {
		s := fmt.Sprintf(`{"name":"%s","type":"%s","required":%v,"desc":"%s"}`,
			v["name"], v["type"], v["required"], v["desc"])
		parts = append(parts, s)
	}
	return "[" + strings.Join(parts, ",") + "]", nil
}