/**
 * pages/VideoPlayer/VideoPlayer.jsx - 企业级视频播放器页面
 *
 * 功能对标 Bilibili / YouTube 视频播放体验:
 *   - MP4 流媒体 (HTTP Range 支持 Seek)
 *   - HLS 流媒体 (hls.js 驱动)
 *   - 多分辨率切换 (4K / 1080P / 720P / 480P / 360P)
 *   - 播放速度控制 (0.25x ~ 2x)
 *   - 高级进度条: 缓冲区域、悬停时间预览、拖拽 Seek
 *   - 音量控制 + 静音
 *   - 全屏 / 网页全屏 / 画中画
 *   - 键盘快捷键 (Space, ←→, ↑↓, F, M, N, [, ])
 *   - 自动隐藏控制栏
 *   - 加载中 / 缓冲中 / 错误状态
 *   - 右键菜单
 *   - 设置面板
 *   - 触屏设备支持
 *   - 断点续播
 *
 * 后端对接:
 *   - 后端实现后，替换 getMockStreamInfo 为 getVideoStreamInfo
 *   - 通过 requestVideoToken 获取签名 URL
 *   - 播放进度通过 reportPlayProgress 上报
 *
 * 依赖: hls.js (npm install hls.js)
 */

import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { Button, Space, Spin, message, Slider, Tooltip, Dropdown, Modal } from 'antd'
import {
  PlayCircleOutlined, PauseCircleOutlined, SoundOutlined, MutedOutlined,
  FullscreenOutlined, FullscreenExitOutlined, SettingOutlined,
  PictureOutlined, StepBackwardOutlined, StepForwardOutlined,
  ArrowLeftOutlined, ReloadOutlined, ExpandOutlined, CompressOutlined,
  LoadingOutlined, CustomerServiceOutlined, CloseOutlined
} from '@ant-design/icons'
import { getFileDetail } from '@/api/file'
import {
  getVideoStreamInfo, getVideoSpriteInfo, getVideoCoverUrl,
  buildStreamUrl, buildHlsUrl, buildHlsResolutionUrl,
  requestVideoToken, reportPlayProgress, getPlayProgress,
  getMockStreamInfo
} from '@/api/video'
import { formatFileSize, formatTime } from '@/utils/helper'
import './VideoPlayer.css'

// ==================== 常量 ====================

const PLAYBACK_SPEEDS = [0.25, 0.5, 0.75, 1, 1.25, 1.5, 1.75, 2]

const SEEK_STEP = 5     // 左右方向键快进步长 (秒)
const VOLUME_STEP = 0.05 // 上下方向键音量步长

const CONTROLS_HIDE_DELAY = 3000  // 鼠标不动后隐藏控制栏 (ms)
const PROGRESS_REPORT_INTERVAL = 5000  // 播放进度上报间隔 (ms)

// ==================== 工具函数 ====================

/** 格式化时间为 HH:MM:SS / MM:SS */
function formatVideoTime(seconds) {
  if (!seconds || seconds < 0 || !isFinite(seconds)) return '00:00'
  const h = Math.floor(seconds / 3600)
  const m = Math.floor((seconds % 3600) / 60)
  const s = Math.floor(seconds % 60)
  const pad = (n) => String(n).padStart(2, '0')
  if (h > 0) return `${pad(h)}:${pad(m)}:${pad(s)}`
  return `${pad(m)}:${pad(s)}`
}

/** 计算百分比 */
function clampPercent(v) {
  return Math.max(0, Math.min(100, v))
}

// ==================== 组件 ====================

