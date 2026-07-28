<template>
  <div class="mx-auto max-w-2xl space-y-5 pb-10">
    <!-- ===== 面包屑导航 ===== -->
    <nav class="flex items-center gap-2 text-sm text-neutral-400">
      <router-link to="/app/profile" class="transition hover:text-primary">
        <i class="fa fa-arrow-left mr-1"></i>返回个人中心
      </router-link>
      <i class="fa fa-angle-right text-xs"></i>
      <span class="text-neutral-600">换绑邮箱</span>
    </nav>

    <!-- ===== 页面头部 ===== -->
    <div class="responsive-panel p-5 sm:p-6">
      <div class="flex items-start gap-4">
        <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-violet-50">
          <i class="fa fa-envelope-o text-xl text-violet-600"></i>
        </span>
        <div>
          <h1 class="text-xl font-bold text-neutral-800">换绑邮箱地址</h1>
          <p class="mt-1 text-sm text-neutral-500">
            更换账号绑定的邮箱地址，新邮箱将用于登录通知和安全提醒。
          </p>
        </div>
      </div>
    </div>

    <!-- ===== 步骤指示器 ===== -->
    <div class="responsive-panel p-5 sm:p-6">
      <div class="flex items-center gap-3">
        <div
          v-for="(step, idx) in steps"
          :key="step.key"
          class="flex items-center gap-3"
        >
          <div class="flex items-center gap-2">
            <span
              class="inline-flex h-8 w-8 items-center justify-center rounded-full text-xs font-extrabold transition"
              :class="stepClass(step.key)"
            >
              <i v-if="currentStep > step.key" class="fa fa-check"></i>
              <span v-else>{{ idx + 1 }}</span>
            </span>
            <span class="hidden text-sm font-bold sm:inline" :class="currentStep >= step.key ? 'text-neutral-800' : 'text-neutral-300'">
              {{ step.label }}
            </span>
          </div>
          <span
            v-if="idx < steps.length - 1"
            class="hidden h-[2px] w-8 rounded-full transition sm:block"
            :class="currentStep > step.key ? 'bg-primary' : 'bg-neutral-200'"
          ></span>
        </div>
      </div>
    </div>

    <!-- ===== 步骤 1：输入新邮箱 + 发送验证码 ===== -->
    <div v-if="currentStep === 0" class="responsive-panel p-5 sm:p-6">
      <form @focusin="ensureTurnstile" @submit.prevent="sendVerificationCode" class="space-y-5">
        <!-- 当前邮箱 -->
        <div class="flex items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 p-3">
          <i class="fa fa-envelope text-neutral-400"></i>
          <div class="min-w-0">
            <p class="text-xs text-neutral-400">当前绑定邮箱</p>
            <p class="truncate text-sm font-bold text-neutral-700">{{ currentEmail }}</p>
          </div>
        </div>

        <!-- 新邮箱 -->
        <div>
          <label class="mb-1.5 block text-sm font-bold text-neutral-700">
            新邮箱地址 <span class="text-danger">*</span>
          </label>
          <div
            class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
            :class="focusedField === 'newEmail'
              ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]'
              : 'border-neutral-200'"
          >
            <i class="fa fa-envelope-o text-neutral-400"></i>
            <input
              v-model="form.newEmail"
              type="email"
              autocomplete="email"
              class="min-w-0 flex-1 border-0 bg-transparent outline-none"
              placeholder="请输入新邮箱地址"
              @focus="focusedField = 'newEmail'"
              @blur="focusedField = ''"
            >
            <i v-if="form.newEmail && emailValid" class="fa fa-check text-emerald-600"></i>
          </div>
          <p v-if="form.newEmail && !emailValid" class="mt-1.5 text-xs text-red-500">请输入有效邮箱地址</p>
          <p v-if="form.newEmail && emailValid && isSameEmail" class="mt-1.5 text-xs text-amber-500">
            新邮箱不能与当前邮箱相同
          </p>
        </div>

        <!-- Turnstile 人机验证 -->
        <div class="rounded-lg border border-neutral-200 bg-neutral-50 p-4">
          <div class="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <strong class="text-sm font-extrabold text-neutral-700">安全验证</strong>
              <p class="text-sm text-neutral-500">发送验证码前需完成身份校验</p>
            </div>
            <span
              class="w-fit rounded-full px-2 py-1 text-xs font-extrabold"
              :class="captchaToken
                ? 'bg-emerald-50 text-emerald-700'
                : captchaError
                  ? 'bg-red-50 text-red-600'
                  : 'bg-neutral-100 text-neutral-500'"
            >
              {{ captchaStatusText }}
            </span>
          </div>

          <!-- 【需求十】Turnstile 去除额外边框容器并改为首次输入时渲染。 -->
          <div
            ref="turnstileContainer"
            class="turnstile-widget"
            :class="{ hidden: !turnstileSiteKey }"
          ></div>
          <div v-if="!turnstileSiteKey" class="text-sm text-red-500">未配置 Turnstile Site Key</div>
          <div v-else-if="captchaLoading" class="inline-flex items-center gap-2 text-sm text-neutral-500">
            <i class="fa fa-spinner fa-spin"></i>正在加载验证组件
          </div>
          <p v-if="captchaError" class="mt-2 text-xs text-red-500">{{ captchaError }}</p>
        </div>

        <!-- 错误提示 -->
        <transition
          enter-active-class="transition duration-150 ease-out"
          enter-from-class="-translate-y-1 opacity-0"
          enter-to-class="translate-y-0 opacity-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="translate-y-0 opacity-100"
          leave-to-class="-translate-y-1 opacity-0"
        >
          <div v-if="formError" class="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-sm text-red-600">
            <i class="fa fa-exclamation-circle"></i>
            <span>{{ formError }}</span>
          </div>
        </transition>

        <div class="flex gap-3 pt-2">
          <router-link
            to="/app/profile"
            class="inline-flex min-h-12 items-center justify-center rounded-lg border border-neutral-200 px-6 font-bold text-neutral-600 transition hover:bg-neutral-50"
          >
            取消
          </router-link>
          <button
            type="submit"
            class="inline-flex flex-1 min-h-12 items-center justify-center gap-2.5 rounded-lg bg-primary font-extrabold text-white shadow-[0_14px_30px_rgba(22,93,255,0.25)] transition duration-200 hover:-translate-y-px hover:bg-[#0e4fe0] hover:shadow-[0_18px_40px_rgba(22,93,255,0.30)] disabled:cursor-not-allowed disabled:bg-neutral-300 disabled:shadow-none"
            :disabled="!canSendCode"
          >
            <span>{{ sendingCode ? '正在发送验证码...' : '发送验证码' }}</span>
            <i :class="sendingCode ? 'fa fa-spinner fa-spin' : 'fa fa-paper-plane'"></i>
          </button>
        </div>
      </form>
    </div>

    <!-- ===== 步骤 2：输入验证码 + 确认换绑 ===== -->
    <div v-if="currentStep === 1" class="responsive-panel p-5 sm:p-6">
      <form @submit.prevent="confirmChangeEmail" class="space-y-5">
        <div class="flex items-start gap-3 rounded-lg border border-emerald-200 bg-emerald-50 p-3">
          <i class="fa fa-check-circle mt-0.5 text-emerald-500"></i>
          <div class="text-xs text-emerald-700">
            <p class="font-bold">验证码已发送</p>
            <p class="mt-0.5">一封包含验证码的邮件已发送至 <strong>{{ form.newEmail }}</strong>，请查收并在下方输入验证码完成换绑。</p>
          </div>
        </div>

        <!-- 目标邮箱 -->
        <div class="flex items-center gap-3 rounded-lg border border-neutral-200 bg-neutral-50 p-3">
          <i class="fa fa-envelope text-neutral-400"></i>
          <div class="min-w-0">
            <p class="text-xs text-neutral-400">新邮箱地址</p>
            <p class="truncate text-sm font-bold text-neutral-700">{{ form.newEmail }}</p>
          </div>
          <button
            type="button"
            class="shrink-0 text-xs text-primary hover:underline"
            @click="goBackToStep0"
          >
            修改
          </button>
        </div>

        <!-- 验证码 -->
        <div>
          <label class="mb-1.5 block text-sm font-bold text-neutral-700">
            邮箱验证码 <span class="text-danger">*</span>
          </label>
          <div
            class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 pr-1.5 transition duration-200"
            :class="focusedField === 'code'
              ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]'
              : 'border-neutral-200'"
          >
            <i class="fa fa-key text-neutral-400"></i>
            <input
              v-model="form.code"
              type="text"
              inputmode="numeric"
              class="min-w-0 flex-1 border-0 bg-transparent outline-none"
              placeholder="请输入6位验证码"
              maxlength="6"
              @focus="focusedField = 'code'"
              @blur="focusedField = ''"
            >
            <button
              type="button"
              class="min-h-9 flex-none rounded-lg bg-blue-50 px-2.5 text-xs font-extrabold text-primary transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:bg-neutral-100 disabled:text-neutral-400"
              :disabled="resendDisabled"
              @click="resendCode"
            >
              {{ resendCountdown > 0 ? `${resendCountdown}s 后重发` : '重新发送' }}
            </button>
          </div>
        </div>

        <!-- 错误提示 -->
        <transition
          enter-active-class="transition duration-150 ease-out"
          enter-from-class="-translate-y-1 opacity-0"
          enter-to-class="translate-y-0 opacity-100"
          leave-active-class="transition duration-150 ease-in"
          leave-from-class="translate-y-0 opacity-100"
          leave-to-class="-translate-y-1 opacity-0"
        >
          <div v-if="formError" class="flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2.5 text-sm text-red-600">
            <i class="fa fa-exclamation-circle"></i>
            <span>{{ formError }}</span>
          </div>
        </transition>

        <div class="flex gap-3 pt-2">
          <button
            type="button"
            class="inline-flex min-h-12 items-center justify-center rounded-lg border border-neutral-200 px-6 font-bold text-neutral-600 transition hover:bg-neutral-50"
            @click="goBackToStep0"
          >
            返回修改
          </button>
          <button
            type="submit"
            class="inline-flex flex-1 min-h-12 items-center justify-center gap-2.5 rounded-lg bg-primary font-extrabold text-white shadow-[0_14px_30px_rgba(22,93,255,0.25)] transition duration-200 hover:-translate-y-px hover:bg-[#0e4fe0] hover:shadow-[0_18px_40px_rgba(22,93,255,0.30)] disabled:cursor-not-allowed disabled:bg-neutral-300 disabled:shadow-none"
            :disabled="confirming || form.code.length < 6"
          >
            <span>{{ confirming ? '正在确认换绑...' : '确认换绑邮箱' }}</span>
            <i :class="confirming ? 'fa fa-spinner fa-spin' : 'fa fa-check'"></i>
          </button>
        </div>
      </form>
    </div>

    <!-- ===== 完成页面 ===== -->
    <div v-if="currentStep === 2" class="responsive-panel p-8 text-center sm:p-10">
      <div class="mx-auto flex h-20 w-20 items-center justify-center rounded-full bg-emerald-50">
        <i class="fa fa-check-circle text-4xl text-emerald-500"></i>
      </div>
      <h2 class="mt-5 text-xl font-bold text-neutral-800">邮箱换绑成功</h2>
      <p class="mt-2 text-sm text-neutral-500">
        你的账号邮箱已成功更换为 <strong>{{ form.newEmail }}</strong>
      </p>
      <router-link
        to="/app/profile"
        class="mt-6 inline-flex min-h-12 items-center justify-center gap-2 rounded-lg bg-primary px-6 font-extrabold text-white shadow-[0_14px_30px_rgba(22,93,255,0.25)] transition hover:-translate-y-px hover:bg-[#0e4fe0]"
      >
        <i class="fa fa-arrow-left"></i>返回个人中心
      </router-link>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, reactive, ref } from 'vue'
