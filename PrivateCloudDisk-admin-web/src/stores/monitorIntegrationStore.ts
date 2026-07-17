// ============================================================
// 监控集成 Store
// Grafana / SkyWalking / Prometheus
// ============================================================
import { create } from 'zustand'
import {
  getGrafanaDashboardsApi, getGrafanaAlertsApi, getGrafanaEmbedUrlApi, getGrafanaConsoleUrlApi,
  getSkyWalkingServicesApi, getSkyWalkingTracesApi, getSkyWalkingTraceDetailApi,
  getSkyWalkingMetricsApi, getSkyWalkingAlarmsApi, getSkyWalkingConsoleUrlApi,
  queryPrometheusApi, queryRangePrometheusApi, getPrometheusTargetsApi,
  getPrometheusRulesApi, getPrometheusConsoleUrlApi,
  type GrafanaDashboard, type GrafanaAlert,
  type SkyWalkingService, type SkyWalkingTrace, type SkyWalkingMetricValue, type SkyWalkingAlarm,
  type PrometheusQueryResult, type PrometheusTarget, type PrometheusRule,
} from '@/api/monitor'

interface MonitorIntegrationState {
  // Grafana
  grafanaDashboards: GrafanaDashboard[]
  grafanaAlerts: GrafanaAlert[]
  grafanaEmbedUrl: string
  grafanaConsoleUrl: string
  // SkyWalking
  skywalkingServices: SkyWalkingService[]
  skywalkingTraces: SkyWalkingTrace[]
  skywalkingTracesTotal: number
  skywalkingCurrentTrace: SkyWalkingTrace | null
  skywalkingMetrics: SkyWalkingMetricValue[]
  skywalkingAlarms: SkyWalkingAlarm[]
  skywalkingAlarmsTotal: number
  skywalkingConsoleUrl: string
  // Prometheus
  prometheusQueryResult: PrometheusQueryResult | null
  prometheusTargets: { activeTargets: PrometheusTarget[]; droppedTargets: PrometheusTarget[] } | null
  prometheusRules: PrometheusRule[]
  prometheusConsoleUrl: string
  // 通用
  loading: boolean
  error: string | null

  // Grafana
  fetchGrafanaDashboards: () => Promise<void>
  fetchGrafanaAlerts: (params?: Record<string, unknown>) => Promise<void>
  fetchGrafanaEmbedUrl: (dashboardUid: string, params?: Record<string, unknown>) => Promise<void>
  fetchGrafanaConsoleUrl: () => Promise<void>
  // SkyWalking
  fetchSkyWalkingServices: () => Promise<void>
  fetchSkyWalkingTraces: (params: Record<string, unknown>) => Promise<void>
  fetchSkyWalkingTraceDetail: (traceId: string) => Promise<void>
  fetchSkyWalkingMetrics: (params: { serviceId: string; metricNames: string[]; duration: { start: string; end: string; step: string } }) => Promise<void>
  fetchSkyWalkingAlarms: (params?: Record<string, unknown>) => Promise<void>
  fetchSkyWalkingConsoleUrl: () => Promise<void>
  // Prometheus
  doQueryPrometheus: (query: string, params?: Record<string, unknown>) => Promise<void>
  doQueryRangePrometheus: (query: string, start: string, end: string, step: string) => Promise<void>
  fetchPrometheusTargets: () => Promise<void>
  fetchPrometheusRules: () => Promise<void>
  fetchPrometheusConsoleUrl: () => Promise<void>
  reset: () => void
}

const initialState = {
  grafanaDashboards: [] as GrafanaDashboard[],
  grafanaAlerts: [] as GrafanaAlert[],
  grafanaEmbedUrl: '',
  grafanaConsoleUrl: '',
  skywalkingServices: [] as SkyWalkingService[],
  skywalkingTraces: [] as SkyWalkingTrace[],
  skywalkingTracesTotal: 0,
  skywalkingCurrentTrace: null as SkyWalkingTrace | null,
  skywalkingMetrics: [] as SkyWalkingMetricValue[],
  skywalkingAlarms: [] as SkyWalkingAlarm[],
  skywalkingAlarmsTotal: 0,
  skywalkingConsoleUrl: '',
  prometheusQueryResult: null as PrometheusQueryResult | null,
  prometheusTargets: null as { activeTargets: PrometheusTarget[]; droppedTargets: PrometheusTarget[] } | null,
  prometheusRules: [] as PrometheusRule[],
  prometheusConsoleUrl: '',
  loading: false,
  error: null as string | null,
}

