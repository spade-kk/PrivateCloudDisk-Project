import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const type = ref('info')
  const visible = ref(false)
  let timer = null

  function showToast(msg, toastType = 'info', duration = 3000) {
    if (timer) clearTimeout(timer)
    message.value = msg
    type.value = toastType
    visible.value = true
    timer = setTimeout(() => {
      visible.value = false
    }, duration)
  }

  function hideToast() {
    visible.value = false
    if (timer) clearTimeout(timer)
  }

  return { message, type, visible, showToast, hideToast }
})