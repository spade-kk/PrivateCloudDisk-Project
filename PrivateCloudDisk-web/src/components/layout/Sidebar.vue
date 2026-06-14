<template>
  <div class="contents">
    <Teleport to="body">
    <div
      v-if="mobileOpen"
      class="fixed inset-0 z-40 bg-black/40 lg:hidden"
      @click="$emit('close')"
    ></div>
    </Teleport>

    <aside
      class="fixed inset-y-0 left-0 z-50 flex h-dvh w-64 flex-col bg-white shadow-lg transition-transform duration-300 lg:sticky lg:top-0 lg:z-30 lg:h-screen lg:translate-x-0"
      :class="[mobileOpen ? 'translate-x-0' : '-translate-x-full', collapsed ? 'lg:w-20' : 'lg:w-64']"
    >
    <!-- Logo 区域 -->
    <div class="border-b p-4" :class="{ 'lg:text-center': collapsed }">
      <div class="flex cursor-pointer items-center justify-between lg:justify-center">
        <div class="flex min-w-0 items-center">
          <i class="fa fa-cloud shrink-0 text-2xl text-primary"></i>
          <h1 v-if="!collapsed" class="ml-2 truncate text-xl font-bold">
            CloudDrive <span class="text-primary">私有云</span>
          </h1>
        </div>
        <button
          @click="$emit('close')"
          class="icon-button mobile-only -mr-2"
          title="关闭导航"
        >
          <i class="fa fa-times"></i>
        </button>
      </div>
    </div>

    <!-- 导航菜单 -->
    <nav class="mt-4 flex-1 overflow-y-auto py-2 lg:mt-6">
      <template v-for="group in menuGroups" :key="group.label">
        <p v-if="!collapsed && group.label" class="mx-4 mb-1 mt-4 text-[10px] font-semibold uppercase tracking-widest text-neutral-400 first:mt-0">
          {{ group.label }}
        </p>
        <router-link
          v-for="item in group.items"
          :key="item.path"
          :to="item.path"
          v-slot="{ isActive }"
        >
          <a
            class="mx-2 flex min-h-11 items-center rounded-lg px-4 py-3 text-neutral-600 transition hover:bg-primary/10 hover:text-primary"
            :class="[
              isActive ? 'bg-primary/10 text-primary font-medium' : '',
              collapsed ? 'lg:justify-center' : ''
            ]"
          >
            <i :class="item.icon" class="h-5 w-5 shrink-0"></i>
            <span v-if="!collapsed" class="ml-3 truncate">{{ item.name }}</span>
          </a>
        </router-link>
      </template>
    </nav>

    <!-- 折叠/展开按钮：垂直居中，右侧边缘突出 -->
    <button
      @click="collapsed = !collapsed"
      class="desktop-only absolute -right-3 top-1/2 z-10 flex h-6 w-6 -translate-y-1/2 items-center justify-center rounded-full border border-neutral-300 bg-white text-neutral-500 shadow-md transition hover:border-primary hover:text-primary"
      :title="collapsed ? '展开侧边栏' : '收起侧边栏'"
    >
      <i :class="collapsed ? 'fa fa-angle-right' : 'fa fa-angle-left'" class="text-sm"></i>
    </button>
    </aside>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  mobileOpen: { type: Boolean, default: false },
})
defineEmits(['close'])

const collapsed = ref(false)

const menuGroups = [
  {
    label: '主菜单',
    items: [
      { path: '/', name: '我的网盘', icon: 'fa fa-cloud' },
      { path: '/search', name: '文件搜索', icon: 'fa fa-search' },
      { path: '/starred', name: '收藏夹', icon: 'fa fa-star' },
      { path: '/versions', name: '版本管理', icon: 'fa fa-history' },
      { path: '/shares', name: '分享管理', icon: 'fa fa-share-alt' },
      { path: '/notifications', name: '消息中心', icon: 'fa fa-bell' },
      { path: '/transfers', name: '传输记录', icon: 'fa fa-exchange' },
      { path: '/trash', name: '回收站', icon: 'fa fa-trash' },
    ],
  },
  {
    label: '协作与管理',
    items: [
      { path: '/team', name: '团队协作', icon: 'fa fa-users' },
      { path: '/admin', name: '管理后台', icon: 'fa fa-cog' },
      { path: '/analytics', name: '数据分析', icon: 'fa fa-bar-chart' },
    ],
  },
  {
    label: '安全与审计',
    items: [
      { path: '/security', name: '安全中心', icon: 'fa fa-shield' },
      { path: '/activity', name: '操作日志', icon: 'fa fa-list-alt' },
    ],
  },
  {
    label: '其他',
    items: [
      { path: '/settings', name: '系统设置', icon: 'fa fa-sliders' },
      { path: '/billing', name: '套餐管理', icon: 'fa fa-credit-card' },
      { path: '/help', name: '帮助中心', icon: 'fa fa-question-circle' },
      { path: '/profile', name: '个人中心', icon: 'fa fa-user-circle' },
    ],
  },
]
</script>

<style scoped>
/* 确保过渡平滑 */
aside {
  transition-property: width;
  transition-timing-function: cubic-bezier(0.4, 0, 0.2, 1);
}
</style>
