<template>
  <div id="pcd-public-share-page" class="share-page share-main-page">
    <div class="page-glow page-glow--one" aria-hidden="true"></div>
    <div class="page-glow page-glow--two" aria-hidden="true"></div>

    <header class="share-header">
      <div class="header-inner">
        <router-link to="/" class="brand-link" aria-label="PrivateCloudDisk 首页">
          <span class="brand-mark"><i class="fa fa-cloud" aria-hidden="true"></i></span>
          <span class="brand-name">PrivateCloudDisk</span>
        </router-link>

        <nav class="header-actions" aria-label="账户操作">
          <template v-if="authStore.isLoggedIn">
            <router-link to="/app" class="dashboard-link">
              <i class="fa fa-th-large" aria-hidden="true"></i>
              <span>进入控制面板</span>
            </router-link>
            <UserDropdown />
          </template>
          <template v-else>
            <router-link to="/login" class="header-link header-link--quiet">登录</router-link>
            <router-link to="/register" class="header-link header-link--primary">注册</router-link>
          </template>
        </nav>
      </div>
    </header>

    <main class="share-main">
      <div class="share-shell">
        <section v-if="loading" class="state-card loading-state" aria-live="polite" aria-busy="true">
          <div class="loading-orbit" aria-hidden="true"><i class="fa fa-cloud"></i></div>
          <h1>正在打开安全分享</h1>
          <p>正在校验链接状态并加载资源信息，请稍候…</p>
          <div class="skeleton-lines" aria-hidden="true">
            <span></span><span></span><span></span>
          </div>
        </section>

        <section v-else-if="errorMessage" class="state-card error-state" role="alert">
          <span class="state-icon state-icon--danger"><i class="fa fa-link" aria-hidden="true"></i></span>
          <p class="state-kicker">链接暂时不可用</p>
          <h1>{{ errorMessage }}</h1>
          <p>{{ errorDetail }}</p>
          <div class="state-actions">
            <button type="button" class="button button--primary" @click="retryLoad">
              <i class="fa fa-refresh" aria-hidden="true"></i>重新加载
            </button>
            <button type="button" class="button button--secondary" @click="router.push('/')">返回首页</button>
          </div>
        </section>

        <section v-else-if="showPasswordScreen" class="password-layout" aria-labelledby="extract-title">
          <div class="password-visual" aria-hidden="true">
            <span class="visual-grid"></span>
            <span class="security-orb"><i class="fa fa-shield"></i></span>
            <div>
              <p>端到端访问保护</p>
              <strong>输入提取码后<br>安全查看分享内容</strong>
              <span>短期访问凭证 · 只读分享 · 到期自动失效</span>
            </div>
          </div>

          <div class="password-card">
            <span class="password-lock"><i class="fa fa-lock" aria-hidden="true"></i></span>
            <p class="state-kicker">受保护的分享</p>
            <h1 id="extract-title">{{ shareInfo?.share_name || '分享文件' }}</h1>
            <p class="password-owner">由 <strong>{{ shareInfo?.owner_name || '未知用户' }}</strong> 分享</p>

            <div class="password-meta" aria-label="分享概要">
              <span><i class="fa fa-files-o" aria-hidden="true"></i>{{ shareInfo?.resource_count || 0 }} 项资源</span>
              <span><i class="fa fa-clock-o" aria-hidden="true"></i>{{ expiryLabel }}</span>
            </div>

            <form class="extract-form" novalidate @submit.prevent="submitPassword">
              <label for="share-password">提取码</label>
              <div class="extract-controls" :class="{ 'extract-controls--error': passwordError }">
                <input
                  id="share-password"
                  ref="passwordInputElement"
                  v-model.trim="passwordInput"
                  type="text"
                  inputmode="text"
                  autocomplete="one-time-code"
                  autocapitalize="off"
                  spellcheck="false"
                  maxlength="20"
                  placeholder="请输入 4–20 位提取码"
                  :aria-invalid="Boolean(passwordError)"
                  aria-describedby="password-help password-error"
                  @input="passwordError = ''"
                />
                <button type="submit" :disabled="passwordVerifying || !passwordInput">
                  <span v-if="passwordVerifying" class="button-spinner" aria-hidden="true"></span>
                  <i v-else class="fa fa-unlock-alt" aria-hidden="true"></i>
                  {{ passwordVerifying ? '正在验证' : '提取文件' }}
                </button>
              </div>
              <p id="password-help" class="field-help">支持直接粘贴；字母区分大小写。</p>
              <p v-if="passwordError" id="password-error" class="field-error" role="alert">
                <i class="fa fa-exclamation-circle" aria-hidden="true"></i>{{ passwordError }}
              </p>
            </form>

            <div class="trust-note">
              <i class="fa fa-lock" aria-hidden="true"></i>
              <span>提取码仅用于本次分享验证，请勿在不可信页面输入账户密码。</span>
            </div>
          </div>
        </section>

        <template v-else-if="shareInfo">
          <section class="share-hero" aria-labelledby="share-title">
            <div class="hero-accent" aria-hidden="true"></div>
            <div class="hero-main">
              <div class="owner-avatar" aria-hidden="true">
                <span>{{ ownerInitial }}</span>
                <i class="fa fa-check-circle"></i>
              </div>
              <div class="hero-copy">
                <p class="hero-kicker"><i class="fa fa-share-alt" aria-hidden="true"></i>公开分享</p>
                <h1 id="share-title">{{ shareInfo.share_name || '分享内容' }}</h1>
                <p class="owner-line">
                  <strong>{{ shareInfo.owner_name || '未知用户' }}</strong>
                  <span>向你分享了以下内容</span>
                </p>
              </div>
            </div>

            <div class="hero-actions">
              <button type="button" class="copy-button" :class="{ 'copy-button--success': copySucceeded }" @click="copyShareLink">
                <i :class="copySucceeded ? 'fa fa-check' : 'fa fa-link'" aria-hidden="true"></i>
                <span>{{ copySucceeded ? '链接已复制' : '复制分享链接' }}</span>
              </button>
              <p class="copy-caption">{{ shareInfo.has_password ? '复制时将包含提取码' : '发送给需要访问的人' }}</p>
            </div>

            <dl class="hero-meta">
              <div>
                <dt><i class="fa fa-files-o" aria-hidden="true"></i>资源数量</dt>
                <dd>{{ shareInfo.resource_count || 0 }} 项</dd>
              </div>
              <div>
                <dt><i class="fa fa-calendar-o" aria-hidden="true"></i>创建时间</dt>
                <dd>{{ shareInfo.created_at ? formatDate(shareInfo.created_at) : '未记录' }}</dd>
              </div>
              <div>
                <dt><i class="fa fa-clock-o" aria-hidden="true"></i>有效期</dt>
                <dd :class="{ 'meta-warning': isExpiringSoon }">{{ expiryLabel }}</dd>
              </div>
              <div>
                <dt><i class="fa fa-shield" aria-hidden="true"></i>访问方式</dt>
                <dd>{{ shareInfo.has_password ? '提取码保护' : '链接访问' }}</dd>
              </div>
            </dl>
          </section>

          <div class="content-grid">
            <div class="content-primary">
              <div class="browser-grid" :class="{ 'browser-grid--with-tree': showFolderBrowser }">
                <ShareDirectoryNav
                  v-if="showFolderBrowser"
                  :breadcrumbs="folderBreadcrumb"
                  :children="folderSidebarChildren"
                  @close="closeFolderBrowser"
                  @navigate="navigateToFolderCrumb"
                  @child="navigateIntoChildFolder"
                />

                <section class="file-card" aria-labelledby="share-content-title">
                  <header class="file-toolbar">
                    <div class="toolbar-title">
                      <template v-if="showFolderBrowser">
                        <div class="breadcrumb-scroll" tabindex="0" aria-label="当前文件夹路径">
                          <button type="button" title="返回全部资源" @click="closeFolderBrowser">
                            <i class="fa fa-home" aria-hidden="true"></i><span class="sr-only">全部资源</span>
                          </button>
                          <template v-for="(crumb, index) in folderBreadcrumb" :key="crumb.id">
                            <i class="fa fa-angle-right breadcrumb-separator" aria-hidden="true"></i>
                            <button
                              type="button"
                              :class="{ 'breadcrumb-current': index === folderBreadcrumb.length - 1 }"
                              :disabled="index === folderBreadcrumb.length - 1"
                              @click="navigateToFolderCrumb(crumb)"
                            >
                              {{ crumb.name }}
                            </button>
                          </template>
                        </div>
                      </template>
                      <template v-else>
                        <div>
                          <p class="card-kicker">文件浏览</p>
                          <h2 id="share-content-title"><i class="fa fa-folder-open-o" aria-hidden="true"></i>分享内容</h2>
                        </div>
                      </template>
                    </div>
                    <span class="item-count">{{ currentList.length }} 项</span>
                  </header>

                  <div v-if="showFolderBrowser && folderContentsLoading" class="folder-loading" aria-live="polite">
                    <span class="loading-spinner" aria-hidden="true"></span>
                    <div><strong>正在打开文件夹</strong><p>目录内容马上就好…</p></div>
                  </div>

                  <div v-else-if="currentList.length === 0" class="empty-files">
                    <span><i class="fa fa-folder-open-o" aria-hidden="true"></i></span>
                    <h3>这个文件夹是空的</h3>
                    <p>分享者尚未在此目录放置文件。</p>
                  </div>

                  <div v-else class="file-table-wrap">
                    <table class="file-table">
                      <thead>
                        <tr>
                          <th scope="col">名称</th>
                          <th scope="col">大小</th>
                          <th scope="col">类型</th>
                          <th scope="col"><span class="sr-only">操作</span></th>
                        </tr>
                      </thead>
                      <tbody>
                        <tr
                          v-for="item in currentList"
                          :key="getItemId(item)"
                          :class="{ 'folder-row': isFolderItem(item) }"
                          @click="isFolderItem(item) ? handleItemClick(item) : previewSharedFileItem(getItemId(item))"
                          @keydown.enter="isFolderItem(item) ? handleItemClick(item) : previewSharedFileItem(getItemId(item))"
                        >
                          <td data-label="名称">
                            <div class="file-name-cell">
                              <span class="file-icon" :class="isFolderItem(item) ? 'file-icon--folder' : 'file-icon--file'">
                                <i :class="isFolderItem(item) ? 'fa fa-folder' : getFileIcon(item)" aria-hidden="true"></i>
                              </span>
                              <div>
                                <p class="file-name">{{ getItemName(item) }}</p>
                                <span class="mobile-file-meta">
                                  {{ isFolderItem(item) ? '文件夹' : `${formatSize(getItemSize(item))} · ${getItemFileType(item) || '文件'}` }}
                                </span>
                              </div>
                            </div>
                          </td>
                          <td data-label="大小">
                            <span v-if="!isFolderItem(item)">{{ formatSize(getItemSize(item)) }}</span>
                            <span v-else class="muted-value">—</span>
                          </td>
                          <td data-label="类型">
                            <span class="type-badge" :class="{ 'type-badge--folder': isFolderItem(item) }">
                              {{ isFolderItem(item) ? '文件夹' : getItemFileType(item) || '文件' }}
                            </span>
                          </td>
                          <td class="file-action-cell">
                            <button
                              v-if="!isFolderItem(item)"
                              type="button"
                              class="download-button"
                              :disabled="downloadingId === getItemId(item)"
                              :aria-label="`下载 ${getItemName(item)}`"
                              @click.stop="downloadSharedFileItem(getItemId(item))"
                            >
                              <span v-if="downloadingId === getItemId(item)" class="button-spinner button-spinner--blue"></span>
                              <i v-else class="fa fa-download" aria-hidden="true"></i>
                              <span>下载</span>
                            </button>
                            <button v-else type="button" class="folder-button" :aria-label="`打开文件夹 ${getItemName(item)}`" @click.stop="handleItemClick(item)">
                              <span>打开</span><i class="fa fa-angle-right" aria-hidden="true"></i>
                            </button>
                          </td>
                        </tr>
                      </tbody>
                    </table>
                  </div>
                </section>
              </div>
            </div>

            <ShareContextPanel
              class="context-rail"
              :description="shareInfo.share_description"
              :qr-url="shareUrl"
              :share-title="shareInfo.share_name"
            />
          </div>
        </template>
      </div>
    </main>

    <footer class="share-footer">
      <div>
        <span><i class="fa fa-cloud" aria-hidden="true"></i>PrivateCloudDisk</span>
        <p>安全、私密的个人云存储</p>
      </div>
      <nav aria-label="页脚链接">
        <router-link to="/security-center">安全中心</router-link>
        <router-link to="/privacy">隐私政策</router-link>
        <router-link to="/terms">服务条款</router-link>
      </nav>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import UserDropdown from '@/components/layout/UserDropdown.vue'