import { useToastStore } from '@/stores/toastStore'
import { useAuthStore } from '@/stores/authStore'
import { useUserStore } from '@/stores/userStore'
import { sendChangeEmailCodeApi, resendChangeEmailCodeApi, confirmChangeEmailApi } from '@/api/modules/users'

const TURNSTILE_SCRIPT_ID = 'cf-turnstile-changeemail'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'
const MAX_RESEND_COUNT = 8

const toast = useToastStore()
const auth = useAuthStore()
const userStore = useUserStore()

// ── 步骤管理 ──
const steps = [
  { key: 0, label: '输入新邮箱' },
  { key: 1, label: '验证确认' },
  { key: 2, label: '完成' },
]
const currentStep = ref(0)

// ── 表单状态 ──
const form = reactive({
  newEmail: '',
  code: '',
})
const formError = ref('')
const sendingCode = ref(false)
const confirming = ref(false)
const focusedField = ref('')
const resendCountdown = ref(0)
let resendTimer: ReturnType<typeof setInterval> | null = null

// ── 验证码重发机制 ──
const resendToken = ref('')

// ── Turnstile 状态 ──
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const turnstileContainer = ref<HTMLElement | null>(null)
const turnstileWidgetId = ref<string | null>(null)
const captchaToken = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')

// ── 当前邮箱 ──
const currentEmail = computed(() => auth.user?.email || '未绑定')

