<template>
  <div class="repository-shell min-h-screen bg-[#f6f8fa] text-[#1f2328]">
    <header class="repository-header border-b border-[#d0d7de] bg-white">
      <div class="mx-auto flex max-w-[1440px] flex-wrap items-center gap-3 px-4 py-4 lg:px-8">
        <!-- [REQ-PUBLIC-REPOSITORY-3.1/3.25] 使用确定的控制台、探索入口替代仅依赖浏览器历史的返回按钮。 -->
        <router-link class="repo-icon-button inline-flex items-center justify-center" to="/app" aria-label="返回控制面板"><i class="fa fa-cube"></i></router-link>
        <nav class="order-3 flex w-full items-center gap-1 overflow-x-auto text-xs sm:order-none sm:w-auto sm:text-sm" aria-label="公开仓库导航">
          <router-link class="rounded-md px-2.5 py-1.5 text-[#57606a] hover:bg-[#f6f8fa] hover:text-[#0969da]" to="/app"><i class="fa fa-arrow-left mr-1"></i>我的网盘</router-link>
          <router-link class="rounded-md px-2.5 py-1.5 text-[#57606a] hover:bg-[#f6f8fa] hover:text-[#0969da]" to="/explore">探索仓库</router-link>
        </nav>
        <button class="owner-avatar" :title="`进入 ${repository.ownerName} 的主页`" @click="router.push(`/user/${encodeURIComponent(repository.ownerName)}`)">
          <img v-if="repository.ownerAvatar" :src="repository.ownerAvatar" alt="" />
          <span v-else>{{ repository.ownerName?.slice(0, 1) || '?' }}</span>
        </button>
        <div class="min-w-0 flex-1">
          <div class="flex flex-wrap items-center gap-2 text-sm text-[#57606a]">
            <button class="font-semibold text-[#0969da] hover:underline" @click="router.push(`/user/${encodeURIComponent(repository.ownerName)}`)">{{ repository.ownerName }}</button>
            <span>/</span>
            <h1 class="truncate text-base font-semibold text-[#1f2328] sm:text-lg">{{ repository.spaceName }}</h1>
            <span class="public-badge">Public</span>
          </div>
          <p class="mt-1 text-xs text-[#57606a]">更新于 {{ formatDate(repository.updatedAt) }}</p>
        </div>
        <div class="flex items-center gap-2">
          <button class="repo-action-button" title="即将支持" disabled><i class="fa fa-star-o mr-1"></i>Star</button>
          <button class="repo-action-button" title="即将支持" disabled><i class="fa fa-code-fork mr-1"></i>Fork</button>
          <button v-if="isOwner" class="repo-action-button primary" @click="router.push(`/repo/${spaceId}/settings`)"><i class="fa fa-cog mr-1"></i>设置</button>
          <button v-if="authStore.user && !isOwner" class="repo-action-button" @click="router.push(`/repo/${spaceId}/settings`)"><i class="fa fa-key mr-1"></i>Git 凭证</button>
        </div>
      </div>
    </header>

    <main class="mx-auto max-w-[1440px] px-4 py-5 lg:px-8">
      <div v-if="pageError" class="mb-5 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
        {{ pageError }}
        <button class="ml-3 underline" @click="reloadPage">重新加载</button>
      </div>
      <GitRepositoryPanel
        v-if="repository.resourceType === 'git'"
        :space-id="spaceId"
        :space-name="repository.spaceName"
        :description="repository.description"
        :is-owner="isOwner"
      />
      <template v-else>
      <nav class="repo-tabs mb-5" aria-label="仓库导航">
        <button :class="{ active: activeTab === 'files' }" @click="activeTab = 'files'"><i class="fa fa-code-fork mr-2"></i>文件</button>
        <button :class="{ active: activeTab === 'readme' }" @click="activeTab = 'readme'; loadReadme()"><i class="fa fa-book mr-2"></i>README</button>
        <button v-if="isOwner" @click="router.push(`/repo/${spaceId}/settings`)"><i class="fa fa-cog mr-2"></i>设置</button>
      </nav>

      <div class="grid gap-5 xl:grid-cols-[minmax(0,1fr)_300px]">
        <section class="min-w-0">
          <div v-if="activeTab === 'files'" class="repo-files-layout">
            <details class="repo-tree-mobile repo-card mb-3 p-3 md:hidden">
              <summary class="cursor-pointer text-sm font-semibold text-[#57606a]">展开目录树</summary>
              <div class="mt-2">
                <button
                  v-for="entry in treeEntries"
                  :key="`mobile-${entry.node.id}`"
                  class="repo-tree-item"
                  :class="{ active: currentNode?.id === entry.node.id }"
                  :style="{ paddingLeft: `${8 + entry.depth * 16}px` }"
                  @click="entry.node.type === 'folder' && toggleTreeFolder(entry.node)"
                >
                  <i :class="expandedTreeIds.includes(entry.node.id) ? 'fa fa-folder-open-o' : 'fa fa-folder-o'"></i>
                  <span class="truncate">{{ entry.node.name }}</span>
                </button>
              </div>
            </details>
            <aside class="repo-tree repo-card hidden h-fit p-3 md:block" aria-label="目录树">
              <h2 class="px-2 pb-2 text-xs font-semibold uppercase tracking-wide text-[#57606a]">目录</h2>
              <button
                v-for="entry in treeEntries"
                :key="entry.node.id"
                class="repo-tree-item"
                :class="{ active: currentNode?.id === entry.node.id }"
                :style="{ paddingLeft: `${8 + entry.depth * 16}px` }"
                @click="entry.node.type === 'folder' && toggleTreeFolder(entry.node)"
              >
                <i :class="expandedTreeIds.includes(entry.node.id) ? 'fa fa-folder-open-o' : 'fa fa-folder-o'"></i>
                <span class="truncate">{{ entry.node.name }}</span>
              </button>
            </aside>
            <div class="min-w-0">
            <div class="repo-card overflow-hidden">
            <div class="flex flex-wrap items-center gap-2 border-b border-[#d0d7de] bg-[#f6f8fa] px-4 py-3">
              <div class="flex min-w-0 flex-1 flex-wrap items-center gap-1 text-sm">
                <button class="font-semibold text-[#0969da] hover:underline" @click="openDirectory(rootNode)">{{ repository.spaceName }}</button>
                <template v-for="(crumb, index) in breadcrumbs" :key="crumb.id">
                  <span class="text-[#57606a]">/</span>
                  <button class="max-w-[200px] truncate text-[#0969da] hover:underline" @click="openDirectory(crumb)">{{ crumb.name }}</button>
                </template>
              </div>
              <label v-if="repository.allowPublicUpload" class="repo-action-button primary cursor-pointer">
                <i class="fa fa-upload mr-1"></i>上传文件
                <input class="hidden" type="file" @change="onUploadSelected" />
              </label>
              <span v-if="uploadMessage" class="text-xs text-[#57606a]">{{ uploadMessage }}</span>
            </div>
            <div v-if="loadingNodes" class="space-y-3 p-5"><div v-for="i in 5" :key="i" class="h-10 animate-pulse rounded bg-[#eaeef2]"></div></div>
            <div v-else-if="nodes.length === 0" class="empty-repository"><i class="fa fa-folder-open-o"></i><p>此目录暂无文件</p></div>
            <div v-else class="divide-y divide-[#d8dee4]">
              <div v-for="node in nodes" :key="node.id" class="repo-row group">
                <button class="flex min-w-0 flex-1 items-center gap-3 text-left" @click="handleNode(node)">
                  <i :class="node.type === 'folder' ? 'fa fa-folder text-[#54aeff]' : fileIcon(node.name)" class="w-5 shrink-0 text-base"></i>
                  <span class="min-w-0 truncate text-sm font-semibold text-[#0969da] group-hover:underline">{{ node.name }}</span>
                </button>
                <span class="hidden w-28 text-right text-xs text-[#57606a] sm:block">{{ node.type === 'file' ? formatSize(node.size || 0) : '目录' }}</span>
                <span class="hidden w-36 text-right text-xs text-[#57606a] lg:block">{{ formatDate(node.updatedAt) }}</span>
                <button v-if="node.type === 'file' && repository.allowPublicDownload" class="repo-row-action" title="下载" @click.stop="downloadNode(node)"><i class="fa fa-download"></i></button>
              </div>
            </div>
            </div>
            </div>
          </div>

          <div v-else-if="activeTab === 'readme'" class="repo-card p-5 sm:p-8">
            <div v-if="readmeLoading" class="space-y-3"><div class="h-6 w-1/3 animate-pulse rounded bg-[#eaeef2]"></div><div class="h-4 w-full animate-pulse rounded bg-[#eaeef2]"></div><div class="h-4 w-5/6 animate-pulse rounded bg-[#eaeef2]"></div></div>
            <template v-else-if="readmeFileId">
              <MarkdownPreview :markdown-content="readmeContent" :file-name="`${repository.spaceName} README.md`" :loading="false" />
            </template>
            <div v-else class="empty-repository"><i class="fa fa-book"></i><p>该仓库暂无 README 文件</p><span>所有者可以在根目录添加 README.md</span></div>
          </div>
        </section>

        <aside class="repo-card h-fit p-5">
          <h2 class="text-sm font-semibold">关于</h2>
          <p class="mt-2 whitespace-pre-wrap text-sm leading-6 text-[#57606a]">{{ repository.description || '暂无仓库描述' }}</p>
          <div class="my-4 border-t border-[#d8dee4]"></div>
          <h2 class="text-sm font-semibold">权限概览</h2>
          <dl class="mt-3 space-y-2 text-xs text-[#57606a]">
            <div class="flex justify-between"><dt>公开浏览</dt><dd>{{ repository.allowPublicBrowse ? '允许' : '关闭' }}</dd></div>
            <div class="flex justify-between"><dt>公开下载</dt><dd>{{ repository.allowPublicDownload ? '允许' : '关闭' }}</dd></div>
            <div class="flex justify-between"><dt>公开上传</dt><dd>{{ repository.allowPublicUpload ? '允许登录用户上传' : '关闭' }}</dd></div>
          </dl>
          <div class="my-4 border-t border-[#d8dee4]"></div>
          <h2 class="text-sm font-semibold">统计</h2>
          <p class="mt-2 text-xs text-[#57606a]">{{ repository.fileCount || 0 }} 个文件 · {{ formatSize(repository.usedBytes || 0) }}</p>
          <p class="mt-1 text-xs text-[#57606a]">创建于 {{ formatDate(repository.createdAt) }}</p>
        </aside>
      </div>
      </template>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import MarkdownPreview from '@/components/preview/MarkdownPreview.vue'
