<!-- ============================================================
  CallView.vue — 视频/语音通话页面
  企业级视频通话界面，支持：
  - 本地/远端视频双画面
  - 画中画切换
  - 通话控制（静音/摄像头/屏幕共享/挂断）
  - 通话时长显示
  - 网络质量指示器
  - 编码参数实时显示
  - 自适应响应式布局
============================================================ -->
<template>
  <div class="call-view" :class="{ 'is-voice': !isVideoCall }">
    <!-- 远端视频（主画面） -->
    <div class="remote-video-container">
      <video
        ref="remoteVideoRef"
        class="remote-video"
        autoplay
        playsinline
        :muted="false"
        @loadedmetadata="onRemoteVideoReady"
      ></video>

      <!-- 通话中无远端视频的后备 -->
      <div v-if="!hasRemoteVideo" class="remote-placeholder">
        <div class="remote-avatar">
          <img
            v-if="remoteAvatar"
            :src="remoteAvatar"
            :alt="remoteName"
          />
          <span v-else class="avatar-text">{{ remoteInitial }}</span>
        </div>
        <p class="remote-name">{{ remoteName }}</p>
        <p v-if="status === CallStatus.RINGING" class="calling-text">正在呼叫...</p>
      </div>

      <!-- 通话时长 / 状态 -->
      <div class="call-duration-badge">
        <span v-if="status === CallStatus.RINGING" class="status-ringing">
          <span class="dot-pulse"></span>
          等待接听
        </span>
        <span v-else-if="status === CallStatus.ACTIVE" class="status-active">
          <span class="dot"></span>
          {{ callDuration }}
        </span>
        <span v-else class="status-ended">通话已结束</span>
      </div>

      <!-- 网络质量指示器 -->
      <div v-if="status === CallStatus.ACTIVE" class="network-indicator">
        <span
          class="quality-dot"
          :class="`quality-${networkQuality}`"
          :title="networkQualityLabel"
        ></span>
        <span class="quality-label">{{ networkQualityLabel }}</span>
        <span v-if="encoderParams" class="encoder-info">
          {{ encoderParams.width }}x{{ encoderParams.height }}@{{ encoderParams.fps }}fps
          {{ encoderParams.targetBitrate }}kbps
        </span>
      </div>
    </div>

    <!-- 本地视频（画中画） -->
    <div
      class="local-video-container"
      :class="{ 'is-dragging': isDragging }"
      @mousedown="startDrag"
      @touchstart="startDrag"
    >
      <video
        ref="localVideoRef"
        class="local-video"
        autoplay
        playsinline
        muted
        :style="isCameraOff ? { display: 'none' } : {}"
      ></video>
      <div v-if="isCameraOff" class="camera-off-overlay">
        <svg viewBox="0 0 24 24" width="32" height="32" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M1 1l22 22" />
          <path d="M21 21H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h3l2-3h8l2 3h3a2 2 0 0 1 2 2v9" />
          <path d="M10 9.34l4 4.66" />
        </svg>
        <span>摄像头已关闭</span>
      </div>
    </div>

    <!-- 底部控制栏 -->
    <div class="call-controls">
      <div class="controls-row">
        <!-- 静音 -->
        <button
          class="control-btn"
          :class="{ active: isMuted }"
          @click="toggleMute"
          title="静音"
        >
          <svg v-if="!isMuted" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 5L6 9H2v6h4l5 4V5z" />
            <path d="M19.07 4.93a10 10 0 0 1 0 14.14" />
            <path d="M15.54 8.46a5 5 0 0 1 0 7.07" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M11 5L6 9H2v6h4l5 4V5z" />
            <line x1="23" y1="9" x2="17" y2="15" />
            <line x1="17" y1="9" x2="23" y2="15" />
          </svg>
          <span class="btn-label">静音</span>
        </button>

        <!-- 摄像头 -->
        <button
          class="control-btn"
          :class="{ active: isCameraOff }"
          @click="toggleCamera"
          title="摄像头"
        >
          <svg v-if="!isCameraOff" viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="23 7 16 12 23 17 23 7" />
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="1" y1="1" x2="23" y2="23" />
            <polygon points="23 7 16 12 23 17 23 7" />
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
          </svg>
          <span class="btn-label">摄像头</span>
        </button>

        <!-- 屏幕共享 -->
        <button
          class="control-btn"
          :class="{ active: isScreenSharing }"
          @click="toggleScreenShare"
          title="屏幕共享"
        >
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <rect x="2" y="3" width="20" height="14" rx="2" ry="2" />
            <line x1="8" y1="21" x2="16" y2="21" />
            <line x1="12" y1="17" x2="12" y2="21" />
          </svg>
          <span class="btn-label">共享</span>
        </button>

        <!-- 切换语音/视频 -->
        <button
          v-if="isVideoCall"
          class="control-btn"
          @click="switchToVoice"
          title="切换为语音"
        >
          <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
          </svg>
          <span class="btn-label">语音</span>
        </button>

        <!-- 挂断 -->
        <button
          class="control-btn btn-hangup"
          @click="hangup"
          title="挂断"
        >
          <svg viewBox="0 0 24 24" width="24" height="24" fill="currentColor">
            <path d="M10.5 5.5h3v7.5l-8.5 4.5" />
          </svg>
          <span class="btn-label">挂断</span>
        </button>
      </div>
    </div>

    <!-- 错误提示 -->
    <div v-if="callError" class="call-error-toast">
      {{ callError }}
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCall } from '@/composables/useCall'
import { CallStatus, CallType, NetworkQuality } from '@/api/im/types'
import type { EncoderParams } from '@/api/im/types'

