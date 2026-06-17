import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import {
  getFilePreviewTokenApi,
  getFileMetadataApi,
  getFileContentApi,
  getDocumentConversionStatusApi,
  requestDocumentConversionApi
} from '@/api'

export interface PreviewError {
  title: string
  message: string
}

export const usePreviewStore = defineStore('preview', () => {
  const toastStore = useToastStore()

  const currentFile = ref<any>(null)
  const fileMetadata = ref<any>(null)
  const previewUrl = ref('')
  const previewContent = ref('')
  const loading = ref(false)
  const error = ref<PreviewError | null>(null)
  const conversionStatus = ref('pending')
  const previewMode = ref('inline')
  const supportedFormats = ref({
    images: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp', 'svg', 'ico'],
    videos: ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv'],
    audios: ['mp3', 'wav', 'ogg', 'flac', 'aac', 'm4a', 'wma'],
    documents: ['pdf'],
    office: ['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx', 'pptm'],
    code: ['js', 'ts', 'html', 'css', 'json', 'xml', 'py', 'java', 'cpp', 'c', 'cs', 'go', 'rb', 'php', 'sql', 'sh', 'md', 'yaml', 'yml', 'txt', 'log'],
    archives: ['zip', 'rar', '7z', 'tar', 'gz']
  })

  const fileExtension = computed(() => {
    if (!currentFile.value?.node_name) return ''
    const parts = currentFile.value.node_name.split('.')
    return parts.length > 1 ? parts.pop()!.toLowerCase() : ''
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

  async function openFile(file: any): Promise<void> {
    loading.value = true
    error.value = null
    currentFile.value = file

    try {
      const metadataRes = await getFileMetadataApi(file.node_id)
      if (metadataRes.code === 200) {
        fileMetadata.value = metadataRes.data
      }

      if (isImage.value || isVideo.value || isAudio.value) {
        await loadMediaPreview(file)
      } else if (isPdf.value) {
        await loadPdfPreview(file)
      } else if (isOffice.value) {
        await loadOfficePreview(file)
      } else if (isCode.value || isText.value) {
        await loadTextPreview(file)
      } else if (isArchive.value) {
        await loadArchivePreview(file)
      } else {
        error.value = {
          title: '不支持的格式',
          message: `暂不支持预览 ${fileExtension.value.toUpperCase()} 格式的文件`
        }
      }
    } catch (err: any) {
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

  async function loadMediaPreview(file: any): Promise<void> {
    try {
      const res = await getFilePreviewTokenApi(file.node_id)
      if (res.code === 200 && res.data?.url) {
        previewUrl.value = res.data.url
      } else {
        throw new Error(res.message || '获取预览URL失败')
      }
    } catch (err: any) {
      throw new Error('无法加载媒体文件预览: ' + err.message)
    }
  }

  async function loadPdfPreview(file: any): Promise<void> {
    try {
      const res = await getFilePreviewTokenApi(file.node_id)
      if (res.code === 200 && res.data?.url) {
        previewUrl.value = res.data.url
      } else {
        throw new Error(res.message || '获取PDF预览失败')
      }
    } catch (err: any) {
      throw new Error('无法加载PDF预览: ' + err.message)
    }
  }

  async function loadOfficePreview(file: any): Promise<void> {
    try {
      const statusRes = await getDocumentConversionStatusApi(file.node_id)
      if (statusRes.code === 200) {
        conversionStatus.value = statusRes.data?.status || 'pending'

        if (conversionStatus.value === 'completed') {
          previewUrl.value = statusRes.data.previewUrl
        } else if (conversionStatus.value === 'processing') {
          toastStore.showToast('文档正在转换中，请稍候...', 'info')
        } else {
          toastStore.showToast('正在准备文档预览...', 'info')
          const convertRes = await requestDocumentConversionApi(file.node_id, {
            format: 'pdf',
            dpi: 150
          })
          if (convertRes.code === 200) {
            conversionStatus.value = 'processing'
            pollConversionStatus(file.node_id)
          }
        }
      }
    } catch (err: any) {
      throw new Error('无法加载Office文档预览: ' + err.message)
    }
  }

  async function pollConversionStatus(fileId: string): Promise<void> {
    const maxAttempts = 20
    let attempts = 0

    const poll = async (): Promise<void> => {
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
            setTimeout(poll, 2000)
          }
        }
      } catch (err) {
        throw err
      }
    }

    await poll()
  }

  async function loadTextPreview(file: any): Promise<void> {
    try {
      const res = await getFileContentApi(file.node_id, {
        maxSize: 1024 * 1024,
        encoding: 'utf-8'
      })
      if (res.code === 200) {
        previewContent.value = res.data?.content || ''
      } else {
        throw new Error(res.message || '获取文件内容失败')
      }
    } catch (err: any) {
      throw new Error('无法加载文本预览: ' + err.message)
    }
  }

  async function loadArchivePreview(_file: any): Promise<void> {
    error.value = {
      title: '压缩包预览',
      message: '压缩包预览功能正在开发中，请下载后查看'
    }
  }

  function closePreview(): void {
    currentFile.value = null
    fileMetadata.value = null
    previewUrl.value = ''
    previewContent.value = ''
    loading.value = false
    error.value = null
    conversionStatus.value = 'pending'
  }

  function setPreviewMode(mode: string): void {
    previewMode.value = mode
  }

  function $reset(): void {
    closePreview()
  }

  return {
    currentFile,
    fileMetadata,
    previewUrl,
    previewContent,
    loading,
    error,
    conversionStatus,
    previewMode,
    supportedFormats,
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
    openFile,
    closePreview,
    setPreviewMode,
    $reset,
  }
})