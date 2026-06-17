<template>
  <header class="fixed top-0 left-0 right-0 z-50 transition-all duration-300" :class="scrolled ? 'bg-white/95 backdrop-blur-md shadow-sm border-b border-neutral-100' : 'bg-transparent'">
    <div class="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:h-18 sm:px-6 lg:px-8">
      <!-- Logo -->
      <router-link to="/" class="flex items-center gap-2.5 shrink-0">
        <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-info">
          <i class="fa fa-cloud text-lg text-white"></i>
        </div>
        <span class="text-lg font-bold text-neutral-800 tracking-tight">CloudDrive</span>
      </router-link>

      <!-- Desktop Nav with Mega Menu -->
      <nav class="hidden items-center lg:flex">
        <template v-for="item in navItems" :key="item.label">
          <!-- Simple link -->
          <router-link
            v-if="!item.children"
            :to="item.path"
            class="relative rounded-lg px-3 py-2 text-sm font-medium transition-colors"
            :class="isActive(item.path) ? 'text-primary bg-primary/5' : 'text-neutral-600 hover:text-primary hover:bg-neutral-50'"
          >
            {{ item.label }}
            <i v-if="item.badge" class="fa fa-circle ml-1 text-[6px] text-danger align-middle"></i>
          </router-link>

          <!-- Mega Menu trigger -->
          <div
            v-else
            class="relative"
            @mouseenter="openMega = item.label"
            @mouseleave="openMega = null"
          >
            <button
              class="flex items-center gap-1 rounded-lg px-3 py-2 text-sm font-medium transition-colors"
              :class="openMega === item.label ? 'text-primary bg-primary/5' : 'text-neutral-600 hover:text-primary hover:bg-neutral-50'"
            >
              {{ item.label }}
              <i class="fa fa-angle-down text-[10px] transition-transform" :class="openMega === item.label ? 'rotate-180' : ''"></i>
            </button>

            <!-- Mega Menu Dropdown -->
            <Transition name="mega-fade">
              <div v-if="openMega === item.label" class="absolute left-0 top-full pt-2">
                <div class="rounded-2xl border border-neutral-200 bg-white shadow-2xl shadow-neutral-900/10 p-6 min-w-[600px]">
                  <div class="grid grid-cols-2 gap-6">
                    <div v-for="col in item.children" :key="col.title">
                      <p class="mb-2 text-xs font-semibold uppercase tracking-wider text-neutral-400">{{ col.title }}</p>
                      <div class="space-y-1">
                        <router-link
                          v-for="child in col.items"
                          :key="child.path"
                          :to="child.path"
                          class="flex items-center gap-2 rounded-lg px-3 py-2 text-sm text-neutral-600 hover:bg-neutral-50 hover:text-primary transition group"
                        >
                          <i v-if="child.icon" :class="[child.icon, 'text-xs w-4 text-center text-neutral-400 group-hover:text-primary transition']"></i>
                          <span>{{ child.label }}</span>
                          <i v-if="child.badge" class="fa fa-circle text-[6px] text-danger ml-auto"></i>
                        </router-link>
                      </div>
                    </div>
                  </div>
                  <!-- Dropdown footer -->
                  <div v-if="item.footer" class="mt-4 rounded-xl bg-neutral-50 p-3 flex items-center justify-between">
                    <span class="text-xs text-neutral-500">{{ item.footer.text }}</span>
                    <router-link :to="item.footer.path" class="text-xs font-medium text-primary hover:underline">{{ item.footer.label }} <i class="fa fa-arrow-right text-[10px]"></i></router-link>
                  </div>
                </div>
              </div>
            </Transition>
          </div>
        </template>
      </nav>

      <!-- Right Actions -->
      <div class="flex items-center gap-2">
        <!-- Search button -->
        <button @click="searchOpen = true" class="hidden sm:flex items-center gap-2 rounded-lg border border-neutral-200/60 px-3 py-1.5 text-xs text-neutral-400 hover:border-primary/30 hover:text-primary transition">
          <i class="fa fa-search text-[11px]"></i>
          <span class="hidden lg:inline">搜索文档...</span>
          <kbd class="hidden lg:inline-flex items-center gap-0.5 rounded bg-neutral-100 px-1.5 py-0.5 text-[10px] font-mono text-neutral-400">⌘K</kbd>
        </button>

        <!-- 已登录：用户信息 + 进入控制面板 -->
        <template v-if="auth.isLoggedIn">
          <router-link to="/app" class="hidden items-center gap-1.5 rounded-lg border border-primary/20 bg-primary/5 px-4 py-2 text-sm font-medium text-primary transition hover:bg-primary/10 hover:border-primary/30 sm:inline-flex">
            <i class="fa fa-th-large text-xs"></i>
            进入控制面板
          </router-link>
          <!-- 用户下拉 -->
          <div class="relative" @mouseenter="userMenuOpen = true" @mouseleave="userMenuOpen = false">
            <button class="flex items-center gap-2 rounded-lg px-2 py-1.5 transition hover:bg-neutral-100">
              <div v-if="auth.user.image_path" class="h-8 w-8 rounded-full overflow-hidden">
                <img :src="auth.user.image_path" alt="avatar" class="h-full w-full object-cover" />
              </div>
              <div v-else class="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
                {{ auth.userInitial }}
              </div>
              <span class="hidden text-sm font-medium text-neutral-700 lg:inline">{{ auth.displayName }}</span>
              <i class="fa fa-angle-down text-[10px] text-neutral-400 hidden sm:inline"></i>
            </button>
            <Transition name="user-drop">
              <div v-if="userMenuOpen" class="absolute right-0 top-full pt-1.5">
                <div class="rounded-xl border border-neutral-200 bg-white shadow-xl shadow-neutral-900/10 w-56 overflow-hidden">
                  <div class="border-b border-neutral-100 px-4 py-3">
                    <p class="text-sm font-semibold text-neutral-700">{{ auth.displayName }}</p>
                    <p class="text-xs text-neutral-400 mt-0.5">{{ auth.user.email || auth.user.phone_number || auth.user.account }}</p>
                  </div>
                  <div class="py-1">
                    <router-link to="/app" class="flex items-center gap-2.5 px-4 py-2.5 text-sm text-neutral-600 hover:bg-neutral-50 transition">
                      <i class="fa fa-th-large w-4 text-center text-neutral-400"></i> 控制面板
                    </router-link>
                    <router-link to="/app/profile" class="flex items-center gap-2.5 px-4 py-2.5 text-sm text-neutral-600 hover:bg-neutral-50 transition">
                      <i class="fa fa-user-circle w-4 text-center text-neutral-400"></i> 个人中心
                    </router-link>
                    <router-link to="/app/settings" class="flex items-center gap-2.5 px-4 py-2.5 text-sm text-neutral-600 hover:bg-neutral-50 transition">
                      <i class="fa fa-cog w-4 text-center text-neutral-400"></i> 账户设置
                    </router-link>
                  </div>
                  <div class="border-t border-neutral-100 py-1">
                    <button @click="handleLogout" class="flex w-full items-center gap-2.5 px-4 py-2.5 text-sm text-danger hover:bg-danger/5 transition">
                      <i class="fa fa-sign-out w-4 text-center"></i> 退出登录
                    </button>
                  </div>
                </div>
              </div>
            </Transition>
          </div>
        </template>

        <!-- 未登录：登录/注册按钮 -->
        <template v-else>
          <router-link to="/login" class="hidden rounded-lg px-4 py-2 text-sm font-medium text-neutral-600 transition hover:text-primary sm:inline-block">
            登录
          </router-link>
          <router-link to="/register" class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white shadow-sm shadow-primary/20 transition hover:bg-primary/90 hover:shadow-md hover:shadow-primary/25">
            免费注册
          </router-link>
        </template>

        <button @click="mobileOpen = !mobileOpen" class="icon-button lg:hidden ml-1">
          <i :class="mobileOpen ? 'fa fa-times' : 'fa fa-bars'"></i>
        </button>
      </div>
    </div>

    <!-- Search Overlay -->
    <Teleport to="body">
      <Transition name="search-fade">
        <div v-if="searchOpen" class="fixed inset-0 z-[60] flex items-start justify-center pt-[20vh]" @click.self="searchOpen = false">
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
          <div class="relative mx-4 w-full max-w-xl rounded-2xl bg-white shadow-2xl border border-neutral-200 overflow-hidden">
            <div class="flex items-center gap-3 border-b border-neutral-100 px-4 py-3">
              <i class="fa fa-search text-neutral-400"></i>
              <input
                ref="searchInputRef"
                v-model="searchQuery"
                @keydown.escape="searchOpen = false"
                placeholder="搜索文档、功能、API..."
                class="flex-1 border-none text-sm outline-none bg-transparent placeholder:text-neutral-300"
              />
              <kbd class="rounded bg-neutral-100 px-1.5 py-0.5 text-[11px] font-mono text-neutral-400">ESC</kbd>
            </div>
            <div class="max-h-80 overflow-y-auto p-2">
              <template v-if="filteredSearchResults.length">
                <a
                  v-for="result in filteredSearchResults"
                  :key="result.label"
                  :href="result.path"
                  @click.prevent="searchOpen = false; $router.push(result.path)"
                  class="flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm hover:bg-neutral-50 transition"
                >
                  <div class="flex h-8 w-8 items-center justify-center rounded-lg" :class="result.bgClass">
                    <i :class="[result.icon, 'text-xs', result.iconClass]"></i>
                  </div>
                  <div>
                    <p class="font-medium text-neutral-700">{{ result.label }}</p>
                    <p class="text-xs text-neutral-400">{{ result.desc }}</p>
                  </div>
                </a>
              </template>
              <p v-else class="p-4 text-center text-sm text-neutral-400">未找到相关结果</p>
            </div>
            <div class="border-t border-neutral-100 px-4 py-2 flex items-center gap-4 text-[11px] text-neutral-400">
              <span><kbd class="rounded bg-neutral-100 px-1 py-0.5 font-mono">↑↓</kbd> 导航</span>
              <span><kbd class="rounded bg-neutral-100 px-1 py-0.5 font-mono">↵</kbd> 选择</span>
              <span><kbd class="rounded bg-neutral-100 px-1 py-0.5 font-mono">ESC</kbd> 关闭</span>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- Mobile Menu -->
    <Teleport to="body">
      <Transition name="mobile-slide">
        <div v-if="mobileOpen" class="fixed inset-0 z-50 lg:hidden">
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm" @click="mobileOpen = false"></div>
          <div class="absolute top-0 right-0 bottom-0 w-full max-w-sm bg-white shadow-2xl overflow-y-auto" @click.stop>
            <div class="flex items-center justify-between px-4 py-4 border-b border-neutral-100">
              <router-link to="/" class="flex items-center gap-2" @click="mobileOpen = false">
                <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-info">
                  <i class="fa fa-cloud text-sm text-white"></i>
                </div>
                <span class="font-bold text-neutral-800">CloudDrive</span>
              </router-link>
              <button @click="mobileOpen = false" class="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-neutral-100">
                <i class="fa fa-times text-neutral-500"></i>
              </button>
            </div>
            <div class="p-4 space-y-1">
              <template v-for="item in navItems" :key="item.label">
                <router-link
                  v-if="!item.children"
                  :to="item.path"
                  @click="mobileOpen = false"
                  class="flex items-center justify-between rounded-lg px-4 py-3 text-sm font-medium transition"
                  :class="isActive(item.path) ? 'bg-primary/5 text-primary' : 'text-neutral-600 hover:bg-neutral-50'"
                >
                  {{ item.label }}
                  <i v-if="item.badge" class="fa fa-circle text-[6px] text-danger"></i>
                </router-link>
                <div v-else>
                  <button
                    @click="expandedMobile = expandedMobile === item.label ? null : item.label"
                    class="flex w-full items-center justify-between rounded-lg px-4 py-3 text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition"
                  >
                    {{ item.label }}
                    <i class="fa fa-angle-down text-[10px] transition-transform" :class="expandedMobile === item.label ? 'rotate-180' : ''"></i>
                  </button>
                  <div v-if="expandedMobile === item.label" class="ml-4 border-l-2 border-neutral-100 pl-4 space-y-1 py-1">
                    <template v-for="col in item.children" :key="col.title">
                      <p class="px-4 py-1 text-[11px] font-semibold uppercase tracking-wider text-neutral-400">{{ col.title }}</p>
                      <router-link
                        v-for="child in col.items"
                        :key="child.path"
                        :to="child.path"
                        @click="mobileOpen = false"
                        class="flex items-center gap-2 rounded-lg px-4 py-2 text-sm text-neutral-500 hover:bg-neutral-50 hover:text-primary transition"
                      >
                        <i v-if="child.icon" :class="[child.icon, 'text-xs w-4 text-center text-neutral-400']"></i>
                        {{ child.label }}
                      </router-link>
                    </template>
                  </div>
                </div>
              </template>
              <hr class="my-3 border-neutral-100" />
              <!-- 移动端：已登录 -->
              <template v-if="auth.isLoggedIn">
                <div class="flex items-center gap-3 px-4 py-3 mb-1">
                  <div v-if="auth.user.image_path" class="h-10 w-10 rounded-full overflow-hidden shrink-0">
                    <img :src="auth.user.image_path" alt="avatar" class="h-full w-full object-cover" />
                  </div>
                  <div v-else class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary shrink-0">
                    {{ auth.userInitial }}
                  </div>
                  <div class="min-w-0">
                    <p class="text-sm font-semibold text-neutral-700 truncate">{{ auth.displayName }}</p>
                    <p class="text-xs text-neutral-400 truncate">{{ auth.user.email || auth.user.phone_number || auth.user.account }}</p>
                  </div>
                </div>
                <router-link to="/app" @click="mobileOpen = false" class="flex items-center rounded-lg px-4 py-3 text-sm font-medium text-primary hover:bg-primary/5 transition">
                  <i class="fa fa-th-large mr-2 w-4 text-center"></i> 进入控制面板
                </router-link>
                <router-link to="/app/profile" @click="mobileOpen = false" class="flex items-center rounded-lg px-4 py-3 text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition">
                  <i class="fa fa-user-circle mr-2 w-4 text-center"></i> 个人中心
                </router-link>
                <button @click="handleLogout; mobileOpen = false" class="flex w-full items-center rounded-lg px-4 py-3 text-sm font-medium text-danger hover:bg-danger/5 transition">
                  <i class="fa fa-sign-out mr-2 w-4 text-center"></i> 退出登录
                </button>
              </template>
              <!-- 移动端：未登录 -->
              <template v-else>
                <router-link to="/login" @click="mobileOpen = false" class="flex items-center rounded-lg px-4 py-3 text-sm font-medium text-neutral-600 hover:bg-neutral-50 transition">
                  <i class="fa fa-sign-in mr-2 w-4 text-center"></i> 登录
                </router-link>
                <router-link to="/register" @click="mobileOpen = false" class="flex items-center rounded-lg bg-primary px-4 py-3 text-sm font-medium text-white hover:bg-primary/90 transition">
                  <i class="fa fa-user-plus mr-2 w-4 text-center"></i> 免费注册
                </router-link>
              </template>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </header>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/authStore'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const scrolled = ref(false)
