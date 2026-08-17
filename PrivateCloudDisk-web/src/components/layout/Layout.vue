<template>
  <div class="app-shell flex overflow-hidden bg-neutral-100" :class="{ dark: isDarkMode }">
    <Sidebar :mobile-open="mobileSidebarOpen" @close="mobileSidebarOpen = false" />
    <div class="app-content flex min-h-0 flex-1 flex-col overflow-hidden">
      <header class="console-header z-20 shrink-0 bg-white shadow-sm">
        <div class="flex min-h-[68px] items-center justify-between gap-3 px-4 py-3 sm:px-5 lg:px-6">
          <div class="flex min-w-0 items-center gap-3 lg:gap-5">
            <button
              @click="mobileSidebarOpen = true"
              class="icon-button mobile-only shrink-0"
              title="打开导航"
            >
              <i class="fa fa-bars"></i>
            </button>
            <router-link to="/app" class="hidden shrink-0 items-center gap-2 rounded-xl px-2 py-1.5 transition hover:bg-neutral-50 lg:flex">
              <span class="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-info text-white shadow-sm shadow-primary/20">
                <i class="fa fa-cloud"></i>
              </span>
              <span class="leading-tight">
                <span class="block text-sm font-bold text-neutral-800">CloudDrive</span>
                <span class="block text-[11px] font-medium text-primary">控制台</span>
              </span>
            </router-link>
            <div class="hidden h-7 w-px bg-neutral-200 lg:block"></div>
            <div class="min-w-0">
              <p class="text-[11px] font-medium text-neutral-400 lg:hidden">当前模块</p>
              <h2 class="truncate text-base font-semibold text-neutral-700 sm:text-lg">{{ currentRouteName }}</h2>
            </div>
          </div>
          <div class="flex min-w-0 items-center justify-end gap-2 sm:gap-3 lg:gap-4">
            <router-link to="/" class="hidden items-center gap-1.5 rounded-lg border border-neutral-200 px-3 py-2 text-xs font-medium text-neutral-500 transition hover:border-primary/30 hover:bg-primary/5 hover:text-primary lg:inline-flex">
              <i class="fa fa-globe"></i>
              官网
            </router-link>
            <router-link to="/download" class="hidden items-center gap-1.5 rounded-lg bg-primary/10 px-3 py-2 text-xs font-medium text-primary transition hover:bg-primary/15 lg:inline-flex">
              <i class="fa fa-download"></i>
              客户端
            </router-link>
            <button @click="toggleDarkMode" class="icon-button shrink-0" title="切换深色模式">
              <i :class="isDarkMode ? 'fa fa-sun-o' : 'fa fa-moon-o'"></i>
            </button>
            <StorageInfo class="hidden sm:flex" />
            <TransferPanel />
            <NotificationCenter />
            <UserDropdown />
          </div>
        </div>
      </header>
      <main class="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5 lg:p-6">
        <div class="page-container">
          <router-view v-slot="{ Component, route: viewRoute }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" :key="viewRoute.fullPath" />
            </transition>
          </router-view>
        </div>
      </main>
      <footer class="shrink-0 border-t border-neutral-200 bg-white py-3 sm:py-4">
        <div class="px-4 text-center text-xs text-neutral-500 sm:text-sm">
          <p>© 2025 CloudDrive 私有云网盘管理系统</p>
        </div>
      </footer>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import Sidebar from './Sidebar.vue'
import UserDropdown from './UserDropdown.vue'
import NotificationCenter from './NotificationCenter.vue'
import TransferPanel from '../transfer/TransferPanel.vue'
import StorageInfo from '../file/StorageInfo.vue'
import { useSpaceStore } from '@/stores/spaceStore'

const route = useRoute()
const isDarkMode = ref(localStorage.getItem('darkMode') === 'true')
const mobileSidebarOpen = ref(false)

const currentRouteName = computed(() => {
  const names = {
    Dashboard: '我的网盘', Search: '文件搜索', Starred: '收藏夹', Notifications: '消息中心',
    Shares: '分享管理', Trash: '回收站', Profile: '个人中心', Transfers: '传输记录',
    Versions: '版本管理', Team: '团队协作', Admin: '管理后台', Analytics: '数据分析',
    Security: '安全中心', ApiKeys: 'API 密钥管理', ActivityLog: '操作日志',
    Settings: '系统设置', Billing: '套餐管理', BillingOrders: '订单管理',
    BillingPayment: '订单支付', BillingPaymentSuccess: '支付成功',
    BillingRefund: '申请退款', BillingRefundSuccess: '退款成功',
    Help: '帮助中心', VideoPlayer: '视频播放', Spaces: '空间管理',
    PluginManagement: '插件管理', PluginCreate: '创建插件',
    PluginMarketplace: '插件市场', SpacePluginManagement: '空间工具',
    WorkflowManagement: '工作流管理', WorkflowCreate: '创建工作流',
    WorkflowEdit: '编辑工作流', WorkflowMarketplace: '工作流市场',
  }
  return names[route.name] || route.name
})

function isConsoleNavActive(item) {
  return item.match?.includes(route.name) || route.path === item.path
}

const toggleDarkMode = () => {
  isDarkMode.value = !isDarkMode.value
  localStorage.setItem('darkMode', isDarkMode.value)
  if (isDarkMode.value) document.documentElement.classList.add('dark')
  else document.documentElement.classList.remove('dark')
}

onMounted(() => {
  if (isDarkMode.value) document.documentElement.classList.add('dark')
  // 初始化空间系统
  const spaceStore = useSpaceStore()
  spaceStore.initSpaces()
})

watch(() => route.fullPath, () => {
  mobileSidebarOpen.value = false
})
</script>

<style scoped>
.console-header {
  border-bottom: 1px solid rgba(228, 231, 237, 0.86);
}

.console-nav {
  border: 1px solid rgba(228, 231, 237, 0.82);
  border-radius: 12px;
  background: rgba(245, 247, 250, 0.74);
  padding: 4px;
}

.console-nav-link {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  border-radius: 9px;
  color: #606266;
  font-size: 13px;
  font-weight: 600;
  line-height: 1;
  padding: 8px 10px;
  transition: background-color 160ms ease, color 160ms ease, box-shadow 160ms ease;
}

.console-nav-link:hover {
  background: rgba(255, 255, 255, 0.88);
  color: #165dff;
}

.console-nav-link.is-active {
  background: #fff;
  color: #165dff;
  box-shadow: 0 8px 18px rgba(22, 93, 255, 0.1);
}
</style>
