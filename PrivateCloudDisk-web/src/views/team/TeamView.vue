<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="团队协作"
      description="管理团队成员、共享工作空间与协作权限"
      :breadcrumbs="[{ label: '团队协作', icon: 'fa fa-users' }]"
      :tabs="teamTabs"
      :active-tab="activeTab"
      @tab-change="activeTab = $event"
    >
      <template #actions>
        <button @click="showInviteModal = true" class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primary/90">
          <i class="fa fa-user-plus mr-1"></i> 邀请成员
        </button>
      </template>
    </PageHeader>

    <!-- 团队概览 -->
    <div v-if="activeTab === 'overview'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
      <StatCard title="团队成员" :value="teamMembers.length" unit="人" />
      <StatCard title="工作空间" :value="workspaces.length" unit="个" />
      <StatCard title="共享文件" :value="246" unit="个" />
      <StatCard title="活跃项目" :value="8" unit="个" />
    </div>

    <!-- 成员列表 -->
    <div v-if="activeTab === 'members'" class="space-y-4">
      <div class="responsive-panel overflow-hidden">
        <div class="overflow-x-auto">
          <table class="w-full text-left text-sm">
            <thead>
              <tr class="border-b border-neutral-100 bg-neutral-50/50">
                <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">成员</th>
                <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">角色</th>
                <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">状态</th>
                <th class="px-4 py-3 text-xs font-medium uppercase tracking-wider text-neutral-400">加入时间</th>
                <th class="px-4 py-3 text-right text-xs font-medium uppercase tracking-wider text-neutral-400">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="member in teamMembers" :key="member.id" class="border-b border-neutral-50 transition-colors hover:bg-neutral-50/50">
                <td class="px-4 py-3">
                  <div class="flex items-center gap-3">
                    <div class="flex h-9 w-9 items-center justify-center rounded-full bg-primary/10 text-sm font-semibold text-primary">
                      {{ member.avatar || member.name?.charAt(0) }}
                    </div>
                    <div>
                      <p class="text-sm font-medium text-neutral-700">{{ member.name }}</p>
                      <p class="text-xs text-neutral-400">{{ member.email }}</p>
                    </div>
                  </div>
                </td>
                <td class="px-4 py-3">
                  <StatusBadge :status="member.role" :statusMap="{ owner: 'bg-purple-100 text-purple-700', admin: 'bg-primary/10 text-primary', editor: 'bg-info/10 text-info', viewer: 'bg-neutral-100 text-neutral-500' }" />
                </td>
                <td class="px-4 py-3">
                  <StatusBadge :status="member.status" :dot="true" />
                </td>
                <td class="px-4 py-3 text-xs text-neutral-400">{{ member.joinedAt }}</td>
                <td class="px-4 py-3 text-right">
                  <button class="text-sm text-neutral-400 hover:text-primary"><i class="fa fa-ellipsis-h"></i></button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>

    <!-- 工作空间 -->
    <div v-if="activeTab === 'workspaces'" class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="ws in workspaces" :key="ws.id" class="responsive-panel p-5 transition-shadow hover:shadow-md">
        <div class="flex items-center justify-between">
          <div :class="['flex h-10 w-10 items-center justify-center rounded-lg', ws.colorClass]">
            <i :class="ws.icon" class="text-lg"></i>
          </div>
          <StatusBadge :status="ws.visibility === 'private' ? 'inactive' : 'active'" />
        </div>
        <h3 class="mt-3 text-base font-semibold text-neutral-700">{{ ws.name }}</h3>
        <p class="mt-1 text-xs text-neutral-400">{{ ws.description }}</p>
        <div class="mt-3 flex items-center justify-between text-xs text-neutral-400">
          <span>{{ ws.fileCount }} 个文件</span>
          <span>{{ ws.memberCount }} 名成员</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatCard from '@/components/common/StatCard.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'

const activeTab = ref('overview')
const showInviteModal = ref(false)

const teamTabs = [
  { key: 'overview', label: '概览', icon: 'fa fa-dashboard' },
  { key: 'members', label: '团队成员', icon: 'fa fa-users', count: 5 },
  { key: 'workspaces', label: '工作空间', icon: 'fa fa-briefcase', count: 3 },
]

const teamMembers = [
  { id: 1, name: '张三', email: 'zhangsan@example.com', role: 'owner', status: 'active', joinedAt: '2025-01-15' },
  { id: 2, name: '李四', email: 'lisi@example.com', role: 'admin', status: 'active', joinedAt: '2025-02-20' },
  { id: 3, name: '王五', email: 'wangwu@example.com', role: 'editor', status: 'active', joinedAt: '2025-03-10' },
  { id: 4, name: '赵六', email: 'zhaoliu@example.com', role: 'viewer', status: 'inactive', joinedAt: '2025-04-05' },
  { id: 5, name: '孙七', email: 'sunqi@example.com', role: 'editor', status: 'active', joinedAt: '2025-05-18' },
]

const workspaces = [
  { id: 1, name: '产品设计', description: 'UI/UX 设计与原型', icon: 'fa fa-paint-brush', colorClass: 'bg-primary/10 text-primary', visibility: 'public', fileCount: 156, memberCount: 4 },
  { id: 2, name: '研发中心', description: '前后端开发与 DevOps', icon: 'fa fa-code', colorClass: 'bg-success/10 text-success', visibility: 'private', fileCount: 320, memberCount: 8 },
  { id: 3, name: '市场营销', description: '品牌推广与营销素材', icon: 'fa fa-bullhorn', colorClass: 'bg-warning/10 text-warning', visibility: 'public', fileCount: 89, memberCount: 3 },
]
</script>