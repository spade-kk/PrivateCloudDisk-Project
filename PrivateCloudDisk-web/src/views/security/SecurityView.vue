<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="安全中心"
      description="管理账号安全、双因素认证、登录会话与设备"
      :breadcrumbs="[{ label: '安全中心', icon: 'fa fa-shield' }]"
      :tabs="securityTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    >
      <template #actions>
        <div class="flex items-center gap-3 rounded-lg border border-neutral-200 px-4 py-2">
          <div>
            <p class="text-xs text-neutral-400">安全评分</p>
            <p :class="['text-lg font-bold', scoreColor]">{{ securityStore.securityScore?.score || '--' }}</p>
          </div>
          <div class="h-10 w-10 rounded-full border-2 flex items-center justify-center" :class="scoreRingClass">
            <i :class="scoreIcon" class="text-lg"></i>
          </div>
        </div>
      </template>
    </PageHeader>

    <!-- 双因素认证 -->
    <div v-if="activeTab === '2fa'" class="responsive-panel p-4 sm:p-6">
      <div class="flex items-start justify-between">
        <div>
          <h3 class="text-base font-semibold text-neutral-700">双因素认证 (2FA)</h3>
          <p class="mt-1 text-sm text-neutral-400">增加额外的安全层，防止未授权访问</p>
        </div>
        <StatusBadge :status="securityStore.twoFactorEnabled ? 'active' : 'inactive'" :dot="true" />
      </div>
      <div class="mt-6">
        <div v-if="!securityStore.twoFactorEnabled" class="rounded-lg border border-dashed border-neutral-300 p-6 text-center">
          <i class="fa fa-shield text-3xl text-neutral-300"></i>
          <p class="mt-3 text-sm text-neutral-500">双因素认证尚未启用</p>
          <p class="mt-1 text-xs text-neutral-400">启用后登录时需输入验证码，提高账号安全性</p>
          <button @click="showEnable2FA = true" class="mt-4 rounded-lg bg-primary px-6 py-2 text-sm text-white hover:bg-primary/90">
            立即启用
          </button>
        </div>
        <div v-else class="space-y-4">
          <p class="text-sm text-success"><i class="fa fa-check-circle mr-1"></i> 双因素认证已启用</p>
          <button @click="showDisable2FA = true" class="rounded-lg border border-danger px-4 py-2 text-sm text-danger hover:bg-danger/5">
            禁用双因素认证
          </button>
        </div>
      </div>
    </div>

    <!-- 登录历史 -->
    <div v-if="activeTab === 'history'" class="space-y-4">
      <DataTable
        :columns="loginHistoryColumns"
        :data="securityStore.loginHistory"
        :loading="securityStore.loginHistoryLoading"
        :total="securityStore.loginHistoryTotal"
        :current-page="securityStore.loginHistoryPage"
        @page-change="securityStore.loginHistoryPage = $event; securityStore.fetchLoginHistory()"
        empty-text="暂无登录记录"
      />
    </div>

    <!-- 活跃会话 -->
    <div v-if="activeTab === 'sessions'" class="space-y-4">
      <div class="flex items-center justify-between">
        <p class="text-sm text-neutral-400">当前共 {{ securityStore.activeSessions.length }} 个活跃会话</p>
        <button @click="revokeAll" class="text-sm text-danger hover:underline">撤销所有其他会话</button>
      </div>
      <div class="space-y-3">
        <div v-for="session in securityStore.activeSessions" :key="session.id" class="responsive-panel flex items-center justify-between p-4">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10">
              <i :class="session.current ? 'fa fa-laptop' : 'fa fa-mobile'" class="text-primary"></i>
            </div>
            <div>
              <p class="text-sm font-medium text-neutral-700">
                {{ session.device || '未知设备' }}
                <StatusBadge v-if="session.current" status="active" class="ml-2" />
              </p>
              <p class="text-xs text-neutral-400">{{ session.ip }} · {{ session.location || '未知位置' }} · {{ formatTime(session.lastActive) }}</p>
            </div>
          </div>
          <button v-if="!session.current" @click="revokeSession(session.id)" class="text-sm text-neutral-400 hover:text-danger">
            <i class="fa fa-times"></i> 撤销
          </button>
        </div>
      </div>
    </div>

    <!-- 信任设备 -->
    <div v-if="activeTab === 'devices'" class="space-y-4">
      <div v-if="securityStore.trustedDevices.length === 0" class="responsive-panel py-12 text-center">
        <i class="fa fa-mobile text-3xl text-neutral-300"></i>
        <p class="mt-3 text-sm text-neutral-400">暂无信任设备</p>
      </div>
      <div v-else class="space-y-3">
        <div v-for="device in securityStore.trustedDevices" :key="device.id" class="responsive-panel flex items-center justify-between p-4">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-neutral-100">
              <i :class="getDeviceIcon(device.type)" class="text-neutral-500"></i>
            </div>
            <div>
              <p class="text-sm font-medium text-neutral-700">{{ device.name || device.deviceName }}</p>
              <p class="text-xs text-neutral-400">{{ device.os }} · 最后活跃: {{ formatTime(device.lastUsed) }}</p>
            </div>
          </div>
          <button @click="removeDevice(device.id)" class="text-sm text-neutral-400 hover:text-danger">
            <i class="fa fa-trash"></i> 移除
          </button>
        </div>
      </div>
    </div>

    <!-- 安全事件 -->
    <div v-if="activeTab === 'events'" class="space-y-4">
      <Timeline :items="securityStore.securityEvents">
        <template #title="{ item }">{{ item.title || item.event }}</template>
        <template #description="{ item }">{{ item.description || item.detail }}</template>
        <template #time="{ item }">{{ item.time || item.createdAt }}</template>
      </Timeline>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import DataTable from '@/components/common/DataTable.vue'
