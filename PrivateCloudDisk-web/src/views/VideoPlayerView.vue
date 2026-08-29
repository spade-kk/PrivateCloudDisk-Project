<template>
  <div class="video-page" :class="{ 'video-page--side-collapsed': sidePanelCollapsed, 'video-page--dark': isDark }">
    <header class="video-header" aria-label="视频导航">
      <div class="video-header__left">
        <button class="back-button" type="button" @click="handleGoBack"><i class="fa fa-arrow-left" aria-hidden="true"></i><span>返回网盘</span></button>
        <span class="video-header__divider" aria-hidden="true"></span>
        <router-link class="video-header__link" to="/app"><i class="fa fa-th-large"></i>控制面板</router-link>
      </div>
      <div class="brand-mark"><i class="fa fa-cloud"></i><span>PrivateCloudDisk</span></div>
    </header>

    <main class="video-layout">
      <section class="video-primary" aria-label="视频播放区域">
        <div class="video-breadcrumb"><button type="button" @click="handleGoBack"><i class="fa fa-folder-open-o"></i>我的文件</button><i class="fa fa-angle-right"></i><span :title="store.currentFile?.node_name || fileName">{{ store.currentFile?.node_name || fileName }}</span></div>
        <div class="player-shell" :class="{ 'is-fullscreen': store.isFullscreen }">
          <div v-if="store.loading" class="state-overlay">
            <div class="spinner"></div>
            <strong>正在加载视频</strong>
            <span>{{ store.currentFile?.node_name || '' }}</span>
          </div>

          <div v-else-if="store.error" class="state-overlay error-state">
            <i class="fa fa-exclamation-circle"></i>
            <strong>{{ store.error.title || '播放错误' }}</strong>
            <span>{{ store.error.message }}</span>
            <div class="state-actions">
              <button type="button" @click="handleRetry"><i class="fa fa-refresh"></i>重新加载</button>
              <button type="button" class="secondary" @click="handleGoBack">返回网盘</button>
            </div>
          </div>

          <VideoPlayerCore
            v-else
            ref="playerRef"
            :stream-info="store.streamInfo"
            :sprite-info="store.spriteInfo"
            :subtitles="store.subtitles"
            :active-subtitle="store.activeSubtitle || undefined"
            :stream-token="store.streamToken"
            :initial-resolution="store.currentResolution"
            :initial-playback-rate="store.playbackRate"
            :initial-volume="store.volume"
            :saved-progress="store.savedProgress?.current_time || 0"
            :is-hls="store.isHls"
            :hls-source-url="store.isHls ? store.videoSourceUrl : ''"
            :video-source-url="store.isMp4 ? store.videoSourceUrl : ''"
            :poster-url="store.previewThumbnailUrl"
            :file-id="store.currentFile?.node_id"
            @timeupdate="onTimeUpdate"
            @progress-report="onProgressReport"
            @resolution-change="store.setResolution"
            @speed-change="store.setPlaybackRate"
            @volume-change="store.setVolume"
            @fullscreen-change="store.isFullscreen = $event"
            @error="onPlayerError"
            @retry="handleRetry"
          />
        </div>

        <section class="video-info" aria-label="视频信息">
          <div class="video-info__title-row"><div><h1>{{ store.currentFile?.node_name || fileName }}</h1><div class="meta-row"><span><i class="fa fa-play-circle"></i>{{ streamLabel }}</span><span v-if="store.streamInfo?.duration"><i class="fa fa-clock-o"></i>{{ formatDuration(store.streamInfo.duration) }}</span><span v-if="store.savedProgress?.current_time" class="resume-chip">已续播 {{ formatDuration(store.savedProgress.current_time) }}</span></div></div><button type="button" class="video-info__more" title="更多视频信息" @click="showTechnicalInfo = !showTechnicalInfo"><i :class="showTechnicalInfo ? 'fa fa-angle-up' : 'fa fa-angle-down'"></i></button></div>
          <div class="video-actions" aria-label="视频操作">
            <button type="button" :class="{ active: isStarred }" :disabled="starLoading" @click="toggleStar"><i :class="isStarred ? 'fa fa-star' : 'fa fa-star-o'"></i><span>{{ isStarred ? '已收藏' : '收藏' }}</span></button>
            <button type="button" :disabled="sharing" @click="shareVideo"><i class="fa fa-share-alt"></i><span>分享</span></button>
            <button type="button" :disabled="downloading" @click="downloadVideo"><i :class="downloading ? 'fa fa-circle-o-notch fa-spin' : 'fa fa-download'"></i><span>{{ downloading ? '准备下载' : '下载' }}</span></button>
            <button type="button" class="video-actions__quiet" title="举报入口暂由文件详情页统一处理" @click="reportVideo"><i class="fa fa-flag-o"></i><span>举报</span></button>
          </div>
          <div class="video-description" :class="{ expanded: descriptionExpanded }"><p>{{ videoDescription }}</p><button v-if="videoDescription.length > 140" type="button" @click="descriptionExpanded = !descriptionExpanded">{{ descriptionExpanded ? '收起' : '展开全文' }}</button></div>
          <div class="video-tags"><span><i class="fa fa-film"></i>{{ store.isHls ? 'HLS' : 'MP4' }}</span><span v-if="resolutionLabel"><i class="fa fa-arrows-alt"></i>{{ resolutionLabel }}</span><span v-if="store.currentFile?.file_size"><i class="fa fa-hdd-o"></i>{{ formatBytes(store.currentFile.file_size) }}</span><span v-if="store.subtitles.length"><i class="fa fa-cc"></i>{{ store.subtitles.length }} 条字幕</span></div>
          <dl v-if="showTechnicalInfo" class="video-technical-info"><div><dt>分辨率</dt><dd>{{ resolutionLabel || '媒体元数据未提供' }}</dd></div><div><dt>播放协议</dt><dd>{{ streamLabel }}</dd></div><div><dt>视频时长</dt><dd>{{ formatDuration(store.streamInfo?.duration) }}</dd></div><div><dt>文件大小</dt><dd>{{ formatBytes(store.currentFile?.file_size) }}</dd></div></dl>
        </section>
      </section>

      <aside class="history-panel" :class="{ 'history-panel--collapsed': sidePanelCollapsed }" aria-label="播放队列与推荐">
        <button class="history-panel__collapse" type="button" :title="sidePanelCollapsed ? '展开播放列表' : '收起播放列表'" @click="sidePanelCollapsed = !sidePanelCollapsed"><i :class="sidePanelCollapsed ? 'fa fa-angle-double-left' : 'fa fa-angle-double-right'"></i></button>
        <template v-if="!sidePanelCollapsed">
        <div class="statistics-card">
          <div>
            <span class="eyebrow">账号媒体库</span>
            <strong>{{ store.playableVideoCount }}</strong>
            <span>个视频可播放</span>
          </div>
          <i class="fa fa-film"></i>
        </div>

        <div class="history-heading">
          <div><p class="video-panel-eyebrow">UP NEXT</p><h2>播放队列</h2><span>最近 {{ store.historyTotal }} 条视频</span></div>
          <button type="button" title="刷新播放历史" @click="store.loadSidebarData"><i class="fa fa-refresh"></i></button>
        </div>

        <div v-if="store.sidebarLoading" class="history-skeleton" aria-label="正在加载播放历史">
          <div v-for="index in 4" :key="index" class="skeleton-row"><span></span><div><i></i><i></i></div></div>
        </div>
        <div v-else-if="store.watchHistory.length === 0" class="history-empty">
          <i class="fa fa-history"></i><p>还没有播放记录</p><span>观看过的视频会显示在这里</span>
        </div>
        <div v-else class="history-list">
          <button
            v-for="item in store.watchHistory"
            :key="item.file_id"
            type="button"
            class="history-item"
            :class="{ active: item.file_id === store.currentFile?.node_id }"
            @click="playHistoryItem(item)"
          >
            <span class="history-thumb">
              <ThumbnailImage :file-id="item.file_id" :file-name="item.file_name || 'video.mp4'" size="medium" />
              <small>{{ formatDuration(item.total_duration) }}</small>
              <i v-if="item.file_id === store.currentFile?.node_id" class="fa fa-volume-up playing-icon"></i>
            </span>
            <span class="history-info">
              <strong>{{ item.file_name || '未命名视频' }}</strong>
              <span>{{ item.completed ? '已看完' : `看到 ${formatDuration(item.watched_duration)}` }}</span>
              <progress :value="item.watched_duration" :max="item.total_duration || 1"></progress>
            </span>
          </button>
        </div>
        <div class="history-panel__foot"><span><i class="fa fa-info-circle"></i>基于当前账号的实际播放记录</span><button v-if="nextVideo" type="button" @click="playHistoryItem(nextVideo)">播放下一个<i class="fa fa-angle-right"></i></button></div>
        </template>
      </aside>
    </main>
  </div>
