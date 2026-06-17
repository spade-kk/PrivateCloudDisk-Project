// ============================================================
// WebRTCService.ts — WebRTC 客户端核心服务
// ============================================================
// 企业级 WebRTC 客户端 SDK，管理整个 WebRTC 通话生命周期。
//
// 核心能力：
// - PeerConnection 创建与管理
// - 媒体流采集（摄像头/麦克风/屏幕共享）
// - SDP 协商（Offer/Answer）
// - ICE Candidate 收集与交换
// - 网络质量实时监控与上报
// - 自适应编码参数调整
// - 通话状态管理
// - 优雅降级（视频 → 语音）
//
// 后端对应：
//   org.project.im.server.signaling.handler.SignalingHandler
//   org.project.im.server.signaling.optimizer.AdaptiveVideoOptimizer
// ============================================================

import type {
  CallSession,
  EncoderParams,
  IceCandidatePayload,
  SdpPayload,
  CallType,
  NetworkQuality,
} from './types'
import { CallStatus } from './types'

// ==================== 类型定义 ====================

/** WebRTC 配置 */
export interface WebRTCConfig {
  /** ICE 服务器配置 */
  iceServers: RTCIceServer[]
  /** ICE 传输策略 */
  iceTransportPolicy?: RTCIceTransportPolicy
  /** ICE Candidate 池大小 */
  iceCandidatePoolSize?: number
  /** 首选编解码器顺序 */
  preferredCodecs?: string[]
  /** 网络质量上报间隔（毫秒） */
  qualityReportInterval?: number
}

/** 媒体流约束 */
export interface MediaConstraints {
  /** 视频约束 */
  video?: boolean | MediaTrackConstraints
  /** 音频约束 */
  audio?: boolean | MediaTrackConstraints
}

/** 发送信令消息的回调 */
export type SignalingSender = (command: number, payload: Record<string, unknown>) => void

/** 通话事件回调 */
export interface CallEventHandlers {
  /** 本地媒体流就绪 */
  onLocalStream?: (stream: MediaStream) => void
  /** 远端媒体流就绪 */
  onRemoteStream?: (stream: MediaStream) => void
  /** 远端媒体流移除 */
  onRemoteStreamRemoved?: (stream: MediaStream) => void
  /** 通话状态变更 */
  onStatusChange?: (status: CallStatus) => void
  /** 编码参数调整 */
  onEncoderAdjust?: (params: EncoderParams) => void
  /** 网络质量变更 */
  onNetworkQualityChange?: (quality: NetworkQuality) => void
  /** 建议降级语音 */
  onSuggestDowngrade?: () => void
  /** 错误回调 */
  onError?: (error: Error) => void
}

/** 网络统计信息 */
interface NetworkStats {
  rtt: number
  packetLoss: number
  jitter: number
  estimatedBandwidth: number
}

// ==================== WebRTC 服务 ====================

export class WebRTCService {
  // ---- 配置 ----
  private config: Required<WebRTCConfig>

  // ---- WebRTC 核心 ----
  private peerConnection: RTCPeerConnection | null = null
  private localStream: MediaStream | null = null
  private remoteStream: MediaStream | null = null
  private screenStream: MediaStream | null = null

  // ---- 通话状态 ----
  private callSession: CallSession | null = null
  private isCaller: boolean = false
  private isMuted: boolean = false
  private isCameraOff: boolean = false
  private isScreenSharing: boolean = false

  // ---- 网络监控 ----
  private qualityMonitorTimer: ReturnType<typeof setInterval> | null = null
  private lastStatsSnapshot: Map<string, RTCStatsReport> = new Map()

  // ---- 回调 ----
  private handlers: CallEventHandlers = {}
  private signalingSender: SignalingSender | null = null

  // ---- ICE Candidate 缓冲 ----
  private pendingIceCandidates: RTCIceCandidateInit[] = []

  /**
   * @param config WebRTC 配置
   */
  constructor(config: WebRTCConfig) {
    this.config = {
      iceTransportPolicy: 'all',
      iceCandidatePoolSize: 2,
      preferredCodecs: ['VP8', 'H264', 'VP9'],
      qualityReportInterval: 2000,
      ...config,
    }
  }

