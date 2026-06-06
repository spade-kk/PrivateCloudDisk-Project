<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
    <div class="w-full max-w-md rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <h2 class="mb-4 text-lg font-bold sm:text-xl">{{ mode === 'move' ? '移动' : '复制' }}文件/文件夹</h2>
      <div class="mb-4">
        <label class="block text-sm font-medium mb-2">选择目标文件夹</label>
        <div class="max-h-[45dvh] overflow-y-auto rounded-lg border border-neutral-200 p-2">
          <TreeFolderPicker :folders="folderTree" @select="selectTarget" />
        </div>
      </div>
      <div class="grid grid-cols-2 gap-3 sm:flex sm:justify-end">
        <button @click="$emit('close')" class="touch-button rounded-lg border px-4 py-2">取消</button>
        <button @click="confirm" :disabled="!selectedTarget" class="touch-button rounded-lg bg-primary px-4 py-2 text-white disabled:opacity-50">
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
