import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useSelectionStore = defineStore('selection', () => {
  const selectedIds = ref<Set<string>>(new Set())
  const selectedTypes = ref<Map<string, string>>(new Map())

  function toggleSelect(id: string, type: string): void {
    if (selectedIds.value.has(id)) {
      selectedIds.value.delete(id)
      selectedTypes.value.delete(id)
    } else {
      selectedIds.value.add(id)
      selectedTypes.value.set(id, type)
    }
  }

  function clearSelection(): void {
    selectedIds.value.clear()
    selectedTypes.value.clear()
  }

  return { selectedIds, selectedTypes, toggleSelect, clearSelection }
})