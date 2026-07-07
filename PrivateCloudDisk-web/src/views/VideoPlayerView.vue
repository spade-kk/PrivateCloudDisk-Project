<template>
  <div class="video-player-view">
    <!-- ============================================================
         加载状态
         ============================================================ -->
    <div v-if="store.loading" class="player-loading-overlay">
      <div class="loading-content">
        <div class="loading-spinner-ring">
          <div class="ring"></div>
        </div>
        <p class="loading-text">正在加载视频...</p>
        <p class="loading-subtext">{{ store.currentFile?.node_name || '' }}</p>
      </div>
    </div>

    <!-- ============================================================
         错误状态
         ============================================================ -->
    <div v-else-if="store.error" class="player-error-overlay">
      <div class="error-content">
        <div class="error-icon">
          <i class="fa fa-exclamation-triangle"></i>
        </div>
        <h2 class="error-title">{{ store.error.title || '播放错误' }}</h2>
        <p class="error-message">{{ store.error.message }}</p>
        <div class="error-actions">
          <button @click="handleRetry" class="error-btn error-btn-primary">
            <i class="fa fa-refresh"></i> 重新加载
          </button>
          <button @click="handleGoBack" class="error-btn error-btn-secondary">
            <i class="fa fa-arrow-left"></i> 返回
          </button>
        </div>
      </div>
    </div>

    <!-- ============================================================
         视频播放器主体 - 企业级核心组件
         ============================================================ -->
    <div v-else class="player-wrapper" :class="{ 'is-fullscreen': store.isFullscreen }">
      <VideoPlayerCore
        ref="playerRef"
        :stream-info="store.streamInfo"
        :sprite-info="store.spriteInfo"
        :subtitles="store.subtitles"
        :active-subtitle="store.activeSubtitle"
        :stream-token="store.streamToken"
        :initial-resolution="store.currentResolution"
        :initial-playback-rate="store.playbackRate"
        :initial-volume="store.volume"
        :saved-progress="store.savedProgress?.current_time || 0"
        :is-hls="store.isHls"
        :hls-source-url="store.isHls ? store.videoSourceUrl : ''"
        :video-source-url="store.isMp4 ? store.videoSourceUrl : ''"
        :poster-url="store.previewThumbnailUrl"
        :file-id="store.currentFile?.node_id"
        @timeupdate="onTimeUpdate"
        @progress-report="onProgressReport"
        @resolution-change="onResolutionChange"
        @speed-change="onSpeedChange"
        @volume-change="onVolumeChange"
        @fullscreen-change="onFullscreenChange"
        @error="onPlayerError"
        @retry="handleRetry"
      />

      <!-- 左上角返回按钮 -->
      <button
        v-if="showTopBar"
        class="top-left-back-btn"
        @click="handleGoBack"
        title="返回"
      >
        <i class="fa fa-arrow-left"></i>
      </button>

      <!-- 视频标题 -->
      <div v-if="showTopBar" class="top-center-title">
        <span class="title-text">{{ store.currentFile?.node_name || '视频播放' }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVideoPlayerStore } from '@/stores/videoPlayerStore'
import VideoPlayerCore from '@/components/video/VideoPlayerCore.vue'

// ============================================================
// 路由 & Store
// ============================================================
const route = useRoute()
const router = useRouter()
const store = useVideoPlayerStore()

// ============================================================
// Refs
// ============================================================
const playerRef = ref<InstanceType<typeof VideoPlayerCore> | null>(null)
const showTopBar = ref(true)

// ============================================================
// 视频初始化
// ============================================================
async function initVideo() {
  const fileId = route.params.fileId as string
  if (!fileId) {
    store.error = { title: '参数错误', message: '缺少文件ID参数' }
    return
  }

  const fileInfo = {
    node_id: fileId,
    node_name: decodeURIComponent((route.query.name as string) || '视频播放'),
    file_size: parseInt((route.query.size as string) || '0'),
    node_type: 'FILE'
  }

  await store.loadVideo(fileInfo)
}

// ============================================================
// 事件处理
// ============================================================
function onTimeUpdate({ currentTime, duration }: { currentTime: number; duration: number }) {
  // 定期保存进度
  if (Math.floor(currentTime) % 5 === 0 && Math.floor(currentTime) > 0) {
    store.saveProgress()
  }
}

