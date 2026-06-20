/**
 * utils/const.js - 全局常量定义
 */
// ==================== API 基础地址 ====================
export const PLATFORM_BASE_URL = 'http://localhost:8080/api/v1/business'
export const FILE_BASE_URL = 'http://localhost:8000'

// ==================== 存储键名 ====================
export const STORAGE_KEYS = {
  TOKEN: 'pcd_token',
  USER_ID: 'pcd_user_id',
  USER_PROFILE: 'pcd_user_profile',
  REMEMBER_LOGIN: 'pcd_remember',
  SAVED_ACCOUNT: 'pcd_saved_account',
  SETTINGS: 'pcd_settings',
  THEME: 'pcd_theme'
}

// ==================== 分页 ====================
export const DEFAULT_PAGE_SIZE = 50
export const MAX_PAGE_SIZE = 200

// ==================== 上传相关 ====================
export const CHUNK_SIZE = 5 * 1024 * 1024 // 5 MB per chunk
export const MAX_FILE_SIZE = 5 * 1024 * 1024 * 1024 // 5 GB total

// ==================== 任务状态 ====================
export const TASK_STATUS = {
  PENDING: 'pending',
  PROCESSING: 'processing',
  COMPLETED: 'completed',
  FAILED: 'failed',
  CANCELLED: 'cancelled'
}

// ==================== 文件类型分类 ====================
export const FILE_CATEGORIES = {
  IMAGE: {
    label: '图片',
    extensions: ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp', '.svg', '.ico', '.heic']
  },
  VIDEO: {
    label: '视频',
    extensions: ['.mp4', '.avi', '.mov', '.mkv', '.wmv', '.flv', '.webm', '.m4v', '.3gp']
  },
  AUDIO: {
    label: '音频',
    extensions: ['.mp3', '.wav', '.flac', '.aac', '.ogg', '.wma', '.m4a']
  },
  DOCUMENT: {
    label: '文档',
    extensions: ['.pdf', '.doc', '.docx', '.xls', '.xlsx', '.ppt', '.pptx', '.txt', '.md', '.csv', '.rtf', '.odt']
  },
  ARCHIVE: {
    label: '压缩包',
    extensions: ['.zip', '.rar', '.7z', '.tar', '.gz', '.bz2', '.xz', '.tgz']
  },
  CODE: {
    label: '代码',
    extensions: ['.js', '.jsx', '.ts', '.tsx', '.py', '.java', '.c', '.cpp', '.h', '.go', '.rs', '.rb', '.php', '.html', '.css', '.scss', '.sql', '.sh', '.yaml', '.json', '.xml']
  }
}

// ==================== 应用主题 ====================
export const THEMES = {
  LIGHT: 'light',
  DARK: 'dark',
  SYSTEM: 'system'
}