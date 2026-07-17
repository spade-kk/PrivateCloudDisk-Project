// ============================================================
// 平台运维监控 API
// 涵盖 CPU、内存、磁盘、节点、Docker、Storage、Cluster、Backup
// ============================================================
import { request } from '@/utils/request'
import type { ApiResponse, PageResult, PageParams } from '@/types/api'

// ── 节点监控相关类型 ──────────────────────────────────────

export interface NodeMetrics {
  nodeId: string
  nodeName: string
  hostname: string
  ipAddress: string
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE' | 'DEGRADED'
  osInfo: string
  uptime: number // 运行秒数
  lastHeartbeat: string
  // CPU 指标
  cpu: {
    cores: number
    usagePercent: number
    loadAverage1m: number
    loadAverage5m: number
    loadAverage15m: number
    history: Array<{ timestamp: string; value: number }>
  }
  // 内存指标
  memory: {
    totalBytes: number
    usedBytes: number
    availableBytes: number
    usagePercent: number
    swapTotalBytes: number
    swapUsedBytes: number
    history: Array<{ timestamp: string; value: number }>
  }
  // 磁盘指标
  disk: {
    partitions: Array<{
      mountPoint: string
      device: string
      filesystem: string
      totalBytes: number
      usedBytes: number
      availableBytes: number
      usagePercent: number
      inodesTotal: number
      inodesUsed: number
    }>
    ioReadBytes: number
    ioWriteBytes: number
    ioReadOps: number
    ioWriteOps: number
  }
  // 网络指标
  network: {
    interfaces: Array<{
      name: string
      macAddress: string
      rxBytes: number
      txBytes: number
      rxPackets: number
      txPackets: number
      rxErrors: number
      txErrors: number
    }>
  }
}

export interface NodeMetricsQuery extends PageParams {
  nodeName?: string
  status?: string
  sortBy?: string
}

// ── Docker 管理相关类型 ───────────────────────────────────

export interface DockerContainer {
  containerId: string
  name: string
  image: string
  imageTag: string
  status: 'RUNNING' | 'STOPPED' | 'PAUSED' | 'RESTARTING' | 'EXITED' | 'DEAD'
  created: string
  ports: Array<{ hostPort: number; containerPort: number; protocol: string }>
  cpuUsage: number
  memoryUsageBytes: number
  memoryLimitBytes: number
  networkRxBytes: number
  networkTxBytes: number
  restarts: number
  nodeName: string
  healthStatus: 'HEALTHY' | 'UNHEALTHY' | 'STARTING' | 'NONE'
  env: Array<{ key: string; value: string }>
  volumes: Array<{ hostPath: string; containerPath: string; mode: string }>
}

export interface DockerImage {
  imageId: string
  repository: string
  tag: string
  size: number
  created: string
  usedBy: string[]
}

export interface ContainerLogQuery {
  containerId: string
  tail?: number
  since?: string
  until?: string
}

export interface ContainerLog {
  timestamp: string
  stream: 'stdout' | 'stderr'
  message: string
}

// ── Storage 管理相关类型 ──────────────────────────────────

export interface StoragePool {
  poolId: string
  name: string
  type: 'CEPH' | 'NFS' | 'LOCAL' | 'GLUSTERFS' | 'MINIO' | 'S3'
  status: 'HEALTHY' | 'DEGRADED' | 'ERROR' | 'MAINTENANCE'
  totalBytes: number
  usedBytes: number
  availableBytes: number
  usagePercent: number
  nodeCount: number
  replicationFactor: number
  iops: number
  throughput: number
  latency: number
  nodes: string[]
}

export interface StorageVolume {
  volumeId: string
  name: string
  poolId: string
  poolName: string
  size: number
  used: number
  status: 'AVAILABLE' | 'IN_USE' | 'ERROR' | 'DELETING'
  accessMode: 'RWO' | 'ROX' | 'RWX'
  attachedTo: string[]
  createdAt: string
  snapshotPolicy: string
}

// ── Cluster 管理相关类型 ──────────────────────────────────

export interface ClusterInfo {
  clusterId: string
  name: string
  version: string
  provider: 'KUBERNETES' | 'DOCKER_SWARM' | 'NOMAD'
  status: 'HEALTHY' | 'DEGRADED' | 'ERROR'
  masterNodes: string[]
  workerNodes: string[]
  totalNodes: number
  healthyNodes: number
  cpuCapacity: number
  memoryCapacity: number
  podsTotal: number
  podsRunning: number
  servicesTotal: number
  deploymentsTotal: number
  createdAt: string
}

export interface ClusterNode {
  nodeId: string
  name: string
  role: 'MASTER' | 'WORKER'
  status: 'READY' | 'NOT_READY' | 'UNKNOWN'
  kubeletVersion: string
  containerRuntime: string
  osImage: string
  kernelVersion: string
  cpuCapacity: number
  memoryCapacity: number
  podCapacity: number
  podCount: number
  conditions: Array<{ type: string; status: string; reason: string; message: string }>
  taints: Array<{ key: string; effect: string; value?: string }>
  labels: Record<string, string>
}

// ── Backup 管理相关类型 ───────────────────────────────────

