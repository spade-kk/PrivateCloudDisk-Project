// ============================================================
// officePreviewStore.ts — Office 文档预览状态管理
// ============================================================
// 参考视频播放器 store（videoPlayerStore.ts）的对接模式：
//   - 后端自动流水线处理，前端不主动触发转换
//   - 仅调用一个查询接口获取预览状态
//   - 根据返回的 status 展示对应的 UI 状态
//
// 状态流转：
//   pending → processing → completed  (正常流程)
//   pending → processing → failed     (转换失败)
//   not_found                         (文件/资源不存在)
//
// 前端 UI 展示策略：
//   - completed:  渲染 PDF 预览页面
//   - processing: 显示"文档转换中"加载动画 + 进度
//   - failed:     显示错误详情 + 重试/返回按钮
//   - not_found:  显示"预览资源不存在" + 返回按钮
//   - pending:    显示"等待处理"提示
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  getDocumentPreviewInfoApi,
  getDocumentThumbnailUrl,
  type DocumentPreviewInfo,
  type DocumentPreviewStatus,
} from '@/api/modules/officePreview'

/** 文件信息 */
export interface OfficeFileInfo {
  node_id: string
  node_name: string
  file_size?: number
  node_type?: string
  [key: string]: any
}

/** 预览配置 */
export interface PreviewConfig {
  scale: number
  rotation: number
  currentPage: number
  colorMode: 'auto' | 'light' | 'dark' | 'sepia'
}

const DEFAULT_PREVIEW_CONFIG: PreviewConfig = {
  scale: 1,
  rotation: 0,
  currentPage: 1,
  colorMode: 'auto',
}