import ShareContextPanel from '@/components/share/ShareContextPanel.vue'
import ShareDirectoryNav from '@/components/share/ShareDirectoryNav.vue'
import { useAuthStore } from '@/stores/authStore'
import { useToastStore } from '@/stores/toastStore'
import { resolveShareBreadcrumb, type ShareBreadcrumbItem, type ShareDirectoryChild } from '@/utils/shareNavigation'
import {
  createShareDownloadGrantApi,
  createSharePreviewGrantApi,
  formatFileSize,
  getShareContentApi,
  getShareDownloadContentApi,
  getSharePreviewContentApi,
  getSharedFolderChildrenApi,
  getShareInfoApi,
  validatePassword,
  verifySharePasswordApi,
  type ShareAccessInfo,
  type ShareContentItem,
  type ShareResourceVO,
} from '@/api/modules/shares'
import { releaseDownloadGrantApi } from '@/api/modules/downloads'
import { releasePreviewGrantApi } from '@/api/modules/previewContent'

type ShareListItem = ShareResourceVO | ShareContentItem

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()
const shareToken = String(route.params.token || '')
const urlPassword = typeof route.query.pwd === 'string' ? route.query.pwd : ''

const loading = ref(true)
const errorMessage = ref('')
const errorDetail = ref('')
const showPasswordScreen = ref(false)
const shareInfo = ref<ShareAccessInfo | null>(null)
const resources = ref<ShareResourceVO[]>([])
const accessToken = ref('')
const passwordInput = ref(urlPassword)
const passwordInputElement = ref<HTMLInputElement | null>(null)
const passwordError = ref('')
const passwordVerifying = ref(false)
const copySucceeded = ref(false)
const downloadingId = ref('')
const showFolderBrowser = ref(false)
const folderContents = ref<ShareContentItem[]>([])
const folderContentsLoading = ref(false)
const folderBreadcrumb = ref<ShareBreadcrumbItem[]>([])

