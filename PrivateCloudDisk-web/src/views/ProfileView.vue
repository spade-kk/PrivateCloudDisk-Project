<template>
  <div class="mx-auto max-w-5xl space-y-5 pb-10">
    <!-- ===== 个人信息头部 ===== -->
    <div class="responsive-panel overflow-visible p-0">
      <div class="h-28 rounded-t-xl bg-gradient-to-r from-primary via-primary to-secondary sm:h-32"></div>
      <div class="relative -mt-12 px-5 pb-5 sm:-mt-14 sm:px-7 sm:pb-6">
        <div class="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
          <div class="flex items-end gap-4 sm:gap-5">
            <!-- 头像 -->
            <div class="relative shrink-0 cursor-pointer group" @click="triggerAvatarUpload">
              <div class="h-20 w-20 overflow-hidden rounded-full border-4 border-white bg-white shadow-lg sm:h-24 sm:w-24">
                <img v-if="userInfo.image_path" :src="userInfo.image_path" class="h-full w-full object-cover" />
                <div v-else class="flex h-full w-full items-center justify-center bg-neutral-100">
                  <i class="fa fa-user text-3xl text-neutral-300 sm:text-4xl"></i>
                </div>
              </div>
              <div class="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 opacity-0 transition-opacity group-hover:opacity-100">
                <i class="fa fa-camera text-lg text-white"></i>
              </div>
              <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="handleAvatarUpload" />
            </div>
            <div class="pb-1">
              <h1 class="text-xl font-bold text-neutral-800 sm:text-2xl">{{ userInfo.name || '未设置昵称' }}</h1>
              <div class="mt-1 flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs text-neutral-500 sm:text-sm">
                <span class="flex max-w-[200px] items-center gap-1 truncate"><i class="fa fa-user-circle-o shrink-0 text-[10px] sm:text-xs"></i><span class="truncate">{{ userInfo.account || '-' }}</span></span>
                <span class="flex max-w-[180px] items-center gap-1 truncate"><i class="fa fa-envelope-o shrink-0 text-[10px] sm:text-xs"></i><span class="truncate">{{ userInfo.email || '未绑定' }}</span></span>
                <span class="flex max-w-[140px] items-center gap-1 truncate"><i class="fa fa-mobile shrink-0 text-[10px] sm:text-xs"></i><span class="truncate">{{ formatPhone(userInfo.phone_number) }}</span></span>
              </div>
            </div>
          </div>
          <div class="flex gap-2 self-end sm:self-auto">
            <button
              v-if="!isEditing"
              @click="startEdit"
              class="inline-flex items-center rounded-lg border border-neutral-200 px-4 py-2 text-sm font-medium text-neutral-600 transition hover:border-neutral-300 hover:bg-neutral-50 active:scale-[0.98]"
            >
              <i class="fa fa-pencil mr-1.5 text-xs"></i>编辑资料
            </button>
            <div v-else class="flex gap-2">
              <button @click="cancelEdit" class="rounded-lg border border-neutral-200 px-4 py-2 text-sm text-neutral-500 transition hover:bg-neutral-50">取消</button>
              <button
                @click="saveProfile"
                :disabled="saving"
                class="rounded-lg bg-primary px-4 py-2 text-sm font-medium text-white transition hover:bg-primary/90 disabled:opacity-60"
              >
                <i v-if="saving" class="fa fa-spinner fa-spin mr-1"></i>保存
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 双栏布局 ===== -->
    <div class="grid gap-5 lg:grid-cols-5">
      <!-- ===== 左栏：存储 + 安全 ===== -->
      <div class="space-y-5 lg:col-span-2">
        <!-- 存储空间 -->
        <div class="responsive-panel p-5 sm:p-6">
          <h2 class="mb-4 flex items-center gap-2 text-[15px] font-bold text-neutral-700">
            <i class="fa fa-database text-primary"></i> 存储空间
          </h2>
          <!-- 环形进度 -->
          <div class="mb-5 flex justify-center">
            <div class="relative inline-flex items-center justify-center">
              <svg class="h-36 w-36 -rotate-90 sm:h-40 sm:w-40" viewBox="0 0 120 120">
                <circle cx="60" cy="60" r="52" fill="none" stroke="#f1f5f9" stroke-width="10" />
                <circle
                  cx="60" cy="60" r="52" fill="none" stroke="url(#storageGrad)" stroke-width="10"
                  stroke-linecap="round"
                  :stroke-dasharray="dashArray"
                  class="transition-all duration-1000 ease-out"
                />
                <defs>
                  <linearGradient id="storageGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                    <stop offset="0%" stop-color="var(--color-primary)" />
                    <stop offset="100%" stop-color="var(--color-secondary)" />
                  </linearGradient>
                </defs>
              </svg>
              <div class="absolute text-center">
                <p class="text-2xl font-bold text-neutral-800 sm:text-3xl">{{ quotaPercent }}%</p>
                <p class="text-[11px] text-neutral-400">已使用</p>
              </div>
            </div>
          </div>
          <!-- 统计 -->
          <div class="grid grid-cols-3 gap-2">
            <div class="rounded-lg bg-neutral-50 p-3 text-center">
              <p class="text-base font-bold text-neutral-700">{{ formatCount(quota.file_count || 0) }}</p>
              <p class="text-[11px] text-neutral-400">文件</p>
            </div>
            <div class="rounded-lg bg-neutral-50 p-3 text-center">
              <p class="text-base font-bold text-neutral-700">{{ formatSize(quota.used_capacity || 0) }}</p>
              <p class="text-[11px] text-neutral-400">已用</p>
            </div>
            <div class="rounded-lg bg-neutral-50 p-3 text-center">
              <p class="text-base font-bold text-neutral-700">{{ formatSize(quota.total_capacity || 0) }}</p>
              <p class="text-[11px] text-neutral-400">总量</p>
            </div>
          </div>
        </div>

        <!-- 安全设置 -->
        <div class="responsive-panel p-5 sm:p-6">
          <h2 class="mb-4 flex items-center gap-2 text-[15px] font-bold text-neutral-700">
            <i class="fa fa-shield text-primary"></i> 安全设置
          </h2>
          <div class="space-y-3">
            <!-- 密码 -->
            <div class="flex items-center justify-between rounded-lg border border-neutral-100 p-3 transition hover:border-neutral-200">
              <div class="flex items-center gap-3 min-w-0">
                <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-blue-50"><i class="fa fa-lock text-xs text-blue-500"></i></span>
                <div class="min-w-0">
                  <p class="text-sm font-medium text-neutral-700">登录密码</p>
                  <p class="truncate text-xs text-neutral-400">建议定期更换以确保安全</p>
                </div>
              </div>
              <button @click="showPasswordSheet = true" class="shrink-0 rounded-lg border px-3 py-1 text-xs text-neutral-500 transition hover:border-primary hover:text-primary">修改</button>
            </div>
            <!-- 设备 -->
            <div class="flex items-center justify-between rounded-lg border border-neutral-100 p-3 transition hover:border-neutral-200">
              <div class="flex items-center gap-3 min-w-0">
                <span class="flex h-8 w-8 shrink-0 items-center justify-center rounded-full bg-green-50"><i class="fa fa-laptop text-xs text-green-500"></i></span>
                <div class="min-w-0">
                  <p class="text-sm font-medium text-neutral-700">登录设备</p>
                  <p class="truncate text-xs text-neutral-400">管理已登录设备</p>
                </div>
              </div>
              <span class="shrink-0 rounded-lg border border-neutral-100 px-3 py-1 text-xs text-neutral-300">即将上线</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ===== 右栏：账号详情 ===== -->
      <div class="space-y-5 lg:col-span-3">
        <!-- 账号信息 -->
        <div class="responsive-panel divide-y divide-neutral-100 p-5 sm:p-6">
          <h2 class="mb-5 flex items-center gap-2 text-[15px] font-bold text-neutral-700">
            <i class="fa fa-id-card-o text-primary"></i> 账号信息
          </h2>

          <!-- 编辑模式 -->
          <template v-if="isEditing">
            <div class="space-y-4 pt-0.5">
              <div class="grid gap-4 sm:grid-cols-2">
                <div>
                  <label class="mb-1 block text-xs font-medium text-neutral-500">昵称</label>
                  <div class="relative">
                    <span class="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-300"><i class="fa fa-user-o text-xs"></i></span>
                    <input v-model="editForm.name" class="w-full rounded-lg border border-neutral-200 py-2.5 pl-9 pr-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="请输入昵称" />
                  </div>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-medium text-neutral-500">邮箱</label>
                  <div class="relative">
                    <span class="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-300"><i class="fa fa-envelope-o text-xs"></i></span>
                    <input v-model="editForm.email" type="email" class="w-full rounded-lg border border-neutral-200 py-2.5 pl-9 pr-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="请输入邮箱" />
                  </div>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-medium text-neutral-500">手机号</label>
                  <div class="relative">
                    <span class="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-300"><i class="fa fa-mobile text-sm"></i></span>
                    <input v-model="editForm.phone_number" class="w-full rounded-lg border border-neutral-200 py-2.5 pl-9 pr-3 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="请输入手机号" />
                  </div>
                </div>
                <div>
                  <label class="mb-1 block text-xs font-medium text-neutral-500">账号</label>
                  <div class="relative">
                    <span class="absolute left-3 top-1/2 -translate-y-1/2 text-neutral-300"><i class="fa fa-at text-xs"></i></span>
                    <input :value="userInfo.account" disabled class="w-full cursor-not-allowed rounded-lg border border-neutral-100 bg-neutral-50 py-2.5 pl-9 pr-3 text-sm text-neutral-400" />
                  </div>
                  <p class="mt-1 text-[11px] text-neutral-400">账号不可修改</p>
                </div>
              </div>
            </div>
          </template>

          <!-- 只读模式 -->
          <template v-else>
            <div class="space-y-0">
              <div v-for="row in readOnlyRows" :key="row.label" class="flex items-center border-b border-neutral-50 py-3.5 last:border-b-0">
                <span class="w-20 shrink-0 text-sm text-neutral-400">{{ row.label }}</span>
                <span class="min-w-0 text-sm font-medium text-neutral-700" :class="{ 'text-neutral-300': row.empty }">
                  {{ row.value }}
                </span>
                <span v-if="row.copy" class="ml-2 shrink-0 cursor-pointer text-neutral-300 hover:text-primary" @click="copyText(row.value)" title="复制">
                  <i class="fa fa-clipboard text-xs"></i>
                </span>
              </div>
            </div>
          </template>
        </div>

        <!-- 危险操作 -->
        <div class="rounded-xl border border-red-100 bg-white p-5 shadow-card sm:p-6">
          <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
            <div class="flex items-start gap-3">
              <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-red-50"><i class="fa fa-exclamation-triangle text-sm text-red-500"></i></span>
              <div>
                <h2 class="text-[15px] font-bold text-neutral-700">注销账号</h2>
                <p class="text-xs text-neutral-400">永久删除账号及所有数据，此操作不可恢复</p>
              </div>
            </div>
            <button @click="showDeleteConfirm = true" class="shrink-0 rounded-lg border border-red-200 px-4 py-2 text-sm text-red-500 transition hover:bg-red-50 active:scale-[0.98]">注销账号</button>
          </div>
        </div>
      </div>
    </div>

    <!-- ===== 密码修改抽屉 ===== -->
    <Teleport to="body">
      <Transition name="sheet">
        <div v-if="showPasswordSheet" class="fixed inset-0 z-50 flex items-end justify-center sm:items-center" @click.self="showPasswordSheet = false">
          <div class="absolute inset-0 bg-black/40 backdrop-blur-sm"></div>
          <div class="relative w-full max-w-md rounded-t-2xl bg-white p-6 shadow-xl sm:rounded-2xl">
            <div class="mb-5 flex items-center justify-between">
              <h3 class="text-lg font-bold text-neutral-800">修改登录密码</h3>
              <button @click="showPasswordSheet = false" class="flex h-8 w-8 items-center justify-center rounded-full text-neutral-400 hover:bg-neutral-100 hover:text-neutral-600">
                <i class="fa fa-times"></i>
              </button>
            </div>
            <form @submit.prevent="changePassword" class="space-y-4">
              <div>
                <label class="mb-1.5 block text-xs font-medium text-neutral-500">原密码</label>
                <input v-model="passwordForm.old" type="password" class="w-full rounded-lg border border-neutral-200 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="输入原密码" />
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-neutral-500">新密码</label>
                <input v-model="passwordForm.new" type="password" class="w-full rounded-lg border border-neutral-200 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="至少 6 位" />
              </div>
              <div>
                <label class="mb-1.5 block text-xs font-medium text-neutral-500">确认新密码</label>
                <input v-model="passwordForm.confirm" type="password" class="w-full rounded-lg border border-neutral-200 px-4 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-2 focus:ring-primary/10" placeholder="再次输入新密码" />
              </div>
              <div class="flex gap-3 pt-1">
                <button type="button" @click="showPasswordSheet = false" class="flex-1 rounded-lg border px-4 py-2.5 text-sm text-neutral-500 transition hover:bg-neutral-50">取消</button>
                <button type="submit" :disabled="changingPassword" class="flex-1 rounded-lg bg-primary px-4 py-2.5 text-sm font-medium text-white transition hover:bg-primary/90 disabled:opacity-60">
                  <i v-if="changingPassword" class="fa fa-spinner fa-spin mr-1"></i>确认修改
                </button>
              </div>
            </form>
          </div>
        </div>
      </Transition>
    </Teleport>

    <!-- ===== 注销确认弹窗 ===== -->
    <Teleport to="body">
      <Transition name="fade">
        <div v-if="showDeleteConfirm" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div class="w-full max-w-sm rounded-2xl bg-white p-6 shadow-xl">
            <div class="mb-5 flex items-center gap-3">
              <span class="flex h-10 w-10 items-center justify-center rounded-full bg-red-100"><i class="fa fa-exclamation-triangle text-red-500"></i></span>
              <div>
                <h3 class="font-bold text-neutral-800">确认注销账号</h3>
                <p class="text-xs text-neutral-400">此操作不可撤销</p>
              </div>
            </div>
            <p class="mb-3 text-sm text-neutral-500">请输入 <code class="rounded bg-neutral-100 px-1.5 py-0.5 text-xs font-bold text-red-500">DELETE</code> 确认操作：</p>
            <input v-model="deleteConfirmText" class="mb-4 w-full rounded-lg border px-4 py-2.5 text-sm outline-none transition focus:border-red-400 focus:ring-2 focus:ring-red-50" placeholder="请输入 DELETE" @keyup.enter="deleteAccount" />
            <div class="flex gap-3">
              <button @click="showDeleteConfirm = false; deleteConfirmText = ''" class="flex-1 rounded-lg border px-4 py-2.5 text-sm text-neutral-500 transition hover:bg-neutral-50">取消</button>
              <button @click="deleteAccount" :disabled="deleteConfirmText !== 'DELETE' || deleting" class="flex-1 rounded-lg bg-red-500 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-red-600 disabled:opacity-50">
                <i v-if="deleting" class="fa fa-spinner fa-spin mr-1"></i>确认注销
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useToastStore } from '@/stores/toastStore'
import { useAuthStore } from '@/stores/authStore'
import {
  getMyUserInfoApi,
  updateMyUserInfoApi,
  changeMyUserPasswordApi,
  uploadUserAvatarApi,
  deleteMyUserApi,
} from '@/api/modules/users'
import { getMyUserQuotaInfoApi } from '@/api/modules/quotas'

