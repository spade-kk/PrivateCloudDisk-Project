/**
 * store/appStore.js - 全局应用状态管理 (Zustand)
 *
 * 管理: 目录导航栈、选中文件、配额信息、上传队列、侧边栏
 */
import { create } from 'zustand'
import { getRootNode } from '@/api/node'

export const useAppStore = create((set, get) => ({
  // ==================== 目录导航 ====================
  directoryStack: [{ id: 'root', name: '根目录' }],
  currentNodeId: 'root',
  currentNodeName: '根目录',

  /** 进入子目录 */
  pushDirectory: (node) => {
    const { directoryStack } = get()
    set({
      directoryStack: [...directoryStack, node],
      currentNodeId: node.id,
      currentNodeName: node.name
    })
  },

  /** 返回上级目录 */
  popDirectory: () => {
    const { directoryStack } = get()
    if (directoryStack.length <= 1) return
    const newStack = directoryStack.slice(0, -1)
    const last = newStack[newStack.length - 1]
    set({
      directoryStack: newStack,
      currentNodeId: last.id,
      currentNodeName: last.name
    })
  },

  /** 跳转到指定目录 */
  navigateToDirectory: (index) => {
    const { directoryStack } = get()
    if (index < 0 || index >= directoryStack.length) return
    const newStack = directoryStack.slice(0, index + 1)
    const target = newStack[newStack.length - 1]
    set({
      directoryStack: newStack,
      currentNodeId: target.id,
      currentNodeName: target.name
    })
  },

  /** 重置导航 (回到根目录) */
  resetDirectory: () => {
    set({
      directoryStack: [{ id: 'root', name: '根目录' }],
      currentNodeId: 'root',
      currentNodeName: '根目录'
    })
  },

  // ==================== 选中文件 ====================
  selectedFiles: [],
  selectMode: false,

  toggleSelect: (file) => {
    const { selectedFiles } = get()
    const exists = selectedFiles.find(f => f.id === file.id)
    if (exists) {
      set({ selectedFiles: selectedFiles.filter(f => f.id !== file.id) })
    } else {
      set({ selectedFiles: [...selectedFiles, file] })
    }
  },

  clearSelection: () => set({ selectedFiles: [], selectMode: false }),

  setSelectMode: (mode) => set({ selectMode: mode }),

  // ==================== 配额 ====================
  quota: null,  // { total_capacity, used_capacity, file_count }

  setQuota: (quota) => set({ quota }),

  // ==================== 上传队列 ====================
  uploadQueue: [],

  addUploadTask: (task) => {
    set({ uploadQueue: [...get().uploadQueue, task] })
  },

  removeUploadTask: (taskId) => {
    set({ uploadQueue: get().uploadQueue.filter(t => t.id !== taskId) })
  },

  updateUploadTask: (taskId, updates) => {
    set({
      uploadQueue: get().uploadQueue.map(t =>
        t.id === taskId ? { ...t, ...updates } : t
      )
    })
  },

  clearUploadQueue: () => set({ uploadQueue: [] }),

  // ==================== 侧边栏 ====================
  sidebarCollapsed: false,

  toggleSidebar: () => set({ sidebarCollapsed: !get().sidebarCollapsed }),

  // ==================== 全局搜索 ====================
  searchKeyword: '',

  setSearchKeyword: (keyword) => set({ searchKeyword: keyword })
}))