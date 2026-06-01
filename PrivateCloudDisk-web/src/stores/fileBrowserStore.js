import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import { getFileInfoApi, getFileInfoByPathAndNameApi, getNodeChildrenApi, moveFileApi, renameFileApi, deleteFileApi, getMyUserRootNodeApi, createFolderApi } from '@/api/index'

const toastStore = useToastStore()

export const useFileBrowserStore = defineStore('fileBrowser', () => {
  const currentNodeId = ref('')
  const pathStack = ref([]) // [{ node_id, node_name }]
  const nodes = ref([])
  const loading = ref(false)
  const searchKeyword = ref('')

  const filteredNodes = computed(() => {
    if (!searchKeyword.value.trim()) return nodes.value
    const kw = searchKeyword.value.toLowerCase()
    return nodes.value.filter(node => node.node_name.toLowerCase().includes(kw))
  })

  async function loadRoot() {
    loading.value = true
    try {
      const res = await getMyUserRootNodeApi();
      if (res.code === 200 && res.data) {
        const root = res.data
        currentNodeId.value = root.node_id
        pathStack.value = [{ node_id: root.node_id, node_name: '我的网盘' }]
        await loadChildren(currentNodeId.value)
        toastStore.showToast('加载根目录成功', 'success')
      } else {
        nodes.value = []
      }
    } catch (error) {
      toastStore.showToast('加载根目录失败', 'error')
      console.error('加载根目录失败', error)
      nodes.value = []
    } finally {
      loading.value = false
    }
  }

  async function loadChildren(nodeId) {
    loading.value = true
    try {
      const res = await getNodeChildrenApi(nodeId);
      if (res.code === 200) {
        nodes.value = res.data || []
        currentNodeId.value = nodeId
      } else {
        nodes.value = []
      }
    } catch (error) {
      toastStore.showToast('加载子节点失败', 'error')
      console.error('加载子节点失败', error)
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
      loadChildren(currentNodeId.value)
    }
  }

  return {
    currentNodeId,
    pathStack,
    nodes,
    filteredNodes,
    loading,
    searchKeyword,
    loadRoot,
    loadChildren,
    navigateTo,
    goHome,
    createFolder,
    refresh,
  }
})