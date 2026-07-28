<template>
  <Teleport to="body">
    <Transition name="dialog-fade">
      <div
        v-if="visible"
        class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
        @click.self="$emit('close')"
      >
        <div class="fade-in w-full max-w-2xl rounded-xl bg-white p-5 shadow-lg sm:p-6">
          <!-- 标题栏 -->
          <div class="mb-5 flex items-center justify-between">
            <h2 class="text-lg font-bold text-neutral-700 sm:text-xl">
              <i class="fa fa-share-alt mr-2 text-primary"></i>新建分享
            </h2>
            <button @click="$emit('close')" class="icon-button -mr-2" title="关闭">
              <i class="fa fa-times"></i>
            </button>
          </div>

          <form class="space-y-5" @submit.prevent="submit">
            <!-- ============================================ -->
            <!-- 资源选择区域（多资源） -->
            <!-- ============================================ -->
            <div>
              <div class="mb-2 flex items-center justify-between">
                <label class="text-sm font-medium text-neutral-600">
                  <i class="fa fa-files-o mr-1"></i>选择分享资源
                  <span class="ml-1 text-xs text-neutral-400">（可同时选择文件和文件夹）</span>
                </label>
                <span class="text-xs text-neutral-400">
                  已选 {{ selectedResources.length }} 项
                </span>
              </div>

              <!-- 已选资源标签 -->
              <div v-if="selectedResources.length > 0" class="mb-3 flex flex-wrap gap-2">
                <span
                  v-for="(res, idx) in selectedResources"
                  :key="res.id"
                  class="inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs"
                  :class="res.type === 'file'
                    ? 'border-primary/30 bg-primary/5 text-primary'
                    : 'border-warning/30 bg-warning/5 text-warning'"
                >
                  <i :class="res.type === 'file' ? 'fa fa-file' : 'fa fa-folder'"></i>
                  <span class="max-w-[120px] truncate">{{ res.name }}</span>
                  <button
                    type="button"
                    @click="removeResource(idx)"
                    class="ml-0.5 rounded-full p-0.5 hover:bg-neutral-200/50"
                  >
                    <i class="fa fa-times text-[10px]"></i>
                  </button>
                </span>
              </div>

              <!-- 资源浏览面板 -->
              <div class="rounded-lg border border-neutral-200">
                <!-- 面包屑导航 -->
                <div class="flex items-center gap-1 border-b border-neutral-100 px-3 py-2 text-xs">
                  <button
                    type="button"
                    @click="navigateToRoot"
                    class="rounded px-1.5 py-0.5 text-primary hover:bg-primary/5"
                  >
                    <i class="fa fa-home"></i>
                  </button>
                  <span class="text-neutral-300">/</span>
                  <template v-for="(crumb, idx) in breadcrumb" :key="crumb.node_id">
                    <button
                      type="button"
                      @click="navigateToFolder(crumb.node_id)"
                      class="rounded px-1.5 py-0.5 text-primary hover:bg-primary/5"
                    >
                      {{ crumb.name }}
                    </button>
                    <span v-if="idx < breadcrumb.length - 1" class="text-neutral-300">/</span>
                  </template>
                </div>

                <!-- 资源列表 -->
                <div class="max-h-[280px] overflow-y-auto">
                  <div v-if="resourceLoading" class="flex justify-center py-8">
                    <div class="h-6 w-6 animate-spin rounded-full border-2 border-primary border-t-transparent"></div>
                  </div>
                  <div v-else-if="resourceList.length === 0" class="py-8 text-center text-sm text-neutral-400">
                    <i class="fa fa-folder-open mb-1 text-2xl"></i>
                    <p>此文件夹为空</p>
                  </div>
                  <div v-else>
                    <div
                      v-for="item in resourceList"
                      :key="item.id"
                      class="flex cursor-pointer items-center gap-3 border-b border-neutral-50 px-3 py-2.5 transition-colors hover:bg-neutral-50"
                      :class="{ 'bg-primary/[0.03]': isSelected(item.id) }"
                    >
                      <!-- 选择框 -->
                      <label class="flex shrink-0 cursor-pointer items-center" @click.stop>
                        <input
                          type="checkbox"
                          :checked="isSelected(item.id)"
                          @change="toggleResource(item)"
                          class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary"
                        />
                      </label>
                      <!-- 图标 -->
                      <div
                        class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                        :class="item.type === 'folder' ? 'bg-warning/10' : 'bg-primary/10'"
                      >
                        <i
                          :class="item.type === 'folder' ? 'fa fa-folder text-warning' : 'fa fa-file text-primary'"
                        ></i>
                      </div>
                      <!-- 名称和元信息 -->
                      <div class="min-w-0 flex-1" @click="item.type === 'folder' && navigateToFolder(item.id)">
                        <p class="truncate text-sm font-medium"
                          :class="item.type === 'folder' ? 'text-primary hover:underline' : 'text-neutral-700'">
                          {{ item.name }}
                        </p>
                        <p class="text-xs text-neutral-400">
                          {{ item.type === 'folder' ? '文件夹' : formatSize(item.size) }}
                        </p>
                      </div>
                      <!-- 文件夹进入箭头 -->
                      <button
                        v-if="item.type === 'folder'"
                        type="button"
                        @click="navigateToFolder(item.id)"
                        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-neutral-400 hover:bg-neutral-200/50"
                      >
                        <i class="fa fa-chevron-right text-xs"></i>
                      </button>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 分享名称 -->
            <div>
              <label class="mb-1 block text-sm font-medium text-neutral-600">
                <i class="fa fa-tag mr-1"></i>分享名称
              </label>
              <input
                v-model="form.share_name"
                class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
                placeholder="请输入分享名称"
                maxlength="200"
                required
              />
            </div>

            <!-- persistent label + safe, lightweight rich-text authoring. -->
            <div>
              <label class="mb-1 block text-sm font-medium text-neutral-600">
                <i class="fa fa-align-left mr-1"></i>分享说明（可选）
              </label>
              <ShareDescriptionEditor v-model="form.share_description" />
            </div>

            <!-- 有效期 + 提取码 -->
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <!-- 有效期 -->
              <div>
                <label class="mb-1 block text-sm font-medium text-neutral-600">
                  <i class="fa fa-clock-o mr-1"></i>有效期
                </label>
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
                <label class="mb-1 block text-sm font-medium text-neutral-600">
                  <i class="fa fa-lock mr-1"></i>提取码（可选）
                </label>
                <input
                  v-model="form.password"
                  class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
                  type="text"
                  placeholder="留空表示无密码"
                  maxlength="20"
                />
              </div>
            </div>

            <!-- 操作按钮 -->
            <div class="flex items-center justify-end gap-3 pt-2">
              <button
                type="button"
                @click="$emit('close')"
                class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50"
              >
                取消
              </button>
              <button
                type="submit"
                :disabled="submitting || selectedResources.length === 0"
                class="touch-button rounded-lg bg-primary px-5 py-2 text-white hover:bg-primary/90 disabled:opacity-50"
              >
                <i class="fa fa-check mr-1"></i>
                {{ submitting ? '创建中...' : `创建分享（${selectedResources.length} 项）` }}
              </button>
            </div>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useShareStore } from '@/stores/shareStore'
