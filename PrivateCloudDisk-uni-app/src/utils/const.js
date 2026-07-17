/**
 * utils/const.js - 全局常量定义
 *
 * 约定:
 * - API 路径前缀区分平台服务 (platform) 与文件服务 (file)
 * - 所有枚举值与后端保持一致
 */

/** 环境配置 - 根据编译平台自动切换 */
const ENV_MAP = {
  development: {
    platformBaseURL: 'http://localhost:8080/api/v1',
    fileBaseURL: 'http://localhost:8080/api/v1'
  },
  production: {
    platformBaseURL: 'https://api.privateclouddisk.com',
    fileBaseURL: 'https://file.privateclouddisk.com'
  }
}

const CURRENT_ENV = process.env.NODE_ENV === 'production' ? 'production' : 'development'

/** ========== API 基础地址 ========== */
export const PLATFORM_BASE_URL = ENV_MAP[CURRENT_ENV].platformBaseURL
export const FILE_BASE_URL = ENV_MAP[CURRENT_ENV].fileBaseURL

/** ========== 接口路径前缀 ========== */
export const API_PREFIX = {
  platform: `${PLATFORM_BASE_URL}/business`,
  file: `${FILE_BASE_URL}`
}

/** ========== 文件类型图标映射（uView Plus 内置图标名） ========== */
export const FILE_TYPE_ICONS = {
  image: 'photo',
  video: 'play-circle',
  audio: 'volume',        // uView 无 music 图标
  document: 'file-text',
  pdf: 'file-text',       // uView 无 file-pdf 图标
  zip: 'file-text',       // uView 无 file-zip 图标
  code: 'file-text',      // uView 无 file-code 图标
  folder: 'folder',
  default: 'file-text'    // uView 无 file 图标
}

/** ========== 文件类型分类 ========== */
export const FILE_CATEGORY = {
  IMAGE: 'image',
  VIDEO: 'video',
  AUDIO: 'audio',
  DOCUMENT: 'document',
  ARCHIVE: 'archive',
  OTHER: 'other'
}

/** ========== 文件扩展名 → 分类映射 ========== */
export const FILE_EXT_CATEGORY = {
  // 图片
  jpg: 'image', jpeg: 'image', png: 'image', gif: 'image',
  webp: 'image', bmp: 'image', svg: 'image', ico: 'image',
  heic: 'image', raw: 'image', tiff: 'image',
  // 视频
  mp4: 'video', avi: 'video', mov: 'video', wmv: 'video',
  flv: 'video', mkv: 'video', webm: 'video', m4v: 'video',
  // 音频
  mp3: 'audio', wav: 'audio', flac: 'audio', aac: 'audio',
  ogg: 'audio', wma: 'audio', m4a: 'audio',
  // 文档
  pdf: 'document', doc: 'document', docx: 'document',
  xls: 'document', xlsx: 'document', ppt: 'document',
  pptx: 'document', txt: 'document', md: 'document',
  // 压缩包
  zip: 'archive', rar: 'archive', '7z': 'archive',
  tar: 'archive', gz: 'archive', bz2: 'archive'
}

/** ========== 操作类型 ========== */
export const OPERATION_TYPE = {
  DOWNLOAD: 'download',
  PREVIEW: 'preview',
  STREAM: 'stream'
}

/** ========== 任务状态 ========== */
export const TASK_STATUS = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled'
}

/** ========== 上传分片默认大小: 5MB ========== */
export const CHUNK_SIZE = 5 * 1024 * 1024

/** ========== 分页默认值 ========== */
export const PAGE_SIZE = 20

/** ========== 本地存储 key 常量 ========== */
export const STORAGE_KEYS = {
  TOKEN: 'pcd_token',
  USER_ID: 'pcd_user_id',
  USER_PROFILE: 'pcd_user_profile',
  ROOT_NODE_ID: 'pcd_root_node_id'
}