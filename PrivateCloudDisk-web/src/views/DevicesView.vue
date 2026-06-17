<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="我的设备"
      description="管理已登录设备和信任设备，确保账号安全"
      :breadcrumbs="[{ label: '我的设备', icon: 'fa fa-laptop' }]"
      :tabs="deviceTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    />

    <!-- 在线设备 -->
    <div v-if="activeTab === 'online'" class="space-y-4">
      <div class="flex items-center justify-between">
        <p class="text-sm text-neutral-400">
          当前共 {{ onlineDevices.length }} 台设备在线
        </p>
        <button
          @click="handleRevokeAll"
          :disabled="onlineDevices.length <= 1"
          class="text-sm text-danger hover:underline disabled:text-neutral-300 disabled:cursor-not-allowed"
        >
          撤销其他设备
        </button>
      </div>

      <div v-if="loading" class="flex justify-center py-20">
        <LoadingSpinner />
      </div>

      <EmptyState
        v-else-if="onlineDevices.length === 0"
        icon="fa fa-laptop"
        message="暂无在线设备"
        description="当前没有活跃的登录状态"
      />

      <div v-else class="space-y-3">
        <div
          v-for="device in onlineDevices"
          :key="device.id || device.deviceId"
          class="responsive-panel flex items-center gap-4 p-4"
        >
          <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl"
               :class="device.current ? 'bg-primary/10' : 'bg-neutral-100'">
            <i :class="getDeviceIcon(device.type)" :style="{ color: device.current ? '#6366f1' : '#6b7280' }" class="text-xl"></i>
          </div>
          <div class="flex-1 min-w-0">
            <div class="flex items-center gap-2">
              <p class="text-sm font-medium text-neutral-700 truncate">
                {{ device.deviceName || device.device || device.name || '未知设备' }}
              </p>
              <StatusBadge v-if="device.current" status="active" size="sm" text="当前设备" />
            </div>
            <p class="mt-0.5 text-xs text-neutral-400">
              {{ device.os || device.platform || '--' }}
              <span class="mx-1.5">·</span>
              {{ device.browser || device.client || '--' }}
            </p>
            <p class="mt-0.5 text-xs text-neutral-400">
              <i class="fa fa-map-marker mr-1 text-[10px]"></i>
              {{ device.location || device.ip || '未知位置' }}
              <span class="mx-1.5">·</span>
              最后活跃: {{ formatTime(device.lastActive || device.lastSeen) }}
            </p>
          </div>
          <button
            v-if="!device.current"
            @click="handleRevoke(device.id || device.deviceId)"
            class="shrink-0 rounded-lg border border-neutral-200 px-3 py-1.5 text-xs text-neutral-500 hover:border-red-200 hover:text-red-500 transition"
          >
            撤销
          </button>
        </div>
      </div>
    </div>

    <!-- 信任设备 -->
    <div v-if="activeTab === 'trusted'" class="space-y-4">
      <p class="text-sm text-neutral-400">
        信任设备在登录时将跳过部分安全验证步骤。共 {{ trustedDevices.length }} 台信任设备。
      </p>

      <div v-if="securityStore.devicesLoading" class="flex justify-center py-20">
        <LoadingSpinner />
      </div>

      <EmptyState
        v-else-if="trustedDevices.length === 0"
        icon="fa fa-mobile"
        message="暂无信任设备"
        description="信任设备可在安全中心添加"
      />

      <div v-else class="space-y-3">
        <div
          v-for="device in trustedDevices"
          :key="device.id"
          class="responsive-panel flex items-center gap-4 p-4"
        >
          <div class="flex h-12 w-12 shrink-0 items-center justify-center rounded-xl bg-blue-50">
            <i class="fa fa-check-circle text-xl text-blue-500"></i>
          </div>
          <div class="flex-1 min-w-0">
            <p class="text-sm font-medium text-neutral-700">
              {{ device.name || device.deviceName || '未知设备' }}
            </p>
            <p class="mt-0.5 text-xs text-neutral-400">
              {{ device.os || '--' }}
              <span class="mx-1.5">·</span>
              信任时间: {{ formatTime(device.trustedAt || device.lastUsed) }}
            </p>
          </div>
          <button
            @click="handleRemoveTrusted(device.id)"
            class="shrink-0 rounded-lg border border-neutral-200 px-3 py-1.5 text-xs text-neutral-500 hover:border-red-200 hover:text-red-500 transition"
          >
            移除信任
          </button>
        </div>
      </div>
    </div>

    <!-- 登录历史 -->
    <div v-if="activeTab === 'history'" class="space-y-4">
      <div v-if="securityStore.loginHistoryLoading" class="flex justify-center py-20">
        <LoadingSpinner />
      </div>

      <EmptyState
        v-else-if="loginHistory.length === 0"
        icon="fa fa-history"
        message="暂无登录记录"
      />

      <div v-else class="responsive-panel overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead class="border-b border-neutral-100 bg-neutral-50/50">
              <tr>
                <th class="px-4 py-3 font-medium text-neutral-500">设备</th>
                <th class="px-4 py-3 font-medium text-neutral-500">IP 地址</th>
                <th class="px-4 py-3 font-medium text-neutral-500">位置</th>
                <th class="px-4 py-3 font-medium text-neutral-500">登录时间</th>
                <th class="px-4 py-3 font-medium text-neutral-500">状态</th>
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="(log, i) in loginHistory"
                :key="i"
                class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50"
              >
                <td class="px-4 py-3">
                  <div class="flex items-center gap-2">
                    <i :class="getDeviceIcon(log.deviceType)" class="text-neutral-400"></i>
                    <span class="text-neutral-700">{{ log.device || '--' }}</span>
                  </div>
                </td>
                <td class="px-4 py-3 text-neutral-500">{{ log.ip || '--' }}</td>
                <td class="px-4 py-3 text-neutral-500">{{ log.location || '--' }}</td>
                <td class="px-4 py-3 text-neutral-400">{{ formatTime(log.createdAt || log.loginTime) }}</td>
                <td class="px-4 py-3">
                  <StatusBadge :status="log.status === 'success' ? 'active' : 'inactive'" />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <!-- 分页 -->
        <div v-if="securityStore.loginHistoryTotal > 20" class="flex items-center justify-between border-t border-neutral-100 px-4 py-3">
          <span class="text-xs text-neutral-400">共 {{ securityStore.loginHistoryTotal }} 条</span>
          <div class="flex items-center gap-2">
            <button
              :disabled="securityStore.loginHistoryPage <= 1"
              @click="securityStore.loginHistoryPage--; securityStore.fetchLoginHistory()"
              class="rounded border px-2 py-1 text-xs disabled:opacity-30"
            >上一页</button>
            <span class="text-xs text-neutral-600">{{ securityStore.loginHistoryPage }}</span>
            <button
              :disabled="securityStore.loginHistoryPage * 20 >= securityStore.loginHistoryTotal"
              @click="securityStore.loginHistoryPage++; securityStore.fetchLoginHistory()"
              class="rounded border px-2 py-1 text-xs disabled:opacity-30"
            >下一页</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useSecurityStore } from '@/stores/securityStore'

