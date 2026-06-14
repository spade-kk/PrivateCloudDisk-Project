<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="管理后台"
      description="系统管理、用户管理、审计日志与系统监控"
      :breadcrumbs="[{ label: '管理后台', icon: 'fa fa-cog' }]"
      :stats="overviewStats"
      :tabs="adminTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    />

    <!-- 概览面板 -->
    <div v-if="activeTab === 'overview'" class="space-y-4 sm:space-y-6">
      <!-- 系统资源 -->
      <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard title="CPU 使用率" :value="adminStore.systemResources?.cpu || 0" unit="%" :progress="adminStore.systemResources?.cpu || 0" progress-color="bg-primary" />
        <StatCard title="内存使用率" :value="adminStore.systemResources?.memory || 0" unit="%" :progress="adminStore.systemResources?.memory || 0" progress-color="bg-warning" />
        <StatCard title="磁盘使用率" :value="adminStore.systemResources?.disk || 0" unit="%" :progress="adminStore.systemResources?.disk || 0" progress-color="bg-info" />
        <StatCard title="在线用户" :value="adminStore.onlineUsers.length" unit="人" />
      </div>
      <!-- 系统概览 + 存储统计 -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="responsive-panel p-4 sm:p-5">
          <h3 class="mb-4 text-base font-semibold text-neutral-700">系统信息</h3>
          <div class="space-y-3 text-sm">
            <div class="flex justify-between"><span class="text-neutral-400">系统版本</span><span class="text-neutral-700 font-medium">{{ adminStore.systemOverview?.version || 'v2.1.0' }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">运行时间</span><span class="text-neutral-700 font-medium">{{ adminStore.systemOverview?.uptime || '--' }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">总用户数</span><span class="text-neutral-700 font-medium">{{ adminStore.systemOverview?.totalUsers || 0 }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">总文件数</span><span class="text-neutral-700 font-medium">{{ adminStore.systemOverview?.totalFiles || 0 }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">活跃任务</span><span class="text-neutral-700 font-medium">{{ adminStore.systemOverview?.activeTasks || 0 }}</span></div>
          </div>
        </div>
        <div class="responsive-panel p-4 sm:p-5">
          <h3 class="mb-4 text-base font-semibold text-neutral-700">存储统计</h3>
          <div class="space-y-3 text-sm">
            <div class="flex justify-between"><span class="text-neutral-400">总存储容量</span><span class="text-neutral-700 font-medium">{{ formatSize(adminStore.storageStats?.totalCapacity) }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">已使用</span><span class="text-neutral-700 font-medium">{{ formatSize(adminStore.storageStats?.used) }}</span></div>
            <div class="flex justify-between"><span class="text-neutral-400">可用空间</span><span class="text-neutral-700 font-medium">{{ formatSize(adminStore.storageStats?.available) }}</span></div>
            <div class="mt-2">
              <div class="h-2 w-full rounded-full bg-neutral-200">
                <div class="h-2 rounded-full bg-primary" :style="{ width: usagePercent + '%' }"></div>
              </div>
              <p class="mt-1 text-xs text-neutral-400">使用率 {{ usagePercent }}%</p>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 用户管理 -->
    <div v-if="activeTab === 'users'" class="space-y-4">
      <div class="flex flex-wrap items-center gap-2">
        <button @click="adminStore.fetchUsers()" class="icon-button" title="刷新">
          <i class="fa fa-refresh"></i>
        </button>
        <div class="relative">
          <i class="fa fa-search absolute left-3 top-1/2 -translate-y-1/2 text-xs text-neutral-400"></i>
          <input v-model="userSearch" @input="onUserSearch" placeholder="搜索用户..." class="rounded-lg border border-neutral-200 py-1.5 pl-8 pr-3 text-sm focus:border-primary focus:outline-none" />
        </div>
      </div>
      <DataTable
        :columns="userColumns"
        :data="adminStore.users"
        :loading="adminStore.usersLoading"
        :total="adminStore.usersTotal"
        :current-page="adminStore.usersPage"
        @page-change="adminStore.setUsersPage($event)"
        empty-text="暂无用户"
      >
        <template #actions="{ row }">
          <div class="flex items-center justify-end gap-1">
            <button @click="toggleUserStatus(row)" class="rounded p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-primary" :title="row.status === 'active' ? '禁用' : '启用'">
              <i :class="row.status === 'active' ? 'fa fa-ban' : 'fa fa-check'"></i>
            </button>
            <button @click="changeRole(row)" class="rounded p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-warning" title="修改角色">
              <i class="fa fa-user-md"></i>
            </button>
            <button @click="deleteUser(row)" class="rounded p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-danger" title="删除">
              <i class="fa fa-trash"></i>
            </button>
          </div>
        </template>
      </DataTable>
    </div>

    <!-- 审计日志 -->
    <div v-if="activeTab === 'audit'" class="space-y-4">
      <DataTable
        :columns="auditColumns"
        :data="adminStore.auditLogs"
        :loading="adminStore.auditLogsLoading"
        :total="adminStore.auditLogsTotal"
        :current-page="adminStore.auditLogsPage"
        @page-change="adminStore.auditLogsPage = $event; adminStore.fetchAuditLogs()"
        empty-text="暂无审计日志"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import DataTable from '@/components/common/DataTable.vue'
import { useAdminStore } from '@/stores/adminStore'

const adminStore = useAdminStore()
const activeTab = ref('overview')
const userSearch = ref('')

const adminTabs = [
  { key: 'overview', label: '系统概览', icon: 'fa fa-dashboard' },
  { key: 'users', label: '用户管理', icon: 'fa fa-users', count: adminStore.usersTotal },
  { key: 'audit', label: '审计日志', icon: 'fa fa-list-alt' },
]

const overviewStats = computed(() => [
  { key: 'users', title: '总用户数', value: adminStore.systemOverview?.totalUsers || 0, unit: '人', trend: '+12%', trendUp: true },
  { key: 'files', title: '总文件数', value: adminStore.systemOverview?.totalFiles || 0, unit: '个', trend: '+8%', trendUp: true },
  { key: 'online', title: '在线用户', value: adminStore.onlineUsers.length, unit: '人' },
  { key: 'tasks', title: '活跃任务', value: adminStore.systemOverview?.activeTasks || 0, unit: '个' },
])

const userColumns = [
  { key: 'id', label: 'ID', width: '80px' },
  { key: 'username', label: '用户名' },
  { key: 'phoneNumber', label: '手机号' },
  { key: 'email', label: '邮箱' },
  { key: 'role', label: '角色', type: 'status', statusMap: { admin: 'bg-purple-100 text-purple-700', user: 'bg-blue-50 text-blue-600' } },
  { key: 'status', label: '状态', type: 'status' },
  { key: 'createdAt', label: '注册时间', type: 'date' },
]

const auditColumns = [
  { key: 'id', label: 'ID', width: '80px' },
  { key: 'action', label: '操作类型' },
  { key: 'userId', label: '用户ID' },
  { key: 'ip', label: 'IP地址' },
  { key: 'detail', label: '详情' },
  { key: 'createdAt', label: '时间', type: 'date' },
]

const usagePercent = computed(() => {
  const s = adminStore.storageStats
  if (!s || !s.totalCapacity) return 0
  return Math.round((s.used / s.totalCapacity) * 100)
})

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '--'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1048576) return (bytes / 1024).toFixed(1) + ' KB'
  if (bytes < 1073741824) return (bytes / 1048576).toFixed(1) + ' MB'
  if (bytes < 1099511627776) return (bytes / 1073741824).toFixed(2) + ' GB'
  return (bytes / 1099511627776).toFixed(2) + ' TB'
}

async function toggleUserStatus(row) {
  const newStatus = row.status === 'active' ? 'inactive' : 'active'
  await adminStore.toggleUser(row.id || row.userId, newStatus)
}

async function changeRole(row) {
  const newRole = row.role === 'admin' ? 'user' : 'admin'
  await adminStore.updateUserRole(row.id || row.userId, newRole)
}

async function deleteUser(row) {
  if (!confirm(`确定要删除用户 ${row.username || row.phoneNumber} 吗？`)) return
  await adminStore.removeUser(row.id || row.userId)
}

let searchTimer = null
function onUserSearch() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    adminStore.fetchUsers({ keyword: userSearch.value })
  }, 300)
}

onMounted(() => {
  adminStore.fetchSystemOverview()
  adminStore.fetchSystemResources()
  adminStore.fetchOnlineUsers()
  adminStore.fetchStorageStats()
  adminStore.fetchUsers()
  adminStore.fetchAuditLogs()
})
</script>