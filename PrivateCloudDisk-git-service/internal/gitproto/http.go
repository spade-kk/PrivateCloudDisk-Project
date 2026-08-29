package gitproto

import (
	"bufio"
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"log"
	"mime"
	"net"
	"net/http"
	"net/textproto"
	"os"
	"os/exec"
	"regexp"
	"strconv"
	"strings"
	"time"

	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/domain"
	"privateclouddisk/git-service/internal/gitrepo"
	"privateclouddisk/git-service/internal/security"
	"privateclouddisk/git-service/internal/store"
)

var (
	slugPattern         = regexp.MustCompile(`^[a-z0-9][a-z0-9._-]{0,189}$`)
	packPathPattern     = regexp.MustCompile(`^objects/pack/[0-9a-f]{40,64}\.(pack|idx)$`)
	looseObjectPattern  = regexp.MustCompile(`^objects/[0-9a-f]{2}/([0-9a-f]{38}|[0-9a-f]{62})$`)
	directObjectPattern = regexp.MustCompile(`^objects/([0-9a-f]{40}|[0-9a-f]{64})$`)
)

type protocolRequestError struct {
	status  int
	message string
	allow   string
}

func (e *protocolRequestError) Error() string { return e.message }

type HTTPHandler struct {
	cfg        config.Config
	store      *store.Store
	manager    *gitrepo.Manager
	authorizer *auth.Authorizer
	semaphore  chan struct{}
	failures   *security.FailureLimiter
}

func NewHTTPHandler(cfg config.Config, dataStore *store.Store, manager *gitrepo.Manager, authorizer *auth.Authorizer) *HTTPHandler {
	if cfg.MaxProtocolConcurrent < 1 {
		cfg.MaxProtocolConcurrent = 1
	}
	if cfg.MaxProtocolRequestBytes < 1 {
		cfg.MaxProtocolRequestBytes = 2 * 1024 * 1024 * 1024
	}
	if cfg.MaxProtocolResponseBytes < 1 {
		cfg.MaxProtocolResponseBytes = 32 * 1024 * 1024
	}
	return &HTTPHandler{cfg: cfg, store: dataStore, manager: manager, authorizer: authorizer,
		semaphore: make(chan struct{}, cfg.MaxProtocolConcurrent),
		failures:  security.NewFailureLimiter(cfg.AuthFailureLimit, cfg.AuthFailureWindow, cfg.AuthFailureCooldown)}
}

// Match 保留 /git/{slug}.git 作为唯一公开协议根，避免将管理 API /git/repos/** 误送给 Git。
func (h *HTTPHandler) Match(requestPath string) bool {
	return strings.HasPrefix(requestPath, "/git/") && strings.Contains(strings.TrimPrefix(requestPath, "/git/"), ".git")
}

