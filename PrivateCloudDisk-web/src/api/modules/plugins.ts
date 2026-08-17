import { del, get, patch, post, put } from '@/utils/request'
import {
  signedWebClientRequest,
  webPluginAppVersion,
} from '@/runtime/webClientIdentity'
import type { LocalPluginDistribution } from '@/runtime/localPluginRuntime'

export type PluginType = 'CLOUD_PLUGIN' | 'LOCAL_PLUGIN' | 'WORKFLOW_PLUGIN'
export type PluginVisibility = 'PRIVATE' | 'SPACE' | 'PUBLIC'

export interface PluginInfo {
  pluginId: string
  ownerUserId: string
  name: string
  slug: string
  description: string
  pluginType: PluginType
  visibility: PluginVisibility
  status: string
  latestVersionId?: string
  rowVersion: number
  createdAt: string
  updatedAt: string
}

/**
 * 插件版本只读投影。
 *
 * [IDE-API-PLUGIN-VERSION] 版本选择器、校验状态和发布按钮统一使用该类型。
 * 后端当前 `GET /plugins/{pluginId}/versions` 已返回基础字段；平台后续补齐
 * 多文件草稿接口后，可选字段用于平滑兼容新增的清单和签名信息。
 */
export interface PluginVersionInfo {
  versionId: string
  pluginId: string
  version: string
  runtime: 'PYTHON_3_11' | 'JAVASCRIPT_ES2022' | 'PCD_WORKFLOW_V1' | string
  entrypoint: string
  manifestJson?: string
  permissionConfig?: string
  packageObjectKey?: string | null
  packageSha256?: string | null
  packageSize?: number
  validationStatus: 'PENDING' | 'PASSED' | 'FAILED' | 'EXPIRED' | string
  validationReportJson?: string | null
  immutable: boolean
  publishedAt?: string | null
  createdAt: string
  /** 后端扩展字段；当前版本查询接口尚未投影，故保持可选。 */
  supportedPlatformsJson?: string
  clientTypesJson?: string
  signature?: string | null
  signingKeyId?: string | null
  revokedAt?: string | null
}

export interface PluginEntrypoint {
  event: 'pcd.file.content.ready.v1' | 'pcd.file.available.v1'
  function: string
  priority: number
  conditions: Record<string, unknown>
  permissions: string[]
}

export interface PluginCapability {
  name: string
  description?: string
  inputSchema: Record<string, unknown>
  outputSchema: Record<string, unknown>
  permissions: string[]
}

export interface CreatePluginVersionPayload {
  version: string
  runtime: 'PYTHON_3_11' | 'JAVASCRIPT_ES2022' | 'PCD_WORKFLOW_V1'
  entrypoint: string
  permissions: string[]
  supported_platforms: string[]
  client_types: string[]
  entrypoints: PluginEntrypoint[]
  capabilities: PluginCapability[]
  manifest: Record<string, unknown>
}

export interface PluginValidationIssue {
  code?: string
  type?: string
  rule?: string
  line?: number
  column?: number
  message?: string
  suggestion?: string
}

/** RuntimeValidationResponse 的完整前端投影，兼容旧版 issues 字段。 */
export interface PluginValidationReport {
  valid: boolean
  errorType?: string
  /** Runtime 使用 @JsonProperty 输出 snake_case；两种命名均兼容。 */
  error_type?: string
  line?: number | null
  column?: number | null
  message?: string
  suggestion?: string
  findings?: PluginValidationIssue[]
  /** 旧版 Runtime 可能返回 issues；保留以兼容历史部署。 */
  issues?: PluginValidationIssue[]
  metrics?: Record<string, unknown>
}

export interface PluginApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
}

export interface PluginInstallation {
  installationId: string
  scopeType: 'USER' | 'SPACE'
  scopeId: string
  pluginId: string
  pluginName: string
  pluginType: PluginType
  versionId: string
  version: string
  enabled: boolean
  configJson: string
  grantedPermissionsJson: string
  autoUpdatePolicy: string
  installedAt: string
  updatedAt: string
}

/** 插件执行记录（列表接口只返回脱敏摘要）。 */
export interface PluginExecutionInfo {
  executionId: string
  pluginId: string
  versionId: string
  installationId: string
  userId?: string | null
  spaceId?: string | null
  triggerEvent: string
  triggerSource: 'EVENT' | 'WORKFLOW' | 'PLUGIN' | 'MANUAL' | 'LOCAL' | string
  executionStatus: 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELLED' | 'SKIPPED' | string
  startedAt?: string | null
  endedAt?: string | null
  durationMs?: number | null
  outputSummary?: string | null
  errorCode?: string | null
  correlationId?: string | null
  causationId?: string | null
}

