<template>
  <div class="api-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-success/5 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-success/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">API 参考</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            API 参考文档
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            完整的 RESTful API 文档，包含认证方式、接口规范、请求示例和错误码说明
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
            <!-- Overview -->
            <section id="overview" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">接口概览</h2>
              <p class="mt-2 text-neutral-500">PrivateCloudDisk 提供两类 RESTful API 接口。</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div class="rounded-xl border border-primary/20 bg-primary/5 p-4">
                  <div class="flex items-center gap-2">
                    <i class="fa fa-gateway text-primary"></i>
                    <span class="font-semibold text-neutral-800">业务服务 API</span>
                  </div>
                  <p class="mt-2 text-sm text-neutral-600">用户、文件、目录、配额、收藏、分享等业务接口</p>
                  <code class="mt-2 block font-mono text-xs text-primary">/api/v1/business/*</code>
                </div>
                <div class="rounded-xl border border-info/20 bg-info/5 p-4">
                  <div class="flex items-center gap-2">
                    <i class="fa fa-file text-info"></i>
                    <span class="font-semibold text-neutral-800">文件服务 API</span>
                  </div>
                  <p class="mt-2 text-sm text-neutral-600">文件上传、下载、缩略图等接口</p>
                  <code class="mt-2 block font-mono text-xs text-info">/api/v1/files/*</code>
                </div>
              </div>
            </section>

            <!-- Authentication -->
            <section id="auth" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">认证方式</h2>
              <p class="mt-2 text-neutral-500">系统采用 JWT 双令牌认证机制。</p>

              <div class="mt-6 space-y-4">
                <div class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="text-base font-semibold text-neutral-800">用户令牌 (User JWT)</h3>
                  <p class="mt-2 text-sm text-neutral-500">用于标识用户身份，有效期 24 小时</p>
                  <div class="mt-3 rounded bg-neutral-900 px-3 py-2">
                    <code class="font-mono text-xs text-neutral-300">Authorization: Bearer eyJhbGciOiJSUzI1NiIs...</code>
                  </div>
                </div>

                <div class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="text-base font-semibold text-neutral-800">操作凭证 (Operation Token)</h3>
                  <p class="mt-2 text-sm text-neutral-500">用于文件操作授权，有效期 1 小时，速率限制 300 次</p>
                  <div class="mt-3 rounded bg-neutral-900 px-3 py-2">
                    <code class="font-mono text-xs text-neutral-300">X-Operation-Token: eyJhbGciOiJSUzI1NiIs...</code>
                  </div>
                </div>
              </div>

              <!-- Login Flow -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">登录流程</h3>
              <div class="mt-4 rounded-xl border border-neutral-200 bg-neutral-50 p-4">
                <div class="space-y-3">
                  <div class="flex items-center gap-3">
                    <span class="h-6 w-6 rounded-full bg-primary text-xs text-white flex items-center justify-center">1</span>
                    <span class="text-sm text-neutral-700">POST /api/v1/business/users/login</span>
                  </div>
                  <div class="ml-3 border-l-2 border-neutral-200 pl-4 space-y-3">
                    <div class="text-xs text-neutral-500">
                      <p>Request Body:</p>
                      <code class="mt-1 block rounded bg-white px-2 py-1 text-neutral-600">{"account": "xxx", "password": "xxx", "token": "turnstile_token"}</code>
                    </div>
                    <div class="text-xs text-neutral-500">
                      <p>Response:</p>
                      <code class="mt-1 block rounded bg-white px-2 py-1 text-neutral-600">{"code": 200, "data": "jwt_token_here"}</code>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- User API -->
            <section id="users" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">用户管理 API</h2>

              <div class="mt-6 space-y-4">
                <div v-for="api in userApis" :key="api.method + api.path"
                     class="rounded-xl border border-neutral-200">
                  <div class="flex items-center gap-3 p-4 border-b border-neutral-100">
                    <span class="shrink-0 rounded px-2 py-0.5 text-xs font-bold" :class="api.method === 'GET' ? 'bg-success/10 text-success' : api.method === 'POST' ? 'bg-primary/10 text-primary' : api.method === 'PUT' ? 'bg-warning/10 text-warning' : 'bg-danger/10 text-danger'">
                      {{ api.method }}
                    </span>
                    <code class="font-mono text-sm text-neutral-700">{{ api.path }}</code>
                  </div>
                  <div class="p-4">
                    <p class="text-sm text-neutral-500">{{ api.desc }}</p>
                    <div v-if="api.params" class="mt-3">
                      <p class="text-xs font-medium text-neutral-400">Parameters:</p>
                      <div class="mt-1 space-y-1">
                        <div v-for="param in api.params" :key="param.name" class="flex items-center gap-2 text-xs">
                          <code class="text-primary">{{ param.name }}</code>
                          <span class="text-neutral-400">{{ param.type }}</span>
                          <span class="text-neutral-500">{{ param.desc }}</span>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- File API -->
            <section id="files" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">文件管理 API</h2>

              <div class="mt-6 space-y-4">
                <div v-for="api in fileApis" :key="api.method + api.path"
                     class="rounded-xl border border-neutral-200">
                  <div class="flex items-center gap-3 p-4 border-b border-neutral-100">
                    <span class="shrink-0 rounded px-2 py-0.5 text-xs font-bold" :class="api.method === 'GET' ? 'bg-success/10 text-success' : 'bg-primary/10 text-primary'">
                      {{ api.method }}
                    </span>
                    <code class="font-mono text-sm text-neutral-700">{{ api.path }}</code>
                  </div>
                  <div class="p-4">
                    <p class="text-sm text-neutral-500">{{ api.desc }}</p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Error Codes -->
            <section id="errors" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">错误码参考</h2>
              <p class="mt-2 text-neutral-500">API 返回统一错误码格式。</p>

              <div class="mt-6 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">错误码</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">说明</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">处理建议</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="err in errorCodes" :key="err.code" class="border-b border-neutral-100">
                      <td class="py-3 px-4"><code class="rounded bg-danger/10 px-1.5 py-0.5 text-xs text-danger">{{ err.code }}</code></td>
                      <td class="py-3 px-4 text-neutral-600">{{ err.desc }}</td>
                      <td class="py-3 px-4 text-neutral-500 text-xs">{{ err.suggestion }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/development" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                开发指南
              </router-link>
              <router-link to="/docs/security" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                安全文档
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

const activeSection = ref('overview')

const sections = [
  { id: 'overview', title: '接口概览' },
  { id: 'auth', title: '认证方式' },
  { id: 'users', title: '用户 API' },
  { id: 'files', title: '文件 API' },
  { id: 'errors', title: '错误码' },
]

const userApis = [
  { method: 'POST', path: '/api/v1/business/users/login', desc: '用户登录，验证密码并签发 JWT', params: [{ name: 'account', type: 'string', desc: '账号/手机/邮箱' }, { name: 'password', type: 'string', desc: 'PBKDF2 预哈希后的密码' }] },
  { method: 'POST', path: '/api/v1/business/users/register', desc: '用户注册，创建新账号', params: [{ name: 'account', type: 'string', desc: '用户名' }, { name: 'password', type: 'string', desc: '密码' }] },
  { method: 'GET', path: '/api/v1/business/users/me', desc: '获取当前用户信息' },
  { method: 'PUT', path: '/api/v1/business/users/password', desc: '修改密码' },
]

const fileApis = [
  { method: 'GET', path: '/api/v1/business/files/tree', desc: '获取用户目录树结构' },
  { method: 'POST', path: '/api/v1/business/files/upload-session', desc: '创建文件上传会话' },
  { method: 'POST', path: '/api/v1/business/files/upload-session/:id/complete', desc: '完成文件上传' },
  { method: 'GET', path: '/api/v1/business/files/:fileId', desc: '获取文件详情' },
  { method: 'DELETE', path: '/api/v1/business/files/:fileId', desc: '删除文件（移入回收站）' },
]

const errorCodes = [
  { code: 400, desc: '请求参数错误', suggestion: '检查请求参数格式和必填项' },
  { code: 401, desc: '认证失败', suggestion: '检查 JWT token 是否有效' },
  { code: 403, desc: '权限不足', suggestion: '检查用户权限设置' },
  { code: 404, desc: '资源不存在', suggestion: '检查请求的文件或目录是否存在' },
  { code: 429, desc: '请求过于频繁', suggestion: '降低请求频率，等待后重试' },
  { code: 500, desc: '服务器内部错误', suggestion: '联系技术支持或查看服务端日志' },
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
