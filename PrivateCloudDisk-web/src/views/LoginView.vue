<template>
  <main class="h-screen h-[100dvh] overflow-y-auto overscroll-contain bg-gradient-to-br from-slate-50 via-blue-50 to-emerald-50 px-3 py-4 sm:px-5 sm:py-6 xl:px-8">
    <div class="mx-auto grid min-h-full max-w-[1240px] gap-4 md:gap-5 xl:grid-cols-[minmax(0,1.12fr)_minmax(430px,0.88fr)]">
      <!-- ============================================================ -->
      <!-- 左侧：产品介绍面板（保持原有设计） -->
      <!-- ============================================================ -->
      <section
        class="relative hidden min-h-[660px] flex-col overflow-hidden rounded-lg border border-slate-300/70 bg-white/85 p-8 shadow-[0_24px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl xl:flex"
        aria-label="CloudDrive product introduction"
      >
        <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(rgba(22,93,255,0.08)_1px,transparent_1px),linear-gradient(90deg,rgba(22,93,255,0.08)_1px,transparent_1px)] bg-[length:44px_44px] opacity-80 [mask-image:linear-gradient(120deg,#000,transparent_72%)]"></div>
        <div class="pointer-events-none absolute -right-28 top-16 h-72 w-72 rounded-full bg-primary/10 blur-3xl"></div>
        <div class="pointer-events-none absolute bottom-8 left-10 h-52 w-52 rounded-full bg-teal-500/10 blur-3xl"></div>

        <div class="relative z-[1] flex flex-col gap-5 sm:flex-row sm:items-center sm:justify-between">
          <div class="flex items-center gap-3">
            <span class="inline-flex h-[46px] w-[46px] shrink-0 items-center justify-center rounded-lg bg-primary text-[22px] text-white shadow-[0_12px_30px_rgba(22,93,255,0.28)]">
              <i class="fa fa-cloud"></i>
            </span>
            <div>
              <strong class="block font-extrabold text-slate-800">CloudDrive</strong>
              <p class="text-sm text-slate-500">企业级私有云文件协作平台</p>
            </div>
          </div>
          <div class="inline-flex w-fit items-center gap-2 rounded-full border border-primary/20 bg-primary/10 px-3 py-1.5 text-xs font-extrabold text-primary">
            <i class="fa fa-circle text-[8px]"></i>
            安全工作台
          </div>
        </div>

        <div class="relative z-[1] mt-8 grid gap-6 xl:mt-10">
          <div class="max-w-[720px] animate-fadeIn">
            <span class="inline-flex items-center gap-2 rounded-full bg-white px-3 py-1.5 text-xs font-extrabold text-primary shadow-card">
              PRIVATE CLOUD DRIVE
            </span>
            <h1 class="mt-4 max-w-[760px] text-[34px] font-black leading-[1.06] text-slate-950 sm:text-[44px] md:text-[52px] lg:text-[50px] xl:text-[58px]">
              让企业文件从存储到协作都保持安全有序
            </h1>
            <p class="mt-4 max-w-[620px] text-sm leading-7 text-slate-500 sm:text-base sm:leading-8">
              CloudDrive 将文件上传、预览、全文搜索、分享权限、回收站保护和协作消息整合为一个清晰的私有云工作台。
            </p>
          </div>

          <div class="rounded-lg border border-slate-200/90 bg-white/85 p-4 shadow-[0_18px_46px_rgba(15,23,42,0.08)]">
            <div class="flex flex-col gap-3 border-b border-slate-200 pb-4 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <p class="text-xs font-extrabold uppercase tracking-wide text-primary">Unified File Workspace</p>
                <strong class="mt-1 block text-lg font-black text-slate-950">一个入口处理完整文件生命周期</strong>
              </div>
              <span class="inline-flex w-fit items-center gap-2 rounded-full bg-emerald-50 px-3 py-1.5 text-xs font-extrabold text-emerald-700">
                <i class="fa fa-check-circle"></i>
                权限与审计已就绪
              </span>
            </div>

            <div class="mt-4 grid gap-3 md:grid-cols-3 lg:grid-cols-1 xl:grid-cols-3">
              <div
                v-for="item in workspacePreview"
                :key="item.title"
                class="rounded-lg bg-slate-50 p-3"
              >
                <div class="flex items-center gap-2">
                  <i :class="[item.icon, 'text-primary']"></i>
                  <strong class="text-sm font-extrabold text-slate-900">{{ item.title }}</strong>
                </div>
                <p class="mt-2 text-xs leading-5 text-slate-500">{{ item.desc }}</p>
              </div>
            </div>

            <div class="mt-4 grid gap-3 sm:grid-cols-3">
              <div
                v-for="flow in workspaceFlow"
                :key="flow"
                class="flex items-center gap-2 rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-xs font-bold text-slate-600"
              >
                <span class="h-2 w-2 rounded-full bg-primary"></span>
                <span>{{ flow }}</span>
              </div>
            </div>
          </div>

          <div class="grid gap-3 md:grid-cols-2">
            <article
              v-for="item in platformFeatures"
              :key="item.title"
              class="group rounded-lg border border-slate-200/90 bg-white/80 p-4 transition duration-200 hover:-translate-y-1 hover:border-primary/30 hover:bg-white hover:shadow-hover"
            >
              <div class="flex items-start gap-3">
                <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary transition group-hover:bg-primary group-hover:text-white">
                  <i :class="item.icon"></i>
                </span>
                <div class="min-w-0">
                  <strong class="block text-sm font-extrabold text-slate-900">{{ item.title }}</strong>
                  <p class="mt-1 text-xs leading-5 text-slate-500">{{ item.desc }}</p>
                </div>
              </div>
            </article>
          </div>

          <div class="grid gap-3 md:grid-cols-3">
            <div
              v-for="item in trustSignals"
              :key="item.label"
              class="flex items-center gap-2.5 rounded-lg border border-slate-200/90 bg-slate-50/80 p-3"
            >
              <i :class="[item.icon, 'text-lg text-teal-700']"></i>
              <div>
                <strong class="block font-extrabold text-slate-950">{{ item.value }}</strong>
                <span class="text-xs text-slate-500">{{ item.label }}</span>
              </div>
            </div>
          </div>
        </div>
      </section>

      <!-- ============================================================ -->
      <!-- 右侧：登录面板（多认证方式） -->
      <!-- ============================================================ -->
      <section class="flex min-h-full items-start justify-center py-1 sm:py-3 xl:items-center xl:py-0">
        <div class="w-full max-w-[460px] animate-fadeIn rounded-lg border border-slate-300/70 bg-white/90 p-5 shadow-[0_24px_80px_rgba(15,23,42,0.12)] backdrop-blur-xl sm:p-7 xl:max-w-none xl:p-8">
          <!-- 移动端品牌标识 -->
          <div class="mb-5 flex items-center justify-between gap-3 rounded-lg border border-primary/10 bg-primary/5 p-3 xl:hidden">
            <div class="flex min-w-0 items-center gap-3">
              <span class="inline-flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary text-lg text-white shadow-[0_10px_24px_rgba(22,93,255,0.22)]">
                <i class="fa fa-cloud"></i>
              </span>
              <div class="min-w-0">
                <strong class="block truncate font-extrabold text-slate-800">CloudDrive</strong>
                <p class="truncate text-xs text-slate-500">私有云文件协作平台</p>
              </div>
            </div>
            <span class="hidden shrink-0 rounded-full bg-white px-2.5 py-1 text-xs font-extrabold text-primary shadow-card min-[430px]:inline-flex">
              安全登录
            </span>
          </div>

          <!-- 标题 -->
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-2.5 py-1.5 text-xs font-extrabold text-primary">
              <i class="fa fa-shield"></i>
              身份认证
            </span>
            <h2 class="mt-4 text-[28px] font-extrabold text-slate-800 sm:text-3xl">欢迎回来</h2>
            <p class="mt-2 text-sm leading-6 text-slate-500 sm:text-base">登录后继续管理文件、分享链接和协作消息。</p>
          </div>

          <!-- ============================================================ -->
          <!-- 登录方式 Tab 切换 -->
          <!-- ============================================================ -->
          <div class="mt-6">
            <div class="flex rounded-lg border border-slate-200 bg-slate-50 p-1">
              <button
                v-for="tab in loginTabs"
                :key="tab.key"
                type="button"
                class="flex-1 rounded-md px-3 py-2 text-sm font-extrabold transition duration-200"
                :class="activeTab === tab.key
                  ? 'bg-white text-primary shadow-sm'
                  : 'text-slate-500 hover:text-slate-700'"
                @click="activeTab = tab.key"
              >
                <i :class="tab.icon" class="mr-1.5 hidden sm:inline"></i>
                {{ tab.label }}
              </button>
            </div>
          </div>

          <!-- ============================================================ -->
          <!-- Tab 1: 密码登录 -->
          <!-- ============================================================ -->
          <div v-show="activeTab === 'password'" class="mt-6">
            <form class="grid gap-4" @submit.prevent="handlePasswordLogin">
              <label class="grid gap-2">
                <span class="text-sm font-extrabold text-slate-700">手机号</span>
                <div
                  class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
                  :class="focusedField === 'phone' ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]' : 'border-slate-200'"
                >
                  <i class="fa fa-mobile text-slate-400"></i>
                  <input
                    v-model.trim="phone"
                    type="tel"
                    inputmode="tel"
                    autocomplete="tel"
                    class="min-w-0 flex-1 border-0 bg-transparent outline-none"
                    placeholder="请输入手机号"
                    required
                    @focus="focusedField = 'phone'"
                    @blur="focusedField = ''"
                    @input="clearFormError"
                  >
                </div>
                <small v-if="phone && !phoneValid" class="text-danger">请输入 11 位手机号</small>
              </label>

              <label class="grid gap-2">
                <span class="text-sm font-extrabold text-slate-700">密码</span>
                <div
                  class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
                  :class="focusedField === 'password' ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]' : 'border-slate-200'"
                >
                  <i class="fa fa-lock text-slate-400"></i>
                  <input
                    v-model="password"
                    :type="showPassword ? 'text' : 'password'"
                    autocomplete="current-password"
                    class="min-w-0 flex-1 border-0 bg-transparent outline-none"
                    placeholder="请输入账号密码"
                    required
                    @focus="focusedField = 'password'"
                    @blur="focusedField = ''"
                    @input="clearFormError"
                  >
                  <button
                    type="button"
                    class="inline-flex h-[34px] w-[34px] shrink-0 items-center justify-center rounded-lg text-slate-500 transition hover:bg-slate-100 hover:text-primary"
                    :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                    @click="showPassword = !showPassword"
                  >
                    <i :class="showPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
                  </button>
                </div>
              </label>

              <div class="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
                <label class="inline-flex items-center gap-2 text-sm font-bold text-slate-700">
                  <input v-model="rememberDevice" type="checkbox" class="accent-primary">
                  <span>信任此设备</span>
                </label>
                <span class="text-xs text-slate-500">建议仅在个人设备启用</span>
              </div>

              <!-- Turnstile 人机验证 -->
              <div
                ref="turnstileContainer"
                :class="{ hidden: !turnstileSiteKey }"
              ></div>
              <p v-if="!turnstileSiteKey" class="text-xs text-danger">未配置 Turnstile Site Key</p>
              <p v-else-if="captchaError" class="text-xs text-danger">{{ captchaError }}</p>

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
                class="inline-flex min-h-12 items-center justify-center gap-2.5 rounded-lg bg-primary font-extrabold text-white shadow-[0_14px_30px_rgba(22,93,255,0.25)] transition duration-200 hover:-translate-y-px hover:bg-[#0e4fe0] hover:shadow-[0_18px_40px_rgba(22,93,255,0.30)] disabled:cursor-not-allowed disabled:bg-slate-300 disabled:shadow-none"
                :disabled="submitDisabled"
              >
                <span>{{ loading ? '正在登录' : '登录工作台' }}</span>
                <i :class="loading ? 'fa fa-spinner fa-spin' : 'fa fa-arrow-right'"></i>
              </button>
            </form>
          </div>

          <!-- ============================================================ -->
          <!-- Tab 2: 验证码登录 -->
          <!-- ============================================================ -->
          <div v-show="activeTab === 'code'" class="mt-6">
            <LoginFormCode
              @login-success="handleLoginSuccess"
              @login-error="(msg: string) => formError = msg"
            />
          </div>

          <!-- ============================================================ -->
          <!-- Tab 3: 扫码登录 -->
          <!-- ============================================================ -->
          <div v-show="activeTab === 'qr'" class="mt-6">
            <LoginFormQR
              @login-success="handleLoginSuccess"
            />
          </div>

          <!-- ============================================================ -->
          <!-- 第三方登录（所有 Tab 底部显示） -->
          <!-- ============================================================ -->
          <div class="mt-6">
            <LoginFormThirdParty
              @login-success="handleLoginSuccess"
            />
          </div>

          <!-- 注册入口 -->
          <div class="mt-5 text-center text-sm text-slate-500">
            <span>还没有团队账号？</span>
            <router-link to="/register" class="font-extrabold text-primary hover:underline">创建账号</router-link>
          </div>
        </div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import LoginFormCode from '@/components/auth/LoginFormCode.vue'
import LoginFormQR from '@/components/auth/LoginFormQR.vue'
import LoginFormThirdParty from '@/components/auth/LoginFormThirdParty.vue'

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const router = useRouter()
const authStore = useAuthStore()

// ============================================================
// 登录方式 Tab
// ============================================================
const activeTab = ref<'password' | 'code' | 'qr'>('password')

const loginTabs = [
  { key: 'password' as const, label: '密码登录', icon: 'fa fa-lock' },
  { key: 'code' as const, label: '验证码', icon: 'fa fa-shield' },
  { key: 'qr' as const, label: '扫码', icon: 'fa fa-qrcode' },
]

// ============================================================
// 密码登录状态
// ============================================================
const phone = ref(import.meta.env.VITE_DEMO_LOGIN_PHONE || '')
const password = ref(import.meta.env.VITE_DEMO_LOGIN_PASSWORD || '')
const loading = ref(false)
const formError = ref('')
const captchaError = ref('')
const captchaLoading = ref(false)
const captchaToken = ref('')
const turnstileContainer = ref(null)
const turnstileWidgetId = ref(null)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const focusedField = ref('')
const showPassword = ref(false)
const rememberDevice = ref(true)

const platformFeatures = [
  { icon: 'fa fa-search', title: '智能搜索', desc: '按关键词、类型与业务字段快速定位文件。' },
  { icon: 'fa fa-share-alt', title: '安全分享', desc: '统一管理分享链接、权限和过期策略。' },
  { icon: 'fa fa-folder-open-o', title: '文件工作台', desc: '上传、预览、收藏、下载保持一致体验。' },
  { icon: 'fa fa-trash-o', title: '回收站保护', desc: '删除记录可追踪，误删内容可恢复。' },
]

const workspacePreview = [
  { icon: 'fa fa-cloud-upload', title: '上传与归档', desc: '大文件、目录和业务资料进入统一空间。' },
  { icon: 'fa fa-eye', title: '预览与检索', desc: '在线预览常见文件，通过搜索快速回到上下文。' },
  { icon: 'fa fa-comments-o', title: '协作与分享', desc: '把文件、链接和消息放在同一个协作流程里。' },
]

const workspaceFlow = ['统一上传', '全文索引', '权限分享']

const trustSignals = [
  { icon: 'fa fa-lock', value: 'TLS', label: '加密传输' },
  { icon: 'fa fa-shield', value: 'Turnstile', label: '人机校验' },
  { icon: 'fa fa-history', value: 'Audit', label: '操作留痕' },
]

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))

