<template>
  <div class="explore-page min-h-screen bg-[#f6f8fa] text-[#1f2328]">
    <header class="explore-nav border-b border-[#d0d7de] bg-white">
      <div class="mx-auto flex min-h-16 max-w-[1280px] flex-wrap items-center gap-3 px-4 py-3 lg:px-8">
        <router-link class="explore-nav-mark" to="/app" aria-label="返回控制面板"><i class="fa fa-cube"></i></router-link>
        <nav class="order-3 flex w-full items-center gap-1 overflow-x-auto text-sm sm:order-none sm:w-auto" aria-label="公开资源导航">
          <router-link class="explore-nav-link" to="/app"><i class="fa fa-arrow-left mr-1.5"></i>我的网盘</router-link>
          <router-link class="explore-nav-link explore-nav-link--active" to="/explore">探索</router-link>
          <router-link class="explore-nav-link" to="/teamwork">团队协作</router-link>
        </nav>
        <div class="ml-auto flex items-center gap-2">
          <router-link class="explore-nav-link hidden sm:inline-flex" to="/space/manage?create=1"><i class="fa fa-plus mr-1.5"></i>创建公开空间</router-link>
          <router-link class="explore-avatar-link" to="/app/profile" title="个人设置">{{ userInitial }}</router-link>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-[1280px] px-4 pb-14 pt-6 lg:px-8 lg:pt-8">
      <section class="explore-hero" aria-labelledby="explore-title">
        <div class="explore-hero__content">
          <p class="eyebrow">PUBLIC SPACES</p>
          <h1 id="explore-title">探索公开仓库</h1>
          <p>浏览平台中可访问的文件与 Git 仓库。搜索、筛选和排序始终基于公开空间服务返回的真实目录数据。</p>
          <form class="explore-search-box" @submit.prevent="submitSearch">
            <i class="fa fa-search" aria-hidden="true"></i>
            <input v-model.trim="keyword" autocomplete="off" aria-label="搜索公开仓库" placeholder="搜索名称、描述或所有者" @input="scheduleSearch" />
            <button v-if="keyword" class="explore-search-clear" type="button" aria-label="清除搜索" @click="clearSearch"><i class="fa fa-times"></i></button>
            <button class="explore-search-submit" type="submit">搜索</button>
          </form>
          <div class="explore-search-helper">
            <span>快速筛选：</span>
            <button v-for="item in resourceFilters" :key="item.value" type="button" :class="{ active: resourceFilter === item.value }" @click="setResourceFilter(item.value)">{{ item.label }}</button>
          </div>
          <div v-if="searchHistory.length" class="explore-search-history" aria-label="最近搜索">
            <span>最近搜索</span>
            <button v-for="entry in searchHistory" :key="entry" type="button" @click="useHistory(entry)">{{ entry }}</button>
            <button type="button" class="history-clear" @click="clearHistory">清除</button>
          </div>
        </div>
        <aside class="explore-hero__stats" aria-label="公开仓库统计">
          <div><b>{{ stats.total }}</b><span>已加载公开仓库</span></div>
          <div><b>{{ stats.git }}</b><span>Git 仓库</span></div>
          <div><b>{{ stats.file }}</b><span>文件仓库</span></div>
          <div><b>{{ stats.active }}</b><span>30 天内更新</span></div>
        </aside>
      </section>

      <section class="explore-toolbar" aria-label="仓库筛选和排序">
        <div class="explore-resource-tabs" role="tablist" aria-label="资源类型">
          <button v-for="item in resourceFilters" :key="`tab-${item.value}`" type="button" role="tab" :aria-selected="resourceFilter === item.value" :class="{ active: resourceFilter === item.value }" @click="setResourceFilter(item.value)">
            <i :class="item.icon"></i>{{ item.label }}<small>{{ countByType(item.value) }}</small>
          </button>
        </div>
        <label class="explore-sort-select">排序
          <select v-model="sortBy" @change="syncRoute">
            <option value="relevance">最相关</option>
            <option value="updated">最近更新</option>
            <option value="created">最新发布</option>
            <option value="files">文件数最多</option>
            <option value="storage">占用空间最多</option>
          </select>
        </label>
      </section>

      <section v-if="loading" class="explore-repo-grid explore-repo-grid--skeleton" aria-label="正在加载">
        <article v-for="index in 6" :key="index" class="explore-repo-card explore-repo-card--skeleton"><span></span><span></span><span></span><span></span></article>
      </section>

      <section v-else-if="error" class="explore-state-card explore-state-card--error">
        <i class="fa fa-exclamation-triangle"></i><h2>公开仓库暂时无法加载</h2><p>{{ error }}</p><button type="button" @click="loadRepositories">重新加载</button>
      </section>

      <template v-else>
        <section v-if="hasSearch" class="explore-content-section" aria-labelledby="search-result-title">
          <div class="explore-section-heading"><div><p class="section-kicker">SEARCH RESULTS</p><h2 id="search-result-title">“{{ keyword }}” 的搜索结果</h2></div><span>{{ filteredRepositories.length }} 个匹配项</span></div>
          <RepositoryGrid :repositories="pagedRepositories" :empty-message="'没有匹配的公开仓库，试试其他关键词或资源类型。'" @open="openRepository" />
        </section>

        <template v-else>
          <section class="explore-content-section" aria-labelledby="featured-title">
            <div class="explore-section-heading"><div><p class="section-kicker">FEATURED</p><h2 id="featured-title">推荐浏览</h2><p>按仓库实际文件规模与更新时间整理，帮助你快速发现内容持续维护的资源。</p></div><button type="button" class="explore-text-button" @click="sortBy = 'files'; syncRoute()">查看全部<i class="fa fa-arrow-right"></i></button></div>
            <RepositoryGrid :repositories="featuredRepositories" :empty-message="'当前还没有可展示的公开仓库。'" @open="openRepository" />
          </section>

          <section class="explore-content-section" aria-labelledby="latest-title">
            <div class="explore-section-heading"><div><p class="section-kicker">LATEST</p><h2 id="latest-title">最新发布</h2><p>依创建时间排序；日期缺失的历史记录会排在列表末尾。</p></div><button type="button" class="explore-text-button" @click="sortBy = 'created'; syncRoute()">按最新浏览<i class="fa fa-arrow-right"></i></button></div>
            <RepositoryGrid :repositories="latestRepositories" :empty-message="'暂无带创建时间的公开仓库。'" @open="openRepository" />
          </section>

          <section class="explore-content-section" aria-labelledby="active-title">
            <div class="explore-section-heading"><div><p class="section-kicker">ACTIVE</p><h2 id="active-title">近期活跃</h2><p>按最近更新时间排序，不以无法验证的收藏或派生指标作为排名依据。</p></div><button type="button" class="explore-text-button" @click="sortBy = 'updated'; syncRoute()">按活跃浏览<i class="fa fa-arrow-right"></i></button></div>
            <RepositoryGrid :repositories="activeRepositories" :empty-message="'暂无包含更新时间的公开仓库。'" @open="openRepository" />
          </section>
        </template>

        <section v-if="filteredRepositories.length" class="explore-content-section explore-content-section--all" aria-labelledby="all-title">
          <div class="explore-section-heading"><div><p class="section-kicker">ALL REPOSITORIES</p><h2 id="all-title">全部结果</h2></div><span>第 {{ currentPage }} / {{ totalPages }} 页</span></div>
          <RepositoryGrid :repositories="pagedRepositories" :empty-message="'暂无公开仓库。'" @open="openRepository" />
          <nav v-if="totalPages > 1" class="explore-pagination" aria-label="公开仓库分页">
            <button type="button" :disabled="currentPage === 1" @click="goPage(currentPage - 1)"><i class="fa fa-angle-left"></i>上一页</button>
            <button v-for="page in pageNumbers" :key="page" type="button" :class="{ active: page === currentPage }" @click="goPage(page)">{{ page }}</button>
            <button type="button" :disabled="currentPage === totalPages" @click="goPage(currentPage + 1)">下一页<i class="fa fa-angle-right"></i></button>
          </nav>
        </section>
      </template>
    </main>

    <button v-show="showBackToTop" class="back-to-top" type="button" aria-label="回到顶部" @click="window.scrollTo({ top: 0, behavior: 'smooth' })"><i class="fa fa-arrow-up"></i></button>
  </div>
