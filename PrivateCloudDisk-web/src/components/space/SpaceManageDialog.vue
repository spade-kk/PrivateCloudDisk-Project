<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="$emit('close')"
    >
      <div class="w-full max-w-2xl max-h-[85vh] overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <!-- 标题栏 -->
        <div class="sticky top-0 z-10 flex items-center justify-between border-b bg-white px-6 py-4">
          <div>
            <h2 class="text-lg font-semibold text-neutral-800">{{ space.spaceName }}</h2>
            <p class="text-xs text-neutral-400">{{ getSpaceTypeLabel(space.spaceType) }}</p>
          </div>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-full text-neutral-400 transition hover:bg-neutral-100"
            @click="$emit('close')"
          >
            <i class="fa fa-times"></i>
          </button>
        </div>

        <!-- Tab 切换 -->
        <div class="flex border-b px-6">
          <button
            v-for="tab in tabs"
            :key="tab.key"
            class="border-b-2 px-3 py-2.5 text-sm font-medium transition"
            :class="activeTab === tab.key
              ? 'border-primary text-primary'
              : 'border-transparent text-neutral-500 hover:text-neutral-700'"
            @click="activeTab = tab.key"
          >
            {{ tab.label }}
          </button>
        </div>

        <!-- 基本信息 -->
        <div v-if="activeTab === 'info'" class="space-y-4 px-6 py-4">
          <div>
            <label class="mb-1 block text-xs font-medium text-neutral-500">空间名称</label>
            <input
              v-model="editForm.spaceName"
              class="w-full rounded-lg border px-3 py-2 text-sm"
              maxlength="200"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-neutral-500">描述</label>
            <textarea
              v-model="editForm.spaceDescription"
              class="w-full rounded-lg border px-3 py-2 text-sm"
              rows="2"
              maxlength="2000"
            ></textarea>
          </div>
          <div>
            <label class="mb-1 block text-xs font-medium text-neutral-500">可见性</label>
            <select v-model="editForm.spaceVisibility" class="w-full rounded-lg border px-3 py-2 text-sm">
              <option value="private">私有</option>
              <option value="public">公开</option>
              <option value="whitelist">白名单</option>
              <option value="blacklist">黑名单</option>
            </select>
          </div>
          <div v-if="isOwner">
            <label class="mb-1 block text-xs font-medium text-neutral-500">配额（GB）</label>
            <input
              v-model.number="editForm.spaceQuotaGB"
              type="number"
              min="1"
              class="w-full rounded-lg border px-3 py-2 text-sm"
            />
          </div>
          <button
            class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary/90"
            @click="saveInfo"
          >
            保存修改
          </button>
        </div>

        <!-- 成员列表 -->
        <div v-if="activeTab === 'members'" class="px-6 py-4">
          <!-- 邀请成员 -->
          <div class="mb-4 flex gap-2">
            <input
              v-model="inviteUserId"
              class="flex-1 rounded-lg border px-3 py-2 text-sm"
              placeholder="输入用户ID"
            />
            <select v-model="inviteRole" class="w-24 rounded-lg border px-2 py-2 text-sm">
              <option value="editor">编辑者</option>
              <option value="viewer">查看者</option>
              <option value="admin">管理员</option>
            </select>
            <button
              class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white hover:bg-primary/90"
              :disabled="!inviteUserId"
              @click="doInvite"
            >
              邀请
            </button>
          </div>

          <!-- 成员列表 -->
          <div v-if="members.length === 0" class="py-8 text-center text-sm text-neutral-400">
            暂无成员
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="member in members"
              :key="member.userId"
              class="flex items-center justify-between rounded-lg border px-4 py-2.5"
            >
              <div class="flex items-center gap-3">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-xs font-medium text-primary">
                  {{ member.userId.slice(0, 2).toUpperCase() }}
                </div>
                <div>
                  <p class="text-sm font-medium text-neutral-700">{{ member.userId }}</p>
                  <span class="rounded-full bg-neutral-100 px-2 py-0.5 text-[10px] text-neutral-500">
                    {{ getRoleLabel(member.role) }}
                  </span>
                </div>
              </div>
              <div v-if="member.role !== 'owner' && isOwner" class="flex items-center gap-1">
                <select
                  class="rounded border px-2 py-1 text-xs"
                  :value="member.role"
                  @change="changeRole(member.userId, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="admin">管理员</option>
                  <option value="editor">编辑者</option>
                  <option value="viewer">查看者</option>
                </select>
                <button
                  class="rounded p-1 text-xs text-red-500 hover:bg-red-50"
                  title="移除"
                  @click="removeMember(member.userId)"
                >
                  <i class="fa fa-trash"></i>
                </button>
              </div>
            </div>
          </div>
        </div>

        <!-- 加入申请 -->
        <div v-if="activeTab === 'requests' && isOwner" class="px-6 py-4">
          <div v-if="joinRequests.length === 0" class="py-8 text-center text-sm text-neutral-400">
            暂无待处理的申请
          </div>
          <div v-else class="space-y-2">
            <div
              v-for="req in joinRequests"
              :key="req.requestId"
              class="flex items-center justify-between rounded-lg border px-4 py-3"
            >
              <div>
                <p class="text-sm font-medium text-neutral-700">{{ req.userId }}</p>
                <p v-if="req.requestMessage" class="text-xs text-neutral-400">{{ req.requestMessage }}</p>
              </div>
              <div class="flex gap-2">
                <button
                  class="rounded-lg bg-green-500 px-3 py-1 text-xs font-medium text-white hover:bg-green-600"
                  @click="reviewRequest(req.userId, 'approved')"
                >
                  通过
                </button>
                <button
                  class="rounded-lg bg-red-500 px-3 py-1 text-xs font-medium text-white hover:bg-red-600"
                  @click="reviewRequest(req.userId, 'rejected')"
                >
                  拒绝
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, watch, onMounted, computed } from 'vue'
import {
  updateSpaceApi,
  listMembersApi,
  addMemberApi,
  updateMemberRoleApi,
  removeMemberApi,
  listJoinRequestsApi,
  reviewJoinRequestApi,
} from '@/api/modules/space'
import type { SpaceInfo, SpaceMember, SpaceJoinRequest } from '@/api/modules/space'
import { useSpaceStore } from '@/stores/spaceStore'

