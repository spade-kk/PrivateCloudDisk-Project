<!-- src/components/auth/LoginFormThirdParty.vue -->
<template>
  <div class="grid gap-4">
    <!-- 标题 -->
    <div class="flex items-center gap-3">
      <div class="h-px flex-1 bg-slate-200"></div>
      <span class="text-xs font-bold text-slate-400 uppercase tracking-wide">第三方账号登录</span>
      <div class="h-px flex-1 bg-slate-200"></div>
    </div>

    <!-- 第三方登录按钮 -->
    <div class="grid grid-cols-2 gap-3">
      <button
        v-for="provider in enabledProviders"
        :key="provider.id"
        type="button"
        class="inline-flex min-h-12 items-center justify-center gap-2 rounded-lg border px-4 font-extrabold transition duration-200 hover:-translate-y-0.5"
        :class="provider.classes"
        :disabled="loadingProvider === provider.id"
        @click="handleThirdPartyLogin(provider.id)"
      >
        <i v-if="loadingProvider === provider.id" class="fa fa-spinner fa-spin"></i>
        <span v-else v-html="provider.icon"></span>
        <span class="text-sm">{{ provider.label }}</span>
      </button>
    </div>

    <!-- Apple ID 登录（仅 Safari 显示） -->
    <button
      v-if="isSafari"
      type="button"
      class="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-lg border border-slate-800 bg-slate-900 px-4 font-extrabold text-white transition duration-200 hover:-translate-y-0.5 hover:bg-black hover:shadow-lg"
      :disabled="loadingProvider === 'apple'"
      @click="handleAppleLogin"
    >
      <i v-if="loadingProvider === 'apple'" class="fa fa-spinner fa-spin"></i>
      <svg v-else class="h-5 w-5" viewBox="0 0 24 24" fill="currentColor">
        <path d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.8 1.18-.24 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.38 5.98.48 7.13-.57 1.5-1.31 2.99-2.54 4.09zM12.03 7.25c-.15-2.23 1.66-4.07 3.74-4.25.29 2.58-2.34 4.5-3.74 4.25z"/>
      </svg>
      <span class="text-sm">使用 Apple 登录</span>
    </button>

    <transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="-translate-y-1 opacity-0"
      enter-to-class="translate-y-0 opacity-100"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="translate-y-0 opacity-100"
      leave-to-class="-translate-y-1 opacity-0"
    >
      <div v-if="error" class="flex items-center gap-2 rounded-lg bg-danger/10 px-3 py-2.5 text-sm text-danger">
        <i class="fa fa-exclamation-circle"></i>
        <span>{{ error }}</span>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { getThirdPartyAuthUrlApi } from '@/api/modules/auth'

const emit = defineEmits<{
  loginSuccess: []
}>()

const error = ref('')
const loadingProvider = ref('')

/** 是否为 Safari 浏览器 */
const isSafari = computed(() => {
  const ua = navigator.userAgent
  return /Safari/i.test(ua) && !/Chrome|CriOS|Edge/i.test(ua)
})

