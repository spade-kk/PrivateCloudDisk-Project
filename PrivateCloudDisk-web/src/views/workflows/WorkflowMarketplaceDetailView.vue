<template>
  <div class="space-y-5">
    <PageHeader title="工作流模板详情" description="先预览流程和 DSL，再导入为独立副本" :breadcrumbs="[{ label: '工作流市场', to: '/app/workflow-market' }, { label: detail?.name || '模板详情' }]" />
    <PageState v-if="loading || error || !detail" :type="loading ? 'loading' : error ? 'error' : 'empty'" :icon="loading ? 'fa fa-spinner fa-spin' : 'fa fa-random'" :title="loading ? '正在加载模板' : error || '模板不存在'" :description="error || '模板可能已经下架'" :action-text="error ? '重试' : ''" @action="load" />
    <template v-else>
      <section class="workflow-detail-hero"><div><h2 class="text-2xl font-bold text-neutral-900">{{ detail.name }}</h2><p class="mt-1 font-mono text-xs text-neutral-400">{{ detail.slug }}</p><p class="mt-4 max-w-2xl text-sm leading-6 text-neutral-600">{{ detail.description || '暂无描述' }}</p></div><button class="import-button" type="button" @click="importTemplate"><i class="fa fa-download"></i> 导入到我的工作流</button></section>
      <section class="workflow-detail-card"><header><h3>DSL 预览</h3><span>只读</span></header><pre><code>{{ dsl }}</code></pre></section>
      <section class="workflow-detail-card"><h3>模板信息</h3><div class="info-grid"><div><small>评分</small><strong>★ {{ Number(detail.ratingAverage).toFixed(1) }}</strong></div><div><small>使用次数</small><strong>{{ detail.installCount }}</strong></div><div><small>分类</small><strong>{{ detail.categoryCode || '通用自动化' }}</strong></div></div></section>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import PageState from '@/components/common/PageState.vue'
import { getLatestWorkflowVersionApi, importWorkflowTemplateApi, listWorkflowMarketplaceApi, type WorkflowMarketplaceItem } from '@/api/modules/workflows'
import { useToastStore } from '@/stores/toastStore'
const route = useRoute(); const router = useRouter(); const toast = useToastStore(); const detail = ref<WorkflowMarketplaceItem | null>(null); const dsl = ref('# 模板 DSL 由受保护的公开版本提供\n'); const loading = ref(false); const error = ref('')
async function load() { loading.value = true; error.value = ''; try { const result = await listWorkflowMarketplaceApi(''); detail.value = result.data.find((item) => item.workflowId === String(route.params.workflowId)) || null; if (!detail.value) { error.value = '模板不存在或已下架'; return }; try { dsl.value = (await getLatestWorkflowVersionApi(detail.value.workflowId)).data.dslText } catch { dsl.value = '# DSL 预览暂未开放\n' } } catch (err: any) { error.value = err?.message || '模板详情加载失败' } finally { loading.value = false } }
async function importTemplate() { if (!detail.value) return; const name = prompt('请输入导入后的工作流名称', `${detail.value.name} 副本`); if (!name) return; try { const result = await importWorkflowTemplateApi(detail.value.workflowId, name, `imported-${detail.value.slug}-${Date.now().toString(36)}`); toast.showToast('模板已导入', 'success'); router.push(`/app/workflows/${result.data.workflowId}/edit`) } catch (err: any) { toast.showToast(err?.message || '导入失败', 'error') } }
onMounted(load)
</script>

<style scoped>.workflow-detail-hero{display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:18px;border-radius:22px;background:linear-gradient(135deg,#ecfdf5,#fff);padding:24px;}.import-button{display:inline-flex;min-height:42px;align-items:center;gap:8px;border-radius:10px;background:#059669;padding:0 16px;color:#fff;font-size:12px;font-weight:700;}.workflow-detail-card{overflow:hidden;border:1px solid #e2e8f0;border-radius:18px;background:#fff;}.workflow-detail-card header{display:flex;align-items:center;justify-content:space-between;border-bottom:1px solid #e2e8f0;padding:14px 16px;}.workflow-detail-card h3{color:#334155;font-size:14px;font-weight:700;}.workflow-detail-card header span{color:#94a3b8;font-size:10px;}.workflow-detail-card pre{max-height:520px;overflow:auto;background:#0f172a;padding:16px;color:#cbd5e1;font:12px/1.6 ui-monospace,SFMono-Regular,Menlo,monospace;}.info-grid{display:grid;grid-template-columns:repeat(3,minmax(0,1fr));gap:12px;padding:16px;}.info-grid small{display:block;color:#94a3b8;font-size:10px;}.info-grid strong{display:block;margin-top:4px;color:#334155;font-size:14px;}@media(max-width:600px){.info-grid{grid-template-columns:1fr;}}
</style>
