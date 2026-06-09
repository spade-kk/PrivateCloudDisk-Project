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
            <!-- 图片预览 -->
            <ImagePreview
              v-if="isImage"
              :file-url="fileUrl"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- 视频预览 -->
            <VideoPreview
              v-else-if="isVideo"
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

            <!-- PDF预览 -->
            <PdfPreview
              v-else-if="isPdf"
              :file-url="fileUrl"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

            <!-- Office文档预览 -->
            <OfficePreview
              v-else-if="isOffice"
              :file-url="fileUrl"
              :file-name="node?.node_name || ''"
              :loading="loading"
            />

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

<script setup>
import { ref, computed, watch } from 'vue'
import { getFilePreviewTokenApi, getFileContentApi } from '@/api/modules/preview'
import { getFileExtension } from '@/utils/helpers'
import ImagePreview from '@/components/preview/ImagePreview.vue'
import VideoPreview from '@/components/preview/VideoPreview.vue'
import AudioPreview from '@/components/preview/AudioPreview.vue'
import PdfPreview from '@/components/preview/PdfPreview.vue'
import OfficePreview from '@/components/preview/OfficePreview.vue'
import CodePreview from '@/components/preview/CodePreview.vue'
import TextPreview from '@/components/preview/TextPreview.vue'

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
const isCode = computed(() => ['js','ts','html','css','json','xml','py','java','cpp','c','cs','go','rb','php','sql','sh'].includes(ext.value))
const isText = computed(() => ['txt','md','log'].includes(ext.value))

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

// 加载预览
const loadPreview = async () => {
  if (!props.node || props.node.node_type === 'FOLDER') return

  loading.value = true
  try {
    if (isImage.value || isVideo.value || isAudio.value || isPdf.value || isOffice.value) {
      // 媒体文件和PDF：获取预览URL
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
