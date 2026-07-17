// ============================================================
// 运维监控 Store
// 管理所有运维监控相关状态
// ============================================================
import { create } from 'zustand'
import type { PageResult } from '@/types/api'
import {
  getNodeMetricsApi, getNodeDetailApi, getNodeHistoryApi,
  getContainersApi, getContainerDetailApi, getContainerLogsApi, containerActionApi,
  getImagesApi, removeImageApi,
  getStoragePoolsApi, getStorageVolumesApi, createStorageVolumeApi, deleteStorageVolumeApi,
  getClustersApi, getClusterNodesApi,
  getBackupJobsApi, createBackupJobApi, executeBackupApi, cancelBackupApi,
  getRestoreJobsApi, createRestoreJobApi,
  type NodeMetrics, type DockerContainer, type DockerImage, type ContainerLog,
  type StoragePool, type StorageVolume,
  type ClusterInfo, type ClusterNode,
  type BackupJob, type BackupRestoreJob,
} from '@/api/opsMonitor'

// ── 节点监控状态 ──────────────────────────────────────────

interface NodeMonitorState {
  nodes: NodeMetrics[]
  nodesTotal: number
  currentNode: NodeMetrics | null
  nodeHistory: Array<{ timestamp: string; value: number }>
  loading: boolean
  error: string | null

  fetchNodes: (params?: Record<string, unknown>) => Promise<void>
  fetchNodeDetail: (nodeId: string) => Promise<void>
  fetchNodeHistory: (nodeId: string, metric: 'cpu' | 'memory' | 'disk' | 'network', range: string) => Promise<void>
}

// ── Docker 管理状态 ───────────────────────────────────────

interface DockerState {
  containers: DockerContainer[]
  containersTotal: number
  currentContainer: DockerContainer | null
  containerLogs: ContainerLog[]
  images: DockerImage[]
  imagesTotal: number
  loading: boolean
  error: string | null

  fetchContainers: (params?: Record<string, unknown>) => Promise<void>
  fetchContainerDetail: (containerId: string) => Promise<void>
  fetchContainerLogs: (containerId: string, params?: Record<string, unknown>) => Promise<void>
  doContainerAction: (containerId: string, action: 'start' | 'stop' | 'restart' | 'pause' | 'unpause') => Promise<boolean>
  fetchImages: (params?: Record<string, unknown>) => Promise<void>
  doRemoveImage: (imageId: string) => Promise<boolean>
}

// ── Storage 管理状态 ──────────────────────────────────────

interface StorageState {
  pools: StoragePool[]
  poolsTotal: number
  volumes: StorageVolume[]
  volumesTotal: number
  loading: boolean
  error: string | null

  fetchPools: (params?: Record<string, unknown>) => Promise<void>
  fetchVolumes: (params?: Record<string, unknown>) => Promise<void>
  doCreateVolume: (data: { name: string; poolId: string; size: number; accessMode: string }) => Promise<boolean>
  doDeleteVolume: (volumeId: string) => Promise<boolean>
}

// ── Cluster 管理状态 ──────────────────────────────────────

interface ClusterState {
  clusters: ClusterInfo[]
  currentClusterNodes: ClusterNode[]
  clusterNodesTotal: number
  loading: boolean
  error: string | null

  fetchClusters: () => Promise<void>
  fetchClusterNodes: (clusterId: string, params?: Record<string, unknown>) => Promise<void>
}

// ── Backup 管理状态 ───────────────────────────────────────

interface BackupState {
  jobs: BackupJob[]
  jobsTotal: number
  restoreJobs: BackupRestoreJob[]
  restoreJobsTotal: number
  loading: boolean
  error: string | null