export const useMonitorIntegrationStore = create<MonitorIntegrationState>((set) => ({
  ...initialState,

  // ── Grafana ───────────────────────────────────────────
  fetchGrafanaDashboards: async () => {
    set({ loading: true })
    try {
      const res = await getGrafanaDashboardsApi()
      if (res.data.code === 200) set({ grafanaDashboards: res.data.data || [], loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchGrafanaAlerts: async (params) => {
    try {
      const res = await getGrafanaAlertsApi(params)
      if (res.data.code === 200) set({ grafanaAlerts: res.data.data || [] })
    } catch {}
  },

  fetchGrafanaEmbedUrl: async (dashboardUid, params) => {
    try {
      const res = await getGrafanaEmbedUrlApi(dashboardUid, params)
      if (res.data.code === 200) set({ grafanaEmbedUrl: res.data.data?.url || '' })
    } catch {}
  },

  fetchGrafanaConsoleUrl: async () => {
    try {
      const res = await getGrafanaConsoleUrlApi()
      if (res.data.code === 200) set({ grafanaConsoleUrl: res.data.data?.url || '' })
    } catch {}
  },

  // ── SkyWalking ────────────────────────────────────────
  fetchSkyWalkingServices: async () => {
    set({ loading: true })
    try {
      const res = await getSkyWalkingServicesApi()
      if (res.data.code === 200) set({ skywalkingServices: res.data.data || [], loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchSkyWalkingTraces: async (params) => {
    set({ loading: true })
    try {
      const res = await getSkyWalkingTracesApi(params)
      if (res.data.code === 200) {
        set({
          skywalkingTraces: res.data.data?.traces || [],
          skywalkingTracesTotal: res.data.data?.total || 0,
          loading: false,
        })
      }
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchSkyWalkingTraceDetail: async (traceId) => {
    set({ loading: true })
    try {
      const res = await getSkyWalkingTraceDetailApi(traceId)
      if (res.data.code === 200) set({ skywalkingCurrentTrace: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchSkyWalkingMetrics: async (params) => {
    try {
      const res = await getSkyWalkingMetricsApi(params)
      if (res.data.code === 200) set({ skywalkingMetrics: res.data.data || [] })
    } catch {}
  },

  fetchSkyWalkingAlarms: async (params) => {
    try {
      const res = await getSkyWalkingAlarmsApi(params)
      if (res.data.code === 200) {
        set({ skywalkingAlarms: res.data.data?.items || [], skywalkingAlarmsTotal: res.data.data?.total || 0 })
      }
    } catch {}
  },

  fetchSkyWalkingConsoleUrl: async () => {
    try {
      const res = await getSkyWalkingConsoleUrlApi()
      if (res.data.code === 200) set({ skywalkingConsoleUrl: res.data.data?.url || '' })
    } catch {}
  },

  // ── Prometheus ────────────────────────────────────────
  doQueryPrometheus: async (query, params) => {
    set({ loading: true })
    try {
      const res = await queryPrometheusApi(query, params)
      if (res.data.code === 200) set({ prometheusQueryResult: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  doQueryRangePrometheus: async (query, start, end, step) => {
    set({ loading: true })
    try {
      const res = await queryRangePrometheusApi(query, start, end, step)
      if (res.data.code === 200) set({ prometheusQueryResult: res.data.data, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchPrometheusTargets: async () => {
    try {
      const res = await getPrometheusTargetsApi()
      if (res.data.code === 200) set({ prometheusTargets: res.data.data })
    } catch {}
  },

  fetchPrometheusRules: async () => {
    try {
      const res = await getPrometheusRulesApi()
      if (res.data.code === 200) set({ prometheusRules: res.data.data?.groups || [] })
    } catch {}
  },

  fetchPrometheusConsoleUrl: async () => {
    try {
      const res = await getPrometheusConsoleUrlApi()
      if (res.data.code === 200) set({ prometheusConsoleUrl: res.data.data?.url || '' })
    } catch {}
  },

  reset: () => set({ ...initialState }),
}))