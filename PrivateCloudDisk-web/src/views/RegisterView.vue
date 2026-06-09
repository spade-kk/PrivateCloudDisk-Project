<template>
  <div class="register-shell flex min-h-screen items-center justify-center p-4">
    <div class="register-panel w-full max-w-md rounded-lg bg-white p-6 shadow-xl shadow-slate-900/10 sm:p-8">
      <div class="mb-7 text-center">
        <i class="fa fa-cloud text-primary text-4xl sm:text-5xl"></i>
        <h1 class="mt-3 text-2xl font-bold text-slate-900">CloudDrive 私有云</h1>
        <p class="mt-1 text-neutral-500">注册新账号</p>
      </div>

      <form @submit.prevent="handleRegister">
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">用户名</label>
            <input
              v-model="form.name"
              type="text"
              class="w-full rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              :class="formError ? 'border-danger' : 'border-slate-200'"
              placeholder="2-10位数字或字母"
              required
              @input="clearFormError"
            >
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">邮箱</label>
            <input
              v-model="form.email"
              type="email"
              class="w-full rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              :class="formError ? 'border-danger' : 'border-slate-200'"
              placeholder="请输入邮箱地址"
              required
              @input="clearFormError"
            >
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">验证码</label>
            <div class="flex gap-2">
              <input
                v-model="form.code"
                type="text"
                class="flex-1 rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                :class="formError ? 'border-danger' : 'border-slate-200'"
                placeholder="请输入验证码"
                required
                @input="clearFormError"
              >
              <button
                type="button"
                @click="sendVerificationCode"
                :disabled="verificationDisabled"
                class="rounded-lg border border-slate-200 px-4 py-2.5 font-medium text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:text-slate-400"
              >
                {{ verificationCountdown > 0 ? `${verificationCountdown}s` : '获取验证码' }}
              </button>
            </div>
          </div>

          <div>
            <label class="mb-1 block text-sm font-medium text-slate-600">密码</label>
            <input
              v-model="form.password"
              type="password"
              class="w-full rounded-lg border px-4 py-2.5 text-slate-900 transition focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              :class="formError ? 'border-danger' : 'border-slate-200'"
              placeholder="8-15位，包含字母和数字"
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
            <i v-else class="fa fa-user-plus"></i>
            <span>{{ loading ? '注册中...' : '注册' }}</span>
          </button>

          <div class="text-center text-sm text-slate-600">
            已有账号？
            <router-link to="/login" class="text-primary hover:underline">
              立即登录
            </router-link>
          </div>
        </div>
      </form>
    </div>
  </div>
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

const turnstileSiteKey = ref(import.meta.env.VITE_TURNSTILE_SITE_KEY || '')
const turnstileContainer = ref(null)
const captchaToken = ref('')
const captchaLoading = ref(false)
const captchaError = ref('')
let turnstileWidgetId = null

const captchaStatusText = computed(() => {
  if (captchaToken.value) return '验证通过'
  if (captchaError.value) return '验证失败'
  if (captchaLoading.value) return '加载中'
  return '等待验证'
})

const verificationDisabled = computed(() => {
  return loading.value || verificationCountdown.value > 0 || !form.email
})

const submitDisabled = computed(() => {
  return loading.value || 
         !captchaToken.value || 
         !form.name || 
         !form.email || 
         !form.code || 
         !form.password
})

const clearFormError = () => {
  formError.value = ''
}

const loadTurnstileScript = () => {
  return new Promise((resolve, reject) => {
    if (document.getElementById(TURNSTILE_SCRIPT_ID)) {
      resolve()
      return
    }
    
    const script = document.createElement('script')
    script.id = TURNSTILE_SCRIPT_ID
    script.src = TURNSTILE_SCRIPT_SRC
    script.onload = resolve
    script.onerror = reject
    document.head.appendChild(script)
  })
}

const initTurnstile = async () => {
  if (!turnstileSiteKey.value || !turnstileContainer.value) return
  
  captchaLoading.value = true
  captchaError.value = ''
  
  try {
    await loadTurnstileScript()
    
    await nextTick()
    
    if (window.turnstile && turnstileContainer.value) {
      turnstileWidgetId = window.turnstile.render(turnstileContainer.value, {
        sitekey: turnstileSiteKey.value,
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
        }
      })
    }
  } catch (error) {
    captchaError.value = '验证组件加载失败'
  } finally {
    captchaLoading.value = false
  }
}

const sendVerificationCode = async () => {
  if (!form.email) {
    formError.value = '请先输入邮箱'
    return
  }
  
  try {
    await post('/business/users/email/verification-code', null, {
      params: { email: form.email }
    })
    
    verificationCountdown.value = 60
    const timer = setInterval(() => {
      verificationCountdown.value--
      if (verificationCountdown.value <= 0) {
        clearInterval(timer)
      }
    }, 1000)
  } catch (error) {
    formError.value = error.response?.data?.message || '发送验证码失败'
  }
}

const handleRegister = async () => {
  // 验证密码格式
  const passwordRegex = /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$/
  if (!passwordRegex.test(form.password)) {
    formError.value = '密码必须是8-15位，包含字母和数字'
    return
  }
  
  // 验证用户名格式
  const nameRegex = /^[a-zA-Z0-9]{2,10}$/
  if (!nameRegex.test(form.name)) {
    formError.value = '用户名必须是2-10位数字或字母'
    return
  }
  
  loading.value = true
  formError.value = ''
  
  try {
    const response = await post('/business/users/', {
      name: form.name,
      email: form.email,
      password: form.password,
      code: form.code,
      captcha_token: captchaToken.value
    })
    
    if (response.data.state === 200) {
      router.push('/login')
    } else {
      formError.value = response.data.message || '注册失败'
    }
  } catch (error) {
    formError.value = error.response?.data?.message || '注册失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  initTurnstile()
})

onBeforeUnmount(() => {
  if (window.turnstile && turnstileWidgetId !== null) {
    window.turnstile.remove(turnstileWidgetId)
  }
})
</script>

<style scoped>
.register-shell {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
</style>