function onProgressReport({ currentTime, duration }: { currentTime: number; duration: number }) {
  store.saveProgress()
}

function onResolutionChange(resolution: string) {
  store.setResolution(resolution)
}

function onSpeedChange(rate: number) {
  store.setPlaybackRate(rate)
}

function onVolumeChange(vol: number) {
  store.setVolume(vol)
}

function onFullscreenChange(isFs: boolean) {
  store.isFullscreen = isFs
}

function onPlayerError(msg: string) {
  store.error = { title: '播放错误', message: msg }
}

// ============================================================
// 导航
// ============================================================
function handleGoBack() {
  store.reset()
  router.back()
}

function handleRetry() {
  store.error = null
  initVideo()
}

// ============================================================
// 键盘快捷键
// ============================================================
function handleKeydown(e: KeyboardEvent) {
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  if (e.key === 'Escape' && document.fullscreenElement) {
    document.exitFullscreen().catch(() => {})
  }
}

// ============================================================
// 全屏变化监听
// ============================================================
function onFullscreenChangeNative() {
  store.isFullscreen = !!document.fullscreenElement
}

// ============================================================
// 生命周期
// ============================================================
onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('fullscreenchange', onFullscreenChangeNative)
  document.addEventListener('webkitfullscreenchange', onFullscreenChangeNative)
  await initVideo()
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('fullscreenchange', onFullscreenChangeNative)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChangeNative)
  store.reset()
})
</script>

<style scoped>
/* ============================================================
   基础布局
   ============================================================ */
.video-player-view {
  position: relative;
  width: 100%;
  height: calc(100vh - 140px);
  min-height: 400px;
  background: #000;
  border-radius: 12px;
  overflow: hidden;
  user-select: none;
  -webkit-user-select: none;
}

.player-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
}

.player-wrapper.is-fullscreen {
  position: fixed;
  inset: 0;
  z-index: 9999;
  width: 100vw;
  height: 100vh;
  border-radius: 0;
}

/* ============================================================
   加载状态
   ============================================================ */
.player-loading-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.92);
  z-index: 100;
}

.loading-content {
  text-align: center;
  color: #fff;
}

.loading-spinner-ring {
  margin-bottom: 20px;
}

.ring {
  width: 48px;
  height: 48px;
  border: 3px solid rgba(255, 255, 255, 0.15);
  border-top-color: #1677ff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

.loading-text {
  font-size: 15px;
  font-weight: 500;
  margin: 0 0 6px;
  color: rgba(255, 255, 255, 0.9);
}

.loading-subtext {
  font-size: 13px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
  max-width: 320px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ============================================================
   错误状态
   ============================================================ */
.player-error-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.92);
  z-index: 100;
}

.error-content {
  text-align: center;
  color: #fff;
  max-width: 420px;
  padding: 40px;
}

.error-icon {
  font-size: 48px;
  color: #f56c6c;
  margin-bottom: 20px;
  opacity: 0.9;
}

.error-title {
  font-size: 20px;
  font-weight: 600;
  margin: 0 0 10px;
  color: #fff;
}

.error-message {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.6);
  margin: 0 0 28px;
  line-height: 1.5;
}

.error-actions {
  display: flex;
  gap: 12px;
  justify-content: center;
}

.error-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 22px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  border: none;
  cursor: pointer;
  transition: all 0.2s;
}

.error-btn-primary {
  background: #1677ff;
  color: #fff;
}

.error-btn-primary:hover {
  background: #4096ff;
}

.error-btn-secondary {
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(255, 255, 255, 0.15);
}

.error-btn-secondary:hover {
  background: rgba(255, 255, 255, 0.18);
}

/* ============================================================
   顶部按钮
   ============================================================ */
.top-left-back-btn {
  position: absolute;
  top: 16px;
  left: 16px;
  z-index: 20;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(0, 0, 0, 0.5);
  border: none;
  color: #fff;
  font-size: 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}

.top-left-back-btn:hover {
  background: rgba(0, 0, 0, 0.7);
}

.top-center-title {
  position: absolute;
  top: 16px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 20;
  transition: opacity 0.3s;
}

.title-text {
  font-size: 15px;
  font-weight: 600;
  color: #fff;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.6);
  padding: 6px 16px;
  background: rgba(0, 0, 0, 0.4);
  border-radius: 20px;
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
</style>