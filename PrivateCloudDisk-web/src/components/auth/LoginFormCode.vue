<!-- src/components/auth/LoginFormCode.vue -->
<template>
  <form class="grid gap-4" @submit.prevent="handleSubmit">
    <!-- 登录方式切换 -->
    <div class="flex rounded-lg border border-slate-200 bg-slate-50 p-1">
      <button
        v-for="opt in typeOptions"
        :key="opt.value"
        type="button"
        class="flex-1 rounded-md px-3 py-2 text-sm font-extrabold transition duration-200"
        :class="loginType === opt.value
          ? 'bg-white text-primary shadow-sm'
          : 'text-slate-500 hover:text-slate-700'"
        @click="loginType = opt.value as 'phone' | 'email'"
      >
        <i :class="opt.icon" class="mr-1.5"></i>
        {{ opt.label }}
      </button>
    </div>

    <!-- 手机号 / 邮箱 输入 -->
    <label class="grid gap-2">
      <span class="text-sm font-extrabold text-slate-700">
        {{ loginType === 'phone' ? '手机号' : '邮箱地址' }}
      </span>
      <div
        class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
        :class="focusedField === 'target' ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]' : 'border-slate-200'"
      >
        <i :class="loginType === 'phone' ? 'fa fa-mobile' : 'fa fa-envelope'" class="text-slate-400"></i>
        <input
          v-model.trim="target"
          :type="loginType === 'phone' ? 'tel' : 'email'"
          :inputmode="loginType === 'phone' ? 'tel' : 'email'"
          :autocomplete="loginType === 'phone' ? 'tel' : 'email'"
          class="min-w-0 flex-1 border-0 bg-transparent outline-none"
          :placeholder="loginType === 'phone' ? '请输入手机号' : '请输入邮箱地址'"
          required
          @focus="focusedField = 'target'"
          @blur="focusedField = ''"
          @input="clearFormError"
        >
      </div>
    </label>

    <!-- 验证码输入 -->
    <label class="grid gap-2">
      <span class="text-sm font-extrabold text-slate-700">验证码</span>
      <div
        class="flex min-h-12 items-center gap-2.5 rounded-lg border bg-white px-3 transition duration-200"
        :class="focusedField === 'code' ? '-translate-y-px border-primary shadow-[0_0_0_4px_rgba(22,93,255,0.10)]' : 'border-slate-200'"
      >
        <i class="fa fa-shield text-slate-400"></i>
        <input
          v-model.trim="code"
          type="text"
          inputmode="numeric"
          autocomplete="one-time-code"
          maxlength="6"
          class="min-w-0 flex-1 border-0 bg-transparent outline-none"
          placeholder="请输入6位验证码"
          required
          @focus="focusedField = 'code'"
          @blur="focusedField = ''"
          @input="clearFormError"
        >
        <button
          type="button"
          :disabled="countdown > 0 || !targetValid"
          class="shrink-0 rounded-md px-3 py-1.5 text-sm font-extrabold transition duration-200"
          :class="countdown > 0 || !targetValid
            ? 'cursor-not-allowed text-slate-400'
            : 'text-primary hover:bg-primary/5'"
          @click="sendCode"
        >
          {{ countdown > 0 ? `${countdown}s 后重发` : (codeSent ? '重新发送' : '获取验证码') }}
        </button>
      </div>
    </label>

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
      :disabled="loading || !targetValid || code.length !== 6"
    >
      <span>{{ loading ? '正在登录' : '验证码登录' }}</span>
      <i :class="loading ? 'fa fa-spinner fa-spin' : 'fa fa-arrow-right'"></i>
    </button>
  </form>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const emit = defineEmits<{
  loginSuccess: []
  loginError: [message: string]
}>()

const toastStore = useToastStore()

const loginType = ref<'phone' | 'email'>('phone')
const target = ref('')
const code = ref('')
const loading = ref(false)
const formError = ref('')
const focusedField = ref('')
const countdown = ref(0)
const codeSent = ref(false)

const typeOptions = [
  { value: 'phone', label: '手机验证码', icon: 'fa fa-mobile' },
  { value: 'email', label: '邮箱验证码', icon: 'fa fa-envelope' },
]

const targetValid = computed(() => {
  if (loginType.value === 'phone') return /^1[3-9]\d{9}$/.test(target.value)
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(target.value)
})

function clearFormError() {
  formError.value = ''
}

async function sendCode() {
  if (!targetValid.value || countdown.value > 0) return
  /*
   * 【需求十一改动说明】
   * 原行为：未确认服务端契约时仍调用验证码接口，容易产生 404 和无效风控计数。
   * 新行为：保留完整 UI 与此函数作为后续接入定位点，当前只展示友好提示，不发起网络请求。
   */
  const message = `${loginType.value === 'phone' ? '手机号' : '邮箱'}验证码登录正在开发中，敬请期待`
  formError.value = message
  toastStore.showToast(message, 'info')
}

async function handleSubmit() {
  formError.value = ''
  if (!targetValid.value || code.value.length !== 6) return
  // 【需求十一】后端验证码登录正式开放后，在此处恢复 authStore.codeLogin 调用。
  const message = '验证码登录正在开发中，敬请期待'
  formError.value = message
  toastStore.showToast(message, 'info')
  emit('loginError', message)
}
</script>