  // ==================== 初始化 ====================

  /** 设置信令发送器 */
  setSignalingSender(sender: SignalingSender): void {
    this.signalingSender = sender
  }

  /** 设置事件回调 */
  setEventHandlers(handlers: CallEventHandlers): void {
    this.handlers = handlers
  }

  /** 获取当前通话会话 */
  getCallSession(): CallSession | null {
    return this.callSession
  }

  /** 获取本地媒体流 */
  getLocalStream(): MediaStream | null {
    return this.localStream
  }

  /** 获取远端媒体流 */
  getRemoteStream(): MediaStream | null {
    return this.remoteStream
  }

  /** 是否静音 */
  getIsMuted(): boolean {
    return this.isMuted
  }

  /** 是否关闭摄像头 */
  getIsCameraOff(): boolean {
    return this.isCameraOff
  }

  /** 是否正在屏幕共享 */
  getIsScreenSharing(): boolean {
    return this.isScreenSharing
  }

  // ==================== 通话发起方（Caller）流程 ====================

  /**
   * 发起通话（作为 Caller）
   *
   * @param calleeId 被叫者 ID
   * @param callType 通话类型
   * @param mediaConstraints 媒体约束
   */
  async startCall(
    calleeId: string,
    callType: CallType,
    mediaConstraints: MediaConstraints = { video: true, audio: true },
  ): Promise<void> {
    this.isCaller = true

    try {
      // 1. 采集本地媒体流
      const videoEnabled = callType === 2 // 视频通话
      await this.acquireLocalMedia({
        video: videoEnabled ? mediaConstraints.video ?? true : false,
        audio: mediaConstraints.audio ?? true,
      })

      // 2. 创建 PeerConnection
      await this.createPeerConnection()

      // 3. 添加本地流到 PeerConnection
      this.addLocalStreamToPeerConnection()

      // 4. 创建 Offer
      const offer = await this.peerConnection!.createOffer({
        offerToReceiveAudio: true,
        offerToReceiveVideo: videoEnabled,
      })
      await this.peerConnection!.setLocalDescription(offer)

      // 5. 发送 Offer SDP 到信令服务器
      this.sendSignaling(2101, { // SIGNALING_OFFER
        calleeId,
        callType,
        sdp: offer,
      })

      // 6. 启动网络质量监控
      this.startQualityMonitor()

      this.handlers.onStatusChange?.(CallStatus.RINGING)
    } catch (error) {
      this.handlers.onError?.(error as Error)
      this.cleanup()
    }
  }

  // ==================== 通话接收方（Callee）流程 ====================

  /**
   * 接收通话（作为 Callee）
   *
   * @param session 通话会话
   * @param mediaConstraints 媒体约束
   */
  async acceptCall(
    session: CallSession,
    mediaConstraints: MediaConstraints = { video: true, audio: true },
  ): Promise<void> {
    this.isCaller = false
    this.callSession = session

    try {
      const videoEnabled = session.callType === 2
      await this.acquireLocalMedia({
        video: videoEnabled ? mediaConstraints.video ?? true : false,
        audio: mediaConstraints.audio ?? true,
      })

      await this.createPeerConnection()
      this.addLocalStreamToPeerConnection()

      // 发送接听确认
      this.sendSignaling(2002, { callId: session.callId }) // CALL_ACCEPT

      this.startQualityMonitor()
      this.handlers.onStatusChange?.(CallStatus.ACTIVE)
    } catch (error) {
      this.handlers.onError?.(error as Error)
      this.cleanup()
    }
  }

  // ==================== SDP 信令处理 ====================