// ==================== 路由 ====================

const route = useRoute()
const router = useRouter()
const call = useCall()

// 从路由 query 获取通话信息
const routePeerName = (route.query.peerName as string) || '未知用户'

// ==================== 状态（从 useCall 获取） ====================

const isMuted = computed(() => call.isMuted.value)
const isCameraOff = computed(() => call.isCameraOff.value)
const isScreenSharing = computed(() => call.isScreenSharing.value)
const status = computed(() => call.status.value)
const isVideoCall = computed(() => call.isVideoCall.value)
const callDuration = computed(() => call.callDuration.value)
const networkQuality = computed(() => call.networkQuality.value)
const encoderParams = computed(() => call.encoderParams.value)
const callError = computed(() => call.callError.value)
const remoteName = computed(() => call.session.value?.calleeName || call.session.value?.callerName || routePeerName)
const remoteAvatar = computed(() => call.session.value?.callerAvatar || call.session.value?.calleeAvatar || '')

// ==================== 视频元素 ====================

const localVideoRef = ref<HTMLVideoElement | null>(null)
const remoteVideoRef = ref<HTMLVideoElement | null>(null)
const hasRemoteVideo = ref(false)

// 绑定流到视频元素
watch(
  () => call.localStream.value,
  (stream) => {
    if (localVideoRef.value && stream) {
      localVideoRef.value.srcObject = stream
    }
  },
  { immediate: true },
)

watch(
  () => call.remoteStream.value,
  (stream) => {
    if (remoteVideoRef.value && stream) {
      remoteVideoRef.value.srcObject = stream
      hasRemoteVideo.value = true
    } else {
      hasRemoteVideo.value = false
    }
  },
  { immediate: true },
)

function onRemoteVideoReady(): void {
  hasRemoteVideo.value = true
}

// ==================== 远程信息 ====================

const remoteInitial = computed(() => (remoteName.value || '?')[0])

// ==================== 网络质量标签 ====================

const networkQualityLabel = computed(() => {
  switch (networkQuality.value) {
    case NetworkQuality.EXCELLENT: return '网络优秀'
    case NetworkQuality.GOOD: return '网络良好'
    case NetworkQuality.FAIR: return '网络一般'
    case NetworkQuality.POOR: return '网络较差'
    case NetworkQuality.VERY_POOR: return '网络极差'
    default: return ''
  }
})

// ==================== 控制操作 ====================

function toggleMute(): void {
  call.toggleMute()
}

function toggleCamera(): void {
  call.toggleCamera()
}

function toggleScreenShare(): void {
  if (isScreenSharing.value) {
    call.stopScreenShare()
  } else {
    call.startScreenShare()
  }
}

function switchToVoice(): void {
  call.switchToVoice()
}

function hangup(): void {
  call.hangup()
  router.back()
}

// ==================== 画中画拖拽 ====================

const isDragging = ref(false)
let dragStartX = 0
let dragStartY = 0
let pipLeft = 0
let pipTop = 0

function startDrag(e: MouseEvent | TouchEvent): void {
  isDragging.value = true
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  dragStartX = clientX
  dragStartY = clientY

  const el = e.currentTarget as HTMLElement
  pipLeft = parseInt(el.style.left || '16px', 10)
  pipTop = parseInt(el.style.top || '16px', 10)

  const onMove = (ev: MouseEvent | TouchEvent) => {
    const cx = 'touches' in ev ? ev.touches[0].clientX : ev.clientX
    const cy = 'touches' in ev ? ev.touches[0].clientY : ev.clientY
    const dx = cx - dragStartX
    const dy = cy - dragStartY
    const el = document.querySelector('.local-video-container') as HTMLElement
    if (el) {
      el.style.left = `${pipLeft + dx}px`
      el.style.top = `${pipTop + dy}px`
    }
  }

  const onEnd = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onEnd)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onEnd)
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onEnd)
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onEnd)
}

// ==================== 键盘快捷键 ====================

function onKeydown(e: KeyboardEvent): void {
  switch (e.key) {
    case 'm':
    case 'M':
      toggleMute()
      break
    case 'v':
    case 'V':
      toggleCamera()
      break
    case 'Escape':
      hangup()
      break
  }
}

