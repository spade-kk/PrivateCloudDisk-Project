// ============================================================
// useCall.ts — 视频通话组合式函数
// ============================================================
// 封装 WebRTC 视频通话的完整生命周期管理，作为 Vue 组合式 API。
// 连接 ImWebSocketClient 与 WebRTCService，处理信令交换。
//
// 使用方式：
//   const call = useCall()
//   call.startCall(calleeId, CallType.VIDEO)
// ============================================================

import { ref, computed, onUnmounted } from 'vue'
import {
  WebRTCService,
  type WebRTCConfig,
  type MediaConstraints,
  type CallEventHandlers,
} from '@/api/im/WebRTCService'
import {
  type CallSession,
  type CallInvitePayload,
  type SdpPayload,
  type IceCandidatePayload,
  type EncoderParams,
  type IceServerConfig,
  CallType,
  CallStatus,
  CallMode,
  NetworkQuality,
} from '@/api/im/types'
import { useImClient } from '@/composables/useImClient'

// ==================== 类型定义 ====================

export interface CallState {
  /** 当前通话会话 */
  session: CallSession | null
  /** 通话状态 */
  status: CallStatus
  /** 本地媒体流 */
  localStream: MediaStream | null
  /** 远端媒体流 */
  remoteStream: MediaStream | null
  /** 是否静音 */
  isMuted: boolean
  /** 是否关闭摄像头 */
  isCameraOff: boolean
  /** 是否正在屏幕共享 */
  isScreenSharing: boolean
  /** 当前编码参数 */
  encoderParams: EncoderParams | null
  /** 当前网络质量 */
  networkQuality: NetworkQuality
  /** 是否有来电 */
  hasIncomingCall: boolean
  /** 来电信息 */
  incomingCallInfo: CallInvitePayload | null
}

// ==================== 组合式函数 ====================