import GitRepositoryPanel from './GitRepositoryPanel.vue'
import { fetchPreviewContentBlob } from '@/api/modules/previewContent'
import { getPublicSpaceApi, getPublicSpaceChildrenApi, getPublicSpaceReadmeApi, getPublicSpaceRootApi, type PublicSpaceDetail, type PublicSpaceNode } from '@/api/modules/publicSpaces'
import { createPublicUploadSessionApi } from '@/api/modules/publicSpaces'
import { uploadFileChunkApi, completeUploadSessionApi } from '@/api/modules/uploads'
import { createDownloadGrantApi, getFileContentApi } from '@/api/modules/downloads'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const spaceId = String(route.params.spaceId)
const repository = ref<PublicSpaceDetail>({ spaceId, spaceName: '加载中…', ownerId: '', ownerName: '', allowPublicBrowse: true, allowPublicDownload: true, allowPublicUpload: false, fileCount: 0, usedBytes: 0, resourceType: 'file' })
const activeTab = ref<'files' | 'readme'>('files')
const rootNode = ref<PublicSpaceNode | null>(null)
const currentNode = ref<PublicSpaceNode | null>(null)
const nodes = ref<PublicSpaceNode[]>([])
const treeChildren = ref<Record<string, PublicSpaceNode[]>>({})
const expandedTreeIds = ref<string[]>([])
const breadcrumbs = ref<PublicSpaceNode[]>([])
const loadingNodes = ref(true)
const readmeLoading = ref(false)
const readmeFileId = ref<string | null>(null)
const readmeContent = ref('')
const uploadMessage = ref('')
const pageError = ref('')