onMounted(async () => {
  document.addEventListener('keydown', onKeydown)
  try {
    await call.init()
  } catch (e) {
    console.warn('[CallView] useCall 初始化:', e)
  }
})

onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  call.destroy()
})

// ==================== 暴露给父组件 ====================

defineExpose({ localVideoRef, remoteVideoRef })
</script>

<style scoped>
.call-view {
  position: fixed;
  inset: 0;
  z-index: 9000;
  background: #0a0a0f;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ---- 远端视频 ---- */
.remote-video-container {
  flex: 1;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #0a0a0f;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: contain;
}

.remote-placeholder {
  position: absolute;
  text-align: center;
}

.remote-avatar {
  width: 120px;
  height: 120px;
  border-radius: 50%;
  overflow: hidden;
  margin: 0 auto 16px;
  border: 2px solid rgba(255, 255, 255, 0.1);
}

.remote-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-text {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 48px;
  font-weight: 700;
  color: #fff;
  background: linear-gradient(135deg, #4caf50, #2196f3);
}

.remote-name {
  font-size: 24px;
  font-weight: 600;
  color: #fff;
  margin: 0 0 8px;
}

.calling-text {
  font-size: 14px;
  color: rgba(255, 255, 255, 0.5);
  margin: 0;
}

/* ---- 通话时长 ---- */
.call-duration-badge {
  position: absolute;
  top: 24px;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 20px;
  border-radius: 20px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  font-size: 14px;
  color: #fff;
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-ringing {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #4caf50;
  animation: pulse 1.5s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
}

/* ---- 网络质量指示器 ---- */
.network-indicator {
  position: absolute;
  top: 24px;
  right: 24px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-radius: 12px;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(10px);
  font-size: 12px;
  color: rgba(255, 255, 255, 0.7);
}

.quality-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.quality-0 { background: #22c55e; } /* EXCELLENT */
.quality-1 { background: #84cc16; } /* GOOD */
.quality-2 { background: #eab308; } /* FAIR */
.quality-3 { background: #f97316; } /* POOR */
.quality-4 { background: #ef4444; } /* VERY_POOR */

.encoder-info {
  margin-left: 8px;
  padding-left: 8px;
  border-left: 1px solid rgba(255, 255, 255, 0.2);
  color: rgba(255, 255, 255, 0.5);
  font-family: monospace;
}

/* ---- 本地视频（画中画） ---- */
.local-video-container {
  position: absolute;
  bottom: 100px;
  right: 24px;
  width: 200px;
  height: 150px;
  border-radius: 12px;
  overflow: hidden;
  border: 2px solid rgba(255, 255, 255, 0.15);
  background: #1a1a2e;
  cursor: grab;
  z-index: 10;
  transition: box-shadow 0.2s;
}

.local-video-container:hover {
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.local-video-container.is-dragging {
  cursor: grabbing;
  opacity: 0.9;
}

.local-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.camera-off-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  color: rgba(255, 255, 255, 0.4);
  font-size: 12px;
}

/* ---- 底部控制栏 ---- */
.call-controls {
  padding: 16px 24px calc(16px + env(safe-area-inset-bottom));
  background: rgba(0, 0, 0, 0.8);
  backdrop-filter: blur(20px);
}

.controls-row {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  max-width: 600px;
  margin: 0 auto;
}

.control-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: #fff;
  width: 64px;
  height: 64px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.15s;
  padding: 0;
}

.control-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.control-btn:active {
  transform: scale(0.95);
}

.control-btn.active {
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.4);
}

.control-btn svg {
  width: 24px;
  height: 24px;
}

.btn-label {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.6);
}

.btn-hangup {
  background: #ef4444 !important;
  width: 72px;
  height: 72px;
  border-radius: 50%;
}

.btn-hangup:hover {
  background: #dc2626 !important;
}

.btn-hangup svg {
  width: 24px;
  height: 24px;
}

/* ---- 错误提示 ---- */
.call-error-toast {
  position: absolute;
  bottom: 120px;
  left: 50%;
  transform: translateX(-50%);
  padding: 12px 24px;
  border-radius: 12px;
  background: rgba(239, 68, 68, 0.9);
  color: #fff;
  font-size: 14px;
  backdrop-filter: blur(10px);
  animation: slideUp 0.3s ease-out;
}

@keyframes slideUp {
  from {
    transform: translateX(-50%) translateY(10px);
    opacity: 0;
  }
  to {
    transform: translateX(-50%) translateY(0);
    opacity: 1;
  }
}

/* ---- 仅语音模式 ---- */
.call-view.is-voice .remote-video-container {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

/* ---- 响应式 ---- */
@media (max-width: 768px) {
  .local-video-container {
    width: 140px;
    height: 105px;
    bottom: 110px;
    right: 16px;
  }

  .controls-row {
    gap: 12px;
  }

  .control-btn {
    width: 52px;
    height: 52px;
    border-radius: 12px;
  }

  .btn-hangup {
    width: 60px;
    height: 60px;
  }
}
</style>