// ServeHTTP 委托系统 Git 的 http-backend 实现 Smart HTTP 协商、浅克隆和 packfile。
// [REQ-GIT-HTTP-2.1~2.50] 原实现只放行三条 Smart HTTP 路径，并把 receive-pack 的
// info/refs 发现误当成写事务；新实现显式区分发现、RPC 与只读 dumb-HTTP 对象端点，
// 在进入 git http-backend 前完成路径、方法、Content-Type、请求大小、认证和统一权限
// 校验。影响范围仅为 Git 协议根 /git/{slug}.git/**，管理 API 路由保持不变。
func (h *HTTPHandler) ServeHTTP(response http.ResponseWriter, request *http.Request) {
	select {
	case h.semaphore <- struct{}{}:
		defer func() { <-h.semaphore }()
	default:
		response.Header().Set("Retry-After", "1")
		http.Error(response, "Git protocol concurrency limit reached", http.StatusTooManyRequests)
		return
	}

	slug, endpoint, ok := parseGitPath(request.URL.Path)
	if !ok {
		http.NotFound(response, request)
		return
	}
	// [REQ-GIT-AUDIT-4.14/6.4] 原行为先查询仓库再校验方法，导致非法方法对存在的
	// HIDDEN/PRIVATE 仓库返回不同结果；新行为先在纯路径层拒绝不支持的协议动作，
	// 再查询仓库并授权，避免用错误方法枚举资源存在性。
	service, operation, requiredScope, mutation, err := resolveGitOperation(request, endpoint)
	if err != nil {
		h.writeProtocolError(response, err)
		h.recordSecurity(request, nil, nil, "HTTP_PROTOCOL_REJECTED", map[string]any{"endpoint": endpoint})
		return
	}
	repo, err := h.store.GetRepositoryBySlug(request.Context(), slug)
	if err != nil {
		http.NotFound(response, request)
		return
	}
	if mutation && request.ContentLength > h.cfg.MaxProtocolRequestBytes {
		http.Error(response, "Git request body exceeds configured limit", http.StatusRequestEntityTooLarge)
		h.recordSecurity(request, &repo, nil, "HTTP_REQUEST_TOO_LARGE", map[string]any{"endpoint": endpoint})
		return
	}

	ip := clientIP(request)
	if allowed, retryAfter := h.failures.Allow(ip); !allowed {
		response.Header().Set("Retry-After", strconv.Itoa(maxSeconds(retryAfter)))
		http.Error(response, "Git authentication temporarily rate limited", http.StatusTooManyRequests)
		h.recordSecurity(request, &repo, nil, "HTTP_AUTH_RATE_LIMITED", map[string]any{"endpoint": endpoint})
		return
	}
	userID, err := h.authenticate(request.Context(), request, requiredScope)
	if err != nil {
		cooldown := h.failures.RecordFailure(ip)
		if cooldown > 0 {
			response.Header().Set("Retry-After", strconv.Itoa(maxSeconds(cooldown)))
			http.Error(response, "Git authentication temporarily rate limited", http.StatusTooManyRequests)
		} else {
			response.Header().Set("WWW-Authenticate", `Basic realm="Git"`)
			http.Error(response, "Git authentication failed", http.StatusUnauthorized)
		}
		h.recordSecurity(request, &repo, nil, "HTTP_AUTH_FAILED", map[string]any{"endpoint": endpoint, "reason": "invalid_or_insufficient_pat"})
		return
	}
	if userID != "" {
		h.failures.RecordSuccess(ip)
	}
	if _, err := h.authorizer.Require(request.Context(), repo, userID, operation); err != nil {
		h.writeAuthorizationFailure(response, request, repo, userID)
		actor := optionalActor(userID)
		h.recordSecurity(request, &repo, actor, "HTTP_PERMISSION_DENIED", map[string]any{"endpoint": endpoint, "operation": operation})
		return
	}

	if mutation {
		request.Body = http.MaxBytesReader(response, request.Body, h.cfg.MaxProtocolRequestBytes)
	}
	repoPath, err := h.manager.EnsureLocal(request.Context(), repo)
	if err != nil {
		http.Error(response, "Repository storage is temporarily unavailable", http.StatusServiceUnavailable)
		return
	}
	// [FIX-GIT-PUSH-RECOVERY-20260816] 首次 push 可能已经更新本地 bare refs，但在共享
	// Object Broker 暂时不可用时被标记为 DEGRADED。Git CLI 的下一次 info/refs 会先比较
	// refs 并直接返回 Everything up-to-date，因而不会重新发送 packfile。仅在真正的
	// receive-pack 写入前恢复；原行为在只读发现请求中同步，造成不必要的写编排。
	if mutation && repo.Status == "DEGRADED" {
		if err := h.manager.Sync(request.Context(), repo); err != nil {
			log.Printf("git repository recovery sync failed repo=%s storage=%s: %v", repo.ID, h.cfg.StorageURL, err)
			http.Error(response, "Repository shared object storage is unavailable; retry later", http.StatusServiceUnavailable)
			return
		}
	}

	var before map[string]string
	if mutation {
		before, err = h.manager.SnapshotRefs(request.Context(), repo)
		if err != nil {
			http.Error(response, "Unable to snapshot repository refs", http.StatusInternalServerError)
			return
		}
	}
	statusCode, headers, stream, command, cancelBackend, err := h.startBackend(request, repoPath, endpoint, userID)
	if err != nil {
		if errors.Is(err, context.DeadlineExceeded) {
			http.Error(response, "Git backend request timed out", http.StatusRequestTimeout)
		} else {
			http.Error(response, "Git backend unavailable", http.StatusServiceUnavailable)
		}
		return
	}
	defer cancelBackend()

	if mutation {
		// receive-pack 的协议回包必须先完整读取并等待子进程退出，随后才可把 refs/object
		// 持久化到共享存储。原行为以 LimitReader 截断后不排空 stdout，超大 side-band 回包
		// 可能让 http-backend 卡住；新行为有界保留、继续排空，避免内存和子进程死锁。
		payload, readErr := readAndDrain(stream, h.cfg.MaxProtocolResponseBytes)
		waitErr := command.Wait()
		if readErr == nil && waitErr != nil && len(payload) > 0 {
			copyHeaders(response.Header(), headers)
			response.WriteHeader(statusCode)
			_, _ = response.Write(payload)
			h.recordAudit(request, repo, userID, "PUSH_REJECTED", map[string]any{"endpoint": endpoint})
			return
		}
		if readErr != nil {
			err = readErr
		} else {
			err = waitErr
		}
		if err == nil {
			err = h.manager.Sync(request.Context(), repo)
		}
		if err != nil {
			log.Printf("git push persistence failed repo=%s storage=%s: %v", repo.ID, h.cfg.StorageURL, err)
			if rollbackErr := h.manager.RestoreRefs(request.Context(), repo, before); rollbackErr != nil {
				log.Printf("git push ref rollback failed repo=%s: %v", repo.ID, rollbackErr)
			}
			_ = h.store.MarkRepositoryStatus(request.Context(), repo.ID, "DEGRADED")
			http.Error(response, "Push accepted locally but shared object persistence failed; retry safely", http.StatusServiceUnavailable)
			return
		}
		after, snapshotErr := h.manager.SnapshotRefs(request.Context(), repo)
		if snapshotErr == nil {
			h.recordPush(request, repo, userID, gitrepo.ChangedRefs(before, after))
		}
		if err := h.manager.UpdateServerInfo(request.Context(), repo); err != nil {
			// Smart HTTP 已经完成对象同步；dumb HTTP 索引刷新失败不应误导客户端把本次
			// push 当作 503 可重试写入，后续 push/运维刷新会恢复静态索引。
			log.Printf("git update-server-info failed after HTTP push repo=%s: %v", repo.ID, err)
		}
		copyHeaders(response.Header(), headers)
		response.WriteHeader(statusCode)
		_, _ = response.Write(payload)
		h.recordAudit(request, repo, userID, "PUSH", map[string]any{"endpoint": endpoint})
		return
	}

	copyHeaders(response.Header(), headers)
	response.WriteHeader(statusCode)
	copyErr := error(nil)
	if request.Method != http.MethodHead {
		_, copyErr = io.Copy(response, stream)
	}
	waitErr := command.Wait()
	if copyErr != nil || waitErr != nil {
		log.Printf("git backend stream failed repo=%s endpoint=%s copy=%v wait=%v", repo.ID, endpoint, copyErr, waitErr)
	}
	h.recordAudit(request, repo, userID, auditOperation(service, endpoint, operation), map[string]any{"endpoint": endpoint})
}

