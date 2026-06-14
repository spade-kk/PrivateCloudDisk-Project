/**
 * 系统设置 API 模块
 * 提供用户偏好、通知设置、外观主题等配置功能接口
 */
import request from '@/utils/request'

// ─── 用户偏好 ─────────────────────────────────────────

/** 获取用户偏好设置 */
export function getUserPreferencesApi() {
  return request({ url: '/api/settings/preferences', method: 'GET' })
}

/** 更新用户偏好设置 */
export function updateUserPreferencesApi(preferences) {
  return request({ url: '/api/settings/preferences', method: 'PUT', data: preferences })
}

// ─── 通知设置 ─────────────────────────────────────────

/** 获取通知设置 */
export function getNotificationSettingsApi() {
  return request({ url: '/api/settings/notifications', method: 'GET' })
}

/** 更新通知设置 */
export function updateNotificationSettingsApi(settings) {
  return request({ url: '/api/settings/notifications', method: 'PUT', data: settings })
}

// ─── 外观设置 ─────────────────────────────────────────

/** 获取外观设置 */
export function getAppearanceSettingsApi() {
  return request({ url: '/api/settings/appearance', method: 'GET' })
}

/** 更新外观设置 */
export function updateAppearanceSettingsApi(settings) {
  return request({ url: '/api/settings/appearance', method: 'PUT', data: settings })
}

// ─── 语言与地区 ───────────────────────────────────────

/** 获取支持的语言列表 */
export function getSupportedLanguagesApi() {
  return request({ url: '/api/settings/languages', method: 'GET' })
}

/** 更新语言设置 */
export function updateLanguageApi(language) {
  return request({ url: '/api/settings/language', method: 'PUT', data: { language } })
}

// ─── 密码管理 ─────────────────────────────────────────

/** 修改密码 */
export function changePasswordApi(data) {
  return request({ url: '/api/settings/password', method: 'PUT', data })
}

// ─── 数据管理 ─────────────────────────────────────────

/** 导出个人数据 */
export function exportPersonalDataApi() {
  return request({ url: '/api/settings/export-data', method: 'GET', responseType: 'blob' })
}

/** 请求删除账号 */
export function requestAccountDeletionApi(reason) {
  return request({ url: '/api/settings/account-deletion', method: 'POST', data: { reason } })
}