<template>
  <div class="transfer-widget" ref="widgetRef">
    <!-- 头部图标按钮 -->
    <button
      class="transfer-trigger icon-button relative shrink-0"
      :class="{ 'is-active': panelOpen, 'pulse-dot': hasOngoing }"
      title="传输列表"
      @click="togglePanel"
    >
      <i class="fa fa-exchange"></i>
      <span v-if="ongoingCount > 0" class="transfer-badge">{{ ongoingCount }}</span>
    </button>

    <!-- 下拉面板：Teleport 到 body，无遮罩，只能通过 X 关闭 -->
    <Teleport to="body">
      <transition name="panel-slide">
        <div v-if="panelOpen" class="transfer-panel">
          <!-- 面板头部 -->
          <div class="transfer-panel-header">
            <div class="flex items-center gap-2">
              <i class="fa fa-exchange text-primary"></i>
              <span class="text-sm font-bold text-neutral-700">传输列表</span>
              <span v-if="ongoingCount > 0" class="ml-1 rounded-full bg-primary/10 px-2 py-0.5 text-[11px] font-semibold text-primary">
                {{ ongoingCount }} 个进行中
              </span>
            </div>
            <div class="flex items-center gap-1">
              <button
                v-if="store.records.some(r => r.status === 'completed' || r.status === 'failed' || r.status === 'cancelled')"
                class="text-xs text-neutral-400 hover:text-neutral-600 transition px-2 py-1 rounded"
                title="清除已完成/失败记录"
                @click="store.clearCompleted()"
              >
                <i class="fa fa-trash-o mr-1"></i>清除
              </button>
              <router-link
                to="/app/transfers"
                class="text-xs text-primary hover:text-primary/80 transition px-2 py-1 rounded hover:bg-primary/5"
                @click="panelOpen = false"
              >
                查看全部 <i class="fa fa-angle-right ml-0.5"></i>
              </router-link>
              <button class="text-neutral-400 hover:text-neutral-600 transition p-1" @click="closePanel">
                <i class="fa fa-times"></i>
              </button>
            </div>
          </div>

          <!-- 面板内容 -->
          <div class="transfer-panel-body">
            <!-- 空状态 -->
            <div v-if="store.records.length === 0" class="py-10 text-center text-neutral-400 text-sm">
              <i class="fa fa-inbox text-3xl mb-2 block"></i>
              暂无传输记录
            </div>

            <!-- 传输列表 -->
            <div v-else class="transfer-list">
              <template v-for="group in groupedRecords" :key="group.key">
                <!-- 文件夹分组头部 -->
                <div
                  v-if="group.type === 'folder'"
                  class="folder-group"
                >
                  <div
                    class="folder-group-header"
                    :class="{ 'is-expanded': isGroupExpanded(group.key) }"
                    @click="toggleGroup(group.key)"
                  >
                    <div class="folder-group-header-left">
                      <i
                        class="fa fa-folder text-amber-500 shrink-0"
                        :class="isGroupExpanded(group.key) ? 'fa-folder-open' : 'fa-folder'"
                      ></i>
                      <div class="folder-group-header-info">
                        <div class="folder-group-name">{{ group.folderName }}</div>
                        <div class="folder-group-stats">
                          {{ group.completedCount }} / {{ group.totalCount }} 个文件已完成
                          <span v-if="group.errorCount > 0" class="text-danger"> · {{ group.errorCount }} 失败</span>
                        </div>
                      </div>
                    </div>
                    <div class="folder-group-header-right">
                      <!-- 文件夹整体进度条 -->
                      <div class="folder-group-progress shrink-0">
                        <div
                          class="folder-group-progress-bar"
                          :class="group.folderProgress === 100 ? 'bg-emerald-500' : 'bg-primary'"
                          :style="{ width: group.folderProgress + '%' }"
                        ></div>
                      </div>
                      <span class="folder-group-percent">{{ group.folderProgress }}%</span>
                      <i
                        class="fa fa-chevron-down text-neutral-400 transition-transform duration-200 shrink-0"
                        :class="{ 'rotate-180': !isGroupExpanded(group.key) }"
                      ></i>
                    </div>
                  </div>

                  <!-- 文件夹内的文件列表 -->
                  <div v-show="isGroupExpanded(group.key)" class="folder-group-body">
                    <div
                      v-for="record in group.records"
                      :key="record.id"
                      class="transfer-item"
                      :class="{ 'is-ongoing': isOngoing(record) }"
                    >
                <!-- 文件图标 -->
                      <div class="transfer-item-icon" :class="iconClass(record)">
                        <i v-if="record.type === 'upload'" class="fa fa-upload"></i>
                        <i v-else class="fa fa-download"></i>
                      </div>

                <!-- 文件信息 -->
                      <div class="transfer-item-info">
                        <div class="transfer-item-name" :title="record.fileName">{{ record.fileName }}</div>
                        <div class="transfer-item-meta">
                          <template v-if="record.status === 'uploading' || record.status === 'downloading'">
                      <!-- 上传/下载进度条 -->
                            <div class="transfer-item-progress">
                              <div
                                class="transfer-item-progress-bar"
                                :class="record.type === 'upload' ? 'bg-primary' : 'bg-emerald-500'"
                                :style="{ width: record.progress + '%' }"
                              ></div>
                            </div>
                            <span class="transfer-item-percent">{{ record.progress.toFixed(1) }}%</span>
                            <span v-if="record.speed" class="transfer-item-speed">{{ record.speed }}</span>
                          </template>
                          <template v-else-if="record.status === 'processing'">
                            <div class="transfer-item-progress">
                              <div class="transfer-item-progress-bar bg-amber-500 animate-pulse" style="width: 100%"></div>
                            </div>
                            <span class="transfer-item-status processing">
                              <i class="fa fa-spinner fa-pulse mr-1"></i>{{ record.processingStatus || '处理中' }}
                            </span>
                          </template>
                          <template v-else-if="record.status === 'completed'">
                            <span class="transfer-item-status success">
                              <i class="fa fa-check-circle mr-1"></i>完成 · {{ formatFileSize(record.fileSize) }}
                            </span>
                          </template>
                          <template v-else-if="record.status === 'failed'">
                            <span class="transfer-item-status error">
                              <i class="fa fa-times-circle mr-1"></i>{{ record.processingStatus || '失败' }}
                            </span>
                          </template>
                          <template v-else-if="record.status === 'cancelled'">
                            <span class="transfer-item-status cancelled">
                              <i class="fa fa-ban mr-1"></i>已取消
                            </span>
                          </template>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 单文件记录（无文件夹分组） -->
                <div
                  v-else
                  class="transfer-item"
                  :class="{ 'is-ongoing': isOngoing(group.record) }"
                >
                  <div class="transfer-item-icon" :class="iconClass(group.record)">
                    <i v-if="group.record.type === 'upload'" class="fa fa-upload"></i>
                    <i v-else class="fa fa-download"></i>
                  </div>
                  <div class="transfer-item-info">
                    <div class="transfer-item-name" :title="group.record.fileName">{{ group.record.fileName }}</div>
                    <div class="transfer-item-meta">
                      <template v-if="group.record.status === 'uploading' || group.record.status === 'downloading'">
                        <div class="transfer-item-progress">
                          <div
                            class="transfer-item-progress-bar"
                            :class="group.record.type === 'upload' ? 'bg-primary' : 'bg-emerald-500'"
                            :style="{ width: group.record.progress + '%' }"
                          ></div>
                        </div>
                        <span class="transfer-item-percent">{{ group.record.progress.toFixed(1) }}%</span>
                        <span v-if="group.record.speed" class="transfer-item-speed">{{ group.record.speed }}</span>
                      </template>
                      <template v-else-if="group.record.status === 'processing'">
                        <div class="transfer-item-progress">
                          <div class="transfer-item-progress-bar bg-amber-500 animate-pulse" style="width: 100%"></div>
                        </div>
                        <span class="transfer-item-status processing">
                          <i class="fa fa-spinner fa-pulse mr-1"></i>{{ group.record.processingStatus || '处理中' }}
                        </span>
                      </template>
                      <template v-else-if="group.record.status === 'completed'">
                        <span class="transfer-item-status success">
                          <i class="fa fa-check-circle mr-1"></i>完成 · {{ formatFileSize(group.record.fileSize) }}
                        </span>
                      </template>
                      <template v-else-if="group.record.status === 'failed'">
                        <span class="transfer-item-status error">
                          <i class="fa fa-times-circle mr-1"></i>{{ group.record.processingStatus || '失败' }}
                        </span>
                      </template>
                      <template v-else-if="group.record.status === 'cancelled'">
                        <span class="transfer-item-status cancelled">
                          <i class="fa fa-ban mr-1"></i>已取消
                        </span>
                      </template>
                    </div>
                  </div>
                </div>
              </template>
            </div>
          </div>
        </div>
      </transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, reactive } from 'vue'
