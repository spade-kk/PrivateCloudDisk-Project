<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="退款申请已提交"
      description="我们将在 24 小时内处理您的退款请求"
      :breadcrumbs="[
        { label: '套餐管理', path: '/app/billing' },
        { label: '订单管理', path: '/app/billing/orders' },
        { label: '退款申请' }
      ]"
    />

    <!-- 成功提示 -->
    <div class="responsive-panel p-8 text-center">
      <div class="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-warning/10">
        <i class="fa fa-check-circle text-5xl text-warning"></i>
      </div>

      <h2 class="text-2xl font-bold text-neutral-800">退款申请已提交！</h2>
      <p class="mt-2 text-neutral-500">您的退款申请已收到，我们将在 24 小时内审核处理</p>

      <!-- 订单信息 -->
      <div v-if="order" class="mx-auto mt-6 max-w-md rounded-lg bg-neutral-50 p-4 text-left">
        <div class="flex items-center justify-between">
          <span class="text-sm text-neutral-500">订单编号</span>
          <span class="font-mono text-sm text-neutral-700">{{ order.order_no }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">退款原因</span>
          <span class="text-sm text-neutral-700">{{ refundReason || '-' }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">退款金额</span>
          <span class="font-bold text-warning">¥{{ formatPrice(order.final_price) }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">退款方式</span>
          <span class="text-neutral-700">原路退回</span>
        </div>
      </div>

      <!-- 退款进度 -->
      <div class="mx-auto mt-6 max-w-md">
        <div class="relative">
          <!-- 时间线 -->
          <div class="absolute left-4 top-0 h-full w-0.5 bg-neutral-200"></div>

          <div class="relative space-y-6">
            <!-- 步骤1：提交申请 -->
            <div class="flex items-start gap-4">
              <div class="relative z-10 flex h-8 w-8 items-center justify-center rounded-full bg-success text-white">
                <i class="fa fa-check text-sm"></i>
              </div>
              <div class="pt-1">
                <p class="text-sm font-medium text-neutral-700">提交退款申请</p>
                <p class="text-xs text-neutral-400">{{ formatDateTime(new Date().toISOString()) }}</p>
              </div>
            </div>

            <!-- 步骤2：审核中 -->
            <div class="flex items-start gap-4">
              <div class="relative z-10 flex h-8 w-8 items-center justify-center rounded-full bg-primary text-white">
                <i class="fa fa-spinner fa-spin text-sm"></i>
              </div>
              <div class="pt-1">
                <p class="text-sm font-medium text-neutral-700">审核中</p>
                <p class="text-xs text-neutral-400">预计 1-24 小时内完成</p>
              </div>
            </div>

            <!-- 步骤3：退款处理 -->
            <div class="flex items-start gap-4">
              <div class="flex h-8 w-8 items-center justify-center rounded-full bg-neutral-200 text-neutral-400">
                <i class="fa fa-exchange text-sm"></i>
              </div>
              <div class="pt-1">
                <p class="text-sm font-medium text-neutral-400">退款处理</p>
                <p class="text-xs text-neutral-400">审核通过后退款原路返回</p>
              </div>
            </div>

            <!-- 步骤4：完成 -->
            <div class="flex items-start gap-4">
              <div class="flex h-8 w-8 items-center justify-center rounded-full bg-neutral-200 text-neutral-400">
                <i class="fa fa-check text-sm"></i>
              </div>
              <div class="pt-1">
                <p class="text-sm font-medium text-neutral-400">退款完成</p>
                <p class="text-xs text-neutral-400">预计 1-7 个工作日到账</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 操作按钮 -->
      <div class="mt-8 flex justify-center gap-4">
        <button
          @click="$router.push('/app/billing/orders')"
          class="rounded-lg border border-neutral-200 px-6 py-2.5 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
        >
          <i class="fa fa-list mr-1"></i>查看订单
        </button>
        <button
          @click="$router.push('/app/billing')"
          class="rounded-lg bg-primary px-6 py-2.5 text-sm font-medium text-white hover:bg-primary/90"
        >
          <i class="fa fa-cube mr-1"></i>返回套餐
        </button>
      </div>
    </div>

    <!-- 温馨提示 -->
    <div class="responsive-panel p-4">
      <h4 class="mb-3 text-sm font-semibold text-neutral-700">退款说明</h4>
      <ul class="space-y-2 text-sm text-neutral-500">
        <li class="flex items-start gap-2">
          <i class="fa fa-info-circle text-info mt-0.5"></i>
          <span>退款申请提交后，我们将在 24 小时内完成审核</span>
        </li>
        <li class="flex items-start gap-2">
          <i class="fa fa-info-circle text-info mt-0.5"></i>
          <span>审核通过后，退款将在 1-7 个工作日内原路返回至您的支付账户</span>
        </li>
        <li class="flex items-start gap-2">
          <i class="fa fa-info-circle text-info mt-0.5"></i>
          <span>如有疑问，请联系客服：400-xxx-xxxx</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { formatPrice, type Order } from '@/api/modules/billing'

const route = useRoute()

// 状态
const order = ref<Order | null>(null)
const refundReason = ref('')

// 格式化日期时间
function formatDateTime(dateStr: string): string {
  const date = new Date(dateStr)
  return date.toLocaleString('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

// 初始化
onMounted(() => {
  const orderId = route.query.orderId as string
  refundReason.value = route.query.reason as string || ''

  if (orderId) {
    order.value = {
      order_id: orderId,
      order_no: 'ORD' + Date.now(),
      user_id: '1',
      user_name: '测试用户',
      user_email: 'test@example.com',
      plan_id: 'pro',
      plan_name: '专业版',
      plan_cycle: 'monthly',
      original_price: 29900,
      discount_amount: 0,
      final_price: 29900,
      status: 'refund_pending',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
  }
})
</script>
