// Package http 提供验证码管理 HTTP API 处理器。
//
// 接口设计：
//  1. 首次发送（需人机验证）
//     POST /api/internal/verification/send
//     Body: { "email"?, "phone"?, "purpose": "REGISTER", "captcha_token": "..." }
//     Response: { "code": 200, "message": "success", "data": { "resend_token": "uuid...", "expires_in": 600, "remaining_resends": 8 } }
//
//  2. 重新发送（需 resend token，免人机验证）
//     POST /api/internal/verification/resend
//     Header: X-Resend-Token: {uuid}
//     Body: { "email"?, "phone"?, "purpose": "REGISTER" }
//     Response: { "code": 200, "data": { "resend_token": "same-uuid...", "expires_in": ..., "remaining_resends": N-1 } }
//
//  3. 验证码校验（内部调用）
//     POST /api/internal/verification/verify
//     Body: { "target_type": "email", "target": "...", "purpose": "REGISTER", "code": "123456" }
//     Response: { "code": 200, "message": "success", "data": { "valid": true } }
//
//  4. 注册专用发送（需人机验证）
//     POST /api/internal/verification/register/send
//     Body: { "email"?, "phone"?, "captcha_token": "..." }
//     Response: 同 /send
//
//  5. 注册专用重新发送
//     POST /api/internal/verification/register/resend
//     Header: X-Resend-Token: {uuid}
//     Body: { "email"?, "phone"? }
//     Response: 同 /resend
package http

import (
	"log"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"

	"github.com/privateclouddisk/notification-service/internal/domain"
	"github.com/privateclouddisk/notification-service/internal/service"
)

// VerificationHandler 验证码 HTTP 处理器
type VerificationHandler struct {
	verificationService *service.VerificationCodeService
}

// NewVerificationHandler 创建验证码处理器
func NewVerificationHandler(verificationService *service.VerificationCodeService) *VerificationHandler {
	return &VerificationHandler{
		verificationService: verificationService,
	}
}

// RegisterRoutes 注册验证码路由
func (h *VerificationHandler) RegisterRoutes(r *gin.Engine) {
	// 内部 API（供其他微服务调用）
	internal := r.Group("/api/internal/verification")
	{
		internal.POST("/send", h.sendVerificationCode)
		internal.POST("/resend", h.resendVerificationCode)
		internal.POST("/verify", h.verifyCode)
		internal.POST("/check-attempts", h.checkCodeAttempts)
		internal.POST("/record-failure", h.recordCodeFailure)
		internal.POST("/clear-attempts", h.clearCodeAttempts)

		// 注册专用（purpose 固定为 REGISTER）
		internal.POST("/register/send", h.sendRegisterVerificationCode)
		internal.POST("/register/resend", h.resendRegisterVerificationCode)
	}

	// 旧版兼容：业务公共接口（来自原 platform-service 的控制器路径）
	business := r.Group("/business/verification")
	{
		business.POST("/send", h.sendVerificationCode)
		business.POST("/resend", h.resendVerificationCode)
	}

	// 旧版兼容：注册专用路径
	businessRegister := r.Group("/business/verification-code")
	{
		businessRegister.POST("/send", h.sendRegisterVerificationCode)
		businessRegister.POST("/resend", h.resendRegisterVerificationCode)
	}
}

// =============================================================================
// 统一响应格式
// =============================================================================

// JSONResponse 统一 JSON 响应
type JSONResponse struct {
	Code    int         `json:"code"`
	Message string      `json:"message"`
	Data    interface{} `json:"data"`
}

func successResponse(data interface{}) JSONResponse {
	return JSONResponse{Code: 200, Message: "success", Data: data}
}

func errorResponse(code int, message string) JSONResponse {
	return JSONResponse{Code: code, Message: message, Data: nil}
}

// =============================================================================
// 首次发送验证码（需人机验证码）
// =============================================================================

