<template>
  <div class="guide-page">
    <!-- ============================================================ -->
    <!-- Hero Section -->
    <!-- ============================================================ -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-info/5 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-info/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <!-- Breadcrumb -->
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">快速入门</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            快速入门指南
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            只需五步，快速部署并运行 PrivateCloudDisk 开发环境
          </p>

          <!-- Meta info -->
          <div class="mt-4 flex flex-wrap items-center gap-4 text-xs text-neutral-500">
            <span class="inline-flex items-center gap-1">
              <i class="fa fa-clock text-neutral-300"></i>
              阅读时间: 约 10 分钟
            </span>
            <span class="inline-flex items-center gap-1">
              <i class="fa fa-refresh text-neutral-300"></i>
              最后更新: 2026-01
            </span>
            <span class="inline-flex items-center gap-1">
              <i class="fa fa-history text-neutral-300"></i>
              v3.0.0+
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Content -->
    <!-- ============================================================ -->
    <section class="py-12 sm:py-16">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 gap-8 lg:grid-cols-4">
          <!-- Sidebar Navigation -->
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
          <div class="lg:col-span-3">
            <!-- Prerequisites -->
            <section id="prerequisites" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">前提条件</h2>
              <p class="mt-2 text-neutral-500">在开始之前，请确保您的开发环境满足以下要求：</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div v-for="req in prerequisites" :key="req.name"
                     class="rounded-xl border border-neutral-100 bg-neutral-50 p-4">
                  <div class="flex items-center gap-2">
                    <i :class="[req.icon, 'text-lg', req.iconColor]"></i>
                    <span class="font-medium text-neutral-800">{{ req.name }}</span>
                    <span class="ml-auto rounded-full bg-primary/10 px-2 py-0.5 text-xs text-primary">{{ req.version }}</span>
                  </div>
                  <p class="mt-2 text-xs text-neutral-500">{{ req.desc }}</p>
                </div>
              </div>

              <!-- System Requirements -->
              <div class="mt-6 rounded-xl border border-neutral-200 bg-warning/5 p-4">
                <div class="flex items-start gap-3">
                  <i class="fa fa-info-circle text-warning mt-0.5"></i>
                  <div>
                    <p class="text-sm font-medium text-neutral-800">系统要求</p>
                    <p class="mt-1 text-xs text-neutral-500">
                      推荐配置: 8核CPU / 16GB内存 / 100GB SSD可用空间。最低配置可运行开发环境，但会影响性能。
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Step 1: Clone -->
            <section id="clone" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <div class="flex items-center gap-3 mb-4">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">1</div>
                <h2 class="text-2xl font-bold text-neutral-900">克隆项目代码</h2>
              </div>

              <p class="text-neutral-500">首先，从 GitHub 克隆 PrivateCloudDisk 项目到本地：</p>

              <div class="mt-4 rounded-xl bg-neutral-900 p-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs text-neutral-400">Bash</span>
                  <button @click="copyCode('git clone https://github.com/your-repo/PrivateCloudDisk.git')"
                          class="text-xs text-neutral-400 hover:text-white transition">
                    <i class="fa fa-copy mr-1"></i>复制
                  </button>
                </div>
                <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">git clone https://github.com/your-repo/PrivateCloudDisk.git
