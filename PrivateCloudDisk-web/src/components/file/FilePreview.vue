<template>
  <Teleport to="body">
    <Transition name="preview-fade">
      <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/80 p-3 sm:p-4" @click.self="$emit('close')">
        <div class="relative max-h-[calc(100dvh-1.5rem)] w-full max-w-5xl overflow-auto rounded-lg bg-white shadow-2xl">
          <!-- 头部 -->
          <div class="sticky top-0 z-10 flex items-center justify-between gap-3 border-b bg-white p-3 sm:p-4">
            <div class="flex items-center gap-3 min-w-0">
              <i :class="fileTypeIcon" class="text-primary text-xl"></i>
              <span class="font-semibold truncate">{{ node?.node_name }}</span>
            </div>
            <div class="flex items-center gap-2">
              <button @click="handleDownload" class="icon-button" title="下载">
                <i class="fa fa-download"></i>
              </button>
              <button @click="$emit('close')" class="icon-button" title="关闭">
                <i class="fa fa-times"></i>
              </button>
            </div>
          </div>

          <!-- 预览内容 -->
          <div class="p-3 sm:p-4">
            <!-- 视频文件：不再内嵌预览，直接跳转至专属流媒体播放页面 -->
            <div v-if="isVideo" class="text-center py-12">
              <i class="fa fa-film text-6xl text-primary/30 mb-4 block"></i>
              <p class="text-neutral-600 mb-2">视频文件</p>
              <p class="text-neutral-400 text-sm mb-6">正在跳转至专属播放器...</p>
              <button @click="openVideoPlayer" class="bg-primary text-white px-6 py-2 rounded-lg hover:bg-primary/90 transition">
                <i class="fa fa-play-circle mr-2"></i>打开播放器
              </button>
            </div>

            <!-- 图片预览 -->
            <ImagePreview
              v-else-if="isImage"
              :file-url="fileUrl"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- 音频预览 -->
            <AudioPreview
              v-else-if="isAudio"
              :file-url="fileUrl"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- PDF 预览 — 已跳转至独立预览页面，此处仅作兜底 -->
            <div v-else-if="isPdf" class="text-center py-12">
              <i class="fa fa-file-pdf-o text-6xl text-primary/30 mb-4 block"></i>
              <p class="text-neutral-600 mb-2">PDF 文档</p>
              <p class="text-neutral-400 text-sm mb-6">正在跳转至专属预览页面...</p>
              <button @click="openPdfPreview" class="bg-primary text-white px-6 py-2 rounded-lg hover:bg-primary/90 transition">
                <i class="fa fa-file-pdf-o mr-2"></i>打开 PDF 预览
              </button>
            </div>

            <!-- Office文档预览 — 已跳转至独立预览页面，此处仅作兜底 -->
            <div v-else-if="isOffice" class="text-center py-12">
              <i :class="officeIcon" class="text-6xl text-primary/30 mb-4 block"></i>
              <p class="text-neutral-600 mb-2">{{ officeLabel }}</p>
              <p class="text-neutral-400 text-sm mb-6">正在跳转至专属预览页面...</p>
              <button @click="openOfficePreview" class="bg-primary text-white px-6 py-2 rounded-lg hover:bg-primary/90 transition">
                <i class="fa fa-external-link mr-2"></i>打开 {{ officeLabel }} 预览
              </button>
            </div>

            <!-- 代码预览 -->
            <CodePreview
              v-else-if="isCode"
              :code-content="textContent"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- 文本预览 -->
            <TextPreview
              v-else-if="isText"
              :text-content="textContent"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- 不支持的类型 -->
            <div v-else class="text-center py-12">
              <i class="fa fa-file-o text-6xl text-neutral-300 mb-4 block"></i>
              <p class="text-neutral-500 mb-4">暂不支持预览此文件类型</p>
              <button @click="handleDownload" class="bg-primary text-white px-6 py-2 rounded-lg hover:bg-primary/90 transition">
                <i class="fa fa-download mr-2"></i>下载文件
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { getFilePreviewTokenApi, getFileContentApi } from '@/api'
import { getFileExtension } from '@/utils/helpers'
import ImagePreview from '@/components/preview/ImagePreview.vue'
import AudioPreview from '@/components/preview/AudioPreview.vue'
import CodePreview from '@/components/preview/CodePreview.vue'
import TextPreview from '@/components/preview/TextPreview.vue'

const router = useRouter()

const props = defineProps({
  visible: Boolean,
  node: Object,
})
const emit = defineEmits(['close', 'download'])

const fileUrl = ref('')
const textContent = ref('')
const loading = ref(false)

// 文件扩展名
const ext = computed(() => props.node?.node_name?.split('.').pop()?.toLowerCase() || '')