</template>

<script setup lang="ts">
// AUDIT FIX [3.1]: 视频页改为顶级独立工作区，保留现有 HLS 核心组件并加入持久化历史与资源统计。
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import VideoPlayerCore from '@/components/video/VideoPlayerCore.vue'
import ThumbnailImage from '@/components/file/ThumbnailImage.vue'
import { useVideoPlayerStore } from '@/stores/videoPlayerStore'
import { addFileStarApi, checkFileStarredApi, removeFileStarApi } from '@/api/modules/stars'
import { createDownloadGrantApi, getFileContentApi, releaseDownloadGrantApi } from '@/api/modules/downloads'
import { createShareApi } from '@/api/modules/shares'
import { useToastStore } from '@/stores/toastStore'

const route = useRoute()
const router = useRouter()
const store = useVideoPlayerStore()
const toast = useToastStore()
const playerRef = ref<InstanceType<typeof VideoPlayerCore> | null>(null)
const lastSavedSecond = ref(0)
const fileName = computed(() => String(route.query.name || '视频播放'))
const sidePanelCollapsed = ref(false)
const descriptionExpanded = ref(false)
const showTechnicalInfo = ref(false)
const isStarred = ref(false)
const starLoading = ref(false)
const downloading = ref(false)
const sharing = ref(false)
// [REQ-VIDEO-1.15] 独立预览页不在 Layout 的 Tailwind 深色容器内；观察 html.dark，
// 让它与控制面板主题切换同步，而不是只依赖操作系统偏好。
const isDark = ref(document.documentElement.classList.contains('dark') || localStorage.getItem('darkMode') === 'true')
let themeObserver: MutationObserver | undefined
function syncTheme(): void { isDark.value = document.documentElement.classList.contains('dark') || localStorage.getItem('darkMode') === 'true' }
const streamLabel = computed(() => store.isHls ? 'HLS 自适应流媒体' : 'MP4 直连播放')
const resolutionLabel = computed(() => store.streamInfo?.width && store.streamInfo?.height ? `${store.streamInfo.width} × ${store.streamInfo.height}` : '')
const videoDescription = computed(() => String(route.query.description || '此视频保存在你的私有云盘中。播放器沿用文件权限、HLS/MP4 访问授权和断点续播机制；可从右侧播放队列继续观看其他视频。'))
const nextVideo = computed(() => store.watchHistory.find(item => item.file_id !== store.currentFile?.node_id) || null)

