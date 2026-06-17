<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">定价方案</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">选择适合您的方案</h1>
          <p class="mt-4 text-lg text-neutral-500">从个人到企业，我们提供灵活的定价方案。所有方案均支持 7 天免费试用。</p>
          <!-- Toggle -->
          <div class="mt-8 inline-flex items-center gap-3 rounded-full bg-neutral-100 p-1">
            <button @click="isYearly = false" :class="['rounded-full px-5 py-2 text-sm font-medium transition', !isYearly ? 'bg-white shadow-sm text-neutral-800' : 'text-neutral-400']">按月付费</button>
            <button @click="isYearly = true" :class="['rounded-full px-5 py-2 text-sm font-medium transition', isYearly ? 'bg-white shadow-sm text-neutral-800' : 'text-neutral-400']">
              按年付费
              <span class="ml-1 rounded bg-success/10 px-1.5 py-0.5 text-xs text-success">省 20%</span>
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
                <th class="px-4 py-3 text-center font-semibold text-neutral-700">免费版</th>
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
              <p class="mt-2 text-sm text-neutral-500">需要专属部署、定制开发或大规模采购？我们的专家团队将为您量身定制方案。</p>
            </div>
            <router-link to="/contact" class="shrink-0 inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">联系销售团队 <i class="fa fa-arrow-right"></i></router-link>
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
              <p class="text-xs text-neutral-400 mt-1">Logo/域名/UI 定制</p>
            </div>
            <div class="rounded-xl bg-white/70 p-4 text-center">
              <i class="fa fa-headphones text-2xl text-info mb-2"></i>
              <p class="text-sm font-semibold text-neutral-700">专属服务</p>
              <p class="text-xs text-neutral-400 mt-1">7x24 专属客户经理</p>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- CTA -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20">
      <div class="mx-auto max-w-3xl px-4 text-center sm:px-6 lg:px-8">
        <h2 class="text-3xl font-bold text-neutral-900">需要定制方案？</h2>
        <p class="mt-4 text-neutral-500">我们的销售团队会为您提供专属的企业定制方案</p>
        <div class="mt-8 flex justify-center gap-4">
          <router-link to="/contact" class="rounded-xl bg-primary px-8 py-3 text-sm font-semibold text-white hover:bg-primary/90">联系销售团队</router-link>
          <router-link to="/register" class="rounded-xl border border-neutral-200 px-8 py-3 text-sm font-semibold text-neutral-600 hover:border-primary hover:text-primary">免费试用</router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const isYearly = ref(false)
const expandedFaq = ref(null)

const plans = [
  {
    key: 'free', name: '免费版', desc: '个人轻度使用', price: 0, priceYearly: 0, period: '月',
    featured: false, actionLabel: '免费注册', actionUrl: '/register',
    features: ['10 GB 存储空间', '单文件最大 1 GB', '基础文件预览', '7 天回收站', '社区支持', '1 个设备同步'],
  },
  {
    key: 'pro', name: '专业版', desc: '专业用户首选', price: 29, priceYearly: 279, period: '月',
    featured: true, actionLabel: '开始试用', actionUrl: '/register',
    features: ['1 TB 存储空间', '单文件最大 50 GB', '高级文件预览', '30 天回收站', '优先技术支持', '无限设备同步', 'API 访问', '团队协作 (5人)'],
  },
  {
    key: 'business', name: '企业版', desc: '中小型团队', price: 99, priceYearly: 950, period: '月',
    featured: false, actionLabel: '开始试用', actionUrl: '/register',
    features: ['10 TB 存储空间', '单文件无限制', '全部预览功能', '90 天回收站', 'VIP 技术支持', 'SSO 单点登录', '审计日志', '团队协作 (50人)'],
  },
  {
    key: 'enterprise', name: '旗舰版', desc: '大型企业定制', price: 0, priceYearly: 0, period: '年',
    featured: false, actionLabel: '联系销售', actionUrl: '/contact',
    features: ['无限存储空间', '全部功能', '永久回收站', '7x24 专属支持', '私有部署', 'SLA 99.99%', '定制开发', '专属客户经理'],
  },
]

const displayPlans = computed(() => {
  return plans.map(p => {
    const price = isYearly.value ? p.priceYearly : p.price
    if (p.key === 'enterprise') return { ...p, priceDisplay: '联系客服', period: '年', originalPrice: null }
    if (p.key === 'free') return { ...p, priceDisplay: '免费', period: '月', originalPrice: null }
    const monthlyPrice = isYearly.value ? Math.round(p.price * 12 * 0.8 / 12) : p.price
    return {
      ...p,
      priceDisplay: `¥${monthlyPrice}`,
      period: '月',
      originalPrice: isYearly.value ? `¥${p.price * 12}/年` : null,
    }
  })
})

const compareRows = [
  { label: '存储空间', values: ['10 GB', '1 TB', '10 TB', '无限'] },
  { label: '单文件大小限制', values: ['1 GB', '50 GB', '无限制', '无限制'] },
  { label: '文件在线预览', values: ['基础', '高级', '全部', '全部'] },
  { label: '版本历史', values: ['5 个', '30 个', '100 个', '无限'] },
  { label: '回收站保留', values: ['7 天', '30 天', '90 天', '永久'] },
  { label: '端到端加密', values: [true, true, true, true] },
  { label: '病毒扫描', values: [false, true, true, true] },
  { label: '双因素认证', values: [true, true, true, true] },
  { label: 'API 访问', values: [false, true, true, true] },
  { label: 'SSO 单点登录', values: [false, false, true, true] },
  { label: '审计日志', values: [false, false, true, true] },
  { label: '团队协作', values: [false, '5 人', '50 人', '无限'] },
  { label: '技术支持', values: ['社区', '优先', 'VIP', '7x24 专属'] },
  { label: '私有部署', values: [false, false, false, true] },
  { label: 'SLA 保障', values: ['99.9%', '99.9%', '99.95%', '99.99%'] },
  { label: '定制开发', values: [false, false, false, true] },
]

const faqs = [
  { q: '可以随时切换套餐吗？', a: '可以。您可以随时升级或降级套餐。升级立即生效，降级将在当前计费周期结束后生效。数据不会丢失。' },
  { q: '如何取消订阅？', a: '在「套餐管理」页面点击「取消订阅」即可。取消后，您可以在当前计费周期结束前继续使用服务，到期后自动降级为免费版。' },
  { q: '是否支持企业发票？', a: '专业版及以上套餐均支持开具增值税专用发票和普通发票。在「套餐管理」→「发票管理」中申请即可。' },
  { q: '数据安全如何保障？', a: '所有数据均采用 AES-256 加密存储，传输过程使用 TLS 1.3。我们已通过 ISO 27001、等保三级、SOC 2 等安全认证，并定期进行第三方安全审计。' },
  { q: '支持哪些支付方式？', a: '支持支付宝、微信支付、银行转账（对公）、信用卡支付。企业版和旗舰版还支持按月结算的对公转账。' },
]
</script>