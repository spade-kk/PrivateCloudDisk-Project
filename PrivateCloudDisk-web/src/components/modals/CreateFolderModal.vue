<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="fade-in w-full max-w-md rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-xl font-bold text-neutral-700">新建文件夹</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700"><i class="fa fa-times text-xl"></i></button>
      </div>
      <div class="space-y-4">
        <div>
          <label class="block text-sm font-medium text-neutral-600 mb-1">文件夹名称</label>
          <input v-model="folderName" type="text" class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:ring-2 focus:ring-primary/30" placeholder="请输入文件夹名称" @keyup.enter="confirm" autofocus>
        </div>
        <div class="grid grid-cols-2 gap-3 sm:flex sm:justify-end">
          <button @click="$emit('close')" class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50">取消</button>
          <button @click="confirm" class="touch-button rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90">确认创建</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

defineProps(['visible'])
const emit = defineEmits(['close', 'confirm'])
const folderName = ref('')

const confirm = () => {
  if (folderName.value.trim()) {
    emit('confirm', folderName.value.trim())
    folderName.value = ''
  }
}
</script>
