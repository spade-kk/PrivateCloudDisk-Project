<template>
  <Transition name="file-hover-preview">
    <section
      v-if="visible && previewable"
      class="file-hover-preview"
      :style="previewStyle"
      aria-live="polite"
      @click.stop
      @contextmenu.stop
    >
      <div class="preview-media">
        <video
          v-if="videoFile && mediaUrl"
          ref="videoRef"
          :src="mediaUrl"
          autoplay
          muted
          loop
          playsinline
          preload="auto"
          controlslist="nodownload noplaybackrate"
          disablepictureinpicture
          @loadedmetadata="syncVideoRatio"
        ></video>
        <ThumbnailImage
          v-else
          :file-id="fileId"
          :file-name="fileName"
          size="large"
          icon-size="3rem"
          fit="contain"
        />
        <div v-if="loading" class="preview-loading">
          <i class="fa fa-circle-o-notch fa-spin"></i>
          正在加载清晰预览
        </div>
      </div>
      <footer>
        <span class="preview-kind">{{ videoFile ? '30 秒预览' : '文件预览' }}</span>
        <strong>{{ fileName }}</strong>
      </footer>
    </section>
  </Transition>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import ThumbnailImage from './ThumbnailImage.vue'
import { getVideoHoverPreviewUrl } from '@/api/modules/video'
import { loadAuthenticatedMedia } from '@/utils/imageCache'
import { isImage, isOffice, isPdf, isVideo } from '@/utils/previewHelper'

const props = withDefaults(defineProps<{
  fileId: string
  fileName: string
  armed: boolean
  delay?: number
}>(), {
  // 需求八：默认 1.0 秒，可通过 VITE_FILE_HOVER_PREVIEW_DELAY_MS 调整，统一作用于全部可预览类型。
  delay: Math.min(3000, Math.max(800, Number(import.meta.env.VITE_FILE_HOVER_PREVIEW_DELAY_MS || 1000))),
})

const visible = ref(false)
const loading = ref(false)
const mediaUrl = ref('')
const videoRef = ref<HTMLVideoElement | null>(null)
const previewWidth = ref(400)
const previewHeight = ref(260)
let hoverTimer: ReturnType<typeof setTimeout> | null = null
let requestController: AbortController | null = null

const videoFile = computed(() => isVideo(props.fileName))
const previewable = computed(() =>
  isImage(props.fileName) || videoFile.value || isPdf(props.fileName) || isOffice(props.fileName),
)
const previewStyle = computed(() => ({
  width: `${previewWidth.value}px`,
  height: `${previewHeight.value + 54}px`,
}))

function clearTimer() {
  if (hoverTimer) {
    clearTimeout(hoverTimer)
    hoverTimer = null
  }
}

function releaseMedia() {
  requestController?.abort()
  requestController = null
  if (mediaUrl.value) {
    URL.revokeObjectURL(mediaUrl.value)
    mediaUrl.value = ''
  }
}

async function loadVideoPreview() {
  loading.value = true
  requestController = new AbortController()
  try {
    mediaUrl.value = await loadAuthenticatedMedia(
      getVideoHoverPreviewUrl(props.fileId),
      requestController.signal,
    )
  } catch (error) {
    // 预览素材尚未生成时保留高清首帧，不影响文件项目的正常操作。
    if (!(error instanceof DOMException && error.name === 'AbortError')) mediaUrl.value = ''
  } finally {
    loading.value = false
  }
}

function syncVideoRatio() {
  const video = videoRef.value
  if (!video?.videoWidth || !video.videoHeight) return
  const ratio = video.videoWidth / video.videoHeight
  const targetHeight = ratio < 1 ? 300 : 230
  previewWidth.value = Math.round(Math.min(480, Math.max(260, targetHeight * ratio)))
  previewHeight.value = Math.round(Math.min(320, Math.max(180, previewWidth.value / ratio)))
}

watch(
  () => props.armed,
  (armed) => {
    clearTimer()
    if (!armed || !previewable.value) {
      visible.value = false
      loading.value = false
      releaseMedia()
      return
    }
    /*
     * AUDIT FIX [2.2]（需求二-2.2/2.3/2.4）：
     * 原行为没有统一的延迟悬停预览；新行为按统一可配置延迟（默认 1.0 秒）原位覆盖展示，
     * 组件绝对定位且不参与文档流，因此不会挤压网格或列表中的其他项目。
     */
    hoverTimer = setTimeout(() => {
      visible.value = true
      if (videoFile.value) void loadVideoPreview()
    }, props.delay)
  },
  { immediate: true },
)

watch(
  () => props.fileId,
  () => {
    visible.value = false
    releaseMedia()
  },
)

onBeforeUnmount(() => {
  clearTimer()
  releaseMedia()
})
</script>

<style scoped>
.file-hover-preview {
  position: absolute;
  z-index: 60;
  left: 50%;
  top: 50%;
  display: flex;
  min-width: 260px;
  max-width: min(480px, calc(100vw - 32px));
  min-height: 234px;
  max-height: min(374px, calc(100vh - 80px));
  flex-direction: column;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.88);
  border-radius: 14px;
  background: #fff;
  box-shadow: 0 20px 56px rgba(17, 24, 39, 0.24), 0 4px 16px rgba(17, 24, 39, 0.12);
  cursor: default;
  transform: translate(-50%, -50%);
  transform-origin: center;
}

.preview-media {
  position: relative;
  display: flex;
  min-height: 180px;
  flex: 1;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: linear-gradient(145deg, #111827, #1f2937);
}

.preview-media video {
  width: 100%;
  height: 100%;
  object-fit: contain;
  pointer-events: none;
}

.preview-loading {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background: rgba(17, 24, 39, 0.72);
  color: #fff;
  font-size: 12px;
  backdrop-filter: blur(4px);
}

footer {
  display: flex;
  min-height: 54px;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  background: rgba(255, 255, 255, 0.98);
}

footer strong {
  min-width: 0;
  overflow: hidden;
  color: #303133;
  font-size: 13px;
  line-height: 18px;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-kind {
  flex: none;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.1);
  padding: 3px 7px;
  color: #165dff;
  font-size: 10px;
  font-weight: 700;
}

.file-hover-preview-enter-active,
.file-hover-preview-leave-active {
  transition: opacity 220ms ease, transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.file-hover-preview-enter-from,
.file-hover-preview-leave-to {
  opacity: 0;
  transform: translate(-50%, -50%) scale(0.72);
}

@media (hover: none), (pointer: coarse), (max-width: 767px) {
  .file-hover-preview {
    display: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .file-hover-preview-enter-active,
  .file-hover-preview-leave-active {
    transition-duration: 1ms;
  }
}
</style>
