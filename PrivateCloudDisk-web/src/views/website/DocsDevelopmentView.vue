<template>
  <div class="development-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-warning/5 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-warning/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">开发指南</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            开发指南
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            环境搭建、本地开发、调试技巧和编码规范，助您快速参与 PrivateCloudDisk 开发
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
            <!-- Environment Setup -->
            <section id="environment" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">环境搭建</h2>
              <p class="mt-2 text-neutral-500">在开始开发前，请确保已安装所有必需的开发工具。</p>

              <!-- Tools -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">推荐开发工具</h3>
              <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="tool in devTools" :key="tool.name"
                     class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <i :class="[tool.icon, 'text-lg', tool.iconColor]"></i>
                    <span class="font-medium text-neutral-800">{{ tool.name }}</span>
                  </div>
                  <p class="mt-2 text-xs text-neutral-500">{{ tool.purpose }}</p>
                </div>
              </div>

              <!-- Environment Table -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">基础环境要求</h3>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">组件</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">版本要求</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="env in envRequirements" :key="env.name" class="border-b border-neutral-100">
                      <td class="py-2 px-3 text-neutral-800">{{ env.name }}</td>
                      <td class="py-2 px-3"><code class="text-xs text-primary">{{ env.version }}</code></td>
                      <td class="py-2 px-3 text-neutral-500 text-xs">{{ env.desc }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Project Structure -->
            <section id="structure" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">项目结构</h2>
              <p class="mt-2 text-neutral-500">PrivateCloudDisk 采用多模块 Monorepo 结构。</p>

              <div class="mt-6 space-y-3">
                <div v-for="project in projectStructure" :key="project.name"
                     class="flex items-start gap-4 rounded-xl border border-neutral-200 p-4">
                  <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" :class="project.bgClass">
                    <i :class="[project.icon, 'text-lg', project.iconClass]"></i>
                  </div>
                  <div class="flex-1">
                    <div class="flex items-center gap-2">
                      <span class="font-semibold text-neutral-800">{{ project.name }}</span>
                      <span class="rounded bg-primary/10 px-1.5 py-0.5 text-xs text-primary">{{ project.type }}</span>
                    </div>
                    <p class="mt-1 text-sm text-neutral-500">{{ project.desc }}</p>
                    <p class="mt-2 font-mono text-xs text-neutral-400">{{ project.path }}</p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Local Development -->
            <section id="local-dev" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">本地开发</h2>

              <!-- Start Middleware -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">启动中间件</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-sm text-neutral-300"># Docker Compose 启动中间件
docker compose up -d mysql redis rabbitmq minio

# 或者本地安装启动
brew services start redis
brew services start rabbitmq</pre>
              </div>

              <!-- Initialize Database -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">初始化数据库</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-sm text-neutral-300">mysql -u root -p scripts/init_database.sql</pre>
              </div>

              <!-- Start Backend Services -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">启动后端服务</h3>
              <div class="mt-3 space-y-4">
                <div class="rounded-lg border border-neutral-200 bg-neutral-50 p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="h-2 w-2 rounded-full bg-danger"></span>
                    <span class="text-xs font-medium">Gateway Service (:8080)</span>
                  </div>
                  <code class="font-mono text-xs text-neutral-600">cd PrivateCloudDisk-gateway-service && ./gradlew bootRun</code>
                </div>
                <div class="rounded-lg border border-neutral-200 bg-neutral-50 p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="h-2 w-2 rounded-full bg-success"></span>
                    <span class="text-xs font-medium">Platform Service (:8081)</span>
                  </div>
                  <code class="font-mono text-xs text-neutral-600">cd PrivateCloudDisk-platform-service && ./gradlew bootRun</code>
                </div>
                <div class="rounded-lg border border-neutral-200 bg-neutral-50 p-4">
                  <div class="flex items-center gap-2 mb-2">
                    <span class="h-2 w-2 rounded-full bg-info"></span>
                    <span class="text-xs font-medium">File Service (:8000)</span>
                  </div>
                  <code class="font-mono text-xs text-neutral-600">cd PrivateCloudDisk-storage-service && uvicorn app.main:app --reload</code>
                </div>
              </div>

              <!-- Start Frontend -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">启动前端</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-sm text-neutral-300">cd PrivateCloudDisk-web
npm install
npm run dev</pre>
              </div>
            </section>

            <!-- Coding Standards -->
            <section id="standards" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">编码规范</h2>

              <div class="mt-6 grid grid-cols-1 gap-6">
                <div v-for="standard in codingStandards" :key="standard.lang"
                     class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="flex items-center gap-2 text-base font-semibold text-neutral-800">
                    <i :class="[standard.icon, 'text-lg', standard.iconColor]"></i>
                    {{ standard.lang }}
                  </h3>
                  <ul class="mt-3 space-y-2">
                    <li v-for="rule in standard.rules" :key="rule" class="flex items-start gap-2 text-sm text-neutral-600">
                      <i class="fa fa-check text-success mt-0.5 text-xs"></i>
                      {{ rule }}
                    </li>
                  </ul>
                </div>
              </div>
            </section>

            <!-- Debugging -->
            <section id="debugging" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">调试技巧</h2>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div v-for="tip in debuggingTips" :key="tip.title"
                     class="rounded-xl border border-neutral-200 p-4">
                  <h3 class="text-sm font-semibold text-neutral-800">{{ tip.title }}</h3>
                  <p class="mt-1 text-sm text-neutral-500">{{ tip.desc }}</p>
                  <div v-if="tip.command" class="mt-3 rounded bg-neutral-900 px-3 py-2">
                    <code class="font-mono text-xs text-neutral-300">{{ tip.command }}</code>
                  </div>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/deployment" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                部署指南
              </router-link>
              <router-link to="/docs/api" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                API 参考
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

const activeSection = ref('environment')

const sections = [
  { id: 'environment', title: '环境搭建' },
  { id: 'structure', title: '项目结构' },
  { id: 'local-dev', title: '本地开发' },
  { id: 'standards', title: '编码规范' },
  { id: 'debugging', title: '调试技巧' },
]

const devTools = [
  { name: 'IntelliJ IDEA', icon: 'fa fa-coffee', iconColor: 'text-success', purpose: 'Java 开发 (Platform, Gateway, IM)' },
  { name: 'VS Code', icon: 'fa fa-code', iconColor: 'text-info', purpose: '前端开发 (Web, Admin, Desktop)' },
  { name: 'PyCharm', icon: 'fa fa-python', iconColor: 'text-warning', purpose: 'Python 开发 (File Service)' },
  { name: 'Xcode', icon: 'fa fa-apple', iconColor: 'text-neutral-700', purpose: 'iOS/macOS 开发' },
  { name: 'Android Studio', icon: 'fa fa-android', iconColor: 'text-success', purpose: 'Android 开发' },
  { name: 'DataGrip', icon: 'fa fa-database', iconColor: 'text-primary', purpose: '数据库管理' },
]

const envRequirements = [
  { name: 'JDK', version: '18+', desc: 'Java 开发环境' },
  { name: 'Python', version: '3.11+', desc: '文件服务开发' },
  { name: 'Node.js', version: '18+', desc: '前端开发' },
  { name: 'MySQL', version: '8.0+', desc: '数据库' },
  { name: 'Redis', version: '7.0+', desc: '缓存' },
  { name: 'RabbitMQ', version: '3.10+', desc: '消息队列' },
]

const projectStructure = [
  { name: 'PrivateCloudDisk-web', type: 'Vue 3', icon: 'fa fa-desktop', iconClass: 'text-primary', bgClass: 'bg-primary/10', desc: 'Web 用户端 SPA 应用', path: 'src/main.ts' },
  { name: 'PrivateCloudDisk-admin-web', type: 'React', icon: 'fa fa-tasks', iconClass: 'text-info', bgClass: 'bg-info/10', desc: '管理后台 Web 应用', path: 'src/main.tsx' },
  { name: 'PrivateCloudDisk-gateway-service', type: 'Java', icon: 'fa fa-gateway', iconClass: 'text-orange-500', bgClass: 'bg-orange-100', desc: 'API 网关服务', path: '*Application.java' },
  { name: 'PrivateCloudDisk-platform-service', type: 'Java', icon: 'fa fa-cogs', iconClass: 'text-success', bgClass: 'bg-success/10', desc: '核心业务服务', path: '*Application.java' },
  { name: 'PrivateCloudDisk-storage-service', type: 'Python', icon: 'fa fa-file', iconClass: 'text-warning', bgClass: 'bg-warning/10', desc: '文件存储与处理服务', path: 'app/main.py' },
  { name: 'PrivateCloudDisk-db', type: 'SQL', icon: 'fa fa-database', iconClass: 'text-purple-500', bgClass: 'bg-purple-50', desc: '数据库初始化脚本', path: 'database_init.sql' },
]

const codingStandards = [
  {
    lang: 'Java',
    icon: 'fa fa-coffee',
    iconColor: 'text-warning',
    rules: [
      '遵循仓库现有 Java 编码与测试约定',
      'Controller → Service → Mapper 三层架构',
      '使用 Lombok 简化代码',
      '参数校验使用 Jakarta Validation',
      '异常统一使用 ServiceException',
    ],
  },
  {
    lang: 'Python',
    icon: 'fa fa-python',
    iconColor: 'text-info',
    rules: [
      '遵循 PEP 8 编码规范',
      '使用 Pydantic 进行数据验证',
      '异步 I/O 使用 async/await',
      '类型注解使用 Python 3.10+ 语法',
    ],
  },
  {
    lang: 'TypeScript/Vue',
    icon: 'fa fa-code',
    iconColor: 'text-primary',
    rules: [
      '使用 Composition API (Vue 3)',
      '状态管理使用 Pinia',
      'API 请求封装在 api/ 目录',
      '组件遵循单一职责原则',
    ],
  },
]

const debuggingTips = [
  { title: '后端远程调试', desc: '使用 JDWP 进行远程调试', command: './gradlew bootRun --debug-jvm' },
  { title: 'Vue DevTools', desc: 'Chrome 扩展用于 Vue 调试', command: null },
  { title: 'API 调试', desc: '访问 Swagger UI', command: 'http://localhost:8081/swagger-ui.html' },
  { title: '数据库调试', desc: '查看数据库状态', command: 'docker compose exec mysql mysql -u root -p' },
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
