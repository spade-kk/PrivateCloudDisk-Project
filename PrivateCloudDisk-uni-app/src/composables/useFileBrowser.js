/**
 * composables/useFileBrowser.js - 文件浏览器组合式函数
 *
 * 封装文件浏览核心逻辑：目录导航、节点加载、面包屑管理
 * 支持 node_id 查询和路径查询两种模式
 */
import { reactive, computed } from 'vue'
import { getRootNode, getChildrenPaged as apiGetChildren } from '@/api/node'
import { getMyQuota } from '@/api/quota'
import { PAGE_SIZE } from '@/utils/const'

export function useFileBrowser() {
  const state = reactive({
    rootNodeId: '',
    nodeStack: [],       // [{ node_id, node_name }, ...]
    children: [],
    loading: false,
    loadingMore: false,
    page: 1,
    hasMore: false,
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

  // ========== 节点加载 ==========
  async function loadChildren(page = 1) {
    const nodeId = currentNodeId.value
    if (!nodeId) return

    if (page === 1) {
      state.loading = true
      state.error = null
    }
    try {
      const res = await apiGetChildren(nodeId, {
        page,
        pageSize: PAGE_SIZE,
        sortBy: 'name',
        sortOrder: 'asc'
      })
      const items = res.data?.items || []
      if (page === 1) {
        state.children = items
      } else {
        state.children.push(...items)
      }
      state.hasMore = items.length >= PAGE_SIZE
      state.page = page
    } catch (e) {
      console.error('[useFileBrowser] loadChildren error:', e)
      if (page === 1) state.error = '加载失败'
    } finally {
      state.loading = false
      state.loadingMore = false
    }
  }

  async function loadMore() {
    if (state.loadingMore || !state.hasMore) return
    state.loadingMore = true
    await loadChildren(state.page + 1)
  }

  // ========== 导航 ==========
  function navigateTo(node) {
    state.nodeStack.push({ node_id: node.node_id, node_name: node.node_name })
    loadChildren(1)
  }

  function goBack() {
    if (state.nodeStack.length > 0) {
      state.nodeStack.pop()
      loadChildren(1)
    }
  }

  function goHome() {
    state.nodeStack = []
    loadChildren(1)
  }

  function refresh() {
    loadChildren(1)
  }

  return {
    state,
    currentNodeId,
    currentNodeName,
    isRoot,
    usagePercent,
    init,
    loadChildren,
    loadMore,
    navigateTo,
    goBack,
    goHome,
    refresh
  }
}