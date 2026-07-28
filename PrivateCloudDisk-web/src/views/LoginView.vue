<template>
  <div class="login-page">
    <!-- ============================================================
         左侧：品牌展示区 — 大气简约，无冗余信息
         参考：Linear / Vercel / Notion 登录页设计
         ============================================================ -->
    <aside class="login-brand">
      <div class="brand-bg">
        <div class="brand-glow brand-glow-1"></div>
        <div class="brand-glow brand-glow-2"></div>
        <div class="brand-grid"></div>
      </div>

      <div class="brand-inner">
        <!-- Logo -->
        <div class="brand-logo">
          <span class="brand-logo-icon">
            <i class="fa fa-cloud"></i>
          </span>
          <span class="brand-logo-text">CloudDrive</span>
        </div>

        <!-- 品牌标语 -->
        <div class="brand-content">
          <h1 class="brand-heading">
            企业级私有云<br />文件协作平台
          </h1>
          <p class="brand-subtitle">
            安全、高效、智能的文件管理体验
          </p>
        </div>

        <!-- 底部信任标识 -->
        <div class="brand-footer">
          <div class="brand-trust">
            <span v-for="item in trustItems" :key="item" class="trust-item">
              <i class="fa fa-check-circle text-emerald-400"></i>
              {{ item }}
            </span>
          </div>
        </div>
      </div>
    </aside>

    <!-- ============================================================
         右侧：登录表单区 — 简洁清晰，聚焦操作
         ============================================================ -->
    <main class="login-form-area">
      <div class="login-form-inner">
        <!-- 移动端品牌标识 -->
        <div class="mobile-brand">
          <span class="mobile-brand-icon">
            <i class="fa fa-cloud"></i>
          </span>
          <span class="mobile-brand-name">CloudDrive</span>
        </div>

        <!-- 标题 -->
        <div class="form-header">
          <h2 class="form-title">欢迎回来</h2>
          <p class="form-desc">登录您的 CloudDrive 账号以继续</p>
        </div>

        <!-- 登录方式 Tab -->
        <div class="login-tabs">
          <button
            v-for="tab in loginTabs"
            :key="tab.key"
            type="button"
            class="login-tab"
            :class="{ active: activeTab === tab.key }"
            @click="selectLoginTab(tab.key)"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 密码登录 -->
        <div v-show="activeTab === 'password'" class="tab-content">
          <form class="login-form" @submit.prevent="handlePasswordLogin">
            <!-- 需求九：账号、手机号和邮箱共用一个输入框，前端实时识别请求字段。 -->
            <div class="field-group">
              <label class="field-label">账号 / 手机号 / 邮箱</label>
              <div class="field-input-wrap" :class="{ focused: focusedField === 'identifier' }">
                <i :class="identifierIcon" class="field-icon"></i>
                <input
                  v-model.trim="identifier"
                  type="text"
                  inputmode="text"
                  autocomplete="username"
                  class="field-input"
                  placeholder="请输入 PCD 账号、手机号或邮箱"
                  required
                  @focus="handleCredentialFocus('identifier')"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                />
              </div>
              <p v-if="identifier" class="field-hint" :class="{ 'field-error': !identifierValid }">
                {{ identifierHint }}
              </p>
            </div>

            <!-- 密码 -->
            <div class="field-group">
              <label class="field-label">密码</label>
              <div class="field-input-wrap" :class="{ focused: focusedField === 'password' }">
                <i class="fa fa-lock field-icon"></i>
                <input
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="current-password"
                  class="field-input"
                  placeholder="请输入账号密码"
                  required
                  @focus="handleCredentialFocus('password')"
                  @blur="focusedField = ''"
                  @input="clearFormError"
                />
                <button type="button" class="field-toggle" @click="showPassword = !showPassword">
                  <i :class="showPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
                </button>
              </div>
            </div>

            <!-- 信任此设备 -->
            <label class="remember-row">
              <input v-model="rememberDevice" type="checkbox" class="remember-check" />
              <span>信任此设备（个人设备建议启用）</span>
            </label>

            <!-- Turnstile -->
            <div ref="turnstileContainer" :class="{ hidden: !turnstileSiteKey }"></div>
            <p v-if="!turnstileSiteKey" class="field-error">未配置 Turnstile Site Key</p>
            <p v-else-if="captchaError" class="field-error">{{ captchaError }}</p>

            <!-- 错误提示 -->
            <transition name="form-error-fade">
              <div v-if="formError" class="form-error-box">
                <i class="fa fa-exclamation-circle"></i>
                {{ formError }}
              </div>
            </transition>

            <!-- 登录按钮 -->
            <button
              type="submit"
              class="submit-btn"
              :disabled="submitDisabled"
            >
              <span v-if="loading" class="spinner"></span>
              <span>{{ loading ? '登录中...' : '登录' }}</span>
            </button>
          </form>
        </div>

        <!-- 验证码登录 -->
        <div v-show="activeTab === 'code'" class="tab-content">
          <LoginFormCode
            @login-success="handleLoginSuccess"
            @login-error="(msg: string) => formError = msg"
          />
        </div>

        <!-- 扫码登录 -->
        <div v-show="activeTab === 'qr'" class="tab-content">
          <LoginFormQR @login-success="handleLoginSuccess" />
        </div>

        <!-- 第三方登录 -->
        <div class="third-party-section">
          <div class="divider">
            <span>或</span>
          </div>
          <LoginFormThirdParty @login-success="handleLoginSuccess" />
        </div>

        <!-- 注册入口 -->
        <p class="switch-link">
          还没有账号？<router-link to="/register">创建账号</router-link>
        </p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'
