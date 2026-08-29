// ============================================================
// config.js — 配置管理模块
// ============================================================
// 负责加载默认配置、合并命令行参数、读取用户配置文件。
// 优先级：命令行参数 > 用户配置文件 > 默认配置
// ============================================================

import { readFileSync } from 'node:fs'
import { resolve, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))

/** 默认配置（从 config/default.json 加载） */
const DEFAULT_CONFIG = JSON.parse(
  readFileSync(resolve(__dirname, '../config/default.json'), 'utf-8'),
)

/**
 * 加载用户配置文件（JSON 格式）
 * @param {string} configPath - 配置文件路径
 * @returns {object} 用户配置对象，加载失败返回空对象
 */
export function loadUserConfig(configPath) {
  if (!configPath) return {}
  try {
    const content = readFileSync(resolve(configPath), 'utf-8')
    return JSON.parse(content)
  } catch (e) {
    console.error(`加载配置文件失败: ${configPath} — ${e.message}`)
    return {}
  }
}

/**
 * 深度合并两个对象（后者覆盖前者）
 * @param {object} base - 基础对象
 * @param {object} override - 覆盖对象
 * @returns {object} 合并后的新对象
 */
function deepMerge(base, override) {
  const result = { ...base }
  for (const key of Object.keys(override)) {
    if (
      typeof base[key] === 'object' &&
      base[key] !== null &&
      !Array.isArray(base[key]) &&
      typeof override[key] === 'object' &&
      override[key] !== null &&
      !Array.isArray(override[key])
    ) {
      result[key] = deepMerge(base[key], override[key])
    } else if (override[key] !== undefined) {
      result[key] = override[key]
    }
  }
  return result
}

/**
 * 构建最终配置
 * 合并顺序：默认配置 < 用户配置文件 < 命令行参数
 *
 * @param {object} cliOptions - commander 解析的命令行参数
 * @returns {object} 合并后的完整配置
 */
export function buildConfig(cliOptions = {}) {
  // 加载用户配置文件（如果指定）
  const userConfig = cliOptions.config
    ? loadUserConfig(cliOptions.config)
    : {}

  // 命令行参数映射到配置结构
  const cliConfig = {}
  if (cliOptions.host) {
    cliConfig.server = { host: cliOptions.host }
  }
  if (cliOptions.port) {
    cliConfig.server = { ...cliConfig.server, port: parseInt(cliOptions.port, 10) }
  }
  if (cliOptions.token) {
    cliConfig.auth = { token: cliOptions.token }
  }
  if (cliOptions.userId) {
    cliConfig.user = { userId: cliOptions.userId }
  }
  if (cliOptions.receiverId) {
    cliConfig.user = { ...cliConfig.user, receiverId: cliOptions.receiverId }
  }
  if (cliOptions.conversationId) {
    cliConfig.user = { ...cliConfig.user, conversationId: cliOptions.conversationId }
  }
  if (cliOptions.heartbeat) {
    cliConfig.heartbeat = { intervalMs: parseInt(cliOptions.heartbeat, 10) }
  }
  if (cliOptions.verbose) {
    cliConfig.logging = { verbose: true, level: 'debug' }
  }

  return deepMerge(DEFAULT_CONFIG, deepMerge(userConfig, cliConfig))
}

/**
 * 构建 WebSocket 连接 URL
 * @param {object} config - 完整配置
 * @returns {string} ws:// 或 wss:// URL
 */
/**
 * 构建 HTTP API 基础地址（im-platform）
 * 默认 http://localhost:8088/api/v1/im，可通过配置 http.baseUrl 覆盖
 * @param {object} config - 完整配置
 * @returns {string} HTTP 基础地址
 */
export function buildHttpBaseUrl(config) {
  const base = config.http?.baseUrl || 'http://localhost:8088/api/v1/im'
  return base.replace(/\/$/, '')
}

export function buildWsUrl(config) {
  const { host, port, path: wsPath, useTls } = config.server
  const protocol = useTls ? 'wss' : 'ws'
  const token = config.auth.token
  return `${protocol}://${host}:${port}${wsPath}?token=${encodeURIComponent(token)}`
}