async function initVideo() {
  const fileId = String(route.params.fileId || '')
  if (!fileId) {
    store.error = { title: '参数错误', message: '缺少文件 ID 参数' }
    return
  }
  await store.loadVideo({
    node_id: fileId,
    node_name: fileName.value,
    file_size: Number(route.query.size || 0),
    node_type: 'FILE',
  })
  lastSavedSecond.value = Math.floor(store.savedProgress?.current_time || 0)
  // 切换历史视频后立即刷新当前条目和可播放资源统计。
  await store.loadSidebarData()
  await loadStarState()
}

async function loadStarState(): Promise<void> {
  if (!store.currentFile?.node_id) return
  try { isStarred.value = Boolean(await checkFileStarredApi(store.currentFile.node_id)) } catch { isStarred.value = false }
}

async function toggleStar(): Promise<void> {
  const fileId = store.currentFile?.node_id
  if (!fileId || starLoading.value) return
  starLoading.value = true
  try { if (isStarred.value) await removeFileStarApi(fileId); else await addFileStarApi(fileId); isStarred.value = !isStarred.value; toast.showToast(isStarred.value ? '已收藏视频' : '已取消收藏', 'success') } catch (cause: any) { toast.showToast(cause?.message || '收藏状态更新失败', 'error') } finally { starLoading.value = false }
}

