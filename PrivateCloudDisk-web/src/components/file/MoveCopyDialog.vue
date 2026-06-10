<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
    <div class="w-full max-w-2xl rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <h2 class="mb-4 text-lg font-bold sm:text-xl">{{ mode === 'move' ? '移动' : '复制' }}文件/文件夹</h2>

      <div class="mb-3 flex items-center text-xs text-neutral-500">
        <i class="fa fa-home mr-1"></i>
        <div v-for="(node) in breadcrumbNodes" :key="node.node_id">
          <i class="fa fa-angle-right mx-1"></i>
          <span>{{ node.node_name }}</span>
        </div>
      </div>

      <TreeFolderPicker
        :folderTree="folderTree"
        :selectedNodeId="selectedTarget?.node_id"
        :loadingColumn="loadingColumn"
        :breadcrumbNodes="breadcrumbNodes"
        @select="handleSelect"
        @expand="handleExpand"
      />

      <div v-if="selectedTarget" class="mt-3 text-sm text-neutral-600">
        已选择目标文件夹: <span class="font-medium text-primary">{{ selectedTarget.node_name }}</span>
      </div>

      <div class="mt-5 grid grid-cols-2 gap-3 sm:flex sm:justify-end">
        <button @click="$emit('close')" class="touch-button rounded-lg border px-4 py-2">取消</button>
        <button
          @click="confirm"
          :disabled="!selectedTarget"
          class="touch-button rounded-lg bg-primary px-4 py-2 text-white disabled:opacity-50"
        >
          确认
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import TreeFolderPicker from './TreeFolderPicker.vue'
import { getMyUserRootNodeApi, getNodeChildrenApi } from '@/api/index'

const props = defineProps({
  visible: Boolean,
  mode: { type: String, default: 'move' },
})

const emit = defineEmits(['close', 'confirm'])

const folderTree = ref([])
const breadcrumbNodes = ref([])
const selectedTarget = ref(null)
const loadingColumn = ref(null)
const rootNode = ref(null)

watch(() => props.visible, async (val) => {
  if (val) await initFolderTree()
})

const initFolderTree = async () => {
  loadingColumn.value = 0
  folderTree.value = []
  breadcrumbNodes.value = []
  selectedTarget.value = null

  try {
    const rootRes = await getMyUserRootNodeApi()
    if (rootRes.code !== 200 || !rootRes.data) {
      console.log('根目录数据异常', rootRes.message)
      loadingColumn.value = null
      return
    }

    rootNode.value = rootRes.data
    selectedTarget.value = rootRes.data

    folderTree.value = [[rootRes.data]]
    breadcrumbNodes.value = [rootRes.data]

    const childrenRes = await getNodeChildrenApi(rootRes.data.node_id)
    if (childrenRes.code === 200 && childrenRes.data) {
      const folders = filterFolders(childrenRes.data)
      folderTree.value.push(folders)
    } else {
      folderTree.value.push([])
    }
  } catch (error) {
    console.error('加载目录树失败', error)
  } finally {
    loadingColumn.value = null
  }
}

const filterFolders = (nodes) => {
  if (!Array.isArray(nodes)) return []
  return nodes.filter((n) => {
    const type = n.node_type || n.type
    return type === 'folder' || type === 'FOLDER' || type === undefined
  })
}

const handleSelect = ({ node }) => {
  selectedTarget.value = node
}

const handleExpand = async ({ node, colIndex }) => {
  loadingColumn.value = colIndex + 1

  folderTree.value = folderTree.value.slice(0, colIndex + 1)
  breadcrumbNodes.value = breadcrumbNodes.value.slice(0, colIndex + 1)

  try {
    const childrenRes = await getNodeChildrenApi(node.node_id)
    if (childrenRes.code === 200 && childrenRes.data) {
      const folders = filterFolders(childrenRes.data)
      folderTree.value.push(folders)
      breadcrumbNodes.value.push(node)
      selectedTarget.value = node
    } else {
      folderTree.value.push([])
      breadcrumbNodes.value.push(node)
    }
  } catch (error) {
    console.error('加载子目录失败', error)
    folderTree.value.push([])
  } finally {
    loadingColumn.value = null
  }
}

const confirm = () => {
  if (selectedTarget.value) emit('confirm', selectedTarget.value.node_id)
}
</script>
