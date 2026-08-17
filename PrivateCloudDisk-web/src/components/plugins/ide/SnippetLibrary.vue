<template>
  <section class="snippet-library" aria-label="代码片段库">
    <header class="snippet-library__header">
      <div>
        <h2>代码片段</h2>
        <p>点击插入到当前光标位置</p>
      </div>
      <button class="snippet-library__icon" type="button" title="新增自定义片段" aria-label="新增自定义片段" @click="$emit('create')"><i class="fa fa-plus" aria-hidden="true"></i></button>
    </header>
    <label class="snippet-library__search"><i class="fa fa-search" aria-hidden="true"></i><span class="sr-only">搜索代码片段</span><input v-model.trim="query" type="search" placeholder="搜索片段" /></label>
    <nav class="snippet-library__categories" aria-label="片段分类">
      <button v-for="category in categories" :key="category" type="button" :class="{ active: selectedCategory === category }" @click="selectedCategory = category">{{ category }}</button>
    </nav>
    <div class="snippet-library__list">
      <button v-for="snippet in visibleSnippets" :key="snippet.id" class="snippet-library__item" type="button" @click="$emit('insert', snippet.content)">
        <span class="snippet-library__item-icon"><i class="fa fa-code" aria-hidden="true"></i></span>
        <span class="snippet-library__item-content"><strong>{{ snippet.name }}</strong><small>{{ snippet.description }}</small><em>{{ snippet.category }}</em></span>
        <i class="fa fa-arrow-right snippet-library__item-arrow" aria-hidden="true"></i>
      </button>
      <p v-if="!visibleSnippets.length" class="snippet-library__empty">没有匹配的代码片段</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

export interface IdeSnippet {
  id: string
  name: string
  description: string
  category: string
  language?: 'python' | 'javascript' | 'yaml' | string
  content: string
  builtIn?: boolean
}

const props = withDefaults(defineProps<{
  snippets?: IdeSnippet[]
}>(), { snippets: () => [] })

defineEmits<{
  /** 传递纯文本，兼容旧页面的插入回调；完整片段对象可从 snippets 里按 id 获取。 */
  insert: [content: string]
  create: []
}>()

const query = ref('')
const selectedCategory = ref('全部')
const categories = computed(() => ['全部', ...new Set(props.snippets.map((item) => item.category).filter(Boolean))])
const visibleSnippets = computed(() => props.snippets.filter((item) => {
  const categoryMatch = selectedCategory.value === '全部' || item.category === selectedCategory.value
  const search = query.value.toLowerCase()
  return categoryMatch && (!search || `${item.name} ${item.description} ${item.content}`.toLowerCase().includes(search))
}))
</script>

<style scoped>
.snippet-library { min-height: 100%; padding: 14px; background: #f8fafc; color: #334155; }
.snippet-library__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 8px; }
.snippet-library__header h2 { font-size: 13px; font-weight: 700; }
.snippet-library__header p { margin-top: 2px; color: #94a3b8; font-size: 10px; }
.snippet-library__icon { display: inline-flex; width: 30px; height: 30px; align-items: center; justify-content: center; border-radius: 7px; color: #2563eb; }
.snippet-library__icon:hover { background: #e0ebff; }
.snippet-library__search { display: flex; align-items: center; gap: 7px; margin-top: 12px; padding: 0 9px; border: 1px solid #dbe4f0; border-radius: 8px; background: #fff; color: #94a3b8; }
.snippet-library__search input { width: 100%; min-height: 31px; border: 0; color: #334155; font-size: 11px; outline: none; }
.snippet-library__categories { display: flex; gap: 4px; margin: 10px 0; overflow-x: auto; }
.snippet-library__categories button { flex: 0 0 auto; padding: 5px 8px; border-radius: 6px; color: #64748b; font-size: 10px; white-space: nowrap; }
.snippet-library__categories button.active { background: #e0ebff; color: #2563eb; font-weight: 700; }
.snippet-library__list { display: grid; gap: 5px; }
.snippet-library__item { display: flex; min-width: 0; align-items: center; gap: 8px; padding: 8px; border: 1px solid transparent; border-radius: 9px; color: #475569; text-align: left; transition: background-color .15s ease, border-color .15s ease; }
.snippet-library__item:hover,
.snippet-library__item:focus-visible { border-color: #bfdbfe; background: #eff6ff; outline: none; }
.snippet-library__item-icon { display: inline-flex; width: 26px; height: 26px; flex: 0 0 26px; align-items: center; justify-content: center; border-radius: 7px; background: #e0ebff; color: #2563eb; font-size: 11px; }
.snippet-library__item-content { display: block; min-width: 0; flex: 1; }
.snippet-library__item-content strong { display: block; overflow: hidden; color: #334155; font-size: 11px; text-overflow: ellipsis; white-space: nowrap; }
.snippet-library__item-content small { display: block; margin-top: 2px; overflow: hidden; color: #94a3b8; font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.snippet-library__item-content em { display: inline-block; margin-top: 3px; color: #64748b; font-size: 9px; font-style: normal; }
.snippet-library__item-arrow { color: #94a3b8; font-size: 10px; }
.snippet-library__empty { padding: 24px 4px; color: #94a3b8; font-size: 11px; text-align: center; }
@media (prefers-reduced-motion: reduce) { .snippet-library__item { transition: none; } }
</style>