/** 插件执行统计投影。 */
export interface PluginExecutionStats {
  totalExecutions: number
  successfulExecutions: number
  failedExecutions: number
  successRate: number
  lastExecutedAt?: string | null
  /** 未来统计接口可返回，当前后端未提供时保持可选。 */
  p95DurationMs?: number | null
}

/** IDE 多文件草稿文件模型（后端文件树接口上线后启用）。 */
export interface PluginDraftFile {
  path: string
  kind: 'file' | 'directory'
  language?: string
  size?: number
  sha256?: string
  content?: string
  updatedAt?: string
}

/** 插件 IDE 测试运行请求；测试运行必须在隔离 Runtime 中执行。 */
export interface PluginTestRunPayload {
  version?: string
  entrypoint?: string
  input?: Record<string, unknown>
  /** true 表示仅 dry-run，不得触发文件生命周期写回。 */
  dryRun?: boolean
}

export interface PluginTestRunAccepted {
  executionId: string
  status: 'QUEUED' | 'RUNNING' | string
}

export interface PluginTestRunStatus {
  executionId: string
  status: 'PENDING' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'CANCELLED' | string
  result?: Record<string, unknown>
  errorCode?: string
  errorSummary?: string
  startedAt?: string
  endedAt?: string
}

/** 完整日志下载授权（日志正文不直接通过执行列表返回）。 */
export interface PluginLogGrant {
  grantId: string
  url: string
  expiresAt: string
  maxBytes?: number
}

/** ZIP 包上传摘要；服务端不返回源码或宿主物理路径。 */
export interface PluginPackageUploadResult {
  /** 现有 Controller 使用 snake_case Map；保留 camelCase 兼容网关转换层。 */
  object_key: string
  objectKey?: string
  sha256: string
  package_bytes: number
  file_count: number
  expanded_bytes: number
}

const idempotency = () => crypto.randomUUID()

export function listPluginsApi(page = 1, size = 50): Promise<PluginApiResponse<PluginInfo[]>> {
  return get('plugins', { page, size })
}

export function getPluginApi(pluginId: string): Promise<PluginApiResponse<PluginInfo>> {
  return get(`plugins/${pluginId}`)
}

/**
 * 获取插件的不可变/草稿版本列表。
 * [IDE-API-PLUGIN-VERSION] 对应现有后端 GET /plugins/{pluginId}/versions。
 */
export function listPluginVersionsApi(
  pluginId: string,
): Promise<PluginApiResponse<PluginVersionInfo[]>> {
  return get(`plugins/${pluginId}/versions`)
}

/**
 * 按版本号读取版本投影。
 *
 * 当前后端尚未提供单版本 GET 接口，因此通过已存在的版本列表接口在客户端筛选，
 * 避免 IDE 为读取一个版本引入不存在的请求路径。后端增加单版本接口后可替换实现而不影响调用方。
 */
export async function getPluginVersionApi(
  pluginId: string,
  version: string,
): Promise<PluginApiResponse<PluginVersionInfo>> {
  const response = await listPluginVersionsApi(pluginId)
  const item = response.data.find((candidate) => candidate.version === version)
  if (!item) {
    throw new Error('插件版本不存在或当前账号无权访问')
  }
  return { ...response, data: item }
}

export function createPluginApi(data: {
  name: string
  slug: string
  description: string
  type: PluginType
  visibility: PluginVisibility
}): Promise<PluginApiResponse<PluginInfo>> {
  return post('plugins', data, { headers: { 'Idempotency-Key': idempotency() } })
}

/** 创建插件 IDE 草稿；复用现有创建接口并保持幂等键。 */
export const createPluginDraftApi = createPluginApi

export function updatePluginApi(
  pluginId: string,
  rowVersion: number,
  data: Partial<Pick<PluginInfo, 'name' | 'description' | 'visibility'>>,
): Promise<PluginApiResponse<PluginInfo>> {
  return patch(`plugins/${pluginId}`, data, { headers: { 'If-Match': rowVersion } })
}

/**
 * IDE 元数据草稿保存别名。
 * [IDE-API-PLUGIN-DRAFT] 当前后端仅支持元数据 PATCH，源码/文件树仍需独立接口；
 * 通过别名保持页面调用语义清晰，同时复用现有 If-Match 乐观锁。
 */
export const savePluginDraftApi = updatePluginApi