cd PrivateCloudDisk</pre>
              </div>

              <div class="mt-4 rounded-xl border border-info/20 bg-info/5 p-4">
                <div class="flex items-start gap-3">
                  <i class="fa fa-lightbulb-o text-info mt-0.5"></i>
                  <div>
                    <p class="text-sm font-medium text-neutral-800">提示</p>
                    <p class="mt-1 text-xs text-neutral-500">
                      如果您在中国大陆，建议使用 GitHub 镜像站点或配置代理以加快克隆速度。
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Step 2: Configure -->
            <section id="configure" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <div class="flex items-center gap-3 mb-4">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">2</div>
                <h2 class="text-2xl font-bold text-neutral-900">配置环境变量</h2>
              </div>

              <p class="text-neutral-500">复制环境变量模板文件并配置数据库、Redis 等连接信息：</p>

              <div class="mt-4 rounded-xl bg-neutral-900 p-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs text-neutral-400">Bash</span>
                  <button @click="copyCode('cp .env.example .env')"
                          class="text-xs text-neutral-400 hover:text-white transition">
                    <i class="fa fa-copy mr-1"></i>复制
                  </button>
                </div>
                <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">cp .env.example .env</pre>
              </div>

              <p class="mt-6 text-neutral-500">编辑 .env 文件，配置以下关键信息：</p>

              <div class="mt-4 space-y-3">
                <div v-for="env in envConfigs" :key="env.key"
                     class="flex items-start gap-3 rounded-lg border border-neutral-200 bg-neutral-50 p-3">
                  <code class="shrink-0 rounded bg-primary/10 px-2 py-1 text-xs text-primary">{{ env.key }}</code>
                  <span class="text-sm text-neutral-600">{{ env.desc }}</span>
                </div>
              </div>
            </section>

            <!-- Step 3: Start Services -->
            <section id="start-services" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <div class="flex items-center gap-3 mb-4">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">3</div>
                <h2 class="text-2xl font-bold text-neutral-900">启动基础服务</h2>
              </div>

              <p class="text-neutral-500">使用 Docker Compose 启动 MySQL、Redis、RabbitMQ、MinIO 等中间件服务：</p>

              <div class="mt-4 rounded-xl bg-neutral-900 p-4">
                <div class="flex items-center justify-between mb-2">
                  <span class="text-xs text-neutral-400">Bash</span>
                  <button @click="copyCode('docker compose up -d mysql redis rabbitmq minio')"
                          class="text-xs text-neutral-400 hover:text-white transition">
                    <i class="fa fa-copy mr-1"></i>复制
                  </button>
                </div>
                <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">docker compose up -d mysql redis rabbitmq minio</pre>
              </div>

              <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div v-for="service in services" :key="service.name"
                     class="flex items-center gap-3 rounded-lg border border-neutral-200 p-3">
                  <span class="h-2 w-2 rounded-full" :class="service.running ? 'bg-success' : 'bg-neutral-300'"></span>
                  <span class="text-sm text-neutral-700">{{ service.name }}</span>
                  <span class="ml-auto text-xs text-neutral-400">{{ service.port }}</span>
                </div>
              </div>

              <div class="mt-4 rounded-xl border border-success/20 bg-success/5 p-4">
                <div class="flex items-start gap-3">
                  <i class="fa fa-check-circle text-success mt-0.5"></i>
                  <div>
                    <p class="text-sm font-medium text-neutral-800">验证服务状态</p>
                    <p class="mt-1 text-xs text-neutral-500">
                      运行 <code class="rounded bg-success/10 px-1">docker compose ps</code> 确认所有服务状态为 Up。
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Step 4: Start Backend -->
            <section id="start-backend" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <div class="flex items-center gap-3 mb-4">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">4</div>
                <h2 class="text-2xl font-bold text-neutral-900">启动后端服务</h2>
              </div>

              <p class="text-neutral-500">分别启动网关服务、平台服务、文件服务。打开四个终端窗口：</p>

              <!-- Terminal 1 -->
              <div class="mt-4">
                <div class="flex items-center gap-2 mb-2">
                  <span class="h-3 w-3 rounded-full bg-danger"></span>
                  <span class="text-xs font-medium text-neutral-600">终端 1 - 网关服务</span>
                </div>
                <div class="rounded-xl bg-neutral-900 p-4">
                  <pre class="font-mono text-sm text-neutral-300">cd PrivateCloudDisk-gateway-service
