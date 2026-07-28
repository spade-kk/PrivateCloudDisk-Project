<template>
  <div class="thumbnail-image-wrapper" :class="sizeClass">
    <!-- 加载中：显示字体图标占位 + 加载指示器 -->
    <template v-if="!loaded">
      <i
        :class="['fa', iconClass, iconColorClass]"
        class="thumbnail-placeholder"
        :style="{ fontSize: iconSize }"
      ></i>
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
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { loadDocumentThumbnail, loadThumbnail, loadVideoThumbnail, type ThumbnailSize } from '@/utils/imageCache'
import { getFileIconClass } from '@/utils/fileIcon'
import { isOffice, isPdf, isVideo } from '@/utils/previewHelper'

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

const alt = computed(() => props.fileName || '图片缩略图')
const sizeClass = computed(() => `thumbnail-${props.size}`)

const iconClass = computed(() => {
  const cls = getFileIconClass(props.fileName)
  return cls ? cls[0] : 'fa-file-image-o'
})

const iconColorClass = computed(() => {
  const cls = getFileIconClass(props.fileName)
  return cls ? cls[1] : 'text-purple-500'
})

async function loadImage() {
  if (!props.fileId) return

  loading.value = true
  loaded.value = false

  try {
    // 视频文件使用独立的视频缩略图接口（ffmpeg 首帧）
    const isVideoFile = isVideo(props.fileName)
    const isDocumentFile = isPdf(props.fileName) || isOffice(props.fileName)
    const url = isVideoFile
      ? await loadVideoThumbnail(props.fileId, props.size)
      : isDocumentFile
        ? await loadDocumentThumbnail(props.fileId, props.size)
        : await loadThumbnail(props.fileId, props.size)
    objectUrl.value = url
    loaded.value = true
    emit('load')
  } catch {
    loaded.value = false
    emit('error')
  } finally {
    loading.value = false
  }
}

// fileId 变化时重新加载
watch(
  () => props.fileId,
  () => {
    loadImage()
  },
)

onMounted(() => {
  loadImage()
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