async function downloadVideo(): Promise<void> {
  const fileId = store.currentFile?.node_id
  if (!fileId || downloading.value) return
  downloading.value = true
  let grant = ''
  try {
    const response = await createDownloadGrantApi(fileId)
    grant = response?.data?.download_grant || response?.data?.operation_token || response?.download_grant || ''
    if (!grant) throw new Error('下载授权创建失败')
    const file = await getFileContentApi(fileId, grant)
    const url = URL.createObjectURL(file instanceof Blob ? file : new Blob([file]))
    const anchor = document.createElement('a'); anchor.href = url; anchor.download = store.currentFile?.node_name || 'video'; anchor.click(); URL.revokeObjectURL(url)
    toast.showToast('视频下载已开始', 'success')
  } catch (cause: any) { toast.showToast(cause?.message || '下载视频失败', 'error') } finally { if (grant) void releaseDownloadGrantApi(grant).catch(() => undefined); downloading.value = false }
}

async function shareVideo(): Promise<void> {
  const fileId = store.currentFile?.node_id
  if (!fileId || sharing.value) return
  sharing.value = true
  try {
    const response = await createShareApi({ resources: [{ type: 'file', id: fileId }], share_name: store.currentFile?.node_name || '视频分享', share_description: '来自 PrivateCloudDisk 视频播放页', expires_in_days: 7, allow_download: true })
    const url = response.data?.share_url
    if (!url) throw new Error('分享链接创建失败')
    await copyText(url)
    toast.showToast('已创建 7 天有效分享链接并复制', 'success')
  } catch (cause: any) { toast.showToast(cause?.message || '创建分享链接失败', 'error') } finally { sharing.value = false }
}

async function copyText(value: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(value)
    return
  }
  // HTTP 本地开发环境可能没有 Clipboard API；回退仍只复制刚由后端创建的分享 URL。
  const input = document.createElement('textarea')
  input.value = value
  input.style.position = 'fixed'
  input.style.opacity = '0'
  document.body.append(input)
  input.select()
  const copied = document.execCommand('copy')
  input.remove()
  if (!copied) throw new Error('浏览器未授予剪贴板权限')
}

function reportVideo(): void { toast.showToast('请在文件详情页使用统一的举报入口提交问题', 'warning') }

function onTimeUpdate(payload: { currentTime: number; duration: number }) {
  // AUDIT FIX [7.4]: 核心组件的时间必须回写 Store，旧实现调用 saveProgress 时始终保存 0 秒。
  store.currentTime = payload.currentTime
  store.duration = payload.duration
  const second = Math.floor(payload.currentTime)
  if (second > 0 && second - lastSavedSecond.value >= 10) {
    lastSavedSecond.value = second
    void store.saveProgress()
  }
}

function onProgressReport(payload: { currentTime: number; duration: number }) {
  store.currentTime = payload.currentTime
  store.duration = payload.duration
  void store.saveProgress()
}

function onPlayerError(message: string) {
  store.error = { title: '播放错误', message }
}

async function playHistoryItem(item: any) {
  if (item.file_id === store.currentFile?.node_id) return
  await store.saveProgress()
  await router.replace({
    name: 'VideoPlayer',
    params: { fileId: item.file_id },
    query: { name: item.file_name || '视频播放', from: 'history' },
  })
}

async function handleGoBack() {
  await store.saveProgress()
  store.reset()
  if (window.history.length > 1) router.back()
  else router.replace({ name: 'Dashboard' })
}

function handleRetry() {
  store.error = null
  void initVideo()
}

function formatDuration(value: number | string | undefined): string {
  const total = Math.max(0, Math.floor(Number(value) || 0))
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  return hours > 0
    ? `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`
    : `${minutes}:${String(seconds).padStart(2, '0')}`
}

