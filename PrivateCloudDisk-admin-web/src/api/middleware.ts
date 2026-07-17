// ============================================================
// 第三方中间件管理 API
// Nacos、RabbitMQ、XXL-Job、MinIO、OpenSearch
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, PageParams } from '@/types/api'

// ── Nacos 相关类型 ────────────────────────────────────────

export interface NacosService {
  serviceId: string
  serviceName: string
  groupName: string
  clusterCount: number
  instanceCount: number
  healthyInstanceCount: number
  protectThreshold: number
  selector: string
  metadata: Record<string, string>
}

export interface NacosConfig {
  configId: string
  dataId: string
  group: string
  content: string
  md5: string
  type: 'TEXT' | 'JSON' | 'XML' | 'YAML' | 'PROPERTIES' | 'HTML'
  tenant: string
  appName: string
  description: string
  createTime: string
  modifyTime: string
}

export interface NacosInstance {
  instanceId: string
  ip: string
  port: number
  weight: number
  healthy: boolean
  enabled: boolean
  ephemeral: boolean
  clusterName: string
  serviceName: string
  metadata: Record<string, string>
  lastBeat: string
}

export interface NacosNamespace {
  namespace: string
  namespaceShowName: string
  quota: number
  configCount: number
  type: number
}

// ── RabbitMQ 相关类型 ─────────────────────────────────────

export interface RabbitMQOverview {
  rabbitmqVersion: string
  erlangVersion: string
  clusterName: string
  nodeCount: number
  queueCount: number
  connectionCount: number
  channelCount: number
  exchangeCount: number
  consumerCount: number
  messagesTotal: number
  messagesReady: number
  messagesUnacknowledged: number
  messagesRate: number
  publishRate: number
  deliverRate: number
  ackRate: number
}

export interface RabbitMQQueue {
  name: string
  vhost: string
  node: string
  state: 'RUNNING' | 'IDLE' | 'FLOW' | 'DOWN'
  ready: number
  unacknowledged: number
  total: number
  messageRate: number
  consumerCount: number
  consumerUtilisation: number
  memory: number
  messagesPersistent: number
  messagesBytes: number
  policy: string
  arguments: Record<string, unknown>
}

export interface RabbitMQExchange {
  name: string
  vhost: string
  type: 'direct' | 'topic' | 'fanout' | 'headers'
  durable: boolean
  autoDelete: boolean
  internal: boolean
  messageRateIn: number
  messageRateOut: number
}

export interface RabbitMQConnection {
  name: string
  node: string
  vhost: string
  user: string
  state: 'RUNNING' | 'FLOW' | 'BLOCKED' | 'BLOCKING'
  channels: number
  protocol: string
  host: string
  port: number
  peerHost: string
  peerPort: number
  ssl: boolean
  connectedAt: string
}

export interface RabbitMQNode {
  name: string
  type: 'DISC' | 'RAM'
  running: boolean
  uptime: number
  memoryUsed: number
  memoryLimit: number
  memoryAlarm: boolean
  diskFree: number
  diskFreeLimit: number
  diskFreeAlarm: boolean
  fdUsed: number
  fdTotal: number
  socketsUsed: number
  socketsTotal: number
  procUsed: number
  procTotal: number
}

// ── XXL-Job 相关类型 ──────────────────────────────────────

export interface XXLJobGroup {
  id: number
  appName: string
  title: string
  addressType: number
  addressList: string
  registryList: string[]
}

export interface XXLJobTask {
  id: number
  jobGroup: number
  jobGroupName: string
  jobDesc: string
  author: string
  alarmEmail: string
  scheduleType: string
  scheduleConf: string
  misfireStrategy: string
  executorRouteStrategy: string
  executorHandler: string
  executorParam: string
  executorBlockStrategy: string
  executorTimeout: number
  executorFailRetryCount: number
  glueType: string
  glueSource: string
  glueRemark: string
  glueUpdatetime: string
  childJobId: string
  triggerStatus: number
  triggerLastTime: string
  triggerNextTime: string
  addTime: string
  updateTime: string
}

export interface XXLJobLog {
  id: number
  jobGroup: number
  jobId: number
  jobDesc: string
  executorAddress: string
  executorHandler: string
  executorParam: string
  executorShardingParam: string
  executorFailRetryCount: number
  triggerTime: string
  triggerCode: number
  triggerMsg: string
  handleTime: string
  handleCode: number
  handleMsg: string
  alarmStatus: number
}

