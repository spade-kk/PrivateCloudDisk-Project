package http

import (
	"crypto/subtle"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/privateclouddisk/client-registration-service/internal/domain"
	"github.com/privateclouddisk/client-registration-service/internal/service"
)

// Handler HTTP 请求处理器
//
// 提供客户端注册相关的 REST API 端点。
type Handler struct {
	registrationService  *service.RegistrationService
	internalServiceToken string
}

// NewHandler 创建新的 Handler
func NewHandler(
	registrationService *service.RegistrationService,
	internalServiceToken string,
) *Handler {
	return &Handler{
		registrationService:  registrationService,
		internalServiceToken: strings.TrimSpace(internalServiceToken),
	}
}

// RegisterRoutes 注册路由
//
// 注意：网关路由匹配 /api/v1/client/** 并通过 StripPrefix=2 剥离 /api/v1 前缀，
// 因此下游服务注册的路由路径不应包含 /api/v1 前缀，而是直接以 /client 开头。
func (h *Handler) RegisterRoutes(r *gin.Engine) {
	// 客户端注册 API（公开接口，由 AuthGlobalFilter 白名单放行）
	// 实际请求路径：POST /api/v1/client/register-challenge → 网关转发 → POST /client/register-challenge
	// 实际请求路径：POST /api/v1/client/register          → 网关转发 → POST /client/register
	clientGroup := r.Group("/client")
	{
		clientGroup.POST("/register-challenge", h.GetRegisterChallenge)
		clientGroup.POST("/register", h.RegisterClient)
		// 第二阶段本地插件：只有“JWT 已认证 + 设备签名已验证”的请求才能建立用户绑定。
		clientGroup.POST("/:clientId/bind", h.BindUser)
	}

	// 内部接口（供网关内部调用，获取客户端公钥、状态、吊销）
	// 实际请求路径：GET /api/v1/client/internal/:clientId/public-key → 网关转发 → GET /client/internal/:clientId/public-key
	internalGroup := r.Group("/client/internal", h.requireInternalServiceToken())
	{
		internalGroup.GET("/:clientId/public-key", h.GetPublicKey)
		internalGroup.GET("/:clientId/status", h.GetClientStatus)
		internalGroup.GET("/:clientId/plugin-binding", h.GetPluginBinding)
		internalGroup.DELETE("/:clientId", h.RevokeClient)
	}

	// 健康检查
	r.GET("/health", h.HealthCheck)
}

// BindUser 建立设备与登录用户的可信绑定。
//
// 需求对应：本地插件只能分发给已注册、未吊销且由当前用户持有的客户端。
// X-User-Id 由网关注入，X-Client-ID 只能由设备签名过滤器验证后重新注入。
func (h *Handler) BindUser(c *gin.Context) {
	clientID := strings.TrimSpace(c.Param("clientId"))
	userID := strings.TrimSpace(c.GetHeader("X-User-Id"))
	verifiedClientID := strings.TrimSpace(c.GetHeader("X-Client-ID"))
	authSource := strings.TrimSpace(c.GetHeader("X-Auth-Source"))
	if clientID == "" || userID == "" {
		c.JSON(http.StatusUnauthorized, domain.APIResponse{
			Code: 401, Message: "缺少登录身份或客户端标识",
		})
		return
	}
	if verifiedClientID != clientID || authSource != "device-identity" {
		c.JSON(http.StatusForbidden, domain.APIResponse{
			Code: 403, Message: "客户端签名身份与绑定目标不一致",
		})
		return
	}

	var req domain.BindUserRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code: 400, Message: "客户端信息格式错误: " + err.Error(),
		})
		return
	}
	binding, err := h.registrationService.BindUser(clientID, userID, &req)
	if err != nil {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code: 400, Message: "客户端绑定失败: " + err.Error(),
		})
		return
	}
	c.JSON(http.StatusOK, domain.APIResponse{
		Code: 200, Message: "客户端绑定成功", Data: binding,
	})
}

// ─── 公开接口 ──────────────────────────────────────────────────────────────────

// GetRegisterChallenge 获取注册挑战值
//
// 网关路径：POST /api/v1/client/register-challenge
// 下游路径：POST /client/register-challenge（网关 StripPrefix=2 剥离 /api/v1）
//
// 请求体:
//
//	{
//	  "platform": "macOS",
//	  "public_key": "<Base64 DER 公钥>",
//	  "key_algorithm": "ECDSA-P256"
//	}
//
// 响应:
//
//	{
//	  "code": 200,
//	  "message": "success",
//	  "data": {
//	    "challenge": "pcd-challenge-<uuid>-<timestamp>",
//	    "expires_at": 1700000000
//	  }
//	}
func (h *Handler) GetRegisterChallenge(c *gin.Context) {
	var req domain.RegisterChallengeRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "请求参数格式错误: " + err.Error(),
		})
		return
	}

	// 参数校验
	if req.Platform == "" {
		req.Platform = "macOS"
	}
	if req.PublicKey == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "公钥不能为空",
		})
		return
	}

	resp, err := h.registrationService.GenerateChallenge(c.Request.Context(), &req)
	if err != nil {
		c.JSON(http.StatusInternalServerError, domain.APIResponse{
			Code:    500,
			Message: "生成挑战值失败: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "success",
		Data:    resp,
	})
}

