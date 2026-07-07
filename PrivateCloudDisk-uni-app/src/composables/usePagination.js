/**
 * composables/usePagination.js - 分页加载组合式函数
 *
 * 封装分页加载逻辑：加载更多、刷新、状态管理
 * 适用于 uni-app 小程序页面
 */
import { reactive, computed } from 'vue'

export function usePagination(fetchFn, pageSize = 20) {
  const state = reactive({
    list: [],
    page: 1,
    hasMore: false,
    loading: false,
    loadingMore: false,
    totalCount: 0
  })

  const isEmpty = computed(() => !state.loading && state.list.length === 0)

  async function loadFirstPage(extraParams = {}) {
    state.loading = true
    try {
      const res = await fetchFn({ page: 1, pageSize, ...extraParams })
      state.list = res.data?.items || res.data || []
      state.totalCount = res.data?.total || 0
      state.hasMore = (res.data?.items?.length || res.data?.length || 0) >= pageSize
      state.page = 1
    } catch (e) {
      console.error('[usePagination] loadFirstPage error:', e)
      throw e
    } finally {
      state.loading = false
    }
  }

  async function loadMore(extraParams = {}) {
    if (state.loadingMore || !state.hasMore) return
    state.loadingMore = true
    try {
      const nextPage = state.page + 1
      const res = await fetchFn({ page: nextPage, pageSize, ...extraParams })
      const items = res.data?.items || res.data || []
      state.list.push(...items)
      state.hasMore = items.length >= pageSize
      state.page = nextPage
    } catch (e) {
      console.error('[usePagination] loadMore error:', e)
    } finally {
      state.loadingMore = false
    }
  }

  function reset() {
    state.list = []
    state.page = 1
    state.hasMore = false
    state.totalCount = 0
  }

  return { state, isEmpty, loadFirstPage, loadMore, reset }
}