const router = useRouter()
const toast = useToastStore()
const auth = useAuthStore()

const userInfo = ref({ name: '', email: '', phone_number: '', account: '', image_path: '', file_count: 0 })
const quota = ref({ used_capacity: 0, total_capacity: 0, file_count: 0 })

const isEditing = ref(false)
const saving = ref(false)
const editForm = ref({ name: '', email: '', phone_number: '' })
const avatarInput = ref(null)

const showDeleteConfirm = ref(false)
const deleteConfirmText = ref('')
const deleting = ref(false)

const showPasswordSheet = ref(false)
const changingPassword = ref(false)
const passwordForm = ref({ old: '', new: '', confirm: '' })

// ── computed ──
const quotaPercent = computed(() => {
  if (!quota.value.total_capacity) return 0
  return Math.min(100, Math.round((quota.value.used_capacity / quota.value.total_capacity) * 100))
})

const circumference = 2 * Math.PI * 52 // r=52
const dashArray = computed(() => {
  const filled = (quotaPercent.value / 100) * circumference
  return `${filled} ${circumference}`
})

const readOnlyRows = computed(() => [
  { label: '昵称', value: userInfo.value.name || '未设置', empty: !userInfo.value.name },
  { label: '邮箱', value: userInfo.value.email || '未绑定', empty: !userInfo.value.email },
  { label: '手机号', value: formatPhone(userInfo.value.phone_number) },
  { label: '账号', value: userInfo.value.account || '-', copy: true },
])

