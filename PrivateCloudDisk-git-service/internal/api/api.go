package api

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"

	"github.com/google/uuid"
	"golang.org/x/crypto/ssh"

	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/config"
	"privateclouddisk/git-service/internal/domain"
	"privateclouddisk/git-service/internal/gitrepo"
	"privateclouddisk/git-service/internal/platform"
	"privateclouddisk/git-service/internal/secretbox"
	"privateclouddisk/git-service/internal/store"
)

type API struct {
	cfg        config.Config
	store      *store.Store
	manager    *gitrepo.Manager
	platform   *platform.Client
	authorizer *auth.Authorizer
	secretBox  *secretbox.Box
	mux        *http.ServeMux
}

func New(cfg config.Config, dataStore *store.Store, manager *gitrepo.Manager, platformClient *platform.Client, authorizer *auth.Authorizer) *API {
	box, err := secretbox.New(cfg.InternalServiceToken)
	if err != nil {
		panic(err)
	}
	api := &API{cfg: cfg, store: dataStore, manager: manager, platform: platformClient, authorizer: authorizer, secretBox: box, mux: http.NewServeMux()}
	api.routes()
	return api
}

func (a *API) routes() {
	a.mux.HandleFunc("GET /health", a.health)
	a.mux.HandleFunc("GET /ready", a.ready)
	a.mux.HandleFunc("POST /git/repos", a.createRepository)
	a.mux.HandleFunc("GET /git/repos/{repoID}", a.getRepository)
	a.mux.HandleFunc("GET /git/repos/stars", a.listMyStars)
	a.mux.HandleFunc("GET /git/repos/forks", a.listMyForks)
	a.mux.HandleFunc("PUT /git/repos/{repoID}/star", a.starRepository)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}/star", a.unstarRepository)
	a.mux.HandleFunc("GET /git/repos/{repoID}/star/status", a.repositoryStarStatus)
	a.mux.HandleFunc("POST /git/repos/{repoID}/fork", a.forkRepository)
	a.mux.HandleFunc("PATCH /git/repos/{repoID}", a.updateRepository)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}", a.deleteRepository)
	a.mux.HandleFunc("GET /git/repos/{repoID}/branches", a.listBranches)
	a.mux.HandleFunc("POST /git/repos/{repoID}/branches", a.createBranch)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}/branches/{name}", a.deleteBranch)
	a.mux.HandleFunc("GET /git/repos/{repoID}/tags", a.listTags)
	a.mux.HandleFunc("POST /git/repos/{repoID}/tags", a.createTag)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}/tags/{name}", a.deleteTag)
	a.mux.HandleFunc("GET /git/repos/{repoID}/commits", a.listCommits)
	a.mux.HandleFunc("GET /git/repos/{repoID}/tree", a.listTree)
	a.mux.HandleFunc("GET /git/repos/{repoID}/blob", a.readBlob)
	a.mux.HandleFunc("GET /git/repos/{repoID}/raw", a.readRawBlob)
	a.mux.HandleFunc("GET /git/repos/{repoID}/archive", a.downloadArchive)
	a.mux.HandleFunc("GET /git/repos/{repoID}/insights", a.repositoryInsights)
	a.mux.HandleFunc("GET /git/repos/{repoID}/readme", a.readREADME)
	a.mux.HandleFunc("GET /git/repos/{repoID}/diff", a.diff)
	a.mux.HandleFunc("GET /git/repos/{repoID}/blame", a.blame)
	a.mux.HandleFunc("POST /git/repos/{repoID}/merge", a.createMergeRequest)
	a.mux.HandleFunc("GET /git/repos/{repoID}/merge-requests", a.listMergeRequests)
	a.mux.HandleFunc("POST /git/repos/{repoID}/merge-requests", a.createMergeRequest)
	a.mux.HandleFunc("GET /git/repos/{repoID}/merge-requests/{mrID}", a.getMergeRequest)
	a.mux.HandleFunc("GET /git/repos/{repoID}/merge-requests/{mrID}/comments", a.listMergeRequestComments)
	a.mux.HandleFunc("POST /git/repos/{repoID}/merge-requests/{mrID}/comments", a.commentMergeRequest)
	a.mux.HandleFunc("POST /git/repos/{repoID}/merge-requests/{mrID}/approve", a.approveMergeRequest)
	a.mux.HandleFunc("POST /git/repos/{repoID}/merge-requests/{mrID}/merge", a.mergeMergeRequest)
	a.mux.HandleFunc("GET /git/repos/{repoID}/permissions", a.listPermissions)
	a.mux.HandleFunc("PUT /git/repos/{repoID}/permissions/{subjectID}", a.upsertPermission)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}/permissions/{subjectID}", a.deletePermission)
	a.mux.HandleFunc("PUT /git/repos/{repoID}/branch-protections", a.upsertBranchProtection)
	a.mux.HandleFunc("GET /git/repos/{repoID}/webhooks", a.listWebhooks)
	a.mux.HandleFunc("POST /git/repos/{repoID}/webhooks", a.createWebhook)
	a.mux.HandleFunc("DELETE /git/repos/{repoID}/webhooks/{webhookID}", a.deleteWebhook)
	a.mux.HandleFunc("GET /git/repos/{repoID}/workflow-bindings", a.listWorkflowBindings)
	a.mux.HandleFunc("POST /git/repos/{repoID}/workflow-bindings", a.createWorkflowBinding)
	a.mux.HandleFunc("GET /git/repos/{repoID}/audit", a.listAudit)
	a.mux.HandleFunc("GET /git/credentials/pats", a.listPATs)
	a.mux.HandleFunc("POST /git/credentials/pats", a.createPAT)
	a.mux.HandleFunc("DELETE /git/credentials/pats/{tokenID}", a.revokePAT)
	a.mux.HandleFunc("GET /git/credentials/ssh-keys", a.listSSHKeys)
	a.mux.HandleFunc("POST /git/credentials/ssh-keys", a.createSSHKey)
	a.mux.HandleFunc("DELETE /git/credentials/ssh-keys/{keyID}", a.revokeSSHKey)
}

