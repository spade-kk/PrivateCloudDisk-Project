package service

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/privateclouddisk/client-registration-service/internal/domain"
	"github.com/privateclouddisk/client-registration-service/internal/redisutil"
	"github.com/privateclouddisk/client-registration-service/internal/repository"
)

// RegistrationService 客户端注册服务
//
// 负责编排完整的客户端注册流程：
//  1. 生成挑战值（Challenge）
//  2. 验证设备信任证明（Attestation）
//  3. 生成客户端 ID
//  4. 持久化客户端身份（数据库 + Redis 缓存）
//
// 对应 macOS 客户端 DeviceIdentityManager.performRegistration() 的服务端实现。
type RegistrationService struct {
	repo               *repository.ClientRepository
	redisRepo          *redisutil.ClientRedisRepo
	attestationService *AttestationService
	challengeTTL       time.Duration
	publicKeyCacheTTL  time.Duration
}

// NewRegistrationService 创建注册服务
func NewRegistrationService(
	repo *repository.ClientRepository,
	redisRepo *redisutil.ClientRedisRepo,
	attestationService *AttestationService,
	challengeTTL time.Duration,
	publicKeyCacheTTL time.Duration,
) *RegistrationService {
	return &RegistrationService{
		repo:               repo,
		redisRepo:          redisRepo,
		attestationService: attestationService,
		challengeTTL:       challengeTTL,
		publicKeyCacheTTL:  publicKeyCacheTTL,
	}
}

// ─── 挑战值生成 ────────────────────────────────────────────────────────────────

// GenerateChallenge 生成注册挑战值
//
// 挑战值 = UUID v4 + 时间戳，TTL 由 challengeTTL 控制。
// 挑战值仅存储在 Redis 中，不持久化到数据库。
// Redis 的 TTL 机制自动处理过期清理，无需手动维护。
func (s *RegistrationService) GenerateChallenge(
	ctx context.Context,
	req *domain.RegisterChallengeRequest,
) (*domain.RegisterChallengeResponse, error) {
	challenge := fmt.Sprintf("pcd-challenge-%s-%d", uuid.New().String(), time.Now().UnixNano())
	expiresAt := time.Now().Add(s.challengeTTL)

	// 挑战值仅存储到 Redis（TTL 自动过期），不写入数据库
	if err := s.redisRepo.CacheChallenge(ctx, challenge, req.PublicKey, s.challengeTTL); err != nil {
		return nil, fmt.Errorf("Redis 存储挑战值失败: %w", err)
	}

	return &domain.RegisterChallengeResponse{
		Challenge: challenge,
		ExpiresAt: expiresAt.Unix(),
	}, nil
}

// ─── 客户端注册 ────────────────────────────────────────────────────────────────

