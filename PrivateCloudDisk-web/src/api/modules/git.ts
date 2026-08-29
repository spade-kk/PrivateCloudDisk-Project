// ============================================================
// git.ts — Git Repository Service 管理与凭证 API
// ============================================================
// Smart HTTP/SSH 数据通道不在浏览器中复刻；浏览器只调用管理、只读内容和凭证 API。

import { del, get, patch, post, put } from '@/utils/request'

// [REQ-GIT-AUDIT-4.1/4.2] Git 仓库是公开空间 resource_type=git 下的资源实现，
// 可见性属于仓库协议访问策略而不是平台“私人空间”类型。保留联合类型，便于未来
// Docker/模型等资源复用空间抽象但拥有各自的访问策略。
export type GitRepositoryVisibility = 'PUBLIC' | 'HIDDEN' | 'PRIVATE'

export interface GitRepository {
  repoId: string
  spaceId: string
  ownerId: string
  name: string
  slug: string
  description: string
  defaultBranch: string
  hashAlgorithm: string
  status: string
  visibility: GitRepositoryVisibility
  objectCount: number
  objectBytes: number
  createdAt?: string
  updatedAt?: string
  httpCloneUrl?: string
  sshCloneUrl?: string
  starred?: boolean
  starCount?: number
  forkCount?: number
}

export interface GitRepositorySocialStats { starred: boolean; starCount: number; forkCount: number }

export interface GitRef {
  name: string
  objectHash: string
  type: string
  protected?: boolean
  updatedAt?: string
}

export interface GitTreeEntry {
  mode: string
  type: 'blob' | 'tree' | string
  hash: string
  size?: number
  name: string
  path: string
}

/** Git 文本预览的元数据；二进制和超限内容通过 raw 流端点读取。 */
export interface GitBlobPreview {
  path: string
  content: string
  size: number
  mimeType: string
  isBinary: boolean
  truncated: boolean
  lineCount: number
}

export interface GitCommit {
  hash: string
  treeHash: string
  parents: string[]
  authorName: string
  authorEmail: string
  authoredAt?: string
  committerName: string
  committedAt?: string
  subject: string
  message: string
}

export interface GitRepositoryInsights {
  commitCount: number
  contributorCount: number
  branchCount: number
  tagCount: number
  languages: Array<{ name: string; bytes: number }>
  contributors: Array<{ name: string; email: string; commits: number }>
  contributions: Array<{ date: string; count: number }>
}

export interface GitMergeRequest {
  mergeRequestId: string
  repoId: string
  number: number
  title: string
  description: string
  sourceBranch: string
  targetBranch: string
  authorId: string
  status: string
  approvalStatus: string
  mergeStrategy: string
  createdAt?: string
  updatedAt?: string
}

export interface GitMergeRequestComment {
  commentId: string
  mergeRequestId: string
  authorId: string
  body: string
  createdAt?: string
}

export interface GitPAT {
  tokenId: string
  name: string
  tokenPrefix: string
  scopes: string[]
  expiresAt?: string | null
  lastUsedAt?: string | null
  createdAt?: string
}

export interface GitSSHKey {
  keyId: string
  keyName: string
  fingerprint: string
  createdAt?: string
  lastUsedAt?: string | null
}

export interface GitWorkflowBinding {
  bindingId: string
  repoId: string
  workflowId: string
  refPattern: string
  events: string[]
  enabled: boolean
}

type ApiResponse<T> = Promise<{ code: number; data: T }>

type UnknownRecord = Record<string, unknown>

function asRecord(value: unknown): UnknownRecord {
  return value !== null && typeof value === 'object' ? value as UnknownRecord : {}
}

function asString(value: unknown): string {
  return typeof value === 'string' ? value : ''
}

