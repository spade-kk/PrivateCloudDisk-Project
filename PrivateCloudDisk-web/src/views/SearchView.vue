<template>
  <div class="search-page space-y-5">
    <section class="search-hero">
      <div class="hero-main">
        <div>
          <p class="text-sm text-neutral-500">OpenSearch</p>
          <h1 class="mt-1 text-2xl font-bold text-neutral-700 sm:text-3xl">文件搜索</h1>
        </div>
        <SmartSearchBox mode="hero" @search="runSearch" />
      </div>
    </section>

    <section class="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
      <aside class="filters-panel">
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold">过滤</h2>
          <button class="text-sm text-primary" @click="clearFilters">重置</button>
        </div>

        <div class="filter-block">
          <label>文件类型</label>
          <button
            v-for="item in categoryBuckets"
            :key="item.key"
            type="button"
            class="bucket-row"
            :class="{ active: searchStore.filters.file_category === item.key }"
            @click="toggleFilter('file_category', item.key)"
          >
            <span>{{ categoryName(item.key) }}</span>
            <strong>{{ item.count }}</strong>
          </button>
        </div>

        <div class="filter-block">
          <label>标签</label>
          <button
            v-for="item in tagBuckets"
            :key="item.key"
            type="button"
            class="bucket-row"
            :class="{ active: searchStore.filters.tags === item.key }"
            @click="toggleFilter('tags', item.key)"
          >
            <span>{{ item.key }}</span>
            <strong>{{ item.count }}</strong>
          </button>
          <p v-if="tagBuckets.length === 0" class="text-sm text-neutral-400">暂无标签聚合</p>
        </div>
      </aside>

      <div class="min-w-0 space-y-4">
        <div class="result-toolbar">
          <div>
            <p class="text-sm text-neutral-500">共 {{ searchStore.total }} 条结果</p>
            <p v-if="searchStore.keyword" class="mt-1 truncate text-base font-semibold">“{{ searchStore.keyword }}”</p>
          </div>
          <div class="toolbar-controls">
            <select v-model="searchStore.sortField" @change="runSearch({ page: 1 })">
              <option value="_score">相关度</option>
              <option value="updated_at">更新时间</option>
              <option value="size_bytes">文件大小</option>
              <option value="filename.keyword">文件名</option>
            </select>
            <button class="icon-button" title="切换排序方向" @click="toggleOrder">
              <i :class="searchStore.asc ? 'fa fa-sort-amount-asc' : 'fa fa-sort-amount-desc'"></i>
            </button>
            <select v-model.number="searchStore.size" @change="runSearch({ page: 1 })">
              <option :value="10">10/页</option>
              <option :value="12">12/页</option>
              <option :value="20">20/页</option>
              <option :value="30">30/页</option>
            </select>
          </div>
        </div>

        <div v-if="searchStore.loading" class="responsive-panel flex justify-center py-16">
          <LoadingSpinner />
        </div>

        <PageState
          v-else-if="searchStore.error"
          type="error"
          icon="fa fa-exclamation-triangle"
          title="搜索失败"
          :description="searchStore.error"
          action-text="重试"
          action-icon="fa fa-refresh"
          @action="runSearch"
        />

        <div v-else-if="searchStore.hits.length === 0" class="empty-search">
          <i class="fa fa-search"></i>
          <p>没有找到匹配文件</p>
        </div>

        <div v-else class="result-list">
          <article v-for="hit in searchStore.hits" :key="resultKey(hit)" class="result-card">
            <div class="file-badge">
              <i :class="['fa', iconClass(hit)]"></i>
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div class="min-w-0">
                  <h3 class="result-title" v-html="highlight(hit, 'filename', fileName(hit))"></h3>
                  <div class="meta-row">
                    <span>{{ categoryName(field(hit, ['file_category', 'category'], 'file')) }}</span>
                    <span>{{ formatFileSize(Number(field(hit, ['size_bytes', 'file_size', 'size'], 0))) }}</span>
                    <span>{{ formatDateTime(field(hit, ['updated_at', 'uploaded_time', 'create_time'])) }}</span>
                  </div>
                </div>
                <button class="open-button" @click="openResult(hit)">
                  <i class="fa fa-folder-open-o"></i>
                  <span>定位</span>
                </button>
              </div>
              <p class="snippet" v-html="bestSnippet(hit)"></p>
              <div class="tag-row" v-if="normalizeTags(field(hit, ['tags'], [])).length">
                <span v-for="tag in normalizeTags(field(hit, ['tags'], []))" :key="tag">{{ tag }}</span>
              </div>
            </div>
          </article>
        </div>

        <div v-if="searchStore.totalPages > 1" class="pagination-bar">
          <button :disabled="searchStore.page <= 1" @click="changePage(searchStore.page - 1)">
            <i class="fa fa-angle-left"></i>
          </button>
          <button
            v-for="item in pageItems"
            :key="item"
            :class="{ active: item === searchStore.page }"
            @click="changePage(item)"
          >
            {{ item }}
          </button>
          <button :disabled="searchStore.page >= searchStore.totalPages" @click="changePage(searchStore.page + 1)">
            <i class="fa fa-angle-right"></i>
          </button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SmartSearchBox from '@/components/search/SmartSearchBox.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageState from '@/components/common/PageState.vue'
