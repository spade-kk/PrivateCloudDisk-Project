<template>
  <div ref="rootRef" class="smart-search" :class="[`smart-search--${mode}`, { 'is-open': panelOpen }]">
    <form class="search-shell" @submit.prevent="submitSearch">
      <button type="submit" class="search-icon" title="搜索">
        <i class="fa fa-search"></i>
      </button>

      <input
        v-model="localKeyword"
        class="search-input"
        type="search"
        autocomplete="off"
        placeholder="搜索文件名、正文、OCR、标签"
        @focus="panelOpen = true"
        @input="panelOpen = true"
        @keydown.down.prevent="moveSuggestion(1)"
        @keydown.up.prevent="moveSuggestion(-1)"
        @keydown.enter.prevent="submitHighlighted"
      />

      <select v-model="localCategory" class="search-select" title="文件类型">
        <option value="">全部</option>
        <option value="document">文档</option>
        <option value="image">图片</option>
        <option value="video">视频</option>
        <option value="audio">音频</option>
        <option value="archive">压缩包</option>
      </select>

      <button v-if="localKeyword" type="button" class="search-clear" title="清空" @click="clearKeyword">
        <i class="fa fa-times"></i>
      </button>
    </form>

    <transition name="search-panel">
      <div v-if="panelOpen" class="search-panel" @mousedown.stop>
        <div class="panel-row">
          <button
            v-for="(suggestion, index) in suggestions"
            :key="suggestion"
            type="button"
            class="suggestion-item"
            :class="{ active: index === activeSuggestion }"
            @click="chooseSuggestion(suggestion)"
          >
            <i :class="index < recentCount ? 'fa fa-history' : 'fa fa-lightbulb-o'"></i>
            <span>{{ suggestion }}</span>
          </button>
        </div>

        <div class="quick-filters">
          <button
            v-for="item in categoryOptions"
            :key="item.value || 'all'"
            type="button"
            :class="{ active: localCategory === item.value }"
            @click="localCategory = item.value"
          >
            <i :class="item.icon"></i>
            <span>{{ item.label }}</span>
          </button>
        </div>

        <div class="panel-footer">
          <div class="sort-control">
            <span>排序</span>
            <select v-model="localSort">
              <option value="_score">相关度</option>
              <option value="updated_at">更新时间</option>
              <option value="size_bytes">文件大小</option>
              <option value="filename.keyword">文件名</option>
            </select>
            <button type="button" class="order-button" title="切换排序方向" @click="localAsc = !localAsc">
              <i :class="localAsc ? 'fa fa-sort-amount-asc' : 'fa fa-sort-amount-desc'"></i>
            </button>
          </div>
          <button type="button" class="submit-button" @click="submitSearch">
            <i class="fa fa-arrow-right"></i>
            <span>{{ mode === 'compact' ? '高级搜索' : '搜索' }}</span>
          </button>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useSearchStore } from '@/stores/searchStore'

const props = defineProps({
  mode: { type: String, default: 'compact' },
})

const emit = defineEmits(['search'])
const router = useRouter()
const searchStore = useSearchStore()
const rootRef = ref(null)
const panelOpen = ref(false)
const activeSuggestion = ref(-1)
const localKeyword = ref(searchStore.keyword)
const localCategory = ref(searchStore.filters.file_category || '')
const localSort = ref(searchStore.sortField)
const localAsc = ref(searchStore.asc)

const categoryOptions = [
  { value: '', label: '全部', icon: 'fa fa-th-large' },
  { value: 'document', label: '文档', icon: 'fa fa-file-text-o' },
  { value: 'image', label: '图片', icon: 'fa fa-file-image-o' },
  { value: 'video', label: '视频', icon: 'fa fa-file-video-o' },
  { value: 'audio', label: '音频', icon: 'fa fa-file-audio-o' },
  { value: 'archive', label: '压缩包', icon: 'fa fa-file-archive-o' },
]

const recentCount = computed(() => Math.min(searchStore.recentKeywords.length, 4))
const suggestions = computed(() => {
  const base = localKeyword.value.trim()
  const recent = searchStore.recentKeywords.filter(item => item.includes(base)).slice(0, 4)
  const generated = base
    ? [
        `${base} 文档`,
        `${base} 图片`,
        `${base} 最新`,
        `${base} 标签`,
      ]
    : ['合同', '发票', '会议纪要', '项目文档']

  return [...new Set([...recent, ...generated])].slice(0, 7)
})

watch(() => searchStore.keyword, value => {
  localKeyword.value = value
})

watch(() => searchStore.filters.file_category, value => {
  localCategory.value = value || ''
})

watch(() => searchStore.sortField, value => {
  localSort.value = value
})

watch(() => searchStore.asc, value => {
  localAsc.value = value
})

onMounted(() => {
  document.addEventListener('mousedown', closeOnOutsideClick)
})

onBeforeUnmount(() => {
  document.removeEventListener('mousedown', closeOnOutsideClick)
})

