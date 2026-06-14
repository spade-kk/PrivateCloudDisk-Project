/**
 * 活动日志 Store
 * 管理用户操作日志、文件操作记录等追溯功能状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getActivityLogsApi, getActivityLogDetailApi,
  getActivitySummaryApi, getFileOperationsApi,
  getLoginActivityApi, exportActivityLogsApi, cleanOldLogsApi,
} from '@/api/index'

export const useActivityStore = defineStore('activity', () => {
  // ─── 状态 ────────────────────────────────────────────
  const logs = ref([])
  const logsTotal = ref(0)
  const logsPage = ref(1)
  const logsPageSize = ref(20)
  const logsLoading = ref(false)

  const summary = ref(null)
  const summaryLoading = ref(false)

  // ─── 筛选条件 ────────────────────────────────────────
  const filters = ref({
    actionType: '',
    dateRange: [],
    keyword: '',
    userId: '',
  })

  // ─── 计算属性 ────────────────────────────────────────
  const totalPages = computed(() => Math.ceil(logsTotal.value / logsPageSize.value))

  // ─── 操作 ────────────────────────────────────────────
  async function fetchLogs(params = {}) {
    logsLoading.value = true
    try {
      const res = await getActivityLogsApi({
        page: logsPage.value,
        pageSize: logsPageSize.value,
        ...filters.value,
        ...params,
      })
      if (res.code === 200) {
        logs.value = res.data?.records || res.data?.list || []
        logsTotal.value = res.data?.total || 0
      }
    } catch (e) {
      console.error('获取活动日志失败:', e)
    } finally {
      logsLoading.value = false
    }
  }

  async function fetchSummary() {
    summaryLoading.value = true
    try {
      const res = await getActivitySummaryApi()
      if (res.code === 200) summary.value = res.data
    } catch (e) {
      console.error('获取活动摘要失败:', e)
    } finally {
      summaryLoading.value = false
    }
  }

  async function exportLogs(params = {}) {
    try {
      const res = await exportActivityLogsApi({ ...filters.value, ...params })
      if (res.data) {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.download = `activity-logs-${Date.now()}.csv`
        link.click()
        window.URL.revokeObjectURL(url)
      }
    } catch (e) {
      console.error('导出活动日志失败:', e)
    }
  }

  async function cleanLogs(days) {
    const res = await cleanOldLogsApi(days)
    if (res.code === 200) {
      await fetchLogs()
    }
    return res
  }

  function setPage(page) {
    logsPage.value = page
    fetchLogs()
  }

  function setFilters(newFilters) {
    filters.value = { ...filters.value, ...newFilters }
    logsPage.value = 1
    fetchLogs()
  }

  function resetFilters() {
    filters.value = { actionType: '', dateRange: [], keyword: '', userId: '' }
    logsPage.value = 1
    fetchLogs()
  }

  return {
    logs, logsTotal, logsPage, logsPageSize, logsLoading, totalPages,
    summary, summaryLoading, filters,
    fetchLogs, fetchSummary, exportLogs, cleanLogs,
    setPage, setFilters, resetFilters,
  }
})