// ── 编辑 ──
const startEdit = () => {
  editForm.value = { name: userInfo.value.name, email: userInfo.value.email, phone_number: userInfo.value.phone_number }
  isEditing.value = true
}
const cancelEdit = () => { isEditing.value = false }
const saveProfile = async () => {
  saving.value = true
  try {
    const res = await updateMyUserInfoApi(editForm.value.email, editForm.value.name, editForm.value.phone_number)
    if (res.code === 200) {
      Object.assign(userInfo.value, editForm.value)
      isEditing.value = false
      toast.showToast('个人信息已更新', 'success')
    } else {
      toast.showToast(res.message || '更新失败', 'error')
    }
  } catch { toast.showToast('网络异常', 'error') }
  finally { saving.value = false }
}

// ── 头像 ──
const triggerAvatarUpload = () => avatarInput.value?.click()
const handleAvatarUpload = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  try {
    const res = await uploadUserAvatarApi(file)
    if (res.code === 200) {
      userInfo.value.image_path = res.data || URL.createObjectURL(file)
      toast.showToast('头像已更新', 'success')
    } else { toast.showToast(res.message || '上传失败', 'error') }
  } catch { toast.showToast('上传失败', 'error') }
  finally { if (avatarInput.value) avatarInput.value.value = '' }
}

