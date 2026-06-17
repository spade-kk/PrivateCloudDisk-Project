// ============================================================
// security.ts — 安全中心 API 模块
// ============================================================
// 封装双因素认证 (2FA)、登录历史、会话管理、设备管理、
// API 密钥管理、安全事件、安全评分等安全功能接口。
// 所有接口均由安全中心页面 (SecurityView.vue) 调用，
// 配合 request.ts 的安全签名头实现端到端安全保护。
// ============================================================

import request from '@/utils/request'

// ============================================================
// 双因素认证 (2FA)
// ============================================================

/** 获取 2FA 设置状态（是否已启用、绑定方式等） */
export function get2FAStatusApi(): Promise<any> {
  return request({ url: '/api/security/2fa/status', method: 'GET' })
}

/** 启用 2FA（返回二维码/密钥用于绑定认证器 App） */
export function enable2FAApi(data: Record<string, any>): Promise<any> {
  return request({ url: '/api/security/2fa/enable', method: 'POST', data })
}

/** 禁用 2FA（需要验证码确认） */
export function disable2FAApi(code: string): Promise<any> {
  return request({ url: '/api/security/2fa/disable', method: 'POST', data: { code } })
}

/** 获取 2FA 恢复码（用于设备丢失时恢复访问） */
export function get2FARecoveryCodesApi(): Promise<any> {
  return request({ url: '/api/security/2fa/recovery-codes', method: 'GET' })
}

// ============================================================
// 登录历史与会话管理
// ============================================================

/** 获取登录历史记录（分页查询，包含 IP、设备、时间等） */
export function getLoginHistoryApi(params: Record<string, any>): Promise<any> {
  return request({ url: '/api/security/login-history', method: 'GET', params })
}

/** 获取当前活跃会话列表 */
export function getActiveSessionsApi(): Promise<any> {
  return request({ url: '/api/security/sessions', method: 'GET' })
}

/** 撤销指定会话（强制设备下线） */
export function revokeSessionApi(sessionId: string): Promise<any> {
  return request({ url: `/api/security/sessions/${sessionId}`, method: 'DELETE' })
}

/** 撤销所有其他会话（保留当前会话，一键踢出所有其他设备） */
export function revokeAllSessionsApi(): Promise<any> {
  return request({ url: '/api/security/sessions', method: 'DELETE' })
}

// ============================================================
// 设备管理
// ============================================================

/** 获取信任设备列表 */
export function getTrustedDevicesApi(): Promise<any> {
  return request({ url: '/api/security/devices', method: 'GET' })
}

/** 移除信任设备（该设备下次登录需重新验证） */
export function removeTrustedDeviceApi(deviceId: string): Promise<any> {
  return request({ url: `/api/security/devices/${deviceId}`, method: 'DELETE' })
}

// ============================================================
// API 密钥管理
// ============================================================

/** 获取 API 密钥列表（用于第三方应用接入） */
export function getApiKeysApi(): Promise<any> {
  return request({ url: '/api/security/api-keys', method: 'GET' })
}

/** 创建 API 密钥（返回密钥值，仅显示一次） */
export function createApiKeyApi(data: Record<string, any>): Promise<any> {
  return request({ url: '/api/security/api-keys', method: 'POST', data })
}

/** 撤销 API 密钥（立即失效） */
export function revokeApiKeyApi(keyId: string): Promise<any> {
  return request({ url: `/api/security/api-keys/${keyId}`, method: 'DELETE' })
}

// ============================================================
// 安全事件与评分
// ============================================================

/** 获取安全事件列表（异常登录、密码变更、权限变更等） */
export function getSecurityEventsApi(params: Record<string, any>): Promise<any> {
  return request({ url: '/api/security/events', method: 'GET', params })
}

/** 获取安全评分（0-100，用于安全中心首页仪表盘） */
export function getSecurityScoreApi(): Promise<any> {
  return request({ url: '/api/security/score', method: 'GET' })
}