func (a *API) ServeHTTP(response http.ResponseWriter, request *http.Request) {
	// 兼容需求文档中的 /api/git/* 直连形式；经 Gateway 时 /api/v1 已被 StripPrefix。
	if strings.HasPrefix(request.URL.Path, "/api/git/") {
		clone := request.Clone(request.Context())
		clone.URL.Path = strings.TrimPrefix(request.URL.Path, "/api")
		request = clone
	}
	// 原行为将 GET /git/repos/by-space/{spaceID} 注册到 Go 1.22+ ServeMux。
	// 该模式会与 GET /git/repos/{repoID}/branches 在
	// /git/repos/by-space/branches 处重叠，Go 1.24 会在注册阶段直接 panic。
	// 新行为保留原公开 URL，仅对 by-space 的单路径段在 ServeMux 前显式分发；
	// 其他仓库路由仍由 ServeMux 负责，避免改变已有 API 的路径和参数语义。
	if request.Method == http.MethodGet {
		const bySpacePrefix = "/git/repos/by-space/"
		if strings.HasPrefix(request.URL.Path, bySpacePrefix) {
			spaceID := strings.TrimPrefix(request.URL.Path, bySpacePrefix)
			if spaceID != "" && !strings.Contains(spaceID, "/") {
				clone := request.Clone(request.Context())
				clone.SetPathValue("spaceID", spaceID)
				a.getRepositoryBySpace(response, clone)
				return
			}
		}
	}
	a.mux.ServeHTTP(response, request)
}

func (a *API) health(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, 200, map[string]any{"status": "healthy", "service": "git-service"})
}

func (a *API) ready(response http.ResponseWriter, request *http.Request) {
	if err := a.store.Ping(request.Context()); err != nil {
		writeError(response, 503, "Git 数据库尚未就绪")
		return
	}
	writeJSON(response, 200, map[string]any{"status": "ready"})
}

type createRepositoryRequest struct {
	SpaceID       string `json:"spaceId"`
	Name          string `json:"name"`
	Description   string `json:"description"`
	Visibility    string `json:"visibility"`
	DefaultBranch string `json:"defaultBranch"`
}

