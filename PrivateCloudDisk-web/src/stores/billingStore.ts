// ============================================================
// billingStore.ts — 订阅与订单状态管理
// ============================================================
// 使用 Pinia 管理订阅计划、订单、支付等全局状态，
// 供套餐管理、订单管理、支付等页面使用。
// ============================================================

import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { useToastStore } from './toastStore'
import {
  getSubscriptionPlansApi,
  getOrdersApi,
  getOrderDetailApi,
  createOrderApi,
  cancelOrderApi,
  refundOrderApi,
  getWechatPayQRApi,
  getAlipayQRApi,
  queryPaymentStatusApi,
  getCurrentSubscriptionApi,
  createTestOrderApi,
  mockPaySuccessApi,
  type SubscriptionPlan,
  type Order,
  type OrderStatus,
  type PaymentChannel,
  type PaymentQRResponse,
  type CurrentSubscription,
} from '@/api/modules/billing'

export const useBillingStore = defineStore('billing', () => {
  // ============================================================
  // 状态定义
  // ============================================================

  /** 订阅计划列表 */
  const plans = ref<SubscriptionPlan[]>([])
  const plansLoading = ref(false)

  /** 当前订阅信息 */
  const currentSubscription = ref<CurrentSubscription | null>(null)
  const subscriptionLoading = ref(false)

  /** 订单列表 */
  const orders = ref<Order[]>([])
  const ordersTotal = ref(0)
  const ordersPage = ref(1)
  const ordersPageSize = ref(10)
  const ordersLoading = ref(false)
  const ordersStatusFilter = ref<OrderStatus | ''>('')

  /** 当前选中订单 */
  const currentOrder = ref<Order | null>(null)
  const currentOrderLoading = ref(false)

  /** 支付二维码 */
  const paymentQR = ref<PaymentQRResponse | null>(null)
  const paymentQRLoading = ref(false)

  /** 支付轮询定时器 */
  let paymentPollingTimer: ReturnType<typeof setInterval> | null = null

  // ============================================================
  // 计算属性
  // ============================================================

  /** 待支付订单数 */
  const pendingPaymentCount = computed(() =>
    orders.value.filter((o) => o.status === 'pending_payment').length
  )

  /** 退款中订单数 */
  const refundPendingCount = computed(() =>
    orders.value.filter((o) => o.status === 'refund_pending').length
  )

  /** 订单状态统计 */
  const orderStatusStats = computed(() => {
    const stats: Record<OrderStatus, number> = {
      pending_payment: 0,
      paid: 0,
      processing: 0,
      cancelled: 0,
      refund_pending: 0,
      refunded: 0,
      refund_rejected: 0,
      expired: 0,
      failed: 0,
    }
    orders.value.forEach((o) => {
      if (stats[o.status] !== undefined) {
        stats[o.status]++
      }
    })
    return stats
  })

  // ============================================================
  // 动作方法
  // ============================================================

  /** 获取订阅计划列表 */
  async function fetchPlans(params?: { billing_cycle?: string; is_active?: boolean }) {
    const toast = useToastStore()
    plansLoading.value = true
    try {
      const res = await getSubscriptionPlansApi(params)
      if (res.code === 200 && res.data) {
        plans.value = res.data
      }
    } catch (error: any) {
      console.error('获取订阅计划失败:', error)
      toast.showToast('获取订阅计划失败', 'error')
    } finally {
      plansLoading.value = false
    }
  }

  /** 获取当前订阅信息 */
  async function fetchCurrentSubscription() {
    const toast = useToastStore()
    subscriptionLoading.value = true
    try {
      const res = await getCurrentSubscriptionApi()
      if (res.code === 200) {
        currentSubscription.value = res.data
      }
    } catch (error: any) {
      console.error('获取当前订阅失败:', error)
      toast.showToast('获取当前订阅失败', 'error')
    } finally {
      subscriptionLoading.value = false
    }
  }

  /** 获取订单列表 */
  async function fetchOrders(params?: {
    page?: number
    page_size?: number
    status?: OrderStatus | ''
    keyword?: string
    start_date?: string
    end_date?: string
  }) {
    const toast = useToastStore()
    ordersLoading.value = true
    try {
      if (params?.page) ordersPage.value = params.page
      if (params?.page_size) ordersPageSize.value = params.page_size
      if (params?.status !== undefined) ordersStatusFilter.value = params.status

      const res = await getOrdersApi({
        page: ordersPage.value,
        page_size: ordersPageSize.value,
        status: ordersStatusFilter.value,
        keyword: params?.keyword,
        start_date: params?.start_date,
        end_date: params?.end_date,
      })
      if (res.code === 200 && res.data) {
        orders.value = res.data.records || res.data.list || []
        ordersTotal.value = res.data.total || 0
      }
    } catch (error: any) {
      console.error('获取订单列表失败:', error)
      toast.showToast('获取订单列表失败', 'error')
    } finally {
      ordersLoading.value = false
    }
  }

  /** 获取订单详情 */
  async function fetchOrderDetail(orderId: string) {
    const toast = useToastStore()
    currentOrderLoading.value = true
    try {
      const res = await getOrderDetailApi(orderId)
      if (res.code === 200 && res.data) {
        currentOrder.value = res.data
      }
      return res.data
    } catch (error: any) {
      console.error('获取订单详情失败:', error)
      toast.showToast('获取订单详情失败', 'error')
      return null
    } finally {
      currentOrderLoading.value = false
    }
  }

  /** 创建订单 */
  async function createOrder(data: {
    plan_id: string
    billing_cycle: 'monthly' | 'quarterly' | 'yearly'
    payment_channel: PaymentChannel
    coupon_code?: string
  }): Promise<Order | null> {
    const toast = useToastStore()
    try {
      const res = await createOrderApi(data)
      if (res.code === 200 && res.data) {
        return res.data
      }
      toast.showToast(res.message || '创建订单失败', 'error')
      return null
    } catch (error: any) {
      console.error('创建订单失败:', error)
      toast.showToast('创建订单失败', 'error')
      return null
    }
  }

  /** 取消订单 */
  async function cancelOrder(orderId: string): Promise<boolean> {
    const toast = useToastStore()
    try {
      const res = await cancelOrderApi(orderId)
      if (res.code === 200) {
        toast.showToast('订单已取消', 'success')
        await fetchOrders()
        return true
      }
      toast.showToast(res.message || '取消订单失败', 'error')
      return false
    } catch (error: any) {
      console.error('取消订单失败:', error)
      toast.showToast('取消订单失败', 'error')
      return false
    }
  }

  /** 申请退款 */
  async function refundOrder(orderId: string, reason: string): Promise<boolean> {
    const toast = useToastStore()
    try {
      const res = await refundOrderApi(orderId, reason)
      if (res.code === 200) {
        toast.showToast('退款申请已提交', 'success')
        await fetchOrders()
        return true
      }
      toast.showToast(res.message || '退款申请失败', 'error')
      return false
    } catch (error: any) {
      console.error('申请退款失败:', error)
      toast.showToast('申请退款失败', 'error')
      return false
    }
  }

  /** 获取微信支付二维码 */
  async function fetchWechatPayQR(orderNo: string): Promise<PaymentQRResponse | null> {
    const toast = useToastStore()
    paymentQRLoading.value = true
    try {
      const res = await getWechatPayQRApi(orderNo)
      if (res.code === 200 && res.data) {
        paymentQR.value = res.data
        return res.data
      }
      toast.showToast(res.message || '获取微信支付二维码失败', 'error')
      return null
    } catch (error: any) {
      console.error('获取微信支付二维码失败:', error)
      toast.showToast('获取微信支付二维码失败', 'error')
      return null
    } finally {
      paymentQRLoading.value = false
    }
  }

  /** 获取支付宝支付二维码 */
  async function fetchAlipayQR(orderNo: string): Promise<PaymentQRResponse | null> {
    const toast = useToastStore()
    paymentQRLoading.value = true
    try {
      const res = await getAlipayQRApi(orderNo)
      if (res.code === 200 && res.data) {
        paymentQR.value = res.data
        return res.data
      }
      toast.showToast(res.message || '获取支付宝支付二维码失败', 'error')
      return null
    } catch (error: any) {
      console.error('获取支付宝支付二维码失败:', error)
      toast.showToast('获取支付宝支付二维码失败', 'error')
      return null
    } finally {
      paymentQRLoading.value = false
    }
  }

  /** 轮询支付状态 */
  function pollPaymentStatus(orderNo: string, onSuccess?: () => void, onFailed?: () => void) {
    if (paymentPollingTimer) {
      clearInterval(paymentPollingTimer)
    }

    let attempts = 0
    const maxAttempts = 60 // 最多轮询60次（约5分钟）

    paymentPollingTimer = setInterval(async () => {
      attempts++
      try {
        const res = await queryPaymentStatusApi(orderNo)
        if (res.code === 200 && res.data) {
          const status = res.data.status
          if (status === 'paid') {
            clearPaymentPolling()
            onSuccess?.()
          } else if (status === 'failed' || status === 'expired') {
            clearPaymentPolling()
            onFailed?.()
          }
        }
        if (attempts >= maxAttempts) {
          clearPaymentPolling()
        }
      } catch (error) {
        console.error('轮询支付状态失败:', error)
      }
    }, 5000) // 每5秒轮询一次
  }

  /** 停止支付轮询 */
  function clearPaymentPolling() {
    if (paymentPollingTimer) {
      clearInterval(paymentPollingTimer)
      paymentPollingTimer = null
    }
  }

  /** 创建测试订单（演示用） */
  async function createTestOrder(planId: string, channel: PaymentChannel): Promise<PaymentQRResponse | null> {
    const toast = useToastStore()
    paymentQRLoading.value = true
    try {
      const res = await createTestOrderApi(planId, channel)
      if (res.code === 200 && res.data) {
        paymentQR.value = res.data
        return res.data
      }
      toast.showToast(res.message || '创建测试订单失败', 'error')
      return null
    } catch (error: any) {
      console.error('创建测试订单失败:', error)
      toast.showToast('创建测试订单失败', 'error')
      return null
    } finally {
      paymentQRLoading.value = false
    }
  }

  /** 模拟支付成功（演示用） */
  async function mockPaySuccess(orderNo: string): Promise<boolean> {
    const toast = useToastStore()
    try {
      const res = await mockPaySuccessApi(orderNo)
      if (res.code === 200) {
        toast.showToast('模拟支付成功', 'success')
        return true
      }
      toast.showToast(res.message || '模拟支付失败', 'error')
      return false
    } catch (error: any) {
      console.error('模拟支付成功失败:', error)
      toast.showToast('模拟支付失败', 'error')
      return false
    }
  }

  /** 重置状态 */
  function reset() {
    plans.value = []
    currentSubscription.value = null
    orders.value = []
    ordersTotal.value = 0
    ordersPage.value = 1
    ordersPageSize.value = 10
    currentOrder.value = null
    paymentQR.value = null
    clearPaymentPolling()
  }

  return {
    // 状态
    plans,
    plansLoading,
    currentSubscription,
    subscriptionLoading,
    orders,
    ordersTotal,
    ordersPage,
    ordersPageSize,
    ordersLoading,
    ordersStatusFilter,
    currentOrder,
    currentOrderLoading,
    paymentQR,
    paymentQRLoading,

    // 计算属性
    pendingPaymentCount,
    refundPendingCount,
    orderStatusStats,

    // 方法
    fetchPlans,
    fetchCurrentSubscription,
    fetchOrders,
    fetchOrderDetail,
    createOrder,
    cancelOrder,
    refundOrder,
    fetchWechatPayQR,
    fetchAlipayQR,
    pollPaymentStatus,
    clearPaymentPolling,
    createTestOrder,
    mockPaySuccess,
    reset,
  }
})