</template>

<script setup lang="ts">
import { computed, defineComponent, h, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import { explorePublicSpacesApi, searchPublicSpacesApi, type PublicSpaceDetail } from '@/api/modules/publicSpaces'

/**
 * [REQ-PUBLIC-EXPLORE-2.1~2.24] 卡片只渲染公开空间服务现有字段。
 * 原有页面只提供简单结果列表；新视图派生真实文件数、存储量和时间排序，避免虚构 Star/Fork 指标，影响范围仅为公开探索工作区。
 */
const RepositoryGrid = defineComponent({
  name: 'RepositoryGrid',
  props: { repositories: { type: Array as () => PublicSpaceDetail[], required: true }, emptyMessage: { type: String, required: true } },
  emits: ['open'],
  setup(props, { emit }) {
    const size = (value = 0) => {
      if (!value) return '0 B'
      const units = ['B', 'KB', 'MB', 'GB', 'TB']; const exponent = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1)
      return `${(value / 1024 ** exponent).toFixed(exponent ? 1 : 0)} ${units[exponent]}`
    }
    const date = (value?: string) => value ? new Intl.DateTimeFormat('zh-CN', { month: 'short', day: 'numeric', year: 'numeric' }).format(new Date(value)) : '暂无更新时间'
    const type = (repository: PublicSpaceDetail) => repository.resourceType === 'git' ? 'Git 仓库' : '文件仓库'
    return () => props.repositories.length
      ? h('div', { class: 'explore-repo-grid' }, props.repositories.map(repository => h('article', { class: 'explore-repo-card', tabindex: 0, onClick: () => emit('open', repository), onKeyup: (event: KeyboardEvent) => event.key === 'Enter' && emit('open', repository) }, [
        h('div', { class: 'repo-card__top' }, [
          h('span', { class: ['repo-type-icon', repository.resourceType === 'git' ? 'repo-type-icon--git' : ''] }, [h('i', { class: repository.resourceType === 'git' ? 'fa fa-code-fork' : 'fa fa-folder-o' })]),
          h('span', { class: 'repo-visibility' }, 'Public'),
          h('button', { class: 'repo-more', type: 'button', title: '打开仓库', onClick: (event: MouseEvent) => { event.stopPropagation(); emit('open', repository) } }, [h('i', { class: 'fa fa-angle-right' })]),
        ]),
        h('h3', { title: repository.spaceName }, repository.spaceName),
        h('p', { class: 'repo-description' }, repository.description || '这个公开仓库暂未填写描述。'),
        h('div', { class: 'repo-owner' }, [h('span', { class: 'repo-owner__avatar' }, (repository.ownerName || '?').slice(0, 1).toUpperCase()), h('span', { class: 'repo-owner__name' }, repository.ownerName || '未知所有者')]),
        h('div', { class: 'repo-meta' }, [h('span', { class: 'repo-type-label' }, [h('i', { class: repository.resourceType === 'git' ? 'fa fa-code-fork' : 'fa fa-folder-o' }), type(repository)]), h('span', [h('i', { class: 'fa fa-files-o' }), `${repository.fileCount || 0} 个文件`])]),
        h('div', { class: 'repo-footer' }, [h('span', [h('i', { class: 'fa fa-database' }), size(repository.usedBytes || 0)]), h('time', { title: repository.updatedAt || '' }, [h('i', { class: 'fa fa-clock-o' }), date(repository.updatedAt || repository.createdAt)])]),
      ])))
      : h('div', { class: 'explore-state-card' }, [h('i', { class: 'fa fa-compass' }), h('h3', '暂未找到仓库'), h('p', props.emptyMessage)])
  },
})

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const repositories = ref<PublicSpaceDetail[]>([])
const keyword = ref('')
const resourceFilter = ref<'all' | 'git' | 'file'>('all')
const sortBy = ref<'relevance' | 'updated' | 'created' | 'files' | 'storage'>('relevance')
const currentPage = ref(1)
const loading = ref(false)
const error = ref('')
const showBackToTop = ref(false)
const searchHistory = ref<string[]>([])
let searchTimer: ReturnType<typeof setTimeout> | undefined
const pageSize = 9
const resourceFilters = [
  { label: '全部', value: 'all' as const, icon: 'fa fa-th-large' },
  { label: 'Git 仓库', value: 'git' as const, icon: 'fa fa-code-fork' },
  { label: '文件仓库', value: 'file' as const, icon: 'fa fa-folder-o' },
]
const userInitial = computed(() => String(authStore.user?.name || authStore.user?.account || 'U').slice(0, 1).toUpperCase())
const hasSearch = computed(() => Boolean(keyword.value || resourceFilter.value !== 'all'))
const stats = computed(() => ({
  total: repositories.value.length,
  git: repositories.value.filter(item => item.resourceType === 'git').length,
  file: repositories.value.filter(item => item.resourceType !== 'git').length,
  active: repositories.value.filter(item => item.updatedAt && Date.now() - new Date(item.updatedAt).getTime() < 30 * 86400000).length,
}))
const filteredRepositories = computed(() => {
  const normalized = keyword.value.toLocaleLowerCase()
  const filtered = repositories.value.filter(item => {
    const matchType = resourceFilter.value === 'all' || (resourceFilter.value === 'git' ? item.resourceType === 'git' : item.resourceType !== 'git')
    const searchable = `${item.spaceName} ${item.description || ''} ${item.ownerName || ''}`.toLocaleLowerCase()
    return matchType && (!normalized || searchable.includes(normalized))
  })
  return [...filtered].sort((a, b) => compareRepositories(a, b, sortBy.value, normalized))
})
const featuredRepositories = computed(() => [...repositories.value].sort((a, b) => compareRepositories(a, b, 'files', '')).slice(0, 3))
const latestRepositories = computed(() => [...repositories.value].sort((a, b) => compareRepositories(a, b, 'created', '')).slice(0, 3))
const activeRepositories = computed(() => [...repositories.value].sort((a, b) => compareRepositories(a, b, 'updated', '')).slice(0, 3))
const totalPages = computed(() => Math.max(1, Math.ceil(filteredRepositories.value.length / pageSize)))
const pagedRepositories = computed(() => filteredRepositories.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize))
const pageNumbers = computed(() => Array.from({ length: totalPages.value }, (_, index) => index + 1).filter(page => page === 1 || page === totalPages.value || Math.abs(page - currentPage.value) <= 1))