type updateRepositoryRequest struct {
	Name          string  `json:"name"`
	Description   *string `json:"description"`
	Visibility    string  `json:"visibility"`
	DefaultBranch string  `json:"defaultBranch"`
}

func (a *API) createRepository(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	var body createRepositoryRequest
	if !decodeJSON(response, request, &body) || !validUUID(body.SpaceID) {
		if body.SpaceID != "" && !validUUID(body.SpaceID) {
			writeError(response, 422, "spaceId 格式无效")
		}
		return
	}
	if existing, err := a.store.GetRepositoryBySpace(request.Context(), body.SpaceID); err == nil {
		a.decorateWithStats(request.Context(), &existing, userID)
		writeData(response, 200, existing)
		return
	} else if !errors.Is(err, store.ErrNotFound) {
		writeError(response, 500, "查询空间关联仓库失败")
		return
	}
	space, err := a.platform.Authorization(request.Context(), body.SpaceID, userID)
	if err != nil || !space.Active || space.ResourceType != "git" || space.OwnerID != userID {
		writeError(response, 403, "仅 Git 公开空间所有者可以创建仓库")
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" || len([]rune(body.Name)) > 128 {
		writeError(response, 422, "仓库名称长度必须为 1-128 个字符")
		return
	}
	if body.DefaultBranch == "" {
		body.DefaultBranch = "main"
	}
	if err := gitrepo.ValidateRefName(body.DefaultBranch); err != nil {
		writeError(response, 422, "默认分支名称无效")
		return
	}
	// [REQ-GIT-AUDIT-4.1/4.2] 原创建接口没有 Git 仓库可见性，只能间接复用空间的
	// 文件公开开关；新行为在 Git 资源内部保存 PUBLIC/HIDDEN/PRIVATE，Space 仍保持
	// public + resource_type=git。影响仅为 Git 协议发现和拉取授权，不改变空间类型。
	visibility, ok := normalizeRepositoryVisibility(body.Visibility)
	if !ok {
		writeError(response, 422, "Git 仓库可见性必须为 PUBLIC、HIDDEN 或 PRIVATE")
		return
	}
	slug, err := a.uniqueSlug(request.Context(), body.Name)
	if err != nil {
		writeError(response, 500, "生成仓库地址失败")
		return
	}
	repo := domain.Repository{ID: uuid.NewString(), SpaceID: body.SpaceID, OwnerID: userID, Name: body.Name,
		Slug: slug, Description: body.Description, Visibility: visibility, DefaultBranch: body.DefaultBranch, HashAlgorithm: "sha1"}
	repo, err = a.store.CreateRepository(request.Context(), repo)
	if err != nil {
		writeError(response, 409, "该空间已关联 Git 仓库或仓库名称冲突")
		return
	}
	if err := a.manager.CreateBare(request.Context(), repo); err != nil {
		_ = a.store.SoftDeleteRepository(request.Context(), repo.ID)
		writeError(response, 500, "初始化 Git 仓库失败")
		return
	}
	a.decorateWithStats(request.Context(), &repo, userID)
	a.audit(request, repo, userID, "REPOSITORY_CREATE", map[string]any{"space_id": body.SpaceID})
	writeData(response, 201, repo)
}

func (a *API) getRepositoryBySpace(response http.ResponseWriter, request *http.Request) {
	repo, err := a.store.GetRepositoryBySpace(request.Context(), request.PathValue("spaceID"))
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(response, 404, "Git 仓库尚未初始化")
		} else {
			writeError(response, 500, "查询空间关联仓库失败")
		}
		return
	}
	userID := optionalUser(request)
	if _, err := a.authorizer.Require(request.Context(), repo, userID, auth.Metadata); err != nil {
		writeRepositoryAuthorizationError(response, repo, userID, "无权浏览该 Git 仓库")
		return
	}
	a.decorateWithStats(request.Context(), &repo, userID)
	writeData(response, 200, repo)
}