  /**
   * 处理收到的 Offer SDP
   */
  async handleOffer(sdpPayload: SdpPayload): Promise<void> {
    if (!this.peerConnection) {
      await this.createPeerConnection()
      this.addLocalStreamToPeerConnection()
    }

    try {
      await this.peerConnection!.setRemoteDescription(
        new RTCSessionDescription(sdpPayload.sdp),
      )

      const videoEnabled = this.callSession?.callType === 2
      const answer = await this.peerConnection!.createAnswer({
        offerToReceiveAudio: true,
        offerToReceiveVideo: videoEnabled,
      })
      await this.peerConnection!.setLocalDescription(answer)

      // 发送 Answer SDP
      this.sendSignaling(2102, { // SIGNALING_ANSWER
        callId: sdpPayload.callId,
        sdp: answer,
      })

      // 处理缓存的 ICE Candidates
      this.flushPendingIceCandidates()
    } catch (error) {
      this.handlers.onError?.(error as Error)
    }
  }

  /**
   * 处理收到的 Answer SDP
   */
  async handleAnswer(sdpPayload: SdpPayload): Promise<void> {
    if (!this.peerConnection) return

    try {
      await this.peerConnection!.setRemoteDescription(
        new RTCSessionDescription(sdpPayload.sdp),
      )
      this.flushPendingIceCandidates()
    } catch (error) {
      this.handlers.onError?.(error as Error)
    }
  }

  /**
   * 处理收到的 ICE Candidate
   */
  async handleIceCandidate(payload: IceCandidatePayload): Promise<void> {
    const candidate = new RTCIceCandidate(payload.candidate)
    if (this.peerConnection?.remoteDescription) {
      try {
        await this.peerConnection.addIceCandidate(candidate)
      } catch (error) {
        this.handlers.onError?.(error as Error)
      }
    } else {
      // 远程描述尚未设置，缓存 ICE Candidate
      this.pendingIceCandidates.push(payload.candidate)
    }
  }

  /**
   * 处理编码参数调整指令
   */
  handleEncoderAdjust(params: EncoderParams): void {
    this.applyEncoderParams(params)
    this.handlers.onEncoderAdjust?.(params)

    if (this.callSession) {
      this.callSession.encoderParams = params
    }
  }

  /**
   * 处理建议降级语音
   */
  handleSuggestDowngrade(): void {
    this.handlers.onSuggestDowngrade?.()
  }

  // ==================== 通话控制 ====================

  /** 拒绝通话 */
  rejectCall(reason?: string): void {
    if (this.callSession) {
      this.sendSignaling(2003, { // CALL_REJECT
        callId: this.callSession.callId,
        reason: reason || '用户拒绝',
      })
    }
    this.cleanup()
  }

  /** 取消通话 */
  cancelCall(): void {
    if (this.callSession) {
      this.sendSignaling(2004, { // CALL_CANCEL
        callId: this.callSession.callId,
      })
    }
    this.cleanup()
  }

  /** 挂断通话 */
  hangupCall(): void {
    if (this.callSession) {
      this.sendSignaling(2005, { // CALL_HANGUP
        callId: this.callSession.callId,
      })
    }
    this.cleanup()
  }

  /** 切换静音 */
  toggleMute(): void {
    this.isMuted = !this.isMuted
    if (this.localStream) {
      this.localStream.getAudioTracks().forEach(track => {
        track.enabled = !this.isMuted
      })
    }
    if (this.callSession) {
      this.sendSignaling(2303, { // CALL_MUTE_TOGGLE
        callId: this.callSession.callId,
        muted: this.isMuted,
      })
    }
  }

  /** 切换摄像头 */
  toggleCamera(): void {
    this.isCameraOff = !this.isCameraOff
    if (this.localStream) {
      this.localStream.getVideoTracks().forEach(track => {
        track.enabled = !this.isCameraOff
      })
    }
    if (this.callSession) {
      this.sendSignaling(2304, { // CALL_CAMERA_TOGGLE
        callId: this.callSession.callId,
        enabled: !this.isCameraOff,
      })
    }
  }

  /** 切换为语音通话 */
  switchToVoice(): void {
    if (this.localStream) {
      this.localStream.getVideoTracks().forEach(track => {
        track.stop()
        this.localStream!.removeTrack(track)
      })
    }
    this.isCameraOff = true
    if (this.callSession) {
      this.callSession.videoEnabled = false
      this.sendSignaling(2305, { callId: this.callSession.callId }) // CALL_SWITCH_TO_VOICE
    }
  }

