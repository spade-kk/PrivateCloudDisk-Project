// Package service 提供模板初始化服务，将嵌入的邮件模板加载到数据库。
package service

import (
	"log"

	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/repository"
	"github.com/privateclouddisk/notification-service/internal/templates"
)

// TemplateInitializer 模板初始化器
// 启动时将嵌入的 HTML 邮件模板同步到数据库
type TemplateInitializer struct {
	repo *repository.TemplateRepo
}

// NewTemplateInitializer 创建模板初始化器
func NewTemplateInitializer(repo *repository.TemplateRepo) *TemplateInitializer {
	return &TemplateInitializer{repo: repo}
}

// Initialize 初始化模板：将嵌入的模板加载到数据库
// 使用 ON DUPLICATE KEY UPDATE 策略，已存在的模板会被更新
func (ti *TemplateInitializer) Initialize() error {
	embedded := templates.GetEmbeddedTemplates()
	log.Printf("[TemplateInit] 开始初始化 %d 个嵌入邮件模板", len(embedded))

	for _, et := range embedded {
		t := &domain.Template{
			Code:          et.Code,
			Name:          et.Name,
			Channel:       et.Channel,
			Lang:          et.Lang,
			Title:         et.Title,
			Body:          et.Body,
			HTMLBody:      et.HTMLBody,
			VariablesJSON: et.VariablesJSON,
			IsActive:      true,
		}

		id, err := ti.repo.Create(t)
		if err != nil {
			log.Printf("[TemplateInit] 模板 %s/%s/%s 初始化失败: %v", et.Code, et.Channel, et.Lang, err)
			return err
		}

		log.Printf("[TemplateInit] 模板 %s/%s/%s 初始化完成, id=%d", et.Code, et.Channel, et.Lang, id)
	}

	log.Printf("[TemplateInit] 所有 %d 个嵌入模板初始化完成", len(embedded))
	return nil
}

// InitializeIfEmpty 仅在模板表为空时初始化
func (ti *TemplateInitializer) InitializeIfEmpty() error {
	_, total, err := ti.repo.List("", 0, 1)
	if err != nil {
		log.Printf("[TemplateInit] 查询模板数量失败: %v，跳过初始化", err)
		return err
	}

	if total > 0 {
		log.Printf("[TemplateInit] 模板表中已有 %d 条记录，跳过初始化", total)
		return nil
	}

	log.Printf("[TemplateInit] 模板表为空，开始初始化嵌入模板")
	return ti.Initialize()
}