func (a *API) getRepository(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	a.decorateWithStats(request.Context(), &repo, userID)
	a.audit(request, repo, userID, "REPOSITORY_VIEW", map[string]any{})
	writeData(response, 200, repo)
}

func (a *API) updateRepository(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	var body updateRepositoryRequest
	if !decodeJSON(response, request, &body) {
		return
	}
	if body.DefaultBranch != "" {
		if err := gitrepo.ValidateRefName(body.DefaultBranch); err != nil {
			writeError(response, 422, "默认分支名称无效")
			return
		}
	}
	visibility, visibilityValid := normalizeRepositoryVisibility(body.Visibility)
	if !visibilityValid {
		writeError(response, 422, "Git 仓库可见性必须为 PUBLIC、HIDDEN 或 PRIVATE")
		return
	}
	if body.Visibility == "" {
		visibility = ""
	}
	// [REQ-GIT-AUDIT-4.1/4.2] 原 PATCH 复用创建 DTO，省略 description 也会传入空
	// 字符串并覆盖旧描述；新 DTO 使用指针区分“未更新”和“明确清空”，因此单独切换
	// PUBLIC/HIDDEN/PRIVATE 不会丢失仓库说明。影响范围仅为仓库 PATCH 的部分更新语义。
	if err := a.store.UpdateRepository(request.Context(), repo.ID, body.Name, body.Description, visibility, body.DefaultBranch); err != nil {
		writeError(response, 500, "更新仓库失败")
		return
	}
	repo, _ = a.store.GetRepository(request.Context(), repo.ID)
	a.decorateWithStats(request.Context(), &repo, userID)
	a.audit(request, repo, userID, "REPOSITORY_UPDATE", body)
	writeData(response, 200, repo)
}

func normalizeRepositoryVisibility(value string) (string, bool) {
	value = strings.ToUpper(strings.TrimSpace(value))
	if value == "" {
		return domain.RepositoryVisibilityPublic, true
	}
	switch value {
	case domain.RepositoryVisibilityPublic, domain.RepositoryVisibilityHidden, domain.RepositoryVisibilityPrivate:
		return value, true
	default:
		return "", false
	}
}

func (a *API) deleteRepository(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	if err := a.store.SoftDeleteRepository(request.Context(), repo.ID); err != nil {
		writeError(response, 500, "删除仓库失败")
		return
	}
	if err := a.manager.DeleteLocal(repo); err != nil {
		writeError(response, 500, "仓库已下线，但本地缓存清理失败")
		return
	}
	a.audit(request, repo, userID, "REPOSITORY_DELETE", map[string]any{})
	response.WriteHeader(http.StatusNoContent)
}

type forkRepositoryRequest struct {
	TargetSpaceID string `json:"targetSpaceId"`
	Name          string `json:"name"`
	Visibility    string `json:"visibility"`
}

// starRepository is idempotent at the database layer. A duplicate click cannot
// inflate the count because (repo_id,user_id) is the source-of-truth key.
func (a *API) starRepository(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	if err := a.store.StarRepository(request.Context(), repo.ID, userID); err != nil {
		writeError(response, 500, "Star 仓库失败")
		return
	}
	a.audit(request, repo, userID, "REPOSITORY_STAR", map[string]any{"repo_id": repo.ID})
	a.writeSocialStats(response, request, repo.ID, userID)
}

func (a *API) unstarRepository(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	if err := a.store.UnstarRepository(request.Context(), repo.ID, userID); err != nil {
		writeError(response, 500, "取消 Star 失败")
		return
	}
	a.audit(request, repo, userID, "REPOSITORY_UNSTAR", map[string]any{"repo_id": repo.ID})
	a.writeSocialStats(response, request, repo.ID, userID)
}

func (a *API) repositoryStarStatus(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	a.writeSocialStats(response, request, repo.ID, userID)
}

func (a *API) writeSocialStats(response http.ResponseWriter, request *http.Request, repoID, userID string) {
	stats, err := a.store.GetRepositorySocialStats(request.Context(), repoID, userID)
	if err != nil {
		writeError(response, 500, "查询仓库社交统计失败")
		return
	}
	writeData(response, http.StatusOK, stats)
}

