// ============================================================
// 安全事件 Store
// ============================================================
import { create } from 'zustand'
import type { SecurityEvent, PageParams } from '@/types/api'
import { getSecurityEventsApi, handleSecurityEventApi } from '@/api/security'

interface SecurityState {
  events: SecurityEvent[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  severity: string | null
  type: string | null

  fetchEvents: () => Promise<void>
  handleEvent: (eventId: string, resolution: string) => Promise<boolean>
  setPage: (page: number) => void
  setPageSize: (pageSize: number) => void
  setSeverity: (severity: string | null) => void
  setType: (type: string | null) => void
  reset: () => void
}

export const useSecurityStore = create<SecurityState>((set, get) => ({
  events: [],
  total: 0,
  page: 1,
  pageSize: 20,
  loading: false,
  severity: null,
  type: null,

  fetchEvents: async () => {
    const { page, pageSize, severity, type } = get()
    set({ loading: true })

    try {
      const params: PageParams = { page, pageSize }
      if (severity) params.severity = severity
      if (type) params.type = type

      const res = await getSecurityEventsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data
        set({
          events: data.records || data.list || [],
          total: data.total || 0,
          loading: false,
        })
      } else {
        set({ loading: false })
      }
    } catch {
      set({ loading: false })
    }
  },

  handleEvent: async (eventId, resolution) => {
    try {
      const res = await handleSecurityEventApi(eventId, resolution)
      if (res.data.code === 200) {
        set((state) => ({
          events: state.events.map((e) =>
            e.id === eventId ? { ...e, handled: true } : e
          ),
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },

  setPage: (page) => {
    set({ page })
    get().fetchEvents()
  },
  setPageSize: (pageSize) => {
    set({ pageSize, page: 1 })
    get().fetchEvents()
  },
  setSeverity: (severity) => {
    set({ severity, page: 1 })
    get().fetchEvents()
  },
  setType: (type) => {
    set({ type, page: 1 })
    get().fetchEvents()
  },
  reset: () =>
    set({
      events: [],
      total: 0,
      page: 1,
      severity: null,
      type: null,
    }),
}))