export function deletePluginApi(pluginId: string): Promise<PluginApiResponse<unknown>> {
  return del(`plugins/${pluginId}`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function createPluginVersionApi(
  pluginId: string,
  data: CreatePluginVersionPayload,
): Promise<PluginApiResponse<PluginVersionInfo>> {
  return post(`plugins/${pluginId}/versions`, data, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function uploadPluginPackageApi(
  pluginId: string,
  version: string,
  packageFile: File,
): Promise<PluginApiResponse<PluginPackageUploadResult>> {
  const data = new FormData()
  data.append('package', packageFile)
  return put(`plugins/${pluginId}/versions/${version}/source`, data, {
    headers: { 'Idempotency-Key': idempotency() },
    timeout: 60_000,
  })
}

export function validatePluginVersionApi(
  pluginId: string,
  version: string,
): Promise<PluginApiResponse<PluginValidationReport>> {
  return post(`plugins/${pluginId}/versions/${version}/validate`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
    timeout: 60_000,
  })
}

export function publishPluginVersionApi(
  pluginId: string,
  version: string,
): Promise<PluginApiResponse<PluginVersionInfo>> {
  return post(`plugins/${pluginId}/versions/${version}/publish`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function installPluginForUserApi(
  pluginId: string,
  version: string,
  permissions: string[],
): Promise<PluginApiResponse<{ installation_id: string }>> {
  return post(`plugins/${pluginId}/installations/user`, {
    version,
    granted_permissions: permissions,
    config: {},
    auto_update_policy: 'MANUAL',
  }, { headers: { 'Idempotency-Key': idempotency() } })
}

export function installPluginForSpaceApi(
  pluginId: string,
  version: string,
  permissions: string[],
): Promise<PluginApiResponse<{ installation_id: string }>> {
  return post(`plugins/${pluginId}/installations/space`, {
    version,
    granted_permissions: permissions,
    config: {},
    auto_update_policy: 'MANUAL',
  }, { headers: { 'Idempotency-Key': idempotency() } })
}

export function setPluginEnabledApi(
  installationId: string,
  enabled: boolean,
): Promise<PluginApiResponse<unknown>> {
  return patch(`plugins/installations/${installationId}`, undefined, {
    params: { enabled },
  })
}

export function listPluginInstallationsApi(): Promise<PluginApiResponse<PluginInstallation[]>> {
  return get('plugins/installations')
}

export function setSpacePluginEnabledApi(
  installationId: string,
  enabled: boolean,
): Promise<PluginApiResponse<unknown>> {
  return patch(`plugins/installations/space/${installationId}`, undefined, {
    params: { enabled },
  })
}

export function uninstallSpacePluginApi(installationId: string): Promise<PluginApiResponse<unknown>> {
  return del(`plugins/installations/space/${installationId}`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function pluginExecutionStatsApi(pluginId: string): Promise<PluginApiResponse<PluginExecutionStats>> {
  return get<PluginApiResponse<PluginExecutionStats>>(`plugins/${pluginId}/execution-stats`)
}

export function pluginExecutionsApi(
  pluginId: string,
  status = '',
  page = 1,
  size = 50,
): Promise<PluginApiResponse<PluginExecutionInfo[]>> {
  return get(`plugins/${pluginId}/executions`, { status, page, size })
}

/**
 * 读取单次插件执行详情。
 * [IDE-API-PLUGIN-EXECUTION] 文档已约定该路径，但当前 PluginController 尚未实现；
 * 保留薄封装供执行记录页接入，后端未部署该接口时由页面统一显示“详情服务未启用”。
 */
export function getPluginExecutionApi(
  executionId: string,
): Promise<PluginApiResponse<PluginExecutionInfo>> {
  return get(`plugins/executions/${executionId}`)
}

/**
 * 申请执行日志短期下载授权。
 * [IDE-API-PLUGIN-LOG] 后端需按 can_view_automation_logs/can_view_sensitive_logs 校验；
 * 当前服务仅有执行摘要查询，未提供该端点，禁止前端自行拼接存储地址。
 */
export function requestPluginLogGrantApi(
  executionId: string,
): Promise<PluginApiResponse<PluginLogGrant>> {
  return post(`plugins/executions/${executionId}/log-grant`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/**
 * 取消运行中的插件测试任务；服务端先校验任务归属，再转发到 Runtime 回收沙箱。
 */
export function cancelPluginExecutionApi(
  executionId: string,
): Promise<PluginApiResponse<{ cancelled: boolean }>> {
  return post(`plugins/test-executions/${executionId}/cancel`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/** 查询异步测试状态；Runtime 仅通过 Plugin Service 间接暴露。 */
export function getPluginTestExecutionApi(
  executionId: string,
): Promise<PluginApiResponse<PluginTestRunStatus>> {
  return get(`plugins/test-executions/${executionId}`)
}

/**
 * 发起 IDE 测试运行（异步 202）。
 * [PLUGIN-TEST-001] 通过 Plugin Service 转发到 Runtime Sandbox，禁止触发 file.content.ready 写回。
 */
export function runPluginTestApi(
  pluginId: string,
  version: string,
  payload: PluginTestRunPayload = {},
): Promise<PluginApiResponse<PluginTestRunAccepted>> {
  return post(`plugins/${pluginId}/versions/${version}/test`, {
    test_entrypoint: payload.entrypoint,
    script_entry: 'src/main.py',
    parameters: payload.input || {},
  }, {
    headers: { 'Idempotency-Key': idempotency() },
    timeout: 15_000,
  })
}

/**
 * 读取插件 IDE 的多文件草稿树。
 * [IDE-API-PLUGIN-DRAFT] 当前仅支持 ZIP 上传，后端文件树接口尚未落地；
 * 此函数保留标准路径，调用前应由页面能力探测或服务版本开关控制。
 */
export function listPluginDraftFilesApi(
  pluginId: string,
  version: string,
): Promise<PluginApiResponse<PluginDraftFile[]>> {
  return get(`plugins/${pluginId}/versions/${version}/files`)
}

/**
 * 读取草稿中的单个文件内容（文本）。后端上线后必须继续执行路径白名单校验。
 */
export function getPluginDraftFileApi(
  pluginId: string,
  version: string,
  path: string,
): Promise<PluginApiResponse<PluginDraftFile>> {
  return get(`plugins/${pluginId}/versions/${version}/files/${encodeURIComponent(path)}`)
}

/**
 * 保存草稿单文件内容。当前后端没有该接口，不能替代 immutable package 上传。
 */
export function savePluginDraftFileApi(
  pluginId: string,
  version: string,
  path: string,
  content: string,
  expectedSha256?: string,
): Promise<PluginApiResponse<PluginDraftFile>> {
  return put(`plugins/${pluginId}/versions/${version}/files/${encodeURIComponent(path)}`, {
    path,
    content,
    expected_sha256: expectedSha256,
  }, {
    headers: {
      'Idempotency-Key': idempotency(),
      ...(expectedSha256 ? { 'If-Match': expectedSha256 } : {}),
    },
  })
}

export interface MarketplacePlugin {
  pluginId: string
  name: string
  slug: string
  description: string
  pluginType: PluginType
  categoryCode: string
  authorDisplayName: string
  latestVersion: string
  permissionConfig: string
  supportedPlatformsJson: string
  clientTypesJson: string
  capabilitiesJson: string
  averageRating: number
  ratingCount: number
  installationCount: number
  publishedAt: string
}

export function listPluginMarketplaceApi(type = '', query = ''):
Promise<PluginApiResponse<MarketplacePlugin[]>> {
  return get('plugins/marketplace', { type, query, page: 1, size: 48 })
}

export function rateMarketplacePluginApi(pluginId: string, rating: number, comment: string):
Promise<PluginApiResponse<any>> {
  return post(`plugins/marketplace/${pluginId}/ratings`, { rating, comment })
}

/** 将已发布、公开的插件提交到人工/自动化市场审核队列。 */
export function submitMarketplacePluginApi(pluginId: string):
Promise<PluginApiResponse<{ review_status: 'PENDING' }>> {
  return post(`plugins/marketplace/${pluginId}/submit`)
}

/** 获取当前 Web 客户端可运行的签名本地插件清单。 */
export function listWebLocalPluginDistributionsApi():
Promise<PluginApiResponse<LocalPluginDistribution[]>> {
  return signedWebClientRequest('plugins/local/distribution', {
    params: {
      platform: 'web',
      client_type: 'web',
      app_version: webPluginAppVersion(),
    },
  })
}

export interface LocalPluginExecutionPayload {
  execution_id: string
  plugin_id: string
  version_id: string
  installation_id: string
  trigger_event: string
  trigger_source: 'LOCAL'
  status: 'SUCCESS' | 'FAILED' | 'TIMEOUT' | 'SKIPPED' | 'CANCELLED'
  started_at: string
  ended_at: string
  output_summary: string
  error_code?: string
  correlation_id?: string
  causation_id?: string
}

/** 上报浏览器沙箱的脱敏执行摘要，客户端身份由设备签名而非请求体确定。 */
export function recordWebLocalPluginExecutionApi(data: LocalPluginExecutionPayload):
Promise<PluginApiResponse<{ accepted: boolean }>> {
  return signedWebClientRequest('plugins/local/executions', {
    method: 'POST',
    data,
  })
}
