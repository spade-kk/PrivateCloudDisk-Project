<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      @click.self="$emit('close')"
    >
      <div class="fade-in w-full max-w-md rounded-xl bg-white p-5 shadow-lg sm:p-6">
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-lg font-bold text-neutral-700 sm:text-xl">新建分享</h2>
          <button @click="$emit('close')" class="icon-button -mr-2" title="关闭">
            <i class="fa fa-times"></i>
          </button>
        </div>

        <form class="space-y-4" @submit.prevent="submit">
          <!-- 分享类型 -->
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">分享类型</label>
            <div class="flex gap-3">
              <label
                class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-2 transition-colors"
                :class="form.target_type === 'file' ? 'border-primary bg-primary/5 text-primary' : 'border-neutral-200 text-neutral-500'"
              >
                <input v-model="form.target_type" type="radio" value="file" class="sr-only" />
                <i class="fa fa-file"></i> 文件
              </label>
              <label
                class="flex cursor-pointer items-center gap-2 rounded-lg border px-4 py-2 transition-colors"
                :class="form.target_type === 'folder' ? 'border-primary bg-primary/5 text-primary' : 'border-neutral-200 text-neutral-500'"
              >
                <input v-model="form.target_type" type="radio" value="folder" class="sr-only" />
                <i class="fa fa-folder"></i> 文件夹
              </label>
            </div>
          </div>

          <!-- 目标资源选择 -->
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">
              {{ form.target_type === 'file' ? '选择文件' : '选择文件夹' }}
            </label>
            <select
              v-model="selectedResourceId"
              class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
              required
            >
              <option value="" disabled>请选择...</option>
              <option v-for="item in resourceOptions" :key="item.id" :value="item.id">
                <span v-if="form.target_type === 'file'">{{ item.name }} ({{ formatSize(item.size) }})</span>
                <span v-else>{{ item.name }}</span>
              </option>
            </select>
            <p class="mt-1 text-xs text-neutral-400">
              {{ resourceOptions.length === 0 ? '正在加载资源列表...' : `共 ${resourceOptions.length} 个可选资源` }}
            </p>
          </div>

          <!-- 分享名称 -->
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">分享名称</label>
            <input
              v-model="form.share_name"
              class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
              placeholder="请输入分享名称"
              maxlength="200"
              required
            />
          </div>

          <!-- 有效期 -->
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">有效期</label>
            <select
              v-model="form.expires_in_days"
              class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
            >
              <option :value="1">1 天</option>
              <option :value="7">7 天</option>
              <option :value="30">30 天</option>
              <option :value="0">永久有效</option>
            </select>
          </div>

          <!-- 提取码 -->
          <div>
            <label class="flex items-center gap-2 text-sm text-neutral-600">
              <input
                v-model="form.need_password"
                type="checkbox"
                class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary"
              />
              设置提取码
            </label>
            <input
              v-if="form.need_password"
              v-model="form.password"
              class="mt-2 w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
              type="text"
              placeholder="请输入提取码（最长20位）"
              maxlength="20"
            />
          </div>

          <!-- 操作按钮 -->
          <div class="grid grid-cols-2 gap-3 pt-2 sm:flex sm:justify-end">
            <button
              type="button"
              @click="$emit('close')"
              class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50"
            >
              取消
            </button>
            <button
              type="submit"
              :disabled="submitting"
              class="touch-button rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90 disabled:opacity-50"
            >
              {{ submitting ? '创建中...' : '创建分享' }}
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, reactive, watch, onMounted } from 'vue'
import { useShareStore } from '@/stores/shareStore'
import { hashPasswordForTransport } from '@/utils/crypto'
import { formatFileSize } from '@/api/modules/shares'
import type { ShareCreateParams } from '@/api/modules/shares'

// 资源项接口
interface ResourceOption {
  id: string
  name: string
  size: number
}

const props = defineProps<{
  visible: boolean
}>()
const emit = defineEmits<{
  close: []
  created: []
}>()

const shareStore = useShareStore()
const submitting = ref(false)
const selectedResourceId = ref('')
const resourceOptions = ref<ResourceOption[]>([])

const form = reactive({
  target_type: 'file' as 'file' | 'folder',
  share_name: '',
  password: '',
  need_password: false,
  expires_in_days: 7,
})

// 监听分享类型变化，重新加载资源列表
watch(() => form.target_type, () => {
  selectedResourceId.value = ''
  loadResources()
})

// 加载资源列表（文件或文件夹）
const loadResources = async () => {
  resourceOptions.value = []
  try {
    const { getMyUserRootNodeApi, getNodeChildrenApi } = await import('@/api/modules/nodes')

    // 获取根节点
    const rootNode = await getMyUserRootNodeApi()
    const rootNodeId = rootNode?.data?.node_id || rootNode?.node_id
    if (!rootNodeId) {
      console.error('无法获取根节点')
      return
    }

    // 获取根节点下的所有子项
    const children = await getNodeChildrenApi(rootNodeId)
    const items = children?.data || children || []

    if (form.target_type === 'file') {
      resourceOptions.value = items
        .filter((item: any) => item.node_type === 'file' || item.file_id)
        .map((f: any) => ({
          id: f.file_id || f.node_id,
          name: f.file_name || f.node_name || f.name,
          size: f.file_size || f.size || 0,
        }))
    } else {
      resourceOptions.value = items
        .filter((item: any) => item.node_type === 'folder' || (item.node_id && !item.file_id))
        .map((f: any) => ({
          id: f.node_id || f.id,
          name: f.node_name || f.name,
          size: 0,
        }))
    }
  } catch (e) {
    console.error('加载资源列表失败:', e)
  }
}

onMounted(loadResources)

const formatSize = (bytes: number) => formatFileSize(bytes)

const submit = async () => {
  if (!selectedResourceId.value) return
  submitting.value = true

  try {
    const params: ShareCreateParams = {
      target_type: form.target_type,
      share_name: form.share_name || `分享${form.target_type === 'file' ? '文件' : '文件夹'}`,
      expires_in_days: form.expires_in_days,
    }

    if (form.target_type === 'file') {
      params.file_id = selectedResourceId.value
    } else {
      params.node_id = selectedResourceId.value
    }

    if (form.need_password && form.password) {
      // 客户端预哈希密码
      params.password = await hashPasswordForTransport(form.password)
    }

    await shareStore.createShare(params)
    emit('created')
    emit('close')
    resetForm()
  } catch (e) {
    console.error('创建分享失败:', e)
  } finally {
    submitting.value = false
  }
}

const resetForm = () => {
  form.target_type = 'file'
  form.share_name = ''
  form.password = ''
  form.need_password = false
  form.expires_in_days = 7
  selectedResourceId.value = ''
}
</script>