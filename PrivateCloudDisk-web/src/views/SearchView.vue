<template>
  <div class="search-page space-y-5">
    <section class="search-hero">
      <div class="hero-main">
        <div>
          <p class="text-sm text-neutral-500">OpenSearch</p>
          <h1 class="mt-1 text-2xl font-bold text-neutral-700 sm:text-3xl">文件搜索</h1>
          <CurrentSpaceBadge class="mt-2" />
        </div>
        <SmartSearchBox mode="hero" @search="runSearch" />
      </div>
    </section>

    <!-- 公开仓库搜索与文件搜索分栏，仓库结果只展示登录后可访问的 visible public space。 -->
    <div class="search-tabs" role="tablist">
      <button :class="{ active: resultTab === 'files' }" @click="resultTab = 'files'">文件</button>
      <button :class="{ active: resultTab === 'spaces' }" @click="resultTab = 'spaces'; loadPublicSpaces()">空间</button>
    </div>

    <section v-if="resultTab === 'spaces'" class="space-search-results">
      <div v-if="spaceLoading" class="responsive-panel flex justify-center py-16"><LoadingSpinner /></div>
      <PageState v-else-if="spaceResults.length === 0" type="empty" icon="fa fa-cubes" title="暂无公开仓库" description="尝试更换关键词，或前往探索页浏览推荐仓库" action-text="打开探索" @action="router.push('/explore')" />
      <div v-else class="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
        <article v-for="space in spaceResults" :key="space.spaceId" class="space-result-card" @click="router.push(`/repo/${space.spaceId}`)">
          <div class="flex items-center justify-between"><h3 class="truncate font-semibold text-primary">{{ space.spaceName }}</h3><span class="rounded-full border px-2 py-0.5 text-[10px] text-neutral-500">Public</span></div>
          <p class="mt-2 line-clamp-2 text-sm text-neutral-500">{{ space.description || '暂无描述' }}</p>
          <div class="mt-3 text-xs text-neutral-400">@{{ space.ownerName }} · {{ space.fileCount || 0 }} 个文件</div>
        </article>
      </div>
    </section>

    <section v-else class="grid gap-4 lg:grid-cols-[260px_minmax(0,1fr)]">
      <!-- 移动端过滤器切换按钮 -->
      <div class="flex items-center gap-2 lg:hidden">
        <button
          @click="showFilters = !showFilters"
          class="touch-button flex items-center gap-2 rounded-lg border border-neutral-200 px-4 py-2 text-sm font-medium text-neutral-700"
        >
          <i class="fa fa-sliders"></i> 过滤
          <span v-if="activeFilterCount" class="ml-1 rounded-full bg-primary px-1.5 text-xs text-white">{{ activeFilterCount }}</span>
        </button>
        <button v-if="activeFilterCount" @click="clearFilters" class="text-sm text-primary">重置</button>
      </div>

      <!-- 过滤器侧栏 -->
      <aside
        class="filters-panel lg:static"
        :class="showFilters ? 'block' : 'hidden lg:block'"
      >
        <div class="flex items-center justify-between">
          <h2 class="text-base font-semibold">过滤</h2>
          <div class="flex items-center gap-3">
            <button class="text-sm text-primary" @click="clearFilters">重置</button>
            <button class="text-sm text-neutral-400 lg:hidden" @click="showFilters = false">
              <i class="fa fa-times"></i>
            </button>
          </div>
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

      <!-- 移动端过滤器遮罩 -->
      <div v-if="showFilters" class="fixed inset-0 z-40 bg-black/30 lg:hidden" @click="showFilters = false"></div>

      <div class="min-w-0 space-y-4">
        <div class="result-toolbar">
          <div class="min-w-0">
            <p class="text-sm text-neutral-500">共 {{ searchStore.total }} 条结果</p>
            <p v-if="searchStore.keyword" class="mt-1 truncate text-base font-semibold">"{{ searchStore.keyword }}"</p>
          </div>
          <div class="toolbar-controls flex-wrap gap-2">
            <select v-model="searchStore.sortField" @change="runSearch({ page: 1 })" class="h-9 rounded-lg border border-neutral-200 px-2 text-sm">
              <option value="_score">相关度</option>
              <option value="updated_at">更新时间</option>
              <option value="size_bytes">文件大小</option>
              <option value="filename.keyword">文件名</option>
            </select>
            <button class="touch-button rounded-lg border border-neutral-200 px-2.5 py-1.5 text-sm" title="切换排序方向" @click="toggleOrder">
              <i :class="searchStore.asc ? 'fa fa-sort-amount-asc' : 'fa fa-sort-amount-desc'"></i>
            </button>
            <select v-model.number="searchStore.size" @change="runSearch({ page: 1 })" class="h-9 rounded-lg border border-neutral-200 px-2 text-sm">
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
              <FileTypeIcon :file-name="fileName(hit)" class="text-2xl" />
            </div>
            <div class="min-w-0 flex-1">
              <div class="flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
                <div class="min-w-0">
                  <h3 class="result-title" v-safe-html="highlight(hit, 'filename', fileName(hit))"></h3>
                  <div class="meta-row">
                    <span>{{ categoryName(field(hit, ['file_category', 'category'], 'file')) }}</span>
                    <span>{{ formatFileSize(Number(field(hit, ['size_bytes', 'file_size', 'size'], 0))) }}</span>
                    <span class="hidden sm:inline">{{ formatDateTime(field(hit, ['updated_at', 'uploaded_time', 'create_time'])) }}</span>
                  </div>
                </div>
                <button class="open-button touch-button" @click="openResult(hit)">
                  <i class="fa fa-folder-open-o"></i>
                  <span>定位</span>
                </button>
              </div>
              <p class="snippet" v-safe-html="bestSnippet(hit)"></p>
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
import { computed, onMounted, watch, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import SmartSearchBox from '@/components/search/SmartSearchBox.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import PageState from '@/components/common/PageState.vue'
import CurrentSpaceBadge from '@/components/space/CurrentSpaceBadge.vue'
import { useSearchStore } from '@/stores/searchStore'
import { useSpaceStore } from '@/stores/spaceStore'
import { formatDateTime, formatFileSize } from '@/utils/helpers'
import FileTypeIcon from '@/components/file/FileTypeIcon.vue'
import { searchPublicSpacesApi, type PublicSpaceDetail } from '@/api/modules/publicSpaces'

const route = useRoute()
const router = useRouter()
const searchStore = useSearchStore()
const spaceStore = useSpaceStore()

const showFilters = ref(false)
const resultTab = ref<'files' | 'spaces'>('files')
const spaceResults = ref<PublicSpaceDetail[]>([])
const spaceLoading = ref(false)

const categoryBuckets = computed(() => toBuckets(searchStore.aggregations.file_category))
const tagBuckets = computed(() => toBuckets(searchStore.aggregations.tags).slice(0, 12))
const activeFilterCount = computed(() => {
  let count = 0
  if (searchStore.filters.file_category) count++
  if (searchStore.filters.tags) count++
  return count
})
const pageItems = computed(() => {
  const total = searchStore.totalPages
  const current = searchStore.page
  const start = Math.max(1, current - 2)
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, index) => start + index)
})

