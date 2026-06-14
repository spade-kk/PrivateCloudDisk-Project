<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="操作日志"
      description="完整的用户操作记录、文件操作追溯与安全审计"
      :breadcrumbs="[{ label: '操作日志', icon: 'fa fa-history' }]"
      :stats="summaryStats"
    >
      <template #actions>
        <button @click="activityStore.exportLogs()" class="rounded-lg border border-neutral-200 px-4 py-2 text-sm text-neutral-600 hover:bg-neutral-50">
          <i class="fa fa-download mr-1"></i> 导出日志
        </button>
      </template>
    </PageHeader>

    <!-- 筛选栏 -->
    <div class="responsive-panel flex flex-wrap items-center gap-3 p-3 sm:p-4">
      <div class="relative flex-1 sm:flex-none sm:w-64">
        <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-xs text-neutral-400"></i>
        <input
          v-model="activityStore.filters.keyword"
          @input="activityStore.setFilters({ keyword: $event.target.value })"
          placeholder="搜索操作日志..."
          class="w-full rounded-lg border border-neutral-200 py-1.5 pl-8 pr-3 text-sm focus:border-primary focus:outline-none"
        />
      </div>
      <select v-model="activityStore.filters.actionType" @change="activityStore.setFilters({ actionType: $event.target.value })" class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-neutral-600 focus:border-primary focus:outline-none">
        <option value="">全部类型</option>
        <option value="upload">上传</option>
        <option value="download">下载</option>
        <option value="delete">删除</option>
        <option value="create">创建</option>
        <option value="update">更新</option>
        <option value="share">分享</option>
        <option value="login">登录</option>
        <option value="security">安全</option>
      </select>
      <button @click="activityStore.resetFilters()" class="text-sm text-neutral-400 hover:text-primary">
        <i class="fa fa-times"></i> 清除筛选
      </button>
      <button @click="activityStore.fetchLogs()" class="icon-button ml-auto" title="刷新">
        <i class="fa fa-refresh"></i>
      </button>
    </div>

    <!-- 日志列表 -->
    <div class="responsive-panel overflow-hidden">
      <div v-if="activityStore.logsLoading" class="flex flex-col items-center py-20">
        <LoadingSpinner />
        <p class="mt-4 text-sm text-neutral-400">加载中...</p>
      </div>
      <div v-else-if="activityStore.logs.length === 0" class="py-16 text-center">
        <i class="fa fa-inbox text-3xl text-neutral-300"></i>
        <p class="mt-3 text-sm text-neutral-400">暂无操作日志</p>
      </div>
      <div v-else class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-neutral-100 bg-neutral-50/50">
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">操作类型</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">用户</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">详情</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">IP</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">时间</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(log, i) in activityStore.logs" :key="log.id || i" class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50">
              <td class="px-4 py-3">
                <StatusBadge :status="log.actionType || log.action" :statusMap="actionTypeMap" />
              </td>
              <td class="px-4 py-3 text-neutral-700">{{ log.username || log.userId || '--' }}</td>
              <td class="px-4 py-3 max-w-xs truncate text-neutral-600">{{ log.detail || log.description || '--' }}</td>
              <td class="px-4 py-3 text-neutral-500 font-mono text-xs">{{ log.ip || '--' }}</td>
              <td class="px-4 py-3 text-neutral-400 text-xs">{{ formatTime(log.createdAt || log.time) }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <!-- 分页 -->
      <div v-if="activityStore.totalPages > 1" class="flex items-center justify-between border-t border-neutral-100 px-4 py-3">
        <span class="text-xs text-neutral-400">共 {{ activityStore.logsTotal }} 条记录</span>
        <div class="flex items-center gap-1">
          <button
            :disabled="activityStore.logsPage <= 1"
            @click="activityStore.setPage(activityStore.logsPage - 1)"
            class="rounded-lg px-2 py-1 text-sm text-neutral-400 hover:bg-neutral-100 disabled:opacity-30"
          >
            <i class="fa fa-angle-left"></i>
          </button>
          <span class="px-3 text-sm text-neutral-600">{{ activityStore.logsPage }} / {{ activityStore.totalPages }}</span>
          <button
            :disabled="activityStore.logsPage >= activityStore.totalPages"
            @click="activityStore.setPage(activityStore.logsPage + 1)"
            class="rounded-lg px-2 py-1 text-sm text-neutral-400 hover:bg-neutral-100 disabled:opacity-30"
          >
            <i class="fa fa-angle-right"></i>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useActivityStore } from '@/stores/activityStore'

const activityStore = useActivityStore()

const actionTypeMap = {
  upload: 'bg-primary/10 text-primary',
  download: 'bg-info/10 text-info',
  delete: 'bg-danger/10 text-danger',
  create: 'bg-success/10 text-success',
  update: 'bg-warning/10 text-warning',
  share: 'bg-purple-100 text-purple-600',
  login: 'bg-success/10 text-success',
  security: 'bg-danger/10 text-danger',
}

const summaryStats = computed(() => [
  { key: 'total', title: '今日操作', value: activityStore.summary?.today || 0, unit: '次' },
  { key: 'uploads', title: '上传', value: activityStore.summary?.uploads || 0, unit: '次' },
  { key: 'downloads', title: '下载', value: activityStore.summary?.downloads || 0, unit: '次' },
  { key: 'logins', title: '登录', value: activityStore.summary?.logins || 0, unit: '次' },
])

function formatTime(time) {
  if (!time) return '--'
  try { return new Date(time).toLocaleString('zh-CN') } catch { return time }
}

onMounted(() => {
  activityStore.fetchSummary()
  activityStore.fetchLogs()
})
</script>