// ── MinIO 相关类型 ────────────────────────────────────────

export interface MinIOBucket {
  name: string
  created: string
  size: number
  objectCount: number
  versioning: boolean
  encryption: boolean
  objectLocking: boolean
  quota: number
  policy: string
  tags: Record<string, string>
}

export interface MinIOObject {
  name: string
  bucket: string
  size: number
  lastModified: string
  etag: string
  contentType: string
  versionId: string
  isDeleteMarker: boolean
  isLatest: boolean
  storageClass: string
  metadata: Record<string, string>
}

export interface MinIOUser {
  accessKey: string
  status: 'ENABLED' | 'DISABLED'
  policyName: string
  memberOf: string[]
  createdAt: string
}

export interface MinIOPolicy {
  name: string
  description: string
  statements: Array<{
    effect: string
    actions: string[]
    resources: string[]
  }>
}

// ── OpenSearch 相关类型 ───────────────────────────────────

export interface OpenSearchCluster {
  clusterName: string
  status: 'GREEN' | 'YELLOW' | 'RED'
  numberOfNodes: number
  numberOfDataNodes: number
  activePrimaryShards: number
  activeShards: number
  relocatingShards: number
  initializingShards: number
  unassignedShards: number
  delayedUnassignedShards: number
  activeShardsPercent: number
  taskMaxWaitingTime: number
  pendingTasks: number
}

export interface OpenSearchNode {
  name: string
  ip: string
  role: 'MASTER' | 'DATA' | 'INGEST' | 'ML' | 'COORDINATING'
  heapUsed: number
  heapMax: number
  heapPercent: number
  diskUsed: number
  diskTotal: number
  diskPercent: number
  cpu: number
  loadAverage: number
  uptime: string
  version: string
}

export interface OpenSearchIndex {
  name: string
  health: 'GREEN' | 'YELLOW' | 'RED'
  status: 'OPEN' | 'CLOSE'
  primaryShards: number
  replicaShards: number
  docsCount: number
  docsDeleted: number
  storeSize: number
  primaryStoreSize: number
  creationDate: string
}

export interface OpenSearchQuery {
  index: string
  query: Record<string, unknown>
  from: number
  size: number
  sort: Array<Record<string, { order: 'asc' | 'desc' }>>
}

// ── API 函数 ──────────────────────────────────────────────

// Nacos
export function getNacosServicesApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<NacosService>>>('/api/admin/middleware/nacos/services', { params })
}

export function getNacosServiceDetailApi(serviceName: string) {
  return request.get<ApiResponse<NacosService>>(`/api/admin/middleware/nacos/services/${encodeURIComponent(serviceName)}`)
}

export function getNacosInstancesApi(serviceName: string, params?: PageParams) {
  return request.get<ApiResponse<PageResult<NacosInstance>>>(`/api/admin/middleware/nacos/services/${encodeURIComponent(serviceName)}/instances`, { params })
}

export function getNacosConfigsApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<NacosConfig>>>('/api/admin/middleware/nacos/configs', { params })
}

export function getNacosConfigDetailApi(dataId: string, group: string) {
  return request.get<ApiResponse<NacosConfig>>('/api/admin/middleware/nacos/configs/detail', { params: { dataId, group } })
}

export function updateNacosConfigApi(data: { dataId: string; group: string; content: string; type: string }) {
  return request.put<ApiResponse<null>>('/api/admin/middleware/nacos/configs', data)
}

export function getNacosConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/middleware/nacos/console-url')
}

// RabbitMQ
export function getRabbitMQOverviewApi() {
  return request.get<ApiResponse<RabbitMQOverview>>('/api/admin/middleware/rabbitmq/overview')
}

export function getRabbitMQQueuesApi(params?: PageParams & { vhost?: string }) {
  return request.get<ApiResponse<PageResult<RabbitMQQueue>>>('/api/admin/middleware/rabbitmq/queues', { params })
}

export function getRabbitMQExchangesApi(params?: PageParams & { vhost?: string }) {
  return request.get<ApiResponse<PageResult<RabbitMQExchange>>>('/api/admin/middleware/rabbitmq/exchanges', { params })
}

export function getRabbitMQConnectionsApi() {
  return request.get<ApiResponse<RabbitMQConnection[]>>('/api/admin/middleware/rabbitmq/connections')
}