function asStringArray(value: unknown): string[] {
  return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : []
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] 兼容修复前 Git Service 返回的默认 Go JSON 键，
// 同时把缺失/异常 scopes 归一化为空数组，避免旧缓存或滚动发布期间出现 scopes.join 崩溃。
function normalizeGitPAT(value: unknown): GitPAT {
  const raw = asRecord(value)
  return {
    tokenId: asString(raw.tokenId ?? raw.ID),
    name: asString(raw.name ?? raw.Name),
    tokenPrefix: asString(raw.tokenPrefix ?? raw.Prefix),
    scopes: asStringArray(raw.scopes ?? raw.Scopes),
    expiresAt: (raw.expiresAt ?? raw.ExpiresAt) as string | null | undefined,
    lastUsedAt: (raw.lastUsedAt ?? raw.LastUsedAt) as string | null | undefined,
    createdAt: asString(raw.createdAt ?? raw.CreatedAt) || undefined,
  }
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] SSH 列表同样兼容旧端的字段大小写，避免出现
// keyId/keyName 为空导致撤销和展示失效。
function normalizeGitSSHKey(value: unknown): GitSSHKey {
  const raw = asRecord(value)
  return {
    keyId: asString(raw.keyId ?? raw.ID),
    keyName: asString(raw.keyName ?? raw.Name),
    fingerprint: asString(raw.fingerprint ?? raw.Fingerprint),
    createdAt: asString(raw.createdAt ?? raw.CreatedAt) || undefined,
    lastUsedAt: (raw.lastUsedAt ?? raw.LastUsedAt) as string | null | undefined,
  }
}

// [FIX-GIT-CREDENTIAL-CONTRACT-20260816] 事件字段即使后端存量数据为空也统一为数组，
// 避免 GitRepositoryPanel 的 events.join 在旧记录上再次触发运行时异常。
function normalizeGitWorkflowBinding(value: unknown): GitWorkflowBinding {
  const raw = asRecord(value)
  return {
    bindingId: asString(raw.bindingId ?? raw.ID),
    repoId: asString(raw.repoId ?? raw.RepoID),
    workflowId: asString(raw.workflowId ?? raw.WorkflowID),
    refPattern: asString(raw.refPattern ?? raw.RefPattern),
    events: asStringArray(raw.events ?? raw.Events),
    enabled: typeof raw.enabled === 'boolean' ? raw.enabled : raw.Enabled !== false,
  }
}

export function getGitRepositoryBySpaceApi(spaceId: string): ApiResponse<GitRepository> {
  return get(`git/repos/by-space/${spaceId}`)
}

export function getGitRepositorySocialStatsApi(repoId: string): ApiResponse<GitRepositorySocialStats> {
  return get(`git/repos/${repoId}/star/status`)
}

export function starGitRepositoryApi(repoId: string): ApiResponse<GitRepositorySocialStats> {
  return put(`git/repos/${repoId}/star`)
}

export function unstarGitRepositoryApi(repoId: string): ApiResponse<GitRepositorySocialStats> {
  return del(`git/repos/${repoId}/star`)
}

export function forkGitRepositoryApi(repoId: string, payload: { targetSpaceId: string; name?: string; visibility?: GitRepositoryVisibility }): ApiResponse<GitRepository> {
  return post(`git/repos/${repoId}/fork`, payload)
}

// AUDIT FIX [3.16-3.17] Fork 列表需要可回收用户自己创建的副本，复用仓库 Admin 删除语义。
export function deleteGitRepositoryApi(repoId: string): ApiResponse<unknown> {
  return del(`git/repos/${repoId}`)
}

export function listMyGitStarsApi(page = 1, size = 30): ApiResponse<GitRepository[]> {
  return get('git/repos/stars', { page, size })
}

export function listMyGitForksApi(page = 1, size = 30): ApiResponse<GitRepository[]> {
  return get('git/repos/forks', { page, size })
}

export function createGitRepositoryApi(payload: {
  spaceId: string
  name: string
  description?: string
  defaultBranch?: string
  visibility?: GitRepositoryVisibility
}): ApiResponse<GitRepository> {
  return post('git/repos', payload)
}

