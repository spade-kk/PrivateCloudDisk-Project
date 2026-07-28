// ============================================================
// 视频流媒体 API 模块
// 对接后端 FastAPI Storage 服务 HLS 流媒体端点
//
// 后端端点 (video_stream.py):
//   GET  /api/v1/files/video/stream/{file_id}/info          — 获取视频流信息
//   POST /api/v1/files/video/stream/{file_id}/token         — 获取流媒体 Token
//   GET  /api/v1/files/video/stream/{file_id}/master.m3u8   — 获取 HLS 主播放列表
//   GET  /api/v1/files/video/stream/{file_id}/{res}/index.m3u8 — 获取分辨率播放列表
//   GET  /api/v1/files/video/stream/{file_id}/{res}/{seg}.ts  — 获取 TS 分片
//
// 后端缩略图端点 (files.py):
//   GET  /api/v1/files/files/{file_id}/thumbnail?size=small|medium|large
// ============================================================

import { get, post } from '@/utils/request'

// ---- 类型定义 ----

export interface VideoStreamInfo {
  file_id: string
  has_hls: boolean
  has_dash: boolean
  hls_url: string | null
  dash_url: string | null
  resolutions: ResolutionOption[]
  duration: number
  width: number
  height: number
  preview_url: string | null
  message?: string
}

export interface ResolutionOption {
  label: string
  width: number
  height: number
  bitrate: number
}

export interface VideoTokenOptions {
  resolution?: string
  expiresIn?: number
}

export interface VideoProgress {
  currentTime: number
  duration: number
  resolution: string
  playbackRate: number
  fileName?: string
}

export interface VideoHistory {
  watchedDuration: number
  totalDuration: number
  completed: boolean
  fileName?: string
}

export interface SubtitleInfo {
  id: string
  label: string
  url: string
}

export interface SpriteInfo {
  sprite_url: string
  sprite_vtt_url: string | null
  sprite_image: string
  config: {
    cols: number
    rows: number
    interval: number
    width: number
    height: number
  }
}

export interface VideoStreamUrlOptions {
  resolution?: string
}

// ---- API 函数 ----

/**
 * 获取视频流信息
 * 后端: GET /api/v1/files/video/stream/{file_id}/info
 */
export function getVideoStreamInfoApi(fileId: string): Promise<{ code: number; data: VideoStreamInfo; message?: string }> {
  return get(`files/video/stream/${fileId}/info`)
}

/**
 * 获取流媒体访问 Token
 * 后端: POST /api/v1/files/video/stream/{file_id}/token
 */
export function requestVideoTokenApi(
  fileId: string,
  options: VideoTokenOptions = {}
): Promise<{ code: number; data: { token: string; expires_at: string }; message?: string }> {
  return post(`files/video/stream/${fileId}/token`, {
    resolution: options.resolution || 'auto',
    expires_in: options.expiresIn || 3600,
  })
}

/**
 * 获取视频缩略图 URL（独立于图片缩略图接口）
 *
 * 后端: GET /api/v1/files/video/stream/{file_id}/thumbnail?size=small|medium|large
 * 使用 ffmpeg 提取视频首帧，与图片缩略图（libvips）生成逻辑分离。
 *
 * @param fileId - 文件 ID
 * @param size - 尺寸: small(160×90), medium(400×225), large(800×450)
 * @param token - HLS 流媒体访问 Token（可选，用于无 Header 鉴权场景如 img 标签）
 * @returns 完整的缩略图 URL 字符串
 */
export function getVideoThumbnailUrl(
  fileId: string,
  size: 'small' | 'medium' | 'large' | 'poster' = 'small',
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ size })
  return `${baseUrl}/files/video/stream/${encodeURIComponent(fileId)}/thumbnail?${params.toString()}`
}

/**
 * 获取文件浏览器前 30 秒悬停预览素材 URL。
 *
 * 素材由 HLS 增强流水线预生成；调用方需通过带鉴权头的 Blob 请求加载。
 */
