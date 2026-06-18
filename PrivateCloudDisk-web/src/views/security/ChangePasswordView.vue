<template>
  <div class="mx-auto max-w-2xl space-y-5 pb-10">
    <!-- ===== 面包屑导航 ===== -->
    <nav class="flex items-center gap-2 text-sm text-neutral-400">
      <router-link to="/app/profile" class="transition hover:text-primary">
        <i class="fa fa-arrow-left mr-1"></i>返回个人中心
      </router-link>
      <i class="fa fa-angle-right text-xs"></i>
      <span class="text-neutral-600">修改登录密码</span>
    </nav>

    <!-- ===== 页面头部 ===== -->
    <div class="responsive-panel p-5 sm:p-6">
      <div class="flex items-start gap-4">
        <span class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-blue-50">
          <i class="fa fa-lock text-xl text-blue-600"></i>
        </span>
        <div>
          <h1 class="text-xl font-bold text-neutral-800">修改登录密码</h1>
          <p class="mt-1 text-sm text-neutral-500">
            为保障账号安全，修改密码需要验证当前密码并完成安全校验。
          </p>
        </div>
      </div>
    </div>

    <!-- ===== 密码修改表单 ===== -->
    <div class="responsive-panel p-5 sm:p-6">
      <form @submit.prevent="handleChangePassword" class="space-y-5">
        <!-- 原密码 -->
        <div>
          <label class="mb-1.5 block text-sm font-bold text-neutral-700">
            当前密码 <span class="text-danger">*</span>
          </label>
          <div
            class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
            :class="focusedField === 'oldPassword'
              ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]'
              : 'border-neutral-200'"
          >
            <i class="fa fa-lock text-neutral-400"></i>
            <input
              v-model="form.oldPassword"
              :type="showOldPassword ? 'text' : 'password'"
              autocomplete="current-password"
              class="min-w-0 flex-1 border-0 bg-transparent outline-none"
              placeholder="请输入当前密码"
              @focus="focusedField = 'oldPassword'"
              @blur="focusedField = ''"
            >
            <button
              type="button"
              class="inline-flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-lg text-neutral-500 transition hover:bg-neutral-100 hover:text-primary"
              :aria-label="showOldPassword ? '隐藏密码' : '显示密码'"
              @click="showOldPassword = !showOldPassword"
            >
              <i :class="showOldPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
            </button>
          </div>
        </div>

        <!-- 新密码 -->
        <div>
          <label class="mb-1.5 block text-sm font-bold text-neutral-700">
            新密码 <span class="text-danger">*</span>
          </label>
          <div
            class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
            :class="focusedField === 'newPassword'
              ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]'
              : 'border-neutral-200'"
          >
            <i class="fa fa-lock text-neutral-400"></i>
            <input
              v-model="form.newPassword"
              :type="showNewPassword ? 'text' : 'password'"
              autocomplete="new-password"
              class="min-w-0 flex-1 border-0 bg-transparent outline-none"
              placeholder="8-128 位，建议包含字母、数字和符号"
              @focus="focusedField = 'newPassword'"
              @blur="focusedField = ''"
            >
            <button
              type="button"
              class="inline-flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-lg text-neutral-500 transition hover:bg-neutral-100 hover:text-primary"
              :aria-label="showNewPassword ? '隐藏密码' : '显示密码'"
              @click="showNewPassword = !showNewPassword"
            >
              <i :class="showNewPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
            </button>
          </div>
          <!-- 密码强度指示器 -->
          <div v-if="form.newPassword" class="mt-2 space-y-1.5">
            <div class="flex items-center gap-1.5">
              <span
                v-for="level in 4"
                :key="level"
                class="h-[5px] flex-1 rounded-full transition"
                :class="passwordScore >= level ? strengthColor : 'bg-neutral-200'"
              ></span>
              <em class="min-w-[54px] text-right text-xs not-italic" :class="strengthTextColor">{{ strengthLabel }}</em>
            </div>
            <ul class="space-y-0.5 text-xs text-neutral-400">
              <li :class="form.newPassword.length >= 8 ? 'text-emerald-600' : ''">
                <i :class="form.newPassword.length >= 8 ? 'fa fa-check-circle' : 'fa fa-circle-o'" class="mr-1"></i>
                至少 8 个字符
              </li>
              <li :class="hasLetterAndDigit ? 'text-emerald-600' : ''">
                <i :class="hasLetterAndDigit ? 'fa fa-check-circle' : 'fa fa-circle-o'" class="mr-1"></i>
                包含字母和数字
              </li>
              <li :class="hasSpecialChar ? 'text-emerald-600' : ''">
                <i :class="hasSpecialChar ? 'fa fa-check-circle' : 'fa fa-circle-o'" class="mr-1"></i>
                包含特殊符号（推荐）
              </li>
            </ul>
          </div>
        </div>

        <!-- 确认新密码 -->
        <div>
          <label class="mb-1.5 block text-sm font-bold text-neutral-700">
            确认新密码 <span class="text-danger">*</span>
          </label>
          <div
            class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
            :class="focusedField === 'confirmPassword'
              ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]'
              : form.confirmPassword && !passwordsMatch
                ? 'border-danger shadow-[0_0_0_4px_rgba(239,68,68,0.08)]'
                : 'border-neutral-200'"
          >
            <i class="fa fa-check-circle text-neutral-400"></i>
            <input
              v-model="form.confirmPassword"
              :type="showConfirmPassword ? 'text' : 'password'"
              autocomplete="new-password"
              class="min-w-0 flex-1 border-0 bg-transparent outline-none"
              placeholder="请再次输入新密码"
              @focus="focusedField = 'confirmPassword'"
              @blur="focusedField = ''"
            >
            <button
              type="button"
              class="inline-flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-lg text-neutral-500 transition hover:bg-neutral-100 hover:text-primary"
              :aria-label="showConfirmPassword ? '隐藏密码' : '显示密码'"
              @click="showConfirmPassword = !showConfirmPassword"
            >
              <i :class="showConfirmPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
            </button>
          </div>
          <p v-if="form.confirmPassword && !passwordsMatch" class="mt-1.5 text-xs text-danger">
            两次输入的密码不一致
          </p>
        </div>

        <!-- Turnstile 人机验证 -->
        <div class="rounded-lg border border-neutral-200 bg-neutral-50 p-4">
          <div class="mb-3 flex flex-col gap-2 sm:flex-row sm:items-start sm:justify-between">
            <div>
              <strong class="text-sm font-extrabold text-neutral-700">安全验证</strong>
              <p class="text-sm text-neutral-500">修改密码前需完成身份校验</p>
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

          <div class="flex items-center justify-center rounded-lg border border-neutral-200 bg-white px-2 py-3">
            <div
              ref="turnstileContainer"
              class="turnstile-widget"
              :class="{ hidden: !turnstileSiteKey }"
            ></div>
            <div v-if="!turnstileSiteKey" class="text-sm text-red-500">
              未配置 Turnstile Site Key
            </div>
            <div v-else-if="captchaLoading" class="inline-flex items-center gap-2 text-sm text-neutral-500">
              <i class="fa fa-spinner fa-spin"></i>正在加载验证组件
            </div>
          </div>
          <p v-if="captchaError" class="mt-2 text-xs text-red-500">{{ captchaError }}</p>
        </div>

        <!-- 安全提示 -->
        <div class="flex items-start gap-2.5 rounded-lg border border-amber-200 bg-amber-50 p-3">
          <i class="fa fa-info-circle mt-0.5 text-amber-500"></i>
          <div class="text-xs text-amber-700">
            <p class="font-bold">修改密码后：</p>
            <ul class="mt-1 list-inside list-disc space-y-0.5">
              <li>所有已登录设备将被强制下线</li>
              <li>需要重新登录以继续使用</li>
              <li>请确保记住新密码，建议使用密码管理器</li>
            </ul>
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

        <!-- 操作按钮 -->
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
            :disabled="submitDisabled"
          >
            <span>{{ submitting ? '正在修改密码...' : '确认修改密码' }}</span>
            <i :class="submitting ? 'fa fa-spinner fa-spin' : 'fa fa-arrow-right'"></i>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { useAuthStore } from '@/stores/authStore'
