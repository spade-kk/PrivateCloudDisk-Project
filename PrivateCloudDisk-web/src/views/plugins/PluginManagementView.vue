<template>
  <div class="space-y-5">
    <PageHeader
      title="插件中心"
      description="开发、安装和观察个人与空间插件"
      :breadcrumbs="[{ label: '插件中心' }]"
    >
      <template #actions>
        <router-link class="secondary-button" to="/app/plugin-market">
          <i class="fa fa-compass"></i> 插件市场
        </router-link>
        <router-link class="secondary-button" to="/app/plugins/new/local">
          <i class="fa fa-desktop"></i> 新建本地插件
        </router-link>
        <button class="secondary-button" type="button" @click="openWebRuntime">
          <i class="fa fa-play-circle"></i> Web 插件运行时
        </button>
        <router-link class="primary-button" to="/app/plugins/new/cloud">
          <i class="fa fa-cloud-upload"></i> 新建云插件
        </router-link>
      </template>
    </PageHeader>

    <section class="rounded-2xl border border-neutral-200 bg-white p-3 shadow-sm sm:p-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
        <div class="flex overflow-x-auto rounded-xl bg-neutral-100 p-1">
          <button
            v-for="item in filters"
            :key="item.value"
            class="filter-button"
            :class="{ active: filter === item.value }"
            @click="filter = item.value"
          >{{ item.label }}</button>
        </div>
        <div class="relative sm:w-72">
          <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"></i>
          <input v-model.trim="keyword" class="search-input" placeholder="搜索插件名称或标识" />
        </div>
      </div>
    </section>

    <PageState
      v-if="loading || loadError || !visiblePlugins.length"
      :type="loading ? 'loading' : loadError ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : loadError ? 'fa fa-exclamation-triangle' : 'fa fa-puzzle-piece'"
      :title="loading ? '正在加载插件' : loadError ? '插件加载失败' : '还没有符合条件的插件'"
      :description="loadError || '创建第一个云插件，让文件在生命周期中自动产生业务价值。'"
      :action-text="loadError ? '重新加载' : ''"
      action-icon="fa fa-refresh"
      @action="loadPlugins"
    />

    <div v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="plugin in visiblePlugins" :key="plugin.pluginId" class="plugin-card">
        <div class="flex items-start gap-3">
          <div class="plugin-icon" :class="iconTone(plugin.pluginType)">
            <i :class="plugin.pluginType === 'LOCAL_PLUGIN' ? 'fa fa-desktop' : plugin.pluginType === 'WORKFLOW_PLUGIN' ? 'fa fa-sitemap' : 'fa fa-cloud'"></i>
          </div>
          <div class="min-w-0 flex-1">
            <div class="flex items-center justify-between gap-2">
              <h2 class="truncate text-base font-bold text-neutral-800">{{ plugin.name }}</h2>
              <StatusBadge :status="plugin.status" />
            </div>
            <p class="mt-0.5 truncate font-mono text-xs text-neutral-400">{{ plugin.slug }}</p>
          </div>
        </div>
        <p class="mt-4 line-clamp-2 min-h-10 text-sm leading-5 text-neutral-500">
          {{ plugin.description || '暂无描述' }}
        </p>
        <div class="mt-4 grid grid-cols-3 gap-2 rounded-xl bg-neutral-50 p-3 text-center">
          <div><strong>{{ stats[plugin.pluginId]?.totalExecutions ?? '—' }}</strong><small>执行</small></div>
          <div><strong>{{ stats[plugin.pluginId]?.successRate ?? '—' }}<em v-if="stats[plugin.pluginId]">%</em></strong><small>成功率</small></div>
          <div><strong>{{ formatTime(stats[plugin.pluginId]?.lastExecutedAt) }}</strong><small>最近运行</small></div>
        </div>
        <div class="mt-4 flex items-center justify-between border-t border-neutral-100 pt-3">
          <span class="rounded-lg bg-neutral-100 px-2 py-1 text-[11px] font-semibold text-neutral-500">
            {{ plugin.pluginType === 'LOCAL_PLUGIN' ? '本地插件' : plugin.pluginType === 'WORKFLOW_PLUGIN' ? '工作流' : '云插件' }}
          </span>
          <div class="flex gap-1">
            <router-link class="card-action" :to="`/developer/plugins/${plugin.pluginId}/edit`" title="在开发 IDE 中编辑">
              <i class="fa fa-pencil"></i>
            </router-link>
            <button v-if="userInstallations[plugin.pluginId]" class="card-action" :class="userInstallations[plugin.pluginId].enabled ? 'text-success' : 'text-neutral-400'" :title="userInstallations[plugin.pluginId].enabled ? '停用插件' : '启用插件'" @click="togglePlugin(plugin)">
              <i :class="userInstallations[plugin.pluginId].enabled ? 'fa fa-toggle-on' : 'fa fa-toggle-off'"></i>
            </button>
            <button
              v-if="plugin.status === 'PUBLISHED' && plugin.visibility === 'PUBLIC'"
              class="card-action"
              title="提交插件市场审核"
              @click="submitToMarketplace(plugin)"
            >
              <i class="fa fa-shopping-bag"></i>
            </button>
            <button class="card-action" title="查看执行记录" @click="showExecutions(plugin)">
              <i class="fa fa-list-alt"></i>
            </button>
            <button class="card-action text-danger hover:!bg-danger/10 hover:!text-danger" title="删除草稿" @click="removePlugin(plugin)">
              <i class="fa fa-trash-o"></i>
            </button>
          </div>
        </div>
      </article>
    </div>

    <Transition name="page-fade">
      <div v-if="executionPanel" class="fixed inset-0 z-[100] flex justify-end bg-black/35" @click.self="executionPanel = null">
        <aside class="h-full w-full max-w-xl overflow-y-auto bg-white p-5 shadow-2xl sm:p-6">
          <div class="flex items-center justify-between">
            <div><h2 class="text-lg font-bold">{{ executionPanel.name }}</h2><p class="text-xs text-neutral-400">脱敏执行记录</p></div>
            <button class="icon-button" @click="executionPanel = null"><i class="fa fa-times"></i></button>
          </div>
          <div v-if="executionLoading" class="py-16 text-center text-sm text-neutral-400">正在加载执行记录…</div>
          <div v-else class="mt-5 space-y-3">
            <article v-for="item in executions" :key="item.executionId" class="rounded-xl border border-neutral-200 p-4">
              <div class="flex items-center justify-between"><StatusBadge :status="item.executionStatus" /><time class="text-xs text-neutral-400">{{ item.startedAt }}</time></div>
              <p class="mt-3 break-words rounded-lg bg-neutral-50 p-3 font-mono text-xs leading-5 text-neutral-600">{{ item.outputSummary || '无输出摘要' }}</p>
              <p v-if="item.errorCode" class="mt-2 text-xs text-danger">{{ item.errorCode }}</p>
              <div class="mt-3 flex justify-end"><button class="row-button" type="button" @click="showExecutionDetail(item)"><i class="fa fa-terminal"></i> 日志与审计</button></div>
            </article>
            <p v-if="!executions.length" class="py-16 text-center text-sm text-neutral-400">暂无执行记录</p>
          </div>
        </aside>
      </div>
    </Transition>

    <PluginExecutionDetailDrawer v-model="executionDetailOpen" :execution-id="selectedExecution?.executionId || ''" :plugin-id="executionPanel?.pluginId || ''" :plugin-name="executionPanel?.name || ''" />

    <Transition name="page-fade">
      <div
        v-if="runtimePanel"
        class="fixed inset-0 z-[105] flex items-center justify-center bg-black/45 p-3 sm:p-6"
        @click.self="closeWebRuntime"
      >
        <section class="runtime-dialog">
          <header class="flex items-start justify-between gap-4 border-b border-neutral-100 p-5">
            <div>
              <h2 class="text-lg font-bold text-neutral-900">Web 本地插件运行时</h2>
              <p class="mt-1 text-xs leading-5 text-neutral-400">
                软件密钥可信等级 low · 签名验包 · 无同源 iframe · 能力逐项授权
              </p>
            </div>
            <button class="icon-button" type="button" @click="closeWebRuntime">
              <i class="fa fa-times"></i>
            </button>
          </header>

          <div class="grid min-h-0 flex-1 lg:grid-cols-[300px_minmax(0,1fr)]">
            <aside class="overflow-y-auto border-b border-neutral-100 p-4 lg:border-b-0 lg:border-r">
              <button
                class="secondary-button mb-4 w-full justify-center"
                type="button"
                :disabled="runtimeLoading"
                @click="loadRuntimePlugins"
              >
                <i class="fa fa-refresh" :class="{ 'fa-spin': runtimeLoading }"></i>
                刷新已安装插件
              </button>
              <PageState
                v-if="runtimeLoading || runtimeError || !distributions.length"
                :type="runtimeLoading ? 'loading' : runtimeError ? 'error' : 'empty'"
                :icon="runtimeLoading ? 'fa fa-spinner fa-spin' : runtimeError ? 'fa fa-exclamation-triangle' : 'fa fa-plug'"
                :title="runtimeLoading ? '正在建立可信客户端身份' : runtimeError ? '运行时不可用' : '当前没有 Web 本地插件'"
                :description="runtimeError || '请先安装支持 web 平台的本地插件。'"
                :action-text="runtimeError ? '重试' : ''"
                @action="loadRuntimePlugins"
              />
              <div v-else class="space-y-2">
                <button
                  v-for="item in distributions"
                  :key="item.installationId"
                  type="button"
                  class="runtime-plugin-item"
                  :class="{ active: activeDistribution?.installationId === item.installationId }"
                  @click="selectDistribution(item)"
                >
                  <span class="plugin-icon bg-violet-100 text-violet-600"><i class="fa fa-code"></i></span>
                  <span class="min-w-0 text-left">
                    <strong>{{ item.name }}</strong>
                    <small>v{{ item.version }} · {{ item.installationScope === 'SPACE' ? '空间' : '个人' }}</small>
                  </span>
                </button>
              </div>
            </aside>

            <main class="flex min-h-0 flex-col overflow-y-auto p-4 sm:p-5">
              <template v-if="activeDistribution">
                <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                  <div>
                    <h3 class="font-bold text-neutral-800">{{ activeDistribution.name }}</h3>
                    <p class="mt-1 text-xs text-neutral-400">
                      SHA-256 {{ activeDistribution.packageSha256.slice(0, 16) }}… ·
                      {{ formatBytes(activeDistribution.packageSize) }}
                    </p>
                  </div>
                  <div class="flex gap-2">
                    <label class="secondary-button cursor-pointer">
                      <i class="fa fa-file-o"></i> 选择输入文件
                      <input class="sr-only" type="file" @change="selectRuntimeFile" />
                    </label>
                    <button
                      class="primary-button"
                      type="button"
                      :disabled="runtimeExecuting"
                      @click="runActivePlugin"
                    >
                      <i class="fa" :class="runtimeExecuting ? 'fa-spinner fa-spin' : 'fa-play'"></i>
                      {{ runtimeExecuting ? '运行中…' : '运行插件' }}
                    </button>
                  </div>
                </div>
                <p class="mt-3 rounded-xl bg-neutral-50 px-3 py-2 text-xs text-neutral-500">
                  输入文件：{{ runtimeInputFile?.name || '未选择；插件请求读取文件时将被拒绝' }}
                </p>
                <div class="mt-4 flex flex-wrap gap-2">
                  <span
                    v-for="permission in activePermissions"
                    :key="permission"
                    class="rounded-lg bg-blue-50 px-2.5 py-1 text-[11px] font-semibold text-primary"
                  >{{ permission }}</span>
                </div>
                <div v-if="runtimeUiMessage" class="mt-4 rounded-xl border border-primary/10 bg-primary/[0.03] p-3 text-sm text-neutral-600">
                  {{ runtimeUiMessage }}
                </div>
                <div ref="runtimeMount" class="runtime-sandbox-mount">
                  <div class="text-center text-xs text-neutral-400">
                    <i class="fa fa-shield mb-2 block text-2xl text-success"></i>
                    沙箱将在验签成功后装载
                  </div>
                </div>
                <section class="mt-4 min-h-32 rounded-xl bg-neutral-950 p-3 font-mono text-xs text-neutral-300">
                  <div v-if="!runtimeLogs.length" class="text-neutral-600">等待插件输出…</div>
                  <div v-for="(line, index) in runtimeLogs" :key="index" class="break-all leading-5">
                    <span class="text-neutral-600">[{{ line.level }}]</span> {{ line.message }}
                  </div>
                </section>
              </template>
              <div v-else class="m-auto py-20 text-center text-sm text-neutral-400">
                <i class="fa fa-hand-pointer-o mb-3 block text-3xl"></i>
                选择一个已安装的 Web 本地插件
              </div>
            </main>
          </div>
        </section>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import PluginExecutionDetailDrawer from '@/components/plugins/execution/PluginExecutionDetailDrawer.vue'
