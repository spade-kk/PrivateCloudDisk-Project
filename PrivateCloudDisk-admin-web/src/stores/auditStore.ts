// ============================================================
// 审计日志 Store
// ============================================================
import { create } from 'zustand'
import type { AuditLog, AuditLogFilterParams } from '@/types/api'
import { getAuditLogsApi } from '@/api/audit'

interface AuditState {
  logs: AuditLog[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  userId: string | null
  action: string | null
  status: string | null
  startDate: string | null
  endDate: string | null

  fetchLogs: () => Promise<void>
  setPage: (page: number) => void
  setPageSize: (pageSize: number) => void
  setUserId: (userId: string | null) => void
  setAction: (action: string | null) => void
  setStatus: (status: string | null) => void
  setDateRange: (start: string | null, end: string | null) => void
  reset: () => void
}

export const useAuditStore = create<AuditState>((set, get) => ({
  logs: [],
  total: 0,
  page: 1,
  pageSize: 20,
  loading: false,
  userId: null,
  action: null,
  status: null,
  startDate: null,
  endDate: null,

  fetchLogs: async () => {
    const { page, pageSize, userId, action, status, startDate, endDate } = get()
    set({ loading: true })

    try {
      const params: AuditLogFilterParams = { page, pageSize }
      if (userId) params.userId = userId
      if (action) params.action = action
      if (status) params.status = status
      if (startDate) params.startDate = startDate
      if (endDate) params.endDate = endDate

      const res = await getAuditLogsApi(params)
      if (res.data.code === 200) {
        const data = res.data.data
        set({
          logs: data.records || data.list || [],
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

  setPage: (page) => {
    set({ page })
    get().fetchLogs()
  },
  setPageSize: (pageSize) => {
    set({ pageSize, page: 1 })
    get().fetchLogs()
  },
  setUserId: (userId) => {
    set({ userId, page: 1 })
    get().fetchLogs()
  },
  setAction: (action) => {
    set({ action, page: 1 })
    get().fetchLogs()
  },
  setStatus: (status) => {
    set({ status, page: 1 })
    get().fetchLogs()
  },
  setDateRange: (startDate, endDate) => {
    set({ startDate, endDate, page: 1 })
    get().fetchLogs()
  },
  reset: () =>
    set({
      logs: [],
      total: 0,
      page: 1,
      userId: null,
      action: null,
      status: null,
      startDate: null,
      endDate: null,
    }),
}))