const securityStore = useSecurityStore()
const activeTab = ref('online')
const loading = ref(false)

const deviceTabs = [
  { key: 'online', label: '在线设备', icon: 'fa fa-plug' },
  { key: 'trusted', label: '信任设备', icon: 'fa fa-check-circle' },
  { key: 'history', label: '登录历史', icon: 'fa fa-history' },
]

// 在线设备（活跃会话）
const onlineDevices = computed(() => securityStore.activeSessions)

// 信任设备
const trustedDevices = computed(() => securityStore.trustedDevices)

// 登录历史
const loginHistory = computed(() => securityStore.loginHistory)

function getDeviceIcon(type) {
  const t = (type || '').toLowerCase()
  if (t.includes('mobile') || t.includes('phone')) return 'fa fa-mobile'
  if (t.includes('tablet') || t.includes('ipad')) return 'fa fa-tablet'
  if (t.includes('laptop')) return 'fa fa-laptop'
  return 'fa fa-desktop'
}

function formatTime(time) {
  if (!time) return '--'
  try { return new Date(time).toLocaleString('zh-CN') } catch { return String(time) }
}

async function handleRevoke(id) {
  if (!confirm('确定要撤销此设备吗？该设备将被强制下线。')) return
  await securityStore.revokeSession(id)
}

async function handleRevokeAll() {
  if (!confirm('确定要撤销所有其他设备吗？')) return
  await securityStore.revokeAllSessions()
}

async function handleRemoveTrusted(id) {
  if (!confirm('确定要移除此设备的信任状态吗？')) return
  await securityStore.removeDevice(id)
}

onMounted(() => {
  securityStore.fetchActiveSessions()
  securityStore.fetchTrustedDevices()
  securityStore.fetchLoginHistory()
})
</script>