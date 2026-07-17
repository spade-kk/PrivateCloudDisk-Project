// ============================================================
// 监控集成 Store 单元测试
// ============================================================
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { useMonitorIntegrationStore } from '@/stores/monitorIntegrationStore'

vi.mock('@/api/monitor', () => ({
  getGrafanaDashboardsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getGrafanaAlertsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getGrafanaEmbedUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:3000/d/test' } },
  }),
  getGrafanaConsoleUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:3000' } },
  }),
  getSkyWalkingServicesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getSkyWalkingTracesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getSkyWalkingTraceDetailApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  getSkyWalkingMetricsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: [] },
  }),
  getSkyWalkingAlarmsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getSkyWalkingConsoleUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:8080' } },
  }),
  queryPrometheusApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  queryRangePrometheusApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: null },
  }),
  getPrometheusTargetsApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { activeTargets: [], droppedTargets: [] } },
  }),
  getPrometheusRulesApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { list: [], total: 0 } },
  }),
  getPrometheusConsoleUrlApi: vi.fn().mockResolvedValue({
    data: { code: 200, data: { url: 'http://localhost:9090' } },
  }),
}))

describe('monitorIntegrationStore', () => {
  beforeEach(() => {
    useMonitorIntegrationStore.getState().reset()
  })

  describe('初始状态', () => {
    it('Grafana 应该初始化为默认值', () => {
      const state = useMonitorIntegrationStore.getState()
      expect(state.grafanaDashboards).toEqual([])
      expect(state.grafanaAlerts).toEqual([])
      expect(state.grafanaEmbedUrl).toBe('')
      expect(state.grafanaConsoleUrl).toBe('')
    })

    it('SkyWalking 应该初始化为默认值', () => {
      const state = useMonitorIntegrationStore.getState()
      expect(state.skywalkingServices).toEqual([])
      expect(state.skywalkingTraces).toEqual([])
      expect(state.skywalkingTracesTotal).toBe(0)
      expect(state.skywalkingCurrentTrace).toBeNull()
      expect(state.skywalkingMetrics).toEqual([])
      expect(state.skywalkingAlarms).toEqual([])
      expect(state.skywalkingAlarmsTotal).toBe(0)
      expect(state.skywalkingConsoleUrl).toBe('')
    })

    it('Prometheus 应该初始化为默认值', () => {
      const state = useMonitorIntegrationStore.getState()
      expect(state.prometheusQueryResult).toBeNull()
      expect(state.prometheusTargets).toBeNull()
      expect(state.prometheusRules).toEqual([])
      expect(state.prometheusConsoleUrl).toBe('')
    })

    it('loading 和 error 应该为默认值', () => {
      const state = useMonitorIntegrationStore.getState()
      expect(state.loading).toBe(false)
      expect(state.error).toBeNull()
    })
  })

  describe('Grafana', () => {
    it('fetchGrafanaDashboards 应该正常调用', async () => {
      await expect(useMonitorIntegrationStore.getState().fetchGrafanaDashboards()).resolves.toBeUndefined()
    })

    it('fetchGrafanaConsoleUrl 应该设置 URL', async () => {
      await useMonitorIntegrationStore.getState().fetchGrafanaConsoleUrl()
      expect(useMonitorIntegrationStore.getState().grafanaConsoleUrl).toBe('http://localhost:3000')
    })

    it('fetchGrafanaEmbedUrl 应该设置 embed URL', async () => {
      await useMonitorIntegrationStore.getState().fetchGrafanaEmbedUrl('test-dashboard')
      expect(useMonitorIntegrationStore.getState().grafanaEmbedUrl).toBe('http://localhost:3000/d/test')
    })
  })

  describe('SkyWalking', () => {
    it('fetchSkyWalkingServices 应该正常调用', async () => {
      await expect(useMonitorIntegrationStore.getState().fetchSkyWalkingServices()).resolves.toBeUndefined()
    })

    it('fetchSkyWalkingConsoleUrl 应该设置 URL', async () => {
      await useMonitorIntegrationStore.getState().fetchSkyWalkingConsoleUrl()
      expect(useMonitorIntegrationStore.getState().skywalkingConsoleUrl).toBe('http://localhost:8080')
    })

    it('fetchSkyWalkingTraceDetail 应该接受 traceId', async () => {
      await expect(useMonitorIntegrationStore.getState().fetchSkyWalkingTraceDetail('trace-123')).resolves.toBeUndefined()
    })
  })

  describe('Prometheus', () => {
    it('doQueryPrometheus 应该接受查询字符串', async () => {
      await expect(useMonitorIntegrationStore.getState().doQueryPrometheus('up')).resolves.toBeUndefined()
    })

    it('fetchPrometheusConsoleUrl 应该设置 URL', async () => {
      await useMonitorIntegrationStore.getState().fetchPrometheusConsoleUrl()
      expect(useMonitorIntegrationStore.getState().prometheusConsoleUrl).toBe('http://localhost:9090')
    })
  })
})