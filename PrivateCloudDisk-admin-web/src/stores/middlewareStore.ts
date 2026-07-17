// ============================================================
// 中间件管理 Store
// Nacos / RabbitMQ / XXL-Job / MinIO / OpenSearch
// ============================================================
import { create } from 'zustand'
import type { PageResult } from '@/types/api'
import {
  getNacosServicesApi, getNacosServiceDetailApi, getNacosInstancesApi,
  getNacosConfigsApi, getNacosConfigDetailApi, updateNacosConfigApi,
  getNacosConsoleUrlApi,
  getRabbitMQOverviewApi, getRabbitMQQueuesApi, getRabbitMQExchangesApi,
  getRabbitMQConnectionsApi, getRabbitMQNodesApi,
  purgeRabbitMQQueueApi, getRabbitMQConsoleUrlApi,
  getXXLJobGroupsApi, getXXLJobTasksApi, executeXXLJobTaskApi,
  pauseXXLJobTaskApi, resumeXXLJobTaskApi, getXXLJobLogsApi,
  getXXLJobConsoleUrlApi,
  getMinIOBucketsApi, getMinIOObjectsApi, getMinIOUsersApi,
  getMinIOPoliciesApi, createMinIOBucketApi, deleteMinIOBucketApi,
  getMinIOConsoleUrlApi,
  getOpenSearchClusterHealthApi, getOpenSearchNodesApi,
  getOpenSearchIndicesApi, searchOpenSearchApi,
  deleteOpenSearchIndexApi, closeOpenSearchIndexApi, openOpenSearchIndexApi,
  getOpenSearchConsoleUrlApi,
  type NacosService, type NacosConfig, type NacosInstance,
  type RabbitMQOverview, type RabbitMQQueue, type RabbitMQExchange,
  type RabbitMQConnection, type RabbitMQNode,
  type XXLJobGroup, type XXLJobTask, type XXLJobLog,
  type MinIOBucket, type MinIOObject, type MinIOUser, type MinIOPolicy,
  type OpenSearchCluster, type OpenSearchNode, type OpenSearchIndex,
} from '@/api/middleware'

interface MiddlewareState {
  // Nacos
  nacosServices: NacosService[]
  nacosServicesTotal: number
  nacosCurrentService: NacosService | null
  nacosInstances: NacosInstance[]
  nacosConfigs: NacosConfig[]
  nacosConfigsTotal: number
  nacosCurrentConfig: NacosConfig | null
  nacosConsoleUrl: string
  // RabbitMQ
  rabbitmqOverview: RabbitMQOverview | null
  rabbitmqQueues: RabbitMQQueue[]
  rabbitmqQueuesTotal: number
  rabbitmqExchanges: RabbitMQExchange[]
  rabbitmqConnections: RabbitMQConnection[]
  rabbitmqNodes: RabbitMQNode[]
  rabbitmqConsoleUrl: string
  // XXL-Job
  xxlJobGroups: XXLJobGroup[]
  xxlJobTasks: XXLJobTask[]
  xxlJobTasksTotal: number
  xxlJobLogs: XXLJobLog[]
  xxlJobLogsTotal: number
  xxlJobConsoleUrl: string
  // MinIO
  minioBuckets: MinIOBucket[]
  minioBucketsTotal: number
  minioObjects: MinIOObject[]
  minioObjectsTotal: number
  minioUsers: MinIOUser[]
  minioPolicies: MinIOPolicy[]
  minioConsoleUrl: string
  // OpenSearch
  opensearchCluster: OpenSearchCluster | null
  opensearchNodes: OpenSearchNode[]
  opensearchIndices: OpenSearchIndex[]
  opensearchIndicesTotal: number
  opensearchConsoleUrl: string
  // 通用
  loading: boolean
  error: string | null

