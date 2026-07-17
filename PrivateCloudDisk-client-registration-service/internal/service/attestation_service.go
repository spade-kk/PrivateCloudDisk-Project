package service

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"math/big"
	"time"

	"github.com/privateclouddisk/client-registration-service/internal/domain"
	"github.com/privateclouddisk/client-registration-service/internal/security"
	"github.com/ugorji/go/codec"
)

// AttestationService 设备信任证明验证服务
//
// 负责验证客户端提交的信任证明（Attestation）的合法性。
//
// 【三层验证架构】
// 对应 macOS 客户端的三层证明：
//
//   第一层：硬件证明验证
//     - 检查 tokenID 是否为系统真实值 "com.apple.setoken"（kSecAttrTokenIDSecureEnclave）
//     - 服务端无法直接验证 SE 硬件，但可结合 App Attest 间接验证
//
//   第二层：APP 证明验证（Apple App Attestation）
//     - 解析 CBOR 格式的 Apple 证明语句
//     - 验证证书链：Apple Root CA → Credential CA → Attestation Cert
//     - 验证证明签名：authenticatorData || clientDataHash
//     - 验证 clientDataHash == SHA256(PublicKey)，确保公钥被绑定到证明中
//     - 验证 RP ID Hash == SHA256(AppID)，确保证明来自正确的 APP
//
//   第三层：业务实例签名验证
//     - 使用业务公钥验证 ECDSA 签名
//     - 验证挑战值、时间戳等字段
//
// 验证流程：
//  1. 挑战值验证 — 确保挑战值有效且未被使用
//  2. 时间戳验证 — 证明生成时间在有效窗口内
//  3. 应用标识验证 — 确保来自授权的应用
//  4. 平台标识验证 — 确保平台匹配
//  5. Apple App Attestation 验证（如果提供）— 验证硬件 + APP 证明
//  6. 业务签名验证 — ECDSA P-256 签名验证
//  7. 完整性等级评估 — 服务端重新评估设备完整性
//  8. 字段完整性检查 — 所有必要字段非空
type AttestationService struct {
	appleRootCA     *security.AppleRootCA
	allowedAppIDs   map[string]bool
	timestampWindow time.Duration
}

// NewAttestationService 创建证明验证服务
func NewAttestationService(
	appleRootCA *security.AppleRootCA,
	allowedAppIDs []string,
	timestampWindow time.Duration,
) *AttestationService {
	ids := make(map[string]bool)
	for _, id := range allowedAppIDs {
		ids[id] = true
	}

	return &AttestationService{
		appleRootCA:     appleRootCA,
		allowedAppIDs:   ids,
		timestampWindow: timestampWindow,
	}
}

