<template>
  <main class="image-preview-page" aria-label="图片预览工作区">
    <ImageLightbox
      :visible="true"
      :file-id="fileId"
      :file-name="fileName"
      :space-id="spaceId"
      @close="goBack"
    />
  </main>
</template>

<script setup lang="ts">
// AUDIT FIX [2.2]: 图片预览使用独立顶级页面并复用统一灯箱，修复控制台内旧弹窗链路未触发的问题。
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ImageLightbox from '@/components/preview/ImageLightbox.vue'

const route = useRoute()
const router = useRouter()
const fileId = computed(() => String(route.params.fileId || ''))
const fileName = computed(() => String(route.query.name || '图片预览'))
const spaceId = computed(() => String(route.query.space || '') || undefined)

function goBack() {
  if (window.history.length > 1) router.back()
  else router.replace({ name: 'Dashboard' })
}
</script>

<style scoped>
.image-preview-page {
  min-height: 100vh;
  min-height: 100dvh;
  background: #111827;
}
</style>
