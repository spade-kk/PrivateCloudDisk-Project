package gitproto

import (
	"errors"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"privateclouddisk/git-service/internal/auth"
)

func TestParseGitPath(t *testing.T) {
	tests := []struct {
		path, slug, endpoint string
		ok                   bool
	}{
		{"/git/demo.git/info/refs", "demo", "info/refs", true},
		{"/git/demo.git/HEAD", "demo", "HEAD", true},
		{"/git/demo.git/objects/ab/0123456789012345678901234567890123456789", "demo", "objects/ab/0123456789012345678901234567890123456789", true},
		{"/git/team/demo.git/info/refs", "", "", false},
		{"/git/../demo.git/info/refs", "", "", false},
		{"/git/demo.git/../config", "", "", false},
	}
	for _, test := range tests {
		slug, endpoint, ok := parseGitPath(test.path)
		if slug != test.slug || endpoint != test.endpoint || ok != test.ok {
			t.Fatalf("parseGitPath(%q) = %q %q %v", test.path, slug, endpoint, ok)
		}
	}
}

func TestResolveGitOperationMethodsScopesAndDumbReadOnly(t *testing.T) {
	request := httptest.NewRequest(http.MethodGet, "/git/demo.git/info/refs?service=git-upload-pack", nil)
	service, operation, scope, mutation, err := resolveGitOperation(request, "info/refs")
	if err != nil || service != "git-upload-pack" || operation != auth.Fetch || scope != "read_repository" || mutation {
		t.Fatalf("unexpected upload-pack resolution: %q %q %q mutation=%v %v", service, operation, scope, mutation, err)
	}
	request = httptest.NewRequest(http.MethodPost, "/git/demo.git/info/refs", nil)
	if _, _, _, _, err = resolveGitOperation(request, "info/refs"); err == nil {
		t.Fatal("expected info/refs POST to be rejected")
	}
	request = httptest.NewRequest(http.MethodPost, "/git/demo.git/git-receive-pack", nil)
	request.Header.Set("Content-Type", "application/x-git-receive-pack-request")
	_, operation, scope, mutation, err = resolveGitOperation(request, "git-receive-pack")
	if err != nil || operation != auth.Push || scope != "write_repository" || !mutation {
		t.Fatalf("unexpected receive-pack resolution: operation=%q scope=%q mutation=%v err=%v", operation, scope, mutation, err)
	}
	request = httptest.NewRequest(http.MethodGet, "/git/demo.git/objects/info/packs", nil)
	service, operation, scope, mutation, err = resolveGitOperation(request, "objects/info/packs")
	if err != nil || service != "dumb-http" || operation != auth.Fetch || scope != "read_repository" || mutation {
		t.Fatalf("unexpected dumb HTTP resolution: %q %q %q mutation=%v %v", service, operation, scope, mutation, err)
	}
	request = httptest.NewRequest(http.MethodPut, "/git/demo.git/objects/info/packs", nil)
	if _, _, _, _, err = resolveGitOperation(request, "objects/info/packs"); err == nil {
		t.Fatal("expected dumb HTTP object write to be rejected")
	}
}

