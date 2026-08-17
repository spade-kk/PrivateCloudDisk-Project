import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import { useSpaceStore } from './spaceStore'
import { getFileInfoApi, getFileInfoByPathAndNameApi, getNodeChildrenApi, getNodeInfoApi, resolvePathToNodeIdApi, getChildrenByPathApi, moveFileApi, moveNodeApi, renameFileApi, getMyUserRootNodeApi, createFolderApi, renameNodeApi, moveFileToTrashApi, moveFolderToTrashApi } from '@/api/index'

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
  const spaceStore = useSpaceStore()
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
        // 需求四-2：面包屑第一级展示当前空间名称；未初始化时仍兼容显示“我的网盘”。
        pathStack.value = [{ node_id: root.node_id, node_name: spaceStore.currentSpaceName }]
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

  /**
   * 通过绝对路径加载子节点（混合查询模型）。
   * 返回 { node_id, children }，供客户端保存 node_id 以便后续切换为 node_id 查询。
   *
   * @param absolutePath 绝对路径（面包屑路径），如 "xxx/hello"
   */
  async function loadChildrenByPath(absolutePath: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await getChildrenByPathApi({ absolute_path: absolutePath })
      if (res.code === 200 && res.data) {
        nodes.value = res.data.children || []
        currentNodeId.value = res.data.node_id
        // 重建面包屑路径
        await rebuildPathStack(currentNodeId.value)
      } else {
        nodes.value = []
        setLoadError('目录加载失败', res.message || '路径查询失败，请稍后重试')
      }
    } catch (error: any) {
      console.error('路径加载子节点失败', error)
      setLoadError('目录加载失败', error.message || '路径查询失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 通过父节点 + 相对路径加载子节点。
   */
  async function loadChildrenByRelativePath(parentNodeId: string, relativePath: string): Promise<void> {
    loading.value = true
    error.value = null
    try {
      const res = await getChildrenByPathApi({
        node_id: parentNodeId,
        relative_path: relativePath,
      })
      if (res.code === 200 && res.data) {
        nodes.value = res.data.children || []
        currentNodeId.value = res.data.node_id
        await rebuildPathStack(currentNodeId.value)
      } else {
        nodes.value = []
        setLoadError('目录加载失败', res.message || '路径查询失败，请稍后重试')
      }
    } catch (error: any) {
      console.error('路径加载子节点失败', error)
      setLoadError('目录加载失败', error.message || '路径查询失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  /**
   * 将路径解析为 node_id（不加载子节点）。
   * 纯路径 → node_id 转换，供客户端在需要时调用。
   */
  async function resolvePathToNodeId(absolutePath: string): Promise<string | null> {
    try {
      const res = await resolvePathToNodeIdApi({ absolute_path: absolutePath })
      if (res.code === 200 && res.data) {
        return res.data.node_id
      }
      return null
    } catch (error: any) {
      console.error('路径解析失败', error)
      return null
    }
  }

  /**
   * 重建面包屑路径栈。
   * 从目标节点向上遍历父节点链，构建完整的路径栈。
   */
  async function rebuildPathStack(nodeId: string): Promise<void> {
    try {
      const chain: PathNode[] = []
      let currentId: string | null = nodeId

      while (currentId) {
        const res = await getNodeInfoApi(currentId)
        if (res.code === 200 && res.data) {
          chain.unshift({
            node_id: res.data.node_id,
            node_name: res.data.name,
          })
          currentId = res.data.parent_id || null
        } else {
          break
        }
      }

      if (chain.length > 0) {
        chain[0].node_name = spaceStore.currentSpaceName
        pathStack.value = chain
      }
    } catch (error) {
      console.error('重建路径栈失败', error)
    }
  }

  /**
   * 从当前 pathStack 构建面包屑路径字符串。
   * 根节点返回 "/"，子目录返回 "/folder1/subfolder2"。
   * 用于 URL query 参数 ?path=xxx，始终显示人类可读的路径。
   */
  function buildBreadcrumbPath(): string {
    if (pathStack.value.length === 0) return '/'
    // 跳过根节点（index 0），剩余部分拼接为路径
    const parts = pathStack.value.slice(1).map(p => p.node_name)
    if (parts.length === 0) return '/'
    return '/' + parts.join('/')
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
    loadChildrenByPath,
    loadChildrenByRelativePath,
    resolvePathToNodeId,
    rebuildPathStack,
    buildBreadcrumbPath,
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