// ── 校验 ──
const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.newEmail))
const isSameEmail = computed(() => {
  return form.newEmail.toLowerCase() === currentEmail.value.toLowerCase()
})

const canSendCode = computed(() => {
  return (
    !sendingCode.value &&
    emailValid.value &&
    !isSameEmail.value &&
    captchaToken.value
  )
})

const resendDisabled = computed(() => {
  return sendingCode.value || resendCountdown.value > 0
})

const captchaStatusText = computed(() => {
  if (!turnstileSiteKey) return '未配置'
  if (captchaToken.value) return '已通过'
  if (captchaError.value) return '需重试'
  if (captchaLoading.value) return '加载中'
  return '待验证'
})

function stepClass(stepKey: number) {
  if (currentStep.value > stepKey) return 'bg-emerald-500 text-white'
  if (currentStep.value === stepKey) return 'bg-primary text-white'
  return 'bg-neutral-100 text-neutral-400'
}

// ── Turnstile ──
function loadTurnstileScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()

  const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID)
  if (existingScript) {
    return new Promise((resolve, reject) => {
      existingScript.addEventListener('load', () => resolve(), { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Turnstile script failed')), { once: true })
    })
  }

  return new Promise((resolve, reject) => {
    const script = document.createElement('script')
    script.id = TURNSTILE_SCRIPT_ID
    script.src = TURNSTILE_SCRIPT_SRC
    script.async = true
    script.defer = true
    script.addEventListener('load', () => resolve(), { once: true })
    script.addEventListener('error', () => reject(new Error('Turnstile script failed')), { once: true })
    document.head.appendChild(script)
  })
}

async function initTurnstile(): Promise<void> {
  if (!turnstileSiteKey || !turnstileContainer.value) return

  captchaLoading.value = true
  captchaError.value = ''

  try {
    await loadTurnstileScript()
    await nextTick()
    if (!window.turnstile || !turnstileContainer.value) throw new Error('Turnstile unavailable')
    if (turnstileWidgetId.value !== null) window.turnstile.remove(turnstileWidgetId.value)

    turnstileWidgetId.value = window.turnstile.render(turnstileContainer.value, {
      sitekey: turnstileSiteKey,
      action: 'change_email',
      theme: 'light',
      size: 'flexible',
      execution: 'execute',
      appearance: 'interaction-only',
      callback: (token: string) => {
        captchaToken.value = token
        captchaError.value = ''
      },
      'error-callback': () => {
        captchaError.value = '验证失败，请重试'
        captchaToken.value = ''
      },
      'expired-callback': () => {
        captchaToken.value = ''
        captchaError.value = '验证已过期，请重新完成验证'
      },
    })
  } catch {
    captchaError.value = '验证组件加载失败'
  } finally {
    captchaLoading.value = false
  }
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.execute(turnstileWidgetId.value)
  }
}

