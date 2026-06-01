<template>
  <aside class="relative bg-white shadow-lg transition-all duration-300" :class="collapsed ? 'w-20' : 'w-64'">
    <!-- Logo 区域 -->
    <div class="p-4 border-b" :class="{ 'text-center': collapsed }">
      <div class="flex items-center justify-center cursor-pointer">
        <i class="fa fa-cloud text-primary text-2xl"></i>
        <h1 v-if="!collapsed" class="ml-2 text-xl font-bold">
          CloudDrive <span class="text-primary">私有云</span>
        </h1>
      </div>
    </div>

    <!-- 导航菜单 -->
    <nav class="mt-6">
      <router-link
        v-for="item in menuItems"
        :key="item.path"
        :to="item.path"
        class="flex items-center px-4 py-3 text-neutral-600 hover:bg-primary/10 hover:text-primary transition"
        :class="{ 'justify-center': collapsed }"
      >
        <i :class="item.icon" class="w-5 h-5"></i>
        <span v-if="!collapsed" class="ml-3">{{ item.name }}</span>
      </router-link>
    </nav>

    <!-- 折叠/展开按钮：垂直居中，右侧边缘突出 -->
    <button
      @click="collapsed = !collapsed"
      class="absolute top-1/2 -translate-y-1/2 -right-3 w-6 h-6 bg-white border border-neutral-300 rounded-full shadow-md flex items-center justify-center text-neutral-500 hover:text-primary hover:border-primary transition z-10"
      :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
    >
      <i :class="collapsed ? 'fa fa-angle-right' : 'fa fa-angle-left'" class="text-sm"></i>
    </button>
  </aside>
</template>

<script setup>
import { ref } from 'vue'

const collapsed = ref(false)

const menuItems = [
  { path: '/', name: '我的网盘', icon: 'fa fa-cloud' },
  { path: '/starred', name: '收藏夹', icon: 'fa fa-star' },
  { path: '/shares', name: '分享管理', icon: 'fa fa-share-alt' },
  { path: '/trash', name: '回收站', icon: 'fa fa-trash' },
  { path: '/transfers', name: '传输记录', icon: 'fa fa-exchange' },
  { path: '/profile', name: '个人中心', icon: 'fa fa-user-circle' },
]
</script>

<style scoped>
/* 确保过渡平滑 */
aside {
  transition-property: width;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
}
</style>