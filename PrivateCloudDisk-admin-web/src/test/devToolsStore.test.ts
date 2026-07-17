// ============================================================
// 开发工具 Store 单元测试
// ============================================================
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useDevToolsStore } from '@/stores/devToolsStore'

vi.mock('@/api/devTools', () => ({
  getSwaggerServicesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getSwaggerEndpointsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getSwaggerEndpointDetailApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  refreshSwaggerServiceApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  getSwaggerUIUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:8080/swagger-ui' } },
  }),
  getApiGatewayApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  getApiRoutesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getApiRouteDetailApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  createApiRouteApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  updateApiRouteApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  deleteApiRouteApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  toggleApiRouteApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  getApiUpstreamsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getApiPluginsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getApiConsumersApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getApiSSLsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getApiTestCasesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  createApiTestCaseApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  updateApiTestCaseApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  deleteApiTestCaseApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  executeApiTestApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  executeAllApiTestsApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  getApiTestResultsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getOpenAPISpecsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  importOpenAPISpecApi: vi.fn().mockResolvedValue({
    data: { code: 200 },
  }),
  exportOpenAPISpecApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { content: '{}' } },
  }),
  getOpenAPIClientCodeApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { code: '// generated code' } },
  }),
}))

describe('devToolsStore', () => {
  beforeEach(() => {
    const { reset } = useDevToolsStore.getState()
    reset()
  })

  describe('初始状态', () => {
    it('应该初始化为默认值', () => {
      const state = useDevToolsStore.getState()
      expect(state.swaggerServices).toEqual([])
      expect(state.swaggerEndpoints).toEqual([])
      expect(state.swaggerCurrentEndpoint).toBeNull()
      expect(state.swaggerUIUrl).toBe('')
      expect(state.apiGateway).toBeNull()
      expect(state.apiRoutes).toEqual([])
      expect(state.apiRoutesTotal).toBe(0)
      expect(state.apiCurrentRoute).toBeNull()
      expect(state.apiUpstreams).toEqual([])
      expect(state.apiPlugins).toEqual([])
      expect(state.apiConsumers).toEqual([])
      expect(state.apiSSLs).toEqual([])
      expect(state.openAPISpecs).toEqual([])
      expect(state.openAPIClientCode).toBe('')
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })
  })

  describe('Swagger', () => {
    it('fetchSwaggerServices 应该正常调用', async () => {
      await expect(useDevToolsStore.getState().fetchSwaggerServices()).resolves.toBeUndefined()
    })

    it('doRefreshSwaggerService 应该返回 boolean', async () => {
      const result = await useDevToolsStore.getState().doRefreshSwaggerService('svc-1')
      expect(result).toBe(true)
    })

    it('fetchSwaggerUIUrl 应该设置 URL', async () => {
      await useDevToolsStore.getState().fetchSwaggerUIUrl('svc-1')
      expect(useDevToolsStore.getState().swaggerUIUrl).toBe('http://localhost:8080/swagger-ui')
    })
  })

  describe('API 管理', () => {
    it('fetchApiGateway 应该正常调用', async () => {
      await expect(useDevToolsStore.getState().fetchApiGateway()).resolves.toBeUndefined()
    })

    it('doCreateApiRoute 应该返回 boolean', async () => {
      const result = await useDevToolsStore.getState().doCreateApiRoute({ name: 'test-route', uri: '/test' })
      expect(result).toBe(true)
    })

    it('doDeleteApiRoute 应该返回 boolean', async () => {
      const result = await useDevToolsStore.getState().doDeleteApiRoute('route-1')
      expect(result).toBe(true)
    })

    it('doToggleApiRoute 应该返回 boolean', async () => {
      const result = await useDevToolsStore.getState().doToggleApiRoute('route-1', true)
      expect(result).toBe(true)
    })
  })

  describe('OpenAPI', () => {
    it('fetchOpenAPISpecs 应该正常调用', async () => {
      await expect(useDevToolsStore.getState().fetchOpenAPISpecs()).resolves.toBeUndefined()
    })

    it('doImportOpenAPISpec 应该返回 boolean', async () => {
      const result = await useDevToolsStore.getState().doImportOpenAPISpec({ url: 'http://example.com/openapi.json', name: 'test' })
      expect(result).toBe(true)
    })

    it('doExportOpenAPISpec 应该返回字符串', async () => {
      const result = await useDevToolsStore.getState().doExportOpenAPISpec('svc-1')
      expect(result).toBe('{}')
    })

    it('fetchOpenAPIClientCode 应该设置代码', async () => {
      await useDevToolsStore.getState().fetchOpenAPIClientCode('svc-1', 'typescript-axios')
      expect(useDevToolsStore.getState().openAPIClientCode).toBe('// generated code')
    })
  })
})