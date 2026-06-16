<template>
  <div class="video-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载视频...</p>
    </div>

    <!-- 视频预览 -->
    <div v-else-if="videoUrl" class="preview-content">
      <!-- 工具栏 -->
      <div class="preview-toolbar">
        <div class="toolbar-left">
          <span class="file-name truncate">{{ fileName }}</span>
        </div>
        <div class="toolbar-right">
          <button @click="openFullPlayer" class="tool-btn tool-btn-primary" title="进入专属播放器">
            <i class="fa fa-play-circle"></i>
            <span class="btn-text">播放器</span>
          </button>
          <button @click="toggleFullscreen" class="tool-btn" title="全屏">
            <i :class="isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
          </button>
          <button @click="downloadVideo" class="tool-btn" title="下载">
            <i class="fa fa-download"></i>
          </button>
        </div>
      </div>

      <!-- 视频容器 -->
      <div class="video-wrapper" ref="videoWrapper">
        <video
          ref="videoRef"
          :src="videoUrl"
          @loadedmetadata="onMetadataLoaded"
          @timeupdate="onTimeUpdate"
          @progress="onProgress"
          @error="onVideoError"
          @ended="onEnded"
          controls
          class="video-player"
        >
          您的浏览器不支持视频播放
        </video>
      </div>

      <!-- 视频信息 -->
      <div class="video-info-bar">
        <div class="info-item">
          <i class="fa fa-film"></i>
          <span>{{ fileExtension.toUpperCase() }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-clock-o"></i>
          <span>{{ formatDuration(duration) }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-expand"></i>
          <span>{{ videoWidth }} × {{ videoHeight }}</span>
        </div>
        <div class="info-item">
          <i class="fa fa-file"></i>
          <span>{{ fileSize }}</span>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>视频加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'

const props = defineProps({
  fileUrl: {
    type: String,
    required: true
  },
  fileName: {
    type: String,
    default: ''
  },
  fileSize: {
    type: String,
    default: ''
  },
  fileExtension: {
    type: String,
    default: ''
  },
  loading: {
    type: Boolean,
    default: false
  },
  fileId: {
    type: [String, Number],
    default: ''
  }
})

const emit = defineEmits(['retry', 'loaded', 'error', 'ended'])

const router = useRouter()
const videoRef = ref(null)
const videoWrapper = ref(null)

// 状态
const duration = ref(0)
const videoWidth = ref(0)
const videoHeight = ref(0)
const isFullscreen = ref(false)
const errorMessage = ref('')

// 计算视频URL
const videoUrl = computed(() => {
  if (!props.fileUrl) return ''
  if (props.fileUrl.startsWith('http://') || props.fileUrl.startsWith('https://')) {
    return props.fileUrl
  }
  if (props.fileUrl.startsWith('/')) {
    return import.meta.env.VITE_API_BASE_URL + props.fileUrl
  }
  return props.fileUrl
})

// 方法
const onMetadataLoaded = (event) => {
  const video = event.target
  duration.value = video.duration
  videoWidth.value = video.videoWidth
  videoHeight.value = video.videoHeight
  emit('loaded', {
    duration: video.duration,
    width: video.videoWidth,
    height: video.videoHeight
  })
}

const onTimeUpdate = (event) => {
  // 实时更新播放进度
}

const onProgress = (event) => {
  // 缓冲进度
}

const onVideoError = (error) => {
  errorMessage.value = '无法加载视频，文件可能已损坏或格式不支持'
  emit('error', error)
}

const onEnded = () => {
  emit('ended')
}

const toggleFullscreen = async () => {
  if (!document.fullscreenElement) {
    await videoWrapper.value?.requestFullscreen()
    isFullscreen.value = true
  } else {
    await document.exitFullscreen()
    isFullscreen.value = false
  }
}

const downloadVideo = () => {
  const link = document.createElement('a')
  link.href = videoUrl.value
  link.download = props.fileName
  link.click()
}

const openFullPlayer = () => {
  if (!props.fileId) return
  const query = {
    name: encodeURIComponent(props.fileName),
    size: props.fileSize || '0'
  }
  router.push({ name: 'VideoPlayer', params: { fileId: props.fileId }, query })
}

const formatDuration = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const hrs = Math.floor(seconds / 3600)
  const mins = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)

  if (hrs > 0) {
    return `${hrs}:${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
  }
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

// 键盘快捷键
const handleKeydown = (e) => {
  if (!videoRef.value) return

  switch (e.key) {
    case ' ':
    case 'k':
      e.preventDefault()
      if (videoRef.value.paused) {
        videoRef.value.play()
      } else {
        videoRef.value.pause()
      }
      break
    case 'ArrowLeft':
      e.preventDefault()
      videoRef.value.currentTime -= 10
      break
    case 'ArrowRight':
      e.preventDefault()
      videoRef.value.currentTime += 10
      break
    case 'ArrowUp':
      e.preventDefault()
      videoRef.value.volume = Math.min(videoRef.value.volume + 0.1, 1)
      break
    case 'ArrowDown':
      e.preventDefault()
      videoRef.value.volume = Math.max(videoRef.value.volume - 0.1, 0)
      break
    case 'f':
      toggleFullscreen()
      break
    case 'm':
      videoRef.value.muted = !videoRef.value.muted
      break
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
})
</script>

<style scoped>
.video-preview-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #6c8ef5;
}

.loading-spinner {
  margin-bottom: 1rem;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.9;
}

.preview-content {
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.preview-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.75rem 1rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  min-width: 0;
  flex: 1;
}

.file-name {
  font-weight: 600;
  color: #182848;
  font-size: 0.95rem;
}

.toolbar-right {
  display: flex;
  gap: 0.5rem;
  flex-shrink: 0;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.5rem;
  height: 2.5rem;
  border: none;
  background: #f1f5f9;
  border-radius: 0.5rem;
  cursor: pointer;
  transition: all 0.2s;
  color: #4b6cb7;
  font-size: 0.9rem;
}

.tool-btn:hover {
  background: #4b6cb7;
  color: white;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(75, 108, 183, 0.3);
}

.tool-btn-primary {
  background: linear-gradient(135deg, #165dff, #4080ff);
  color: white;
  width: auto;
  padding: 0 14px;
  gap: 6px;
  font-size: 0.85rem;
  font-weight: 600;
}

.tool-btn-primary:hover {
  background: linear-gradient(135deg, #4080ff, #5c9dff);
  color: white;
  box-shadow: 0 4px 16px rgba(22, 93, 255, 0.4);
}

.btn-text {
  font-size: 0.82rem;
}

.video-wrapper {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: #000;
  position: relative;
}

.video-player {
  max-width: 100%;
  max-height: 100%;
  width: 100%;
  height: auto;
  background: #000;
}

.video-info-bar {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding: 0.75rem 1rem;
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-top: 1px solid #e2e8f0;
}

.info-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #64748b;
}

.info-item i {
  color: #4b6cb7;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #fbbf24;
  text-align: center;
  padding: 2rem;
}

.preview-error h3 {
  margin: 1rem 0 0.5rem;
  color: white;
}

.preview-error p {
  color: #94a3b8;
  margin-bottom: 1.5rem;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: linear-gradient(90deg, #4b6cb7 0%, #182848 100%);
  color: white;
  border: none;
  padding: 0.75rem 1.5rem;
  border-radius: 2rem;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s;
}

.retry-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 20px rgba(75, 108, 183, 0.4);
}
</style>
