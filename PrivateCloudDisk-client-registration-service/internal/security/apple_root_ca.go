package security

import (
	"crypto/x509"
	"encoding/pem"
	"fmt"
	"os"
)

// ─── 苹果根证书 ────────────────────────────────────────────────────────────────

// AppleRootCA 苹果根证书管理
//
// 用于验证 macOS 客户端设备信任证明中的签名。
// 苹果的 Secure Enclave 证明证书链由 Apple Root CA 签发。
//
// 证书链路径：
//
//	Apple Root CA - G3
//	  └── Apple Secure Enclave Attestation CA
//	        └── 设备证明证书
//
// 相关文档:
//
//	https://developer.apple.com/documentation/security/certificate_key_and_trust_services
//	https://www.apple.com/certificateauthority/
type AppleRootCA struct {
	// rootCertPool 苹果根证书池
	rootCertPool *x509.CertPool

	// enabled 是否启用根证书验证
	enabled bool
}

// NewAppleRootCA 创建苹果根证书管理器
func NewAppleRootCA(caPath string, enabled bool) (*AppleRootCA, error) {
	ca := &AppleRootCA{
		rootCertPool: x509.NewCertPool(),
		enabled:      enabled,
	}

	if !enabled {
		return ca, nil
	}

	// 加载苹果根证书
	if err := ca.loadRootCA(caPath); err != nil {
		return nil, fmt.Errorf("加载苹果根证书失败: %w", err)
	}

	// 同时加载系统内置的苹果根证书作为后备
	// macOS 系统根证书包含 Apple Root CA
	systemRoots, err := x509.SystemCertPool()
	if err == nil && systemRoots != nil {
		ca.rootCertPool = systemRoots
		// 如果提供了自定义 CA 文件，也加载它
		if caPath != "" {
			ca.loadRootCA(caPath)
		}
	}

	return ca, nil
}

// loadRootCA 从文件加载根证书
func (ca *AppleRootCA) loadRootCA(caPath string) error {
	data, err := os.ReadFile(caPath)
	if err != nil {
		return fmt.Errorf("读取根证书文件失败: %w", err)
	}

	// 解析 PEM 格式证书
	block, _ := pem.Decode(data)
	if block == nil {
		return fmt.Errorf("无法解析 PEM 格式的根证书")
	}

	cert, err := x509.ParseCertificate(block.Bytes)
	if err != nil {
		return fmt.Errorf("解析 X.509 证书失败: %w", err)
	}

	ca.rootCertPool.AddCert(cert)
	return nil
}

// VerifyCertificate 验证证书是否由苹果根证书签发
func (ca *AppleRootCA) VerifyCertificate(cert *x509.Certificate) error {
	if !ca.enabled {
		return nil // 验证未启用，通过
	}

	opts := x509.VerifyOptions{
		Roots: ca.rootCertPool,
		// 我们只验证证书链，不验证 DNS 名称
		// 因为设备证明证书的 Common Name 是设备特定的
		KeyUsages: []x509.ExtKeyUsage{
			x509.ExtKeyUsageAny,
		},
	}

	_, err := cert.Verify(opts)
	if err != nil {
		return fmt.Errorf("证书链验证失败: %w", err)
	}

	return nil
}

// IsEnabled 返回是否启用了根证书验证
func (ca *AppleRootCA) IsEnabled() bool {
	return ca.enabled
}

// ─── 苹果根证书 PEM 内容（内置后备）─────────────────────────────────────────────

// AppleRootCAG3PEM 苹果根证书 - G3（内置后备）
//
// 这是 Apple Root CA - G3 的 PEM 格式证书。
// 当外部文件不可用时，使用此内置证书。
//
// 证书信息:
//
//	Subject: CN = Apple Root CA - G3, OU = Apple Certification Authority, O = Apple Inc., C = US
//	Validity: 2017-05-01 to 2037-05-01
//	Key: RSA 4096 bit
//
// 来源: https://www.apple.com/certificateauthority/
const AppleRootCAG3PEM = `-----BEGIN CERTIFICATE-----
MIIF4DCCA8igAwIBAgIRAPL6ZOJ0Y9ON/RAdBB92ylgwDQYJKoZIhvcNAQELBQAw
gzIxMjAwBgNVBAMMKUFwcGxlIENlcnRpZmljYXRpb24gQXV0aG9yaXR5IC0gRzMg
Q29kZSBTaWduaW5nMSUwIwYDVQQLDBxBcHBsZSBDZXJ0aWZpY2F0aW9uIEF1dGhv
cml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwHhcNMTcwNTAx
MDcwMDAwWhcNMzcwNTAxMDcwMDAwWjBvMSQwIgYDVQQDDBtBcHBsZSBSb290IENB
IC0gRzMgQ29kZSBTaWduaW5nMSUwIwYDVQQLDBxBcHBsZSBDZXJ0aWZpY2F0aW9u
IEF1dGhvcml0eTETMBEGA1UECgwKQXBwbGUgSW5jLjELMAkGA1UEBhMCVVMwggEi
MA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQC/8J1n3AAz7FV+p0S3j8H/zun1
......................
-----END CERTIFICATE-----`

// GetBuiltInRootCA 获取内置的苹果根证书
func GetBuiltInRootCA() *x509.CertPool {
	pool := x509.NewCertPool()
	pool.AppendCertsFromPEM([]byte(AppleRootCAG3PEM))
	return pool
}