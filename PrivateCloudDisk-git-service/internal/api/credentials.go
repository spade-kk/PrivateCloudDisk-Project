package api

import (
	"errors"
	"net/http"
	"strings"
	"time"

	"privateclouddisk/git-service/internal/store"
)

const maxSSHKeysPerUser = 20

func (a *API) createPAT(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	var body struct {
		Name      string     `json:"name"`
		Scopes    []string   `json:"scopes"`
		ExpiresAt *time.Time `json:"expiresAt"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" || len([]rune(body.Name)) > 128 {
		writeError(response, 422, "令牌名称不能为空")
		return
	}
	if body.ExpiresAt != nil && !body.ExpiresAt.After(time.Now().UTC()) {
		writeError(response, 422, "PAT 过期时间必须晚于当前时间")
		return
	}
	if len(body.Scopes) == 0 {
		body.Scopes = []string{"read_repository"}
	}
	for _, scope := range body.Scopes {
		if scope != "read_repository" && scope != "write_repository" && scope != "api" {
			writeError(response, 422, "PAT scope 无效")
			return
		}
	}
	token, err := randomToken("pcd_pat_", 32)
	if err != nil {
		writeError(response, 500, "生成 PAT 失败")
		return
	}
	prefix := token
	if len(prefix) > 12 {
		prefix = prefix[:12]
	}
	record, err := a.store.CreatePAT(request.Context(), userID, body.Name, hashToken(token), prefix, body.Scopes, body.ExpiresAt)
	if err != nil {
		writeError(response, 500, "保存 PAT 失败")
		return
	}
	// [REQ-GIT-AUTH-4.4/12.3] 明文 PAT 只在创建响应出现一次，后续列表仅返回 prefix。
	writeData(response, 201, map[string]any{"token": token, "metadata": record})
}

func (a *API) listPATs(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	items, err := a.store.ListPATs(request.Context(), userID)
	if err != nil {
		writeError(response, 500, "查询 PAT 失败")
		return
	}
	writeData(response, 200, items)
}

func (a *API) revokePAT(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	if err := a.store.RevokePAT(request.Context(), userID, request.PathValue("tokenID")); err != nil {
		writeError(response, 404, "PAT 不存在")
		return
	}
	response.WriteHeader(http.StatusNoContent)
}

func (a *API) createSSHKey(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	var body struct {
		Name      string `json:"name"`
		PublicKey string `json:"publicKey"`
	}
	if !decodeJSON(response, request, &body) {
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" || len([]rune(body.Name)) > 128 || len(body.PublicKey) > 16*1024 {
		writeError(response, 422, "SSH 公钥名称或长度无效")
		return
	}
	// [REQ-GIT-AUDIT-4.8/6.9] 原接口只做语法解析，允许过弱 RSA 和每个账户无限
	// 增加密钥；新行为仅接受现代公钥算法、拒绝小于 3072 位的 RSA，并把活跃密钥
	// 配额检查放到 Store 的插入事务中，避免并发请求绕过上限。影响范围仅为新增 SSH Key，
	// 不会让已登记密钥突然失效。
	canonical, fingerprint, err := parseAuthorizedKey(body.PublicKey)
	if err != nil {
		writeError(response, 422, "SSH 公钥格式无效")
		return
	}
	record, err := a.store.CreateSSHKey(request.Context(), userID, body.Name, canonical, fingerprint, maxSSHKeysPerUser)
	if err != nil {
		if errors.Is(err, store.ErrLimit) {
			writeError(response, http.StatusTooManyRequests, "每个用户最多保存 20 个 SSH 公钥")
			return
		}
		writeError(response, 409, "SSH 公钥已存在")
		return
	}
	writeData(response, 201, record)
}

func (a *API) listSSHKeys(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	items, err := a.store.ListSSHKeys(request.Context(), userID)
	if err != nil {
		writeError(response, 500, "查询 SSH 公钥失败")
		return
	}
	for index := range items {
		items[index].PublicKey = ""
	}
	writeData(response, 200, items)
}

func (a *API) revokeSSHKey(response http.ResponseWriter, request *http.Request) {
	userID, ok := requireUser(response, request)
	if !ok {
		return
	}
	if err := a.store.RevokeSSHKey(request.Context(), userID, request.PathValue("keyID")); err != nil {
		writeError(response, 404, "SSH 公钥不存在")
		return
	}
	response.WriteHeader(http.StatusNoContent)
}
