// Package http 提供 HTTP API 处理器。
// 包含通知服务、模板管理、偏好设置、设备管理等 REST API。
package http

import (
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/google/uuid"
	"github.com/jmoiron/sqlx"

	"github.com/privateclouddisk/notification-service/internal/config"
	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/rabbitmq"
	"github.com/privateclouddisk/notification-service/internal/repository"
	"github.com/privateclouddisk/notification-service/internal/service"
)

// Handler HTTP 处理器
type Handler struct {
	cfg              *config.Config
	db               *sqlx.DB
	consumer         *rabbitmq.Consumer
	notifService     *service.NotificationService
	templateService  *service.TemplateService
	preferenceService *service.PreferenceService
	aggregationService *service.AggregationService
}

// NewHandler 创建 HTTP 处理器
func NewHandler(
	cfg *config.Config,
	db *sqlx.DB,
	consumer *rabbitmq.Consumer,
	notifService *service.NotificationService,
	templateService *service.TemplateService,
	preferenceService *service.PreferenceService,
	aggregationService *service.AggregationService,
) *Handler {
	return &Handler{
		cfg:                cfg,
		db:                 db,
		consumer:           consumer,
		notifService:       notifService,
		templateService:    templateService,
		preferenceService:  preferenceService,
		aggregationService: aggregationService,
	}
}

// RegisterRoutes 注册路由
func (h *Handler) RegisterRoutes(r *gin.Engine) {
	// 健康检查
	r.GET("/health", h.healthCheck)
	r.GET("/ready", h.readinessCheck)

	// API v1
	v1 := r.Group("/api/v1/notification")
	{
		// 通知发送
		v1.POST("/send", h.sendNotification)

		// 通知历史
		v1.GET("/history", h.getHistory)
		v1.GET("/record/:id", h.getRecord)

		// 模板管理
		v1.GET("/templates", h.listTemplates)
		v1.POST("/templates", h.createTemplate)
		v1.GET("/templates/:code", h.getTemplate)

		// 用户偏好
		v1.GET("/preferences", h.getPreferences)
		v1.PUT("/preferences", h.updatePreference)
		v1.PUT("/preferences/dnd", h.setDND)
		v1.PUT("/preferences/max-per-day", h.setMaxPerDay)
		v1.PUT("/preferences/toggle", h.toggleChannel)

		// 设备订阅
		v1.POST("/devices", h.registerDevice)
		v1.DELETE("/devices", h.unregisterDevice)
		v1.GET("/devices", h.getDevices)
	}

	// 内部 API（供其他服务调用）
	internal := r.Group("/api/internal/notification")
	{
		internal.POST("/send", h.sendNotification)
		internal.POST("/batch", h.sendBatchNotification)
	}
}

// =============================================================================
// 健康检查
// =============================================================================

// healthCheck 存活检查
func (h *Handler) healthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, gin.H{
		"status": "alive",
		"time":   time.Now().Unix(),
	})
}

// readinessCheck 就绪检查
func (h *Handler) readinessCheck(c *gin.Context) {
	// 检查数据库
	if err := h.db.Ping(); err != nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"status": "not ready",
			"reason": "database unreachable",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"status": "ready",
		"time":   time.Now().Unix(),
	})
}

// =============================================================================
// 通知发送
// =============================================================================

// sendNotificationRequest 发送通知请求
type sendNotificationRequest struct {
	EventType    string                 `json:"event_type" binding:"required"`
	UserID       string                 `json:"user_id" binding:"required"`
	Email        string                 `json:"email,omitempty"`
	Phone        string                 `json:"phone,omitempty"`
	Channels     []string               `json:"channels,omitempty"`
	TemplateCode string                 `json:"template_code"`
	TemplateLang string                 `json:"template_lang,omitempty"`
	Variables    map[string]interface{} `json:"variables,omitempty"`
	DeviceTokens []string               `json:"device_tokens,omitempty"`
	PushTitle    string                 `json:"push_title,omitempty"`
	PushBody     string                 `json:"push_body,omitempty"`
	PushData     map[string]interface{} `json:"push_data,omitempty"`
	Priority     int                    `json:"priority"`
}

// sendNotification 发送通知
func (h *Handler) sendNotification(c *gin.Context) {
	var req sendNotificationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if req.Priority == 0 {
		req.Priority = 5
	}

	event := &domain.NotificationEvent{
		EventID:      uuid.New().String(),
		EventType:    req.EventType,
		UserID:       req.UserID,
		Email:        req.Email,
		Phone:        req.Phone,
		Channels:     req.Channels,
		TemplateCode: req.TemplateCode,
		TemplateLang: req.TemplateLang,
		Variables:    req.Variables,
		DeviceTokens: req.DeviceTokens,
		PushTitle:    req.PushTitle,
		PushBody:     req.PushBody,
		PushData:     req.PushData,
		Priority:     req.Priority,
		CreatedAt:    time.Now(),
	}

	// 发布到消息队列
	if err := h.consumer.PublishEvent(event); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "发布事件失败: " + err.Error()})
		return
	}

	c.JSON(http.StatusAccepted, gin.H{
		"event_id": event.EventID,
		"status":   "accepted",
	})
}

