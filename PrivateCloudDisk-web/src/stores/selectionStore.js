import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSelectionStore = defineStore('selection', () => {
  const selectedIds = ref(new Set())
  const selectedTypes = ref(new Map()) // id -> type

  function toggleSelect(id, type) {
    if (selectedIds.value.has(id)) {
      selectedIds.value.delete(id)
      selectedTypes.value.delete(id)
    } else {
      selectedIds.value.add(id)
      selectedTypes.value.set(id, type)
    }
  }

  function clearSelection() { 
      selectedIds.value.clear()
      selectedTypes.value.clear()
   }

  return { selectedIds, selectedTypes, toggleSelect, clearSelection }
})