// RegisterClient 注册客户端
//
// 网关路径：POST /api/v1/client/register
// 下游路径：POST /client/register（网关 StripPrefix=2 剥离 /api/v1）
//
// 请求体: 完整的 AttestationObject
// 响应: 包含 client_id 和完整性等级
func (h *Handler) RegisterClient(c *gin.Context) {
	var req domain.RegisterRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "请求参数格式错误: " + err.Error(),
		})
		return
	}

	// 参数校验
	if req.Attestation.PublicKey == "" || req.Attestation.Challenge == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "证明缺少必要字段（公钥或挑战值）",
		})
		return
	}

	resp, err := h.registrationService.RegisterClient(c.Request.Context(), &req)
	if err != nil {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "客户端注册失败: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "客户端注册成功",
		Data:    resp,
	})
}

// ─── 内部接口 ──────────────────────────────────────────────────────────────────

// GetPublicKey 获取客户端公钥（内部接口）
//
// 网关路径：GET /api/v1/client/internal/:clientId/public-key
// 下游路径：GET /client/internal/:clientId/public-key（网关 StripPrefix=2 剥离 /api/v1）
//
// 由网关 DeviceIdentityFilter 调用，用于验证请求签名。
// 实现缓存优先（Cache-Aside）模式：
//  1. Redis 缓存命中 → 直接返回
//  2. 缓存未命中 → 查询数据库 → 回写 Redis → 返回
func (h *Handler) GetPublicKey(c *gin.Context) {
	clientID := c.Param("clientId")
	if clientID == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "客户端 ID 不能为空",
		})
		return
	}

	resp, err := h.registrationService.GetPublicKey(c.Request.Context(), clientID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, domain.APIResponse{
			Code:    500,
			Message: "查询公钥失败: " + err.Error(),
		})
		return
	}

	if resp == nil {
		c.JSON(http.StatusNotFound, domain.APIResponse{
			Code:    404,
			Message: "客户端未注册或身份已过期",
		})
		return
	}

	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "success",
		Data:    resp,
	})
}

// GetClientStatus 获取客户端状态（内部接口）
//
// 网关路径：GET /api/v1/client/internal/:clientId/status
// 下游路径：GET /client/internal/:clientId/status（网关 StripPrefix=2 剥离 /api/v1）
func (h *Handler) GetClientStatus(c *gin.Context) {
	clientID := c.Param("clientId")
	if clientID == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "客户端 ID 不能为空",
		})
		return
	}

	identity, err := h.registrationService.GetClientStatus(c.Request.Context(), clientID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, domain.APIResponse{
			Code:    500,
			Message: "查询客户端状态失败: " + err.Error(),
		})
		return
	}

	if identity == nil {
		c.JSON(http.StatusNotFound, domain.APIResponse{
			Code:    404,
			Message: "客户端不存在",
		})
		return
	}

	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "success",
		Data:    identity,
	})
}

// GetPluginBinding 供 Plugin Service 在每次本地插件分发前核验客户端归属和能力。
func (h *Handler) GetPluginBinding(c *gin.Context) {
	clientID := strings.TrimSpace(c.Param("clientId"))
	userID := strings.TrimSpace(c.Query("user_id"))
	if clientID == "" || userID == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code: 400, Message: "客户端标识和用户标识不能为空",
		})
		return
	}
	binding, err := h.registrationService.GetUserBinding(clientID, userID)
	if err != nil {
		c.JSON(http.StatusInternalServerError, domain.APIResponse{
			Code: 500, Message: "查询客户端绑定失败",
		})
		return
	}
	if binding == nil {
		c.JSON(http.StatusNotFound, domain.APIResponse{
			Code: 404, Message: "客户端未绑定、已吊销或不属于当前用户",
		})
		return
	}
	c.JSON(http.StatusOK, domain.APIResponse{
		Code: 200, Message: "success", Data: binding,
	})
}

// RevokeClient 吊销客户端（内部接口）
//
// 网关路径：DELETE /api/v1/client/internal/:clientId
// 下游路径：DELETE /client/internal/:clientId（网关 StripPrefix=2 剥离 /api/v1）
func (h *Handler) RevokeClient(c *gin.Context) {
	clientID := c.Param("clientId")
	if clientID == "" {
		c.JSON(http.StatusBadRequest, domain.APIResponse{
			Code:    400,
			Message: "客户端 ID 不能为空",
		})
		return
	}

	if err := h.registrationService.RevokeClient(c.Request.Context(), clientID); err != nil {
		c.JSON(http.StatusInternalServerError, domain.APIResponse{
			Code:    500,
			Message: "吊销客户端失败: " + err.Error(),
		})
		return
	}

	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "客户端已吊销",
	})
}

// ─── 健康检查 ──────────────────────────────────────────────────────────────────

// HealthCheck 健康检查
func (h *Handler) HealthCheck(c *gin.Context) {
	c.JSON(http.StatusOK, domain.APIResponse{
		Code:    200,
		Message: "ok",
		Data: map[string]string{
			"service": "client-registration-service",
			"version": "1.0.0",
		},
	})
}

// requireInternalServiceToken 对所有私网管理接口执行默认拒绝的服务凭证校验。
func (h *Handler) requireInternalServiceToken() gin.HandlerFunc {
	return func(c *gin.Context) {
		configured := []byte(h.internalServiceToken)
		presented := []byte(strings.TrimSpace(c.GetHeader("X-PCD-Service-Token")))
		if len(configured) == 0 {
			c.AbortWithStatusJSON(http.StatusServiceUnavailable, domain.APIResponse{
				Code: 503, Message: "内部服务凭证未配置",
			})
			return
		}
		if len(configured) != len(presented) ||
			subtle.ConstantTimeCompare(configured, presented) != 1 {
			c.AbortWithStatusJSON(http.StatusUnauthorized, domain.APIResponse{
				Code: 401, Message: "内部服务凭证无效",
			})
			return
		}
		c.Next()
	}
}
