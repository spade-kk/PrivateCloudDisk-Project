// ============================================================
// billing.ts — 订阅与订单管理 API 模块
// ============================================================
// 封装订阅计划、订单、支付、退款相关接口，
// 供套餐管理、订单管理、支付等页面调用。
// ============================================================

import { get, post } from '@/utils/request'

// ============================================================
// 类型定义
// ============================================================

/** 订阅计划 */
export interface SubscriptionPlan {
  id: string
  plan_id: string
  name: string
  description: string
  price: number // 分
  original_price?: number // 原价 分
  billing_cycle: 'monthly' | 'quarterly' | 'yearly'
  storage_quota: number // 字节
  bandwidth_quota?: number // 字节
  features: string[]
  is_popular?: boolean
  is_active: boolean
  sort_order: number
  created_at: string
  updated_at: string
}

/** 订单状态 */
export type OrderStatus =
  | 'pending_payment' // 待支付
  | 'paid' // 已支付
  | 'processing' // 处理中
  | 'cancelled' // 已取消
  | 'refund_pending' // 退款中
  | 'refunded' // 已退款
  | 'refund_rejected' // 退款被拒绝
  | 'expired' // 已过期
  | 'failed' // 失败

/** 支付渠道 */
export type PaymentChannel = 'wechat' | 'alipay' | 'balance' | 'coupon'

/** 订单信息 */
export interface Order {
  order_id: string
  order_no: string
  user_id: string
  user_name: string
  user_email: string
  plan_id: string
  plan_name: string
  plan_cycle: 'monthly' | 'quarterly' | 'yearly'
  original_price: number // 分
  discount_amount: number // 分
  final_price: number // 分
  status: OrderStatus
  payment_channel?: PaymentChannel
  payment_time?: string
  paid_amount?: number // 分
  coupon_id?: string
  coupon_code?: string
  transaction_id?: string // 第三方交易流水号
  remark?: string
  expire_time?: string
  refund_reason?: string
  refund_time?: string
  refund_amount?: number // 分
  refund_reject_reason?: string
  created_at: string
  updated_at: string
}

/** 创建订单请求 */
export interface CreateOrderRequest {
  plan_id: string
  billing_cycle: 'monthly' | 'quarterly' | 'yearly'
  coupon_code?: string
  payment_channel: PaymentChannel
}

/** 订单查询参数 */
export interface OrderQueryParams {
  page: number
  page_size: number
  status?: OrderStatus | ''
  keyword?: string
  start_date?: string
  end_date?: string
}

/** 支付二维码响应 */
export interface PaymentQRResponse {
  order_no: string
  qr_code: string // 二维码 Base64 或 URL
  qr_content: string // 二维码内容（支付链接）
  payment_url?: string // H5 支付链接
  expire_time: string
  amount: number
  channel: PaymentChannel
}

/** 当前订阅信息 */
export interface CurrentSubscription {
  plan: SubscriptionPlan
  expire_time: string
  is_active: boolean
}

// ============================================================
// API 函数
// ============================================================

/** 获取订阅计划列表 */
export function getSubscriptionPlansApi(params?: { billing_cycle?: string; is_active?: boolean }): Promise<any> {
  return get('billing/plans', params)
}

/** 获取订阅计划详情 */
export function getPlanDetailApi(planId: string): Promise<any> {
  return get(`billing/plans/${planId}`)
}

/** 创建订单 */
export function createOrderApi(data: CreateOrderRequest): Promise<any> {
  return post('billing/orders', data)
}

/** 获取订单列表（分页） */
export function getOrdersApi(params: OrderQueryParams): Promise<any> {
  return get('billing/orders', params)
}

/** 获取订单详情 */
export function getOrderDetailApi(orderId: string): Promise<any> {
  return get(`billing/orders/${orderId}`)
}

/** 根据订单号获取订单详情 */
export function getOrderByNoApi(orderNo: string): Promise<any> {
  return get(`billing/orders/no/${orderNo}`)
}

/** 取消订单 */
export function cancelOrderApi(orderId: string): Promise<any> {
  return post(`billing/orders/${orderId}/cancel`, {})
}

/** 申请退款 */
export function refundOrderApi(orderId: string, reason: string): Promise<any> {
  return post(`billing/orders/${orderId}/refund`, { reason })
}

/** 获取微信支付二维码 */
export function getWechatPayQRApi(orderNo: string): Promise<any> {
  return post('billing/pay/wechat', { order_no: orderNo })
}

/** 获取支付宝支付二维码 */
export function getAlipayQRApi(orderNo: string): Promise<any> {
  return post('billing/pay/alipay', { order_no: orderNo })
}

/** 查询支付状态 */
export function queryPaymentStatusApi(orderNo: string): Promise<any> {
  return get(`billing/pay/status/${orderNo}`)
}

/** 获取用户当前订阅信息 */
export function getCurrentSubscriptionApi(): Promise<any> {
  return get('billing/subscription/current')
}

/** 模拟创建测试订单（演示用） */
export function createTestOrderApi(planId: string, channel: PaymentChannel): Promise<any> {
  return post('billing/test/order', { plan_id: planId, channel })
}

/** 模拟支付成功回调（演示用） */
export function mockPaySuccessApi(orderNo: string): Promise<any> {
  return post('billing/test/pay-success', { order_no: orderNo })
}

// ============================================================
// 工具函数
// ============================================================

/** 格式化金额（分 -> 元） */
export function formatPrice(priceInCents: number): string {
  return (priceInCents / 100).toFixed(2)
}

/** 格式化存储大小（字节） */
export function formatStorage(bytes: number): string {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

/** 订单状态中文映射 */
export const orderStatusText: Record<OrderStatus, string> = {
  pending_payment: '待支付',
  paid: '已支付',
  processing: '处理中',
  cancelled: '已取消',
  refund_pending: '退款中',
  refunded: '已退款',
  refund_rejected: '退款被拒绝',
  expired: '已过期',
  failed: '失败',
}

/** 支付渠道中文映射 */
export const paymentChannelText: Record<PaymentChannel, string> = {
  wechat: '微信支付',
  alipay: '支付宝',
  balance: '余额支付',
  coupon: '优惠券',
}

/** 计费周期中文映射 */
export const billingCycleText: Record<string, string> = {
  monthly: '月付',
  quarterly: '季付',
  yearly: '年付',
}