const mobileOpen = ref(false)
const openMega = ref(null)
const expandedMobile = ref(null)
const searchOpen = ref(false)
const searchQuery = ref('')
const searchInputRef = ref(null)
const userMenuOpen = ref(false)

function handleLogout() {
  auth.logout()
  userMenuOpen.value = false
  router.push('/')
}

const navItems = [
  {
    label: '产品',
    children: [
      {
        title: '核心功能',
        items: [
          { label: '功能特性', path: '/features', icon: 'fa fa-star', badge: true },
          { label: '客户端下载', path: '/download', icon: 'fa fa-download' },
          { label: '定价方案', path: '/pricing', icon: 'fa fa-tag' },
          { label: '安全中心', path: '/security-center', icon: 'fa fa-shield' },
        ],
      },
      {
        title: '解决方案',
        items: [
          { label: '行业解决方案', path: '/solutions', icon: 'fa fa-building' },
          { label: '客户案例', path: '/case-studies', icon: 'fa fa-trophy' },
          { label: '合作伙伴', path: '/partners', icon: 'fa fa-handshake-o' },
          { label: '系统状态', path: '/status', icon: 'fa fa-check-circle' },
        ],
      },
    ],
    footer: { text: '全新 v3.2 发布，性能提升 300%', label: '查看更新', path: '/changelog' },
  },
  { label: '定价', path: '/pricing' },
  { label: '下载', path: '/download', badge: true },
  {
    label: '资源',
    children: [
      {
        title: '学习资源',
        items: [
          { label: '文档中心', path: '/docs', icon: 'fa fa-book' },
          { label: '更新日志', path: '/changelog', icon: 'fa fa-refresh' },
          { label: '技术博客', path: '/blog', icon: 'fa fa-rss' },
          { label: '常见问题', path: '/docs', icon: 'fa fa-question-circle' },
        ],
      },
      {
        title: '关于我们',
        items: [
          { label: '公司介绍', path: '/about', icon: 'fa fa-info-circle' },
          { label: '加入我们', path: '/careers', icon: 'fa fa-briefcase' },
          { label: '联系我们', path: '/contact', icon: 'fa fa-envelope' },
          { label: '媒体报道', path: '/press', icon: 'fa fa-newspaper-o' },
        ],
      },
    ],
    footer: { text: '我们正在招聘！', label: '查看职位', path: '/careers' },
  },
  { label: '企业', path: '/solutions' },
  { label: '博客', path: '/blog' },
]

