<template>
  <div v-if="visible" class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
    <div class="w-full max-w-md rounded-xl bg-white p-5 shadow-lg sm:p-6">
      <h2 class="mb-4 text-lg font-bold sm:text-xl">重命名</h2>
      <div class="mb-4">
        <label class="block text-sm font-medium mb-1">新名称</label>
        <input v-model="newName" class="w-full px-3 py-2 border rounded-lg" @keyup.enter="confirm" />
      </div>
      <div class="grid grid-cols-2 gap-3 sm:flex sm:justify-end">
        <button @click="$emit('close')" class="touch-button rounded-lg border px-4 py-2">取消</button>
        <button @click="confirm" class="touch-button rounded-lg bg-primary px-4 py-2 text-white">确认</button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  visible: Boolean,
  currentName: String,
})
const emit = defineEmits(['close', 'confirm'])

const newName = ref('')
watch(() => props.currentName, (val) => { newName.value = val }, { immediate: true })
const confirm = () => {
  if (newName.value.trim()) emit('confirm', newName.value.trim())
}
</script>
