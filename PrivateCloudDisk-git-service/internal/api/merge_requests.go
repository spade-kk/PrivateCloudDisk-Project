package api

import (
	"fmt"
	"net/http"
	"strings"

	"github.com/google/uuid"

	"privateclouddisk/git-service/internal/auth"
	"privateclouddisk/git-service/internal/domain"
)

func (a *API) createMergeRequest(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	var body struct {
		Title         string `json:"title"`
		Description   string `json:"description"`
		SourceBranch  string `json:"sourceBranch"`
		TargetBranch  string `json:"targetBranch"`
		MergeStrategy string `json:"mergeStrategy"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if body.Title == "" || body.SourceBranch == "" || body.TargetBranch == "" || body.SourceBranch == body.TargetBranch {
		writeError(response, 422, "合并请求标题、源分支和不同的目标分支不能为空")
		return
	}
	if body.MergeStrategy == "" {
		body.MergeStrategy = "MERGE_COMMIT"
	}
	if body.MergeStrategy != "MERGE_COMMIT" && body.MergeStrategy != "FAST_FORWARD" {
		writeError(response, 422, "合并策略无效")
		return
	}
	mr := domain.MergeRequest{ID: uuid.NewString(), RepoID: repo.ID, Title: body.Title, Description: body.Description,
		SourceBranch: body.SourceBranch, TargetBranch: body.TargetBranch, AuthorID: userID, MergeStrategy: body.MergeStrategy}
	mr, err := a.store.CreateMergeRequest(request.Context(), mr)
	if err != nil {
		writeError(response, 409, "创建合并请求失败: "+err.Error())
		return
	}
	a.audit(request, repo, userID, "MERGE_REQUEST_CREATE", mr)
	writeData(response, 201, mr)
}

func (a *API) listMergeRequests(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	items, err := a.store.ListMergeRequests(request.Context(), repo.ID, strings.ToUpper(request.URL.Query().Get("status")))
	if err != nil {
		writeError(response, 500, "查询合并请求失败")
		return
	}
	writeData(response, 200, items)
}

func (a *API) getMergeRequest(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	mr, err := a.store.GetMergeRequest(request.Context(), repo.ID, request.PathValue("mrID"))
	if err != nil {
		writeError(response, 404, "合并请求不存在")
		return
	}
	writeData(response, 200, mr)
}

func (a *API) listMergeRequestComments(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	if _, err := a.store.GetMergeRequest(request.Context(), repo.ID, request.PathValue("mrID")); err != nil {
		writeError(response, 404, "合并请求不存在")
		return
	}
	comments, err := a.store.ListMRComments(request.Context(), request.PathValue("mrID"))
	if err != nil {
		writeError(response, 500, "查询合并请求评论失败")
		return
	}
	writeData(response, 200, comments)
}

func (a *API) commentMergeRequest(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	var body struct {
		Body string `json:"body"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if strings.TrimSpace(body.Body) == "" {
		writeError(response, 422, "评论内容不能为空")
		return
	}
	id, err := a.store.AddMRComment(request.Context(), request.PathValue("mrID"), userID, body.Body)
	if err != nil {
		writeError(response, 404, "合并请求不存在")
		return
	}
	a.audit(request, repo, userID, "MERGE_REQUEST_COMMENT", map[string]string{"merge_request_id": request.PathValue("mrID")})
	writeData(response, 201, map[string]string{"commentId": id})
}

func (a *API) approveMergeRequest(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	var body struct {
		Decision string `json:"decision"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	body.Decision = strings.ToUpper(body.Decision)
	if body.Decision != "APPROVED" && body.Decision != "CHANGES_REQUESTED" {
		writeError(response, 422, "审批结果无效")
		return
	}
	if err := a.store.UpsertApproval(request.Context(), request.PathValue("mrID"), userID, body.Decision); err != nil {
		writeError(response, 404, "合并请求不存在")
		return
	}
	a.audit(request, repo, userID, "MERGE_REQUEST_REVIEW", body)
	writeData(response, 200, map[string]string{"decision": body.Decision})
}

func (a *API) mergeMergeRequest(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	mr, err := a.store.GetMergeRequest(request.Context(), repo.ID, request.PathValue("mrID"))
	if err != nil {
		writeError(response, 404, "合并请求不存在")
		return
	}
	if mr.Status != "OPEN" || mr.ApprovalStatus == "CHANGES_REQUESTED" {
		writeError(response, 409, "合并请求当前不可合并")
		return
	}
	requiredApprovals, err := a.store.MergeApprovalRequirement(request.Context(), repo.ID, "refs/heads/"+mr.TargetBranch)
	if err != nil {
		writeError(response, 500, "读取分支保护规则失败")
		return
	}
	approved, changesRequested, err := a.store.MergeReviewSummary(request.Context(), mr.ID)
	if err != nil {
		writeError(response, 500, "读取审批结果失败")
		return
	}
	if changesRequested > 0 || approved < requiredApprovals {
		writeError(response, 409, fmt.Sprintf("合并请求需要 %d 个有效审批，当前为 %d", requiredApprovals, approved))
		return
	}
	commit, err := a.manager.Merge(request.Context(), repo, mr.SourceBranch, mr.TargetBranch, mr.MergeStrategy, userID)
	if err != nil {
		writeError(response, 409, "合并失败: "+err.Error())
		return
	}
	if err := a.manager.Sync(request.Context(), repo); err != nil {
		writeError(response, 503, "合并已完成但对象同步失败")
		return
	}
	if err := a.store.MarkMerged(request.Context(), mr.ID, userID); err != nil {
		writeError(response, 500, "合并状态更新失败")
		return
	}
	a.audit(request, repo, userID, "MERGE_REQUEST_MERGE", map[string]string{"merge_request_id": mr.ID, "commit": commit})
	writeData(response, 200, map[string]string{"mergeCommit": commit})
}
