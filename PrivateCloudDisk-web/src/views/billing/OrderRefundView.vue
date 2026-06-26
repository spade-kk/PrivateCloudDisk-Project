<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="申请退款"
      description="提交退款申请，我们将尽快处理"
      :breadcrumbs="[
        { label: '套餐管理', path: '/app/billing' },
        { label: '订单管理', path: '/app/billing/orders' },
        { label: '申请退款' }
      ]"
    />

    <div class="grid grid-cols-1 gap-6 lg:grid-cols-3">
      <!-- 左侧：订单信息 -->
      <div class="lg:col-span-1">
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
              <div class="mt-3 flex items-center justify-between">
                <span class="text-sm text-neutral-500">支付金额</span>
                <span class="font-bold text-primary">¥{{ formatPrice(order.final_price) }}</span>
              </div>
              <div class="mt-3 flex items-center justify-between">
                <span class="text-sm text-neutral-500">支付时间</span>
                <span class="text-neutral-700">{{ formatDate(order.payment_time) }}</span>
              </div>
            </div>

            <!-- 退款提示 -->
            <div class="rounded-lg border border-warning/20 bg-warning/5 p-4">
              <div class="flex items-start gap-3">
                <i class="fa fa-exclamation-triangle text-warning mt-0.5"></i>
                <div class="text-sm">
                  <p class="font-medium text-warning">退款说明</p>
                  <ul class="mt-2 space-y-1 text-neutral-600">
                    <li>• 仅已支付且未过期的订单可以申请退款</li>
                    <li>• 退款将在 1-7 个工作日内原路退回</li>
                    <li>• 已使用部分服务将按比例扣除</li>
                  </ul>
                </div>
              </div>
            </div>
          </div>

          <div v-else class="py-8 text-center">
            <i class="fa fa-inbox text-4xl text-neutral-300"></i>
            <p class="mt-2 text-sm text-neutral-400">未找到订单信息</p>
          </div>
        </div>
      </div>

      <!-- 右侧：退款表单 -->
      <div class="lg:col-span-2">
        <div class="responsive-panel p-6">
          <h3 class="mb-4 text-lg font-semibold text-neutral-800">退款申请</h3>

          <form @submit.prevent="handleSubmit" class="space-y-6">
            <!-- 退款原因 -->
            <div>
              <label class="block text-sm font-medium text-neutral-700">
                退款原因 <span class="text-danger">*</span>
              </label>
              <textarea
                v-model="refundReason"
                rows="4"
                placeholder="请详细描述退款原因，我们将尽快为您处理..."
                class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none"
                :class="{ 'border-danger': submitted && !refundReason.trim() }"
              ></textarea>
              <p v-if="submitted && !refundReason.trim()" class="mt-1 text-xs text-danger">
                请填写退款原因
              </p>
            </div>

            <!-- 退款金额确认 -->
            <div class="rounded-lg border border-neutral-200 p-4">
              <div class="flex items-center justify-between">
                <span class="text-sm text-neutral-500">订单支付金额</span>
                <span class="font-medium text-neutral-700">¥{{ formatPrice(order?.final_price || 0) }}</span>
              </div>
              <div class="mt-2 flex items-center justify-between">
                <span class="text-sm text-neutral-500">预计退款金额</span>
                <span class="text-lg font-bold text-warning">¥{{ formatPrice(order?.final_price || 0) }}</span>
              </div>
              <p class="mt-2 text-xs text-neutral-400">
                <i class="fa fa-info-circle mr-1"></i>
                实际退款金额以审核结果为准
              </p>
            </div>

            <!-- 退款方式 -->
            <div>
              <label class="block text-sm font-medium text-neutral-700">
                退款方式
              </label>
              <div class="mt-2 flex items-center gap-3">
                <div class="flex items-center gap-2 rounded-lg border border-primary bg-primary/5 px-4 py-2">
                  <i class="fa fa-exchange text-primary"></i>
                  <span class="text-sm text-primary">原路退回</span>
                </div>
                <span class="text-xs text-neutral-400">退款将原路返回至您的支付账户</span>
              </div>
            </div>

            <!-- 提示信息 -->
            <div class="rounded-lg bg-neutral-50 p-4">
              <h4 class="text-sm font-medium text-neutral-700">退款流程</h4>
              <div class="mt-3 space-y-2">
                <div class="flex items-center gap-3">
                  <div class="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs text-primary">1</div>
                  <span class="text-sm text-neutral-600">提交退款申请</span>
                </div>
                <div class="ml-3 border-l-2 border-neutral-200 py-1 pl-3">
                  <p class="text-xs text-neutral-400">我们将在 24 小时内审核您的申请</p>
                </div>
                <div class="flex items-center gap-3">
                  <div class="flex h-6 w-6 items-center justify-center rounded-full bg-primary/10 text-xs text-primary">2</div>
                  <span class="text-sm text-neutral-600">审核通过后退款处理</span>
                </div>
                <div class="ml-3 border-l-2 border-neutral-200 py-1 pl-3">
                  <p class="text-xs text-neutral-400">退款将原路返回，预计 1-7 个工作日到账</p>
                </div>
              </div>
            </div>

            <!-- 提交按钮 -->
            <div class="flex items-center gap-4">
              <button
                type="submit"
                :disabled="submitting"
                class="rounded-lg bg-danger px-6 py-2.5 text-sm font-medium text-white hover:bg-danger/90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <i v-if="submitting" class="fa fa-spinner fa-spin mr-1"></i>
                {{ submitting ? '提交中...' : '提交退款申请' }}
              </button>
              <button
                type="button"
                @click="$router.push('/app/billing/orders')"
                class="rounded-lg border border-neutral-200 px-6 py-2.5 text-sm font-medium text-neutral-600 hover:bg-neutral-50"
              >
                取消
              </button>
            </div>
          </form>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import { useBillingStore } from '@/stores/billingStore'
import { formatPrice, billingCycleText, type Order } from '@/api/modules/billing'

const router = useRouter()
const route = useRoute()
const billingStore = useBillingStore()

// 状态
const order = ref<Order | null>(null)
const orderLoading = ref(false)
const refundReason = ref('')
const submitting = ref(false)
const submitted = ref(false)

// 加载订单
async function loadOrder() {
  const orderId = route.query.orderId as string
  if (!orderId) return

  orderLoading.value = true
  try {
    // 模拟订单数据
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
      status: 'paid',
      payment_channel: 'wechat',
      payment_time: new Date().toISOString(),
      created_at: new Date().toISOString(),
      updated_at: new Date().toISOString(),
    }
  } finally {
    orderLoading.value = false
  }
}

// 提交退款申请
async function handleSubmit() {
  submitted.value = true
  if (!refundReason.value.trim()) return

  submitting.value = true
  try {
    const orderId = order.value?.order_id
    if (!orderId) return

    const success = await billingStore.refundOrder(orderId, refundReason.value)
    if (success) {
      router.push({
        path: '/app/billing/refund/success',
        query: { orderId, reason: refundReason.value },
      })
    }
  } finally {
    submitting.value = false
  }
}

// 格式化日期
function formatDate(dateStr?: string): string {
  if (!dateStr) return '-'
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
  loadOrder()
})
</script>
