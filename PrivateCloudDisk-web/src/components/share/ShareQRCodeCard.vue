<template>
  <section class="qr-card" aria-labelledby="share-qr-title">
    <div class="qr-copy">
      <p class="qr-kicker">移动端访问</p>
      <h3 id="share-qr-title">扫码打开这份分享</h3>
      <p>二维码已包含当前分享地址<span v-if="containsPassword">和提取码</span>，请谨慎转发。</p>
    </div>
    <button
      ref="triggerButton"
      type="button"
      class="qr-preview"
      aria-haspopup="dialog"
      :aria-label="qrError ? '重新生成分享二维码' : '放大分享二维码'"
      @click="openDialog"
    >
      <canvas
        ref="previewCanvas"
        width="116"
        height="116"
        draggable="false"
        aria-label="当前分享链接二维码"
        @contextmenu.prevent
        @dragstart.prevent
      ></canvas>
      <span class="qr-preview-action">
        <i class="fa fa-search-plus" aria-hidden="true"></i>
        点击放大
      </span>
    </button>

    <p v-if="qrError" class="qr-error" role="alert">{{ qrError }}</p>

    <Teleport to="body">
      <Transition name="qr-dialog">
        <div
          v-if="dialogOpen"
          class="qr-dialog-backdrop"
          role="presentation"
          @click.self="closeDialog"
        >
          <section
            class="qr-dialog-panel"
            role="dialog"
            aria-modal="true"
            aria-labelledby="share-qr-dialog-title"
            aria-describedby="share-qr-dialog-desc"
          >
            <button
              ref="closeButton"
              type="button"
              class="qr-dialog-close"
              aria-label="关闭二维码弹窗"
              @click="closeDialog"
            >
              <i class="fa fa-times" aria-hidden="true"></i>
            </button>

            <div class="qr-dialog-brand" aria-hidden="true">
              <i class="fa fa-cloud"></i>
            </div>
            <p class="qr-dialog-kicker">PrivateCloudDisk</p>
            <h2 id="share-qr-dialog-title">用手机扫一扫，继续查看分享</h2>
            <p id="share-qr-dialog-desc">
              在微信或浏览器中扫描二维码，即可打开“{{ title || '分享内容' }}”。
              <span v-if="containsPassword">提取码已随链接携带，无需再次输入。</span>
            </p>

            <div class="qr-large-wrap">
              <canvas
                ref="dialogCanvas"
                width="320"
                height="320"
                draggable="false"
                aria-label="放大的当前分享链接二维码"
                @contextmenu.prevent
                @dragstart.prevent
              ></canvas>
              <span class="qr-corner qr-corner--tl"></span>
              <span class="qr-corner qr-corner--br"></span>
            </div>

            <div class="qr-safety-note">
              <i class="fa fa-shield" aria-hidden="true"></i>
              <span>仅向可信的人分享此二维码；链接失效后二维码也将无法访问。</span>
            </div>
          </section>
        </div>
      </Transition>
    </Teleport>
  </section>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useQRCode } from '@/composables/useQRCode'

const props = defineProps<{
  url: string
  title?: string
}>()

const previewCanvas = ref<HTMLCanvasElement | null>(null)
const dialogCanvas = ref<HTMLCanvasElement | null>(null)
const triggerButton = ref<HTMLButtonElement | null>(null)
const closeButton = ref<HTMLButtonElement | null>(null)
const dialogOpen = ref(false)
const qrError = ref('')
const { renderQRToCanvas } = useQRCode({ logoRatio: 0.18, logoUrl: '/favicon.svg' })
const containsPassword = computed(() => new URL(props.url, window.location.origin).searchParams.has('pwd'))

async function renderPreview() {
  if (!previewCanvas.value || !props.url) return
  qrError.value = ''
  try {
    await renderQRToCanvas(previewCanvas.value, props.url, 116)
  } catch {
    qrError.value = '二维码生成失败，请点击重试'
  }
}

async function renderDialog() {
  if (!dialogCanvas.value || !props.url) return
  try {
    await renderQRToCanvas(dialogCanvas.value, props.url, 320)
  } catch {
    qrError.value = '二维码生成失败，请稍后重试'
  }
}

async function openDialog() {
  dialogOpen.value = true
  await nextTick()
  await renderDialog()
  closeButton.value?.focus()
}

function closeDialog() {
  dialogOpen.value = false
  nextTick(() => triggerButton.value?.focus())
}

function handleKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape' && dialogOpen.value) closeDialog()
}

watch(() => props.url, () => {
  void nextTick(renderPreview)
  if (dialogOpen.value) void nextTick(renderDialog)
})

onMounted(() => {
  void renderPreview()
  window.addEventListener('keydown', handleKeydown)
})

onBeforeUnmount(() => window.removeEventListener('keydown', handleKeydown))
</script>

<style scoped>
.qr-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 132px;
  align-items: center;
  gap: 16px;
  padding: 18px;
  border: 1px solid #e7ecf4;
  border-radius: 18px;
  background: linear-gradient(145deg, #f8fbff, #ffffff 62%);
}

