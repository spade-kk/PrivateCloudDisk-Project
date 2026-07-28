<template>
  <div class="space-y-4 sm:space-y-6">
    <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
      <div>
        <h1 class="text-xl font-bold sm:text-2xl">分享管理</h1>
        <p class="mt-1 text-sm text-neutral-500">管理您创建的所有分享链接</p>
      </div>
      <button
        @click="showCreateDialog = true"
        class="touch-button flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90"
      >
        <i class="fa fa-plus"></i><span>新建分享</span>
      </button>
    </div>

    <div v-if="shareStore.loading" class="flex justify-center py-10">
      <LoadingSpinner />
    </div>

    <div v-else-if="shareStore.shares.length === 0" class="responsive-panel p-8 text-center text-neutral-400 sm:p-10">
      <i class="fa fa-share-alt mb-2 text-4xl"></i>
      <p class="text-lg font-medium">暂无分享链接</p>
      <p class="mt-1 text-sm">点击"新建分享"创建您的第一个分享链接</p>
    </div>

    <div v-else class="space-y-3">
      <div class="mb-3 flex items-center gap-2 text-sm text-neutral-500">
        <i class="fa fa-filter"></i>
        <span>共 {{ shareStore.shares.length }} 个分享</span>
      </div>
      <!-- AUDIT FIX [2.4]（需求一-4/7）:
           原 :share.share-page 是 Vue 动态参数/修饰符误写，不能向组件传入 share 对象；
           新行为恢复 ShareLinkItem 声明的标准 share prop。 -->
      <ShareLinkItem
        v-for="share in shareStore.shares"
        :key="share.share_id"
        :share="share"
        @revoke="handleRevoke"
        @view-detail="handleViewDetail(share.share_id)"
      />
    </div>

    <!-- 创建分享对话框 -->
    <CreateShareDialog
      :visible="showCreateDialog"
      @close="showCreateDialog = false"
      @created="handleCreated"
    />

    <!-- 分享详情弹窗 -->
    <Teleport to="body">
      <Transition name="dialog-fade">
        <div
          v-if="showDetailDialog"
          class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
          @click.self="showDetailDialog = false"
        >
          <div class="fade-in w-full max-w-2xl max-h-[80vh] overflow-y-auto rounded-xl bg-white p-5 shadow-lg sm:p-6">
            <!-- 标题栏 -->
            <div class="mb-5 flex items-center justify-between">
              <h2 class="text-lg font-bold text-neutral-700 sm:text-xl">
                <i class="fa fa-info-circle mr-2 text-primary"></i>分享详情
              </h2>
              <button @click="showDetailDialog = false" class="icon-button -mr-2" title="关闭">
                <i class="fa fa-times"></i>
              </button>
            </div>

            <div v-if="detailLoading" class="flex justify-center py-10">
              <div class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
            </div>

            <div v-else-if="detailData" class="space-y-4">
              <!-- 基本信息 -->
              <div class="grid grid-cols-2 gap-4 text-sm">
                <div>
                  <span class="text-neutral-400">分享名称</span>
                  <p class="mt-0.5 font-medium text-neutral-700">{{ detailData.share_name }}</p>
                </div>
                <div>
                  <span class="text-neutral-400">状态</span>
                  <p class="mt-0.5">
                    <span
                      class="inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium"
                      :style="{ color: getShareStatusColor(detailData.share_status), backgroundColor: getShareStatusColor(detailData.share_status) + '15' }"
                    >
                      {{ getShareStatusText(detailData.share_status) }}
                    </span>
                  </p>
                </div>
                <div>
                  <span class="text-neutral-400">浏览次数</span>
                  <p class="mt-0.5 font-medium text-neutral-700">{{ detailData.share_view_count }} 次</p>
                </div>
                <div>
                  <span class="text-neutral-400">过期时间</span>
                  <p class="mt-0.5 font-medium text-neutral-700">
                    {{ detailData.share_expires_at ? new Date(detailData.share_expires_at).toLocaleDateString('zh-CN') : '永久有效' }}
                  </p>
                </div>
                <div>
                  <span class="text-neutral-400">创建时间</span>
                  <p class="mt-0.5 font-medium text-neutral-700">
                    {{ new Date(detailData.share_created_at).toLocaleString('zh-CN') }}
                  </p>
                </div>
                <div>
                  <span class="text-neutral-400">资源数量</span>
                  <p class="mt-0.5 font-medium text-neutral-700">{{ detailData.resource_count }} 项</p>
                </div>
              </div>

              <!-- 提取码（如果有） -->
              <div v-if="detailData.share_has_password && detailData.share_password" class="rounded-lg bg-neutral-50 p-4">
                <div class="flex items-center justify-between">
                  <div>
                    <span class="text-sm text-neutral-400">提取码</span>
                    <p class="mt-0.5 text-lg font-mono font-bold tracking-widest text-neutral-700">
                      {{ showingPassword ? detailData.share_password : '****' }}
                    </p>
                  </div>
                  <div class="flex items-center gap-2">
                    <button
                      @click="showingPassword = !showingPassword"
                      class="rounded-lg border border-neutral-200 px-3 py-1.5 text-xs text-neutral-600 hover:bg-neutral-100"
                    >
                      <i :class="showingPassword ? 'fa fa-eye-slash' : 'fa fa-eye'"></i>
                      {{ showingPassword ? '隐藏' : '显示' }}
                    </button>
                    <button
                      @click="showEditPassword = true; newPasswordInput = detailData.share_password"
                      class="rounded-lg border border-primary px-3 py-1.5 text-xs text-primary hover:bg-primary/5"
                    >
                      <i class="fa fa-edit"></i> 修改
                    </button>
                  </div>
                </div>
              </div>

              <!-- 修改提取码表单 -->
              <div v-if="showEditPassword" class="rounded-lg bg-neutral-50 p-4">
                <span class="text-sm text-neutral-400">修改提取码</span>
                <div class="mt-2 flex items-center gap-2">
                  <input
                    v-model="newPasswordInput"
                    type="text"
                    class="flex-1 rounded-lg border border-neutral-200 px-3 py-2 text-sm focus:ring-2 focus:ring-primary/30"
                    placeholder="新提取码（留空表示移除密码）"
                    maxlength="20"
                  />
                  <button
                    @click="saveNewPassword"
                    :disabled="passwordUpdating"
                    class="rounded-lg bg-primary px-4 py-2 text-xs text-white hover:bg-primary/90 disabled:opacity-50"
                  >
                    {{ passwordUpdating ? '保存中...' : '保存' }}
                  </button>
                  <button
                    @click="cancelEditPassword"
                    class="rounded-lg border border-neutral-200 px-3 py-2 text-xs text-neutral-600 hover:bg-neutral-100"
                  >
                    取消
                  </button>
                </div>
              </div>

              <!-- 无密码时添加密码 -->
              <div v-if="!detailData.share_has_password && !showEditPassword" class="rounded-lg bg-neutral-50 p-4">
                <div class="flex items-center justify-between">
                  <span class="text-sm text-neutral-400">此分享无提取码</span>
                  <button
                    @click="showEditPassword = true; newPasswordInput = ''"
                    class="rounded-lg border border-primary px-3 py-1.5 text-xs text-primary hover:bg-primary/5"
                  >
                    <i class="fa fa-plus"></i> 添加提取码
                  </button>
                </div>
              </div>

              <!-- 分享链接 -->
              <div class="rounded-lg bg-neutral-50 p-4">
                <span class="text-sm text-neutral-400">分享链接</span>
                <div class="mt-1 flex items-center gap-2">
                  <code class="flex-1 break-all rounded border border-neutral-200 bg-white px-3 py-2 text-xs text-neutral-700">
                    {{ shareUrl }}
                  </code>
                  <button
                    @click="copyShareUrl"
                    class="shrink-0 rounded-lg border border-primary px-3 py-2 text-xs text-primary hover:bg-primary/5"
                  >
                    <i class="fa fa-copy"></i> 复制
                  </button>
                </div>
              </div>

              <!-- 资源列表 -->
              <div>
                <h3 class="mb-2 text-sm font-medium text-neutral-600">
                  <i class="fa fa-list-ul mr-1"></i>分享资源（{{ detailData.resources?.length || 0 }} 项）
                </h3>
                <div v-if="!detailData.resources || detailData.resources.length === 0" class="py-4 text-center text-sm text-neutral-400">
                  暂无资源
                </div>
                <div v-else class="space-y-1">
                  <div
                    v-for="res in detailData.resources"
                    :key="res.share_resource_id"
                    class="flex items-center gap-3 rounded-lg px-3 py-2 hover:bg-neutral-50"
                  >
                    <div
                      class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                      :class="res.resource_type === 'folder' ? 'bg-warning/10' : 'bg-primary/10'"
                    >
                      <i
                        :class="res.resource_type === 'folder' ? 'fa fa-folder text-warning' : 'fa fa-file text-primary'"
                      ></i>
                    </div>
                    <div class="min-w-0 flex-1">
                      <p class="truncate text-sm font-medium text-neutral-700">{{ res.resource_name }}</p>
                      <p class="text-xs text-neutral-400">
                        {{ res.resource_type === 'file' ? formatFileSize(res.resource_size) : '文件夹' }}
                        <span v-if="res.file_type"> · {{ res.file_type }}</span>
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import ShareLinkItem from '@/components/share/ShareLinkItem.vue'
import CreateShareDialog from '@/components/share/CreateShareDialog.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useShareStore } from '@/stores/shareStore'
import {
  getShareDetailApi,
  updateSharePasswordApi,
  formatShareUrl,
  formatFileSize,
  getShareStatusText,
  getShareStatusColor,
  type ShareDetailVO,
} from '@/api/modules/shares'