func (a *API) forkRepository(response http.ResponseWriter, request *http.Request) {
	source, _, ok := a.requireRepo(response, request, auth.Fetch)
	if !ok {
		return
	}
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	var body forkRepositoryRequest
	if !decodeJSON(response, request, &body) || !validUUID(body.TargetSpaceID) {
		if body.TargetSpaceID != "" && !validUUID(body.TargetSpaceID) {
			writeError(response, 422, "targetSpaceId 格式无效")
		}
		return
	}
	targetSpace, err := a.platform.Authorization(request.Context(), body.TargetSpaceID, userID)
	if err != nil || !targetSpace.Active || targetSpace.ResourceType != "git" || (targetSpace.OwnerID != userID && targetSpace.PermissionLevel != domain.PermissionAdmin) {
		writeError(response, http.StatusForbidden, "目标空间不是可写的 Git 空间")
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" {
		body.Name = source.Name + "-fork"
	}
	if len([]rune(body.Name)) > 128 {
		writeError(response, 422, "Fork 仓库名称长度必须为 1-128 个字符")
		return
	}
	visibility := domain.RepositoryVisibilityPrivate
	if body.Visibility != "" {
		var valid bool
		visibility, valid = normalizeRepositoryVisibility(body.Visibility)
		if !valid {
			writeError(response, 422, "Git 仓库可见性必须为 PUBLIC、HIDDEN 或 PRIVATE")
			return
		}
	}
	slug, err := a.uniqueSlug(request.Context(), body.Name)
	if err != nil {
		writeError(response, 500, "生成 Fork 仓库地址失败")
		return
	}
	target := domain.Repository{ID: uuid.NewString(), SpaceID: body.TargetSpaceID, OwnerID: userID, Name: body.Name, Slug: slug, Description: source.Description, Visibility: visibility, DefaultBranch: source.DefaultBranch, HashAlgorithm: source.HashAlgorithm}
	target, err = a.store.CreateRepository(request.Context(), target)
	if err != nil {
		writeError(response, 409, "目标空间已关联 Git 仓库或仓库名称冲突")
		return
	}
	if err := a.manager.Fork(request.Context(), source, target); err != nil {
		_ = a.store.SoftDeleteRepository(request.Context(), target.ID)
		writeError(response, 503, "Fork 数据复制失败，请稍后重试")
		return
	}
	if err := a.store.CreateForkRecord(request.Context(), domain.RepositoryFork{RepoID: source.ID, ForkedRepoID: target.ID, UserID: userID}); err != nil {
		_ = a.store.SoftDeleteRepository(request.Context(), target.ID)
		_ = a.manager.DeleteLocal(target)
		writeError(response, 500, "保存 Fork 关系失败")
		return
	}
	a.decorateWithStats(request.Context(), &target, userID)
	a.audit(request, source, userID, "REPOSITORY_FORK", map[string]any{"forked_repo_id": target.ID, "target_space_id": target.SpaceID})
	writeData(response, http.StatusCreated, target)
}

func (a *API) listMyStars(response http.ResponseWriter, request *http.Request) {
	a.listSocialRepositories(response, request, true)
}
func (a *API) listMyForks(response http.ResponseWriter, request *http.Request) {
	a.listSocialRepositories(response, request, false)
}

func (a *API) listSocialRepositories(response http.ResponseWriter, request *http.Request, stars bool) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	page := max(1, queryInt(request, "page", 1))
	size := min(100, max(1, queryInt(request, "size", 30)))
	var ids []string
	var err error
	if stars {
		ids, err = a.store.ListStarredRepositoryIDs(request.Context(), userID, size, (page-1)*size)
	} else {
		ids, err = a.store.ListForkedRepositoryIDs(request.Context(), userID, size, (page-1)*size)
	}
	if err != nil {
		writeError(response, 500, "查询仓库列表失败")
		return
	}
	items := make([]domain.Repository, 0, len(ids))
	for _, id := range ids {
		if repo, getErr := a.store.GetRepository(request.Context(), id); getErr == nil {
			a.decorateWithStats(request.Context(), &repo, userID)
			items = append(items, repo)
		}
	}
	writeData(response, http.StatusOK, items)
}

