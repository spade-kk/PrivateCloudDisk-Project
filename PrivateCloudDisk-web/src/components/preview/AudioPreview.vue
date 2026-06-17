<template>
  <div class="audio-preview-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="preview-loading">
      <div class="loading-spinner">
        <i class="fa fa-spinner fa-spin fa-3x"></i>
      </div>
      <p class="loading-text">正在加载音频...</p>
    </div>

    <!-- 音频预览 -->
    <div v-else-if="audioUrl" class="preview-content">
      <!-- 音频卡片 -->
      <div class="audio-card">
        <div class="audio-icon">
          <i class="fa fa-music"></i>
        </div>

        <div class="audio-info">
          <h3 class="audio-title">{{ fileName }}</h3>
          <p class="audio-meta">
            <span class="audio-format">{{ fileExtension.toUpperCase() }}</span>
            <span class="audio-duration">{{ formatDuration(duration) }}</span>
          </p>
        </div>

        <!-- 音频可视化（可选） -->
        <div class="audio-visualization">
          <div class="visual-bars">
            <div v-for="i in 20" :key="i" class="visual-bar" :style="getBarStyle(i)"></div>
          </div>
        </div>

        <!-- 音频播放器 -->
        <div class="audio-player-wrapper">
          <audio
            ref="audioRef"
            :src="audioUrl"
            @loadedmetadata="onMetadataLoaded"
            @timeupdate="onTimeUpdate"
            @ended="onEnded"
            @error="onAudioError"
            class="audio-player"
          >
            您的浏览器不支持音频播放
          </audio>

          <!-- 进度条 -->
          <div class="progress-section">
            <span class="time-current">{{ formatTime(currentTime) }}</span>
            <div class="progress-bar-container" @click="seekAudio">
              <div class="progress-bar">
                <div class="progress-fill" :style="{ width: progressPercent + '%' }"></div>
              </div>
            </div>
            <span class="time-total">{{ formatTime(duration) }}</span>
          </div>

          <!-- 控制按钮 -->
          <div class="controls">
            <button @click="togglePlay" class="play-btn">
              <i :class="isPlaying ? 'fa fa-pause' : 'fa fa-play'"></i>
            </button>

            <div class="volume-control">
              <button @click="toggleMute" class="volume-btn">
                <i :class="volumeIcon"></i>
              </button>
              <input
                type="range"
                min="0"
                max="1"
                step="0.01"
                v-model="volume"
                @input="updateVolume"
                class="volume-slider"
              />
            </div>
          </div>
        </div>

        <!-- 音频信息 -->
        <div class="audio-details">
          <div class="detail-item">
            <i class="fa fa-file"></i>
            <span>{{ fileSize }}</span>
          </div>
          <div class="detail-item">
            <i class="fa fa-clock-o"></i>
            <span>时长: {{ formatDuration(duration) }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 错误状态 -->
    <div v-else class="preview-error">
      <i class="fa fa-exclamation-triangle fa-4x"></i>
      <h3>音频加载失败</h3>
      <p>{{ errorMessage }}</p>
      <button @click="$emit('retry')" class="retry-btn">
        <i class="fa fa-refresh"></i> 重新加载
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

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
  }
})

const emit = defineEmits(['retry', 'loaded', 'error', 'ended'])

const audioRef = ref(null)

// 状态
const duration = ref(0)
const currentTime = ref(0)
const volume = ref(0.8)
const isPlaying = ref(false)
const errorMessage = ref('')

// 计算属性
const progressPercent = computed(() => {
  if (!duration.value) return 0
  return (currentTime.value / duration.value) * 100
})

const volumeIcon = computed(() => {
  if (volume.value === 0) return 'fa fa-volume-off'
  if (volume.value < 0.5) return 'fa fa-volume-down'
  return 'fa fa-volume-up'
})