// ── 密码 ──
const changePassword = async () => {
  if (!passwordForm.value.old) return toast.showToast('请输入原密码', 'error')
  if (passwordForm.value.new.length < 6) return toast.showToast('新密码至少6位', 'error')
  if (passwordForm.value.new !== passwordForm.value.confirm) return toast.showToast('两次密码不一致', 'error')
  changingPassword.value = true
  try {
    const res = await changeMyUserPasswordApi(passwordForm.value.old, passwordForm.value.new)
    if (res.code === 200) {
      toast.showToast('密码已更新', 'success')
      showPasswordSheet.value = false
      passwordForm.value = { old: '', new: '', confirm: '' }
    } else { toast.showToast(res.message || '修改失败', 'error') }
  } catch (e) { toast.showToast(e?.message || '修改失败', 'error') }
  finally { changingPassword.value = false }
}

// ── 注销 ──
const deleteAccount = async () => {
  if (deleteConfirmText.value !== 'DELETE') return
  deleting.value = true
  try {
    const res = await deleteMyUserApi()
    if (res.code === 200) { toast.showToast('账号已注销', 'success'); auth.logout(); router.push('/login') }
    else { toast.showToast(res.message || '注销失败', 'error') }
  } catch (e) { toast.showToast(e?.message || '注销失败', 'error') }
  finally { deleting.value = false; showDeleteConfirm.value = false; deleteConfirmText.value = '' }
}

