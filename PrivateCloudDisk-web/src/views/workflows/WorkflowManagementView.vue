<template>
  <div class="space-y-5">
    <PageHeader title="自动化工作流" description="通过事件、条件与能力节点编排可审计的业务流程">
      <template #actions>
        <router-link class="secondary-button" to="/app/workflow-market"><i class="fa fa-compass"></i> 模板市场</router-link>
        <router-link class="primary-button" to="/app/workflows/new"><i class="fa fa-plus"></i> 创建工作流</router-link>
      </template>
    </PageHeader>
    <PageState
      v-if="loading || error || !workflows.length"
      :type="loading ? 'loading' : error ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : error ? 'fa fa-exclamation-triangle' : 'fa fa-random'"
      :title="loading ? '正在加载工作流' : error ? '工作流加载失败' : '还没有工作流'"
      :description="error || '从空白画布或模板开始创建第一个自动化。'"
      :action-text="error ? '重新加载' : ''"
      action-icon="fa fa-refresh"
      @action="load"
    />
    <div v-else class="overflow-hidden rounded-2xl border border-neutral-200 bg-white shadow-sm">
      <div v-for="workflow in workflows" :key="workflow.workflowId" class="workflow-row">
        <div class="flex min-w-0 items-center gap-3">
          <span class="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600"><i class="fa fa-random"></i></span>
          <div class="min-w-0"><h2 class="truncate font-bold text-neutral-800">{{ workflow.name }}</h2><p class="truncate text-xs text-neutral-400">{{ workflow.description || workflow.slug }}</p></div>
        </div>
        <div class="hidden text-center md:block"><StatusBadge :status="workflow.status" /><p class="mt-1 text-[10px] text-neutral-400">{{ workflow.ownerScopeType === 'SPACE' ? '空间工作流' : '个人工作流' }}</p></div>
        <time class="hidden text-xs text-neutral-400 lg:block">{{ new Date(workflow.updatedAt).toLocaleString() }}</time>
        <div class="flex justify-end gap-2">
          <button class="row-button" @click="run(workflow)"><i class="fa fa-play"></i><span class="hidden sm:inline">运行</span></button>
          <router-link class="row-button" :to="`/app/workflows/${workflow.workflowId}/edit`"><i class="fa fa-pencil"></i><span class="hidden sm:inline">编辑</span></router-link>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { listWorkflowsApi, runWorkflowApi, type WorkflowInfo } from '@/api/modules/workflows'
import { useToastStore } from '@/stores/toastStore'

const toast = useToastStore()
const workflows = ref<WorkflowInfo[]>([])
const loading = ref(false)
const error = ref('')
async function load() {
  loading.value = true
  error.value = ''
  try { workflows.value = (await listWorkflowsApi()).data }
  catch (err: any) { error.value = err?.message || '工作流加载失败' }
  finally { loading.value = false }
}
async function run(workflow: WorkflowInfo) {
  try {
    await runWorkflowApi(workflow.workflowId)
    toast.showToast('工作流已进入异步执行队列', 'success')
  } catch (err: any) { toast.showToast(err?.message || '运行失败', 'error') }
}
onMounted(load)
</script>

<style scoped>
.workflow-row { @apply grid grid-cols-[minmax(0,1fr)_auto] items-center gap-4 border-b border-neutral-100 p-4 last:border-0 md:grid-cols-[minmax(0,1fr)_130px_auto] lg:grid-cols-[minmax(0,1fr)_130px_190px_auto]; }
.row-button { @apply inline-flex min-h-9 items-center gap-1.5 rounded-lg border border-neutral-200 px-3 text-xs font-semibold text-neutral-600 transition hover:border-primary/30 hover:text-primary; }
.primary-button,.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl px-4 text-sm font-semibold; }
.primary-button { @apply bg-primary text-white; }
.secondary-button { @apply border border-neutral-200 bg-white text-neutral-600; }
</style>