export interface BackupJob {
  jobId: string
  name: string
  type: 'FULL' | 'INCREMENTAL' | 'DIFFERENTIAL' | 'SNAPSHOT'
  target: string
  targetType: 'DATABASE' | 'FILE_SYSTEM' | 'VOLUME' | 'CONFIGURATION' | 'KUBERNETES_RESOURCE'
  status: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'PENDING' | 'SCHEDULED'
  progress: number
  sizeBytes: number
  compressedSizeBytes: number
  startTime: string
  endTime: string
  durationSeconds: number
  schedule: string
  retentionDays: number
  storageLocation: string
  encryptionEnabled: boolean
  compressionEnabled: boolean
  lastError: string
  nodeName: string
}

export interface BackupRestoreJob {
  restoreId: string
  backupJobId: string
  status: 'RUNNING' | 'COMPLETED' | 'FAILED'
  progress: number
  startTime: string
  endTime: string
  targetLocation: string
  error: string
}

// ── API 函数 ──────────────────────────────────────────────

/** 获取所有节点的实时指标 */
export function getNodeMetricsApi(params?: NodeMetricsQuery) {
  return request.get<ApiResponse<PageResult<NodeMetrics>>>('/api/admin/ops/nodes/metrics', { params })
}

/** 获取单个节点详情 */
export function getNodeDetailApi(nodeId: string) {
  return request.get<ApiResponse<NodeMetrics>>(`/api/admin/ops/nodes/${nodeId}`)
}

/** 获取节点历史指标 */
export function getNodeHistoryApi(nodeId: string, metric: 'cpu' | 'memory' | 'disk' | 'network', range: string) {
  return request.get<ApiResponse<Array<{ timestamp: string; value: number }>>>(`/api/admin/ops/nodes/${nodeId}/history`, {
    params: { metric, range },
  })
}

/** 获取 Docker 容器列表 */
export function getContainersApi(params?: PageParams & { nodeName?: string; status?: string; name?: string }) {
  return request.get<ApiResponse<PageResult<DockerContainer>>>('/api/admin/ops/containers', { params })
}

/** 获取单个容器详情 */
export function getContainerDetailApi(containerId: string) {
  return request.get<ApiResponse<DockerContainer>>(`/api/admin/ops/containers/${containerId}`)
}

/** 获取容器日志 */
export function getContainerLogsApi(containerId: string, params?: ContainerLogQuery) {
  return request.get<ApiResponse<ContainerLog[]>>(`/api/admin/ops/containers/${containerId}/logs`, { params })
}

/** 容器操作 */
export function containerActionApi(containerId: string, action: 'start' | 'stop' | 'restart' | 'pause' | 'unpause') {
  return request.post<ApiResponse<null>>(`/api/admin/ops/containers/${containerId}/${action}`)
}

/** 获取 Docker 镜像列表 */
export function getImagesApi(params?: PageParams & { repository?: string }) {
  return request.get<ApiResponse<PageResult<DockerImage>>>('/api/admin/ops/images', { params })
}

/** 删除镜像 */
export function removeImageApi(imageId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/ops/images/${imageId}`)
}

/** 获取存储池列表 */
export function getStoragePoolsApi(params?: PageParams & { type?: string; status?: string }) {
  return request.get<ApiResponse<PageResult<StoragePool>>>('/api/admin/ops/storage/pools', { params })
}

/** 获取存储卷列表 */
export function getStorageVolumesApi(params?: PageParams & { poolId?: string; status?: string }) {
  return request.get<ApiResponse<PageResult<StorageVolume>>>('/api/admin/ops/storage/volumes', { params })
}

/** 创建存储卷 */
export function createStorageVolumeApi(data: { name: string; poolId: string; size: number; accessMode: string }) {
  return request.post<ApiResponse<StorageVolume>>('/api/admin/ops/storage/volumes', data)
}

/** 删除存储卷 */
export function deleteStorageVolumeApi(volumeId: string) {
  return request.delete<ApiResponse<null>>(`/api/admin/ops/storage/volumes/${volumeId}`)
}

/** 获取集群信息 */
export function getClustersApi() {
  return request.get<ApiResponse<ClusterInfo[]>>('/api/admin/ops/clusters')
}

/** 获取集群节点列表 */
export function getClusterNodesApi(clusterId: string, params?: PageParams) {
  return request.get<ApiResponse<PageResult<ClusterNode>>>(`/api/admin/ops/clusters/${clusterId}/nodes`, { params })
}

/** 获取备份任务列表 */
export function getBackupJobsApi(params?: PageParams & { type?: string; status?: string; targetType?: string }) {
  return request.get<ApiResponse<PageResult<BackupJob>>>('/api/admin/ops/backups', { params })
}

/** 创建备份任务 */
export function createBackupJobApi(data: { name: string; type: string; target: string; targetType: string; schedule: string; retentionDays: number }) {
  return request.post<ApiResponse<BackupJob>>('/api/admin/ops/backups', data)
}

/** 执行备份 */
export function executeBackupApi(jobId: string) {
  return request.post<ApiResponse<BackupJob>>(`/api/admin/ops/backups/${jobId}/execute`)
}

/** 取消备份 */
export function cancelBackupApi(jobId: string) {
  return request.post<ApiResponse<null>>(`/api/admin/ops/backups/${jobId}/cancel`)
}

/** 获取恢复任务列表 */
export function getRestoreJobsApi(params?: PageParams) {
  return request.get<ApiResponse<PageResult<BackupRestoreJob>>>('/api/admin/ops/restores', { params })
}

/** 创建恢复任务 */
export function createRestoreJobApi(data: { backupJobId: string; targetLocation: string }) {
  return request.post<ApiResponse<BackupRestoreJob>>('/api/admin/ops/restores', data)
}