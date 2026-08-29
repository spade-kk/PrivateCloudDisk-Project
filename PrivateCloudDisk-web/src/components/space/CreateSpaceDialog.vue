<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center overflow-y-auto bg-black/40 p-3 sm:p-6"
      @click.self="cancel"
    >
      <div class="create-space-dialog relative my-auto flex max-h-[calc(100dvh-1.5rem)] w-full max-w-md flex-col overflow-hidden rounded-2xl bg-white shadow-2xl sm:max-h-[calc(100dvh-3rem)]">
        <!-- 标题栏 -->
        <div class="flex items-center justify-between border-b px-6 py-4">
          <h2 class="text-lg font-semibold text-neutral-800">创建新空间</h2>
          <button
            class="flex h-8 w-8 items-center justify-center rounded-full text-neutral-400 transition hover:bg-neutral-100 hover:text-neutral-600"
            @click="cancel"
          >
            <i class="fa fa-times"></i>
          </button>
        </div>

        <!-- 表单 -->
        <!-- [REQ-SPACE-MANAGEMENT-6.6~6.13] 原表单容器没有高度边界和滚动上下文，
             小屏/键盘弹起时会被裁剪且底部按钮不可达。主体现在独立滚动，标题与操作区固定。 -->
        <div class="min-h-0 flex-1 space-y-5 overflow-y-auto overscroll-contain px-6 py-5">
          <!-- 空间名称 -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-neutral-700">空间名称</label>
            <input
              v-model="form.spaceName"
              type="text"
              class="w-full rounded-lg border border-neutral-300 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
              placeholder="输入空间名称"
              maxlength="200"
            />
          </div>

          <!-- 空间类型 -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-neutral-700">空间类型</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="opt in spaceTypes"
                :key="opt.value"
                class="flex flex-col items-center rounded-lg border px-3 py-3 text-center transition"
                :class="form.spaceType === opt.value
                  ? 'border-primary bg-primary/5 text-primary'
                  : 'border-neutral-200 text-neutral-500 hover:border-primary/30'"
                @click="form.spaceType = opt.value"
              >
                <i :class="opt.icon" class="mb-1 text-lg"></i>
                <span class="text-xs font-medium">{{ opt.label }}</span>
                <span class="mt-0.5 text-[10px] text-neutral-400">{{ opt.desc }}</span>
              </button>
            </div>
          </div>

          <!-- 空间描述 -->
          <div>
            <label class="mb-1.5 block text-sm font-medium text-neutral-700">空间描述（可选）</label>
            <textarea
              v-model="form.spaceDescription"
              class="w-full rounded-lg border border-neutral-300 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
              placeholder="简要描述空间用途"
              rows="2"
              maxlength="2000"
            ></textarea>
          </div>

          <!-- 公开空间按仓库语义固定 visible/invite_only，不再显示普通成员空间可见性选择。 -->
          <div v-if="form.spaceType === 'public'" class="rounded-lg border border-blue-100 bg-blue-50 p-3 text-xs text-blue-700">
            公开仓库面向登录用户发现，不参与成员加入流程。创建后可在仓库设置中分别控制浏览、下载和上传权限。
          </div>

          <div v-if="form.spaceType === 'public'">
            <label class="mb-1.5 block text-sm font-medium text-neutral-700">资源类型</label>
            <div class="grid grid-cols-2 gap-2">
              <button
                v-for="opt in publicResourceTypes"
                :key="opt.value"
                type="button"
                class="rounded-lg border px-3 py-3 text-left transition"
                :class="form.resourceType === opt.value ? 'border-primary bg-primary/5 text-primary' : 'border-neutral-200 text-neutral-500 hover:border-primary/30'"
                @click="form.resourceType = opt.value"
              >
                <span class="block text-xs font-medium"><i :class="opt.icon" class="mr-1"></i>{{ opt.label }}</span>
                <span class="mt-1 block text-[10px] text-neutral-400">{{ opt.desc }}</span>
              </button>
            </div>
          </div>

          <div v-if="form.spaceType !== 'personal' && form.spaceType !== 'public'" class="rounded-lg border border-neutral-200 p-3">
            <label class="mb-1 block text-xs font-medium text-neutral-600">加入策略</label>
            <select v-model="form.joinPolicy" class="w-full rounded-lg border px-3 py-2 text-sm">
              <option value="open">开放加入</option>
              <option value="approval_required">需要审批</option>
              <option value="invite_only">仅限邀请</option>
            </select>
          </div>

          <!-- 配额信息 -->
          <div class="rounded-lg bg-neutral-50 p-3 text-xs text-neutral-500">
            <p class="font-medium text-neutral-600">配额说明</p>
            <p class="mt-1">个人空间：10GB | 企业空间：100GB | 团队空间：50GB | 公共空间：20GB</p>
          </div>
          <p v-if="submitError" class="rounded-lg bg-red-50 px-3 py-2 text-xs text-red-700">{{ submitError }}</p>
        </div>

        <!-- 按钮 -->
        <div class="flex shrink-0 justify-end gap-3 border-t bg-white px-6 py-4">
          <button
            class="rounded-lg px-4 py-2 text-sm text-neutral-600 transition hover:bg-neutral-100"
            @click="cancel"
          >
            取消
          </button>
          <button
            class="rounded-lg bg-primary px-5 py-2 text-sm font-medium text-white transition hover:bg-primary/90 disabled:opacity-50"
            :disabled="!form.spaceName || submitting"
            @click="submit"
          >
            {{ submitting ? '创建中...' : '创建空间' }}
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { createSpaceApi } from '@/api/modules/space'
import { createGitRepositoryApi } from '@/api/modules/git'
import { useSpaceStore } from '@/stores/spaceStore'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  created: [spaceId: string]
}>()