function closeOnOutsideClick(event) {
  if (rootRef.value && !rootRef.value.contains(event.target)) {
    panelOpen.value = false
    activeSuggestion.value = -1
  }
}

function buildPayload(keyword = localKeyword.value) {
  return {
    keyword: keyword.trim(),
    filters: {
      file_category: localCategory.value,
      tags: searchStore.filters.tags || '',
    },
    sortField: localSort.value,
    asc: localAsc.value,
    page: 1,
  }
}

function submitSearch() {
  const payload = buildPayload()
  panelOpen.value = false
  activeSuggestion.value = -1

  if (props.mode === 'compact') {
    router.push({
      path: '/search',
      query: {
        q: payload.keyword || undefined,
        category: payload.filters.file_category || undefined,
        sort: payload.sortField,
        asc: String(payload.asc),
      },
    })
    return
  }

  emit('search', payload)
}

function chooseSuggestion(suggestion) {
  localKeyword.value = suggestion
  submitSearch()
}

function submitHighlighted() {
  if (activeSuggestion.value >= 0 && suggestions.value[activeSuggestion.value]) {
    chooseSuggestion(suggestions.value[activeSuggestion.value])
    return
  }
  submitSearch()
}

function moveSuggestion(direction) {
  if (!panelOpen.value) panelOpen.value = true
  const count = suggestions.value.length
  if (!count) return
  activeSuggestion.value = (activeSuggestion.value + direction + count) % count
}

function clearKeyword() {
  localKeyword.value = ''
  activeSuggestion.value = -1
}
</script>

<style scoped>
.smart-search {
  position: relative;
  width: 100%;
}

.search-shell {
  display: grid;
  grid-template-columns: 42px minmax(0, 1fr) auto 40px;
  align-items: center;
  min-height: 46px;
  border: 1px solid rgba(22, 93, 255, 0.16);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 10px 30px rgba(22, 93, 255, 0.08);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.smart-search.is-open .search-shell {
  border-color: rgba(22, 93, 255, 0.45);
  box-shadow: 0 18px 46px rgba(22, 93, 255, 0.18);
  transform: translateY(-1px);
}

.smart-search--hero .search-shell {
  min-height: 58px;
}

.search-icon,
.search-clear,
.order-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 40px;
  width: 40px;
  color: #606266;
  transition: color 0.18s ease, background 0.18s ease;
}

.search-icon:hover,
.search-clear:hover,
.order-button:hover {
  color: #165dff;
}

.search-input {
  min-width: 0;
  border: 0;
  outline: 0;
  background: transparent;
  color: #1e1e1e;
  font-size: 14px;
}

.smart-search--hero .search-input {
  font-size: 16px;
}

.search-select,
.sort-control select {
  height: 34px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #f8fafc;
  color: #303133;
  font-size: 13px;
  outline: none;
}

.search-select {
  max-width: 92px;
  padding: 0 8px;
}

.search-panel {
  position: absolute;
  inset-inline: 0;
  top: calc(100% + 10px);
  z-index: 30;
  border: 1px solid rgba(228, 231, 237, 0.9);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 20px 60px rgba(48, 49, 51, 0.16);
  backdrop-filter: blur(14px);
  padding: 10px;
}

.panel-row {
  display: grid;
  gap: 4px;
}

.suggestion-item,
.quick-filters button,
.submit-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-height: 36px;
  border-radius: 8px;
  padding: 0 10px;
  color: #303133;
  transition: background 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.suggestion-item:hover,
.suggestion-item.active {
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
}

.quick-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 10px;
}

.quick-filters button {
  border: 1px solid #e4e7ed;
  background: #fff;
  font-size: 12px;
}

.quick-filters button.active {
  border-color: rgba(22, 93, 255, 0.4);
  background: rgba(22, 93, 255, 0.08);
  color: #165dff;
}

.panel-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
  border-top: 1px solid #f0f2f5;
  padding-top: 10px;
}

.sort-control {
  display: flex;
  min-width: 0;
  align-items: center;
  gap: 8px;
  color: #606266;
  font-size: 12px;
}

.sort-control select {
  width: 108px;
  padding: 0 8px;
}

.submit-button {
  background: #165dff;
  color: #fff;
  padding-inline: 14px;
}

.submit-button:hover {
  transform: translateX(1px);
}

.search-panel-enter-active,
.search-panel-leave-active {
  transform-origin: top;
  transition: opacity 0.2s ease, transform 0.2s ease, filter 0.2s ease;
}

.search-panel-enter-from,
.search-panel-leave-to {
  opacity: 0;
  filter: blur(3px);
  transform: translateY(-8px) scale(0.98);
}

@media (max-width: 640px) {
  .search-shell {
    grid-template-columns: 38px minmax(0, 1fr) 38px;
  }

  .search-select {
    display: none;
  }

  .panel-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .submit-button {
    justify-content: center;
  }
}
</style>
