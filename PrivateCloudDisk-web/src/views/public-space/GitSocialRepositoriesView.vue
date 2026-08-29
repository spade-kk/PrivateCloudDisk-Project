<template>
  <main class="git-social-page" :class="{ 'dark-mode': isDark }">
    <header class="git-social-page__topbar">
      <button type="button" class="git-social-page__back" @click="router.push('/app')"><i class="fa fa-arrow-left"></i>返回控制面板</button>
      <div class="git-social-page__brand"><i class="fa fa-code-fork"></i><span>Git 资源</span></div>
      <button type="button" class="git-social-page__home" @click="router.push('/explore')"><i class="fa fa-compass"></i>探索公开仓库</button>
    </header>
    <section class="git-social-page__hero">
      <span class="git-social-page__eyebrow">YOUR GIT ACTIVITY</span>
      <h1>我的 Star 与 Fork</h1>
      <p>集中管理你关注和派生的公开空间 Git 资源。Star 只保存当前用户关系，Fork 是独立的仓库副本。</p>
      <div class="git-social-page__tabs" role="tablist" aria-label="我的 Git 资源">
        <button type="button" role="tab" :aria-selected="activeTab === 'stars'" :class="{ active: activeTab === 'stars' }" @click="switchTab('stars')"><i class="fa fa-star"></i>我的 Star <b>{{ stars.length }}</b></button>
        <button type="button" role="tab" :aria-selected="activeTab === 'forks'" :class="{ active: activeTab === 'forks' }" @click="switchTab('forks')"><i class="fa fa-code-fork"></i>我的 Fork <b>{{ forks.length }}</b></button>
      </div>
    </section>
    <section class="git-social-page__content" aria-live="polite">
      <div v-if="loading" class="git-social-page__skeleton"><span v-for="index in 6" :key="index" :style="{ width: `${58 + (index % 3) * 12}%` }"></span></div>
      <div v-else-if="error" class="git-social-page__error"><i class="fa fa-exclamation-triangle"></i><p>{{ error }}</p><button type="button" @click="load">重新加载</button></div>
      <div v-else-if="!items.length" class="git-social-page__empty"><i :class="activeTab === 'stars' ? 'fa fa-star-o' : 'fa fa-code-fork'"></i><h2>{{ activeTab === 'stars' ? '还没有 Star 仓库' : '还没有 Fork 仓库' }}</h2><p>{{ activeTab === 'stars' ? '在探索页发现喜欢的代码仓库后，Star 它以便稍后回访。' : 'Fork 一个公开 Git 仓库，把它复制到自己的 Git 空间。' }}</p><button type="button" @click="router.push('/explore')">去探索公开仓库</button></div>
      <div v-else class="git-social-page__grid">
        <article v-for="item in items" :key="item.repoId" class="git-social-card">
          <div class="git-social-card__icon"><i class="fa fa-code-fork"></i></div>
          <div class="git-social-card__body">
            <div class="git-social-card__title"><button type="button" @click="openRepository(item)">{{ item.name }}</button><span>{{ item.visibility }}</span></div>
            <p>{{ item.description || '暂无仓库描述' }}</p>
            <div class="git-social-card__meta"><span><i class="fa fa-code-fork"></i>{{ item.defaultBranch }}</span><span><i class="fa fa-star"></i>{{ item.starCount || 0 }}</span><span><i class="fa fa-code-fork"></i>{{ item.forkCount || 0 }}</span><span>{{ item.hashAlgorithm.toUpperCase() }}</span></div>
          </div>
          <div class="git-social-card__actions"><button type="button" @click="openRepository(item)">进入仓库</button><button v-if="activeTab === 'stars'" type="button" class="is-muted" :disabled="busyId === item.repoId" @click="unstar(item)"><i class="fa fa-star"></i>取消 Star</button><button v-else type="button" class="is-danger" :disabled="busyId === item.repoId" @click="removeFork(item)"><i class="fa fa-trash"></i>删除 Fork</button></div>
        </article>
      </div>
      <footer v-if="items.length && !loading" class="git-social-page__footer"><button type="button" :disabled="page === 1" @click="changePage(page - 1)">上一页</button><span>第 {{ page }} 页</span><button type="button" :disabled="items.length < pageSize" @click="changePage(page + 1)">下一页</button></footer>
    </section>
  </main>
</template>

