// ============================================================
// 开发工具 Store
// Swagger / API 管理 / OpenAPI
// ============================================================
import { create } from 'zustand'
import type { PageResult } from '@/types/api'
import {
  getSwaggerServicesApi, getSwaggerEndpointsApi, getSwaggerEndpointDetailApi,
  refreshSwaggerServiceApi, getSwaggerUIUrlApi,
  getApiGatewayApi, getApiRoutesApi, getApiRouteDetailApi,
  createApiRouteApi, updateApiRouteApi, deleteApiRouteApi, toggleApiRouteApi,
  getApiUpstreamsApi, getApiPluginsApi, getApiConsumersApi, getApiSSLsApi,
  getApiTestCasesApi, createApiTestCaseApi, updateApiTestCaseApi,
  deleteApiTestCaseApi, executeApiTestApi, executeAllApiTestsApi, getApiTestResultsApi,
  getOpenAPISpecsApi, importOpenAPISpecApi, exportOpenAPISpecApi, getOpenAPIClientCodeApi,
  type SwaggerService, type SwaggerEndpoint,
  type ApiGateway, type ApiRoute, type ApiUpstream, type ApiPlugin,
  type ApiConsumer, type ApiSSL,
  type ApiTestCase, type ApiTestResult,
} from '@/api/devTools'

interface DevToolsState {
  // Swagger
  swaggerServices: SwaggerService[]
  swaggerEndpoints: SwaggerEndpoint[]
  swaggerCurrentEndpoint: SwaggerEndpoint | null
  swaggerUIUrl: string
  // API 管理
  apiGateway: ApiGateway | null
  apiRoutes: ApiRoute[]
  apiRoutesTotal: number
  apiCurrentRoute: ApiRoute | null
  apiUpstreams: ApiUpstream[]
  apiUpstreamsTotal: number
  apiPlugins: ApiPlugin[]
  apiPluginsTotal: number
  apiConsumers: ApiConsumer[]
  apiConsumersTotal: number
  apiSSLs: ApiSSL[]
  apiSSLsTotal: number
  // API 测试
  apiTestCases: ApiTestCase[]
  apiTestCasesTotal: number
  apiTestResults: ApiTestResult[]
  apiTestResultsTotal: number
  // OpenAPI
  openAPISpecs: SwaggerService[]
  openAPIClientCode: string
  // 通用
  loading: boolean
  error: string | null

  // Swagger
  fetchSwaggerServices: () => Promise<void>
  fetchSwaggerEndpoints: (serviceId: string, params?: Record<string, unknown>) => Promise<void>
  fetchSwaggerEndpointDetail: (serviceId: string, path: string, method: string) => Promise<void>
  doRefreshSwaggerService: (serviceId: string) => Promise<boolean>
  fetchSwaggerUIUrl: (serviceId: string) => Promise<void>
  // API 管理
  fetchApiGateway: () => Promise<void>
  fetchApiRoutes: (params?: Record<string, unknown>) => Promise<void>
  fetchApiRouteDetail: (routeId: string) => Promise<void>
  doCreateApiRoute: (data: Partial<ApiRoute>) => Promise<boolean>
  doUpdateApiRoute: (routeId: string, data: Partial<ApiRoute>) => Promise<boolean>
  doDeleteApiRoute: (routeId: string) => Promise<boolean>
  doToggleApiRoute: (routeId: string, enabled: boolean) => Promise<boolean>
  fetchApiUpstreams: (params?: Record<string, unknown>) => Promise<void>
  fetchApiPlugins: (params?: Record<string, unknown>) => Promise<void>
  fetchApiConsumers: (params?: Record<string, unknown>) => Promise<void>
  fetchApiSSLs: (params?: Record<string, unknown>) => Promise<void>
  // API 测试
  fetchApiTestCases: (params?: Record<string, unknown>) => Promise<void>
  doCreateApiTestCase: (data: Partial<ApiTestCase>) => Promise<boolean>
  doUpdateApiTestCase: (testId: string, data: Partial<ApiTestCase>) => Promise<boolean>
  doDeleteApiTestCase: (testId: string) => Promise<boolean>
  doExecuteApiTest: (testId: string) => Promise<ApiTestResult | null>
  doExecuteAllApiTests: () => Promise<void>
  fetchApiTestResults: (params?: Record<string, unknown>) => Promise<void>
  // OpenAPI
  fetchOpenAPISpecs: () => Promise<void>
  doImportOpenAPISpec: (data: { url: string; name: string }) => Promise<boolean>
  doExportOpenAPISpec: (serviceId: string) => Promise<string>
  fetchOpenAPIClientCode: (serviceId: string, language: string) => Promise<void>
  reset: () => void
}