export function updateGitRepositoryApi(repoId: string, payload: {
  name?: string
  description?: string
  defaultBranch?: string
  visibility?: GitRepositoryVisibility
}): ApiResponse<GitRepository> {
  // [FIX-GIT-API-METHOD-20260816] Git Service 注册的是 PATCH；原前端 PUT 会命中不存在的路由。
  return patch(`git/repos/${repoId}`, payload)
}

export function listGitBranchesApi(repoId: string): ApiResponse<GitRef[]> {
  return get(`git/repos/${repoId}/branches`)
}

export function createGitBranchApi(repoId: string, payload: { name: string; startPoint?: string }) {
  return post(`git/repos/${repoId}/branches`, payload)
}

export function deleteGitBranchApi(repoId: string, name: string) {
  return del(`git/repos/${repoId}/branches/${encodeURIComponent(name)}`)
}

export function listGitTagsApi(repoId: string): ApiResponse<GitRef[]> {
  return get(`git/repos/${repoId}/tags`)
}

export function createGitTagApi(repoId: string, payload: { name: string; target?: string; message?: string }) {
  return post(`git/repos/${repoId}/tags`, payload)
}

export function deleteGitTagApi(repoId: string, name: string) {
  return del(`git/repos/${repoId}/tags/${encodeURIComponent(name)}`)
}

export function listGitTreeApi(repoId: string, ref: string, path = ''): ApiResponse<GitTreeEntry[]> {
  return get(`git/repos/${repoId}/tree`, { ref, path: path || undefined })
}

export function getGitBlobApi(repoId: string, ref: string, path: string): ApiResponse<GitBlobPreview> {
  return get(`git/repos/${repoId}/blob`, { ref, path })
}

// [REQ-GIT-UIUX-20260816] 原 Blob API 仅返回 JSON 文本；图片、PDF 和下载改为请求
// 受 Git Fetch 权限保护的二进制流，避免将 Authorization 暴露到 URL 查询参数中。
export function getGitRawBlobApi(repoId: string, ref: string, path: string, download = false): Promise<Blob> {
  return get<Blob>(`git/repos/${repoId}/raw`, { ref, path, download: download ? '1' : undefined }, {
    responseType: 'blob',
    suppressToast: true,
  })
}

export function getGitArchiveApi(repoId: string, ref: string): Promise<Blob> {
  return get<Blob>(`git/repos/${repoId}/archive`, { ref }, { responseType: 'blob', suppressToast: true })
}

export function getGitReadmeApi(repoId: string, ref: string): ApiResponse<{ name: string; content: string }> {
  return get(`git/repos/${repoId}/readme`, { ref })
}

export function listGitCommitsApi(
  repoId: string,
  ref: string,
  page = 1,
  size = 30,
  filters: { author?: string; since?: string; until?: string; path?: string; all?: boolean } = {},
): ApiResponse<GitCommit[]> {
  const { all, ...query } = filters
  return get(`git/repos/${repoId}/commits`, { ref: all ? undefined : ref, page, size, ...query, all: all ? '1' : undefined })
}

export function getGitDiffApi(repoId: string, from: string, to: string, path?: string): ApiResponse<{ diff: string }> {
  return get(`git/repos/${repoId}/diff`, { from, to, path: path || undefined })
}

export function getGitBlameApi(repoId: string, ref: string, path: string): ApiResponse<{ porcelain: string }> {
  return get(`git/repos/${repoId}/blame`, { ref, path })
}

export function getGitRepositoryInsightsApi(repoId: string): ApiResponse<GitRepositoryInsights> {
  return get(`git/repos/${repoId}/insights`)
}

export function listGitMergeRequestsApi(repoId: string, status = ''): ApiResponse<GitMergeRequest[]> {
  return get(`git/repos/${repoId}/merge-requests`, { status: status || undefined })
}

