<template>
  <main class="h-screen h-[100dvh] overflow-y-auto overscroll-contain bg-gradient-to-br from-slate-50 via-emerald-50 to-blue-50 px-3 py-4 sm:px-5 sm:py-6 xl:px-8">
    <div class="mx-auto grid min-h-full max-w-[1240px] gap-4 md:gap-5 xl:grid-cols-[minmax(0,1.02fr)_minmax(430px,0.98fr)]">
      <section
        class="relative hidden min-h-[680px] flex-col justify-between overflow-hidden rounded-lg border border-slate-300/70 bg-white/85 p-8 shadow-[0_24px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl xl:flex"
        aria-label="CloudDrive account onboarding"
      >
        <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(15,118,110,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(22,93,255,0.07)_1px,transparent_1px)] bg-[length:46px_46px] opacity-80 [mask-image:linear-gradient(120deg,#000,transparent_72%)]"></div>
        <div class="pointer-events-none absolute -right-24 top-16 h-72 w-72 rounded-full bg-teal-500/10 blur-3xl"></div>

        <div class="relative z-[1] flex items-center gap-3">
          <span class="inline-flex h-[46px] w-[46px] items-center justify-center rounded-lg bg-primary text-[22px] text-white shadow-[0_12px_30px_rgba(22,93,255,0.28)]">
            <i class="fa fa-cloud"></i>
          </span>
          <div>
            <strong class="font-extrabold text-slate-800">CloudDrive</strong>
            <p class="text-slate-500">Team Account Setup</p>
          </div>
        </div>

        <div class="relative z-[1] my-8 max-w-[680px] animate-fadeIn sm:my-10 lg:my-0">
          <span class="inline-flex items-center gap-2 rounded-full bg-teal-700/10 px-2.5 py-1.5 text-xs font-extrabold text-teal-700">
            SECURE ONBOARDING
          </span>
          <h1 class="mt-[18px] max-w-[680px] text-[34px] font-black leading-[1.06] text-slate-950 sm:text-[44px] md:text-[52px] lg:text-[56px]">
            创建一个可协作、可审计的私有云账号
          </h1>
          <p class="mt-[18px] max-w-[560px] text-sm leading-7 text-slate-500 sm:text-base sm:leading-8">
            注册后即可接入文件管理、分享协作、搜索索引与团队消息能力。
          </p>
        </div>

        <div class="relative z-[1] grid gap-3 md:grid-cols-3 lg:grid-cols-1">
          <div
            v-for="(step, index) in onboardingSteps"
            :key="step.title"
            class="flex items-center gap-3 rounded-lg border p-3.5 transition duration-200 md:flex-col md:items-start xl:flex-row xl:items-center"
            :class="index <= currentStep ? 'border-teal-700/30 bg-teal-50/80 xl:translate-x-1' : 'border-slate-200/90 bg-slate-50/80'"
          >
            <span
              class="inline-flex h-[34px] w-[34px] items-center justify-center rounded-lg font-black"
              :class="index <= currentStep ? 'bg-teal-700 text-white' : 'bg-slate-200 text-slate-700'"
            >
              {{ index + 1 }}
            </span>
            <div>
              <strong class="font-extrabold text-slate-950">{{ step.title }}</strong>
              <p class="text-xs text-slate-500">{{ step.desc }}</p>
            </div>
          </div>
        </div>
      </section>

      <section class="flex min-h-full items-start justify-center py-1 sm:py-3 xl:items-center xl:py-0">
        <div class="w-full max-w-[480px] animate-fadeIn rounded-lg border border-slate-300/70 bg-white/90 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl sm:p-7 xl:max-w-none xl:p-8">
          <div class="mb-5 flex items-center justify-between gap-3 rounded-lg border border-teal-700/10 bg-teal-50/70 p-3 xl:hidden">
            <div class="flex min-w-0 items-center gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary text-lg text-white shadow-[0_10px_24px_rgba(22,93,255,0.22)]">
                <i class="fa fa-cloud"></i>
              </span>
              <div class="min-w-0">
                <strong class="block truncate font-extrabold text-slate-800">CloudDrive</strong>
                <p class="truncate text-xs text-slate-500">创建私有云协作账号</p>
              </div>
            </div>
            <span class="hidden shrink-0 rounded-full bg-white px-2.5 py-1 text-xs font-extrabold text-teal-700 shadow-card min-[430px]:inline-flex">
              安全注册
            </span>
          </div>

          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-teal-700/10 px-2.5 py-1.5 text-xs font-extrabold text-teal-700">
              <i class="fa fa-user-plus"></i>
              新账号注册
            </span>
            <h2 class="mt-4 text-[28px] font-extrabold text-slate-800 sm:text-3xl">创建账号</h2>
            <p class="mt-2 text-sm leading-6 text-slate-500 sm:text-base">请使用真实邮箱接收验证码，后续可用于协作通知和账号安全提醒。</p>
          </div>

          <form class="mt-6 grid gap-[15px]" @submit.prevent="handleRegister">
            <label class="grid gap-2">
              <span class="text-sm font-extrabold text-slate-700">用户名</span>
              <div
                class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
                :class="focusedField === 'name' ? '-translate-y-px border-teal-700 shadow-[0_0_0_4px_rgba(15,118,110,0.10)]' : 'border-slate-200'"
              >
                <i class="fa fa-user-o text-slate-400"></i>
                <input
                  v-model.trim="form.name"
                  type="text"
                  autocomplete="username"
                  class="min-w-0 flex-1 border-0 bg-transparent outline-none"
                  placeholder="2-10 位数字或字母"
                  required
                  @focus="focusedField = 'name'"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                >
                <i v-if="form.name && nameValid" class="fa fa-check text-teal-700"></i>
              </div>
              <small v-if="form.name && !nameValid" class="text-danger">用户名必须是 2-10 位数字或字母</small>
            </label>

            <label class="grid gap-2">
              <span class="text-sm font-extrabold text-slate-700">邮箱</span>
              <div
                class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
                :class="focusedField === 'email' ? '-translate-y-px border-teal-700 shadow-[0_0_0_4px_rgba(15,118,110,0.10)]' : 'border-slate-200'"
              >
                <i class="fa fa-envelope-o text-slate-400"></i>
                <input
                  v-model.trim="form.email"
                  type="email"
                  autocomplete="email"
                  class="min-w-0 flex-1 border-0 bg-transparent outline-none"
                  placeholder="请输入邮箱地址"
                  required
                  @focus="focusedField = 'email'"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                >
                <i v-if="form.email && emailValid" class="fa fa-check text-teal-700"></i>
              </div>
              <small v-if="form.email && !emailValid" class="text-danger">请输入有效邮箱地址</small>
            </label>

            <label class="grid gap-2">
              <span class="text-sm font-extrabold text-slate-700">邮箱验证码</span>
              <div
                class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 pr-1.5 transition duration-200 max-[560px]:flex-wrap max-[560px]:items-stretch max-[560px]:pb-1.5"
                :class="focusedField === 'code' ? '-translate-y-px border-teal-700 shadow-[0_0_0_4px_rgba(15,118,110,0.10)]' : 'border-slate-200'"
              >
                <i class="fa fa-key text-slate-400"></i>
                <input
                  v-model.trim="form.code"
                  type="text"
                  inputmode="numeric"
                  class="min-w-0 flex-1 border-0 bg-transparent outline-none max-[560px]:basis-[calc(100%-34px)]"
                  placeholder="请输入验证码"
                  required
                  @focus="focusedField = 'code'"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                >
                <button
                  type="button"
                  class="min-h-9 flex-none rounded-lg bg-blue-50 px-2.5 text-xs font-extrabold text-primary transition hover:bg-blue-100 disabled:cursor-not-allowed disabled:bg-slate-100 disabled:text-slate-400 max-[560px]:w-full"
                  :disabled="verificationDisabled"
                  @click="sendVerificationCode"
                >
                  {{ verificationCountdown > 0 ? `${verificationCountdown}s` : '获取验证码' }}
                </button>
              </div>
            </label>

            <label class="grid gap-2">
              <span class="text-sm font-extrabold text-slate-700">密码</span>
              <div
                class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
                :class="focusedField === 'password' ? '-translate-y-px border-teal-700 shadow-[0_0_0_4px_rgba(15,118,110,0.10)]' : 'border-slate-200'"
              >
                <i class="fa fa-lock text-slate-400"></i>
                <input
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  class="min-w-0 flex-1 border-0 bg-transparent outline-none"
                  placeholder="8-15 位，包含字母和数字"
                  required
                  @focus="focusedField = 'password'"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                >
                <button
                  type="button"
                  class="inline-flex h-[34px] w-[34px] items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-teal-700"
                  :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                  @click="showPassword = !showPassword"
                >
                  <i :class="showPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
                </button>
              </div>
              <div class="flex items-center gap-1.5">
                <span
                  v-for="level in 3"
                  :key="level"
                  class="h-[5px] flex-1 rounded-full transition"
                  :class="passwordScore >= level ? 'bg-teal-700' : 'bg-slate-200'"
                ></span>
                <em class="min-w-[54px] text-right text-xs not-italic text-slate-500">{{ passwordStrengthText }}</em>
              </div>
            </label>

            <div class="rounded-lg border border-slate-200 bg-slate-50 p-3">
              <div class="mb-2.5 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                <div>
                  <strong class="text-sm font-extrabold text-slate-700">安全验证</strong>
                  <p class="text-sm text-slate-500">创建账号前完成 Turnstile 校验</p>
                </div>
                <span
                  class="rounded-full px-2 py-1 text-xs font-extrabold"
                  :class="captchaToken ? 'bg-success/10 text-green-700' : captchaError ? 'bg-danger/10 text-danger' : 'bg-slate-100 text-slate-500'"
                >
                  {{ captchaStatusText }}
                </span>
              </div>

              <div class="flex min-h-[74px] items-center justify-center overflow-hidden rounded-lg border border-slate-200 bg-white">
                <div
                  ref="turnstileContainer"
                  class="min-h-[65px] w-[300px] max-[560px]:origin-center max-[560px]:scale-[0.92]"
                  :class="{ hidden: !turnstileSiteKey }"
                ></div>
                <div v-if="!turnstileSiteKey" class="text-sm text-danger">
                  未配置 Turnstile Site Key
                </div>
                <div v-else-if="captchaLoading" class="inline-flex items-center gap-2 text-sm text-slate-500">
                  <i class="fa fa-spinner fa-spin"></i>
                  正在加载验证组件
                </div>
              </div>
              <p v-if="captchaError" class="mt-2 text-xs text-danger">{{ captchaError }}</p>
            </div>

            <transition
              enter-active-class="transition duration-150 ease-out"
              enter-from-class="-translate-y-1 opacity-0"
              enter-to-class="translate-y-0 opacity-100"
              leave-active-class="transition duration-150 ease-in"
              leave-from-class="translate-y-0 opacity-100"
              leave-to-class="-translate-y-1 opacity-0"
            >
              <div v-if="formError" class="flex items-center gap-2 rounded-lg bg-danger/10 px-3 py-2.5 text-sm text-danger">
                <i class="fa fa-exclamation-circle"></i>
                <span>{{ formError }}</span>
              </div>
            </transition>

            <button
              type="submit"
              class="inline-flex min-h-12 items-center justify-center gap-2.5 rounded-lg bg-teal-700 font-extrabold text-white shadow-[0_14px_30px_rgba(15,118,110,0.25)] transition duration-200 hover:-translate-y-px hover:bg-teal-800 hover:shadow-[0_18px_40px_rgba(15,118,110,0.28)] disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
              :disabled="submitDisabled"
            >
              <span>{{ loading ? '正在创建账号' : '创建账号' }}</span>
              <i :class="loading ? 'fa fa-spinner fa-spin' : 'fa fa-arrow-right'"></i>
            </button>
          </form>

          <div class="mt-5 text-center text-sm text-slate-500">
            <span>已有账号？</span>
            <router-link to="/login" class="font-extrabold text-teal-700 hover:underline">立即登录</router-link>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { post } from '@/utils/request'

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const router = useRouter()
const form = reactive({
  name: '',
  email: '',
  code: '',
  password: '',
})
const loading = ref(false)
const formError = ref('')
const verificationCountdown = ref(0)
const verificationTimer = ref(null)
const focusedField = ref('')
const showPassword = ref(false)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const turnstileContainer = ref(null)
const turnstileWidgetId = ref(null)
const captchaToken = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')

const onboardingSteps = [
  { title: '填写资料', desc: '账号、邮箱与密码' },
  { title: '邮箱确认', desc: '验证注册邮箱归属' },
  { title: '安全校验', desc: '防护异常注册请求' },
]

const nameValid = computed(() => /^[a-zA-Z0-9]{2,10}$/.test(form.name))
const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email))
const passwordValid = computed(() => /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$/.test(form.password))