const isOwner = computed(() => repository.value.ownerId === authStore.user?.id
  || (repository.value.ownerName && (repository.value.ownerName === authStore.user?.name || repository.value.ownerName === authStore.user?.account)))
const treeEntries = computed(() => {
  const result: Array<{ node: PublicSpaceNode; depth: number }> = []
  const walk = (node: PublicSpaceNode, depth: number) => {
    result.push({ node, depth })
    if (!expandedTreeIds.value.includes(node.id)) return
    for (const child of (treeChildren.value[node.id] || []).filter(item => item.type === 'folder')) walk(child, depth + 1)
  }
  if (rootNode.value) walk(rootNode.value, 0)
  return result
})

onMounted(async () => {
  try {
    await authStore.fetchUserInfo()
    const detail = await getPublicSpaceApi(spaceId)
    repository.value = detail.data
    if (detail.data.resourceType !== 'git' && detail.data.allowPublicBrowse) {
      const rootResponse = await getPublicSpaceRootApi(spaceId)
      rootNode.value = rootResponse.data
      await openDirectory(rootResponse.data)
    }
  } catch (cause: any) {
    pageError.value = cause?.message || '仓库加载失败，请稍后重试'
  } finally {
    loadingNodes.value = false
  }
})

async function openDirectory(node: PublicSpaceNode | null) {
  if (!node) return
  loadingNodes.value = true
  try {
    const result = await getPublicSpaceChildrenApi(spaceId, node.id)
    currentNode.value = node
    nodes.value = result.data || []
    treeChildren.value[node.id] = result.data || []
    if (!expandedTreeIds.value.includes(node.id)) expandedTreeIds.value = [...expandedTreeIds.value, node.id]
    if (rootNode.value?.id === node.id) breadcrumbs.value = []
    else if (!breadcrumbs.value.some(item => item.id === node.id)) breadcrumbs.value = [...breadcrumbs.value, node]
  } finally { loadingNodes.value = false }
}