func parseGitPath(requestPath string) (slug, endpoint string, ok bool) {
	trimmed := strings.TrimPrefix(requestPath, "/git/")
	marker := strings.Index(trimmed, ".git")
	if marker <= 0 {
		return "", "", false
	}
	slug = trimmed[:marker]
	remainder := trimmed[marker+4:]
	if !slugPattern.MatchString(slug) || !strings.HasPrefix(remainder, "/") {
		return "", "", false
	}
	endpoint = strings.TrimPrefix(remainder, "/")
	if endpoint == "" || strings.Contains(endpoint, "\\") || strings.Contains(endpoint, "..") || strings.ContainsRune(endpoint, 0) {
		return "", "", false
	}
	// [REQ-GIT-HTTP-2.11/2.31] 标准 dumb HTTP 的 loose object 地址为
	// objects/xx/rest；为兼容需求文档和调试客户端的 objects/{object-id} 形式，
	// 仅对完整 SHA-1/SHA-256 哈希规范化到标准路径，不接受任意文件名。
	if matched := directObjectPattern.FindStringSubmatch(endpoint); len(matched) == 2 {
		hash := matched[1]
		endpoint = "objects/" + hash[:2] + "/" + hash[2:]
	}
	return slug, endpoint, true
}

func resolveGitOperation(request *http.Request, endpoint string) (string, auth.Operation, string, bool, error) {
	if endpoint == "info/refs" {
		if request.Method != http.MethodGet {
			return "", "", "", false, &protocolRequestError{status: http.StatusMethodNotAllowed, message: "Git reference discovery requires GET", allow: http.MethodGet}
		}
		services := request.URL.Query()["service"]
		if len(services) != 1 {
			return "", "", "", false, &protocolRequestError{status: http.StatusBadRequest, message: "Git reference discovery requires exactly one service parameter"}
		}
		switch services[0] {
		case "git-upload-pack":
			return services[0], auth.Fetch, "read_repository", false, nil
		case "git-receive-pack":
			return services[0], auth.Push, "write_repository", false, nil
		default:
			return "", "", "", false, &protocolRequestError{status: http.StatusBadRequest, message: "unsupported Git service"}
		}
	}
	if endpoint == "git-upload-pack" || endpoint == "git-receive-pack" {
		if request.Method != http.MethodPost {
			return "", "", "", false, &protocolRequestError{status: http.StatusMethodNotAllowed, message: "Git RPC requires POST", allow: http.MethodPost}
		}
		if err := validateRPCContentType(request, endpoint); err != nil {
			return "", "", "", false, err
		}
		if endpoint == "git-upload-pack" {
			return endpoint, auth.Fetch, "read_repository", false, nil
		}
		return endpoint, auth.Push, "write_repository", true, nil
	}
	if !isSafeReadEndpoint(endpoint) {
		return "", "", "", false, &protocolRequestError{status: http.StatusNotFound, message: "Git protocol endpoint not found"}
	}
	if request.Method != http.MethodGet && request.Method != http.MethodHead {
		return "", "", "", false, &protocolRequestError{status: http.StatusMethodNotAllowed, message: "Git dumb HTTP endpoints are read-only", allow: "GET, HEAD"}
	}
	return "dumb-http", auth.Fetch, "read_repository", false, nil
}