let tokenRefreshTimer: ReturnType<typeof setInterval> | null = null
let copyFeedbackTimer: ReturnType<typeof setTimeout> | null = null
const originalDocumentTitle = document.title
const originalMetaContents = new Map<string, string | null>()
const TOKEN_REFRESH_INTERVAL = 13 * 60 * 1000

const currentList = computed<ShareListItem[]>(() => showFolderBrowser.value ? folderContents.value : resources.value)
const folderSidebarChildren = computed<ShareDirectoryChild[]>(() => folderContents.value
  .filter((item) => item.item_type === 'folder')
  .map((item) => ({ id: item.share_resource_id, name: item.name })))
const ownerInitial = computed(() => (shareInfo.value?.owner_name?.trim().charAt(0) || '云').toUpperCase())
const expiryLabel = computed(() => shareInfo.value?.expires_at ? `有效至 ${formatDate(shareInfo.value.expires_at)}` : '长期有效')
const isExpiringSoon = computed(() => {
  if (!shareInfo.value?.expires_at) return false
  const remaining = new Date(shareInfo.value.expires_at).getTime() - Date.now()
  return remaining > 0 && remaining <= 3 * 24 * 60 * 60 * 1000
})
const shareUrl = computed(() => {
  const url = new URL(`/share/${encodeURIComponent(shareToken)}`, window.location.origin)
  const password = shareInfo.value?.has_password && accessToken.value
    ? (urlPassword || passwordInput.value).trim()
    : urlPassword.trim()
  if (password) url.searchParams.set('pwd', password)
  return url.toString()
})

function formatSize(bytes: number) {
  return formatFileSize(bytes)
}

function formatDate(dateStr: string) {
  const date = new Date(dateStr)
  if (Number.isNaN(date.getTime())) return '日期未知'
  return new Intl.DateTimeFormat('zh-CN', { year: 'numeric', month: 'short', day: 'numeric' }).format(date)
}

function isFolderItem(item: ShareListItem) {
  return ('resource_type' in item && item.resource_type === 'folder') || ('item_type' in item && item.item_type === 'folder')
}

function getItemId(item: ShareListItem) {
  return item.share_resource_id
}

function getItemName(item: ShareListItem) {
  return 'resource_name' in item ? item.resource_name : item.name
}

function getItemSize(item: ShareListItem) {
  return ('resource_size' in item ? item.resource_size : item.size) || 0
}

function getItemFileType(item: ShareListItem) {
  return item.file_type || ''
}

function getFileIcon(item: ShareListItem) {
  const name = getItemName(item)
  const extension = name.match(/\.([a-zA-Z0-9]+)$/)?.[1]?.toLowerCase() || getItemFileType(item).toLowerCase()
  const iconMap: Record<string, string> = {
    pdf: 'fa fa-file-pdf-o icon-danger', doc: 'fa fa-file-word-o icon-blue', docx: 'fa fa-file-word-o icon-blue',
    xls: 'fa fa-file-excel-o icon-green', xlsx: 'fa fa-file-excel-o icon-green', ppt: 'fa fa-file-powerpoint-o icon-orange',
    pptx: 'fa fa-file-powerpoint-o icon-orange', jpg: 'fa fa-file-image-o icon-cyan', jpeg: 'fa fa-file-image-o icon-cyan',
    png: 'fa fa-file-image-o icon-cyan', gif: 'fa fa-file-image-o icon-cyan', webp: 'fa fa-file-image-o icon-cyan',
    mp4: 'fa fa-file-video-o icon-purple', mov: 'fa fa-file-video-o icon-purple', mkv: 'fa fa-file-video-o icon-purple',
    mp3: 'fa fa-file-audio-o icon-pink', wav: 'fa fa-file-audio-o icon-pink', flac: 'fa fa-file-audio-o icon-pink',
    zip: 'fa fa-file-archive-o icon-amber', rar: 'fa fa-file-archive-o icon-amber', '7z': 'fa fa-file-archive-o icon-amber',
    txt: 'fa fa-file-text-o icon-slate', md: 'fa fa-file-text-o icon-slate', json: 'fa fa-file-code-o icon-teal',
    js: 'fa fa-file-code-o icon-amber', ts: 'fa fa-file-code-o icon-blue', py: 'fa fa-file-code-o icon-green',
    java: 'fa fa-file-code-o icon-orange', html: 'fa fa-file-code-o icon-orange', css: 'fa fa-file-code-o icon-blue',
  }
  return iconMap[extension] || 'fa fa-file-o icon-blue'
}

function getReadableError(error: unknown, fallback: string) {
  const message = error instanceof Error ? error.message : ''
  if (message.includes('429') || message.includes('频繁')) return '尝试次数过多，请稍后再试'
  if (message.includes('timeout') || message.includes('超时')) return '请求超时，请检查网络后重试'
  if (message.includes('提取码') || message.includes('密码')) return '提取码不正确，请检查后重试'
  return fallback
}

async function writeClipboard(text: string) {
  if (navigator.clipboard?.writeText && window.isSecureContext) {
    await navigator.clipboard.writeText(text)
    return
  }
  const input = document.createElement('textarea')
  input.value = text
  input.setAttribute('readonly', '')
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.appendChild(input)
  input.select()
  const copied = document.execCommand('copy')
  input.remove()
  if (!copied) throw new Error('copy failed')
}

async function copyShareLink() {
  try {
    await writeClipboard(shareUrl.value)
    copySucceeded.value = true
    toastStore.showToast('分享链接已复制，可直接发送给好友', 'success', 3500)
    if (copyFeedbackTimer) clearTimeout(copyFeedbackTimer)
    copyFeedbackTimer = setTimeout(() => { copySucceeded.value = false }, 4000)
  } catch {
    toastStore.showToast('复制失败，请从浏览器地址栏手动复制', 'error', 4500)
  }
}

function updateShareMetadata(info: ShareAccessInfo) {
  const title = `${info.share_name || '分享内容'} | PrivateCloudDisk`
  const description = `${info.owner_name || '分享者'} 分享了 ${info.resource_count || 0} 项资源，使用 PrivateCloudDisk 安全查看。`
  document.title = title
  upsertMeta('name', 'description', description)
  upsertMeta('property', 'og:title', title)
  upsertMeta('property', 'og:description', description)
  upsertMeta('property', 'og:type', 'website')
  upsertMeta('property', 'og:url', shareUrl.value)
  upsertMeta('property', 'og:image', `${window.location.origin}/share-card.jpg`)
  upsertMeta('property', 'og:image:width', '1200')
  upsertMeta('property', 'og:image:height', '630')
  upsertMeta('name', 'twitter:card', 'summary_large_image')
}

