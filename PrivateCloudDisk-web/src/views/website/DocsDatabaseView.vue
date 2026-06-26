<template>
  <div class="database-page">
    <!-- Hero Section -->
    <section class="relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-purple-50/50 py-16 sm:py-20">
      <div class="absolute inset-0 pointer-events-none">
        <div class="absolute -top-40 -right-40 h-96 w-96 rounded-full bg-purple-500/10 blur-3xl"></div>
        <div class="absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="mx-auto max-w-3xl">
          <div class="flex items-center gap-2 text-xs text-neutral-400 mb-4">
            <router-link to="/docs" class="hover:text-primary transition">文档中心</router-link>
            <i class="fa fa-angle-right text-[10px]"></i>
            <span class="text-neutral-600">数据库设计</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            数据库设计文档
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            表结构设计、闭包表实现、索引策略和 UUID 主键方案的完整参考
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
              <h2 class="text-2xl font-bold text-neutral-900">数据库概览</h2>

              <div class="mt-6 grid grid-cols-2 gap-4 sm:grid-cols-4">
                <div class="rounded-xl border border-neutral-200 p-4 text-center">
                  <p class="text-2xl font-bold text-primary">19+</p>
                  <p class="mt-1 text-xs text-neutral-500">数据表</p>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4 text-center">
                  <p class="text-2xl font-bold text-success">InnoDB</p>
                  <p class="mt-1 text-xs text-neutral-500">存储引擎</p>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4 text-center">
                  <p class="text-2xl font-bold text-info">UTF8MB4</p>
                  <p class="mt-1 text-xs text-neutral-500">字符集</p>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4 text-center">
                  <p class="text-2xl font-bold text-warning">BINARY(16)</p>
                  <p class="mt-1 text-xs text-neutral-500">主键策略</p>
                </div>
              </div>

              <!-- Table Categories -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">表分类</h3>
              <div class="mt-4 space-y-3">
                <div v-for="cat in tableCategories" :key="cat.name"
                     class="rounded-lg border border-neutral-200 p-4">
                  <div class="flex items-center gap-2">
                    <span class="h-2 w-2 rounded-full" :class="cat.color"></span>
                    <span class="font-medium text-neutral-800">{{ cat.name }} ({{ cat.count }}张)</span>
                  </div>
                  <div class="mt-2 flex flex-wrap gap-2">
                    <code v-for="table in cat.tables" :key="table"
                          class="rounded bg-neutral-100 px-1.5 py-0.5 text-xs text-neutral-600">
                      {{ table }}
                    </code>
                  </div>
                </div>
              </div>
            </section>

            <!-- Closure Table -->
            <section id="closure" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">闭包表设计</h2>
              <p class="mt-2 text-neutral-500">基于闭包表的目录树结构，支持 O(1) 复杂度的子树查询。</p>

              <!-- Design Principle -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">设计原理</h3>
              <p class="mt-2 text-sm text-neutral-600">
                闭包表通过 (ancestor_id, descendant_id, depth) 三元组表示节点间的所有祖先-后代关系。
              </p>

              <!-- Table Structure -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">表结构</h3>
              <div class="mt-3 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">CREATE TABLE pcd_directory_closure_table (
    user_id       BINARY(16) NOT NULL,
    ancestor_id   BINARY(16) NOT NULL,
    descendant_id BINARY(16) NOT NULL,
    depth         INT NOT NULL,
    PRIMARY KEY (ancestor_id, descendant_id),
    INDEX idx_depth (depth)
);</pre>
              </div>

              <!-- Example -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">操作示例</h3>
              <div class="mt-4 space-y-4">
                <div class="rounded-lg border border-neutral-200 p-4">
                  <p class="text-sm font-medium text-neutral-700">根节点自引用 (depth=0)</p>
                  <code class="mt-2 block font-mono text-xs text-neutral-500">(root, root, 0)</code>
                </div>
                <div class="rounded-lg border border-neutral-200 p-4">
                  <p class="text-sm font-medium text-neutral-700">插入子节点 A 到根目录</p>
                  <code class="mt-2 block font-mono text-xs text-neutral-500">(root, A, 1) -- 根 → A<br/>(A, A, 0) -- A 自引用</code>
                </div>
                <div class="rounded-lg border border-neutral-200 p-4">
                  <p class="text-sm font-medium text-neutral-700">查询某目录下所有子节点</p>
                  <code class="mt-2 block font-mono text-xs text-neutral-500">SELECT descendant_id FROM closure_table<br/>WHERE ancestor_id = ? AND depth > 0;</code>
                </div>
                <div class="rounded-lg border border-neutral-200 p-4">
                  <p class="text-sm font-medium text-neutral-700">查询某节点的所有祖先</p>
                  <code class="mt-2 block font-mono text-xs text-neutral-500">SELECT ancestor_id FROM closure_table<br/>WHERE descendant_id = ? AND depth > 0<br/>ORDER BY depth DESC;</code>
                </div>
              </div>

              <!-- Advantages -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">优势</h3>
              <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
                <div class="flex items-center gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success"></i>
                  查询子树 O(1) 复杂度
                </div>
                <div class="flex items-center gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success"></i>
                  支持任意深度层级
                </div>
                <div class="flex items-center gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success"></i>
                  无需递归查询
                </div>
                <div class="flex items-center gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success"></i>
                  写入时自动维护闭包关系
                </div>
              </div>
            </section>

            <!-- UUID Strategy -->
            <section id="uuid" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">UUID 主键策略</h2>
              <p class="mt-2 text-neutral-500">全局使用 BINARY(16) 存储 UUID，不可遍历，防止 ID 枚举攻击。</p>

              <div class="mt-6 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">-- 插入时
