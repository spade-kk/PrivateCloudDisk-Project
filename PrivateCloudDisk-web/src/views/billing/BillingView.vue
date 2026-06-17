<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="套餐管理"
      description="选择适合的套餐，管理订阅与账单"
      :breadcrumbs="[{ label: '套餐管理', icon: 'fa fa-credit-card' }]"
    />

    <!-- 当前套餐 -->
    <div class="responsive-panel p-4 sm:p-6">
      <div class="flex flex-col sm:flex-row sm:items-center sm:justify-between">
        <div>
          <p class="text-sm text-neutral-400">当前套餐</p>
          <h3 class="text-xl font-bold text-neutral-800">专业版</h3>
          <p class="mt-1 text-sm text-success">下次续费: 2026-01-15 · ¥299/月</p>
        </div>
        <div class="mt-3 sm:mt-0">
          <button class="rounded-lg border border-danger px-4 py-2 text-sm text-danger hover:bg-danger/5">取消订阅</button>
        </div>
      </div>
      <!-- 使用进度 -->
      <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
        <div class="rounded-lg border border-neutral-100 p-3">
          <p class="text-xs text-neutral-400">存储空间</p>
          <div class="mt-1 h-2 w-full rounded-full bg-neutral-200">
            <div class="h-2 rounded-full bg-primary" style="width: 68%"></div>
          </div>
          <p class="mt-1 text-xs text-neutral-500">2.4 TB / 5 TB</p>
        </div>
        <div class="rounded-lg border border-neutral-100 p-3">
          <p class="text-xs text-neutral-400">上传流量</p>
          <div class="mt-1 h-2 w-full rounded-full bg-neutral-200">
            <div class="h-2 rounded-full bg-success" style="width: 45%"></div>
          </div>
          <p class="mt-1 text-xs text-neutral-500">450 GB / 1 TB</p>
        </div>
        <div class="rounded-lg border border-neutral-100 p-3">
          <p class="text-xs text-neutral-400">API 调用</p>
          <div class="mt-1 h-2 w-full rounded-full bg-neutral-200">
            <div class="h-2 rounded-full bg-warning" style="width: 82%"></div>
          </div>
          <p class="mt-1 text-xs text-neutral-500">8.2万 / 10万</p>
        </div>
      </div>
    </div>

    <!-- 套餐对比 -->
    <div class="grid grid-cols-1 gap-4 lg:grid-cols-4">
      <div v-for="plan in plans" :key="plan.key" :class="['responsive-panel p-5 relative', plan.current ? 'ring-2 ring-primary' : '']">
        <div v-if="plan.current" class="absolute -top-2 left-1/2 -translate-x-1/2 rounded-full bg-primary px-3 py-0.5 text-xs text-white">当前套餐</div>
        <h3 class="text-lg font-bold text-neutral-800">{{ plan.name }}</h3>
        <p class="mt-1 text-xs text-neutral-400">{{ plan.description }}</p>
        <div class="mt-4">
          <span class="text-3xl font-bold text-neutral-800">¥{{ plan.price }}</span>
          <span class="text-sm text-neutral-400">/月</span>
        </div>
        <ul class="mt-4 space-y-2">
          <li v-for="feature in plan.features" :key="feature" class="flex items-center gap-2 text-sm">
            <i class="fa fa-check text-success text-xs"></i>
            <span class="text-neutral-600">{{ feature }}</span>
          </li>
        </ul>
        <button
          :class="['mt-5 w-full rounded-lg py-2 text-sm font-medium transition', plan.current ? 'bg-neutral-100 text-neutral-400 cursor-default' : 'bg-primary text-white hover:bg-primary/90']"
          :disabled="plan.current"
        >
          {{ plan.current ? '当前套餐' : '立即升级' }}
        </button>
      </div>
    </div>

    <!-- 账单历史 -->
    <div class="responsive-panel overflow-hidden">
      <div class="flex items-center justify-between border-b border-neutral-100 px-4 py-3">
        <h3 class="text-base font-semibold text-neutral-700">账单历史</h3>
      </div>
      <div class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-neutral-100 bg-neutral-50/50">
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">账单编号</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">套餐</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">金额</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">状态</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">日期</th>
              <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-400">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="bill in bills" :key="bill.id" class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50">
              <td class="px-4 py-3 font-mono text-xs text-neutral-500">{{ bill.id }}</td>
              <td class="px-4 py-3 text-neutral-700">{{ bill.plan }}</td>
              <td class="px-4 py-3 text-neutral-700 font-medium">¥{{ bill.amount }}</td>
              <td class="px-4 py-3"><StatusBadge :status="bill.status" /></td>
              <td class="px-4 py-3 text-xs text-neutral-400">{{ bill.date }}</td>
              <td class="px-4 py-3 text-right">
                <button class="text-sm text-primary hover:underline"><i class="fa fa-download mr-1"></i>下载</button>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'

const plans = [
  {
    key: 'free', name: '免费版', description: '个人轻度使用', price: 0,
    features: ['10 GB 存储', '单文件最大 1 GB', '基础文件预览', '7 天回收站', '社区支持'],
    current: false,
  },
  {
    key: 'pro', name: '专业版', description: '专业用户首选', price: 299,
    features: ['5 TB 存储', '单文件最大 50 GB', '高级文件预览', '30 天回收站', '优先技术支持', 'API 访问', '团队协作'],
    current: true,
  },
  {
    key: 'business', name: '企业版', description: '团队与组织', price: 999,
    features: ['50 TB 存储', '无单文件限制', '全部预览功能', '90 天回收站', '专属客户经理', 'SSO 单点登录', '审计日志', '自定义品牌'],
    current: false,
  },
  {
    key: 'enterprise', name: '旗舰版', description: '大型企业定制', price: '联系客服',
    features: ['无限存储', '无限制', '全部功能', '永久回收站', '7x24 专属支持', '私有部署', 'SLA 保障', '定制开发'],
    current: false,
  },
]

const bills = [
  { id: 'INV-2026-005', plan: '专业版', amount: 299, status: 'paid', date: '2026-01-01' },
  { id: 'INV-2025-012', plan: '专业版', amount: 299, status: 'paid', date: '2025-12-01' },
  { id: 'INV-2025-011', plan: '专业版', amount: 299, status: 'paid', date: '2025-11-01' },
  { id: 'INV-2025-010', plan: '免费版', amount: 0, status: 'completed', date: '2025-10-01' },
]
</script>