<!-- src/components/modals/LoginModal.vue -->
<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-md p-6 fade-in">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-xl font-bold text-neutral-700">用户登录</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700 transition">
          <i class="fa fa-times text-xl"></i>
        </button>
      </div>

      <!-- 登录方式 Tab -->
      <div class="flex rounded-lg border border-neutral-200 bg-neutral-50 p-1 mb-4">
        <button
          v-for="tab in loginTabs"
          :key="tab.key"
          type="button"
          class="flex-1 rounded-md px-3 py-1.5 text-xs font-bold transition duration-200"
          :class="activeTab === tab.key
            ? 'bg-white text-primary shadow-sm'
            : 'text-neutral-500 hover:text-neutral-700'"
          @click="activeTab = tab.key"
        >
          {{ tab.label }}
        </button>
      </div>

      <!-- 密码登录 -->
      <form v-if="activeTab === 'password'" @submit.prevent="handleSubmit">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">手机号</label>
            <input
              v-model="phone"
              type="tel"
              class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
              placeholder="请输入手机号"
              required
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">密码</label>
            <input
              v-model="password"
              type="password"
              class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
              placeholder="请输入密码"
              required
            />
          </div>
          <button
            type="submit"
            :disabled="loading"
            class="w-full bg-primary hover:bg-primary/90 text-white py-2.5 rounded-lg font-medium flex items-center justify-center space-x-2 transition"
          >
            <i v-if="loading" class="fa fa-spinner fa-spin"></i>
            <i v-else class="fa fa-sign-in"></i>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </div>
      </form>

      <!-- 验证码登录 -->
      <div v-if="activeTab === 'code'" class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-neutral-600 mb-1">手机号</label>
          <input
            v-model="codePhone"
            type="tel"
            class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
            placeholder="请输入手机号"
          />
        </div>
        <div class="flex gap-2">
          <div class="flex-1">
            <label class="block text-sm font-medium text-neutral-600 mb-1">验证码</label>
            <input
              v-model="smsCode"
              type="text"
              maxlength="6"
              class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
              placeholder="6位验证码"
            />
          </div>
          <button
            type="button"
            :disabled="codeLoading || codeCountdown > 0"
            class="mt-6 shrink-0 rounded-lg px-3 py-2 text-sm font-bold text-primary hover:bg-primary/5 transition disabled:text-neutral-400"
            @click="sendModalCode"
          >
            {{ codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码' }}
          </button>
        </div>
        <button
          :disabled="codeLoading"
          class="w-full bg-primary hover:bg-primary/90 text-white py-2.5 rounded-lg font-medium flex items-center justify-center space-x-2 transition"
          @click="handleCodeSubmit"
        >
          <i v-if="codeLoading" class="fa fa-spinner fa-spin"></i>
          <i v-else class="fa fa-shield"></i>
          <span>{{ codeLoading ? '登录中...' : '验证码登录' }}</span>
        </button>
      </div>

      <!-- 错误提示 -->
      <div v-if="error" class="mt-3 flex items-center gap-2 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-600">
        <i class="fa fa-exclamation-circle"></i>
        <span>{{ error }}</span>
      </div>

      <!-- 第三方登录 -->
      <div class="mt-4">
        <div class="flex items-center gap-2 mb-3">
          <div class="h-px flex-1 bg-neutral-200"></div>
          <span class="text-xs text-neutral-400">其他方式</span>
          <div class="h-px flex-1 bg-neutral-200"></div>
        </div>
        <div class="flex justify-center gap-3">
          <button
            v-for="p in thirdPartyProviders"
            :key="p.id"
            type="button"
            class="inline-flex h-10 w-10 items-center justify-center rounded-full border border-neutral-200 text-neutral-500 transition hover:border-primary hover:text-primary hover:bg-primary/5"
            :title="p.label"
            @click="handleThirdParty(p.id)"
          >
            <i :class="p.icon"></i>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { sendVerificationCodeApi } from '@/api/modules/users'
import { getThirdPartyAuthUrlApi } from '@/api/modules/auth'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'login'])

const authStore = useAuthStore()

const activeTab = ref<'password' | 'code'>('password')
const loginTabs = [
  { key: 'password' as const, label: '密码登录' },
  { key: 'code' as const, label: '验证码' },
]

const thirdPartyProviders = [
  { id: 'wechat', label: '微信', icon: 'fa fa-wechat' },
  { id: 'qq', label: 'QQ', icon: 'fa fa-qq' },
  { id: 'github', label: 'GitHub', icon: 'fa fa-github' },
]

// 密码登录
const phone = ref('')
const password = ref('')
const loading = ref(false)
const error = ref('')

// 验证码登录
const codePhone = ref('')
const smsCode = ref('')
const codeLoading = ref(false)
const codeCountdown = ref(0)

async function handleSubmit() {
  if (!phone.value.trim() || !password.value.trim()) return
  error.value = ''

  loading.value = true
  const result = await authStore.login(phone.value, password.value, '')
  loading.value = false

  if (result.success) {
    emit('login')
  } else {
    error.value = result.message || '手机号或密码错误'
  }
}

async function sendModalCode() {
  if (!/^1[3-9]\d{9}$/.test(codePhone.value)) {
    error.value = '请输入正确的手机号'
    return
  }
  error.value = ''

  try {
    const res = await sendVerificationCodeApi(codePhone.value, '', 'code_login', 'login')
    if (res.code === 200) {
      codeCountdown.value = 60
      const timer = setInterval(() => {
        codeCountdown.value--
        if (codeCountdown.value <= 0) clearInterval(timer)
      }, 1000)
    } else {
      error.value = res.message || '发送失败'
    }
  } catch (e: any) {
    error.value = e?.message || '发送失败'
  }
}

async function handleCodeSubmit() {
  if (!/^1[3-9]\d{9}$/.test(codePhone.value) || smsCode.value.length !== 6) return
  error.value = ''

  codeLoading.value = true
  const result = await authStore.codeLogin(codePhone.value, smsCode.value, 'phone')
  codeLoading.value = false

  if (result.success) {
    emit('login')
  } else {
    error.value = result.message || '验证码登录失败'
  }
}

async function handleThirdParty(provider: string) {
  error.value = ''
  try {
    const state = crypto.randomUUID()
    const res = await getThirdPartyAuthUrlApi(provider, state)
    if (res.code === 200 && res.data?.authorizationUrl) {
      sessionStorage.setItem(`oauth_state_${provider}`, state)
      window.location.href = res.data.authorizationUrl
    } else {
      error.value = res.message || '获取授权链接失败'
    }
  } catch (e: any) {
    error.value = e?.message || '获取授权链接失败'
  }
}
</script>

<style scoped>
.fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>