function toggleTreeFolder(node: PublicSpaceNode) {
  if (expandedTreeIds.value.includes(node.id)) {
    expandedTreeIds.value = expandedTreeIds.value.filter(id => id !== node.id)
    return
  }
  if (treeChildren.value[node.id]) {
    expandedTreeIds.value = [...expandedTreeIds.value, node.id]
  } else {
    void openDirectory(node)
  }
}

function reloadPage() {
  window.location.reload()
}

function handleNode(node: PublicSpaceNode) {
  if (node.type === 'folder') return void openDirectory(node)
  const extension = node.name.split('.').pop()?.toLowerCase()
  const previewMap: Record<string, string> = { md: 'markdown', markdown: 'markdown', js: 'code', ts: 'code', py: 'code', java: 'code', txt: 'code', json: 'code', png: 'image', jpg: 'image', jpeg: 'image', gif: 'image', pdf: 'pdf' }
  const target = previewMap[extension || '']
  if (target) router.push({ path: `/preview/${target}/${node.id}`, query: { space: spaceId, name: node.name } })
}

async function loadReadme() {
  if (readmeFileId.value || readmeLoading.value) return
  readmeLoading.value = true
  try {
    const response = await getPublicSpaceReadmeApi(spaceId)
    readmeFileId.value = response.data
    if (readmeFileId.value) {
      const blob = await fetchPreviewContentBlob(readmeFileId.value, spaceId)
      readmeContent.value = await blob.text()
    }
  } finally { readmeLoading.value = false }
}

async function downloadNode(node: PublicSpaceNode) {
  if (!repository.value.allowPublicDownload) return
  const grant = await createDownloadGrantApi(node.id, spaceId)
  const downloadGrant = grant.data?.download_grant || grant.data
  const blob = await getFileContentApi(node.id, downloadGrant, undefined, spaceId)
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement('a'); anchor.href = url; anchor.download = node.name; anchor.click(); URL.revokeObjectURL(url)
}

