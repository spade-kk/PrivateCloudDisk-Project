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

export interface Resolution {
  label: string
  value: string
  width: number
  height: number
  bitrate: number
}

export interface VideoFileInfo {
  node_id: string
  node_name: string
  file_size?: number
  [key: string]: any
}

export interface BufferedRange {
  start: number
  end: number
}

export const useVideoPlayerStore = defineStore('videoPlayer', () => {
  const currentFile = ref<VideoFileInfo | null>(null)
  const streamInfo = ref<any>(null)
  const spriteInfo = ref<any>(null)
  const subtitles = ref<any[]>([])
  const activeSubtitle = ref<string | null>(null)
  const streamToken = ref('')
  const tokenExpiresAt = ref(0)

  const loading = ref(false)
  const error = ref<{ title: string; message: string } | null>(null)
  const playing = ref(false)
  const paused = ref(true)
  const seeking = ref(false)
  const buffering = ref(false)
  const ended = ref(false)

  const currentTime = ref(0)
  const duration = ref(0)
  const buffered = ref(0)
  const bufferedRanges = ref<BufferedRange[]>([])

  const volume = ref(1)
  const muted = ref(false)
  const playbackRate = ref(1)
  const currentResolution = ref('auto')
  const isFullscreen = ref(false)
  const isPiP = ref(false)

  const controlsVisible = ref(true)
  const controlsTimer = ref<ReturnType<typeof setTimeout> | null>(null)

  const videoElement = shallowRef<HTMLVideoElement | null>(null)
  const hlsInstance = shallowRef<any>(null)

  const savedProgress = ref<any>(null)
  const watchHistory = ref<any[]>([])

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
    const res = streamInfo.value?.resolutions?.find((r: Resolution) => r.label === currentResolution.value)
    return res?.label || currentResolution.value
  })

  const currentPlaybackRateLabel = computed(() => `${playbackRate.value}x`)

  async function loadVideo(file: VideoFileInfo): Promise<void> {
    loading.value = true
    error.value = null
    currentFile.value = file
    resetPlaybackState()

    try {
      const infoRes = await getVideoStreamInfoApi(file.node_id)
      if (infoRes.code !== 200) {
        throw new Error(infoRes.message || '获取视频信息失败')
      }
      streamInfo.value = infoRes.data

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

      getVideoSubtitlesApi(file.node_id).then(res => {
        if (res.code === 200 && res.data?.subtitles) {
          subtitles.value = res.data.subtitles
        }
      }).catch(() => {})

      getVideoSpriteInfoApi(file.node_id).then(res => {
        if (res.code === 200) {
          spriteInfo.value = res.data
        }
      }).catch(() => {})

      const progressRes = await getVideoProgressApi(file.node_id)
      if (progressRes.code === 200 && progressRes.data) {
        savedProgress.value = progressRes.data
        currentResolution.value = progressRes.data.resolution || 'auto'
        playbackRate.value = progressRes.data.playback_rate || 1
      }
    } catch (err: any) {
      console.error('加载视频失败:', err)
      error.value = {
        title: '视频加载失败',
        message: err.message || '无法加载视频，请稍后重试'
      }
    } finally {
      loading.value = false
    }
  }

  async function refreshToken(): Promise<void> {
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

  function play(): void {
    if (videoElement.value) {
      videoElement.value.play()
      playing.value = true
      paused.value = false
    }
  }

  function pause(): void {
    if (videoElement.value) {
      videoElement.value.pause()
      playing.value = false
      paused.value = true
    }
  }

  function togglePlay(): void {
    if (playing.value) {
      pause()
    } else {
      play()
    }
  }

  function seek(time: number): void {
    if (videoElement.value) {
      videoElement.value.currentTime = Math.max(0, Math.min(time, duration.value))
      currentTime.value = videoElement.value.currentTime
    }
  }

  function seekRelative(offset: number): void {
    seek(currentTime.value + offset)
  }

  function setVolume(val: number): void {
    const v = Math.max(0, Math.min(1, val))
    volume.value = v
    if (videoElement.value) {
      videoElement.value.volume = v
    }
    if (v > 0) muted.value = false
  }

  function toggleMute(): void {
    muted.value = !muted.value
    if (videoElement.value) {
      videoElement.value.muted = muted.value
    }
  }

  function setPlaybackRate(rate: number): void {
    playbackRate.value = rate
    if (videoElement.value) {
      videoElement.value.playbackRate = rate
    }
  }

  function setResolution(resolution: string): void {
    currentResolution.value = resolution
    if (videoElement.value) {
      const currentTimeSnapshot = currentTime.value
      const wasPlaying = playing.value

      refreshToken().then(() => {
        if (videoElement.value) {
          videoElement.value.load()
          videoElement.value.currentTime = currentTimeSnapshot
          if (wasPlaying) {
            videoElement.value.play()
          }
        }
      })
    }
  }

  function setSubtitle(lang: string): void {
    activeSubtitle.value = lang === activeSubtitle.value ? null : lang
  }

  async function toggleFullscreen(): Promise<void> {
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

  async function togglePiP(): Promise<void> {
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

  function onLoadedMetadata(event: Event): void {
    const video = event.target as HTMLVideoElement
    duration.value = video.duration
    if (savedProgress.value?.current_time && savedProgress.value.current_time < video.duration - 10) {
      video.currentTime = savedProgress.value.current_time
    }
  }

  function onTimeUpdate(event: Event): void {
    const video = event.target as HTMLVideoElement
    currentTime.value = video.currentTime
    buffered.value = video.buffered.length > 0
      ? video.buffered.end(video.buffered.length - 1)
      : 0
    const ranges: BufferedRange[] = []
    for (let i = 0; i < video.buffered.length; i++) {
      ranges.push({ start: video.buffered.start(i), end: video.buffered.end(i) })
    }
    bufferedRanges.value = ranges
  }

  function onWaiting(): void {
    buffering.value = true
  }

  function onCanPlay(): void {
    buffering.value = false
  }

  function onPlaying(): void {
    playing.value = true
    paused.value = false
    buffering.value = false
    ended.value = false
  }

  function onPause(): void {
    playing.value = false
    paused.value = true
  }

  function onEnded(): void {
    playing.value = false
    paused.value = true
    ended.value = true
    if (currentFile.value) {
      recordVideoHistoryApi(currentFile.value.node_id, {
        watchedDuration: duration.value,
        totalDuration: duration.value,
        completed: true
      }).catch(() => {})
    }
  }

  function onSeeking(): void {
    seeking.value = true
  }

  function onSeeked(): void {
    seeking.value = false
  }

  function onError(event: Event): void {
    const video = event.target as HTMLVideoElement
    let message = '视频播放出错'
    if (video?.error) {
      const errorCodes: Record<number, string> = {
        1: '视频加载被中止',
        2: '网络错误导致视频加载失败',
        3: '视频解码失败',
        4: '视频格式不支持或文件损坏',
      }
      message = errorCodes[video.error.code] || message
    }
    error.value = { title: '播放错误', message }
  }

  function resetPlaybackState(): void {
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

  /** 显示控制栏 */
  function showControls(): void {
    controlsVisible.value = true
    if (controlsTimer.value) {
      clearTimeout(controlsTimer.value)
      controlsTimer.value = null
    }
    if (playing.value) {
      controlsTimer.value = setTimeout(() => {
        controlsVisible.value = false
      }, 3000)
    }
  }

  /** 隐藏控制栏 */
  function hideControls(): void {
    controlsVisible.value = false
    if (controlsTimer.value) {
      clearTimeout(controlsTimer.value)
      controlsTimer.value = null
    }
  }

  /** 保存播放进度 */
  async function saveProgress(): Promise<void> {
    if (!currentFile.value || duration.value <= 0) return
    try {
      await saveVideoProgressApi(currentFile.value.node_id, {
        currentTime: currentTime.value,
        duration: duration.value,
        resolution: currentResolution.value,
        playbackRate: playbackRate.value,
      })
    } catch {
      // 静默失败
    }
  }

  /** 重置整个播放器状态 */
  function reset(): void {
    resetPlaybackState()
    if (hlsInstance.value) {
      hlsInstance.value.destroy()
      hlsInstance.value = null
    }
    if (controlsTimer.value) {
      clearTimeout(controlsTimer.value)
      controlsTimer.value = null
    }
    loading.value = false
    error.value = null
    streamInfo.value = null
    spriteInfo.value = null
    subtitles.value = []
    activeSubtitle.value = null
    streamToken.value = ''
    tokenExpiresAt.value = 0
    currentFile.value = null
    currentResolution.value = 'auto'
    playbackRate.value = 1
    volume.value = 1
    muted.value = false
    isFullscreen.value = false
    isPiP.value = false
    controlsVisible.value = true
    watchHistory.value = []
  }

  return {
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
    controlsTimer,
    videoElement,
    hlsInstance,
    savedProgress,
    watchHistory,
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
    showControls,
    hideControls,
    saveProgress,
    reset,
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
    resetPlaybackState,
  }
})