<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">部署与能力</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">按空间、服务与扩展能力规划</h1>
          <p class="mt-4 text-lg text-neutral-500">当前仓库未固定公开商业套餐和服务指标，实际资源、部署方式与支持范围请结合环境评估</p>
          <!-- Toggle -->
          <div class="mt-8 inline-flex items-center gap-3 rounded-full bg-neutral-100 p-1">
            <button @click="isYearly = false" :class="['rounded-full px-5 py-2 text-sm font-medium transition', !isYearly ? 'bg-white shadow-sm text-neutral-800' : 'text-neutral-400']">按月付费</button>
            <button @click="isYearly = true" :class="['rounded-full px-5 py-2 text-sm font-medium transition', isYearly ? 'bg-white shadow-sm text-neutral-800' : 'text-neutral-400']">
              按年付费
              <span class="ml-1 rounded bg-success/10 px-1.5 py-0.5 text-xs text-success">配置说明</span>
            </button>
          </div>
        </div>
      </div>
    </section>

    <!-- Plans -->
    <section class="-mt-8 pb-20 sm:pb-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-6 lg:grid-cols-4">
          <div v-for="plan in displayPlans" :key="plan.key" :class="['rounded-2xl border p-8 transition-all hover:-translate-y-1', plan.featured ? 'border-primary bg-white shadow-xl shadow-primary/10 relative' : 'border-neutral-200 bg-white hover:shadow-lg']">
            <div v-if="plan.featured" class="absolute -top-3 left-1/2 -translate-x-1/2 rounded-full bg-primary px-4 py-1 text-xs font-semibold text-white">最受欢迎</div>
            <h3 class="text-xl font-bold text-neutral-800">{{ plan.name }}</h3>
            <p class="mt-1 text-sm text-neutral-400">{{ plan.desc }}</p>
            <div class="mt-6">
              <span class="text-4xl font-extrabold text-neutral-900">{{ plan.priceDisplay }}</span>
              <span class="text-sm text-neutral-400">/{{ plan.period }}</span>
            </div>
            <p v-if="plan.originalPrice" class="mt-1 text-xs text-neutral-400 line-through">{{ plan.originalPrice }}</p>
            <router-link :to="plan.actionUrl" :class="['mt-6 flex items-center justify-center gap-2 rounded-xl py-3 text-sm font-semibold transition', plan.featured ? 'bg-primary text-white hover:bg-primary/90' : 'border-2 border-neutral-200 text-neutral-700 hover:border-primary hover:text-primary']">
              {{ plan.actionLabel }}
            </router-link>
            <ul class="mt-6 space-y-3">
              <li v-for="f in plan.features" :key="f" class="flex items-start gap-2 text-sm">
                <i class="fa fa-check text-success mt-0.5 text-xs"></i>
                <span class="text-neutral-600">{{ f }}</span>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </section>

    <!-- Compare Table -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">功能对比</h2>
        <div class="mt-10 overflow-x-auto">
          <table class="w-full min-w-[640px] text-left text-sm">
            <thead>
              <tr class="border-b-2 border-neutral-200">
                <th class="py-3 pr-4 font-semibold text-neutral-700">功能</th>
              <th class="px-4 py-3 text-center font-semibold text-neutral-700">基础能力</th>
                <th class="px-4 py-3 text-center font-semibold text-primary">专业版</th>
                <th class="px-4 py-3 text-center font-semibold text-neutral-700">企业版</th>
                <th class="px-4 py-3 text-center font-semibold text-neutral-700">旗舰版</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in compareRows" :key="row.label" class="border-b border-neutral-100">
                <td class="py-3 pr-4 font-medium text-neutral-700">{{ row.label }}</td>
                <td v-for="col in row.values" :key="col" class="px-4 py-3 text-center text-neutral-500">
                  <template v-if="col === true"><i class="fa fa-check text-success"></i></template>
                  <template v-else-if="col === false"><i class="fa fa-times text-neutral-300"></i></template>
                  <template v-else>{{ col }}</template>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </section>

    <!-- FAQ -->
    <section class="py-20 sm:py-24">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">常见问题</h2>
        <div class="mt-10 space-y-4">
          <div v-for="(faq, i) in faqs" :key="i" class="rounded-xl border border-neutral-200 p-5">
            <button @click="expandedFaq = expandedFaq === i ? null : i" class="flex w-full items-center justify-between text-left">
              <span class="text-sm font-semibold text-neutral-700">{{ faq.q }}</span>
              <i :class="[expandedFaq === i ? 'fa fa-angle-up' : 'fa fa-angle-down', 'text-neutral-400']"></i>
            </button>
            <p v-if="expandedFaq === i" class="mt-3 text-sm text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Enterprise Custom -->
    <section class="py-16 sm:py-20 bg-white">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div class="rounded-3xl border border-neutral-200 bg-gradient-to-br from-primary/5 to-info/5 p-8 sm:p-10">
          <div class="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-6">
            <div>
              <h2 class="text-2xl font-bold text-neutral-900">需要定制方案？</h2>
              <p class="mt-2 text-sm text-neutral-500">需要专属部署、定制开发或组织级接入？请结合服务边界、存储和客户端范围进行评估。</p>
            </div>
            <router-link to="/contact" class="shrink-0 inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">提交部署问题 <i class="fa fa-arrow-right"></i></router-link>
          </div>
          <div class="mt-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-server text-2xl text-primary mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">私有化部署</p>
              <p class="text-xs text-neutral-400 mt-1">部署在自有服务器</p>
            </div>
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-paint-brush text-2xl text-purple-500 mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">品牌定制</p>
              <p class="text-xs text-neutral-400 mt-1">品牌与页面范围按项目确认</p>
            </div>
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-headphones text-2xl text-info mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">专属服务</p>
              <p class="text-xs text-neutral-400 mt-1">支持范围按维护渠道确认</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">需要定制方案？</h2>
        <p class="mt-4 text-neutral-500">通过项目维护渠道提交部署、接口或扩展范围问题</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/contact" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">提交问题</router-link>
          <router-link to="/docs" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">阅读文档</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：将未有服务端计费依据的固定价格、SLA、配额和商业承诺改为部署与能力规划说明。