import LoginFormCode from '@/components/auth/LoginFormCode.vue'
import LoginFormQR from '@/components/auth/LoginFormQR.vue'
import LoginFormThirdParty from '@/components/auth/LoginFormThirdParty.vue'
import { detectLoginIdentifierType } from '@/api/modules/users'
import { useToastStore } from '@/stores/toastStore'

const TURNSTILE_SCRIPT_ID = 'cloudflare-turnstile-script'
const TURNSTILE_SCRIPT_SRC = 'https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit'

const router = useRouter()
const authStore = useAuthStore()
const toastStore = useToastStore()

// 登录方式 Tab
const activeTab = ref<'password' | 'code' | 'qr'>('password')
const loginTabs = [
  { key: 'password' as const, label: '密码登录' },
  { key: 'code' as const, label: '验证码登录' },
  { key: 'qr' as const, label: '扫码登录' },
]

// 密码登录状态
const identifier = ref(import.meta.env.VITE_DEMO_LOGIN_IDENTIFIER || import.meta.env.VITE_DEMO_LOGIN_PHONE || '')
const password = ref(import.meta.env.VITE_DEMO_LOGIN_PASSWORD || '')
const loading = ref(false)
const formError = ref('')
const captchaError = ref('')
const captchaLoading = ref(false)
const captchaToken = ref('')
const turnstileContainer = ref(null)
const turnstileWidgetId = ref<string | null>(null)
const turnstileSiteKey = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''
const focusedField = ref('')
const showPassword = ref(false)
const rememberDevice = ref(true)

const trustItems = ['端到端加密', '操作审计', '等保合规']

const identifierType = computed(() => detectLoginIdentifierType(identifier.value))
const identifierValid = computed(() => {
  const value = identifier.value.trim()
  if (identifierType.value === 'phone_number') return /^1[3-9]\d{9}$/.test(value)
  if (identifierType.value === 'email') return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value) && value.length <= 254
  return /^[a-zA-Z0-9_]{4,16}$/.test(value)
})
const identifierHint = computed(() => {
  if (!identifierValid.value) return '请输入 4–16 位字母数字账号、11 位手机号或标准邮箱'
  if (identifierType.value === 'phone_number') return '已识别为手机号'
  if (identifierType.value === 'email') return '已识别为邮箱'
  return '已识别为 PCD 账号'
})
const identifierIcon = computed(() => {
  if (identifierType.value === 'phone_number') return 'fa fa-mobile'
  if (identifierType.value === 'email') return 'fa fa-envelope'
  return 'fa fa-user'
})

const submitDisabled = computed(() => {
  return loading.value || !identifierValid.value || !password.value || !turnstileSiteKey || !captchaToken.value
})

function clearFormError() {
  formError.value = ''
}

function selectLoginTab(tab: 'password' | 'code' | 'qr') {
  if (tab !== 'password') {
    // 【需求十一】保留入口布局作为后续接入点，但未实现功能不得发起占位网络请求。
    toastStore.showToast(`${tab === 'code' ? '验证码登录' : '扫码登录'}正在开发中，敬请期待`, 'info')
    return
  }
  activeTab.value = tab
}

function handleCredentialFocus(field: string) {
  focusedField.value = field
  void ensureTurnstile()
}

function handleLoginSuccess() {
  if (rememberDevice.value) localStorage.setItem('cloudDriveTrustedDevice', '1')
  router.push('/')
}

async function handlePasswordLogin() {
  formError.value = ''
  captchaError.value = ''

  if (!identifierValid.value) {
    formError.value = '请输入正确的账号、手机号或邮箱'
    return
  }
  if (!captchaToken.value) {
    captchaError.value = '请先完成安全验证'
    return
  }

  loading.value = true
  const result = await authStore.login(identifier.value, password.value, captchaToken.value)
  loading.value = false
  if (result.success) {
    handleLoginSuccess()
    return
  }

  resetTurnstile()
  formError.value = result.message || (result.scope === 'form' ? '账号或密码错误' : '网络错误，请稍后重试')
}