<script setup lang="ts">
// AUDIT FIX [3.16-3.18] Star/Fork API now has a user-facing list, pagination and
// safe self-service actions instead of leaving the new backend endpoints orphaned.
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { deleteGitRepositoryApi, listMyGitForksApi, listMyGitStarsApi, unstarGitRepositoryApi, type GitRepository } from '@/api/modules/git'
import { useToastStore } from '@/stores/toastStore'

const router = useRouter()
const toast = useToastStore()
const isDark = ref(document.documentElement.classList.contains('dark'))
const activeTab = ref<'stars' | 'forks'>('stars')
const stars = ref<GitRepository[]>([])
const forks = ref<GitRepository[]>([])
const loading = ref(false)
const error = ref('')
const page = ref(1)
const pageSize = 30
const busyId = ref('')
const items = computed(() => activeTab.value === 'stars' ? stars.value : forks.value)

onMounted(load)

function switchTab(tab: 'stars' | 'forks'): void { if (activeTab.value === tab) return; activeTab.value = tab; page.value = 1; void load() }
async function load(): Promise<void> {
  loading.value = true; error.value = ''
  try {
    const result = activeTab.value === 'stars' ? await listMyGitStarsApi(page.value, pageSize) : await listMyGitForksApi(page.value, pageSize)
    if (activeTab.value === 'stars') stars.value = result.data || []; else forks.value = result.data || []
  } catch (cause: any) { error.value = cause?.message || 'Git 社交列表加载失败' } finally { loading.value = false }
}
function changePage(value: number): void { if (value < 1) return; page.value = value; void load() }
function openRepository(item: GitRepository): void { void router.push(`/repo/${encodeURIComponent(item.spaceId)}`) }
async function unstar(item: GitRepository): Promise<void> { busyId.value = item.repoId; try { await unstarGitRepositoryApi(item.repoId); stars.value = stars.value.filter((entry) => entry.repoId !== item.repoId); toast.showToast('已取消 Star', 'success') } catch (cause: any) { toast.showToast(cause?.message || '取消 Star 失败', 'error') } finally { busyId.value = '' } }
async function removeFork(item: GitRepository): Promise<void> { if (!window.confirm(`确定删除 Fork 仓库“${item.name}”吗？`)) return; busyId.value = item.repoId; try { await deleteGitRepositoryApi(item.repoId); forks.value = forks.value.filter((entry) => entry.repoId !== item.repoId); toast.showToast('Fork 仓库已删除', 'success') } catch (cause: any) { toast.showToast(cause?.message || '删除 Fork 失败', 'error') } finally { busyId.value = '' } }
</script>

