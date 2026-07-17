// ============================================================
// 中间件 Store 单元测试
// ============================================================
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useMiddlewareStore } from '@/stores/middlewareStore'

const { resolveMock } = vi.hoisted(() => ({
  resolveMock: (data: unknown) => ({ data: { code: 200, data } }),
}))

vi.mock('@/api/middleware', () => ({
  getNacosServicesApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getNacosServiceDetailApi: vi.fn().mockResolvedValue(resolveMock(null)),
  getNacosInstancesApi: vi.fn().mockResolvedValue(resolveMock([])),
  getNacosConfigsApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getNacosConfigDetailApi: vi.fn().mockResolvedValue(resolveMock(null)),
  updateNacosConfigApi: vi.fn().mockResolvedValue(resolveMock({})),
  getNacosConsoleUrlApi: vi.fn().mockResolvedValue(resolveMock({ url: 'http://localhost:8848/nacos' })),
  getRabbitMQOverviewApi: vi.fn().mockResolvedValue(resolveMock(null)),
  getRabbitMQQueuesApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getRabbitMQExchangesApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getRabbitMQConnectionsApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getRabbitMQNodesApi: vi.fn().mockResolvedValue(resolveMock([])),
  purgeRabbitMQQueueApi: vi.fn().mockResolvedValue(resolveMock({})),
  getRabbitMQConsoleUrlApi: vi.fn().mockResolvedValue(resolveMock({ url: 'http://localhost:15672' })),
  getXXLJobGroupsApi: vi.fn().mockResolvedValue(resolveMock([])),
  getXXLJobTasksApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  executeXXLJobTaskApi: vi.fn().mockResolvedValue(resolveMock({})),
  pauseXXLJobTaskApi: vi.fn().mockResolvedValue(resolveMock({})),
  resumeXXLJobTaskApi: vi.fn().mockResolvedValue(resolveMock({})),
  getXXLJobLogsApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getXXLJobConsoleUrlApi: vi.fn().mockResolvedValue(resolveMock({ url: 'http://localhost:8080/xxl-job-admin' })),
  getMinIOBucketsApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getMinIOObjectsApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  getMinIOUsersApi: vi.fn().mockResolvedValue(resolveMock([])),
  getMinIOPoliciesApi: vi.fn().mockResolvedValue(resolveMock([])),
  createMinIOBucketApi: vi.fn().mockResolvedValue(resolveMock({})),
  deleteMinIOBucketApi: vi.fn().mockResolvedValue(resolveMock({})),
  getMinIOConsoleUrlApi: vi.fn().mockResolvedValue(resolveMock({ url: 'http://localhost:9001' })),
  getOpenSearchClusterHealthApi: vi.fn().mockResolvedValue(resolveMock(null)),
  getOpenSearchNodesApi: vi.fn().mockResolvedValue(resolveMock([])),
  getOpenSearchIndicesApi: vi.fn().mockResolvedValue(resolveMock({ list: [], total: 0 })),
  searchOpenSearchApi: vi.fn().mockResolvedValue(resolveMock([])),
  deleteOpenSearchIndexApi: vi.fn().mockResolvedValue(resolveMock({})),
  closeOpenSearchIndexApi: vi.fn().mockResolvedValue(resolveMock({})),
  openOpenSearchIndexApi: vi.fn().mockResolvedValue(resolveMock({})),
  getOpenSearchConsoleUrlApi: vi.fn().mockResolvedValue(resolveMock({ url: 'http://localhost:5601' })),
}))

describe('middlewareStore', () => {
  beforeEach(() => {
    useMiddlewareStore.getState().reset()
  })

  describe('初始状态', () => {
    it('Nacos 状态应该初始化为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.nacosServices).toEqual([])
      expect(state.nacosServicesTotal).toBe(0)
      expect(state.nacosCurrentService).toBeNull()
      expect(state.nacosInstances).toEqual([])
      expect(state.nacosConfigs).toEqual([])
      expect(state.nacosConfigsTotal).toBe(0)
      expect(state.nacosCurrentConfig).toBeNull()
      expect(state.nacosConsoleUrl).toBe('')
    })

    it('RabbitMQ 状态应该初始化为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.rabbitmqOverview).toBeNull()
      expect(state.rabbitmqQueues).toEqual([])
      expect(state.rabbitmqQueuesTotal).toBe(0)
      expect(state.rabbitmqExchanges).toEqual([])
      expect(state.rabbitmqConnections).toEqual([])
      expect(state.rabbitmqNodes).toEqual([])
      expect(state.rabbitmqConsoleUrl).toBe('')
    })

    it('XXL-Job 状态应该初始化为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.xxlJobGroups).toEqual([])
      expect(state.xxlJobTasks).toEqual([])
      expect(state.xxlJobTasksTotal).toBe(0)
      expect(state.xxlJobLogs).toEqual([])
      expect(state.xxlJobLogsTotal).toBe(0)
      expect(state.xxlJobConsoleUrl).toBe('')
    })

    it('MinIO 状态应该初始化为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.minioBuckets).toEqual([])
      expect(state.minioBucketsTotal).toBe(0)
      expect(state.minioObjects).toEqual([])
      expect(state.minioObjectsTotal).toBe(0)
      expect(state.minioConsoleUrl).toBe('')
    })

    it('OpenSearch 状态应该初始化为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.opensearchIndices).toEqual([])
      expect(state.opensearchIndicesTotal).toBe(0)
      expect(state.opensearchConsoleUrl).toBe('')
    })

    it('loading 和 error 应该为默认值', () => {
      const state = useMiddlewareStore.getState()
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })
  })

  describe('fetchNacosServices', () => {
    it('应该正常调用', async () => {
      await expect(useMiddlewareStore.getState().fetchNacosServices()).resolves.toBeUndefined()
    })
  })

  describe('fetchRabbitMQQueues', () => {
    it('应该接受参数', async () => {
      await expect(useMiddlewareStore.getState().fetchRabbitMQQueues({ page: 1, pageSize: 20 })).resolves.toBeUndefined()
    })
  })

  describe('fetchMinIOBuckets', () => {
    it('应该正常调用', async () => {
      await expect(useMiddlewareStore.getState().fetchMinIOBuckets()).resolves.toBeUndefined()
    })
  })

  describe('控制台 URL', () => {
    it('fetchNacosConsoleUrl 应该设置 URL', async () => {
      await useMiddlewareStore.getState().fetchNacosConsoleUrl()
      expect(useMiddlewareStore.getState().nacosConsoleUrl).toBe('http://localhost:8848/nacos')
    })

    it('fetchRabbitMQConsoleUrl 应该设置 URL', async () => {
      await useMiddlewareStore.getState().fetchRabbitMQConsoleUrl()
      expect(useMiddlewareStore.getState().rabbitmqConsoleUrl).toBe('http://localhost:15672')
    })

    it('fetchMinIOConsoleUrl 应该设置 URL', async () => {
      await useMiddlewareStore.getState().fetchMinIOConsoleUrl()
      expect(useMiddlewareStore.getState().minioConsoleUrl).toBe('http://localhost:9001')
    })
  })
})