func validateRPCContentType(request *http.Request, endpoint string) error {
	contentType, _, err := mime.ParseMediaType(request.Header.Get("Content-Type"))
	if err != nil || contentType == "" {
		return &protocolRequestError{status: http.StatusUnsupportedMediaType, message: "Git RPC Content-Type is required"}
	}
	expected := "application/x-" + endpoint + "-request"
	if contentType != expected {
		return &protocolRequestError{status: http.StatusUnsupportedMediaType, message: "unsupported Git RPC Content-Type"}
	}
	return nil
}

func isSafeReadEndpoint(endpoint string) bool {
	switch endpoint {
	case "HEAD", "description", "objects/info/alternates", "objects/info/http-alternates", "objects/info/packs", "objects/info/commit-graph":
		return true
	}
	if packPathPattern.MatchString(endpoint) || looseObjectPattern.MatchString(endpoint) {
		return true
	}
	if strings.HasPrefix(endpoint, "objects/info/commit-graphs/") || strings.HasPrefix(endpoint, "objects/info/commit-graph/") {
		rest := strings.TrimPrefix(strings.TrimPrefix(endpoint, "objects/info/commit-graphs/"), "objects/info/commit-graph/")
		return rest != "" && !strings.Contains(rest, "/") && !strings.Contains(rest, "..")
	}
	for _, prefix := range []string{"refs/heads/", "refs/tags/"} {
		if strings.HasPrefix(endpoint, prefix) {
			return gitrepo.ValidateRefName(strings.TrimPrefix(endpoint, prefix)) == nil
		}
	}
	return false
}

func (h *HTTPHandler) authenticate(ctx context.Context, request *http.Request, requiredScope string) (string, error) {
	username, password, ok := request.BasicAuth()
	if !ok {
		return "", nil
	}

	// [FIX-GIT-CLI-PAT-20260816] GitHub 风格 HTTPS 认证要求客户端发送 Basic Auth，
	// 用户名可以是账号名或 x-access-token，密码是完整 PAT；Git Service 不把用户名
	// 当作平台 userID。为兼容部分 CI/凭证助手发送的 Basic(PAT, "") 形式，只有当
	// 密码为空且用户名具有 pcd_pat_ 前缀时才将用户名作为 PAT 候选，不改变标准路径。
	username = strings.TrimSpace(username)
	password = strings.TrimSpace(password)
	tokens := []string{password}
	if password == "" && strings.HasPrefix(username, "pcd_pat_") {
		tokens = append(tokens, username)
	}
	var lastErr error
	var scopeErr error
	for _, token := range tokens {
		if token == "" {
			continue
		}
		userID, scopes, err := h.store.AuthenticatePAT(ctx, token)
		if err != nil {
			lastErr = err
			continue
		}
		if hasScope(scopes, requiredScope) {
			return userID, nil
		}
		scopeErr = fmt.Errorf("PAT scope %s is required", requiredScope)
	}
	if scopeErr != nil {
		return "", scopeErr
	}
	if lastErr != nil {
		return "", lastErr
	}
	return "", fmt.Errorf("PAT is required as the Basic Auth password")
}

