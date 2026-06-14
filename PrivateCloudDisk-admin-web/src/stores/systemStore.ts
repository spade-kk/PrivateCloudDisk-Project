// ============================================================
// 系统管理 Store
// ============================================================
import { create } from 'zustand'
import type { SystemOverview, SystemResources, OnlineUser, SystemConfig } from '@/types/api'
import {
  getSystemOverviewApi,
  getSystemResourcesApi,
  getOnlineUsersApi,
  getSystemConfigApi,
  updateSystemConfigApi,
} from '@/api/system'

interface SystemState {
  overview: SystemOverview | null
  resources: SystemResources | null
  onlineUsers: OnlineUser[]
  config: SystemConfig | null
  loading: {
    overview: boolean
    resources: boolean
    onlineUsers: boolean
    config: boolean
  }

  fetchOverview: () => Promise<void>
  fetchResources: () => Promise<void>
  fetchOnlineUsers: () => Promise<void>
  fetchConfig: () => Promise<void>
  updateConfig: (config: Partial<SystemConfig>) => Promise<boolean>
}

export const useSystemStore = create<SystemState>((set) => ({
  overview: null,
  resources: null,
  onlineUsers: [],
  config: null,
  loading: {
    overview: false,
    resources: false,
    onlineUsers: false,
    config: false,
  },

  fetchOverview: async () => {
    set((s) => ({ loading: { ...s.loading, overview: true } }))
    try {
      const res = await getSystemOverviewApi()
      if (res.data.code === 200) {
        set({ overview: res.data.data })
      }
    } catch {
      // 静默失败
    } finally {
      set((s) => ({ loading: { ...s.loading, overview: false } }))
    }
  },

  fetchResources: async () => {
    set((s) => ({ loading: { ...s.loading, resources: true } }))
    try {
      const res = await getSystemResourcesApi()
      if (res.data.code === 200) {
        set({ resources: res.data.data })
      }
    } catch {
      // 静默失败
    } finally {
      set((s) => ({ loading: { ...s.loading, resources: false } }))
    }
  },

  fetchOnlineUsers: async () => {
    set((s) => ({ loading: { ...s.loading, onlineUsers: true } }))
    try {
      const res = await getOnlineUsersApi()
      if (res.data.code === 200) {
        set({ onlineUsers: res.data.data || [] })
      }
    } catch {
      // 静默失败
    } finally {
      set((s) => ({ loading: { ...s.loading, onlineUsers: false } }))
    }
  },

  fetchConfig: async () => {
    set((s) => ({ loading: { ...s.loading, config: true } }))
    try {
      const res = await getSystemConfigApi()
      if (res.data.code === 200) {
        set({ config: res.data.data })
      }
    } catch {
      // 静默失败
    } finally {
      set((s) => ({ loading: { ...s.loading, config: false } }))
    }
  },

  updateConfig: async (config) => {
    try {
      const res = await updateSystemConfigApi(config)
      if (res.data.code === 200) {
        set((state) => ({
          config: state.config ? { ...state.config, ...config } : null,
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },
}))