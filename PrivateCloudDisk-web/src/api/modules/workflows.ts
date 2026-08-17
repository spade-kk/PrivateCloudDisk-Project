import { del, get, patch, post } from '@/utils/request'

export interface WorkflowInfo {
  workflowId: string
  ownerUserId: string
  ownerScopeType: 'USER' | 'SPACE'
  ownerScopeId: string
  name: string
  slug: string
  description: string
  status: string
  latestVersionId?: string
  rowVersion: number
  createdAt: string
  updatedAt: string
}

/** 工作流版本投影；发布版本不可变，草稿通过 update 接口创建新版本。 */
export interface WorkflowVersionInfo {
  versionId: string
  workflowId: string
  version: number
  dslText: string
  graphJson: string
  schemaVersion: string
  validationReportJson: string
  immutable: boolean
  publishedAt?: string | null
  createdAt: string
}

/** 工作流执行历史摘要，与后端 ExecutionRow 保持 camelCase 响应字段。 */
export interface WorkflowExecutionInfo {
  executionId: string
  workflowId: string
  versionId: string
  userId: string
  spaceId?: string | null
  triggerType: 'MANUAL' | 'EVENT' | 'SCHEDULE' | string
  status: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'TIMEOUT' | 'CANCELLED' | string
  currentStep?: string | null
  inputSummaryJson?: string | null
  outputSummaryJson?: string | null
  errorCode?: string | null
  errorSummary?: string | null
  traceId?: string | null
  retryOfExecutionId?: string | null
  cancelRequested?: boolean
  startedAt?: string | null
  endedAt?: string | null
  createdAt: string
}

/** 工作流测试运行响应；测试执行由后端异步 Worker 处理。 */
export interface WorkflowTestRunAccepted {
  executionId: string
  status: 'QUEUED' | 'RUNNING' | string
  dryRun?: boolean
}

export interface WorkflowScheduleInfo {
  scheduleId: string
  workflowId: string
  version?: number
  cron: string
  timezone: string
  misfirePolicy: 'SKIP' | 'FIRE_ONCE' | 'CATCH_UP_LIMITED' | string
  enabled: boolean
  nextFireAt?: string | null
  lastFireAt?: string | null
}

export interface CapabilityInfo {
  capabilityKey: string
  /** 服务端数据库使用 API；保留 PLATFORM_API 兼容早期设计文档与历史投影。 */
  sourceType: 'BUILTIN' | 'API' | 'PLATFORM_API' | 'PLUGIN' | 'LOCAL_PLUGIN'
  sourceId?: string
  sourceVersion?: string
  displayName: string
  description: string
  inputSchemaJson: string
  outputSchemaJson: string
  requiredPermissionsJson: string
  availabilityPolicyJson: string
  status: string
  revision: number
}

export interface WorkflowApiResponse<T> {
  code: string
  message: string
  data: T
  requestId: string
}

const idempotency = () => crypto.randomUUID()

export function listWorkflowsApi(page = 1, size = 50): Promise<WorkflowApiResponse<WorkflowInfo[]>> {
  return get('workflows', { page, size })
}

export function getWorkflowApi(workflowId: string): Promise<WorkflowApiResponse<any>> {
  return get(`workflows/${workflowId}`)
}

export function getLatestWorkflowVersionApi(workflowId: string): Promise<WorkflowApiResponse<WorkflowVersionInfo>> {
  return get<WorkflowApiResponse<WorkflowVersionInfo>>(`workflows/${workflowId}/versions/latest`)
}

/**
 * 获取工作流版本列表。
 * [IDE-API-WORKFLOW-VERSION] 当前后端仅实现 `/versions/latest`，该列表路径为后端缺口；
 * IDE 版本选择器应先调用最新版本接口，服务端支持列表后再启用此函数。
 */
export function listWorkflowVersionsApi(
  workflowId: string,
): Promise<WorkflowApiResponse<WorkflowVersionInfo[]>> {
  return get(`workflows/${workflowId}/versions`)
}

