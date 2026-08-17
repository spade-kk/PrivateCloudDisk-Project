<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">更新日志</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">产品更新日志</h1>
          <p class="mt-4 text-lg text-neutral-500">记录当前仓库的能力、服务边界和文档更新；不把未核验版本号和性能结果写成发布事实</p>
        </div>
      </div>
    </section>

    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <!-- Subscribe -->
        <div class="mb-16 flex flex-col items-center justify-between gap-4 rounded-2xl border border-neutral-200 bg-neutral-50/50 p-6 sm:flex-row">
          <div>
            <p class="text-sm font-semibold text-neutral-700">订阅更新通知</p>
            <p class="text-xs text-neutral-400">通过邮件接收最新版本更新通知</p>
          </div>
          <form @submit.prevent class="flex gap-2">
            <input type="email" placeholder="输入邮箱" class="rounded-xl border border-neutral-200 px-4 py-2 text-sm focus:border-primary focus:outline-none w-48" />
            <button class="rounded-xl bg-primary px-4 py-2 text-sm font-semibold text-white hover:bg-primary/90">订阅</button>
          </form>
        </div>

        <!-- Timeline -->
        <div class="relative">
          <div class="absolute left-4 top-0 bottom-0 w-px bg-neutral-200 sm:left-8" aria-hidden></div>
          <div class="space-y-12">
            <div v-for="release in releases" :key="release.version" class="relative pl-12 sm:pl-16">
              <div class="absolute left-0 top-1 flex h-8 w-8 items-center justify-center rounded-full border-4 border-white bg-primary text-white sm:left-4">
                <i class="fa fa-tag text-xs"></i>
              </div>
              <div>
                <div class="flex flex-wrap items-center gap-3">
                  <span class="inline-flex items-center rounded-lg bg-primary/10 px-3 py-1 text-sm font-bold text-primary">v{{ release.version }}</span>
                  <span class="text-sm text-neutral-400">{{ release.date }}</span>
                  <span v-if="release.latest" class="rounded-full bg-success/10 px-2 py-0.5 text-[10px] font-medium text-success">Latest</span>
                </div>
                <p class="mt-2 text-lg font-semibold text-neutral-800">{{ release.title }}</p>
                <p class="mt-1 text-sm text-neutral-500">{{ release.summary }}</p>
                <div class="mt-5 space-y-4">
                  <div v-for="section in release.sections" :key="section.label">
                    <p class="text-xs font-semibold uppercase tracking-wider text-neutral-400 mb-2">{{ section.label }}</p>
                    <ul class="space-y-1.5">
                      <li v-for="item in section.items" :key="item" class="flex items-start gap-2 text-sm">
                        <i class="fa fa-plus-circle text-[10px] text-success mt-1 shrink-0" v-if="section.label === '新增功能'"></i>
                        <i class="fa fa-wrench text-[10px] text-info mt-1 shrink-0" v-else-if="section.label === '优化改进'"></i>
                        <i class="fa fa-bug text-[10px] text-danger mt-1 shrink-0" v-else></i>
                        <span class="text-neutral-600">{{ item }}</span>
                      </li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- Pagination -->
        <div class="mt-12 flex justify-center">
          <button class="rounded-xl border border-neutral-200 px-6 py-2.5 text-sm font-medium text-neutral-600 hover:border-primary hover:text-primary transition">加载更多版本</button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const releases = [
  {
    version: '当前仓库', date: '2026-07-29', latest: true,
    title: '空间协作、文件生命周期与扩展平台',
    summary: '补充空间、文件、在线预览、云插件、本地扩展、工作流和市场能力说明。',
    sections: [
      { label: '能力说明', items: ['文件和文件夹 CRUD、上传下载与传输', '空间成员、角色、权限和资源范围', '图片、PDF、Office、代码和媒体预览入口', '插件服务、插件运行时和插件市场', '工作流 DSL、能力中心、调度和工作流市场', '文件事件、自动化执行和状态追踪'] },
      { label: '能力整理', items: ['补充空间协作与资源边界说明', '补充插件、工作流与市场入口', '补充文件预览和媒体播放说明', '补充服务职责与事件链路文档', '补充客户端身份与扩展边界说明'] },
      { label: '文档修订', items: ['移除未经验证的客户、规模和 SLA 文案', '统一 storage-service 服务命名', '更新微服务技术栈与职责边界', '补充文件分享、回收站和标签能力', '更新部署配置与 API 导航'] },
    ],
  },
  {
    version: '文档修订', date: '2026-07-29',
    title: '移除虚构指标与过期服务命名',
    summary: '官网、根 README、docs 和各子项目 README 统一按当前代码、配置、迁移和契约描述能力。',
    sections: [
      { label: '能力整理', items: ['统一 storage-service 服务名', '补充 Gateway、Platform、Storage 边界', '补充 Plugin、Workflow、Automation、Scheduler', '补充 Client Registration 与 Runtime', '补充 contracts 和 Compose 导航'] },
      { label: '文案边界', items: ['移除客户、用户、容量和性能数字', '移除未验证的认证和合规结果', '移除固定价格、免费额度和 SLA 承诺'] },
      { label: '验证方式', items: ['以服务代码和控制器为准', '以配置、迁移和事件契约为准', '以构建、测试和实际部署探针为准'] },
    ],
  },
  {
    version: '架构说明', date: '2026-07-29',
    title: '微服务职责与性能边界',
    summary: '根 README 和 docs/architecture.md 补充技术栈、服务边界、通信方式和性能验证要求。',
    sections: [
      { label: '新增内容', items: ['Gateway、Platform、Storage 服务职责', 'MySQL、Redis、RabbitMQ、MinIO 和 OpenSearch', 'Web、桌面、移动、原生和 CLI 客户端目录', '文件分片、Range、异步 Worker 和观测基础', '与传统单体和 Nextcloud 的边界比较', '快速开始与自动化 profile 说明'] },
      { label: '优化改进', items: ['补充空间上下文与资源边界', '完善文件内容处理生命周期', '接入插件、工作流和市场能力', '官网与项目文档按当前实现更新'] },
      { label: '问题修复', items: ['修复大量已知问题', '改进系统稳定性', '优化数据库查询性能'] },
    ],
  },
]
</script>
