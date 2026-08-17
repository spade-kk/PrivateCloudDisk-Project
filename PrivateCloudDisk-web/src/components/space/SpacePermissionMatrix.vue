<template>
  <div class="space-y-4">
    <label class="block"><span class="mb-1 block text-xs font-medium text-neutral-500">角色</span><select v-model="role" class="w-full rounded-lg border border-neutral-200 px-3 py-2 text-sm" :disabled="locked"><option value="admin">管理员</option><option value="editor">编辑者</option><option value="viewer">只读成员</option><option value="custom">自定义</option></select></label>
    <div v-if="role === 'custom'" class="divide-y rounded-lg border bg-neutral-50"><label v-for="item in permissions" :key="item.key" class="flex items-center justify-between px-3 py-2.5 text-sm text-neutral-700"><span>{{ item.label }}</span><input v-model="values[item.key]" type="checkbox" :disabled="locked" class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary" /></label></div>
    <p v-if="locked" class="text-xs text-amber-600"><i class="fa fa-lock mr-1"></i>所有者/管理员权限不可在此处降级。</p>
  </div>
</template>
<script setup lang="ts">
import { computed, reactive, watch } from 'vue'
import type { SpacePermissionUpdate, SpaceMember } from '@/api/modules/space'
const props = defineProps<{ modelValue: SpacePermissionUpdate; targetRole?: SpaceMember['role'] }>()
const emit = defineEmits<{ 'update:modelValue': [value: SpacePermissionUpdate] }>()
const role = computed({ get: () => props.modelValue.role || 'viewer', set: (value: SpaceMember['role']) => emitValue({ ...props.modelValue, role: value }) })
const values = reactive<Record<string, boolean>>({ canView: false, canRead: false, canDownload: false, canUpload: false, canEdit: false, canDelete: false, canShare: false, canManageMembers: false, canManagePlugins: false, canManageSettings: false })
const permissions = [{ key: 'canView', label: '查看空间' }, { key: 'canRead', label: '读取/预览' }, { key: 'canDownload', label: '下载文件' }, { key: 'canUpload', label: '上传文件' }, { key: 'canEdit', label: '编辑元数据' }, { key: 'canDelete', label: '删除文件' }, { key: 'canShare', label: '创建分享' }, { key: 'canManageMembers', label: '管理成员' }, { key: 'canManagePlugins', label: '管理插件' }, { key: 'canManageSettings', label: '修改设置' }] as const
const locked = computed(() => props.targetRole === 'owner' || props.targetRole === 'admin')
function emitValue(value: SpacePermissionUpdate) { emit('update:modelValue', value) }
watch(() => props.modelValue, (value) => { Object.keys(values).forEach((key) => { values[key] = Boolean(value[key as keyof SpacePermissionUpdate]) }); if (value.role === 'admin') permissions.forEach((item) => { values[item.key] = true }) }, { immediate: true, deep: true })
watch(values, () => { if (role.value === 'custom') emitValue({ ...props.modelValue, ...values }) }, { deep: true })
</script>
