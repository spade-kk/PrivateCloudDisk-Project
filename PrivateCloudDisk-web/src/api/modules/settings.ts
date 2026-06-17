// ============================================================
// settings.ts — 系统设置 API 模块
// ============================================================
// 封装用户偏好、通知设置、外观主题、语言地区、密码管理、
// 数据导出、账号注销等系统配置功能接口。
// 所有设置变更立即生效，部分设置（如外观主题）同步到 localStorage。
// ============================================================

import request from '@/utils/request'

// ============================================================
// 用户偏好
// ============================================================

/** 获取用户偏好设置（默认排序、视图模式、每页条数等） */
export function getUserPreferencesApi(): Promise<any> {
  return request({ url: '/api/settings/preferences', method: 'GET' })
}

/** 更新用户偏好设置 */
export function updateUserPreferencesApi(preferences: Record<string, any>): Promise<any> {
  return request({ url: '/api/settings/preferences', method: 'PUT', data: preferences })
}

// ============================================================
// 通知设置
// ============================================================

/** 获取通知设置（邮件通知、推送通知、通知频率等） */
export function getNotificationSettingsApi(): Promise<any> {
  return request({ url: '/api/settings/notifications', method: 'GET' })
}

/** 更新通知设置 */
export function updateNotificationSettingsApi(settings: Record<string, any>): Promise<any> {
  return request({ url: '/api/settings/notifications', method: 'PUT', data: settings })
}

// ============================================================
// 外观设置
// ============================================================

/** 获取外观设置（主题色、暗黑模式、字体大小、布局密度等） */
export function getAppearanceSettingsApi(): Promise<any> {
  return request({ url: '/api/settings/appearance', method: 'GET' })
}

/** 更新外观设置 */
export function updateAppearanceSettingsApi(settings: Record<string, any>): Promise<any> {
  return request({ url: '/api/settings/appearance', method: 'PUT', data: settings })
}

// ============================================================
// 语言与地区
// ============================================================

/** 获取系统支持的语言列表 */
export function getSupportedLanguagesApi(): Promise<any> {
  return request({ url: '/api/settings/languages', method: 'GET' })
}

/** 更新语言设置（切换 UI 语言） */
export function updateLanguageApi(language: string): Promise<any> {
  return request({ url: '/api/settings/language', method: 'PUT', data: { language } })
}

// ============================================================
// 密码管理
// ============================================================

/** 修改密码（需提供旧密码验证，新密码经客户端预哈希） */
export function changePasswordApi(data: Record<string, any>): Promise<any> {
  return request({ url: '/api/settings/password', method: 'PUT', data })
}

// ============================================================
// 数据管理
// ============================================================

/** 导出个人数据（返回 Blob，包含用户所有文件和元数据） */
export function exportPersonalDataApi(): Promise<any> {
  return request({ url: '/api/settings/export-data', method: 'GET', responseType: 'blob' })
}

/** 请求注销账号（需提供注销原因，进入审核流程） */
export function requestAccountDeletionApi(reason: string): Promise<any> {
  return request({ url: '/api/settings/account-deletion', method: 'POST', data: { reason } })
}