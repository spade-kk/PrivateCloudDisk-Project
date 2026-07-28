<template>
  <div class="video-page">
    <header class="video-header">
      <button class="back-button" type="button" @click="handleGoBack">
        <i class="fa fa-arrow-left" aria-hidden="true"></i>
        <span>返回网盘</span>
      </button>
      <div class="brand-mark"><i class="fa fa-cloud"></i><span>PrivateCloudDisk</span></div>
    </header>

    <main class="video-layout">
      <section class="video-primary" aria-label="视频播放区域">
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

        <div class="video-meta">
          <h1>{{ store.currentFile?.node_name || fileName }}</h1>
          <div class="meta-row">
            <span><i class="fa fa-play-circle"></i> HLS 自适应流媒体</span>
            <span v-if="store.streamInfo?.duration">{{ formatDuration(store.streamInfo.duration) }}</span>
            <span v-if="store.savedProgress?.current_time" class="resume-chip">已续播 {{ formatDuration(store.savedProgress.current_time) }}</span>
          </div>
        </div>
      </section>

      <aside class="history-panel" aria-label="播放历史">
        <div class="statistics-card">
          <div>
            <span class="eyebrow">账号媒体库</span>
            <strong>{{ store.playableVideoCount }}</strong>
            <span>个视频可播放</span>
          </div>
          <i class="fa fa-film"></i>
        </div>

        <div class="history-heading">
          <div><h2>播放历史</h2><span>最近 {{ store.historyTotal }} 条</span></div>
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

const route = useRoute()
const router = useRouter()
const store = useVideoPlayerStore()
const playerRef = ref<InstanceType<typeof VideoPlayerCore> | null>(null)
const lastSavedSecond = ref(0)
const fileName = computed(() => String(route.query.name || '视频播放'))

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
}

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

function saveBeforePageHide() {
  void store.saveProgress()
}

watch(() => route.params.fileId, () => void initVideo(), { immediate: true })

onMounted(() => {
  window.addEventListener('pagehide', saveBeforePageHide)
})

onUnmounted(() => {
  window.removeEventListener('pagehide', saveBeforePageHide)
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
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1100px) { .video-layout { grid-template-columns: 1fr; }.history-panel { position: static; }.history-list { max-height: 420px; }.history-item { grid-template-columns: 150px minmax(0,1fr); } }
@media (max-width: 640px) { .video-header { height: 56px; }.brand-mark span { display: none; }.video-layout { padding: 14px 0 30px; gap: 14px; }.player-shell { border-radius: 0; box-shadow: none; }.video-meta { padding: 16px; }.history-panel { margin: 0 12px; }.history-item { grid-template-columns: 116px minmax(0,1fr); }.history-thumb { height: 66px; } }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { animation-duration: .01ms !important; transition-duration: .01ms !important; } }
</style>
