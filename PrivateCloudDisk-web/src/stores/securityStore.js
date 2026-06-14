/**
 * 安全中心 Store
 * 管理双因素认证、登录历史、会话、设备、API 密钥等安全状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  get2FAStatusApi, enable2FAApi, disable2FAApi, get2FARecoveryCodesApi,
  getLoginHistoryApi, getActiveSessionsApi, revokeSessionApi, revokeAllSessionsApi,
  getTrustedDevicesApi, removeTrustedDeviceApi,
  getApiKeysApi, createApiKeyApi, revokeApiKeyApi,
  getSecurityEventsApi, getSecurityScoreApi,
} from '@/api/index'

export const useSecurityStore = defineStore('security', () => {
  // ─── 2FA 状态 ────────────────────────────────────────
  const twoFactorEnabled = ref(false)
  const recoveryCodes = ref([])
  const twoFactorLoading = ref(false)

  // ─── 登录历史 ────────────────────────────────────────
  const loginHistory = ref([])
  const loginHistoryTotal = ref(0)
  const loginHistoryPage = ref(1)
  const loginHistoryLoading = ref(false)

  // ─── 活跃会话 ────────────────────────────────────────
  const activeSessions = ref([])
  const sessionsLoading = ref(false)

  // ─── 信任设备 ────────────────────────────────────────
  const trustedDevices = ref([])
  const devicesLoading = ref(false)

  // ─── API 密钥 ────────────────────────────────────────
  const apiKeys = ref([])
  const apiKeysLoading = ref(false)

  // ─── 安全事件 ────────────────────────────────────────
  const securityEvents = ref([])
  const securityScore = ref(null)
  const eventsLoading = ref(false)

  // ─── 计算属性 ────────────────────────────────────────
  const scoreLevel = computed(() => {
    if (!securityScore.value) return 'unknown'
    const s = securityScore.value.score || securityScore.value
    if (s >= 80) return 'high'
    if (s >= 60) return 'medium'
    return 'low'
  })

  // ─── 2FA 操作 ────────────────────────────────────────
  async function fetch2FAStatus() {
    twoFactorLoading.value = true
    try {
      const res = await get2FAStatusApi()
      if (res.code === 200) twoFactorEnabled.value = res.data?.enabled || false
    } catch (e) {
      console.error('获取2FA状态失败:', e)
    } finally {
      twoFactorLoading.value = false
    }
  }

  async function enable2FA(data) {
    const res = await enable2FAApi(data)
    if (res.code === 200) {
      twoFactorEnabled.value = true
      recoveryCodes.value = res.data?.recoveryCodes || []
    }
    return res
  }

  async function disable2FA(code) {
    const res = await disable2FAApi(code)
    if (res.code === 200) twoFactorEnabled.value = false
    return res
  }

  async function fetchRecoveryCodes() {
    const res = await get2FARecoveryCodesApi()
    if (res.code === 200) recoveryCodes.value = res.data?.codes || []
    return res
  }

  // ─── 登录历史 ────────────────────────────────────────
  async function fetchLoginHistory(params = {}) {
    loginHistoryLoading.value = true
    try {
      const res = await getLoginHistoryApi({ page: loginHistoryPage.value, pageSize: 20, ...params })
      if (res.code === 200) {
        loginHistory.value = res.data?.records || res.data?.list || []
        loginHistoryTotal.value = res.data?.total || 0
      }
    } catch (e) {
      console.error('获取登录历史失败:', e)
    } finally {
      loginHistoryLoading.value = false
    }
  }

  // ─── 会话管理 ────────────────────────────────────────
  async function fetchActiveSessions() {
    sessionsLoading.value = true
    try {
      const res = await getActiveSessionsApi()
      if (res.code === 200) activeSessions.value = res.data?.list || res.data || []
    } catch (e) {
      console.error('获取活跃会话失败:', e)
    } finally {
      sessionsLoading.value = false
    }
  }

  async function revokeSession(sessionId) {
    const res = await revokeSessionApi(sessionId)
    if (res.code === 200) {
      activeSessions.value = activeSessions.value.filter(s => s.id !== sessionId)
    }
    return res
  }

  async function revokeAllSessions() {
    const res = await revokeAllSessionsApi()
    if (res.code === 200) activeSessions.value = []
    return res
  }

  // ─── 设备管理 ────────────────────────────────────────
  async function fetchTrustedDevices() {
    devicesLoading.value = true
    try {
      const res = await getTrustedDevicesApi()
      if (res.code === 200) trustedDevices.value = res.data?.list || res.data || []
    } catch (e) {
      console.error('获取信任设备失败:', e)
    } finally {
      devicesLoading.value = false
    }
  }

  async function removeDevice(deviceId) {
    const res = await removeTrustedDeviceApi(deviceId)
    if (res.code === 200) {
      trustedDevices.value = trustedDevices.value.filter(d => d.id !== deviceId)
    }
    return res
  }

  // ─── API 密钥 ────────────────────────────────────────
  async function fetchApiKeys() {
    apiKeysLoading.value = true
    try {
      const res = await getApiKeysApi()
      if (res.code === 200) apiKeys.value = res.data?.list || res.data || []
    } catch (e) {
      console.error('获取API密钥失败:', e)
    } finally {
      apiKeysLoading.value = false
    }
  }

  async function createApiKey(data) {
    const res = await createApiKeyApi(data)
    if (res.code === 200) {
      apiKeys.value.push(res.data)
    }
    return res
  }

  async function revokeApiKey(keyId) {
    const res = await revokeApiKeyApi(keyId)
    if (res.code === 200) {
      apiKeys.value = apiKeys.value.filter(k => k.id !== keyId)
    }
    return res
  }

  // ─── 安全事件 ────────────────────────────────────────
  async function fetchSecurityEvents(params = {}) {
    eventsLoading.value = true
    try {
      const res = await getSecurityEventsApi(params)
      if (res.code === 200) securityEvents.value = res.data?.list || res.data || []
    } catch (e) {
      console.error('获取安全事件失败:', e)
    } finally {
      eventsLoading.value = false
    }
  }

  async function fetchSecurityScore() {
    try {
      const res = await getSecurityScoreApi()
      if (res.code === 200) securityScore.value = res.data
    } catch (e) {
      console.error('获取安全评分失败:', e)
    }
  }

  return {
    twoFactorEnabled, recoveryCodes, twoFactorLoading,
    loginHistory, loginHistoryTotal, loginHistoryPage, loginHistoryLoading,
    activeSessions, sessionsLoading,
    trustedDevices, devicesLoading,
    apiKeys, apiKeysLoading,
    securityEvents, securityScore, eventsLoading, scoreLevel,
    fetch2FAStatus, enable2FA, disable2FA, fetchRecoveryCodes,
    fetchLoginHistory, fetchActiveSessions, revokeSession, revokeAllSessions,
    fetchTrustedDevices, removeDevice,
    fetchApiKeys, createApiKey, revokeApiKey,
    fetchSecurityEvents, fetchSecurityScore,
  }
})