const spaceStore = useSpaceStore()
const submitting = ref(false)
const submitError = ref('')

const spaceTypes = [
  { value: 'personal', label: '个人', icon: 'fa fa-user', desc: '主网盘' },
  { value: 'enterprise', label: '企业', icon: 'fa fa-building', desc: '企业级管理' },
  { value: 'private', label: '私有', icon: 'fa fa-lock', desc: '邀请成员协作' },
  { value: 'public', label: '公开仓库', icon: 'fa fa-globe', desc: '长期资源发布' },
  { value: 'team', label: '团队', icon: 'fa fa-users', desc: '团队协作' },
]
const publicResourceTypes = [
  { value: 'file' as const, label: '文件仓库', icon: 'fa fa-folder-o', desc: '在线浏览、分享和上传文件' },
  { value: 'git' as const, label: 'Git 仓库', icon: 'fa fa-code-fork', desc: '通过 Git HTTP/SSH 管理源码版本' },
]

const form = reactive({
  spaceName: '',
  spaceType: 'team',
  spaceDescription: '',
  spaceVisibility: 'visible',
  joinPolicy: 'approval_required' as 'open' | 'approval_required' | 'invite_only',
  allowPublicBrowse: true,
  allowPublicDownload: true,
  allowPublicUpload: false,
  resourceType: 'file' as 'file' | 'git',
})

let previousBodyOverflow = ''
watch(() => props.visible, (visible) => {
  /* [REQ-SPACE-MANAGEMENT-6.13] 遮罩开启时锁住文档滚动，关闭后恢复进入弹窗前的值；
     原行为允许背景与表单同时滚动，移动端很容易误触并导致表单位置丢失。 */
  if (visible) {
    previousBodyOverflow = document.body.style.overflow
    document.body.style.overflow = 'hidden'
  } else {
    document.body.style.overflow = previousBodyOverflow
  }
}, { immediate: true })

onBeforeUnmount(() => {
  document.body.style.overflow = previousBodyOverflow
})

function cancel() {
  emit('update:visible', false)
}

async function submit() {
  if (!form.spaceName || submitting.value) return
  submitting.value = true
  submitError.value = ''
  try {
    const res = await createSpaceApi({
      spaceName: form.spaceName,
      spaceType: form.spaceType,
      spaceDescription: form.spaceDescription,
      spaceVisibility: form.spaceVisibility,
      joinPolicy: form.spaceType === 'personal' || form.spaceType === 'public' ? 'invite_only' : form.joinPolicy,
      allowPublicBrowse: form.spaceType === 'public' ? form.allowPublicBrowse : undefined,
      allowPublicDownload: form.spaceType === 'public' ? form.allowPublicDownload : undefined,
      allowPublicUpload: form.spaceType === 'public' ? form.allowPublicUpload : undefined,
      resourceType: form.spaceType === 'public' ? form.resourceType : 'file',
    })
    if (res.code === 200) {
      // [REQ-GIT-SPACE-12.1/3.1] 空间先建立身份与权限，Git 仓库再由独立服务初始化；
      // 旧文件空间仍只执行原有空间创建流程，不引入 Git 服务依赖。
      if (form.spaceType === 'public' && form.resourceType === 'git') {
        await createGitRepositoryApi({ spaceId: res.data.spaceId, name: form.spaceName, description: form.spaceDescription })
      }
      await spaceStore.refreshSpaces()
      emit('created', res.data.spaceId)
      emit('update:visible', false)
      resetForm()
    }
  } catch (cause: any) {
    submitError.value = cause?.message || '创建失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.spaceName = ''
  form.spaceType = 'team'
  form.spaceDescription = ''
  form.spaceVisibility = 'visible'
  form.joinPolicy = 'approval_required'
  form.allowPublicBrowse = true
  form.allowPublicDownload = true
  form.allowPublicUpload = false
  form.resourceType = 'file'
  submitError.value = ''
}
</script>

<style scoped>
/* [REQ-SPACE-MANAGEMENT-6.6~6.13] 对话框本体承担滚动，避免 Teleport 遮罩层在移动端
   与浏览器视口滚动竞争；保留原有表单、校验和创建逻辑。 */
.create-space-dialog {
  overscroll-behavior: contain;
}

.create-space-dialog :deep(textarea),
.create-space-dialog :deep(input),
.create-space-dialog :deep(select) {
  max-width: 100%;
}

.create-space-dialog::-webkit-scrollbar {
  width: 8px;
}

.create-space-dialog::-webkit-scrollbar-thumb {
  border: 2px solid transparent;
  border-radius: 999px;
  background: rgba(115, 115, 115, .35);
  background-clip: padding-box;
}

:global(.dark) .create-space-dialog {
  background: #161b22;
}

:global(.dark) .create-space-dialog > div,
:global(.dark) .create-space-dialog :deep(.bg-white) {
  background: #161b22;
}

@media (max-width: 640px) {
  .create-space-dialog {
    max-height: calc(100dvh - .75rem);
  }
}
</style>
