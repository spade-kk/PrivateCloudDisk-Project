<template>
  <div class="video-player-view" @mousemove="onPlayerMouseMove" @mouseleave="onPlayerMouseLeave">
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
         视频播放器主体
         ============================================================ -->
    <div v-else class="player-wrapper" ref="playerWrapperRef" :class="{ 'is-fullscreen': store.isFullscreen }">
      <!-- 视频元素 -->
      <video
        ref="videoRef"
        class="player-video"
        :src="store.isHls ? undefined : store.videoSourceUrl"
        :volume="store.volume"
        :muted="store.muted"
        :playbackRate="store.playbackRate"
        crossorigin="anonymous"
        playsinline
        webkit-playsinline
        x5-video-player-type="h5"
        x5-video-orientation="landscape"
        @loadedmetadata="store.onLoadedMetadata"
        @timeupdate="onTimeUpdateWithSave"
        @waiting="store.onWaiting"
        @canplay="store.onCanPlay"
        @playing="store.onPlaying"
        @pause="store.onPause"
        @ended="store.onEnded"
        @seeking="store.onSeeking"
        @seeked="store.onSeeked"
        @error="store.onError"
        @click="store.togglePlay"
        @dblclick="store.toggleFullscreen"
        @contextmenu.prevent
      >
        <!-- 字幕轨道 -->
        <track
          v-for="sub in activeSubtitlesList"
          :key="sub.id"
          :src="sub.url"
          :srclang="sub.id"
          :label="sub.label"
          :kind="'subtitles'"
          :default="sub.id === store.activeSubtitle"
        />
      </video>

      <!-- 中央大播放按钮（暂停时显示） -->
      <div
        v-if="store.paused && !store.seeking"
        class="center-play-btn"
        @click="store.togglePlay"
      >
        <div class="play-icon-circle">
          <i class="fa fa-play"></i>
        </div>
      </div>

      <!-- 缓冲指示器 -->
      <div v-if="store.buffering && store.playing" class="buffering-indicator">
        <div class="buffering-ring">
          <div class="ring ring-sm"></div>
        </div>
      </div>

      <!-- 左上角返回按钮 -->
      <button
        v-if="store.controlsVisible"
        class="top-left-back-btn"
        @click="handleGoBack"
        title="返回"
      >
        <i class="fa fa-arrow-left"></i>
      </button>

      <!-- 右上角视频标题 -->
      <div v-if="store.controlsVisible" class="top-center-title">
        <span class="title-text">{{ store.currentFile?.node_name || '视频播放' }}</span>
      </div>

      <!-- ============================================================
           底部控制栏
           ============================================================ -->
      <div
        class="player-controls"
        :class="{ 'controls-hidden': !store.controlsVisible && store.playing }"
      >
        <!-- 高级进度条 -->
        <div class="progress-bar-container" ref="progressBarRef">
          <!-- 鼠标悬停预览 -->
          <div
            v-if="hoverPreview.visible && store.spriteInfo"
            class="hover-preview-tooltip"
            :style="hoverPreviewTooltipStyle"
          >
            <div
              class="hover-preview-image"
              :style="hoverPreviewImageStyle"
            ></div>
            <span class="hover-preview-time">{{ formatTime(hoverPreview.time) }}</span>
          </div>

          <div
            class="progress-bar-track"
            @mousedown="onProgressMouseDown"
            @mousemove="onProgressMouseMove"
            @mouseleave="onProgressMouseLeave"
            ref="progressTrackRef"
          >
            <!-- 缓冲进度 -->
            <div class="progress-bar-buffered" :style="{ width: store.bufferedPercent + '%' }">
              <span
                v-for="(range, i) in store.bufferedRanges"
                :key="i"
                class="buffered-segment"
                :style="{
                  left: (range.start / store.duration * 100) + '%',
                  width: ((range.end - range.start) / store.duration * 100) + '%'
                }"
              ></span>
            </div>
            <!-- 播放进度 -->
            <div class="progress-bar-played" :style="{ width: store.progressPercent + '%' }"></div>
            <!-- 悬停预览指示 -->
            <div
              v-if="hoverPreview.visible"
              class="progress-bar-hover-indicator"
              :style="{ left: hoverPreview.percent + '%' }"
            ></div>
            <!-- 进度条滑块 -->
            <div
              class="progress-bar-thumb"
              :style="{ left: store.progressPercent + '%' }"
              :class="{ 'is-dragging': isDragging }"
            ></div>
            <!-- 点击区域 -->
            <input
              type="range"
              class="progress-bar-input"
              min="0"
              :max="store.duration || 100"
              :value="store.currentTime"
              step="0.1"
              @input="onProgressInput"
              @change="onProgressChange"
            />
          </div>
        </div>

        <!-- 控制按钮行 -->
        <div class="controls-row">
          <!-- 左侧控制 -->
          <div class="controls-left">
            <!-- 播放/暂停 -->
            <button
              class="control-btn"
              @click="store.togglePlay"
              :title="store.playing ? '暂停 (空格)' : '播放 (空格)'"
            >
              <i :class="store.playing ? 'fa fa-pause' : 'fa fa-play'"></i>
            </button>

            <!-- 快退10秒 -->
            <button
              class="control-btn control-btn-sm"
              @click="store.seekRelative(-10)"
              title="快退10秒 (←)"
            >
              <i class="fa fa-backward"></i>
            </button>

            <!-- 快进10秒 -->
            <button
              class="control-btn control-btn-sm"
              @click="store.seekRelative(10)"
              title="快进10秒 (→)"
            >
              <i class="fa fa-forward"></i>
            </button>

            <!-- 音量控制 -->
            <div class="volume-control-wrapper" @mouseenter="showVolumeSlider = true" @mouseleave="showVolumeSlider = false">
              <button
                class="control-btn"
                @click="store.toggleMute"
                :title="store.muted ? '取消静音' : '静音'"
              >
                <i :class="volumeIconClass"></i>
              </button>
              <div class="volume-slider-container" :class="{ 'is-visible': showVolumeSlider }">
                <input
                  type="range"
                  class="volume-slider"
                  min="0"
                  max="100"
                  :value="store.muted ? 0 : Math.round(store.volume * 100)"
                  @input="onVolumeInput"
                  orient="vertical"
                />
                <span class="volume-value">{{ Math.round(store.volume * 100) }}</span>
              </div>
            </div>

            <!-- 时间显示 -->
            <div class="time-display">
              <span class="time-current">{{ formatTime(store.currentTime) }}</span>
              <span class="time-separator">/</span>
              <span class="time-duration">{{ formatTime(store.duration) }}</span>
            </div>
          </div>

          <!-- 右侧控制 -->
          <div class="controls-right">
            <!-- 字幕选择 -->
            <div v-if="store.subtitles.length > 0" class="control-dropdown-wrapper">
              <button
                class="control-btn"
                @click="toggleSubtitleMenu"
                :class="{ 'is-active': store.activeSubtitle }"
                title="字幕"
              >
                <i class="fa fa-closed-captioning"></i>
              </button>
              <div v-if="showSubtitleMenu" class="control-dropdown-menu subtitle-menu">
                <button
                  class="dropdown-item"
                  :class="{ 'is-selected': !store.activeSubtitle }"
                  @click="selectSubtitle(null)"
                >关闭字幕</button>
                <button
                  v-for="sub in store.subtitles"
                  :key="sub.id"
                  class="dropdown-item"
                  :class="{ 'is-selected': store.activeSubtitle === sub.id }"
                  @click="selectSubtitle(sub.id)"
                >{{ sub.label }}</button>
              </div>
            </div>

            <!-- 倍速选择 -->
            <div class="control-dropdown-wrapper">
              <button
                class="control-btn control-btn-text"
                @click="toggleSpeedMenu"
                title="播放速度"
              >
                {{ store.currentPlaybackRateLabel }}
              </button>
              <div v-if="showSpeedMenu" class="control-dropdown-menu speed-menu">
                <button
                  v-for="rate in store.availablePlaybackRates"
                  :key="rate.value"
                  class="dropdown-item"
                  :class="{ 'is-selected': store.playbackRate === rate.value }"
                  @click="selectSpeed(rate.value)"
                >{{ rate.label }}</button>
              </div>
            </div>

            <!-- 分辨率选择 -->
            <div v-if="store.availableResolutions.length > 1" class="control-dropdown-wrapper">
              <button
                class="control-btn control-btn-text"
                @click="toggleResolutionMenu"
                title="画质"
              >
                {{ store.currentResolutionLabel }}
              </button>
              <div v-if="showResolutionMenu" class="control-dropdown-menu resolution-menu">
                <button
                  v-for="res in store.availableResolutions"
                  :key="res.value"
                  class="dropdown-item"
                  :class="{ 'is-selected': store.currentResolution === res.value }"
                  @click="selectResolution(res.value)"
                >
                  {{ res.label }}
                  <span v-if="res.bitrate" class="resolution-bitrate">{{ formatBitrate(res.bitrate) }}</span>
                </button>
              </div>
            </div>

            <!-- 画中画 -->
            <button
              class="control-btn"
              @click="store.togglePiP"
              title="画中画"
            >
              <i class="fa fa-window-restore"></i>
            </button>

            <!-- 全屏 -->
            <button
              class="control-btn"
              @click="store.toggleFullscreen"
              :title="store.isFullscreen ? '退出全屏' : '全屏'"
            >
              <i :class="store.isFullscreen ? 'fa fa-compress' : 'fa fa-expand'"></i>
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useVideoPlayerStore } from '@/stores/videoPlayerStore'
import { useToastStore } from '@/stores/toastStore'
import Hls from 'hls.js'
import { formatFileSize } from '@/utils/helpers'

