import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import {
  getFilePreviewTokenApi,
  getFileMetadataApi,
  getFileContentApi,
  getDocumentConversionStatusApi,
  requestDocumentConversionApi
} from '@/api/modules/preview'

/**
 * 文件预览状态管理
 * 统一管理文件预览的加载状态、预览内容、错误处理等
 */
export const usePreviewStore = defineStore('preview', () => {
  const toastStore = useToastStore()

  // 状态
  const currentFile = ref(null) // 当前预览文件
  const fileMetadata = ref(null) // 文件元数据
  const previewUrl = ref('') // 预览URL
  const previewContent = ref('') // 预览内容（文本/代码）
  const loading = ref(false) // 加载状态
  const error = ref(null) // 错误信息
  const conversionStatus = ref('pending') // Office文档转换状态
  const previewMode = ref('inline') // 预览模式: inline/attachment
  const supportedFormats = ref({
    images: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico'],
    videos: ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv'],
    audios: ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'wma'],
    documents: ['pdf'],
    office: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pptm'],
    code: ['js', 'ts', 'html', 'css', 'json', 'xml', 'py', 'java', 'cpp', 'c', 'cs', 'go', 'rb', 'php', 'sql', 'sh', 'md', 'yaml', 'yml', 'txt', 'log'],
    archives: ['zip', 'rar', '7z', 'tar', 'gz']
  })

  // 计算属性
  const fileExtension = computed(() => {
    if (!currentFile.value?.node_name) return ''
    const parts = currentFile.value.node_name.split('.')
    return parts.length > 1 ? parts.pop().toLowerCase() : ''
  })

  const isImage = computed(() => supportedFormats.value.images.includes(fileExtension.value))
  const isVideo = computed(() => supportedFormats.value.videos.includes(fileExtension.value))
  const isAudio = computed(() => supportedFormats.value.audios.includes(fileExtension.value))
  const isPdf = computed(() => fileExtension.value === 'pdf')
  const isOffice = computed(() => supportedFormats.value.office.includes(fileExtension.value))
  const isWord = computed(() => ['doc', 'docx'].includes(fileExtension.value))
  const isExcel = computed(() => ['xls', 'xlsx', 'csv'].includes(fileExtension.value))
  const isPowerPoint = computed(() => ['ppt', 'pptx', 'pptm'].includes(fileExtension.value))
  const isCode = computed(() => supportedFormats.value.code.includes(fileExtension.value))
  const isArchive = computed(() => supportedFormats.value.archives.includes(fileExtension.value))
  const isText = computed(() => ['txt', 'md', 'log'].includes(fileExtension.value))

  const isPreviewable = computed(() => {
    return isImage.value || isVideo.value || isAudio.value || isPdf.value ||
           isOffice.value || isCode.value || isArchive.value || isText.value
  })

  const fileSizeFormatted = computed(() => {
    if (!fileMetadata.value?.file_size) return ''
    const bytes = fileMetadata.value.file_size
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
  })

  // 方法
  async function openFile(file) {
    loading.value = true
    error.value = null
    currentFile.value = file

    try {
      // 获取文件元数据
      const metadataRes = await getFileMetadataApi(file.node_id)
      if (metadataRes.code === 200) {
        fileMetadata.value = metadataRes.data
      }

      // 根据文件类型获取预览内容
      if (isImage.value || isVideo.value || isAudio.value) {
        // 媒体文件：获取预览URL
        await loadMediaPreview(file)
      } else if (isPdf.value) {
        // PDF文件：获取预览URL
        await loadPdfPreview(file)
      } else if (isOffice.value) {
        // Office文档：检查转换状态
        await loadOfficePreview(file)
      } else if (isCode.value || isText.value) {
        // 代码/文本文件：获取文本内容
        await loadTextPreview(file)
      } else if (isArchive.value) {
        // 压缩包：获取文件列表
        await loadArchivePreview(file)
      } else {
        error.value = {
          title: '不支持的格式',
          message: `暂不支持预览 ${fileExtension.value.toUpperCase()} 格式的文件`
        }
      }
    } catch (err) {
      console.error('加载预览失败:', err)
      error.value = {
        title: '预览加载失败',
        message: err.message || '无法加载文件预览，请稍后重试'
      }
      toastStore.showToast('预览加载失败', 'error')
    } finally {
      loading.value = false
    }
  }

  async function loadMediaPreview(file) {
    try {
      const res = await getFilePreviewTokenApi(file.node_id)
      if (res.code === 200 && res.data?.url) {
        previewUrl.value = res.data.url
      } else {
        throw new Error(res.message || '获取预览URL失败')
      }
    } catch (err) {
      throw new Error('无法加载媒体文件预览: ' + err.message)
    }
  }

  async function loadPdfPreview(file) {
    try {
      const res = await getFilePreviewTokenApi(file.node_id)
      if (res.code === 200 && res.data?.url) {
        previewUrl.value = res.data.url
      } else {
        throw new Error(res.message || '获取PDF预览失败')
      }
    } catch (err) {
      throw new Error('无法加载PDF预览: ' + err.message)
    }
  }

  async function loadOfficePreview(file) {
    try {
      // 检查转换状态
      const statusRes = await getDocumentConversionStatusApi(file.node_id)
      if (statusRes.code === 200) {
        conversionStatus.value = statusRes.data?.status || 'pending'

        if (conversionStatus.value === 'completed') {
          // 已转换，直接使用转换后的预览
          previewUrl.value = statusRes.data.previewUrl
        } else if (conversionStatus.value === 'processing') {
          // 正在转换，等待完成
          toastStore.showToast('文档正在转换中，请稍候...', 'info')
        } else {
          // 未转换，请求转换
          toastStore.showToast('正在准备文档预览...', 'info')
          const convertRes = await requestDocumentConversionApi(file.node_id, {
            format: 'pdf',
            dpi: 150
          })
          if (convertRes.code === 200) {
            conversionStatus.value = 'processing'
            // 轮询转换状态
            pollConversionStatus(file.node_id)
          }
        }
      }
    } catch (err) {
      throw new Error('无法加载Office文档预览: ' + err.message)
    }
  }

  async function pollConversionStatus(fileId) {
    const maxAttempts = 20
    let attempts = 0

    const poll = async () => {
      if (attempts >= maxAttempts) {
        throw new Error('文档转换超时')
      }

      try {
        const res = await getDocumentConversionStatusApi(fileId)
        if (res.code === 200) {
          if (res.data?.status === 'completed') {
            previewUrl.value = res.data.previewUrl
            conversionStatus.value = 'completed'
            toastStore.showToast('文档预览已就绪', 'success')
          } else if (res.data?.status === 'failed') {
            throw new Error('文档转换失败')
          } else {
            attempts++
            setTimeout(poll, 2000) // 每2秒检查一次
          }
        }
      } catch (err) {
        throw err
      }
    }

    await poll()
  }

  async function loadTextPreview(file) {
    try {
      const res = await getFileContentApi(file.node_id, {
        maxSize: 1024 * 1024, // 1MB
        encoding: 'utf-8'
      })
      if (res.code === 200) {
        previewContent.value = res.data?.content || ''
      } else {
        throw new Error(res.message || '获取文件内容失败')
      }
    } catch (err) {
      throw new Error('无法加载文本预览: ' + err.message)
    }
  }

  async function loadArchivePreview(file) {
    // 压缩包预览暂未实现
    error.value = {
      title: '压缩包预览',
      message: '压缩包预览功能正在开发中，请下载后查看'
    }
  }

  function closePreview() {
    currentFile.value = null
    fileMetadata.value = null
    previewUrl.value = ''
    previewContent.value = ''
    loading.value = false
    error.value = null
    conversionStatus.value = 'pending'
  }

  function setPreviewMode(mode) {
    previewMode.value = mode
  }

  // 监听文件变化，自动清理
  function $reset() {
    closePreview()
  }

  return {
    // 状态
    currentFile,
    fileMetadata,
    previewUrl,
    previewContent,
    loading,
    error,
    conversionStatus,
    previewMode,
    supportedFormats,

    // 计算属性
    fileExtension,
    isImage,
    isVideo,
    isAudio,
    isPdf,
    isOffice,
    isWord,
    isExcel,
    isPowerPoint,
    isCode,
    isArchive,
    isText,
    isPreviewable,
    fileSizeFormatted,

    // 方法
    openFile,
    closePreview,
    setPreviewMode,
    $reset
  }
})
