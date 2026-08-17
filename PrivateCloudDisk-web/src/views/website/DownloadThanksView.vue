<template>
  <div class="download-thanks-page">
    <!-- ============================================================
         Hero — 感谢下载
         ============================================================ -->
    <section class="thanks-hero">
      <div class="thanks-bg">
        <div class="thanks-glow thanks-glow-1"></div>
        <div class="thanks-glow thanks-glow-2"></div>
      </div>
      <div class="thanks-hero-overlay"></div>
      <div class="relative z-10 mx-auto max-w-4xl px-4 py-24 sm:py-32 text-center">
        <!-- 成功图标 -->
        <div class="thanks-check-wrapper">
          <div class="thanks-check-circle">
            <svg class="thanks-check-svg" viewBox="0 0 52 52">
              <circle class="thanks-check-circle-bg" cx="26" cy="26" r="25" fill="none" />
              <circle class="thanks-check-circle-fg" cx="26" cy="26" r="25" fill="none" />
              <path class="thanks-check-mark" fill="none" d="M14 27l7 7 16-16" />
            </svg>
          </div>
        </div>

        <h1 class="mt-8 text-3xl font-extrabold tracking-tight text-neutral-900 sm:text-4xl lg:text-5xl">
          感谢下载 <span class="text-primary">PrivateCloudDisk</span>
        </h1>
        <p class="mt-4 text-lg text-neutral-500">
          您的 <strong>{{ clientDisplayName }}</strong> 客户端正在下载中
        </p>

        <!-- 重新下载提示 -->
        <p class="mt-4 text-sm text-neutral-400">
          没有收到下载？
          <button @click="triggerDownload" class="font-semibold text-primary hover:underline cursor-pointer bg-transparent border-0 p-0">
            点击重新下载
          </button>
          <span class="mx-1">·</span>
          <span class="text-neutral-400">版本 {{ clientVersion }}</span>
        </p>

        <!-- 下载进度提示 -->
        <div class="mt-6 flex justify-center">
          <div class="flex items-center gap-2 rounded-full bg-primary/5 border border-primary/10 px-4 py-2 text-sm text-primary">
            <span class="thanks-dot-pulse"></span>
            正在为您准备下载文件...
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         安装指南
         ============================================================ -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary">安装指南</span>
          <h2 class="mt-4 text-2xl font-bold text-neutral-900 sm:text-3xl">如何安装 {{ clientDisplayName }} 客户端</h2>
        </div>

        <div class="mt-10 grid grid-cols-1 gap-8 sm:grid-cols-2">
          <!-- 安装步骤 -->
          <div class="rounded-2xl border border-neutral-200 p-6 sm:p-8">
            <h3 class="flex items-center gap-2 text-lg font-semibold text-neutral-800">
              <i class="fa fa-list-ol text-primary"></i> 安装步骤
            </h3>
            <ol class="mt-4 space-y-4">
              <li v-for="(step, idx) in installSteps" :key="idx" class="flex gap-3">
                <span class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">{{ idx + 1 }}</span>
                <span class="text-sm text-neutral-600">{{ step }}</span>
              </li>
            </ol>
          </div>

          <!-- 系统要求 -->
          <div class="rounded-2xl border border-neutral-200 p-6 sm:p-8">
            <h3 class="flex items-center gap-2 text-lg font-semibold text-neutral-800">
              <i class="fa fa-laptop text-primary"></i> 系统要求
            </h3>
            <ul class="mt-4 space-y-3">
              <li v-for="req in sysReqs" :key="req.label" class="flex items-start gap-2 text-sm">
                <i class="fa fa-check-circle text-success mt-0.5 shrink-0"></i>
                <span class="text-neutral-600"><strong>{{ req.label }}：</strong>{{ req.value }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         功能亮点预览
         ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-semibold text-success">功能预览</span>
          <h2 class="mt-4 text-2xl font-bold text-neutral-900 sm:text-3xl">安装后您可以</h2>
        </div>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="item in featureHighlights" :key="item.title" class="group rounded-2xl border border-neutral-200 bg-white p-6 transition-all hover:border-primary/30 hover:shadow-lg hover:-translate-y-1">
            <div class="flex h-12 w-12 items-center justify-center rounded-xl" :class="item.bgClass">
              <i :class="[item.icon, 'text-xl', item.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ item.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500 leading-relaxed">{{ item.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         快速入门教程
         ============================================================ -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-semibold text-warning">快速入门</span>
          <h2 class="mt-4 text-2xl font-bold text-neutral-900 sm:text-3xl">5 分钟快速上手</h2>
        </div>
        <div class="mt-10 space-y-6">
          <div v-for="(tutorial, idx) in tutorials" :key="idx" class="flex gap-4 rounded-2xl border border-neutral-200 p-6 transition-all hover:border-primary/20">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary text-lg font-bold text-white">{{ idx + 1 }}</div>
            <div>
              <h3 class="text-base font-semibold text-neutral-800">{{ tutorial.title }}</h3>
              <p class="mt-1 text-sm text-neutral-500 leading-relaxed">{{ tutorial.desc }}</p>
              <div v-if="tutorial.tip" class="mt-2 rounded-lg bg-primary/5 border border-primary/10 px-3 py-1.5 text-xs text-primary">
                <i class="fa fa-lightbulb-o mr-1"></i> {{ tutorial.tip }}
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================
         其他平台
         ============================================================ -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-16 sm:py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8 text-center">
        <h2 class="text-xl font-bold text-neutral-900">也需要其他平台客户端？</h2>
        <p class="mt-2 text-sm text-neutral-500">仓库包含 Web、桌面端、移动端、原生客户端和 CLI 入口，具体能力以各子项目为准</p>
        <div class="mt-6 flex flex-wrap justify-center gap-3">
          <router-link
            v-for="p in allPlatforms"
            :key="p.platform"
            :to="`/download?platform=${p.platform}`"
            class="inline-flex items-center gap-2 rounded-xl border border-neutral-200 px-5 py-2.5 text-sm font-medium text-neutral-600 transition-all hover:border-primary hover:text-primary hover:bg-primary/5"
          >
            <i :class="[p.icon, 'text-base']"></i> {{ p.label }}
          </router-link>
        </div>
        <div class="mt-8">
          <router-link to="/download" class="text-sm font-semibold text-primary hover:underline">
            查看所有下载选项 <i class="fa fa-arrow-right text-xs"></i>
          </router-link>
        </div>
      </div>
    </section>

    <!-- ============================================================
         常见问题
         ============================================================ -->
    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-xl font-bold text-neutral-900 text-center">常见问题</h2>
        <div class="mt-8 space-y-3">
          <details v-for="faq in faqs" :key="faq.q" class="group rounded-xl border border-neutral-200 transition-all">
            <summary class="flex cursor-pointer items-center justify-between px-5 py-4 text-sm font-medium text-neutral-700">
              {{ faq.q }}
              <i class="fa fa-chevron-down text-neutral-400 transition-transform group-open:rotate-180"></i>
            </summary>
            <p class="border-t border-neutral-100 px-5 pb-4 pt-3 text-sm text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </details>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { recordDownloadEvent } from '@/api/modules/clientDownloads'

const route = useRoute()

// ============================================================
// 从 query 参数获取客户端信息
// ============================================================
const clientPlatform = computed(() => (route.query.platform as string) || 'windows-x64')
const clientVersion = computed(() => (route.query.version as string) || '3.2.0')
const clientDownloadPath = computed(() => (route.query.downloadPath as string) || '')
const clientDisplayName = computed(() => (route.query.displayName as string) || 'PrivateCloudDisk')
const clientExtension = computed(() => (route.query.extension as string) || 'exe')

// ============================================================
// 自动触发下载
// ============================================================
let downloadTriggered = false

function triggerDownload() {
  if (!clientDownloadPath.value) return

  // 记录下载事件
  recordDownloadEvent(clientPlatform.value as any, clientVersion.value)

  // 触发浏览器下载
  const link = document.createElement('a')
  link.href = clientDownloadPath.value
  link.download = `PrivateCloudDisk-${clientPlatform.value}-${clientVersion.value}.${clientExtension.value}`
  link.style.display = 'none'
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

onMounted(() => {
  // 自动触发下载（仅一次）
  if (!downloadTriggered && clientDownloadPath.value) {
    downloadTriggered = true
    // 延迟 600ms 确保页面渲染完成，让用户看到感谢信息
    setTimeout(() => {
      triggerDownload()
    }, 600)
  }
})

// ============================================================
// 安装步骤（根据平台动态生成）
// ============================================================
const platformInstallSteps: Record<string, string[]> = {
  'windows-x64': [
    '下载完成后，双击 <code>PrivateCloudDisk-Setup.exe</code> 安装程序',
    '按照安装向导提示，选择安装目录并点击"下一步"',
    '安装完成后，PrivateCloudDisk 将按客户端实现启动；是否常驻以客户端配置为准',
    '使用您的账户登录，即可开始同步文件',
  ],
  'windows-arm64': [
    '下载完成后，双击 <code>PrivateCloudDisk-Setup-ARM64.exe</code> 安装程序',
    '按照安装向导提示完成安装',
    '安装完成后，PrivateCloudDisk 将按客户端实现启动',
    '使用您的账户登录，即可开始同步文件',
  ],
  'macos-x64': [
    '下载完成后，打开 <code>PrivateCloudDisk.dmg</code> 磁盘映像',
    '将 PrivateCloudDisk 图标拖拽到 Applications 文件夹',
    '首次打开时，在"系统偏好设置 > 安全性与隐私"中允许运行',
    '使用您的账户登录，即可开始同步文件',
  ],
  'macos-arm64': [
    '下载完成后，打开 <code>PrivateCloudDisk-AppleSilicon.dmg</code> 磁盘映像',
    '将 PrivateCloudDisk 图标拖拽到 Applications 文件夹',
    '首次打开时，在"系统偏好设置 > 安全性与隐私"中允许运行',
    '使用您的账户登录，即可开始同步文件',
  ],
  'linux-x64': [
    '下载完成后，打开终端进入下载目录',
    '运行 <code>chmod +x PrivateCloudDisk-*.AppImage && ./PrivateCloudDisk-*.AppImage</code>',
    '或按当前发布包说明使用包管理器安装',
    '使用您的账户登录，即可开始同步文件',
  ],
  'linux-arm64': [
    '下载完成后，打开终端进入下载目录',
    '运行 <code>chmod +x PrivateCloudDisk-*-arm64.AppImage && ./PrivateCloudDisk-*-arm64.AppImage</code>',
    '或按当前发布包说明使用包管理器安装',
    '使用您的账户登录，即可开始同步文件',
  ],
  'linux-deb': [
    '下载完成后，打开终端进入下载目录',
    '按当前发布包说明完成安装并启动客户端',
    '使用您的账户登录，即可开始同步文件',
  ],
  'linux-rpm': [
    '下载完成后，打开终端进入下载目录',
    '按当前发布包说明完成安装并启动客户端',
    '使用您的账户登录，即可开始同步文件',
  ],
}

const installSteps = computed(() => {
  return platformInstallSteps[clientPlatform.value] || [
    '下载完成后，运行安装程序',
    '按照安装向导完成安装',
    '启动 PrivateCloudDisk 客户端',
    '使用您的账户登录，即可开始同步文件',
  ]
})

// ============================================================
// 系统要求（根据平台）
// ============================================================
const platformSysReqs: Record<string, { label: string; value: string }[]> = {
  'windows-x64': [
    { label: '操作系统', value: 'Windows 10 / 11 (64-bit)' },
    { label: '处理器', value: 'Intel Core i3 或同等' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
  'windows-arm64': [
    { label: '操作系统', value: 'Windows 10 / 11 (ARM64)' },
    { label: '处理器', value: 'Snapdragon 或同等 ARM 处理器' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
  'macos-x64': [
    { label: '操作系统', value: 'macOS 11 (Big Sur) 或更高版本' },
    { label: '处理器', value: 'Intel Core 处理器' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
  'macos-arm64': [
    { label: '操作系统', value: 'macOS 11 (Big Sur) 或更高版本' },
    { label: '处理器', value: 'Apple Silicon (M1/M2/M3/M4)' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
  'linux-x64': [
    { label: '操作系统', value: 'Linux 内核 5.4+ (Ubuntu 20.04+, Debian 11+, CentOS 8+)' },
    { label: '处理器', value: 'x86_64 处理器' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
  'linux-arm64': [
    { label: '操作系统', value: 'Linux 内核 5.4+ (ARM64 发行版)' },
    { label: '处理器', value: 'ARM64 处理器' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ],
}

const sysReqs = computed(() => {
  return platformSysReqs[clientPlatform.value] || [
    { label: '操作系统', value: 'Windows 10+ / macOS 11+ / Linux 内核 5.4+' },
    { label: '处理器', value: '现代处理器' },
    { label: '内存', value: '4 GB RAM 以上' },
    { label: '存储空间', value: '200 MB 可用空间' },
  ]
})

// ============================================================
// 静态数据
// ============================================================
const featureHighlights = [
  { title: '多端文件访问', desc: '通过对应客户端访问平台文件，具体同步策略以客户端实现和部署配置为准。', icon: 'fa fa-refresh', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '拖拽上传', desc: '直接将文件拖入客户端即可上传，体验与本地文件管理器一致。', icon: 'fa fa-upload', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '在线预览', desc: '支持 Office 文档、PDF、图片、视频、代码等多类文件在线预览，实际格式以服务配置为准。', icon: 'fa fa-eye', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '空间协作', desc: '在空间中组织成员、角色、文件夹和分享资源，具体协作能力以平台接口为准。', icon: 'fa fa-users', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '访问边界', desc: '通过空间、角色、分享资源和操作凭证控制文件访问范围。', icon: 'fa fa-lock', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '处理状态', desc: '上传、预览、下载和异步处理状态由服务接口协同记录。', icon: 'fa fa-history', bgClass: 'bg-purple-100', iconClass: 'text-purple-600' },
]

const tutorials = [
  { title: '登录您的平台账户', desc: '安装完成后，打开客户端并使用平台支持的账户认证方式登录；具体认证选项以实际部署为准。', tip: '客户端身份注册和扩展绑定由 client-registration-service 负责' },
  { title: '选择文件访问方式', desc: '根据客户端能力访问文件列表、空间、分享、收藏和回收站；本地同步或挂载能力以对应客户端实现为准。', tip: '请先阅读对应客户端 README 和部署配置' },
  { title: '上传或下载文件', desc: '使用客户端上传、下载或预览文件；大文件处理会经过上传会话、文件服务和异步处理链路。', tip: '文件夹上传、文件夹下载和断点续传以客户端与服务接口支持为准' },
  { title: '创建共享链接', desc: '在平台文件操作中创建分享资源，访问范围、凭证和撤回能力以当前接口与部署配置为准。', tip: '请以 Share API 和空间权限规则为准' },
  { title: '加入空间协作', desc: '在空间中管理成员、角色、文件夹和分享资源，按项目或部门组织协作边界。', tip: '空间权限和资源范围以平台服务接口与实际配置为准' },
]

const faqs = [
  { q: '下载的文件在哪里？', a: '下载的文件默认保存在浏览器的下载目录中。您可以在浏览器的下载管理器中查看下载进度和文件位置。Windows 默认路径为 C:\\Users\\用户名\\Downloads，macOS 默认路径为 ~/Downloads。' },
  { q: '安装过程中遇到安全警告怎么办？', a: '请根据操作系统提示核对安装包来源、发布流程和本机安全策略；仓库页面不对未提供签名证据的安装包作数字签名承诺。' },
  { q: '如何卸载客户端？', a: '请按照对应操作系统和客户端子项目的卸载说明处理；卸载前请确认本地未保存需要上传的文件。' },
  { q: '客户端支持代理设置吗？', a: '代理能力取决于具体客户端实现和部署网络，请查看对应客户端 README 与配置项。' },
  { q: '如何更新到最新版本？', a: '请以对应客户端子项目的发布说明、构建产物和部署流程为准；官网不虚构自动更新或静默下载能力。' },
]

const allPlatforms = [
  { platform: 'windows-x64', label: 'Windows', icon: 'fa fa-windows' },
  { platform: 'macos-arm64', label: 'macOS', icon: 'fa fa-apple' },
  { platform: 'linux-x64', label: 'Linux', icon: 'fa fa-linux' },
  { platform: 'ios', label: 'iOS', icon: 'fa fa-mobile' },
  { platform: 'android', label: 'Android', icon: 'fa fa-android' },
]
</script>

<style scoped>
/* ============================================================
   Hero 区域
   ============================================================ */
.thanks-hero {
  position: relative;
  overflow: hidden;
  border-bottom: 1px solid rgba(228, 231, 237, 0.85);
}

.thanks-bg {
  position: absolute;
  inset: 0;
  pointer-events: none;
}

.thanks-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(80px);
  opacity: 0.4;
  animation: thanksGlowFloat 8s ease-in-out infinite alternate;
}

.thanks-glow-1 {
  width: 400px;
  height: 400px;
  background: radial-gradient(circle, rgba(22, 93, 255, 0.25), transparent 70%);
  top: -100px;
  right: -80px;
}

.thanks-glow-2 {
  width: 350px;
  height: 350px;
  background: radial-gradient(circle, rgba(14, 140, 106, 0.2), transparent 70%);
  bottom: -80px;
  left: -60px;
  animation-delay: -4s;
}

@keyframes thanksGlowFloat {
  0% { transform: translate(0, 0) scale(1); }
  100% { transform: translate(30px, -20px) scale(1.1); }
}

.thanks-hero-overlay {
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(255,255,255,0.6) 0%, rgba(255,255,255,0.2) 100%);
  z-index: 1;
}

/* ============================================================
   成功打勾动画
   ============================================================ */
.thanks-check-wrapper {
  display: flex;
  justify-content: center;
}

.thanks-check-circle {
  width: 88px;
  height: 88px;
}

.thanks-check-svg {
  width: 100%;
  height: 100%;
}

.thanks-check-circle-bg {
  stroke: rgba(22, 93, 255, 0.12);
  stroke-width: 3;
}

.thanks-check-circle-fg {
  stroke: #165DFF;
  stroke-width: 3;
  stroke-dasharray: 157;
  stroke-dashoffset: 157;
  animation: thanksCircleDraw 0.8s ease-out 0.2s forwards;
  transform: rotate(-90deg);
  transform-origin: center;
}

@keyframes thanksCircleDraw {
  to { stroke-dashoffset: 0; }
}

.thanks-check-mark {
  stroke: #165DFF;
  stroke-width: 3;
  stroke-linecap: round;
  stroke-linejoin: round;
  stroke-dasharray: 36;
  stroke-dashoffset: 36;
  animation: thanksCheckDraw 0.5s ease-out 0.6s forwards;
}

@keyframes thanksCheckDraw {
  to { stroke-dashoffset: 0; }
}

/* ============================================================
   脉冲点
   ============================================================ */
.thanks-dot-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #165DFF;
  animation: thanksPulse 1.5s ease-in-out infinite;
}

@keyframes thanksPulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}

/* ============================================================
   通用
   ============================================================ */
details summary::-webkit-details-marker {
  display: none;
}

details summary {
  list-style: none;
}
</style>