import { formatFileSize } from '@/api/modules/shares'
import type { ShareCreateParams, ShareResourceItem } from '@/api/modules/shares'
import ShareDescriptionEditor from './ShareDescriptionEditor.vue'

// 资源项（含本地类型信息）
interface ResourceOption {
  id: string
  name: string
  type: 'file' | 'folder'
  size: number
}

// 已选资源
interface SelectedResource {
  id: string
  name: string
  type: 'file' | 'folder'
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

// 资源浏览
const resourceLoading = ref(false)
const resourceList = ref<ResourceOption[]>([])
const currentFolderId = ref<string | null>(null)
const breadcrumb = ref<{ node_id: string; name: string }[]>([])

// 已选资源
const selectedResources = ref<SelectedResource[]>([])

// 表单
const form = reactive({
  share_name: '',
  share_description: '',
  password: '',
  expires_in_days: 7,
})

// 初始化
onMounted(async () => {
  await loadRootResources()
})

// 加载根目录资源
const loadRootResources = async () => {
  resourceLoading.value = true
  try {
    const { getMyUserRootNodeApi, getNodeChildrenApi } = await import('@/api/modules/nodes')
    const rootNode = await getMyUserRootNodeApi()
    const rootNodeId = rootNode?.data?.node_id || rootNode?.node_id
    if (!rootNodeId) {
      console.error('无法获取根节点')
      return
    }
    currentFolderId.value = rootNodeId
    breadcrumb.value = []

    // 获取根节点下的所有子项
    const children = await getNodeChildrenApi(rootNodeId)
    const items = children?.data || children || []
    resourceList.value = (Array.isArray(items) ? items : []).map((item: any) => ({
      id: item.file_id || item.node_id || item.id,
      name: item.file_name || item.node_name || item.name,
      type: item.node_type === 'folder' || (item.node_id && !item.file_id) ? 'folder' : 'file',
      size: item.file_size || item.size || 0,
    }))
  } catch (e) {
    console.error('加载资源列表失败:', e)
    resourceList.value = []
  } finally {
    resourceLoading.value = false
  }
}

// 导航到文件夹
const navigateToFolder = async (nodeId: string) => {
  resourceLoading.value = true
  try {
    const { getNodeChildrenApi } = await import('@/api/modules/nodes')
    const children = await getNodeChildrenApi(nodeId)
    const items = children?.data || children || []
    resourceList.value = (Array.isArray(items) ? items : []).map((item: any) => ({
      id: item.file_id || item.node_id || item.id,
      name: item.file_name || item.node_name || item.name,
      type: item.node_type === 'folder' || (item.node_id && !item.file_id) ? 'folder' : 'file',
      size: item.file_size || item.size || 0,
    }))

    // 更新面包屑
    const targetItem = (Array.isArray(items) ? items : []).find((item: any) =>
      (item.node_id || item.id) === nodeId
    )
    if (targetItem) {
      breadcrumb.value.push({
        node_id: nodeId,
        name: targetItem.node_name || targetItem.name || nodeId,
      })
    } else {
      breadcrumb.value.push({ node_id: nodeId, name: '文件夹' })
    }
    currentFolderId.value = nodeId
  } catch (e) {
    console.error('加载文件夹内容失败:', e)
  } finally {
    resourceLoading.value = false
  }
}

// 返回根目录
const navigateToRoot = () => {
  breadcrumb.value = []
  loadRootResources()
}

// 判断是否已选中
const isSelected = (id: string) => selectedResources.value.some((r) => r.id === id)

// 切换选中
const toggleResource = (item: ResourceOption) => {
  const idx = selectedResources.value.findIndex((r) => r.id === item.id)
  if (idx >= 0) {
    selectedResources.value.splice(idx, 1)
  } else {
    selectedResources.value.push({
      id: item.id,
      name: item.name,
      type: item.type,
    })
  }

  // 自动填充分享名称（取第一个选中资源的名称）
  if (selectedResources.value.length === 1 && !form.share_name) {
    form.share_name = selectedResources.value[0].name
  }
}

// 移除已选资源
const removeResource = (idx: number) => {
  selectedResources.value.splice(idx, 1)
}

// 提交
const submit = async () => {
  if (selectedResources.value.length === 0) return
  submitting.value = true

  try {
    const resources: ShareResourceItem[] = selectedResources.value.map((r) => ({
      type: r.type,
      id: r.id,
    }))

    const params: ShareCreateParams = {
      resources,
      share_name: form.share_name || resources.map((r) => {
        const found = selectedResources.value.find((s) => s.id === r.id)
        return found?.name || ''
      }).join('、'),
      share_description: form.share_description || undefined,
      expires_in_days: form.expires_in_days,
    }

    if (form.password) {
      // 提取码明文传入，服务端 AES 加密存储
      params.password = form.password
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

// 重置表单
const resetForm = () => {
  form.share_name = ''
  form.share_description = ''
  form.password = ''
  form.expires_in_days = 7
  selectedResources.value = []
  breadcrumb.value = []
  loadRootResources()
}

const formatSize = (bytes: number) => formatFileSize(bytes)
</script>

<style scoped>
.dialog-fade-enter-active,
.dialog-fade-leave-active {
  transition: opacity 0.2s ease;
}
.dialog-fade-enter-from,
.dialog-fade-leave-to {
  opacity: 0;
}
</style>
