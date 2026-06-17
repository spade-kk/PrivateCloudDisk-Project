import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getUserPreferencesApi, updateUserPreferencesApi,
  getNotificationSettingsApi, updateNotificationSettingsApi,
  getAppearanceSettingsApi, updateAppearanceSettingsApi,
  updateLanguageApi, changePasswordApi,
  exportPersonalDataApi, requestAccountDeletionApi,
} from '@/api/index'

export interface Preferences {
  defaultView: string
  itemsPerPage: number
  autoPlay: boolean
  language: string
  timezone: string
}

export interface NotificationSettings {
  emailNotifications: boolean
  pushNotifications: boolean
  fileShared: boolean
  fileDownloaded: boolean
  storageWarning: boolean
  securityAlerts: boolean
  marketingEmails: boolean
  weeklyDigest: boolean
}

export interface Appearance {
  theme: string
  fontSize: string
  density: string
  sidebarCollapsed: boolean
  animationEnabled: boolean
}

export const useSettingsStore = defineStore('settings', () => {
  const preferences = ref<Preferences>({
    defaultView: localStorage.getItem('defaultView') || 'grid',
    itemsPerPage: parseInt(localStorage.getItem('itemsPerPage') || '50'),
    autoPlay: localStorage.getItem('autoPlay') !== 'false',
    language: localStorage.getItem('language') || 'zh-CN',
    timezone: localStorage.getItem('timezone') || 'Asia/Shanghai',
  })

  const notificationSettings = ref<NotificationSettings>({
    emailNotifications: true,
    pushNotifications: true,
    fileShared: true,
    fileDownloaded: false,
    storageWarning: true,
    securityAlerts: true,
    marketingEmails: false,
    weeklyDigest: true,
  })

  const appearance = ref<Appearance>({
    theme: localStorage.getItem('theme') || 'light',
    fontSize: localStorage.getItem('fontSize') || 'medium',
    density: localStorage.getItem('density') || 'comfortable',
    sidebarCollapsed: localStorage.getItem('sidebarCollapsed') === 'true',
    animationEnabled: localStorage.getItem('animationEnabled') !== 'false',
  })

  const loading = ref(false)
  const saving = ref(false)

  async function fetchPreferences(): Promise<void> {
    loading.value = true
    try {
      const res = await getUserPreferencesApi()
      if (res.code === 200 && res.data) {
        preferences.value = { ...preferences.value, ...res.data }
      }
    } catch (e: any) {
      console.error('获取偏好设置失败:', e)
    } finally {
      loading.value = false
    }
  }

  async function savePreferences(data: Partial<Preferences>): Promise<any> {
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
    } catch (e: any) {
      console.error('保存偏好设置失败:', e)
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  async function fetchNotificationSettings(): Promise<void> {
    try {
      const res = await getNotificationSettingsApi()
      if (res.code === 200 && res.data) {
        notificationSettings.value = { ...notificationSettings.value, ...res.data }
      }
    } catch (e: any) {
      console.error('获取通知设置失败:', e)
    }
  }

  async function saveNotificationSettings(data: Partial<NotificationSettings>): Promise<any> {
    saving.value = true
    try {
      const res = await updateNotificationSettingsApi(data)
      if (res.code === 200) {
        notificationSettings.value = { ...notificationSettings.value, ...data }
      }
      return res
    } catch (e: any) {
      console.error('保存通知设置失败:', e)
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  async function fetchAppearance(): Promise<void> {
    try {
      const res = await getAppearanceSettingsApi()
      if (res.code === 200 && res.data) {
        appearance.value = { ...appearance.value, ...res.data }
      }
    } catch (e: any) {
      console.error('获取外观设置失败:', e)
    }
  }

  async function saveAppearance(data: Partial<Appearance>): Promise<any> {
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
    } catch (e: any) {
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  async function changePassword(data: Record<string, any>): Promise<any> {
    saving.value = true
    try {
      const res = await changePasswordApi(data)
      return res
    } catch (e: any) {
      return { success: false, message: e.message }
    } finally {
      saving.value = false
    }
  }

  async function exportData(): Promise<void> {
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
    } catch (e: any) {
      console.error('导出数据失败:', e)
    }
  }

  async function requestDeletion(reason: string): Promise<any> {
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