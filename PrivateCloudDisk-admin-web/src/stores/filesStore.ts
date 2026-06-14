// ============================================================
// 文件管理 Store
// ============================================================
import { create } from 'zustand'
import type { FileNode, FileFilterParams, StorageStats } from '@/types/api'
import { getFilesApi, adminDeleteFileApi, batchDeleteFilesApi, getStorageStatsApi } from '@/api/files'

interface FilesState {
  files: FileNode[]
  total: number
  page: number
  pageSize: number
  loading: boolean
  keyword: string
  nodeType: string | null
  virusScanStatus: string | null
  selectedRowKeys: string[]
  storageStats: StorageStats | null

  fetchFiles: () => Promise<void>
  removeFile: (fileId: string) => Promise<boolean>
  batchRemoveFiles: (fileIds: string[]) => Promise<boolean>
  fetchStorageStats: () => Promise<void>
  setPage: (page: number) => void
  setPageSize: (pageSize: number) => void
  setKeyword: (keyword: string) => void
  setNodeType: (nodeType: string | null) => void
  setVirusScanStatus: (status: string | null) => void
  setSelectedRowKeys: (keys: string[]) => void
  reset: () => void
}

export const useFilesStore = create<FilesState>((set, get) => ({
  files: [],
  total: 0,
  page: 1,
  pageSize: 20,
  loading: false,
  keyword: '',
  nodeType: null,
  virusScanStatus: null,
  selectedRowKeys: [],
  storageStats: null,

  fetchFiles: async () => {
    const { page, pageSize, keyword, nodeType, virusScanStatus } = get()
    set({ loading: true })

    try {
      const params: FileFilterParams = { page, pageSize }
      if (keyword) params.keyword = keyword
      if (nodeType) params.nodeType = nodeType as 'FOLDER' | 'FILE'
      if (virusScanStatus) params.virusScanStatus = virusScanStatus

      const res = await getFilesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data
        set({
          files: data.records || data.list || [],
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

  removeFile: async (fileId) => {
    try {
      const res = await adminDeleteFileApi(fileId)
      if (res.data.code === 200) {
        set((state) => ({
          files: state.files.filter((f) => f.nodeId !== fileId),
          total: state.total - 1,
          selectedRowKeys: state.selectedRowKeys.filter((k) => k !== fileId),
        }))
        return true
      }
      return false
    } catch {
      return false
    }
  },

  batchRemoveFiles: async (fileIds) => {
    try {
      const res = await batchDeleteFilesApi(fileIds)
      if (res.data.code === 200) {
        await get().fetchFiles()
        set({ selectedRowKeys: [] })
        return true
      }
      return false
    } catch {
      return false
    }
  },

  fetchStorageStats: async () => {
    try {
      const res = await getStorageStatsApi()
      if (res.data.code === 200) {
        set({ storageStats: res.data.data })
      }
    } catch {
      // 静默失败
    }
  },

  setPage: (page) => {
    set({ page })
    get().fetchFiles()
  },
  setPageSize: (pageSize) => {
    set({ pageSize, page: 1 })
    get().fetchFiles()
  },
  setKeyword: (keyword) => {
    set({ keyword, page: 1 })
  },
  setNodeType: (nodeType) => {
    set({ nodeType, page: 1 })
  },
  setVirusScanStatus: (virusScanStatus) => {
    set({ virusScanStatus, page: 1 })
  },
  setSelectedRowKeys: (selectedRowKeys) => set({ selectedRowKeys }),
  reset: () =>
    set({
      files: [],
      total: 0,
      page: 1,
      keyword: '',
      nodeType: null,
      virusScanStatus: null,
      selectedRowKeys: [],
    }),
}))