/** 可用的第三方登录平台 */
const providers = [
  {
    id: 'wechat',
    label: '微信登录',
    icon: '<svg class="h-5 w-5" viewBox="0 0 24 24" fill="#07C160"><path d="M8.69 3.45c3.88 0 7.03 2.88 7.03 6.44 0 3.56-3.15 6.44-7.03 6.44-.88 0-1.73-.15-2.52-.42l-2.1.63.6-1.87c-1.15-1.2-1.86-2.77-1.86-4.49 0-3.56 3.15-6.44 7.03-6.44zm-.19 2.48c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1zm3.5 0c-.55 0-1 .45-1 1s.45 1 1 1 1-.45 1-1-.45-1-1-1zm-3.1 5.82c1.8 0 3.45-.55 4.8-1.5h-4.8v1.5zm4.19-3.82c-.87 0-1.58.37-2.08.98l1.27.8h-3.19c-.67 0-1.22.55-1.22 1.22s.55 1.22 1.22 1.22h3.56c.35.47.57 1.02.57 1.6 0 1.44-1.17 2.6-2.6 2.6-.34 0-.66-.07-.96-.19l-1.56.47.44-1.4c-.7-.7-1.13-1.67-1.13-2.73 0-2.16 1.76-3.92 3.92-3.92h.01v.01z"/></svg>',
    classes: 'border-green-200 bg-green-50 text-green-700 hover:border-green-400 hover:bg-green-100',
  },
  {
    id: 'qq',
    label: 'QQ 登录',
    icon: '<svg class="h-5 w-5" viewBox="0 0 24 24" fill="#12B7F5"><circle cx="12" cy="12" r="10" fill="#12B7F5"/><text x="12" y="16" text-anchor="middle" fill="white" font-size="10" font-weight="bold">QQ</text></svg>',
    classes: 'border-sky-200 bg-sky-50 text-sky-700 hover:border-sky-400 hover:bg-sky-100',
  },
  {
    id: 'github',
    label: 'GitHub',
    icon: '<svg class="h-5 w-5" viewBox="0 0 24 24" fill="#24292F"><path d="M12 0C5.37 0 0 5.37 0 12c0 5.31 3.435 9.795 8.205 11.385.6.105.825-.255.825-.57 0-.285-.015-1.23-.015-2.235-3.015.555-3.795-.735-4.035-1.41-.135-.345-.72-1.41-1.23-1.695-.42-.225-1.02-.78-.015-.795.945-.015 1.62.87 1.845 1.23 1.08 1.815 2.805 1.305 3.495.99.105-.78.42-1.305.765-1.605-2.67-.3-5.46-1.335-5.46-5.925 0-1.305.465-2.385 1.23-3.225-.12-.3-.54-1.53.12-3.18 0 0 1.005-.315 3.3 1.23.96-.27 1.98-.405 3-.405s2.04.135 3 .405c2.295-1.56 3.3-1.23 3.3-1.23.66 1.65.24 2.88.12 3.18.765.84 1.23 1.905 1.23 3.225 0 4.605-2.805 5.625-5.475 5.925.435.375.81 1.095.81 2.22 0 1.605-.015 2.895-.015 3.3 0 .315.225.69.825.57A12.02 12.02 0 0024 12c0-6.63-5.37-12-12-12z"/></svg>',
    classes: 'border-slate-300 bg-slate-50 text-slate-700 hover:border-slate-500 hover:bg-slate-100',
  },
]

/** 已启用的第三方平台（可根据配置控制） */
const enabledProviders = providers

/** 发起第三方登录 */
async function handleThirdPartyLogin(providerId: string) {
  error.value = ''
  loadingProvider.value = providerId

  try {
    const state = crypto.randomUUID()
    const res = await getThirdPartyAuthUrlApi(providerId, state)

    if (res.code === 200 && res.data?.authorizationUrl) {
      // 保存 state 到 sessionStorage 用于回调验证
      sessionStorage.setItem(`oauth_state_${providerId}`, state)
      // 跳转到第三方授权页面
      window.location.href = res.data.authorizationUrl
    } else {
      error.value = res.message || '获取授权链接失败'
    }
  } catch (e: any) {
    error.value = e?.message || '获取授权链接失败'
  } finally {
    loadingProvider.value = ''
  }
}

/** Apple ID 登录 */
async function handleAppleLogin() {
  error.value = ''
  loadingProvider.value = 'apple'

  try {
    // Apple Sign In 使用 Web Authentication API 或 Sign in with Apple JS
    // 这里提供标准 Redirect 模式
    const state = crypto.randomUUID()
    const res = await getThirdPartyAuthUrlApi('apple', state)

    if (res.code === 200 && res.data?.authorizationUrl) {
      sessionStorage.setItem('oauth_state_apple', state)
      window.location.href = res.data.authorizationUrl
    } else {
      error.value = res.message || '获取 Apple 授权链接失败'
    }
  } catch (e: any) {
    error.value = e?.message || '获取 Apple 授权链接失败'
  } finally {
    loadingProvider.value = ''
  }
}
</script>