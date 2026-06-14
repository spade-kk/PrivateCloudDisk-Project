<template>
  <nav class="flex items-center space-x-1 text-sm" aria-label="Breadcrumb">
    <ol class="flex items-center flex-wrap gap-1">
      <li v-for="(item, index) in items" :key="index" class="flex items-center gap-1">
        <router-link
          v-if="item.to && index < items.length - 1"
          :to="item.to"
          class="text-neutral-400 hover:text-primary transition-colors"
        >
          <i v-if="item.icon" :class="item.icon" class="mr-1"></i>
          {{ item.label }}
        </router-link>
        <span
          v-else-if="index < items.length - 1"
          class="text-neutral-400 cursor-pointer hover:text-primary transition-colors"
          @click="$emit('navigate', item, index)"
        >
          <i v-if="item.icon" :class="item.icon" class="mr-1"></i>
          {{ item.label }}
        </span>
        <span v-else class="text-neutral-700 font-semibold">
          <i v-if="item.icon" :class="item.icon" class="mr-1"></i>
          {{ item.label }}
        </span>
        <i v-if="index < items.length - 1" class="fa fa-angle-right text-neutral-300 text-xs mx-1"></i>
      </li>
    </ol>
  </nav>
</template>

<script setup>
defineProps({
  items: {
    type: Array,
    required: true,
    // each item: { label: string, to?: string, icon?: string }
  },
})
defineEmits(['navigate'])
</script>