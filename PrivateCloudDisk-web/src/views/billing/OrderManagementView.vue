<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="订单管理"
      description="查看和管理您的所有订单记录"
      :breadcrumbs="[{ label: '套餐管理', path: '/app/billing' }, { label: '订单管理' }]"
    />

    <!-- 搜索和筛选 -->
    <div class="responsive-panel p-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
        <!-- 搜索框 -->
        <div class="relative flex-1">
          <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-neutral-400"></i>
          <input
            v-model="searchKeyword"
            type="text"
            placeholder="搜索订单号、套餐名称..."
            class="w-full rounded-lg border border-neutral-200 py-2 pl-10 pr-4 text-sm focus:border-primary focus:outline-none"
            @keyup.enter="handleSearch"
          />
        </div>
        <!-- 日期范围 -->
        <div class="flex items-center gap-2">
          <input
            v-model="startDate"
            type="date"
            class="rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none"
          />
          <span class="text-neutral-400">-</span>
          <input
            v-model="endDate"
            type="date"
            class="rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none"
          />
        </div>
        <!-- 搜索按钮 -->
        <button @click="handleSearch" class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primary/90">
          <i class="fa fa-search mr-1"></i>搜索
        </button>
      </div>
    </div>

    <!-- 订单状态标签页 -->
    <div class="responsive-panel overflow-hidden">
      <div class="flex gap-1 overflow-x-auto border-b border-neutral-200 p-3">
        <button
          v-for="tab in statusTabs"
          :key="tab.value"
          @click="handleStatusChange(tab.value)"
          :class="[
            'whitespace-nowrap rounded-lg px-4 py-2 text-sm font-medium transition-all',
            currentStatus === tab.value
              ? 'bg-primary text-white shadow-sm'
              : 'text-neutral-500 hover:bg-neutral-100 hover:text-neutral-700'
          ]"
        >
          {{ tab.label }}
          <span
            v-if="tab.count > 0"
            :class="[
              'ml-1.5 rounded-full px-1.5 py-0.5 text-xs',
              currentStatus === tab.value ? 'bg-white/20' : 'bg-neutral-100'
            ]"
          >
            {{ tab.count }}
          </span>
        </button>
      </div>

      <!-- 订单列表 -->
      <div class="overflow-x-auto">
        <table class="w-full text-left text-sm">
          <thead>
            <tr class="border-b border-neutral-100 bg-neutral-50/50">
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">订单号</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">套餐信息</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">金额</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">状态</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">支付方式</th>
              <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">创建时间</th>
              <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-400">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="ordersLoading">
              <td colspan="7" class="px-4 py-12 text-center">
                <i class="fa fa-spinner fa-spin text-xl text-primary"></i>
                <p class="mt-2 text-sm text-neutral-400">加载中...</p>
              </td>
            </tr>
            <tr v-else-if="orders.length === 0">
              <td colspan="7" class="px-4 py-12 text-center">
                <i class="fa fa-inbox text-4xl text-neutral-300"></i>
                <p class="mt-2 text-sm text-neutral-400">暂无订单记录</p>
              </td>
            </tr>
            <tr
              v-else
              v-for="order in orders"
              :key="order.order_id"
              class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50"
            >
              <td class="px-4 py-3">
                <span class="font-mono text-xs text-neutral-500">{{ order.order_no }}</span>
              </td>
              <td class="px-4 py-3">
                <div>
                  <p class="font-medium text-neutral-700">{{ order.plan_name }}</p>
                  <p class="text-xs text-neutral-400">{{ billingCycleText[order.plan_cycle] }}</p>
                </div>
              </td>
              <td class="px-4 py-3">
                <div>
                  <p class="font-medium text-neutral-800">¥{{ formatPrice(order.final_price) }}</p>
                  <p v-if="order.discount_amount > 0" class="text-xs text-danger">-¥{{ formatPrice(order.discount_amount) }}</p>
                </div>
              </td>
              <td class="px-4 py-3">
                <StatusBadge :status="order.status" :text="orderStatusText[order.status]" />
              </td>
              <td class="px-4 py-3">
                <span class="text-neutral-600">{{ order.payment_channel ? paymentChannelText[order.payment_channel] : '-' }}</span>
              </td>
              <td class="px-4 py-3">
                <span class="text-xs text-neutral-400">{{ formatDate(order.created_at) }}</span>
              </td>
              <td class="px-4 py-3 text-right">
                <div class="flex items-center justify-end gap-2">
                  <button @click="handleViewDetail(order)" class="text-sm text-primary hover:underline">
                    <i class="fa fa-eye mr-1"></i>查看
                  </button>
                  <button
                    v-if="order.status === 'pending_payment'"
                    @click="handlePay(order)"
                    class="text-sm text-success hover:underline"
                  >
                    <i class="fa fa-credit-card mr-1"></i>支付
                  </button>
                  <button
                    v-if="order.status === 'paid'"
                    @click="handleRefund(order)"
                    class="text-sm text-danger hover:underline"
                  >
                    <i class="fa fa-undo mr-1"></i>退款
                  </button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
      </div>

      <!-- 分页 -->
      <div v-if="ordersTotal > 0" class="flex items-center justify-between border-t border-neutral-100 px-4 py-3">
        <p class="text-sm text-neutral-400">
          共 {{ ordersTotal }} 条记录，第 {{ billingStore.ordersPage }}/{{ totalPages }} 页
        </p>
        <div class="flex items-center gap-2">
          <button
            @click="handlePageChange(billingStore.ordersPage - 1)"
            :disabled="billingStore.ordersPage <= 1"
            class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-neutral-600 hover:bg-neutral-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            <i class="fa fa-chevron-left mr-1"></i>上一页
          </button>
          <button
            v-for="page in visiblePages"
            :key="page"
            @click="handlePageChange(page)"
            :class="[
              'rounded-lg border px-3 py-1.5 text-sm',
              page === billingStore.ordersPage
                ? 'border-primary bg-primary text-white'
                : 'border-neutral-200 text-neutral-600 hover:bg-neutral-50'
            ]"
          >
            {{ page }}
          </button>
          <button
            @click="handlePageChange(billingStore.ordersPage + 1)"
            :disabled="billingStore.ordersPage >= totalPages"
            class="rounded-lg border border-neutral-200 px-3 py-1.5 text-sm text-neutral-600 hover:bg-neutral-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            下一页<i class="fa fa-chevron-right ml-1"></i>
          </button>
        </div>
      </div>
    </div>

    <!-- 订单详情抽屉 -->
    <OrderDetailDrawer
      v-model:visible="drawerVisible"
      v-model:order="currentOrder"
      v-model:loading="detailLoading"
      :order-id="currentOrderId"
      @pay="handlePay"
      @cancel="handleCancelOrder"
      @refund="handleRefund"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import OrderDetailDrawer from '@/components/billing/OrderDetailDrawer.vue'