  fetchJobs: (params?: Record<string, unknown>) => Promise<void>
  doCreateJob: (data: { name: string; type: string; target: string; targetType: string; schedule: string; retentionDays: number }) => Promise<boolean>
  doExecuteBackup: (jobId: string) => Promise<boolean>
  doCancelBackup: (jobId: string) => Promise<boolean>
  fetchRestoreJobs: (params?: Record<string, unknown>) => Promise<void>
  doCreateRestore: (data: { backupJobId: string; targetLocation: string }) => Promise<boolean>
}

// ── 组合 Store ────────────────────────────────────────────

interface OpsMonitorState extends NodeMonitorState, DockerState, StorageState, ClusterState, BackupState {
  reset: () => void
}

const initialNodeState: NodeMonitorState = {
  nodes: [],
  nodesTotal: 0,
  currentNode: null,
  nodeHistory: [],
  loading: false,
  error: null,
  fetchNodes: async () => {},
  fetchNodeDetail: async () => {},
  fetchNodeHistory: async () => {},
}

const initialDockerState: DockerState = {
  containers: [],
  containersTotal: 0,
  currentContainer: null,
  containerLogs: [],
  images: [],
  imagesTotal: 0,
  loading: false,
  error: null,
  fetchContainers: async () => {},
  fetchContainerDetail: async () => {},
  fetchContainerLogs: async () => {},
  doContainerAction: async () => false,
  fetchImages: async () => {},
  doRemoveImage: async () => false,
}

const initialStorageState: StorageState = {
  pools: [],
  poolsTotal: 0,
  volumes: [],
  volumesTotal: 0,
  loading: false,
  error: null,
  fetchPools: async () => {},
  fetchVolumes: async () => {},
  doCreateVolume: async () => false,
  doDeleteVolume: async () => false,
}

const initialClusterState: ClusterState = {
  clusters: [],
  currentClusterNodes: [],
  clusterNodesTotal: 0,
  loading: false,
  error: null,
  fetchClusters: async () => {},
  fetchClusterNodes: async () => {},
}

const initialBackupState: BackupState = {
  jobs: [],
  jobsTotal: 0,
  restoreJobs: [],
  restoreJobsTotal: 0,
  loading: false,
  error: null,
  fetchJobs: async () => {},
  doCreateJob: async () => false,
  doExecuteBackup: async () => false,
  doCancelBackup: async () => false,
  fetchRestoreJobs: async () => {},
  doCreateRestore: async () => false,
}

