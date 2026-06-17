<!-- ============================================================
  CallButton.vue — 通话发起按钮
  在IM消息中心嵌入，用于发起视频/语音通话。
============================================================ -->
<template>
  <div class="call-button-group">
    <!-- 语音通话按钮 -->
    <button
      class="call-btn call-voice-btn"
      :disabled="disabled"
      :title="disabled ? '请先选择联系人' : '发起语音通话'"
      @click="initiateCall(CallType.VOICE)"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
      </svg>
      <span v-if="showLabel">语音</span>
    </button>

    <!-- 视频通话按钮 -->
    <button
      class="call-btn call-video-btn"
      :disabled="disabled"
      :title="disabled ? '请先选择联系人' : '发起视频通话'"
      @click="initiateCall(CallType.VIDEO)"
    >
      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="2">
        <polygon points="23 7 16 12 23 17 23 7" />
        <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
      </svg>
      <span v-if="showLabel">视频</span>
    </button>
  </div>
</template>

<script setup lang="ts">
import { CallType } from '@/api/im/types'

const props = withDefaults(defineProps<{
  /** 目标用户 ID */
  calleeId?: string
  /** 目标用户名称 */
  calleeName?: string
  /** 目标用户头像 */
  calleeAvatar?: string
  /** 是否禁用 */
  disabled?: boolean
  /** 是否显示文字标签 */
  showLabel?: boolean
}>(), {
  disabled: false,
  showLabel: false,
})

const emit = defineEmits<{
  call: [type: CallType, calleeId: string, calleeName: string, calleeAvatar: string]
}>()

function initiateCall(type: CallType): void {
  if (props.disabled || !props.calleeId) return
  emit('call', type, props.calleeId, props.calleeName || '', props.calleeAvatar || '')
}
</script>

<style scoped>
.call-button-group {
  display: flex;
  gap: 8px;
}

.call-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  border: 1px solid var(--color-border, rgba(255, 255, 255, 0.1));
  border-radius: 8px;
  background: var(--color-bg-elevated, #1e1e2e);
  color: var(--color-text-primary, #fff);
  cursor: pointer;
  transition: all 0.15s;
  font-size: 13px;
}

.call-btn:hover:not(:disabled) {
  background: var(--color-bg-hover, rgba(255, 255, 255, 0.1));
}

.call-btn:active:not(:disabled) {
  transform: scale(0.97);
}

.call-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.call-voice-btn:hover:not(:disabled) {
  border-color: #22c55e;
  color: #22c55e;
}

.call-video-btn:hover:not(:disabled) {
  border-color: #3b82f6;
  color: #3b82f6;
}
</style>