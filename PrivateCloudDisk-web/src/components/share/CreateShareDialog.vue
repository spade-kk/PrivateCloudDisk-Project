<template>
  <Teleport to="body">
    <div
      v-if="visible"
      class="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4"
      @click.self="$emit('close')"
    >
      <div class="fade-in w-full max-w-md rounded-xl bg-white p-5 shadow-lg sm:p-6">
        <div class="mb-5 flex items-center justify-between">
          <h2 class="text-lg font-bold text-neutral-700 sm:text-xl">新建分享</h2>
          <button @click="$emit('close')" class="icon-button -mr-2" title="关闭">
            <i class="fa fa-times"></i>
          </button>
        </div>
        <form class="space-y-4" @submit.prevent="submit">
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">分享名称</label>
            <input
              v-model="form.title"
              class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30"
              placeholder="请输入分享名称"
              required
            />
          </div>
          <div>
            <label class="mb-1 block text-sm font-medium text-neutral-600">有效期</label>
            <select v-model="form.expiresIn" class="w-full rounded-lg border border-neutral-200 px-4 py-2 focus:ring-2 focus:ring-primary/30">
              <option value="7">7 天</option>
              <option value="30">30 天</option>
              <option value="0">永久有效</option>
            </select>
          </div>
          <label class="flex items-center gap-2 text-sm text-neutral-600">
            <input v-model="form.needCode" type="checkbox" class="h-4 w-4 rounded border-neutral-300 text-primary focus:ring-primary" />
            生成提取码
          </label>
          <div class="grid grid-cols-2 gap-3 pt-2 sm:flex sm:justify-end">
            <button type="button" @click="$emit('close')" class="touch-button rounded-lg border border-neutral-200 px-4 py-2 hover:bg-neutral-50">
              取消
            </button>
            <button type="submit" class="touch-button rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90">
              创建
            </button>
          </div>
        </form>
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { reactive } from 'vue'
import { useShareStore } from '@/stores/shareStore'

defineProps({
  visible: Boolean,
})
const emit = defineEmits(['close', 'created'])
const shareStore = useShareStore()

const form = reactive({
  title: '',
  expiresIn: '7',
  needCode: true,
})

const submit = async () => {
  await shareStore.createShare({ ...form })
  emit('created')
  emit('close')
  form.title = ''
  form.expiresIn = '7'
  form.needCode = true
}
</script>
