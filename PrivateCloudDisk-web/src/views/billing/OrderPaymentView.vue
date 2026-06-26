<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="订单支付"
      description="选择支付方式完成订单支付"
      :breadcrumbs="[
        { label: '套餐管理', path: '/app/billing' },
        { label: '订单管理', path: '/app/billing/orders' },
        { label: '订单支付' }
      ]"
    />

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-2">
      <!-- 订单信息 -->
      <div class="responsive-panel p-6">
        <h3 class="mb-4 text-lg font-semibold text-neutral-800">订单信息</h3>

        <div v-if="orderLoading" class="flex items-center justify-center py-8">
          <i class="fa fa-spinner fa-spin text-2xl text-primary"></i>
        </div>

        <div v-else-if="order" class="space-y-4">
          <div class="rounded-lg bg-neutral-50 p-4">
            <div class="flex items-center justify-between">
              <span class="text-sm text-neutral-500">订单编号</span>
              <span class="font-mono text-sm text-neutral-700">{{ order.order_no }}</span>
            </div>
            <div class="mt-3 flex items-center justify-between">
              <span class="text-sm text-neutral-500">套餐名称</span>
              <span class="font-medium text-neutral-700">{{ order.plan_name }}</span>
            </div>
            <div class="mt-3 flex items-center justify-between">
              <span class="text-sm text-neutral-500">计费周期</span>
              <span class="text-neutral-700">{{ billingCycleText[order.plan_cycle] }}</span>
            </div>
          </div>

          <div class="rounded-lg border border-neutral-100 p-4">
            <div class="flex items-center justify-between">
              <span class="text-sm text-neutral-500">原价</span>
              <span class="text-neutral-700">¥{{ formatPrice(order.original_price) }}</span>
            </div>
            <div v-if="order.discount_amount > 0" class="mt-2 flex items-center justify-between">
              <span class="text-sm text-neutral-500">优惠</span>
              <span class="text-danger">-¥{{ formatPrice(order.discount_amount) }}</span>
            </div>
            <div class="mt-3 flex items-center justify-between border-t border-neutral-100 pt-3">
              <span class="text-base font-medium text-neutral-700">应付金额</span>
              <span class="text-2xl font-bold text-primary">¥{{ formatPrice(order.final_price) }}</span>
            </div>
          </div>

          <div class="text-xs text-neutral-400">
            <i class="fa fa-info-circle mr-1"></i>
            请在 <span class="font-medium">15分钟</span> 内完成支付，超时订单将自动取消。
          </div>
        </div>

        <div v-else class="py-8 text-center">
          <i class="fa fa-inbox text-4xl text-neutral-300"></i>
          <p class="mt-2 text-sm text-neutral-400">未找到订单信息</p>
          <button @click="$router.push('/app/billing/orders')" class="mt-4 text-sm text-primary hover:underline">
            返回订单列表
          </button>
        </div>
      </div>

      <!-- 支付方式 -->
      <div class="responsive-panel p-6">
        <h3 class="mb-4 text-lg font-semibold text-neutral-800">选择支付方式</h3>

        <!-- 测试模式提示 -->
        <div class="mb-4 rounded-lg bg-info/10 p-3">
          <p class="text-sm text-info">
            <i class="fa fa-info-circle mr-1"></i>
            <strong>演示模式：</strong>点击支付方式后将生成测试二维码，支付金额不会真实扣除。
          </p>
        </div>

        <!-- 支付渠道选择 -->
        <div class="mb-6 space-y-3">
          <button
            v-for="channel in paymentChannels"
            :key="channel.value"
            @click="selectChannel(channel.value)"
            :class="[
              'flex w-full items-center justify-between rounded-lg border-2 p-4 transition-all',
              selectedChannel === channel.value
                ? 'border-primary bg-primary/5'
                : 'border-neutral-200 hover:border-primary/50'
            ]"
          >
            <div class="flex items-center gap-3">
              <div :class="['text-2xl', channel.iconColor]">
                <i :class="channel.icon"></i>
              </div>
              <div class="text-left">
                <p class="font-medium text-neutral-800">{{ channel.label }}</p>
                <p class="text-xs text-neutral-400">{{ channel.description }}</p>
              </div>
            </div>
            <div v-if="selectedChannel === channel.value" class="text-primary">
              <i class="fa fa-check-circle text-xl"></i>
            </div>
          </button>
        </div>

        <!-- 二维码展示区域 -->
        <div class="rounded-lg border border-neutral-200 p-6">
          <div v-if="!selectedChannel" class="flex h-64 flex-col items-center justify-center text-neutral-400">
            <i class="fa fa-qrcode text-5xl"></i>
            <p class="mt-3">请选择支付方式</p>
          </div>

          <div v-else-if="paymentQRLoading" class="flex h-64 flex-col items-center justify-center">
            <i class="fa fa-spinner fa-spin text-3xl text-primary"></i>
            <p class="mt-3 text-sm text-neutral-500">正在生成二维码...</p>
          </div>

          <div v-else-if="paymentQR" class="text-center">
            <div class="mx-auto" style="width: 200px; height: 200px;">
              <img
                v-if="paymentQR.qr_code"
                :src="paymentQR.qr_code"
                alt="支付二维码"
                class="h-full w-full"
              />
              <div v-else class="flex h-full items-center justify-center bg-neutral-100">
                <i class="fa fa-qrcode text-4xl text-neutral-400"></i>
              </div>
            </div>
            <p class="mt-4 text-sm text-neutral-500">
              请使用{{ selectedChannel === 'wechat' ? '微信' : '支付宝' }}扫码支付
            </p>
            <p class="mt-1 text-lg font-medium text-primary">
              ¥{{ formatPrice(paymentQR.amount || order?.final_price || 0) }}
            </p>
            <p class="mt-2 text-xs text-neutral-400">
              二维码有效期：{{ formatExpireTime(paymentQR.expire_time) }}
            </p>

            <!-- 模拟支付成功按钮（演示用） -->
            <button
              @click="handleMockPaySuccess"
              class="mt-4 rounded-lg bg-success px-6 py-2 text-sm font-medium text-white transition hover:bg-success/90"
            >
              <i class="fa fa-check mr-1"></i>模拟支付成功（演示）
            </button>
          </div>

          <div v-else class="flex h-64 flex-col items-center justify-center text-neutral-400">
            <i class="fa fa-exclamation-triangle text-4xl"></i>
            <p class="mt-3">生成二维码失败，请重试</p>
            <button @click="generateQR" class="mt-3 text-sm text-primary hover:underline">
              重新生成
            </button>
          </div>
        </div>

        <!-- 返回按钮 -->
        <div class="mt-4 text-center">
          <button @click="$router.push('/app/billing/orders')" class="text-sm text-neutral-500 hover:text-neutral-700">
            <i class="fa fa-arrow-left mr-1"></i>返回订单列表
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { useBillingStore } from '@/stores/billingStore'
import { formatPrice, billingCycleText, type PaymentChannel, type Order } from '@/api/modules/billing'