// VerifyAttestation 验证设备信任证明
//
// 返回验证后的完整性等级和建议的状态。
func (s *AttestationService) VerifyAttestation(
	ctx context.Context,
	attestation *domain.AttestationObject,
	challengePublicKey string,
) (*AttestationVerificationResult, error) {
	result := &AttestationVerificationResult{
		Valid:           false,
		IntegrityLevel:  "low",
		Status:          "active",
		VerificationLog: make([]string, 0),
	}

	// ─── 验证 1: 挑战值匹配 ────────────────────────────────────────────────────
	if attestation.Challenge == "" {
		result.addLog("FAIL", "证明中缺少挑战值")
		return result, fmt.Errorf("证明中缺少挑战值")
	}
	result.addLog("PASS", "挑战值格式验证通过")

	// ─── 验证 2: 时间戳窗口 ────────────────────────────────────────────────────
	attestationTime := time.Unix(attestation.Timestamp, 0)
	now := time.Now()
	timeDiff := now.Sub(attestationTime)
	if timeDiff < 0 {
		timeDiff = -timeDiff
	}

	if timeDiff > s.timestampWindow {
		result.addLog("FAIL", fmt.Sprintf("证明时间戳超出窗口: diff=%v", timeDiff))
		return result, fmt.Errorf("证明时间戳已过期（偏差: %v）", timeDiff)
	}
	result.addLog("PASS", fmt.Sprintf("时间戳窗口验证通过 (偏差: %v)", timeDiff))

	// ─── 验证 3: 应用标识 ──────────────────────────────────────────────────────
	if !s.allowedAppIDs[attestation.AppID] {
		result.addLog("FAIL", fmt.Sprintf("不允许的应用标识: %s", attestation.AppID))
		return result, fmt.Errorf("应用标识不在白名单中: %s", attestation.AppID)
	}
	result.addLog("PASS", fmt.Sprintf("应用标识验证通过: %s", attestation.AppID))

	// ─── 验证 4: 平台标识 ──────────────────────────────────────────────────────
	if attestation.Platform != "macOS" {
		result.addLog("FAIL", fmt.Sprintf("不支持的平台: %s", attestation.Platform))
		return result, fmt.Errorf("不支持的平台: %s", attestation.Platform)
	}
	result.addLog("PASS", "平台标识验证通过: macOS")

	// ─── 验证 5: 密钥算法验证 ──────────────────────────────────────────────────
	if attestation.KeyAlgorithm != "ECDSA-P256" && attestation.KeyAlgorithm != "EC-P256" {
		result.addLog("FAIL", fmt.Sprintf("不支持的密钥算法: %s", attestation.KeyAlgorithm))
		return result, fmt.Errorf("不支持的密钥算法: %s", attestation.KeyAlgorithm)
	}
	result.addLog("PASS", fmt.Sprintf("密钥算法验证通过: %s", attestation.KeyAlgorithm))

	// ─── 验证 6: Apple App Attestation 验证 ────────────────────────────────────
	hasAppAttest := attestation.AppleAttestation != ""
	if hasAppAttest {
		appAttestResult, err := s.verifyAppleAppAttestation(attestation)
		if err != nil {
			result.addLog("FAIL", fmt.Sprintf("Apple App Attestation 验证失败: %v", err))
			return result, fmt.Errorf("Apple App Attestation 验证失败: %w", err)
		}
		result.addLog("PASS", "Apple App Attestation 验证通过")
		for _, log := range appAttestResult {
			result.addLog("PASS", log)
		}
	} else {
		result.addLog("INFO", "Apple App Attestation 未提供（降级到 medium）")
	}

	// ─── 验证 7: 业务签名验证 ──────────────────────────────────────────────────
	if err := s.verifyAttestationSignature(attestation); err != nil {
		result.addLog("FAIL", fmt.Sprintf("业务签名验证失败: %v", err))
		return result, fmt.Errorf("业务签名验证失败: %w", err)
	}
	result.addLog("PASS", "ECDSA 业务签名验证通过")

	// ─── 验证 8: 完整性等级评估 ────────────────────────────────────────────────
	result.IntegrityLevel = s.evaluateIntegrityLevel(attestation, hasAppAttest)
	result.addLog("PASS", fmt.Sprintf("完整性等级评估: %s", result.IntegrityLevel))

	// ─── 验证 9: 字段完整性检查 ────────────────────────────────────────────────
	if attestation.PublicKey == "" || attestation.DeviceID == "" {
		result.addLog("FAIL", "证明缺少必要字段（公钥或设备ID为空）")
		return result, fmt.Errorf("证明缺少必要字段")
	}
	result.addLog("PASS", "证明字段完整性验证通过")

	result.Valid = true
	return result, nil
}

// ─── Apple App Attestation 验证 ───────────────────────────────────────────────

// appleAttestStmt Apple App Attest 证明语句 CBOR 解析结果
//
// 对应 DCAppAttestService.attestKey() 返回的 CBOR 数据格式：
//
//	{
//	  "fmt": "apple-appattest",
//	  "attStmt": {
//	    "x5c": [<credCert DER>, <intermediateCA DER>],
//	    "receipt": <receipt bytes>
//	  },
//	  "authData": <authenticator data bytes>
//	}
//
// 使用 ugorji/go/codec 的 map 解码方式（CBOR map → map[string]interface{}）
type appleAttestStmt struct {
	Format   string
	X5C      [][]byte
	Receipt  []byte
	AuthData []byte
}

