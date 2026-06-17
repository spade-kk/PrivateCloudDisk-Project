<!-- ============================================================
  IncomingCallDialog.vue — 来电通知弹窗
  在企业IM控制面板消息中心显示来电通知，支持接听/拒绝。
  后端对应：SignalingHandler.handleCallInvite()
============================================================ -->
<template>
  <Teleport to="body">
    <Transition name="call-slide">
      <div v-if="visible" class="incoming-call-overlay">
        <div class="incoming-call-dialog" :class="{ 'is-video': isVideo }">
          <!-- 来电动画波纹 -->
          <div class="call-ripple">
            <div class="ripple-ring ring-1"></div>
            <div class="ripple-ring ring-2"></div>
            <div class="ripple-ring ring-3"></div>
          </div>

          <!-- 来电信息 -->
          <div class="call-info">
            <div class="caller-avatar">
              <img
                v-if="incomingCallInfo?.callerAvatar"
                :src="incomingCallInfo.callerAvatar"
                :alt="incomingCallInfo.callerName"
              />
              <span v-else class="avatar-placeholder">
                {{ (incomingCallInfo?.callerName || '?')[0] }}
              </span>
            </div>
            <h3 class="caller-name">{{ incomingCallInfo?.callerName || '未知用户' }}</h3>
            <p class="call-type-label">
              <span v-if="isVideo" class="type-icon video-icon">📹</span>
              <span v-else class="type-icon voice-icon">📞</span>
              {{ isVideo ? '视频通话邀请' : '语音通话邀请' }}
            </p>
          </div>

          <!-- 操作按钮 -->
          <div class="call-actions">
            <button
              class="btn-reject"
              @click="$emit('reject')"
              aria-label="拒绝"
            >
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M10.5 5.5h3v7.5l-8.5 4.5" />
                <line x1="18" y1="9" x2="23" y2="14" />
                <line x1="23" y1="9" x2="18" y2="14" />
              </svg>
              <span>拒绝</span>
            </button>
            <button
              class="btn-accept"
              @click="$emit('accept')"
              aria-label="接听"
            >
              <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="2">
                <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
              </svg>
              <span>接听</span>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { CallInvitePayload } from '@/api/im/types'
import { CallType } from '@/api/im/types'

const props = defineProps<{
  visible: boolean
  incomingCallInfo: CallInvitePayload | null
}>()

defineEmits<{
  accept: []
  reject: []
}>()

const isVideo = computed(() => props.incomingCallInfo?.callType === CallType.VIDEO)
</script>

<style scoped>
.incoming-call-overlay {
  position: fixed;
  top: 0;
  right: 0;
  z-index: 9999;
  padding: 24px;
  pointer-events: none;
}

.incoming-call-dialog {
  width: 360px;
  background: var(--color-bg-elevated, #1e1e2e);
  border: 1px solid var(--color-border, rgba(255, 255, 255, 0.1));
  border-radius: 20px;
  padding: 32px 24px 24px;
  text-align: center;
  pointer-events: auto;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(20px);
  position: relative;
  overflow: hidden;
}

.incoming-call-dialog.is-video {
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%);
}

/* ---- 来电波纹动画 ---- */
.call-ripple {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.ripple-ring {
  position: absolute;
  border: 2px solid rgba(76, 175, 80, 0.4);
  border-radius: 50%;
  animation: ripple 2s ease-out infinite;
  top: -60px;
  left: -60px;
  width: 120px;
  height: 120px;
}

.ring-2 {
  animation-delay: 0.5s;
}
.ring-3 {
  animation-delay: 1s;
}

@keyframes ripple {
  0% {
    transform: scale(0.8);
    opacity: 0.8;
  }
  100% {
    transform: scale(2.5);
    opacity: 0;
  }
}

/* ---- 来电信息 ---- */
.call-info {
  position: relative;
  z-index: 1;
}

.caller-avatar {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  margin: 0 auto 16px;
  border: 3px solid rgba(76, 175, 80, 0.3);
}

.caller-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text-primary, #fff);
  background: linear-gradient(135deg, #4caf50, #2196f3);
}

.caller-name {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary, #fff);
  margin: 0 0 8px;
}

.call-type-label {
  font-size: 14px;
  color: var(--color-text-secondary, rgba(255, 255, 255, 0.6));
  margin: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.type-icon {
  font-size: 18px;
}

/* ---- 操作按钮 ---- */
.call-actions {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-top: 28px;
  position: relative;
  z-index: 1;
}

.btn-reject,
.btn-accept {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border: none;
  background: none;
  cursor: pointer;
  padding: 0;
  transition: transform 0.15s;
}

.btn-reject:hover,
.btn-accept:hover {
  transform: scale(1.1);
}

.btn-reject:active,
.btn-accept:active {
  transform: scale(0.95);
}

.btn-reject svg {
  width: 56px;
  height: 56px;
  padding: 14px;
  border-radius: 50%;
  background: #ef4444;
  color: #fff;
}

.btn-accept svg {
  width: 56px;
  height: 56px;
  padding: 14px;
  border-radius: 50%;
  background: #22c55e;
  color: #fff;
}

.btn-reject span,
.btn-accept span {
  font-size: 12px;
  color: var(--color-text-secondary, rgba(255, 255, 255, 0.6));
}

/* ---- 过渡动画 ---- */
.call-slide-enter-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}
.call-slide-leave-active {
  transition: all 0.2s ease-in;
}
.call-slide-enter-from {
  transform: translateX(100%);
  opacity: 0;
}
.call-slide-leave-to {
  transform: translateX(100%);
  opacity: 0;
}
</style>