async function ensureTurnstile(): Promise<void> {
  if (captchaToken.value || captchaLoading.value || turnstileWidgetId.value !== null) return
  // 【需求十】原 mounted 自动加载改为表单交互时加载，失败后仍调用 reset。
  await initTurnstile()
}

function resetTurnstile(): void {
  captchaToken.value = ''
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.reset(turnstileWidgetId.value)
  }
}

// ── 发送验证码（首次：需 Turnstile） ──
async function sendVerificationCode(): Promise<void> {
  formError.value = ''

  if (!emailValid.value) {
    formError.value = '请输入有效邮箱地址'
    return
  }
  if (isSameEmail.value) {
    formError.value = '新邮箱不能与当前邮箱相同'
    return
  }
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  sendingCode.value = true

  try {
    const res = await sendChangeEmailCodeApi(form.newEmail, captchaToken.value, 'change_email')

    if (res.code === 200) {
      // 存储 resend_token 供后续重发使用
      if (res?.data?.resend_token) {
        resendToken.value = res.data.resend_token
      }
      currentStep.value = 1
      startResendCountdown()
      toast.showToast('验证码已发送至新邮箱', 'success')
    } else {
      formError.value = res.message || '发送验证码失败'
      resetTurnstile()
    }
  } catch (error: any) {
    formError.value = error?.message || error?.response?.data?.message || '发送失败，请稍后重试'
    resetTurnstile()
  } finally {
    sendingCode.value = false
  }
}

