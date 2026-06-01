<template>
  <div class="tree-picker max-h-96 overflow-y-auto">
    <div v-for="folder in folders" :key="folder.node_id" class="pl-4">
      <div class="flex items-center py-1 cursor-pointer hover:bg-neutral-100" @click="toggleExpand(folder)">
        <i :class="expandedFolders.has(folder.node_id) ? 'fa fa-folder-open' : 'fa fa-folder'" class="text-primary w-5"></i>
        <span class="ml-2 flex-1">{{ folder.node_name }}</span>
        <button @click.stop="select(folder)" class="text-primary text-sm">选择</button>
      </div>
      <div v-if="expandedFolders.has(folder.node_id)" class="ml-4">
        <TreeFolderPicker :folders="folder.children" @select="$emit('select', $event)" />
      </div>
    </div>
  </div>
</template>
<script setup>import { ref } from 'vue'; defineProps(['folders']); const emit = defineEmits(['select']); const expandedFolders = ref(new Set()); const toggleExpand = (folder) => { if (expandedFolders.value.has(folder.node_id)) expandedFolders.value.delete(folder.node_id); else expandedFolders.value.add(folder.node_id); }; const select = (folder) => emit('select', folder);</script>