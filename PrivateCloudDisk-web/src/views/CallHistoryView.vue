<!-- ============================================================
  CallHistoryView.vue — 通话记录页面
  展示用户的历史通话记录，支持分页、删除等操作。
  后端对应：CallRecordController
============================================================ -->
<template>
  <div class="call-history-page">
    <div class="page-header">
      <h2>通话记录</h2>
      <button
        v-if="selectedIds.length > 0"
        class="btn-delete-batch"
        @click="batchDelete"
      >
        删除选中 ({{ selectedIds.length }})
      </button>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="loading-state">
      <LoadingSpinner />
      <span>加载中...</span>
    </div>

    <!-- 空状态 -->
    <div v-else-if="records.length === 0" class="empty-state">
      <svg viewBox="0 0 24 24" width="48" height="48" fill="none" stroke="currentColor" stroke-width="1.5" opacity="0.3">
        <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
      </svg>
      <p>暂无通话记录</p>
      <span>通过消息中心发起视频或语音通话</span>
    </div>

    <!-- 通话记录列表 -->
    <div v-else class="call-list">
      <div
        v-for="record in records"
        :key="record.callId"
        class="call-item"
        :class="{ selected: selectedIds.includes(record.id!) }"
        @click="toggleSelect(record.id!)"
      >
        <!-- 类型图标 -->
        <div class="call-type-icon" :class="record.callType === 2 ? 'video' : 'voice'">
          <svg v-if="record.callType === 2" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <polygon points="23 7 16 12 23 17 23 7" />
            <rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
          </svg>
          <svg v-else viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M22 16.92v3a2 2 0 0 1-2.18 2 19.79 19.79 0 0 1-8.63-3.07 19.5 19.5 0 0 1-6-6 19.79 19.79 0 0 1-3.07-8.67A2 2 0 0 1 4.11 2h3a2 2 0 0 1 2 1.72 12.84 12.84 0 0 0 .7 2.81 2 2 0 0 1-.45 2.11L8.09 9.91a16 16 0 0 0 6 6l1.27-1.27a2 2 0 0 1 2.11-.45 12.84 12.84 0 0 0 2.81.7A2 2 0 0 1 22 16.92z" />
          </svg>
        </div>

        <!-- 通话信息 -->
        <div class="call-info">
          <div class="call-participants">
            <span class="participant-name">{{ record.callerId === currentUserId ? (record.calleeName || '未知') : (record.callerName || '未知') }}</span>
            <span class="call-direction">
              <svg v-if="record.callerId === currentUserId" viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="7" y1="17" x2="17" y2="7" />
                <polyline points="7 7 17 7 17 17" />
              </svg>
              <svg v-else viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2">
                <line x1="17" y1="7" x2="7" y2="17" />
                <polyline points="17 17 7 17 7 7" />
              </svg>
            </span>
          </div>
          <div class="call-meta">
            <span class="call-type-text">{{ record.callType === 2 ? '视频通话' : '语音通话' }}</span>
            <span class="dot-sep">&middot;</span>
            <span class="call-status-text">{{ statusText(record.status) }}</span>
            <span v-if="record.duration" class="dot-sep">&middot;</span>
            <span v-if="record.duration" class="call-duration">{{ formatDuration(record.duration) }}</span>
          </div>
        </div>

        <!-- 时间 -->
        <div class="call-time">
          <span>{{ formatTime(record.createTime) }}</span>
        </div>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="totalPages > 1" class="pagination">
      <button :disabled="currentPage <= 1" @click="changePage(currentPage - 1)">上一页</button>
      <span>第 {{ currentPage }} / {{ totalPages }} 页</span>
      <button :disabled="currentPage >= totalPages" @click="changePage(currentPage + 1)">下一页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getCallHistoryApi, deleteCallRecordsApi } from '@/api/im/callApi'
import { CallStatus } from '@/api/im/types'
import type { CallRecordDTO } from '@/api/im/types'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useUserStore } from '@/stores/userStore'