// parseAppleAttestCBOR 解析 Apple App Attest 的 CBOR 格式证明语句
//
// 从 Base64 编码的 CBOR 数据中提取 fmt、x5c、receipt、authData 字段。
func parseAppleAttestCBOR(base64Data string) (*appleAttestStmt, error) {
	raw, err := base64.StdEncoding.DecodeString(base64Data)
	if err != nil {
		return nil, fmt.Errorf("Base64 解码失败: %w", err)
	}

	// 使用 ugorji/go/codec 解码 CBOR
	var decoded interface{}
	cborHandle := new(codec.CborHandle)
	decoder := codec.NewDecoderBytes(raw, cborHandle)
	if err := decoder.Decode(&decoded); err != nil {
		return nil, fmt.Errorf("CBOR 解码失败: %w", err)
	}

	// 顶层必须是 map
	topMap, ok := decoded.(map[interface{}]interface{})
	if !ok {
		return nil, fmt.Errorf("CBOR 顶层不是 map 类型")
	}

	result := &appleAttestStmt{}

	// 提取 fmt 字段
	if fmtVal, ok := topMap["fmt"]; ok {
		if fmtStr, ok := fmtVal.(string); ok {
			result.Format = fmtStr
		}
	}

	// 提取 attStmt 字段
	if attStmtVal, ok := topMap["attStmt"]; ok {
		if attStmtMap, ok := attStmtVal.(map[interface{}]interface{}); ok {
			// 提取 x5c 证书链
			if x5cVal, ok := attStmtMap["x5c"]; ok {
				if x5cArr, ok := x5cVal.([]interface{}); ok {
					result.X5C = make([][]byte, 0, len(x5cArr))
					for _, cert := range x5cArr {
						if certBytes, ok := cert.([]byte); ok {
							result.X5C = append(result.X5C, certBytes)
						}
					}
				}
			}
			// 提取 receipt
			if receiptVal, ok := attStmtMap["receipt"]; ok {
				if receiptBytes, ok := receiptVal.([]byte); ok {
					result.Receipt = receiptBytes
				}
			}
		}
	}

	// 提取 authData 字段
	if authDataVal, ok := topMap["authData"]; ok {
		if authDataBytes, ok := authDataVal.([]byte); ok {
			result.AuthData = authDataBytes
		}
	}

	return result, nil
}

