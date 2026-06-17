<template>
  <section class="py-16 sm:py-20">
    <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
      <div class="relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16">
        <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
        <div class="absolute -bottom-20 -left-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
        <div class="relative flex flex-col items-center text-center sm:flex-row sm:justify-between sm:text-left">
          <div>
            <h2 class="text-2xl font-bold text-white sm:text-3xl">{{ title }}</h2>
            <p class="mt-2 text-white/80 max-w-lg">{{ description }}</p>
          </div>
          <div class="mt-6 sm:mt-0 flex shrink-0">
            <form @submit.prevent="handleSubmit" class="flex w-full gap-3 sm:w-auto">
              <input
                v-model="email"
                type="email"
                :placeholder="placeholder"
                class="w-full sm:w-64 rounded-xl border-0 px-4 py-3 text-sm text-neutral-800 placeholder:text-neutral-400 focus:outline-none focus:ring-2 focus:ring-white/30"
                required
              />
              <button type="submit" :disabled="submitted" class="rounded-xl bg-white px-6 py-3 text-sm font-semibold text-primary transition hover:bg-neutral-50 disabled:opacity-50">
                {{ submitted ? buttonDoneText : buttonText }}
              </button>
            </form>
          </div>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps({
  title: { type: String, default: '订阅 CloudDrive 产品动态' },
  description: { type: String, default: '第一时间获取产品更新、技术分享和优惠活动信息' },
  placeholder: { type: String, default: '请输入您的邮箱地址' },
  buttonText: { type: String, default: '立即订阅' },
  buttonDoneText: { type: String, default: '已订阅' },
})

const email = ref('')
const submitted = ref(false)

function handleSubmit() {
  if (email.value) {
    submitted.value = true
    setTimeout(() => { submitted.value = false; email.value = '' }, 3000)
  }
}
</script>