// 文件类型检测
const isImage = computed(() => ['jpg','jpeg','png','gif','webp','bmp','svg'].includes(ext.value))
const isPdf = computed(() => ext.value === 'pdf')
const isVideo = computed(() => ['mp4','webm','ogg','mov','avi','mkv'].includes(ext.value))
const isAudio = computed(() => ['mp3','wav','ogg','flac','m4a'].includes(ext.value))
const isOffice = computed(() => ['doc','docx','xls','xlsx','ppt','pptx','pptm'].includes(ext.value))
const isWord = computed(() => ['doc','docx'].includes(ext.value))
const isExcel = computed(() => ['xls','xlsx'].includes(ext.value))
const isPPT = computed(() => ['ppt','pptx','pptm'].includes(ext.value))
const isCode = computed(() => ['js','ts','html','css','json','xml','py','java','cpp','c','cs','go','rb','php','sql','sh'].includes(ext.value))
const isText = computed(() => ['txt','md','log'].includes(ext.value))

/** Office 文档类型图标 */
const officeIcon = computed(() => {
  if (isWord.value) return 'fa fa-file-word-o'
  if (isExcel.value) return 'fa fa-file-excel-o'
  if (isPPT.value) return 'fa fa-file-powerpoint-o'
  return 'fa fa-file-o'
})

/** Office 文档类型标签 */
const officeLabel = computed(() => {
  if (isWord.value) return 'Word 文档'
  if (isExcel.value) return 'Excel 表格'
  if (isPPT.value) return 'PPT 演示文稿'
  return 'Office 文档'
})

// 文件类型图标
const fileTypeIcon = computed(() => {
  if (isImage.value) return 'fa fa-image'
  if (isVideo.value) return 'fa fa-film'
  if (isAudio.value) return 'fa fa-music'
  if (isPdf.value) return 'fa fa-file-pdf-o'
  if (isOffice.value) return 'fa fa-file-word-o'
  if (isCode.value) return 'fa fa-code'
  if (isText.value) return 'fa fa-file-text-o'
  return 'fa fa-file'
})

// 跳转至专属流媒体播放页面，携带 fileId 参数
const openVideoPlayer = () => {
  if (!props.node?.node_id) return
  router.push({
    name: 'VideoPlayer',
    params: { fileId: props.node.node_id },
    query: { name: encodeURIComponent(props.node?.node_name || '') }
  })
}

/** 跳转至 PDF 独立预览页面 */
const openPdfPreview = () => {
  if (!props.node?.node_id) return
  router.push({
    name: 'PDFPreview',
    params: { fileId: props.node.node_id },
    query: { name: encodeURIComponent(props.node?.node_name || '') }
  })
}

/** 跳转至 Office 独立预览页面（根据文件类型路由到不同页面） */
const openOfficePreview = () => {
  if (!props.node?.node_id) return
  const routeName = isWord.value ? 'WordPreview'
    : isExcel.value ? 'ExcelPreview'
    : isPPT.value ? 'PPTPreview'
    : 'PDFPreview' // 兜底
  router.push({
    name: routeName,
    params: { fileId: props.node.node_id },
    query: { name: encodeURIComponent(props.node?.node_name || '') }
  })
}

/** 自动跳转至 Office 独立预览页面 */
const autoOpenOfficePreview = () => {
  if (!props.node?.node_id) return
  if (isPdf.value) {
    openPdfPreview()
  } else if (isOffice.value) {
    openOfficePreview()
  }
}

// 加载预览
const loadPreview = async () => {
  if (!props.node || props.node.node_type === 'FOLDER') return

  loading.value = true
  try {
    // 视频文件：已在模板中跳转至专属播放器，无需加载预览
    if (isVideo.value) {
      return
    }

    // PDF 和 Office 文件：自动跳转至独立预览页面
    if (isPdf.value || isOffice.value) {
      autoOpenOfficePreview()
      return
    }

    if (isImage.value || isAudio.value) {
      // 媒体文件：获取预览URL
      const res = await getFilePreviewTokenApi(props.node.node_id)
      if (res.code === 200 && res.data?.url) {
        fileUrl.value = res.data.url
      }
    } else if (isCode.value || isText.value) {
      // 文本和代码：获取文本内容
      const res = await getFileContentApi(props.node.node_id)
      if (res.code === 200 && res.data?.content) {
        textContent.value = res.data.content
      }
    }
  } catch (err) {
    console.error('加载预览失败:', err)
  } finally {
    loading.value = false
  }
}

// 下载文件
const handleDownload = () => emit('download', props.node)

// 监听文件变化
watch(() => props.node, (node) => {
  if (node && props.visible && node.node_type !== 'FOLDER') {
    loadPreview()
  }
}, { immediate: true })

watch(() => props.visible, (visible) => {
  if (visible && props.node) {
    loadPreview()
  }
})
</script>