  /** 切换为视频通话 */
  async switchToVideo(): Promise<void> {
    try {
      const videoStream = await navigator.mediaDevices.getUserMedia({ video: true })
      videoStream.getVideoTracks().forEach(track => {
        this.localStream?.addTrack(track)
        this.peerConnection?.addTrack(track, this.localStream!)
      })
      this.isCameraOff = false
      if (this.callSession) {
        this.callSession.videoEnabled = true
        this.sendSignaling(2306, { callId: this.callSession.callId }) // CALL_SWITCH_TO_VIDEO
      }
    } catch (error) {
      this.handlers.onError?.(error as Error)
    }
  }

  /** 开始屏幕共享 */
  async startScreenShare(): Promise<void> {
    try {
      this.screenStream = await navigator.mediaDevices.getDisplayMedia({
        video: { frameRate: { ideal: 15 } },
        audio: false,
      })

      // 替换视频轨道为屏幕共享
      const videoTrack = this.screenStream.getVideoTracks()[0]
      const sender = this.peerConnection?.getSenders().find(s => s.track?.kind === 'video')
      if (sender) {
        await sender.replaceTrack(videoTrack)
      }

      // 监听用户停止屏幕共享
      videoTrack.onended = () => this.stopScreenShare()

      this.isScreenSharing = true
      if (this.callSession) {
        this.sendSignaling(2301, { callId: this.callSession.callId }) // CALL_SCREEN_SHARE_START
      }
    } catch (error) {
      this.handlers.onError?.(error as Error)
    }
  }

  /** 停止屏幕共享 */
  stopScreenShare(): void {
    if (this.screenStream) {
      this.screenStream.getTracks().forEach(track => track.stop())
      this.screenStream = null
    }

    // 恢复摄像头
    if (this.localStream) {
      const videoTrack = this.localStream.getVideoTracks()[0]
      if (videoTrack) {
        const sender = this.peerConnection?.getSenders().find(s => s.track?.kind === 'video')
        if (sender) {
          sender.replaceTrack(videoTrack)
        }
      }
    }

    this.isScreenSharing = false
    if (this.callSession) {
      this.sendSignaling(2302, { callId: this.callSession.callId }) // CALL_SCREEN_SHARE_STOP
    }
  }

  // ==================== 清理 ====================

  /** 清理所有资源 */
  cleanup(): void {
    this.stopQualityMonitor()

    // 停止本地流
    if (this.localStream) {
      this.localStream.getTracks().forEach(track => track.stop())
      this.localStream = null
    }

    // 停止屏幕共享流
    if (this.screenStream) {
      this.screenStream.getTracks().forEach(track => track.stop())
      this.screenStream = null
    }

    // 关闭 PeerConnection
    if (this.peerConnection) {
      this.peerConnection.close()
      this.peerConnection = null
    }

    this.remoteStream = null
    this.callSession = null
    this.pendingIceCandidates = []
    this.isMuted = false
    this.isCameraOff = false
    this.isScreenSharing = false

    this.handlers.onStatusChange?.(CallStatus.ENDED)
  }

  // ==================== 私有方法 ====================

  /**
   * 采集本地媒体流
   */
  private async acquireLocalMedia(constraints: MediaConstraints): Promise<void> {
    this.localStream = await navigator.mediaDevices.getUserMedia(constraints)
    this.handlers.onLocalStream?.(this.localStream)
  }

