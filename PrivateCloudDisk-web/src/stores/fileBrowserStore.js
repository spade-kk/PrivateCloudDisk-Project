import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import { getFileInfoApi, getFileInfoByPathAndNameApi, getNodeChildrenApi, moveFileApi, renameFileApi, deleteFileApi, getMyUserRootNodeApi, createFolderApi } from '@/api/index'

export const useFileBrowserStore = defineStore('fileBrowser', () => {
  const toastStore = useToastStore()
  const currentNodeId = ref('')
  const pathStack = ref([]) // [{ node_id, node_name }]
  const nodes = ref([])
  const loading = ref(false)
  const error = ref(null)
  const searchKeyword = ref('')

  const filteredNodes = computed(() => {
    if (!searchKeyword.value.trim()) return nodes.value
    const kw = searchKeyword.value.toLowerCase()
    return nodes.value.filter(node => node.node_name.toLowerCase().includes(kw))
  })

  function setLoadError(title, message, rawError) {
    error.value = {
      title,
      message,
      isNetworkError: !!rawError?.isNetworkError,
      isTimeout: !!rawError?.isTimeout,
    }
  }

  async function loadRoot() {
    loading.value = true
    error.value = null
    try {
      const res = await getMyUserRootNodeApi();
      if (res.code === 200 && res.data) {
        const root = res.data
        currentNodeId.value = root.node_id
        pathStack.value = [{ node_id: root.node_id, node_name: '我的网盘' }]
        await loadChildren(currentNodeId.value)
      } else {
        nodes.value = []
        setLoadError('无法加载网盘', res.message || '根目录数据异常，请稍后重试')
      }
    } catch (error) {
      console.error('加载根目录失败', error)
      setLoadError('无法加载网盘', error.message || '根目录加载失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadChildren(nodeId) {
    loading.value = true
    error.value = null
    try {
      const res = await getNodeChildrenApi(nodeId);
      if (res.code === 200) {
        nodes.value = res.data || []
        currentNodeId.value = nodeId
      } else {
        nodes.value = []
        setLoadError('目录加载失败', res.message || '当前目录数据异常，请稍后重试')
      }
    } catch (error) {
      console.error('加载子节点失败', error)
      setLoadError('目录加载失败', error.message || '当前目录加载失败，请稍后重试', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  function navigateTo(node) {
    // 如果 node 已经在路径栈中，则截断到该位置
    const index = pathStack.value.findIndex(p => p.node_id === node.node_id)
    if (index !== -1) {
      pathStack.value = pathStack.value.slice(0, index + 1)
    } else {
      pathStack.value.push(node)
    }
    currentNodeId.value = node.node_id
    loadChildren(node.node_id)
  }

  function goHome() {
    loadRoot()
  }

  async function createFolder(folderName) {
    try {
      const res = await createFolderApi(folderName, currentNodeId.value)
      if (res.code === 200) {
        await loadChildren(currentNodeId.value)
        return { success: true }
      }
      return { success: false, message: res.message || '创建失败' }
    } catch (error) {
      toastStore.showToast('网络错误', 'error')
      return { success: false, message: '网络错误' }
    }
  }

  function refresh() {
    if (currentNodeId.value) {
      return loadChildren(currentNodeId.value)
    }
    return loadRoot()
  }

  function retry() {
    return refresh()
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
  }
})