import { changeMyUserPasswordApi } from '@/api/modules/users'
import { hashPasswordForTransport } from '@/utils/crypto'

const TURNSTILE_SCRIPT_ID = 'cf-turnstile-changepwd'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()

// ── 表单状态 ──
const form = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})
const submitting = ref(false)
const formError = ref('')
const focusedField = ref('')
const showOldPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

// ── Turnstile 状态 ──
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const turnstileContainer = ref<HTMLElement | null>(null)
const turnstileWidgetId = ref<string | null>(null)
const captchaToken = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')

// ── 密码强度计算 ──
const hasLetterAndDigit = computed(() => {
  return /[A-Za-z]/.test(form.newPassword) && /\d/.test(form.newPassword)
})
const hasSpecialChar = computed(() => {
  return /[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>\/?`~]/.test(form.newPassword)
})

const passwordScore = computed(() => {
  let score = 0
  if (form.newPassword.length >= 8) score++
  if (form.newPassword.length >= 12) score++
  if (hasLetterAndDigit.value) score++
  if (hasSpecialChar.value) score++
  return score
})

const strengthColor = computed(() => {
  if (passwordScore.value <= 1) return 'bg-red-500'
  if (passwordScore.value === 2) return 'bg-amber-500'
  if (passwordScore.value === 3) return 'bg-emerald-500'
  return 'bg-emerald-600'
})

const strengthTextColor = computed(() => {
  if (passwordScore.value <= 1) return 'text-red-500'
  if (passwordScore.value === 2) return 'text-amber-500'
  return 'text-emerald-600'
})

const strengthLabel = computed(() => {
  if (!form.newPassword) return '密码强度'
  if (passwordScore.value <= 1) return '弱'
  if (passwordScore.value === 2) return '一般'
  if (passwordScore.value === 3) return '强'
  return '非常强'
})

const passwordsMatch = computed(() => {
  return form.newPassword === form.confirmPassword
})

const captchaStatusText = computed(() => {
  if (!turnstileSiteKey) return '未配置'
  if (captchaToken.value) return '已通过'
  if (captchaError.value) return '需重试'
  if (captchaLoading.value) return '加载中'
  return '待验证'
})

const submitDisabled = computed(() => {
  return (
    submitting.value ||
    !form.oldPassword ||
    form.newPassword.length < 8 ||
    !hasLetterAndDigit.value ||
    !passwordsMatch.value ||
    !captchaToken.value
  )
})

// ── Turnstile 脚本加载 ──
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
      action: 'change_password',
      theme: 'light',
      size: 'normal',
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
}

function resetTurnstile(): void {
  captchaToken.value = ''
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.reset(turnstileWidgetId.value)
  }
}

// ── 表单提交 ──
async function handleChangePassword(): Promise<void> {
  formError.value = ''

  // 前端校验
  if (!form.oldPassword) {
    formError.value = '请输入当前密码'
    return
  }
  if (form.newPassword.length < 8) {
    formError.value = '新密码至少需要 8 个字符'
    return
  }
  if (!hasLetterAndDigit.value) {
    formError.value = '新密码需包含字母和数字'
    return
  }
  if (!passwordsMatch.value) {
    formError.value = '两次输入的密码不一致'
    return
  }
  if (form.oldPassword === form.newPassword) {
    formError.value = '新密码不能与当前密码相同'
    return
  }
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  submitting.value = true

  try {
    // 客户端密码预哈希 - 使用用户邮箱作为盐值的一部分
    const userEmail = auth.user?.email || auth.user?.account || ''
    const hashedOldPassword = await hashPasswordForTransport(form.oldPassword, userEmail)
    const hashedNewPassword = await hashPasswordForTransport(form.newPassword, userEmail)

    const res = await changeMyUserPasswordApi(
      hashedOldPassword,
      hashedNewPassword,
      captchaToken.value,
      'change_password',
    )

    if (res.code === 200) {
      toast.showToast('密码修改成功，请重新登录', 'success')
      // 清除登录状态，跳转登录页
      auth.logout()
      router.push('/login')
    } else {
      formError.value = res.message || '密码修改失败'
      resetTurnstile()
    }
  } catch (error: any) {
    formError.value = error?.message || error?.response?.data?.message || '密码修改失败，请稍后重试'
    resetTurnstile()
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  initTurnstile()
})

onBeforeUnmount(() => {
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
})
</script>