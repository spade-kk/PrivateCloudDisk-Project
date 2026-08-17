<template>
  <div class="space-y-5">
    <PageHeader title="工作流模板市场" description="把成熟自动化作为副本导入个人或当前空间">
      <template #actions><router-link class="secondary-button" to="/app/workflows"><i class="fa fa-arrow-left"></i> 我的工作流</router-link></template>
    </PageHeader>
    <section class="flex flex-col gap-4 rounded-3xl bg-gradient-to-r from-emerald-600 to-teal-500 p-6 text-white shadow-xl shadow-emerald-600/10 sm:flex-row sm:items-center sm:justify-between lg:p-8"><div><p class="text-xs font-bold uppercase tracking-[.2em] text-emerald-100">Workflow Gallery</p><h2 class="mt-2 text-2xl font-bold">从验证过的流程开始</h2><p class="mt-2 text-sm text-emerald-50">导入后生成独立副本，可安全修改，不影响模板原件。</p></div><input v-model.trim="query" class="w-full max-w-sm rounded-2xl border border-white/20 bg-white/15 px-4 py-3 text-sm text-white outline-none placeholder:text-emerald-100 focus:bg-white/20" placeholder="搜索工作流模板" @keyup.enter="load" /></section>
    <PageState
      v-if="loading || error || !items.length"
      :type="loading ? 'loading' : error ? 'error' : 'empty'"
      :icon="loading ? 'fa fa-spinner fa-spin' : error ? 'fa fa-exclamation-triangle' : 'fa fa-sitemap'"
      :title="loading ? '正在加载模板市场' : error ? '模板市场加载失败' : '暂无已审核模板'"
      :description="error || '模板通过审核后将在这里展示。'"
      :action-text="error ? '重新加载' : ''"
      action-icon="fa fa-refresh"
      @action="load"
    />
    <div v-else class="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
      <article v-for="item in items" :key="item.workflowId" class="rounded-2xl border border-neutral-200 bg-white p-5 shadow-sm">
        <div class="flex items-center gap-3"><span class="flex h-11 w-11 items-center justify-center rounded-xl bg-emerald-50 text-emerald-600"><i class="fa fa-random"></i></span><div><h2 class="font-bold text-neutral-800">{{ item.name }}</h2><p class="font-mono text-xs text-neutral-400">{{ item.slug }}</p></div></div>
        <router-link class="mt-4 block line-clamp-3 min-h-[60px] text-sm leading-5 text-neutral-500 hover:text-primary" :to="`/app/workflow-market/${item.workflowId}`">{{ item.description || '暂无描述' }}</router-link>
        <div class="mt-4 flex gap-4 text-xs text-neutral-400"><span class="text-amber-500"><i class="fa fa-star"></i> {{ Number(item.ratingAverage).toFixed(1) }}</span><span><i class="fa fa-copy"></i> {{ item.installCount }} 次导入</span></div>
        <div class="mt-4 flex gap-2"><router-link class="detail-button" :to="`/app/workflow-market/${item.workflowId}`">查看详情</router-link><button class="import-button" @click="importTemplate(item)"><i class="fa fa-download"></i> 导入模板</button></div>
      </article>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import { importWorkflowTemplateApi, listWorkflowMarketplaceApi, type WorkflowMarketplaceItem } from '@/api/modules/workflows'
import { useToastStore } from '@/stores/toastStore'
const router = useRouter(); const toast = useToastStore()
const items = ref<WorkflowMarketplaceItem[]>([]); const loading = ref(false); const error = ref(''); const query = ref('')
async function load() { loading.value = true; error.value = ''; try { items.value = (await listWorkflowMarketplaceApi(query.value)).data } catch (err:any) { error.value = err?.message || '模板市场加载失败' } finally { loading.value = false } }
async function importTemplate(item: WorkflowMarketplaceItem) {
  const name = prompt('请输入导入后的工作流名称', `${item.name} 副本`); if (!name) return
  const slug = `imported-${item.slug}-${Date.now().toString(36)}`
  try { const result = await importWorkflowTemplateApi(item.workflowId, name, slug); toast.showToast('模板已导入当前空间', 'success'); await router.push(`/app/workflows/${result.data.workflowId}/edit`) } catch (err:any) { toast.showToast(err?.message || '导入失败', 'error') }
}
onMounted(load)
</script>
<style scoped>.secondary-button { @apply inline-flex min-h-10 items-center gap-2 rounded-xl border border-neutral-200 bg-white px-4 text-sm font-semibold text-neutral-600; }.detail-button{ @apply inline-flex min-h-10 items-center rounded-xl border border-neutral-200 px-3 text-xs font-semibold text-neutral-600 hover:border-primary hover:text-primary; }.import-button{ @apply inline-flex min-h-10 flex-1 items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-semibold text-white hover:bg-emerald-700; }</style>
