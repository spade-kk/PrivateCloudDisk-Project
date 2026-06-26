<template>
  <div class="customize-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-neutral-50 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-neutral-500/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">自定义扩展</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            自定义扩展指南
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            二次开发、插件机制、主题定制和升级指南，助您在 PrivateCloudDisk 基础上构建个性化解决方案
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
              <h2 class="text-2xl font-bold text-neutral-900">扩展能力概览</h2>
              <p class="mt-2 text-neutral-500">PrivateCloudDisk 提供多种扩展方式，满足不同层次的定制需求。</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="ext in extensionTypes" :key="ext.title"
                     class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg" :class="ext.bgClass">
                    <i :class="[ext.icon, 'text-lg', ext.iconClass]"></i>
                  </div>
                  <h3 class="mt-3 text-sm font-semibold text-neutral-800">{{ ext.title }}</h3>
                  <p class="mt-1 text-xs text-neutral-500">{{ ext.desc }}</p>
                  <p class="mt-2 text-xs text-neutral-400">难度: {{ ext.difficulty }}</p>
                </div>
              </div>
            </section>

            <!-- Theme Customization -->
            <section id="theme" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">主题定制</h2>
              <p class="mt-2 text-neutral-500">通过 CSS 变量和配置文件轻松定制品牌主题。</p>

              <!-- CSS Variables -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">CSS 变量</h3>
              <p class="mt-2 text-sm text-neutral-600">修改 src/assets/base.css 中的 CSS 变量：</p>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">:root {
  --color-primary: #3B82F6;      /* 主色调 */
  --color-success: #10B981;      /* 成功色 */
  --color-warning: #F59E0B;      /* 警告色 */
  --color-danger: #EF4444;        /* 危险色 */
  --color-info: #06B6D4;          /* 信息色 */
  
  --border-radius: 0.75rem;       /* 圆角 */
  --shadow-sm: 0 1px 2px;         /* 阴影 */
  --shadow-lg: 0 10px 15px;       /* 大阴影 */
}</pre>
              </div>

              <!-- Logo -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">Logo 定制</h3>
              <div class="mt-3 space-y-2 text-sm text-neutral-600">
                <p>替换以下文件以更改 Logo：</p>
                <div class="rounded bg-neutral-50 p-3 space-y-1">
                  <p><code class="text-xs text-primary">public/logo.svg</code> - 主 Logo</p>
                  <p><code class="text-xs text-primary">public/favicon.ico</code> - 浏览器图标</p>
                  <p><code class="text-xs text-primary">public/favicon.svg</code> - SVG 图标</p>
                </div>
              </div>
            </section>

            <!-- Adding New Features -->
            <section id="features" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">新增功能模块</h2>
              <p class="mt-2 text-neutral-500">以文件评论功能为例，演示如何在现有系统中添加新功能。</p>

              <div class="mt-6 space-y-6">
                <!-- Step 1 -->
                <div class="rounded-xl border border-neutral-200 p-5">
                  <div class="flex items-center gap-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">1</div>
                    <h3 class="text-base font-semibold text-neutral-800">数据库设计</h3>
                  </div>
                  <div class="mt-4 rounded-xl bg-neutral-900 p-4">
                    <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">CREATE TABLE pcd_file_comment_table (
    comment_id     BINARY(16) NOT NULL PRIMARY KEY,
    file_id        BINARY(16) NOT NULL,
    user_id        BINARY(16) NOT NULL,
    content        TEXT NOT NULL,
    created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_file_id (file_id),
    INDEX idx_user_id (user_id)
);</pre>
                  </div>
                </div>

                <!-- Step 2 -->
                <div class="rounded-xl border border-neutral-200 p-5">
                  <div class="flex items-center gap-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">2</div>
                    <h3 class="text-base font-semibold text-neutral-800">后端 API 开发</h3>
                  </div>
                  <div class="mt-4 space-y-3 text-sm text-neutral-600">
                    <p>在 Platform Service 中添加：</p>
                    <div class="space-y-1">
                      <p><code class="text-xs text-primary">CommentMapper.java</code> - 数据访问层</p>
                      <p><code class="text-xs text-primary">CommentService.java</code> - 业务逻辑层</p>
                      <p><code class="text-xs text-primary">CommentController.java</code> - REST 接口</p>
                    </div>
                  </div>
                </div>

                <!-- Step 3 -->
                <div class="rounded-xl border border-neutral-200 p-5">
                  <div class="flex items-center gap-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-full bg-primary text-sm font-bold text-white">3</div>
                    <h3 class="text-base font-semibold text-neutral-800">前端组件开发</h3>
                  </div>
                  <div class="mt-4 space-y-3 text-sm text-neutral-600">
                    <p>在 Web 前端中添加：</p>
                    <div class="space-y-1">
                      <p><code class="text-xs text-primary">src/api/modules/comment.ts</code> - API 接口封装</p>
                      <p><code class="text-xs text-primary">src/stores/commentStore.ts</code> - Pinia 状态管理</p>
                      <p><code class="text-xs text-primary">src/components/comment/*.vue</code> - 评论组件</p>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- API Integration -->
            <section id="api-integration" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">第三方系统集成</h2>
              <p class="mt-2 text-neutral-500">通过 Webhook 和内部 API 实现与第三方系统的集成。</p>

              <!-- Webhook -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">Webhook 配置</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto"># .env 配置
WEBHOOK_URL=https://your-system.com/webhook
WEBHOOK_SECRET=your-secret-key
WEBHOOK_EVENTS=file.uploaded,file.deleted,user.registered</pre>
              </div>

              <!-- Internal API -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">内部 API 调用</h3>
              <p class="mt-2 text-sm text-neutral-600">服务间内部调用需携带 X-Internal 头：</p>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">curl -X POST http://platform-service:8081/api/v1/internal/files \
  -H "X-Internal: internal-secret-key" \
  -H "Content-Type: application/json" \
  -d '{"action": "create", "file_name": "test.pdf"}'</pre>
              </div>
            </section>

            <!-- Upgrade Guide -->
            <section id="upgrade" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">版本升级指南</h2>
              <p class="mt-2 text-neutral-500">从旧版本升级时需要注意的兼容性和迁移事项。</p>

              <div class="mt-6 space-y-4">
                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <span class="h-2 w-2 rounded-full bg-success"></span>
                    <span class="font-medium text-neutral-800">备份数据</span>
                  </div>
                  <p class="mt-2 text-sm text-neutral-500">升级前务必备份数据库和所有配置文件。</p>
                  <div class="mt-2 rounded bg-neutral-900 px-2 py-1">
                    <code class="font-mono text-xs text-neutral-300">mysqldump -u root -p private_cloud_disk > backup.sql</code>
                  </div>
                </div>

                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <span class="h-2 w-2 rounded-full bg-primary"></span>
                    <span class="font-medium text-neutral-800">查看变更日志</span>
                  </div>
                  <p class="mt-2 text-sm text-neutral-500">升级前请仔细阅读 CHANGELOG.md，了解Breaking Changes。</p>
                </div>

                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <span class="h-2 w-2 rounded-full bg-warning"></span>
                    <span class="font-medium text-neutral-800">测试环境验证</span>
                  </div>
                  <p class="mt-2 text-sm text-neutral-500">在测试环境完成升级验证后再升级生产环境。</p>
                </div>

                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <span class="h-2 w-2 rounded-full bg-info"></span>
                    <span class="font-medium text-neutral-800">执行升级</span>
                  </div>
                  <div class="mt-2 rounded bg-neutral-900 px-2 py-1">
                    <code class="font-mono text-xs text-neutral-300">git pull origin main
docker compose down
docker compose up -d --build</code>
                  </div>
                </div>
              </div>
            </section>

            <!-- Best Practices -->
            <section id="best-practices" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">最佳实践</h2>

              <div class="mt-6 space-y-4">
                <div v-for="practice in bestPractices" :key="practice.title"
                     class="rounded-xl border border-neutral-200 p-4">
                  <h3 class="flex items-center gap-2 text-sm font-semibold text-neutral-800">
                    <i :class="[practice.icon, 'text-primary']"></i>
                    {{ practice.title }}
                  </h3>
                  <p class="mt-2 text-sm text-neutral-600">{{ practice.desc }}</p>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/database" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                数据库设计
              </router-link>
              <router-link to="/docs" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                返回文档中心
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
  { id: 'overview', title: '扩展能力' },
  { id: 'theme', title: '主题定制' },
  { id: 'features', title: '新增功能' },
  { id: 'api-integration', title: '系统集成' },
  { id: 'upgrade', title: '版本升级' },
  { id: 'best-practices', title: '最佳实践' },
]

const extensionTypes = [
  { title: '主题定制', desc: '修改 CSS 变量和 Logo', icon: 'fa fa-palette', iconClass: 'text-purple-500', bgClass: 'bg-purple-50', difficulty: '简单' },
  { title: '新功能开发', desc: '添加评论、标签等功能', icon: 'fa fa-plus-circle', iconClass: 'text-success', bgClass: 'bg-success/10', difficulty: '中等' },
  { title: '插件开发', desc: '独立插件模块开发', icon: 'fa fa-puzzle-piece', iconClass: 'text-info', bgClass: 'bg-info/10', difficulty: '复杂' },
  { title: '系统集成', desc: '对接第三方系统', icon: 'fa fa-link', iconClass: 'text-primary', bgClass: 'bg-primary/10', difficulty: '中等' },
  { title: '性能优化', desc: '缓存、索引、CDN 等', icon: 'fa fa-tachometer', iconClass: 'text-warning', bgClass: 'bg-warning/10', difficulty: '中等' },
  { title: '安全加固', desc: 'SSL、日志审计等', icon: 'fa fa-shield', iconClass: 'text-danger', bgClass: 'bg-danger/10', difficulty: '简单' },
]

const bestPractices = [
  { title: '模块化开发', icon: 'fa fa-cubes', desc: '保持功能模块独立，便于维护和升级。遵循单一职责原则，每个模块只负责一件事。' },
  { title: '配置外置', icon: 'fa fa-sliders', desc: '将可配置项放到 .env 或配置中心，避免硬编码，便于不同环境使用不同配置。' },
  { title: '日志规范', icon: 'fa fa-file-text', desc: '使用统一的日志格式，记录关键操作和异常信息，便于问题排查和审计。' },
  { title: '错误处理', icon: 'fa fa-exclamation-triangle', desc: '对异常情况进行处理，提供友好的错误提示，不要暴露内部实现细节。' },
  { title: '测试覆盖', icon: 'fa fa-check-square', desc: '为新增功能编写单元测试和集成测试，确保代码质量和稳定性。' },
  { title: '版本控制', icon: 'fa fa-code-branch', desc: '使用 Git 进行版本控制，编写清晰的 commit message，便于追溯和回滚。' },
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
