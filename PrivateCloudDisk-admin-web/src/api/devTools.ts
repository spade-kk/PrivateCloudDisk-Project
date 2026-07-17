// ============================================================
// 开发工具 API
// Swagger 文档、API 管理、OpenAPI 对接
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, PageParams } from '@/types/api'

// ── Swagger / OpenAPI 相关类型 ─────────────────────────────

export interface SwaggerService {
  id: string
  name: string
  description: string
  version: string
  baseUrl: string
  specUrl: string
  specVersion: '2.0' | '3.0' | '3.1'
  status: 'AVAILABLE' | 'UNAVAILABLE' | 'ERROR'
  lastChecked: string
  tags: string[]
  pathCount: number
  schemaCount: number
}

export interface SwaggerEndpoint {
  path: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH' | 'HEAD' | 'OPTIONS'
  summary: string
  description: string
  tags: string[]
  operationId: string
  deprecated: boolean
  parameters: SwaggerParameter[]
  requestBody: SwaggerRequestBody | null
  responses: Record<string, SwaggerResponse>
  security: Array<Record<string, string[]>>
}

export interface SwaggerParameter {
  name: string
  in: 'query' | 'header' | 'path' | 'cookie'
  required: boolean
  description: string
  schema: SwaggerSchema
  example: unknown
}

export interface SwaggerRequestBody {
  required: boolean
  description: string
  content: Record<string, { schema: SwaggerSchema; example?: unknown }>
}

export interface SwaggerResponse {
  description: string
  content: Record<string, { schema: SwaggerSchema }>
  headers: Record<string, SwaggerSchema>
}

export interface SwaggerSchema {
  type: string
  format: string
  properties: Record<string, SwaggerSchema>
  items: SwaggerSchema
  required: string[]
  enum: unknown[]
  example: unknown
  description: string
  nullable: boolean
  oneOf: SwaggerSchema[]
  allOf: SwaggerSchema[]
  anyOf: SwaggerSchema[]
  additionalProperties: SwaggerSchema | boolean
  ref: string
}

// ── API 管理相关类型 ──────────────────────────────────────

export interface ApiGateway {
  id: string
  name: string
  type: 'APISIX' | 'KONG' | 'NGINX' | 'TYK' | 'TRAEFIK'
  version: string
  status: 'RUNNING' | 'STOPPED' | 'ERROR'
  upstreamCount: number
  routeCount: number
  serviceCount: number
  pluginCount: number
  consumerCount: number
  sslCount: number
  uptime: number
  nodeCount: number
  dashboardUrl: string
}

export interface ApiRoute {
  id: string
  name: string
  description: string
  uri: string
  uris: string[]
  host: string
  hosts: string[]
  remoteAddr: string
  remoteAddrs: string[]
  methods: string[]
  priority: number
  status: 'ENABLED' | 'DISABLED'
  upstreamId: string
  upstreamName: string
  serviceId: string
  plugins: ApiPlugin[]
  labels: Record<string, string>
  createTime: string
  updateTime: string
}

export interface ApiUpstream {
  id: string
  name: string
  description: string
  type: 'ROUND_ROBIN' | 'LEAST_CONN' | 'CHASH' | 'EWMA'
  scheme: 'HTTP' | 'HTTPS' | 'GRPC' | 'GRPCS'
  nodes: Array<{ host: string; port: number; weight: number; status: 'HEALTHY' | 'UNHEALTHY' }>
  timeout: { connect: number; send: number; read: number }
  retries: number
  retryTimeout: number
  healthCheck: {
    active: {
      type: string
      timeout: number
      concurrency: number
      host: string
      port: number
      httpPath: string
      healthy: { interval: number; successes: number }
      unhealthy: { interval: number; httpFailures: number; tcpFailures: number; timeouts: number }
    }
  }
  labels: Record<string, string>
  createTime: string
  updateTime: string
}

export interface ApiPlugin {
  id: string
  name: string
  description: string
  type: 'AUTH' | 'SECURITY' | 'TRAFFIC' | 'TRANSFORM' | 'LOGGING' | 'METRICS' | 'OTHER'
  enabled: boolean
  config: Record<string, unknown>
  routeId: string
  serviceId: string
  consumerId: string
}

export interface ApiConsumer {
  id: string
  username: string
  description: string
  plugins: ApiPlugin[]
  labels: Record<string, string>
  createTime: string
  updateTime: string
}

export interface ApiSSL {
  id: string
  sni: string
  snis: string[]
  cert: string
  key: string
  expireTime: string
  status: 'VALID' | 'EXPIRING' | 'EXPIRED'
  daysRemaining: number
  labels: Record<string, string>
}

// ── API 测试相关类型 ──────────────────────────────────────

export interface ApiTestCase {
  id: string
  name: string
  description: string
  method: string
  url: string
  headers: Array<{ key: string; value: string }>
  queryParams: Array<{ key: string; value: string }>
  body: string
  bodyType: 'NONE' | 'JSON' | 'FORM' | 'XML' | 'RAW'
  expectedStatus: number
  expectedResponse: string
  timeout: number
  tags: string[]
  lastRunAt: string
  lastRunStatus: 'PASS' | 'FAIL' | 'ERROR' | 'PENDING'
  createTime: string
}