function formatBytes(value: number | string | undefined): string {
  const bytes = Number(value) || 0
  if (!bytes) return '—'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']; const index = Math.min(units.length - 1, Math.floor(Math.log(bytes) / Math.log(1024)))
  return `${(bytes / 1024 ** index).toFixed(index ? 1 : 0)} ${units[index]}`
}

function saveBeforePageHide() {
  void store.saveProgress()
}

watch(() => route.params.fileId, () => void initVideo(), { immediate: true })

onMounted(() => {
  window.addEventListener('pagehide', saveBeforePageHide)
  themeObserver = new MutationObserver(syncTheme)
  themeObserver.observe(document.documentElement, { attributes: true, attributeFilter: ['class'] })
  window.addEventListener('storage', syncTheme)
})

onUnmounted(() => {
  window.removeEventListener('pagehide', saveBeforePageHide)
  window.removeEventListener('storage', syncTheme)
  themeObserver?.disconnect()
  void store.saveProgress()
  store.reset()
})
</script>

<style scoped>
.video-page { min-height: 100vh; min-height: 100dvh; color: #111827; background: #f5f7fb; }
.video-header { position: sticky; top: 0; z-index: 30; height: 64px; display: flex; align-items: center; justify-content: space-between; padding: 0 clamp(16px, 3vw, 42px); border-bottom: 1px solid #e5e7eb; background: rgba(255,255,255,.94); -webkit-backdrop-filter: blur(14px); backdrop-filter: blur(14px); }
.back-button, .history-heading button { border: 0; background: transparent; cursor: pointer; }
.back-button { display: inline-flex; align-items: center; gap: 10px; color: #374151; font-weight: 600; padding: 10px 12px; border-radius: 10px; }
.back-button:hover { color: #1677ff; background: #eef5ff; }
.brand-mark { display: flex; align-items: center; gap: 9px; color: #17233d; font-weight: 750; letter-spacing: -.02em; }
.brand-mark i { color: #1677ff; font-size: 21px; }
.video-layout { width: min(1680px, 100%); margin: 0 auto; padding: 28px clamp(16px, 3vw, 42px) 48px; display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 28px; align-items: start; }
.player-shell { position: relative; width: 100%; aspect-ratio: 16 / 9; max-height: calc(100vh - 180px); overflow: hidden; border-radius: 14px; background: #050505; box-shadow: 0 18px 48px rgba(15,23,42,.18); }
.player-shell.is-fullscreen { position: fixed; inset: 0; z-index: 9999; width: 100vw; height: 100vh; max-height: none; border-radius: 0; }
.state-overlay { position: absolute; inset: 0; z-index: 10; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px; padding: 32px; color: #f9fafb; text-align: center; background: #090b10; }
.state-overlay > span { color: #9ca3af; max-width: 520px; overflow-wrap: anywhere; }
.spinner { width: 44px; height: 44px; border: 3px solid #303642; border-top-color: #4b9cff; border-radius: 50%; animation: spin .8s linear infinite; }
.error-state > i { font-size: 40px; color: #ff6b6b; }
.state-actions { display: flex; gap: 10px; margin-top: 8px; }
.state-actions button { border: 0; border-radius: 9px; padding: 10px 18px; color: #fff; background: #1677ff; cursor: pointer; }
.state-actions .secondary { background: #2b303b; }
.video-meta { padding: 20px 2px; }
.video-meta h1 {
  margin: 0;
  /* AUDIT FIX [2.4]（需求四-4）: 文件名降为播放器后的次级信息，避免与视频画面争夺视觉重心。 */
  font-size: clamp(17px, 1.35vw, 22px);
  line-height: 1.42;
  overflow-wrap: anywhere;
}
.meta-row { display: flex; flex-wrap: wrap; gap: 10px 18px; margin-top: 12px; color: #6b7280; font-size: 13px; }
.resume-chip { padding: 3px 9px; color: #1769cc; background: #e9f3ff; border-radius: 999px; }
.history-panel { position: sticky; top: 88px; overflow: hidden; border: 1px solid #e4e8f0; border-radius: 16px; background: #fff; box-shadow: 0 10px 30px rgba(15,23,42,.06); }
.statistics-card { display: flex; justify-content: space-between; align-items: center; margin: 16px; padding: 18px; border-radius: 13px; color: #fff; background: linear-gradient(135deg,#165dff,#6b8cff); }
.statistics-card div { display: grid; grid-template-columns: auto 1fr; gap: 2px 8px; align-items: baseline; }
.statistics-card .eyebrow { grid-column: 1 / -1; opacity: .78; font-size: 12px; }
.statistics-card strong { font-size: 30px; }
.statistics-card div > span:last-child { font-size: 13px; opacity: .9; }
.statistics-card > i { font-size: 32px; opacity: .65; }
.history-heading { display: flex; align-items: center; justify-content: space-between; padding: 2px 18px 12px; }
.history-heading h2 { display: inline; margin: 0 8px 0 0; font-size: 17px; }
.history-heading span { color: #9ca3af; font-size: 12px; }
.history-heading button { color: #9ca3af; padding: 7px; }
.history-list { max-height: calc(100vh - 282px); overflow: auto; padding: 0 10px 12px; scrollbar-width: thin; }
.history-item { width: 100%; display: grid; grid-template-columns: 128px minmax(0,1fr); gap: 11px; padding: 9px; border: 0; border-radius: 11px; color: inherit; text-align: left; background: transparent; cursor: pointer; transition: background .18s, transform .18s; }
.history-item:hover { background: #f3f7fc; transform: translateY(-1px); }
.history-item.active { background: #eaf3ff; }
.history-thumb { position: relative; height: 72px; overflow: hidden; border-radius: 8px; background: #111827; }
.history-thumb :deep(.thumbnail-image-wrapper), .history-thumb :deep(.thumbnail-img) { max-width: none; max-height: none; width: 100%; height: 100%; border-radius: 0; }
.history-thumb small { position: absolute; right: 5px; bottom: 5px; padding: 2px 5px; border-radius: 4px; color: #fff; background: rgba(0,0,0,.72); font-size: 11px; }
.playing-icon { position: absolute; left: 7px; bottom: 7px; color: #58a6ff; }
.history-info { min-width: 0; display: flex; flex-direction: column; gap: 6px; padding-top: 2px; }
.history-info strong { display: -webkit-box; overflow: hidden; font-size: 13px; line-height: 1.4; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow-wrap: anywhere; }
.history-info span { color: #8a93a2; font-size: 11px; }
.history-info progress { width: 100%; height: 3px; border: 0; accent-color: #1677ff; }
.history-empty { display: grid; place-items: center; padding: 54px 20px 66px; color: #9ca3af; }
.history-empty i { font-size: 30px; }.history-empty p { margin: 12px 0 4px; color: #4b5563; }.history-empty span { font-size: 12px; }
.history-skeleton { padding: 0 18px 20px; }.skeleton-row { display: grid; grid-template-columns: 110px 1fr; gap: 10px; margin: 12px 0; }.skeleton-row > span { height: 62px; border-radius: 8px; background: #edf0f5; }.skeleton-row div i { display: block; height: 11px; margin: 8px 0; border-radius: 6px; background: #edf0f5; }
.video-header__left { display:flex;align-items:center;min-width:0;gap:4px; }.video-header__divider { width:1px;height:20px;margin:0 5px;background:#d8dee9; }.video-header__link { display:inline-flex;align-items:center;gap:7px;border-radius:9px;padding:9px 10px;color:#64748b;font-size:13px;font-weight:600;text-decoration:none; }.video-header__link:hover { color:#1677ff;background:#eef5ff; }.video-breadcrumb { display:flex;align-items:center;gap:8px;min-width:0;margin:0 0 12px;color:#738095;font-size:12px; }.video-breadcrumb button { border:0;background:transparent;padding:0;color:#4b78ad;font:inherit;cursor:pointer; }.video-breadcrumb span { overflow:hidden;text-overflow:ellipsis;white-space:nowrap; }.video-breadcrumb > i { color:#a7b1bf; }.video-info { padding:20px 2px 4px; }.video-info__title-row { display:flex;align-items:flex-start;justify-content:space-between;gap:12px; }.video-info h1 { margin:0;font-size:clamp(19px,1.55vw,25px);line-height:1.42;letter-spacing:-.02em;overflow-wrap:anywhere; }.video-info__more { display:inline-flex;width:32px;height:32px;flex:0 0 32px;align-items:center;justify-content:center;border:1px solid #e1e7ef;border-radius:8px;background:#fff;color:#64748b;cursor:pointer; }.video-info__more:hover { color:#1677ff;border-color:#a8c9ed;background:#f4f9ff; }.video-actions { display:flex;flex-wrap:wrap;gap:8px;margin-top:17px;padding:13px 0;border-top:1px solid #edf0f4;border-bottom:1px solid #edf0f4; }.video-actions button { display:inline-flex;min-height:34px;align-items:center;gap:7px;border:0;border-radius:8px;background:#f2f5f9;padding:0 11px;color:#536277;font-size:12px;font-weight:650;cursor:pointer;transition:background .16s,color .16s,transform .16s; }.video-actions button:hover:not(:disabled),.video-actions button.active { background:#e9f3ff;color:#1677ff; }.video-actions button:disabled { opacity:.58;cursor:wait; }.video-actions button:hover:not(:disabled) { transform:translateY(-1px); }.video-actions__quiet { margin-left:auto; }.video-description { position:relative;max-width:920px;margin-top:15px;color:#536277;font-size:13px;line-height:1.75; }.video-description p { display:-webkit-box;overflow:hidden;margin:0;-webkit-box-orient:vertical;-webkit-line-clamp:2; }.video-description.expanded p { display:block; }.video-description button { margin-top:4px;border:0;background:transparent;padding:0;color:#1677ff;font-size:12px;font-weight:650;cursor:pointer; }.video-tags { display:flex;flex-wrap:wrap;gap:7px;margin-top:13px; }.video-tags span { display:inline-flex;align-items:center;gap:5px;border:1px solid #dce6f1;border-radius:999px;background:#f8fbff;padding:4px 8px;color:#5d6d82;font-size:11px; }.video-tags i { color:#5d91c5; }.video-technical-info { display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:8px;margin:16px 0 0;border-radius:10px;background:#f6f8fb;padding:11px; }.video-technical-info div { min-width:0;padding:4px 7px; }.video-technical-info dt { color:#8b97a6;font-size:10px; }.video-technical-info dd { overflow:hidden;margin:4px 0 0;color:#344256;font-size:12px;font-weight:650;text-overflow:ellipsis;white-space:nowrap; }.history-panel { transition:width .2s ease,flex-basis .2s ease,padding .2s ease; }.history-panel--collapsed { width:42px;min-height:420px;flex-basis:42px;border-radius:12px; }.history-panel__collapse { position:absolute;top:11px;right:11px;z-index:2;display:inline-flex;width:28px;height:28px;align-items:center;justify-content:center;border:1px solid #e1e7ef;border-radius:7px;background:#fff;color:#64748b;cursor:pointer; }.history-panel__collapse:hover { color:#1677ff;border-color:#a8c9ed;background:#f4f9ff; }.history-panel--collapsed .history-panel__collapse { right:6px; }.history-heading { padding-right:52px; }.video-panel-eyebrow { margin:0 0 3px;color:#6b91ba;font-size:9px;font-weight:800;letter-spacing:.13em; }.history-panel__foot { display:flex;align-items:center;justify-content:space-between;gap:8px;border-top:1px solid #edf0f4;padding:10px 15px;color:#8a96a6;font-size:10px; }.history-panel__foot span { display:inline-flex;align-items:center;gap:4px; }.history-panel__foot button { border:0;background:transparent;padding:0;color:#1677ff;font-size:11px;font-weight:700;cursor:pointer; }.history-panel__foot button i { margin-left:4px; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) { .video-layout { grid-template-columns: 1fr; }.history-panel { position: static; }.history-panel--collapsed { width:auto;min-height:42px; }.history-panel--collapsed .history-panel__collapse { position:static;margin:7px; }.history-list { max-height: 420px; }.history-item { grid-template-columns: 150px minmax(0,1fr); } }
@media (max-width: 640px) { .video-header { height: 56px; }.video-header__divider,.video-header__link { display:none; }.brand-mark span { display: none; }.video-layout { padding: 14px 0 30px; gap: 14px; }.video-breadcrumb { padding:0 16px;margin-bottom:10px; }.player-shell { border-radius: 0; box-shadow: none; }.video-info { padding:16px; }.video-info h1 { font-size:19px; }.video-actions { gap:6px; }.video-actions button { min-height:32px;padding:0 9px; }.video-actions__quiet { margin-left:0; }.video-technical-info { grid-template-columns:repeat(2,minmax(0,1fr)); }.history-panel { margin: 0 12px; }.history-item { grid-template-columns: 116px minmax(0,1fr); }.history-thumb { height: 66px; } }
@media (prefers-color-scheme: dark) { .video-page { color:#d7dee8;background:#0f141c; }.video-header { border-color:#273140;background:rgba(16,22,31,.94); }.back-button,.brand-mark { color:#e6edf6; }.video-header__divider { background:#334155; }.video-header__link { color:#aebdce; }.video-header__link:hover,.back-button:hover { color:#7fbeff;background:#172b42; }.history-panel { border-color:#283444;background:#151c26;box-shadow:none; }.history-heading h2,.history-empty p,.history-info strong { color:#e0e7f0; }.history-item:hover { background:#1d2734; }.history-item.active { background:#17304b; }.video-info h1 { color:#f0f5fb; }.meta-row,.video-description { color:#aebdce; }.video-actions { border-color:#283444; }.video-actions button,.video-info__more { border-color:#344253;background:#1d2734;color:#b9c6d5; }.video-actions button:hover:not(:disabled),.video-actions button.active,.video-info__more:hover { border-color:#376b9c;background:#17304b;color:#8fc7ff; }.video-tags span { border-color:#344253;background:#17212d;color:#b9c6d5; }.video-technical-info { background:#17212d; }.video-technical-info dd { color:#d7e0ea; }.history-panel__foot { border-color:#283444; }.history-skeleton .skeleton-row>span,.history-skeleton .skeleton-row div i { background:#263241; } }
/* 平台深色模式优先于系统偏好；选择器与上方媒体查询保持同一组语义。 */
.video-page--dark { color:#d7dee8;background:#0f141c; }.video-page--dark .video-header { border-color:#273140;background:rgba(16,22,31,.94); }.video-page--dark .back-button,.video-page--dark .brand-mark { color:#e6edf6; }.video-page--dark .video-header__divider { background:#334155; }.video-page--dark .video-header__link { color:#aebdce; }.video-page--dark .video-header__link:hover,.video-page--dark .back-button:hover { color:#7fbeff;background:#172b42; }.video-page--dark .history-panel { border-color:#283444;background:#151c26;box-shadow:none; }.video-page--dark .history-heading h2,.video-page--dark .history-empty p,.video-page--dark .history-info strong { color:#e0e7f0; }.video-page--dark .history-item:hover { background:#1d2734; }.video-page--dark .history-item.active { background:#17304b; }.video-page--dark .video-info h1 { color:#f0f5fb; }.video-page--dark .meta-row,.video-page--dark .video-description { color:#aebdce; }.video-page--dark .video-actions { border-color:#283444; }.video-page--dark .video-actions button,.video-page--dark .video-info__more { border-color:#344253;background:#1d2734;color:#b9c6d5; }.video-page--dark .video-actions button:hover:not(:disabled),.video-page--dark .video-actions button.active,.video-page--dark .video-info__more:hover { border-color:#376b9c;background:#17304b;color:#8fc7ff; }.video-page--dark .video-tags span { border-color:#344253;background:#17212d;color:#b9c6d5; }.video-page--dark .video-technical-info { background:#17212d; }.video-page--dark .video-technical-info dd { color:#d7e0ea; }.video-page--dark .history-panel__foot { border-color:#283444; }.video-page--dark .history-skeleton .skeleton-row>span,.video-page--dark .history-skeleton .skeleton-row div i { background:#263241; }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; } }
</style>
