<template>
  <div class="space-y-5">
    <PageHeader
      :title="plugin?.name ? `${plugin.name} · 执行记录` : '插件执行记录'"
      description="按时间和状态查看脱敏运行结果，避免将沙箱内部路径或敏感数据暴露到浏览器。"
      :breadcrumbs="[
        { label: '插件中心', to: '/app/plugins' },
        { label: plugin?.name || '执行记录' },
      ]"
    >
      <template #actions>
        <router-link v-if="plugin" class="secondary-button" :to="`/developer/plugins/${plugin.pluginId}/edit`">
          <i class="fa fa-pencil"></i> 打开 IDE
        </router-link>
        <button class="secondary-button" type="button" :disabled="loading" @click="load">
          <i class="fa fa-refresh" :class="{ 'fa-spin': loading }"></i> 刷新
        </button>
      </template>
    </PageHeader>

    <section class="grid gap-3 rounded-2xl border border-neutral-200 bg-white p-4 shadow-sm md:grid-cols-[1fr_180px_auto]">
      <label class="form-label">时间范围
        <select v-model="range" class="form-input" @change="load">
          <option value="24h">最近 24 小时</option>
          <option value="7d">最近 7 天</option>
          <option value="30d">最近 30 天</option>
          <option value="all">全部记录</option>
        </select>
      </label>
      <label class="form-label">状态
        <select v-model="status" class="form-input" @change="load">
          <option value="">全部</option>
          <option value="SUCCESS">成功</option>
          <option value="FAILED">失败</option>
          <option value="TIMEOUT">超时</option>
          <option value="RUNNING">运行中</option>
        </select>
      </label>
      <div class="flex items-end">
        <div class="rounded-xl bg-neutral-50 px-4 py-2 text-xs text-neutral-500">
          <strong class="mr-1 text-neutral-800">{{ executions.length }}</strong> 条已加载
        </div>
      </div>
    </section>

    <PageState
      v-if="loading || error || !executions.length"
      :type="loading ? 'loading' : error ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : error ? 'fa fa-exclamation-triangle' : 'fa fa-history'"
      :title="loading ? '正在加载执行记录' : error ? '执行记录加载失败' : '暂无匹配记录'"
      :description="error || '插件运行后，脱敏后的状态和输出摘要会显示在这里。'"
      :action-text="error ? '重试' : ''"
      action-icon="fa fa-refresh"
      @action="load"
    />

    <section v-else class="overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-sm">
      <div class="hidden grid-cols-[170px_160px_110px_100px_minmax(0,1fr)_auto] gap-3 border-b border-neutral-100 bg-neutral-50 px-4 py-3 text-xs font-semibold text-neutral-500 lg:grid">
        <span>执行时间</span><span>触发事件</span><span>状态</span><span>耗时</span><span>输出摘要</span><span>操作</span>
      </div>
      <article v-for="item in executions" :key="item.executionId" class="execution-row">
        <time class="text-xs text-neutral-500">{{ formatDate(item.startedAt || item.startTime) }}</time>
        <span class="break-all font-mono text-[11px] text-neutral-500">{{ item.triggerEvent || '手动运行' }}</span>
        <StatusBadge :status="item.executionStatus || item.status || 'UNKNOWN'" />
        <span class="text-xs text-neutral-500">{{ formatDuration(item) }}</span>
        <p class="min-w-0 break-words rounded-lg bg-neutral-50 px-3 py-2 text-xs leading-5 text-neutral-600">
          {{ item.outputSummary || item.errorMessage || '无输出摘要' }}
        </p>
        <div class="flex justify-end gap-2">
          <button class="row-button" type="button" @click="openDetail(item)">
            <i class="fa fa-terminal"></i>
            <span class="hidden sm:inline">详情</span>
          </button>
          <button v-if="canReplay(item)" class="row-button" type="button" :disabled="replaying === item.executionId" @click="replay(item)">
            <i class="fa" :class="replaying === item.executionId ? 'fa-spinner fa-spin' : 'fa-repeat'"></i>
            <span class="hidden sm:inline">重跑</span>
          </button>
        </div>
      </article>
    </section>
    <PluginExecutionDetailDrawer v-model="detailOpen" :execution-id="selectedExecution?.executionId || ''" :plugin-id="pluginId" :plugin-name="plugin?.name || ''" />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { getPluginApi, pluginExecutionsApi, type PluginExecutionInfo, type PluginInfo } from '@/api/modules/plugins'
import { useToastStore } from '@/stores/toastStore'
import PluginExecutionDetailDrawer from '@/components/plugins/execution/PluginExecutionDetailDrawer.vue'

const route = useRoute()
const toast = useToastStore()
const pluginId = computed(() => String(route.params.pluginId || ''))
const plugin = ref<PluginInfo | null>(null)
const executions = ref<any[]>([])
const loading = ref(false)
const error = ref('')
const status = ref('')
const range = ref('7d')
const replaying = ref('')
const selectedExecution = ref<PluginExecutionInfo | null>(null)
const detailOpen = ref(false)

function formatDate(value?: string) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString()
}

function formatDuration(item: any) {
  const start = Date.parse(item.startedAt || item.startTime || '')
  const end = Date.parse(item.endedAt || item.endTime || '')
  if (!Number.isFinite(start) || !Number.isFinite(end) || end < start) return '—'
  return `${Math.max(0, end - start)} ms`
}

function canReplay(item: any) {
  return Boolean(item.triggerEvent && item.triggerEvent !== 'manual' && ['FAILED', 'TIMEOUT', 'FAILED_RETRYABLE'].includes(item.executionStatus || item.status))
}

function openDetail(item: PluginExecutionInfo) {
  selectedExecution.value = item
  detailOpen.value = true
}

async function replay(item: any) {
  // 当前后端仅在内部自动化接口提供事件重放；前端保留按钮和兼容提示，避免伪造生命周期请求。
  replaying.value = item.executionId
  try {
    toast.showToast('当前服务尚未开放公开重放接口，未发送请求；请由自动化管理端重放', 'warning')
    // 后端补充 POST /plugins/executions/{id}/replay 后在此处接入；当前不发送不存在的接口请求。
  } finally {
    replaying.value = ''
  }
}

async function load() {
  if (!pluginId.value) return
  loading.value = true
  error.value = ''
  try {
    const [pluginRes, executionRes] = await Promise.all([
      getPluginApi(pluginId.value),
      pluginExecutionsApi(pluginId.value, status.value),
    ])
    plugin.value = pluginRes.data
    executions.value = Array.isArray(executionRes.data) ? executionRes.data : []
  } catch (err: any) {
    error.value = err?.message || '执行记录加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form-label { @apply block text-xs font-semibold text-neutral-500; }
.form-input { @apply mt-1.5 w-full rounded-xl border border-neutral-200 bg-white px-3 py-2.5 text-sm text-neutral-700 outline-none focus:border-primary focus:ring-2 focus:ring-primary/10; }
.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl border border-neutral-200 bg-white px-4 text-sm font-semibold text-neutral-600 transition hover:border-primary/30 hover:text-primary disabled:opacity-50; }
.execution-row { @apply grid gap-3 border-b border-neutral-100 p-4 last:border-0 lg:grid-cols-[170px_160px_110px_100px_minmax(0,1fr)_auto] lg:items-center; }
.row-button { @apply inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-neutral-200 px-3 text-xs font-semibold text-neutral-600 transition hover:border-primary/30 hover:text-primary disabled:opacity-50; }
</style>
