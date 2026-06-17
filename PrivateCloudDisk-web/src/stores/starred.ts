import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useStarredStore = defineStore('starred', () => {
  async function fetchStarredNodes(): Promise<any[]> {
    return []
  }

  return { fetchStarredNodes }
})