const props = defineProps<{
  visible: boolean
  space: SpaceInfo
}>()

const emit = defineEmits<{
  close: []
  refresh: []
}>()

const spaceStore = useSpaceStore()
const activeTab = ref('info')
const members = ref<SpaceMember[]>([])
const joinRequests = ref<SpaceJoinRequest[]>([])
const inviteUserId = ref('')
const inviteRole = ref('editor')

const isOwner = computed(() => {
  const member = members.value.find((m) => m.role === 'owner')
  return member?.userId === props.space.spaceOwnerId
})

const editForm = ref({
  spaceName: '',
  spaceDescription: '',
  spaceVisibility: '',
  spaceQuotaGB: 0,
})

const tabs = [
  { key: 'info', label: '基本信息' },
  { key: 'members', label: '成员管理' },
  { key: 'requests', label: '加入申请' },
]

watch(() => props.visible, (val) => {
  if (val) {
    editForm.value = {
      spaceName: props.space.spaceName,
      spaceDescription: props.space.spaceDescription || '',
      spaceVisibility: props.space.spaceVisibility,
      spaceQuotaGB: Math.round(props.space.spaceQuota / 1024 / 1024 / 1024),
    }
    activeTab.value = 'info'
    loadMembers()
    loadRequests()
  }
})

async function loadMembers() {
  try {
    const res = await listMembersApi(props.space.spaceId)
    if (res.code === 200) members.value = res.data || []
  } catch { /* ignore */ }
}

async function loadRequests() {
  try {
    const res = await listJoinRequestsApi(props.space.spaceId, 'pending')
    if (res.code === 200) joinRequests.value = res.data || []
  } catch { /* ignore */ }
}

async function saveInfo() {
  try {
    await updateSpaceApi(props.space.spaceId, {
      spaceName: editForm.value.spaceName,
      spaceDescription: editForm.value.spaceDescription,
      spaceVisibility: editForm.value.spaceVisibility,
      spaceQuota: isOwner.value ? editForm.value.spaceQuotaGB * 1024 * 1024 * 1024 : undefined,
    })
    await spaceStore.refreshSpaces()
    emit('refresh')
  } catch { /* ignore */ }
}

async function doInvite() {
  if (!inviteUserId.value) return
  try {
    await addMemberApi(props.space.spaceId, inviteUserId.value, inviteRole.value)
    inviteUserId.value = ''
    await loadMembers()
  } catch { /* ignore */ }
}

async function changeRole(userId: string, role: string) {
  try {
    await updateMemberRoleApi(props.space.spaceId, userId, role)
    await loadMembers()
  } catch { /* ignore */ }
}

async function removeMember(userId: string) {
  try {
    await removeMemberApi(props.space.spaceId, userId)
    await loadMembers()
  } catch { /* ignore */ }
}

async function reviewRequest(userId: string, action: string) {
  try {
    await reviewJoinRequestApi(props.space.spaceId, userId, action)
    await loadRequests()
    if (action === 'approved') await loadMembers()
  } catch { /* ignore */ }
}

function getSpaceTypeLabel(type: string): string {
  const labels: Record<string, string> = {
    personal: '个人空间', enterprise: '企业空间', public: '公共空间', team: '团队空间',
  }
  return labels[type] || '空间'
}

function getRoleLabel(role: string): string {
  const labels: Record<string, string> = {
    owner: '所有者', admin: '管理员', editor: '编辑者', viewer: '查看者',
  }
  return labels[role] || role
}
</script>