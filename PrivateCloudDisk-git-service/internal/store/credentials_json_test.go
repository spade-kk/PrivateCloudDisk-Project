package store

import (
	"encoding/json"
	"strings"
	"testing"
	"time"
)

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] 锁定凭证管理 API 的 JSON 命名，防止恢复默认
// Go 字段序列化后再次破坏 Web 端 GitPAT/GitSSHKey 对接。
func TestCredentialRecordsMarshalWebContract(t *testing.T) {
	now := time.Date(2026, time.August, 16, 0, 0, 0, 0, time.UTC)
	patPayload, err := json.Marshal(TokenRecord{
		ID: "pat-id", UserID: "internal-user", Name: "laptop", Prefix: "pcd_pat_abc",
		Scopes: []string{"read_repository", "write_repository"}, CreatedAt: now,
	})
	if err != nil {
		t.Fatal(err)
	}
	patJSON := string(patPayload)
	for _, expected := range []string{`"tokenId":"pat-id"`, `"tokenPrefix":"pcd_pat_abc"`, `"scopes":["read_repository","write_repository"]`, `"createdAt":"2026-08-16T00:00:00Z"`} {
		if !strings.Contains(patJSON, expected) {
			t.Fatalf("PAT JSON missing %s: %s", expected, patJSON)
		}
	}
	for _, forbidden := range []string{`"ID"`, `"UserID"`, `"Prefix"`, `"Scopes"`} {
		if strings.Contains(patJSON, forbidden) {
			t.Fatalf("PAT JSON leaked legacy/internal key %s: %s", forbidden, patJSON)
		}
	}

	sshPayload, err := json.Marshal(SSHKeyRecord{ID: "key-id", UserID: "internal-user", Name: "MacBook", Fingerprint: "SHA256:test", CreatedAt: now})
	if err != nil {
		t.Fatal(err)
	}
	sshJSON := string(sshPayload)
	for _, expected := range []string{`"keyId":"key-id"`, `"keyName":"MacBook"`, `"fingerprint":"SHA256:test"`} {
		if !strings.Contains(sshJSON, expected) {
			t.Fatalf("SSH JSON missing %s: %s", expected, sshJSON)
		}
	}
	for _, forbidden := range []string{`"ID"`, `"UserID"`, `"Name"`, `"Fingerprint"`} {
		if strings.Contains(sshJSON, forbidden) {
			t.Fatalf("SSH JSON leaked legacy/internal key %s: %s", forbidden, sshJSON)
		}
	}
}
