<template>
  <footer class="bg-neutral-900 text-neutral-300">
    <!-- Newsletter -->
    <div class="border-b border-neutral-800">
      <div class="mx-auto max-w-7xl px-4 py-12 sm:px-6 lg:px-8">
        <div class="flex flex-col items-center gap-6 sm:flex-row sm:justify-between">
          <div>
            <h3 class="text-lg font-semibold text-white">订阅 CloudDrive 资讯</h3>
            <p class="mt-1 text-sm text-neutral-400">获取最新产品更新、技术分享和优惠活动</p>
          </div>
          <form @submit.prevent="handleSubscribe" class="flex w-full gap-3 sm:w-auto">
            <input
              v-model="email"
              type="email"
              placeholder="输入您的邮箱"
              class="flex-1 sm:w-64 rounded-xl border border-neutral-700 bg-neutral-800 px-4 py-2.5 text-sm text-white placeholder:text-neutral-500 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/30"
              required
            />
            <button type="submit" :disabled="subscribed" class="rounded-xl bg-primary px-5 py-2.5 text-sm font-semibold text-white transition hover:bg-primary/90 disabled:opacity-50 shrink-0">
              {{ subscribed ? '已订阅' : '订阅' }}
            </button>
          </form>
        </div>
      </div>
    </div>

    <div class="mx-auto max-w-7xl px-4 py-16 sm:px-6 lg:px-8">
      <div class="grid grid-cols-2 gap-10 sm:grid-cols-2 lg:grid-cols-6">
        <!-- Brand -->
        <div class="col-span-2">
          <div class="flex items-center gap-2.5">
            <div class="flex h-9 w-9 items-center justify-center rounded-lg bg-gradient-to-br from-primary to-info">
              <i class="fa fa-cloud text-lg text-white"></i>
            </div>
            <span class="text-lg font-bold text-white tracking-tight">CloudDrive</span>
          </div>
          <p class="mt-4 max-w-sm text-sm leading-relaxed text-neutral-400">
            CloudDrive 私有云盘，为企业提供安全、高效、智能的文件存储与管理解决方案。端到端加密，随时随地访问您的数据。
          </p>
          <div class="mt-5 flex items-center gap-3">
            <a v-for="s in socials" :key="s.label" :href="s.url" target="_blank" class="flex h-9 w-9 items-center justify-center rounded-lg bg-neutral-800 text-neutral-400 transition hover:bg-primary hover:text-white" :title="s.label">
              <i :class="s.icon" class="text-sm"></i>
            </a>
          </div>
          <!-- App Store badges -->
          <div class="mt-5 flex items-center gap-3">
            <a href="#" class="inline-flex items-center gap-1.5 rounded-lg bg-neutral-800 px-3 py-2 text-xs text-neutral-300 hover:bg-neutral-700 transition">
              <i class="fa fa-apple text-base"></i>
              <div class="text-left leading-tight"><span class="block text-[10px]">Download on the</span>App Store</div>
            </a>
            <a href="#" class="inline-flex items-center gap-1.5 rounded-lg bg-neutral-800 px-3 py-2 text-xs text-neutral-300 hover:bg-neutral-700 transition">
              <i class="fa fa-google text-base"></i>
              <div class="text-left leading-tight"><span class="block text-[10px]">GET IT ON</span>Google Play</div>
            </a>
          </div>
        </div>

        <!-- Column: Product -->
        <div>
          <h4 class="mb-4 text-sm font-semibold text-white">产品</h4>
          <ul class="space-y-2.5">
            <li><router-link to="/features" class="text-sm text-neutral-400 hover:text-white transition">功能特性</router-link></li>
            <li><router-link to="/download" class="text-sm text-neutral-400 hover:text-white transition">客户端下载</router-link></li>
            <li><router-link to="/pricing" class="text-sm text-neutral-400 hover:text-white transition">定价方案</router-link></li>
            <li><router-link to="/security-center" class="text-sm text-neutral-400 hover:text-white transition">安全中心</router-link></li>
            <li><router-link to="/changelog" class="text-sm text-neutral-400 hover:text-white transition">更新日志</router-link></li>
            <li><router-link to="/status" class="text-sm text-neutral-400 hover:text-white transition">服务状态</router-link></li>
          </ul>
        </div>

        <!-- Column: Solutions -->
        <div>
          <h4 class="mb-4 text-sm font-semibold text-white">解决方案</h4>
          <ul class="space-y-2.5">
            <li><router-link to="/solutions" class="text-sm text-neutral-400 hover:text-white transition">行业解决方案</router-link></li>
            <li><router-link to="/case-studies" class="text-sm text-neutral-400 hover:text-white transition">客户案例</router-link></li>
            <li><router-link to="/partners" class="text-sm text-neutral-400 hover:text-white transition">合作伙伴</router-link></li>
            <li><router-link to="/docs" class="text-sm text-neutral-400 hover:text-white transition">API 接口</router-link></li>
            <li><a href="#" class="text-sm text-neutral-400 hover:text-white transition">系统集成</a></li>
            <li><a href="#" class="text-sm text-neutral-400 hover:text-white transition">私有化部署</a></li>
          </ul>
        </div>

        <!-- Column: Support -->
        <div>
          <h4 class="mb-4 text-sm font-semibold text-white">支持</h4>
          <ul class="space-y-2.5">
            <li><router-link to="/docs" class="text-sm text-neutral-400 hover:text-white transition">帮助文档</router-link></li>
            <li><router-link to="/contact" class="text-sm text-neutral-400 hover:text-white transition">联系我们</router-link></li>
            <li><router-link to="/docs" class="text-sm text-neutral-400 hover:text-white transition">常见问题</router-link></li>
            <li><a href="#" class="text-sm text-neutral-400 hover:text-white transition">社区论坛</a></li>
            <li><a href="#" class="text-sm text-neutral-400 hover:text-white transition">视频教程</a></li>
            <li><a href="#" class="text-sm text-neutral-400 hover:text-white transition">技术支持</a></li>
          </ul>
        </div>

        <!-- Column: Company -->
        <div>
          <h4 class="mb-4 text-sm font-semibold text-white">公司</h4>
          <ul class="space-y-2.5">
            <li><router-link to="/about" class="text-sm text-neutral-400 hover:text-white transition">关于我们</router-link></li>
            <li><router-link to="/careers" class="text-sm text-neutral-400 hover:text-white transition">加入我们</router-link></li>
            <li><router-link to="/blog" class="text-sm text-neutral-400 hover:text-white transition">技术博客</router-link></li>
            <li><router-link to="/press" class="text-sm text-neutral-400 hover:text-white transition">媒体报道</router-link></li>
            <li><router-link to="/contact" class="text-sm text-neutral-400 hover:text-white transition">商务合作</router-link></li>
            <li><router-link to="/privacy" class="text-sm text-neutral-400 hover:text-white transition">隐私政策</router-link></li>
          </ul>
        </div>
      </div>

      <!-- Bottom bar -->
      <div class="mt-12 flex flex-col items-center justify-between gap-4 border-t border-neutral-800 pt-8 sm:flex-row">
        <div class="flex items-center gap-4 text-xs text-neutral-500">
          <span>&copy; {{ year }} CloudDrive. All rights reserved.</span>
          <span class="hidden sm:inline">·</span>
          <span>京ICP备XXXXXXXX号-1</span>
        </div>
        <div class="flex items-center gap-4 text-xs text-neutral-500">
          <router-link to="/privacy" class="hover:text-white transition">隐私政策</router-link>
          <router-link to="/terms" class="hover:text-white transition">服务条款</router-link>
          <a href="#" class="hover:text-white transition">Cookie 设置</a>
          <a href="#" class="hover:text-white transition">网站地图</a>
        </div>
      </div>
    </div>
  </footer>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const year = new Date().getFullYear()
const email = ref('')
const subscribed = ref(false)

const socials = [
  { label: 'GitHub', icon: 'fa fa-github', url: '#' },
  { label: 'Twitter', icon: 'fa fa-twitter', url: '#' },
  { label: '微信公众号', icon: 'fa fa-weixin', url: '#' },
  { label: '邮箱', icon: 'fa fa-envelope', url: '#' },
  { label: '知乎', icon: 'fa fa-comment', url: '#' },
]

function handleSubscribe() {
  if (email.value) {
    subscribed.value = true
    email.value = ''
    setTimeout(() => { subscribed.value = false }, 3000)
  }
}
</script>