import {
  deletePluginApi,
  listWebLocalPluginDistributionsApi,
  listPluginInstallationsApi,
  listPluginsApi,
  pluginExecutionsApi,
  pluginExecutionStatsApi,
  recordWebLocalPluginExecutionApi,
  submitMarketplacePluginApi,
  setPluginEnabledApi,
  type PluginInfo,
  type PluginExecutionInfo,
  type PluginInstallation,
} from '@/api/modules/plugins'
import { useToastStore } from '@/stores/toastStore'
import { useUploaderStore } from '@/stores/uploaderStore'
import { useSpaceStore } from '@/stores/spaceStore'
import {
  WebLocalPluginRuntime,
  type LocalPluginDistribution,
} from '@/runtime/localPluginRuntime'

const toast = useToastStore()
const uploaderStore = useUploaderStore()
const spaceStore = useSpaceStore()
const plugins = ref<PluginInfo[]>([])
const loading = ref(false)
const loadError = ref('')
const filter = ref('ALL')
const keyword = ref('')
const stats = reactive<Record<string, any>>({})
const userInstallations = reactive<Record<string, PluginInstallation>>({})
const executionPanel = ref<PluginInfo | null>(null)
const executionLoading = ref(false)
const executions = ref<any[]>([])
const selectedExecution = ref<PluginExecutionInfo | null>(null)
const executionDetailOpen = ref(false)
const runtimePanel = ref(false)
const runtimeLoading = ref(false)
const runtimeError = ref('')
const runtimeExecuting = ref(false)
const distributions = ref<LocalPluginDistribution[]>([])
const activeDistribution = ref<LocalPluginDistribution | null>(null)
const runtimeMount = ref<HTMLElement | null>(null)
const runtimeInputFile = ref<File | null>(null)
const runtimeLogs = ref<Array<{ level: string; message: string }>>([])
const runtimeUiMessage = ref('')
let localRuntime: WebLocalPluginRuntime | null = null
const filters = [
  { label: '全部', value: 'ALL' },
  { label: '云插件', value: 'CLOUD_PLUGIN' },
  { label: '本地插件', value: 'LOCAL_PLUGIN' },
  { label: '工作流', value: 'WORKFLOW_PLUGIN' },
  { label: '已发布', value: 'PUBLISHED' },
]