// 原有行为：前端静态展示虚构价格、容量和服务等级；新行为：展示当前代码支持的能力组合，并明确实际范围需按部署评估。
// 影响范围：仅影响官网定价文案和静态比较数据，不改变页面布局、套餐切换交互或路由。
const isYearly = ref(false)
const expandedFaq = ref(null)

const plans = [
  {
    key: 'self-hosted', name: '自部署基础', desc: '从核心文件能力开始', priceDisplay: '按环境评估', period: '部署配置',
    featured: false, actionLabel: '查看文档', actionUrl: '/docs',
    features: ['文件与文件夹 CURD', '文件上传与下载', '回收站、收藏和标签', '基础在线预览', 'Docker Compose 联调', '数据与存储位置可控'],
  },
  {
    key: 'space', name: '空间协作', desc: '面向团队和项目', priceDisplay: '按规模评估', period: '部署配置',
    featured: true, actionLabel: '开始使用', actionUrl: '/register',
    features: ['个人空间与团队空间', '成员、角色和权限', '共享文件夹与分享链接', '空间插件管理', '文件和目录资源边界', '协作状态可追踪'],
  },
  {
    key: 'automation', name: '自动化扩展', desc: '连接插件与工作流', priceDisplay: '按能力组合', period: '部署配置',
    featured: false, actionLabel: '了解扩展', actionUrl: '/features',
    features: ['云插件', '本地扩展', '能力中心', '工作流 DSL 与执行', '插件市场与工作流市场', '调度、重试和执行记录'],
  },
  {
    key: 'enterprise', name: '定制部署', desc: '按组织与合规要求规划', priceDisplay: '联系项目组', period: '部署配置',
    featured: false, actionLabel: '联系项目组', actionUrl: '/contact',
    features: ['服务边界梳理', '存储与网络规划', '客户端接入评估', '内部 API 与事件契约', '监控与安全基线', '定制开发范围确认'],
  },
]

const displayPlans = computed(() => {
  return plans.map(p => {
    return { ...p, originalPrice: null }
  })
})

const compareRows = [
  { label: '文件与文件夹 CURD', values: [true, true, true, true] },
  { label: '上传、下载与文件夹传输', values: [true, true, true, true] },
  { label: '在线预览与媒体播放', values: ['按配置', '按配置', '按配置', '按配置'] },
  { label: '回收站、收藏和标签', values: [true, true, true, true] },
  { label: '空间成员与角色', values: [false, true, true, '按需规划'] },
  { label: '空间插件', values: [false, true, true, '按需规划'] },
  { label: '云插件与本地扩展', values: [false, false, true, '按需规划'] },
  { label: '工作流与能力中心', values: [false, false, true, '按需规划'] },
  { label: '插件/工作流市场', values: [false, false, true, '按需规划'] },
  { label: '监控与安全基线', values: ['基础', '基础', '扩展', '定制'] },
]

const faqs = [
  { q: '页面上的方案是固定商业套餐吗？', a: '不是。本页只按当前平台能力整理部署和扩展维度，仓库没有固定公开的商业价格、SLA 或配额承诺。' },
  { q: '如何选择空间协作能力？', a: '如果需要多人共享项目或部门文件，优先规划团队空间、成员角色、权限和资源范围，再决定是否启用空间插件。' },
  { q: '如何选择自动化能力？', a: '先确认事件来源、能力权限和执行边界，再组合云插件、本地扩展或工作流；具体支持范围以服务实现和部署配置为准。' },
  { q: '数据和服务如何部署？', a: '仓库提供 Docker Compose 联调拓扑，业务服务、存储服务、网关和基础设施通过服务名通信；生产环境请根据实际安全和运维要求规划。' },
  { q: '如何获取准确的资源限制？', a: '请查看 .env.example、各服务 application.yml/配置文件和对应 README；页面不再写入未经配置或测试验证的容量、性能和可用性指标。' },
]
</script>