  // Nacos 操作
  fetchNacosServices: (params?: Record<string, unknown>) => Promise<void>
  fetchNacosServiceDetail: (serviceName: string) => Promise<void>
  fetchNacosInstances: (serviceName: string, params?: Record<string, unknown>) => Promise<void>
  fetchNacosConfigs: (params?: Record<string, unknown>) => Promise<void>
  fetchNacosConfigDetail: (dataId: string, group: string) => Promise<void>
  doUpdateNacosConfig: (data: { dataId: string; group: string; content: string; type: string }) => Promise<boolean>
  fetchNacosConsoleUrl: () => Promise<void>
  // RabbitMQ 操作
  fetchRabbitMQOverview: () => Promise<void>
  fetchRabbitMQQueues: (params?: Record<string, unknown>) => Promise<void>
  fetchRabbitMQExchanges: (params?: Record<string, unknown>) => Promise<void>
  fetchRabbitMQConnections: () => Promise<void>
  fetchRabbitMQNodes: () => Promise<void>
  doPurgeQueue: (vhost: string, queueName: string) => Promise<boolean>
  fetchRabbitMQConsoleUrl: () => Promise<void>
  // XXL-Job 操作
  fetchXXLJobGroups: () => Promise<void>
  fetchXXLJobTasks: (params?: Record<string, unknown>) => Promise<void>
  doExecuteXXLJobTask: (taskId: number) => Promise<boolean>
  doPauseXXLJobTask: (taskId: number) => Promise<boolean>
  doResumeXXLJobTask: (taskId: number) => Promise<boolean>
  fetchXXLJobLogs: (params?: Record<string, unknown>) => Promise<void>
  fetchXXLJobConsoleUrl: () => Promise<void>
  // MinIO 操作
  fetchMinIOBuckets: (params?: Record<string, unknown>) => Promise<void>
  fetchMinIOObjects: (bucketName: string, params?: Record<string, unknown>) => Promise<void>
  fetchMinIOUsers: (params?: Record<string, unknown>) => Promise<void>
  fetchMinIOPolicies: () => Promise<void>
  doCreateMinIOBucket: (data: { name: string; region: string; versioning: boolean; objectLocking: boolean }) => Promise<boolean>
  doDeleteMinIOBucket: (bucketName: string) => Promise<boolean>
  fetchMinIOConsoleUrl: () => Promise<void>
  // OpenSearch 操作
  fetchOpenSearchClusterHealth: () => Promise<void>
  fetchOpenSearchNodes: () => Promise<void>
  fetchOpenSearchIndices: (params?: Record<string, unknown>) => Promise<void>
  doSearchOpenSearch: (data: { index: string; query: Record<string, unknown>; from: number; size: number }) => Promise<unknown>
  doDeleteOpenSearchIndex: (indexName: string) => Promise<boolean>
  doCloseOpenSearchIndex: (indexName: string) => Promise<boolean>
  doOpenOpenSearchIndex: (indexName: string) => Promise<boolean>
  fetchOpenSearchConsoleUrl: () => Promise<void>
  // 通用
  reset: () => void
}

const initialState = {
  nacosServices: [] as NacosService[],
  nacosServicesTotal: 0,
  nacosCurrentService: null as NacosService | null,
  nacosInstances: [] as NacosInstance[],
  nacosConfigs: [] as NacosConfig[],
  nacosConfigsTotal: 0,
  nacosCurrentConfig: null as NacosConfig | null,
  nacosConsoleUrl: '',
  rabbitmqOverview: null as RabbitMQOverview | null,
  rabbitmqQueues: [] as RabbitMQQueue[],
  rabbitmqQueuesTotal: 0,
  rabbitmqExchanges: [] as RabbitMQExchange[],
  rabbitmqConnections: [] as RabbitMQConnection[],
  rabbitmqNodes: [] as RabbitMQNode[],
  rabbitmqConsoleUrl: '',
  xxlJobGroups: [] as XXLJobGroup[],
  xxlJobTasks: [] as XXLJobTask[],
  xxlJobTasksTotal: 0,
  xxlJobLogs: [] as XXLJobLog[],
  xxlJobLogsTotal: 0,
  xxlJobConsoleUrl: '',
  minioBuckets: [] as MinIOBucket[],
  minioBucketsTotal: 0,
  minioObjects: [] as MinIOObject[],
  minioObjectsTotal: 0,
  minioUsers: [] as MinIOUser[],
  minioPolicies: [] as MinIOPolicy[],
  minioConsoleUrl: '',
  opensearchCluster: null as OpenSearchCluster | null,
  opensearchNodes: [] as OpenSearchNode[],
  opensearchIndices: [] as OpenSearchIndex[],
  opensearchIndicesTotal: 0,
  opensearchConsoleUrl: '',
  loading: false,
  error: null as string | null,
}

