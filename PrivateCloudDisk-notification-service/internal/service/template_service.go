// Package service 提供模板管理服务。
package service

import (
	"encoding/json"
	"fmt"
	"log"
	"strings"

	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/repository"
)

// TemplateService 模板管理服务
type TemplateService struct {
	repo *repository.TemplateRepo
}

// NewTemplateService 创建模板服务
func NewTemplateService(repo *repository.TemplateRepo) *TemplateService {
	return &TemplateService{repo: repo}
}

// GetTemplate 获取模板
func (s *TemplateService) GetTemplate(code, channel, lang string) (*domain.Template, error) {
	return s.repo.GetByCodeFallback(code, channel, lang)
}

// ListTemplates 获取模板列表
func (s *TemplateService) ListTemplates(channel string, page, pageSize int) ([]domain.Template, int, error) {
	offset := (page - 1) * pageSize
	return s.repo.List(channel, offset, pageSize)
}

// CreateTemplate 创建模板
func (s *TemplateService) CreateTemplate(t *domain.Template) (int64, error) {
	// 验证模板变量
	if t.Variables != nil {
		varsJSON, err := json.Marshal(t.Variables)
		if err != nil {
			return 0, fmt.Errorf("模板变量序列化失败: %w", err)
		}
		t.VariablesJSON = string(varsJSON)
	}

	// 验证模板占位符
	if err := s.validateTemplateVars(t); err != nil {
		return 0, err
	}

	return s.repo.Create(t)
}

// RenderTemplate 渲染模板
func (s *TemplateService) RenderTemplate(template *domain.Template, variables map[string]interface{}) (string, string, string) {
	title := template.Title
	body := template.Body
	htmlBody := template.HTMLBody

	for k, v := range variables {
		placeholder := fmt.Sprintf("{{.%s}}", k)
		value := fmt.Sprintf("%v", v)
		title = strings.ReplaceAll(title, placeholder, value)
		body = strings.ReplaceAll(body, placeholder, value)
		if htmlBody != "" {
			htmlBody = strings.ReplaceAll(htmlBody, placeholder, value)
		}
	}

	return title, body, htmlBody
}

// validateTemplateVars 验证模板变量完整性
func (s *TemplateService) validateTemplateVars(t *domain.Template) error {
	// 提取模板中所有 {{.var}} 占位符
	placeholders := extractPlaceholders(t.Title)
	placeholders = append(placeholders, extractPlaceholders(t.Body)...)
	if t.HTMLBody != "" {
		placeholders = append(placeholders, extractPlaceholders(t.HTMLBody)...)
	}

	// 如果定义了变量配置，检查是否匹配
	if t.VariablesJSON != "" {
		var varDefs []map[string]interface{}
		if err := json.Unmarshal([]byte(t.VariablesJSON), &varDefs); err != nil {
			return fmt.Errorf("变量定义解析失败: %w", err)
		}

		definedVars := make(map[string]bool)
		for _, def := range varDefs {
			if name, ok := def["name"].(string); ok {
				definedVars[name] = true
			}
		}

		// 检查模板中的占位符是否都有定义
		for _, p := range placeholders {
			if !definedVars[p] {
				log.Printf("[Template] 模板占位符 %s 未在变量定义中找到", p)
			}
		}
	}

	return nil
}

// extractPlaceholders 提取模板中的 {{.var}} 占位符
func extractPlaceholders(content string) []string {
	var result []string
	seen := make(map[string]bool)

	for {
		start := strings.Index(content, "{{.")
		if start == -1 {
			break
		}
		end := strings.Index(content[start:], "}}")
		if end == -1 {
			break
		}
		placeholder := content[start+3 : start+end]
		if !seen[placeholder] {
			seen[placeholder] = true
			result = append(result, placeholder)
		}
		content = content[start+end+2:]
	}

	return result
}