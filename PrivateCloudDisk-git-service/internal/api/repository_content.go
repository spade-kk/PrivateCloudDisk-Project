package api

import (
	"mime"
	"net/http"
	"net/url"
	"path/filepath"
	"strconv"
	"strings"

	"privateclouddisk/git-service/internal/auth"
)

func (a *API) listBranches(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	refs, err := a.store.ListRefs(request.Context(), repo.ID, "BRANCH")
	if err != nil {
		writeError(response, 500, "查询分支失败")
		return
	}
	for index := range refs {
		refs[index].Name = strings.TrimPrefix(refs[index].Name, "refs/heads/")
	}
	writeData(response, 200, refs)
}

func (a *API) createBranch(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	var body struct {
		Name       string `json:"name"`
		StartPoint string `json:"startPoint"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if err := a.manager.CreateBranch(request.Context(), repo, body.Name, body.StartPoint); err != nil {
		writeError(response, 409, "创建分支失败: "+err.Error())
		return
	}
	if err := a.manager.Sync(request.Context(), repo); err != nil {
		writeError(response, 503, "分支已创建但对象同步失败")
		return
	}
	a.audit(request, repo, userID, "BRANCH_CREATE", body)
	writeData(response, 201, map[string]string{"name": body.Name})
}

func (a *API) deleteBranch(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	name, _ := url.PathUnescape(request.PathValue("name"))
	if err := a.manager.DeleteBranch(request.Context(), repo, name); err != nil {
		writeError(response, 409, "删除分支失败: "+err.Error())
		return
	}
	if err := a.manager.Sync(request.Context(), repo); err != nil {
		writeError(response, 503, "分支已删除但索引同步失败")
		return
	}
	a.audit(request, repo, userID, "BRANCH_DELETE", map[string]string{"name": name})
	response.WriteHeader(http.StatusNoContent)
}

func (a *API) listTags(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	refs, err := a.store.ListRefs(request.Context(), repo.ID, "TAG")
	if err != nil {
		writeError(response, 500, "查询标签失败")
		return
	}
	for index := range refs {
		refs[index].Name = strings.TrimPrefix(refs[index].Name, "refs/tags/")
	}
	writeData(response, 200, refs)
}

func (a *API) createTag(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	var body struct {
		Name    string `json:"name"`
		Target  string `json:"target"`
		Message string `json:"message"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	if err := a.manager.CreateTag(request.Context(), repo, body.Name, body.Target, body.Message, userID); err != nil {
		writeError(response, 409, "创建标签失败: "+err.Error())
		return
	}
	if err := a.manager.Sync(request.Context(), repo); err != nil {
		writeError(response, 503, "标签已创建但对象同步失败")
		return
	}
	a.audit(request, repo, userID, "TAG_CREATE", body)
	writeData(response, 201, map[string]string{"name": body.Name})
}

func (a *API) deleteTag(response http.ResponseWriter, request *http.Request) {
	repo, userID, ok := a.requireRepo(response, request, auth.Push)
	if !ok {
		return
	}
	name, _ := url.PathUnescape(request.PathValue("name"))
	if err := a.manager.DeleteTag(request.Context(), repo, name); err != nil {
		writeError(response, 409, "删除标签失败: "+err.Error())
		return
	}
	if err := a.manager.Sync(request.Context(), repo); err != nil {
		writeError(response, 503, "标签已删除但索引同步失败")
		return
	}
	a.audit(request, repo, userID, "TAG_DELETE", map[string]string{"name": name})
	response.WriteHeader(http.StatusNoContent)
}

func (a *API) listCommits(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	query := request.URL.Query()
	// [REQ-GIT-GRAPH-5.1~5.25] all=1 is a server-owned, fixed Git argument.
	// It lets the repository UI render a true multi-branch DAG without making one
	// unbounded request per branch in the browser. It does not accept arbitrary
	// revision expressions, so existing ref validation and path safety remain intact.
	commits, err := a.manager.ListCommits(request.Context(), repo, query.Get("ref"), query.Get("author"), query.Get("since"), query.Get("until"), query.Get("path"), query.Get("all") == "1", queryInt(request, "page", 1), queryInt(request, "size", 30))
	if err != nil {
		writeError(response, 404, "提交历史不可用: "+err.Error())
		return
	}
	writeData(response, 200, commits)
}

func (a *API) listTree(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	entries, err := a.manager.ListTree(request.Context(), repo, request.URL.Query().Get("ref"), request.URL.Query().Get("path"))
	if err != nil {
		writeError(response, 404, "代码树不可用: "+err.Error())
		return
	}
	writeData(response, 200, entries)
}

func (a *API) readBlob(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	content, err := a.manager.ReadBlobPreview(request.Context(), repo, request.URL.Query().Get("ref"), request.URL.Query().Get("path"))
	if err != nil {
		writeError(response, 404, "文件内容不可用: "+err.Error())
		return
	}
	writeData(response, 200, content)
}

// readRawBlob 提供图片、PDF、原始代码和单文件下载的二进制流。
// [REQ-GIT-UIUX-20260816] 该端点使用 Fetch 而非 Metadata 权限：原行为没有原始流，
// 新行为不会让 allow_public_download=false 的空间通过预览 UI 绕过下载限制。
func (a *API) readRawBlob(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Fetch)
	if !ok {
		return
	}
	path := request.URL.Query().Get("path")
	ref := request.URL.Query().Get("ref")
	size, err := a.manager.RawBlobSize(request.Context(), repo, ref, path)
	if err != nil {
		if strings.Contains(err.Error(), "exceeds configured limit") {
			writeError(response, http.StatusRequestEntityTooLarge, "原始文件超过服务端单文件输出限制")
			return
		}
		writeError(response, http.StatusNotFound, "原始文件不可用: "+err.Error())
		return
	}
	contentType := mime.TypeByExtension(filepath.Ext(path))
	if contentType == "" {
		contentType = "application/octet-stream"
	}
	response.Header().Set("Content-Type", contentType)
	response.Header().Set("X-Content-Type-Options", "nosniff")
	response.Header().Set("Cache-Control", "private, max-age=60")
	if request.URL.Query().Get("download") == "1" {
		response.Header().Set("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{"filename": filepath.Base(path)}))
	}
	response.Header().Set("Content-Length", strconv.FormatInt(size, 10))
	response.WriteHeader(http.StatusOK)
	if err := a.manager.StreamRawBlob(request.Context(), repo, ref, path, response); err != nil {
		// 响应体已开始传输，不能再写 JSON 错误；审计后让客户端按中断结果安全重试。
		a.audit(request, repo, optionalUser(request), "RAW_BLOB_STREAM_FAILED", map[string]string{"path": path, "reason": err.Error()})
	}
}

// downloadArchive 以 Git 原生 archive 输出当前引用 ZIP，不将对象树装入 API JSON。
func (a *API) downloadArchive(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Fetch)
	if !ok {
		return
	}
	ref := request.URL.Query().Get("ref")
	filename := repo.Slug
	if ref != "" {
		filename += "-" + strings.ReplaceAll(ref, "/", "-")
	}
	response.Header().Set("Content-Type", "application/zip")
	response.Header().Set("Content-Disposition", mime.FormatMediaType("attachment", map[string]string{"filename": filename + ".zip"}))
	response.Header().Set("X-Content-Type-Options", "nosniff")
	if err := a.manager.Archive(request.Context(), repo, ref, response); err != nil {
		// 若 Git 尚未写入 ZIP，统一错误信封仍可返回；若流中途失败，HTTP 客户端会收到中断并可重试。
		a.audit(request, repo, optionalUser(request), "ARCHIVE_DOWNLOAD_FAILED", map[string]string{"ref": ref, "reason": err.Error()})
		writeError(response, http.StatusBadRequest, "仓库归档失败: "+err.Error())
		return
	}
	a.audit(request, repo, optionalUser(request), "ARCHIVE_DOWNLOAD", map[string]string{"ref": ref})
}