// ============================================================
// 路由 & Store
// ============================================================
const route = useRoute()
const router = useRouter()
const store = useVideoPlayerStore()
const toastStore = useToastStore()

// ============================================================
// Refs
// ============================================================
const videoRef = ref(null)
const playerWrapperRef = ref(null)
const progressBarRef = ref(null)
const progressTrackRef = ref(null)

// ============================================================
// 本地状态
// ============================================================
const showVolumeSlider = ref(false)
const showSpeedMenu = ref(false)
const showResolutionMenu = ref(false)
const showSubtitleMenu = ref(false)
const isDragging = ref(false)
const hoverPreview = ref({
  visible: false,
  time: 0,
  percent: 0,
  spriteX: 0,
  spriteY: 0
})

// ============================================================
// 计算属性
// ============================================================
const volumeIconClass = computed(() => {
  if (store.muted || store.volume === 0) return 'fa fa-volume-off'
  if (store.volume < 0.5) return 'fa fa-volume-down'
  return 'fa fa-volume-up'
})

const activeSubtitlesList = computed(() => {
  if (!store.activeSubtitle) return []
  return store.subtitles.filter(s => s.id === store.activeSubtitle)
})

const hoverPreviewTooltipStyle = computed(() => {
  if (!hoverPreview.value.visible) return { display: 'none' }
  return {
    left: `calc(${hoverPreview.value.percent}% - 80px)`,
    bottom: '100%'
  }
})