./gradlew bootRun</pre>
                </div>
              </div>

              <!-- Terminal 2 -->
              <div class="mt-4">
                <div class="flex items-center gap-2 mb-2">
                  <span class="h-3 w-3 rounded-full bg-success"></span>
                  <span class="text-xs font-medium text-neutral-600">终端 2 - 平台服务</span>
                </div>
                <div class="rounded-xl bg-neutral-900 p-4">
                  <pre class="font-mono text-sm text-neutral-300">cd PrivateCloudDisk-platform-service
./gradlew bootRun</pre>
                </div>
              </div>

              <!-- Terminal 3 -->
              <div class="mt-4">
                <div class="flex items-center gap-2 mb-2">
                  <span class="h-3 w-3 rounded-full bg-info"></span>
                  <span class="text-xs font-medium text-neutral-600">终端 3 - 文件服务</span>
                </div>
                <div class="rounded-xl bg-neutral-900 p-4">
                  <pre class="font-mono text-sm text-neutral-300">cd PrivateCloudDisk-shortage-service
pip install -r requirements.txt
uvicorn server:app --host 0.0.0.0 --port 8000 --reload</pre>
                </div>
              </div>

              <!-- Terminal 4 -->
              <div class="mt-4">
                <div class="flex items-center gap-2 mb-2">
                  <span class="h-3 w-3 rounded-full bg-warning"></span>
                  <span class="text-xs font-medium text-neutral-600">终端 4 - 前端 (可选)</span>
                </div>
                <div class="rounded-xl bg-neutral-900 p-4">
                  <pre class="font-mono text-sm text-neutral-300">cd PrivateCloudDisk-web
npm install
npm run dev</pre>
                </div>
              </div>
            </section>

            <!-- Step 5: Access -->
            <section id="access" class="mb-12 rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <div class="flex items-center gap-3 mb-4">
                <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">5</div>
                <h2 class="text-2xl font-bold text-neutral-900">访问并体验</h2>
              </div>

              <p class="text-neutral-500">打开浏览器访问 Web 控制台，使用测试账号登录体验：</p>

              <div class="mt-4 rounded-xl border border-neutral-200 bg-neutral-50 p-6">
                <div class="text-center">
                  <div class="inline-flex items-center gap-2 rounded-full bg-success/10 px-4 py-2">
                    <i class="fa fa-check-circle text-success"></i>
                    <span class="text-sm font-medium text-success">服务已启动</span>
                  </div>
                  <p class="mt-4 text-lg text-neutral-700">
                    访问地址: <a href="http://localhost" class="text-primary hover:underline">http://localhost</a>
                  </p>
                </div>
              </div>

              <div class="mt-6">
                <h3 class="text-base font-semibold text-neutral-800 mb-3">测试账号</h3>
                <div class="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  <div class="flex items-center justify-between rounded-lg border border-neutral-200 bg-white p-3">
                    <div>
                      <p class="text-sm font-medium text-neutral-800">超级管理员</p>
                      <p class="text-xs text-neutral-500">系统全部权限</p>
                    </div>
                    <code class="rounded bg-primary/10 px-2 py-1 text-xs text-primary">superadmin / admin123</code>
                  </div>
                  <div class="flex items-center justify-between rounded-lg border border-neutral-200 bg-white p-3">
                    <div>
                      <p class="text-sm font-medium text-neutral-800">普通用户</p>
                      <p class="text-xs text-neutral-500">测试用账号</p>
                    </div>
                    <code class="rounded bg-primary/10 px-2 py-1 text-xs text-primary">pcd_test001 / test123456</code>
                  </div>
                </div>
              </div>
            </section>

            <!-- Next Steps -->
            <section class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">后续步骤</h2>
              <p class="mt-2 text-neutral-500">您已经成功运行了 PrivateCloudDisk，接下来可以：</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <router-link v-for="next in nextSteps" :key="next.title" 
                             :to="next.path"
                             class="group rounded-xl border border-neutral-200 p-4 transition hover:-translate-y-1 hover:border-primary/20 hover:shadow-lg">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg" :class="next.bgClass">
                    <i :class="[next.icon, 'text-lg', next.iconClass]"></i>
                  </div>
                  <h3 class="mt-3 text-sm font-semibold text-neutral-800 group-hover:text-primary">{{ next.title }}</h3>
                  <p class="mt-1 text-xs text-neutral-500">{{ next.desc }}</p>
                </router-link>
              </div>
            </section>
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'

