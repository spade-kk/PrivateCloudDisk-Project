// Package http 提供 HTTP API 处理器。
// 包含通知服务、模板管理、偏好设置、设备管理等 REST API。
package http

import (
	"net/http"
	"regexp"
	"strconv"
	"strings"
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
	templateRepo     *repository.TemplateRepo
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
	templateRepo *repository.TemplateRepo,
) *Handler {
	return &Handler{
		cfg:              cfg,
		db:               db,
		consumer:         consumer,
		notifService:     notifService,
		templateService:  templateService,
		preferenceService: preferenceService,
		aggregationService: aggregationService,
		templateRepo:     templateRepo,
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

	// 内部 API（供其他服务调用，支持模板源等高级参数）
	internal := r.Group("/api/internal/notification")
	{
		internal.POST("/send", h.internalSendNotification)
		internal.POST("/batch", h.internalBatchNotification)
	}

	// 管理 API（模板热加载等）
	admin := r.Group("/api/admin")
	{
		admin.POST("/templates/reload", h.reloadFileTemplates)
		admin.GET("/templates/stats", h.getTemplateStats)
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
// 参数校验
// =============================================================================

var (
	emailRegex = regexp.MustCompile(`^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$`)
	phoneRegex = regexp.MustCompile(`^\+?[1-9]\d{1,14}$`)
)

// validateRequest 严格校验公开请求参数
func validateRequest(req *sendNotificationRequest) error {
	var errs []string

	if req.EventType == "" {
		errs = append(errs, "event_type 不能为空")
	}
	if req.UserID == "" {
		errs = append(errs, "user_id 不能为空")
	}
	if req.Email != "" && !emailRegex.MatchString(req.Email) {
		errs = append(errs, "邮箱格式不正确")
	}
	if req.Phone != "" && !phoneRegex.MatchString(req.Phone) {
		errs = append(errs, "手机号格式不正确")
	}

	validChannels := map[string]bool{
		"email": true, "sms": true, "push": true,
		"apns": true, "fcm": true, "webpush": true,
		"wechat_mp": true, "alipay_mp": true, "ws": true,
	}
	for _, ch := range req.Channels {
		if !validChannels[ch] {
			errs = append(errs, "不支持的渠道: "+ch)
		}
	}
	if req.Priority < 0 || req.Priority > 10 {
		errs = append(errs, "priority 必须在 0-10 之间")
	}

	if len(errs) > 0 {
		return &domain.VerificationError{Code: 400, Message: "参数校验失败: " + strings.Join(errs, "; ")}
	}
	return nil
}

// validateInternalRequest 严格校验内部请求参数（含模板源等高级参数）
func validateInternalRequest(req *internalSendRequest) error {
	var errs []string

	if req.EventType == "" {
		errs = append(errs, "event_type 不能为空")
	}
	if req.UserID == "" {
		errs = append(errs, "user_id 不能为空")
	}
	if req.Email != "" && !emailRegex.MatchString(req.Email) {
		errs = append(errs, "邮箱格式不正确")
	}
	if req.Phone != "" && !phoneRegex.MatchString(req.Phone) {
		errs = append(errs, "手机号格式不正确")
	}

	validChannels := map[string]bool{
		"email": true, "sms": true, "push": true,
		"apns": true, "fcm": true, "webpush": true,
		"wechat_mp": true, "alipay_mp": true, "ws": true,
	}
	for _, ch := range req.Channels {
		if !validChannels[ch] {
			errs = append(errs, "不支持的渠道: "+ch)
		}
	}

	if req.TemplateSource != "" {
		validSources := map[string]bool{"database": true, "file": true, "raw": true}
		if !validSources[req.TemplateSource] {
			errs = append(errs, "不支持的模板源: "+req.TemplateSource+", 可选: database/file/raw")
		}
	}
	if req.WSCacheStrategy != "" {
		validStrategies := map[string]bool{"none": true, "persist": true}
		if !validStrategies[req.WSCacheStrategy] {
			errs = append(errs, "不支持的缓存策略: "+req.WSCacheStrategy+", 可选: none/persist")
		}
	}
	if req.Priority < 0 || req.Priority > 10 {
		errs = append(errs, "priority 必须在 0-10 之间")
	}

	if len(errs) > 0 {
		return &domain.VerificationError{Code: 400, Message: "参数校验失败: " + strings.Join(errs, "; ")}
	}
	return nil
}

// =============================================================================
// 通知发送
// =============================================================================

// sendNotificationRequest 发送通知请求（公开接口）
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

// internalSendRequest 内部接口发送通知请求（支持模板源等高级参数）
type internalSendRequest struct {
	EventType       string                 `json:"event_type" binding:"required"`
	UserID          string                 `json:"user_id" binding:"required"`
	Email           string                 `json:"email,omitempty"`
	Phone           string                 `json:"phone,omitempty"`
	Channels        []string               `json:"channels,omitempty"`
	TemplateCode    string                 `json:"template_code"`
	TemplateLang    string                 `json:"template_lang,omitempty"`
	TemplateSource  string                 `json:"template_source,omitempty"`  // database / file / raw
	RawTemplate     *domain.RawTemplate    `json:"raw_template,omitempty"`     // 仅 template_source=raw 时使用
	WSCacheStrategy string                 `json:"ws_cache_strategy,omitempty"` // ws 渠道缓存策略: none / persist
	Variables       map[string]interface{} `json:"variables,omitempty"`
	DeviceTokens    []string               `json:"device_tokens,omitempty"`
	PushTitle       string                 `json:"push_title,omitempty"`
	PushBody        string                 `json:"push_body,omitempty"`
	PushData        map[string]interface{} `json:"push_data,omitempty"`
	Priority        int                    `json:"priority"`
}

// sendNotification 发送通知（公开接口，使用系统默认模板源）
func (h *Handler) sendNotification(c *gin.Context) {
	var req sendNotificationRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数错误: " + err.Error()})
		return
	}

	// 严格参数校验
	if err := validateRequest(&req); err != nil {
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
		// 公开接口不暴露 TemplateSource，使用系统默认配置
		TemplateSource: h.cfg.Template.DefaultSource,
		Variables:      req.Variables,
		DeviceTokens:   req.DeviceTokens,
		PushTitle:      req.PushTitle,
		PushBody:       req.PushBody,
		PushData:       req.PushData,
		Priority:       req.Priority,
		CreatedAt:      time.Now(),
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

// internalSendNotification 内部接口发送通知（支持模板源等高级参数）
func (h *Handler) internalSendNotification(c *gin.Context) {
	var req internalSendRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": "请求参数错误: " + err.Error()})
		return
	}

	// 严格参数校验
	if err := validateInternalRequest(&req); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	if req.Priority == 0 {
		req.Priority = 5
	}

	// 模板源默认值：未指定时使用系统配置
	templateSource := req.TemplateSource
	if templateSource == "" {
		templateSource = h.cfg.Template.DefaultSource
	}

	// WS 缓存策略默认值：persist
	wsCacheStrategy := req.WSCacheStrategy
	if wsCacheStrategy == "" {
		wsCacheStrategy = "persist"
	}

	event := &domain.NotificationEvent{
		EventID:         uuid.New().String(),
		EventType:       req.EventType,
		UserID:          req.UserID,
		Email:           req.Email,
		Phone:           req.Phone,
		Channels:        req.Channels,
		TemplateCode:    req.TemplateCode,
		TemplateLang:    req.TemplateLang,
		TemplateSource:  templateSource,
		RawTemplate:     req.RawTemplate,
		WSCacheStrategy: wsCacheStrategy,
		Variables:       req.Variables,
		DeviceTokens:    req.DeviceTokens,
		PushTitle:       req.PushTitle,
		PushBody:        req.PushBody,
		PushData:        req.PushData,
		Priority:        req.Priority,
		CreatedAt:       time.Now(),
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

// sendBatchNotification 批量发送通知（公开接口）
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

		// 参数校验
		if err := validateRequest(&req); err != nil {
			eventIDs = append(eventIDs, "invalid:"+err.Error())
			continue
		}

		event := &domain.NotificationEvent{
			EventID:        uuid.New().String(),
			EventType:      req.EventType,
			UserID:         req.UserID,
			Email:          req.Email,
			Phone:          req.Phone,
			Channels:       req.Channels,
			TemplateCode:   req.TemplateCode,
			TemplateLang:   req.TemplateLang,
			TemplateSource: h.cfg.Template.DefaultSource,
			Variables:      req.Variables,
			DeviceTokens:   req.DeviceTokens,
			PushTitle:      req.PushTitle,
			PushBody:       req.PushBody,
			PushData:       req.PushData,
			Priority:       req.Priority,
			CreatedAt:      time.Now(),
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

// internalBatchNotification 内部批量发送通知（支持模板源等高级参数）
func (h *Handler) internalBatchNotification(c *gin.Context) {
	var reqs []internalSendRequest
	if err := c.ShouldBindJSON(&reqs); err != nil {
		c.JSON(http.StatusBadRequest, gin.H{"error": err.Error()})
		return
	}

	eventIDs := make([]string, 0, len(reqs))
	for _, req := range reqs {
		if req.Priority == 0 {
			req.Priority = 5
		}

		if err := validateInternalRequest(&req); err != nil {
			eventIDs = append(eventIDs, "invalid:"+err.Error())
			continue
		}

		templateSource := req.TemplateSource
		if templateSource == "" {
			templateSource = h.cfg.Template.DefaultSource
		}
		wsCacheStrategy := req.WSCacheStrategy
		if wsCacheStrategy == "" {
			wsCacheStrategy = "persist"
		}

		event := &domain.NotificationEvent{
			EventID:         uuid.New().String(),
			EventType:       req.EventType,
			UserID:          req.UserID,
			Email:           req.Email,
			Phone:           req.Phone,
			Channels:        req.Channels,
			TemplateCode:    req.TemplateCode,
			TemplateLang:    req.TemplateLang,
			TemplateSource:  templateSource,
			RawTemplate:     req.RawTemplate,
			WSCacheStrategy: wsCacheStrategy,
			Variables:       req.Variables,
			DeviceTokens:    req.DeviceTokens,
			PushTitle:       req.PushTitle,
			PushBody:        req.PushBody,
			PushData:        req.PushData,
			Priority:        req.Priority,
			CreatedAt:       time.Now(),
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

// =============================================================================
// 管理 API
// =============================================================================

// reloadFileTemplates 热加载模板文件（无需重启服务）
func (h *Handler) reloadFileTemplates(c *gin.Context) {
	if h.templateRepo == nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": "模板仓库未初始化"})
		return
	}
	h.templateRepo.ReloadFileTemplates()
	c.JSON(http.StatusOK, gin.H{
		"status":  "ok",
		"message": "模板文件已热加载",
		"time":    time.Now().Unix(),
	})
}

// getTemplateStats 获取模板统计信息
func (h *Handler) getTemplateStats(c *gin.Context) {
	templates, _, err := h.templateService.ListTemplates("", 1, 1000)
	if err != nil {
		c.JSON(http.StatusInternalServerError, gin.H{"error": err.Error()})
		return
	}

	// 按渠道统计
	channelStats := make(map[string]int)
	for _, t := range templates {
		channelStats[t.Channel]++
	}

	c.JSON(http.StatusOK, gin.H{
		"total":         len(templates),
		"channel_stats": channelStats,
		"templates":     templates,
	})
}