import { useSearchStore } from '@/stores/searchStore'
import { formatDateTime, formatFileSize } from '@/utils/helpers'
import { getFileIconClass } from '@/utils/fileIcon'

const route = useRoute()
const router = useRouter()
const searchStore = useSearchStore()

const categoryBuckets = computed(() => toBuckets(searchStore.aggregations.file_category))
const tagBuckets = computed(() => toBuckets(searchStore.aggregations.tags).slice(0, 12))
const pageItems = computed(() => {
  const total = searchStore.totalPages
  const current = searchStore.page
  const start = Math.max(1, current - 2)
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

onMounted(loadFromRoute)

watch(() => route.query, loadFromRoute)

function loadFromRoute() {
  searchStore.keyword = route.query.q || ''
  searchStore.filters.file_category = route.query.category || ''
  searchStore.filters.tags = route.query.tag || ''
  searchStore.sortField = route.query.sort || '_score'
  searchStore.asc = route.query.asc === 'true'
  searchStore.page = Number(route.query.page || 1)
  searchStore.size = Number(route.query.size || searchStore.size || 12)
  searchStore.search()
}

function runSearch(payload = {}) {
  const nextKeyword = payload.keyword ?? searchStore.keyword
  const nextFilters = payload.filters ?? searchStore.filters
  const nextSort = payload.sortField ?? searchStore.sortField
  const nextAsc = payload.asc ?? searchStore.asc
  const nextPage = payload.page ?? 1

  router.push({
    path: '/app/search',
    query: {
      q: nextKeyword || undefined,
      category: nextFilters.file_category || undefined,
      tag: nextFilters.tags || undefined,
      sort: nextSort,
      asc: String(nextAsc),
      page: String(nextPage),
      size: String(searchStore.size),
    },
  })
}

function clearFilters() {
  searchStore.resetFilters()
  runSearch({ page: 1 })
}

function toggleFilter(key, value) {
  const next = { ...searchStore.filters, [key]: searchStore.filters[key] === value ? '' : value }
  searchStore.setFilters(next)
  runSearch({ filters: next, page: 1 })
}

function toggleOrder() {
  searchStore.asc = !searchStore.asc
  runSearch({ page: 1 })
}

function changePage(nextPage) {
  runSearch({ page: nextPage })
}

function openResult(hit) {
  const nodeId = field(hit, ['node_id', 'file_node_id', 'parent_id'])
  if (nodeId) router.push({ path: '/', query: { node: nodeId } })
}

function toBuckets(source = {}) {
  return Object.entries(source || {})
    .map(([key, count]) => ({ key, count }))
    .sort((a, b) => b.count - a.count)
}

function field(hit, keys, fallback = '') {
  for (const key of keys) {
    if (hit?.[key] !== undefined && hit?.[key] !== null) return hit[key]
  }
  return fallback
}

function fileName(hit) {
  return field(hit, ['filename', 'file_name', 'name'], '未命名文件')
}

function resultKey(hit) {
  return field(hit, ['file_id', 'id', 'filename'], Math.random())
}

function iconClass(hit) {
  return getFileIconClass(fileName(hit))
}

function categoryName(value) {
  const map = {
    document: '文档',
    image: '图片',
    video: '视频',
    audio: '音频',
    archive: '压缩包',
    file: '文件',
  }
  return map[value] || value || '文件'
}

function normalizeTags(value) {
  if (Array.isArray(value)) return value
  if (typeof value === 'string') return value.split(',').map(item => item.trim()).filter(Boolean)
  return []
}

function escapeHtml(value) {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function highlight(hit, key, fallback = '') {
  const raw = hit?._highlight?.[key] || fallback
  const marked = String(raw)
    .replaceAll('<em>', '[[hit]]')
    .replaceAll('</em>', '[[/hit]]')
  return escapeHtml(marked)
    .replaceAll('[[hit]]', '<mark>')
    .replaceAll('[[/hit]]', '</mark>')
}

function bestSnippet(hit) {
  const highlights = hit?._highlight || {}
  if (highlights.content_text) return highlight(hit, 'content_text')
  if (highlights.summary) return highlight(hit, 'summary')
  if (highlights.ocr_text) return highlight(hit, 'ocr_text')
  return escapeHtml(field(hit, ['summary', 'content_text', 'ocr_text'], '暂无内容摘要'))
}
</script>

<style scoped>
.search-hero,
.filters-panel,
.result-toolbar,
.result-card,
.empty-search {
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.search-hero {
  padding: 18px;
}

.hero-main {
  display: grid;
  gap: 16px;
}

.filters-panel {
  align-self: start;
  padding: 16px;
}

.filter-block {
  margin-top: 18px;
}

.filter-block label {
  display: block;
  margin-bottom: 8px;
  color: #606266;
  font-size: 13px;
}

.bucket-row {
  display: flex;
  min-height: 38px;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  border-radius: 8px;
  padding: 0 10px;
  color: #303133;
  transition: background 0.16s ease, color 0.16s ease;
}

.bucket-row:hover,
.bucket-row.active {
  background: rgba(22, 93, 255, 0.08);
  color: #165dff;
}

.result-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px;
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.toolbar-controls select {
  height: 38px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  padding: 0 10px;
  outline: none;
}

.result-list {
  display: grid;
  gap: 10px;
}

.result-card {
  display: flex;
  gap: 14px;
  padding: 16px;
}

.file-badge {
  display: flex;
  height: 44px;
  width: 44px;
  flex: 0 0 auto;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  background: #f5f7fa;
  color: #165dff;
  font-size: 20px;
}

.result-title {
  overflow: hidden;
  color: #1e1e1e;
  font-size: 16px;
  font-weight: 700;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta-row,
.tag-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.meta-row span,
.tag-row span {
  border-radius: 8px;
  background: #f5f7fa;
  padding: 3px 8px;
}

.snippet {
  display: -webkit-box;
  margin-top: 10px;
  overflow: hidden;
  color: #606266;
  font-size: 13px;
  line-height: 1.65;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.open-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 36px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  padding: 0 12px;
  color: #165dff;
}

.empty-search {
  display: grid;
  place-items: center;
  min-height: 260px;
  color: #909399;
  gap: 10px;
}

.empty-search i {
  font-size: 36px;
}

.pagination-bar {
  display: flex;
  justify-content: center;
  gap: 6px;
}

.pagination-bar button {
  min-width: 38px;
  min-height: 38px;
  border-radius: 8px;
  background: #fff;
  color: #303133;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
}

.pagination-bar button.active {
  background: #165dff;
  color: #fff;
}

.pagination-bar button:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

:deep(mark) {
  border-radius: 4px;
  background: rgba(250, 173, 20, 0.24);
  color: #8a5a00;
  padding: 0 2px;
}

@media (min-width: 768px) {
  .hero-main {
    grid-template-columns: 220px minmax(0, 1fr);
    align-items: center;
  }
}

@media (max-width: 640px) {
  .result-toolbar {
    align-items: stretch;
    flex-direction: column;
  }

  .toolbar-controls {
    overflow-x: auto;
  }

  .result-card {
    padding: 14px;
  }
}
</style>
