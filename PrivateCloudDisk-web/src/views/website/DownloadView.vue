<template>
  <div>
    <!-- ============================================================
         Hero 区域 — 展示平台概览和快捷下载入口
         ============================================================ -->
    <section class="download-hero">
      <!-- 装饰性客户端设备展示 -->
      <div class="client-stage" aria-hidden="true">
        <div class="client-window desktop-window">
          <div class="window-topbar">
            <span class="dot bg-danger"></span>
            <span class="dot bg-warning"></span>
            <span class="dot bg-success"></span>
            <span class="ml-auto text-[10px] font-semibold text-neutral-400">CloudDrive Desktop</span>
          </div>
          <div class="desktop-body">
            <aside class="desktop-sidebar">
              <span v-for="item in ['我的文件', '团队空间', '共享链接', '同步任务']" :key="item">{{ item }}</span>
            </aside>
            <div class="desktop-content">
              <div class="desktop-toolbar">
                <span></span><span></span><span></span>
              </div>
              <div class="desktop-grid">
                <div v-for="n in 8" :key="n" class="mock-file">
                  <i :class="n % 3 === 0 ? 'fa fa-file-text-o text-secondary' : 'fa fa-folder text-primary'"></i>
                  <span></span>
                </div>
              </div>
            </div>
          </div>
        </div>
        <div class="client-window tablet-window">
          <div class="window-topbar compact">
            <span class="dot bg-danger"></span>
            <span class="dot bg-warning"></span>
            <span class="dot bg-success"></span>
          </div>
          <div class="tablet-body">
            <div v-for="n in 6" :key="n" class="tablet-card">
              <i class="fa fa-folder text-primary"></i>
              <span></span>
            </div>
          </div>
        </div>
        <div class="phone-frame">
          <div class="phone-speaker"></div>
          <div class="phone-screen">
            <div class="phone-header"></div>
            <div v-for="n in 5" :key="n" class="phone-row">
              <i :class="n % 2 === 0 ? 'fa fa-file-o text-secondary' : 'fa fa-folder text-primary'"></i>
              <span></span>
            </div>
          </div>
        </div>
      </div>

      <div class="hero-overlay"></div>
      <div class="relative z-10 mx-auto max-w-7xl px-4 py-20 sm:px-6 sm:py-28 lg:px-8">
        <div class="max-w-3xl">
          <span class="inline-flex items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-primary shadow-sm ring-1 ring-primary/10 backdrop-blur">客户端下载</span>
          <h1 class="mt-5 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">
            全平台客户端
          </h1>
          <p class="mt-4 max-w-2xl text-lg leading-8 text-neutral-500">选择适合您设备的客户端，开始体验 CloudDrive。Windows、macOS、Linux、iOS 与 Android 保持一致的企业级同步、预览和加密传输体验。</p>
          <div class="mt-8 flex flex-col gap-3 sm:flex-row">
            <a href="#desktop-clients" class="inline-flex items-center justify-center gap-2 rounded-xl bg-primary px-5 py-3 text-sm font-semibold text-white shadow-lg shadow-primary/20 transition hover:bg-primary/90">
              <i class="fa fa-desktop"></i> 下载桌面端
            </a>
            <a v-if="recommendedClient" :href="recommendedClient.downloadPath" @click.prevent="handleDownload(recommendedClient)" class="inline-flex items-center justify-center gap-2 rounded-xl border-2 border-primary/30 bg-white/80 px-5 py-3 text-sm font-semibold text-primary backdrop-blur transition hover:bg-primary/5">
              <i class="fa fa-bolt"></i> 为您的设备推荐 {{ recommendedClient.displayName }}
            </a>
          </div>
          <div class="mt-10 grid max-w-2xl grid-cols-3 gap-3">
            <div v-for="item in heroStats" :key="item.label" class="rounded-xl border border-white/70 bg-white/70 p-3 backdrop-blur">
              <p class="text-base font-bold text-neutral-800">{{ item.value }}</p>
              <p class="mt-1 text-xs text-neutral-400">{{ item.label }}</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         桌面客户端
         ============================================================ -->
    <section id="desktop-clients" class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">桌面客户端</h2>
        <p class="mt-2 text-center text-sm text-neutral-400">适用于 Windows、macOS 和 Linux 操作系统</p>

        <!-- 加载状态 -->
        <div v-if="loading" class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="n in 3" :key="n" class="animate-pulse rounded-2xl border border-neutral-200 p-8">
            <div class="flex items-center gap-4">
              <div class="h-14 w-14 rounded-xl bg-neutral-100"></div>
              <div class="space-y-2">
                <div class="h-5 w-24 rounded bg-neutral-100"></div>
                <div class="h-3 w-16 rounded bg-neutral-100"></div>
              </div>
            </div>
            <div class="mt-4 space-y-2">
              <div class="h-3 w-full rounded bg-neutral-100"></div>
              <div class="h-3 w-3/4 rounded bg-neutral-100"></div>
            </div>
            <div class="mt-5 h-10 w-full rounded-xl bg-neutral-100"></div>
          </div>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="error" class="mt-10 text-center">
          <p class="text-neutral-500">{{ error }}</p>
          <button @click="loadManifest" class="mt-4 text-sm text-primary hover:underline">重新加载</button>
        </div>

        <!-- 正常内容 -->
        <div v-else class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div
            v-for="client in desktopClients"
            :key="client.platform"
            class="group rounded-2xl border border-neutral-200 p-8 transition-all hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-1"
            :class="{ 'ring-2 ring-primary/30 border-primary/40': client.isRecommended }"
          >
            <!-- 推荐标签 -->
            <div v-if="client.isRecommended" class="mb-3 inline-flex items-center gap-1 rounded-full bg-primary/10 px-2.5 py-0.5 text-[11px] font-semibold text-primary">
              <i class="fa fa-star text-[10px]"></i> 推荐
            </div>

            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl" :class="client.bgClass">
                <i :class="[client.iconClass, 'text-2xl', client.iconColorClass]"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">{{ client.displayName }}</h3>
                <p class="text-xs text-neutral-400">版本 {{ client.version }}</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">{{ client.description }}</p>
            <div class="mt-5 space-y-2">
              <p class="text-xs text-neutral-400"><i class="fa fa-calendar mr-1"></i> 更新于 {{ formatDate(client.releaseDate) }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-hdd-o mr-1"></i> {{ client.fileSizeFormatted }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-check-circle text-success mr-1"></i> {{ client.requirement }}</p>
              <p class="text-xs text-neutral-400" :title="client.sha256">
                <i class="fa fa-shield mr-1"></i> SHA256: {{ client.sha256 ? client.sha256.substring(0, 16) + '...' : 'N/A' }}
              </p>
            </div>
            <div class="mt-5 flex flex-col gap-2">
              <a
                :href="client.downloadPath"
                @click.prevent="handleDownload(client)"
                class="flex items-center justify-center gap-2 rounded-xl bg-primary py-2.5 text-sm font-semibold text-white hover:bg-primary/90 transition"
              >
                <i class="fa fa-download"></i> 立即下载
              </a>
              <a v-if="getAltDownload(client)" :href="getAltDownload(client)" class="text-center text-xs text-neutral-400 hover:text-primary transition">其他版本下载</a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         移动客户端
         ============================================================ -->
    <section id="mobile-clients" class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">移动客户端</h2>
        <p class="mt-2 text-center text-sm text-neutral-400">随时随地访问您的文件，支持 iOS 和 Android</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div v-for="client in mobileClients" :key="client.platform" class="rounded-2xl border border-neutral-200 bg-white p-8 transition-all hover:border-primary/30 hover:shadow-lg hover:-translate-y-1">
            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl" :class="client.bgClass">
                <i :class="[client.iconClass, 'text-2xl', client.iconColorClass]"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">{{ client.displayName }}</h3>
                <p class="text-xs text-neutral-400">版本 {{ client.version }}</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">{{ client.description }}</p>
            <div class="mt-5 space-y-2">
              <p class="text-xs text-neutral-400"><i class="fa fa-calendar mr-1"></i> 更新于 {{ formatDate(client.releaseDate) }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-hdd-o mr-1"></i> {{ client.fileSizeFormatted }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-check-circle text-success mr-1"></i> {{ client.requirement }}</p>
            </div>
            <div class="mt-5 flex flex-col gap-2">
              <a :href="client.downloadPath" target="_blank" rel="noopener" @click="handleDownload(client)" class="flex items-center justify-center gap-2 rounded-xl bg-neutral-900 py-2.5 text-sm font-semibold text-white hover:bg-neutral-800 transition">
                <i :class="client.platform === 'ios' ? 'fa fa-apple' : 'fa fa-google'"></i>
                {{ client.platform === 'ios' ? 'App Store 下载' : 'Google Play 下载' }}
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         CLI & SDK 工具
         ============================================================ -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">命令行工具 & SDK</h2>
        <p class="mt-2 text-center text-sm text-neutral-400">面向开发者和运维人员的高级工具</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div class="rounded-2xl border border-neutral-200 p-8 transition-all hover:border-primary/30 hover:shadow-lg">
            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl bg-neutral-900">
                <i class="fa fa-terminal text-2xl text-success"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">CLI 命令行工具</h3>
                <p class="text-xs text-neutral-400">版本 v2.1.0</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">通过命令行管理文件、同步目录、自动化操作。支持 Bash/Zsh 自动补全。</p>
            <div class="mt-4 rounded-xl bg-neutral-900 p-4 font-mono text-xs text-green-400">
              <p class="text-neutral-500"># macOS / Linux</p>
              <p>brew install clouddrive/tap/cli</p>
              <p class="mt-2 text-neutral-500"># npm</p>
              <p>npm install -g @clouddrive/cli</p>
              <p class="mt-2 text-neutral-500"># 使用示例</p>
              <p>clouddrive upload ./report.pdf</p>
              <p>clouddrive sync ~/Documents /clouddrive/Documents</p>
            </div>
            <a href="#" class="mt-4 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline">CLI 文档 <i class="fa fa-arrow-right text-xs"></i></a>
          </div>

          <div class="rounded-2xl border border-neutral-200 p-8 transition-all hover:border-primary/30 hover:shadow-lg">
            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl bg-primary/10">
                <i class="fa fa-code text-2xl text-primary"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">SDK 开发工具包</h3>
                <p class="text-xs text-neutral-400">多语言支持</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">集成 CloudDrive API 到您的应用中。支持 Python、JavaScript/TypeScript、Java、Go 等主流语言。</p>
            <div class="mt-4 space-y-2">
              <div v-for="sdk in sdks" :key="sdk.lang" class="flex items-center justify-between rounded-lg border border-neutral-100 px-4 py-2.5 text-sm">
                <div class="flex items-center gap-2">
                  <i :class="[sdk.icon, 'text-neutral-500']"></i>
                  <span class="font-medium text-neutral-700">{{ sdk.lang }}</span>
                </div>
                <a :href="sdk.url" class="text-xs text-primary hover:underline">文档 <i class="fa fa-external-link"></i></a>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         版本历史
         ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">版本历史</h2>
        <div class="mt-10 space-y-4">
          <div v-for="v in versions" :key="v.version" class="flex items-start gap-4 rounded-xl border border-neutral-200 bg-white p-5">
            <span class="rounded-lg bg-primary/10 px-3 py-1 text-xs font-bold text-primary shrink-0">{{ v.version }}</span>
            <div>
              <div class="flex items-center gap-2 text-sm">
                <span class="font-semibold text-neutral-700">{{ v.title }}</span>
                <span class="text-neutral-400">{{ v.date }}</span>
              </div>
              <ul class="mt-2 space-y-1">
                <li v-for="change in v.changes" :key="change" class="text-xs text-neutral-500 flex items-start gap-2">
                  <span class="mt-0.5 h-1.5 w-1.5 rounded-full bg-neutral-300 shrink-0"></span>
                  {{ change }}
                </li>
              </ul>
            </div>
          </div>
        </div>
        <p class="mt-6 text-center text-sm text-neutral-400">
          <a href="#" class="text-primary hover:underline">查看完整更新日志 <i class="fa fa-arrow-right text-xs"></i></a>
        </p>
      </div>
    </section>

    <!-- ============================================================
         系统要求
         ============================================================ -->
    <section class="py-16 sm:py-20 border-t border-neutral-100 bg-neutral-50/50">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">系统要求</h2>
        <div class="mt-10 overflow-hidden rounded-2xl border border-neutral-200">
          <table class="w-full text-sm">
            <thead>
              <tr class="bg-neutral-50">
                <th class="px-4 py-3 text-left text-xs font-semibold text-neutral-500 uppercase tracking-wider">组件</th>
                <th class="px-4 py-3 text-left text-xs font-semibold text-neutral-500 uppercase tracking-wider">桌面端</th>
                <th class="px-4 py-3 text-left text-xs font-semibold text-neutral-500 uppercase tracking-wider">移动端</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="req in sysRequirements" :key="req.label" class="border-t border-neutral-100">
                <td class="px-4 py-3 text-xs font-medium text-neutral-600">{{ req.label }}</td>
                <td class="px-4 py-3 text-xs text-neutral-500">{{ req.desktop }}</td>
                <td class="px-4 py-3 text-xs text-neutral-500">{{ req.mobile }}</td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- ============================================================
         企业批量部署
         ============================================================ -->
    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary/5 to-info/5 border border-primary/10 p-8 sm:p-10">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-6">
            <div>
              <h2 class="text-2xl font-bold text-neutral-900">企业批量部署</h2>
              <p class="mt-2 text-sm text-neutral-500">支持 MSI/AD 组策略/移动设备管理 (MDM) 等多种企业级部署方式</p>
            </div>
            <div class="flex gap-3">
              <button class="rounded-xl bg-primary px-6 py-2.5 text-sm font-semibold text-white hover:bg-primary/90">下载部署包</button>
              <router-link to="/docs" class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">部署文档</router-link>
            </div>
          </div>
          <div class="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-windows text-2xl text-primary mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">MSI 安装包</p>
              <p class="text-xs text-neutral-400 mt-1">AD 组策略静默推送</p>
            </div>
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-apple text-2xl text-neutral-700 mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">MDM 管理</p>
              <p class="text-xs text-neutral-400 mt-1">Jamf / Intune 管理</p>
            </div>
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-docker text-2xl text-info mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">Docker 镜像</p>
              <p class="text-xs text-neutral-400 mt-1">容器化部署方案</p>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// ============================================================
// DownloadView.vue — 企业级客户端下载页面
// ============================================================
// 动态从版本清单 JSON 获取各平台客户端信息，自动检测用户平台
// 推荐最合适的下载，并记录下载事件用于统计分析。
// ============================================================

import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchVersionManifest,
  detectCurrentPlatform,
  recordDownloadEvent,
  type ClientDownload,
  type VersionManifest,
} from '@/api/modules/clientDownloads'

const router = useRouter()

// ============================================================
// 响应式状态
// ============================================================

/** 版本清单数据 */
const manifest = ref<VersionManifest | null>(null)
/** 加载状态 */
const loading = ref(true)
/** 错误信息 */
const error = ref<string | null>(null)

// ============================================================
// 计算属性
// ============================================================

/** 桌面客户端列表 */
const desktopClients = computed<ClientDownload[]>(() => {
  if (!manifest.value) return []
  return manifest.value.desktopClients
})

/** 移动客户端列表 */
const mobileClients = computed<ClientDownload[]>(() => {
  if (!manifest.value) return []
  return manifest.value.mobileClients
})

/** 当前平台推荐的客户端 */
const recommendedClient = computed<ClientDownload | null>(() => {
  if (!manifest.value) return null
  const currentPlatform = detectCurrentPlatform()
  // 找到恰好匹配当前平台的客户端
  return manifest.value.desktopClients.find(
    (c) => c.platform === currentPlatform,
  ) || null
})

// ============================================================
// 生命周期
// ============================================================

onMounted(() => {
  loadManifest()
})

// ============================================================
// 方法
// ============================================================

/**
 * 加载版本清单
 *
 * 从 /downloads/version-manifest.json 获取数据，
 * 并根据当前平台标记推荐下载项。
 */
async function loadManifest(): Promise<void> {
  loading.value = true
  error.value = null
  try {
    const data = await fetchVersionManifest()
    // 标记推荐客户端
    const currentPlatform = detectCurrentPlatform()
    data.desktopClients.forEach((client) => {
      client.isRecommended = client.platform === currentPlatform
    })
    manifest.value = data
  } catch (e) {
    error.value = '无法加载版本信息，请刷新页面重试。'
    console.error('[DownloadView] 加载版本清单失败:', e)
  } finally {
    loading.value = false
  }
}

/**
 * 处理下载点击
 *
 * 记录下载事件用于统计，如果是外部链接则在新窗口打开。
 *
 * @param client - 客户端下载信息
 */
function handleDownload(client: ClientDownload): void {
  // 记录下载事件（fire-and-forget）
  recordDownloadEvent(client.platform, client.version)

  // 对于移动端（App Store / Google Play 链接），新窗口打开
  if (client.platform === 'ios' || client.platform === 'android') {
    window.open(client.downloadPath, '_blank', 'noopener')
    return
  }

  // 桌面端：跳转到下载感谢页，由感谢页的 JS 脚本触发浏览器下载
  router.push({
    name: 'DownloadThanks',
    query: {
      platform: client.platform,
      version: client.version,
      displayName: client.displayName,
      downloadPath: client.downloadPath,
      extension: client.extension,
    },
  })
}

/**
 * 格式化日期
 *
 * @param dateStr - ISO 日期字符串
 * @returns 格式化的日期，如 "2026-01-12"
 */
function formatDate(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  if (isNaN(d.getTime())) return dateStr
  return d.toISOString().split('T')[0]
}

/**
 * 获取同平台的其他版本链接
 *
 * 例如 macOS ARM64 用户可以查看 Intel 版本。
 *
 * @param client - 当前客户端
 * @returns 其他版本下载链接或空字符串
 */
function getAltDownload(client: ClientDownload): string {
  if (client.platform === 'macos-arm64') {
    const intel = desktopClients.value.find(c => c.platform === 'macos-x64')
    return intel?.downloadPath || ''
  }
  if (client.platform === 'macos-x64') {
    const arm = desktopClients.value.find(c => c.platform === 'macos-arm64')
    return arm?.downloadPath || ''
  }
  if (client.platform === 'windows-x64') {
    const arm = desktopClients.value.find(c => c.platform === 'windows-arm64')
    return arm?.downloadPath || ''
  }
  return ''
}

// ============================================================
// 静态数据
// ============================================================

const heroStats = [
  { label: '桌面与移动系统', value: '5 平台' },
  { label: '断点续传与同步', value: '实时' },
  { label: '传输链路加密', value: 'E2E' },
]

const sdks = [
  { lang: 'Python', icon: 'fa fa-code', url: '#' },
  { lang: 'JavaScript / TypeScript', icon: 'fa fa-code', url: '#' },
  { lang: 'Java', icon: 'fa fa-code', url: '#' },
  { lang: 'Go', icon: 'fa fa-code', url: '#' },
  { lang: 'REST API', icon: 'fa fa-plug', url: '#' },
]

const versions = [
  { version: 'v3.2.0', title: '新增 AI 智能搜索与文件标签', date: '2026-01-12', changes: ['新增 AI 文档内容智能搜索', '新增文件自定义标签功能', '优化大文件上传速度，提升 40%', '修复 macOS 深色模式兼容性问题'] },
  { version: 'v3.1.0', title: '团队协作功能增强', date: '2025-12-20', changes: ['新增在线协同编辑功能', '新增文件评论与批注', '优化共享链接安全性', '新增文件锁定功能'] },
  { version: 'v3.0.0', title: '全新架构升级', date: '2025-11-15', changes: ['重构存储引擎，性能提升 300%', '全面升级 UI 界面', '新增命令行工具', '开放 API 接口'] },
]

const sysRequirements = [
  { label: '处理器', desktop: 'Intel Core i3 或同等', mobile: 'ARM64 或同等' },
  { label: '内存', desktop: '4 GB RAM 以上', mobile: '2 GB RAM 以上' },
  { label: '存储空间', desktop: '200 MB 可用空间', mobile: '100 MB 可用空间' },
  { label: '网络', desktop: '宽带连接', mobile: 'Wi-Fi 或蜂窝数据' },
  { label: '操作系统', desktop: 'Windows 10+ / macOS 11+ / Linux 内核 5.4+', mobile: 'iOS 15+ / Android 8.0+' },
]
</script>

<style scoped>
/* ============================================================
   Hero 区域背景和装饰
   ============================================================ */
.download-hero {
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid rgba(228, 231, 237, 0.85);
  background:
    radial-gradient(circle at 78% 22%, rgba(54, 207, 201, 0.18), transparent 30%),
    radial-gradient(circle at 20% 18%, rgba(22, 93, 255, 0.13), transparent 28%),
    linear-gradient(135deg, #f8fbff 0%, #ffffff 46%, #eef7ff 100%);
}

.client-stage {
  pointer-events: none;
  position: absolute;
  inset: 0;
  z-index: 0;
  opacity: 1;
}

.hero-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  background:
    linear-gradient(90deg, rgba(255, 255, 255, 0.96) 0%, rgba(255, 255, 255, 0.86) 34%, rgba(255, 255, 255, 0.28) 68%, rgba(255, 255, 255, 0.1) 100%),
    linear-gradient(180deg, transparent 0%, rgba(255, 255, 255, 0.32) 100%);
}

/* ============================================================
   装饰性客户端设备展示
   ============================================================ */
.client-window,
.phone-frame {
  position: absolute;
  border: 1px solid rgba(22, 93, 255, 0.1);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 30px 80px rgba(31, 41, 55, 0.18);
  backdrop-filter: blur(16px);
}

.desktop-window {
  right: max(24px, calc((100vw - 1180px) / 2));
  bottom: -38px;
  width: min(680px, 52vw);
  height: 390px;
  overflow: hidden;
  border-radius: 18px;
  transform: rotate(-1.4deg);
}

.tablet-window {
  right: min(54vw, 560px);
  bottom: 38px;
  width: 340px;
  height: 235px;
  overflow: hidden;
  border-radius: 24px;
  transform: rotate(4deg);
}

.phone-frame {
  right: max(18px, calc((100vw - 1180px) / 2 + 34px));
  bottom: 64px;
  width: 144px;
  height: 278px;
  border-radius: 32px;
  padding: 12px 10px;
  transform: rotate(7deg);
}

.window-topbar {
  display: flex;
  height: 38px;
  align-items: center;
  gap: 7px;
  border-bottom: 1px solid rgba(228, 231, 237, 0.85);
  background: rgba(255, 255, 255, 0.72);
  padding: 0 14px;
}

.window-topbar.compact {
  height: 30px;
}

.dot {
  height: 10px;
  width: 10px;
  border-radius: 999px;
}

.desktop-body {
  display: grid;
  height: calc(100% - 38px);
  grid-template-columns: 154px minmax(0, 1fr);
}

.desktop-sidebar {
  display: flex;
  flex-direction: column;
  gap: 10px;
  border-right: 1px solid rgba(228, 231, 237, 0.72);
  background: rgba(245, 247, 250, 0.7);
  padding: 18px 14px;
}

.desktop-sidebar span {
  border-radius: 10px;
  color: #606266;
  font-size: 12px;
  font-weight: 600;
  padding: 8px 10px;
}

.desktop-sidebar span:first-child {
  background: rgba(22, 93, 255, 0.1);
  color: #165dff;
}

.desktop-content {
  padding: 18px;
}

.desktop-toolbar {
  display: flex;
  gap: 10px;
  margin-bottom: 22px;
}

.desktop-toolbar span {
  height: 28px;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.08);
}

