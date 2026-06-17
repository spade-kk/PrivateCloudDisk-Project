<template>
  <div>
    <!-- Hero -->
    <section class="bg-gradient-to-br from-primary/5 via-white to-info/5 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-primary/10 px-3 py-1 text-xs font-medium text-primary">联系我们</span>
          <h1 class="mt-4 text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl">获取帮助与支持</h1>
          <p class="mt-4 text-lg text-neutral-500">无论您有任何问题，我们的团队都随时为您提供帮助</p>
        </div>
      </div>
    </section>

    <!-- Contact Cards -->
    <section class="-mt-8 pb-20 sm:pb-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="card in contactCards" :key="card.title" class="rounded-2xl border border-neutral-200 bg-white p-6 text-center transition hover:-translate-y-1 hover:shadow-lg">
            <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-xl" :class="card.bgClass">
              <i :class="[card.icon, 'text-lg', card.iconClass]"></i>
            </div>
            <h3 class="mt-4 text-base font-semibold text-neutral-800">{{ card.title }}</h3>
            <p class="mt-1 text-xs text-neutral-400">{{ card.desc }}</p>
            <p class="mt-2 text-sm font-medium text-primary">{{ card.contact }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- Contact Form + Info -->
    <section class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-12 lg:grid-cols-2">
          <!-- Form -->
          <div class="rounded-2xl border border-neutral-200 bg-white p-8">
            <h2 class="text-xl font-bold text-neutral-800">发送消息</h2>
            <p class="mt-1 text-sm text-neutral-400">我们会在 24 小时内回复您的消息</p>
            <form @submit.prevent="handleSubmit" class="mt-6 space-y-4">
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label class="text-sm font-medium text-neutral-600">姓名 *</label>
                  <input v-model="form.name" type="text" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" required />
                </div>
                <div>
                  <label class="text-sm font-medium text-neutral-600">公司名称</label>
                  <input v-model="form.company" type="text" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" />
                </div>
              </div>
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div>
                  <label class="text-sm font-medium text-neutral-600">邮箱 *</label>
                  <input v-model="form.email" type="email" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" required />
                </div>
                <div>
                  <label class="text-sm font-medium text-neutral-600">手机号码</label>
                  <input v-model="form.phone" type="tel" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" />
                </div>
              </div>
              <div>
                <label class="text-sm font-medium text-neutral-600">咨询类型 *</label>
                <select v-model="form.type" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" required>
                  <option value="">请选择</option>
                  <option value="sales">销售咨询</option>
                  <option value="support">技术支持</option>
                  <option value="billing">计费与账户</option>
                  <option value="partnership">商务合作</option>
                  <option value="other">其他</option>
                </select>
              </div>
              <div>
                <label class="text-sm font-medium text-neutral-600">消息内容 *</label>
                <textarea v-model="form.message" rows="5" class="mt-1 w-full rounded-lg border border-neutral-200 px-3 py-2.5 text-sm focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20" placeholder="请详细描述您的需求..." required></textarea>
              </div>
              <div class="flex items-center gap-2">
                <input type="checkbox" id="agree" v-model="form.agree" class="h-4 w-4 rounded border-neutral-300 text-primary" required />
                <label for="agree" class="text-xs text-neutral-400">我已阅读并同意 <a href="#" class="text-primary hover:underline">隐私政策</a></label>
              </div>
              <button type="submit" :disabled="submitting" class="w-full rounded-xl bg-primary py-3 text-sm font-semibold text-white hover:bg-primary/90 disabled:opacity-50 transition">
                {{ submitting ? '发送中...' : '发送消息' }}
              </button>
            </form>
          </div>

          <!-- Info -->
          <div class="space-y-6">
            <div class="rounded-2xl border border-neutral-200 bg-white p-8">
              <h3 class="text-lg font-bold text-neutral-800">企业服务热线</h3>
              <div class="mt-4 space-y-3">
                <div class="flex items-center gap-3">
                  <i class="fa fa-phone text-primary"></i>
                  <span class="text-sm text-neutral-600">400-888-XXXX</span>
                </div>
                <div class="flex items-center gap-3">
                  <i class="fa fa-envelope text-primary"></i>
                  <span class="text-sm text-neutral-600">support@clouddrive.com</span>
                </div>
                <p class="text-xs text-neutral-400 mt-2">工作时间: 周一至周五 9:00 - 21:00</p>
                <p class="text-xs text-neutral-400">周末及节假日: 10:00 - 18:00</p>
              </div>
            </div>

            <div class="rounded-2xl border border-neutral-200 bg-white p-8">
              <h3 class="text-lg font-bold text-neutral-800">快速链接</h3>
              <div class="mt-4 space-y-3">
                <router-link v-for="link in quickLinks" :key="link.label" :to="link.url" class="flex items-center gap-3 text-sm text-neutral-600 hover:text-primary transition">
                  <i :class="[link.icon, 'w-5 text-center text-primary']"></i>
                  {{ link.label }}
                </router-link>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- Map placeholder -->
    <section class="border-t border-neutral-100 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <h2 class="text-2xl font-bold text-neutral-900 text-center">我们的办公地点</h2>
        <div class="mt-10 grid grid-cols-1 gap-6 sm:grid-cols-2">
          <div v-for="office in offices" :key="office.city" class="rounded-2xl border border-neutral-200 bg-white p-6">
            <div class="flex items-start gap-4">
              <i class="fa fa-building text-2xl text-primary mt-1"></i>
              <div>
                <h3 class="text-lg font-semibold text-neutral-800">{{ office.city }} · {{ office.type }}</h3>
                <p class="mt-1 text-sm text-neutral-500">{{ office.address }}</p>
                <p class="mt-1 text-xs text-neutral-400"><i class="fa fa-phone mr-1"></i> {{ office.phone }}</p>
                <p class="mt-0.5 text-xs text-neutral-400"><i class="fa fa-envelope mr-1"></i> {{ office.email }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const submitting = ref(false)
const form = ref({ name: '', company: '', email: '', phone: '', type: '', message: '', agree: false })

const contactCards = [
  { title: '售前咨询', desc: '产品咨询与方案推荐', contact: 'sales@clouddrive.com', icon: 'fa fa-comments', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '技术支持', desc: '技术问题与故障排查', contact: 'support@clouddrive.com', icon: 'fa fa-life-ring', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '商务合作', desc: '渠道合作与生态合作', contact: 'partner@clouddrive.com', icon: 'fa fa-handshake-o', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '社区论坛', desc: '用户交流与技术分享', contact: '社区论坛', icon: 'fa fa-users', bgClass: 'bg-info/10', iconClass: 'text-info' },
]

const quickLinks = [
  { label: '帮助文档', url: '/docs', icon: 'fa fa-book' },
  { label: 'API 文档', url: '#', icon: 'fa fa-code' },
  { label: '系统状态', url: '#', icon: 'fa fa-check-circle' },
  { label: '更新日志', url: '#', icon: 'fa fa-refresh' },
  { label: '安全白皮书', url: '#', icon: 'fa fa-shield' },
]

const offices = [
  { city: '北京', type: '全球总部', address: '北京市海淀区中关村软件园 A 座 18 层', phone: '010-88888888', email: 'beijing@clouddrive.com' },
  { city: '上海', type: '研发中心', address: '上海市浦东新区张江高科技园区 B 栋 12 层', phone: '021-66666666', email: 'shanghai@clouddrive.com' },
]

async function handleSubmit() {
  submitting.value = true
  // 模拟提交
  await new Promise(r => setTimeout(r, 1000))
  alert('消息已发送，我们会在 24 小时内回复您。')
  form.value = { name: '', company: '', email: '', phone: '', type: '', message: '', agree: false }
  submitting.value = false
}
</script>