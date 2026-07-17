/**
 * store/app.js - 全局应用状态
 *
 * 管理: 当前目录栈、网络状态、全局加载等
 */
import { defineStore } from 'pinia'

export const useAppStore = defineStore('app', {
  state: () => ({
    /** 根节点 ID (用户云盘根目录) */
    rootNodeId: null,

    /** 当前浏览目录栈 [{ node_id, name }, ...] */
    nodeStack: [],

    /** 当前选中的节点 (用于操作菜单) */
    selectedNode: null,

    /** 全局加载状态 */
    loading: false,

    /** 网络状态 */
    networkConnected: true,

    /** 配额信息 */
    quota: null  // { total_capacity, used_capacity, file_count }
  }),

  getters: {
    /** 当前所在目录 ID (栈顶) */
    currentNodeId(state) {
      if (state.nodeStack.length === 0) return state.rootNodeId
      return state.nodeStack[state.nodeStack.length - 1].node_id
    },

    /** 当前目录名 */
    currentNodeName(state) {
      if (state.nodeStack.length === 0) return '首页'
      return state.nodeStack[state.nodeStack.length - 1].name
    },

    /** 是否在根目录 */
    isRoot(state) {
      return state.nodeStack.length === 0
    },

    /** 已用容量百分比 */
    usagePercent(state) {
      if (!state.quota || !state.quota.total_capacity) return 0
      return ((state.quota.used_capacity / state.quota.total_capacity) * 100).toFixed(1)
    }
  },

  actions: {
    /** 初始化网络状态 */
    initNetworkStatus() {
      uni.getNetworkType({
        success: (res) => {
          this.networkConnected = res.networkType !== 'none'
        }
      })
    },

    /** 设置网络状态 */
    setNetworkConnected(connected) {
      this.networkConnected = connected
    },

    /** 导航进入子目录 */
    pushNode(node) {
      this.nodeStack.push({ node_id: node.node_id, name: node.node_name || node.name })
    },

    /** 返回上级目录 */
    popNode() {
      this.nodeStack.pop()
    },

    /** 重置目录栈 */
    resetNodeStack() {
      this.nodeStack = []
    },

    /** 设置根节点 ID */
    setRootNodeId(id) {
      this.rootNodeId = id
    },

    /** 设置配额 */
    setQuota(quota) {
      this.quota = quota
    },

    /** 设置加载状态 */
    setLoading(loading) {
      this.loading = loading
    }
  }
})