async function onUploadSelected(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !currentNode.value) return
  uploadMessage.value = '上传中…'
  try {
    const chunkSize = 5 * 1024 * 1024
    const totalChunks = Math.max(1, Math.ceil(file.size / chunkSize))
    const checksum = await sha256(file)
    const session = await createPublicUploadSessionApi(spaceId, {
      total_chunks: totalChunks, file_size: file.size, file_checksum: checksum,
      chunks_max_size: chunkSize, file_type: file.type || 'application/octet-stream',
      file_name: file.name, node_id: currentNode.value.id,
    })
    // 兼容旧服务返回字符串和新服务返回会话并发快照对象。
    const uploadsId = typeof session.data === 'string' ? session.data : session.data?.uploads_id
    if (!uploadsId) throw new Error('上传会话创建失败')
    for (let index = 0; index < totalChunks; index += 1) {
      await uploadFileChunkApi(uploadsId, index + 1, file.slice(index * chunkSize, Math.min(file.size, (index + 1) * chunkSize)) as File)
    }
    await completeUploadSessionApi(uploadsId)
    uploadMessage.value = '上传已提交，后台处理完成后刷新目录'
    await openDirectory(currentNode.value)
  } catch (cause: any) { uploadMessage.value = cause?.message || '上传失败，请稍后重试' }
  finally { (event.target as HTMLInputElement).value = '' }
}
async function sha256(file: File) { const buffer = await crypto.subtle.digest('SHA-256', await file.arrayBuffer()); return Array.from(new Uint8Array(buffer)).map(value => value.toString(16).padStart(2, '0')).join('') }
function fileIcon(name: string) { const ext = name.split('.').pop()?.toLowerCase(); return ext === 'md' ? 'fa fa-file-text-o text-[#8250df]' : 'fa fa-file-o text-[#57606a]' }
function formatSize(bytes: number) { if (!bytes) return '0 B'; const units = ['B', 'KB', 'MB', 'GB', 'TB']; const i = Math.floor(Math.log(bytes) / Math.log(1024)); return `${(bytes / 1024 ** i).toFixed(i ? 1 : 0)} ${units[i]}` }
function formatDate(value?: string | null) { return value ? new Date(value).toLocaleDateString('zh-CN') : '—' }
</script>

<style scoped>
.repository-shell { min-height: 100vh; }
.repo-card { border: 1px solid #d0d7de; border-radius: 8px; background: #fff; box-shadow: 0 1px 2px rgba(27,31,36,.04); }
.repo-files-layout { display:grid; grid-template-columns:minmax(180px,220px) minmax(0,1fr); gap:12px; }
.repo-tree-item { display:flex; align-items:center; gap:8px; width:100%; min-height:34px; border-radius:5px; padding-top:5px; padding-bottom:5px; color:#57606a; font-size:13px; text-align:left; }
.repo-tree-item:hover,.repo-tree-item.active { background:#ddf4ff; color:#0969da; }
.repo-icon-button,.repo-row-action { color:#57606a; transition:color .15s,background .15s; }
.repo-icon-button { width:36px;height:36px;border-radius:6px; }
.repo-icon-button:hover,.repo-row-action:hover { background:#f6f8fa;color:#0969da; }
.owner-avatar { width:36px;height:36px;border-radius:50%;overflow:hidden;background:#ddf4ff;color:#0969da;font-weight:700;display:flex;align-items:center;justify-content:center; }
.owner-avatar img { width:100%;height:100%;object-fit:cover; }
.public-badge { border:1px solid #d0d7de;border-radius:999px;padding:1px 7px;font-size:12px;color:#57606a; }
.repo-action-button { min-height:32px;border:1px solid #d0d7de;border-radius:6px;background:#f6f8fa;padding:0 12px;font-size:12px;color:#24292f;transition:.15s; }
.repo-action-button:hover:not(:disabled) { background:#f3f4f6;border-color:#afb8c1; }
.repo-action-button.primary { background:#2da44e;border-color:#2da44e;color:#fff; }
.repo-tabs { display:flex;gap:4px;border-bottom:1px solid #d0d7de; }
.repo-tabs button { border-bottom:2px solid transparent;padding:9px 14px;font-size:14px;color:#57606a; }
.repo-tabs button.active { border-bottom-color:#fd8c73;color:#24292f;font-weight:600; }
.repo-row { display:flex;align-items:center;gap:10px;min-height:52px;padding:8px 16px;transition:background .15s; }
.repo-row:hover { background:#f6f8fa; }
.empty-repository { display:flex;flex-direction:column;align-items:center;justify-content:center;min-height:240px;color:#57606a;gap:8px;font-size:14px; }
.empty-repository i { font-size:32px;color:#8c959f; }
@media (max-width: 767px) { .repo-files-layout { display:block; } .repo-action-button { padding:0 8px; } .repo-row { padding:8px 12px; } }
</style>