export default function VideoPlayerPage() {
  const { fileId } = useParams()
  const navigate = useNavigate()

  // ================== Refs ==================
  const containerRef = useRef(null)       // 播放器容器
  const videoRef = useRef(null)           // <video> 元素
  const progressRef = useRef(null)        // 进度条容器
  const controlsTimerRef = useRef(null)   // 控制栏隐藏定时器
  const hlsInstanceRef = useRef(null)     // HLS.js 实例
  const progressReportRef = useRef(null)  // 进度上报定时器
  const lastVolumeRef = useRef(1)         // 静音前音量

  // ================== 核心状态 ==================
  const [fileInfo, setFileInfo] = useState(null)       // 文件基本信息
  const [streamInfo, setStreamInfo] = useState(null)   // 流媒体信息
  const [loading, setLoading] = useState(true)          // 页面加载
  const [error, setError] = useState(null)              // 错误信息

  // ================== 播放状态 ==================
  const [playing, setPlaying] = useState(false)         // 是否播放中
  const [buffering, setBuffering] = useState(false)     // 是否缓冲中
  const [currentTime, setCurrentTime] = useState(0)     // 当前播放时间
  const [duration, setDuration] = useState(0)           // 总时长
  const [buffered, setBuffered] = useState([])          // 缓冲区间 [{ start, end }]
  const [volume, setVolume] = useState(1)               // 音量 0~1
  const [muted, setMuted] = useState(false)             // 是否静音
  const [playbackRate, setPlaybackRate] = useState(1)   // 播放速度
  const [currentResolution, setCurrentResolution] = useState(null) // 当前分辨率
  const [isFullscreen, setIsFullscreen] = useState(false)
  const [isPiP, setIsPiP] = useState(false)

  // ================== UI 状态 ==================
  const [controlsVisible, setControlsVisible] = useState(true)  // 控制栏是否可见
  const [showSettings, setShowSettings] = useState(false)        // 设置面板
  const [isSeeking, setIsSeeking] = useState(false)              // 是否正在拖拽进度条
  const [hoverTime, setHoverTime] = useState(null)               // 进度条悬停时间
  const [hoverPosition, setHoverPosition] = useState(null)       // 进度条悬停位置 (px)
  const [hasUserInteracted, setHasUserInteracted] = useState(false) // 用户是否已交互

  // ================== 初始化 ==================

  useEffect(() => {
    if (!fileId) return
    initPlayer()
    return () => cleanup()
  }, [fileId])

  const initPlayer = async () => {
    setLoading(true)
    setError(null)
    try {
      // 1. 获取文件基本信息
      const fileRes = await getFileDetail(fileId)
      const fInfo = fileRes.data || fileRes
      setFileInfo(fInfo)

      // 2. 获取流媒体信息 (后端未对接时用模拟数据)
      let sInfo
      try {
        const streamRes = await getVideoStreamInfo(fileId)
        sInfo = streamRes.data || streamRes
      } catch {
        // 后端未对接，使用模拟数据
        sInfo = getMockStreamInfo(fInfo)
      }
      setStreamInfo(sInfo)

      // 3. 设置默认分辨率 (1080P 或最高可用)
      const defaultRes = sInfo.resolutions?.find(r => r.height === 1080)
        || sInfo.resolutions?.find(r => r.height === 720)
        || sInfo.resolutions?.[0]
      if (defaultRes) {
        setCurrentResolution(defaultRes)
      }

      // 4. 尝试恢复播放进度
      try {
        const progressRes = await getPlayProgress(fileId)
        const savedTime = progressRes.data?.current_time || progressRes?.current_time
        if (savedTime > 0) {
          setCurrentTime(savedTime)
        }
      } catch { /* 忽略 */ }

    } catch (e) {
      setError(e.message || '加载视频信息失败')
    } finally {
      setLoading(false)
    }
  }

  const cleanup = () => {
    // 销毁 HLS 实例
    if (hlsInstanceRef.current) {
      hlsInstanceRef.current.destroy()
      hlsInstanceRef.current = null
    }
    // 清除定时器
    if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current)
    if (progressReportRef.current) clearInterval(progressReportRef.current)
    // 退出全屏
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {})
    }
    // 退出画中画
    if (document.pictureInPictureElement) {
      document.exitPictureInPicture().catch(() => {})
    }
  }

  // ================== 视频源加载 ==================

  useEffect(() => {
    if (!streamInfo || !currentResolution) return
    const video = videoRef.current
    if (!video) return

    loadVideoSource(video, currentResolution, streamInfo)
  }, [streamInfo, currentResolution])

  const loadVideoSource = async (video, resolution, sInfo) => {
    setBuffering(true)

    // 销毁旧 HLS 实例
    if (hlsInstanceRef.current) {
      hlsInstanceRef.current.destroy()
      hlsInstanceRef.current = null
    }

    try {
      // 尝试获取操作凭证
      let opToken = ''
      try {
        const tokenRes = await requestVideoToken(fileId)
        opToken = tokenRes.data?.operation_token || tokenRes.data
      } catch { /* 后端未对接 */ }

      const isHls = resolution.type === 'hls'

      if (isHls && typeof Hls !== 'undefined') {
        // HLS.js 播放
        const hlsUrl = opToken
          ? buildHlsResolutionUrl(fileId, opToken, resolution.height)
          : resolution.url
        initHls(video, hlsUrl)
      } else if (isHls && video.canPlayType('application/vnd.apple.mpegurl')) {
        // Safari 原生 HLS
        const hlsUrl = opToken
          ? buildHlsResolutionUrl(fileId, opToken, resolution.height)
          : resolution.url
        video.src = hlsUrl
      } else {
        // MP4 直链 (支持 Range)
        const streamUrl = opToken
          ? buildStreamUrl(fileId, opToken, resolution.height)
          : resolution.url || `http://localhost:8000/downloads/files/${fileId}/content?token=${opToken}`
        video.src = streamUrl
      }

      video.load()

      // 恢复播放进度
      if (currentTime > 0 && video.duration) {
        video.currentTime = currentTime
      }

    } catch (e) {
      setError(`视频源加载失败: ${e.message}`)
    }
  }

  /** 初始化 HLS.js 播放 */
  const initHls = (video, url) => {
    if (hlsInstanceRef.current) {
      hlsInstanceRef.current.destroy()
    }

    const hls = new Hls({
      enableWorker: true,
      lowLatencyMode: false,
      backBufferLength: 90,
      maxBufferLength: 30,
      maxMaxBufferLength: 60,
      maxBufferSize: 60 * 1000 * 1000,
      maxBufferHole: 0.5,
      highBufferWatchdogPeriod: 2,
      nudgeOffset: 0.1,
      nudgeMaxRetry: 3,
      maxFragLookUpTolerance: 0.25,
      liveSyncDurationCount: 3,
      abrEwmaFastLive: 3,
      abrEwmaSlowLive: 9,
      abrEwmaFastVoD: 3,
      abrEwmaSlowVoD: 9,
      abrEwmaDefaultEstimate: 500000,
      abrBandWidthFactor: 0.95,
      abrBandWidthUpFactor: 0.7,
      abrMaxWithRealBitrate: true,
      maxStarvationDelay: 4,
      maxLoadingDelay: 4,
      manifestLoadingTimeOut: 10000,
      manifestLoadingMaxRetry: 1,
      levelLoadingTimeOut: 10000,
      levelLoadingMaxRetry: 4,
      fragLoadingTimeOut: 20000,
      fragLoadingMaxRetry: 6,
      startFragPrefetch: false,
      testBandwidth: true,
      progressive: false,
    })

    hls.loadSource(url)
    hls.attachMedia(video)

    hls.on(Hls.Events.MANIFEST_PARSED, () => {
      // HLS 清单解析成功，可以播放
      if (currentTime > 0) {
        video.currentTime = currentTime
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
            setError('视频流加载失败，请尝试切换分辨率')
            break
        }
      }
    })

    hlsInstanceRef.current = hls
  }

  // ================== 视频事件处理 ==================

  const handleVideoEvents = useCallback(() => {
    const video = videoRef.current
    if (!video) return

    const onLoadStart = () => setBuffering(true)
    const onCanPlay = () => setBuffering(false)
    const onWaiting = () => setBuffering(true)
    const onPlaying = () => {
      setBuffering(false)
      setPlaying(true)
      startProgressReport()
    }
    const onPause = () => {
      setPlaying(false)
      stopProgressReport()
    }
    const onEnded = () => {
      setPlaying(false)
      stopProgressReport()
    }
    const onTimeUpdate = () => {
      if (!isSeeking) {
        setCurrentTime(video.currentTime)
      }
    }
    const onDurationChange = () => {
      setDuration(video.duration || 0)
    }
    const onProgress = () => {
      const buf = video.buffered
      const ranges = []
      for (let i = 0; i < buf.length; i++) {
        ranges.push({ start: buf.start(i), end: buf.end(i) })
      }
      setBuffered(ranges)
    }
    const onError = () => {
      setBuffering(false)
      const err = video.error
      if (err) {
        const msg = err.code === 4
          ? '视频源不可用，请尝试切换分辨率'
          : `播放错误 [${err.code}]: ${err.message || '未知错误'}`
        setError(msg)
      }
    }
    const onVolumeChange = () => {
      setVolume(video.volume)
      setMuted(video.muted)
    }

    video.addEventListener('loadstart', onLoadStart)
    video.addEventListener('canplay', onCanPlay)
    video.addEventListener('waiting', onWaiting)
    video.addEventListener('playing', onPlaying)
    video.addEventListener('pause', onPause)
    video.addEventListener('ended', onEnded)
    video.addEventListener('timeupdate', onTimeUpdate)
    video.addEventListener('durationchange', onDurationChange)
    video.addEventListener('progress', onProgress)
    video.addEventListener('error', onError)
    video.addEventListener('volumechange', onVolumeChange)

    return () => {
      video.removeEventListener('loadstart', onLoadStart)
      video.removeEventListener('canplay', onCanPlay)
      video.removeEventListener('waiting', onWaiting)
      video.removeEventListener('playing', onPlaying)
      video.removeEventListener('pause', onPause)
      video.removeEventListener('ended', onEnded)
      video.removeEventListener('timeupdate', onTimeUpdate)
      video.removeEventListener('durationchange', onDurationChange)
      video.removeEventListener('progress', onProgress)
      video.removeEventListener('error', onError)
      video.removeEventListener('volumechange', onVolumeChange)
    }
  }, [isSeeking, fileId])

  // 绑定视频事件
  useEffect(() => {
    const cleanup = handleVideoEvents()
    return cleanup
  }, [handleVideoEvents])

  // 监听全屏变化
  useEffect(() => {
    const handler = () => {
      setIsFullscreen(!!document.fullscreenElement)
    }
    document.addEventListener('fullscreenchange', handler)
    return () => document.removeEventListener('fullscreenchange', handler)
  }, [])

  // ================== 播放进度上报 ==================

  const startProgressReport = () => {
    stopProgressReport()
    progressReportRef.current = setInterval(() => {
      const video = videoRef.current
      if (video && video.currentTime > 0 && video.duration > 0) {
        reportPlayProgress(fileId, video.currentTime, video.duration)
          .catch(() => {}) // 静默失败
      }
    }, PROGRESS_REPORT_INTERVAL)
  }

  const stopProgressReport = () => {
    if (progressReportRef.current) {
      clearInterval(progressReportRef.current)
      progressReportRef.current = null
      // 最后上报一次
      const video = videoRef.current
      if (video && video.currentTime > 0) {
        reportPlayProgress(fileId, video.currentTime, video.duration || 0)
          .catch(() => {})
      }
    }
  }

  // ================== 播放控制 ==================

  const togglePlay = useCallback(() => {
    const video = videoRef.current
    if (!video) return

    if (video.paused || video.ended) {
      if (video.ended) {
        video.currentTime = 0
      }
      video.play().catch(() => {})
    } else {
      video.pause()
    }
  }, [])

  const seekTo = useCallback((time) => {
    const video = videoRef.current
    if (!video) return
    const target = Math.max(0, Math.min(time, duration || video.duration || 0))
    video.currentTime = target
    setCurrentTime(target)
  }, [duration])

  const skipBackward = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    seekTo(video.currentTime - SEEK_STEP)
  }, [seekTo])

  const skipForward = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    seekTo(video.currentTime + SEEK_STEP)
  }, [seekTo])

  const changeVolume = useCallback((newVol) => {
    const video = videoRef.current
    if (!video) return
    const v = Math.max(0, Math.min(1, newVol))
    video.volume = v
    video.muted = v === 0
    setVolume(v)
    setMuted(v === 0)
    if (v > 0) lastVolumeRef.current = v
  }, [])

  const toggleMute = useCallback(() => {
    const video = videoRef.current
    if (!video) return
    if (video.muted) {
      video.muted = false
      video.volume = lastVolumeRef.current || 1
      setMuted(false)
      setVolume(lastVolumeRef.current || 1)
    } else {
      lastVolumeRef.current = video.volume
      video.muted = true
      setMuted(true)
    }
  }, [])

  const changePlaybackRate = useCallback((rate) => {
    const video = videoRef.current
    if (!video) return
    video.playbackRate = rate
    setPlaybackRate(rate)
  }, [])

  const changeResolution = useCallback((resolution) => {
    const video = videoRef.current
    if (!video) return
    const prevTime = video.currentTime
    const wasPlaying = !video.paused
    setCurrentTime(prevTime)
    setCurrentResolution(resolution)
    // 切换分辨率后恢复播放位置
    setTimeout(() => {
      if (videoRef.current && prevTime > 0) {
        videoRef.current.currentTime = prevTime
        if (wasPlaying) {
          videoRef.current.play().catch(() => {})
        }
      }
    }, 500)
  }, [])

  const toggleFullscreen = useCallback(() => {
    const container = containerRef.current
    if (!container) return

    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {})
    } else {
      container.requestFullscreen().catch(() => {})
    }
  }, [])

  const togglePiP = useCallback(async () => {
    const video = videoRef.current
    if (!video) return

    try {
      if (document.pictureInPictureElement) {
        await document.exitPictureInPicture()
        setIsPiP(false)
      } else {
        await video.requestPictureInPicture()
        setIsPiP(true)
      }
    } catch (e) {
      message.error('画中画功能不可用')
    }
  }, [])

  // ================== 进度条交互 ==================

  const handleProgressClick = useCallback((e) => {
    const rect = progressRef.current.getBoundingClientRect()
    const ratio = (e.clientX - rect.left) / rect.width
    const time = ratio * (duration || 0)
    seekTo(time)
  }, [duration, seekTo])

  const handleProgressHover = useCallback((e) => {
    const rect = progressRef.current.getBoundingClientRect()
    const x = e.clientX - rect.left
    const ratio = Math.max(0, Math.min(1, x / rect.width))
    setHoverPosition(x)
    setHoverTime(ratio * (duration || 0))
  }, [duration])

  const handleProgressLeave = useCallback(() => {
    setHoverPosition(null)
    setHoverTime(null)
  }, [])

  // 拖拽进度条
  const handleProgressDragStart = useCallback(() => {
    setIsSeeking(true)
  }, [])

  const handleProgressDrag = useCallback((value) => {
    const time = (value / 100) * (duration || 0)
    setCurrentTime(time)
  }, [duration])

  const handleProgressDragEnd = useCallback((value) => {
    const time = (value / 100) * (duration || 0)
    seekTo(time)
    setIsSeeking(false)
  }, [duration, seekTo])

  // ================== 控制栏自动隐藏 ==================

  const showControls = useCallback(() => {
    setControlsVisible(true)
    if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current)
    if (playing) {
      controlsTimerRef.current = setTimeout(() => {
        setControlsVisible(false)
      }, CONTROLS_HIDE_DELAY)
    }
  }, [playing])

  const hideControlsImmediate = useCallback(() => {
    if (controlsTimerRef.current) clearTimeout(controlsTimerRef.current)
    if (playing) {
      setControlsVisible(false)
    }
  }, [playing])

  // 鼠标移动时显示控制栏
  const handleMouseMove = useCallback(() => {
    showControls()
  }, [showControls])

  // ================== 键盘快捷键 ==================

  useEffect(() => {
    const handler = (e) => {
      // 防止在输入框中触发
      if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA' || e.target.isContentEditable) {
        return
      }

      const video = videoRef.current
      if (!video) return

      switch (e.key) {
        case ' ':
        case 'k':
          e.preventDefault()
          togglePlay()
          showControls()
          break
        case 'ArrowLeft':
          e.preventDefault()
          seekTo(video.currentTime - SEEK_STEP)
          showControls()
          break
        case 'ArrowRight':
          e.preventDefault()
          seekTo(video.currentTime + SEEK_STEP)
          showControls()
          break
        case 'ArrowUp':
          e.preventDefault()
          changeVolume(video.volume + VOLUME_STEP)
          showControls()
          break
        case 'ArrowDown':
          e.preventDefault()
          changeVolume(video.volume - VOLUME_STEP)
          showControls()
          break
        case 'f':
          e.preventDefault()
          toggleFullscreen()
          break
        case 'm':
          e.preventDefault()
          toggleMute()
          showControls()
          break
        case '[':
          e.preventDefault()
          changePlaybackRate(Math.max(0.25, playbackRate - 0.25))
          break
        case ']':
          e.preventDefault()
          changePlaybackRate(Math.min(2, playbackRate + 0.25))
          break
        case 'n':
          e.preventDefault()
          skipForward()
          showControls()
          break
        case 'p':
          e.preventDefault()
          skipBackward()
          showControls()
          break
        case 'Escape':
          if (document.fullscreenElement) {
            document.exitFullscreen().catch(() => {})
          }
          break
        default:
          break
      }
    }

    window.addEventListener('keydown', handler)
    return () => window.removeEventListener('keydown', handler)
  }, [togglePlay, seekTo, changeVolume, toggleFullscreen, toggleMute,
    changePlaybackRate, playbackRate, skipForward, skipBackward, showControls])

  // ================== 进度条百分比计算 ==================

  const playedPercent = useMemo(() => {
    if (!duration || duration === 0) return 0
    return clampPercent((currentTime / duration) * 100)
  }, [currentTime, duration])

  const bufferedPercent = useMemo(() => {
    if (!duration || duration === 0) return 0
    let maxEnd = 0
    for (const range of buffered) {
      if (range.end > maxEnd) maxEnd = range.end
    }
    return clampPercent((maxEnd / duration) * 100)
  }, [buffered, duration])

  // ================== 渲染辅助 ==================

  const fileName = fileInfo?.file_name || fileInfo?.name || '视频播放'
  const title = streamInfo?.title || fileName.replace(/\.[^.]+$/, '')

  // ================== 页面加载状态 ==================

  if (loading) {
    return (
      <div className="vp-page">
        <div className="vp-loading-overlay">
          <Spin size="large" indicator={<LoadingOutlined style={{ fontSize: 48 }} spin />} />
          <p style={{ marginTop: 16, color: '#aaa' }}>加载视频信息...</p>
        </div>
      </div>
    )
  }

  // ================== 渲染 ==================

  return (
    <div className="vp-page">
      {/* 顶部返回栏 */}
      <div className="vp-top-bar">
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate(-1)}
          className="vp-back-btn"
        >
          返回
        </Button>
        <span className="vp-title">{title}</span>
        <span className="vp-file-info">
          {fileInfo && formatFileSize(fileInfo.file_size || fileInfo.size)}
        </span>
      </div>

      {/* 视频播放器容器 */}
      <div
        ref={containerRef}
        className={`vp-container ${isFullscreen ? 'vp-fullscreen' : ''}`}
        onMouseMove={handleMouseMove}
        onMouseLeave={hideControlsImmediate}
        onClick={(e) => {
          // 双击切换全屏
          if (e.detail === 2) {
            toggleFullscreen()
          }
        }}
      >
        {/* 视频元素 */}
        <video
          ref={videoRef}
          className="vp-video"
          playsInline
          crossOrigin="anonymous"
          preload="auto"
          onClick={(e) => {
            e.stopPropagation()
            togglePlay()
          }}
        >
          您的浏览器不支持 HTML5 视频播放
        </video>

        {/* 中央大播放按钮 (暂停时显示) */}
        {!playing && !buffering && !error && (
          <div className="vp-center-play" onClick={(e) => { e.stopPropagation(); togglePlay() }}>
            <PlayCircleOutlined style={{ fontSize: 72, color: 'rgba(255,255,255,0.9)' }} />
          </div>
        )}

        {/* 缓冲中遮罩 */}
        {buffering && playing && (
          <div className="vp-buffering-overlay">
            <Spin indicator={<LoadingOutlined style={{ fontSize: 36, color: '#fff' }} spin />} />
          </div>
        )}

        {/* 错误遮罩 */}
        {error && (
          <div className="vp-error-overlay">
            <div className="vp-error-content">
              <p className="vp-error-text">{error}</p>
              <Space>
                <Button
                  type="primary"
                  icon={<ReloadOutlined />}
                  onClick={() => { setError(null); loadVideoSource(videoRef.current, currentResolution, streamInfo) }}
                >
                  重试
                </Button>
                {streamInfo?.resolutions?.length > 1 && (
                  <Button onClick={() => {
                    setError(null)
                    const idx = streamInfo.resolutions.findIndex(r => r.height === currentResolution?.height)
                    const next = streamInfo.resolutions[(idx + 1) % streamInfo.resolutions.length]
                    changeResolution(next)
                  }}>
                    切换分辨率
                  </Button>
                )}
              </Space>
            </div>
          </div>
        )}

        {/* 底部控制栏 */}
        <div className={`vp-controls ${controlsVisible ? 'vp-controls-visible' : 'vp-controls-hidden'}`}>
          {/* 进度条 */}
          <div
            ref={progressRef}
            className="vp-progress-bar"
            onMouseMove={handleProgressHover}
            onMouseLeave={handleProgressLeave}
            onClick={(e) => { e.stopPropagation(); handleProgressClick(e) }}
          >
            {/* 缓冲区域 */}
            <div className="vp-progress-buffered" style={{ width: `${bufferedPercent}%` }} />
            {/* 已播放区域 */}
            <div className="vp-progress-played" style={{ width: `${playedPercent}%` }} />
            {/* 进度滑块 */}
            <div
              className="vp-progress-thumb"
              style={{ left: `${playedPercent}%` }}
            />
            {/* 悬停时间提示 */}
            {hoverTime !== null && hoverPosition !== null && (
              <div
                className="vp-progress-tooltip"
                style={{ left: hoverPosition }}
              >
                {formatVideoTime(hoverTime)}
              </div>
            )}
          </div>

          {/* 控制按钮行 */}
          <div className="vp-controls-row">
            {/* 左侧 */}
            <div className="vp-controls-left">
              <Tooltip title={playing ? '暂停 (Space)' : '播放 (Space)'}>
                <Button
                  type="text"
                  size="small"
                  icon={playing ? <PauseCircleOutlined /> : <PlayCircleOutlined />}
                  onClick={(e) => { e.stopPropagation(); togglePlay() }}
                  className="vp-btn"
                />
              </Tooltip>

              <Tooltip title="快退 5 秒 (←)">
                <Button
                  type="text"
                  size="small"
                  icon={<StepBackwardOutlined />}
                  onClick={(e) => { e.stopPropagation(); skipBackward() }}
                  className="vp-btn"
                />
              </Tooltip>

              <Tooltip title="快进 5 秒 (→)">
                <Button
                  type="text"
                  size="small"
                  icon={<StepForwardOutlined />}
                  onClick={(e) => { e.stopPropagation(); skipForward() }}
                  className="vp-btn"
                />
              </Tooltip>

              {/* 音量控制 */}
              <div className="vp-volume-control">
                <Tooltip title={muted ? '取消静音 (M)' : '静音 (M)'}>
                  <Button
                    type="text"
                    size="small"
                    icon={muted || volume === 0 ? <MutedOutlined /> : <SoundOutlined />}
                    onClick={(e) => { e.stopPropagation(); toggleMute() }}
                    className="vp-btn"
                  />
                </Tooltip>
                <div className="vp-volume-slider" onClick={(e) => e.stopPropagation()}>
                  <Slider
                    min={0}
                    max={100}
                    value={muted ? 0 : volume * 100}
                    onChange={(v) => changeVolume(v / 100)}
                    tooltip={{ formatter: (v) => `${v}%` }}
                    className="vp-slider"
                  />
                </div>
              </div>

              {/* 时间显示 */}
              <span className="vp-time-display">
                <span className="vp-time-current">{formatVideoTime(currentTime)}</span>
                <span className="vp-time-separator"> / </span>
                <span className="vp-time-duration">
                  {duration > 0 ? formatVideoTime(duration) : '--:--'}
                </span>
              </span>
            </div>

            {/* 右侧 */}
            <div className="vp-controls-right">
              {/* 播放速度 */}
              <Dropdown
                menu={{
                  items: PLAYBACK_SPEEDS.map(speed => ({
                    key: speed,
                    label: `${speed}x`,
                    onClick: () => changePlaybackRate(speed)
                  })),
                  selectedKeys: [String(playbackRate)]
                }}
                trigger={['click']}
                placement="top"
              >
                <Button
                  type="text"
                  size="small"
                  className="vp-btn vp-speed-btn"
                  onClick={(e) => e.stopPropagation()}
                >
                  倍速 {playbackRate}x
                </Button>
              </Dropdown>

              {/* 分辨率选择 */}
              {streamInfo?.resolutions?.length > 0 && (
                <Dropdown
                  menu={{
                    items: streamInfo.resolutions.map(res => ({
                      key: res.height,
                      label: (
                        <span>
                          {res.label}
                          {res.bitrate > 0 && (
                            <span style={{ color: '#999', fontSize: 11, marginLeft: 8 }}>
                              {res.bitrate >= 1000 ? `${(res.bitrate / 1000).toFixed(1)}Mbps` : `${res.bitrate}Kbps`}
                            </span>
                          )}
                        </span>
                      ),
                      onClick: () => changeResolution(res)
                    })),
                    selectedKeys: [String(currentResolution?.height || '')]
                  }}
                  trigger={['click']}
                  placement="top"
                >
                  <Button
                    type="text"
                    size="small"
                    className="vp-btn"
                    onClick={(e) => e.stopPropagation()}
                  >
                    {currentResolution?.label || '自动'}
                  </Button>
                </Dropdown>
              )}

              {/* 设置 */}
              <Dropdown
                menu={{
                  items: [
                    {
                      key: 'quality',
                      label: '画质',
                      children: (streamInfo?.resolutions || []).map(res => ({
                        key: `q-${res.height}`,
                        label: `${res.label} ${res.bitrate > 0 ? `(${res.bitrate >= 1000 ? (res.bitrate/1000).toFixed(1)+'Mbps' : res.bitrate+'Kbps'})` : ''}`,
                        onClick: () => changeResolution(res)
                      }))
                    },
                    {
                      key: 'speed',
                      label: '播放速度',
                      children: PLAYBACK_SPEEDS.map(s => ({
                        key: `s-${s}`,
                        label: `${s}x`,
                        onClick: () => changePlaybackRate(s)
                      }))
                    },
                    { type: 'divider' },
                    {
                      key: 'loop',
                      label: '循环播放',
                      onClick: () => {
                        const video = videoRef.current
                        if (video) {
                          video.loop = !video.loop
                          message.info(video.loop ? '已开启循环播放' : '已关闭循环播放')
                        }
                      }
                    }
                  ]
                }}
                trigger={['click']}
                placement="top"
              >
                <Tooltip title="设置">
                  <Button
                    type="text"
                    size="small"
                    icon={<SettingOutlined />}
                    className="vp-btn"
                    onClick={(e) => e.stopPropagation()}
                  />
                </Tooltip>
              </Dropdown>

              {/* 画中画 */}
              <Tooltip title="画中画">
                <Button
                  type="text"
                  size="small"
                  icon={<PictureOutlined />}
                  onClick={(e) => { e.stopPropagation(); togglePiP() }}
                  className="vp-btn"
                />
              </Tooltip>

              {/* 全屏 */}
              <Tooltip title={isFullscreen ? '退出全屏 (F)' : '全屏 (F)'}>
                <Button
                  type="text"
                  size="small"
                  icon={isFullscreen ? <FullscreenExitOutlined /> : <FullscreenOutlined />}
                  onClick={(e) => { e.stopPropagation(); toggleFullscreen() }}
                  className="vp-btn"
                />
              </Tooltip>
            </div>
          </div>
        </div>
      </div>

      {/* 视频信息面板 */}
      {fileInfo && (
        <div className="vp-info-panel">
          <div className="vp-info-title">{title}</div>
          <div className="vp-info-meta">
            <span>文件大小: {formatFileSize(fileInfo.file_size || fileInfo.size)}</span>
            <span>上传时间: {formatTime(fileInfo.uploaded_time || fileInfo.updated_at)}</span>
            {streamInfo?.resolutions && (
              <span>可用分辨率: {streamInfo.resolutions.map(r => r.label).join(' / ')}</span>
            )}
          </div>
        </div>
      )}
    </div>
  )
}

// ==================== 导出 ====================

export { formatVideoTime }