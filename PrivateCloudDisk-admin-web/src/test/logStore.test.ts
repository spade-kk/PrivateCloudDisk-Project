// ============================================================
// 日志系统 Store 单元测试
// ============================================================
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useLogStore } from '@/stores/logStore'

vi.mock('@/api/logs', () => ({
  queryLokiApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { streams: [] } },
  }),
  queryLokiRangeApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { streams: [] } },
  }),
  getLokiLabelsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getLokiLabelValuesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getLokiConsoleUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:3100' } },
  }),
  getKibanaIndexPatternsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getKibanaSavedSearchesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getKibanaDashboardsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  searchElasticsearchApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { hits: { hits: [], total: { value: 0 } } } },
  }),
  getKibanaEmbedUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:5601/embed/test' } },
  }),
  getKibanaConsoleUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:5601' } },
  }),
  queryLogsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getLogLevelsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getLogServicesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
}))

describe('logStore', () => {
  beforeEach(() => {
    const { reset } = useLogStore.getState()
    reset()
  })

  describe('初始状态', () => {
    it('应该初始化为默认值', () => {
      const state = useLogStore.getState()
      expect(state.lokiResult).toBeNull()
      expect(state.lokiLabels).toEqual([])
      expect(state.lokiLabelValues).toEqual([])
      expect(state.lokiConsoleUrl).toBe('')
      expect(state.kibanaIndexPatterns).toEqual([])
      expect(state.kibanaSavedSearches).toEqual([])
      expect(state.kibanaDashboards).toEqual([])
      expect(state.kibanaSearchHits).toEqual([])
      expect(state.kibanaSearchTotal).toBe(0)
      expect(state.kibanaEmbedUrl).toBe('')
      expect(state.kibanaConsoleUrl).toBe('')
      expect(state.logs).toEqual([])
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })
  })

  describe('Loki', () => {
    it('doQueryLoki 应该正常调用', async () => {
      await expect(useLogStore.getState().doQueryLoki({ query: '{job="test"}', limit: 100, direction: 'BACKWARD' })).resolves.toBeUndefined()
    })

    it('doQueryLokiRange 应该接受时间范围', async () => {
      await expect(useLogStore.getState().doQueryLokiRange({
        query: '{job="test"}',
        limit: 100,
        start: '2026-01-01T00:00:00Z',
        end: '2026-01-01T01:00:00Z',
        direction: 'BACKWARD',
      })).resolves.toBeUndefined()
    })

    it('fetchLokiLabels 应该正常调用', async () => {
      await expect(useLogStore.getState().fetchLokiLabels()).resolves.toBeUndefined()
    })

    it('fetchLokiConsoleUrl 应该设置 URL', async () => {
      await useLogStore.getState().fetchLokiConsoleUrl()
      expect(useLogStore.getState().lokiConsoleUrl).toBe('http://localhost:3100')
    })
  })

  describe('Kibana', () => {
    it('fetchKibanaIndexPatterns 应该正常调用', async () => {
      await expect(useLogStore.getState().fetchKibanaIndexPatterns()).resolves.toBeUndefined()
    })

    it('doSearchElasticsearch 应该接受查询参数', async () => {
      await expect(useLogStore.getState().doSearchElasticsearch({
        index: 'test-*',
        query: { match_all: {} },
        from: 0,
        size: 10,
      })).resolves.toBeUndefined()
    })

    it('fetchKibanaConsoleUrl 应该设置 URL', async () => {
      await useLogStore.getState().fetchKibanaConsoleUrl()
      expect(useLogStore.getState().kibanaConsoleUrl).toBe('http://localhost:5601')
    })
  })
})