/**
 * composables/useFileBrowser.js - 文件浏览器组合式函数
 *
 * 封装文件浏览核心逻辑：目录导航、节点加载、面包屑管理
 *
 * 对标 Vue3 Web 项目：使用非分页 getChildren 端点，
 * 一次性加载当前目录所有子节点。
 * 分页查询（getChildrenPaged）保留供后续按需使用。
 */
import { reactive, computed } from 'vue'
import { getRootNode, getChildren as apiGetChildren } from '@/api/node'
import { getMyQuota } from '@/api/quota'

export function useFileBrowser() {
  const state = reactive({
    rootNodeId: '',
    nodeStack: [],       // [{ node_id, node_name }, ...]
    children: [],
    loading: false,
    quota: null,
    error: null
  })

  // ========== 计算属性 ==========
  const currentNodeId = computed(() => {
    if (state.nodeStack.length === 0) return state.rootNodeId
    return state.nodeStack[state.nodeStack.length - 1].node_id
  })

  const currentNodeName = computed(() => {
    if (state.nodeStack.length === 0) return '首页'
    return state.nodeStack[state.nodeStack.length - 1].node_name
  })

  const isRoot = computed(() => state.nodeStack.length === 0)

  const usagePercent = computed(() => {
    if (!state.quota?.total_capacity) return 0
    return ((state.quota.used_capacity / state.quota.total_capacity) * 100).toFixed(1)
  })

  // ========== 初始化 ==========
  async function init() {
    try {
      const [rootRes, quotaRes] = await Promise.all([
        getRootNode(),
        getMyQuota()
      ])
      state.rootNodeId = rootRes.data.node_id
      state.quota = quotaRes.data
    } catch (e) {
      console.error('[useFileBrowser] init error:', e)
      state.error = '初始化失败'
    }
  }

  // ========== 节点加载（非分页，对标 Vue3 getNodeChildrenApi） ==========
  async function loadChildren() {
    const nodeId = currentNodeId.value
    if (!nodeId) return

    state.loading = true
    state.error = null
    try {
      const res = await apiGetChildren(nodeId)
      // 对标 Vue3: getNodeChildrenApi 返回 data 为子节点数组
      state.children = Array.isArray(res.data) ? res.data : (res.data?.items || [])
    } catch (e) {
      console.error('[useFileBrowser] loadChildren error:', e)
      state.error = '加载失败'
    } finally {
      state.loading = false
    }
  }

  // ========== 导航 ==========
  function navigateTo(node) {
    state.nodeStack.push({ node_id: node.node_id, node_name: node.node_name })
    loadChildren()
  }

  function goBack() {
    if (state.nodeStack.length > 0) {
      state.nodeStack.pop()
      loadChildren()
    }
  }

  function goHome() {
    state.nodeStack = []
    loadChildren()
  }

  function refresh() {
    loadChildren()
  }

  return {
    state,
    currentNodeId,
    currentNodeName,
    isRoot,
    usagePercent,
    init,
    loadChildren,
    navigateTo,
    goBack,
    goHome,
    refresh
  }
}