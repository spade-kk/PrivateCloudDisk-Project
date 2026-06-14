<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">客户端下载</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">
            全平台客户端
          </h1>
          <p class="mt-4 text-lg text-neutral-500">选择适合您设备的客户端，开始体验 CloudDrive。所有客户端均支持端到端加密传输。</p>
        </div>
      </div>
    </section>

    <!-- Desktop Clients -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">桌面客户端</h2>
        <p class="mt-2 text-center text-sm text-neutral-400">适用于 Windows、macOS 和 Linux 操作系统</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="client in desktopClients" :key="client.os" class="rounded-2xl border border-neutral-200 p-8 transition-all hover:border-primary/30 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-1">
            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl" :class="client.bgClass">
                <i :class="[client.icon, 'text-2xl', client.iconClass]"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">{{ client.os }}</h3>
                <p class="text-xs text-neutral-400">版本 {{ client.version }}</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">{{ client.desc }}</p>
            <div class="mt-5 space-y-2">
              <p class="text-xs text-neutral-400"><i class="fa fa-calendar mr-1"></i> 更新于 {{ client.updated }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-hdd-o mr-1"></i> {{ client.size }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-check-circle text-success mr-1"></i> {{ client.requirement }}</p>
            </div>
            <div class="mt-5 flex flex-col gap-2">
              <a :href="client.downloadUrl" class="flex items-center justify-center gap-2 rounded-xl bg-primary py-2.5 text-sm font-semibold text-white hover:bg-primary/90 transition">
                <i class="fa fa-download"></i> 立即下载
              </a>
              <a v-if="client.altUrl" :href="client.altUrl" class="text-center text-xs text-neutral-400 hover:text-primary transition">其他版本下载</a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Mobile Clients -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">移动客户端</h2>
        <p class="mt-2 text-center text-sm text-neutral-400">随时随地访问您的文件，支持 iOS 和 Android</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div v-for="client in mobileClients" :key="client.os" class="rounded-2xl border border-neutral-200 bg-white p-8 transition-all hover:border-primary/30 hover:shadow-lg hover:-translate-y-1">
            <div class="flex items-center gap-4">
              <div class="flex h-14 w-14 items-center justify-center rounded-xl" :class="client.bgClass">
                <i :class="[client.icon, 'text-2xl', client.iconClass]"></i>
              </div>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">{{ client.os }}</h3>
                <p class="text-xs text-neutral-400">版本 {{ client.version }}</p>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">{{ client.desc }}</p>
            <div class="mt-5 space-y-2">
              <p class="text-xs text-neutral-400"><i class="fa fa-calendar mr-1"></i> 更新于 {{ client.updated }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-hdd-o mr-1"></i> {{ client.size }}</p>
              <p class="text-xs text-neutral-400"><i class="fa fa-check-circle text-success mr-1"></i> {{ client.requirement }}</p>
            </div>
            <div class="mt-5 flex flex-col gap-2">
              <a :href="client.storeUrl" class="flex items-center justify-center gap-2 rounded-xl bg-neutral-900 py-2.5 text-sm font-semibold text-white hover:bg-neutral-800 transition">
                <i :class="client.storeIcon"></i> {{ client.storeLabel }}
              </a>
              <a :href="client.downloadUrl" class="flex items-center justify-center gap-2 rounded-xl border border-neutral-200 py-2.5 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary transition">
                <i class="fa fa-download"></i> 直接下载 APK/IPA
              </a>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CLI & SDK -->
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

    <!-- Version History -->
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

    <!-- System Requirements -->
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

    <!-- Enterprise Deployment -->
    <section class="py-16 sm:py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="rounded-3xl bg-gradient-to-br from-primary/5 to-info/5 border border-primary/10 p-8 sm:p-10">
          <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-6">
            <div>
              <h2 class="text-2xl font-bold text-neutral-900">企业批量部署</h2>
              <p class="mt-2 text-sm text-neutral-500">支持 MSI/AD组策略/移动设备管理(MDM) 等多种企业级部署方式</p>
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

<script setup>
const desktopClients = [
  {
    os: 'Windows', icon: 'fa fa-windows', iconClass: 'text-primary', bgClass: 'bg-primary/10',
    version: '3.2.0', size: '128 MB', requirement: 'Windows 10/11 (64-bit)',
    desc: '支持文件资源管理器集成、右键菜单快捷操作、托盘图标常驻、自动同步文件夹。', updated: '2026-01-12',
    downloadUrl: '#', altUrl: '#',
  },
  {
    os: 'macOS', icon: 'fa fa-apple', iconClass: 'text-neutral-800', bgClass: 'bg-neutral-100',
    version: '3.2.0', size: '156 MB', requirement: 'macOS 12.0+ (Intel & Apple Silicon)',
    desc: '原生 Apple Silicon 支持，Finder 集成、Touch Bar 快捷操作、iCloud 文件夹同步。', updated: '2026-01-12',
    downloadUrl: '#', altUrl: '#',
  },
  {
    os: 'Linux', icon: 'fa fa-linux', iconClass: 'text-warning', bgClass: 'bg-warning/10',
    version: '3.2.0', size: '98 MB', requirement: 'Ubuntu 20.04+, Debian 11+, CentOS 8+',
    desc: '支持 AppImage、deb、rpm 格式，Nautilus 集成，命令行工具，Docker 镜像。', updated: '2026-01-12',
    downloadUrl: '#', altUrl: '#',
  },
]

const mobileClients = [
  {
    os: 'iOS', icon: 'fa fa-apple', iconClass: 'text-neutral-800', bgClass: 'bg-neutral-100',
    version: '3.1.5', size: '89 MB', requirement: 'iOS 15.0+',
    desc: '支持 Face ID/Touch ID 解锁，文件应用集成，照片自动备份，离线文件访问。', updated: '2026-01-10',
    downloadUrl: '#', storeUrl: '#', storeIcon: 'fa fa-apple', storeLabel: 'App Store 下载',
  },
  {
    os: 'Android', icon: 'fa fa-android', iconClass: 'text-success', bgClass: 'bg-success/10',
    version: '3.1.5', size: '72 MB', requirement: 'Android 8.0+',
    desc: '支持指纹解锁，文件管理器集成，照片自动备份，应用数据同步，Material You 主题。', updated: '2026-01-10',
    downloadUrl: '#', storeUrl: '#', storeIcon: 'fa fa-google', storeLabel: 'Google Play 下载',
  },
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