// 计算音频URL
const audioUrl = computed(() => {
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
  const audio = event.target
  duration.value = audio.duration
  audio.volume = volume.value
  emit('loaded', {
    duration: audio.duration
  })
}

const onTimeUpdate = (event) => {
  currentTime.value = event.target.currentTime
}

const onEnded = () => {
  isPlaying.value = false
  currentTime.value = 0
  emit('ended')
}

const onAudioError = (error) => {
  errorMessage.value = '无法加载音频，文件可能已损坏或格式不支持'
  emit('error', error)
}

const togglePlay = () => {
  if (!audioRef.value) return

  if (isPlaying.value) {
    audioRef.value.pause()
  } else {
    audioRef.value.play()
  }
  isPlaying.value = !isPlaying.value
}

const seekAudio = (event) => {
  if (!audioRef.value || !duration.value) return

  const rect = event.currentTarget.getBoundingClientRect()
  const percent = (event.clientX - rect.left) / rect.width
  audioRef.value.currentTime = percent * duration.value
}

const updateVolume = () => {
  if (audioRef.value) {
    audioRef.value.volume = volume.value
  }
}

const toggleMute = () => {
  if (!audioRef.value) return

  if (volume.value > 0) {
    audioRef.value.volume = 0
    volume.value = 0
  } else {
    volume.value = 0.8
    audioRef.value.volume = 0.8
  }
}

const formatTime = (seconds) => {
  if (!seconds || isNaN(seconds)) return '00:00'
  const mins = Math.floor(seconds / 60)
  const secs = Math.floor(seconds % 60)
  return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`
}

const formatDuration = (seconds) => {
  return formatTime(seconds)
}

// 获取可视化条样式
const getBarStyle = (index) => {
  if (!isPlaying.value) {
    return { height: '20%', animation: 'none' }
  }
  const height = Math.random() * 60 + 20
  return {
    height: `${height}%`,
    animationDelay: `${index * 0.05}s`
  }
}

// 键盘快捷键
const handleKeydown = (e) => {
  if (!audioRef.value) return

  switch (e.key) {
    case ' ':
    case 'k':
      e.preventDefault()
      togglePlay()
      break
    case 'ArrowLeft':
      e.preventDefault()
      audioRef.value.currentTime -= 5
      break
    case 'ArrowRight':
      e.preventDefault()
      audioRef.value.currentTime += 5
      break
    case 'ArrowUp':
      e.preventDefault()
      volume.value = Math.min(volume.value + 0.1, 1)
      updateVolume()
      break
    case 'ArrowDown':
      e.preventDefault()
      volume.value = Math.max(volume.value - 0.1, 0)
      updateVolume()
      break
    case 'm':
      toggleMute()
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
.audio-preview-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 2rem;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: white;
}

.loading-spinner {
  margin-bottom: 1rem;
}

.loading-text {
  font-size: 0.95rem;
  opacity: 0.9;
}

.preview-content {
  width: 100%;
  max-width: 600px;
}

.audio-card {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(10px);
  border-radius: 1.5rem;
  padding: 2rem;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
}

.audio-icon {
  width: 80px;
  height: 80px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 1.5rem;
  font-size: 2.5rem;
  color: white;
}

.audio-info {
  text-align: center;
  margin-bottom: 1.5rem;
}

.audio-title {
  font-size: 1.25rem;
  font-weight: 700;
  color: #182848;
  margin-bottom: 0.5rem;
}

.audio-meta {
  display: flex;
  justify-content: center;
  gap: 1rem;
  font-size: 0.9rem;
  color: #64748b;
}

.audio-format {
  background: #f1f5f9;
  padding: 0.25rem 0.75rem;
  border-radius: 1rem;
  font-weight: 600;
}

.audio-visualization {
  margin-bottom: 1.5rem;
  padding: 1rem 0;
}

.visual-bars {
  display: flex;
  justify-content: center;
  align-items: flex-end;
  gap: 4px;
  height: 60px;
}

.visual-bar {
  width: 4px;
  background: linear-gradient(to top, #667eea, #764ba2);
  border-radius: 2px;
  animation: visualize 0.5s ease-in-out infinite alternate;
}

@keyframes visualize {
  from {
    transform: scaleY(0.3);
  }
  to {
    transform: scaleY(1);
  }
}

.audio-player-wrapper {
  margin-bottom: 1.5rem;
}

.progress-section {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.time-current,
.time-total {
  font-size: 0.85rem;
  color: #64748b;
  font-weight: 600;
  min-width: 45px;
}

.progress-bar-container {
  flex: 1;
  cursor: pointer;
  padding: 0.5rem 0;
}

.progress-bar {
  width: 100%;
  height: 6px;
  background: #e2e8f0;
  border-radius: 3px;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #667eea 0%, #764ba2 100%);
  border-radius: 3px;
  transition: width 0.1s linear;
}

.controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 2rem;
}

.play-btn {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  border: none;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: white;
  font-size: 1.5rem;
  cursor: pointer;
  transition: all 0.3s;
  box-shadow: 0 8px 20px rgba(102, 126, 234, 0.4);
}

.play-btn:hover {
  transform: scale(1.1);
  box-shadow: 0 12px 30px rgba(102, 126, 234, 0.5);
}

.volume-control {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}

.volume-btn {
  background: none;
  border: none;
  color: #4b6cb7;
  font-size: 1.2rem;
  cursor: pointer;
  padding: 0.5rem;
}

.volume-slider {
  width: 80px;
  height: 6px;
  border-radius: 3px;
  background: #e2e8f0;
  outline: none;
  -webkit-appearance: none;
}

.volume-slider::-webkit-slider-thumb {
  -webkit-appearance: none;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  cursor: pointer;
}

.audio-details {
  display: flex;
  justify-content: center;
  gap: 2rem;
  padding-top: 1rem;
  border-top: 1px solid #e2e8f0;
}

.detail-item {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.85rem;
  color: #64748b;
}

.detail-item i {
  color: #667eea;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: white;
  text-align: center;
  padding: 2rem;
}

.preview-error h3 {
  margin: 1rem 0 0.5rem;
}

.preview-error p {
  opacity: 0.9;
  margin-bottom: 1.5rem;
}

.retry-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  background: white;
  color: #667eea;
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
  box-shadow: 0 6px 20px rgba(255, 255, 255, 0.3);
}
</style>
