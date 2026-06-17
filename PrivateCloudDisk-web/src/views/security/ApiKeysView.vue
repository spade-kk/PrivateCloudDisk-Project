<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="API 密钥管理"
      description="管理 API 访问密钥，用于第三方应用集成"
      :breadcrumbs="[{ label: '安全中心', to: '/security' }, { label: 'API 密钥', icon: 'fa fa-key' }]"
    >
      <template #actions>
        <button @click="showCreateModal = true" class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primary/90">
          <i class="fa fa-plus mr-1"></i> 创建密钥
        </button>
      </template>
    </PageHeader>

    <!-- 使用提示 -->
    <div class="rounded-lg border border-info/30 bg-info/5 p-4">
      <div class="flex items-start gap-3">
        <i class="fa fa-info-circle text-info mt-0.5"></i>
        <div>
          <p class="text-sm font-medium text-neutral-700">安全提示</p>
          <p class="mt-1 text-xs text-neutral-500">API 密钥具有账户的全部权限，请妥善保管。不要在客户端代码或公开仓库中暴露密钥。如发现密钥泄露，请立即撤销并重新生成。</p>
        </div>
      </div>
    </div>

    <!-- 密钥列表 -->
    <div class="space-y-3">
      <div v-for="key in apiKeys" :key="key.id" class="responsive-panel p-4">
        <div class="flex items-center justify-between">
          <div class="min-w-0 flex-1">
            <div class="flex items-center gap-3">
              <h4 class="text-sm font-semibold text-neutral-700 truncate">{{ key.name }}</h4>
              <StatusBadge :status="key.status" :dot="true" />
            </div>
            <p class="mt-1 font-mono text-xs text-neutral-400">
              {{ maskKey(key.prefix) }}
              <button @click="copyKey(key)" class="ml-2 text-primary hover:underline">复制</button>
            </p>
            <p class="mt-1 text-xs text-neutral-400">
              创建于 {{ key.createdAt }} · 最后使用 {{ key.lastUsed || '从未使用' }}
            </p>
          </div>
          <div class="flex items-center gap-2">
            <button @click="showUsage(key)" class="rounded p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-primary" title="使用统计">
              <i class="fa fa-bar-chart"></i>
            </button>
            <button @click="revokeKey(key)" class="rounded p-1.5 text-neutral-400 hover:bg-neutral-100 hover:text-danger" title="撤销">
              <i class="fa fa-trash"></i>
            </button>
          </div>
        </div>
        <!-- 权限标签 -->
        <div class="mt-3 flex flex-wrap gap-1">
          <span v-for="perm in key.permissions" :key="perm" class="rounded bg-neutral-100 px-2 py-0.5 text-xs text-neutral-500">{{ perm }}</span>
        </div>
      </div>
      <!-- 空状态 -->
      <div v-if="apiKeys.length === 0" class="responsive-panel py-12 text-center">
        <i class="fa fa-key text-3xl text-neutral-300"></i>
        <p class="mt-3 text-sm text-neutral-400">暂无 API 密钥</p>
        <p class="mt-1 text-xs text-neutral-400">创建一个 API 密钥以开始使用 API</p>
      </div>
    </div>

    <!-- 创建密钥弹窗 -->
    <div v-if="showCreateModal" class="fixed inset-0 z-50 flex items-center justify-center bg-black/40" @click.self="showCreateModal = false">
      <div class="w-full max-w-md rounded-xl bg-white p-6 shadow-xl">
        <h3 class="text-lg font-bold text-neutral-800">创建 API 密钥</h3>
        <form @submit.prevent="createKey" class="mt-4 space-y-4">
          <div>
            <label class="text-sm font-medium text-neutral-600">密钥名称</label>
            <input v-model="newKey.name" type="text" placeholder="例如：生产环境、移动App" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:border-primary focus:outline-none" required />
          </div>
          <div>
            <label class="text-sm font-medium text-neutral-600">权限范围</label>
            <div class="mt-2 flex flex-wrap gap-2">
              <label v-for="perm in availablePermissions" :key="perm" class="flex items-center gap-1.5 text-sm text-neutral-600">
                <input type="checkbox" v-model="newKey.permissions" :value="perm" class="h-4 w-4 rounded border-neutral-300 text-primary" />
                {{ perm }}
              </label>
            </div>
          </div>
          <div>
            <label class="text-sm font-medium text-neutral-600">过期时间</label>
            <select v-model="newKey.expiry" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm">
              <option value="">永不过期</option>
              <option value="30">30 天</option>
              <option value="90">90 天</option>
              <option value="180">180 天</option>
              <option value="365">1 年</option>
            </select>
          </div>
          <div class="flex justify-end gap-3 pt-2">
            <button type="button" @click="showCreateModal = false" class="rounded-lg border border-neutral-200 px-4 py-2 text-sm text-neutral-600">取消</button>
            <button type="submit" class="rounded-lg bg-primary px-4 py-2 text-sm text-white hover:bg-primary/90">创建</button>
          </div>
        </form>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'
import StatusBadge from '@/components/common/StatusBadge.vue'
import { useSecurityStore } from '@/stores/securityStore'

const securityStore = useSecurityStore()
const showCreateModal = ref(false)

const newKey = ref({ name: '', permissions: ['read'], expiry: '' })
const availablePermissions = ['read', 'write', 'delete', 'admin']

const apiKeys = [
  { id: 1, name: '生产环境后端', prefix: 'pcd_live_a1b2c3...', status: 'active', createdAt: '2025-06-15', lastUsed: '2026-01-10 14:30', permissions: ['read', 'write', 'delete'] },
  { id: 2, name: '移动App客户端', prefix: 'pcd_mob_x7y8z9...', status: 'active', createdAt: '2025-09-20', lastUsed: '2026-01-12 09:15', permissions: ['read', 'write'] },
  { id: 3, name: '测试环境', prefix: 'pcd_test_d4e5f6...', status: 'inactive', createdAt: '2025-03-01', lastUsed: '2025-08-15 11:00', permissions: ['read'] },
]

function maskKey(prefix) {
  return prefix || '●●●●●●●●●●●●●●●●'
}

function copyKey(key) {
  navigator.clipboard?.writeText(key.prefix || '')
  alert('已复制到剪贴板')
}

function revokeKey(key) {
  if (!confirm(`确定要撤销密钥 "${key.name}" 吗？此操作不可撤销。`)) return
  securityStore.revokeApiKey(key.id)
}

function showUsage(key) {
  // 显示使用统计
}

async function createKey() {
  const res = await securityStore.createApiKey({
    name: newKey.value.name,
    permissions: newKey.value.permissions,
    expiry: newKey.value.expiry || null,
  })
  if (res.code === 200) {
    showCreateModal.value = false
    newKey.value = { name: '', permissions: ['read'], expiry: '' }
  }
}
</script>