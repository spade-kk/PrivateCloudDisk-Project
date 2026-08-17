<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">媒体报道</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">媒体中心</h1>
          <p class="mt-4 text-lg text-neutral-500">了解 PrivateCloudDisk 的版本能力、技术文档与项目资源</p>
        </div>
      </div>
    </section>

    <!-- Press Releases -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">项目动态</h2>
        <div class="space-y-6">
          <div v-for="pr in pressReleases" :key="pr.title" class="rounded-2xl border border-neutral-200 p-6 hover:border-primary/20 hover:shadow-sm transition cursor-pointer">
            <div class="flex items-center gap-2 text-xs text-neutral-400 mb-2">
              <span class="rounded-md bg-primary/10 px-2 py-0.5 text-primary font-medium">{{ pr.category }}</span>
              <span>{{ pr.date }}</span>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800 hover:text-primary transition">{{ pr.title }}</h3>
            <p class="mt-2 text-sm text-neutral-500">{{ pr.summary }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Media Coverage -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">技术资料</h2>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div v-for="m in mediaCoverage" :key="m.source" class="rounded-2xl border border-neutral-200 bg-white p-6 hover:shadow-md transition">
            <p class="text-xs font-semibold text-primary uppercase tracking-wider">{{ m.source }}</p>
            <h3 class="mt-2 text-base font-semibold text-neutral-800">{{ m.title }}</h3>
            <p class="mt-2 text-xs text-neutral-400">{{ m.date }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Brand Resources -->
    <section class="py-20">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 mb-10">品牌资源</h2>
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-image text-3xl text-primary mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">Logo 素材</h3>
            <p class="mt-1 text-xs text-neutral-400">PNG, SVG, AI 格式</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">下载</a>
          </div>
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-paint-brush text-3xl text-purple-500 mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">品牌指南</h3>
            <p class="mt-1 text-xs text-neutral-400">颜色、字体、使用规范</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">查看</a>
          </div>
          <div class="rounded-2xl border border-neutral-200 p-6 text-center hover:shadow-md transition">
            <i class="fa fa-camera text-3xl text-info mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">产品截图</h3>
            <p class="mt-1 text-xs text-neutral-400">Web 端与移动端</p>
            <a href="#" class="mt-4 inline-block text-sm font-medium text-primary hover:underline">下载</a>
          </div>
        </div>
      </div>
    </section>

    <section class="border-t border-neutral-100 py-16">
      <div class="mx-auto max-w-4xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900">媒体联系</h2>
        <p class="mt-2 text-neutral-500">功能、文档和集成建议请通过项目仓库的 issue 或维护渠道反馈</p>
        <p class="mt-4 text-primary font-medium">以仓库实际维护渠道为准</p>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const pressReleases = [
  { category: '能力更新', date: '2026-07-29', title: '空间协作、插件与工作流能力进入官网说明', summary: '官网文案同步当前仓库中的空间、文件生命周期、扩展市场和自动化服务边界。' },
  { category: '文件能力', date: '2026-07-29', title: '补充文件管理、在线预览与媒体播放说明', summary: '覆盖文件和文件夹 CRUD、上传下载、分享、回收站、收藏、标签及多类型预览入口。' },
  { category: '架构文档', date: '2026-07-29', title: '更新微服务技术栈与职责边界', summary: '根 README 与 docs 文档改为以实际代码、Compose 配置、接口和契约为依据的 GitHub 项目说明。' },
]

const mediaCoverage = [
  { source: 'README', title: '技术栈、服务边界与快速开始', date: '根目录' },
  { source: 'docs/architecture', title: '微服务拓扑、通信方式与部署边界', date: '文档' },
  { source: 'docs/api-overview', title: '网关路由、平台接口与文件服务入口', date: '文档' },
  { source: 'Plugin Guide', title: '插件清单、运行时和市场能力', date: '文档' },
  { source: 'Workflow Guide', title: '工作流 DSL、能力中心和执行链路', date: '文档' },
  { source: 'contracts', title: '跨服务事件与插件契约', date: '目录' },
]
</script>
