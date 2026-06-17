import { defineStore } from 'pinia'
import { ref } from 'vue'

export type ToastType = 'info' | 'success' | 'warning' | 'error'

export const useToastStore = defineStore('toast', () => {
  const message = ref('')
  const type = ref<ToastType>('info')
  const visible = ref(false)
  let timer: ReturnType<typeof setTimeout> | null = null

  function showToast(msg: string, toastType: ToastType = 'info', duration: number = 3000): void {
    if (timer) clearTimeout(timer)
    message.value = msg
    type.value = toastType
    visible.value = true
    timer = setTimeout(() => {
      visible.value = false
    }, duration)
  }

  function hideToast(): void {
    visible.value = false
    if (timer) clearTimeout(timer)
  }

  return { message, type, visible, showToast, hideToast }
})