// RegisterClient 注册客户端
//
// 完整注册流程：
//  1. 从 Redis 验证挑战值有效性（挑战值不存在 = 已过期或已使用）
//  2. 验证设备信任证明
//  3. 检查设备是否已注册（重复注册保护）
//  4. 生成客户端 ID
//  5. 持久化到数据库
//  6. 缓存公钥到 Redis
//  7. 标记设备已注册
//  8. 删除挑战值（标记已使用，防止重复利用）
func (s *RegistrationService) RegisterClient(
	ctx context.Context,
	req *domain.RegisterRequest,
) (*domain.RegisterResponse, error) {
	attestation := &req.Attestation
	if req.Platform != "" && req.Platform != attestation.Platform {
		return nil, fmt.Errorf("请求平台与证明平台不一致")
	}
	if len(req.AppVersion) == 0 || len(req.AppVersion) > 32 {
		return nil, fmt.Errorf("客户端版本格式无效")
	}

	// ─── 步骤 1: 从 Redis 验证挑战值 ───────────────────────────────────────────
	// 挑战值仅存于 Redis，查不到就意味着已过期、已使用或从未生成。
	// Redis 的 TTL 机制等价于过期判断，DEL 操作等价于 used 标记。
	challengePublicKey, err := s.redisRepo.GetChallenge(ctx, attestation.Challenge)
	if err != nil {
		return nil, fmt.Errorf("查询挑战值失败: %w", err)
	}
	if challengePublicKey == "" {
		return nil, fmt.Errorf("挑战值不存在或已过期")
	}

	// ─── 步骤 2: 验证设备信任证明 ──────────────────────────────────────────────
	verifyResult, err := s.attestationService.VerifyAttestation(
		ctx, attestation, challengePublicKey,
	)
	if err != nil {
		return nil, fmt.Errorf("证明验证失败: %w", err)
	}
	if !verifyResult.Valid {
		return nil, fmt.Errorf("证明验证未通过")
	}

	// 打印验证日志（调试用）
	for _, log := range verifyResult.VerificationLog {
		fmt.Printf("[Attestation] %s\n", log)
	}

	// ─── 步骤 3: 检查设备是否已注册 ────────────────────────────────────────────
	existingIdentity, err := s.repo.GetByDeviceID(attestation.DeviceID)
	if err != nil {
		return nil, fmt.Errorf("查询设备注册状态失败: %w", err)
	}
	if existingIdentity != nil {
		// 设备已注册，返回已有 client_id（幂等性）
		fmt.Printf("[Registration] 设备已注册，返回已有身份: client_id=%s\n",
			existingIdentity.ClientID)
		/*
		 * 本地插件可信分发可靠性修复：
		 * 原行为在重复注册时直接返回，若 Redis 公钥缓存已过期，网关仍无法验证后续设备签名。
		 * 新行为以数据库中的可信身份回填缓存；不改变 client_id，也不修改原注册时间。
		 */
		if err := s.redisRepo.CachePublicKey(ctx, &domain.PublicKeyResponse{
			ClientID:       existingIdentity.ClientID,
			PublicKey:      existingIdentity.PublicKey,
			KeyAlgorithm:   existingIdentity.KeyAlgorithm,
			IntegrityLevel: existingIdentity.IntegrityLevel,
			Status:         existingIdentity.Status,
		}); err != nil {
			return nil, fmt.Errorf("刷新客户端公钥缓存失败: %w", err)
		}
		if err := s.redisRepo.MarkChallengeUsed(ctx, attestation.Challenge); err != nil {
			return nil, fmt.Errorf("消费注册挑战值失败: %w", err)
		}

		return &domain.RegisterResponse{
			ClientID:       existingIdentity.ClientID,
			IntegrityLevel: existingIdentity.IntegrityLevel,
			RegisteredAt:   existingIdentity.RegisteredAt.Unix(),
		}, nil
	}

	// ─── 步骤 4: 生成客户端 ID ─────────────────────────────────────────────────
	clientID := uuid.New().String()

	// ─── 步骤 5: 构建客户端身份并持久化 ─────────────────────────────────────────
	now := time.Now()
	identity := &domain.ClientIdentity{
		ClientID:       clientID,
		DeviceID:       attestation.DeviceID,
		Platform:       attestation.Platform,
		AppID:          attestation.AppID,
		PublicKey:      attestation.PublicKey,
		KeyAlgorithm:   attestation.KeyAlgorithm,
		TokenID:        attestation.TokenID,
		IntegrityLevel: verifyResult.IntegrityLevel,
		OSVersion:      attestation.OSVersion,
		Hostname:       attestation.Hostname,
		Status:         "active",
		RegisteredAt:   now,
		LastVerifiedAt: now,
	}

	if err := s.repo.InsertClient(identity); err != nil {
		return nil, fmt.Errorf("保存客户端身份失败: %w", err)
	}

	// ─── 步骤 6: 缓存公钥到 Redis ──────────────────────────────────────────────
	pubKeyResp := &domain.PublicKeyResponse{
		ClientID:       clientID,
		PublicKey:      attestation.PublicKey,
		KeyAlgorithm:   attestation.KeyAlgorithm,
		IntegrityLevel: verifyResult.IntegrityLevel,
		Status:         "active",
	}

	if err := s.redisRepo.CachePublicKey(ctx, pubKeyResp); err != nil {
		// Redis 缓存失败不阻塞注册流程，公钥已持久化到数据库
		fmt.Printf("Redis 缓存公钥失败: %v\n", err)
	}

	// ─── 步骤 7: 标记设备已注册 ────────────────────────────────────────────────
	if err := s.redisRepo.MarkDeviceRegistered(
		ctx, attestation.DeviceID, clientID, s.publicKeyCacheTTL,
	); err != nil {
		fmt.Printf("Redis 标记设备注册失败: %v\n", err)
	}

	// ─── 步骤 8: 删除挑战值（标记已使用，防重复利用） ──────────────────────────
	// Redis DEL 操作：挑战值只能用一次，删除后无法再次通过验证
	if err := s.redisRepo.MarkChallengeUsed(ctx, attestation.Challenge); err != nil {
		fmt.Printf("Redis 删除挑战值失败: %v\n", err)
	}

	fmt.Printf("[Registration] 客户端注册成功: client_id=%s, device=%s, integrity=%s\n",
		clientID, attestation.DeviceID[:16]+"...", verifyResult.IntegrityLevel)

	return &domain.RegisterResponse{
		ClientID:       clientID,
		IntegrityLevel: verifyResult.IntegrityLevel,
		RegisteredAt:   now.Unix(),
	}, nil
}