// Turnstile 验证
function loadTurnstileScript(): Promise<void> {
  if (window.turnstile) return Promise.resolve()

  const existingScript = document.getElementById(TURNSTILE_SCRIPT_ID)
  if (existingScript) {
    return new Promise<void>((resolve, reject) => {
      existingScript.addEventListener('load', () => resolve(), { once: true })
      existingScript.addEventListener('error', () => reject(new Error('Turnstile script failed')), { once: true })
    })
  }

  return new Promise<void>((resolve, reject) => {
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
      size: 'flexible',
      execution: 'execute',
      appearance: 'interaction-only',
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

  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.execute(turnstileWidgetId.value)
  }
}

async function ensureTurnstile() {
  if (captchaToken.value || captchaLoading.value || turnstileWidgetId.value !== null) return
  /*
   * 【需求十改动说明】
   * 原行为：页面 mounted 后立即加载第三方脚本并渲染组件。
   * 新行为：用户聚焦账号或密码输入框时才按需渲染，降低首屏外部资源开销；
   * 业务失败仍沿用 resetTurnstile，保证同一组件可重新获取挑战令牌。
   */
  await renderTurnstile()
}

function resetTurnstile() {
  captchaToken.value = ''
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.reset(turnstileWidgetId.value)
  }
}

onBeforeUnmount(() => {
  if (window.turnstile && turnstileWidgetId.value !== null) {
    window.turnstile.remove(turnstileWidgetId.value)
  }
})
</script>

<style scoped>
/* ============================================================
   整体布局
   ============================================================ */
.login-page {
  display: flex;
  min-height: 100vh;
  min-height: 100dvh;
}

/* ============================================================
   左侧品牌区
   ============================================================ */
.login-brand {
  display: none;
  position: relative;
  width: 44%;
  min-width: 480px;
  overflow: hidden;
  background: linear-gradient(160deg, #0f172a 0%, #1e293b 40%, #0f172a 100%);
}

@media (min-width: 1024px) {
  .login-brand {
    display: flex;
  }
}

.brand-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.brand-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.3;
}

.brand-glow-1 {
  width: 500px;
  height: 500px;
  background: radial-gradient(circle, rgba(22, 93, 255, 0.25), transparent 70%);
  top: -120px;
  right: -100px;
  animation: glowFloat 12s ease-in-out infinite alternate;
}

.brand-glow-2 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(14, 140, 106, 0.18), transparent 70%);
  bottom: -80px;
  left: -60px;
  animation: glowFloat 10s ease-in-out 4s infinite alternate;
}

@keyframes glowFloat {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(20px, -15px) scale(1.08); }
}

.brand-grid {
  position: absolute;
  inset: 0;
  opacity: 0.04;
  background-image:
    linear-gradient(rgba(255,255,255,0.8) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.8) 1px, transparent 1px);
  background-size: 60px 60px;
  mask-image: radial-gradient(ellipse at 30% 50%, black 40%, transparent 70%);
}

.brand-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  padding: 48px;
  width: 100%;
  height: 100%;
}

.brand-logo {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #165DFF, #0E8C6A);
  color: #fff;
  font-size: 22px;
  box-shadow: 0 8px 24px rgba(22, 93, 255, 0.3);
}

.brand-logo-text {
  font-size: 22px;
  font-weight: 700;
  color: #fff;
  letter-spacing: -0.5px;
}

.brand-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  margin-top: -60px;
}

.brand-heading {
  font-size: 40px;
  font-weight: 700;
  line-height: 1.15;
  color: #fff;
  letter-spacing: -1px;
}

.brand-subtitle {
  margin-top: 20px;
  font-size: 16px;
  line-height: 1.6;
  color: rgba(255, 255, 255, 0.55);
  max-width: 360px;
}

.brand-footer {
  margin-top: auto;
}

.brand-trust {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}

.trust-item {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: rgba(255, 255, 255, 0.45);
}

/* ============================================================
   右侧表单区
   ============================================================ */
.login-form-area {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background: #fff;
}

@media (max-width: 639px) {
  .login-form-area {
    padding: 20px 16px;
    align-items: flex-start;
    padding-top: 40px;
  }
}

.login-form-inner {
  width: 100%;
  max-width: 400px;
}

/* 移动端品牌 */
.mobile-brand {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 32px;
  justify-content: center;
}

@media (min-width: 1024px) {
  .mobile-brand {
    display: none;
  }
}