function compareRepositories(a: PublicSpaceDetail, b: PublicSpaceDetail, mode: typeof sortBy.value, query: string) {
  if (mode === 'files') return (b.fileCount || 0) - (a.fileCount || 0) || dateNumber(b.updatedAt) - dateNumber(a.updatedAt)
  if (mode === 'storage') return (b.usedBytes || 0) - (a.usedBytes || 0) || dateNumber(b.updatedAt) - dateNumber(a.updatedAt)
  if (mode === 'updated') return dateNumber(b.updatedAt) - dateNumber(a.updatedAt)
  if (mode === 'created') return dateNumber(b.createdAt) - dateNumber(a.createdAt)
  const aName = a.spaceName.toLocaleLowerCase(); const bName = b.spaceName.toLocaleLowerCase()
  return (aName.startsWith(query) ? -1 : 0) - (bName.startsWith(query) ? -1 : 0) || dateNumber(b.updatedAt) - dateNumber(a.updatedAt)
}
function dateNumber(value?: string) { const result = value ? new Date(value).getTime() : 0; return Number.isFinite(result) ? result : 0 }
function countByType(type: typeof resourceFilter.value) { return type === 'all' ? repositories.value.length : repositories.value.filter(item => type === 'git' ? item.resourceType === 'git' : item.resourceType !== 'git').length }
function readHistory() { try { searchHistory.value = JSON.parse(localStorage.getItem('pcd-public-space-explore-search-history') || '[]').filter((entry: unknown) => typeof entry === 'string').slice(0, 5) } catch { searchHistory.value = [] } }
function storeHistory(value: string) { if (!value) return; searchHistory.value = [value, ...searchHistory.value.filter(item => item !== value)].slice(0, 5); localStorage.setItem('pcd-public-space-explore-search-history', JSON.stringify(searchHistory.value)) }
function clearHistory() { searchHistory.value = []; localStorage.removeItem('pcd-public-space-explore-search-history') }
function hydrateRoute() {
  keyword.value = typeof route.query.q === 'string' ? route.query.q : ''
  resourceFilter.value = route.query.type === 'git' || route.query.type === 'file' ? route.query.type : 'all'
  sortBy.value = ['updated', 'created', 'files', 'storage'].includes(String(route.query.sort)) ? route.query.sort as typeof sortBy.value : 'relevance'
  currentPage.value = Math.max(1, Number(route.query.page) || 1)
}
function syncRoute() {
  currentPage.value = Math.min(currentPage.value, totalPages.value)
  void router.replace({ query: { ...(keyword.value ? { q: keyword.value } : {}), ...(resourceFilter.value !== 'all' ? { type: resourceFilter.value } : {}), ...(sortBy.value !== 'relevance' ? { sort: sortBy.value } : {}), ...(currentPage.value > 1 ? { page: String(currentPage.value) } : {}) } })
}
function goPage(page: number) { currentPage.value = Math.min(Math.max(page, 1), totalPages.value); syncRoute(); window.scrollTo({ top: 0, behavior: 'smooth' }) }
function openRepository(repository: PublicSpaceDetail) { void router.push(`/repo/${encodeURIComponent(repository.spaceId)}`) }
function setResourceFilter(value: typeof resourceFilter.value) { resourceFilter.value = value; currentPage.value = 1; syncRoute() }
function submitSearch() { storeHistory(keyword.value); currentPage.value = 1; syncRoute(); void loadRepositories() }
function useHistory(value: string) { keyword.value = value; submitSearch() }
function clearSearch() { keyword.value = ''; currentPage.value = 1; syncRoute(); void loadRepositories() }
function scheduleSearch() { if (searchTimer) clearTimeout(searchTimer); searchTimer = setTimeout(() => { currentPage.value = 1; syncRoute(); void loadRepositories() }, 300) }
async function loadRepositories() {
  loading.value = true; error.value = ''
  try {
    const response = keyword.value ? await searchPublicSpacesApi(keyword.value) : await explorePublicSpacesApi()
    repositories.value = response.data || []
    currentPage.value = Math.min(currentPage.value, totalPages.value)
  } catch (cause: unknown) {
    error.value = cause instanceof Error ? cause.message : '请求未能完成，请检查网络后重试。'
    repositories.value = []
  } finally { loading.value = false }
}
function onScroll() { showBackToTop.value = window.scrollY > 480 }