const passwordScore = computed(() => {
  let score = 0
  if (form.password.length >= 8) score += 1
  if (/[A-Za-z]/.test(form.password) && /\d/.test(form.password)) score += 1
  if (form.password.length >= 12) score += 1
  return score
})

const passwordStrengthText = computed(() => {
  if (!form.password) return '密码强度'
  if (passwordScore.value >= 3) return '强'
  if (passwordScore.value === 2) return '中'
  return '弱'
})

const currentStep = computed(() => {
  if (captchaToken.value) return 2
  if (form.code) return 1
  if (form.name || form.email || form.password) return 0
  return -1
})

const captchaStatusText = computed(() => {
  if (!turnstileSiteKey) return '未配置'
  if (captchaToken.value) return '验证通过'
  if (captchaError.value) return '验证失败'
  if (captchaLoading.value) return '加载中'
  return '等待验证'
})

const verificationDisabled = computed(() => {
  return loading.value || verificationCountdown.value > 0 || !emailValid.value
})

const submitDisabled = computed(() => {
  return loading.value ||
    !nameValid.value ||
    !emailValid.value ||
    !form.code ||
    !passwordValid.value ||
    !captchaToken.value
})

function clearFormError() {
  formError.value = ''
}

function loadTurnstileScript() {
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

async function initTurnstile() {
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
      action: 'register',
      theme: 'light',
      size: 'normal',
      callback: (token) => {
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

function resetTurnstile() {
  captchaToken.value = ''
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.reset(turnstileWidgetId.value)
  }
}

async function sendVerificationCode() {
  formError.value = ''
  if (!emailValid.value) {
    formError.value = '请先输入有效邮箱'
    return
  }

  try {
    await post('business/users/email/verification-code', null, {
      params: { email: form.email },
    })

    verificationCountdown.value = 60
    if (verificationTimer.value) clearInterval(verificationTimer.value)
    verificationTimer.value = setInterval(() => {
      verificationCountdown.value -= 1
      if (verificationCountdown.value <= 0) {
        clearInterval(verificationTimer.value)
        verificationTimer.value = null
      }
    }, 1000)
  } catch (error) {
    formError.value = error.message || error.response?.data?.message || '发送验证码失败'
  }
}

async function handleRegister() {
  formError.value = ''

  if (!nameValid.value) {
    formError.value = '用户名必须是 2-10 位数字或字母'
    return
  }
  if (!emailValid.value) {
    formError.value = '请输入有效邮箱地址'
    return
  }
  if (!passwordValid.value) {
    formError.value = '密码必须是 8-15 位，且包含字母和数字'
    return
  }
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  loading.value = true

  try {
    const response = await post('business/users/', {
      name: form.name,
      email: form.email,
      password: form.password,
      code: form.code,
      captcha_token: captchaToken.value,
      captcha_action: 'register',
    })

    const success = response?.code === 200 || response?.state === 200 || response?.data?.state === 200
    if (success) {
      router.push('/login')
      return
    }
    formError.value = response?.message || response?.data?.message || '注册失败'
    resetTurnstile()
  } catch (error) {
    formError.value = error.message || error.response?.data?.message || '注册失败，请稍后重试'
    resetTurnstile()
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  initTurnstile()
})

onBeforeUnmount(() => {
  if (verificationTimer.value) clearInterval(verificationTimer.value)
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
})
</script>
