/**
 * 文件预览工具类
 * 提供文件类型检测、预览配置等功能
 */

// 支持的文件格式
export const SUPPORTED_FORMATS = {
  // 图片格式
  images: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico', 'tiff', 'tif'],

  // 视频格式
  videos: ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv', 'm4v'],

  // 音频格式
  audios: ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'wma', 'opus'],

  // 文档格式
  documents: ['pdf'],

  // Office 文档
  office: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pptm', 'csv', 'rtf'],

  // 代码文件
  code: [
    'js', 'ts', 'jsx', 'tsx',
    'html', 'htm', 'css', 'scss', 'sass', 'less',
    'json', 'xml', 'yaml', 'yml', 'toml',
    'py', 'pyw', 'java', 'class',
    'cpp', 'c', 'h', 'hpp',
    'cs', 'go', 'rs', 'rb', 'php',
    'swift', 'kt', 'scala',
    'sql', 'sh', 'bash', 'zsh',
    'md', 'txt', 'log', 'ini', 'conf', 'cfg'
  ],

  // 压缩文件
  archives: ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz']
}

// 获取文件扩展名
export function getFileExtension(fileName) {
  if (!fileName) return ''
  const parts = fileName.split('.')
  return parts.length > 1 ? parts.pop().toLowerCase() : ''
}

// 检测是否为图片
export function isImage(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.images.includes(ext)
}

// 检测是否为视频
export function isVideo(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.videos.includes(ext)
}

// 检测是否为音频
export function isAudio(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.audios.includes(ext)
}

// 检测是否为PDF
export function isPdf(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.documents.includes(ext)
}

// 检测是否为Office文档
export function isOffice(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.office.includes(ext)
}

// 检测是否为Word文档
export function isWord(fileName) {
  const ext = getFileExtension(fileName)
  return ['doc', 'docx'].includes(ext)
}

// 检测是否为Excel文件
export function isExcel(fileName) {
  const ext = getFileExtension(fileName)
  return ['xls', 'xlsx', 'csv'].includes(ext)
}

// 检测是否为PowerPoint文件
export function isPowerPoint(fileName) {
  const ext = getFileExtension(fileName)
  return ['ppt', 'pptx', 'pptm'].includes(ext)
}

// 检测是否为代码文件
export function isCode(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.code.includes(ext)
}

// 检测是否为压缩文件
export function isArchive(fileName) {
  const ext = getFileExtension(fileName)
  return SUPPORTED_FORMATS.archives.includes(ext)
}

// 检测是否为文本文件
export function isText(fileName) {
  const ext = getFileExtension(fileName)
  return ['txt', 'md', 'log'].includes(ext)
}

// 检测是否为可预览文件
export function isPreviewable(fileName) {
  return isImage(fileName) || isVideo(fileName) || isAudio(fileName) ||
         isPdf(fileName) || isOffice(fileName) || isCode(fileName) ||
         isArchive(fileName) || isText(fileName)
}

// 获取文件类型图标
export function getFileTypeIcon(fileName) {
  if (isImage(fileName)) return 'fa fa-image'
  if (isVideo(fileName)) return 'fa fa-film'
  if (isAudio(fileName)) return 'fa fa-music'
  if (isPdf(fileName)) return 'fa fa-file-pdf-o'
  if (isWord(fileName)) return 'fa fa-file-word-o'
  if (isExcel(fileName)) return 'fa fa-file-excel-o'
  if (isPowerPoint(fileName)) return 'fa fa-file-powerpoint-o'
  if (isCode(fileName)) return 'fa fa-code'
  if (isArchive(fileName)) return 'fa fa-file-archive-o'
  if (isText(fileName)) return 'fa fa-file-text-o'
  return 'fa fa-file-o'
}

// 获取文件类型名称
export function getFileTypeName(fileName) {
  const ext = getFileExtension(fileName)
  if (isImage(fileName)) return '图片'
  if (isVideo(fileName)) return '视频'
  if (isAudio(fileName)) return '音频'
  if (isPdf(fileName)) return 'PDF文档'
  if (isWord(fileName)) return 'Word文档'
  if (isExcel(fileName)) return 'Excel表格'
  if (isPowerPoint(fileName)) return 'PowerPoint演示文稿'
  if (isCode(fileName)) return '代码文件'
  if (isArchive(fileName)) return '压缩文件'
  if (isText(fileName)) return '文本文件'
  return `${ext.toUpperCase()} 文件`
}

// 获取预览配置
export function getPreviewConfig(fileName) {
  const ext = getFileExtension(fileName)

  // 图片配置
  if (isImage(fileName)) {
    return {
      type: 'image',
      supports: ['zoom', 'rotate', 'fullscreen'],
      maxSize: 10 * 1024 * 1024, // 10MB
      thumbnail: true
    }
  }

  // 视频配置
  if (isVideo(fileName)) {
    return {
      type: 'video',
      supports: ['fullscreen', 'download', 'quality'],
      maxSize: 500 * 1024 * 1024, // 500MB
      thumbnail: true
    }
  }

  // 音频配置
  if (isAudio(fileName)) {
    return {
      type: 'audio',
      supports: ['download', 'visualization'],
      maxSize: 50 * 1024 * 1024, // 50MB
      thumbnail: false
    }
  }

  // PDF配置
  if (isPdf(fileName)) {
    return {
      type: 'pdf',
      supports: ['fullscreen', 'download', 'zoom', 'page'],
      maxSize: 100 * 1024 * 1024, // 100MB
      thumbnail: false
    }
  }

  // Office文档配置
  if (isOffice(fileName)) {
    return {
      type: 'office',
      supports: ['download'],
      maxSize: 50 * 1024 * 1024, // 50MB
      thumbnail: false,
      requiresConversion: true
    }
  }

  // 代码配置
  if (isCode(fileName)) {
    return {
      type: 'code',
      supports: ['copy', 'download', 'highlight'],
      maxSize: 1 * 1024 * 1024, // 1MB
      thumbnail: false
    }
  }

  // 文本配置
  if (isText(fileName)) {
    return {
      type: 'text',
      supports: ['copy', 'download'],
      maxSize: 1 * 1024 * 1024, // 1MB
      thumbnail: false
    }
  }

  // 压缩文件配置
  if (isArchive(fileName)) {
    return {
      type: 'archive',
      supports: ['download'],
      maxSize: 200 * 1024 * 1024, // 200MB
      thumbnail: false
    }
  }

  return {
    type: 'unsupported',
    supports: ['download'],
    maxSize: 0,
    thumbnail: false
  }
}

// 格式化文件大小
export function formatFileSize(bytes) {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}
