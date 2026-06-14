// ============================================================
// 仪表盘 Store
// ============================================================
import { create } from 'zustand'
import type { DashboardData } from '@/types/api'
import { getDashboardApi } from '@/api/system'
import { getSystemOverviewApi, getSystemResourcesApi } from '@/api/system'

interface DashboardState {
  data: DashboardData | null
  loading: boolean
  error: string | null

  fetchDashboard: () => Promise<void>
  refreshOverview: () => Promise<void>
  refreshResources: () => Promise<void>
}

export const useDashboardStore = create<DashboardState>((set) => ({
  data: null,
  loading: false,
  error: null,

  fetchDashboard: async () => {
    set({ loading: true, error: null })
    try {
      const res = await getDashboardApi()
      if (res.data.code === 200) {
        set({ data: res.data.data, loading: false })
      } else {
        set({ error: res.data.message || '获取仪表盘数据失败', loading: false })
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '网络错误'
      set({ error: msg, loading: false })
    }
  },

  refreshOverview: async () => {
    try {
      const [overviewRes] = await Promise.all([
        getSystemOverviewApi(),
        getSystemResourcesApi(),
      ])

      const { data } = useDashboardStore.getState()
      if (data) {
        const updated = { ...data }
        if (overviewRes.data.code === 200) {
          updated.overview = overviewRes.data.data
        }
        set({ data: updated })
      }
    } catch {
      // 静默失败
    }
  },

  refreshResources: async () => {
    try {
      const res = await getSystemResourcesApi()
      // 资源数据是 dashboard 的一部分，这里不单独存储
      // 如果独立需要，可以扩展
      void res
    } catch {
      // 静默失败
    }
  },
}))