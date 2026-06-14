/**
 * 管理后台 Store
 * 管理用户列表、系统监控、审计日志等管理功能状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getUsersApi, getUserDetailApi, toggleUserStatusApi,
  updateUserRoleApi, deleteUserApi, batchUserActionApi,
  getSystemOverviewApi, getSystemResourcesApi, getOnlineUsersApi,
  getAuditLogsApi, getStorageStatsApi,
} from '@/api/index'

export const useAdminStore = defineStore('admin', () => {
  // ─── 状态 ────────────────────────────────────────────
  const users = ref([])
  const usersTotal = ref(0)
  const usersPage = ref(1)
  const usersPageSize = ref(20)
  const usersLoading = ref(false)
  const usersError = ref(null)

  const systemOverview = ref(null)
  const systemResources = ref(null)
  const onlineUsers = ref([])
  const overviewLoading = ref(false)

  const auditLogs = ref([])
  const auditLogsTotal = ref(0)
  const auditLogsPage = ref(1)
  const auditLogsLoading = ref(false)

  const storageStats = ref(null)

  // ─── 计算属性 ────────────────────────────────────────
  const totalPages = computed(() => Math.ceil(usersTotal.value / usersPageSize.value))

  // ─── 用户管理 ────────────────────────────────────────
  async function fetchUsers(params = {}) {
    usersLoading.value = true
    usersError.value = null
    try {
      const res = await getUsersApi({
        page: usersPage.value,
        pageSize: usersPageSize.value,
        ...params,
      })
      if (res.code === 200) {
        users.value = res.data?.records || res.data?.list || []
        usersTotal.value = res.data?.total || 0
      }
    } catch (e) {
      usersError.value = e.message || '获取用户列表失败'
    } finally {
      usersLoading.value = false
    }
  }

  async function toggleUser(userId, status) {
    const res = await toggleUserStatusApi(userId, status)
    if (res.code === 200) {
      const idx = users.value.findIndex(u => u.id === userId || u.userId === userId)
      if (idx !== -1) users.value[idx].status = status
    }
    return res
  }

  async function updateUserRole(userId, role) {
    const res = await updateUserRoleApi(userId, role)
    if (res.code === 200) {
      const idx = users.value.findIndex(u => u.id === userId || u.userId === userId)
      if (idx !== -1) users.value[idx].role = role
    }
    return res
  }

  async function removeUser(userId) {
    const res = await deleteUserApi(userId)
    if (res.code === 200) {
      users.value = users.value.filter(u => u.id !== userId && u.userId !== userId)
      usersTotal.value--
    }
    return res
  }

  async function batchAction(action, userIds) {
    const res = await batchUserActionApi(action, userIds)
    if (res.code === 200) {
      await fetchUsers()
    }
    return res
  }

  function setUsersPage(page) {
    usersPage.value = page
    fetchUsers()
  }

  // ─── 系统监控 ────────────────────────────────────────
  async function fetchSystemOverview() {
    overviewLoading.value = true
    try {
      const res = await getSystemOverviewApi()
      if (res.code === 200) systemOverview.value = res.data
    } catch (e) {
      console.error('获取系统概览失败:', e)
    } finally {
      overviewLoading.value = false
    }
  }

  async function fetchSystemResources() {
    try {
      const res = await getSystemResourcesApi()
      if (res.code === 200) systemResources.value = res.data
    } catch (e) {
      console.error('获取系统资源失败:', e)
    }
  }

  async function fetchOnlineUsers() {
    try {
      const res = await getOnlineUsersApi()
      if (res.code === 200) onlineUsers.value = res.data?.list || res.data || []
    } catch (e) {
      console.error('获取在线用户失败:', e)
    }
  }

  async function fetchStorageStats() {
    try {
      const res = await getStorageStatsApi()
      if (res.code === 200) storageStats.value = res.data
    } catch (e) {
      console.error('获取存储统计失败:', e)
    }
  }

  // ─── 审计日志 ────────────────────────────────────────
  async function fetchAuditLogs(params = {}) {
    auditLogsLoading.value = true
    try {
      const res = await getAuditLogsApi({ page: auditLogsPage.value, pageSize: 20, ...params })
      if (res.code === 200) {
        auditLogs.value = res.data?.records || res.data?.list || []
        auditLogsTotal.value = res.data?.total || 0
      }
    } catch (e) {
      console.error('获取审计日志失败:', e)
    } finally {
      auditLogsLoading.value = false
    }
  }

  return {
    users, usersTotal, usersPage, usersPageSize, usersLoading, usersError, totalPages,
    systemOverview, systemResources, onlineUsers, overviewLoading,
    auditLogs, auditLogsTotal, auditLogsPage, auditLogsLoading, storageStats,
    fetchUsers, toggleUser, updateUserRole, removeUser, batchAction, setUsersPage,
    fetchSystemOverview, fetchSystemResources, fetchOnlineUsers, fetchStorageStats,
    fetchAuditLogs,
  }
})