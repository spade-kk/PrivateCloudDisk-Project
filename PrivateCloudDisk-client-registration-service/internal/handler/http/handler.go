package http

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/privateclouddisk/client-registration-service/internal/domain"
	"github.com/privateclouddisk/client-registration-service/internal/service"
)

// Handler HTTP 请求处理器
//
// 提供客户端注册相关的 REST API 端点。
type Handler struct {
	registrationService *service.RegistrationService
}

// NewHandler 创建新的 Handler
func NewHandler(registrationService *service.RegistrationService) *Handler {
	return &Handler{
		registrationService: registrationService,
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
	}

	// 内部接口（供网关内部调用，获取客户端公钥、状态、吊销）
	// 实际请求路径：GET /api/v1/client/internal/:clientId/public-key → 网关转发 → GET /client/internal/:clientId/public-key
	internalGroup := r.Group("/client/internal")
	{
		internalGroup.GET("/:clientId/public-key", h.GetPublicKey)
		internalGroup.GET("/:clientId/status", h.GetClientStatus)
		internalGroup.DELETE("/:clientId", h.RevokeClient)
	}

	// 健康检查
	r.GET("/health", h.HealthCheck)
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