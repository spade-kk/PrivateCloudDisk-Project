<template>
  <div class="login-shell flex min-h-screen items-center justify-center p-4">
    <div class="login-panel w-full max-w-md rounded-lg bg-white p-6 shadow-xl shadow-slate-900/10 sm:p-8">
      <div class="mb-7 text-center">
        <i class="fa fa-cloud text-primary text-4xl sm:text-5xl"></i>
        <h1 class="mt-3 text-2xl font-bold text-slate-900">CloudDrive 私有云</h1>
        <p class="mt-1 text-neutral-500">登录以管理您的文件</p>
      </div>

      <form @submit.prevent="handleLogin">
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">手机号</label>
            <input
              v-model="phone"
              type="tel"
              class="w-full rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              :class="formError ? 'border-danger' : 'border-slate-200'"
              placeholder="请输入手机号"
              required
              @input="clearFormError"
            >
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">密码</label>
            <input
              v-model="password"
              type="password"
              class="w-full rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              :class="formError ? 'border-danger' : 'border-slate-200'"
              placeholder="请输入密码"
              required
              @input="clearFormError"
            >
          </div>

          <div class="captcha-card rounded-lg border border-slate-200 bg-slate-50/80 p-3">
            <div class="mb-2 flex items-center justify-between">
              <div class="flex items-center gap-2 text-sm font-medium text-slate-700">
                <i class="fa fa-shield text-primary"></i>
                <span>安全验证</span>
              </div>
              <span
                class="text-xs"
                :class="captchaToken ? 'text-emerald-600' : captchaError ? 'text-danger' : 'text-slate-400'"
              >
                {{ captchaStatusText }}
              </span>
            </div>

            <div class="captcha-frame flex min-h-[70px] items-center justify-center overflow-hidden rounded-md bg-white">
              <div
                ref="turnstileContainer"
                class="turnstile-host"
                :class="{ 'is-hidden': !turnstileSiteKey }" style="width:100%;height: 100%;"
              ></div>
              <div v-if="!turnstileSiteKey" class="px-3 text-center text-sm text-danger">
                未配置 Turnstile Site Key
              </div>
              <div v-else-if="captchaLoading" class="flex items-center gap-2 text-sm text-slate-500">
                <i class="fa fa-spinner fa-spin"></i>
                <span>正在加载验证组件</span>
              </div>
            </div>

            <p v-if="captchaError" class="mt-2 text-xs text-danger">{{ captchaError }}</p>
          </div>

          <p v-if="formError" class="text-sm text-danger">{{ formError }}</p>

          <button
            type="submit"
            :disabled="submitDisabled"
            class="touch-button flex w-full items-center justify-center gap-2 rounded-lg bg-primary py-2.5 font-medium text-white transition hover:bg-primary/90 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            <i v-if="loading" class="fa fa-spinner fa-spin"></i>
            <i v-else class="fa fa-sign-in"></i>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const router = useRouter()
const authStore = useAuthStore()
const phone = ref('15777446691')
const password = ref('20070315mwz')
const loading = ref(false)
const formError = ref('')
const captchaError = ref('')
const captchaLoading = ref(false)
const captchaToken = ref('')
const turnstileContainer = ref(null)
const turnstileWidgetId = ref(null)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''

const captchaStatusText = computed(() => {
  if (!turnstileSiteKey) return '未配置'
  if (captchaToken.value) return '已通过'
  if (captchaError.value) return '需重试'
  return '待验证'
})

const submitDisabled = computed(() => loading.value || !turnstileSiteKey || !captchaToken.value)

function clearFormError() {
  formError.value = ''
}

function loadTurnstileScript() {
  if (window.turnstile) {
    return Promise.resolve()
  }

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

async function renderTurnstile() {
  if (!turnstileSiteKey || !turnstileContainer.value) {
    return
  }

  captchaLoading.value = true
  captchaError.value = ''
  try {
    await loadTurnstileScript()
    await nextTick()
    if (!window.turnstile || !turnstileContainer.value) {
      throw new Error('Turnstile unavailable')
    }
    if (turnstileWidgetId.value !== null) {
      window.turnstile.remove(turnstileWidgetId.value)
    }
    turnstileWidgetId.value = window.turnstile.render(turnstileContainer.value, {
      sitekey: turnstileSiteKey,
      action: 'login',
      theme: 'light',
      size: 'normal',
      callback: (token) => {
        captchaToken.value = token
        captchaError.value = ''
      },
      'expired-callback': () => {
        captchaToken.value = ''
        captchaError.value = '验证已过期，请重新完成验证'
      },
      'error-callback': () => {
        captchaToken.value = ''
        captchaError.value = '验证组件加载失败，请刷新或稍后重试'
      },
    })
  } catch (error) {
    captchaToken.value = ''
    captchaError.value = '验证组件加载失败，请刷新或稍后重试'
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

async function handleLogin() {
  formError.value = ''
  captchaError.value = ''
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  loading.value = true
  const result = await authStore.login(phone.value, password.value, captchaToken.value)
  loading.value = false
  if (result.success) {
    router.push('/')
    return
  }

  resetTurnstile()
  if (result.scope === 'form') {
    formError.value = result.message || '手机号或密码错误'
  } else {
    formError.value = result.message || '网络错误，请稍后重试'
  }
}

onMounted(() => {
  renderTurnstile()
})

onBeforeUnmount(() => {
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
})
</script>

<style scoped>
.login-shell {
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.16), transparent 30%),
    linear-gradient(135deg, #f8fafc 0%, #eef2f7 48%, #f7f8fb 100%);
}

.login-panel {
  border: 1px solid rgba(226, 232, 240, 0.86);
}

.captcha-frame {
  border: 1px solid rgba(226, 232, 240, 0.92);
}

.turnstile-host {
  min-height: 65px;
  width: 300px;
}

.turnstile-host.is-hidden {
  display: none;
}

@media (max-width: 380px) {
  .turnstile-host {
    transform: scale(0.92);
    transform-origin: center;
  }
}
</style>
