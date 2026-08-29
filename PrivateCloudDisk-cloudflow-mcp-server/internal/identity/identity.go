// Package identity verifies the gateway-issued, request-bound user context.
// It deliberately does not parse or forward the external OAuth/JWT bearer
// token, preventing a confused-deputy token-passthrough path to Capability Hub.
package identity

import (
	"crypto/hmac"
	"crypto/sha256"
	"crypto/subtle"
	"encoding/hex"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"time"
)

const (
	HeaderUserID    = "X-PCD-User-Id"
	HeaderTenantID  = "X-PCD-Tenant-Id"
	HeaderSpaceID   = "X-PCD-Space-Id"
	HeaderRequestID = "X-PCD-Request-Id"
	HeaderTimestamp = "X-PCD-Identity-Timestamp"
	HeaderSignature = "X-PCD-Identity-Signature"
)

type Identity struct {
	UserID    string
	TenantID  string
	SpaceID   string
	RequestID string
	AgentID   string
}

type Verifier struct {
	secret []byte
	maxAge time.Duration
	now    func() time.Time
}

func NewVerifier(secret string, maxAge time.Duration) *Verifier {
	return &Verifier{secret: []byte(secret), maxAge: maxAge, now: time.Now}
}

func (verifier *Verifier) Verify(request *http.Request) (Identity, error) {
	if len(verifier.secret) < 32 {
		return Identity{}, errors.New("trusted identity verifier is not configured")
	}
	userID := clean(request.Header.Get(HeaderUserID), 128)
	tenantID := clean(request.Header.Get(HeaderTenantID), 128)
	spaceID := clean(request.Header.Get(HeaderSpaceID), 128)
	requestID := clean(request.Header.Get(HeaderRequestID), 128)
	timestamp := clean(request.Header.Get(HeaderTimestamp), 32)
	signature := clean(request.Header.Get(HeaderSignature), 128)
	if userID == "" || requestID == "" || timestamp == "" || signature == "" {
		return Identity{}, errors.New("missing gateway-signed identity context")
	}
	issuedAt, err := time.Parse(time.RFC3339, timestamp)
	if err != nil {
		return Identity{}, errors.New("invalid identity timestamp")
	}
	if delta := verifier.now().UTC().Sub(issuedAt.UTC()); delta > verifier.maxAge || delta < -30*time.Second {
		return Identity{}, errors.New("expired gateway-signed identity context")
	}
	expected := sign(verifier.secret, request.Method, request.URL.Path, requestID, timestamp, userID, tenantID, spaceID)
	presented, err := hex.DecodeString(signature)
	if err != nil || subtle.ConstantTimeCompare(expected, presented) != 1 {
		return Identity{}, errors.New("invalid gateway-signed identity context")
	}
	return Identity{
		UserID: userID, TenantID: tenantID, SpaceID: spaceID, RequestID: requestID,
		AgentID: clean(request.Header.Get("MCP-Client-Id"), 128),
	}, nil
}

func SignForGateway(secret, method, path, requestID, timestamp, userID, tenantID, spaceID string) string {
	return hex.EncodeToString(sign([]byte(secret), method, path, requestID, timestamp, userID, tenantID, spaceID))
}

func sign(secret []byte, method, path, requestID, timestamp, userID, tenantID, spaceID string) []byte {
	canonical := strings.Join([]string{
		"pcd-mcp-v1", strings.ToUpper(method), path, requestID, timestamp, userID, tenantID, spaceID,
	}, "\n")
	mac := hmac.New(sha256.New, secret)
	_, _ = mac.Write([]byte(canonical))
	return mac.Sum(nil)
}

func clean(value string, limit int) string {
	value = strings.TrimSpace(value)
	if len(value) > limit || strings.ContainsAny(value, "\r\n") {
		return ""
	}
	return value
}

func CanonicalTimestamp(now time.Time) string {
	return now.UTC().Format(time.RFC3339)
}

func (identity Identity) String() string {
	return fmt.Sprintf("user=%s tenant=%s space=%s", identity.UserID, identity.TenantID, identity.SpaceID)
}