// sendVerificationCode 首次发送验证码
// POST /api/internal/verification/send
func (h *VerificationHandler) sendVerificationCode(c *gin.Context) {
	var req domain.VerificationSendRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	targetType, target, purpose, err := req.ValidTarget()
	if err != nil {
		ve, ok := domain.IsVerificationError(err)
		if ok {
			c.JSON(http.StatusBadRequest, errorResponse(ve.Code, ve.Message))
			return
		}
		c.JSON(http.StatusBadRequest, errorResponse(400, err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	vo, err := h.verificationService.SendCode(
		c.Request.Context(),
		string(targetType), target, string(purpose),
		req.CaptchaToken, req.CaptchaAction, clientIP,
	)
	if err != nil {
		handleVerificationError(c, err)
		return
	}

	c.JSON(http.StatusOK, successResponse(vo))
}

// =============================================================================
// 重新发送验证码（无需人机验证码，需携带有效的 resend token）
// =============================================================================

// resendVerificationCode 重新发送验证码
// POST /api/internal/verification/resend
// Header: X-Resend-Token: {uuid}
func (h *VerificationHandler) resendVerificationCode(c *gin.Context) {
	resendToken := c.GetHeader("X-Resend-Token")
	if resendToken == "" {
		c.JSON(http.StatusBadRequest, errorResponse(400, "缺少 X-Resend-Token 请求头"))
		return
	}

	var req domain.VerificationSendRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	targetType, target, purpose, err := req.ValidTarget()
	if err != nil {
		ve, ok := domain.IsVerificationError(err)
		if ok {
			c.JSON(http.StatusBadRequest, errorResponse(ve.Code, ve.Message))
			return
		}
		c.JSON(http.StatusBadRequest, errorResponse(400, err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	vo, err := h.verificationService.ResendCode(
		c.Request.Context(),
		string(targetType), target, string(purpose),
		resendToken, clientIP,
	)
	if err != nil {
		handleVerificationError(c, err)
		return
	}

	c.JSON(http.StatusOK, successResponse(vo))
}

// =============================================================================
// 验证码校验（内部调用）
// =============================================================================

// verifyCode 校验验证码
// POST /api/internal/verification/verify
func (h *VerificationHandler) verifyCode(c *gin.Context) {
	var req domain.VerificationVerifyRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	// 先检查防爆破
	if err := h.verificationService.CheckCodeAttempts(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose, clientIP,
	); err != nil {
		handleVerificationError(c, err)
		return
	}

	valid, err := h.verificationService.VerifyCode(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose,
		req.Code, clientIP,
	)
	if err != nil {
		c.JSON(http.StatusInternalServerError, errorResponse(500, "服务器内部错误"))
		return
	}

	if !valid {
		// 记录失败
		h.verificationService.RecordCodeFailure(
			c.Request.Context(),
			req.TargetType, req.Target, req.Purpose, clientIP,
		)
		c.JSON(http.StatusOK, errorResponse(400, "验证码错误"))
		return
	}

	// 验证成功，清除失败计数
	h.verificationService.ClearCodeAttempts(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose, clientIP,
	)

	c.JSON(http.StatusOK, successResponse(gin.H{"valid": true}))
}

// =============================================================================
// 防爆破检查
// =============================================================================

// checkCodeAttempts 检查验证码校验失败次数
// POST /api/internal/verification/check-attempts
func (h *VerificationHandler) checkCodeAttempts(c *gin.Context) {
	var req struct {
		TargetType string `json:"target_type" binding:"required"`
		Target     string `json:"target" binding:"required"`
		Purpose    string `json:"purpose" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	if err := h.verificationService.CheckCodeAttempts(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose, clientIP,
	); err != nil {
		handleVerificationError(c, err)
		return
	}

	c.JSON(http.StatusOK, successResponse(gin.H{"allowed": true}))
}

// recordCodeFailure 记录验证码校验失败
// POST /api/internal/verification/record-failure
func (h *VerificationHandler) recordCodeFailure(c *gin.Context) {
	var req struct {
		TargetType string `json:"target_type" binding:"required"`
		Target     string `json:"target" binding:"required"`
		Purpose    string `json:"purpose" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	h.verificationService.RecordCodeFailure(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose, clientIP,
	)

	c.JSON(http.StatusOK, successResponse(nil))
}

// clearCodeAttempts 清除验证码失败计数
// POST /api/internal/verification/clear-attempts
func (h *VerificationHandler) clearCodeAttempts(c *gin.Context) {
	var req struct {
		TargetType string `json:"target_type" binding:"required"`
		Target     string `json:"target" binding:"required"`
		Purpose    string `json:"purpose" binding:"required"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	clientIP := resolveClientIP(c)

	h.verificationService.ClearCodeAttempts(
		c.Request.Context(),
		req.TargetType, req.Target, req.Purpose, clientIP,
	)

	c.JSON(http.StatusOK, successResponse(nil))
}

// =============================================================================
// 注册专用接口
// =============================================================================

// sendRegisterVerificationCode 首次发送注册验证码
// POST /api/internal/verification/register/send
func (h *VerificationHandler) sendRegisterVerificationCode(c *gin.Context) {
	var req struct {
		Email         string `json:"email,omitempty"`
		Phone         string `json:"phone,omitempty"`
		CaptchaToken  string `json:"captcha_token,omitempty"`
		CaptchaAction string `json:"captcha_action,omitempty"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	var targetType, target string
	if req.Email != "" {
		targetType = "email"
		target = strings.TrimSpace(strings.ToLower(req.Email))
	} else if req.Phone != "" {
		targetType = "phone"
		target = strings.TrimSpace(req.Phone)
	} else {
		c.JSON(http.StatusBadRequest, errorResponse(400, "邮箱和手机号至少提供一个"))
		return
	}

	clientIP := resolveClientIP(c)

	vo, err := h.verificationService.SendCode(
		c.Request.Context(),
		targetType, target, "REGISTER",
		req.CaptchaToken, req.CaptchaAction, clientIP,
	)
	if err != nil {
		handleVerificationError(c, err)
		return
	}

	c.JSON(http.StatusOK, successResponse(vo))
}

// resendRegisterVerificationCode 重新发送注册验证码
// POST /api/internal/verification/register/resend
func (h *VerificationHandler) resendRegisterVerificationCode(c *gin.Context) {
	resendToken := c.GetHeader("X-Resend-Token")
	if resendToken == "" {
		c.JSON(http.StatusBadRequest, errorResponse(400, "缺少 X-Resend-Token 请求头"))
		return
	}

	var req struct {
		Email string `json:"email,omitempty"`
		Phone string `json:"phone,omitempty"`
	}
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, errorResponse(400, "请求参数错误: "+err.Error()))
		return
	}

	var targetType, target string
	if req.Email != "" {
		targetType = "email"
		target = strings.TrimSpace(strings.ToLower(req.Email))
	} else if req.Phone != "" {
		targetType = "phone"
		target = strings.TrimSpace(req.Phone)
	} else {
		c.JSON(http.StatusBadRequest, errorResponse(400, "邮箱和手机号至少提供一个"))
		return
	}

	clientIP := resolveClientIP(c)

	vo, err := h.verificationService.ResendCode(
		c.Request.Context(),
		targetType, target, "REGISTER",
		resendToken, clientIP,
	)
	if err != nil {
		handleVerificationError(c, err)
		return
	}

	c.JSON(http.StatusOK, successResponse(vo))
}

// =============================================================================
// 工具函数
// =============================================================================

// resolveClientIP 解析客户端真实 IP
func resolveClientIP(c *gin.Context) string {
	// 优先从 X-Forwarded-For 获取（网关注入）
	if forwarded := c.GetHeader("X-Forwarded-For"); forwarded != "" {
		ips := strings.Split(forwarded, ",")
		return strings.TrimSpace(ips[0])
	}
	// 其次 X-Real-IP
	if realIP := c.GetHeader("X-Real-IP"); realIP != "" {
		return realIP
	}
	// 最后使用请求 IP
	return c.ClientIP()
}

// handleVerificationError 统一处理验证码业务错误
func handleVerificationError(c *gin.Context, err error) {
	ve, ok := domain.IsVerificationError(err)
	if ok {
		statusCode := http.StatusBadRequest
		if ve.Code == 429 {
			statusCode = http.StatusTooManyRequests
		}
		c.JSON(statusCode, errorResponse(ve.Code, ve.Message))
		return
	}

	log.Printf("[验证码] 未预期的错误: %v", err)
	c.JSON(http.StatusInternalServerError, errorResponse(500, "服务器内部错误"))
}