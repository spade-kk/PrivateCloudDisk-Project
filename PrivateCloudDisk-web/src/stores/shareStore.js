import { defineStore } from 'pinia'
import client from '@/api/client'

export const useShareStore = defineStore('share', () => {
  async function fetchMyShares() {
    const res = await client.get('/v1/shares/my')
    return res.data.data
  }
  async function createShare(data) {
    await client.post('/v1/shares', data)
  }
  async function revokeShare(id) {
    await client.delete(`/v1/shares/${id}`)
  }
  return { fetchMyShares, createShare, revokeShare }
})