const searchResults = [
  { label: '快速入门指南', desc: '5 分钟上手 CloudDrive', path: '/docs', icon: 'fa fa-rocket', iconClass: 'text-primary', bgClass: 'bg-primary/10' },
  { label: '文件上传功能', desc: '支持拖拽、批量、分片上传', path: '/features', icon: 'fa fa-upload', iconClass: 'text-warning', bgClass: 'bg-warning/10' },
  { label: '客户端下载', desc: 'Windows / macOS / Linux / iOS / Android', path: '/download', icon: 'fa fa-download', iconClass: 'text-info', bgClass: 'bg-info/10' },
  { label: '定价方案', desc: '免费版到旗舰版，灵活选择', path: '/pricing', icon: 'fa fa-tag', iconClass: 'text-success', bgClass: 'bg-success/10' },
  { label: '安全与加密', desc: 'AES-256 端到端加密', path: '/security-center', icon: 'fa fa-shield', iconClass: 'text-danger', bgClass: 'bg-danger/10' },
  { label: 'API 参考文档', desc: 'REST API 完整参考', path: '/docs', icon: 'fa fa-code', iconClass: 'text-purple-500', bgClass: 'bg-purple-50' },
  { label: '团队协作', desc: '实时共享与协同编辑', path: '/features', icon: 'fa fa-users', iconClass: 'text-success', bgClass: 'bg-success/10' },
  { label: '联系我们', desc: '售前咨询与技术支持', path: '/contact', icon: 'fa fa-envelope', iconClass: 'text-primary', bgClass: 'bg-primary/10' },
]