.qr-kicker,
.qr-dialog-kicker { margin: 0 0 5px; color: #165dff; font-size: 11px; font-weight: 800; letter-spacing: 0.12em; text-transform: uppercase; }
.qr-copy h3 { margin: 0; color: #172033; font-size: 15px; font-weight: 760; }
.qr-copy p:last-child { margin: 8px 0 0; color: #718096; font-size: 12px; line-height: 1.65; }

.qr-preview {
  position: relative;
  display: flex;
  min-width: 132px;
  min-height: 154px;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 8px;
  border: 1px solid #e0e7f2;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 10px 25px rgba(15, 23, 42, 0.08);
  cursor: zoom-in;
  transition: transform 180ms ease, border-color 180ms ease, box-shadow 180ms ease;
}

.qr-preview:hover { transform: translateY(-2px); border-color: #b9ceff; box-shadow: 0 14px 32px rgba(22, 93, 255, 0.14); }
.qr-preview:focus-visible,
.qr-dialog-close:focus-visible { outline: 3px solid rgba(22, 93, 255, 0.26); outline-offset: 3px; }
.qr-preview canvas,
.qr-large-wrap canvas { display: block; user-select: none; -webkit-user-drag: none; touch-action: none; }
.qr-preview-action { color: #59708f; font-size: 11px; font-weight: 650; }
.qr-error { grid-column: 1 / -1; margin: 0; color: #dc2626; font-size: 12px; }

.qr-dialog-backdrop {
  position: fixed;
  z-index: 1000;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  background: rgba(6, 12, 24, 0.66);
  -webkit-backdrop-filter: blur(8px);
  backdrop-filter: blur(8px);
}

.qr-dialog-panel {
  position: relative;
  width: min(100%, 470px);
  max-height: calc(100vh - 32px);
  overflow-y: auto;
  padding: 30px 30px 26px;
  border: 1px solid rgba(255, 255, 255, 0.78);
  border-radius: 28px;
  background: #fff;
  box-shadow: 0 32px 90px rgba(0, 0, 0, 0.32);
  text-align: center;
}

.qr-dialog-close {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  width: 44px;
  height: 44px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 14px;
  background: #f2f5f9;
  color: #536176;
  cursor: pointer;
}

.qr-dialog-brand { display: inline-flex; width: 52px; height: 52px; align-items: center; justify-content: center; margin-bottom: 12px; border-radius: 17px; background: #165dff; color: #fff; font-size: 22px; box-shadow: 0 12px 30px rgba(22, 93, 255, 0.28); }
.qr-dialog-panel h2 { margin: 0; color: #14203a; font-size: 23px; line-height: 1.35; }
.qr-dialog-panel > p:not(.qr-dialog-kicker) { margin: 12px auto 20px; max-width: 370px; color: #64748b; font-size: 14px; line-height: 1.75; }

.qr-large-wrap { position: relative; display: inline-block; padding: 15px; border: 1px solid #e7edf7; border-radius: 24px; background: #fff; box-shadow: 0 16px 40px rgba(15, 23, 42, 0.08); }
.qr-corner { position: absolute; width: 25px; height: 25px; border-color: #165dff; border-style: solid; }
.qr-corner--tl { top: 7px; left: 7px; border-width: 3px 0 0 3px; border-top-left-radius: 9px; }
.qr-corner--br { right: 7px; bottom: 7px; border-width: 0 3px 3px 0; border-bottom-right-radius: 9px; }
.qr-safety-note { display: flex; align-items: flex-start; gap: 9px; margin-top: 20px; padding: 12px 14px; border-radius: 14px; background: #f6f8fc; color: #68768c; font-size: 12px; line-height: 1.55; text-align: left; }
.qr-safety-note i { margin-top: 2px; color: #22a06b; }

.qr-dialog-enter-active,
.qr-dialog-leave-active { transition: opacity 180ms ease; }
.qr-dialog-enter-active .qr-dialog-panel,
.qr-dialog-leave-active .qr-dialog-panel { transition: transform 220ms cubic-bezier(.2,.8,.2,1), opacity 180ms ease; }
.qr-dialog-enter-from,
.qr-dialog-leave-to { opacity: 0; }
.qr-dialog-enter-from .qr-dialog-panel,
.qr-dialog-leave-to .qr-dialog-panel { opacity: 0; transform: translateY(14px) scale(0.96); }

@media (max-width: 430px) {
  .qr-card { grid-template-columns: 1fr; }
  .qr-preview { width: 100%; min-height: 146px; }
  .qr-dialog-panel { padding: 26px 16px 22px; border-radius: 22px; }
  .qr-dialog-panel h2 { padding: 0 34px; font-size: 20px; }
  .qr-large-wrap { width: min(100%, 340px); padding: 10px; }
  .qr-large-wrap canvas { width: 100% !important; height: auto !important; }
}

@media (prefers-reduced-motion: reduce) {
  .qr-preview,
  .qr-dialog-enter-active,
  .qr-dialog-leave-active,
  .qr-dialog-enter-active .qr-dialog-panel,
  .qr-dialog-leave-active .qr-dialog-panel { transition: none; }
}
</style>
