import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSelectionStore = defineStore('selection', () => {
  const selectedIds = ref(new Set())

  function toggleSelect(id) {
    if (selectedIds.value.has(id)) selectedIds.value.delete(id)
    else selectedIds.value.add(id)
  }

  function clearSelection() { selectedIds.value.clear() }

  return { selectedIds, toggleSelect, clearSelection }
})