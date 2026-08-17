<template>
  <main class="min-h-screen bg-neutral-50 px-4 py-8 sm:px-6">
    <div class="mx-auto max-w-4xl">
      <button class="mb-5 text-sm text-neutral-500 hover:text-primary" @click="router.back()"><i class="fa fa-arrow-left mr-1"></i>返回团队协作</button>
      <div v-if="loading" class="h-72 animate-pulse rounded-2xl bg-neutral-200"></div>
      <div v-else-if="!space" class="rounded-2xl border bg-white py-20 text-center text-neutral-500">空间不存在或你无权查看</div>
      <section v-else class="overflow-hidden rounded-2xl border bg-white shadow-sm">
        <div class="bg-gradient-to-br from-primary/10 via-white to-blue-50 px-6 py-8 sm:px-10">
          <div class="flex flex-wrap items-start justify-between gap-6"><div class="flex items-center gap-4"><div class="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-2xl text-white"><i class="fa fa-users"></i></div><div><h1 class="text-2xl font-bold text-neutral-900">{{ space.spaceName }}</h1><p class="mt-1 text-sm text-neutral-500">{{ typeLabel(space.spaceType) }} · {{ policyLabel(space.joinPolicy) }}</p></div></div><span class="rounded-full bg-green-100 px-3 py-1 text-xs font-medium text-green-700">可发现</span></div>
          <p class="mt-6 max-w-2xl text-sm leading-6 text-neutral-600">{{ space.spaceDescription || '空间创建者暂未填写描述。' }}</p>
        </div>
        <div class="grid gap-6 border-t px-6 py-6 sm:grid-cols-3 sm:px-10"><div><p class="text-xs text-neutral-400">空间所有者</p><p class="mt-1 font-medium text-neutral-800">{{ space.ownerUsername || space.spaceOwnerId }}</p></div><div><p class="text-xs text-neutral-400">成员数量</p><p class="mt-1 font-medium text-neutral-800">{{ space.memberCount ?? space.spaceFileCount ?? 0 }} 人</p></div><div><p class="text-xs text-neutral-400">创建时间</p><p class="mt-1 font-medium text-neutral-800">{{ space.spaceCreatedAt || '—' }}</p></div></div>
        <div class="flex flex-wrap items-center justify-between gap-3 border-t bg-neutral-50 px-6 py-4 sm:px-10"><p class="text-sm text-neutral-500">{{ actionDescription }}</p><div class="flex gap-2"><button v-if="space.isMember" class="rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-white hover:bg-primary/90" @click="enter">进入空间</button><button v-else-if="space.joinPolicy !== 'invite_only' || inviteToken" class="rounded-lg bg-primary px-5 py-2.5 text-sm font-medium text-white hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50" :disabled="joining || space.currentRequestStatus === 'pending'" @click="join"><i v-if="joining" class="fa fa-spinner fa-spin mr-1"></i>{{ inviteToken ? '使用邀请加入' : space.currentRequestStatus === 'pending' ? '申请已提交' : space.joinPolicy === 'open' ? '立即加入' : '申请加入' }}</button><button v-else class="cursor-not-allowed rounded-lg border border-neutral-200 bg-white px-5 py-2.5 text-sm text-neutral-400" disabled>仅限邀请加入</button></div></div>
      </section>
    </div>
  </main>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getCollaborationSpacePreviewApi, joinCollaborationSpaceApi, listMyCollaborationSpacesApi, listMyJoinRequestsApi, type SpacePreview } from '@/api/modules/space'
import { useToastStore } from '@/stores/toastStore'
import { useSpaceStore } from '@/stores/spaceStore'

const route = useRoute(); const router = useRouter(); const toast = useToastStore(); const spaceStore = useSpaceStore()
const space = ref<SpacePreview | null>(null); const loading = ref(true); const joining = ref(false); const inviteToken = computed(() => typeof route.query.invite === 'string' ? route.query.invite : '')
const actionDescription = computed(() => space.value?.isMember ? '你已加入该空间，可以直接进入文件协作。' : inviteToken.value ? '你正在使用空间管理员发出的邀请链接。' : space.value?.joinPolicy === 'open' ? '该空间允许登录用户直接加入。' : space.value?.joinPolicy === 'invite_only' ? '需要空间管理员提供邀请链接或邀请码。' : '提交申请后等待空间管理员审批。')
async function load() { loading.value = true; try { const [res, mine, reqs] = await Promise.all([getCollaborationSpacePreviewApi(String(route.params.spaceId)), listMyCollaborationSpacesApi(), listMyJoinRequestsApi()]); if (res.code === 200) { const data = res.data; data.isMember = (mine.data || []).some((item) => item.spaceId === data.spaceId); data.currentRequestStatus = (reqs.data || []).find((item) => item.spaceId === data.spaceId && item.status === 'pending')?.status || null; space.value = data } } catch { space.value = null } finally { loading.value = false } }
async function join() { if (!space.value) return; const token = inviteToken.value || (space.value.joinPolicy === 'invite_only' ? window.prompt('请输入邀请令牌') || undefined : undefined); joining.value = true; try { await joinCollaborationSpaceApi(space.value.spaceId, '', token); toast.showToast(token || space.value.joinPolicy === 'open' ? '已加入空间' : '申请已提交', 'success'); await load() } catch { toast.showToast('操作失败，请检查空间策略或稍后重试', 'error') } finally { joining.value = false } }
async function enter() { if (!space.value) return; await spaceStore.refreshSpaces(); if (await spaceStore.switchSpace(space.value.spaceId)) router.push({ path: '/app', query: { space_id: space.value.spaceId } }) }
function typeLabel(type: string) { return ({ enterprise: '企业空间', team: '团队空间', private: '私有空间' } as Record<string, string>)[type] || '协作空间' }
function policyLabel(policy?: string) { return ({ open: '开放加入', approval_required: '需要审批', invite_only: '仅限邀请' } as Record<string, string>)[policy || 'approval_required'] || '需要审批' }
onMounted(load)
</script>