INSERT INTO pcd_user_info_table (user_id, ...)
VALUES (UNHEX(REPLACE('a1b2c3d4-e5f6-7890-abcd-ef1234567890', '-', '')), ...);

-- 查询时
SELECT HEX(user_id) AS user_id, ... FROM pcd_user_info_table;</pre>
              </div>

              <h3 class="mt-6 text-base font-semibold text-neutral-800">优势</h3>
              <ul class="mt-3 space-y-2">
                <li class="flex items-start gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success mt-0.5"></i>
                  不可遍历，防止 ID 枚举攻击
                </li>
                <li class="flex items-start gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success mt-0.5"></i>
                  分布式环境无需协调 ID 生成
                </li>
                <li class="flex items-start gap-2 text-sm text-neutral-600">
                  <i class="fa fa-check-circle text-success mt-0.5"></i>
                  应用层生成，减少数据库压力
                </li>
              </ul>
            </section>

            <!-- Index Strategy -->
            <section id="indexes" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">索引策略</h2>

              <div class="mt-6 space-y-4">
                <div v-for="index in indexStrategies" :key="index.table"
                     class="rounded-xl border border-neutral-200 p-4">
                  <h3 class="text-sm font-semibold text-neutral-800">{{ index.table }}</h3>
                  <div class="mt-3 space-y-2">
                    <div v-for="idx in index.indexes" :key="idx.name" class="flex items-center gap-2 text-xs">
                      <span class="rounded bg-primary/10 px-1.5 py-0.5 text-primary">{{ idx.type }}</span>
                      <span class="text-neutral-700">{{ idx.name }}</span>
                      <span class="text-neutral-400">{{ idx.fields }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- Optimistic Locking -->
            <section id="optimistic-lock" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">乐观锁设计</h2>
              <p class="mt-2 text-neutral-500">配额表使用版本号实现乐观锁，防止并发更新冲突。</p>

              <div class="mt-6 rounded-xl bg-neutral-900 p-4">
                <pre class="font-mono text-xs text-neutral-300 overflow-x-auto">UPDATE pcd_user_quota_table
SET quota_used_capacity = quota_used_capacity + ?,
    quota_version = quota_version + 1
WHERE quota_user_id = ? AND quota_version = ?;</pre>
              </div>

              <div class="mt-4 rounded-lg border border-info/20 bg-info/5 p-4">
                <div class="flex items-start gap-3">
                  <i class="fa fa-info-circle text-info mt-0.5"></i>
                  <div>
                    <p class="text-sm font-medium text-neutral-800">注意</p>
                    <p class="mt-1 text-xs text-neutral-500">
                      如 affected_rows = 0，说明版本号已变更，需重试更新操作。
                    </p>
                  </div>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/security" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                安全文档
              </router-link>
              <router-link to="/docs/customize" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                自定义扩展
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
  { id: 'overview', title: '数据库概览' },
  { id: 'closure', title: '闭包表设计' },
  { id: 'uuid', title: 'UUID主键' },
  { id: 'indexes', title: '索引策略' },
  { id: 'optimistic-lock', title: '乐观锁' },
]

const tableCategories = [
  { name: '用户与认证', count: 5, color: 'bg-blue-500', tables: ['pcd_user_info_table', 'pcd_user_device_table', 'pcd_login_session_table', 'pcd_login_audit_table', 'pcd_admin_user_table'] },
  { name: '目录树与文件', count: 3, color: 'bg-green-500', tables: ['pcd_directory_tree_table', 'pcd_directory_closure_table', 'pcd_file_info_table'] },
  { name: '上传管理', count: 2, color: 'bg-warning', tables: ['pcd_uploads_session_table', 'pcd_upload_chunks_table'] },
  { name: '回收站与收藏', count: 2, color: 'bg-neutral-500', tables: ['pcd_trash_target_table', 'pcd_file_star_table'] },
  { name: '配额与分享', count: 3, color: 'bg-purple-500', tables: ['pcd_user_quota_table', 'pcd_user_quota_log_table', 'pcd_sharing_link_table'] },
  { name: '安全与管理', count: 4, color: 'bg-danger', tables: ['pcd_admin_audit_log_table', 'pcd_security_event_table', 'pcd_ip_blacklist_table', 'pcd_system_config_table'] },
]

const indexStrategies = [
  {
    table: '用户表 (pcd_user_info_table)',
    indexes: [
      { type: 'UNIQUE', name: 'user_account', fields: '(user_account)' },
      { type: 'UNIQUE', name: 'user_phone_number', fields: '(user_phone_number)' },
      { type: 'UNIQUE', name: 'user_email', fields: '(user_email)' },
    ],
  },
  {
    table: '文件表 (pcd_file_info_table)',
    indexes: [
      { type: 'UNIQUE', name: 'uk_file_info', fields: '(file_id, file_author_id, file_node_id)' },
      { type: 'INDEX', name: 'idx_file_node_status', fields: '(file_node_id, file_status)' },
      { type: 'INDEX', name: 'idx_file_author', fields: '(file_author_id)' },
    ],
  },
  {
    table: '回收站表 (pcd_trash_target_table)',
    indexes: [
      { type: 'INDEX', name: 'idx_user_deleted', fields: '(trash_user_id, trash_deleted_at)' },
      { type: 'INDEX', name: 'idx_expires', fields: '(trash_expires_at)' },
    ],
  },
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