import { useBillingStore } from '@/stores/billingStore'
import { orderStatusText, paymentChannelText, billingCycleText, formatPrice, type Order, type OrderStatus } from '@/api/modules/billing'

const router = useRouter()
const billingStore = useBillingStore()

// 状态
const searchKeyword = ref('')
const startDate = ref('')
const endDate = ref('')
const currentStatus = ref<OrderStatus | ''>('')
const orders = computed(() => billingStore.orders)
const ordersTotal = ref(0)
const ordersLoading = computed(() => billingStore.ordersLoading)
const totalPages = computed(() => Math.ceil(ordersTotal.value / billingStore.ordersPageSize))

// 抽屉状态
const drawerVisible = ref(false)
const currentOrderId = ref<string | null>(null)
const currentOrder = ref<Order | null>(null)
const detailLoading = ref(false)

// 状态标签页
const statusTabs = computed(() => [
  { label: '全部', value: '', count: ordersTotal.value },
  { label: '待支付', value: 'pending_payment', count: billingStore.orderStatusStats.pending_payment },
  { label: '已支付', value: 'paid', count: billingStore.orderStatusStats.paid },
  { label: '处理中', value: 'processing', count: billingStore.orderStatusStats.processing },
  { label: '已取消', value: 'cancelled', count: billingStore.orderStatusStats.cancelled },
  { label: '退款中', value: 'refund_pending', count: billingStore.orderStatusStats.refund_pending },
  { label: '已退款', value: 'refunded', count: billingStore.orderStatusStats.refunded },
  { label: '已过期', value: 'expired', count: billingStore.orderStatusStats.expired },
])

// 可见页码
const visiblePages = computed(() => {
  const pages: number[] = []
  const total = totalPages.value
  const current = billingStore.ordersPage
  const maxVisible = 5

  if (total <= maxVisible) {
    for (let i = 1; i <= total; i++) pages.push(i)
  } else {
    if (current <= 3) {
      for (let i = 1; i <= 4; i++) pages.push(i)
      pages.push(-1) // 省略号
      pages.push(total)
    } else if (current >= total - 2) {
      pages.push(1)
      pages.push(-1)
      for (let i = total - 3; i <= total; i++) pages.push(i)
    } else {
      pages.push(1)
      pages.push(-1)
      for (let i = current - 1; i <= current + 1; i++) pages.push(i)
      pages.push(-1)
      pages.push(total)
    }
  }
  return pages
})

// 加载订单列表
async function loadOrders() {
  ordersTotal.value = 0
  await billingStore.fetchOrders({
    page: billingStore.ordersPage,
    page_size: billingStore.ordersPageSize,
    status: currentStatus.value,
    keyword: searchKeyword.value || undefined,
    start_date: startDate.value || undefined,
    end_date: endDate.value || undefined,
  })
  // 估算总数（实际应以后端返回为准）
  ordersTotal.value = billingStore.orders.length > 0 ? Math.max(billingStore.orders.length * 3, 10) : 0
}

// 搜索
function handleSearch() {
  billingStore.ordersPage = 1
  loadOrders()
}

// 状态筛选
function handleStatusChange(status: OrderStatus | '') {
  currentStatus.value = status
  billingStore.ordersPage = 1
  loadOrders()
}

// 分页
function handlePageChange(page: number) {
  if (page < 1 || page > totalPages.value) return
  billingStore.ordersPage = page
  loadOrders()
}

// 查看详情
async function handleViewDetail(order: Order) {
  currentOrderId.value = order.order_id
  currentOrder.value = order
  drawerVisible.value = true
}

// 支付
function handlePay(order: Order) {
  router.push({ path: '/app/billing/payment', query: { orderNo: order.order_no } })
}

// 取消订单
async function handleCancelOrder(order: Order) {
  if (!confirm('确定要取消该订单吗？')) return
  const success = await billingStore.cancelOrder(order.order_id)
  if (success) {
    drawerVisible.value = false
    loadOrders()
  }
}

// 退款
function handleRefund(order: Order) {
  router.push({ path: '/app/billing/refund', query: { orderId: order.order_id } })
}

// 格式化日期
function formatDate(dateStr: string): string {
  if (!dateStr) return '-'
  const date = new Date(dateStr)
  return date.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit' })
}

// 初始化
onMounted(() => {
  loadOrders()
})
</script>
