/**
 * Cookie 工具函数
 *
 * 用于安全存储 access token 等敏感信息。
 * 虽然 HttpOnly 需要服务端设置（Set-Cookie 响应头），
 * 但客户端侧可通过 Secure + SameSite 增强安全性。
 *
 * 使用方式：
 *   cookie.set('cloud_drive_token', token, { days: 7 })
 *   cookie.get('cloud_drive_token')
 *   cookie.remove('cloud_drive_token')
 */

const SECURE_FLAGS = '; Secure; SameSite=Strict; path=/'

/**
 * 设置 cookie
 * @param {string} name
 * @param {string} value
 * @param {{ days?: number, path?: string }} [options]
 */
function set(name, value, options = {}) {
  const { days = 7, path = '/' } = options
  let cookieStr = `${encodeURIComponent(name)}=${encodeURIComponent(value)}`
  cookieStr += `; path=${path}`
  cookieStr += `; max-age=${days * 86400}`

  // 在生产环境或 HTTPS 下启用 Secure 和 SameSite
  if (window.location.protocol === 'https:') {
    cookieStr += '; Secure; SameSite=Strict'
  } else {
    // 本地开发环境：SameSite=Lax 兼容 localhost
    cookieStr += '; SameSite=Lax'
  }

  document.cookie = cookieStr
}

/**
 * 获取 cookie 值
 * @param {string} name
 * @returns {string|null}
 */
function get(name) {
  const encoded = encodeURIComponent(name) + '='
  const cookies = document.cookie.split(';')
  for (let i = 0; i < cookies.length; i++) {
    let c = cookies[i].trim()
    if (c.indexOf(encoded) === 0) {
      return decodeURIComponent(c.substring(encoded.length))
    }
  }
  return null
}

/**
 * 删除 cookie
 * @param {string} name
 * @param {string} [path]
 */
function remove(name, path = '/') {
  document.cookie = `${encodeURIComponent(name)}=; path=${path}; max-age=0`
}

/**
 * 检查 cookie 是否存在
 * @param {string} name
 * @returns {boolean}
 */
function has(name) {
  return get(name) !== null
}

export const cookie = { set, get, remove, has }