func hasScope(scopes []string, required string) bool {
	for _, scope := range scopes {
		if scope == "api" || scope == required || (required == "read_repository" && scope == "write_repository") {
			return true
		}
	}
	return false
}

func (h *HTTPHandler) startBackend(request *http.Request, repoPath, endpoint, userID string) (int, http.Header, io.Reader, *exec.Cmd, context.CancelFunc, error) {
	ctx, cancel := context.WithTimeout(request.Context(), h.cfg.GitCommandTimeout)
	cmd := exec.CommandContext(ctx, h.cfg.GitBinary, "http-backend")
	pathInfo := "/" + filepathBase(repoPath) + "/" + endpoint
	cmd.Env = append(os.Environ(),
		"GIT_PROJECT_ROOT="+h.cfg.RepoRoot,
		"GIT_HTTP_EXPORT_ALL=1",
		"PATH_INFO="+pathInfo,
		"QUERY_STRING="+request.URL.RawQuery,
		"REQUEST_METHOD="+request.Method,
		"CONTENT_TYPE="+request.Header.Get("Content-Type"),
		"CONTENT_LENGTH="+strconv.FormatInt(request.ContentLength, 10),
		"REMOTE_USER="+userID,
		"REMOTE_ADDR="+clientIP(request),
		"SERVER_PROTOCOL="+request.Proto,
		"HTTP_GIT_PROTOCOL="+request.Header.Get("Git-Protocol"),
		"HTTP_RANGE="+request.Header.Get("Range"),
		"HTTP_ACCEPT="+request.Header.Get("Accept"),
		"HTTP_USER_AGENT="+request.Header.Get("User-Agent"),
	)
	cmd.Stdin = request.Body
	stdout, err := cmd.StdoutPipe()
	if err != nil {
		cancel()
		return 0, nil, nil, nil, nil, err
	}
	var stderr limitedBuffer
	cmd.Stderr = &stderr
	if err := cmd.Start(); err != nil {
		cancel()
		return 0, nil, nil, nil, nil, err
	}
	reader := bufio.NewReaderSize(stdout, 64*1024)
	mimeHeader, err := textproto.NewReader(reader).ReadMIMEHeader()
	if err != nil {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		cancel()
		if ctx.Err() != nil {
			return 0, nil, nil, nil, nil, ctx.Err()
		}
		return 0, nil, nil, nil, nil, fmt.Errorf("read Git backend headers: %w", err)
	}
	headers := http.Header(mimeHeader)
	statusCode := http.StatusOK
	if status := headers.Get("Status"); status != "" {
		headers.Del("Status")
		fields := strings.Fields(status)
		if len(fields) > 0 {
			if code, parseErr := strconv.Atoi(fields[0]); parseErr == nil {
				statusCode = code
			}
		}
	}
	return statusCode, headers, reader, cmd, cancel, nil
}

type limitedBuffer struct {
	bytes.Buffer
}

func (b *limitedBuffer) Write(value []byte) (int, error) {
	const maxStderrBytes = 256 * 1024
	remaining := maxStderrBytes - b.Len()
	if remaining > 0 {
		if len(value) > remaining {
			_, _ = b.Buffer.Write(value[:remaining])
		} else {
			_, _ = b.Buffer.Write(value)
		}
	}
	return len(value), nil
}

func readAndDrain(reader io.Reader, limit int64) ([]byte, error) {
	if limit < 1 {
		limit = 32 * 1024 * 1024
	}
	var payload bytes.Buffer
	_, err := io.Copy(&payload, io.LimitReader(reader, limit+1))
	if err != nil {
		return nil, err
	}
	if int64(payload.Len()) > limit {
		_, _ = io.Copy(io.Discard, reader)
		return nil, fmt.Errorf("Git protocol response exceeds configured limit")
	}
	return payload.Bytes(), nil
}

