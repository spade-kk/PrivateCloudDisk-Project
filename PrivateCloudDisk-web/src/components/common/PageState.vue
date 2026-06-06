<template>
  <div class="flex min-h-[260px] flex-col items-center justify-center rounded-lg px-4 py-12 text-center">
    <div
      class="mb-4 flex h-16 w-16 items-center justify-center rounded-full"
      :class="iconWrapClass"
    >
      <i :class="[icon, iconClass]" class="text-3xl"></i>
    </div>
    <h3 class="text-lg font-semibold text-neutral-700">{{ title }}</h3>
    <p v-if="description" class="mt-2 max-w-md text-sm leading-6 text-neutral-500">{{ description }}</p>
    <button
      v-if="actionText"
      @click="$emit('action')"
      class="touch-button mt-5 inline-flex items-center justify-center gap-2 rounded-lg border px-4 py-2 text-sm transition"
      :class="actionClass"
    >
      <i v-if="actionIcon" :class="actionIcon"></i>
      <span>{{ actionText }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  type: { type: String, default: 'empty' },
  icon: { type: String, default: 'fa fa-folder-open' },
  title: { type: String, required: true },
  description: { type: String, default: '' },
  actionText: { type: String, default: '' },
  actionIcon: { type: String, default: '' },
})

defineEmits(['action'])

const iconWrapClass = computed(() => {
  if (props.type === 'error') return 'bg-danger/10'
  if (props.type === 'warning') return 'bg-warning/10'
  if (props.type === 'loading') return 'bg-primary/10'
  return 'bg-neutral-100'
})

const iconClass = computed(() => {
  if (props.type === 'error') return 'text-danger'
  if (props.type === 'warning') return 'text-warning'
  if (props.type === 'loading') return 'text-primary'
  return 'text-neutral-300'
})

const actionClass = computed(() => {
  if (props.type === 'error') return 'border-danger text-danger hover:bg-danger/10'
  return 'border-primary text-primary hover:bg-primary/10'
})
</script>
