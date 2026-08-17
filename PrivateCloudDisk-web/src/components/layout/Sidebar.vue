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

    <!-- 空间选择器 -->
    <div class="border-b">
      <SpaceSelector
        :collapsed="collapsed"
        @create="showCreateSpaceDialog = true"
      />
    </div>

    <!-- 导航菜单 -->
    <nav class="mt-4 flex-1 overflow-y-auto py-2 lg:mt-6">
      <template v-for="group in menuGroups" :key="group.label">
        <p v-if="!collapsed && group.label" class="mx-4 mb-1 mt-4 text-[10px] font-semibold uppercase tracking-widest text-neutral-400 first:mt-0">
          {{ group.label }}
        </p>
        <template v-for="item in group.items" :key="item.path">
          <!-- 有子菜单的项 -->
          <div v-if="item.children && !collapsed" class="px-2">
            <div
              class="flex min-h-11 cursor-pointer items-center rounded-lg px-4 py-3 text-neutral-600 transition hover:bg-primary/10 hover:text-primary"
              :class="isItemActive(item) ? 'bg-primary/10 text-primary font-medium' : ''"
              @click="toggleSubmenu(item.path)"
            >
              <i :class="item.icon" class="h-5 w-5 shrink-0"></i>
              <span class="ml-3 truncate">{{ item.name }}</span>
              <i class="fa fa-chevron-down ml-auto text-xs transition-transform" :class="activeSubmenu === item.path ? 'rotate-180' : ''"></i>
            </div>
            <div v-if="activeSubmenu === item.path" class="ml-6 space-y-1">
              <router-link
                v-for="child in item.children"
                :key="child.path"
                :to="child.path"
                v-slot="{ isActive: childActive }"
              >
                <a
                  class="flex items-center rounded-lg px-4 py-2 text-sm text-neutral-500 transition hover:bg-primary/5 hover:text-primary"
                  :class="childActive ? 'bg-primary/10 text-primary font-medium' : ''"
                >
                  <i :class="child.icon" class="mr-2"></i>
                  {{ child.name }}
                </a>
              </router-link>
            </div>
          </div>
          <!-- 无子菜单的项 -->
          <router-link
            v-else
            :to="item.path"
            v-slot="{ isExactActive }"
          >
            <a
              class="mx-2 flex min-h-11 items-center rounded-lg px-4 py-3 text-neutral-600 transition hover:bg-primary/10 hover:text-primary"
              :class="[
                isExactActive || isItemActive(item) ? 'bg-primary/10 text-primary font-medium' : '',
                collapsed ? 'lg:justify-center' : ''
              ]"
            >
              <i :class="item.icon" class="h-5 w-5 shrink-0"></i>
              <span v-if="!collapsed" class="ml-3 truncate">{{ item.name }}</span>
            </a>
          </router-link>
        </template>
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

    <!-- 创建空间对话框 -->
    <CreateSpaceDialog v-model:visible="showCreateSpaceDialog" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute } from 'vue-router'
import SpaceSelector from '@/components/space/SpaceSelector.vue'
import CreateSpaceDialog from '@/components/space/CreateSpaceDialog.vue'

defineProps({
  mobileOpen: { type: Boolean, default: false },
})
defineEmits(['close'])

const route = useRoute()
const collapsed = ref(false)
const activeSubmenu = ref<string | null>(null)
const showCreateSpaceDialog = ref(false)

// 检查当前路由是否匹配某一项（包括子菜单）
function isItemActive(item: any): boolean {
  if (!item.children) return false
  return item.children.some((child: any) => route.path === child.path)
}

function toggleSubmenu(path: string) {
  if (activeSubmenu.value === path) {
    activeSubmenu.value = null
  } else {
    activeSubmenu.value = path
  }
}

const menuGroups = [
  {
    label: '主菜单',
    items: [
      { path: '/app', name: '我的网盘', icon: 'fa fa-cloud' },
      { path: '/app/spaces', name: '空间管理', icon: 'fa fa-cubes' },
      { path: '/app/search', name: '文件搜索', icon: 'fa fa-search' },
      { path: '/explore', name: '探索公开仓库', icon: 'fa fa-compass' },
      { path: '/app/starred', name: '收藏夹', icon: 'fa fa-star' },
      { path: '/app/tagged', name: '标签管理', icon: 'fa fa-tags' },
      { path: '/app/recent', name: '最近访问', icon: 'fa fa-clock-o' },
      { path: '/app/shares', name: '分享管理', icon: 'fa fa-share-alt' },
      { path: '/app/transfers', name: '传输记录', icon: 'fa fa-exchange' },
      { path: '/app/trash', name: '回收站', icon: 'fa fa-trash' },
    ],
  },
  {
    label: '文件管理',
    items: [
      { path: '/app/versions', name: '版本管理', icon: 'fa fa-history' },
      { path: '/app/storage', name: '存储空间', icon: 'fa fa-hdd-o' },
    ],
  },
  {
    label: '协作',
    items: [
      { path: '/teamwork', name: '团队协作', icon: 'fa fa-users' },
    ],
  },
  {
    label: '自动化',
    items: [
      { path: '/app/plugins', name: '插件管理', icon: 'fa fa-puzzle-piece' },
      { path: '/app/workflows', name: '工作流', icon: 'fa fa-random' },
      { path: '/app/space-tools', name: '空间工具', icon: 'fa fa-briefcase' },
      { path: '/app/plugin-market', name: '插件市场', icon: 'fa fa-shopping-bag' },
      { path: '/app/workflow-market', name: '工作流市场', icon: 'fa fa-sitemap' },
    ],
  },
  {
    label: '安全',
    items: [
      { path: '/app/security', name: '安全中心', icon: 'fa fa-shield' },
      { path: '/app/devices', name: '我的设备', icon: 'fa fa-laptop' },
    ],
  },
  {
    label: '其他',
    items: [
      { path: '/app/billing', name: '套餐管理', icon: 'fa fa-credit-card', children: [
        { path: '/app/billing', name: '我的套餐', icon: 'fa fa-cube' },
        { path: '/app/billing/orders', name: '订单管理', icon: 'fa fa-file-text-o' },
      ]},
      { path: '/app/notifications', name: '消息中心', icon: 'fa fa-bell' },
      { path: '/app/settings', name: '系统设置', icon: 'fa fa-sliders' },
      { path: '/app/profile', name: '个人中心', icon: 'fa fa-user-circle' },
      { path: '/app/help', name: '帮助中心', icon: 'fa fa-question-circle' },
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