.desktop-toolbar span:nth-child(1) { width: 108px; }
.desktop-toolbar span:nth-child(2) { width: 78px; }
.desktop-toolbar span:nth-child(3) { width: 58px; }

.desktop-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.mock-file {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  border-radius: 10px;
  background: rgba(245, 247, 250, 0.7);
  padding: 16px 8px;
}

.mock-file i {
  font-size: 22px;
}

.mock-file span {
  height: 6px;
  width: 48px;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.08);
}

.tablet-body {
  display: grid;
  height: calc(100% - 30px);
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 16px;
}

.tablet-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  border-radius: 14px;
  background: rgba(245, 247, 250, 0.7);
  padding: 14px 0;
}

.tablet-card i {
  font-size: 20px;
}

.tablet-card span {
  height: 5px;
  width: 42px;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.08);
}

.phone-speaker {
  height: 4px;
  width: 48px;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.12);
  margin: 0 auto 8px;
}

.phone-screen {
  height: calc(100% - 12px);
  border-radius: 24px;
  background: rgba(250, 251, 254, 0.9);
  padding: 14px 12px;
}

.phone-header {
  height: 22px;
  border-radius: 8px;
  background: rgba(22, 93, 255, 0.08);
  margin-bottom: 14px;
}

.phone-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.phone-row i {
  font-size: 14px;
}

.phone-row span {
  height: 5px;
  flex: 1;
  border-radius: 999px;
  background: rgba(22, 93, 255, 0.06);
}

/* ============================================================
   响应式 — 移动端隐藏装饰性设备
   ============================================================ */
@media (max-width: 768px) {
  .tablet-window,
  .phone-frame {
    display: none;
  }
  .desktop-window {
    right: -40px;
    bottom: -60px;
    width: 280px;
    height: 200px;
  }
}
</style>