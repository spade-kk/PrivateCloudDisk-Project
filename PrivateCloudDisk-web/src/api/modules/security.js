/**
 * 安全中心 API 模块
 * 提供双因素认证、登录历史、会话管理、设备管理等安全功能接口
 */
import request from '@/utils/request'

// ─── 双因素认证 (2FA) ────────────────────────────────

/** 获取 2FA 设置状态 */
export function get2FAStatusApi() {
  return request({ url: '/api/security/2fa/status', method: 'GET' })
}

/** 启用 2FA */
export function enable2FAApi(data) {
  return request({ url: '/api/security/2fa/enable', method: 'POST', data })
}

/** 禁用 2FA */
export function disable2FAApi(code) {
  return request({ url: '/api/security/2fa/disable', method: 'POST', data: { code } })
}

/** 获取 2FA 恢复码 */
export function get2FARecoveryCodesApi() {
  return request({ url: '/api/security/2fa/recovery-codes', method: 'GET' })
}

// ─── 登录历史 ─────────────────────────────────────────

/** 获取登录历史 */
export function getLoginHistoryApi(params) {
  return request({ url: '/api/security/login-history', method: 'GET', params })
}

/** 获取当前活跃会话 */
export function getActiveSessionsApi() {
  return request({ url: '/api/security/sessions', method: 'GET' })
}

/** 撤销指定会话 */
export function revokeSessionApi(sessionId) {
  return request({ url: `/api/security/sessions/${sessionId}`, method: 'DELETE' })
}

/** 撤销所有其他会话 */
export function revokeAllSessionsApi() {
  return request({ url: '/api/security/sessions', method: 'DELETE' })
}

// ─── 设备管理 ─────────────────────────────────────────

/** 获取信任设备列表 */
export function getTrustedDevicesApi() {
  return request({ url: '/api/security/devices', method: 'GET' })
}

/** 移除信任设备 */
export function removeTrustedDeviceApi(deviceId) {
  return request({ url: `/api/security/devices/${deviceId}`, method: 'DELETE' })
}

// ─── API 密钥管理 ─────────────────────────────────────

/** 获取 API 密钥列表 */
export function getApiKeysApi() {
  return request({ url: '/api/security/api-keys', method: 'GET' })
}

/** 创建 API 密钥 */
export function createApiKeyApi(data) {
  return request({ url: '/api/security/api-keys', method: 'POST', data })
}

/** 撤销 API 密钥 */
export function revokeApiKeyApi(keyId) {
  return request({ url: `/api/security/api-keys/${keyId}`, method: 'DELETE' })
}

// ─── 安全事件 ─────────────────────────────────────────

/** 获取安全事件列表 */
export function getSecurityEventsApi(params) {
  return request({ url: '/api/security/events', method: 'GET', params })
}

/** 获取安全评分 */
export function getSecurityScoreApi() {
  return request({ url: '/api/security/score', method: 'GET' })
}