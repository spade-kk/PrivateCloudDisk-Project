import { get, post } from '@/utils/request'
import request from '@/utils/request'

// ============================================================
// 视频流媒体 API 模块
// 对接后端视频流媒体服务，支持 MP4 Range、HLS、DASH 等协议
// ============================================================

/**
 * 获取视频流媒体信息
 * 包含可用的分辨率列表、编码信息、HLS manifest URL 等
 * @param {string} fileId - 文件ID
 * @returns {Promise<Object>}
 * @example
 * {
 *   code: 200,
 *   data: {
 *     file_id: 'xxx',
 *     file_name: 'video.mp4',
 *     duration: 120.5,
 *     width: 1920,
 *     height: 1080,
 *     bitrate: 5000000,
 *     codec: 'h264',
 *     resolutions: [
 *       { label: '1080p', width: 1920, height: 1080, bitrate: 5000000 },
 *       { label: '720p', width: 1280, height: 720, bitrate: 2500000 },
 *       { label: '480p', width: 854, height: 480, bitrate: 1000000 },
 *       { label: '360p', width: 640, height: 360, bitrate: 500000 }
 *     ],
 *     has_hls: true,
 *     has_dash: false,
 *     hls_url: '/api/v1/video/stream/xxx/master.m3u8',
 *     dash_url: null,
 *     mp4_url: '/api/v1/video/stream/xxx/playlist.mp4',
 *     thumbnail_url: '/api/v1/video/thumbnail/xxx',
 *     sprite_url: '/api/v1/video/sprite/xxx',
 *     sprite_config: { cols: 10, rows: 10, interval: 10 }
 *   }
 * }
 */
export function getVideoStreamInfoApi(fileId) {
  return get(`video/stream/${fileId}/info`)
}

/**
 * 获取视频播放凭证（临时 Token）
 * 用于鉴权访问视频流，防止盗链
 * @param {string} fileId - 文件ID
 * @param {Object} options - 选项
 * @param {string} options.resolution - 分辨率标签，如 '1080p', '720p'
 * @param {number} options.expiresIn - Token 有效期（秒），默认 3600
 * @returns {Promise<Object>} { token, expiresAt }
 */
export function requestVideoTokenApi(fileId, options = {}) {
  return post(`video/stream/${fileId}/token`, {
    resolution: options.resolution || 'auto',
    expires_in: options.expiresIn || 3600
  })
}

/**
 * 获取视频缩略图
 * @param {string} fileId - 文件ID
 * @param {Object} options
 * @param {number} options.time - 截取时间点（秒），默认 0
 * @param {number} options.width - 缩略图宽度
 * @param {number} options.height - 缩略图高度
 * @returns {Promise<Blob>} 图片 Blob
 */
export function getVideoThumbnailApi(fileId, options = {}) {
  const params = new URLSearchParams()
  if (options.time !== undefined) params.set('time', options.time)
  if (options.width) params.set('width', options.width)
  if (options.height) params.set('height', options.height)
  return get(`video/thumbnail/${fileId}?${params.toString()}`, null, {
    responseType: 'blob'
  })
}

/**
 * 获取视频雪碧图（进度条预览缩略图）
 * 用于鼠标悬停进度条时显示对应时间点的画面预览
 * @param {string} fileId - 文件ID
 * @returns {Promise<Object>}
 * @example
 * {
 *   code: 200,
 *   data: {
 *     sprite_url: '/api/v1/video/sprite/xxx/vtt',
 *     sprite_image: '/api/v1/video/sprite/xxx/image.jpg',
 *     config: { cols: 10, rows: 10, interval: 10, width: 160, height: 90 }
 *   }
 * }
 */
export function getVideoSpriteInfoApi(fileId) {
  return get(`video/sprite/${fileId}`)
}

/**
 * 获取视频字幕列表
 * @param {string} fileId - 文件ID
 * @returns {Promise<Object>}
 * @example
 * {
 *   code: 200,
 *   data: {
 *     subtitles: [
 *       { id: 'zh-CN', label: '中文（简体）', url: '/api/v1/video/subtitle/xxx/zh-CN.vtt' },
 *       { id: 'en', label: 'English', url: '/api/v1/video/subtitle/xxx/en.vtt' }
 *     ]
 *   }
 * }
 */