const visiblePlugins = computed(() => plugins.value.filter((plugin) => {
  const filterMatch = filter.value === 'ALL'
    || plugin.pluginType === filter.value
    || plugin.status === filter.value
  const query = keyword.value.toLowerCase()
  return filterMatch && (!query
    || plugin.name.toLowerCase().includes(query)
    || plugin.slug.toLowerCase().includes(query))
}))

async function loadPlugins() {
  loading.value = true
  loadError.value = ''
  try {
    const [pluginResponse, installationResponse] = await Promise.all([listPluginsApi(), listPluginInstallationsApi()])
    plugins.value = pluginResponse.data
    installationResponse.data.filter((item) => item.scopeType === 'USER').forEach((item) => { userInstallations[item.pluginId] = item })
    await Promise.allSettled(plugins.value.map(async (plugin) => {
      stats[plugin.pluginId] = (await pluginExecutionStatsApi(plugin.pluginId)).data
    }))
  } catch (error: any) {
    loadError.value = error?.message || '插件列表加载失败'
  } finally {
    loading.value = false
  }
}

async function togglePlugin(plugin: PluginInfo) {
  const installation = userInstallations[plugin.pluginId]
  if (!installation) { toast.showToast('该插件尚未安装到个人账号，请先从市场安装', 'warning'); return }
  try { await setPluginEnabledApi(installation.installationId, !installation.enabled); installation.enabled = !installation.enabled; toast.showToast(installation.enabled ? '插件已启用' : '插件已停用', 'success') } catch (error: any) { toast.showToast(error?.message || '插件状态更新失败', 'error') }
}

