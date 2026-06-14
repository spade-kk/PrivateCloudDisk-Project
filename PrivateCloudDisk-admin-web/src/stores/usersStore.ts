// ============================================================
// 用户管理 Store
// ============================================================
import { create } from 'zustand'
import type { User, PageParams, BatchUserAction } from '@/types/api'
import { getUsersApi, toggleUserStatusApi, updateUserRoleApi, deleteUserApi, batchUserActionApi } from '@/api/users'

interface UsersState {
  /** 用户列表 */
  users: User[]
  /** 总数 */
  total: number
  /** 当前页 */
  page: number
  /** 每页条数 */
  pageSize: number
  /** 加载状态 */
  loading: boolean
  /** 搜索关键词 */
  keyword: string
  /** 筛选状态 */
  statusFilter: string | null
  /** 选中行 */
  selectedRowKeys: string[]

  fetchUsers: (params?: Partial<PageParams>) => Promise<void>
  toggleUserStatus: (userId: string, status: string) => Promise<boolean>
  updateUserRole: (userId: string, role: string) => Promise<boolean>
  removeUser: (userId: string) => Promise<boolean>
  batchAction: (action: string, userIds: string[]) => Promise<boolean>
  setPage: (page: number) => void
  setPageSize: (pageSize: number) => void
  setKeyword: (keyword: string) => void
  setStatusFilter: (status: string | null) => void
  setSelectedRowKeys: (keys: string[]) => void
  reset: () => void
}

export const useUsersStore = create<UsersState>((set, get) => ({
  users: [],
  total: 0,
  page: 1,
  pageSize: 20,
  loading: false,
  keyword: '',
  statusFilter: null,
  selectedRowKeys: [],

  fetchUsers: async () => {
    const { page, pageSize, keyword, statusFilter } = get()
    set({ loading: true })

    try {
      const params: PageParams = { page, pageSize }
      if (keyword) params.keyword = keyword
      if (statusFilter) params.status = statusFilter

      const res = await getUsersApi(params)
      if (res.data.code === 200) {
        const data = res.data.data
        set({
          users: data.records || data.list || [],
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

  toggleUserStatus: async (userId, status) => {
    try {
      const res = await toggleUserStatusApi(userId, status)
      if (res.data.code === 200) {
        set((state) => ({
          users: state.users.map((u) =>
            u.userId === userId ? { ...u, status: status as User['status'] } : u
          ),
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },

  updateUserRole: async (userId, role) => {
    try {
      const res = await updateUserRoleApi(userId, role)
      if (res.data.code === 200) {
        set((state) => ({
          users: state.users.map((u) =>
            u.userId === userId ? { ...u, role } : u
          ),
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },

  removeUser: async (userId) => {
    try {
      const res = await deleteUserApi(userId)
      if (res.data.code === 200) {
        set((state) => ({
          users: state.users.filter((u) => u.userId !== userId),
          total: state.total - 1,
          selectedRowKeys: state.selectedRowKeys.filter((k) => k !== userId),
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },

  batchAction: async (action, userIds) => {
    try {
      const data: BatchUserAction = {
        action: action as BatchUserAction['action'],
        userIds,
      }
      const res = await batchUserActionApi(data)
      if (res.data.code === 200) {
        await get().fetchUsers()
        set({ selectedRowKeys: [] })
        return true
      }
      return false
    } catch {
      return false
    }
  },

  setPage: (page) => {
    set({ page })
    get().fetchUsers()
  },
  setPageSize: (pageSize) => {
    set({ pageSize, page: 1 })
    get().fetchUsers()
  },
  setKeyword: (keyword) => {
    set({ keyword, page: 1 })
  },
  setStatusFilter: (statusFilter) => {
    set({ statusFilter, page: 1 })
  },
  setSelectedRowKeys: (selectedRowKeys) => set({ selectedRowKeys }),
  reset: () =>
    set({
      users: [],
      total: 0,
      page: 1,
      pageSize: 20,
      keyword: '',
      statusFilter: null,
      selectedRowKeys: [],
    }),
}))