func (a *API) readREADME(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	name, content, err := a.manager.ReadREADME(request.Context(), repo, request.URL.Query().Get("ref"))
	if err != nil {
		writeError(response, 500, "README 读取失败")
		return
	}
	writeData(response, 200, map[string]string{"name": name, "content": content})
}

func (a *API) repositoryInsights(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	insights, err := a.manager.Insights(request.Context(), repo)
	if err != nil {
		writeError(response, http.StatusServiceUnavailable, "仓库统计暂不可用: "+err.Error())
		return
	}
	writeData(response, http.StatusOK, insights)
}

func (a *API) diff(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	query := request.URL.Query()
	content, err := a.manager.Diff(request.Context(), repo, query.Get("from"), query.Get("to"), query.Get("path"))
	if err != nil {
		writeError(response, 422, "Diff 生成失败: "+err.Error())
		return
	}
	writeData(response, 200, map[string]string{"diff": content})
}

func (a *API) blame(response http.ResponseWriter, request *http.Request) {
	repo, _, ok := a.requireRepo(response, request, auth.Metadata)
	if !ok {
		return
	}
	content, err := a.manager.Blame(request.Context(), repo, request.URL.Query().Get("ref"), request.URL.Query().Get("path"))
	if err != nil {
		writeError(response, 422, "Blame 生成失败: "+err.Error())
		return
	}
	writeData(response, 200, map[string]string{"porcelain": content})
}