// ── 工具 ──
const copyText = (text) => {
  if (!text || text === '-') return
  navigator.clipboard.writeText(text).then(() => toast.showToast('已复制', 'success'))
}

const formatSize = (bytes) => {
  if (!bytes || bytes < 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i > 0 ? 1 : 0) + ' ' + units[i]
}
const formatCount = (num) => {
  if (num < 1000) return String(num)
  if (num < 1000000) return (num / 1000).toFixed(1) + 'K'
  return (num / 1000000).toFixed(1) + 'M'
}
const formatPhone = (phone) => {
  if (!phone) return '未绑定'
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

// ── 初始化 ──
onMounted(async () => {
  try {
    const [u, q] = await Promise.all([getMyUserInfoApi(), getMyUserQuotaInfoApi()])
    if (u.code === 200 && u.data) {
      const d = u.data
      userInfo.value = {
        name: d.name || '',
        email: d.email || '',
        phone_number: d.phone_number || '',
        account: d.account || '',
        image_path: d.image_path || '',
        file_count: d.file_count || 0,
      }
    }
    if (q.code === 200 && q.data) {
      quota.value = {
        used_capacity: q.data.used_capacity || 0,
        total_capacity: q.data.total_capacity || 0,
        file_count: q.data.file_count || 0,
      }
    }
  } catch { /* silent */ }
})
</script>

<style scoped>
/* ── 密码抽屉动画 ── */
.sheet-enter-active { transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1); }
.sheet-leave-active { transition: all 0.2s ease-in; }
.sheet-enter-from .absolute { opacity: 0; }
.sheet-leave-to .absolute { opacity: 0; }
.sheet-enter-from .relative { transform: translateY(100%); }
@media (min-width: 640px) {
  .sheet-enter-from .relative { transform: translateY(20px) scale(0.96); opacity: 0; }
  .sheet-leave-to .relative { transform: translateY(20px) scale(0.96); opacity: 0; }
}

/* ── 弹窗动画 ── */
.fade-enter-active { transition: all 0.2s ease-out; }
.fade-leave-active { transition: all 0.15s ease-in; }
.fade-enter-from,
.fade-leave-to { opacity: 0; }
</style>