watch(() => route.query, () => { hydrateRoute() }, { deep: true })
watch([resourceFilter, sortBy], () => { currentPage.value = 1 })
onMounted(async () => { hydrateRoute(); readHistory(); await authStore.fetchUserInfo(); window.addEventListener('scroll', onScroll, { passive: true }); await loadRepositories() })
onBeforeUnmount(() => { if (searchTimer) clearTimeout(searchTimer); window.removeEventListener('scroll', onScroll) })
</script>

<style>
.explore-nav { position:sticky;top:0;z-index:30;box-shadow:0 1px 0 rgba(31,35,40,.04); }.explore-nav-mark { display:inline-flex;height:32px;width:32px;align-items:center;justify-content:center;border-radius:7px;background:#0969da;color:#fff; }.explore-nav-link { display:inline-flex;min-height:32px;align-items:center;border-radius:6px;padding:0 10px;color:#57606a;white-space:nowrap; }.explore-nav-link:hover,.explore-nav-link--active { background:#f6f8fa;color:#0969da;font-weight:600; }.explore-avatar-link { display:inline-flex;height:32px;width:32px;align-items:center;justify-content:center;border:1px solid #d0d7de;border-radius:50%;background:#ddf4ff;color:#0969da;font-size:12px;font-weight:700; }.explore-hero { display:grid;gap:20px;overflow:hidden;border:1px solid #d0d7de;border-radius:12px;background:radial-gradient(circle at 88% 15%,#ddf4ff 0,transparent 27%),linear-gradient(135deg,#fff,#f6f8fa);padding:28px; }.eyebrow,.section-kicker { margin:0;color:#0969da;font-size:11px;font-weight:800;letter-spacing:.14em; }.explore-hero h1 { margin:8px 0 0;font-size:30px;font-weight:700;letter-spacing:-.02em; }.explore-hero__content>p:not(.eyebrow) { max-width:650px;margin:9px 0 0;color:#57606a;font-size:14px;line-height:1.7; }.explore-search-box { display:flex;align-items:center;max-width:720px;min-height:46px;margin-top:20px;border:1px solid #8c959f;border-radius:8px;background:#fff;box-shadow:0 1px 2px rgba(31,35,40,.04); }.explore-search-box>i { margin-left:14px;color:#57606a; }.explore-search-box input { min-width:0;flex:1;border:0;outline:0;background:transparent;padding:0 10px;color:#1f2328;font-size:14px; }.explore-search-box:focus-within { border-color:#0969da;box-shadow:0 0 0 3px rgba(9,105,218,.15); }.explore-search-clear { width:32px;color:#57606a; }.explore-search-submit { align-self:stretch;border-left:1px solid #d0d7de;background:#f6f8fa;padding:0 15px;color:#24292f;font-size:13px;font-weight:600; }.explore-search-submit:hover { background:#0969da;color:#fff; }.explore-search-helper,.explore-search-history { display:flex;flex-wrap:wrap;align-items:center;gap:7px;margin-top:12px;color:#57606a;font-size:12px; }.explore-search-helper button,.explore-search-history button { border:1px solid #d0d7de;border-radius:999px;background:#fff;padding:3px 9px;color:#57606a; }.explore-search-helper button:hover,.explore-search-helper button.active,.explore-search-history button:hover { border-color:#0969da;background:#ddf4ff;color:#0969da; }.explore-search-history .history-clear { margin-left:2px;border:0;background:transparent;color:#0969da;text-decoration:underline; }.explore-hero__stats { display:grid;grid-template-columns:repeat(2,minmax(0,1fr));border:1px solid #d0d7de;border-radius:8px;background:rgba(255,255,255,.7); }.explore-hero__stats div { padding:14px;border-bottom:1px solid #d0d7de; }.explore-hero__stats div:nth-child(odd) { border-right:1px solid #d0d7de; }.explore-hero__stats div:nth-last-child(-n+2) { border-bottom:0; }.explore-hero__stats b,.explore-hero__stats span { display:block; }.explore-hero__stats b { color:#24292f;font-size:20px; }.explore-hero__stats span { margin-top:3px;color:#57606a;font-size:11px; }.explore-toolbar { display:flex;flex-wrap:wrap;align-items:center;justify-content:space-between;gap:12px;margin:22px 0 0;border-bottom:1px solid #d0d7de; }.explore-resource-tabs { display:flex;overflow-x:auto; }.explore-resource-tabs button { display:inline-flex;align-items:center;gap:7px;border-bottom:2px solid transparent;padding:11px 12px;color:#57606a;font-size:13px;white-space:nowrap; }.explore-resource-tabs button:hover,.explore-resource-tabs button.active { border-bottom-color:#fd8c73;color:#24292f;font-weight:600; }.explore-resource-tabs small { border-radius:999px;background:#eaeef2;padding:1px 5px;color:#57606a;font-size:10px; }.explore-sort-select { display:flex;align-items:center;gap:7px;padding:7px 0;color:#57606a;font-size:12px; }.explore-sort-select select { border:1px solid #d0d7de;border-radius:6px;background:#fff;padding:6px;color:#24292f;font-size:12px; }.explore-content-section { margin-top:34px; }.explore-content-section--all { padding-top:32px;border-top:1px solid #d8dee4; }.explore-section-heading { display:flex;align-items:flex-start;justify-content:space-between;gap:14px;margin-bottom:15px; }.explore-section-heading h2 { margin:4px 0 0;font-size:20px;letter-spacing:-.01em; }.explore-section-heading p { max-width:660px;margin:5px 0 0;color:#57606a;font-size:13px;line-height:1.6; }.explore-section-heading>span { padding-top:9px;color:#57606a;font-size:12px;white-space:nowrap; }.explore-text-button { display:inline-flex;align-items:center;gap:7px;margin-top:6px;color:#0969da;font-size:13px;white-space:nowrap; }.explore-repo-grid { display:grid;gap:14px;grid-template-columns:repeat(auto-fill,minmax(250px,1fr)); }.explore-repo-card { display:flex;min-height:242px;flex-direction:column;border:1px solid #d0d7de;border-radius:8px;background:#fff;padding:16px;box-shadow:0 1px 2px rgba(27,31,36,.04);cursor:pointer;transition:border-color .16s,box-shadow .16s,transform .16s; }.explore-repo-card:hover,.explore-repo-card:focus-visible { outline:0;border-color:#8c959f;box-shadow:0 8px 24px rgba(140,149,159,.18);transform:translateY(-2px); }.repo-card__top { display:flex;align-items:center;gap:8px; }.repo-type-icon { display:inline-flex;height:32px;width:32px;align-items:center;justify-content:center;border-radius:7px;background:#ddf4ff;color:#0969da; }.repo-type-icon--git { background:#fff1e5;color:#bf8700; }.repo-visibility { border:1px solid #d0d7de;border-radius:999px;padding:2px 7px;color:#57606a;font-size:10px;font-weight:600; }.repo-more { margin-left:auto;width:28px;height:28px;border-radius:5px;color:#57606a; }.repo-more:hover { background:#f6f8fa;color:#0969da; }.explore-repo-card h3 { overflow:hidden;margin:13px 0 0;color:#0969da;font-size:15px;line-height:1.35;text-overflow:ellipsis;white-space:nowrap; }.repo-description { display:-webkit-box;min-height:42px;overflow:hidden;margin:7px 0 0;color:#57606a;font-size:12px;line-height:1.7;-webkit-box-orient:vertical;-webkit-line-clamp:2; }.repo-owner { display:flex;min-width:0;align-items:center;gap:7px;margin-top:14px;color:#57606a;font-size:11px; }.repo-owner__avatar { display:inline-flex;width:21px;height:21px;flex:0 0 21px;align-items:center;justify-content:center;border-radius:50%;background:#eaeef2;color:#57606a;font-size:9px;font-weight:700; }.repo-owner__name { overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.repo-meta,.repo-footer { display:flex;align-items:center;gap:11px;color:#57606a;font-size:11px; }.repo-meta { margin-top:13px; }.repo-meta i,.repo-footer i { margin-right:4px; }.repo-type-label { color:#8250df; }.repo-footer { justify-content:space-between;margin-top:auto;padding-top:13px;border-top:1px solid #d8dee4; }.repo-footer time { overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.explore-repo-card--skeleton { min-height:236px;gap:15px;cursor:default; }.explore-repo-card--skeleton span { height:14px;border-radius:5px;background:linear-gradient(90deg,#eaeef2 25%,#f6f8fa 50%,#eaeef2 75%);background-size:200% 100%;animation:loading 1.2s infinite; }.explore-repo-card--skeleton span:nth-child(1) { width:36%;height:32px; }.explore-repo-card--skeleton span:nth-child(2) { width:64%; }.explore-repo-card--skeleton span:nth-child(3) { width:92%; }.explore-repo-card--skeleton span:nth-child(4) { width:48%;margin-top:auto; }.explore-state-card { display:flex;min-height:220px;flex-direction:column;align-items:center;justify-content:center;border:1px dashed #d0d7de;border-radius:8px;background:#fff;padding:28px;color:#57606a;text-align:center; }.explore-state-card i { color:#8c959f;font-size:29px; }.explore-state-card h2,.explore-state-card h3 { margin:12px 0 0;color:#24292f;font-size:16px; }.explore-state-card p { max-width:480px;margin:6px 0 0;font-size:13px;line-height:1.6; }.explore-state-card--error i,.explore-state-card--error h2 { color:#cf222e; }.explore-state-card button { margin-top:14px;border:1px solid #d0d7de;border-radius:6px;background:#f6f8fa;padding:7px 12px;color:#24292f;font-size:12px;font-weight:600; }.explore-pagination { display:flex;align-items:center;justify-content:center;gap:4px;margin-top:20px; }.explore-pagination button { min-width:32px;height:32px;border:1px solid #d0d7de;border-radius:6px;background:#fff;padding:0 9px;color:#24292f;font-size:12px; }.explore-pagination button:hover:not(:disabled),.explore-pagination button.active { border-color:#0969da;background:#0969da;color:#fff; }.explore-pagination button:disabled { color:#8c959f;cursor:not-allowed; }.back-to-top { position:fixed;right:22px;bottom:24px;z-index:20;display:inline-flex;width:38px;height:38px;align-items:center;justify-content:center;border:1px solid #d0d7de;border-radius:50%;background:#fff;color:#0969da;box-shadow:0 6px 20px rgba(27,31,36,.18); }.back-to-top:hover { background:#0969da;color:#fff; }@keyframes loading { to { background-position:-200% 0; } }@media (min-width:900px) { .explore-hero { grid-template-columns:minmax(0,1fr) 300px;padding:34px; }.explore-hero__stats { align-self:stretch;grid-template-columns:1fr; }.explore-hero__stats div,.explore-hero__stats div:nth-child(odd) { border-right:0;border-bottom:1px solid #d0d7de; }.explore-hero__stats div:last-child { border-bottom:0; } }@media (max-width:640px) { .explore-hero { margin:0 -4px;padding:21px; }.explore-hero h1 { font-size:25px; }.explore-search-submit { padding:0 11px; }.explore-section-heading h2 { font-size:18px; }.explore-section-heading>span,.explore-text-button { font-size:11px; }.explore-repo-grid { grid-template-columns:1fr; }.explore-content-section { margin-top:26px; }.explore-pagination button:not(:first-child):not(:last-child):not(.active) { display:none; } }.dark .explore-page { background:#0d1117;color:#f0f6fc; }.dark .explore-nav,.dark .explore-repo-card,.dark .explore-state-card,.dark .explore-hero,.dark .explore-search-box,.dark .explore-sort-select select,.dark .explore-pagination button { border-color:#30363d;background-color:#161b22; }.dark .explore-nav-link:hover,.dark .explore-nav-link--active,.dark .explore-search-submit:hover,.dark .repo-more:hover { background:#21262d; }.dark .explore-hero { background-image:radial-gradient(circle at 88% 15%,rgba(56,139,253,.18) 0,transparent 27%),linear-gradient(135deg,#161b22,#0d1117); }.dark .explore-hero h1,.dark .explore-repo-card h3,.dark .explore-state-card h2,.dark .explore-state-card h3,.dark .explore-resource-tabs button.active { color:#f0f6fc; }.dark .explore-hero__content>p:not(.eyebrow),.dark .explore-section-heading p,.dark .repo-description,.dark .repo-owner,.dark .repo-meta,.dark .repo-footer,.dark .explore-section-heading>span { color:#8b949e; }.dark .explore-search-box input { color:#f0f6fc; }.dark .explore-search-submit { border-color:#30363d;background:#21262d;color:#c9d1d9; }.dark .repo-footer,.dark .explore-content-section--all,.dark .explore-toolbar { border-color:#30363d; }
</style>

<!-- [REQ-PUBLIC-EXPLORE-2.17] 动态卡片由运行时组件生成，主题覆盖使用标准全局选择器以确保暗色模式生效。 -->
<style>
.dark .explore-page{background:#0d1117;color:#f0f6fc}.dark .explore-nav,.dark .explore-hero,.dark .explore-hero__stats,.dark .explore-search-box,.dark .explore-sort-select select,.dark .explore-repo-card,.dark .explore-state-card,.dark .explore-pagination button{border-color:#30363d;background-color:#161b22}.dark .explore-hero{background-image:radial-gradient(circle at 88% 15%,rgba(56,139,253,.18) 0,transparent 27%),linear-gradient(135deg,#161b22,#0d1117)}.dark .explore-hero h1,.dark .explore-repo-card h3,.dark .explore-state-card h2,.dark .explore-state-card h3,.dark .explore-resource-tabs button.active{color:#f0f6fc}.dark .explore-hero__content>p:not(.eyebrow),.dark .explore-section-heading p,.dark .repo-description,.dark .repo-owner,.dark .repo-meta,.dark .repo-footer,.dark .explore-section-heading>span,.dark .explore-hero__stats span{color:#8b949e}.dark .explore-search-box input{color:#f0f6fc}.dark .explore-search-submit,.dark .explore-nav-link:hover,.dark .explore-nav-link--active,.dark .repo-more:hover{border-color:#30363d;background:#21262d;color:#c9d1d9}.dark .repo-footer,.dark .explore-content-section--all,.dark .explore-toolbar,.dark .explore-hero__stats div{border-color:#30363d}
</style>
