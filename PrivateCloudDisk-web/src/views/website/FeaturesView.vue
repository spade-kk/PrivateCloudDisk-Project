<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">功能特性</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">
            探索 PrivateCloudDisk 当前能力
          </h1>
          <p class="mt-4 text-lg text-neutral-500">从文件与文件夹 CURD、空间协作，到在线预览、插件和工作流市场，集中展示当前平台已接入的应用能力</p>
        </div>
      </div>
    </section>

    <!-- 文件管理 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div class="order-2 lg:order-1">
            <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">文件管理</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">文件与文件夹管理</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">从文件与文件夹的基础 CURD，到上传、下载、分享、回收站、收藏和标签管理，覆盖日常文件处理闭环。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in fileMgmtFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
          <div class="order-1 lg:order-2">
            <div class="rounded-2xl border border-neutral-200 bg-neutral-50 p-6">
              <div class="grid grid-cols-3 gap-3">
                <div v-for="icon in fileMgmtIcons" :key="icon" class="flex flex-col items-center gap-2 rounded-xl bg-white p-4 shadow-sm">
                  <i :class="[icon.icon, 'text-2xl', icon.color]"></i>
                  <span class="text-xs text-neutral-500">{{ icon.label }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 团队协作 -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <div class="rounded-2xl border border-neutral-200 bg-white p-6">
              <div class="space-y-3">
                <div v-for="c in collabCards" :key="c.title" class="flex items-center gap-4 rounded-xl border border-neutral-100 p-4">
                  <div :class="['flex h-10 w-10 items-center justify-center rounded-lg', c.bg]"><i :class="[c.icon, c.color]"></i></div>
                  <div><p class="text-sm font-semibold text-neutral-700">{{ c.title }}</p><p class="text-xs text-neutral-400">{{ c.desc }}</p></div>
                </div>
              </div>
            </div>
          </div>
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">团队协作</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">高效团队协作</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">以空间承载成员、角色、权限和资源范围，支持共享文件夹、空间插件和多人协作。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in collabFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- 安全防护 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-danger/10 px-3 py-1 text-xs font-medium text-danger">安全防护</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">企业级安全防护</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">通过网关认证、空间上下文、操作凭证、内部服务校验和生命周期状态，建立可配置、可追踪的访问边界。</p>
            <div class="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-2">
              <div v-for="s in securityFeatures" :key="s.title" class="rounded-xl border border-neutral-100 p-4 hover:border-primary/20 transition">
                <i :class="[s.icon, 'text-lg', s.color]"></i>
                <p class="mt-2 text-sm font-semibold text-neutral-700">{{ s.title }}</p>
                <p class="mt-1 text-xs text-neutral-400">{{ s.desc }}</p>
              </div>
            </div>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-neutral-900 p-8">
            <div class="space-y-4 font-mono text-xs">
              <div class="flex items-center gap-2 text-success"><span class="h-2 w-2 rounded-full bg-success"></span> Access boundary configured</div>
              <div class="flex items-center gap-2 text-info"><span class="h-2 w-2 rounded-full bg-info"></span> Gateway Auth Boundary</div>
              <div class="text-neutral-500 pl-4">│ JWT / client identity / route</div>
              <div class="flex items-center gap-2 text-warning"><span class="h-2 w-2 rounded-full bg-warning"></span> Space Context</div>
              <div class="text-neutral-500 pl-4">│ member / role / resource scope</div>
              <div class="flex items-center gap-2 text-success"><span class="h-2 w-2 rounded-full bg-success"></span> Operation Grant</div>
              <div class="text-neutral-500 pl-4">│ preview / download / upload</div>
              <div class="flex items-center gap-2 text-info"><span class="h-2 w-2 rounded-full bg-info"></span> Lifecycle Tracking</div>
              <div class="text-neutral-500 pl-4">│ event / state / execution record</div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- 多端同步 -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div class="order-2 lg:order-1">
            <div class="flex flex-wrap justify-center gap-4">
              <div v-for="d in devices" :key="d.label" class="flex flex-col items-center gap-2 rounded-2xl border border-neutral-200 bg-white p-6 w-28">
                <i :class="[d.icon, 'text-2xl text-primary']"></i>
                <span class="text-xs font-medium text-neutral-600">{{ d.label }}</span>
              </div>
            </div>
          </div>
          <div class="order-1 lg:order-2">
            <span class="inline-flex items-center gap-2 rounded-full bg-info/10 px-3 py-1 text-xs font-medium text-info">多端访问</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">按客户端能力接入</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">Web、桌面端、移动端、原生客户端和 CLI 通过统一 API 访问平台，具体同步、挂载和离线能力以各端实现为准。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in syncFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
            <router-link to="/download" class="mt-6 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline">下载所有客户端 <i class="fa fa-arrow-right text-xs"></i></router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- 智能分析 -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-medium text-warning">状态与观测</span>
            <h2 class="mt-4 text-3xl font-bold text-neutral-900 sm:text-4xl">按事件和执行状态定位问题</h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">通过文件、空间、上传、预览、下载和自动化执行状态观察业务链路，具体指标由部署侧监控接入提供。</p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in analyticsFeatures" :key="item" class="flex items-start gap-3 text-sm"><i class="fa fa-check-circle text-success mt-0.5"></i><span class="text-neutral-600">{{ item }}</span></li>
            </ul>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6">
            <div class="grid grid-cols-2 gap-4 mb-4">
              <div class="rounded-xl border border-neutral-100 p-4"><p class="text-xs text-neutral-400">空间配额</p><p class="text-2xl font-bold text-neutral-800 mt-1">按配置</p><div class="mt-2 h-2 rounded-full bg-neutral-200"><div class="h-2 rounded-full bg-primary" style="width:68%"></div></div></div>
              <div class="rounded-xl border border-neutral-100 p-4"><p class="text-xs text-neutral-400">执行状态</p><p class="text-2xl font-bold text-neutral-800 mt-1">可追踪</p><p class="text-xs text-success mt-1"><i class="fa fa-check"></i> 以服务记录为准</p></div>
            </div>
            <div class="rounded-xl border border-neutral-100 p-4">
              <p class="text-xs text-neutral-400 mb-3">月流量趋势</p>
              <div class="flex items-end gap-1 h-20">
                <div v-for="(h, i) in [45,62,55,80,72,90,78,85,65,88,70,92]" :key="i" class="flex-1 rounded-t bg-info/30" :style="{ height: h + '%' }"></div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- More Features -->
    <section class="py-20 sm:py-24 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-2xl text-center mb-12">
          <h2 class="text-3xl font-bold text-neutral-900">更多高级功能</h2>
          <p class="mt-4 text-neutral-500">云插件、本地扩展、工作流和市场能力按服务边界逐步组合</p>
        </div>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="f in moreFeatures" :key="f.title" class="rounded-2xl border border-neutral-200 bg-white p-6 transition hover:-translate-y-1 hover:shadow-lg">
            <div class="flex h-10 w-10 items-center justify-center rounded-xl" :class="f.bgClass">
              <i :class="[f.icon, 'text-lg', f.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ f.title }}</h3>
            <p class="mt-2 text-xs text-neutral-500 leading-relaxed">{{ f.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">准备好梳理文件业务了吗？</h2>
        <p class="mt-4 text-neutral-500">先阅读文档并结合部署配置，逐步启用文件、空间和扩展能力</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/register" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">开始使用</router-link>
          <router-link to="/docs" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">阅读文档</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：将功能页从泛化的企业宣传改为当前实现可核验的文件、空间、预览、插件与工作流能力说明。
// 原有行为：展示未绑定实现的数量、认证和业务承诺；新行为：仅展示仓库代码、接口和配置能够支撑的能力。
// 影响范围：仅影响官网文案和静态功能列表，不改变页面布局、样式、路由或业务交互。
const fileMgmtFeatures = [
  '支持拖拽上传、文件上传、文件夹上传及批量文件操作',
  '文件和文件夹支持创建、读取、更新、移动、复制与删除',
  '图片、PDF、Office、Markdown、代码、压缩包、音视频等在线预览入口',
  '分享链接支持访问控制、资源范围和撤回管理',
  '标签、收藏、最近访问和搜索帮助整理文件',
  '回收站支持删除、恢复和彻底清理流程',
]

const fileMgmtIcons = [
  { icon: 'fa fa-file-word-o', color: 'text-primary', label: '文档' },
  { icon: 'fa fa-file-image-o', color: 'text-danger', label: '图片' },
  { icon: 'fa fa-file-video-o', color: 'text-warning', label: '视频' },
  { icon: 'fa fa-file-audio-o', color: 'text-success', label: '音频' },
  { icon: 'fa fa-file-archive-o', color: 'text-purple-500', label: '压缩包' },
  { icon: 'fa fa-file-code-o', color: 'text-info', label: '代码' },
]

const collabFeatures = [
  '创建个人空间或团队空间，按项目/部门组织文件',
  '管理成员、角色、权限和可见资源范围',
  '在空间内启用插件，按空间边界授予扩展能力',
  '支持文件分享与外部访问控制，必要时撤回链接',
  '通过文件生命周期事件连接自动化处理',
  '以服务端状态和操作记录追踪协作过程',
]

const collabCards = [
  { title: '共享文件夹', desc: '团队统一存储空间', icon: 'fa fa-folder', bg: 'bg-primary/10', color: 'text-primary' },
  { title: '权限管理', desc: '精细化权限控制', icon: 'fa fa-lock', bg: 'bg-warning/10', color: 'text-warning' },
  { title: '实时评论', desc: '文件批注与讨论', icon: 'fa fa-comment', bg: 'bg-info/10', color: 'text-info' },
  { title: '链接分享', desc: '安全的外部分享', icon: 'fa fa-link', bg: 'bg-success/10', color: 'text-success' },
]

const securityFeatures = [
  { title: '统一认证', desc: '网关与 JWT 认证链路', icon: 'fa fa-lock', color: 'text-primary' },
  { title: '处理状态', desc: '上传、处理与激活状态追踪', icon: 'fa fa-refresh', color: 'text-danger' },
  { title: '空间权限', desc: '成员、角色与资源范围控制', icon: 'fa fa-shield', color: 'text-success' },
  { title: '操作记录', desc: '关键操作和执行结果可追踪', icon: 'fa fa-list-alt', color: 'text-warning' },
  { title: '内部服务校验', desc: '服务间令牌与来源控制', icon: 'fa fa-filter', color: 'text-purple-500' },
  { title: '临时访问凭证', desc: '下载、预览等操作按需授权', icon: 'fa fa-key', color: 'text-info' },
]

const devices = [
  { label: 'Windows', icon: 'fa fa-windows' },
  { label: 'macOS', icon: 'fa fa-apple' },
  { label: 'Linux', icon: 'fa fa-linux' },
  { label: 'iOS', icon: 'fa fa-apple' },
  { label: 'Android', icon: 'fa fa-android' },
  { label: 'Web', icon: 'fa fa-globe' },
]

const syncFeatures = [
  'Web、桌面端、移动端和原生客户端按各自能力接入',
  '文件操作通过统一 API 和网关路由进入平台',
  '上传下载任务提供进度、凭证和状态反馈',
  '文件夹上传与下载保持目录结构',
  '客户端身份用于识别设备与扩展分发范围',
  '具体同步策略以对应客户端实现为准',
]

const analyticsFeatures = [
  '按空间查看资源与配额边界',
  '通过文件、空间和扩展执行状态定位问题',
  '上传、预览、下载和自动化链路分段观测',
  '插件执行记录支持按状态查看',
  '工作流执行记录支持重试与取消',
  '服务健康检查与监控端点由各服务提供',
]

const moreFeatures = [
  { title: '云插件', desc: '由平台托管、校验并按权限运行的插件扩展。', icon: 'fa fa-cloud', bgClass: 'bg-neutral-100', iconClass: 'text-neutral-600' },
  { title: '本地扩展', desc: '面向 Web、桌面和移动客户端的受控扩展运行能力。', icon: 'fa fa-desktop', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '插件市场', desc: '浏览、发布、安装和管理可复用插件版本。', icon: 'fa fa-shopping-bag', bgClass: 'bg-purple-50', iconClass: 'text-purple-500' },
  { title: '自动化工作流', desc: '通过 DSL、能力中心和执行记录编排可复用流程。', icon: 'fa fa-magic', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '工作流市场', desc: '发现、提交、审核和导入已发布的工作流模板。', icon: 'fa fa-sitemap', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '文件内容处理', desc: '围绕文件内容就绪和可用事件接入处理与通知。', icon: 'fa fa-exchange', bgClass: 'bg-success/10', iconClass: 'text-success' },
]
</script>
