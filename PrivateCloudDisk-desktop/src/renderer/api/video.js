/**
 * api/video.js - 视频流媒体 API 层
 *
 * 后端对接说明:
 *   1. 视频流媒体播放需要通过后端获取带签名的流媒体 URL
 *   2. MP4 文件通过 HTTP Range 请求实现 Seek 和分片加载
 *   3. HLS 流通过 .m3u8 清单文件 + .ts 分片实现
 *   4. 后端需要实现以下端点 (后续对接):
 *
 *   GET /business/videos/{file_id}/stream-info
 *     → 返回视频流信息，包含可用分辨率列表、签名 URL 等
 *     Response: {
 *       code: 200,
 *       data: {
 *         title: string,
 *         duration: number,          // 秒
 *         cover_url: string,
 *         resolutions: [
 *           { label: '4K', height: 2160, url: string, type: 'mp4'|'hls' },
 *           { label: '1080P', height: 1080, url: string, type: 'mp4'|'hls' },
 *           { label: '720P', height: 720, url: string, type: 'mp4'|'hls' },
 *           { label: '480P', height: 480, url: string, type: 'mp4'|'hls' },
 *           { label: '360P', height: 360, url: string, type: 'mp4'|'hls' },
 *         ]
 *       }
 *     }
 *
 *   GET /business/videos/{file_id}/sprite-image
 *     → 返回进度条悬停预览雪碧图
 *     Response: { code: 200, data: { sprite_url: string, cols: number, rows: number,
 *                thumbnail_width: number, thumbnail_height: number, interval: number } }
 *
 *   GET /file-service/videos/{file_id}/stream?token={op_token}&resolution={height}
 *     → 流媒体直链 (支持 Range 请求)
 *     Response: 视频二进制流 (206 Partial Content)
 *
 *   GET /file-service/videos/{file_id}/hls/{resolution}/master.m3u8?token={op_token}
 *     → HLS 主清单文件
 *     Response: application/vnd.apple.mpegurl
 */

import { get, post } from '@/utils/request'
import { FILE_BASE_URL, PLATFORM_BASE_URL } from '@/utils/const'

// ==================== 视频流信息 ====================

/**
 * 获取视频流播放信息
 * @param {string} fileId - 文件 ID
 * @returns {Promise<{ title: string, duration: number, cover_url: string, resolutions: Array }>}
 */
export function getVideoStreamInfo(fileId) {
  return get(`/videos/${fileId}/stream-info`)
}

/**
 * 获取视频进度条雪碧图信息 (用于悬停预览)
 * @param {string} fileId
 * @returns {Promise<{ sprite_url: string, cols: number, rows: number, thumbnail_width: number, thumbnail_height: number, interval: number }>}
 */
export function getVideoSpriteInfo(fileId) {
  return get(`/videos/${fileId}/sprite-image`)
}

/**
 * 获取视频封面图 URL
 * @param {string} fileId
 * @param {string} [fileName]
 * @returns {string}
 */
export function getVideoCoverUrl(fileId, fileName) {
  const token = window.localStorage.getItem('pcd_token')
  return `${FILE_BASE_URL}/videos/${fileId}/cover?token=${token || ''}`
}

// ==================== 流媒体 URL 构建 ====================

/**
 * 构建 MP4 流媒体直链 URL (支持 Range 请求)
 * 后端需实现 GET /downloads/videos/{file_id}/stream 端点
 * 支持 Range 请求头: Range: bytes=0-1048575
 *
 * @param {string} fileId
 * @param {string} operationToken - 操作凭证
 * @param {number} [resolutionHeight] - 指定分辨率高度
 * @returns {string}
 */
export function buildStreamUrl(fileId, operationToken, resolutionHeight) {
  const params = new URLSearchParams()
  params.set('token', operationToken)
  if (resolutionHeight) {
    params.set('resolution', resolutionHeight)
  }
  return `${FILE_BASE_URL}/downloads/videos/${fileId}/stream?${params.toString()}`
}

/**
 * 构建 HLS 主清单 URL
 * 后端需实现 GET /downloads/videos/{file_id}/hls/master.m3u8 端点
 *
 * @param {string} fileId
 * @param {string} operationToken
 * @returns {string}
 */
export function buildHlsUrl(fileId, operationToken) {
  return `${FILE_BASE_URL}/downloads/videos/${fileId}/hls/master.m3u8?token=${operationToken}`
}

/**
 * 构建 HLS 多码率清单 URL (指定分辨率)
 * @param {string} fileId
 * @param {string} operationToken
 * @param {number} resolutionHeight
 * @returns {string}
 */
export function buildHlsResolutionUrl(fileId, operationToken, resolutionHeight) {
  return `${FILE_BASE_URL}/downloads/videos/${fileId}/hls/${resolutionHeight}/playlist.m3u8?token=${operationToken}`
}

// ==================== 操作凭证 ====================

/**
 * 申请视频流媒体操作凭证
 * @param {string} fileId
 * @returns {Promise<{ operation_token: string }>}
 */
export function requestVideoToken(fileId) {
  return post('/files/operation-tokens', {
    file_id: fileId,
    operation_type: 'stream'
  }, 'file')
}

// ==================== 播放历史 ====================

/**
 * 上报播放进度 (用于断点续播)
 * @param {string} fileId
 * @param {number} currentTime - 当前播放时间 (秒)
 * @param {number} duration    - 总时长 (秒)
 */
export function reportPlayProgress(fileId, currentTime, duration) {
  return post('/videos/play-progress', {
    file_id: fileId,
    current_time: currentTime,
    duration
  })
}

/**
 * 获取上次播放进度
 * @param {string} fileId
 * @returns {Promise<{ current_time: number }>}
 */
export function getPlayProgress(fileId) {
  return get(`/videos/${fileId}/play-progress`)
}

// ==================== 模拟数据 (后端未对接时使用) ====================

/**
 * 生成模拟视频流信息 (用于前端开发调试)
 * 后端对接后删除此函数，改用 getVideoStreamInfo
 *
 * @param {object} fileInfo - 文件基本信息
 * @returns {object}
 */
export function getMockStreamInfo(fileInfo) {
  const name = fileInfo?.file_name || fileInfo?.name || 'video.mp4'
  const ext = (name.split('.').pop() || '').toLowerCase()

  const isHlsCapable = ['mp4', 'mov', 'mkv', 'webm', 'm4v'].includes(ext)

  // 构建本地开发用的流 URL (使用 Electron file:// 协议)
  const baseCoverUrl = fileInfo?.cover_url || ''

  return {
    title: name.replace(/\.[^.]+$/, ''),
    duration: 0,
    cover_url: baseCoverUrl,
    resolutions: isHlsCapable ? [
      { label: '4K', height: 2160, url: '', type: 'mp4', bitrate: 45000, width: 3840 },
      { label: '1080P 高清', height: 1080, url: '', type: 'mp4', bitrate: 8000, width: 1920 },
      { label: '720P 高清', height: 720, url: '', type: 'mp4', bitrate: 4000, width: 1280 },
      { label: '480P 清晰', height: 480, url: '', type: 'mp4', bitrate: 1500, width: 854 },
      { label: '360P 流畅', height: 360, url: '', type: 'mp4', bitrate: 800, width: 640 }
    ] : [
      { label: '原画', height: 0, url: '', type: 'mp4', bitrate: 0, width: 0 }
    ]
  }
}