const activeSection = ref('prerequisites')

const sections = [
  { id: 'prerequisites', title: '前提条件' },
  { id: 'clone', title: '克隆项目' },
  { id: 'configure', title: '配置环境' },
  { id: 'start-services', title: '启动服务' },
  { id: 'start-backend', title: '启动后端' },
  { id: 'access', title: '访问体验' },
]

const prerequisites = [
  { name: 'Docker', version: '24.0+', icon: 'fa fa-docker', iconColor: 'text-info', desc: '容器化部署中间件服务' },
  { name: 'Docker Compose', version: '2.20+', icon: 'fa fa-compose', iconColor: 'text-primary', desc: '多容器编排工具' },
  { name: 'JDK', version: '18+', icon: 'fa fa-coffee', iconColor: 'text-warning', desc: 'Java后端服务运行环境' },
  { name: 'Python', version: '3.11+', icon: 'fa fa-code', iconColor: 'text-success', desc: '文件服务运行环境' },
  { name: 'Node.js', version: '18+', icon: 'fa fa-node-js', iconColor: 'text-success', desc: '前端开发环境' },
  { name: 'Git', version: '2.30+', icon: 'fa fa-git', iconColor: 'text-danger', desc: '代码版本管理工具' },
]

const envConfigs = [
  { key: 'MYSQL_ROOT_PASSWORD', desc: 'MySQL root 密码' },
  { key: 'REDIS_PASSWORD', desc: 'Redis 密码（可选）' },
  { key: 'MINIO_ROOT_PASSWORD', desc: 'MinIO 管理密码' },
  { key: 'OPENSEARCH_PASSWORD', desc: 'OpenSearch 密码' },
  { key: 'JWT_PRIVATE_KEY', desc: 'JWT 私钥（生产环境必须更换）' },
  { key: 'VITE_API_BASE_URL', desc: '前端 API 地址' },
]

const services = [
  { name: 'MySQL', port: ':3306', running: true },
  { name: 'Redis', port: ':6379', running: true },
  { name: 'RabbitMQ', port: ':5672', running: true },
  { name: 'MinIO', port: ':9000', running: true },
  { name: 'OpenSearch', port: ':9200', running: false },
]

const nextSteps = [
  { title: '架构设计', desc: '了解系统整体架构', path: '/docs/architecture', icon: 'fa fa-puzzle-piece', iconClass: 'text-purple-500', bgClass: 'bg-purple-50' },
  { title: '部署指南', desc: '生产环境部署', path: '/docs/deployment', icon: 'fa fa-cloud-upload', iconClass: 'text-info', bgClass: 'bg-info/10' },
  { title: '开发指南', desc: '本地开发调试', path: '/docs/development', icon: 'fa fa-code', iconClass: 'text-warning', bgClass: 'bg-warning/10' },
  { title: 'API 参考', desc: '接口文档', path: '/docs/api', icon: 'fa fa-plug', iconClass: 'text-success', bgClass: 'bg-success/10' },
  { title: '安全文档', desc: '安全机制', path: '/docs/security', icon: 'fa fa-shield', iconClass: 'text-danger', bgClass: 'bg-danger/10' },
  { title: '数据库设计', desc: '表结构设计', path: '/docs/database', icon: 'fa fa-database', iconClass: 'text-primary', bgClass: 'bg-primary/10' },
]

const copyCode = (code: string) => {
  navigator.clipboard.writeText(code)
}

onMounted(() => {
  const handleScroll = () => {
    const scrollY = window.scrollY
    const sectionElements = sections.map(s => document.getElementById(s.id))
    
    for (let i = sectionElements.length - 1; i >= 0; i--) {
      const el = sectionElements[i]
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