func (a *API) requireRepo(response http.ResponseWriter, request *http.Request, operation auth.Operation) (domain.Repository, string, bool) {
	repo, err := a.store.GetRepository(request.Context(), request.PathValue("repoID"))
	if err != nil {
		if errors.Is(err, store.ErrNotFound) {
			writeError(response, 404, "Git 仓库不存在")
		} else {
			writeError(response, 500, "查询 Git 仓库失败")
		}
		return repo, "", false
	}
	userID := optionalUser(request)
	if operation == auth.Push || operation == auth.Admin {
		var ok bool
		userID, ok = requireUser(response, request)
		if !ok {
			return repo, "", false
		}
	}
	if _, err := a.authorizer.Require(request.Context(), repo, userID, operation); err != nil {
		writeRepositoryAuthorizationError(response, repo, userID, "无权执行该仓库操作")
		return repo, userID, false
	}
	return repo, userID, true
}

// writeRepositoryAuthorizationError 让管理 API 与 Git HTTP/SSH 使用同一隐藏资源策略。
// [REQ-GIT-AUDIT-4.2/4.14] 原 API 始终返回 403，已认证用户可通过 repoID 枚举
// HIDDEN/PRIVATE 仓库；新行为对无仓库权限的隐藏资源返回 404，PUBLIC 资源仍返回
// 明确的 403，便于前端展示授权错误。影响范围仅为 Git 管理 API 的拒绝状态码。
func writeRepositoryAuthorizationError(response http.ResponseWriter, repo domain.Repository, userID, publicMessage string) {
	if userID != "" && auth.IsConcealed(repo) {
		writeError(response, http.StatusNotFound, "Git 仓库不存在")
		return
	}
	writeError(response, http.StatusForbidden, publicMessage)
}

func (a *API) decorate(repo *domain.Repository) {
	repo.HTTPCloneURL = a.cfg.HTTPCloneBaseURL + "/" + repo.Slug + ".git"
	if a.cfg.SSHClonePort == 22 {
		repo.SSHCloneURL = "git@" + a.cfg.SSHCloneHost + ":" + repo.Slug + ".git"
	} else {
		repo.SSHCloneURL = fmt.Sprintf("ssh://git@%s:%d/%s.git", a.cfg.SSHCloneHost, a.cfg.SSHClonePort, repo.Slug)
	}
}

func (a *API) decorateWithStats(ctx context.Context, repo *domain.Repository, userID string) {
	a.decorate(repo)
	if stats, err := a.store.GetRepositorySocialStats(ctx, repo.ID, userID); err == nil {
		repo.Starred = stats.Starred
		repo.StarCount = stats.StarCount
		repo.ForkCount = stats.ForkCount
	}
}

var slugInvalid = regexp.MustCompile(`[^a-z0-9._-]+`)

func (a *API) uniqueSlug(ctx context.Context, name string) (string, error) {
	base := strings.ToLower(strings.TrimSpace(name))
	base = slugInvalid.ReplaceAllString(base, "-")
	base = strings.Trim(base, "-._")
	if base == "" {
		base = "repository"
	}
	if len(base) > 160 {
		base = base[:160]
	}
	for index := 0; index < 1000; index++ {
		candidate := base
		if index > 0 {
			candidate = fmt.Sprintf("%s-%d", base, index+1)
		}
		exists, err := a.store.SlugExists(ctx, candidate)
		if err != nil {
			return "", err
		}
		if !exists {
			return candidate, nil
		}
	}
	return "", errors.New("unable to allocate repository slug")
}

func optionalUser(request *http.Request) string {
	value := strings.TrimSpace(request.Header.Get("X-User-Id"))
	if validUUID(value) {
		return value
	}
	return ""
}

func requireUser(response http.ResponseWriter, request *http.Request) (string, bool) {
	userID := optionalUser(request)
	if userID == "" {
		writeError(response, 401, "缺少已认证用户身份")
		return "", false
	}
	return userID, true
}

