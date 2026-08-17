<template>
  <section class="template-library" aria-label="插件模板库">
    <header class="template-library__header"><div><h2>模板库</h2><p>从经过审核的示例开始</p></div><span class="template-library__count">{{ templates.length }}</span></header>
    <label class="template-library__search"><i class="fa fa-search" aria-hidden="true"></i><span class="sr-only">搜索插件模板</span><input v-model.trim="query" type="search" placeholder="搜索模板" /></label>
    <div class="template-library__list">
      <article v-for="template in visibleTemplates" :key="template.id" class="template-library__item">
        <div class="template-library__item-heading"><span class="template-library__item-icon"><i :class="template.icon || 'fa fa-puzzle-piece'" aria-hidden="true"></i></span><div class="min-w-0"><h3>{{ template.name }}</h3><p>{{ template.category }}</p></div></div>
        <p class="template-library__description">{{ template.description }}</p>
        <div class="template-library__actions">
          <button type="button" @click="$emit('preview', template)"><i class="fa fa-eye" aria-hidden="true"></i> 预览</button>
          <button type="button" class="primary" @click="$emit('apply', template.content, 'replace')"><i class="fa fa-file-code-o" aria-hidden="true"></i> 替换当前文件</button>
          <button type="button" @click="$emit('apply', template.content, 'append')"><i class="fa fa-plus" aria-hidden="true"></i> 追加</button>
        </div>
      </article>
      <p v-if="!visibleTemplates.length" class="template-library__empty">没有匹配的模板</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

export interface IdeTemplate {
  id: string
  name: string
  category: string
  description: string
  content: string
  icon?: string
  source?: 'platform' | 'marketplace' | 'user'
}

const props = withDefaults(defineProps<{ templates?: IdeTemplate[] }>(), { templates: () => [] })
defineEmits<{
  preview: [template: IdeTemplate]
  /** 传递模板源码以兼容编辑器的字符串模型；预览仍通过完整对象事件提供。 */
  apply: [content: string, mode: 'replace' | 'append']
}>()

const query = ref('')
const visibleTemplates = computed(() => {
  const search = query.value.toLowerCase()
  return props.templates.filter((item) => !search || `${item.name} ${item.category} ${item.description}`.toLowerCase().includes(search))
})
</script>

<style scoped>
.template-library { min-height: 100%; padding: 14px; background: #f8fafc; color: #334155; }
.template-library__header { display: flex; align-items: flex-start; justify-content: space-between; }
.template-library__header h2 { font-size: 13px; font-weight: 700; }
.template-library__header p { margin-top: 2px; color: #94a3b8; font-size: 10px; }
.template-library__count { display: inline-flex; min-width: 22px; height: 22px; align-items: center; justify-content: center; border-radius: 7px; background: #e0ebff; color: #2563eb; font-size: 10px; font-weight: 700; }
.template-library__search { display: flex; align-items: center; gap: 7px; margin-top: 12px; padding: 0 9px; border: 1px solid #dbe4f0; border-radius: 8px; background: #fff; color: #94a3b8; }
.template-library__search input { width: 100%; min-height: 31px; border: 0; color: #334155; font-size: 11px; outline: none; }
.template-library__list { display: grid; gap: 8px; margin-top: 12px; }
.template-library__item { padding: 10px; border: 1px solid #e2e8f0; border-radius: 10px; background: #fff; }
.template-library__item-heading { display: flex; align-items: center; gap: 8px; }
.template-library__item-icon { display: inline-flex; width: 29px; height: 29px; flex: 0 0 29px; align-items: center; justify-content: center; border-radius: 8px; background: #ecfdf5; color: #059669; font-size: 12px; }
.template-library__item-heading h3 { overflow: hidden; font-size: 11px; font-weight: 700; text-overflow: ellipsis; white-space: nowrap; }
.template-library__item-heading p { margin-top: 2px; color: #94a3b8; font-size: 9px; }
.template-library__description { display: -webkit-box; min-height: 31px; margin-top: 8px; overflow: hidden; color: #64748b; font-size: 10px; line-height: 1.55; -webkit-box-orient: vertical; -webkit-line-clamp: 2; }
.template-library__actions { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 9px; }
.template-library__actions button { display: inline-flex; min-height: 28px; align-items: center; gap: 4px; padding: 0 7px; border-radius: 6px; color: #64748b; font-size: 10px; }
.template-library__actions button:hover { background: #f1f5f9; color: #2563eb; }
.template-library__actions button.primary { background: #165dff; color: #fff; }
.template-library__actions button.primary:hover { background: #124fda; }
.template-library__empty { padding: 24px 4px; color: #94a3b8; font-size: 11px; text-align: center; }
</style>