const hoverPreviewImageStyle = computed(() => {
  if (!store.spriteInfo?.config) return {}
  const { cols, width, height } = store.spriteInfo.config
  const totalCols = cols
  const col = hoverPreview.value.spriteX % totalCols
  const row = Math.floor(hoverPreview.value.spriteX / totalCols)
  return {
    backgroundImage: `url(${store.spriteInfo.sprite_image})`,
    backgroundPosition: `-${col * width}px -${row * height}px`,
    backgroundSize: `${totalCols * width}px auto`,
    width: `${width}px`,
    height: `${height}px`
  }
})

// ============================================================
// 工具函数
// ============================================================
function formatTime(seconds) {
  if (!seconds || isNaN(seconds) || !isFinite(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  if (h > 0) {
    return `${h}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
  }
  return `${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`
}

function formatBitrate(bps) {
  if (bps >= 1000000) return `${(bps / 1000000).toFixed(1)}Mbps`
  if (bps >= 1000) return `${(bps / 1000).toFixed(0)}Kbps`
  return `${bps}bps`
}

// ============================================================
// 视频加载
// ============================================================
async function initVideo() {
  const fileId = route.params.fileId
  if (!fileId) {
    store.error = { title: '参数错误', message: '缺少文件ID参数' }
    return
  }

  const fileInfo = {
    node_id: fileId,
    node_name: decodeURIComponent(route.query.name || '视频播放'),
    file_size: parseInt(route.query.size) || 0,
    node_type: 'FILE'
  }

  await store.loadVideo(fileInfo)
  await nextTick()
  setupVideoElement()
}

function setupVideoElement() {
  const video = videoRef.value
  if (!video) return

  store.videoElement = video

  // HLS 流媒体处理
  if (store.isHls && store.videoSourceUrl && Hls.isSupported()) {
    setupHls(video)
  } else if (store.isHls && video.canPlayType('application/vnd.apple.mpegurl')) {
    // Safari 原生 HLS 支持
    video.src = store.videoSourceUrl
  }
  // MP4 使用原生 video 元素（已通过 :src 绑定）
}

function setupHls(video) {
  if (store.hlsInstance) {
    store.hlsInstance.destroy()
  }

  const hls = new Hls({
    enableWorker: true,
    lowLatencyMode: false,
    backBufferLength: 90,
    maxBufferLength: 30,
    maxMaxBufferLength: 600,
    maxBufferSize: 60 * 1000 * 1000,
    maxBufferHole: 0.5,
    // ABR (自适应码率) 配置
    abrEwmaDefaultEstimate: 500000,
    abrBandWidthFactor: 0.95,
    abrBandWidthUpFactor: 0.7,
    startLevel: -1, // 自动选择
    // 错误重试
    manifestLoadingMaxRetry: 4,
    levelLoadingMaxRetry: 4,
    fragLoadingMaxRetry: 6,
    // 调试
    debug: false,
  })

  hls.loadSource(store.videoSourceUrl)
  hls.attachMedia(video)

  hls.on(Hls.Events.MANIFEST_PARSED, (event, data) => {
    // 自动选择初始分辨率
    if (store.currentResolution === 'auto' && data.levels.length > 0) {
      const autoLevel = data.firstLevel
      if (autoLevel >= 0) {
        hls.currentLevel = autoLevel
      }
    }
  })

  hls.on(Hls.Events.ERROR, (event, data) => {
    if (data.fatal) {
      switch (data.type) {
        case Hls.ErrorTypes.NETWORK_ERROR:
          hls.startLoad()
          break
        case Hls.ErrorTypes.MEDIA_ERROR:
          hls.recoverMediaError()
          break
        default:
          store.error = {
            title: '流媒体加载失败',
            message: 'HLS 流媒体播放出错，请尝试切换分辨率或刷新'
          }
          hls.destroy()
          break
      }
    }
  })

  store.hlsInstance = hls
}

// ============================================================
// 进度条交互
// ============================================================
function onProgressMouseDown(e) {
  isDragging.value = true
  updateProgressFromEvent(e)
  document.addEventListener('mousemove', onProgressDrag)
  document.addEventListener('mouseup', onProgressDragEnd)
}

function onProgressDrag(e) {
  if (isDragging.value) {
    updateProgressFromEvent(e)
  }
}

function onProgressDragEnd() {
  if (isDragging.value) {
    const time = hoverPreview.value.time
    store.seek(time)
    isDragging.value = false
  }
  document.removeEventListener('mousemove', onProgressDrag)
  document.removeEventListener('mouseup', onProgressDragEnd)
}

function onProgressInput(e) {
  const time = parseFloat(e.target.value)
  store.seek(time)
}

function onProgressChange(e) {
  const time = parseFloat(e.target.value)
  store.seek(time)
}

function updateProgressFromEvent(e) {
  const rect = progressTrackRef.value?.getBoundingClientRect()
  if (!rect || !store.duration) return
  const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  const time = percent * store.duration
  hoverPreview.value.percent = percent * 100
  hoverPreview.value.time = time
  updateSpritePreview(time)
}

function onProgressMouseMove(e) {
  const rect = progressTrackRef.value?.getBoundingClientRect()
  if (!rect || !store.duration) return
  const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width))
  const time = percent * store.duration
  hoverPreview.value.visible = true
  hoverPreview.value.percent = percent * 100
  hoverPreview.value.time = time
  updateSpritePreview(time)
}

function onProgressMouseLeave() {
  if (!isDragging.value) {
    hoverPreview.value.visible = false
  }
}

function updateSpritePreview(time) {
  if (!store.spriteInfo?.config) return
  const { interval, cols } = store.spriteInfo.config
  const frameIndex = Math.floor(time / interval)
  const totalCols = cols
  hoverPreview.value.spriteX = frameIndex
  hoverPreview.value.spriteY = Math.floor(frameIndex / totalCols)
}

// ============================================================
// 音量控制
// ============================================================
function onVolumeInput(e) {
  const val = parseInt(e.target.value) / 100
  store.setVolume(val)
}

// ============================================================
// 下拉菜单
// ============================================================
function toggleSpeedMenu() {
  showSpeedMenu.value = !showSpeedMenu.value
  showResolutionMenu.value = false
  showSubtitleMenu.value = false
}

function toggleResolutionMenu() {
  showResolutionMenu.value = !showResolutionMenu.value
  showSpeedMenu.value = false
  showSubtitleMenu.value = false
}

function toggleSubtitleMenu() {
  showSubtitleMenu.value = !showSubtitleMenu.value
  showSpeedMenu.value = false
  showResolutionMenu.value = false
}

function selectSpeed(rate) {
  store.setPlaybackRate(rate)
  showSpeedMenu.value = false
}

function selectResolution(resolution) {
  store.setResolution(resolution)
  showResolutionMenu.value = false
}

function selectSubtitle(lang) {
  store.setSubtitle(lang)
  showSubtitleMenu.value = false
}

// 点击外部关闭下拉菜单
function onDocumentClick(e) {
  if (!e.target.closest('.control-dropdown-wrapper')) {
    showSpeedMenu.value = false
    showResolutionMenu.value = false
    showSubtitleMenu.value = false
  }
}

// ============================================================
// 播放器鼠标交互
// ============================================================
let mouseMoveTimer = null

function onPlayerMouseMove() {
  store.showControls()
  if (mouseMoveTimer) clearTimeout(mouseMoveTimer)
  mouseMoveTimer = setTimeout(() => {
    if (store.playing) {
      store.hideControls()
    }
  }, 3000)
}

function onPlayerMouseLeave() {
  if (mouseMoveTimer) clearTimeout(mouseMoveTimer)
  if (store.playing) {
    store.hideControls()
  }
}

// ============================================================
// 时间更新（含进度保存）
// ============================================================
function onTimeUpdateWithSave(event) {
  store.onTimeUpdate(event)
  // 定期保存进度
  if (Math.floor(store.currentTime) % 5 === 0) {
    store.saveProgress()
  }
}

// ============================================================
// 键盘快捷键
// ============================================================
function handleKeydown(e) {
  // 如果焦点在输入框内，不处理快捷键
  const tag = document.activeElement?.tagName?.toLowerCase()
  if (tag === 'input' || tag === 'textarea' || tag === 'select') return

  switch (e.key) {
    case ' ':
    case 'k':
      e.preventDefault()
      store.togglePlay()
      break
    case 'ArrowLeft':
      e.preventDefault()
      store.seekRelative(-5)
      break
    case 'ArrowRight':
      e.preventDefault()
      store.seekRelative(5)
      break
    case 'ArrowUp':
      e.preventDefault()
      store.setVolume(Math.min(1, store.volume + 0.05))
      break
    case 'ArrowDown':
      e.preventDefault()
      store.setVolume(Math.max(0, store.volume - 0.05))
      break
    case 'f':
      e.preventDefault()
      store.toggleFullscreen()
      break
    case 'm':
      e.preventDefault()
      store.toggleMute()
      break
    case 'p':
      if (e.shiftKey) {
        e.preventDefault()
        store.togglePiP()
      }
      break
    case '0':
    case '1':
    case '2':
    case '3':
    case '4':
    case '5':
    case '6':
    case '7':
    case '8':
    case '9':
      e.preventDefault()
      const num = parseInt(e.key)
      store.seek((num / 10) * store.duration)
      break
    case 'j':
      e.preventDefault()
      store.seekRelative(-10)
      break
    case 'l':
      e.preventDefault()
      store.seekRelative(10)
      break
  }
}

// ============================================================
// 全屏变化监听
// ============================================================
function onFullscreenChange() {
  store.isFullscreen = !!document.fullscreenElement
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
// 生命周期
// ============================================================
onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('fullscreenchange', onFullscreenChange)
  document.addEventListener('webkitfullscreenchange', onFullscreenChange)

  await initVideo()
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('fullscreenchange', onFullscreenChange)
  document.removeEventListener('webkitfullscreenchange', onFullscreenChange)
  document.removeEventListener('mousemove', onProgressDrag)
  document.removeEventListener('mouseup', onProgressDragEnd)

  if (mouseMoveTimer) clearTimeout(mouseMoveTimer)
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
  display: flex;
  align-items: center;
  justify-content: center;
  background: #000;
}

.player-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  cursor: pointer;
  outline: none;
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
  border-top-color: #165dff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
  margin: 0 auto;
}

.ring-sm {
  width: 32px;
  height: 32px;
  border-width: 2px;
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
  background: #165dff;
  color: #fff;
}

.error-btn-primary:hover {
  background: #4080ff;
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
   中央播放按钮
   ============================================================ */
.center-play-btn {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
  cursor: pointer;
  transition: opacity 0.3s;
}

.play-icon-circle {
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: rgba(22, 93, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.2s, background 0.2s;
  box-shadow: 0 4px 24px rgba(22, 93, 255, 0.4);
}

.play-icon-circle i {
  font-size: 24px;
  color: #fff;
  margin-left: 3px;
}

.center-play-btn:hover .play-icon-circle {
  transform: scale(1.08);
  background: rgba(64, 128, 255, 0.95);
}

/* ============================================================
   缓冲指示器
   ============================================================ */
.buffering-indicator {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: 10;
}

.buffering-ring {
  background: rgba(0, 0, 0, 0.6);
  padding: 12px;
  border-radius: 50%;
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
  max-width: 480px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}

/* ============================================================
   底部控制栏
   ============================================================ */
.player-controls {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: 20;
  background: linear-gradient(to top, rgba(0, 0, 0, 0.85) 0%, rgba(0, 0, 0, 0.6) 60%, transparent 100%);
  padding: 24px 16px 12px;
  transition: opacity 0.35s, transform 0.35s;
}

.controls-hidden {
  opacity: 0;
  transform: translateY(8px);
  pointer-events: none;
}

/* ============================================================
   进度条
   ============================================================ */
.progress-bar-container {
  position: relative;
  padding: 6px 0 14px;
  margin: 0 4px;
}

.progress-bar-track {
  position: relative;
  width: 100%;
  height: 4px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  cursor: pointer;
  transition: height 0.15s;
}

.progress-bar-container:hover .progress-bar-track {
  height: 6px;
}

.progress-bar-buffered {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
  overflow: hidden;
}

.buffered-segment {
  position: absolute;
  top: 0;
  height: 100%;
  background: rgba(255, 255, 255, 0.35);
}

.progress-bar-played {
  position: absolute;
  top: 0;
  left: 0;
  height: 100%;
  background: #165dff;
  border-radius: 2px;
  z-index: 2;
  transition: width 0.1s linear;
}

.progress-bar-hover-indicator {
  position: absolute;
  top: 0;
  height: 100%;
  width: 3px;
  background: rgba(255, 255, 255, 0.6);
  z-index: 3;
  border-radius: 1px;
  pointer-events: none;
}

.progress-bar-thumb {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 14px;
  height: 14px;
  background: #165dff;
  border: 2px solid #fff;
  border-radius: 50%;
  z-index: 4;
  opacity: 0;
  transition: opacity 0.15s, transform 0.15s;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.3);
  pointer-events: none;
}

.progress-bar-container:hover .progress-bar-thumb,
.progress-bar-thumb.is-dragging {
  opacity: 1;
}

.progress-bar-thumb.is-dragging {
  transform: translate(-50%, -50%) scale(1.2);
}

.progress-bar-input {
  position: absolute;
  top: -8px;
  left: 0;
  width: 100%;
  height: 20px;
  opacity: 0;
  cursor: pointer;
  z-index: 5;
  margin: 0;
  -webkit-appearance: none;
  appearance: none;
}

/* 悬停预览 */
.hover-preview-tooltip {
  position: absolute;
  bottom: calc(100% + 8px);
  transform: translateX(-50%);
  z-index: 10;
  pointer-events: none;
  display: flex;
  flex-direction: column;
  align-items: center;
  background: rgba(0, 0, 0, 0.85);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.5);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.hover-preview-image {
  background-repeat: no-repeat;
  min-width: 160px;
  min-height: 90px;
}

.hover-preview-time {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.8);
  padding: 4px 10px;
  font-weight: 500;
}

/* ============================================================
   控制按钮行
   ============================================================ */
.controls-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px;
  gap: 8px;
}

.controls-left,
.controls-right {
  display: flex;
  align-items: center;
  gap: 4px;
}

.control-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.88);
  font-size: 15px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}

.control-btn-sm {
  width: 30px;
  height: 30px;
  font-size: 13px;
}

.control-btn-text {
  width: auto;
  padding: 0 10px;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.3px;
  min-width: 40px;
}

.control-btn.is-active {
  color: #165dff;
  background: rgba(22, 93, 255, 0.15);
}

/* 音量控制 */
.volume-control-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.volume-slider-container {
  position: absolute;
  bottom: 100%;
  left: 50%;
  transform: translateX(-50%);
  background: rgba(0, 0, 0, 0.85);
  border-radius: 10px;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  opacity: 0;
  visibility: hidden;
  transition: opacity 0.2s, visibility 0.2s;
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.1);
  margin-bottom: 8px;
}

.volume-slider-container.is-visible {
  opacity: 1;
  visibility: visible;
}

.volume-slider {
  -webkit-appearance: slider-vertical;
  appearance: slider-vertical;
  width: 4px;
  height: 80px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 2px;
  outline: none;
  cursor: pointer;
  accent-color: #165dff;
}

.volume-value {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.7);
  font-weight: 500;
}

/* 时间显示 */
.time-display {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 12px;
  font-weight: 500;
  font-variant-numeric: tabular-nums;
  margin-left: 6px;
}

.time-current {
  color: #fff;
}

.time-separator {
  color: rgba(255, 255, 255, 0.4);
  margin: 0 2px;
}

.time-duration {
  color: rgba(255, 255, 255, 0.5);
}

/* ============================================================
   下拉菜单
   ============================================================ */
.control-dropdown-wrapper {
  position: relative;
}

.control-dropdown-menu {
  position: absolute;
  bottom: calc(100% + 8px);
  right: 0;
  background: rgba(0, 0, 0, 0.88);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 6px;
  min-width: 140px;
  backdrop-filter: blur(12px);
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.45);
  z-index: 30;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.dropdown-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  border-radius: 6px;
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.8);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s;
  text-align: left;
  white-space: nowrap;
}

.dropdown-item:hover {
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
}

.dropdown-item.is-selected {
  color: #165dff;
  background: rgba(22, 93, 255, 0.12);
}

.resolution-bitrate {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  margin-left: 12px;
}

/* ============================================================
   全屏适配
   ============================================================ */
.is-fullscreen .player-wrapper {
  border-radius: 0;
}

.is-fullscreen .video-player-view {
  border-radius: 0;
  height: 100vh;
}

/* 视频元素在容器内居中 */
.player-video::-webkit-media-controls {
  display: none !important;
}

.player-video::-webkit-media-controls-enclosure {
  display: none !important;
}
</style>