func validUUID(value string) bool { _, err := uuid.Parse(value); return err == nil }

func decodeJSON(response http.ResponseWriter, request *http.Request, target any) bool {
	decoder := json.NewDecoder(io.LimitReader(request.Body, 1024*1024))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(target); err != nil {
		writeError(response, 400, "请求体格式无效: "+err.Error())
		return false
	}
	return true
}

func writeData(response http.ResponseWriter, statusCode int, data any) {
	writeJSON(response, statusCode, map[string]any{"code": 200, "data": data})
}

func writeError(response http.ResponseWriter, statusCode int, message string) {
	writeJSON(response, statusCode, map[string]any{"code": statusCode, "message": message})
}

func writeJSON(response http.ResponseWriter, statusCode int, value any) {
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.Header().Set("X-Content-Type-Options", "nosniff")
	response.WriteHeader(statusCode)
	_ = json.NewEncoder(response).Encode(value)
}

func queryInt(request *http.Request, name string, fallback int) int {
	value, err := strconv.Atoi(request.URL.Query().Get(name))
	if err != nil {
		return fallback
	}
	return value
}

func (a *API) audit(request *http.Request, repo domain.Repository, userID, operation string, detail any) {
	var actor *string
	if userID != "" {
		actor = &userID
	}
	_ = a.store.InsertAudit(request.Context(), repo.ID, actor, operation, remoteIP(request), request.Header.Get("X-Trace-Id"), detail)
}

func remoteIP(request *http.Request) string {
	host, _, err := net.SplitHostPort(request.RemoteAddr)
	if err == nil {
		return host
	}
	return request.RemoteAddr
}

func randomToken(prefix string, byteCount int) (string, error) {
	buffer := make([]byte, byteCount)
	if _, err := rand.Read(buffer); err != nil {
		return "", err
	}
	return prefix + hex.EncodeToString(buffer), nil
}

func hashToken(value string) string {
	digest := sha256.Sum256([]byte(value))
	return hex.EncodeToString(digest[:])
}

func validateWebhookURL(raw string, allowHTTP bool) error {
	parsed, err := url.Parse(raw)
	if err != nil || parsed.Hostname() == "" || parsed.User != nil {
		return errors.New("invalid webhook URL")
	}
	if parsed.Scheme != "https" && !(allowHTTP && parsed.Scheme == "http") {
		return errors.New("webhook URL must use HTTPS")
	}
	addresses, err := net.LookupIP(parsed.Hostname())
	if err != nil || len(addresses) == 0 {
		return errors.New("webhook hostname cannot be resolved")
	}
	for _, address := range addresses {
		if address.IsPrivate() || address.IsLoopback() || address.IsLinkLocalUnicast() || address.IsUnspecified() {
			return errors.New("webhook URL resolves to a private address")
		}
	}
	return nil
}

func parseAuthorizedKey(value string) (string, string, error) {
	value = strings.TrimSpace(value)
	if value == "" || len(value) > 16*1024 {
		return "", "", errors.New("SSH public key length is invalid")
	}
	key, _, _, _, err := ssh.ParseAuthorizedKey([]byte(value))
	if err != nil {
		return "", "", err
	}
	switch key.Type() {
	case ssh.KeyAlgoED25519, ssh.KeyAlgoECDSA256, ssh.KeyAlgoECDSA384, ssh.KeyAlgoECDSA521, ssh.KeyAlgoRSA:
		// 允许的现代密钥类型；明确排除 DSA 与未知实验算法。
	default:
		return "", "", errors.New("unsupported SSH public key type")
	}
	if cryptoKey, ok := key.(ssh.CryptoPublicKey); ok {
		if rsaKey, isRSA := cryptoKey.CryptoPublicKey().(*rsa.PublicKey); isRSA && rsaKey.N.BitLen() < 3072 {
			return "", "", errors.New("RSA public key must be at least 3072 bits")
		}
	}
	return strings.TrimSpace(string(ssh.MarshalAuthorizedKey(key))), ssh.FingerprintSHA256(key), nil
}
