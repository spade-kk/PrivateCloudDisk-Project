// ============================================================
// 日志系统 API
// Loki、Kibana
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageParams } from '@/types/api'

// ── Loki 相关类型 ─────────────────────────────────────────

export interface LokiLabel {
  name: string
  values: string[]
}

export interface LokiStream {
  stream: Record<string, string>
  values: Array<[string, string]> // [timestamp_ns, log_line]
}

export interface LokiQueryResult {
  status: string
  data: {
    resultType: string
    result: Array<{
      stream: Record<string, string>
      values: Array<[string, string]>
    }>
    stats: {
      summary: {
        bytesProcessedPerSecond: number
        linesProcessedPerSecond: number
        totalBytesProcessed: number
        totalLinesProcessed: number
        execTime: number
      }
    }
  }
}

export interface LokiQueryParams {
  query: string
  start: string // ISO 8601
  end: string // ISO 8601
  limit?: number
  direction?: 'forward' | 'backward'
}

export interface LogEntry {
  timestamp: string
  labels: Record<string, string>
  line: string
  level: 'DEBUG' | 'INFO' | 'WARN' | 'ERROR' | 'FATAL' | 'TRACE'
  service: string
  instance: string
}

// ── Kibana 相关类型 ───────────────────────────────────────

export interface KibanaIndexPattern {
  id: string
  title: string
  timeFieldName: string
  fields: string
}

export interface KibanaSavedSearch {
  id: string
  title: string
  description: string
  columns: string[]
  sort: Array<[string, string]>
  filters: Array<{
    field: string
    value: string
    operator: string
  }>
  timeRange: { from: string; to: string }
}

export interface KibanaDashboard {
  id: string
  title: string
  description: string
  panels: Array<{
    id: string
    title: string
    type: string
    gridData: { x: number; y: number; w: number; h: number }
  }>
}

export interface ElasticsearchQuery {
  index: string
  query: Record<string, unknown>
  from: number
  size: number
  sort: Array<Record<string, { order: 'asc' | 'desc' }>>
  _source: string[]
  highlight?: {
    fields: Record<string, Record<string, unknown>>
  }
}

export interface ElasticsearchHit {
  _index: string
  _id: string
  _score: number
  _source: Record<string, unknown>
  highlight?: Record<string, string[]>
}

// ── API 函数 ──────────────────────────────────────────────

// Loki
export function getLokiLabelsApi() {
  return request.get<ApiResponse<LokiLabel[]>>('/api/admin/logs/loki/labels')
}

export function getLokiLabelValuesApi(labelName: string) {
  return request.get<ApiResponse<string[]>>(`/api/admin/logs/loki/labels/${labelName}/values`)
}

export function queryLokiApi(params: LokiQueryParams) {
  return request.get<ApiResponse<LokiQueryResult>>('/api/admin/logs/loki/query', { params })
}

export function queryLokiRangeApi(params: LokiQueryParams) {
  return request.get<ApiResponse<LokiQueryResult>>('/api/admin/logs/loki/query_range', { params })
}

export function getLokiTailApi(params: { query: string; limit?: number }) {
  // SSE 流式请求，不使用 axios
  const baseUrl = import.meta.env.VITE_API_BASE_URL || ''
  const token = localStorage.getItem('access_token')
  const url = new URL(`${baseUrl}/api/admin/logs/loki/tail`)
  url.searchParams.set('query', params.query)
  if (params.limit) url.searchParams.set('limit', String(params.limit))

  return new EventSource(`${url.toString()}&token=${encodeURIComponent(token || '')}`)
}

export function getLokiConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/logs/loki/console-url')
}

// Kibana
export function getKibanaIndexPatternsApi() {
  return request.get<ApiResponse<KibanaIndexPattern[]>>('/api/admin/logs/kibana/index-patterns')
}

export function getKibanaSavedSearchesApi() {
  return request.get<ApiResponse<KibanaSavedSearch[]>>('/api/admin/logs/kibana/saved-searches')
}

export function getKibanaDashboardsApi() {
  return request.get<ApiResponse<KibanaDashboard[]>>('/api/admin/logs/kibana/dashboards')
}

export function searchElasticsearchApi(data: ElasticsearchQuery) {
  return request.post<ApiResponse<{ total: number; hits: ElasticsearchHit[]; aggregations?: Record<string, unknown> }>>('/api/admin/logs/kibana/search', data)
}

export function getKibanaEmbedUrlApi(dashboardId: string) {
  return request.get<ApiResponse<{ url: string }>>(`/api/admin/logs/kibana/embed/${dashboardId}`)
}

export function getKibanaConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/logs/kibana/console-url')
}

// ── 通用日志查询 ──────────────────────────────────────────

export interface LogQueryParams extends PageParams {
  startTime?: string
  endTime?: string
  level?: string
  service?: string
  keyword?: string
  sortField?: string
  sortOrder?: 'asc' | 'desc'
}

export function queryLogsApi(params: LogQueryParams) {
  return request.get<ApiResponse<{ total: number; logs: LogEntry[] }>>('/api/admin/logs/query', { params })
}

export function getLogLevelsApi() {
  return request.get<ApiResponse<Array<{ level: string; count: number }>>>('/api/admin/logs/levels')
}

export function getLogServicesApi() {
  return request.get<ApiResponse<string[]>>('/api/admin/logs/services')
}