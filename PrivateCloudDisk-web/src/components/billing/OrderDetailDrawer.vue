<template>
  <!-- 订单详情抽屉 -->
  <Teleport to="body">
    <Transition name="drawer-fade">
      <div v-if="visible" class="drawer-overlay" @click.self="handleClose">
        <Transition name="drawer-slide">
          <div v-if="visible" class="drawer-panel" :style="{ width: '480px', maxWidth: '100vw' }">
            <!-- 头部 -->
            <div class="drawer-header">
              <h3 class="drawer-title">订单详情</h3>
              <button class="drawer-close" @click="handleClose">
                <i class="fa fa-times"></i>
              </button>
            </div>

            <!-- 加载状态 -->
            <div v-if="loading" class="drawer-loading">
              <i class="fa fa-spinner fa-spin text-2xl text-primary"></i>
              <p class="mt-2 text-sm text-neutral-400">加载中...</p>
            </div>

            <!-- 内容 -->
            <div v-else-if="order" class="drawer-content">
              <!-- 订单状态 -->
              <div class="status-banner" :class="statusBannerClass">
                <i :class="statusIcon"></i>
                <span>{{ statusText }}</span>
              </div>

              <!-- 基本信息 -->
              <div class="info-section">
                <h4 class="section-title">基本信息</h4>
                <div class="info-grid">
                  <div class="info-item">
                    <label>订单编号</label>
                    <span class="font-mono text-xs">{{ order.order_no }}</span>
                  </div>
                  <div class="info-item">
                    <label>下单时间</label>
                    <span>{{ formatDate(order.created_at) }}</span>
                  </div>
                  <div class="info-item">
                    <label>套餐名称</label>
                    <span>{{ order.plan_name }}</span>
                  </div>
                  <div class="info-item">
                    <label>计费周期</label>
                    <span>{{ billingCycleText[order.plan_cycle] || order.plan_cycle }}</span>
                  </div>
                </div>
              </div>

              <!-- 金额信息 -->
              <div class="info-section">
                <h4 class="section-title">金额信息</h4>
                <div class="info-grid">
                  <div class="info-item">
                    <label>原价</label>
                    <span>¥{{ formatPrice(order.original_price) }}</span>
                  </div>
                  <div class="info-item">
                    <label>优惠</label>
                    <span class="text-danger">-¥{{ formatPrice(order.discount_amount) }}</span>
                  </div>
                  <div class="info-item col-span-2">
                    <label>实付金额</label>
                    <span class="text-xl font-bold text-primary">¥{{ formatPrice(order.final_price) }}</span>
                  </div>
                </div>
              </div>

              <!-- 支付信息 -->
              <div v-if="order.payment_channel" class="info-section">
                <h4 class="section-title">支付信息</h4>
                <div class="info-grid">
                  <div class="info-item">
                    <label>支付方式</label>
                    <span>{{ paymentChannelText[order.payment_channel] || order.payment_channel }}</span>
                  </div>
                  <div v-if="order.payment_time" class="info-item">
                    <label>支付时间</label>
                    <span>{{ formatDate(order.payment_time) }}</span>
                  </div>
                  <div v-if="order.transaction_id" class="info-item col-span-2">
                    <label>交易流水号</label>
                    <span class="font-mono text-xs">{{ order.transaction_id }}</span>
                  </div>
                </div>
              </div>

              <!-- 退款信息 -->
              <div v-if="order.status === 'refund_pending' || order.status === 'refunded' || order.status === 'refund_rejected'" class="info-section">
                <h4 class="section-title">退款信息</h4>
                <div class="info-grid">
                  <div v-if="order.refund_reason" class="info-item col-span-2">
                    <label>退款原因</label>
                    <span>{{ order.refund_reason }}</span>
                  </div>
                  <div v-if="order.refund_time" class="info-item">
                    <label>退款时间</label>
                    <span>{{ formatDate(order.refund_time) }}</span>
                  </div>
                  <div v-if="order.refund_amount" class="info-item">
                    <label>退款金额</label>
                    <span class="text-warning">¥{{ formatPrice(order.refund_amount) }}</span>
                  </div>
                  <div v-if="order.refund_reject_reason" class="info-item col-span-2">
                    <label>拒绝原因</label>
                    <span class="text-danger">{{ order.refund_reject_reason }}</span>
                  </div>
                </div>
              </div>

              <!-- 有效期信息 -->
              <div v-if="order.expire_time" class="info-section">
                <h4 class="section-title">有效期</h4>
                <div class="info-grid">
                  <div class="info-item col-span-2">
                    <label>到期时间</label>
                    <span :class="isExpiringSoon ? 'text-warning' : ''">{{ formatDate(order.expire_time) }}</span>
                  </div>
                </div>
              </div>

              <!-- 操作按钮 -->
              <div class="drawer-actions">
                <button v-if="order.status === 'pending_payment'" class="btn btn-primary" @click="handlePay">
                  <i class="fa fa-credit-card mr-1"></i>立即支付
                </button>
                <button v-if="order.status === 'pending_payment'" class="btn btn-default" @click="handleCancel">
                  <i class="fa fa-times mr-1"></i>取消订单
                </button>
                <button
                  v-if="canRefund"
                  class="btn btn-danger"
                  @click="handleRefund"
                >
                  <i class="fa fa-undo mr-1"></i>申请退款
                </button>
                <button class="btn btn-default" @click="handleClose">
                  <i class="fa fa-check mr-1"></i>关闭
                </button>
              </div>
            </div>

            <!-- 空状态 -->
            <div v-else class="drawer-empty">
              <i class="fa fa-inbox text-4xl text-neutral-300"></i>
              <p class="mt-2 text-sm text-neutral-400">未找到订单信息</p>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, watch } from 'vue'