const initialState = {
  swaggerServices: [] as SwaggerService[],
  swaggerEndpoints: [] as SwaggerEndpoint[],
  swaggerCurrentEndpoint: null as SwaggerEndpoint | null,
  swaggerUIUrl: '',
  apiGateway: null as ApiGateway | null,
  apiRoutes: [] as ApiRoute[],
  apiRoutesTotal: 0,
  apiCurrentRoute: null as ApiRoute | null,
  apiUpstreams: [] as ApiUpstream[],
  apiUpstreamsTotal: 0,
  apiPlugins: [] as ApiPlugin[],
  apiPluginsTotal: 0,
  apiConsumers: [] as ApiConsumer[],
  apiConsumersTotal: 0,
  apiSSLs: [] as ApiSSL[],
  apiSSLsTotal: 0,
  apiTestCases: [] as ApiTestCase[],
  apiTestCasesTotal: 0,
  apiTestResults: [] as ApiTestResult[],
  apiTestResultsTotal: 0,
  openAPISpecs: [] as SwaggerService[],
  openAPIClientCode: '',
  loading: false,
  error: null as string | null,
}

export const useDevToolsStore = create<DevToolsState>((set, get) => ({
  ...initialState,

  // ── Swagger ───────────────────────────────────────────
  fetchSwaggerServices: async () => {
    set({ loading: true })
    try {
      const res = await getSwaggerServicesApi()
      if (res.data.code === 200) set({ swaggerServices: res.data.data || [], loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchSwaggerEndpoints: async (serviceId, params) => {
    set({ loading: true })
    try {
      const res = await getSwaggerEndpointsApi(serviceId, params)
      if (res.data.code === 200) set({ swaggerEndpoints: res.data.data || [], loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchSwaggerEndpointDetail: async (serviceId, path, method) => {
    try {
      const res = await getSwaggerEndpointDetailApi(serviceId, path, method)
      if (res.data.code === 200) set({ swaggerCurrentEndpoint: res.data.data })
    } catch {}
  },

  doRefreshSwaggerService: async (serviceId) => {
    try { const res = await refreshSwaggerServiceApi(serviceId); return res.data.code === 200 } catch { return false }
  },

  fetchSwaggerUIUrl: async (serviceId) => {
    try {
      const res = await getSwaggerUIUrlApi(serviceId)
      if (res.data.code === 200) set({ swaggerUIUrl: res.data.data?.url || '' })
    } catch {}
  },

  // ── API 管理 ──────────────────────────────────────────
  fetchApiGateway: async () => {
    try {
      const res = await getApiGatewayApi()
      if (res.data.code === 200) set({ apiGateway: res.data.data })
    } catch {}
  },

  fetchApiRoutes: async (params) => {
    set({ loading: true })
    try {
      const res = await getApiRoutesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiRoute>
        set({ apiRoutes: data.list || data.records || [], apiRoutesTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchApiRouteDetail: async (routeId) => {
    try {
      const res = await getApiRouteDetailApi(routeId)
      if (res.data.code === 200) set({ apiCurrentRoute: res.data.data })
    } catch {}
  },

  doCreateApiRoute: async (data) => {
    try { const res = await createApiRouteApi(data); return res.data.code === 200 } catch { return false }
  },

  doUpdateApiRoute: async (routeId, data) => {
    try { const res = await updateApiRouteApi(routeId, data); return res.data.code === 200 } catch { return false }
  },

  doDeleteApiRoute: async (routeId) => {
    try {
      const res = await deleteApiRouteApi(routeId)
      if (res.data.code === 200) {
        const { apiRoutes } = get()
        set({ apiRoutes: apiRoutes.filter((r) => r.id !== routeId), apiRoutesTotal: get().apiRoutesTotal - 1 })
        return true
      }
      return false
    } catch { return false }
  },

  doToggleApiRoute: async (routeId, enabled) => {
    try { const res = await toggleApiRouteApi(routeId, enabled); return res.data.code === 200 } catch { return false }
  },

  fetchApiUpstreams: async (params) => {
    try {
      const res = await getApiUpstreamsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiUpstream>
        set({ apiUpstreams: data.list || data.records || [], apiUpstreamsTotal: data.total })
      }
    } catch {}
  },

  fetchApiPlugins: async (params) => {
    try {
      const res = await getApiPluginsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiPlugin>
        set({ apiPlugins: data.list || data.records || [], apiPluginsTotal: data.total })
      }
    } catch {}
  },

  fetchApiConsumers: async (params) => {
    try {
      const res = await getApiConsumersApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiConsumer>
        set({ apiConsumers: data.list || data.records || [], apiConsumersTotal: data.total })
      }
    } catch {}
  },

  fetchApiSSLs: async (params) => {
    try {
      const res = await getApiSSLsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiSSL>
        set({ apiSSLs: data.list || data.records || [], apiSSLsTotal: data.total })
      }
    } catch {}
  },

  // ── API 测试 ──────────────────────────────────────────
  fetchApiTestCases: async (params) => {
    set({ loading: true })
    try {
      const res = await getApiTestCasesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiTestCase>
        set({ apiTestCases: data.list || data.records || [], apiTestCasesTotal: data.total, loading: false })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  doCreateApiTestCase: async (data) => {
    try { const res = await createApiTestCaseApi(data); return res.data.code === 200 } catch { return false }
  },

  doUpdateApiTestCase: async (testId, data) => {
    try { const res = await updateApiTestCaseApi(testId, data); return res.data.code === 200 } catch { return false }
  },

  doDeleteApiTestCase: async (testId) => {
    try {
      const res = await deleteApiTestCaseApi(testId)
      if (res.data.code === 200) {
        const { apiTestCases } = get()
        set({ apiTestCases: apiTestCases.filter((t) => t.id !== testId), apiTestCasesTotal: get().apiTestCasesTotal - 1 })
        return true
      }
      return false
    } catch { return false }
  },

  doExecuteApiTest: async (testId) => {
    try {
      const res = await executeApiTestApi(testId)
      if (res.data.code === 200) return res.data.data as ApiTestResult
      return null
    } catch { return null }
  },

  doExecuteAllApiTests: async () => {
    try {
      await executeAllApiTestsApi()
    } catch {}
  },

  fetchApiTestResults: async (params) => {
    try {
      const res = await getApiTestResultsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data as PageResult<ApiTestResult>
        set({ apiTestResults: data.list || data.records || [], apiTestResultsTotal: data.total })
      }
    } catch {}
  },

  // ── OpenAPI ───────────────────────────────────────────
  fetchOpenAPISpecs: async () => {
    try {
      const res = await getOpenAPISpecsApi()
      if (res.data.code === 200) set({ openAPISpecs: res.data.data || [] })
    } catch {}
  },

  doImportOpenAPISpec: async (data) => {
    try { const res = await importOpenAPISpecApi(data); return res.data.code === 200 } catch { return false }
  },

  doExportOpenAPISpec: async (serviceId) => {
    try {
      const res = await exportOpenAPISpecApi(serviceId)
      if (res.data.code === 200) return res.data.data?.content || ''
      return ''
    } catch { return '' }
  },

  fetchOpenAPIClientCode: async (serviceId, language) => {
    try {
      const res = await getOpenAPIClientCodeApi(serviceId, language)
      if (res.data.code === 200) set({ openAPIClientCode: res.data.data?.code || '' })
    } catch {}
  },

  reset: () => set({ ...initialState }),
}))