function upsertMeta(attribute: 'name' | 'property', key: string, content: string) {
  const identity = `${attribute}:${key}`
  let meta = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
  if (!originalMetaContents.has(identity)) {
    originalMetaContents.set(identity, meta?.content ?? null)
  }
  if (!meta) {
    meta = document.createElement('meta')
    meta.setAttribute(attribute, key)
    meta.dataset.shareMeta = 'true'
    document.head.appendChild(meta)
  }
  meta.content = content
}

async function initializeShare() {
  loading.value = true
  errorMessage.value = ''
  errorDetail.value = ''
  showPasswordScreen.value = false
  try {
    const response = await getShareInfoApi(shareToken)
    if (response.code !== 200 || !response.data) throw new Error('not-found')
    const info = response.data
    shareInfo.value = info
    updateShareMetadata(info)

    if (info.is_revoked) {
      errorMessage.value = '这份分享已被取消'
      errorDetail.value = '分享者已撤销链接，你可以联系对方获取新的分享地址。'
      return
    }
    if (info.is_expired) {
      errorMessage.value = '这份分享已过期'
      errorDetail.value = '链接已超过有效期，请联系分享者重新创建分享。'
      return
    }
    if (info.has_password) {
      if (urlPassword) {
        try {
          await verifyAndLoadContent(urlPassword)
        } catch {
          showPasswordScreen.value = true
          passwordError.value = '链接中的提取码不可用，请重新输入'
          await nextTick(() => passwordInputElement.value?.focus())
        }
      } else {
        showPasswordScreen.value = true
        await nextTick(() => passwordInputElement.value?.focus())
      }
      return
    }
    await verifyAndLoadContent('')
  } catch (error) {
    errorMessage.value = '没有找到这份分享'
    errorDetail.value = getReadableError(error, '请检查链接是否完整，或联系分享者确认链接状态。')
  } finally {
    loading.value = false
  }
}

async function retryLoad() {
  stopTokenRefresh()
  resources.value = []
  accessToken.value = ''
  await initializeShare()
}

async function verifyAndLoadContent(password: string) {
  const response = await verifySharePasswordApi(shareToken, password)
  if (response.code !== 200 || !response.data?.access_token) {
    throw new Error(response.message || '提取码验证失败')
  }
  accessToken.value = response.data.access_token
  await loadShareContent()
  startTokenRefresh(password)
}

async function submitPassword() {
  if (passwordVerifying.value) return
  const validationError = validatePassword(passwordInput.value)
  if (validationError) {
    passwordError.value = validationError
    return
  }
  passwordError.value = ''
  passwordVerifying.value = true
  try {
    await verifyAndLoadContent(passwordInput.value)
    showPasswordScreen.value = false
    updateShareMetadata(shareInfo.value!)
    toastStore.showToast('提取码验证成功，已为你打开分享', 'success')
  } catch (error) {
    passwordError.value = getReadableError(error, '验证失败，请稍后重试')
    await nextTick(() => passwordInputElement.value?.focus())
  } finally {
    passwordVerifying.value = false
  }
}

async function loadShareContent() {
  const response = await getShareContentApi(shareToken, accessToken.value)
  if (response.code !== 200) throw new Error(response.message || '加载分享内容失败')
  resources.value = response.data || []
}

function startTokenRefresh(password: string) {
  stopTokenRefresh()
  if (!password) return
  tokenRefreshTimer = setInterval(async () => {
    try {
      const response = await verifySharePasswordApi(shareToken, password)
      if (response.code === 200 && response.data?.access_token) accessToken.value = response.data.access_token
    } catch {
      stopTokenRefresh()
    }
  }, TOKEN_REFRESH_INTERVAL)
}

function stopTokenRefresh() {
  if (!tokenRefreshTimer) return
  clearInterval(tokenRefreshTimer)
  tokenRefreshTimer = null
}

function isTokenExpiredError(error: unknown) {
  const message = error instanceof Error ? error.message : ''
  return message.includes('过期') || message.includes('无效') || message.includes('重新验证')
}

async function refreshToken() {
  const password = urlPassword || passwordInput.value
  if (!password && shareInfo.value?.has_password) {
    showPasswordScreen.value = true
    throw new Error('请重新输入提取码')
  }
  await verifyAndLoadContent(password || '')
}

async function handleItemClick(item: ShareListItem) {
  if (!isFolderItem(item)) return
  if (showFolderBrowser.value) {
    await navigateIntoChildFolder({ id: getItemId(item), name: getItemName(item) })
  } else {
    await openFolderBrowser(item as ShareResourceVO)
  }
}

async function openFolderBrowser(item: ShareResourceVO) {
  showFolderBrowser.value = true
  folderBreadcrumb.value = [{ id: item.share_resource_id, name: item.resource_name }]
  await loadFolderContents(item.share_resource_id)
}

function closeFolderBrowser() {
  showFolderBrowser.value = false
  folderContents.value = []
  folderBreadcrumb.value = []
}

async function loadFolderContents(shareResourceId: string, retried = false) {
  folderContentsLoading.value = true
  try {
    const response = await getSharedFolderChildrenApi(shareToken, shareResourceId, accessToken.value)
    if (response.code !== 200) throw new Error(response.message || '加载目录失败')
    folderContents.value = response.data || []
  } catch (error) {
    if (!retried && isTokenExpiredError(error)) {
      await refreshToken()
      await loadFolderContents(shareResourceId, true)
      return
    }
    folderContents.value = []
    toastStore.showToast(getReadableError(error, '文件夹加载失败，请稍后重试'), 'error', 4500)
  } finally {
    folderContentsLoading.value = false
  }
}

async function navigateToFolderCrumb(crumb: ShareBreadcrumbItem) {
  folderBreadcrumb.value = resolveShareBreadcrumb(folderBreadcrumb.value, crumb)
  await loadFolderContents(crumb.id)
}

async function navigateIntoChildFolder(item: ShareDirectoryChild) {
  folderBreadcrumb.value = resolveShareBreadcrumb(folderBreadcrumb.value, item)
  await loadFolderContents(item.id)
}

