<!-- ============================================================
  FloatingCallWindow.vue — 浮窗通话组件
  企业级浮窗通话组件，支持：
  - 最小化/最大化/全屏切换
  - 画中画模式
  - 可拖拽移动
  - 静音/摄像头/挂断快捷控制
  - 通话时长显示
  - 支持在任意页面悬浮显示（消息中心、文件列表等）
============================================================ -->
<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="floating-call-window"
      :class="{
        'is-minimized': isMinimized,
        'is-dragging': isDragging,
        'is-voice': !isVideo
      }"
      :style="floatingStyle"
      @mousedown="onWindowMouseDown"
    >
      <!-- 拖拽手柄 -->
      <div class="call-handle" @mousedown.stop="startDrag" @touchstart.stop="startDrag">
        <div class="handle-bar"></div>
      </div>

      <!-- 最小化状态 -->
      <div v-if="isMinimized" class="minimized-bar" @click="isMinimized = false">
        <div class="mini-avatar">
          <span>{{ peerName.slice(0, 1) }}</span>
          <span class="mini-dot" :class="isVideo ? 'dot-video' : 'dot-voice'"></span>
        </div>
        <div class="mini-info">
          <strong>{{ peerName }}</strong>
          <span>{{ isVideo ? '视频通话中' : '语音通话中' }} · {{ callDuration }}</span>
        </div>
        <div class="mini-actions">
          <button class="mini-btn btn-expand" @click.stop="isMinimized = false" title="展开">
            <i class="fa fa-expand"></i>
          </button>
          <button class="mini-btn btn-hangup" @click.stop="$emit('hangup')" title="挂断">
            <i class="fa fa-phone"></i>
          </button>
        </div>
      </div>

      <!-- 正常/展开状态 -->
      <template v-else>
        <!-- 视频区域 -->
        <div class="call-video-area">
          <video
            v-if="remoteStream && isVideo"
            ref="remoteVideoRef"
            class="remote-video"
            autoplay
            playsinline
          ></video>
          <div v-else class="remote-placeholder">
            <div class="placeholder-avatar">
              <span>{{ peerName.slice(0, 1) }}</span>
            </div>
            <p>{{ peerName }}</p>
            <p v-if="!isVideo" class="voice-label">语音通话中</p>
          </div>

          <!-- 本地视频小窗 -->
          <video
            v-if="localStream && isVideo && !isMinimized"
            class="local-video-pip"
            autoplay
            playsinline
            muted
          ></video>

          <!-- 通话时长 -->
          <div class="call-timer">{{ callDuration }}</div>
        </div>

        <!-- 控制栏 -->
        <div class="call-controls">
          <button class="ctrl-btn" :class="{ active: isMuted }" @click="$emit('toggleMute')" title="静音">
            <i :class="isMuted ? 'fa fa-microphone-slash' : 'fa fa-microphone'"></i>
          </button>
          <button v-if="isVideo" class="ctrl-btn" :class="{ active: isCameraOff }" @click="$emit('toggleCamera')" title="摄像头">
            <i :class="isCameraOff ? 'fa fa-video-camera off' : 'fa fa-video-camera'"></i>
          </button>
          <button class="ctrl-btn btn-minimize" @click="isMinimized = true" title="最小化">
            <i class="fa fa-window-minimize"></i>
          </button>
          <button class="ctrl-btn btn-fullscreen" @click="$emit('fullscreen')" title="全屏通话">
            <i class="fa fa-arrows-alt"></i>
          </button>
          <button class="ctrl-btn btn-hangup" @click="$emit('hangup')" title="挂断">
            <i class="fa fa-phone"></i>
          </button>
        </div>
      </template>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, computed, watch, onBeforeUnmount } from 'vue'

const props = defineProps<{
  visible: boolean
  peerName: string
  isVideo: boolean
  callDuration: string
  isMuted: boolean
  isCameraOff: boolean
  localStream: MediaStream | null
  remoteStream: MediaStream | null
}>()

defineEmits<{
  hangup: []
  toggleMute: []
  toggleCamera: []
  fullscreen: []
}>()

// ---- 浮窗状态 ----
const isMinimized = ref(false)
const isDragging = ref(false)
const windowX = ref(window.innerWidth - 340)
const windowY = ref(60)

// ---- 拖拽 ----
let dragStartX = 0
let dragStartY = 0
let dragStartWinX = 0
let dragStartWinY = 0

const floatingStyle = computed(() => ({
  left: `${windowX.value}px`,
  top: `${windowY.value}px`,
}))

