<template>
  <div class="transfer-table-wrapper">
    <!-- 桌面端：表格 -->
    <div class="hidden md:block">
      <div class="grid grid-cols-12 bg-neutral-50 px-4 py-2.5 text-xs font-medium text-neutral-500 border-b">
        <div class="col-span-5">文件名</div>
        <div class="col-span-1">类型</div>
        <div class="col-span-1">大小</div>
        <div class="col-span-1">时间</div>
        <div class="col-span-3">进度 / 状态</div>
        <div class="col-span-1 text-right">操作</div>
      </div>
      <div v-for="record in records" :key="record.id" class="grid grid-cols-12 items-center border-b px-4 py-3 text-sm transition hover:bg-neutral-50/60">
        <!-- 文件名 -->
        <div class="col-span-5 flex min-w-0 items-center gap-2.5">
          <span class="transfer-type-icon" :class="typeIconClass(record)">
            <i :class="record.type === 'upload' ? 'fa fa-upload' : 'fa fa-download'"></i>
          </span>
          <span class="truncate font-medium text-neutral-700" :title="record.fileName">{{ record.fileName }}</span>
        </div>
        <!-- 类型 -->
        <div class="col-span-1 text-neutral-500 text-xs">
          {{ record.type === 'upload' ? '上传' : '下载' }}
        </div>
        <!-- 大小 -->
        <div class="col-span-1 text-neutral-500 text-xs">
          {{ formatFileSize(record.fileSize) }}
        </div>
        <!-- 时间 -->
        <div class="col-span-1 text-neutral-400 text-xs">
          {{ formatRelativeTime(record.startTime) }}
        </div>
        <!-- 进度 / 状态 -->
        <div class="col-span-3">
          <!-- 上传/下载进度 -->
          <div v-if="record.status === 'uploading' || record.status === 'downloading'" class="flex items-center gap-3">
            <div class="h-1.5 flex-1 rounded-full bg-neutral-200 overflow-hidden min-w-0">
              <div
                class="h-full rounded-full transition-all duration-300"
                :class="record.type === 'upload' ? 'bg-primary' : 'bg-emerald-500'"
                :style="{ width: record.progress + '%' }"
              ></div>
            </div>
            <span class="text-xs font-semibold text-neutral-600 w-10 text-right">{{ record.progress.toFixed(0) }}%</span>
            <span v-if="record.speed" class="text-xs text-neutral-400 w-16 text-right">{{ record.speed }}</span>
          </div>
          <!-- 后台处理中 -->
          <div v-else-if="record.status === 'processing'" class="flex items-center gap-3">
            <div class="h-1.5 flex-1 rounded-full bg-neutral-200 overflow-hidden min-w-0">
              <div class="h-full rounded-full bg-amber-500 animate-pulse" style="width: 100%"></div>
            </div>
            <span class="text-xs font-medium text-amber-600 whitespace-nowrap">
              <i class="fa fa-spinner fa-pulse mr-1"></i>{{ record.processingStatus || '处理中' }}
            </span>
          </div>
          <!-- 完成 -->
          <span v-else-if="record.status === 'completed'" class="inline-flex items-center gap-1 text-xs font-medium text-emerald-600">
            <i class="fa fa-check-circle"></i>完成
          </span>
          <!-- 失败 -->
          <span v-else-if="record.status === 'failed'" class="inline-flex items-center gap-1 text-xs font-medium text-red-500" :title="record.processingStatus">
            <i class="fa fa-times-circle"></i>{{ record.processingStatus || '失败' }}
          </span>
          <!-- 取消 -->
          <span v-else-if="record.status === 'cancelled'" class="inline-flex items-center gap-1 text-xs font-medium text-neutral-400">
            <i class="fa fa-ban"></i>已取消
          </span>
        </div>
        <div class="col-span-1 text-right">
          <button
            v-if="canRetry(record)"
            type="button"
            class="retry-button"
            title="重新上传（从头开始）"
            @click="retry(record.id)"
          >
            <i class="fa fa-refresh mr-1"></i>重试
          </button>
        </div>
      </div>
    </div>

    <!-- 移动端：卡片列表 -->
    <div class="md:hidden divide-y">
      <div v-for="record in records" :key="record.id" class="px-4 py-3">
        <div class="flex items-start gap-3">
          <span class="transfer-type-icon mt-0.5" :class="typeIconClass(record)">
            <i :class="record.type === 'upload' ? 'fa fa-upload' : 'fa fa-download'"></i>
          </span>
          <div class="min-w-0 flex-1">
            <div class="text-sm font-medium text-neutral-700 truncate" :title="record.fileName">{{ record.fileName }}</div>
            <div class="mt-1 flex items-center gap-3 text-xs text-neutral-400">
              <span>{{ record.type === 'upload' ? '上传' : '下载' }}</span>
              <span>{{ formatFileSize(record.fileSize) }}</span>
              <span>{{ formatRelativeTime(record.startTime) }}</span>
            </div>
            <!-- 进度条 / 状态 -->
            <div class="mt-2" v-if="record.status === 'uploading' || record.status === 'downloading'">
              <div class="flex items-center gap-2">
                <div class="h-1.5 flex-1 rounded-full bg-neutral-200 overflow-hidden">
                  <div
                    class="h-full rounded-full transition-all duration-300"
                    :class="record.type === 'upload' ? 'bg-primary' : 'bg-emerald-500'"
                    :style="{ width: record.progress + '%' }"
                  ></div>
                </div>
                <span class="text-xs font-semibold text-neutral-600">{{ record.progress.toFixed(0) }}%</span>
              </div>
              <div v-if="record.speed" class="text-xs text-neutral-400 mt-1">{{ record.speed }}</div>
            </div>
            <div v-else-if="record.status === 'processing'" class="mt-2">
              <div class="h-1.5 w-full rounded-full bg-amber-500 animate-pulse"></div>
              <span class="text-xs font-medium text-amber-600 mt-1 block">
                <i class="fa fa-spinner fa-pulse mr-1"></i>{{ record.processingStatus || '处理中' }}
              </span>
            </div>
            <span v-else-if="record.status === 'completed'" class="inline-flex items-center gap-1 text-xs font-medium text-emerald-600 mt-1">
              <i class="fa fa-check-circle"></i>完成
            </span>
            <span v-else-if="record.status === 'failed'" class="inline-flex items-center gap-1 text-xs font-medium text-red-500 mt-1">
              <i class="fa fa-times-circle"></i>{{ record.processingStatus || '失败' }}
            </span>
            <span v-else class="inline-flex items-center gap-1 text-xs font-medium text-neutral-400 mt-1">
              <i class="fa fa-ban"></i>已取消
            </span>
            <button
              v-if="canRetry(record)"
              type="button"
              class="retry-button mt-2"
              title="重新上传（从头开始）"
              @click="retry(record.id)"
            >
              <i class="fa fa-refresh mr-1"></i>重试上传
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatFileSize } from '@/utils/helpers'
import { useTransferStore, type TransferRecord } from '@/stores/transferStore'

