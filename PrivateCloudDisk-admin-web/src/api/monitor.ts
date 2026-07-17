// ============================================================
// 监控集成 API
// Grafana、SkyWalking、Prometheus
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse } from '@/types/api'

// ── Grafana 相关类型 ──────────────────────────────────────

export interface GrafanaDashboard {
  id: number
  uid: string
  title: string
  uri: string
  url: string
  slug: string
  type: string
  tags: string[]
  isStarred: boolean
  folderId: number
  folderTitle: string
  folderUid: string
  created: string
  updated: string
}

export interface GrafanaDataSource {
  id: number
  uid: string
  name: string
  type: string
  url: string
  access: string
  isDefault: boolean
  jsonData: Record<string, unknown>
  readOnly: boolean
}

export interface GrafanaAlert {
  id: number
  dashboardUid: string
  panelId: number
  name: string
  state: 'OK' | 'ALERTING' | 'PENDING' | 'NO_DATA' | 'ERROR'
  newStateDate: string
  evalData: Record<string, unknown>
  executionError: string
  url: string
}

export interface GrafanaAnnotation {
  dashboardId: number
  panelId: number
  time: number
  timeEnd: number
  tags: string[]
  text: string
}

// ── SkyWalking 相关类型 ───────────────────────────────────

export interface SkyWalkingService {
  id: string
  name: string
  group: string
  shortName: string
  normal: boolean
  layers: string[]
}

export interface SkyWalkingEndpoint {
  id: string
  name: string
  serviceId: string
  serviceName: string
}

export interface SkyWalkingTrace {
  traceId: string
  endpointName: string
  duration: number
  start: string
  isError: boolean
  traceIds: string[]
  spans: SkyWalkingSpan[]
}

export interface SkyWalkingSpan {
  spanId: number
  parentSpanId: number
  serviceCode: string
  serviceInstanceName: string
  startTime: number
  endTime: number
  endpointName: string
  type: string
  peer: string
  component: string
  isError: boolean
  layer: string
  tags: Array<{ key: string; value: string }>
  logs: Array<{ time: number; data: Array<{ key: string; value: string }> }>
}

export interface SkyWalkingMetricValue {
  label: string
  values: Record<string, number>
}

export interface SkyWalkingAlarm {
  id: string
  name: string
  message: string
  startTime: number
  scope: string
  scopeId: string
  ruleName: string
  tags: Array<{ key: string; value: string }>
}

// ── Prometheus 相关类型 ───────────────────────────────────

export interface PrometheusMetric {
  name: string
  help: string
  type: 'GAUGE' | 'COUNTER' | 'HISTOGRAM' | 'SUMMARY'
}

export interface PrometheusQueryResult {
  resultType: 'matrix' | 'vector' | 'scalar' | 'string'
  result: Array<{
    metric: Record<string, string>
    value?: [number, string]
    values?: Array<[number, string]>
  }>
}

export interface PrometheusTarget {
  discoveredLabels: Record<string, string>
  labels: Record<string, string>
  scrapePool: string
  scrapeUrl: string
  lastError: string
  lastScrape: string
  lastScrapeDuration: number
  health: 'UP' | 'DOWN' | 'UNKNOWN'
}

export interface PrometheusRule {
  name: string
  file: string
  rules: Array<{
    name: string
    query: string
    duration: number
    labels: Record<string, string>
    annotations: Record<string, string>
    alerts: Array<{
      state: 'FIRING' | 'PENDING' | 'INACTIVE'
      activeAt: string
      value: string
    }>
    health: 'OK' | 'ERR' | 'UNKNOWN'
    lastError: string
    evaluationTime: number
    lastEvaluation: string
    type: 'RECORDING' | 'ALERTING'
  }>
}

// ── API 函数 ──────────────────────────────────────────────

// Grafana
export function getGrafanaDashboardsApi() {
  return request.get<ApiResponse<GrafanaDashboard[]>>('/api/admin/monitor/grafana/dashboards')
}

export function getGrafanaDashboardByUidApi(uid: string) {
  return request.get<ApiResponse<GrafanaDashboard>>(`/api/admin/monitor/grafana/dashboards/${uid}`)
}

export function getGrafanaDatasourcesApi() {
  return request.get<ApiResponse<GrafanaDataSource[]>>('/api/admin/monitor/grafana/datasources')
}

export function getGrafanaAlertsApi(params?: { state?: string; dashboardId?: number }) {
  return request.get<ApiResponse<GrafanaAlert[]>>('/api/admin/monitor/grafana/alerts', { params })
}

export function getGrafanaEmbedUrlApi(dashboardUid: string, params?: { from?: string; to?: string; theme?: string; kiosk?: string }) {
  return request.get<ApiResponse<{ url: string }>>(`/api/admin/monitor/grafana/embed/${dashboardUid}`, { params })
}

export function getGrafanaConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/monitor/grafana/console-url')
}

// SkyWalking
export function getSkyWalkingServicesApi() {
  return request.get<ApiResponse<SkyWalkingService[]>>('/api/admin/monitor/skywalking/services')
}

export function getSkyWalkingEndpointsApi(serviceId: string) {
  return request.get<ApiResponse<SkyWalkingEndpoint[]>>(`/api/admin/monitor/skywalking/services/${serviceId}/endpoints`)
}

export function getSkyWalkingTracesApi(params: { serviceId?: string; endpointId?: string; traceState?: string; queryOrder?: string; paging?: { pageNum: number; pageSize: number } }) {
  return request.post<ApiResponse<{ total: number; traces: SkyWalkingTrace[] }>>('/api/admin/monitor/skywalking/traces', params)
}

export function getSkyWalkingTraceDetailApi(traceId: string) {
  return request.get<ApiResponse<SkyWalkingTrace>>(`/api/admin/monitor/skywalking/traces/${traceId}`)
}

export function getSkyWalkingMetricsApi(params: { serviceId: string; metricNames: string[]; duration: { start: string; end: string; step: string } }) {
  return request.post<ApiResponse<SkyWalkingMetricValue[]>>('/api/admin/monitor/skywalking/metrics', params)
}

export function getSkyWalkingAlarmsApi(params?: { duration?: { start: string; end: string }; keyword?: string }) {
  return request.post<ApiResponse<{ total: number; items: SkyWalkingAlarm[] }>>('/api/admin/monitor/skywalking/alarms', params)
}

export function getSkyWalkingConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/monitor/skywalking/console-url')
}

// Prometheus
export function getPrometheusMetricsApi() {
  return request.get<ApiResponse<PrometheusMetric[]>>('/api/admin/monitor/prometheus/metrics')
}

export function queryPrometheusApi(query: string, params?: { time?: string; timeout?: string }) {
  return request.get<ApiResponse<PrometheusQueryResult>>('/api/admin/monitor/prometheus/query', { params: { query, ...params } })
}

export function queryRangePrometheusApi(query: string, start: string, end: string, step: string) {
  return request.get<ApiResponse<PrometheusQueryResult>>('/api/admin/monitor/prometheus/query_range', { params: { query, start, end, step } })
}

export function getPrometheusTargetsApi() {
  return request.get<ApiResponse<{ activeTargets: PrometheusTarget[]; droppedTargets: PrometheusTarget[] }>>('/api/admin/monitor/prometheus/targets')
}

export function getPrometheusRulesApi() {
  return request.get<ApiResponse<{ groups: PrometheusRule[] }>>('/api/admin/monitor/prometheus/rules')
}

export function getPrometheusConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/monitor/prometheus/console-url')
}