// ─── 公钥查询（缓存优先） ──────────────────────────────────────────────────────

// GetPublicKey 获取客户端公钥（Cache-Aside 模式）
//
// 查询流程：
//  1. 检查 Redis 缓存
//  2. 缓存命中 → 直接返回
//  3. 缓存未命中 → 查询数据库
//  4. 数据库查询结果回写 Redis
//  5. 返回结果
//
// 此方法供网关内部接口调用，用于请求签名验证。
func (s *RegistrationService) GetPublicKey(
	ctx context.Context,
	clientID string,
) (*domain.PublicKeyResponse, error) {
	// 步骤 1: 查询 Redis 缓存
	resp, err := s.redisRepo.GetPublicKey(ctx, clientID)
	if err != nil {
		fmt.Printf("Redis 查询公钥失败: %v，回退到数据库查询\n", err)
	}

	if resp != nil {
		// 缓存命中
		return resp, nil
	}

	// 步骤 2: 缓存未命中，查询数据库
	dbResp, err := s.repo.GetPublicKeyByClientID(clientID)
	if err != nil {
		return nil, fmt.Errorf("查询客户端公钥失败: %w", err)
	}
	if dbResp == nil {
		return nil, nil // 客户端不存在
	}

	// 步骤 3: 回写 Redis 缓存（异步，不影响主流程）
	go func() {
		if cacheErr := s.redisRepo.CachePublicKey(context.Background(), dbResp); cacheErr != nil {
			fmt.Printf("Redis 回写公钥缓存失败: %v\n", cacheErr)
		}
	}()

	return dbResp, nil
}

// ─── 客户端状态管理 ────────────────────────────────────────────────────────────

// RevokeClient 吊销客户端身份
func (s *RegistrationService) RevokeClient(ctx context.Context, clientID string) error {
	if err := s.repo.UpdateStatus(clientID, "revoked"); err != nil {
		return fmt.Errorf("吊销客户端失败: %w", err)
	}

	// 使 Redis 缓存失效
	if err := s.redisRepo.InvalidatePublicKey(ctx, clientID); err != nil {
		fmt.Printf("Redis 失效公钥缓存失败: %v\n", err)
	}

	return nil
}

// GetClientStatus 获取客户端状态
func (s *RegistrationService) GetClientStatus(ctx context.Context, clientID string) (*domain.ClientIdentity, error) {
	return s.repo.GetByClientID(clientID)
}

// BindUser 只接受 Handler 已核对过的网关注入身份与设备签名结果。
func (s *RegistrationService) BindUser(
	clientID, userID string,
	request *domain.BindUserRequest,
) (*domain.ClientUserBinding, error) {
	identity, err := s.repo.GetByClientID(clientID)
	if err != nil {
		return nil, err
	}
	if identity == nil || identity.Status != "active" {
		return nil, fmt.Errorf("客户端不存在或已吊销")
	}
	if err := s.repo.BindUser(
		clientID, userID, request.ClientType, request.Platform,
		request.AppVersion, request.Capabilities,
	); err != nil {
		return nil, err
	}
	binding, err := s.repo.GetUserBinding(clientID, userID)
	if err != nil {
		return nil, err
	}
	if binding == nil {
		return nil, fmt.Errorf("客户端已绑定到其他账号，需先由原账号解除或吊销")
	}
	return binding, nil
}

// GetUserBinding 供 Plugin Service 在每次分发时实时核验。
func (s *RegistrationService) GetUserBinding(
	clientID, userID string,
) (*domain.ClientUserBinding, error) {
	return s.repo.GetUserBinding(clientID, userID)
}