<style scoped>
.git-social-page { --git-panel:#fff; --git-subtle:#f6f8fa; --git-text:#24292f; --git-muted:#57606a; --git-border:#d0d7de; min-height:100vh; background:#f6f8fa; color:var(--git-text); }
.git-social-page__topbar { display:flex; height:58px; align-items:center; gap:20px; border-bottom:1px solid var(--git-border); background:var(--git-panel); padding:0 max(20px,calc((100vw - 1180px) / 2)); }
.git-social-page__topbar button { border:0; background:transparent; color:var(--git-muted); font-size:12px; }.git-social-page__topbar button:hover { color:#0969da; }.git-social-page__brand { display:flex; align-items:center; gap:8px; color:var(--git-text); font-size:14px; font-weight:700; }.git-social-page__brand i { color:#0969da; }.git-social-page__home { margin-left:auto; }
.git-social-page__hero,.git-social-page__content { width:min(1180px,calc(100% - 32px)); margin:0 auto; }.git-social-page__hero { padding:52px 0 24px; }.git-social-page__eyebrow { color:#0969da; font-size:11px; font-weight:800; letter-spacing:.12em; }.git-social-page__hero h1 { margin:8px 0 6px; font-size:32px; letter-spacing:-.03em; }.git-social-page__hero p { max-width:660px; margin:0; color:var(--git-muted); font-size:13px; line-height:1.7; }.git-social-page__tabs { display:flex; gap:4px; margin-top:30px; border-bottom:1px solid var(--git-border); }.git-social-page__tabs button { display:inline-flex; align-items:center; gap:7px; border:0; border-bottom:2px solid transparent; background:transparent; padding:12px 15px; color:var(--git-muted); font-size:13px; }.git-social-page__tabs button.active { border-bottom-color:#fd8c73; color:var(--git-text); font-weight:700; }.git-social-page__tabs b { border-radius:999px; background:var(--git-subtle); padding:2px 7px; font-size:10px; }
.git-social-page__content { min-height:440px; padding-bottom:45px; }.git-social-page__grid { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; }.git-social-card { display:flex; min-width:0; flex-wrap:wrap; gap:12px; border:1px solid var(--git-border); border-radius:8px; background:var(--git-panel); padding:17px; }.git-social-card__icon { display:flex; width:38px; height:38px; flex:0 0 38px; align-items:center; justify-content:center; border-radius:9px; background:#ddf4ff; color:#0969da; }.git-social-card__body { min-width:0; flex:1; }.git-social-card__title { display:flex; align-items:center; gap:8px; }.git-social-card__title button { overflow:hidden; border:0; background:transparent; padding:0; color:#0969da; font-size:15px; font-weight:700; text-overflow:ellipsis; white-space:nowrap; }.git-social-card__title span { border:1px solid var(--git-border); border-radius:999px; padding:2px 6px; color:var(--git-muted); font-size:9px; }.git-social-card__body p { overflow:hidden; min-height:39px; margin:8px 0; color:var(--git-muted); font-size:12px; line-height:1.6; display:-webkit-box; -webkit-line-clamp:2; -webkit-box-orient:vertical; }.git-social-card__meta { display:flex; flex-wrap:wrap; gap:11px; color:var(--git-muted); font-size:10px; }.git-social-card__meta i { margin-right:4px; color:#0969da; }.git-social-card__actions { display:flex; width:100%; justify-content:flex-end; gap:7px; border-top:1px solid var(--git-border); padding-top:12px; }.git-social-card__actions button { border:1px solid var(--git-border); border-radius:6px; background:var(--git-panel); padding:6px 9px; color:var(--git-text); font-size:11px; }.git-social-card__actions button:hover { border-color:#0969da; color:#0969da; }.git-social-card__actions .is-muted { color:var(--git-muted); }.git-social-card__actions .is-danger { color:#cf222e; }.git-social-card__actions button:disabled { opacity:.55; cursor:not-allowed; }
.git-social-page__footer { display:flex; align-items:center; justify-content:center; gap:12px; margin-top:24px; color:var(--git-muted); font-size:12px; }.git-social-page__footer button,.git-social-page__empty button,.git-social-page__error button { border:1px solid var(--git-border); border-radius:6px; background:var(--git-panel); padding:7px 12px; color:var(--git-text); font-size:12px; }.git-social-page__footer button:hover,.git-social-page__empty button:hover,.git-social-page__error button:hover { border-color:#0969da; color:#0969da; }.git-social-page__footer button:disabled { opacity:.45; cursor:not-allowed; }.git-social-page__empty,.git-social-page__error { display:flex; min-height:380px; flex-direction:column; align-items:center; justify-content:center; gap:10px; color:var(--git-muted); text-align:center; }.git-social-page__empty i,.git-social-page__error i { color:#0969da; font-size:34px; }.git-social-page__empty h2 { margin:0; color:var(--git-text); font-size:18px; }.git-social-page__empty p { max-width:420px; margin:0 0 8px; line-height:1.7; }.git-social-page__error { color:#cf222e; }.git-social-page__skeleton { display:grid; grid-template-columns:repeat(2,minmax(0,1fr)); gap:14px; }.git-social-page__skeleton span { display:block; height:150px; border-radius:8px; background:linear-gradient(90deg,#eaeef2 25%,#fff 50%,#eaeef2 75%); background-size:200% 100%; animation:git-social-loading 1.2s infinite; }
.dark-mode { --git-panel:#0d1117; --git-subtle:#161b22; --git-text:#c9d1d9; --git-muted:#8b949e; --git-border:#30363d; background:#010409; }.dark-mode .git-social-card__icon { background:#1f6feb44; color:#58a6ff; }.dark-mode .git-social-page__skeleton span { background:linear-gradient(90deg,#161b22 25%,#21262d 50%,#161b22 75%); background-size:200% 100%; }.dark-mode .git-social-page__tabs b { background:#21262d; }
@keyframes git-social-loading { to { background-position:-200% 0; } }
@media (max-width:767px) { .git-social-page__topbar { gap:10px; padding:0 12px; }.git-social-page__back { font-size:0!important; }.git-social-page__back i { margin:0; font-size:14px; }.git-social-page__home { font-size:0!important; }.git-social-page__home i { font-size:14px; }.git-social-page__hero,.git-social-page__content { width:calc(100% - 22px); }.git-social-page__hero { padding-top:34px; }.git-social-page__hero h1 { font-size:26px; }.git-social-page__grid,.git-social-page__skeleton { grid-template-columns:1fr; }.git-social-card { padding:13px; } }
</style>
