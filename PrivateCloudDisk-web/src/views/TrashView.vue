<template>
  <div class="space-y-4">
    <h1 class="text-2xl font-bold">回收站</h1>
    <div v-if="loading" class="flex justify-center py-10"><LoadingSpinner /></div>
    <div v-else-if="items.length === 0" class="bg-white rounded-lg shadow-card p-10 text-center text-neutral-400">
      <i class="fa fa-trash text-4xl mb-2"></i><p>回收站为空</p>
    </div>
    <div class="bg-white rounded-lg shadow-card overflow-hidden">
      <div class="grid grid-cols-12 bg-neutral-50 p-3 font-medium text-sm">
        <div class="col-span-6">名称</div>
        <div class="col-span-3">删除时间</div>
        <div class="col-span-3 text-right">操作</div>
      </div>
      <TrashItem v-for="item in items" :key="item.id" :item="item" @restore="restoreItem" @delete="deletePermanently" />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import TrashItem from '@/components/trash/TrashItem.vue'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'
import { useTrashStore } from '@/stores/trashStore'

const trashStore = useTrashStore()
const items = ref([])
const loading = ref(false)

const loadTrash = async () => {
  loading.value = true
  items.value = await trashStore.fetchTrash()
  loading.value = false
}
const restoreItem = async (id) => { await trashStore.restore(id); await loadTrash() }
const deletePermanently = async (id) => { await trashStore.permanentDelete(id); await loadTrash() }
onMounted(loadTrash)
</script>