async function showExecutions(plugin: PluginInfo) {
  executionPanel.value = plugin
  executionLoading.value = true
  try {
    executions.value = (await pluginExecutionsApi(plugin.pluginId)).data
  } catch (error: any) {
    toast.showToast(error?.message || '执行记录加载失败', 'error')
  } finally {
    executionLoading.value = false
  }
}

function showExecutionDetail(item: PluginExecutionInfo) {
  selectedExecution.value = item
  executionDetailOpen.value = true
}

async function removePlugin(plugin: PluginInfo) {
  if (!confirm(`确定删除插件“${plugin.name}”吗？已发布版本将进入审计保留流程。`)) return
  try {
    await deletePluginApi(plugin.pluginId)
    plugins.value = plugins.value.filter((item) => item.pluginId !== plugin.pluginId)
    toast.showToast('插件已删除', 'success')
  } catch (error: any) {
    toast.showToast(error?.message || '删除失败', 'error')
  }
}

async function submitToMarketplace(plugin: PluginInfo) {
  if (!confirm(`提交“${plugin.name}”到插件市场审核？审核期间已发布版本保持不可变。`)) return
  try {
    await submitMarketplacePluginApi(plugin.pluginId)
    toast.showToast('已提交市场审核', 'success')
  } catch (error: any) {
    toast.showToast(error?.message || '提交审核失败', 'error')
  }
}