export function getVideoSubtitlesApi(fileId) {
  return get(`video/subtitle/${fileId}`)
}

/**
 * 获取视频字幕内容
 * @param {string} fileId - 文件ID
 * @param {string} lang - 语言代码，如 'zh-CN', 'en'
 * @returns {Promise<string>} WebVTT 格式字幕内容
 */
export function getVideoSubtitleContentApi(fileId, lang) {
  return get(`video/subtitle/${fileId}/${lang}`, null, {
    responseType: 'text'
  })
}

/**
 * 记录视频播放进度
 * 用于断点续播功能
 * @param {string} fileId - 文件ID
 * @param {Object} progress
 * @param {number} progress.currentTime - 当前播放时间（秒）
 * @param {number} progress.duration - 视频总时长（秒）
 * @param {string} progress.resolution - 当前分辨率
 * @param {number} progress.playbackRate - 当前播放倍速
 * @returns {Promise<Object>}
 */
export function saveVideoProgressApi(fileId, progress) {
  return post(`video/progress/${fileId}`, {
    current_time: progress.currentTime,
    duration: progress.duration,
    resolution: progress.resolution,
    playback_rate: progress.playbackRate
  })
}

/**
 * 获取视频播放进度（断点续播）
 * @param {string} fileId - 文件ID
 * @returns {Promise<Object>}
 * @example
 * {
 *   code: 200,
 *   data: {
 *     current_time: 120.5,
 *     duration: 600,
 *     resolution: '1080p',
 *     playback_rate: 1.0,
 *     updated_at: '2025-06-01T12:00:00Z'
 *   }
 * }
 */
export function getVideoProgressApi(fileId) {
  return get(`video/progress/${fileId}`)
}

/**
 * 记录视频播放历史（用于推荐/历史记录）
 * @param {string} fileId - 文件ID
 * @param {Object} history
 * @param {number} history.watchedDuration - 已观看时长
 * @param {number} history.totalDuration - 总时长
 * @param {boolean} history.completed - 是否看完
 * @returns {Promise<Object>}
 */
export function recordVideoHistoryApi(fileId, history) {
  return post(`video/history/${fileId}`, {
    watched_duration: history.watchedDuration,
    total_duration: history.totalDuration,
    completed: history.completed
  })
}

// ============================================================
// 视频流 URL 构建工具函数
// ============================================================

/**
 * 构建视频流 URL（支持 Range 请求的 MP4 直链）
 * @param {string} fileId - 文件ID
 * @param {string} token - 播放凭证
 * @param {Object} options
 * @param {string} options.resolution - 分辨率标签
 * @returns {string}
 */
export function buildVideoStreamUrl(fileId, token, options = {}) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ token })
  if (options.resolution && options.resolution !== 'auto') {
    params.set('resolution', options.resolution)
  }
  return `${baseUrl}/video/stream/${fileId}/playlist.mp4?${params.toString()}`
}

/**
 * 构建 HLS 流 URL
 * @param {string} fileId - 文件ID
 * @param {string} token - 播放凭证
 * @param {Object} options
 * @param {string} options.resolution - 分辨率标签
 * @returns {string}
 */
export function buildHlsStreamUrl(fileId, token, options = {}) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ token })
  if (options.resolution && options.resolution !== 'auto') {
    params.set('resolution', options.resolution)
  }
  return `${baseUrl}/video/stream/${fileId}/master.m3u8?${params.toString()}`
}

/**
 * 构建 DASH 流 URL
 * @param {string} fileId - 文件ID
 * @param {string} token - 播放凭证
 * @returns {string}
 */
export function buildDashStreamUrl(fileId, token) {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${baseUrl}/video/stream/${fileId}/manifest.mpd?token=${token}`
}

/**
 * 获取视频播放器配置
 * 从后端获取播放器的全局配置（如快捷键映射、默认音量等）
 * @returns {Promise<Object>}
 */
export function getPlayerConfigApi() {
  return get('video/player/config')
}