export interface ApiTestResult {
  id: string
  testCaseId: string
  testCaseName: string
  status: 'PASS' | 'FAIL' | 'ERROR'
  statusCode: number
  responseTime: number
  responseSize: number
  responseBody: string
  errorMessage: string
  executedAt: string
  assertions: Array<{ name: string; passed: boolean; message: string }>
}

// ── API 函数 ──────────────────────────────────────────────

// Swagger
export function getSwaggerServicesApi() {
  return request.get<ApiResponse<SwaggerService[]>>('/api/admin/dev/swagger/services')
}

export function getSwaggerServiceDetailApi(serviceId: string) {
  return request.get<ApiResponse<SwaggerService>>(`/api/admin/dev/swagger/services/${serviceId}`)
}

export function getSwaggerEndpointsApi(serviceId: string, params?: { tag?: string; keyword?: string; method?: string }) {
  return request.get<ApiResponse<SwaggerEndpoint[]>>(`/api/admin/dev/swagger/services/${serviceId}/endpoints`, { params })
}

export function getSwaggerEndpointDetailApi(serviceId: string, path: string, method: string) {
  return request.get<ApiResponse<SwaggerEndpoint>>(`/api/admin/dev/swagger/services/${serviceId}/endpoints/detail`, {
    params: { path, method },
  })
}

export function refreshSwaggerServiceApi(serviceId: string) {
  return request.post<ApiResponse<null>>(`/api/admin/dev/swagger/services/${serviceId}/refresh`)
}

export function getSwaggerUIUrlApi(serviceId: string) {
  return request.get<ApiResponse<{ url: string }>>(`/api/admin/dev/swagger/services/${serviceId}/ui-url`)
}

// API 管理
export function getApiGatewayApi() {
  return request.get<ApiResponse<ApiGateway>>('/api/admin/dev/gateway')
}

export function getApiRoutesApi(params?: PageParams & { name?: string; status?: string; method?: string }) {
  return request.get<ApiResponse<PageResult<ApiRoute>>>('/api/admin/dev/gateway/routes', { params })
}

export function getApiRouteDetailApi(routeId: string) {
  return request.get<ApiResponse<ApiRoute>>(`/api/admin/dev/gateway/routes/${routeId}`)
}

export function createApiRouteApi(data: Partial<ApiRoute>) {
  return request.post<ApiResponse<ApiRoute>>('/api/admin/dev/gateway/routes', data)
}

export function updateApiRouteApi(routeId: string, data: Partial<ApiRoute>) {
  return request.put<ApiResponse<ApiRoute>>(`/api/admin/dev/gateway/routes/${routeId}`, data)
}

export function deleteApiRouteApi(routeId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/dev/gateway/routes/${routeId}`)
}

export function toggleApiRouteApi(routeId: string, enabled: boolean) {
  return request.put<ApiResponse<null>>(`/api/admin/dev/gateway/routes/${routeId}/status`, { enabled })
}

export function getApiUpstreamsApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<ApiUpstream>>>('/api/admin/dev/gateway/upstreams', { params })
}

export function getApiPluginsApi(params?: PageParams & { type?: string }) {
  return request.get<ApiResponse<PageResult<ApiPlugin>>>('/api/admin/dev/gateway/plugins', { params })
}

export function getApiConsumersApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<ApiConsumer>>>('/api/admin/dev/gateway/consumers', { params })
}

export function getApiSSLsApi(params?: PageParams & { status?: string }) {
  return request.get<ApiResponse<PageResult<ApiSSL>>>('/api/admin/dev/gateway/ssl', { params })
}

// API 测试
export function getApiTestCasesApi(params?: PageParams & { tag?: string }) {
  return request.get<ApiResponse<PageResult<ApiTestCase>>>('/api/admin/dev/tests', { params })
}

export function createApiTestCaseApi(data: Partial<ApiTestCase>) {
  return request.post<ApiResponse<ApiTestCase>>('/api/admin/dev/tests', data)
}

export function updateApiTestCaseApi(testId: string, data: Partial<ApiTestCase>) {
  return request.put<ApiResponse<ApiTestCase>>(`/api/admin/dev/tests/${testId}`, data)
}

export function deleteApiTestCaseApi(testId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/dev/tests/${testId}`)
}

export function executeApiTestApi(testId: string) {
  return request.post<ApiResponse<ApiTestResult>>(`/api/admin/dev/tests/${testId}/execute`)
}

export function executeAllApiTestsApi() {
  return request.post<ApiResponse<ApiTestResult[]>>('/api/admin/dev/tests/execute-all')
}

export function getApiTestResultsApi(params?: PageParams & { testCaseId?: string; status?: string }) {
  return request.get<ApiResponse<PageResult<ApiTestResult>>>('/api/admin/dev/tests/results', { params })
}

// OpenAPI
export function getOpenAPISpecsApi() {
  return request.get<ApiResponse<SwaggerService[]>>('/api/admin/dev/openapi/specs')
}

export function importOpenAPISpecApi(data: { url: string; name: string }) {
  return request.post<ApiResponse<SwaggerService>>('/api/admin/dev/openapi/import', data)
}

export function exportOpenAPISpecApi(serviceId: string) {
  return request.get<ApiResponse<{ content: string }>>(`/api/admin/dev/openapi/export/${serviceId}`)
}

export function getOpenAPIClientCodeApi(serviceId: string, language: string) {
  return request.get<ApiResponse<{ code: string; language: string }>>(`/api/admin/dev/openapi/generate/${serviceId}`, {
    params: { language },
  })
}