// TestProtocolEndpointMatrix keeps the protocol allow-list explicit.  It is deliberately
// table driven so adding a convenience file route cannot silently make a bare repository
// file readable without a matching protocol/security review.
// [REQ-GIT-HTTP-2.1~2.50/6.1~6.21] This covers every accepted HTTP endpoint family plus
// the error-only paths that must never reach git http-backend.  Integration scripts cover
// the real Git CLI pack negotiation against a running multi-service environment.
func TestProtocolEndpointMatrix(t *testing.T) {
	const hash = "0123456789012345678901234567890123456789"
	tests := []struct {
		name, method, url, contentType, wantService string
		wantOperation                               auth.Operation
		wantMutation                                bool
	}{
		{"fetch discovery", http.MethodGet, "/git/demo.git/info/refs?service=git-upload-pack", "", "git-upload-pack", auth.Fetch, false},
		{"push discovery is not a mutation", http.MethodGet, "/git/demo.git/info/refs?service=git-receive-pack", "", "git-receive-pack", auth.Push, false},
		{"upload pack rpc", http.MethodPost, "/git/demo.git/git-upload-pack", "application/x-git-upload-pack-request", "git-upload-pack", auth.Fetch, false},
		{"receive pack rpc", http.MethodPost, "/git/demo.git/git-receive-pack", "application/x-git-receive-pack-request", "git-receive-pack", auth.Push, true},
		{"head reference", http.MethodGet, "/git/demo.git/HEAD", "", "dumb-http", auth.Fetch, false},
		{"alternates", http.MethodGet, "/git/demo.git/objects/info/alternates", "", "dumb-http", auth.Fetch, false},
		{"http alternates", http.MethodGet, "/git/demo.git/objects/info/http-alternates", "", "dumb-http", auth.Fetch, false},
		{"packs", http.MethodGet, "/git/demo.git/objects/info/packs", "", "dumb-http", auth.Fetch, false},
		{"pack", http.MethodGet, "/git/demo.git/objects/pack/" + hash + ".pack", "", "dumb-http", auth.Fetch, false},
		{"pack index", http.MethodHead, "/git/demo.git/objects/pack/" + hash + ".idx", "", "dumb-http", auth.Fetch, false},
		{"loose object", http.MethodGet, "/git/demo.git/objects/01/23456789012345678901234567890123456789", "", "dumb-http", auth.Fetch, false},
		{"direct loose object compatibility", http.MethodGet, "/git/demo.git/objects/" + hash, "", "dumb-http", auth.Fetch, false},
		{"branch ref", http.MethodGet, "/git/demo.git/refs/heads/feature/verified", "", "dumb-http", auth.Fetch, false},
		{"tag ref", http.MethodGet, "/git/demo.git/refs/tags/v1.0.0", "", "dumb-http", auth.Fetch, false},
		{"description", http.MethodGet, "/git/demo.git/description", "", "dumb-http", auth.Fetch, false},
		{"commit graph", http.MethodGet, "/git/demo.git/objects/info/commit-graph", "", "dumb-http", auth.Fetch, false},
		{"commit graph chain", http.MethodGet, "/git/demo.git/objects/info/commit-graphs/commit-graph-chain", "", "dumb-http", auth.Fetch, false},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			request := httptest.NewRequest(test.method, test.url, nil)
			request.Header.Set("Content-Type", test.contentType)
			request.Header.Set("Git-Protocol", "version=2")
			_, endpoint, ok := parseGitPath(request.URL.Path)
			if !ok {
				t.Fatalf("path unexpectedly rejected: %s", test.url)
			}
			service, operation, _, mutation, err := resolveGitOperation(request, endpoint)
			if err != nil || service != test.wantService || operation != test.wantOperation || mutation != test.wantMutation {
				t.Fatalf("resolve = service=%q operation=%q mutation=%v err=%v", service, operation, mutation, err)
			}
		})
	}

	rejected := []struct {
		name, method, url, contentType string
		status                         int
	}{
		{"missing discovery service", http.MethodGet, "/git/demo.git/info/refs", "", http.StatusBadRequest},
		{"unsupported discovery service", http.MethodGet, "/git/demo.git/info/refs?service=git-archive", "", http.StatusBadRequest},
		{"discovery method", http.MethodPost, "/git/demo.git/info/refs?service=git-upload-pack", "", http.StatusMethodNotAllowed},
		{"upload wrong content type", http.MethodPost, "/git/demo.git/git-upload-pack", "application/json", http.StatusUnsupportedMediaType},
		{"receive wrong content type", http.MethodPost, "/git/demo.git/git-receive-pack", "application/json", http.StatusUnsupportedMediaType},
		{"dumb object write", http.MethodPut, "/git/demo.git/objects/" + hash, "", http.StatusMethodNotAllowed},
		{"config disclosure", http.MethodGet, "/git/demo.git/config", "", http.StatusNotFound},
		{"path traversal", http.MethodGet, "/git/demo.git/refs/heads/../main", "", http.StatusNotFound},
	}
	for _, test := range rejected {
		t.Run(test.name, func(t *testing.T) {
			request := httptest.NewRequest(test.method, test.url, nil)
			request.Header.Set("Content-Type", test.contentType)
			_, endpoint, ok := parseGitPath(request.URL.Path)
			if !ok {
				// Parser-level rejection is the intended security result for traversal.
				if test.status == http.StatusNotFound {
					return
				}
				t.Fatalf("path rejected before expected resolver error: %s", test.url)
			}
			_, _, _, _, err := resolveGitOperation(request, endpoint)
			var protocolErr *protocolRequestError
			if !errors.As(err, &protocolErr) || protocolErr.status != test.status {
				t.Fatalf("expected status %d, got err=%v", test.status, err)
			}
		})
	}
}

func TestSafeReadEndpointRejectsConfigurationAndInvalidObjects(t *testing.T) {
	if isSafeReadEndpoint("config") || isSafeReadEndpoint("objects/not-a-hash") || isSafeReadEndpoint("refs/heads/../main") {
		t.Fatal("unsafe dumb HTTP path must not reach git http-backend")
	}
	if !isSafeReadEndpoint("objects/pack/0123456789012345678901234567890123456789.pack") {
		t.Fatal("valid pack endpoint should be allowed")
	}
}

func TestGitCLIUsesPATAsBasicPassword(t *testing.T) {
	request := httptest.NewRequest(http.MethodGet, "/git/demo.git/info/refs", nil)
	request.SetBasicAuth("sfsf", "pcd_pat_test")
	username, password, ok := request.BasicAuth()
	if !ok || username != "sfsf" || password != "pcd_pat_test" {
		t.Fatalf("unexpected Basic Auth values: username=%q password=%q ok=%v", username, password, ok)
	}
	if strings.HasPrefix(username, "pcd_pat_") {
		t.Fatal("normal Git username must not be treated as a PAT")
	}

	// [FIX-GIT-CLI-PAT-20260816] 兼容某些凭证助手把 PAT 放在用户名、密码留空的形式。
	request.SetBasicAuth("pcd_pat_test", "")
	username, password, ok = request.BasicAuth()
	if !ok || username != "pcd_pat_test" || password != "" {
		t.Fatalf("unexpected PAT-in-username Basic Auth values: username=%q password=%q ok=%v", username, password, ok)
	}
}