export const useOpsMonitorStore = create<OpsMonitorState>((set, get) => ({
  ...initialNodeState,
  ...initialDockerState,
  ...initialStorageState,
  ...initialClusterState,
  ...initialBackupState,

  // ── 节点监控 ──────────────────────────────────────────
  fetchNodes: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getNodeMetricsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<NodeMetrics>
        set({ nodes: data.list || data.records || [], nodesTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取节点监控失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchNodeDetail: async (nodeId) => {
    set({ loading: true, error: null })
    try {
      const res = await getNodeDetailApi(nodeId)
      if (res.data.code === 200) {
        set({ currentNode: res.data.data, loading: false })
      } else {
        set({ error: res.data.message || '获取节点详情失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchNodeHistory: async (nodeId, metric, range) => {
    try {
      const res = await getNodeHistoryApi(nodeId, metric, range)
      if (res.data.code === 200) {
        set({ nodeHistory: res.data.data || [] })
      }
    } catch {
      // 静默失败
    }
  },

  // ── Docker 管理 ───────────────────────────────────────
  fetchContainers: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getContainersApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<DockerContainer>
        set({ containers: data.list || data.records || [], containersTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取容器列表失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchContainerDetail: async (containerId) => {
    set({ loading: true, error: null })
    try {
      const res = await getContainerDetailApi(containerId)
      if (res.data.code === 200) {
        set({ currentContainer: res.data.data, loading: false })
      } else {
        set({ error: res.data.message || '获取容器详情失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchContainerLogs: async (containerId, params) => {
    try {
      const res = await getContainerLogsApi(containerId, params)
      if (res.data.code === 200) {
        set({ containerLogs: res.data.data || [] })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg })
    }
  },

  doContainerAction: async (containerId, action) => {
    try {
      const res = await containerActionApi(containerId, action)
      return res.data.code === 200
    } catch {
      return false
    }
  },

  fetchImages: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getImagesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<DockerImage>
        set({ images: data.list || data.records || [], imagesTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取镜像列表失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  doRemoveImage: async (imageId) => {
    try {
      const res = await removeImageApi(imageId)
      if (res.data.code === 200) {
        const { images } = get()
        set({ images: images.filter((img) => img.imageId !== imageId), imagesTotal: get().imagesTotal - 1 })
        return true
      }
      return false
    } catch {
      return false
    }
  },

  // ── Storage 管理 ──────────────────────────────────────
  fetchPools: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getStoragePoolsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<StoragePool>
        set({ pools: data.list || data.records || [], poolsTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取存储池列表失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchVolumes: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getStorageVolumesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<StorageVolume>
        set({ volumes: data.list || data.records || [], volumesTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取存储卷列表失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  doCreateVolume: async (data) => {
    try {
      const res = await createStorageVolumeApi(data)
      if (res.data.code === 200) {
        const { volumes, volumesTotal } = get()
        const newVolume = res.data.data
        if (newVolume) {
          set({ volumes: [newVolume, ...volumes], volumesTotal: volumesTotal + 1 })
        }
        return true
      }
      return false
    } catch {
      return false
    }
  },

  doDeleteVolume: async (volumeId) => {
    try {
      const res = await deleteStorageVolumeApi(volumeId)
      if (res.data.code === 200) {
        const { volumes } = get()
        set({ volumes: volumes.filter((v) => v.volumeId !== volumeId), volumesTotal: get().volumesTotal - 1 })
        return true
      }
      return false
    } catch {
      return false
    }
  },

  // ── Cluster 管理 ──────────────────────────────────────
  fetchClusters: async () => {
    set({ loading: true, error: null })
    try {
      const res = await getClustersApi()
      if (res.data.code === 200) {
        set({ clusters: res.data.data || [], loading: false })
      } else {
        set({ error: res.data.message || '获取集群列表失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  fetchClusterNodes: async (clusterId, params) => {
    set({ loading: true, error: null })
    try {
      const res = await getClusterNodesApi(clusterId, params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ClusterNode>
        set({ currentClusterNodes: data.list || data.records || [], clusterNodesTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取集群节点失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  // ── Backup 管理 ───────────────────────────────────────
  fetchJobs: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getBackupJobsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<BackupJob>
        set({ jobs: data.list || data.records || [], jobsTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取备份任务失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  doCreateJob: async (data) => {
    try {
      const res = await createBackupJobApi(data)
      if (res.data.code === 200) {
        const { jobs, jobsTotal } = get()
        const newJob = res.data.data
        if (newJob) {
          set({ jobs: [newJob, ...jobs], jobsTotal: jobsTotal + 1 })
        }
        return true
      }
      return false
    } catch {
      return false
    }
  },

  doExecuteBackup: async (jobId) => {
    try {
      const res = await executeBackupApi(jobId)
      return res.data.code === 200
    } catch {
      return false
    }
  },

  doCancelBackup: async (jobId) => {
    try {
      const res = await cancelBackupApi(jobId)
      return res.data.code === 200
    } catch {
      return false
    }
  },

  fetchRestoreJobs: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getRestoreJobsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<BackupRestoreJob>
        set({ restoreJobs: data.list || data.records || [], restoreJobsTotal: data.total, loading: false })
      } else {
        set({ error: res.data.message || '获取恢复任务失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  doCreateRestore: async (data) => {
    try {
      const res = await createRestoreJobApi(data)
      return res.data.code === 200
    } catch {
      return false
    }
  },

  reset: () => {
    set({
      ...initialNodeState,
      ...initialDockerState,
      ...initialStorageState,
      ...initialClusterState,
      ...initialBackupState,
    })
  },
}))