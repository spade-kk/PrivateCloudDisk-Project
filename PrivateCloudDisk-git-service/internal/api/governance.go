package api

import (
	"net/http"
	"regexp"
	"strings"

	"github.com/google/uuid"

	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/domain"
)

var safeRefGlob = regexp.MustCompile(`^[A-Za-z0-9._/*-]{1,255}$`)

func (a *API) listPermissions(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	items, err := a.store.ListPermissions(request.Context(), repo.ID)
	if err != nil {
		writeError(response, 500, "查询仓库权限失败")
		return
	}
	writeData(response, 200, items)
}

func (a *API) upsertPermission(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	subjectID := request.PathValue("subjectID")
	if !validUUID(subjectID) {
		writeError(response, 422, "授权主体 ID 格式无效")
		return
	}
	var body struct {
		SubjectType     string `json:"subjectType"`
		PermissionLevel string `json:"permissionLevel"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	body.SubjectType = strings.ToUpper(body.SubjectType)
	body.PermissionLevel = strings.ToUpper(body.PermissionLevel)
	if body.SubjectType == "" {
		body.SubjectType = "USER"
	}
	if (body.SubjectType != "USER" && body.SubjectType != "TEAM") ||
		(body.PermissionLevel != domain.PermissionRead && body.PermissionLevel != domain.PermissionWrite && body.PermissionLevel != domain.PermissionAdmin) {
		writeError(response, 422, "仓库权限参数无效")
		return
	}
	if err := a.store.UpsertPermission(request.Context(), repo.ID, body.SubjectType, subjectID, body.PermissionLevel, userID); err != nil {
		writeError(response, 500, "更新仓库权限失败")
		return
	}
	a.audit(request, repo, userID, "PERMISSION_UPDATE", map[string]string{"subject_id": subjectID, "level": body.PermissionLevel})
	writeData(response, 200, map[string]string{"subjectId": subjectID, "permissionLevel": body.PermissionLevel})
}

func (a *API) deletePermission(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	if err := a.store.DeletePermission(request.Context(), repo.ID, request.PathValue("subjectID")); err != nil {
		writeError(response, 500, "删除仓库权限失败")
		return
	}
	a.audit(request, repo, userID, "PERMISSION_DELETE", map[string]string{"subject_id": request.PathValue("subjectID")})
	response.WriteHeader(http.StatusNoContent)
}

func (a *API) upsertBranchProtection(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	var body struct {
		RefPattern          string `json:"refPattern"`
		RequireMergeRequest bool   `json:"requireMergeRequest"`
		RequiredApprovals   int    `json:"requiredApprovals"`
		AllowForcePush      bool   `json:"allowForcePush"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if body.RefPattern == "" {
		body.RefPattern = "refs/heads/" + repo.DefaultBranch
	}
	if !safeRefGlob.MatchString(body.RefPattern) || !strings.HasPrefix(body.RefPattern, "refs/heads/") || strings.Contains(body.RefPattern, "..") || strings.Contains(body.RefPattern, "//") || body.RequiredApprovals < 0 || body.RequiredApprovals > 20 {
		writeError(response, 422, "分支保护规则无效")
		return
	}
	if err := a.store.UpsertBranchProtection(request.Context(), repo.ID, body.RefPattern, body.RequireMergeRequest, body.RequiredApprovals, body.AllowForcePush, userID); err != nil {
		writeError(response, 500, "保存分支保护规则失败")
		return
	}
	if err := a.manager.RefreshProtectionHook(request.Context(), repo); err != nil {
		writeError(response, 500, "分支保护 Hook 更新失败")
		return
	}
	a.audit(request, repo, userID, "BRANCH_PROTECTION_UPDATE", body)
	writeData(response, 200, body)
}

func (a *API) listWebhooks(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	items, err := a.store.ListWebhooks(request.Context(), repo.ID, false)
	if err != nil {
		writeError(response, 500, "查询 Webhook 失败")
		return
	}
	writeData(response, 200, items)
}

func (a *API) createWebhook(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	var body struct {
		URL    string   `json:"url"`
		Events []string `json:"events"`
		Secret string   `json:"secret"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if err := validateWebhookURL(body.URL, a.cfg.WebhookAllowHTTP); err != nil {
		writeError(response, 422, "Webhook URL 不安全: "+err.Error())
		return
	}
	if len(body.Events) == 0 {
		body.Events = []string{"pcd.git.push.completed.v1"}
	}
	if body.Secret == "" {
		body.Secret, _ = randomToken("whsec_", 24)
	}
	hook := domain.Webhook{ID: uuid.NewString(), RepoID: repo.ID, URL: body.URL, Events: body.Events, Active: true}
	// [REQ-GIT-HOOK-10.3/20] Secret 只在创建响应返回一次；数据库保存 AES-256-GCM 密文，API 永不回显。
	ciphertext, err := a.secretBox.Seal([]byte(body.Secret))
	if err != nil {
		writeError(response, 500, "加密 Webhook Secret 失败")
		return
	}
	if err := a.store.CreateWebhook(request.Context(), hook, ciphertext, userID); err != nil {
		writeError(response, 500, "创建 Webhook 失败")
		return
	}
	a.audit(request, repo, userID, "WEBHOOK_CREATE", map[string]any{"webhook_id": hook.ID, "url": hook.URL, "events": hook.Events})
	writeData(response, 201, map[string]any{"webhook": hook, "secret": body.Secret})
}

func (a *API) deleteWebhook(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	if err := a.store.DeleteWebhook(request.Context(), repo.ID, request.PathValue("webhookID")); err != nil {
		writeError(response, 500, "删除 Webhook 失败")
		return
	}
	a.audit(request, repo, userID, "WEBHOOK_DELETE", map[string]string{"webhook_id": request.PathValue("webhookID")})
	response.WriteHeader(http.StatusNoContent)
}

func (a *API) listWorkflowBindings(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	items, err := a.store.ListWorkflowBindings(request.Context(), repo.ID)
	if err != nil {
		writeError(response, 500, "查询工作流绑定失败")
		return
	}
	writeData(response, 200, items)
}

func (a *API) createWorkflowBinding(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	var body domain.WorkflowBinding
	if !decodeJSON(response, request, &body) {
		return
	}
	if !validUUID(body.WorkflowID) {
		writeError(response, 422, "workflowId 格式无效")
		return
	}
	if body.RefPattern == "" {
		body.RefPattern = "refs/heads/" + repo.DefaultBranch
	}
	if !safeRefGlob.MatchString(body.RefPattern) || !strings.HasPrefix(body.RefPattern, "refs/") || strings.Contains(body.RefPattern, "..") || strings.Contains(body.RefPattern, "//") {
		writeError(response, 422, "工作流 Ref 匹配模式无效")
		return
	}
	if len(body.Events) == 0 {
		body.Events = []string{"pcd.git.push.completed.v1"}
	}
	body.ID, body.RepoID, body.Enabled = uuid.NewString(), repo.ID, true
	if err := a.store.CreateWorkflowBinding(request.Context(), body, userID); err != nil {
		writeError(response, 409, "创建工作流绑定失败")
		return
	}
	a.audit(request, repo, userID, "WORKFLOW_BINDING_CREATE", body)
	writeData(response, 201, body)
}

func (a *API) listAudit(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Admin)
	if !ok {
		return
	}
	page, size := queryInt(request, "page", 1), queryInt(request, "size", 50)
	if page < 1 {
		page = 1
	}
	if size < 1 || size > 200 {
		size = 50
	}
	items, err := a.store.ListAudit(request.Context(), repo.ID, size, (page-1)*size)
	if err != nil {
		writeError(response, 500, "查询审计日志失败")
		return
	}
	writeData(response, 200, items)
}