onMounted(loadFromRoute)

watch(() => route.query, loadFromRoute)

/*
 * 空间管理能力全量集成（需求四-2/3）：
 * 空间切换后保留检索词和筛选条件，仅刷新结果集，避免短暂展示上一个空间的数据。
 */
watch(() => spaceStore.revision, () => {
  searchStore.page = 1
  void searchStore.search({ page: 1 })
})

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

async function loadPublicSpaces() {
  spaceLoading.value = true
  try { spaceResults.value = (await searchPublicSpacesApi(searchStore.keyword)).data || [] } finally { spaceLoading.value = false }
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

.search-tabs { display:flex; gap:4px; border-bottom:1px solid #e5e7eb; }
.search-tabs button { border-bottom:2px solid transparent; padding:9px 14px; font-size:13px; color:#6b7280; }
.search-tabs button.active { border-bottom-color:#165dff; color:#165dff; font-weight:600; }
.space-search-results { min-height:220px; }
.space-result-card { cursor:pointer; border:1px solid #e5e7eb; border-radius:12px; background:#fff; padding:18px; transition:box-shadow .15s, transform .15s; }
.space-result-card:hover { box-shadow:0 8px 20px rgba(0,0,0,.08); transform:translateY(-1px); }

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

/* 移动端：过滤器浮层 */
@media (max-width: 1023px) {
  .filters-panel {
    position: fixed;
    top: 0;
    left: 0;
    z-index: 50;
    height: 100dvh;
    width: 280px;
    max-width: 85vw;
    overflow-y: auto;
    border-radius: 0 12px 12px 0;
    box-shadow: 4px 0 24px rgba(0, 0, 0, 0.15);
  }
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
  flex-wrap: wrap;
}

@media (max-width: 639px) {
  .result-toolbar {
    padding: 10px 12px;
    gap: 8px;
  }
}

.toolbar-controls {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
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