export function createGitMergeRequestApi(repoId: string, payload: { title: string; description?: string; sourceBranch: string; targetBranch: string; mergeStrategy?: string }): ApiResponse<GitMergeRequest> {
  return post(`git/repos/${repoId}/merge-requests`, payload)
}

export function getGitMergeRequestApi(repoId: string, mergeRequestId: string): ApiResponse<GitMergeRequest> {
  return get(`git/repos/${repoId}/merge-requests/${mergeRequestId}`)
}

export function listGitMergeRequestCommentsApi(repoId: string, mergeRequestId: string): ApiResponse<GitMergeRequestComment[]> {
  return get(`git/repos/${repoId}/merge-requests/${mergeRequestId}/comments`)
}

export function createGitMergeRequestCommentApi(repoId: string, mergeRequestId: string, body: string): ApiResponse<{ commentId: string }> {
  return post(`git/repos/${repoId}/merge-requests/${mergeRequestId}/comments`, { body })
}

export function reviewGitMergeRequestApi(repoId: string, mergeRequestId: string, decision: 'APPROVED' | 'CHANGES_REQUESTED'): ApiResponse<{ decision: string }> {
  return post(`git/repos/${repoId}/merge-requests/${mergeRequestId}/approve`, { decision })
}

export function mergeGitMergeRequestApi(repoId: string, mergeRequestId: string): ApiResponse<{ mergeCommit: string }> {
  return post(`git/repos/${repoId}/merge-requests/${mergeRequestId}/merge`)
}

/** [REQ-GIT-CI-10.4/13.4] Git push 只绑定既有 Workflow DSL，不在前端或 Git Service 复制 Actions Runner。 */
export function listGitWorkflowBindingsApi(repoId: string): ApiResponse<GitWorkflowBinding[]> {
  return get<{ code: number; data: unknown }>(`git/repos/${repoId}/workflow-bindings`).then((response) => ({
    ...response,
    data: Array.isArray(response.data) ? response.data.map(normalizeGitWorkflowBinding) : [],
  }))
}

export function createGitWorkflowBindingApi(repoId: string, payload: { workflowId: string; refPattern: string; events?: string[] }): ApiResponse<GitWorkflowBinding> {
  return post(`git/repos/${repoId}/workflow-bindings`, payload)
}

export function listGitPATsApi(): ApiResponse<GitPAT[]> {
  return get<{ code: number; data: unknown }>('git/credentials/pats').then((response) => ({
    ...response,
    data: Array.isArray(response.data) ? response.data.map(normalizeGitPAT) : [],
  }))
}

export function createGitPATApi(payload: { name: string; scopes: string[]; expiresAt?: string }): ApiResponse<{ token: string; metadata: GitPAT }> {
  return post<{ code: number; data: { token?: unknown; metadata?: unknown } }>('git/credentials/pats', payload).then((response) => ({
    ...response,
    data: {
      token: asString(response.data?.token),
      metadata: normalizeGitPAT(response.data?.metadata),
    },
  }))
}

export function revokeGitPATApi(tokenId: string) {
  return del(`git/credentials/pats/${tokenId}`)
}

export function listGitSSHKeysApi(): ApiResponse<GitSSHKey[]> {
  return get<{ code: number; data: unknown }>('git/credentials/ssh-keys').then((response) => ({
    ...response,
    data: Array.isArray(response.data) ? response.data.map(normalizeGitSSHKey) : [],
  }))
}

export function createGitSSHKeyApi(payload: { name: string; publicKey: string }): ApiResponse<GitSSHKey> {
  return post<{ code: number; data: unknown }>('git/credentials/ssh-keys', payload).then((response) => ({
    ...response,
    data: normalizeGitSSHKey(response.data),
  }))
}

export function revokeGitSSHKeyApi(keyId: string) {
  return del(`git/credentials/ssh-keys/${keyId}`)
}
