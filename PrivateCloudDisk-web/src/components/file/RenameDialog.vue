<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-md p-6">
      <h2 class="text-xl font-bold mb-4">重命名</h2>
      <div class="mb-4">
        <label class="block text-sm font-medium mb-1">新名称</label>
        <input v-model="newName" class="w-full px-3 py-2 border rounded-lg" @keyup.enter="confirm" />
      </div>
      <div class="flex justify-end space-x-3">
        <button @click="$emit('close')" class="px-4 py-2 border rounded-lg">取消</button>
        <button @click="confirm" class="px-4 py-2 bg-primary text-white rounded-lg">确认</button>
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