import Timeline from '@/components/common/Timeline.vue'
import { useSecurityStore } from '@/stores/securityStore'

const securityStore = useSecurityStore()
const activeTab = ref('2fa')
const showEnable2FA = ref(false)
const showDisable2FA = ref(false)

const securityTabs = [
  { key: '2fa', label: '双因素认证', icon: 'fa fa-lock' },
  { key: 'history', label: '登录历史', icon: 'fa fa-history' },
  { key: 'sessions', label: '活跃会话', icon: 'fa fa-desktop' },
  { key: 'devices', label: '信任设备', icon: 'fa fa-mobile' },
  { key: 'events', label: '安全事件', icon: 'fa fa-exclamation-triangle' },
]

const loginHistoryColumns = [
  { key: 'ip', label: 'IP地址' },
  { key: 'device', label: '设备' },
  { key: 'location', label: '位置' },
  { key: 'status', label: '状态', type: 'status' },
  { key: 'createdAt', label: '时间', type: 'date' },
]

const scoreColor = computed(() => {
  const level = securityStore.scoreLevel
  if (level === 'high') return 'text-success'
  if (level === 'medium') return 'text-warning'
  return 'text-danger'
})

const scoreRingClass = computed(() => {
  const level = securityStore.scoreLevel
  if (level === 'high') return 'border-success text-success'
  if (level === 'medium') return 'border-warning text-warning'
  return 'border-danger text-danger'
})

const scoreIcon = computed(() => {
  const level = securityStore.scoreLevel
  if (level === 'high') return 'fa fa-check'
  if (level === 'medium') return 'fa fa-exclamation'
  return 'fa fa-times'
})

function getDeviceIcon(type) {
  const map = { desktop: 'fa fa-desktop', laptop: 'fa fa-laptop', mobile: 'fa fa-mobile', tablet: 'fa fa-tablet' }
  return map[type] || 'fa fa-desktop'
}

function formatTime(time) {
  if (!time) return '--'
  try { return new Date(time).toLocaleString('zh-CN') } catch { return time }
}

async function revokeSession(id) {
  if (!confirm('确定要撤销此会话吗？')) return
  await securityStore.revokeSession(id)
}

async function revokeAll() {
  if (!confirm('确定要撤销所有其他会话吗？')) return
  await securityStore.revokeAllSessions()
}

async function removeDevice(id) {
  if (!confirm('确定要移除此设备吗？')) return
  await securityStore.removeDevice(id)
}

onMounted(() => {
  securityStore.fetch2FAStatus()
  securityStore.fetchActiveSessions()
  securityStore.fetchTrustedDevices()
  securityStore.fetchSecurityScore()
  securityStore.fetchSecurityEvents()
})
</script>