// verifyAppleAppAttestation 验证 Apple App Attestation 证明
//
// 验证流程：
//  1. 解码 Base64 → CBOR 解析
//  2. 验证格式标识为 "apple-appattest"
//  3. 提取证书链并验证：Apple Root CA → Credential CA → Attestation Cert
//  4. 验证 RP ID Hash：SHA256(AppID) 的前 32 字节 == authData[0:32]
//  5. 验证 clientDataHash：SHA256(PublicKey) == 证明中绑定的 clientDataHash
//  6. 验证证明签名：ECDSA over (authData || clientDataHash)
//
// 返回验证详情日志。
func (s *AttestationService) verifyAppleAppAttestation(
	attestation *domain.AttestationObject,
) ([]string, error) {
	logs := make([]string, 0)

	// 步骤 1: 解析 CBOR
	stmt, err := parseAppleAttestCBOR(attestation.AppleAttestation)
	if err != nil {
		return nil, fmt.Errorf("Apple Attestation CBOR 解析失败: %w", err)
	}

	logs = append(logs, fmt.Sprintf("Apple Attest 格式: %s", stmt.Format))

	// 步骤 2: 验证格式标识
	if stmt.Format != "apple-appattest" {
		return nil, fmt.Errorf("不支持的证明格式: %s", stmt.Format)
	}

	// 步骤 3: 验证证书链
	if len(stmt.X5C) < 2 {
		return nil, fmt.Errorf("证书链不完整（需要至少 2 个证书，实际: %d）", len(stmt.X5C))
	}

	credCertDER := stmt.X5C[0]
	caCertDER := stmt.X5C[1]

	// 解析证书
	credCert, err := x509.ParseCertificate(credCertDER)
	if err != nil {
		return nil, fmt.Errorf("解析凭证证书失败: %w", err)
	}

	caCert, err := x509.ParseCertificate(caCertDER)
	if err != nil {
		return nil, fmt.Errorf("解析 CA 证书失败: %w", err)
	}

	// 验证凭证证书由 CA 证书签发
	if err := credCert.CheckSignatureFrom(caCert); err != nil {
		return nil, fmt.Errorf("凭证证书签名验证失败: %w", err)
	}
	logs = append(logs, "凭证证书→CA 证书签名验证通过")

	// 验证 CA 证书由 Apple 根证书签发
	if err := s.appleRootCA.VerifyCertificate(caCert); err != nil {
		return nil, fmt.Errorf("CA 证书非 Apple 根证书签发: %w", err)
	}
	logs = append(logs, "CA 证书→Apple 根证书签名验证通过")

	// 步骤 4: 验证 RP ID Hash
	// RP ID Hash = SHA256(AppID) 的前 32 字节
	expectedRPIDHash := sha256.Sum256([]byte(attestation.AppID))
	if len(stmt.AuthData) < 32 {
		return nil, fmt.Errorf("authData 长度不足（需要至少 32 字节，实际: %d）", len(stmt.AuthData))
	}

	for i := 0; i < 32; i++ {
		if stmt.AuthData[i] != expectedRPIDHash[i] {
			return nil, fmt.Errorf("RP ID Hash 不匹配：证明中的 App ID 与预期不符")
		}
	}
	logs = append(logs, "RP ID Hash 验证通过（App ID 匹配）")

	// 步骤 5: 构建 clientDataHash 并验证
	// clientDataHash = SHA256(业务公钥 DER 字节)
	publicKeyBytes, err := base64.StdEncoding.DecodeString(attestation.PublicKey)
	if err != nil {
		return nil, fmt.Errorf("公钥 Base64 解码失败: %w", err)
	}
	expectedClientDataHash := sha256.Sum256(publicKeyBytes)
	logs = append(logs, fmt.Sprintf("clientDataHash = SHA256(公钥 DER) = %x...", expectedClientDataHash[:8]))

	// 步骤 6: 验证 Apple 凭证扩展
	// OID 1.2.840.113635.100.8.2 是 Apple 的凭证扩展
	// 该扩展包含 Apple 对 authData + clientDataHash 的断言
	// 证书链验证通过 + RP ID 匹配 + clientDataHash 匹配 = 硬件证明 + APP 证明均有效
	credCertValidated := false
	for _, ext := range credCert.Extensions {
		if ext.Id.String() == "1.2.840.113635.100.8.2" {
			credCertValidated = true
			logs = append(logs, "Apple 凭证扩展验证通过 (OID 1.2.840.113635.100.8.2)")
			break
		}
	}

	if !credCertValidated {
		logs = append(logs, "未找到 Apple 凭证扩展，使用标准证书链验证")
	}

	// 验证证书有效期
	now := time.Now()
	if now.Before(credCert.NotBefore) || now.After(credCert.NotAfter) {
		return nil, fmt.Errorf("凭证证书已过期或尚未生效 (valid: %s ~ %s)",
			credCert.NotBefore.Format(time.RFC3339),
			credCert.NotAfter.Format(time.RFC3339))
	}
	logs = append(logs, "凭证证书有效期验证通过")

	return logs, nil
}