// ── 重新发送验证码（免人机验证，凭 resend_token） ──
async function resendCode(): Promise<void> {
  formError.value = ''

  if (!resendToken.value) {
    // 无 resend_token，回退到首次流程
    formError.value = '验证已过期，请返回重新验证'
    return
  }

  sendingCode.value = true

  try {
    await resendChangeEmailCodeApi(form.newEmail, resendToken.value)
    startResendCountdown()
    toast.showToast('验证码已重新发送', 'success')
  } catch (error: any) {
    // 重发达上限或超时，需重新走首次流程
    if (error?.response?.status === 429 || error?.response?.data?.code === 'RESEND_LIMIT') {
      resendToken.value = ''
      currentStep.value = 0
      resetTurnstile()
      formError.value = '重发次数已达上限，请重新验证'
    } else {
      formError.value = error?.message || '重发失败，请稍后重试'
    }
  } finally {
    sendingCode.value = false
  }
}

function startResendCountdown(): void {
  resendCountdown.value = 60
  if (resendTimer) clearInterval(resendTimer)
  resendTimer = setInterval(() => {
    resendCountdown.value--
    if (resendCountdown.value <= 0) {
      if (resendTimer) clearInterval(resendTimer)
      resendTimer = null
    }
  }, 1000)
}

// ── 确认换绑 ──
async function confirmChangeEmail(): Promise<void> {
  formError.value = ''

  if (form.code.length < 6) {
    formError.value = '请输入完整的6位验证码'
    return
  }

  confirming.value = true

  try {
    const res = await confirmChangeEmailApi(form.newEmail, form.code)

    if (res.code === 200) {
      currentStep.value = 2
      userStore.mergeProfile({ email: form.newEmail })
      toast.showToast('邮箱换绑成功', 'success')
    } else {
      formError.value = res.message || '邮箱换绑失败'
    }
  } catch (error: any) {
    formError.value = error?.message || error?.response?.data?.message || '换绑失败，请稍后重试'
  } finally {
    confirming.value = false
  }
}

// ── 返回上一步 ──
function goBackToStep0() {
  currentStep.value = 0
  resendToken.value = ''
  resetTurnstile()
}

onBeforeUnmount(() => {
  if (resendTimer) clearInterval(resendTimer)
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
})
</script>
