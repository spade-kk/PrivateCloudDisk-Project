<template>
  <aside class="workflow-node-library" aria-label="CloudFlow 节点库">
    <header class="workflow-node-library__header">
      <div><p class="workflow-node-library__eyebrow">CloudFlow nodes</p><h2>节点库</h2></div>
      <button class="workflow-node-library__help" type="button" title="查看快捷键与节点说明" aria-label="查看快捷键与节点说明" @click="$emit('help')"><i class="fa fa-question-circle-o"></i></button>
    </header>
    <label class="workflow-node-library__search"><i class="fa fa-search" aria-hidden="true"></i><input v-model.trim="query" type="search" placeholder="搜索节点或能力" aria-label="搜索节点或能力" /></label>
    <section v-for="group in filteredGroups" :key="group.key" class="workflow-node-library__group">
      <h3>{{ group.label }}</h3>
      <button
        v-for="item in group.items"
        :key="item.key"
        class="workflow-node-library__item"
        type="button"
        draggable="true"
        :data-kind="item.nodeType"
        @dragstart="startDrag($event, item)"
        @click="$emit('create', item)"
      >
        <span class="workflow-node-library__icon" :style="{ '--node-color': item.color }"><i :class="`fa ${item.icon}`" aria-hidden="true"></i></span>
        <span class="workflow-node-library__copy"><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span>
        <i class="fa fa-plus workflow-node-library__add" aria-hidden="true"></i>
      </button>
    </section>
  </aside>
</template>

<script setup lang="ts">
// [AUDIT FIX 2.2] 节点库按 DSL 语义分类，不把无对应语法的视觉元素混进执行节点。
import { computed, ref } from 'vue'
import type { CapabilityInfo } from '@/api/modules/workflows'
import { nodeColor, nodeIcon } from '@/utils/cloudflowVisualDsl'
import type { WorkflowVisualNodeType } from '@/types/cloudflowVisual'

export interface WorkflowNodeLibraryItem {
  key: string
  label: string
  description: string
  icon: string
  color: string
  nodeType: WorkflowVisualNodeType
  capability?: CapabilityInfo
}

const props = withDefaults(defineProps<{ capabilities?: CapabilityInfo[] }>(), { capabilities: () => [] })
defineEmits<{ create: [item: WorkflowNodeLibraryItem]; help: [] }>()
const query = ref('')

function staticItem(nodeType: WorkflowVisualNodeType, label: string, description: string): WorkflowNodeLibraryItem {
  return { key: nodeType, nodeType, label, description, icon: nodeIcon(nodeType), color: nodeColor(nodeType) }
}
const groups = computed(() => {
  const capabilityItems = props.capabilities.map((capability) => {
    const nodeType: WorkflowVisualNodeType = capability.sourceType === 'PLUGIN' ? 'plugin' : capability.sourceType === 'API' || capability.sourceType === 'PLATFORM_API' ? 'api' : 'task'
    return { key: `capability:${capability.capabilityKey}`, nodeType, label: capability.displayName, description: capability.description || capability.capabilityKey, icon: nodeIcon(nodeType), color: nodeColor(nodeType), capability }
  })
  return [
    { key: 'entry', label: '触发与任务', items: [staticItem('trigger', '触发器', '手动、定时、事件或 HTTP'), staticItem('task', '通用任务', '内置能力或服务动作'), staticItem('plugin', '插件函数', '调用已授权的云插件能力'), staticItem('api', '平台 API', '通过平台能力中心调用')] },
    { key: 'control', label: '控制流', items: [staticItem('condition', '条件判断', 'if / else 分支'), staticItem('foreach', '遍历集合', 'foreach item in collection'), staticItem('while', '条件循环', 'while { expression }'), staticItem('parallel', '并行分支', 'parallel { ... }'), staticItem('try', '异常处理', 'try / catch / finally'), staticItem('wait', '等待', 'wait approval'), staticItem('assert', '断言', 'assert { expression }')] },
    { key: 'capability', label: '能力中心', items: capabilityItems },
    { key: 'annotation', label: '画布注释（不参与执行）', items: [staticItem('group', '分组', '整理大型流程'), staticItem('note', '便签', '记录设计说明')] },
  ]
})
const filteredGroups = computed(() => {
  const keyword = query.value.toLocaleLowerCase()
  return groups.value.map((group) => ({ ...group, items: group.items.filter((item) => !keyword || `${item.label} ${item.description} ${item.key}`.toLocaleLowerCase().includes(keyword)) })).filter((group) => group.items.length)
})
function startDrag(event: DragEvent, item: WorkflowNodeLibraryItem) {
  event.dataTransfer?.setData('application/pcd-workflow-node', JSON.stringify({ key: item.key, nodeType: item.nodeType, capabilityKey: item.capability?.capabilityKey }))
  if (event.dataTransfer) event.dataTransfer.effectAllowed = 'copy'
}
</script>

