<!-- src/components/auth/LoginFormQR.vue -->
<template>
  <div class="grid gap-4">
    <!-- 标题 -->
    <div class="text-center">
      <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-2.5 py-1.5 text-xs font-extrabold text-primary">
        <i class="fa fa-qrcode"></i>
        扫码登录
      </span>
      <p class="mt-2 text-sm text-slate-500">使用 CloudDrive 移动端扫描二维码快速登录</p>
    </div>

    <!-- 二维码区域 -->
    <div
      class="relative mx-auto flex h-56 w-56 items-center justify-center rounded-xl border-2 p-3 transition duration-300"
      :class="qrBorderClass"
      @contextmenu.prevent
    >
      <!-- 加载中 -->
      <div v-if="qrStatus === 'loading'" class="flex flex-col items-center gap-3 text-slate-400">
        <i class="fa fa-spinner fa-spin text-4xl"></i>
        <span class="text-sm font-bold">正在生成二维码</span>
      </div>

      <!-- 二维码 Canvas（企业级渲染：中心嵌入品牌 Logo，防拖拽/防下载） -->
      <canvas
        ref="qrCanvasRef"
        v-show="qrStatus === 'ready' || qrStatus === 'scanning'"
        width="200"
        height="200"
        class="qr-canvas h-[200px] w-[200px] rounded-lg"
        draggable="false"
      ></canvas>

      <!-- 已过期遮罩 -->
      <div
        v-if="qrStatus === 'expired'"
        class="absolute inset-0 flex flex-col items-center justify-center gap-3 rounded-xl bg-white/90"
      >
        <i class="fa fa-refresh text-4xl text-slate-400"></i>
        <span class="text-sm font-bold text-slate-500">二维码已过期</span>
        <button
          type="button"
          class="rounded-lg bg-primary px-4 py-1.5 text-sm font-extrabold text-white transition hover:bg-[#0e4fe0]"
          @click="isTestMode ? handleTestQR() : refreshQRCode()"
        >
          点击刷新
        </button>
      </div>

      <!-- 已确认遮罩 -->
      <div
        v-if="qrStatus === 'confirmed'"
        class="absolute inset-0 flex flex-col items-center justify-center gap-3 rounded-xl bg-white/90"
      >
        <div class="flex h-14 w-14 items-center justify-center rounded-full bg-green-100">
          <i class="fa fa-check text-2xl text-green-600"></i>
        </div>
        <span class="text-sm font-bold text-green-700">扫码成功</span>
        <span class="text-xs text-slate-500">正在跳转...</span>
      </div>

      <!-- 错误遮罩 -->
      <div
        v-if="qrStatus === 'error'"
        class="absolute inset-0 flex flex-col items-center justify-center gap-3 rounded-xl bg-white/90"
      >
        <i class="fa fa-exclamation-circle text-4xl text-danger"></i>
        <span class="text-sm font-bold text-slate-500">{{ qrError || '加载失败' }}</span>
        <button
          type="button"
          class="rounded-lg bg-primary px-4 py-1.5 text-sm font-extrabold text-white transition hover:bg-[#0e4fe0]"
          @click="isTestMode ? handleTestQR() : refreshQRCode()"
        >
          重新加载
        </button>
      </div>

      <!-- 扫描中提示 -->
      <div
        v-if="qrStatus === 'scanning'"
        class="absolute inset-0 flex items-center justify-center rounded-xl bg-primary/10"
      >
        <div class="flex flex-col items-center gap-2">
          <i class="fa fa-check-circle text-3xl text-primary"></i>
          <span class="text-sm font-extrabold text-primary">已扫描，请在手机上确认</span>
        </div>
      </div>
    </div>

    <!-- 二维码状态提示 -->
    <div class="flex items-center justify-center gap-2 text-sm">
      <span
        class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-bold"
        :class="statusClass"
      >
        <i :class="statusIcon"></i>
        {{ statusText }}
      </span>
      <span v-if="qrStatus === 'ready' || qrStatus === 'scanning'" class="text-xs text-slate-400">
        {{ formatCountdown(qrCountdown) }} 后过期
      </span>
      <!-- 测试模式标签 -->
      <span v-if="isTestMode" class="inline-flex items-center gap-1 rounded-full bg-amber-50 px-2 py-0.5 text-xs font-bold text-amber-600">
        <i class="fa fa-flask"></i>
        测试
      </span>
    </div>

    <!-- 底部操作 -->
    <div class="flex items-center justify-center gap-4">
      <button
        type="button"
        class="inline-flex items-center gap-1.5 text-sm font-bold text-slate-500 transition hover:text-primary"
        :disabled="qrStatus === 'loading'"
        @click="isTestMode ? handleTestQR() : refreshQRCode()"
      >
        <i class="fa fa-refresh" :class="{ 'fa-spin': qrStatus === 'loading' }"></i>
        刷新二维码
      </button>
      <span class="text-slate-300">|</span>
      <button
        type="button"
        class="inline-flex items-center gap-1.5 text-sm font-bold transition"
        :class="isTestMode ? 'text-amber-600 hover:text-amber-700' : 'text-slate-400 hover:text-slate-600'"
        @click="toggleTestMode"
      >
        <i class="fa fa-flask"></i>
        {{ isTestMode ? '退出测试' : '测试二维码' }}
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, watch, nextTick } from 'vue'
import { useQRCode } from '@/composables/useQRCode'
import { useAuthStore } from '@/stores/authStore'

