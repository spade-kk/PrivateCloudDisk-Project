// ============================================================
// clientDownloads.ts — 客户端下载与版本管理 API 模块
// ============================================================
// 提供企业级客户端版本查询、下载链接生成、下载统计和自动更新检测功能。
//
// 架构设计：
//   1. 版本清单 (/downloads/version-manifest.json) — 静态 JSON 文件，
//      Nginx 直接返回，无需后端服务，CDN 可缓存。
//   2. 下载统计 API (/api/v1/downloads/stats) — 记录下载事件，用于
//      分析用户平台分布和下载量。
//   3. 客户端自动更新检测 — 通过版本清单的 latest 字段与本地版本
//      对比，判断是否需要更新。
//
// 安全考虑：
//   - 二进制文件通过 Nginx 直接提供，不经过后端服务
//   - 下载链接包含文件 SHA256 哈希，客户端可验证完整性
//   - 支持 HTTPS 强制，防止中间人篡改
// ============================================================

import { get, post } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/**
 * 客户端平台枚举
 *
 * 覆盖桌面端、移动端和命令行工具三大类。
 */
export type ClientPlatform =
  | 'windows-x64'
  | 'windows-arm64'
  | 'macos-x64'
  | 'macos-arm64'
  | 'linux-x64'
  | 'linux-arm64'
  | 'linux-deb'
  | 'linux-rpm'
  | 'ios'
  | 'android'
  | 'cli-macos'
  | 'cli-linux'
  | 'cli-windows'

/**
 * 客户端下载信息
 *
 * 包含二进制文件的元数据，用于前端展示下载按钮和版本信息。
 */
export interface ClientDownload {
  /** 平台标识，如 "windows-x64" */
  platform: ClientPlatform
  /** 显示名称，如 "Windows 64-bit" */
  displayName: string
  /** 操作系统图标类名 */
  iconClass: string
  /** 图标背景色类名 */
  bgClass: string
  /** 图标颜色类名 */
  iconColorClass: string
  /** 版本号，如 "3.2.0" */
  version: string
  /** 文件大小（字节） */
  fileSize: number
  /** 可读的文件大小，如 "128 MB" */
  fileSizeFormatted: string
  /** 文件 SHA256 哈希值 */
  sha256: string
  /** 下载相对路径，如 "/downloads/binaries/CloudDrive-Setup-3.2.0-x64.exe" */
  downloadPath: string
  /** 最低系统要求 */
  requirement: string
  /** 发布日期 (ISO 8601) */
  releaseDate: string
  /** 平台简短描述 */
  description: string
  /** 文件扩展名 */
  extension: string
  /** 是否为推荐下载（当前平台） */
  isRecommended?: boolean
}

/**
 * 版本清单
 *
 * 静态 JSON 文件结构，定义所有客户端版本信息。
 */
export interface VersionManifest {
  /** 最新稳定版本号 */
  latest: string
  /** 最低支持版本（低于此版本强制更新） */
  minSupportedVersion: string
  /** 发布日期 (ISO 8601) */
  releaseDate: string
  /** 更新日志摘要 */
  changelog: string[]
  /** 各平台客户端下载列表 */
  clients: ClientDownload[]
  /** 移动端客户端 */
  mobileClients: ClientDownload[]
}

/**
 * 下载事件记录请求
 */
export interface DownloadEventPayload {
  /** 下载的平台 */
  platform: ClientPlatform
  /** 下载的版本号 */
  version: string
  /** 用户代理字符串 */
  userAgent?: string
}

// ============================================================
// 版本清单获取
// ============================================================

/**
 * 获取客户端版本清单
 *
 * 从静态 JSON 文件获取所有平台的最新版本信息。
 * 路径 /downloads/version-manifest.json 由 Nginx 直接提供，
 * 不经过后端 API 网关，提高响应速度。
 *
 * 缓存策略：
 *   - 浏览器缓存 5 分钟（stale-while-revalidate）
 *   - CDN 缓存 10 分钟
 *   - 客户端可设置 If-None-Match / If-Modified-Since 头
 *
 * @returns Promise<VersionManifest> 版本清单
 */
export async function fetchVersionManifest(): Promise<VersionManifest> {
  const response = await fetch('/downloads/version-manifest.json', {
    method: 'GET',
    headers: {
      'Accept': 'application/json',
      'Cache-Control': 'max-age=300',
    },
  })
  if (!response.ok) {
    throw new Error(`获取版本清单失败: HTTP ${response.status}`)
  }
  return response.json()
}

