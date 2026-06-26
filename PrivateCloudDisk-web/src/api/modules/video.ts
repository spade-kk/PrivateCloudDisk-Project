// ============================================================
// 视频流媒体 API 模块
// 对接后端视频流媒体服务，支持 MP4 Range、HLS、DASH 等协议
// ============================================================

import { get, post } from '@/utils/request'

// ---- 类型定义 ----

export interface VideoStreamInfo {
  file_id: string
  file_name: string
  duration: number
  width: number
  height: number
  bitrate: number
  codec: string
  resolutions: ResolutionOption[]
  has_hls: boolean
  has_dash: boolean
  hls_url: string | null
  dash_url: string | null
  mp4_url: string
  thumbnail_url: string
  sprite_url: string
  sprite_config: { cols: number; rows: number; interval: number }
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
}

export interface VideoHistory {
  watchedDuration: number
  totalDuration: number
  completed: boolean
}

export interface SubtitleInfo {
  id: string
  label: string
  url: string
}

export interface SpriteInfo {
  sprite_url: string
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

export function getVideoStreamInfoApi(fileId: string): Promise<{ code: number; data: VideoStreamInfo; message?: string }> {
  return get(`files/video/stream/${fileId}/info`)
}

export function requestVideoTokenApi(
  fileId: string,
  options: VideoTokenOptions = {}
): Promise<{ code: number; data: { token: string; expires_at: string }; message?: string }> {
  return post(`files/video/stream/${fileId}/token`, {
    resolution: options.resolution || 'auto',
    expires_in: options.expiresIn || 3600,
  })
}

export function getVideoThumbnailApi(
  fileId: string,
  options: { time?: number; width?: number; height?: number } = {}
): Promise<Blob> {
  const params = new URLSearchParams()
  if (options.time !== undefined) params.set('time', String(options.time))
  if (options.width) params.set('width', String(options.width))
  if (options.height) params.set('height', String(options.height))
  return get(`video/thumbnail/${fileId}?${params.toString()}`, null, {
    responseType: 'blob',
  }) as Promise<Blob>
}

export function getVideoSpriteInfoApi(fileId: string): Promise<{ code: number; data: SpriteInfo; message?: string }> {
  return get(`files/video/sprite/${fileId}`)
}

export function getVideoSubtitlesApi(fileId: string): Promise<{ code: number; data: { subtitles: SubtitleInfo[] }; message?: string }> {
  return get(`files/video/subtitle/${fileId}`)
}

export function getVideoSubtitleContentApi(fileId: string, lang: string): Promise<string> {
  return get(`video/subtitle/${fileId}/${lang}`, null, {
    responseType: 'text',
  }) as Promise<string>
}

export function saveVideoProgressApi(
  fileId: string,
  progress: VideoProgress
): Promise<{ code: number; message?: string }> {
  return post(`files/video/progress/${fileId}`, {
    current_time: progress.currentTime,
    duration: progress.duration,
    resolution: progress.resolution,
    playback_rate: progress.playbackRate,
  })
}

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

export function recordVideoHistoryApi(
  fileId: string,
  history: VideoHistory
): Promise<{ code: number; message?: string }> {
  return post(`files/video/history/${fileId}`, {
    watched_duration: history.watchedDuration,
    total_duration: history.totalDuration,
    completed: history.completed,
  })
}

// ---- URL 构建工具函数 ----

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

export function buildDashStreamUrl(fileId: string, token: string): string {
  const baseUrl = import.meta.env.VITE_API_BASE_URL || '/api/v1'
  return `${baseUrl}/files/video/stream/${fileId}/manifest.mpd?token=${token}`
}

export function getPlayerConfigApi(): Promise<{ code: number; data: Record<string, unknown>; message?: string }> {
  return get('files/video/player/config')
}