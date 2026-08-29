package identity

import (
	"net/http/httptest"
	"testing"
	"time"
)

func TestVerifierAcceptsOnlyRequestBoundGatewaySignature(t *testing.T) {
	secret := "0123456789abcdef0123456789abcdef"
	issuedAt := time.Date(2026, 8, 29, 10, 0, 0, 0, time.UTC)
	verifier := NewVerifier(secret, 2*time.Minute)
	verifier.now = func() time.Time { return issuedAt.Add(time.Minute) }
	request := httptest.NewRequest("POST", "http://mcp.internal/mcp", nil)
	request.Header.Set(HeaderUserID, "00000000-0000-0000-0000-000000000001")
	request.Header.Set(HeaderTenantID, "tenant-a")
	request.Header.Set(HeaderSpaceID, "00000000-0000-0000-0000-000000000002")
	request.Header.Set(HeaderRequestID, "request-1")
	request.Header.Set(HeaderTimestamp, CanonicalTimestamp(issuedAt))
	request.Header.Set(HeaderSignature, SignForGateway(
		secret, "POST", "/mcp", "request-1", CanonicalTimestamp(issuedAt),
		"00000000-0000-0000-0000-000000000001", "tenant-a", "00000000-0000-0000-0000-000000000002",
	))
	if _, err := verifier.Verify(request); err != nil {
		t.Fatalf("expected valid signed identity: %v", err)
	}
	request.Header.Set(HeaderSpaceID, "00000000-0000-0000-0000-000000000003")
	if _, err := verifier.Verify(request); err == nil {
		t.Fatal("changed space context must invalidate the signature")
	}
}

func TestVerifierRejectsExpiredContext(t *testing.T) {
	secret := "0123456789abcdef0123456789abcdef"
	issuedAt := time.Date(2026, 8, 29, 10, 0, 0, 0, time.UTC)
	verifier := NewVerifier(secret, time.Minute)
	verifier.now = func() time.Time { return issuedAt.Add(2 * time.Minute) }
	request := httptest.NewRequest("POST", "http://mcp.internal/mcp", nil)
	request.Header.Set(HeaderUserID, "00000000-0000-0000-0000-000000000001")
	request.Header.Set(HeaderRequestID, "request-1")
	request.Header.Set(HeaderTimestamp, CanonicalTimestamp(issuedAt))
	request.Header.Set(HeaderSignature, SignForGateway(secret, "POST", "/mcp", "request-1", CanonicalTimestamp(issuedAt), "00000000-0000-0000-0000-000000000001", "", ""))
	if _, err := verifier.Verify(request); err == nil {
		t.Fatal("expired identity must be rejected")
	}
}
