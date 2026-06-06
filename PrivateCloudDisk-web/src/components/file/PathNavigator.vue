<template>
  <div class="flex min-w-0 flex-1 items-center gap-2 overflow-x-auto pb-1">
    <button @click="$emit('home')" class="flex shrink-0 items-center whitespace-nowrap text-sm text-primary hover:text-primary/80 sm:text-base">
      <i class="fa fa-home mr-1"></i><span>我的网盘</span>
    </button>
    <div v-for="(item, idx) in pathStack" :key="item.node_id" class="flex shrink-0 items-center gap-2">
      <span class="text-neutral-300"><i class="fa fa-angle-right"></i></span>
      <div class="max-w-[48vw] shrink-0 sm:max-w-xs">
        <a @click="idx < pathStack.length - 1 ? $emit('navigate', item) : null" class="block truncate text-sm sm:text-base" :class="idx === pathStack.length - 1 ? 'text-neutral-700 font-medium' : 'text-primary hover:text-primary/80 cursor-pointer'">
          {{ item.node_name }}
        </a>
      </div>
    </div>
  </div>
</template>

<script setup>
defineProps(['pathStack'])
defineEmits(['home', 'navigate'])
</script>