const shareStore = useShareStore()
const showCreateDialog = ref(false)

// 详情弹窗
const showDetailDialog = ref(false)
const detailLoading = ref(false)
const detailData = ref<ShareDetailVO | null>(null)
const showingPassword = ref(false)

// 修改密码
const showEditPassword = ref(false)
const newPasswordInput = ref('')
const passwordUpdating = ref(false)

const shareUrl = computed(() => {
  if (!detailData.value) return ''
  return formatShareUrl(detailData.value.share_token, detailData.value.share_password || undefined)
})

const handleRevoke = async (share_id: string) => {
  await shareStore.revokeShare(share_id)
}

const handleCreated = () => {
  showCreateDialog.value = false
}

const handleViewDetail = async (share_id: string) => {
  showDetailDialog.value = true
  detailLoading.value = true
  showingPassword.value = false
  showEditPassword.value = false
  newPasswordInput.value = ''
  try {
    const res = await getShareDetailApi(share_id)
    if(res.code == 200) {
      detailData.value = res.data
    }
    console.error('获取分享详情失败:', res.message || `未知错误 业务异常码${res.code}`)
  } catch (e) {
    console.error('获取分享详情失败:', e)
  } finally {
    detailLoading.value = false
  }
}

const copyShareUrl = async () => {
  try {
    await navigator.clipboard.writeText(shareUrl.value)
  } catch {
    // fallback
  }
}

// 修改提取码
const saveNewPassword = async () => {
  if (!detailData.value || passwordUpdating.value) return
  passwordUpdating.value = true
  try {
    await updateSharePasswordApi(detailData.value.share_id, newPasswordInput.value)
    // 更新本地数据
    detailData.value.share_password = newPasswordInput.value || null
    detailData.value.share_has_password = !!newPasswordInput.value
    showEditPassword.value = false
    newPasswordInput.value = ''
  } catch (e) {
    console.error('修改提取码失败:', e)
  } finally {
    passwordUpdating.value = false
  }
}

const cancelEditPassword = () => {
  showEditPassword.value = false
  newPasswordInput.value = ''
}

onMounted(() => {
  shareStore.fetchMyShares()
})
</script>