// sendBatchNotification 批量发送通知
func (h *Handler) sendBatchNotification(c *gin.Context) {
	var reqs []sendNotificationRequest
	if err := c.ShouldBindJSON(&reqs); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	eventIDs := make([]string, 0, len(reqs))
	for _, req := range reqs {
		if req.Priority == 0 {
			req.Priority = 5
		}

		event := &domain.NotificationEvent{
			EventID:      uuid.New().String(),
			EventType:    req.EventType,
			UserID:       req.UserID,
			Email:        req.Email,
			Phone:        req.Phone,
			Channels:     req.Channels,
			TemplateCode: req.TemplateCode,
			TemplateLang: req.TemplateLang,
			Variables:    req.Variables,
			DeviceTokens: req.DeviceTokens,
			PushTitle:    req.PushTitle,
			PushBody:     req.PushBody,
			PushData:     req.PushData,
			Priority:     req.Priority,
			CreatedAt:    time.Now(),
		}

		if err := h.consumer.PublishEvent(event); err != nil {
			eventIDs = append(eventIDs, "failed:"+event.EventID)
		} else {
			eventIDs = append(eventIDs, event.EventID)
		}
	}

	c.JSON(http.StatusAccepted, gin.H{
		"event_ids": eventIDs,
		"count":     len(eventIDs),
		"status":    "accepted",
	})
}

// =============================================================================
// 通知历史
// =============================================================================

// getHistory 获取通知历史
func (h *Handler) getHistory(c *gin.Context) {
	userID := c.Query("user_id")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))

	records, total, err := h.notifService.GetHistory(userID, (page-1)*pageSize, pageSize)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"data":  records,
		"total": total,
		"page":  page,
	})
}

// getRecord 获取单条通知记录
func (h *Handler) getRecord(c *gin.Context) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "无效的 ID"})
		return
	}

	record, err := h.notifService.GetRecord(id)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "通知不存在"})
		return
	}

	c.JSON(http.StatusOK, gin.H{"data": record})
}

// =============================================================================
// 模板管理
// =============================================================================

// listTemplates 获取模板列表
func (h *Handler) listTemplates(c *gin.Context) {
	channel := c.Query("channel")
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	pageSize, _ := strconv.Atoi(c.DefaultQuery("page_size", "20"))

	templates, total, err := h.templateService.ListTemplates(channel, page, pageSize)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"data":  templates,
		"total": total,
		"page":  page,
	})
}

// createTemplate 创建模板
func (h *Handler) createTemplate(c *gin.Context) {
	var t domain.Template
	if err := c.ShouldBindJSON(&t); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	id, err := h.templateService.CreateTemplate(&t)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusCreated, gin.H{"id": id, "code": t.Code})
}

// getTemplate 获取模板详情
func (h *Handler) getTemplate(c *gin.Context) {
	code := c.Param("code")
	channel := c.Query("channel")
	lang := c.DefaultQuery("lang", "zh-CN")

	template, err := h.templateService.GetTemplate(code, channel, lang)
	if err != nil {
		c.JSON(http.StatusNotFound, gin.H{"error": "模板不存在: " + err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"data": template})
}

// =============================================================================
// 用户偏好
// =============================================================================

// getPreferences 获取用户偏好
func (h *Handler) getPreferences(c *gin.Context) {
	userID := c.Query("user_id")
	prefs, err := h.preferenceService.GetPreferences(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"data": prefs})
}

// updatePreference 更新用户偏好
func (h *Handler) updatePreference(c *gin.Context) {
	var pref domain.NotificationPreference
	if err := c.ShouldBindJSON(&pref); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if err := h.preferenceService.UpdatePreference(&pref); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// setDND 设置免打扰
func (h *Handler) setDND(c *gin.Context) {
	var req struct {
		UserID  string `json:"user_id" binding:"required"`
		Channel string `json:"channel" binding:"required"`
		Enabled bool   `json:"enabled"`
		Start   string `json:"start"` // HH:mm
		End     string `json:"end"`   // HH:mm
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if err := h.preferenceService.SetDND(req.UserID, req.Channel, req.Enabled, req.Start, req.End); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// setMaxPerDay 设置每日最大推送数
func (h *Handler) setMaxPerDay(c *gin.Context) {
	var req struct {
		UserID    string `json:"user_id" binding:"required"`
		Channel   string `json:"channel" binding:"required"`
		MaxPerDay int    `json:"max_per_day" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if err := h.preferenceService.SetMaxPerDay(req.UserID, req.Channel, req.MaxPerDay); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// toggleChannel 切换渠道开关
func (h *Handler) toggleChannel(c *gin.Context) {
	var req struct {
		UserID  string `json:"user_id" binding:"required"`
		Channel string `json:"channel" binding:"required"`
		Enabled bool   `json:"enabled"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if err := h.preferenceService.ToggleChannel(req.UserID, req.Channel, req.Enabled); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// =============================================================================
// 设备订阅
// =============================================================================

// registerDevice 注册设备
func (h *Handler) registerDevice(c *gin.Context) {
	var device domain.DeviceSubscription
	if err := c.ShouldBindJSON(&device); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}
	device.IsActive = true

	deviceRepo := repository.NewDeviceRepo(h.db)
	if err := deviceRepo.Upsert(&device); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// unregisterDevice 注销设备
func (h *Handler) unregisterDevice(c *gin.Context) {
	var req struct {
		UserID      string `json:"user_id" binding:"required"`
		DeviceToken string `json:"device_token" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	deviceRepo := repository.NewDeviceRepo(h.db)
	if err := deviceRepo.Deactivate(req.UserID, req.DeviceToken); err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"status": "ok"})
}

// getDevices 获取用户设备
func (h *Handler) getDevices(c *gin.Context) {
	userID := c.Query("user_id")

	deviceRepo := repository.NewDeviceRepo(h.db)
	devices, err := deviceRepo.GetByUserID(userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	c.JSON(http.StatusOK, gin.H{"data": devices})
}