import { useTransferStore, type TransferRecord } from '@/stores/transferStore'
import { formatFileSize } from '@/utils/helpers'

const store = useTransferStore()
const panelOpen = ref(false)

const ongoingCount = computed(() => store.ongoingCount)
const hasOngoing = computed(() => store.hasOngoing)

// 展开的文件夹分组 key 集合
const expandedGroups = reactive(new Set<string>())

interface DisplayGroup {
  type: 'single' | 'folder'
  key: string
  // 单文件
  record?: TransferRecord
  // 文件夹分组
  folderName?: string
  records?: TransferRecord[]
  totalCount?: number
  completedCount?: number
  errorCount?: number
  folderProgress?: number
}

// 显示记录：进行中的排在前面，然后最近 10 条已完成的
const displayRecords = computed(() => {
  const all = [...store.records]
  const ongoing = all.filter(r => isOngoing(r))
  const rest = all.filter(r => !isOngoing(r)).slice(0, 10)
  return [...ongoing, ...rest]
})

// 将连续的同 folderName 记录分组
const groupedRecords = computed<DisplayGroup[]>(() => {
  const groups: DisplayGroup[] = []
  const records = displayRecords.value
  let i = 0

  while (i < records.length) {
    const current = records[i]
    if (current.folderName) {
      // 收集同 folderName 的连续记录
      const folderRecords: TransferRecord[] = []
      const folderName = current.folderName
      while (i < records.length && records[i].folderName === folderName) {
        folderRecords.push(records[i])
        i++
      }
      const completedCount = folderRecords.filter(r => r.status === 'completed').length
      const errorCount = folderRecords.filter(r => r.status === 'failed').length
      const folderProgress = folderRecords.length > 0
        ? Math.round((completedCount / folderRecords.length) * 100)
        : 0

      groups.push({
        type: 'folder',
        key: `folder-${folderName}-${folderRecords[0].id}`,
        folderName,
        records: folderRecords,
        totalCount: folderRecords.length,
        completedCount,
        errorCount,
        folderProgress,
      })
    } else {
      groups.push({
        type: 'single',
        key: `single-${current.id}`,
        record: current,
      })
      i++
    }
  }

  return groups
})