  /**
   * 创建 RTCPeerConnection
   */
  private async createPeerConnection(): Promise<void> {
    this.peerConnection = new RTCPeerConnection({
      iceServers: this.config.iceServers,
      iceTransportPolicy: this.config.iceTransportPolicy,
      iceCandidatePoolSize: this.config.iceCandidatePoolSize,
    })

    // ---- ICE Candidate 事件 ----
    this.peerConnection.onicecandidate = (event) => {
      if (event.candidate) {
        this.sendSignaling(2103, { // SIGNALING_ICE_CANDIDATE
          callId: this.callSession?.callId,
          candidate: event.candidate.toJSON(),
        })
      }
    }

    // ---- ICE 连接状态变更 ----
    this.peerConnection.oniceconnectionstatechange = () => {
      const state = this.peerConnection?.iceConnectionState
      console.log('[WebRTC] ICE connection state:', state)
      if (state === 'failed' || state === 'disconnected') {
        // 尝试 ICE 重启
        this.restartIce()
      }
    }

    // ---- 连接状态变更 ----
    this.peerConnection.onconnectionstatechange = () => {
      const state = this.peerConnection?.connectionState
      console.log('[WebRTC] Connection state:', state)
      if (state === 'failed' || state === 'disconnected') {
        this.handlers.onError?.(new Error('连接断开'))
      }
    }

    // ---- 远端媒体流 ----
    this.peerConnection.ontrack = (event) => {
      if (event.streams && event.streams[0]) {
        this.remoteStream = event.streams[0]
        this.handlers.onRemoteStream?.(this.remoteStream)
      }
    }

    // ---- 远端媒体流移除 ----
    this.peerConnection.onremovetrack = (event) => {
      if (this.remoteStream) {
        this.handlers.onRemoteStreamRemoved?.(this.remoteStream)
      }
    }
  }

  /**
   * 添加本地流到 PeerConnection
   */
  private addLocalStreamToPeerConnection(): void {
    if (!this.localStream || !this.peerConnection) return
    this.localStream.getTracks().forEach(track => {
      this.peerConnection!.addTrack(track, this.localStream!)
    })
  }

  /**
   * 发送信令消息
   */
  private sendSignaling(command: number, payload: Record<string, unknown>): void {
    this.signalingSender?.(command, payload)
  }

  /**
   * 刷新缓存的 ICE Candidates
   */
  private async flushPendingIceCandidates(): Promise<void> {
    const candidates = this.pendingIceCandidates.splice(0)
    for (const candidate of candidates) {
      try {
        await this.peerConnection?.addIceCandidate(new RTCIceCandidate(candidate))
      } catch (error) {
        console.warn('[WebRTC] Failed to add cached ICE candidate:', error)
      }
    }
  }

  /**
   * ICE 重启
   */
  private async restartIce(): Promise<void> {
    if (!this.peerConnection) return
    try {
      this.peerConnection.restartIce()
      const offer = await this.peerConnection.createOffer({ iceRestart: true })
      await this.peerConnection.setLocalDescription(offer)
      this.sendSignaling(2104, { // SIGNALING_RENEGOTIATE
        callId: this.callSession?.callId,
        sdp: offer,
      })
    } catch (error) {
      console.error('[WebRTC] ICE restart failed:', error)
    }
  }

  /**
   * 应用编码参数
   */
  private applyEncoderParams(params: EncoderParams): void {
    if (!this.peerConnection) return

    const senders = this.peerConnection.getSenders()
    for (const sender of senders) {
      if (sender.track?.kind === 'video') {
        const parameters = sender.getParameters()
        if (!parameters.encodings) {
          parameters.encodings = [{}]
        }
        for (const encoding of parameters.encodings) {
          encoding.maxBitrate = params.maxBitrate * 1000 // 转换为 bps
          encoding.scaleResolutionDownBy = params.scaleResolutionDownBy
          encoding.maxFramerate = params.fps
        }
        sender.setParameters(parameters).catch(err => {
          console.warn('[WebRTC] Failed to set encoder params:', err)
        })
      }
    }
  }

  // ==================== 网络质量监控 ====================

  /**
   * 启动网络质量监控
   */
  private startQualityMonitor(): void {
    this.stopQualityMonitor()
    this.qualityMonitorTimer = setInterval(async () => {
      await this.collectAndReportNetworkStats()
    }, this.config.qualityReportInterval)
  }

  /**
   * 停止网络质量监控
   */
  private stopQualityMonitor(): void {
    if (this.qualityMonitorTimer) {
      clearInterval(this.qualityMonitorTimer)
      this.qualityMonitorTimer = null
    }
  }

