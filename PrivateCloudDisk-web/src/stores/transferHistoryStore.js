import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useTransferHistoryStore = defineStore('transferHistory', () => {
  const records = ref([])

  function addRecord(type, fileName, size, status) {
    records.value.unshift({ id: Date.now(), type, fileName, size, status, time: new Date().toISOString() })
    if (records.value.length > 100) records.value.pop()
  }
  function updateRecord(id, status) {
    const idx = records.value.findIndex(r => r.id === id)
    if (idx !== -1) records.value[idx].status = status
  }
  return { records, addRecord, updateRecord }
})