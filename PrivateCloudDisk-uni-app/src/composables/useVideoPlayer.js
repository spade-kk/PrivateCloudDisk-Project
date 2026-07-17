/**
 * composables/useVideoPlayer.js - 视频播放器组合式函数
 *
 * 核心能力:
 * - HLS 流媒体播放 (m3u8 支持)
 * - 播放/暂停/进度/音量/全屏控制
 * - 多码率切换
 * - 播放速度调整 (0.5x ~ 2.0x)
 * - 缓冲状态管理
 * - 断线重连
 * - 播放历史记录
 */
import { ref, reactive, computed, onUnmounted } from 'vue'

/**
 * @param {Object} options
 * @param {string} options.url      视频地址 (支持 .mp4 / .m3u8)
 * @param {string} options.poster   封面图
 * @param {Array}  options.qualities 多码率列表 [{ label, url }]
 */
export function useVideoPlayer(options = {}) {
  // ========== 核心状态 ==========
  const videoContext = ref(null)
  const isPlaying = ref(false)
  const isFullscreen = ref(false)
  const isLoading = ref(true)
  const isBuffering = ref(false)
  const hasError = ref(false)
  const errorMessage = ref('')

  // 播放参数
  const currentTime = ref(0)
  const duration = ref(0)
  const volume = ref(1)
  const isMuted = ref(false)
  const playbackRate = ref(1.0)
  const buffered = ref(0)

  // 多码率
  const qualities = ref(options.qualities || [])
  const currentQualityIndex = ref(0)
  const src = ref(options.url || '')

  // 重连
  const maxRetryCount = 3
  const retryCount = ref(0)
  const retryTimer = ref(null)

  // ========== 计算属性 ==========

  const progress = computed(() => {
    if (!duration.value) return 0
    return (currentTime.value / duration.value) * 100
  })

  const currentQuality = computed(() => {
    return qualities.value[currentQualityIndex.value] || null
  })

  const formattedCurrentTime = computed(() => formatTime(currentTime.value))
  const formattedDuration = computed(() => formatTime(duration.value))

  const playbackRates = [0.5, 0.75, 1.0, 1.25, 1.5, 2.0]

  // ========== 初始化 ==========

  function initVideo(id = 'video-player') {
    videoContext.value = uni.createVideoContext(id)
  }

  // ========== 播放控制 ==========

  function play() {
    if (!videoContext.value) return
    videoContext.value.play()
    isPlaying.value = true
    hasError.value = false
  }

  function pause() {
    if (!videoContext.value) return
    videoContext.value.pause()
    isPlaying.value = false
  }

  function togglePlay() {
    if (isPlaying.value) {
      pause()
    } else {
      play()
    }
  }

  function stop() {
    if (!videoContext.value) return
    videoContext.value.stop()
    isPlaying.value = false
    currentTime.value = 0
  }

  // ========== 进度控制 ==========

  function seek(position) {
    if (!videoContext.value) return
    videoContext.value.seek(position)
    currentTime.value = position
  }

  function seekPercent(percent) {
    if (!duration.value) return
    const targetTime = (percent / 100) * duration.value
    seek(targetTime)
  }

  function forward(seconds = 10) {
    seek(Math.min(currentTime.value + seconds, duration.value))
  }

  function rewind(seconds = 10) {
    seek(Math.max(currentTime.value - seconds, 0))
  }

  // ========== 音量控制 ==========

  function setVolume(vol) {
    const v = Math.max(0, Math.min(1, vol))
    volume.value = v
    isMuted.value = v === 0
  }

  function toggleMute() {
    isMuted.value = !isMuted.value
    volume.value = isMuted.value ? 0 : 1
  }

  // ========== 全屏控制 ==========

  function requestFullscreen() {
    if (!videoContext.value) return
    videoContext.value.requestFullScreen({ direction: 90 })
    isFullscreen.value = true
  }

  function exitFullscreen() {
    if (!videoContext.value) return
    videoContext.value.exitFullScreen()
    isFullscreen.value = false
  }

  function toggleFullscreen() {
    if (isFullscreen.value) {
      exitFullscreen()
    } else {
      requestFullscreen()
    }
  }

  // ========== 播放速度 ==========

  function setPlaybackRate(rate) {
    if (!videoContext.value) return
    playbackRate.value = rate
    videoContext.value.playbackRate(rate)
  }

  function cyclePlaybackRate() {
    const currentIndex = playbackRates.indexOf(playbackRate.value)
    const nextIndex = (currentIndex + 1) % playbackRates.length
    setPlaybackRate(playbackRates[nextIndex])
  }

  // ========== 多码率切换 ==========

  function switchQuality(index) {
    if (index < 0 || index >= qualities.value.length) return
    const currentTimeSnapshot = currentTime.value
    currentQualityIndex.value = index
    src.value = qualities.value[index].url
    // 切换后恢复播放位置
    setTimeout(() => {
      if (videoContext.value && currentTimeSnapshot > 0) {
        videoContext.value.seek(currentTimeSnapshot)
        if (isPlaying.value) {
          videoContext.value.play()
        }
      }
    }, 300)
  }

  // ========== 断线重连 ==========

  function handleReconnect() {
    if (retryCount.value >= maxRetryCount) {
      hasError.value = true
      errorMessage.value = '视频加载失败, 请检查网络后重试'
      return
    }
    retryCount.value++
    const delay = Math.pow(2, retryCount.value) * 1000
    retryTimer.value = setTimeout(() => {
      if (videoContext.value) {
        videoContext.value.play()
      }
    }, delay)
  }

  function clearRetryTimer() {
    if (retryTimer.value) {
      clearTimeout(retryTimer.value)
      retryTimer.value = null
    }
  }

  // ========== 事件处理 ==========

  const eventHandlers = {
    onPlay() {
      isPlaying.value = true
      isLoading.value = false
      hasError.value = false
      retryCount.value = 0
    },

    onPause() {
      isPlaying.value = false
    },

    onEnded() {
      isPlaying.value = false
      // 可在此保存播放记录
    },

    onTimeUpdate(e) {
      currentTime.value = e.detail.currentTime
      duration.value = e.detail.duration
    },

    onWaiting() {
      isBuffering.value = true
    },

    onCanplay() {
      isLoading.value = false
      isBuffering.value = false
    },

    onError(e) {
      console.error('[VideoPlayer] 播放错误:', e.detail)
      handleReconnect()
    },

    onLoadedmetadata(e) {
      duration.value = e.detail.duration
      isLoading.value = false
    },

    onFullscreenChange(e) {
      isFullscreen.value = e.detail.fullScreen
    },

    onProgress(e) {
      if (e.detail.buffered) {
        buffered.value = e.detail.buffered
      }
    }
  }

  // ========== 清理 ==========

  onUnmounted(() => {
    clearRetryTimer()
    if (videoContext.value) {
      videoContext.value.stop()
    }
  })

  // ========== 工具函数 ==========

  function formatTime(seconds) {
    if (!seconds || isNaN(seconds)) return '00:00'
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = Math.floor(seconds % 60)
    const pad = (n) => String(n).padStart(2, '0')
    if (h > 0) {
      return `${pad(h)}:${pad(m)}:${pad(s)}`
    }
    return `${pad(m)}:${pad(s)}`
  }

  return {
    // 状态
    videoContext,
    isPlaying,
    isFullscreen,
    isLoading,
    isBuffering,
    hasError,
    errorMessage,
    currentTime,
    duration,
    volume,
    isMuted,
    playbackRate,
    buffered,
    src,
    qualities,
    currentQualityIndex,
    currentQuality,
    playbackRates,
    retryCount,

    // 计算属性
    progress,
    formattedCurrentTime,
    formattedDuration,

    // 方法
    initVideo,
    play,
    pause,
    togglePlay,
    stop,
    seek,
    seekPercent,
    forward,
    rewind,
    setVolume,
    toggleMute,
    requestFullscreen,
    exitFullscreen,
    toggleFullscreen,
    setPlaybackRate,
    cyclePlaybackRate,
    switchQuality,
    eventHandlers,
    clearRetryTimer
  }
}