export function useCall() {
  // ---- 状态 ----
  const session = ref<CallSession | null>(null)
  const status = ref<CallStatus>(CallStatus.ENDED)
  const localStream = ref<MediaStream | null>(null)
  const remoteStream = ref<MediaStream | null>(null)
  const isMuted = ref(false)
  const isCameraOff = ref(false)
  const isScreenSharing = ref(false)
  const encoderParams = ref<EncoderParams | null>(null)
  const networkQuality = ref<NetworkQuality>(NetworkQuality.EXCELLENT)
  const hasIncomingCall = ref(false)
  const incomingCallInfo = ref<CallInvitePayload | null>(null)
  const callError = ref<string | null>(null)

  // ---- 服务实例 ----
  let webrtcService: WebRTCService | null = null
  let imClient = useImClient().client

  // ---- 计算属性 ----
  const isInCall = computed(() =>
    status.value === CallStatus.ACTIVE || status.value === CallStatus.RINGING
  )
  const isVideoCall = computed(() =>
    session.value?.callType === CallType.VIDEO
  )
  const callDuration = computed(() => {
    if (!session.value?.startTime) return '00:00'
    const start = new Date(session.value.startTime).getTime()
    const now = Date.now()
    const elapsed = Math.floor((now - start) / 1000)
    const mins = Math.floor(elapsed / 60)
    const secs = elapsed % 60
    return `${String(mins).padStart(2, '0')}:${String(secs).padStart(2, '0')}`
  })

  // ==================== 初始化 ====================

  /**
   * 初始化 WebRTC 服务
   */
  async function init(iceConfig?: IceServerConfig): Promise<void> {
    // 1. 获取 ICE 服务器配置（如果未提供）
    if (!iceConfig) {
      iceConfig = await fetchIceConfig()
    }

    // 2. 创建 WebRTC 服务
    const config: WebRTCConfig = {
      iceServers: iceConfig.iceServers,
      iceTransportPolicy: iceConfig.iceTransportPolicy,
      iceCandidatePoolSize: iceConfig.iceCandidatePoolSize,
    }

    webrtcService = new WebRTCService(config)

    // 3. 设置信令发送器
    webrtcService.setSignalingSender((command, payload) => {
      imClient?.sendSignaling(command, payload)
    })

    // 4. 设置事件回调
    const handlers: CallEventHandlers = {
      onLocalStream: (stream) => {
        localStream.value = stream
      },
      onRemoteStream: (stream) => {
        remoteStream.value = stream
      },
      onRemoteStreamRemoved: () => {
        remoteStream.value = null
      },
      onStatusChange: (newStatus) => {
        status.value = newStatus
      },
      onEncoderAdjust: (params) => {
        encoderParams.value = params
      },
      onNetworkQualityChange: (quality) => {
        networkQuality.value = quality
      },
      onSuggestDowngrade: () => {
        callError.value = '当前网络较差，建议切换为语音通话'
      },
      onError: (error) => {
        callError.value = error.message
        console.error('[Call] Error:', error)
      },
    }
    webrtcService.setEventHandlers(handlers)

    // 5. 注册信令监听器
    setupSignalingListeners()
  }

  // ==================== 通话操作 ====================

  /**
   * 发起通话
   */
  async function startCall(
    calleeId: string,
    calleeName: string,
    calleeAvatar: string,
    callType: CallType = CallType.VIDEO,
  ): Promise<void> {
    if (!webrtcService) {
      callError.value = 'WebRTC 服务未初始化'
      return
    }

    callError.value = null

    const mediaConstraints: MediaConstraints = {
      video: callType === CallType.VIDEO
        ? { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } }
        : false,
      audio: true,
    }

    await webrtcService.startCall(calleeId, callType, mediaConstraints)

    session.value = {
      callId: '',
      callType,
      callMode: CallMode.P2P,
      callerId: '',
      callerName: '',
      calleeId,
      calleeName,
      calleeAvatar,
      status: CallStatus.RINGING,
      videoEnabled: callType === CallType.VIDEO,
      audioEnabled: true,
      screenShareEnabled: false,
      networkQuality: NetworkQuality.EXCELLENT,
    }
    status.value = CallStatus.RINGING
  }

  /**
   * 接听来电
   */
  async function acceptIncomingCall(): Promise<void> {
    if (!webrtcService || !incomingCallInfo.value) return

    callError.value = null
    const info = incomingCallInfo.value

    const callSession: CallSession = {
      callId: info.callId,
      callType: info.callType,
      callMode: CallMode.P2P,
      callerId: info.callerId,
      callerName: info.callerName,
      callerAvatar: info.callerAvatar,
      status: CallStatus.ACTIVE,
      videoEnabled: info.callType === CallType.VIDEO,
      audioEnabled: true,
      screenShareEnabled: false,
      networkQuality: NetworkQuality.EXCELLENT,
    }

    const mediaConstraints: MediaConstraints = {
      video: info.callType === CallType.VIDEO
        ? { width: { ideal: 1280 }, height: { ideal: 720 }, frameRate: { ideal: 30 } }
        : false,
      audio: true,
    }

    await webrtcService.acceptCall(callSession, mediaConstraints)

    session.value = callSession
    status.value = CallStatus.ACTIVE
    hasIncomingCall.value = false
    incomingCallInfo.value = null
  }

  /**
   * 拒绝来电
   */
  function rejectIncomingCall(reason?: string): void {
    webrtcService?.rejectCall(reason)
    hasIncomingCall.value = false
    incomingCallInfo.value = null
    resetCallState()
  }

  /**
   * 挂断通话
   */
  function hangup(): void {
    webrtcService?.hangupCall()
    resetCallState()
  }

  /**
   * 取消通话
   */
  function cancel(): void {
    webrtcService?.cancelCall()
    resetCallState()
  }

  /**
   * 切换静音
   */
  function toggleMute(): void {
    webrtcService?.toggleMute()
    isMuted.value = webrtcService?.getIsMuted() ?? false
  }

  /**
   * 切换摄像头
   */
  function toggleCamera(): void {
    webrtcService?.toggleCamera()
    isCameraOff.value = webrtcService?.getIsCameraOff() ?? false
  }

  /**
   * 切换为语音通话
   */
  function switchToVoice(): void {
    webrtcService?.switchToVoice()
    isCameraOff.value = true
    if (session.value) {
      session.value.videoEnabled = false
    }
  }

  /**
   * 切换为视频通话
   */
  async function switchToVideo(): Promise<void> {
    await webrtcService?.switchToVideo()
    isCameraOff.value = false
    if (session.value) {
      session.value.videoEnabled = true
    }
  }

  /**
   * 开始屏幕共享
   */
  async function startScreenShare(): Promise<void> {
    await webrtcService?.startScreenShare()
    isScreenSharing.value = true
  }

  /**
   * 停止屏幕共享
   */
  function stopScreenShare(): void {
    webrtcService?.stopScreenShare()
    isScreenSharing.value = false
  }

  // ==================== 私有方法 ====================

  /**
   * 获取 ICE 服务器配置
   */
  async function fetchIceConfig(): Promise<IceServerConfig> {
    return new Promise((resolve) => {
      imClient?.sendSignaling(2601, {}) // CALL_ICE_SERVERS

      // 临时监听 ICE 服务器响应
      const unsubscribe = imClient?.onCommand(2601 as any, (protocol) => {
        const payload = protocol.payload as IceServerConfig
        resolve(payload)
        unsubscribe?.()
      })

      // 超时降级（使用默认 STUN）
      setTimeout(() => {
        resolve({
          iceServers: [{ urls: 'stun:stun.l.google.com:19302' }],
          iceTransportPolicy: 'all',
          iceCandidatePoolSize: 2,
        })
        unsubscribe?.()
      }, 5000)
    })
  }

  /**
   * 设置信令监听器
   */
  function setupSignalingListeners(): void {
    if (!imClient) return

    // 监听来电
    imClient.onCommand(2001 as any, (protocol) => {
      const payload = protocol.payload as CallInvitePayload
      incomingCallInfo.value = payload
      hasIncomingCall.value = true
      new Audio('/sounds/call_ring.mp3').play().catch(() => {})
    })

    // 监听通话取消
    imClient.onCommand(2004 as any, () => {
      hasIncomingCall.value = false
      incomingCallInfo.value = null
      resetCallState()
    })

    // 监听 Offer
    imClient.onCommand(2101 as any, (protocol) => {
      const payload = protocol.payload as SdpPayload
      webrtcService?.handleOffer(payload)
    })

    // 监听 Answer
    imClient.onCommand(2102 as any, (protocol) => {
      const payload = protocol.payload as SdpPayload
      webrtcService?.handleAnswer(payload)
    })

    // 监听 ICE Candidate
    imClient.onCommand(2103 as any, (protocol) => {
      const payload = protocol.payload as IceCandidatePayload
      webrtcService?.handleIceCandidate(payload)
    })

    // 监听编码参数调整
    imClient.onCommand(2202 as any, (protocol) => {
      const payload = protocol.payload as { encoderParams: EncoderParams }
      if (payload.encoderParams) {
        webrtcService?.handleEncoderAdjust(payload.encoderParams)
      }
    })

    // 监听建议降级语音
    imClient.onCommand(2305 as any, () => {
      webrtcService?.handleSuggestDowngrade()
    })

    // 监听通话挂断
    imClient.onCommand(2005 as any, () => {
      resetCallState()
    })

    // 监听通话拒绝
    imClient.onCommand(2003 as any, () => {
      resetCallState()
    })

    // 监听通话超时
    imClient.onCommand(2007 as any, () => {
      resetCallState()
    })

    // 监听忙线
    imClient.onCommand(2006 as any, () => {
      callError.value = '对方正在通话中'
      resetCallState()
    })
  }

  /**
   * 重置通话状态
   */
  function resetCallState(): void {
    webrtcService?.cleanup()
    session.value = null
    status.value = CallStatus.ENDED
    localStream.value = null
    remoteStream.value = null
    isMuted.value = false
    isCameraOff.value = false
    isScreenSharing.value = false
    encoderParams.value = null
    networkQuality.value = NetworkQuality.EXCELLENT
  }

  /**
   * 销毁
   */
  function destroy(): void {
    webrtcService?.cleanup()
    webrtcService = null
    resetCallState()
  }

  // 组件卸载时自动清理
  onUnmounted(() => {
    destroy()
  })

  return {
    // 状态
    session,
    status,
    localStream,
    remoteStream,
    isMuted,
    isCameraOff,
    isScreenSharing,
    encoderParams,
    networkQuality,
    hasIncomingCall,
    incomingCallInfo,
    callError,
    // 计算属性
    isInCall,
    isVideoCall,
    callDuration,
    // 方法
    init,
    startCall,
    acceptIncomingCall,
    rejectIncomingCall,
    hangup,
    cancel,
    toggleMute,
    toggleCamera,
    switchToVoice,
    switchToVideo,
    startScreenShare,
    stopScreenShare,
    destroy,
  }
}