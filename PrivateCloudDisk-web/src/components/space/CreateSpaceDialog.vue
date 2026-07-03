<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/40"
      @click.self="cancel"
    >
      <div class="w-full max-w-md rounded-2xl bg-white shadow-2xl">
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
        <div class="space-y-5 px-6 py-5">
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

          <!-- 可见性 -->
          <div v-if="form.spaceType === 'public'">
            <label class="mb-1.5 block text-sm font-medium text-neutral-700">可见性</label>
            <select
              v-model="form.spaceVisibility"
              class="w-full rounded-lg border border-neutral-300 px-3 py-2.5 text-sm outline-none transition focus:border-primary focus:ring-1 focus:ring-primary"
            >
              <option value="public">公开 - 所有人可见</option>
              <option value="whitelist">白名单 - 指定用户可见</option>
              <option value="blacklist">黑名单 - 指定用户不可见</option>
              <option value="private">私有 - 仅成员可见</option>
            </select>
          </div>

          <!-- 配额信息 -->
          <div class="rounded-lg bg-neutral-50 p-3 text-xs text-neutral-500">
            <p class="font-medium text-neutral-600">配额说明</p>
            <p class="mt-1">个人空间：10GB | 企业空间：100GB | 团队空间：50GB | 公共空间：20GB</p>
          </div>
        </div>

        <!-- 按钮 -->
        <div class="flex justify-end gap-3 border-t px-6 py-4">
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
import { ref, reactive } from 'vue'
import { createSpaceApi } from '@/api/modules/space'
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

const spaceTypes = [
  { value: 'personal', label: '个人', icon: 'fa fa-user', desc: '主网盘' },
  { value: 'enterprise', label: '企业', icon: 'fa fa-building', desc: '企业级管理' },
  { value: 'public', label: '公共', icon: 'fa fa-globe', desc: '开放共享' },
  { value: 'team', label: '团队', icon: 'fa fa-users', desc: '团队协作' },
]

const form = reactive({
  spaceName: '',
  spaceType: 'team',
  spaceDescription: '',
  spaceVisibility: 'public',
})

function cancel() {
  emit('update:visible', false)
}

async function submit() {
  if (!form.spaceName || submitting.value) return
  submitting.value = true
  try {
    const res = await createSpaceApi({
      spaceName: form.spaceName,
      spaceType: form.spaceType,
      spaceDescription: form.spaceDescription,
      spaceVisibility: form.spaceVisibility,
    })
    if (res.code === 200) {
      await spaceStore.refreshSpaces()
      emit('created', res.data.spaceId)
      emit('update:visible', false)
      resetForm()
    }
  } finally {
    submitting.value = false
  }
}

function resetForm() {
  form.spaceName = ''
  form.spaceType = 'team'
  form.spaceDescription = ''
  form.spaceVisibility = 'public'
}
</script>