function isGroupExpanded(key: string): boolean {
  return expandedGroups.has(key)
}

function toggleGroup(key: string): void {
  if (expandedGroups.has(key)) {
    expandedGroups.delete(key)
  } else {
    expandedGroups.add(key)
  }
}

function isOngoing(record: TransferRecord): boolean {
  return record.status === 'uploading' || record.status === 'downloading' || record.status === 'processing'
}

function iconClass(record: TransferRecord): string {
  if (record.status === 'completed') return 'icon-completed'
  if (record.status === 'failed' || record.status === 'cancelled') return 'icon-error'
  if (record.status === 'processing') return 'icon-processing'
  return record.type === 'upload' ? 'icon-upload' : 'icon-download'
}

function togglePanel() {
  panelOpen.value = !panelOpen.value
}

function closePanel() {
  panelOpen.value = false
}

// 核心逻辑：当有新的传输操作开始时，自动弹出面板
let prevOngoingCount = 0

watch(ongoingCount, (newVal, oldVal) => {
  if (newVal > oldVal) {
    panelOpen.value = true
    // 有新的文件夹上传时，自动展开新的文件夹分组
    const folderGroups = groupedRecords.value.filter(g => g.type === 'folder')
    for (const group of folderGroups) {
      const hasOngoing = group.records?.some(r => isOngoing(r))
      if (hasOngoing) {
        expandedGroups.add(group.key)
      }
    }
  }
  prevOngoingCount = newVal
})
</script>

<style scoped>
.transfer-widget {
  position: relative;
}

.transfer-trigger {
  transition: color 0.2s;
}

.transfer-trigger.is-active,
.transfer-trigger:hover {
  color: #165dff;
}