async function downloadSharedFileItem(shareResourceId: string, retried = false) {
  if (downloadingId.value) return
  if (!authStore.isLoggedIn) {
    toastStore.showToast('下载分享文件前请先登录', 'info', 3500)
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (shareInfo.value?.allow_download === false) {
    toastStore.showToast('分享者已关闭下载，仅可浏览目录和文件信息', 'info', 4000)
    return
  }
  downloadingId.value = shareResourceId
  let downloadGrant = ''
  try {
    const grantResponse = await createShareDownloadGrantApi(shareToken, shareResourceId, accessToken.value)
    if (grantResponse.code !== 200 || !grantResponse.data?.download_grant) {
      throw new Error(grantResponse.message || '无法获取下载授权')
    }
    downloadGrant = grantResponse.data.download_grant
    const content = await getShareDownloadContentApi(shareToken, shareResourceId, downloadGrant)
    const blobUrl = URL.createObjectURL(content instanceof Blob ? content : new Blob([content as any]))
    const anchor = document.createElement('a')
    anchor.href = blobUrl
    anchor.download = grantResponse.data.file_name || 'download'
    anchor.rel = 'noopener'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
    toastStore.showToast('已开始下载文件', 'success')
  } catch (error) {
    if (!retried && isTokenExpiredError(error)) {
      downloadingId.value = ''
      await refreshToken()
      await downloadSharedFileItem(shareResourceId, true)
      return
    }
    toastStore.showToast(getReadableError(error, '下载失败，请稍后重试'), 'error', 4500)
  } finally {
    if (downloadGrant) await releaseDownloadGrantApi(downloadGrant).catch(() => undefined)
    downloadingId.value = ''
  }
}

/**
 * 需求 2.1/2.2：文件行点击使用分享专用 Preview Grant，避免旧接口返回 storage_path。
 * 预览失败不会影响目录浏览或下载流程，用户可再次点击重试。
 */
async function previewSharedFileItem(shareResourceId: string, retried = false) {
  if (!authStore.isLoggedIn) {
    toastStore.showToast('在线预览分享文件前请先登录', 'info', 3500)
    await router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const opened = window.open('', '_blank', 'noopener,noreferrer')
  let previewGrant = ''
  try {
    const grantResponse = await createSharePreviewGrantApi(shareToken, shareResourceId, accessToken.value)
    if (grantResponse.code !== 200 || !grantResponse.data?.preview_grant) {
      throw new Error(grantResponse.message || '无法获取预览授权')
    }
    previewGrant = grantResponse.data.preview_grant
    const content = await getSharePreviewContentApi(shareToken, shareResourceId, previewGrant)
    const blobUrl = URL.createObjectURL(content instanceof Blob ? content : new Blob([content as any]))
    if (opened) {
      opened.location.href = blobUrl
      window.setTimeout(() => URL.revokeObjectURL(blobUrl), 60_000)
    } else {
      URL.revokeObjectURL(blobUrl)
      throw new Error('浏览器阻止了新窗口，请允许弹窗后重试')
    }
  } catch (error) {
    opened?.close()
    if (!retried && isTokenExpiredError(error)) {
      await refreshToken()
      await previewSharedFileItem(shareResourceId, true)
      return
    }
    toastStore.showToast(getReadableError(error, '预览失败，请稍后重试'), 'error', 4500)
  } finally {
    if (previewGrant) await releasePreviewGrantApi(previewGrant).catch(() => undefined)
  }
}

onMounted(() => {
  // 提取码仍用于复制与二维码，但验证页加载后立即从地址栏移除，
  // 减少浏览历史、截图和同源 Referer 意外记录明文提取码的概率。
  if (urlPassword) window.history.replaceState(window.history.state, '', route.path)
  if (authStore.isLoggedIn) void authStore.fetchUserInfo()
  void initializeShare()
})

onUnmounted(() => {
  stopTokenRefresh()
  if (copyFeedbackTimer) clearTimeout(copyFeedbackTimer)
  document.title = originalDocumentTitle
  originalMetaContents.forEach((content, identity) => {
    const separator = identity.indexOf(':')
    const attribute = identity.slice(0, separator)
    const key = identity.slice(separator + 1)
    const meta = document.head.querySelector<HTMLMetaElement>(`meta[${attribute}="${key}"]`)
    if (!meta) return
    if (content === null) meta.remove()
    else meta.content = content
  })
})
</script>

<style scoped>
#pcd-public-share-page.share-page.share-main-page {
  --ink: #152039;
  --muted: #69778d;
  --line: #e3e9f2;
  --blue: #165dff;
  position: relative;
  display: flex;
  /* AUDIT FIX [2.4]（需求一-7）:
     原 share-page 被 Safari 内容拦截器的通用元素隐藏规则误判；历史改名只能绕过。
     页面专属 ID + scoped 属性提供更高特异性，即使保留原类名也显式恢复可见布局。 */
  display: flex !important;
  visibility: visible !important;
  min-height: 100vh;
  min-height: 100dvh;
  flex-direction: column;

}

.page-glow { position: fixed; z-index: 0; border-radius: 50%; pointer-events: none; filter: blur(2px); }
.page-glow--one { top: 70px; left: -160px; width: 470px; height: 470px; background: radial-gradient(circle, rgba(74,132,255,.12), transparent 68%); }
.page-glow--two { top: 250px; right: -210px; width: 620px; height: 620px; background: radial-gradient(circle, rgba(54,207,201,.08), transparent 66%); }

.share-header { position: sticky; z-index: 40; top: 0; border-bottom: 1px solid rgba(220,227,238,.88); background: rgba(255,255,255,.96); -webkit-backdrop-filter: saturate(155%) blur(18px); backdrop-filter: saturate(155%) blur(18px); }
.header-inner { position: relative; display: flex; width: min(100%, 1500px); min-height: 66px; align-items: center; justify-content: space-between; gap: 18px; margin: 0 auto; padding: 0 24px; }
.brand-link { display: inline-flex; align-items: center; gap: 11px; color: #1a2945; text-decoration: none; }
.brand-mark { display: inline-flex; width: 38px; height: 38px; align-items: center; justify-content: center; border-radius: 13px; background: linear-gradient(140deg, #0e54f4, #3b7aff); color: #fff; box-shadow: 0 9px 22px rgba(22,93,255,.24); }
.brand-name { font-size: 17px; font-weight: 800; letter-spacing: -.02em; }
.header-actions { display: flex; align-items: center; gap: 8px; }
.dashboard-link,
.header-link { display: inline-flex; min-height: 42px; align-items: center; justify-content: center; gap: 7px; border-radius: 12px; padding: 0 15px; font-size: 13px; font-weight: 700; text-decoration: none; transition: transform 160ms ease, background-color 160ms ease, box-shadow 160ms ease; }
.dashboard-link { border: 1px solid #d7e3ff; background: #f3f7ff; color: #164ecc; }
.header-link--quiet { color: #526078; }
.header-link--primary { background: #165dff; color: #fff; box-shadow: 0 8px 18px rgba(22,93,255,.2); }
.dashboard-link:hover,
.header-link:hover { transform: translateY(-1px); }
.header-link--quiet:hover { background: #f1f4f8; }

.share-main { position: relative; z-index: 1; flex: 1; }
.share-shell { width: min(100%, 1500px); margin: 0 auto; padding: 28px 24px 46px; }
.state-card { width: min(100%, 620px); margin: 8vh auto 0; padding: 52px 42px; border: 1px solid rgba(226,232,240,.9); border-radius: 28px; background: rgba(255,255,255,.94); box-shadow: 0 25px 70px rgba(15,23,42,.1); text-align: center; }
.loading-orbit { display: inline-flex; width: 72px; height: 72px; align-items: center; justify-content: center; border: 1px solid #dce7ff; border-radius: 50%; background: #eef4ff; color: #165dff; font-size: 27px; animation: loadingPulse 1.7s ease-in-out infinite; }
.state-card h1 { margin: 20px 0 8px; font-size: 25px; }
.state-card > p { margin: 0 auto; color: var(--muted); font-size: 14px; line-height: 1.7; }
.skeleton-lines { display: grid; gap: 9px; width: min(100%, 330px); margin: 28px auto 0; }
.skeleton-lines span { height: 10px; border-radius: 999px; background: linear-gradient(90deg, #eef2f7 20%, #f8fafc 50%, #eef2f7 80%); background-size: 240% 100%; animation: shimmer 1.4s infinite; }
.skeleton-lines span:nth-child(2) { width: 84%; }
.skeleton-lines span:nth-child(3) { width: 62%; }
.state-icon { display: inline-flex; width: 70px; height: 70px; align-items: center; justify-content: center; border-radius: 22px; font-size: 27px; }
.state-icon--danger { background: #fff0f0; color: #e53e3e; }
.state-kicker { color: #165dff !important; font-size: 11px !important; font-weight: 800; letter-spacing: .13em; text-transform: uppercase; }
.error-state h1 { margin-top: 8px; }
.state-actions { display: flex; justify-content: center; gap: 10px; margin-top: 28px; }
.button { display: inline-flex; min-height: 44px; align-items: center; justify-content: center; gap: 8px; border-radius: 12px; padding: 0 18px; border: 1px solid transparent; cursor: pointer; font-size: 13px; font-weight: 750; }
.button--primary { background: #165dff; color: #fff; }
.button--secondary { border-color: #dfe5ee; background: #fff; color: #536176; }

.password-layout { display: grid; width: min(100%, 980px); min-height: 600px; grid-template-columns: .9fr 1.1fr; overflow: hidden; margin: 3.5vh auto 0; border: 1px solid rgba(216,225,239,.92); border-radius: 30px; background: #fff; box-shadow: 0 30px 85px rgba(15,23,42,.13); }
.password-visual { position: relative; display: flex; min-height: 600px; flex-direction: column; justify-content: flex-end; overflow: hidden; padding: 45px; background: linear-gradient(150deg, #0e49c8 0%, #165dff 46%, #1f7be5 100%); color: #fff; }
.password-visual::before { content: ''; position: absolute; top: -100px; right: -120px; width: 360px; height: 360px; border: 1px solid rgba(255,255,255,.17); border-radius: 50%; box-shadow: 0 0 0 52px rgba(255,255,255,.04), 0 0 0 110px rgba(255,255,255,.025); }
.visual-grid { position: absolute; inset: 0; opacity: .1; background-image: linear-gradient(rgba(255,255,255,.35) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.35) 1px, transparent 1px); background-size: 32px 32px; mask-image: linear-gradient(to bottom, transparent, #000); }
.security-orb { position: absolute; top: 75px; left: 50%; display: inline-flex; width: 128px; height: 128px; align-items: center; justify-content: center; border: 1px solid rgba(255,255,255,.25); border-radius: 40px; background: rgba(255,255,255,.18); font-size: 50px; box-shadow: inset 0 1px 0 rgba(255,255,255,.26), 0 26px 60px rgba(4,31,93,.25); transform: translateX(-50%) rotate(-5deg); -webkit-backdrop-filter: blur(10px); backdrop-filter: blur(10px); }
.password-visual > div { position: relative; }
.password-visual p { margin: 0 0 12px; color: rgba(255,255,255,.72); font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.password-visual strong { display: block; font-size: 30px; line-height: 1.28; letter-spacing: -.035em; }
.password-visual div > span { display: block; margin-top: 20px; color: rgba(255,255,255,.72); font-size: 12px; line-height: 1.7; }
.password-card { display: flex; flex-direction: column; justify-content: center; padding: 52px 58px; }
.password-lock { display: inline-flex; width: 58px; height: 58px; align-items: center; justify-content: center; margin-bottom: 20px; border-radius: 19px; background: #eef4ff; color: #165dff; font-size: 23px; }
.password-card h1 { margin: 7px 0 8px; color: #15213a; font-size: clamp(25px, 3vw, 34px); line-height: 1.25; overflow-wrap: anywhere; letter-spacing: -.035em; }
.password-owner { margin: 0; color: #768398; font-size: 14px; }
.password-owner strong { color: #3e4b60; }
.password-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 19px; }
.password-meta span { display: inline-flex; min-height: 34px; align-items: center; gap: 7px; border-radius: 999px; background: #f3f6fa; padding: 0 11px; color: #65748a; font-size: 11px; }
.extract-form { margin-top: 30px; }
.extract-form > label { display: block; margin-bottom: 8px; color: #3c4a60; font-size: 13px; font-weight: 750; }
.extract-controls { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 10px; }
.extract-controls input { min-width: 0; height: 52px; border: 1px solid #d8e0eb; border-radius: 13px; padding: 0 16px; color: #1e293b; font-size: 16px; letter-spacing: .08em; outline: 0; transition: border-color 160ms ease, box-shadow 160ms ease; }
.extract-controls input:focus { border-color: #7ca3ff; box-shadow: 0 0 0 4px rgba(22,93,255,.12); }
.extract-controls--error input { border-color: #f08c8c; box-shadow: 0 0 0 4px rgba(229,62,62,.08); }
.extract-controls button { display: inline-flex; height: 52px; min-width: 126px; align-items: center; justify-content: center; gap: 8px; border: 0; border-radius: 13px; background: #165dff; padding: 0 18px; color: #fff; cursor: pointer; font-size: 14px; font-weight: 760; box-shadow: 0 11px 23px rgba(22,93,255,.22); }
.extract-controls button:disabled { cursor: not-allowed; opacity: .58; box-shadow: none; }
.field-help { margin: 8px 0 0; color: #9aa6b6; font-size: 11px; }
.field-error { display: flex; align-items: flex-start; gap: 6px; margin: 9px 0 0; color: #d9363e; font-size: 12px; line-height: 1.5; }
.trust-note { display: flex; align-items: flex-start; gap: 9px; margin-top: 28px; padding-top: 18px; border-top: 1px solid #edf0f5; color: #8793a4; font-size: 11px; line-height: 1.6; }
.trust-note i { margin-top: 2px; color: #28a66f; }
.button-spinner,
.loading-spinner { width: 17px; height: 17px; border: 2px solid rgba(255,255,255,.42); border-top-color: #fff; border-radius: 50%; animation: spin 700ms linear infinite; }

.share-hero { position: relative; display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 25px; overflow: hidden; margin-bottom: 22px; padding: 30px 32px 0; border: 1px solid rgba(219,227,239,.94); border-radius: 26px; background: rgba(255,255,255,.96); box-shadow: 0 18px 55px rgba(15,23,42,.075); }
.hero-accent { position: absolute; top: -90px; right: 150px; width: 300px; height: 220px; border-radius: 50%; background: radial-gradient(circle, rgba(22,93,255,.12), transparent 67%); pointer-events: none; }
.hero-main { position: relative; display: flex; min-width: 0; align-items: center; gap: 17px; }
.owner-avatar { position: relative; display: inline-flex; width: 68px; height: 68px; flex: 0 0 auto; align-items: center; justify-content: center; border: 4px solid #fff; border-radius: 22px; background: linear-gradient(145deg, #dce8ff, #f2f7ff); color: #1555dc; font-size: 25px; font-weight: 850; box-shadow: 0 8px 22px rgba(22,93,255,.13); }
.owner-avatar i { position: absolute; right: -5px; bottom: -5px; border: 3px solid #fff; border-radius: 50%; background: #fff; color: #25a56c; font-size: 17px; }
.hero-copy { min-width: 0; }
.hero-kicker { display: flex; align-items: center; gap: 6px; margin: 0 0 6px; color: #165dff; font-size: 11px; font-weight: 800; letter-spacing: .1em; text-transform: uppercase; }
.hero-copy h1 { margin: 0; color: #131f37; font-size: clamp(24px, 3vw, 34px); line-height: 1.26; overflow-wrap: anywhere; letter-spacing: -.035em; }
.owner-line { display: flex; flex-wrap: wrap; gap: 7px; margin: 9px 0 0; color: #718096; font-size: 13px; }
.owner-line strong { color: #394960; }
.hero-actions { position: relative; display: flex; min-width: 178px; flex-direction: column; align-items: stretch; justify-content: center; }
.copy-button { display: inline-flex; min-height: 48px; align-items: center; justify-content: center; gap: 9px; border: 0; border-radius: 14px; background: #165dff; padding: 0 19px; color: #fff; cursor: pointer; font-size: 13px; font-weight: 780; box-shadow: 0 12px 25px rgba(22,93,255,.23); transition: transform 160ms ease, background-color 160ms ease, box-shadow 160ms ease; }
.copy-button:hover { transform: translateY(-2px); box-shadow: 0 15px 30px rgba(22,93,255,.29); }
.copy-button--success { background: #1f9d68; box-shadow: 0 12px 25px rgba(31,157,104,.22); }
.copy-caption { margin: 7px 0 0; color: #95a0af; font-size: 10px; text-align: center; }
.hero-meta { grid-column: 1 / -1; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); margin: 4px -32px 0; border-top: 1px solid #edf1f6; background: #fbfcfe; }
.hero-meta > div { min-width: 0; padding: 16px 24px 18px; border-right: 1px solid #edf1f6; }
.hero-meta > div:last-child { border-right: 0; }
.hero-meta dt { display: flex; align-items: center; gap: 7px; color: #8995a7; font-size: 10px; font-weight: 750; letter-spacing: .04em; }
.hero-meta dt i { color: #7197e9; }
.hero-meta dd { margin: 6px 0 0; color: #344258; font-size: 13px; font-weight: 700; overflow-wrap: anywhere; }
.hero-meta dd.meta-warning { color: #b26a00; }

.content-grid { display: grid; grid-template-columns: minmax(0, 1fr) 360px; align-items: start; gap: 22px; }
.content-primary { min-width: 0; }
.browser-grid { display: grid; grid-template-columns: minmax(0, 1fr); align-items: start; gap: 18px; }
.browser-grid--with-tree { grid-template-columns: 290px minmax(0, 1fr); }
.context-rail { position: sticky; top: 88px; }
.file-card { min-width: 0; overflow: hidden; border: 1px solid rgba(222,229,240,.94); border-radius: 21px; background: rgba(255,255,255,.97); box-shadow: 0 14px 40px rgba(15,23,42,.065); }
.file-toolbar { display: flex; min-height: 75px; align-items: center; justify-content: space-between; gap: 14px; padding: 14px 20px; border-bottom: 1px solid #edf1f6; }
.toolbar-title { min-width: 0; flex: 1; }
.card-kicker { margin: 0 0 4px; color: #8b99ad; font-size: 10px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
.file-toolbar h2 { display: flex; align-items: center; gap: 8px; margin: 0; color: #172033; font-size: 16px; }
.file-toolbar h2 i { color: #e9a009; }
.item-count { display: inline-flex; min-height: 30px; flex: 0 0 auto; align-items: center; border-radius: 999px; background: #f1f5f9; padding: 0 10px; color: #69778c; font-size: 11px; font-weight: 700; }
.breadcrumb-scroll { display: flex; max-width: 100%; align-items: center; gap: 4px; overflow-x: auto; padding: 5px 2px; scrollbar-width: thin; }
.breadcrumb-scroll button { min-height: 36px; flex: 0 0 auto; border: 0; border-radius: 10px; background: transparent; padding: 0 9px; color: #165dff; cursor: pointer; font-size: 12px; font-weight: 650; white-space: nowrap; }
.breadcrumb-scroll button:hover { background: #eff5ff; }
.breadcrumb-scroll .breadcrumb-current { background: #f1f4f8; color: #394960; cursor: default; }
.breadcrumb-separator { flex: 0 0 auto; color: #b6c0ce; font-size: 11px; }
.folder-loading { display: flex; min-height: 265px; align-items: center; justify-content: center; gap: 14px; color: #64748b; }
.folder-loading .loading-spinner { width: 30px; height: 30px; border-width: 3px; border-color: #d9e5ff; border-top-color: #165dff; }
.folder-loading strong { color: #344258; font-size: 13px; }
.folder-loading p { margin: 3px 0 0; color: #94a3b8; font-size: 11px; }
.empty-files { min-height: 320px; padding: 70px 20px; text-align: center; }
.empty-files > span { display: inline-flex; width: 62px; height: 62px; align-items: center; justify-content: center; border-radius: 20px; background: #f2f5f9; color: #93a1b5; font-size: 25px; }
.empty-files h3 { margin: 16px 0 6px; color: #445268; font-size: 15px; }
.empty-files p { margin: 0; color: #97a3b2; font-size: 12px; }
.file-table-wrap { overflow-x: auto; }
.file-table { width: 100%; border-collapse: collapse; table-layout: auto; }
.file-table th { height: 42px; padding: 0 16px; background: #fbfcfe; color: #8e9aac; font-size: 10px; font-weight: 780; letter-spacing: .06em; text-align: left; text-transform: uppercase; }
.file-table th:first-child { width: 56%; padding-left: 20px; }
.file-table th:last-child { width: 104px; }
.file-table td { padding: 13px 16px; border-top: 1px solid #f0f3f7; color: #657287; font-size: 12px; vertical-align: middle; }
.file-table td:first-child { padding-left: 20px; }
.file-table tbody tr { transition: background-color 150ms ease; }
.file-table tbody tr:hover { background: #f8faff; }
.file-table tbody tr.folder-row { cursor: pointer; }
.file-name-cell { display: flex; min-width: 220px; align-items: center; gap: 12px; }
.file-name-cell > div { min-width: 0; }
.file-icon { display: inline-flex; width: 42px; height: 42px; flex: 0 0 auto; align-items: center; justify-content: center; border-radius: 13px; font-size: 17px; }
.file-icon--folder { background: #fff7e3; color: #e6a00a; }
.file-icon--file { background: #edf4ff; }
.file-name { max-width: 100%; margin: 0; color: #344258; font-size: 13px; font-weight: 700; line-height: 1.5; white-space: normal; overflow-wrap: anywhere; word-break: break-word; }
.mobile-file-meta { display: none; }
.type-badge { display: inline-flex; min-height: 27px; align-items: center; border-radius: 8px; background: #f0f4f8; padding: 0 8px; color: #65748a; font-size: 10px; font-weight: 700; }
.type-badge--folder { background: #fff5dc; color: #b57700; }
.muted-value { color: #b1bac7; }
.file-action-cell { text-align: right; }
.download-button,
.folder-button { display: inline-flex; min-width: 78px; min-height: 40px; align-items: center; justify-content: center; gap: 7px; border: 1px solid #d6e3ff; border-radius: 11px; background: #f6f9ff; color: #165dff; cursor: pointer; font-size: 11px; font-weight: 750; transition: background-color 150ms ease, transform 150ms ease; }
.download-button:hover,
.folder-button:hover { background: #eaf2ff; transform: translateY(-1px); }
.download-button:disabled { cursor: wait; opacity: .65; }
.folder-button { border-color: transparent; background: transparent; color: #65748a; }
.button-spinner--blue { border-color: #c5d8ff; border-top-color: #165dff; }
.icon-danger { color: #e34850; } .icon-blue { color: #2468e8; } .icon-green { color: #2a9a66; }
.icon-orange { color: #e08026; } .icon-cyan { color: #159cb3; } .icon-purple { color: #7c5ad7; }
.icon-pink { color: #d24f8d; } .icon-amber { color: #bf7913; } .icon-slate { color: #64748b; } .icon-teal { color: #168a83; }

.share-footer { position: relative; z-index: 1; display: flex; width: min(100%, 1500px); align-items: center; justify-content: space-between; gap: 20px; margin: 0 auto; padding: 22px 24px 26px; border-top: 1px solid rgba(219,227,237,.8); color: #8190a5; }
.share-footer > div { display: flex; align-items: center; gap: 12px; }
.share-footer span { display: inline-flex; align-items: center; gap: 7px; color: #4e5d72; font-size: 12px; font-weight: 800; }
.share-footer span i { color: #165dff; }
.share-footer p { margin: 0; font-size: 11px; }
.share-footer nav { display: flex; gap: 18px; }
.share-footer a { min-height: 36px; display: inline-flex; align-items: center; color: #718096; font-size: 11px; text-decoration: none; }
.share-footer a:hover { color: #165dff; }

button:focus-visible,
a:focus-visible,
.breadcrumb-scroll:focus-visible { outline: 3px solid rgba(22,93,255,.24); outline-offset: 2px; }

@keyframes spin { to { transform: rotate(360deg); } }
@keyframes shimmer { to { background-position: -240% 0; } }
@keyframes loadingPulse { 50% { transform: translateY(-4px); box-shadow: 0 16px 32px rgba(22,93,255,.14); } }

@media (max-width: 1199px) {
  .share-shell { max-width: 1100px; }
  .content-grid { grid-template-columns: 1fr; }
  .context-rail { position: static; display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .context-rail :deep(.comments-card) { grid-column: 1 / -1; }
}

@media (max-width: 920px) {
  .browser-grid--with-tree { grid-template-columns: 250px minmax(0, 1fr); }
  .password-layout { grid-template-columns: .78fr 1.22fr; }
  .password-visual { padding: 34px; }
  .password-card { padding: 42px 38px; }
  .hero-meta { grid-template-columns: repeat(2, 1fr); }
  .hero-meta > div:nth-child(2) { border-right: 0; }
  .hero-meta > div:nth-child(-n+2) { border-bottom: 1px solid #edf1f6; }
}

@media (max-width: 767px) {
  .header-inner { min-height: 60px; padding: 0 15px; }
  .brand-mark { width: 36px; height: 36px; }
  .dashboard-link span { display: none; }
  .dashboard-link { width: 42px; padding: 0; }
  .header-link { min-height: 40px; padding: 0 12px; }
  .share-shell { padding: 18px 14px 34px; }
  .state-card { margin-top: 4vh; padding: 40px 22px; border-radius: 22px; }
  .password-layout { display: block; margin-top: 1vh; border-radius: 24px; }
  .password-visual { display: none; }
  .password-card { min-height: 550px; padding: 35px 24px; }
  .extract-controls { grid-template-columns: 1fr; }
  .extract-controls input,
  .extract-controls button { width: 100%; height: 52px; }
  .share-hero { grid-template-columns: 1fr; gap: 20px; padding: 23px 20px 0; border-radius: 21px; }
  .hero-main { align-items: flex-start; }
  .owner-avatar { width: 58px; height: 58px; border-radius: 18px; font-size: 21px; }
  .hero-copy h1 { font-size: 24px; }
  .hero-actions { width: 100%; }
  .copy-button { min-height: 50px; }
  .hero-meta { margin: 0 -20px; }
  .hero-meta > div { padding: 14px 16px; }
  .browser-grid--with-tree { grid-template-columns: 1fr; }
  .context-rail { display: flex; }
  .file-toolbar { min-height: 68px; padding: 12px 14px; }
  .file-table thead { display: none; }
  .file-table,
  .file-table tbody { display: block; }
  .file-table tr { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: center; padding: 13px 14px; border-top: 1px solid #eef2f6; }
  .file-table td { display: block; padding: 0; border: 0; }
  .file-table td:first-child { padding-left: 0; }
  .file-table td:nth-child(2),
  .file-table td:nth-child(3) { display: none; }
  .file-name-cell { min-width: 0; align-items: flex-start; }
  .file-name { padding-top: 2px; font-size: 13px; }
  .mobile-file-meta { display: block; margin-top: 4px; color: #909cad; font-size: 10px; }
  .file-action-cell { padding-left: 9px !important; }
  .download-button,
  .folder-button { min-width: 48px; min-height: 44px; padding: 0 11px; }
  .download-button span:not(.button-spinner),
  .folder-button span { display: none; }
  .share-footer { align-items: flex-start; flex-direction: column; padding: 20px 16px 28px; }
  .share-footer > div { align-items: flex-start; flex-direction: column; gap: 4px; }
  .share-footer nav { width: 100%; justify-content: space-between; gap: 8px; }
}

@media (max-width: 460px) {
  .brand-name { display: none; }
  .state-actions { flex-direction: column; }
  .button { width: 100%; }
  .password-card { padding: 30px 19px; }
  .password-meta { align-items: stretch; flex-direction: column; }
  .password-meta span { justify-content: center; }
  .hero-meta { grid-template-columns: 1fr; }
  .hero-meta > div { border-right: 0; border-bottom: 1px solid #edf1f6; }
  .hero-meta > div:last-child { border-bottom: 0; }
  .owner-line { flex-direction: column; gap: 2px; }
  .share-footer nav { flex-wrap: wrap; }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after { scroll-behavior: auto !important; animation-duration: .01ms !important; animation-iteration-count: 1 !important; transition-duration: .01ms !important; }
}
</style>
