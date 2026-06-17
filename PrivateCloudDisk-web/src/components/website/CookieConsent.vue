<template>
  <Transition name="cookie-banner">
    <div v-if="show" class="fixed bottom-0 left-0 right-0 z-50 p-4 sm:p-6">
      <div class="mx-auto max-w-7xl">
        <div class="rounded-2xl border border-neutral-200 bg-white p-5 shadow-2xl shadow-neutral-900/10 sm:p-6">
          <div class="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex items-start gap-3">
              <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10">
                <i class="fa fa-cookie-bite text-primary"></i>
              </div>
              <div>
                <h3 class="text-sm font-semibold text-neutral-800">Cookie 使用声明</h3>
                <p class="mt-0.5 text-xs leading-relaxed text-neutral-500">
                  我们使用 Cookie 来提升您的浏览体验、分析网站流量并提供个性化内容。点击"接受全部"即表示您同意我们使用所有 Cookie。您也可以在
                  <a href="#" class="text-primary hover:underline">Cookie 设置</a> 中进行详细配置。详见
                  <router-link to="/privacy" class="text-primary hover:underline">隐私政策</router-link>。
                </p>
              </div>
            </div>
            <div class="flex items-center gap-2 shrink-0 sm:flex-col sm:items-stretch xl:flex-row">
              <button @click="acceptAll" class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90">接受全部</button>
              <button @click="acceptNecessary" class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-semibold text-neutral-600 transition hover:border-neutral-300 hover:bg-neutral-50">仅必要</button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'

const show = ref(false)

function acceptAll() {
  localStorage.setItem('cookie-consent', 'all')
  show.value = false
}

function acceptNecessary() {
  localStorage.setItem('cookie-consent', 'necessary')
  show.value = false
}

onMounted(() => {
  if (!localStorage.getItem('cookie-consent')) {
    setTimeout(() => { show.value = true }, 1500)
  }
})
</script>

<style scoped>
.cookie-banner-enter-active { transition: all 0.4s ease; }
.cookie-banner-leave-active { transition: all 0.3s ease; }
.cookie-banner-enter-from,
.cookie-banner-leave-to { opacity: 0; transform: translateY(100%); }
</style>