<style scoped>
.workflow-node-library { display:flex;min-height:0;flex-direction:column;overflow:auto;background:var(--workflow-panel,#fff);color:var(--workflow-text,#334155); }
.workflow-node-library__header { display:flex;align-items:center;justify-content:space-between;gap:10px;padding:16px 16px 10px; }
.workflow-node-library__eyebrow { margin:0 0 3px;color:var(--workflow-muted,#94a3b8);font-size:9px;font-weight:800;letter-spacing:.15em;text-transform:uppercase; }
.workflow-node-library h2 { margin:0;font-size:15px; }
.workflow-node-library__help { display:inline-grid;width:34px;height:34px;place-items:center;border-radius:9px;color:var(--workflow-muted,#64748b); }.workflow-node-library__help:hover,.workflow-node-library__help:focus-visible { background:var(--workflow-hover,#f1f5f9);color:var(--workflow-primary,#2563eb);outline:none; }
.workflow-node-library__search { display:flex;min-height:40px;align-items:center;gap:8px;margin:0 14px 10px;padding:0 11px;border:1px solid var(--workflow-border,#e2e8f0);border-radius:10px;color:var(--workflow-muted,#94a3b8); }.workflow-node-library__search:focus-within { border-color:var(--workflow-primary,#2563eb);box-shadow:0 0 0 3px rgb(37 99 235 / .1); }.workflow-node-library__search input { width:100%;border:0;outline:0;background:transparent;color:inherit;font-size:12px; }
.workflow-node-library__group { padding:8px 10px 6px; }.workflow-node-library__group h3 { margin:0 6px 7px;color:var(--workflow-muted,#64748b);font-size:10px;font-weight:800;letter-spacing:.1em;text-transform:uppercase; }
.workflow-node-library__item { display:flex;width:100%;min-height:48px;align-items:center;gap:9px;padding:7px;border-radius:10px;text-align:left;transition:background-color .16s ease,transform .16s ease; }.workflow-node-library__item:hover,.workflow-node-library__item:focus-visible { background:var(--workflow-hover,#f1f5f9);outline:none;transform:translateX(2px); }.workflow-node-library__icon { display:grid;width:30px;height:30px;flex:0 0 30px;place-items:center;border-radius:9px;background:color-mix(in srgb,var(--node-color) 14%,transparent);color:var(--node-color);font-size:13px; }.workflow-node-library__copy { min-width:0; }.workflow-node-library__copy strong,.workflow-node-library__copy small { display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.workflow-node-library__copy strong { font-size:12px; }.workflow-node-library__copy small { margin-top:2px;color:var(--workflow-muted,#94a3b8);font-size:10px; }.workflow-node-library__add { margin-left:auto;color:var(--workflow-muted,#94a3b8);font-size:11px; }
@media (prefers-reduced-motion: reduce) { .workflow-node-library__item { transition:none; } }
</style>