const userStore = useUserStore()
const currentUserId = ref(userStore.user?.id || '')

// ---- 状态 ----
const records = ref<(CallRecordDTO & { id?: number })[]>([])
const loading = ref(false)
const currentPage = ref(1)
const totalPages = ref(1)
const pageSize = 20
const selectedIds = ref<number[]>([])

// ---- 加载 ----
async function loadRecords(): Promise<void> {
  loading.value = true
  try {
    const res = await getCallHistoryApi(currentUserId.value, currentPage.value, pageSize)
    if (res.code === 200 && res.data) {
      records.value = res.data
    }
  } catch (error) {
    console.error('[CallHistory] Failed to load:', error)
  } finally {
    loading.value = false
  }
}

function changePage(page: number): void {
  currentPage.value = page
  loadRecords()
}

// ---- 选择 ----
function toggleSelect(id: number): void {
  const idx = selectedIds.value.indexOf(id)
  if (idx === -1) {
    selectedIds.value.push(id)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

async function batchDelete(): Promise<void> {
  if (selectedIds.value.length === 0) return
  try {
    await deleteCallRecordsApi(selectedIds.value)
    selectedIds.value = []
    loadRecords()
  } catch (error) {
    console.error('[CallHistory] Failed to delete:', error)
  }
}

// ---- 格式化 ----
function statusText(status: number): string {
  switch (status) {
    case CallStatus.RINGING: return '等待接听'
    case CallStatus.ACTIVE: return '已接听'
    case CallStatus.REJECTED: return '已拒绝'
    case CallStatus.CANCELLED: return '已取消'
    case CallStatus.ENDED: return '已挂断'
    case CallStatus.TIMEOUT: return '超时'
    case CallStatus.BUSY: return '忙线'
    default: return '未知'
  }
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${String(secs).padStart(2, '0')}`
}

function formatTime(time?: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  if (isToday) {
    return date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  }
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

onMounted(loadRecords)
</script>

<style scoped>
.call-history-page {
  padding: 24px;
  max-width: 800px;
  margin: 0 auto;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.btn-delete-batch {
  padding: 8px 16px;
  border: 1px solid #ef4444;
  border-radius: 8px;
  background: transparent;
  color: #ef4444;
  cursor: pointer;
  font-size: 13px;
  transition: all 0.15s;
}

.btn-delete-batch:hover {
  background: rgba(239, 68, 68, 0.1);
}

/* ---- 加载/空状态 ---- */
.loading-state,
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--color-text-tertiary);
}

.empty-state p {
  margin: 0;
  font-size: 16px;
}

.empty-state span {
  font-size: 13px;
}

/* ---- 通话记录列表 ---- */
.call-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  background: var(--color-border, rgba(255, 255, 255, 0.06));
  border-radius: 12px;
  overflow: hidden;
}

.call-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 14px 16px;
  background: var(--color-bg-elevated);
  cursor: pointer;
  transition: background 0.15s;
}

.call-item:hover {
  background: var(--color-bg-hover);
}

.call-item.selected {
  background: rgba(59, 130, 246, 0.1);
}

.call-type-icon {
  width: 40px;
  height: 40px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.call-type-icon.video {
  background: rgba(59, 130, 246, 0.15);
  color: #3b82f6;
}

.call-type-icon.voice {
  background: rgba(34, 197, 94, 0.15);
  color: #22c55e;
}

.call-info {
  flex: 1;
  min-width: 0;
}

.call-participants {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 4px;
}

.participant-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.call-direction {
  color: var(--color-text-tertiary);
}

.call-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.dot-sep {
  margin: 0 2px;
}

.call-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

/* ---- 分页 ---- */
.pagination {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 16px;
  margin-top: 24px;
}

.pagination button {
  padding: 8px 16px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: var(--color-bg-elevated);
  color: var(--color-text-primary);
  cursor: pointer;
  font-size: 13px;
}

.pagination button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pagination span {
  font-size: 13px;
  color: var(--color-text-secondary);
}
</style>