// ============================================================
// 下载统计
// ============================================================

/**
 * 记录客户端下载事件
 *
 * 在用户点击下载按钮后异步调用，用于后端统计下载量。
 * 调用失败不影响下载流程（fire-and-forget 模式）。
 *
 * @param platform - 下载的平台
 * @param version - 下载的版本号
 */
export function recordDownloadEvent(platform: ClientPlatform, version: string): void {
  const payload: DownloadEventPayload = {
    platform,
    version,
    userAgent: navigator.userAgent,
  }
  // fire-and-forget：不阻塞下载流程
  post('downloads/stats', payload).catch(() => {
    // 统计失败不影响用户体验，静默忽略
  })
}

// ============================================================
// 平台检测工具
// ============================================================

/**
 * 检测当前用户的操作系统和架构
 *
 * 通过 navigator.userAgent 和 navigator.platform 推断用户平台，
 * 用于自动推荐合适的下载链接。
 *
 * @returns ClientPlatform 当前平台标识
 */
export function detectCurrentPlatform(): ClientPlatform {
  const ua = navigator.userAgent.toLowerCase()
  const platform = navigator.platform?.toLowerCase() || ''

  // macOS
  if (platform.includes('mac')) {
    // Apple Silicon 检测：通过 User-Agent 数据
    // 注意：navigator.userAgentData 在部分浏览器中可用
    return 'macos-arm64'
  }

  // Windows
  if (platform.includes('win')) {
    // ARM64 Windows 检测
    if (ua.includes('arm') || ua.includes('aarch64')) {
      return 'windows-arm64'
    }
    return 'windows-x64'
  }

  // Linux
  if (platform.includes('linux')) {
    if (ua.includes('arm') || ua.includes('aarch64')) {
      return 'linux-arm64'
    }
    return 'linux-x64'
  }

  // iOS
  if (/iphone|ipad|ipod/.test(ua)) {
    return 'ios'
  }

  // Android
  if (/android/.test(ua)) {
    return 'android'
  }

  // 默认返回 Windows x64
  return 'windows-x64'
}

/**
 * 根据平台标识获取平台显示名称
 *
 * @param platform - 平台标识
 * @returns 可读的平台名称
 */
export function getPlatformDisplayName(platform: ClientPlatform): string {
  const names: Record<ClientPlatform, string> = {
    'windows-x64': 'Windows 64-bit',
    'windows-arm64': 'Windows ARM64',
    'macos-x64': 'macOS Intel',
    'macos-arm64': 'macOS Apple Silicon',
    'linux-x64': 'Linux x86_64',
    'linux-arm64': 'Linux ARM64',
    'linux-deb': 'Linux (Debian/Ubuntu)',
    'linux-rpm': 'Linux (RHEL/CentOS)',
    'ios': 'iOS',
    'android': 'Android',
    'cli-macos': 'CLI macOS',
    'cli-linux': 'CLI Linux',
    'cli-windows': 'CLI Windows',
  }
  return names[platform] || platform
}

// ============================================================
// 版本比较工具
// ============================================================

/**
 * 比较两个语义化版本号
 *
 * @param v1 - 版本号 1
 * @param v2 - 版本号 2
 * @returns 1 表示 v1 > v2，-1 表示 v1 < v2，0 表示相等
 */
export function compareVersions(v1: string, v2: string): number {
  const parts1 = v1.split('.').map(Number)
  const parts2 = v2.split('.').map(Number)
  const len = Math.max(parts1.length, parts2.length)

  for (let i = 0; i < len; i++) {
    const a = parts1[i] || 0
    const b = parts2[i] || 0
    if (a > b) return 1
    if (a < b) return -1
  }
  return 0
}

/**
 * 检查当前版本是否需要更新
 *
 * @param currentVersion - 当前客户端版本
 * @param latestVersion - 最新版本
 * @param minSupportedVersion - 最低支持版本
 * @returns 更新状态
 */
export function checkUpdateStatus(
  currentVersion: string,
  latestVersion: string,
  minSupportedVersion: string,
): 'up-to-date' | 'update-available' | 'update-required' {
  if (compareVersions(currentVersion, minSupportedVersion) < 0) {
    return 'update-required'
  }
  if (compareVersions(currentVersion, latestVersion) < 0) {
    return 'update-available'
  }
  return 'up-to-date'
}