/**
 * 按版本号获取版本投影。当前后端无单版本路径，先从列表接口筛选以便兼容未来实现。
 */
export async function getWorkflowVersionApi(
  workflowId: string,
  version: number,
): Promise<WorkflowApiResponse<WorkflowVersionInfo>> {
  const response = await listWorkflowVersionsApi(workflowId)
  const item = response.data.find((candidate) => candidate.version === version)
  if (!item) throw new Error('工作流版本不存在或当前账号无权访问')
  return { ...response, data: item }
}

export function createWorkflowApi(data: {
  name: string
  slug: string
  description: string
  dsl: string
  graph: Record<string, unknown>
}): Promise<WorkflowApiResponse<WorkflowInfo>> {
  return post('workflows', data, { headers: { 'Idempotency-Key': idempotency() } })
}

/** 创建工作流 IDE 草稿；后端创建接口会同时生成首个可编辑版本。 */
export const createWorkflowDraftApi = createWorkflowApi

export function updateWorkflowApi(
  workflowId: string,
  rowVersion: number,
  data: { name: string; description: string; dsl: string; graph: Record<string, unknown> },
): Promise<WorkflowApiResponse<WorkflowInfo>> {
  return patch(`workflows/${workflowId}`, data, { headers: { 'If-Match': rowVersion } })
}

/**
 * IDE 草稿保存的语义化别名。
 * [IDE-API-WORKFLOW-DRAFT] 现有后端使用 PATCH + If-Match 保存并创建新版本；
 * 保留原 `updateWorkflowApi`，新页面可使用该名称表达“自动保存草稿”而不改请求路径。
 */
export const saveWorkflowDraftApi = updateWorkflowApi

