import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import { getFileInfoApi, getFileInfoByPathAndNameApi, getNodeChildrenApi, moveFileApi, moveNodeApi, renameFileApi, getMyUserRootNodeApi, createFolderApi, renameNodeApi, moveFileToTrashApi, moveFolderToTrashApi } from '@/api/index'

export interface PathNode {
  node_id: string
  node_name: string
}

export interface FileNode {
  node_id: string
  node_name: string
  node_type: string
  file_size?: number
  [key: string]: any
}

export interface LoadError {
  title: string
  message: string
  isNetworkError?: boolean
  isTimeout?: boolean
}

export const useFileBrowserStore = defineStore('fileBrowser', () => {
  const toastStore = useToastStore()
  const currentNodeId = ref('')
  const pathStack = ref<PathNode[]>([])
  const nodes = ref<FileNode[]>([])
  const loading = ref(false)
  const error = ref<LoadError | null>(null)
  const searchKeyword = ref('')

  const filteredNodes = computed(() => {
    if (!searchKeyword.value.trim()) return nodes.value
    const kw = searchKeyword.value.toLowerCase()
    return nodes.value.filter(node => node.node_name.toLowerCase().includes(kw))
  })

  function setLoadError(title: string, message: string, rawError?: any): void {
    error.value = {
      title,
      message,
      isNetworkError: !!rawError?.isNetworkError,
      isTimeout: !!rawError?.isTimeout,
    }
  }

  async function loadRoot(): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await getMyUserRootNodeApi()
      if (res.code === 200 && res.data) {
        const root = res.data
        currentNodeId.value = root.node_id
        pathStack.value = [{ node_id: root.node_id, node_name: '我的网盘' }]
        await loadChildren(currentNodeId.value)
      } else {
        nodes.value = []
        setLoadError('无法加载网盘', res.message || '根目录数据异常，请稍后重试')
      }
    } catch (error: any) {
      console.error('加载根目录失败', error)
      setLoadError('无法加载网盘', error.message || '根目录加载失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadChildren(nodeId: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await getNodeChildrenApi(nodeId)
      if (res.code === 200) {
        nodes.value = res.data || []
        currentNodeId.value = nodeId
      } else {
        nodes.value = []
        setLoadError('目录加载失败', res.message || '当前目录数据异常，请稍后重试')
      }
    } catch (error: any) {
      console.error('加载子节点失败', error)
      setLoadError('目录加载失败', error.message || '当前目录加载失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  function navigateTo(node: PathNode): void {
    const index = pathStack.value.findIndex(p => p.node_id === node.node_id)
    if (index !== -1) {
      pathStack.value = pathStack.value.slice(0, index + 1)
    } else {
      pathStack.value.push(node)
    }
    currentNodeId.value = node.node_id
    loadChildren(node.node_id)
  }

  function goHome(): void {
    loadRoot()
  }

  async function createFolder(folderName: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await createFolderApi(currentNodeId.value, folderName)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '创建失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function moveFile(fileId: string, targetNodeId: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await moveFileApi(fileId, targetNodeId)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '移动文件失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function moveFolder(nodeId: string, targetNodeId: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await moveNodeApi(nodeId, targetNodeId)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '移动文件夹失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function renameFileNode(nodeId: string, newName: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await renameFileApi(nodeId, newName)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '重命名文件失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function renameFolderNode(nodeId: string, newName: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await renameNodeApi(nodeId, newName)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '重命名文件夹失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function deleteFileNode(nodeId: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await moveFileToTrashApi(nodeId)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '移入回收站失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  async function deleteFolderNode(nodeId: string): Promise<{ success: boolean; message?: string }> {
    try {
      const res = await moveFolderToTrashApi(nodeId)
      if (res.code === 200) {
        refresh()
        return { success: true }
      }
      return { success: false, message: res.message || '移入回收站失败' }
    } catch (error: any) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  function refresh(): void {
    if (currentNodeId.value) {
      loadChildren(currentNodeId.value)
    } else {
      loadRoot()
    }
  }

  function retry(): void {
    refresh()
  }

  return {
    currentNodeId,
    pathStack,
    nodes,
    filteredNodes,
    loading,
    error,
    searchKeyword,
    loadRoot,
    loadChildren,
    navigateTo,
    goHome,
    createFolder,
    refresh,
    retry,
    moveFile,
    moveFolder,
    renameFileNode,
    renameFolderNode,
    deleteFileNode,
    deleteFolderNode,
  }
})