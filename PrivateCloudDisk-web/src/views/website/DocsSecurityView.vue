<template>
  <div class="security-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-danger/5 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-danger/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">安全文档</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            安全设计文档
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            四层安全防线，详解认证鉴权、密码加密、限流防护和数据安全机制
          </p>
        </div>
      </div>
    </section>

    <!-- Content -->
    <section class="py-12 sm:py-16">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-8 lg:grid-cols-4">
          <!-- Sidebar -->
          <aside class="lg:col-span-1">
            <div class="sticky top-24 rounded-2xl border border-neutral-200 bg-white p-4">
              <h3 class="text-xs font-semibold uppercase tracking-wider text-neutral-400 mb-3">目录</h3>
              <nav class="space-y-1">
                <a v-for="section in sections" :key="section.id" 
                   :href="'#' + section.id"
                   class="block rounded-lg px-3 py-2 text-sm transition"
                   :class="activeSection === section.id ? 'bg-primary/10 text-primary font-medium' : 'text-neutral-600 hover:bg-neutral-50'">
                  {{ section.title }}
                </a>
              </nav>
            </div>
          </aside>

          <!-- Main Content -->
          <div class="lg:col-span-3 space-y-8">
            <!-- Security Architecture -->
            <section id="architecture" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">安全架构总览</h2>
              <p class="mt-2 text-neutral-500">系统采用纵深防御策略，从网关层到数据层共四道安全防线。</p>

              <div class="mt-6 space-y-4">
                <div v-for="(layer, i) in securityLayers" :key="layer.name"
                     class="flex items-start gap-4 rounded-xl border border-neutral-200 p-4">
                  <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-full text-sm font-bold text-white" :class="layer.bgClass">
                    {{ i + 1 }}
                  </div>
                  <div>
                    <h3 class="font-semibold text-neutral-800">{{ layer.name }}</h3>
                    <p class="mt-1 text-sm text-neutral-500">{{ layer.desc }}</p>
                    <div class="mt-2 flex flex-wrap gap-2">
                      <span v-for="tech in layer.techs" :key="tech"
                            class="rounded bg-neutral-100 px-2 py-0.5 text-xs text-neutral-600">
                        {{ tech }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- JWT Auth -->
            <section id="jwt" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">JWT 认证机制</h2>

              <div class="mt-6">
                <div class="rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                  <h3 class="text-base font-semibold text-neutral-800 mb-3">JWT 配置</h3>
                  <div class="grid grid-cols-2 gap-4 text-sm">
                    <div>
                      <p class="text-neutral-500">算法</p>
                      <p class="font-medium text-neutral-800">RSA-256</p>
                    </div>
                    <div>
                      <p class="text-neutral-500">有效期</p>
                      <p class="font-medium text-neutral-800">24 小时</p>
                    </div>
                    <div>
                      <p class="text-neutral-500">密钥类型</p>
                      <p class="font-medium text-neutral-800">公私钥对</p>
                    </div>
                    <div>
                      <p class="text-neutral-500">载荷</p>
                      <p class="font-medium text-neutral-800">sub, iat, exp</p>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Auth Flow -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">认证流程</h3>
              <div class="mt-4 rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                <div class="space-y-3">
                  <div class="flex items-center gap-3">
                    <span class="h-6 w-6 rounded-full bg-primary text-xs text-white flex items-center justify-center">1</span>
                    <span class="text-sm text-neutral-700">用户登录 → 验证密码</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span class="h-6 w-6 rounded-full bg-primary text-xs text-white flex items-center justify-center">2</span>
                    <span class="text-sm text-neutral-700">签发 JWT (RSA-256)</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span class="h-6 w-6 rounded-full bg-primary text-xs text-white flex items-center justify-center">3</span>
                    <span class="text-sm text-neutral-700">后续请求携带 JWT</span>
                  </div>
                  <div class="flex items-center gap-3">
                    <span class="h-6 w-6 rounded-full bg-primary text-xs text-white flex items-center justify-center">4</span>
                    <span class="text-sm text-neutral-700">网关 JWT 验证 → 提取 X-User-Id → 透传业务服务</span>
                  </div>
                </div>
              </div>
            </section>

            <!-- Password Security -->
            <section id="password" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">密码安全</h2>
              <p class="mt-2 text-neutral-500">系统采用前端预哈希 + 后端二次加密的双层密码架构。</p>

              <!-- Double Hash Architecture -->
              <div class="mt-6 rounded-xl border border-neutral-200 bg-neutral-900 p-4">
                <div class="space-y-4">
                  <div class="text-center">
                    <div class="inline-block rounded-lg border border-neutral-700 bg-neutral-800 px-4 py-2">
                      <span class="text-xs text-neutral-400">原始密码</span>
                    </div>
                  </div>
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-500"></i>
                  </div>
                  <div class="text-center">
                    <div class="inline-block rounded-lg border border-primary/50 bg-primary/10 px-4 py-2">
                      <span class="text-xs text-primary">前端 PBKDF2-SHA256</span>
                      <p class="text-[10px] text-neutral-400 mt-1">迭代 600,000 次</p>
                    </div>
                  </div>
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-500"></i>
                  </div>
                  <div class="text-center">
                    <div class="inline-block rounded-lg border border-success/50 bg-success/10 px-4 py-2">
                      <span class="text-xs text-success">后端 BCrypt</span>
                      <p class="text-[10px] text-neutral-400 mt-1">轮数 12</p>
                    </div>
                  </div>
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-500"></i>
                  </div>
                  <div class="text-center">
                    <div class="inline-block rounded-lg border border-neutral-700 bg-neutral-800 px-4 py-2">
                      <span class="text-xs text-neutral-400">$2b$12$... (存入数据库)</span>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Design Reasons -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">设计理由</h3>
              <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div class="rounded-lg border border-neutral-200 p-3">
                  <p class="text-sm font-medium text-neutral-800">PBKDF2</p>
                  <p class="mt-1 text-xs text-neutral-500">内存密集型，抵抗 GPU 暴力破解</p>
                </div>
                <div class="rounded-lg border border-neutral-200 p-3">
                  <p class="text-sm font-medium text-neutral-800">BCrypt</p>
                  <p class="mt-1 text-xs text-neutral-500">自适应盐值，12 rounds 提供足够安全性</p>
                </div>
                <div class="rounded-lg border border-neutral-200 p-3">
                  <p class="text-sm font-medium text-neutral-800">双层哈希</p>
                  <p class="mt-1 text-xs text-neutral-500">即使数据库泄露，攻击者无法直接破解原始密码</p>
                </div>
                <div class="rounded-lg border border-neutral-200 p-3">
                  <p class="text-sm font-medium text-neutral-800">Pepper</p>
                  <p class="mt-1 text-xs text-neutral-500">固定 pepper 存储在代码中，与数据库分离</p>
                </div>
              </div>
            </section>

            <!-- Rate Limiting -->
            <section id="rate-limit" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">分布式限流</h2>
              <p class="mt-2 text-neutral-500">网关层实现多维度限流策略，保护系统免受滥用攻击。</p>

              <div class="mt-6 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">规则</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">维度</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">限制</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="rule in rateLimitRules" :key="rule.name" class="border-b border-neutral-100">
                      <td class="py-3 px-4 text-neutral-800">{{ rule.name }}</td>
                      <td class="py-3 px-4 text-neutral-600">{{ rule.dimension }}</td>
                      <td class="py-3 px-4"><code class="text-xs text-primary">{{ rule.limit }}</code></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Login Protection -->
            <section id="protection" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">防暴力破解</h2>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div class="rounded-xl border border-neutral-200 p-4">
                  <h3 class="text-sm font-semibold text-neutral-800">账号维度锁定</h3>
                  <div class="mt-3 space-y-2 text-sm">
                    <div class="flex justify-between">
                      <span class="text-neutral-500">连续失败次数</span>
                      <span class="font-medium">5 次</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-neutral-500">锁定时长</span>
                      <span class="font-medium">15 分钟</span>
                    </div>
                  </div>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4">
                  <h3 class="text-sm font-semibold text-neutral-800">IP 维度锁定</h3>
                  <div class="mt-3 space-y-2 text-sm">
                    <div class="flex justify-between">
                      <span class="text-neutral-500">连续失败次数</span>
                      <span class="font-medium">20 次</span>
                    </div>
                    <div class="flex justify-between">
                      <span class="text-neutral-500">锁定时长</span>
                      <span class="font-medium">30 分钟</span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/api" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                API 参考
              </router-link>
              <router-link to="/docs/database" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                数据库设计
                <i class="fa fa-arrow-right"></i>
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const activeSection = ref('architecture')

const sections = [
  { id: 'architecture', title: '安全架构' },
  { id: 'jwt', title: 'JWT认证' },
  { id: 'password', title: '密码安全' },
  { id: 'rate-limit', title: '限流机制' },
  { id: 'protection', title: '暴力破解防护' },
]

const securityLayers = [
  { name: '第一道防线：网关层', desc: 'JWT签名验证、分布式限流、请求头清洗、CORS', bgClass: 'bg-orange-500', techs: ['JWT验证', '限流', 'CORS', '请求清洗'] },
  { name: '第二道防线：业务服务层', desc: 'BCrypt密码哈希、登录失败锁定、人机验证、API防护', bgClass: 'bg-green-500', techs: ['BCrypt', '失败锁定', '人机验证', 'API防护'] },
  { name: '第三道防线：文件服务层', desc: '操作凭证JWT、并发控制、下载授权、完整性校验', bgClass: 'bg-purple-500', techs: ['操作凭证', '并发控制', '下载授权', 'SHA校验'] },
  { name: '第四道防线：数据层', desc: '外键约束、乐观锁、软删除、审计日志', bgClass: 'bg-red-500', techs: ['外键约束', '乐观锁', '软删除', '审计日志'] },
]

const rateLimitRules = [
  { name: '全局限流', dimension: '所有请求', limit: '1000 次/秒' },
  { name: '登录限流', dimension: 'IP', limit: '10 次/分钟' },
  { name: '注册限流', dimension: 'IP', limit: '3 次/分钟' },
  { name: '文件上传', dimension: '用户', limit: '100 次/分钟' },
  { name: '文件下载', dimension: '用户', limit: '200 次/分钟' },
  { name: 'API查询', dimension: '用户', limit: '300 次/分钟' },
  { name: '凭证申请', dimension: '用户', limit: '30 次/分钟' },
]

onMounted(() => {
  const handleScroll = () => {
    const scrollY = window.scrollY
    for (let i = sections.length - 1; i >= 0; i--) {
      const el = document.getElementById(sections[i].id)
      if (el && el.offsetTop - 100 <= scrollY) {
        activeSection.value = sections[i].id
        break
      }
    }
  }
  window.addEventListener('scroll', handleScroll)
  onUnmounted(() => window.removeEventListener('scroll', handleScroll))
})
</script>