function startDrag(e: MouseEvent | TouchEvent) {
  isDragging.value = true
  const clientX = 'touches' in e ? e.touches[0].clientX : e.clientX
  const clientY = 'touches' in e ? e.touches[0].clientY : e.clientY
  dragStartX = clientX
  dragStartY = clientY
  dragStartWinX = windowX.value
  dragStartWinY = windowY.value

  const onMove = (ev: MouseEvent | TouchEvent) => {
    const cx = 'touches' in ev ? ev.touches[0].clientX : ev.clientX
    const cy = 'touches' in ev ? ev.touches[0].clientY : ev.clientY
    windowX.value = Math.max(0, Math.min(window.innerWidth - 320, dragStartWinX + cx - dragStartX))
    windowY.value = Math.max(0, Math.min(window.innerHeight - 60, dragStartWinY + cy - dragStartY))
  }
  const onUp = () => {
    isDragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.removeEventListener('touchmove', onMove)
    document.removeEventListener('touchend', onUp)
  }
  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.addEventListener('touchmove', onMove)
  document.addEventListener('touchend', onUp)
}

function onWindowMouseDown(e: MouseEvent) {
  e.stopPropagation()
}

// ---- 视频流绑定 ----
const remoteVideoRef = ref<HTMLVideoElement | null>(null)

watch(() => props.remoteStream, (stream) => {
  if (remoteVideoRef.value && stream) {
    remoteVideoRef.value.srcObject = stream
  }
}, { immediate: true })

watch(() => props.localStream, (stream) => {
  const localVideo = document.querySelector('.local-video-pip') as HTMLVideoElement | null
  if (localVideo && stream) {
    localVideo.srcObject = stream
  }
}, { immediate: true })
</script>

<style scoped>
.floating-call-window {
  position: fixed;
  z-index: 9999;
  width: 320px;
  background: #1a1a2e;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.5), 0 0 0 1px rgba(255, 255, 255, 0.1);
  overflow: hidden;
  user-select: none;
  transition: width 0.2s, height 0.2s;
}

.floating-call-window.is-dragging {
  opacity: 0.9;
  cursor: grabbing;
}

.floating-call-window.is-minimized {
  width: 280px;
}

.call-handle {
  cursor: grab;
  padding: 4px 0;
  display: flex;
  justify-content: center;
  background: rgba(255, 255, 255, 0.05);
}

.call-handle:active {
  cursor: grabbing;
}

.handle-bar {
  width: 40px;
  height: 4px;
  background: rgba(255, 255, 255, 0.3);
  border-radius: 2px;
}

/* ---- 最小化状态 ---- */
.minimized-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  cursor: pointer;
  background: linear-gradient(135deg, #16213e, #0f3460);
}

.mini-avatar {
  position: relative;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
}

.mini-dot {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  border: 2px solid #1a1a2e;
}

.dot-video { background: #52c41a; }
.dot-voice { background: #1890ff; }

.mini-info {
  flex: 1;
  min-width: 0;
}

.mini-info strong {
  display: block;
  font-size: 13px;
  color: #fff;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.mini-info span {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.6);
}

.mini-actions {
  display: flex;
  gap: 6px;
}

.mini-btn {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  color: #fff;
}

.btn-expand { background: rgba(255, 255, 255, 0.15); }
.btn-expand:hover { background: rgba(255, 255, 255, 0.25); }
.btn-hangup { background: #ff4d4f; }
.btn-hangup:hover { background: #ff7875; }

/* ---- 视频区域 ---- */
.call-video-area {
  position: relative;
  width: 100%;
  height: 200px;
  background: #000;
  overflow: hidden;
}

.remote-video {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.remote-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: rgba(255, 255, 255, 0.7);
}

.placeholder-avatar {
  width: 64px;
  height: 64px;
  border-radius: 50%;
  background: linear-gradient(135deg, #667eea, #764ba2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: #fff;
  margin-bottom: 10px;
}

.placeholder-avatar p {
  font-size: 14px;
  margin: 0;
}

.voice-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 4px;
}

.local-video-pip {
  position: absolute;
  bottom: 8px;
  right: 8px;
  width: 80px;
  height: 60px;
  border-radius: 6px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  object-fit: cover;
  background: #333;
}

.call-timer {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(0, 0, 0, 0.5);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  font-family: monospace;
}

/* ---- 控制栏 ---- */
.call-controls {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: rgba(255, 255, 255, 0.03);
}

.ctrl-btn {
  width: 40px;
  height: 40px;
  border: none;
  border-radius: 50%;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  color: #fff;
  background: rgba(255, 255, 255, 0.1);
  transition: all 0.2s;
}

.ctrl-btn:hover {
  background: rgba(255, 255, 255, 0.2);
}

.ctrl-btn.active {
  background: rgba(255, 255, 255, 0.25);
}

.ctrl-btn .off {
  opacity: 0.5;
}

.btn-minimize {
  background: rgba(255, 255, 255, 0.08);
}

.btn-fullscreen {
  background: rgba(255, 255, 255, 0.08);
}

.btn-hangup {
  background: #ff4d4f !important;
  transform: rotate(135deg);
}

.btn-hangup:hover {
  background: #ff7875 !important;
}

/* 语音通话时调整高度 */
.floating-call-window.is-voice .call-video-area {
  height: 140px;
}
</style>