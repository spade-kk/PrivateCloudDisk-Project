<template>
  <span
    :class="['inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium', badgeClass]"
  >
    <span v-if="dot" class="mr-1.5 h-1.5 w-1.5 rounded-full" :class="dotClass"></span>
    {{ label }}
  </span>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps({
  status: { type: [String, Number, Boolean], default: '' },
  statusMap: { type: Object, default: () => ({}) },
  dot: { type: Boolean, default: false },
})

const statusKey = computed(() => String(props.status).toLowerCase())

const badgeClass = computed(() => {
  if (props.statusMap[statusKey.value]) return props.statusMap[statusKey.value]
  // 默认映射
  const map = {
    active: 'bg-success/10 text-success',
    enabled: 'bg-success/10 text-success',
    success: 'bg-success/10 text-success',
    online: 'bg-success/10 text-success',
    completed: 'bg-success/10 text-success',
    resolved: 'bg-success/10 text-success',
    paid: 'bg-success/10 text-success',

    inactive: 'bg-neutral-100 text-neutral-500',
    disabled: 'bg-neutral-100 text-neutral-500',
    pending: 'bg-warning/10 text-warning',
    warning: 'bg-warning/10 text-warning',
    processing: 'bg-info/10 text-info',
    suspended: 'bg-warning/10 text-warning',

    failed: 'bg-danger/10 text-danger',
    error: 'bg-danger/10 text-danger',
    banned: 'bg-danger/10 text-danger',
    blocked: 'bg-danger/10 text-danger',
    deleted: 'bg-danger/10 text-danger',
    expired: 'bg-danger/10 text-danger',
    revoked: 'bg-danger/10 text-danger',

    admin: 'bg-purple-100 text-purple-700',
    user: 'bg-blue-50 text-blue-600',
    moderator: 'bg-teal-50 text-teal-600',
    guest: 'bg-neutral-100 text-neutral-500',
  }
  return map[statusKey.value] || 'bg-neutral-100 text-neutral-600'
})

const dotClass = computed(() => {
  const map = {
    active: 'bg-success', enabled: 'bg-success', success: 'bg-success',
    online: 'bg-success', completed: 'bg-success',
    inactive: 'bg-neutral-400', disabled: 'bg-neutral-400',
    pending: 'bg-warning', warning: 'bg-warning',
    failed: 'bg-danger', error: 'bg-danger', banned: 'bg-danger',
    admin: 'bg-purple-500', user: 'bg-blue-500',
  }
  return map[statusKey.value] || 'bg-neutral-400'
})

const label = computed(() => {
  if (props.statusMap[statusKey.value]) {
    // statusMap value is class, return status as label
    return props.status
  }
  const map = {
    active: '活跃', enabled: '已启用', success: '成功', online: '在线',
    completed: '已完成', resolved: '已解决', paid: '已支付',
    inactive: '未激活', disabled: '已禁用', pending: '待处理',
    warning: '警告', processing: '处理中', suspended: '已暂停',
    failed: '失败', error: '错误', banned: '已封禁', blocked: '已阻止',
    deleted: '已删除', expired: '已过期', revoked: '已撤销',
    admin: '管理员', user: '用户', moderator: '协管员', guest: '访客',
  }
  return map[statusKey.value] || props.status
})
</script>