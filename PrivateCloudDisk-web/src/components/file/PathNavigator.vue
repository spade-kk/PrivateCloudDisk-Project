<template>
  <div class="flex items-center space-x-2 overflow-x-auto flex-1 pb-1">
    <button @click="$emit('home')" class="text-primary hover:text-primary/80 flex items-center whitespace-nowrap">
      <i class="fa fa-home mr-1"></i><span>我的网盘</span>
    </button>
    <template v-for="(item, idx) in pathStack" :key="item.node_id">
      <span class="text-neutral-300"><i class="fa fa-angle-right"></i></span>
      <div class="flex-shrink-0">
        <a @click="idx < pathStack.length - 1 ? $emit('navigate', item) : null" :class="idx === pathStack.length - 1 ? 'text-neutral-700 font-medium' : 'text-primary hover:text-primary/80 cursor-pointer'">
          {{ item.node_name }}
        </a>
      </div>
    </template>
  </div>
</template>

<script setup>
defineProps(['pathStack'])
defineEmits(['home', 'navigate'])
</script>