export function archiveWorkflowApi(workflowId: string): Promise<WorkflowApiResponse<{ archived: boolean }>> {
  return del(`workflows/${workflowId}`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function validateWorkflowApi(dsl: string, graph: Record<string, unknown> = {}):
Promise<WorkflowApiResponse<{ valid: boolean; issues: Array<{ code: string; path: string; message: string }>; sha256: string }>> {
  return post('workflows/validate', { dsl, graph })
}

export function publishWorkflowApi(workflowId: string, version: number):
Promise<WorkflowApiResponse<{ published: boolean; version: number }>> {
  return post(`workflows/${workflowId}/versions/${version}/publish`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function runWorkflowApi(
  workflowId: string,
  inputs: Record<string, unknown> = {},
  version?: number,
): Promise<WorkflowApiResponse<WorkflowExecutionInfo>> {
  return post(`workflows/${workflowId}/run`, { inputs, ...(version ? { version } : {}) }, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

export function listWorkflowExecutionsApi(
  workflowId: string,
  options: { page?: number; size?: number; status?: string } = {},
): Promise<WorkflowApiResponse<WorkflowExecutionInfo[]>> {
  return get(`workflows/${workflowId}/executions`, {
    page: options.page || 1,
    size: options.size || 50,
    ...(options.status ? { status: options.status } : {}),
  })
}

/** 获取工作流执行详情；后端已实现，适用于 IDE 底部执行日志面板。 */
export function getWorkflowExecutionApi(
  executionId: string,
): Promise<WorkflowApiResponse<WorkflowExecutionInfo>> {
  return get(`workflows/executions/${executionId}`)
}

/**
 * 失败执行重跑；后端已实现并返回 202，必须携带幂等键。
 * 空间上下文由 request.ts 统一注入 X-Space-Id。
 */
export function retryWorkflowExecutionApi(
  executionId: string,
): Promise<WorkflowApiResponse<WorkflowExecutionInfo>> {
  return post(`workflows/executions/${executionId}/retry`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/** 取消排队或运行中的工作流执行；后端已实现，取消请求本身幂等。 */
export function cancelWorkflowExecutionApi(
  executionId: string,
): Promise<WorkflowApiResponse<{ cancelRequested: boolean }>> {
  return post(`workflows/executions/${executionId}/cancel`, undefined, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/**
 * 获取逐节点执行日志。
 * [IDE-API-WORKFLOW-LOG] 当前后端执行详情只返回摘要，未提供 logs 子资源；
 * 保留标准路径，后端上线后 IDE 可直接展示脱敏节点日志，不在前端解析内部存储。
 */
export function getWorkflowExecutionLogsApi(
  executionId: string,
  page = 1,
  size = 100,
): Promise<WorkflowApiResponse<unknown[]>> {
  return get(`workflows/executions/${executionId}/logs`, { page, size })
}

/**
 * 创建工作流测试运行（dry-run）。后端当前没有 test-runs 路径；不可用时应回退到
 * `runWorkflowApi` 的正式执行（需要用户明确确认），默认不自动降级以避免副作用。
 */
export function runWorkflowTestApi(
  workflowId: string,
  inputs: Record<string, unknown> = {},
  version?: number,
): Promise<WorkflowApiResponse<WorkflowTestRunAccepted>> {
  return post(`workflows/${workflowId}/test-runs`, {
    inputs,
    ...(version ? { version } : {}),
    dry_run: true,
  }, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/** 创建定时触发器；后端已实现，IDE 可在属性面板配置。 */
export function createWorkflowScheduleApi(
  workflowId: string,
  data: {
    version?: number
    cron: string
    timezone: string
    misfirePolicy: 'SKIP' | 'FIRE_ONCE' | 'CATCH_UP_LIMITED'
    inputs?: Record<string, unknown>
  },
): Promise<WorkflowApiResponse<WorkflowScheduleInfo>> {
  return post(`workflows/${workflowId}/schedules`, {
    ...data,
    misfire_policy: data.misfirePolicy,
  }, { headers: { 'Idempotency-Key': idempotency() } })
}

/** 查询工作流定时触发器；后端已实现。 */
export function listWorkflowSchedulesApi(
  workflowId: string,
): Promise<WorkflowApiResponse<WorkflowScheduleInfo[]>> {
  return get(`workflows/${workflowId}/schedules`)
}

/** 更新定时触发器启停状态；后端通过 query 参数 enabled 接收。 */
export function setWorkflowScheduleEnabledApi(
  workflowId: string,
  scheduleId: string,
  enabled: boolean,
): Promise<WorkflowApiResponse<WorkflowScheduleInfo>> {
  return patch(`workflows/${workflowId}/schedules/${scheduleId}`, undefined, { params: { enabled } })
}

export function listCapabilitiesApi(sourceType = '', query = ''):
Promise<WorkflowApiResponse<CapabilityInfo[]>> {
  return get('capabilities', { sourceType: sourceType || undefined, query: query || undefined, page: 1, size: 100 })
}

export interface WorkflowMarketplaceItem {
  workflowId: string
  name: string
  slug: string
  description: string
  categoryCode: string
  tagsJson: string
  installCount: number
  ratingAverage: number
  ratingCount: number
  publishedAt: string
}

export function listWorkflowMarketplaceApi(query = ''):
Promise<WorkflowApiResponse<WorkflowMarketplaceItem[]>> {
  return get('marketplace/workflows', { query, page: 1, size: 48 })
}

export function importWorkflowTemplateApi(
  workflowId: string,
  name: string,
  slug: string,
): Promise<WorkflowApiResponse<WorkflowInfo>> {
  return post(`marketplace/workflows/${workflowId}/import`, { name, slug }, {
    headers: { 'Idempotency-Key': idempotency() },
  })
}

/** 将已发布工作流提交到模板市场审核。 */
export function submitWorkflowMarketplaceApi(workflowId: string):
Promise<WorkflowApiResponse<{ review_status: 'PENDING' }>> {
  return post(`marketplace/workflows/${workflowId}/submit`)
}
