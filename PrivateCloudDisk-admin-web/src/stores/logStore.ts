// ============================================================
// 日志系统 Store
// Loki / Kibana
// ============================================================
import { create } from 'zustand'
import type { PageResult } from '@/types/api'
import {
  queryLokiApi, queryLokiRangeApi, getLokiLabelsApi, getLokiLabelValuesApi, getLokiConsoleUrlApi,
  getKibanaIndexPatternsApi, getKibanaSavedSearchesApi, getKibanaDashboardsApi,
  searchElasticsearchApi, getKibanaEmbedUrlApi, getKibanaConsoleUrlApi,
  queryLogsApi, getLogLevelsApi, getLogServicesApi,
  type LokiQueryResult, type LokiLabel, type LokiQueryParams,
  type KibanaIndexPattern, type KibanaSavedSearch, type KibanaDashboard,
  type ElasticsearchQuery, type ElasticsearchHit,
  type LogEntry, type LogQueryParams,
} from '@/api/logs'

interface LogState {
  // Loki
  lokiResult: LokiQueryResult | null
  lokiLabels: LokiLabel[]
  lokiLabelValues: string[]
  lokiConsoleUrl: string
  // Kibana
  kibanaIndexPatterns: KibanaIndexPattern[]
  kibanaSavedSearches: KibanaSavedSearch[]
  kibanaDashboards: KibanaDashboard[]
  kibanaSearchHits: ElasticsearchHit[]
  kibanaSearchTotal: number
  kibanaEmbedUrl: string
  kibanaConsoleUrl: string
  // 通用日志
  logs: LogEntry[]
  logsTotal: number
  logLevels: Array<{ level: string; count: number }>
  logServices: string[]
  // 通用
  loading: boolean
  error: string | null

  // Loki
  doQueryLoki: (params: LokiQueryParams) => Promise<void>
  doQueryLokiRange: (params: LokiQueryParams) => Promise<void>
  fetchLokiLabels: () => Promise<void>
  fetchLokiLabelValues: (labelName: string) => Promise<void>
  fetchLokiConsoleUrl: () => Promise<void>
  // Kibana
  fetchKibanaIndexPatterns: () => Promise<void>
  fetchKibanaSavedSearches: () => Promise<void>
  fetchKibanaDashboards: () => Promise<void>
  doSearchElasticsearch: (data: ElasticsearchQuery) => Promise<void>
  fetchKibanaEmbedUrl: (dashboardId: string) => Promise<void>
  fetchKibanaConsoleUrl: () => Promise<void>
  // 通用日志
  fetchLogs: (params: LogQueryParams) => Promise<void>
  fetchLogLevels: () => Promise<void>
  fetchLogServices: () => Promise<void>
  reset: () => void
}

const initialState = {
  lokiResult: null as LokiQueryResult | null,
  lokiLabels: [] as LokiLabel[],
  lokiLabelValues: [] as string[],
  lokiConsoleUrl: '',
  kibanaIndexPatterns: [] as KibanaIndexPattern[],
  kibanaSavedSearches: [] as KibanaSavedSearch[],
  kibanaDashboards: [] as KibanaDashboard[],
  kibanaSearchHits: [] as ElasticsearchHit[],
  kibanaSearchTotal: 0,
  kibanaEmbedUrl: '',
  kibanaConsoleUrl: '',
  logs: [] as LogEntry[],
  logsTotal: 0,
  logLevels: [] as Array<{ level: string; count: number }>,
  logServices: [] as string[],
  loading: false,
  error: null as string | null,
}

export const useLogStore = create<LogState>((set) => ({
  ...initialState,

  // ── Loki ──────────────────────────────────────────────
  doQueryLoki: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await queryLokiApi(params)
      if (res.data.code === 200) set({ lokiResult: res.data.data, loading: false })
      else set({ error: res.data.message, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  doQueryLokiRange: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await queryLokiRangeApi(params)
      if (res.data.code === 200) set({ lokiResult: res.data.data, loading: false })
      else set({ error: res.data.message, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchLokiLabels: async () => {
    try {
      const res = await getLokiLabelsApi()
      if (res.data.code === 200) set({ lokiLabels: res.data.data || [] })
    } catch {}
  },

  fetchLokiLabelValues: async (labelName) => {
    try {
      const res = await getLokiLabelValuesApi(labelName)
      if (res.data.code === 200) set({ lokiLabelValues: res.data.data || [] })
    } catch {}
  },

  fetchLokiConsoleUrl: async () => {
    try {
      const res = await getLokiConsoleUrlApi()
      if (res.data.code === 200) set({ lokiConsoleUrl: res.data.data?.url || '' })
    } catch {}
  },

  // ── Kibana ────────────────────────────────────────────
  fetchKibanaIndexPatterns: async () => {
    try {
      const res = await getKibanaIndexPatternsApi()
      if (res.data.code === 200) set({ kibanaIndexPatterns: res.data.data || [] })
    } catch {}
  },

  fetchKibanaSavedSearches: async () => {
    try {
      const res = await getKibanaSavedSearchesApi()
      if (res.data.code === 200) set({ kibanaSavedSearches: res.data.data || [] })
    } catch {}
  },

  fetchKibanaDashboards: async () => {
    try {
      const res = await getKibanaDashboardsApi()
      if (res.data.code === 200) set({ kibanaDashboards: res.data.data || [] })
    } catch {}
  },

  doSearchElasticsearch: async (data) => {
    set({ loading: true, error: null })
    try {
      const res = await searchElasticsearchApi(data)
      if (res.data.code === 200) {
        set({
          kibanaSearchHits: res.data.data?.hits || [],
          kibanaSearchTotal: res.data.data?.total || 0,
          loading: false,
        })
      } else set({ error: res.data.message, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchKibanaEmbedUrl: async (dashboardId) => {
    try {
      const res = await getKibanaEmbedUrlApi(dashboardId)
      if (res.data.code === 200) set({ kibanaEmbedUrl: res.data.data?.url || '' })
    } catch {}
  },

  fetchKibanaConsoleUrl: async () => {
    try {
      const res = await getKibanaConsoleUrlApi()
      if (res.data.code === 200) set({ kibanaConsoleUrl: res.data.data?.url || '' })
    } catch {}
  },

  // ── 通用日志 ──────────────────────────────────────────
  fetchLogs: async (params) => {
    set({ loading: true, error: null })
    try {
      const res = await queryLogsApi(params)
      if (res.data.code === 200) {
        set({ logs: res.data.data?.logs || [], logsTotal: res.data.data?.total || 0, loading: false })
      } else set({ error: res.data.message, loading: false })
    } catch (err: unknown) { set({ error: (err as Error).message, loading: false }) }
  },

  fetchLogLevels: async () => {
    try {
      const res = await getLogLevelsApi()
      if (res.data.code === 200) set({ logLevels: res.data.data || [] })
    } catch {}
  },

  fetchLogServices: async () => {
    try {
      const res = await getLogServicesApi()
      if (res.data.code === 200) set({ logServices: res.data.data || [] })
    } catch {}
  },

  reset: () => set({ ...initialState }),
}))