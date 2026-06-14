<template>
  <div class="space-y-4 sm:space-y-6">
    <PageHeader
      title="帮助中心"
      description="常见问题、使用指南与技术支持"
      :breadcrumbs="[{ label: '帮助中心', icon: 'fa fa-question-circle' }]"
    />

    <!-- 搜索 -->
    <div class="responsive-panel p-4 sm:p-6">
      <div class="relative mx-auto max-w-xl">
        <i class="fa fa-search absolute left-4 top-1/2 -translate-y-1/2 text-neutral-400"></i>
        <input
          v-model="searchQuery"
          type="text"
          placeholder="搜索帮助文档..."
          class="w-full rounded-xl border border-neutral-200 py-3 pl-11 pr-4 text-sm focus:border-primary focus:outline-none"
        />
      </div>
    </div>

    <!-- 分类卡片 -->
    <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="category in filteredCategories" :key="category.key" class="responsive-panel p-5 transition-shadow hover:shadow-md cursor-pointer" @click="selectedCategory = category.key">
        <div class="mb-3 flex h-12 w-12 items-center justify-center rounded-xl" :class="category.bgClass">
          <i :class="[category.icon, 'text-xl', category.iconClass]"></i>
        </div>
        <h3 class="text-base font-semibold text-neutral-700">{{ category.label }}</h3>
        <p class="mt-1 text-xs text-neutral-400">{{ category.description }}</p>
        <p class="mt-2 text-xs text-primary">{{ category.articleCount }} 篇文章</p>
      </div>
    </div>

    <!-- 常见问题 -->
    <div class="responsive-panel p-4 sm:p-6">
      <h3 class="mb-4 text-base font-semibold text-neutral-700">常见问题</h3>
      <div class="space-y-1">
        <div v-for="(faq, i) in faqs" :key="i" class="rounded-lg border border-neutral-100">
          <button @click="toggleFaq(i)" class="flex w-full items-center justify-between px-4 py-3 text-left text-sm font-medium text-neutral-700 hover:bg-neutral-50">
            <span>{{ faq.question }}</span>
            <i :class="expandedFaq === i ? 'fa fa-angle-up' : 'fa fa-angle-down'" class="text-neutral-400"></i>
          </button>
          <div v-if="expandedFaq === i" class="border-t border-neutral-100 px-4 py-3 text-sm text-neutral-500">
            {{ faq.answer }}
          </div>
        </div>
      </div>
    </div>

    <!-- 联系支持 -->
    <div class="responsive-panel p-4 sm:p-6 text-center">
      <h3 class="text-base font-semibold text-neutral-700">仍未解决？</h3>
      <p class="mt-2 text-sm text-neutral-400">联系我们的技术支持团队获取帮助</p>
      <div class="mt-4 flex justify-center gap-3">
        <button class="rounded-lg bg-primary px-5 py-2 text-sm text-white hover:bg-primary/90">
          <i class="fa fa-envelope mr-1"></i> 发送邮件
        </button>
        <button class="rounded-lg border border-neutral-200 px-5 py-2 text-sm text-neutral-600 hover:bg-neutral-50">
          <i class="fa fa-comments mr-1"></i> 在线客服
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import PageHeader from '@/components/common/PageHeader.vue'

const searchQuery = ref('')
const expandedFaq = ref(null)
const selectedCategory = ref('')

const categories = [
  { key: 'getting-started', label: '快速入门', icon: 'fa fa-rocket', iconClass: 'text-primary', bgClass: 'bg-primary/10', description: '新手指南与基本操作', articleCount: 8 },
  { key: 'files', label: '文件管理', icon: 'fa fa-folder', iconClass: 'text-warning', bgClass: 'bg-warning/10', description: '上传、下载、分享与组织', articleCount: 12 },
  { key: 'security', label: '安全与隐私', icon: 'fa fa-shield', iconClass: 'text-success', bgClass: 'bg-success/10', description: '账号保护与数据安全', articleCount: 6 },
  { key: 'billing', label: '套餐与计费', icon: 'fa fa-credit-card', iconClass: 'text-purple-500', bgClass: 'bg-purple-50', description: '套餐选择与账单管理', articleCount: 5 },
  { key: 'troubleshooting', label: '故障排除', icon: 'fa fa-wrench', iconClass: 'text-danger', bgClass: 'bg-danger/10', description: '常见问题诊断与解决', articleCount: 10 },
  { key: 'api', label: 'API 文档', icon: 'fa fa-code', iconClass: 'text-info', bgClass: 'bg-info/10', description: '开发者接口与集成指南', articleCount: 7 },
]

const faqs = [
  { question: '如何上传大于 5GB 的文件？', answer: '我们支持断点续传和大文件分片上传，最大可支持 50GB 单文件。系统会自动将大文件分片上传并在服务端合并，上传过程中如遇网络中断可自动恢复。' },
  { question: '如何与他人共享文件？', answer: '在文件列表中找到要分享的文件，点击分享按钮即可生成分享链接。您可以设置链接有效期（1小时/1天/7天/永久）、访问密码和下载权限。' },
  { question: '回收站中的文件会保存多久？', answer: '回收站中的文件默认保存 30 天，到期后系统会自动永久删除。您也可以在回收站中手动永久删除或恢复文件。' },
  { question: '如何启用双因素认证？', answer: '前往「安全中心」→「双因素认证」，按提示使用 Google Authenticator 或其他 TOTP 应用扫描二维码即可启用。' },
  { question: '支持哪些文件类型预览？', answer: '支持图片（JPG/PNG/GIF/SVG/WebP）、视频（MP4/WebM）、音频（MP3/WAV）、文档（PDF/Word/Excel/PPT）、代码（支持语法高亮）、文本等格式的在线预览。' },
  { question: '如何升级存储空间？', answer: '前往「套餐管理」页面，选择适合的套餐即可升级。升级后存储配额立即生效，已有文件不受影响。' },
]

const filteredCategories = computed(() => {
  if (!searchQuery.value.trim()) return categories
  const q = searchQuery.value.toLowerCase()
  return categories.filter(c => c.label.toLowerCase().includes(q) || c.description.toLowerCase().includes(q))
})

function toggleFaq(i) {
  expandedFaq.value = expandedFaq.value === i ? null : i
}
</script>