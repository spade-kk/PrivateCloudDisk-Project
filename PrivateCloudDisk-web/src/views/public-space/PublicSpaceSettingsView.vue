<template>
  <div class="repository-settings min-h-screen bg-[#f6f8fa]">
    <header class="border-b border-[#d0d7de] bg-white"><div class="mx-auto flex max-w-4xl items-center gap-3 px-4 py-4 lg:px-8"><button class="settings-back" @click="router.push(`/repo/${spaceId}`)"><i class="fa fa-arrow-left mr-2"></i>返回仓库</button><h1 class="text-lg font-semibold">仓库设置</h1></div></header>
    <main class="mx-auto max-w-4xl space-y-5 px-4 py-6 lg:px-8">
      <div v-if="loading" class="repo-settings-card animate-pulse"><div class="h-5 w-40 rounded bg-[#eaeef2]"></div><div class="mt-4 h-10 rounded bg-[#eaeef2]"></div></div>
      <div v-else-if="!isOwner" class="repo-settings-card text-sm text-red-700">只有仓库所有者可以修改公开仓库设置。</div>
      <form v-else class="repo-settings-card space-y-5" @submit.prevent="save">
        <div><h2 class="text-base font-semibold">基本信息</h2><p class="mt-1 text-sm text-[#57606a]">公开仓库不参与成员加入流程，名称和描述用于仓库主页展示。</p></div>
        <label class="field"><span>仓库名称</span><input v-model.trim="form.name" maxlength="64" required /></label>
        <label class="field"><span>描述</span><textarea v-model="form.description" maxlength="500" rows="4"></textarea></label>
        <div><h2 class="text-base font-semibold">公开权限</h2><div class="mt-3 space-y-3"><label v-for="item in permissionItems" :key="item.key" class="permission-row"><span><strong>{{ item.label }}</strong><small>{{ item.description }}</small></span><input v-model="form[item.key]" type="checkbox" /></label></div></div>
        <p v-if="error" class="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{{ error }}</p>
        <div class="flex justify-end gap-2 border-t border-[#d8dee4] pt-4"><button type="button" class="settings-button" @click="router.push(`/repo/${spaceId}`)">取消</button><button class="settings-button primary" :disabled="saving">{{ saving ? '保存中…' : '保存设置' }}</button></div>
      </form>
    </main>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { computed } from 'vue'
import { useAuthStore } from '@/stores/authStore'
import { getPublicSpaceApi, updatePublicSpaceApi, type PublicSpaceDetail } from '@/api/modules/publicSpaces'

const route = useRoute(); const router = useRouter(); const authStore = useAuthStore(); const spaceId = String(route.params.spaceId)
const loading = ref(true); const saving = ref(false); const error = ref('')
const ownerId = ref('')
const ownerName = ref('')
const isOwner = computed(() => ownerId.value === authStore.user?.id
  || ownerName.value === authStore.user?.name || ownerName.value === authStore.user?.account)
const form = reactive({ name: '', description: '', allowPublicBrowse: true, allowPublicDownload: true, allowPublicUpload: false })
const permissionItems = [
  { key: 'allowPublicBrowse' as const, label: '允许公开浏览', description: '登录用户可以浏览仓库目录和 README。' },
  { key: 'allowPublicDownload' as const, label: '允许公开下载', description: '登录用户可以下载仓库中的文件。' },
  { key: 'allowPublicUpload' as const, label: '允许公开上传', description: '开启后任何登录用户都可以向仓库上传文件。' },
]
onMounted(async () => { try { await authStore.fetchUserInfo(); const response = await getPublicSpaceApi(spaceId); apply(response.data) } catch (cause: any) { error.value = cause?.message || '加载仓库设置失败' } finally { loading.value = false } })
function apply(data: PublicSpaceDetail) { ownerId.value = data.ownerId; ownerName.value = data.ownerName; form.name = data.spaceName; form.description = data.description || ''; form.allowPublicBrowse = data.allowPublicBrowse; form.allowPublicDownload = data.allowPublicDownload; form.allowPublicUpload = data.allowPublicUpload }
async function save() { saving.value = true; error.value = ''; try { const response = await updatePublicSpaceApi(spaceId, form); apply(response.data); router.push(`/repo/${spaceId}`) } catch (cause: any) { error.value = cause?.message || '保存失败，请稍后重试' } finally { saving.value = false } }
</script>

<style scoped>
.repo-settings-card { border:1px solid #d0d7de;border-radius:8px;background:#fff;padding:24px;box-shadow:0 1px 2px rgba(27,31,36,.04); }
.settings-back { color:#0969da;font-size:13px; }
.field { display:flex;flex-direction:column;gap:6px;font-size:13px;font-weight:600; }
.field input,.field textarea { border:1px solid #d0d7de;border-radius:6px;padding:9px 10px;font-size:14px;font-weight:400;outline:none; }
.field input:focus,.field textarea:focus { border-color:#0969da;box-shadow:0 0 0 3px rgba(9,105,218,.12); }
.permission-row { display:flex;align-items:center;justify-content:space-between;gap:16px;border:1px solid #d8dee4;border-radius:6px;padding:12px; }
.permission-row span { display:flex;flex-direction:column;gap:3px; }.permission-row small { color:#57606a;font-weight:400; }
.settings-button { min-height:34px;border:1px solid #d0d7de;border-radius:6px;padding:0 14px;font-size:13px; }.settings-button.primary { background:#2da44e;border-color:#2da44e;color:white; }
</style>