.transfer-trigger.pulse-dot::after {
  content: '';
  position: absolute;
  top: 6px;
  right: 6px;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #165dff;
  animation: transfer-dot-pulse 2s infinite;
}

@keyframes transfer-dot-pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.3); }
}

.transfer-badge {
  position: absolute;
  top: -2px;
  right: -4px;
  min-width: 18px;
  height: 18px;
  border-radius: 9px;
  background: #f43f5e;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  line-height: 1;
  box-shadow: 0 2px 6px rgba(244, 63, 94, 0.3);
}

/* 面板 */
.transfer-panel {
  position: fixed;
  top: 80px;
  right: 20px;
  z-index: 9999;
  width: 420px;
  max-height: 560px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.15), 0 0 0 1px rgba(0, 0, 0, 0.05);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.transfer-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-bottom: 1px solid #f0f0f0;
  flex-shrink: 0;
}

.transfer-panel-body {
  overflow-y: auto;
  flex: 1;
  min-height: 0;
}

.transfer-list {
  padding: 6px 0;
}

/* ====== 文件夹分组 ====== */
.folder-group {
  border-bottom: 1px solid #f0f0f0;
}

.folder-group:last-child {
  border-bottom: none;
}

.folder-group-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  background: #fafbfc;
  transition: background 0.15s;
  user-select: none;
}

.folder-group-header:hover {
  background: #f0f4ff;
}

.folder-group-header.is-expanded {
  background: #f0f4ff;
}

.folder-group-header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}

.folder-group-header-info {
  min-width: 0;
}

.folder-group-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.folder-group-stats {
  font-size: 11px;
  color: #9ca3af;
  margin-top: 1px;
}

.folder-group-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.folder-group-progress {
  width: 60px;
  height: 4px;
  background: #e5e7eb;
  border-radius: 2px;
  overflow: hidden;
}

.folder-group-progress-bar {
  height: 100%;
  border-radius: 2px;
  transition: width 0.3s ease;
}

.folder-group-percent {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  min-width: 2.5em;
  text-align: right;
}

.folder-group-body {
  border-top: 1px solid #f5f5f5;
}

/* ====== 传输条目 ====== */
.transfer-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 10px 16px;
  transition: background 0.15s;
}

.transfer-item:hover {
  background: #f8fafc;
}

.transfer-item + .transfer-item {
  border-top: 1px solid #f5f5f5;
}

.transfer-item-icon {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 14px;
  flex-shrink: 0;
  margin-top: 2px;
}

.icon-upload {
  background: #eff6ff;
  color: #165dff;
}

.icon-download {
  background: #ecfdf5;
  color: #10b981;
}

.icon-processing {
  background: #fffbeb;
  color: #f59e0b;
}

.icon-completed {
  background: #f0fdf4;
  color: #22c55e;
}

.icon-error {
  background: #fef2f2;
  color: #ef4444;
}

.transfer-item-info {
  flex: 1;
  min-width: 0;
}

.transfer-item-name {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-bottom: 6px;
}

.transfer-item-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.transfer-item-progress {
  flex: 1;
  min-width: 60px;
  height: 5px;
  background: #e5e7eb;
  border-radius: 3px;
  overflow: hidden;
}

.transfer-item-progress-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.3s ease;
}

.transfer-item-percent {
  font-size: 11px;
  font-weight: 600;
  color: #6b7280;
  white-space: nowrap;
}

.transfer-item-speed {
  font-size: 11px;
  color: #9ca3af;
  white-space: nowrap;
}

.transfer-item-status {
  font-size: 12px;
  font-weight: 500;
}

.transfer-item-status.success {
  color: #16a34a;
}

.transfer-item-status.error {
  color: #dc2626;
}

.transfer-item-status.cancelled {
  color: #9ca3af;
}

.transfer-item-status.processing {
  color: #d97706;
}

/* 面板动画 */
.panel-slide-enter-active {
  transition: all 0.2s ease-out;
}

.panel-slide-leave-active {
  transition: all 0.15s ease-in;
}

.panel-slide-enter-from {
  opacity: 0;
  transform: translateY(-8px) scale(0.97);
}

.panel-slide-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.98);
}

/* 响应式 */
@media (max-width: 440px) {
  .transfer-panel {
    right: 8px;
    left: 8px;
    width: auto;
  }
}
</style>