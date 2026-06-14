<template>
  <div class="flex min-h-screen items-center justify-center bg-gradient-to-br from-neutral-50 via-white to-primary/5 px-4 py-12">
    <div class="w-full max-w-lg text-center">
      <!-- 404 图标 -->
      <div class="relative mx-auto w-40 h-40">
        <div class="absolute inset-0 flex items-center justify-center">
          <span class="text-[120px] font-extrabold leading-none text-neutral-100 select-none">404</span>
        </div>
        <div class="absolute inset-0 flex items-center justify-center">
          <i class="fa fa-cloud text-5xl text-primary/30"></i>
        </div>
      </div>

      <h1 class="mt-6 text-3xl font-bold text-neutral-900">页面未找到</h1>
      <p class="mt-3 text-neutral-500 max-w-sm mx-auto">
        你访问的页面可能已被移除、链接已失效，或者输入了错误的地址。
      </p>

      <!-- 导航选项 -->
      <div class="mt-8 grid grid-cols-1 gap-3 sm:grid-cols-2 max-w-md mx-auto">
        <router-link to="/" class="flex items-center gap-3 rounded-xl border border-primary/20 bg-primary/5 px-5 py-3.5 text-sm font-medium text-primary hover:bg-primary/10 hover:border-primary/30 transition">
          <i class="fa fa-home w-4 text-center"></i>
          返回官网首页
        </router-link>
        <router-link v-if="auth.isLoggedIn" to="/app" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-5 py-3.5 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
          <i class="fa fa-th-large w-4 text-center"></i>
          进入控制面板
        </router-link>
        <router-link to="/docs" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-5 py-3.5 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
          <i class="fa fa-book w-4 text-center"></i>
          帮助文档
        </router-link>
        <router-link to="/sitemap" class="flex items-center gap-3 rounded-xl border border-neutral-200 px-5 py-3.5 text-sm font-medium text-neutral-600 hover:border-primary/20 hover:text-primary hover:bg-neutral-50 transition">
          <i class="fa fa-sitemap w-4 text-center"></i>
          站点地图
        </router-link>
      </div>

      <!-- 搜索提示 -->
      <div class="mt-6">
        <p class="text-xs text-neutral-400">或者试试搜索</p>
        <router-link to="/docs" class="mt-2 inline-flex items-center gap-2 rounded-lg border border-neutral-200 px-4 py-2 text-sm text-neutral-400 hover:border-primary/30 hover:text-primary transition">
          <i class="fa fa-search text-xs"></i>
          搜索文档...
        </router-link>
      </div>

      <!-- 底部 -->
      <div class="mt-12 border-t border-neutral-100 pt-6">
        <p class="text-xs text-neutral-300">
          <span class="font-mono">HTTP 404</span>
          <span class="mx-2">|</span>
          <a href="mailto:support@clouddrive.com" class="hover:text-primary transition">support@clouddrive.com</a>
        </p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useAuthStore } from '@/stores/authStore'
const auth = useAuthStore()
</script>