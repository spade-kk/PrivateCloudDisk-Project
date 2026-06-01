<template>
  <div class="max-w-2xl mx-auto space-y-6">
    <!-- 基本信息 -->
    <div class="bg-white rounded-lg shadow-card p-6">
      <h2 class="text-xl font-bold mb-4">个人资料</h2>
      <form @submit.prevent="updateProfile">
        <div class="space-y-4">
          <div>
            <label class="block text-sm font-medium mb-1">头像</label>
            <div class="flex items-center space-x-4">
              <img :src="avatarUrl" class="w-16 h-16 rounded-full object-cover border" />
              <button type="button" @click="selectAvatar" class="text-primary text-sm">更换头像</button>
              <input ref="avatarInput" type="file" accept="image/*" class="hidden" @change="uploadAvatar" />
            </div>
          </div>
          <div><label class="block text-sm font-medium mb-1">昵称</label><input v-model="userInfo.name" class="w-full px-3 py-2 border rounded-lg" /></div>
          <div><label class="block text-sm font-medium mb-1">邮箱</label><input v-model="userInfo.email" type="email" class="w-full px-3 py-2 border rounded-lg" /></div>
          <div><button type="submit" class="bg-primary text-white px-4 py-2 rounded-lg">保存修改</button></div>
        </div>
      </form>
    </div>
    <!-- 修改密码 -->
    <div class="bg-white rounded-lg shadow-card p-6">
      <h2 class="text-xl font-bold mb-4">修改密码</h2>
      <form @submit.prevent="changePassword">
        <div class="space-y-4">
          <div><label class="block text-sm font-medium mb-1">原密码</label><input v-model="passwordForm.old" type="password" class="w-full px-3 py-2 border rounded-lg" /></div>
          <div><label class="block text-sm font-medium mb-1">新密码</label><input v-model="passwordForm.new" type="password" class="w-full px-3 py-2 border rounded-lg" /></div>
          <div><label class="block text-sm font-medium mb-1">确认新密码</label><input v-model="passwordForm.confirm" type="password" class="w-full px-3 py-2 border rounded-lg" /></div>
          <div><button type="submit" class="bg-primary text-white px-4 py-2 rounded-lg">修改密码</button></div>
        </div>
      </form>
    </div>
    <!-- 登录日志入口 -->
    <div class="bg-white rounded-lg shadow-card p-6">
      <div class="flex justify-between items-center">
        <div><h2 class="text-xl font-bold">登录日志</h2><p class="text-sm text-neutral-500">查看最近登录记录</p></div>
        <router-link to="/login-history" class="text-primary">查看详情 &rarr;</router-link>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/userStore'
import { useToastStore } from '@/stores/toastStore'

const userStore = useUserStore()
const toastStore = useToastStore()
const userInfo = ref({ name: '', email: '' })
const avatarUrl = ref('')
const passwordForm = ref({ old: '', new: '', confirm: '' })
const avatarInput = ref(null)

onMounted(async () => {
  const data = await userStore.fetchUserInfo()
  userInfo.value = data
  avatarUrl.value = data.avatar || 'https://via.placeholder.com/64'
})
const updateProfile = async () => { await userStore.updateProfile(userInfo.value); toastStore.showToast('保存成功', 'success') }
const selectAvatar = () => avatarInput.value.click()
const uploadAvatar = async (e) => { /* 上传逻辑，调用后端API */ }
const changePassword = async () => {
  if (passwordForm.value.new !== passwordForm.value.confirm) { toastStore.showToast('两次密码不一致', 'error'); return }
  await userStore.changePassword(passwordForm.value.old, passwordForm.value.new)
  toastStore.showToast('密码修改成功', 'success')
  passwordForm.value = { old: '', new: '', confirm: '' }
}
</script>