async function openWebRuntime() {
  runtimePanel.value = true
  await loadRuntimePlugins()
}

function closeWebRuntime() {
  localRuntime?.stop()
  localRuntime = null
  runtimePanel.value = false
  activeDistribution.value = null
  runtimeInputFile.value = null
  runtimeLogs.value = []
}

async function loadRuntimePlugins() {
  runtimeLoading.value = true
  runtimeError.value = ''
  localRuntime?.stop()
  localRuntime = null
  try {
    distributions.value = (await listWebLocalPluginDistributionsApi()).data
    if (distributions.value.length) await selectDistribution(distributions.value[0])
  } catch (error: any) {
    runtimeError.value = error?.message || '无法建立 Web 插件运行时'
  } finally {
    runtimeLoading.value = false
  }
}

async function selectDistribution(distribution: LocalPluginDistribution) {
  localRuntime?.stop()
  localRuntime = null
  activeDistribution.value = distribution
  runtimeLogs.value = []
  runtimeUiMessage.value = ''
  await nextTick()
}

const activePermissions = computed<string[]>(() => {
  if (!activeDistribution.value) return []
  try {
    const parsed = JSON.parse(activeDistribution.value.permissionConfig || '[]')
    return Array.isArray(parsed) ? parsed.filter((item) => typeof item === 'string') : []
  } catch {
    return []
  }
})

function selectRuntimeFile(event: Event) {
  runtimeInputFile.value = (event.target as HTMLInputElement).files?.[0] || null
}

