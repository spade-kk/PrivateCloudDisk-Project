// ============================================================
// http.js — HTTP REST API 客户端（离线消息拉取 / 历史消息查询）
// ============================================================
// 仿照 box-im 客户端设计：离线消息与历史消息通过 HTTP 主动拉取，
// 与 WebSocket 实时推送形成互补渠道。
//
// 对接后端接口（im-platform）：
//   GET /api/v1/im/messages/offline         拉取当前用户离线消息（PREPARING，拉取后置 DELIVERED）
//   GET /api/v1/im/messages/history/cursor  游标分页历史消息（仅终态 DELIVERED/READ/FAILED）
// ============================================================

/**
 * HTTP API 客户端
 */
export class HttpApi {
  /**
   * @param {object} config - 完整配置（含 http.baseUrl、auth.token、user.userId）
   * @param {import('./logger.js').Logger} logger - 日志实例
   */
  constructor(config, logger) {
    // im-platform HTTP 基础地址；默认本地 8088 端口，经网关前缀 /api/v1/im
    this.baseUrl = config.http?.baseUrl || 'http://localhost:8088/api/v1/im'
    this.token = config.auth?.token || ''
    this.log = logger
  }

  /**
   * 通用 GET 请求（拼接查询参数，返回 Result.data）
   * @param {string} path - 接口路径（相对 baseUrl，如 /messages/offline）
   * @param {object} params - 查询参数（自动忽略 undefined/null/空串）
   * @returns {Promise<any>} Result.data
   */
  async request(path, params = {}) {
    const url = new URL(this.baseUrl + path)
    for (const [k, v] of Object.entries(params)) {
      if (v !== undefined && v !== null && v !== '') {
        url.searchParams.set(k, v)
      }
    }
    const headers = { 'Content-Type': 'application/json' }
    if (this.token) {
      headers['Authorization'] = `Bearer ${this.token}`
    }
    const res = await fetch(url.toString(), { method: 'GET', headers })
    const json = await res.json().catch(() => ({}))
    if (!res.ok || (json.code !== undefined && json.code !== 200)) {
      throw new Error(`HTTP ${res.status}: ${json.message || '请求失败'}`)
    }
    return json.data
  }

  /**
   * 拉取当前用户离线消息（状态 PREPARING，拉取后服务端置为 DELIVERED）
   * @param {string} userId - 当前用户 ID
   * @param {number} limit - 最大拉取条数（默认 100）
   * @returns {Promise<Array>} 离线消息列表
   */
  async getOfflineMessages(userId, limit = 100) {
    return this.request('/messages/offline', { userId, limit })
  }

  /**
   * 游标分页拉取会话历史消息（仅终态 DELIVERED/READ/FAILED）
   * @param {string} conversationId - 会话 ID
   * @param {string} userId - 当前用户 ID
   * @param {number} limit - 每页条数（默认 20，最大 100）
   * @param {number|undefined} cursor - 上一页最小 server_seq（首次 undefined）
   * @returns {Promise<Array>} 历史消息列表（按时间倒序）
   */
  async getHistory(conversationId, userId, limit = 20, cursor) {
    return this.request('/messages/history/cursor', {
      conversationId,
      userId,
      limit,
      cursor,
    })
  }
}