const emit = defineEmits<{
  loginSuccess: []
}>()

const authStore = useAuthStore()
const qrCanvasRef = ref<HTMLCanvasElement | null>(null)

const {
  qrCodeUrl,
  qrStatus,
  qrCountdown,
  qrError,
  renderQRToCanvas,
  startQRCodeAuth,
  refreshQRCode,
  generateTestQRCode,
  destroy,
} = useQRCode()

const isTestMode = ref(false)

// 二维码边框颜色
const qrBorderClass = computed(() => {
  switch (qrStatus.value) {
    case 'ready': return 'border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.08)]'
    case 'scanning': return 'border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.15)]'
    case 'confirmed': return 'border-green-500 shadow-[0_0_0_4px_rgba(34,197,94,0.15)]'
    case 'expired': return 'border-slate-300'
    case 'error': return 'border-danger'
    default: return 'border-slate-200'
  }
})

const statusClass = computed(() => {
  switch (qrStatus.value) {
    case 'ready': return 'bg-primary/10 text-primary'
    case 'scanning': return 'bg-primary/10 text-primary'
    case 'confirmed': return 'bg-green-50 text-green-700'
    case 'expired': return 'bg-slate-100 text-slate-500'
    case 'error': return 'bg-danger/10 text-danger'
    default: return 'bg-slate-100 text-slate-500'
  }
})

const statusIcon = computed(() => {
  switch (qrStatus.value) {
    case 'ready': return 'fa fa-clock-o'
    case 'scanning': return 'fa fa-mobile'
    case 'confirmed': return 'fa fa-check-circle'
    case 'expired': return 'fa fa-exclamation-circle'
    case 'error': return 'fa fa-times-circle'
    default: return 'fa fa-spinner fa-spin'
  }
})

const statusText = computed(() => {
  switch (qrStatus.value) {
    case 'ready': return '等待扫码'
    case 'scanning': return '已扫描，请确认'
    case 'confirmed': return '登录成功'
    case 'expired': return '已过期'
    case 'error': return '出错了'
    default: return '加载中'
  }
})

function formatCountdown(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

// ============================================================
// Canvas 渲染：监听 URL 变化，渲染二维码 + 中心 Logo
// ============================================================

watch(qrCodeUrl, async (url) => {
  if (!url || !qrCanvasRef.value) return
  await nextTick()
  try {
    await renderQRToCanvas(qrCanvasRef.value, url, 200)
  } catch {
    // 渲染失败由 composable 状态管理处理
  }
})

// ============================================================
// 测试模式
// ============================================================

function toggleTestMode() {
  isTestMode.value = !isTestMode.value
  if (isTestMode.value) {
    handleTestQR()
  } else {
    // 切换回正常模式，重新从后端获取授权二维码
    startQRCodeAuth((token) => {
      authStore.saveDeviceToken(token)
      emit('loginSuccess')
    })
  }
}

/** 生成测试二维码 */
async function handleTestQR() {
  try {
    await generateTestQRCode()
  } catch {
    // 错误已在 composable 中处理
  }
}

// 监听成功状态
watch(qrStatus, (status) => {
  if (status === 'confirmed') {
    setTimeout(() => emit('loginSuccess'), 800)
  }
})

// 启动二维码授权
onMounted(async () => {
  await startQRCodeAuth((token) => {
    authStore.saveDeviceToken(token)
    emit('loginSuccess')
  })
})

onBeforeUnmount(() => {
  destroy()
})
</script>

<style scoped>
/* 企业级防拖拽/防下载：Canvas 元素禁止交互式操作 */
.qr-canvas {
  user-select: none;
  -webkit-user-select: none;
  -webkit-user-drag: none;
  -webkit-touch-callout: none;
  pointer-events: none;
}
</style>