async function runActivePlugin() {
  const distribution = activeDistribution.value
  const mount = runtimeMount.value
  if (!distribution || !mount || runtimeExecuting.value) return
  runtimeExecuting.value = true
  runtimeLogs.value = []
  runtimeUiMessage.value = ''
  const startedAt = new Date()
  const executionId = crypto.randomUUID()
  let status: 'SUCCESS' | 'FAILED' | 'TIMEOUT' = 'SUCCESS'
  let summary = ''
  let errorCode: string | undefined
  try {
    if (
      activePermissions.value.includes('client.system.notify')
      && 'Notification' in window
      && Notification.permission === 'default'
    ) {
      await Notification.requestPermission()
    }
    localRuntime?.stop()
    localRuntime = new WebLocalPluginRuntime(distribution, {
      mount,
      grantedPermissions: new Set(activePermissions.value),
      invoke: invokeWebCapability,
      onLog: (level, message) => {
        runtimeLogs.value = [...runtimeLogs.value.slice(-99), { level, message }]
      },
    })
    await localRuntime.start()
    try {
      await localRuntime.execute('activate', {
        pluginId: distribution.pluginId,
        spaceId: spaceStore.currentSpaceId || null,
      })
    } catch (error: any) {
      // activate 为可选入口；缺失时继续执行 run，其他初始化异常仍然失败。
      if (!String(error?.message || '').includes('未导出指定入口函数')) throw error
    }
    const result = await localRuntime.execute('run', {
      pluginId: distribution.pluginId,
      spaceId: spaceStore.currentSpaceId || null,
      file: runtimeInputFile.value
        ? { name: runtimeInputFile.value.name, size: runtimeInputFile.value.size, type: runtimeInputFile.value.type }
        : null,
    })
    summary = JSON.stringify(result ?? { completed: true }).slice(0, 3800)
    runtimeLogs.value.push({ level: 'success', message: `执行完成：${summary}` })
  } catch (error: any) {
    const message = error?.message || '本地插件执行失败'
    status = message.includes('超时') ? 'TIMEOUT' : 'FAILED'
    errorCode = status === 'TIMEOUT' ? 'LOCAL_PLUGIN_TIMEOUT' : 'LOCAL_PLUGIN_FAILED'
    summary = message.slice(0, 3800)
    runtimeLogs.value.push({ level: 'error', message })
  } finally {
    runtimeExecuting.value = false
    try {
      await recordWebLocalPluginExecutionApi({
        execution_id: executionId,
        plugin_id: distribution.pluginId,
        version_id: distribution.versionId,
        installation_id: distribution.installationId,
        trigger_event: 'web.manual',
        trigger_source: 'LOCAL',
        status,
        started_at: startedAt.toISOString(),
        ended_at: new Date().toISOString(),
        output_summary: summary || '无输出',
        error_code: errorCode,
        correlation_id: executionId,
      })
    } catch (error: any) {
      runtimeLogs.value.push({ level: 'warning', message: `执行摘要上报失败：${error?.message || '网络异常'}` })
    }
  }
}

