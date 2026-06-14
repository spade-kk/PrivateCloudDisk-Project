/**
 * 系统设置 Store
 * 管理用户偏好、通知、外观、语言等设置状态
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUserPreferencesApi, updateUserPreferencesApi,
  getNotificationSettingsApi, updateNotificationSettingsApi,
  getAppearanceSettingsApi, updateAppearanceSettingsApi,
  updateLanguageApi, changePasswordApi,
  exportPersonalDataApi, requestAccountDeletionApi,
} from '@/api/index'

export const useSettingsStore = defineStore('settings', () => {
  // ─── 偏好设置 ────────────────────────────────────────
  const preferences = ref({
    defaultView: localStorage.getItem('defaultView') || 'grid',
    itemsPerPage: parseInt(localStorage.getItem('itemsPerPage')) || 50,
    autoPlay: localStorage.getItem('autoPlay') !== 'false',
    language: localStorage.getItem('language') || 'zh-CN',
    timezone: localStorage.getItem('timezone') || 'Asia/Shanghai',
  })

  // ─── 通知设置 ────────────────────────────────────────
  const notificationSettings = ref({
    emailNotifications: true,
    pushNotifications: true,
    fileShared: true,
    fileDownloaded: false,
    storageWarning: true,
    securityAlerts: true,
    marketingEmails: false,
    weeklyDigest: true,
  })

  // ─── 外观设置 ────────────────────────────────────────
  const appearance = ref({
    theme: localStorage.getItem('theme') || 'light',
    fontSize: localStorage.getItem('fontSize') || 'medium',
    density: localStorage.getItem('density') || 'comfortable',
    sidebarCollapsed: localStorage.getItem('sidebarCollapsed') === 'true',
    animationEnabled: localStorage.getItem('animationEnabled') !== 'false',
  })

  // ─── 加载状态 ────────────────────────────────────────
  const loading = ref(false)
  const saving = ref(false)

  // ─── 偏好操作 ────────────────────────────────────────
  async function fetchPreferences() {
    loading.value = true
    try {
      const res = await getUserPreferencesApi()
      if (res.code === 200 && res.data) {
        preferences.value = { ...preferences.value, ...res.data }
      }
    } catch (e) {
      console.error('获取偏好设置失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function savePreferences(data) {
    saving.value = true
    try {
      const res = await updateUserPreferencesApi(data)
      if (res.code === 200) {
        preferences.value = { ...preferences.value, ...data }
        Object.entries(data).forEach(([key, val]) => {
          if (val !== undefined) localStorage.setItem(key, String(val))
        })
      }
      return res
    } catch (e) {
      console.error('保存偏好设置失败:', e)
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  // ─── 通知操作 ────────────────────────────────────────
  async function fetchNotificationSettings() {
    try {
      const res = await getNotificationSettingsApi()
      if (res.code === 200 && res.data) {
        notificationSettings.value = { ...notificationSettings.value, ...res.data }
      }
    } catch (e) {
      console.error('获取通知设置失败:', e)
    }
  }

  async function saveNotificationSettings(data) {
    saving.value = true
    try {
      const res = await updateNotificationSettingsApi(data)
      if (res.code === 200) {
        notificationSettings.value = { ...notificationSettings.value, ...data }
      }
      return res
    } catch (e) {
      console.error('保存通知设置失败:', e)
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  // ─── 外观操作 ────────────────────────────────────────
  async function fetchAppearance() {
    try {
      const res = await getAppearanceSettingsApi()
      if (res.code === 200 && res.data) {
        appearance.value = { ...appearance.value, ...res.data }
      }
    } catch (e) {
      console.error('获取外观设置失败:', e)
    }
  }

  async function saveAppearance(data) {
    saving.value = true
    try {
      const res = await updateAppearanceSettingsApi(data)
      if (res.code === 200) {
        appearance.value = { ...appearance.value, ...data }
        Object.entries(data).forEach(([key, val]) => {
          if (val !== undefined) localStorage.setItem(key, String(val))
        })
      }
      return res
    } catch (e) {
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  // ─── 密码 ────────────────────────────────────────────
  async function changePassword(data) {
    saving.value = true
    try {
      const res = await changePasswordApi(data)
      return res
    } catch (e) {
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  // ─── 数据管理 ────────────────────────────────────────
  async function exportData() {
    try {
      const res = await exportPersonalDataApi()
      if (res.data) {
        const url = window.URL.createObjectURL(new Blob([res.data]))
        const link = document.createElement('a')
        link.href = url
        link.download = `personal-data-${Date.now()}.zip`
        link.click()
        window.URL.revokeObjectURL(url)
      }
    } catch (e) {
      console.error('导出数据失败:', e)
    }
  }

  async function requestDeletion(reason) {
    return requestAccountDeletionApi(reason)
  }

  return {
    preferences, notificationSettings, appearance,
    loading, saving,
    fetchPreferences, savePreferences,
    fetchNotificationSettings, saveNotificationSettings,
    fetchAppearance, saveAppearance,
    changePassword, exportData, requestDeletion,
  }
})