export function getRabbitMQNodesApi() {
  return request.get<ApiResponse<RabbitMQNode[]>>('/api/admin/middleware/rabbitmq/nodes')
}

export function purgeRabbitMQQueueApi(vhost: string, queueName: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/middleware/rabbitmq/queues/${encodeURIComponent(vhost)}/${encodeURIComponent(queueName)}/contents`)
}

export function getRabbitMQConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/middleware/rabbitmq/console-url')
}

// XXL-Job
export function getXXLJobGroupsApi() {
  return request.get<ApiResponse<XXLJobGroup[]>>('/api/admin/middleware/xxljob/groups')
}

export function getXXLJobTasksApi(params?: PageParams & { jobGroup?: number; jobDesc?: string; triggerStatus?: number }) {
  return request.get<ApiResponse<PageResult<XXLJobTask>>>('/api/admin/middleware/xxljob/tasks', { params })
}

export function executeXXLJobTaskApi(taskId: number, executorParam?: string) {
  return request.post<ApiResponse<null>>(`/api/admin/middleware/xxljob/tasks/${taskId}/trigger`, { executorParam })
}

export function pauseXXLJobTaskApi(taskId: number) {
  return request.post<ApiResponse<null>>(`/api/admin/middleware/xxljob/tasks/${taskId}/pause`)
}

export function resumeXXLJobTaskApi(taskId: number) {
  return request.post<ApiResponse<null>>(`/api/admin/middleware/xxljob/tasks/${taskId}/resume`)
}

export function getXXLJobLogsApi(params?: PageParams & { jobId?: number; jobGroup?: number; triggerStatus?: number }) {
  return request.get<ApiResponse<PageResult<XXLJobLog>>>('/api/admin/middleware/xxljob/logs', { params })
}

export function getXXLJobConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/middleware/xxljob/console-url')
}

// MinIO
export function getMinIOBucketsApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<MinIOBucket>>>('/api/admin/middleware/minio/buckets', { params })
}

export function getMinIOObjectsApi(bucketName: string, params?: PageParams & { prefix?: string }) {
  return request.get<ApiResponse<PageResult<MinIOObject>>>(`/api/admin/middleware/minio/buckets/${encodeURIComponent(bucketName)}/objects`, { params })
}

export function getMinIOUsersApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<MinIOUser>>>('/api/admin/middleware/minio/users', { params })
}

export function getMinIOPoliciesApi() {
  return request.get<ApiResponse<MinIOPolicy[]>>('/api/admin/middleware/minio/policies')
}

export function createMinIOBucketApi(data: { name: string; region: string; versioning: boolean; objectLocking: boolean }) {
  return request.post<ApiResponse<null>>('/api/admin/middleware/minio/buckets', data)
}

export function deleteMinIOBucketApi(bucketName: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/middleware/minio/buckets/${encodeURIComponent(bucketName)}`)
}

export function getMinIOConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/middleware/minio/console-url')
}

// OpenSearch
export function getOpenSearchClusterHealthApi() {
  return request.get<ApiResponse<OpenSearchCluster>>('/api/admin/middleware/opensearch/cluster/health')
}

export function getOpenSearchNodesApi() {
  return request.get<ApiResponse<OpenSearchNode[]>>('/api/admin/middleware/opensearch/nodes')
}

export function getOpenSearchIndicesApi(params?: PageParams & { name?: string; health?: string }) {
  return request.get<ApiResponse<PageResult<OpenSearchIndex>>>('/api/admin/middleware/opensearch/indices', { params })
}

export function searchOpenSearchApi(data: OpenSearchQuery) {
  return request.post<ApiResponse<{ total: number; hits: Array<{ index: string; id: string; score: number; source: Record<string, unknown> }> }>>('/api/admin/middleware/opensearch/search', data)
}

export function deleteOpenSearchIndexApi(indexName: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/middleware/opensearch/indices/${encodeURIComponent(indexName)}`)
}

export function closeOpenSearchIndexApi(indexName: string) {
  return request.post<ApiResponse<null>>(`/api/admin/middleware/opensearch/indices/${encodeURIComponent(indexName)}/close`)
}

export function openOpenSearchIndexApi(indexName: string) {
  return request.post<ApiResponse<null>>(`/api/admin/middleware/opensearch/indices/${encodeURIComponent(indexName)}/open`)
}

export function getOpenSearchConsoleUrlApi() {
  return request.get<ApiResponse<{ url: string }>>('/api/admin/middleware/opensearch/console-url')
}