const filteredSearchResults = computed(() => {
  if (!searchQuery.value.trim()) return searchResults
  const q = searchQuery.value.toLowerCase()
  return searchResults.filter(r => r.label.toLowerCase().includes(q) || r.desc.toLowerCase().includes(q))
})

function isActive(path) {
  if (path === '/') return route.path === '/'
  return route.path.startsWith(path)
}

function onScroll() {
  scrolled.value = window.scrollY > 10
}

function onKeydown(e) {
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchOpen.value = !searchOpen.value
  }
}

watch(searchOpen, async (val) => {
  if (val) {
    await nextTick()
    searchInputRef.value?.focus()
  } else {
    searchQuery.value = ''
  }
})

onMounted(() => {
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('keydown', onKeydown)
  // 如果已登录则拉取用户信息供头部展示
  if (auth.isLoggedIn) auth.fetchUserInfo()
})
onUnmounted(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.mega-fade-enter-active,
.mega-fade-leave-active {
  transition: all 0.15s ease;
}
.mega-fade-enter-from,
.mega-fade-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}

.user-drop-enter-active,
.user-drop-leave-active {
  transition: all 0.15s ease;
}
.user-drop-enter-from,
.user-drop-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.search-fade-enter-active,
.search-fade-leave-active {
  transition: all 0.2s ease;
}
.search-fade-enter-from,
.search-fade-leave-to {
  opacity: 0;
}
.search-fade-enter-from > div:last-child,
.search-fade-leave-to > div:last-child {
  transform: scale(0.95) translateY(-10px);
}

.mobile-slide-enter-active,
.mobile-slide-leave-active {
  transition: all 0.25s ease;
}
.mobile-slide-enter-from,
.mobile-slide-leave-to {
  opacity: 0;
}
.mobile-slide-enter-from > div:nth-child(2),
.mobile-slide-leave-to > div:nth-child(2) {
  transform: translateX(100%);
}
</style>