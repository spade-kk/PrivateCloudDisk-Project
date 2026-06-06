import { defineStore } from 'pinia'

export const useStarredStore = defineStore('starred', () => {
  async function fetchStarredNodes() {
    return []
  }

  return { fetchStarredNodes }
})
