<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="支付成功"
      description="订单支付已完成"
      :breadcrumbs="[
        { label: '套餐管理', path: '/app/billing' },
        { label: '订单管理', path: '/app/billing/orders' },
        { label: '支付成功' }
      ]"
    />

    <!-- 成功提示 -->
    <div class="responsive-panel p-8 text-center">
      <div class="mx-auto mb-6 flex h-20 w-20 items-center justify-center rounded-full bg-success/10">
        <i class="fa fa-check-circle text-5xl text-success"></i>
      </div>

      <h2 class="text-2xl font-bold text-neutral-800">支付成功！</h2>
      <p class="mt-2 text-neutral-500">您的订单已支付成功，套餐已开通</p>

      <!-- 订单信息 -->
      <div v-if="order" class="mx-auto mt-6 max-w-md rounded-lg bg-neutral-50 p-4 text-left">
        <div class="flex items-center justify-between">
          <span class="text-sm text-neutral-500">订单编号</span>
          <span class="font-mono text-sm text-neutral-700">{{ order.order_no }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">套餐名称</span>
          <span class="font-medium text-neutral-700">{{ order.plan_name }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">支付金额</span>
          <span class="font-bold text-success">¥{{ formatPrice(order.final_price) }}</span>
        </div>
        <div class="mt-3 flex items-center justify-between">
          <span class="text-sm text-neutral-500">支付方式</span>
          <span class="text-neutral-700">{{ channelText }}</span>
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
      <h4 class="mb-3 text-sm font-semibold text-neutral-700">温馨提示</h4>
      <ul class="space-y-2 text-sm text-neutral-500">
        <li class="flex items-start gap-2">
          <i class="fa fa-check-circle text-success mt-0.5"></i>
          <span>支付成功后，套餐将立即生效，您可以开始使用新功能</span>
        </li>
        <li class="flex items-start gap-2">
          <i class="fa fa-check-circle text-success mt-0.5"></i>
          <span>您可以在订单管理中查看订单详情和下载发票</span>
        </li>
        <li class="flex items-start gap-2">
          <i class="fa fa-check-circle text-success mt-0.5"></i>
          <span>如需退款，可在订单详情页申请退款，审核通过后将原路退回</span>
        </li>
      </ul>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { formatPrice, paymentChannelText, type Order } from '@/api/modules/billing'

const route = useRoute()

// 订单信息
const order = ref<Order | null>(null)

const channelText = computed(() => {
  if (!order.value?.payment_channel) return '-'
  return paymentChannelText[order.value.payment_channel] || order.value.payment_channel
})

// 初始化
onMounted(() => {
  const orderNo = route.query.orderNo as string
  if (orderNo) {
    order.value = {
      order_id: '1',
      order_no: orderNo,
      user_id: '1',
      user_name: '测试用户',
      user_email: 'test@example.com',
      plan_id: 'pro',
      plan_name: '专业版',
      plan_cycle: 'monthly',
      original_price: 29900,
      discount_amount: 0,
      final_price: 29900,
      status: 'paid',
      payment_channel: 'wechat',
      payment_time: new Date().toISOString(),
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
  }
})
</script>
