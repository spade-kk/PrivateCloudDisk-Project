<template>
  <div>
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">合作伙伴</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">携手共建生态</h1>
          <p class="mt-4 text-lg text-neutral-500">PrivateCloudDisk 展示当前仓库使用的基础设施、服务机制和开放扩展接口</p>
        </div>
      </div>
    </section>

    <!-- Partner Categories -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8 space-y-20">
        <div v-for="cat in partnerCategories" :key="cat.title">
          <div class="text-center mb-12">
            <h2 class="text-2xl font-bold text-neutral-900">{{ cat.title }}</h2>
            <p class="mt-2 text-sm text-neutral-400">{{ cat.desc }}</p>
          </div>
          <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
            <div v-for="p in cat.partners" :key="p.name" class="rounded-2xl border border-neutral-200 bg-white p-8 text-center transition hover:-translate-y-1 hover:shadow-lg">
              <div class="mx-auto flex h-16 w-16 items-center justify-center rounded-2xl bg-neutral-100 text-2xl font-bold text-neutral-400">{{ p.name.charAt(0) }}</div>
              <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ p.name }}</h3>
              <p class="mt-1 text-xs text-neutral-400">{{ p.desc }}</p>
              <span class="mt-3 inline-block rounded-full bg-neutral-100 px-3 py-1 text-[11px] font-medium text-neutral-500">{{ p.type }}</span>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Become Partner -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">参与平台生态建设</h2>
        <p class="mt-4 text-neutral-500">围绕基础设施、插件、工作流、客户端和文档提交可核验的集成贡献</p>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-3">
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-certificate text-3xl text-primary mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">认证合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">获得销售和技术认证，共同服务客户</p>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-puzzle-piece text-3xl text-info mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">技术合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">产品集成与联合解决方案开发</p>
          </div>
          <div class="rounded-2xl border border-neutral-200 bg-white p-6 text-center">
            <i class="fa fa-globe text-3xl text-success mb-3"></i>
            <h3 class="text-base font-semibold text-neutral-800">渠道合作伙伴</h3>
            <p class="mt-2 text-xs text-neutral-500">区域代理与分销，拓展市场覆盖</p>
          </div>
        </div>
        <router-link to="/contact" class="mt-8 inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">申请成为合作伙伴 <i class="fa fa-arrow-right"></i></router-link>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
const partnerCategories = [
  {
    title: '基础设施组件', desc: '当前仓库和部署配置中使用的存储、消息、搜索与观测组件',
    partners: [
      { name: 'MinIO', desc: 'S3 兼容对象存储后端', type: '仓库能力' },
      { name: 'MySQL', desc: '业务与服务元数据存储', type: '仓库能力' },
      { name: 'Redis', desc: '缓存、限流与临时凭证', type: '仓库能力' },
      { name: 'RabbitMQ', desc: '文件生命周期与异步任务', type: '仓库能力' },
      { name: 'OpenSearch', desc: '搜索与索引基础设施', type: '按配置接入' },
      { name: 'Docker Compose', desc: '本地与联调部署编排', type: '仓库能力' },
      { name: 'Prometheus', desc: '指标采集接口', type: '按配置接入' },
      { name: 'SkyWalking', desc: '服务链路追踪', type: '按配置接入' },
    ],
  },
  {
    title: '安全边界机制', desc: '当前代码中用于认证、空间和扩展运行边界的机制',
    partners: [
      { name: 'Gateway Auth', desc: '统一认证与路由边界', type: '仓库能力' },
      { name: 'Space Context', desc: '空间成员与资源范围', type: '仓库能力' },
      { name: 'Operation Token', desc: '预览和下载临时授权', type: '仓库能力' },
      { name: 'Plugin Sandbox', desc: '扩展包校验与隔离运行', type: '仓库能力' },
    ],
  },
  {
    title: '开放扩展接口', desc: '当前平台提供的 API、插件和工作流扩展入口',
    partners: [
      { name: 'REST API', desc: '平台业务与文件能力入口', type: '仓库能力' },
      { name: 'Plugin SDK', desc: '云插件与本地扩展能力', type: '仓库能力' },
      { name: 'Workflow DSL', desc: '可校验、可发布的流程定义', type: '仓库能力' },
      { name: 'Event Contract', desc: '文件生命周期事件契约', type: '仓库能力' },
    ],
  },
]
</script>
