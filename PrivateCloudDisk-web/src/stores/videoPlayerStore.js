import { defineStore } from 'pinia'
import { ref, computed, shallowRef } from 'vue'
import {
  getVideoStreamInfoApi,
  requestVideoTokenApi,
  getVideoProgressApi,
  saveVideoProgressApi,
  getVideoSubtitlesApi,
  getVideoSpriteInfoApi,
  buildVideoStreamUrl,
  buildHlsStreamUrl,
  recordVideoHistoryApi
} from '@/api/modules/video'

/**
 * 视频播放器状态管理
 * 企业级视频播放器核心状态，管理播放、分辨率、倍速、字幕等
 */
export const useVideoPlayerStore = defineStore('videoPlayer', () => {
  // ============================================================
  // 核心状态
  // ============================================================
  const currentFile = ref(null)           // 当前播放文件
  const streamInfo = ref(null)            // 流媒体信息（分辨率列表、编码等）
  const spriteInfo = ref(null)            // 雪碧图信息
  const subtitles = ref([])               // 可用字幕列表
  const activeSubtitle = ref(null)        // 当前字幕语言
  const streamToken = ref('')             // 播放凭证
  const tokenExpiresAt = ref(0)           // 凭证过期时间

  // 播放状态
  const loading = ref(false)              // 加载状态
  const error = ref(null)                 // 错误信息
  const playing = ref(false)              // 是否正在播放
  const paused = ref(true)                // 是否暂停
  const seeking = ref(false)              // 是否正在跳转
  const buffering = ref(false)            // 是否正在缓冲
  const ended = ref(false)                // 是否播放完毕

  // 时间相关
  const currentTime = ref(0)              // 当前播放时间（秒）
  const duration = ref(0)                 // 视频总时长（秒）
  const buffered = ref(0)                 // 已缓冲时长（秒）
  const bufferedRanges = ref([])          // 缓冲区间 [{start, end}]

  // 播放设置
  const volume = ref(1)                   // 音量 0-1
  const muted = ref(false)                // 是否静音
  const playbackRate = ref(1)             // 播放倍速
  const currentResolution = ref('auto')   // 当前分辨率
  const isFullscreen = ref(false)         // 是否全屏
  const isPiP = ref(false)                // 是否画中画

  // 控制栏显示
  const controlsVisible = ref(true)       // 控制栏是否可见
  const controlsTimer = ref(null)         // 控制栏自动隐藏定时器

  // 视频元素引用
  const videoElement = shallowRef(null)   // 原生 video 元素引用
  const hlsInstance = shallowRef(null)    // HLS.js 实例引用

  // 历史记录
  const savedProgress = ref(null)         // 上次播放进度
  const watchHistory = ref([])            // 观看历史

  // ============================================================
  // 计算属性
  // ============================================================
  const availableResolutions = computed(() => {
    if (!streamInfo.value?.resolutions) return []
    return [
      { label: '自动', value: 'auto', width: 0, height: 0, bitrate: 0 },
      ...streamInfo.value.resolutions
    ]
  })

  const availablePlaybackRates = computed(() => [
    { label: '0.5x', value: 0.5 },
    { label: '0.75x', value: 0.75 },
    { label: '1.0x', value: 1.0 },
    { label: '1.25x', value: 1.25 },
    { label: '1.5x', value: 1.5 },
    { label: '2.0x', value: 2.0 }
  ])

  const isHls = computed(() => streamInfo.value?.has_hls ?? false)
  const isDash = computed(() => streamInfo.value?.has_dash ?? false)
  const isMp4 = computed(() => !isHls.value && !isDash.value)

  const videoSourceUrl = computed(() => {
    if (!streamInfo.value || !streamToken.value) return ''
    if (isHls.value) {
      return buildHlsStreamUrl(currentFile.value?.node_id, streamToken.value, {
        resolution: currentResolution.value
      })
    }
    return buildVideoStreamUrl(currentFile.value?.node_id, streamToken.value, {
      resolution: currentResolution.value
    })
  })

  const progressPercent = computed(() => {
    if (duration.value <= 0) return 0
    return (currentTime.value / duration.value) * 100
  })

  const bufferedPercent = computed(() => {
    if (duration.value <= 0) return 0
    return (buffered.value / duration.value) * 100
  })

  const currentResolutionLabel = computed(() => {
    if (currentResolution.value === 'auto') return '自动'
    const res = streamInfo.value?.resolutions?.find(r => r.label === currentResolution.value)
    return res?.label || currentResolution.value
  })

  const currentPlaybackRateLabel = computed(() => {
    return `${playbackRate.value}x`
  })

  // ============================================================
  // 视频加载
  // ============================================================

  /**
   * 加载视频
   * @param {Object} file - 文件信息 { node_id, node_name, file_size, ... }
   */
  async function loadVideo(file) {
    loading.value = true
    error.value = null
    currentFile.value = file
    resetPlaybackState()

    try {
      // 1. 获取流媒体信息
      const infoRes = await getVideoStreamInfoApi(file.node_id)
      if (infoRes.code !== 200) {
        throw new Error(infoRes.message || '获取视频信息失败')
      }
      streamInfo.value = infoRes.data

      // 2. 获取播放凭证
      const tokenRes = await requestVideoTokenApi(file.node_id, {
        resolution: currentResolution.value
      })
      if (tokenRes.code !== 200) {
        throw new Error(tokenRes.message || '获取播放凭证失败')
      }
      streamToken.value = tokenRes.data?.token || tokenRes.data
      tokenExpiresAt.value = tokenRes.data?.expires_at
        ? new Date(tokenRes.data.expires_at).getTime()
        : Date.now() + 3600 * 1000

      // 3. 获取字幕（并行）
      getVideoSubtitlesApi(file.node_id).then(res => {
        if (res.code === 200 && res.data?.subtitles) {
          subtitles.value = res.data.subtitles
        }
      }).catch(() => {})

      // 4. 获取雪碧图信息（并行）
      getVideoSpriteInfoApi(file.node_id).then(res => {
        if (res.code === 200) {
          spriteInfo.value = res.data
        }
      }).catch(() => {})

      // 5. 获取上次播放进度（断点续播）
      const progressRes = await getVideoProgressApi(file.node_id)
      if (progressRes.code === 200 && progressRes.data) {
        savedProgress.value = progressRes.data
        currentResolution.value = progressRes.data.resolution || 'auto'
        playbackRate.value = progressRes.data.playback_rate || 1
      }
    } catch (err) {
      console.error('加载视频失败:', err)
      error.value = {
        title: '视频加载失败',
        message: err.message || '无法加载视频，请稍后重试'
      }
    } finally {
      loading.value = false
    }
  }

  /**
   * 刷新播放凭证（Token 过期时自动刷新）
   */
  async function refreshToken() {
    if (!currentFile.value) return
    try {
      const tokenRes = await requestVideoTokenApi(currentFile.value.node_id, {
        resolution: currentResolution.value
      })
      if (tokenRes.code === 200) {
        streamToken.value = tokenRes.data?.token || tokenRes.data
        tokenExpiresAt.value = tokenRes.data?.expires_at
          ? new Date(tokenRes.data.expires_at).getTime()
          : Date.now() + 3600 * 1000
      }
    } catch (err) {
      console.error('刷新凭证失败:', err)
    }
  }

  // ============================================================
  // 播放控制
  // ============================================================

  function play() {
    if (videoElement.value) {
      videoElement.value.play()
      playing.value = true
      paused.value = false
    }
  }

  function pause() {
    if (videoElement.value) {
      videoElement.value.pause()
      playing.value = false
      paused.value = true
    }
  }

  function togglePlay() {
    if (playing.value) {
      pause()
    } else {
      play()
    }
  }

  function seek(time) {
    if (videoElement.value) {
      videoElement.value.currentTime = Math.max(0, Math.min(time, duration.value))
      currentTime.value = videoElement.value.currentTime
    }
  }

  function seekRelative(offset) {
    seek(currentTime.value + offset)
  }

  function setVolume(val) {
    const v = Math.max(0, Math.min(1, val))
    volume.value = v
    if (videoElement.value) {
      videoElement.value.volume = v
    }
    if (v > 0) muted.value = false
  }

  function toggleMute() {
    muted.value = !muted.value
    if (videoElement.value) {
      videoElement.value.muted = muted.value
    }
  }

  function setPlaybackRate(rate) {
    playbackRate.value = rate
    if (videoElement.value) {
      videoElement.value.playbackRate = rate
    }
  }

  function setResolution(resolution) {
    currentResolution.value = resolution
    // 切换分辨率时需要重新加载流
    // 这里触发视频源的切换
    if (videoElement.value) {
      const currentTimeSnapshot = currentTime.value
      const wasPlaying = playing.value

      // 刷新 token（新分辨率可能需要新 token）
      refreshToken().then(() => {
        if (videoElement.value) {
          // 重新加载视频
          videoElement.value.load()
          videoElement.value.currentTime = currentTimeSnapshot
          if (wasPlaying) {
            videoElement.value.play()
          }
        }
      })
    }
  }

  function setSubtitle(lang) {
    activeSubtitle.value = lang === activeSubtitle.value ? null : lang
  }

  async function toggleFullscreen() {
    const el = videoElement.value?.parentElement || videoElement.value
    if (!el) return

    if (!document.fullscreenElement) {
      await el.requestFullscreen()
      isFullscreen.value = true
    } else {
      await document.exitFullscreen()
      isFullscreen.value = false
    }
  }

  async function togglePiP() {
    if (!videoElement.value) return

    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture()
        isPiP.value = false
      } else {
        await videoElement.value.requestPictureInPicture()
        isPiP.value = true
      }
    } catch (err) {
      console.error('画中画切换失败:', err)
    }
  }

  // ============================================================
  // 视频事件处理
  // ============================================================

  function onLoadedMetadata(event) {
    const video = event.target
    duration.value = video.duration
    // 断点续播
    if (savedProgress.value?.current_time && savedProgress.value.current_time < video.duration - 10) {
      video.currentTime = savedProgress.value.current_time
    }
  }

  function onTimeUpdate(event) {
    const video = event.target
    currentTime.value = video.currentTime
    buffered.value = video.buffered.length > 0
      ? video.buffered.end(video.buffered.length - 1)
      : 0
    // 更新缓冲区间
    const ranges = []
    for (let i = 0; i < video.buffered.length; i++) {
      ranges.push({ start: video.buffered.start(i), end: video.buffered.end(i) })
    }
    bufferedRanges.value = ranges
  }

  function onWaiting() {
    buffering.value = true
  }

  function onCanPlay() {
    buffering.value = false
  }

  function onPlaying() {
    playing.value = true
    paused.value = false
    buffering.value = false
    ended.value = false
  }

  function onPause() {
    playing.value = false
    paused.value = true
  }

  function onEnded() {
    playing.value = false
    paused.value = true
    ended.value = true
    // 保存完整播放记录
    if (currentFile.value) {
      recordVideoHistoryApi(currentFile.value.node_id, {
        watchedDuration: duration.value,
        totalDuration: duration.value,
        completed: true
      }).catch(() => {})
    }
  }

  function onSeeking() {
    seeking.value = true
  }

  function onSeeked() {
    seeking.value = false
  }

  function onError(event) {
    const video = event.target
    let message = '视频播放出错'
    if (video?.error) {
      const errorCodes = {
        1: '视频加载被中止',
        2: '网络错误，无法加载视频',
        3: '视频解码失败',
        4: '视频格式不支持或文件已损坏'
      }
      message = errorCodes[video.error.code] || message
    }
    error.value = { title: '播放错误', message }
  }

  // ============================================================
  // 控制栏自动隐藏
  // ============================================================

  function showControls() {
    controlsVisible.value = true
    resetControlsTimer()
  }

  function hideControls() {
    if (playing.value && !paused.value) {
      controlsVisible.value = false
    }
  }

  function resetControlsTimer() {
    if (controlsTimer.value) clearTimeout(controlsTimer.value)
    if (playing.value) {
      controlsTimer.value = setTimeout(() => {
        hideControls()
      }, 3000)
    }
  }

  // ============================================================
  // 进度保存
  // ============================================================

  let lastSaveTime = 0
  const SAVE_INTERVAL = 5000 // 每5秒保存一次

  async function saveProgress() {
    if (!currentFile.value || !duration.value) return
    const now = Date.now()
    if (now - lastSaveTime < SAVE_INTERVAL) return
    lastSaveTime = now

    try {
      await saveVideoProgressApi(currentFile.value.node_id, {
        currentTime: currentTime.value,
        duration: duration.value,
        resolution: currentResolution.value,
        playbackRate: playbackRate.value
      })
    } catch {
      // 静默失败
    }
  }

  // ============================================================
  // 重置
  // ============================================================

  function resetPlaybackState() {
    playing.value = false
    paused.value = true
    seeking.value = false
    buffering.value = false
    ended.value = false
    currentTime.value = 0
    duration.value = 0
    buffered.value = 0
    bufferedRanges.value = []
    savedProgress.value = null
  }

  function reset() {
    resetPlaybackState()
    currentFile.value = null
    streamInfo.value = null
    spriteInfo.value = null
    subtitles.value = []
    activeSubtitle.value = null
    streamToken.value = ''
    tokenExpiresAt.value = 0
    volume.value = 1
    muted.value = false
    playbackRate.value = 1
    currentResolution.value = 'auto'
    isFullscreen.value = false
    isPiP.value = false
    error.value = null
    loading.value = false
    lastSaveTime = 0
    if (hlsInstance.value) {
      hlsInstance.value.destroy()
      hlsInstance.value = null
    }
    videoElement.value = null
  }

  return {
    // 核心状态
    currentFile,
    streamInfo,
    spriteInfo,
    subtitles,
    activeSubtitle,
    streamToken,
    tokenExpiresAt,
    loading,
    error,
    playing,
    paused,
    seeking,
    buffering,
    ended,
    currentTime,
    duration,
    buffered,
    bufferedRanges,
    volume,
    muted,
    playbackRate,
    currentResolution,
    isFullscreen,
    isPiP,
    controlsVisible,
    videoElement,
    hlsInstance,
    savedProgress,
    watchHistory,
    // 计算属性
    availableResolutions,
    availablePlaybackRates,
    isHls,
    isDash,
    isMp4,
    videoSourceUrl,
    progressPercent,
    bufferedPercent,
    currentResolutionLabel,
    currentPlaybackRateLabel,
    // 方法
    loadVideo,
    refreshToken,
    play,
    pause,
    togglePlay,
    seek,
    seekRelative,
    setVolume,
    toggleMute,
    setPlaybackRate,
    setResolution,
    setSubtitle,
    toggleFullscreen,
    togglePiP,
    onLoadedMetadata,
    onTimeUpdate,
    onWaiting,
    onCanPlay,
    onPlaying,
    onPause,
    onEnded,
    onSeeking,
    onSeeked,
    onError,
    showControls,
    hideControls,
    resetControlsTimer,
    saveProgress,
    reset,
  }
})