import type { Order, OrderStatus, PaymentChannel } from '@/api/modules/billing'
import { formatPrice, orderStatusText, paymentChannelText, billingCycleText } from '@/api/modules/billing'

const props = defineProps<{
  visible: boolean
  orderId: string | null
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'pay', order: Order): void
  (e: 'cancel', order: Order): void
  (e: 'refund', order: Order): void
}>()

// 由于此组件为展示组件，订单数据由父组件传入
// 如需独立获取，可在此处调用 API
const order = defineModel<Order | null>('order', { default: null })
const loading = defineModel<boolean>('loading', { default: false })

// 状态文本和样式
const statusText = computed(() => {
  if (!order.value) return ''
  return orderStatusText[order.value.status] || order.value.status
})

const statusBannerClass = computed(() => {
  if (!order.value) return ''
  const statusClasses: Record<OrderStatus, string> = {
    pending_payment: 'bg-warning/10 text-warning border-warning/20',
    paid: 'bg-success/10 text-success border-success/20',
    processing: 'bg-primary/10 text-primary border-primary/20',
    cancelled: 'bg-neutral/10 text-neutral border-neutral/20',
    refund_pending: 'bg-warning/10 text-warning border-warning/20',
    refunded: 'bg-info/10 text-info border-info/20',
    refund_rejected: 'bg-danger/10 text-danger border-danger/20',
    expired: 'bg-neutral/10 text-neutral border-neutral/20',
    failed: 'bg-danger/10 text-danger border-danger/20',
  }
  return statusClasses[order.value.status] || ''
})

const statusIcon = computed(() => {
  if (!order.value) return ''
  const statusIcons: Record<OrderStatus, string> = {
    pending_payment: 'fa fa-clock-o',
    paid: 'fa fa-check-circle',
    processing: 'fa fa-spinner fa-spin',
    cancelled: 'fa fa-ban',
    refund_pending: 'fa fa-undo',
    refunded: 'fa fa-check-circle-o',
    refund_rejected: 'fa fa-exclamation-circle',
    expired: 'fa fa-clock-o',
    failed: 'fa fa-times-circle',
  }
  return statusIcons[order.value.status] || 'fa fa-circle'
})

// 是否可以退款（已支付且未完成的订单可以退款）
const canRefund = computed(() => {
  if (!order.value) return false
  return order.value.status === 'paid'
})

// 是否即将到期（7天内）
const isExpiringSoon = computed(() => {
  if (!order.value?.expire_time) return false
  const expireDate = new Date(order.value.expire_time)
  const now = new Date()
  const diffDays = (expireDate.getTime() - now.getTime()) / (1000 * 60 * 60 * 24)
  return diffDays > 0 && diffDays <= 7
})

// 格式化日期
function formatDate(dateStr: string): string {
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

// 关闭抽屉
function handleClose() {
  emit('update:visible', false)
}

// 支付
function handlePay() {
  if (order.value) {
    emit('pay', order.value)
  }
}

// 取消订单
function handleCancel() {
  if (order.value) {
    emit('cancel', order.value)
  }
}

// 申请退款
function handleRefund() {
  if (order.value) {
    emit('refund', order.value)
  }
}
</script>

<style scoped>
.drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.4);
}

.drawer-panel {
  position: fixed;
  top: 0;
  right: 0;
  height: 100vh;
  background: white;
  box-shadow: -4px 0 24px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
}

.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid #e5e5e5;
}

.drawer-title {
  font-size: 16px;
  font-weight: 600;
  color: #1a1a1a;
}

.drawer-close {
  width: 32px;
  height: 32px;
  border: none;
  background: transparent;
  cursor: pointer;
  border-radius: 6px;
  color: #666;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.2s;
}

.drawer-close:hover {
  background: #f5f5f5;
  color: #333;
}

.drawer-loading,
.drawer-empty {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.drawer-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px 20px;
}

.status-banner {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  border-radius: 8px;
  border: 1px solid;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.info-section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 13px;
  font-weight: 600;
  color: #666;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f0f0f0;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item label {
  font-size: 12px;
  color: #999;
}

.info-item span {
  font-size: 14px;
  color: #333;
}

.col-span-2 {
  grid-column: span 2;
}

.drawer-actions {
  display: flex;
  gap: 8px;
  padding: 16px 0;
  border-top: 1px solid #e5e5e5;
  margin-top: auto;
}

.btn {
  flex: 1;
  padding: 10px 16px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-primary {
  background: #3b82f6;
  color: white;
}

.btn-primary:hover {
  background: #2563eb;
}

.btn-danger {
  background: #ef4444;
  color: white;
}

.btn-danger:hover {
  background: #dc2626;
}

.btn-default {
  background: #f5f5f5;
  color: #666;
}

.btn-default:hover {
  background: #e5e5e5;
}

/* 过渡动画 */
.drawer-fade-enter-active,
.drawer-fade-leave-active {
  transition: opacity 0.3s ease;
}

.drawer-fade-enter-from,
.drawer-fade-leave-to {
  opacity: 0;
}

.drawer-slide-enter-active,
.drawer-slide-leave-active {
  transition: transform 0.3s ease;
}

.drawer-slide-enter-from,
.drawer-slide-leave-to {
  transform: translateX(100%);
}
</style>