async function invokeWebCapability(capability: string, payload: any): Promise<unknown> {
  switch (capability) {
    case 'client.file.read': {
      const file = runtimeInputFile.value
      if (!file) throw new Error('请先在宿主页面选择允许插件读取的文件')
      if (file.size > 32 * 1024 * 1024) throw new Error('Web 插件单次读取文件不能超过 32 MB')
      return {
        name: file.name,
        type: file.type,
        size: file.size,
        content: await file.arrayBuffer(),
      }
    }
    case 'client.file.upload': {
      const raw = payload?.content
      if (!(raw instanceof ArrayBuffer) && !ArrayBuffer.isView(raw)) {
        throw new Error('上传能力要求 ArrayBuffer 内容')
      }
      const bytes = raw instanceof ArrayBuffer
        ? new Uint8Array(raw)
        : new Uint8Array(raw.buffer, raw.byteOffset, raw.byteLength)
      if (bytes.byteLength > 32 * 1024 * 1024) throw new Error('Web 插件单次上传不能超过 32 MB')
      const content = bytes.buffer.slice(
        bytes.byteOffset,
        bytes.byteOffset + bytes.byteLength,
      ) as ArrayBuffer
      const file = new File([content], String(payload?.name || 'plugin-output.bin').slice(0, 255), {
        type: String(payload?.type || 'application/octet-stream'),
      })
      await uploaderStore.startUpload(file)
      return { accepted: true, name: file.name, size: file.size }
    }
    case 'client.clipboard.write':
      await navigator.clipboard.writeText(String(payload?.text ?? payload ?? '').slice(0, 100_000))
      return { written: true }
    case 'client.system.notify': {
      if (!('Notification' in window) || Notification.permission !== 'granted') {
        throw new Error('用户未授予浏览器通知权限')
      }
      new Notification(String(payload?.title || 'PrivateCloudDisk'), {
        body: String(payload?.body || '').slice(0, 500),
      })
      return { shown: true }
    }
    case 'client.ui.show':
      runtimeUiMessage.value = String(payload?.message ?? payload?.text ?? payload ?? '').slice(0, 2000)
      return { shown: true }
    default:
      throw new Error(`Web 宿主未实现能力：${capability}`)
  }
}

function iconTone(type: string) {
  return type === 'LOCAL_PLUGIN' ? 'bg-violet-100 text-violet-600' : 'bg-primary/10 text-primary'
}

function formatTime(value?: string) {
  if (!value) return '从未'
  return new Date(value).toLocaleDateString()
}

onMounted(loadPlugins)
onBeforeUnmount(() => localRuntime?.stop())

function formatBytes(value: number) {
  if (!Number.isFinite(value) || value <= 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
  return `${(value / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}
</script>

<style scoped>
.plugin-card { @apply rounded-2xl border border-neutral-200 bg-white p-5 shadow-sm transition duration-200 hover:-translate-y-0.5 hover:border-primary/20 hover:shadow-lg; }
.plugin-icon { @apply flex h-11 w-11 shrink-0 items-center justify-center rounded-xl text-lg; }
.plugin-card strong { @apply block text-sm font-bold text-neutral-700; }
.plugin-card strong em { @apply text-[10px] not-italic; }
.plugin-card small { @apply mt-1 block text-[10px] text-neutral-400; }
.filter-button { @apply whitespace-nowrap rounded-lg px-3 py-2 text-xs font-semibold text-neutral-500 transition; }
.filter-button.active { @apply bg-white text-primary shadow-sm; }
.search-input { @apply w-full rounded-xl border border-neutral-200 py-2.5 pl-9 pr-3 text-sm outline-none focus:border-primary; }
.primary-button,
.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold transition; }
.primary-button { @apply bg-primary text-white hover:bg-primary/90; }
.secondary-button { @apply border border-neutral-200 bg-white text-neutral-600 hover:border-primary/30 hover:text-primary; }
.card-action { @apply flex h-9 w-9 items-center justify-center rounded-lg text-neutral-400 transition hover:bg-primary/10 hover:text-primary; }
.row-button { @apply inline-flex min-h-8 items-center gap-1.5 rounded-lg border border-neutral-200 px-3 text-xs font-semibold text-neutral-600 transition hover:border-primary/30 hover:text-primary; }
.runtime-dialog { @apply flex max-h-[92vh] w-full max-w-6xl flex-col overflow-hidden rounded-3xl bg-white shadow-2xl; }
.runtime-plugin-item { @apply flex w-full items-center gap-3 rounded-xl border border-transparent p-3 transition hover:bg-neutral-50; }
.runtime-plugin-item.active { @apply border-primary/20 bg-primary/[0.04]; }
.runtime-plugin-item strong { @apply block truncate text-sm text-neutral-700; }
.runtime-plugin-item small { @apply mt-1 block truncate text-[11px] text-neutral-400; }
.runtime-sandbox-mount { @apply mt-4 flex min-h-44 items-center justify-center overflow-hidden rounded-xl border border-dashed border-neutral-200 bg-neutral-50; }
</style>