const submitDisabled = computed(() => {
  return loading.value || !phoneValid.value || !password.value || !turnstileSiteKey || !captchaToken.value
})

function clearFormError() {
  formError.value = ''
}

// ============================================================
// 登录成功处理
// ============================================================
function handleLoginSuccess() {
  if (rememberDevice.value) localStorage.setItem('cloudDriveTrustedDevice', '1')
  router.push('/')
}

// ============================================================
// 密码登录
// ============================================================
async function handlePasswordLogin() {
  formError.value = ''
  captchaError.value = ''

  if (!phoneValid.value) {
    formError.value = '请输入正确的手机号'
    return
  }
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  loading.value = true
  const result = await authStore.login(phone.value, password.value, captchaToken.value)
  loading.value = false
  if (result.success) {
    handleLoginSuccess()
    return
  }

  resetTurnstile()
  formError.value = result.message || (result.scope === 'form' ? '手机号或密码错误' : '网络错误，请稍后重试')
}

// ============================================================
// Turnstile 验证
// ============================================================
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

async function renderTurnstile() {
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
      action: 'login',
      theme: 'light',
      size: 'normal',
      callback: (token: string) => {
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
  } catch {
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
.animate-fadeIn {
  animation: fadeIn 0.5s ease-out;
}
@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>