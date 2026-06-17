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
  const twoFactorEnabled = ref(false)
  const recoveryCodes = ref<string[]>([])
  const twoFactorLoading = ref(false)

  const loginHistory = ref<any[]>([])
  const loginHistoryTotal = ref(0)
  const loginHistoryPage = ref(1)
  const loginHistoryLoading = ref(false)

  const activeSessions = ref<any[]>([])
  const sessionsLoading = ref(false)

  const trustedDevices = ref<any[]>([])
  const devicesLoading = ref(false)

  const apiKeys = ref<any[]>([])
  const apiKeysLoading = ref(false)

  const securityEvents = ref<any[]>([])
  const securityScore = ref<any>(null)
  const eventsLoading = ref(false)

  const scoreLevel = computed(() => {
    if (!securityScore.value) return 'unknown'
    const s = securityScore.value.score || securityScore.value
    if (s >= 80) return 'high'
    if (s >= 60) return 'medium'
    return 'low'
  })

  async function fetch2FAStatus(): Promise<void> {
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

  async function enable2FA(data: Record<string, any>): Promise<any> {
    const res = await enable2FAApi(data)
    if (res.code === 200) {
      twoFactorEnabled.value = true
      recoveryCodes.value = res.data?.recoveryCodes || []
    }
    return res
  }

  async function disable2FA(code: string): Promise<any> {
    const res = await disable2FAApi(code)
    if (res.code === 200) twoFactorEnabled.value = false
    return res
  }

  async function fetchRecoveryCodes(): Promise<any> {
    const res = await get2FARecoveryCodesApi()
    if (res.code === 200) recoveryCodes.value = res.data?.codes || []
    return res
  }

  async function fetchLoginHistory(params: Record<string, any> = {}): Promise<void> {
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

  async function fetchActiveSessions(): Promise<void> {
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

  async function revokeSession(sessionId: string): Promise<any> {
    const res = await revokeSessionApi(sessionId)
    if (res.code === 200) {
      activeSessions.value = activeSessions.value.filter(s => s.id !== sessionId)
    }
    return res
  }

  async function revokeAllSessions(): Promise<any> {
    const res = await revokeAllSessionsApi()
    if (res.code === 200) activeSessions.value = []
    return res
  }

  async function fetchTrustedDevices(): Promise<void> {
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

  async function removeDevice(deviceId: string): Promise<any> {
    const res = await removeTrustedDeviceApi(deviceId)
    if (res.code === 200) {
      trustedDevices.value = trustedDevices.value.filter(d => d.id !== deviceId)
    }
    return res
  }

  async function fetchApiKeys(): Promise<void> {
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

  async function createApiKey(data: Record<string, any>): Promise<any> {
    const res = await createApiKeyApi(data)
    if (res.code === 200) {
      apiKeys.value.push(res.data)
    }
    return res
  }

  async function revokeApiKey(keyId: string): Promise<any> {
    const res = await revokeApiKeyApi(keyId)
    if (res.code === 200) {
      apiKeys.value = apiKeys.value.filter(k => k.id !== keyId)
    }
    return res
  }

  async function fetchSecurityEvents(params: Record<string, any> = {}): Promise<void> {
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

  async function fetchSecurityScore(): Promise<void> {
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
    securityEvents, securityScore, eventsLoading,
    scoreLevel,
    fetch2FAStatus, enable2FA, disable2FA, fetchRecoveryCodes,
    fetchLoginHistory, fetchActiveSessions, revokeSession, revokeAllSessions,
    fetchTrustedDevices, removeDevice,
    fetchApiKeys, createApiKey, revokeApiKey,
    fetchSecurityEvents, fetchSecurityScore,
  }
})