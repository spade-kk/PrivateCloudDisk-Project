<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-md p-6">
      <h2 class="text-xl font-bold mb-4">{{ mode === 'move' ? '移动' : '复制' }}文件/文件夹</h2>
      <div class="mb-4">
        <label class="block text-sm font-medium mb-2">选择目标文件夹</label>
        <TreeFolderPicker :folders="folderTree" @select="selectTarget" />
      </div>
      <div class="flex justify-end space-x-3">
        <button @click="$emit('close')" class="px-4 py-2 border rounded-lg">取消</button>
        <button @click="confirm" :disabled="!selectedTarget" class="px-4 py-2 bg-primary text-white rounded-lg disabled:opacity-50">
          确认
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import TreeFolderPicker from './TreeFolderPicker.vue'
//import { getFolderTree } from '@/api/folder'

const props = defineProps({
  visible: Boolean,
  mode: { type: String, default: 'move' }, // 'move' or 'copy'
})
const emit = defineEmits(['close', 'confirm'])

const folderTree = ref([])
const selectedTarget = ref(null)

onMounted(async () => {
  //folderTree.value = await getFolderTree() // 需要实现该API，获取完整目录树
})
const selectTarget = (folder) => { selectedTarget.value = folder }
const confirm = () => {
  if (selectedTarget.value) emit('confirm', selectedTarget.value.node_id)
}
</script>