const router = useRouter()
const route = useRoute()
const billingStore = useBillingStore()

// 状态
const selectedChannel = ref<PaymentChannel | ''>('')
const order = ref<Order | null>(null)
const orderLoading = ref(false)
const paymentQRLoading = computed(() => billingStore.paymentQRLoading)
const paymentQR = computed(() => billingStore.paymentQR)

// 支付渠道配置
const paymentChannels = [
  {
    value: 'wechat' as PaymentChannel,
    label: '微信支付',
    icon: 'fa fa-weixin',
    iconColor: 'text-[#07c160]',
    description: '使用微信扫描二维码支付',
  },
  {
    value: 'alipay' as PaymentChannel,
    label: '支付宝',
    icon: 'fa fa-alipay',
    iconColor: 'text-[#1677ff]',
    description: '使用支付宝扫描二维码支付',
  },
]

// 加载订单
async function loadOrder() {
  const orderNo = route.query.orderNo as string
  if (!orderNo) return

  orderLoading.value = true
  try {
    // 实际应调用 API 获取订单详情
    // 这里模拟一个订单
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
      status: 'pending_payment',
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
  } finally {
    orderLoading.value = false
  }
}

// 选择支付渠道
async function selectChannel(channel: PaymentChannel) {
  selectedChannel.value = channel
  await generateQR()
}

// 生成二维码
async function generateQR() {
  if (!selectedChannel.value || !order.value) return

  // 创建测试订单并获取二维码
  await billingStore.createTestOrder(order.value.plan_id, selectedChannel.value)
}

// 模拟支付成功
async function handleMockPaySuccess() {
  if (!paymentQR.value?.order_no) return

  const success = await billingStore.mockPaySuccess(paymentQR.value.order_no)
  if (success) {
    // 停止轮询
    billingStore.clearPaymentPolling()
    // 跳转到支付成功页面
    router.push({
      path: '/app/billing/payment/success',
      query: { orderNo: order.value?.order_no },
    })
  }
}

// 格式化过期时间
function formatExpireTime(expireTime: string): string {
  if (!expireTime) return '15:00'
  const date = new Date(expireTime)
  const now = new Date()
  const diff = Math.max(0, Math.floor((date.getTime() - now.getTime()) / 1000))
  const minutes = Math.floor(diff / 60)
  const seconds = diff % 60
  return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`
}

// 组件卸载时停止轮询
onUnmounted(() => {
  billingStore.clearPaymentPolling()
})

// 初始化
onMounted(() => {
  loadOrder()
})
</script>