func filepathBase(value string) string {
	value = strings.TrimRight(value, string(os.PathSeparator))
	if index := strings.LastIndex(value, string(os.PathSeparator)); index >= 0 {
		return value[index+1:]
	}
	return value
}

func copyHeaders(destination, source http.Header) {
	for name, values := range source {
		for _, value := range values {
			destination.Add(name, value)
		}
	}
	destination.Set("X-Content-Type-Options", "nosniff")
	destination.Set("Referrer-Policy", "no-referrer")
	destination.Set("X-Frame-Options", "DENY")
}

func (h *HTTPHandler) writeProtocolError(response http.ResponseWriter, err error) {
	var requestErr *protocolRequestError
	if errors.As(err, &requestErr) {
		if requestErr.allow != "" {
			response.Header().Set("Allow", requestErr.allow)
		}
		http.Error(response, requestErr.message, requestErr.status)
		return
	}
	http.Error(response, "invalid Git protocol request", http.StatusBadRequest)
}

func (h *HTTPHandler) writeAuthorizationFailure(response http.ResponseWriter, request *http.Request, repo domain.Repository, userID string) {
	if userID == "" {
		response.Header().Set("WWW-Authenticate", `Basic realm="Git"`)
		http.Error(response, "Authentication required", http.StatusUnauthorized)
		return
	}
	if auth.IsConcealed(repo) {
		http.NotFound(response, request)
		return
	}
	http.Error(response, "Repository permission denied", http.StatusForbidden)
}

func auditOperation(service, endpoint string, operation auth.Operation) string {
	if endpoint == "info/refs" {
		if operation == auth.Push {
			return "PUSH_DISCOVERY"
		}
		return "FETCH_DISCOVERY"
	}
	if service == "dumb-http" {
		return "DUMB_FETCH"
	}
	if operation == auth.Fetch {
		return "FETCH"
	}
	return "PUSH"
}

func clientIP(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err == nil {
		return host
	}
	return request.RemoteAddr
}

func maxSeconds(duration time.Duration) int {
	seconds := int(duration.Round(time.Second).Seconds())
	if seconds < 1 {
		return 1
	}
	return seconds
}

func optionalActor(userID string) *string {
	if userID == "" {
		return nil
	}
	return &userID
}

func (h *HTTPHandler) recordPush(request *http.Request, repo domain.Repository, userID string, changed map[string]map[string]string) {
	if len(changed) == 0 {
		return
	}
	eventID := fmt.Sprintf("%d-%s", time.Now().UnixNano(), repo.ID)
	bindings, _ := h.store.ListWorkflowBindings(request.Context(), repo.ID)
	payload := map[string]any{
		"specversion": "1.0", "id": eventID, "source": "pcd://git-service/repos/" + repo.ID,
		"type": "pcd.git.push.completed.v1", "time": time.Now().UTC().Format(time.RFC3339Nano),
		"subject": repo.ID, "datacontenttype": "application/json",
		"data": map[string]any{"repository_id": repo.ID, "space_id": repo.SpaceID, "actor_id": userID,
			"changed_refs": changed, "workflow_bindings": bindings},
	}
	if err := h.store.InsertOutbox(request.Context(), repo.ID, "pcd.git.push.completed.v1", h.cfg.EventExchange, "git.push.completed", payload); err != nil {
		log.Printf("insert Git push outbox failed repo=%s: %v", repo.ID, err)
	}
}

func (h *HTTPHandler) recordAudit(request *http.Request, repo domain.Repository, userID, operation string, detail any) {
	if err := h.store.InsertAudit(request.Context(), repo.ID, optionalActor(userID), operation, clientIP(request), request.Header.Get("X-Trace-Id"), detail); err != nil {
		log.Printf("insert Git audit failed repo=%s: %v", repo.ID, err)
	}
}

func (h *HTTPHandler) recordSecurity(request *http.Request, repo *domain.Repository, actor *string, operation string, detail any) {
	var repoID *string
	if repo != nil {
		repoID = &repo.ID
	}
	if err := h.store.InsertSecurityAudit(request.Context(), repoID, actor, operation, clientIP(request), detail); err != nil {
		log.Printf("insert Git security audit failed operation=%s: %v", operation, err)
	}
}