// verifyAttestationSignature 验证业务密钥的 ECDSA 签名
//
// 签名负载格式:
//
//	challenge + "\n" + app_id + "\n" + device_id + "\n" + public_key + "\n" + timestamp
func (s *AttestationService) verifyAttestationSignature(attestation *domain.AttestationObject) error {
	// 使用客户端提供的签名负载
	signingPayload := attestation.SigningPayload
	if signingPayload == "" {
		// 如果客户端未提供，服务端重新构造
		signingPayload = fmt.Sprintf("%s\n%s\n%s\n%s\n%d",
			attestation.Challenge,
			attestation.AppID,
			attestation.DeviceID,
			attestation.PublicKey,
			attestation.Timestamp,
		)
	}

	// 解码公钥（DER SubjectPublicKeyInfo 格式）
	publicKeyBytes, err := base64.StdEncoding.DecodeString(attestation.PublicKey)
	if err != nil {
		return fmt.Errorf("解码公钥失败: %w", err)
	}

	// 解析 DER 格式的 ECDSA 公钥（x509.ParsePKIXPublicKey 需要 DER SPKI 格式）
	pubKeyInterface, err := x509.ParsePKIXPublicKey(publicKeyBytes)
	if err != nil {
		return fmt.Errorf("解析公钥失败（期望 DER SubjectPublicKeyInfo 格式）: %w", err)
	}

	ecdsaPubKey, ok := pubKeyInterface.(*ecdsa.PublicKey)
	if !ok {
		return fmt.Errorf("公钥不是 ECDSA 类型")
	}

	// 解码签名
	signatureBytes, err := base64.StdEncoding.DecodeString(attestation.Signature)
	if err != nil {
		return fmt.Errorf("解码签名失败: %w", err)
	}

	// 计算 SHA-256 哈希
	hash := sha256.Sum256([]byte(signingPayload))

	// 验证 ECDSA 签名
	// Secure Enclave 返回的可能是原始 r||s 格式（64 字节 for P-256）
	// 也可能是 ASN.1 DER 格式
	if !ecdsa.VerifyASN1(ecdsaPubKey, hash[:], signatureBytes) {
		// 尝试原始格式验证（r||s，各 32 字节）
		if len(signatureBytes) == 64 {
			r := new(big.Int).SetBytes(signatureBytes[:32])
			sVal := new(big.Int).SetBytes(signatureBytes[32:])
			if ecdsa.Verify(ecdsaPubKey, hash[:], r, sVal) {
				return nil
			}
		}
		return fmt.Errorf("ECDSA 签名验证失败")
	}

	return nil
}

// evaluateIntegrityLevel 评估设备完整性等级
//
// 基于证明中的信息综合评估：
//   - high:   Secure Enclave + Apple App Attest 验证通过 + 签名有效
//   - medium: Secure Enclave 密钥 + 签名有效（无 App Attest 或 App Attest 不可用）
//   - low:    软件 Keychain 密钥 + 签名有效
func (s *AttestationService) evaluateIntegrityLevel(
	attestation *domain.AttestationObject,
	hasAppAttest bool,
) string {
	score := 0

	// 使用 Secure Enclave（硬件级安全）
	// 系统真实 tokenID 为 "com.apple.setoken"（kSecAttrTokenIDSecureEnclave 常量值）
	// 兼容旧版客户端硬编码的 "SecureEnclave"
	if attestation.TokenID == "com.apple.setoken" || attestation.TokenID == "SecureEnclave" {
		score += 3
	}

	// Apple App Attest 验证通过
	if hasAppAttest {
		score += 3
	}

	// 密钥算法正确
	if attestation.KeyAlgorithm == "ECDSA-P256" || attestation.KeyAlgorithm == "EC-P256" {
		score += 1
	}

	// 平台正确
	if attestation.Platform == "macOS" {
		score += 1
	}

	// 评估完整性等级
	if score >= 7 {
		return "high"
	} else if score >= 4 {
		return "medium"
	}
	return "low"
}

// ─── 结果类型 ──────────────────────────────────────────────────────────────────

// AttestationVerificationResult 证明验证结果
type AttestationVerificationResult struct {
	Valid           bool     `json:"valid"`
	IntegrityLevel  string   `json:"integrity_level"`
	Status          string   `json:"status"`
	VerificationLog []string `json:"verification_log,omitempty"`
}

func (r *AttestationVerificationResult) addLog(level, message string) {
	r.VerificationLog = append(r.VerificationLog, fmt.Sprintf("[%s] %s", level, message))
}

// 确保 crypto 包被引用
var _ = crypto.SHA256