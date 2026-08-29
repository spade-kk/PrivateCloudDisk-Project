<template>
  <div class="thumbnail-image-wrapper" :class="sizeClass">
    <!-- AUDIT FIX [7.1-7.20/8.14-8.18]：请求失败、空响应或图片解码失败均显示统一类型图标。 -->
    <template v-if="!loaded">
      <FileTypeIcon
        :file-name="fileName"
        :style="{ fontSize: iconSize }"
        class="thumbnail-placeholder"
        :title="`${fileName} 缩略图不可用，显示文件类型图标`"
      />
      <span v-if="loading" class="thumbnail-spinner"></span>
    </template>

    <!-- 加载成功：显示缩略图 -->
    <img
      v-show="loaded && objectUrl"
      :src="objectUrl || undefined"
      :alt="alt"
      loading="lazy"
      decoding="async"
      class="thumbnail-img"
      :style="{ objectFit: fit }"
      @error="handleImageError"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import { loadDocumentThumbnail, loadThumbnail, loadVideoThumbnail, type ThumbnailSize } from '@/utils/imageCache'
import { isOffice, isPdf, isVideo } from '@/utils/previewHelper'
import { imageCache } from '@/utils/imageCache'
import FileTypeIcon from './FileTypeIcon.vue'

const props = withDefaults(
  defineProps<{
    fileId: string
    fileName: string
    size?: ThumbnailSize
    iconSize?: string
    fit?: 'cover' | 'contain'
  }>(),
  {
    size: 'small',
    iconSize: '1.5rem',
    fit: 'cover',
  },
)

const emit = defineEmits<{
  load: []
  error: []
}>()

const loading = ref(false)
const loaded = ref(false)
const objectUrl = ref<string | null>(null)
let loadSequence = 0

const alt = computed(() => props.fileName || '图片缩略图')
const sizeClass = computed(() => `thumbnail-${props.size}`)

function releaseObjectUrl() {
  if (!objectUrl.value) return
  imageCache.evictUrl(objectUrl.value)
  objectUrl.value = null
}

async function loadImage() {
  const sequence = ++loadSequence
  releaseObjectUrl()
  loaded.value = false
  if (!props.fileId) return

  loading.value = true

  try {
    // 视频文件使用独立的视频缩略图接口（ffmpeg 首帧）
    const isVideoFile = isVideo(props.fileName)
    const isDocumentFile = isPdf(props.fileName) || isOffice(props.fileName)
    const url = isVideoFile
      ? await loadVideoThumbnail(props.fileId, props.size)
      : isDocumentFile
        ? await loadDocumentThumbnail(props.fileId, props.size)
        : await loadThumbnail(props.fileId, props.size)
    if (sequence !== loadSequence || !url) throw new Error('thumbnail response is empty')
    objectUrl.value = url
    loaded.value = true
    emit('load')
  } catch {
    if (sequence !== loadSequence) return
    loaded.value = false
    emit('error')
  } finally {
    if (sequence === loadSequence) loading.value = false
  }
}

function handleImageError() {
  // 浏览器可能在 HTTP 200 空响应、错误 MIME 或 Blob 解码失败时才触发这里。
  loaded.value = false
  loading.value = false
  releaseObjectUrl()
  emit('error')
}

// fileId 变化时重新加载
watch(
  () => [props.fileId, props.fileName, props.size],
  () => {
    loadImage()
  },
)

onMounted(() => {
  loadImage()
})

onBeforeUnmount(() => {
  ++loadSequence
  releaseObjectUrl()
})
</script>

<style scoped>
.thumbnail-image-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
}

.thumbnail-placeholder {
  flex-shrink: 0;
}

.thumbnail-spinner {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
}

.thumbnail-spinner::after {
  content: '';
  width: 16px;
  height: 16px;
  border: 2px solid rgba(0, 0, 0, 0.15);
  border-top-color: rgba(59, 130, 246, 0.6);
  border-radius: 50%;
  animation: thumb-spin 0.6s linear infinite;
}

@keyframes thumb-spin {
  to {
    transform: rotate(360deg);
  }
}

.thumbnail-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 4px;
  image-rendering: auto;
}

.thumbnail-small .thumbnail-img {
  max-width: 48px;
  max-height: 48px;
}

.thumbnail-medium .thumbnail-img {
  max-width: 120px;
  max-height: 120px;
}

.thumbnail-large .thumbnail-img {
  max-width: 100%;
  max-height: 100%;
}
</style>
