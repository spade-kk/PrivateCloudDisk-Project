package service

import (
	"context"
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"fmt"
	"testing"
	"time"

	"github.com/privateclouddisk/client-registration-service/internal/domain"
)

/**
 * Web 本地插件客户端证明测试。
 *
 * 需求对应：WebCrypto 软件密钥可以注册，但服务端完整性等级必须固定为 low，
 * 且挑战值必须和申请挑战时的公钥绑定，避免替换公钥完成注册。
 */
func TestVerifyWebAttestationReturnsLowIntegrity(t *testing.T) {
	privateKey, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	publicDER, err := x509.MarshalPKIXPublicKey(&privateKey.PublicKey)
	if err != nil {
		t.Fatal(err)
	}
	publicKey := base64.StdEncoding.EncodeToString(publicDER)
	attestation := validWebAttestation(t, privateKey, publicKey)
	service := NewAttestationService(
		nil,
		[]string{"web.privateclouddisk.app"},
		5*time.Minute,
	)

	result, err := service.VerifyAttestation(context.Background(), attestation, publicKey)

	if err != nil {
		t.Fatalf("Web 证明应验证通过: %v", err)
	}
	if !result.Valid || result.IntegrityLevel != "low" {
		t.Fatalf("期望 valid=true 且 integrity=low，实际: %#v", result)
	}
}

func TestVerifyWebAttestationRejectsChallengeKeyReplacement(t *testing.T) {
	privateKey, _ := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	publicDER, _ := x509.MarshalPKIXPublicKey(&privateKey.PublicKey)
	publicKey := base64.StdEncoding.EncodeToString(publicDER)
	attestation := validWebAttestation(t, privateKey, publicKey)
	service := NewAttestationService(
		nil,
		[]string{"web.privateclouddisk.app"},
		5*time.Minute,
	)

	if _, err := service.VerifyAttestation(
		context.Background(), attestation, "other-public-key",
	); err == nil {
		t.Fatal("替换挑战绑定公钥必须被拒绝")
	}
}

func validWebAttestation(
	t *testing.T,
	privateKey *ecdsa.PrivateKey,
	publicKey string,
) *domain.AttestationObject {
	t.Helper()
	timestamp := time.Now().Unix()
	payload := fmt.Sprintf("%s\n%s\n%s\n%s\n%d",
		"challenge",
		"web.privateclouddisk.app",
		"web-device-1234567890",
		publicKey,
		timestamp,
	)
	digest := sha256.Sum256([]byte(payload))
	signature, err := ecdsa.SignASN1(rand.Reader, privateKey, digest[:])
	if err != nil {
		t.Fatal(err)
	}
	return &domain.AttestationObject{
		Version:        "1",
		AppID:          "web.privateclouddisk.app",
		Platform:       "Web",
		DeviceID:       "web-device-1234567890",
		PublicKey:      publicKey,
		KeyAlgorithm:   "ECDSA-P256",
		TokenID:        "WebCrypto-P256",
		IntegrityLevel: "low",
		OSVersion:      "browser",
		Hostname:       "browser",
		Timestamp:      timestamp,
		Challenge:      "challenge",
		Signature:      base64.StdEncoding.EncodeToString(signature),
		SigningPayload: payload,
	}
}
