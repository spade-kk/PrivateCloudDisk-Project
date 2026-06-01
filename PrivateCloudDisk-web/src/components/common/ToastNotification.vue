<template>
  <Teleport to="body">
    <div class="fixed top-20 right-4 z-50 max-w-xs transition-all duration-300" :class="visible ? 'translate-x-0' : 'translate-x-full'">
      <div :class="['text-white px-4 py-3 rounded-lg shadow-lg flex items-center space-x-2', bgClass]">
        <i :class="iconClass"></i>
        <span>{{ message }}</span>
      </div>
    </div>
  </Teleport>
</template>

<script setup>
import { computed } from 'vue'
import { useToastStore } from '@/stores/toastStore'

const toastStore = useToastStore()
const visible = computed(() => toastStore.visible)
const message = computed(() => toastStore.message)
const type = computed(() => toastStore.type)

const bgClass = computed(() => {
  switch (type.value) {
    case 'success': return 'bg-success'
    case 'error': return 'bg-danger'
    case 'warning': return 'bg-warning text-neutral-700'
    default: return 'bg-primary'
  }
})

const iconClass = computed(() => {
  switch (type.value) {
    case 'success': return 'fa fa-check-circle'
    case 'error': return 'fa fa-exclamation-circle'
    case 'warning': return 'fa fa-exclamation-triangle'
    default: return 'fa fa-info-circle'
  }
})
</script>