export function getVideoHoverPreviewUrl(fileId: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${baseUrl}/files/video/stream/${encodeURIComponent(fileId)}/hover-preview`
}

/**
 * 获取视频雪碧图信息
 * 后端: GET /api/v1/files/video/sprite/{file_id}?token={token}
 *
 * @param fileId - 文件 ID
 * @param token - HLS 流媒体访问 Token（用于生成雪碧图/VTT 鉴权 URL）
 */
export function getVideoSpriteInfoApi(
  fileId: string,
  token?: string,
): Promise<{ code: number; data: SpriteInfo; message?: string }> {
  const params = token ? { token } : {}
  return get(`files/video/sprite/${fileId}`, params)
}

/**
 * 获取视频字幕列表
 * 后端: GET /api/v1/files/video/subtitle/{file_id}
 */
export function getVideoSubtitlesApi(fileId: string): Promise<{ code: number; data: { subtitles: SubtitleInfo[] }; message?: string }> {
  return get(`files/video/subtitle/${fileId}`)
}

/**
 * 获取视频字幕内容
 * 后端: GET /api/v1/video/subtitle/{file_id}/{lang}
 */
export function getVideoSubtitleContentApi(fileId: string, lang: string): Promise<string> {
  return get(`video/subtitle/${fileId}/${lang}`, undefined, {
    responseType: 'text',
  }) as Promise<string>
}

/**
 * 保存视频播放进度
 * 后端: POST /api/v1/files/video/progress/{file_id}
 */
export function saveVideoProgressApi(
  fileId: string,
  progress: VideoProgress
): Promise<{ code: number; message?: string }> {
  return post(`files/video/progress/${fileId}`, {
    current_time: progress.currentTime,
    duration: progress.duration,
    resolution: progress.resolution,
    playback_rate: progress.playbackRate,
    file_name: progress.fileName || '',
  })
}

/**
 * 获取视频播放进度
 * 后端: GET /api/v1/files/video/progress/{file_id}
 */
export function getVideoProgressApi(
  fileId: string
): Promise<{
  code: number
  data: {
    current_time: number
    duration: number
    resolution: string
    playback_rate: number
    updated_at: string
  }
  message?: string
}> {
  return get(`files/video/progress/${fileId}`)
}

/**
 * 记录视频观看历史
 * 后端: POST /api/v1/files/video/history/{file_id}
 */
export function recordVideoHistoryApi(
  fileId: string,
  history: VideoHistory
): Promise<{ code: number; message?: string }> {
  return post(`files/video/history/${fileId}`, {
    watched_duration: history.watchedDuration,
    total_duration: history.totalDuration,
    completed: history.completed,
    file_name: history.fileName || '',
  })
}

export interface VideoHistoryItem {
  file_id: string
  file_name: string
  watched_duration: number
  total_duration: number
  completed: boolean
  updated_at: string
  thumbnail_url: string
}

export function getVideoHistoryApi(limit = 20, offset = 0): Promise<{ code: number; data: { items: VideoHistoryItem[]; total: number } }> {
  return get('files/video/history', { limit, offset })
}

export function getVideoStatisticsApi(): Promise<{ code: number; data: { playable_video_count: number } }> {
  return get('files/video/history/statistics')
}

// ---- URL 构建工具函数 ----

/**
 * 构建 HLS 主播放列表 URL
 *
 * 后端: GET /api/v1/files/video/stream/{file_id}/master.m3u8?token={token}
 *
 * 注意: 后端返回的 master.m3u8 中，variant playlist 和 TS segment 的
 * URL 已经被后端自动附加 token 参数，前端无需额外处理。
 * hls.js 会自动解析 master.m3u8 并跟随到 variant playlist 和 segment。
 */
export function buildHlsStreamUrl(
  fileId: string | undefined,
  token: string,
  options: VideoStreamUrlOptions = {}
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ token })
  if (options.resolution && options.resolution !== 'auto') {
    params.set('resolution', options.resolution)
  }
  return `${baseUrl}/files/video/stream/${fileId}/master.m3u8?${params.toString()}`
}

/**
 * 构建 MP4 直链播放 URL
 *
 * 后端: GET /api/v1/files/video/stream/{file_id}/playlist.mp4?token={token}
 * 备注: 对于 MP4 文件，后端支持 HTTP Range 请求（206 Partial Content），
 * 前端可直接使用 <video src="..."> 播放，浏览器自动处理 Range。
 *
 * @deprecated 优先使用 HLS 流播放（buildHlsStreamUrl），MP4 仅作为降级方案
 */
export function buildVideoStreamUrl(
  fileId: string | undefined,
  token: string,
  options: VideoStreamUrlOptions = {}
): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  const params = new URLSearchParams({ token })
  if (options.resolution && options.resolution !== 'auto') {
    params.set('resolution', options.resolution)
  }
  return `${baseUrl}/files/video/stream/${fileId}/playlist.mp4?${params.toString()}`
}

/**
 * 构建 DASH manifest URL
 *
 * 后端: GET /api/v1/files/video/stream/{file_id}/manifest.mpd?token={token}
 * 备注: 当前后端仅支持 HLS，DASH 为预留接口
 */
export function buildDashStreamUrl(fileId: string, token: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${baseUrl}/files/video/stream/${fileId}/manifest.mpd?token=${token}`
}

/**
 * 获取播放器全局配置
 * 后端: GET /api/v1/files/video/player/config
 */
export function getPlayerConfigApi(): Promise<{ code: number; data: Record<string, unknown>; message?: string }> {
  return get('files/video/player/config')
}