export const useOfficePreviewStore = defineStore('officePreview', () => {
  // ============================================================
  // 状态
  // ============================================================

  /** 当前文件信息 */
  const currentFile = ref<OfficeFileInfo | null>(null)

  /** 预览信息（后端返回的完整数据） */
  const previewInfo = ref<DocumentPreviewInfo | null>(null)

  /** 加载状态 */
  const loading = ref(false)

  /** 错误信息 */
  const error = ref<{ title: string; message: string } | null>(null)

  /** 预览配置 */
  const config = ref<PreviewConfig>({ ...DEFAULT_PREVIEW_CONFIG })

  // ============================================================
  // 计算属性
  // ============================================================

  /** 预览状态 */
  const status = computed<DocumentPreviewStatus>(() => {
    return previewInfo.value?.status || 'pending'
  })

  /** 是否已完成转换 */
  const isCompleted = computed(() => status.value === 'completed')

  /** 是否正在处理 */
  const isProcessing = computed(() => status.value === 'processing')

  /** 是否转换失败 */
  const isFailed = computed(() => status.value === 'failed')

  /** 是否未找到 */
  const isNotFound = computed(() => status.value === 'not_found')

  /** 是否等待中 */
  const isPending = computed(() => status.value === 'pending')

  /** 预览 URL */
  const previewUrl = computed(() => previewInfo.value?.previewUrl || '')

  /** 缩略图 URL */
  const thumbnailUrl = computed(() => {
    if (previewInfo.value?.thumbnailUrl) return previewInfo.value.thumbnailUrl
    if (!currentFile.value?.node_id) return ''
    return getDocumentThumbnailUrl(currentFile.value.node_id, 'medium')
  })

  /** 总页数 */
  const totalPages = computed(() => previewInfo.value?.metadata?.totalPages || 0)

  /** 文档元数据 */
  const metadata = computed(() => previewInfo.value?.metadata)

  /** 转换进度 */
  const progress = computed(() => previewInfo.value?.progress || 0)

  /** 文档类型 */
  const documentType = computed(() => {
    return metadata.value?.documentType || inferDocumentType(currentFile.value?.node_name || '')
  })

  /** 文件大小（格式化） */
  const fileSizeFormatted = computed(() => {
    const size = currentFile.value?.file_size || previewInfo.value?.metadata?.fileSize
    if (!size) return ''
    return formatFileSize(size)
  })

  // ============================================================
  // 方法
  // ============================================================

  /**
   * 加载文档预览信息
   *
   * 流程：调用后端 preview-info 接口 → 根据返回的 status 更新 UI 状态
   * 与视频播放器 loadVideo() 模式一致
   */
  async function loadDocument(file: OfficeFileInfo): Promise<void> {
    loading.value = true
    error.value = null
    currentFile.value = file
    resetConfig()

    try {
      const res = await getDocumentPreviewInfoApi(file.node_id)

      if (res.code !== 200) {
        // 后端返回非 200：可能是文件不存在或接口错误
        error.value = {
          title: '无法加载预览',
          message: res.message || '获取文档预览信息失败，请检查文件是否存在',
        }
        return
      }

      previewInfo.value = res.data

      // 根据后端返回的状态展示不同 UI
      if (res.data.status === 'failed') {
        error.value = {
          title: '预览生成失败',
          message: res.data.message || res.data.errorDetail || '文档转换过程中发生错误，可能是文件已损坏或格式不支持',
        }
      } else if (res.data.status === 'not_found') {
        error.value = {
          title: '预览资源不存在',
          message: res.data.message || '该文件的预览资源尚未生成或已被清理，请联系管理员',
        }
      }
      // processing / pending / completed 由组件根据 status 自行渲染 UI
    } catch (err: any) {
      console.error('加载文档预览失败:', err)
      error.value = {
        title: '网络错误',
        message: err.message || '无法连接到服务器，请检查网络连接后重试',
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 重试加载
   */
  async function retry(): Promise<void> {
    error.value = null
    if (currentFile.value) {
      await loadDocument(currentFile.value)
    }
  }

  /** 重置配置 */
  function resetConfig(): void {
    config.value = { ...DEFAULT_PREVIEW_CONFIG }
  }

  /** 设置当前页 */
  function setCurrentPage(page: number): void {
    config.value.currentPage = Math.max(1, Math.min(page, totalPages.value || 1))
  }

  /** 上一页 */
  function previousPage(): void {
    if (config.value.currentPage > 1) {
      config.value.currentPage--
    }
  }

  /** 下一页 */
  function nextPage(): void {
    if (config.value.currentPage < (totalPages.value || 1)) {
      config.value.currentPage++
    }
  }

  /** 放大 */
  function zoomIn(): void {
    config.value.scale = Math.min(3, config.value.scale + 0.1)
  }

  /** 缩小 */
  function zoomOut(): void {
    config.value.scale = Math.max(0.25, config.value.scale - 0.1)
  }

  /** 重置缩放 */
  function resetZoom(): void {
    config.value.scale = 1
  }

  /** 旋转 */
  function rotate(delta: number): void {
    config.value.rotation = (config.value.rotation + delta) % 360
  }

  /** 关闭预览 */
  function closePreview(): void {
    resetConfig()
    previewInfo.value = null
    error.value = null
    loading.value = false
    currentFile.value = null
  }

  return {
    // 状态
    currentFile,
    previewInfo,
    loading,
    error,
    config,
    // 计算属性
    status,
    isCompleted,
    isProcessing,
    isFailed,
    isNotFound,
    isPending,
    previewUrl,
    thumbnailUrl,
    totalPages,
    metadata,
    progress,
    documentType,
    fileSizeFormatted,
    // 方法
    loadDocument,
    retry,
    resetConfig,
    setCurrentPage,
    previousPage,
    nextPage,
    zoomIn,
    zoomOut,
    resetZoom,
    rotate,
    closePreview,
  }
})

// ============================================================
// 工具函数
// ============================================================

/** 根据文件扩展名推断文档类型 */
function inferDocumentType(fileName: string): string {
  const ext = (fileName || '').split('.').pop()?.toLowerCase() || ''
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'excel'
  if (['ppt', 'pptx', 'pptm'].includes(ext)) return 'powerpoint'
  if (ext === 'pdf') return 'pdf'
  return 'unknown'
}

/** 格式化文件大小 */
function formatFileSize(bytes: number): string {
  if (!bytes || bytes <= 0) return ''
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let size = bytes
  let unitIndex = 0
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024
    unitIndex++
  }
  return `${size.toFixed(unitIndex === 0 ? 0 : 1)} ${units[unitIndex]}`
}