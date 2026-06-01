import { defineStore } from 'pinia'

export const useTrashStore = defineStore('trash', () => {
  async function fetchTrash() {
    // const res = await client.get('/v1/trash')
    // return res.data.data
  }
  async function restore(id) {
    // await client.post(`/v1/trash/${id}/restore`)
  }
  async function permanentDelete(id) {
    // await client.delete(`/v1/trash/${id}`)
  }
  return { fetchTrash, restore, permanentDelete }
})