export const useMiddlewareStore = create<MiddlewareState>((set, get) => ({
  ...initialState,

  // ── Nacos ─────────────────────────────────────────────
  fetchNacosServices: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await getNacosServicesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<NacosService>
        set({ nacosServices: data.list || data.records || [], nacosServicesTotal: data.total, loading: false })
      } else { set({ error: res.data.message, loading: false }) }
    } catch (err: unknown) { set({ error: (err as Error).message || '网络错误', loading: false }) }
  },

  fetchNacosServiceDetail: async (serviceName) => {
    set({ loading: true })
    try {
      const res = await getNacosServiceDetailApi(serviceName)
      if (res.data.code === 200) set({ nacosCurrentService: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchNacosInstances: async (serviceName, params) => {
    set({ loading: true })
    try {
      const res = await getNacosInstancesApi(serviceName, params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<NacosInstance>
        set({ nacosInstances: data.list || data.records || [], loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchNacosConfigs: async (params) => {
    set({ loading: true })
    try {
      const res = await getNacosConfigsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<NacosConfig>
        set({ nacosConfigs: data.list || data.records || [], nacosConfigsTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchNacosConfigDetail: async (dataId, group) => {
    try {
      const res = await getNacosConfigDetailApi(dataId, group)
      if (res.data.code === 200) set({ nacosCurrentConfig: res.data.data })
    } catch { /* 静默 */ }
  },

  doUpdateNacosConfig: async (data) => {
    try { const res = await updateNacosConfigApi(data); return res.data.code === 200 } catch { return false }
  },

  fetchNacosConsoleUrl: async () => {
    try { const res = await getNacosConsoleUrlApi(); if (res.data.code === 200) set({ nacosConsoleUrl: res.data.data?.url || '' }) } catch {}
  },

  // ── RabbitMQ ──────────────────────────────────────────
  fetchRabbitMQOverview: async () => {
    set({ loading: true })
    try {
      const res = await getRabbitMQOverviewApi()
      if (res.data.code === 200) set({ rabbitmqOverview: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchRabbitMQQueues: async (params) => {
    set({ loading: true })
    try {
      const res = await getRabbitMQQueuesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<RabbitMQQueue>
        set({ rabbitmqQueues: data.list || data.records || [], rabbitmqQueuesTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchRabbitMQExchanges: async (params) => {
    try {
      const res = await getRabbitMQExchangesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<RabbitMQExchange>
        set({ rabbitmqExchanges: data.list || data.records || [] })
      }
    } catch {}
  },

  fetchRabbitMQConnections: async () => {
    try {
      const res = await getRabbitMQConnectionsApi()
      if (res.data.code === 200) set({ rabbitmqConnections: res.data.data || [] })
    } catch {}
  },

  fetchRabbitMQNodes: async () => {
    try {
      const res = await getRabbitMQNodesApi()
      if (res.data.code === 200) set({ rabbitmqNodes: res.data.data || [] })
    } catch {}
  },

  doPurgeQueue: async (vhost, queueName) => {
    try { const res = await purgeRabbitMQQueueApi(vhost, queueName); return res.data.code === 200 } catch { return false }
  },

  fetchRabbitMQConsoleUrl: async () => {
    try { const res = await getRabbitMQConsoleUrlApi(); if (res.data.code === 200) set({ rabbitmqConsoleUrl: res.data.data?.url || '' }) } catch {}
  },

  // ── XXL-Job ───────────────────────────────────────────
  fetchXXLJobGroups: async () => {
    try {
      const res = await getXXLJobGroupsApi()
      if (res.data.code === 200) set({ xxlJobGroups: res.data.data || [] })
    } catch {}
  },

  fetchXXLJobTasks: async (params) => {
    set({ loading: true })
    try {
      const res = await getXXLJobTasksApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<XXLJobTask>
        set({ xxlJobTasks: data.list || data.records || [], xxlJobTasksTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  doExecuteXXLJobTask: async (taskId) => {
    try { const res = await executeXXLJobTaskApi(taskId); return res.data.code === 200 } catch { return false }
  },

  doPauseXXLJobTask: async (taskId) => {
    try { const res = await pauseXXLJobTaskApi(taskId); return res.data.code === 200 } catch { return false }
  },

  doResumeXXLJobTask: async (taskId) => {
    try { const res = await resumeXXLJobTaskApi(taskId); return res.data.code === 200 } catch { return false }
  },

  fetchXXLJobLogs: async (params) => {
    set({ loading: true })
    try {
      const res = await getXXLJobLogsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<XXLJobLog>
        set({ xxlJobLogs: data.list || data.records || [], xxlJobLogsTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchXXLJobConsoleUrl: async () => {
    try { const res = await getXXLJobConsoleUrlApi(); if (res.data.code === 200) set({ xxlJobConsoleUrl: res.data.data?.url || '' }) } catch {}
  },

  // ── MinIO ─────────────────────────────────────────────
  fetchMinIOBuckets: async (params) => {
    set({ loading: true })
    try {
      const res = await getMinIOBucketsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<MinIOBucket>
        set({ minioBuckets: data.list || data.records || [], minioBucketsTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchMinIOObjects: async (bucketName, params) => {
    set({ loading: true })
    try {
      const res = await getMinIOObjectsApi(bucketName, params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<MinIOObject>
        set({ minioObjects: data.list || data.records || [], minioObjectsTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchMinIOUsers: async (params) => {
    try {
      const res = await getMinIOUsersApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<MinIOUser>
        set({ minioUsers: data.list || data.records || [] })
      }
    } catch {}
  },

  fetchMinIOPolicies: async () => {
    try {
      const res = await getMinIOPoliciesApi()
      if (res.data.code === 200) set({ minioPolicies: res.data.data || [] })
    } catch {}
  },

  doCreateMinIOBucket: async (data) => {
    try { const res = await createMinIOBucketApi(data); return res.data.code === 200 } catch { return false }
  },

  doDeleteMinIOBucket: async (bucketName) => {
    try { const res = await deleteMinIOBucketApi(bucketName); return res.data.code === 200 } catch { return false }
  },

  fetchMinIOConsoleUrl: async () => {
    try { const res = await getMinIOConsoleUrlApi(); if (res.data.code === 200) set({ minioConsoleUrl: res.data.data?.url || '' }) } catch {}
  },

  // ── OpenSearch ────────────────────────────────────────
  fetchOpenSearchClusterHealth: async () => {
    set({ loading: true })
    try {
      const res = await getOpenSearchClusterHealthApi()
      if (res.data.code === 200) set({ opensearchCluster: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchOpenSearchNodes: async () => {
    try {
      const res = await getOpenSearchNodesApi()
      if (res.data.code === 200) set({ opensearchNodes: res.data.data || [] })
    } catch {}
  },

  fetchOpenSearchIndices: async (params) => {
    set({ loading: true })
    try {
      const res = await getOpenSearchIndicesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<OpenSearchIndex>
        set({ opensearchIndices: data.list || data.records || [], opensearchIndicesTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  doSearchOpenSearch: async (data) => {
    try {
      const res = await searchOpenSearchApi(data)
      if (res.data.code === 200) return res.data.data
      return null
    } catch { return null }
  },

  doDeleteOpenSearchIndex: async (indexName) => {
    try { const res = await deleteOpenSearchIndexApi(indexName); return res.data.code === 200 } catch { return false }
  },

  doCloseOpenSearchIndex: async (indexName) => {
    try { const res = await closeOpenSearchIndexApi(indexName); return res.data.code === 200 } catch { return false }
  },

  doOpenOpenSearchIndex: async (indexName) => {
    try { const res = await openOpenSearchIndexApi(indexName); return res.data.code === 200 } catch { return false }
  },

  fetchOpenSearchConsoleUrl: async () => {
    try { const res = await getOpenSearchConsoleUrlApi(); if (res.data.code === 200) set({ opensearchConsoleUrl: res.data.data?.url || '' }) } catch {}
  },

  reset: () => set({ ...initialState }),
}))