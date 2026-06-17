import { defineStore } from 'pinia'

export const useShareStore = defineStore('share', () => {
  async function fetchMyShares(): Promise<any[]> {
    return []
  }
  async function createShare(data: Record<string, any>): Promise<void> {
    // await client.post('/v1/shares', data)
  }
  async function revokeShare(id: string): Promise<void> {
    // await client.delete(`/v1/shares/${id}`)
  }
  return { fetchMyShares, createShare, revokeShare }
})