const props = defineProps<{ records: TransferRecord[] }>()
const records = computed(() => props.records)

const transferStore = useTransferStore()

function canRetry(record: TransferRecord): boolean {
  return record.type === 'upload' && record.status === 'failed'
}

function retry(id: number): void {
  void transferStore.retryRecord(id)
}

function typeIconClass(record: TransferRecord): string {
  if (record.status === 'completed') return 'icon-completed'
  if (record.status === 'failed' || record.status === 'cancelled') return 'icon-error'
  if (record.status === 'processing') return 'icon-processing'
  return record.type === 'upload' ? 'icon-upload' : 'icon-download'
}

function formatRelativeTime(timestamp: number): string {
  if (!timestamp) return '--'
  const now = Date.now()
  const diff = now - timestamp
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return `${Math.floor(diff / 60000)} 分钟前`
  if (diff < 86400000) return `${Math.floor(diff / 3600000)} 小时前`
  const d = new Date(timestamp)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}`
}
</script>

<style scoped>
.transfer-table-wrapper {
  overflow-x: auto;
}

.transfer-type-icon {
  width: 32px;
  height: 32px;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
}

.icon-upload { background: #eff6ff; color: #165dff; }
.icon-download { background: #ecfdf5; color: #10b981; }
.icon-processing { background: #fffbeb; color: #f59e0b; }
.icon-completed { background: #f0fdf4; color: #22c55e; }
.icon-error { background: #fef2f2; color: #ef4444; }
.retry-button {
  color: #165dff;
  font-size: 12px;
  white-space: nowrap;
}
.retry-button:hover { color: #0f46c9; text-decoration: underline; }
</style>