@media (max-width: 639px) {
  .mobile-brand {
    margin-bottom: 24px;
  }
}

.mobile-brand-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: 10px;
  background: linear-gradient(135deg, #165DFF, #0E8C6A);
  color: #fff;
  font-size: 20px;
}

.mobile-brand-name {
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}

/* 表单标题 */
.form-header {
  margin-bottom: 28px;
  text-align: center;
}

@media (max-width: 639px) {
  .form-header {
    margin-bottom: 20px;
  }
}

.form-title {
  font-size: 26px;
  font-weight: 700;
  color: #0f172a;
  letter-spacing: -0.5px;
}

@media (max-width: 639px) {
  .form-title {
    font-size: 22px;
  }
}

.form-desc {
  margin-top: 8px;
  font-size: 14px;
  color: #64748b;
}

@media (max-width: 639px) {
  .form-desc {
    font-size: 13px;
  }
}

/* 登录方式 Tab */
.login-tabs {
  display: flex;
  gap: 0;
  margin-bottom: 24px;
  background: #f1f5f9;
  border-radius: 10px;
  padding: 3px;
}

.login-tab {
  flex: 1;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  background: transparent;
  border: none;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.login-tab.active {
  background: #fff;
  color: #165DFF;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
}

.login-tab:not(.active):hover {
  color: #334155;
}

.tab-content {
  min-height: 280px;
}

/* 表单 */
.login-form {
  display: flex;
  flex-direction: column;
  gap: 18px;
}

@media (max-width: 639px) {
  .login-form {
    gap: 14px;
  }
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field-label {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.field-input-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
  height: 44px;
  padding: 0 14px;
  border: 1.5px solid #e2e8f0;
  border-radius: 10px;
  background: #fff;
  transition: all 0.2s ease;
}

.field-input-wrap.focused {
  border-color: #165DFF;
  box-shadow: 0 0 0 3px rgba(22, 93, 255, 0.08);
}

.field-icon {
  color: #94a3b8;
  font-size: 15px;
  flex-shrink: 0;
}

.field-input {
  flex: 1;
  border: none;
  outline: none;
  font-size: 14px;
  color: #0f172a;
  background: transparent;
}

.field-input::placeholder {
  color: #94a3b8;
}

.field-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  border-radius: 6px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.field-toggle:hover {
  background: #f1f5f9;
  color: #64748b;
}

.field-hint,
.field-error {
  font-size: 12px;
  margin: 0;
}

.field-hint {
  color: #64748b;
}

.field-error {
  color: #ef4444;
}

/* 记住设备 */
.remember-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #64748b;
  cursor: pointer;
}

.remember-check {
  width: 16px;
  height: 16px;
  accent-color: #165DFF;
  cursor: pointer;
}

/* 错误提示 */
.form-error-box {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  background: #fef2f2;
  color: #dc2626;
  font-size: 13px;
  border: 1px solid #fecaca;
}

.form-error-fade-enter-active,
.form-error-fade-leave-active {
  transition: all 0.2s ease;
}
.form-error-fade-enter-from,
.form-error-fade-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

/* 提交按钮 */
.submit-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 46px;
  width: 100%;
  border: none;
  border-radius: 10px;
  background: #165DFF;
  color: #fff;
  font-size: 15px;
  font-weight: 600;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 2px 8px rgba(22, 93, 255, 0.25);
}

.submit-btn:hover:not(:disabled) {
  background: #1452e0;
  box-shadow: 0 4px 16px rgba(22, 93, 255, 0.35);
  transform: translateY(-1px);
}

.submit-btn:active:not(:disabled) {
  transform: translateY(0);
}

.submit-btn:disabled {
  background: #cbd5e1;
  box-shadow: none;
  cursor: not-allowed;
  color: #94a3b8;
}

@media (max-width: 639px) {
  .submit-btn {
    height: 48px;
    font-size: 15px;
    border-radius: 12px;
  }
}

.spinner {
  width: 18px;
  height: 18px;
  border: 2px solid rgba(255, 255, 255, 0.3);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.6s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 第三方登录区域 */
.third-party-section {
  margin-top: 24px;
}

.divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: #e2e8f0;
}

.divider span {
  font-size: 12px;
  color: #94a3b8;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

/* 切换链接 */
.switch-link {
  margin-top: 24px;
  text-align: center;
  font-size: 14px;
  color: #64748b;
}

@media (max-width: 639px) {
  .switch-link {
    margin-top: 20px;
    font-size: 13px;
  }
}

.switch-link a {
  color: #165DFF;
  font-weight: 600;
  text-decoration: none;
  margin-left: 4px;
}

.switch-link a:hover {
  text-decoration: underline;
}
</style>
