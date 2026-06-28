<template>
  <div class="deployment-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-info/5 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-info/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">部署指南</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            部署指南
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            从开发环境到生产环境的完整部署方案，支持 Docker Compose、Kubernetes 等多种部署方式
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
            <!-- Requirements -->
            <section id="requirements" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">环境要求</h2>

              <!-- Server Requirements -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">服务器配置</h3>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">环境</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">CPU</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">内存</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">磁盘</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="env in serverEnv" :key="env.name" class="border-b border-neutral-100">
                      <td class="py-3 px-4 font-medium text-neutral-800">{{ env.name }}</td>
                      <td class="py-3 px-4 text-neutral-600">{{ env.cpu }}</td>
                      <td class="py-3 px-4 text-neutral-600">{{ env.memory }}</td>
                      <td class="py-3 px-4 text-neutral-600">{{ env.disk }}</td>
                      <td class="py-3 px-4 text-neutral-500 text-xs">{{ env.note }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <!-- Software Requirements -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">软件依赖</h3>
              <div class="mt-4 grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div v-for="sw in softwareDeps" :key="sw.name"
                     class="flex items-center gap-3 rounded-lg border border-neutral-200 p-3">
                  <i :class="[sw.icon, 'text-lg', sw.iconColor]"></i>
                  <div class="flex-1">
                    <p class="text-sm font-medium text-neutral-800">{{ sw.name }}</p>
                    <p class="text-xs text-neutral-500">{{ sw.version }}</p>
                  </div>
                  <i class="fa fa-check-circle text-success text-sm" v-if="sw.required"></i>
                </div>
              </div>
            </section>

            <!-- Docker Compose -->
            <section id="docker" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">Docker Compose 部署</h2>
              <p class="mt-2 text-neutral-500">适用于开发和测试环境，一键部署所有服务。</p>

              <div class="mt-6 space-y-4">
                <div class="rounded-xl bg-neutral-900 p-4">
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-xs text-neutral-400">Step 1: 克隆项目</span>
                  </div>
                  <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">git clone https://github.com/your-repo/PrivateCloudDisk.git
cd PrivateCloudDisk</pre>
                </div>

                <div class="rounded-xl bg-neutral-900 p-4">
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-xs text-neutral-400">Step 2: 配置环境变量</span>
                  </div>
                  <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">cp .env.example .env
vim .env  # 编辑关键配置</pre>
                </div>

                <div class="rounded-xl bg-neutral-900 p-4">
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-xs text-neutral-400">Step 3: 构建并启动</span>
                  </div>
                  <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">docker compose up -d --build</pre>
                </div>

                <div class="rounded-xl bg-neutral-900 p-4">
                  <div class="flex items-center justify-between mb-2">
                    <span class="text-xs text-neutral-400">Step 4: 查看服务状态</span>
                  </div>
                  <pre class="font-mono text-sm text-neutral-300 overflow-x-auto">docker compose ps</pre>
                </div>
              </div>

              <!-- Port Mapping -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">端口映射</h3>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">服务</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">容器端口</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">宿主机端口</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="port in portMappings" :key="port.service" class="border-b border-neutral-100">
                      <td class="py-2 px-3 text-neutral-800">{{ port.service }}</td>
                      <td class="py-2 px-3"><code class="text-xs text-primary">{{ port.container }}</code></td>
                      <td class="py-2 px-3"><code class="text-xs text-primary">{{ port.host }}</code></td>
                      <td class="py-2 px-3 text-neutral-500 text-xs">{{ port.desc }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Production -->
            <section id="production" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">生产环境部署</h2>
              <p class="mt-2 text-neutral-500">生产环境部署需要考虑高可用、安全加固和性能优化。</p>

              <div class="mt-6 grid grid-cols-1 gap-6">
                <div v-for="tip in productionTips" :key="tip.title"
                     class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="flex items-center gap-2 text-base font-semibold text-neutral-800">
                    <i :class="[tip.icon, 'text-lg', tip.iconColor]"></i>
                    {{ tip.title }}
                  </h3>
                  <ul class="mt-3 space-y-2">
                    <li v-for="item in tip.items" :key="item" class="flex items-start gap-2 text-sm text-neutral-600">
                      <i class="fa fa-check-circle text-success mt-0.5 text-xs"></i>
                      {{ item }}
                    </li>
                  </ul>
                </div>
              </div>

              <!-- Security Checklist -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">安全加固清单</h3>
              <div class="mt-4 space-y-2">
                <div v-for="check in securityChecklist" :key="check"
                     class="flex items-center gap-3 rounded-lg border border-neutral-200 p-3">
                  <i class="fa fa-square-o text-neutral-300"></i>
                  <span class="text-sm text-neutral-700">{{ check }}</span>
                </div>
              </div>
            </section>

            <!-- Middleware Config -->
            <section id="middleware" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">中间件配置</h2>

              <!-- MySQL -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">MySQL 8.0</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">mysql:
  build: ./PrivateCloudDisk-infra/mysql
  environment:
    MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD}
    MYSQL_DATABASE: private_cloud_disk
  volumes:
    - mysql-data:/var/lib/mysql
  command: --default-authentication-plugin=mysql_native_password</pre>
              </div>

              <!-- Redis -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">Redis 7</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">redis:
  build: ./PrivateCloudDisk-infra/redis
  environment:
    REDIS_PASSWORD: ${REDIS_PASSWORD}
  volumes:
    - redis-data:/data</pre>
              </div>

              <!-- RabbitMQ -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">RabbitMQ 3</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
              <pre class="text-sm text-neutral-300 overflow-x-auto"></pre>
            </div>
            </section>

            <!-- Troubleshooting -->
            <section id="troubleshooting" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">常见问题排查</h2>

              <div class="mt-6 space-y-4">
                <div v-for="issue in troubleshooting" :key="issue.problem"
                     class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="text-sm font-semibold text-neutral-800">
                    <i class="fa fa-exclamation-circle text-danger mr-2"></i>
                    {{ issue.problem }}
                  </h3>
                  <p class="mt-2 text-sm text-neutral-600">{{ issue.solution }}</p>
                  <div v-if="issue.command" class="mt-3 rounded bg-neutral-900 px-3 py-2">
                    <code class="font-mono text-xs text-neutral-300">{{ issue.command }}</code>
                  </div>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/architecture" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                架构设计
              </router-link>
              <router-link to="/docs/development" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                开发指南
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

const activeSection = ref('requirements')

const sections = [
  { id: 'requirements', title: '环境要求' },
  { id: 'docker', title: 'Docker部署' },
  { id: 'production', title: '生产环境' },
  { id: 'middleware', title: '中间件配置' },
  { id: 'troubleshooting', title: '问题排查' },
]

const serverEnv = [
  { name: '开发/测试', cpu: '4核', memory: '8 GB', disk: '50 GB SSD', note: '仅基础功能' },
  { name: '生产环境', cpu: '8核+', memory: '16 GB+', disk: '200 GB+ SSD', note: '含 OpenSearch、MinIO' },
  { name: '生产高可用', cpu: '16核+', memory: '32 GB+', disk: '500 GB+ SSD', note: '多副本、高并发' },
]

const softwareDeps = [
  { name: 'Docker', version: '24.0+', icon: 'fa fa-docker', iconColor: 'text-info', required: true },
  { name: 'Docker Compose', version: '2.20+', icon: 'fa fa-compose', iconColor: 'text-primary', required: true },
  { name: 'Git', version: '2.30+', icon: 'fa fa-git', iconColor: 'text-danger', required: true },
  { name: 'make', version: '-', icon: 'fa fa-terminal', iconColor: 'text-neutral-500', required: false },
]

const portMappings = [
  { service: 'Nginx (frontend)', container: '80, 443', host: '80, 443', desc: 'HTTP/HTTPS 入口' },
  { service: 'Gateway Service', container: '8080', host: '8080', desc: '仅开发环境暴露' },
  { service: 'Platform Service', container: '8081', host: '8081', desc: '仅开发环境暴露' },
  { service: 'MinIO API', container: '9000', host: '-', desc: '仅内部网络' },
  { service: 'MinIO Console', container: '9001', host: '9001', desc: '管理面板' },
  { service: 'RabbitMQ', container: '5672, 15672', host: '15672', desc: 'AMQP + 管理面板' },
]

const productionTips = [
  {
    title: '高可用架构',
    icon: 'fa fa-server',
    iconColor: 'text-primary',
    items: ['部署多个 Gateway 实例，使用 Nginx 负载均衡', '部署多个 Platform Service 实例', 'MySQL 使用主从复制', 'Redis 使用哨兵或集群模式'],
  },
  {
    title: '安全加固',
    icon: 'fa fa-shield',
    iconColor: 'text-success',
    items: ['配置 HTTPS SSL 证书', '修改所有默认密码', '配置防火墙仅开放 80/443 端口', '启用 JWT 密钥轮换'],
  },
  {
    title: '性能优化',
    icon: 'fa fa-tachometer',
    iconColor: 'text-warning',
    items: ['配置 Nginx Gzip 压缩', '启用 MySQL 查询缓存', '配置 Redis 持久化', '优化 MinIO 纠删码设置'],
  },
]

const securityChecklist = [
  '修改 MySQL root 密码',
  '配置 Redis 密码认证',
  '配置 MinIO 访问密钥',
  '修改 JWT 签名密钥',
  '配置 SSL/TLS 证书',
  '启用防火墙端口限制',
  '配置日志审计',
  '设置备份策略',
]

const troubleshooting = [
  { problem: 'Docker 容器启动失败', solution: '检查端口占用情况，确保所需端口未被占用。查看容器日志定位具体错误。', command: 'docker compose logs -f [service]' },
  { problem: '数据库连接失败', solution: '确认 MySQL 容器状态正常，检查 .env 中的数据库连接配置是否正确。', command: 'docker compose exec mysql mysql -u root -p' },
  { problem: '前端无法访问 API', solution: '确认后端服务已启动，检查 VITE_API_BASE_URL 环境变量配置。', command: 'curl http://localhost:8080/actuator/health' },
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