  /**
   * 收集并上报网络统计信息
   */
  private async collectAndReportNetworkStats(): Promise<void> {
    if (!this.peerConnection) return

    try {
      const stats = await this.peerConnection.getStats()
      const networkStats = this.parseNetworkStats(stats)

      if (networkStats) {
        // 上报到信令服务器
        this.sendSignaling(2201, { // CALL_QUALITY_REPORT
          callId: this.callSession?.callId,
          rtt: Math.round(networkStats.rtt),
          packetLoss: Math.round(networkStats.packetLoss * 100) / 100,
          jitter: Math.round(networkStats.jitter * 100) / 100,
          estimatedBandwidth: Math.round(networkStats.estimatedBandwidth),
          isScreenShare: this.isScreenSharing,
          qualityLevel: this.calculateQualityLevel(networkStats),
        })
      }
    } catch (error) {
      console.warn('[WebRTC] Failed to collect network stats:', error)
    }
  }

  /**
   * 解析网络统计信息
   */
  private parseNetworkStats(stats: RTCStatsReport): NetworkStats | null {
    const result: NetworkStats = {
      rtt: 0,
      packetLoss: 0,
      jitter: 0,
      estimatedBandwidth: 3000,
    }

    let candidatePairFound = false
    let inboundRtpFound = false

    stats.forEach((report) => {
      // ICE Candidate Pair 统计
      if (report.type === 'candidate-pair' && report.state === 'succeeded') {
        const pair = report as RTCIceCandidatePairStats
        if (pair.currentRoundTripTime !== undefined) {
          result.rtt = pair.currentRoundTripTime * 1000 // 转换为 ms
          candidatePairFound = true
        }
      }

      // Inbound RTP 统计（接收端视角）
      if (report.type === 'inbound-rtp' && report.kind === 'video') {
        const inbound = report as RTCInboundRtpStreamStats
        if (inbound.packetsLost !== undefined && inbound.packetsReceived !== undefined) {
          const total = inbound.packetsLost + inbound.packetsReceived
          if (total > 0) {
            result.packetLoss = (inbound.packetsLost / total) * 100
          }
        }
        if (inbound.jitter !== undefined) {
          result.jitter = inbound.jitter * 1000 // 转换为 ms
        }
        inboundRtpFound = true
      }

      // Remote Inbound RTP 统计（发送端视角，用于估算带宽）
      if (report.type === 'remote-inbound-rtp') {
        const remote = report as RTCRemoteInboundRtpStreamStats
        if (remote.roundTripTime !== undefined) {
          result.rtt = remote.roundTripTime * 1000
        }
      }
    })

    // 基于 RTT 和丢包率估算带宽（简化算法）
    if (result.rtt > 0) {
      const baseBw = 3000 // 基准带宽 3Mbps
      const rttFactor = Math.max(0.1, 100 / Math.max(result.rtt, 1))
      const lossFactor = Math.max(0.1, 1 - result.packetLoss / 100)
      result.estimatedBandwidth = baseBw * rttFactor * lossFactor
    }

    return candidatePairFound || inboundRtpFound ? result : null
  }

  /**
   * 计算网络质量等级
   */
  private calculateQualityLevel(stats: NetworkStats): number {
    let score = 0

    // RTT 评分
    if (stats.rtt < 50) score = Math.max(score, 0)
    else if (stats.rtt < 100) score = Math.max(score, 1)
    else if (stats.rtt < 200) score = Math.max(score, 2)
    else if (stats.rtt < 500) score = Math.max(score, 3)
    else score = Math.max(score, 4)

    // 丢包率评分
    if (stats.packetLoss < 0.5) score = Math.max(score, 0)
    else if (stats.packetLoss < 2) score = Math.max(score, 1)
    else if (stats.packetLoss < 5) score = Math.max(score, 2)
    else if (stats.packetLoss < 15) score = Math.max(score, 3)
    else score = Math.max(score, 4)

    // 抖动评分
    if (stats.jitter < 10) score = Math.max(score, 0)
    else if (stats.jitter < 30) score = Math.max(score, 1)
    else if (stats.jitter < 50) score = Math.max(score, 2)
    else if (stats.jitter < 100) score = Math.max(score, 3)
    else score = Math.max(score, 4)

    return score
  }
}