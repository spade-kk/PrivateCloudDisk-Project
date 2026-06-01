<!-- src/components/modals/LoginModal.vue -->
<template>
  <div v-if="visible" class="fixed inset-0 bg-black/50 z-50 flex items-center justify-center p-4" @click.self="$emit('close')">
    <div class="bg-white rounded-xl shadow-lg w-full max-w-md p-6 fade-in">
      <div class="flex justify-between items-center mb-6">
        <h2 class="text-xl font-bold text-neutral-700">用户登录</h2>
        <button @click="$emit('close')" class="text-neutral-400 hover:text-neutral-700 transition">
          <i class="fa fa-times text-xl"></i>
        </button>
      </div>
      <form @submit.prevent="handleSubmit">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">手机号</label>
            <input 
              v-model="phone" 
              type="tel" 
              class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
              placeholder="请输入手机号"
              required
            />
          </div>
          <div>
            <label class="block text-sm font-medium text-neutral-600 mb-1">密码</label>
            <input 
              v-model="password" 
              type="password" 
              class="w-full px-4 py-2 border border-neutral-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-primary/30 focus:border-primary transition"
              placeholder="请输入密码"
              required
            />
          </div>
          <button 
            type="submit" 
            :disabled="loading"
            class="w-full bg-primary hover:bg-primary/90 text-white py-2.5 rounded-lg font-medium flex items-center justify-center space-x-2 transition"
          >
            <i v-if="loading" class="fa fa-spinner fa-spin"></i>
            <i v-else class="fa fa-sign-in"></i>
            <span>{{ loading ? '登录中...' : '登录' }}</span>
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['close', 'login'])

const phone = ref('15777446691')
const password = ref('20070315mwz')
const loading = ref(false)

const handleSubmit = async () => {
  if (!phone.value.trim() || !password.value.trim()) {
    // 可增加简单校验提示，但实际使用中父组件会处理提示
    return
  }
  loading.value = true
  try {
    await emit('login', phone.value, password.value)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.fade-in {
  animation: fadeIn 0.3s ease-out;
}
@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>