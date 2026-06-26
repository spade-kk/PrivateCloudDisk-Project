<template>
  <div class="architecture-page">
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
            <span class="text-neutral-600">架构设计</span>
          </div>

          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900">
            系统架构设计
          </h1>
          <p class="mt-4 text-lg text-neutral-500">
            深入了解 PrivateCloudDisk 的微服务架构设计、技术选型、高可用方案及多客户端实现
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
            <!-- Architecture Overview -->
            <section id="overview" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">架构概览</h2>
              <p class="mt-2 text-neutral-500">
                PrivateCloudDisk 采用 <strong>微服务 + 前后端分离</strong> 架构，支持多客户端接入（Web、桌面、移动端、CLI），后端通过 API 网关统一鉴权和路由分发。
              </p>

              <!-- Complete Architecture Diagram -->
              <div class="mt-6 rounded-xl border border-neutral-200 bg-neutral-50 p-4 overflow-x-auto">
                <div class="space-y-4 min-w-[800px]">
                  <!-- Client Layer -->
                  <div class="rounded-lg border-2 border-blue-300 bg-blue-50/50 p-3">
                    <div class="flex items-center gap-2 mb-2">
                      <span class="h-3 w-3 rounded-full bg-blue-500"></span>
                      <span class="text-xs font-bold text-blue-700">客户端层 (Multi-Client Layer)</span>
                    </div>
                    <div class="flex flex-wrap gap-2">
                      <span v-for="client in clients" :key="client.name" 
                            class="rounded border border-blue-200 bg-white px-2 py-1 text-xs" :class="client.class">
                        {{ client.name }}
                      </span>
                    </div>
                  </div>

                  <!-- Arrow -->
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-300"></i>
                  </div>

                  <!-- Gateway Layer -->
                  <div class="rounded-lg border-2 border-orange-300 bg-orange-50/50 p-3">
                    <div class="flex items-center gap-2 mb-2">
                      <span class="h-3 w-3 rounded-full bg-orange-500"></span>
                      <span class="text-xs font-bold text-orange-700">网关层 (Gateway Layer)</span>
                    </div>
                    <div class="flex flex-wrap gap-2">
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">Nginx 反向代理</span>
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">TLS 终止</span>
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">Gzip 压缩</span>
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">Spring Cloud Gateway</span>
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">JWT 鉴权</span>
                      <span class="rounded border border-orange-200 bg-white px-2 py-1 text-xs text-orange-600">Sentinel 限流</span>
                    </div>
                  </div>

                  <!-- Arrow -->
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-300"></i>
                  </div>

                  <!-- Service Layer Grid -->
                  <div class="grid grid-cols-2 gap-4">
                    <div class="rounded-lg border-2 border-green-300 bg-green-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-green-500"></span>
                        <span class="text-xs font-bold text-green-700">Platform Service :8081</span>
                      </div>
                      <div class="space-y-1 text-xs text-green-600">
                        <p>用户管理 / 文件管理 / 目录树</p>
                        <p>配额管理 / 收藏 / 回收站</p>
                        <p>分享链接 / 订阅管理</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-purple-300 bg-purple-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-purple-500"></span>
                        <span class="text-xs font-bold text-purple-700">File Service :8000</span>
                      </div>
                      <div class="space-y-1 text-xs text-purple-600">
                        <p>分片上传 / 流式下载</p>
                        <p>缩略图生成 / 操作凭证</p>
                        <p>OpenSearch 索引</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-pink-300 bg-pink-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-pink-500"></span>
                        <span class="text-xs font-bold text-pink-700">Notification Service</span>
                      </div>
                      <div class="space-y-1 text-xs text-pink-600">
                        <p>邮件通知 / 短信通知</p>
                        <p>系统消息 / 站内信</p>
                        <p>推送路由</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-cyan-300 bg-cyan-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-cyan-500"></span>
                        <span class="text-xs font-bold text-cyan-700">Subscription Service</span>
                      </div>
                      <div class="space-y-1 text-xs text-cyan-600">
                        <p>订阅计划 / 配额分配</p>
                        <p>用量统计 / 配额预警</p>
                        <p>计费周期</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-yellow-300 bg-yellow-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-yellow-500"></span>
                        <span class="text-xs font-bold text-yellow-700">IM Platform</span>
                      </div>
                      <div class="space-y-1 text-xs text-yellow-600">
                        <p>消息 / 会话 / 群组</p>
                        <p>文件消息 / 离线推送</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-indigo-300 bg-indigo-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-indigo-500"></span>
                        <span class="text-xs font-bold text-indigo-700">IM Server</span>
                      </div>
                      <div class="space-y-1 text-xs text-indigo-600">
                        <p>Netty WebSocket</p>
                        <p>WebRTC 音视频</p>
                        <p>多端同步</p>
                      </div>
                    </div>
                  </div>

                  <!-- Arrow -->
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-300"></i>
                  </div>

                  <!-- Middleware Layer -->
                  <div class="rounded-lg border-2 border-red-300 bg-red-50/50 p-3">
                    <div class="flex items-center gap-2 mb-2">
                      <span class="h-3 w-3 rounded-full bg-red-500"></span>
                      <span class="text-xs font-bold text-red-700">数据层 (Data Layer)</span>
                    </div>
                    <div class="grid grid-cols-5 gap-2">
                      <div class="rounded border border-red-200 bg-white px-2 py-1 text-center text-xs text-red-600">
                        <div class="font-medium">MySQL 8.0</div>
                        <div class="text-[10px] text-red-400">业务数据</div>
                      </div>
                      <div class="rounded border border-red-200 bg-white px-2 py-1 text-center text-xs text-red-600">
                        <div class="font-medium">Redis 7</div>
                        <div class="text-[10px] text-red-400">缓存/会话</div>
                      </div>
                      <div class="rounded border border-red-200 bg-white px-2 py-1 text-center text-xs text-red-600">
                        <div class="font-medium">RabbitMQ</div>
                        <div class="text-[10px] text-red-400">消息队列</div>
                      </div>
                      <div class="rounded border border-red-200 bg-white px-2 py-1 text-center text-xs text-red-600">
                        <div class="font-medium">MinIO</div>
                        <div class="text-[10px] text-red-400">对象存储</div>
                      </div>
                      <div class="rounded border border-red-200 bg-white px-2 py-1 text-center text-xs text-red-600">
                        <div class="font-medium">OpenSearch</div>
                        <div class="text-[10px] text-red-400">全文检索</div>
                      </div>
                    </div>
                  </div>

                  <!-- Arrow -->
                  <div class="flex justify-center">
                    <i class="fa fa-arrow-down text-neutral-300"></i>
                  </div>

                  <!-- File Worker & Background Tasks -->
                  <div class="grid grid-cols-2 gap-4">
                    <div class="rounded-lg border-2 border-teal-300 bg-teal-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-teal-500"></span>
                        <span class="text-xs font-bold text-teal-700">File Worker (Python)</span>
                      </div>
                      <div class="space-y-1 text-xs text-teal-600">
                        <p>病毒扫描 / 缩略图生成</p>
                        <p>文件转码 / 内容提取</p>
                        <p>全文索引构建</p>
                      </div>
                    </div>
                    <div class="rounded-lg border-2 border-gray-300 bg-gray-50/50 p-3">
                      <div class="flex items-center gap-2 mb-2">
                        <span class="h-3 w-3 rounded-full bg-gray-500"></span>
                        <span class="text-xs font-bold text-gray-700">治理与监控</span>
                      </div>
                      <div class="space-y-1 text-xs text-gray-600">
                        <p>Nacos / Sentinel / Seata</p>
                        <p>SkyWalking + SkyWalking UI</p>
                        <p>Prometheus + Grafana</p>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </section>

            <!-- Service Topology -->
            <section id="services" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">微服务拓扑</h2>
              <p class="mt-2 text-neutral-500">系统由多个独立服务组成，每个服务负责特定的业务领域。</p>

              <!-- Service Table -->
              <div class="mt-6 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">服务</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">技术栈</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">端口</th>
                      <th class="text-left py-3 px-4 font-semibold text-neutral-700">职责</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="service in services" :key="service.name" class="border-b border-neutral-100 hover:bg-neutral-50">
                      <td class="py-3 px-4">
                        <span class="font-medium text-neutral-800">{{ service.name }}</span>
                      </td>
                      <td class="py-3 px-4 text-neutral-600">{{ service.tech }}</td>
                      <td class="py-3 px-4">
                        <code class="rounded bg-primary/10 px-1.5 py-0.5 text-xs text-primary">{{ service.port }}</code>
                      </td>
                      <td class="py-3 px-4 text-neutral-600">{{ service.desc }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Communication -->
            <section id="communication" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">服务间通信</h2>
              <p class="mt-2 text-neutral-500">系统采用多种通信方式以适应不同的业务场景。</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2">
                <div v-for="comm in communications" :key="comm.type"
                     class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2 mb-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg" :class="comm.bgClass">
                      <i :class="[comm.icon, 'text-sm', comm.iconClass]"></i>
                    </div>
                    <span class="font-semibold text-neutral-800">{{ comm.type }}</span>
                  </div>
                  <p class="text-sm text-neutral-600">{{ comm.desc }}</p>
                  <div class="mt-2 rounded bg-neutral-50 px-2 py-1 text-xs text-neutral-500">
                    {{ comm.example }}
                  </div>
                </div>
              </div>
            </section>

            <!-- Multi-Client Architecture -->
            <section id="clients" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">多客户端架构</h2>
              <p class="mt-2 text-neutral-500">系统支持 10+ 客户端类型，覆盖 Web、桌面、移动端、CLI 等全场景。</p>

              <!-- Client Cards Grid -->
              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                <div v-for="client in clientDetails" :key="client.name"
                     class="rounded-xl border border-neutral-200 p-4 hover:border-primary/30 transition">
                  <div class="flex items-center gap-3 mb-3">
                    <div class="flex h-10 w-10 items-center justify-center rounded-lg" :class="client.bgClass">
                      <i :class="[client.icon, 'text-lg', client.iconClass]"></i>
                    </div>
                    <div>
                      <h3 class="font-semibold text-neutral-800">{{ client.name }}</h3>
                      <p class="text-xs text-neutral-400">{{ client.platform }}</p>
                    </div>
                  </div>
                  <div class="space-y-2">
                    <div>
                      <p class="text-xs font-medium text-neutral-500">技术栈</p>
                      <code class="mt-1 block rounded bg-neutral-100 px-2 py-1 text-xs text-neutral-600">{{ client.tech }}</code>
                    </div>
                    <div>
                      <p class="text-xs font-medium text-neutral-500">亮点特性</p>
                      <ul class="mt-1 space-y-1">
                        <li v-for="feature in client.features" :key="feature" class="flex items-center gap-1 text-xs text-neutral-600">
                          <i class="fa fa-check-circle text-success text-[10px]"></i>
                          {{ feature }}
                        </li>
                      </ul>
                    </div>
                  </div>
                </div>
              </div>

              <!-- Client Comparison Table -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">客户端功能对比</h3>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">客户端</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">文件管理</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">实时同步</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">离线访问</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">原生体验</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">本地磁盘挂载</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="client in clientComparison" :key="client.name" class="border-b border-neutral-100">
                      <td class="py-2 px-3 font-medium text-neutral-800">{{ client.name }}</td>
                      <td class="py-2 px-3 text-center"><i :class="client.file ? 'fa fa-check text-success' : 'fa fa-times text-neutral-300'"></i></td>
                      <td class="py-2 px-3 text-center"><i :class="client.sync ? 'fa fa-check text-success' : 'fa fa-times text-neutral-300'"></i></td>
                      <td class="py-2 px-3 text-center"><i :class="client.offline ? 'fa fa-check text-success' : 'fa fa-times text-neutral-300'"></i></td>
                      <td class="py-2 px-3 text-center"><i :class="client.native ? 'fa fa-check text-success' : 'fa fa-times text-neutral-300'"></i></td>
                      <td class="py-2 px-3 text-center"><i :class="client.mount ? 'fa fa-check text-success' : 'fa fa-times text-neutral-300'"></i></td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- Technology Stack -->
            <section id="tech-stack" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">技术选型</h2>
              <p class="mt-2 text-neutral-500">系统采用业界主流技术栈，兼顾性能、稳定性和开发效率。</p>

              <!-- Backend Tech -->
              <h3 class="mt-6 text-base font-semibold text-neutral-800">后端技术栈</h3>
              <div class="mt-4 space-y-3">
                <div v-for="tech in backendTechs" :key="tech.name"
                     class="flex items-start gap-4 rounded-lg border border-neutral-200 p-4">
                  <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg" :class="tech.bgClass">
                    <i :class="[tech.icon, 'text-lg', tech.iconClass]"></i>
                  </div>
                  <div class="flex-1">
                    <div class="flex items-center gap-2">
                      <span class="font-semibold text-neutral-800">{{ tech.name }}</span>
                      <span class="rounded bg-primary/10 px-1.5 py-0.5 text-xs text-primary">{{ tech.version }}</span>
                    </div>
                    <p class="mt-1 text-sm text-neutral-500">{{ tech.desc }}</p>
                    <p class="mt-2 text-xs text-neutral-400">
                      <strong>理由:</strong> {{ tech.reason }}
                    </p>
                  </div>
                </div>
              </div>

              <!-- Frontend Tech -->
              <h3 class="mt-8 text-base font-semibold text-neutral-800">前端技术栈</h3>
              <div class="mt-4 overflow-x-auto">
                <table class="w-full text-sm">
                  <thead>
                    <tr class="border-b border-neutral-200">
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">客户端</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">技术</th>
                      <th class="text-left py-2 px-3 font-semibold text-neutral-700">理由</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="client in frontendTechs" :key="client.client" class="border-b border-neutral-100">
                      <td class="py-2 px-3 text-neutral-800">{{ client.client }}</td>
                      <td class="py-2 px-3">
                        <code class="rounded bg-primary/10 px-1 py-0.5 text-xs text-primary">{{ client.tech }}</code>
                      </td>
                      <td class="py-2 px-3 text-neutral-600">{{ client.reason }}</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>

            <!-- High Availability -->
            <section id="ha" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">高可用设计</h2>
              <p class="mt-2 text-neutral-500">系统从网关层、服务层到数据层均采用高可用设计。</p>

              <div class="mt-6 grid grid-cols-1 gap-6">
                <div v-for="layer in haLayers" :key="layer.name"
                     class="rounded-xl border border-neutral-200 p-5">
                  <h3 class="flex items-center gap-2 text-base font-semibold text-neutral-800">
                    <span class="h-3 w-3 rounded-full" :class="layer.color"></span>
                    {{ layer.name }}
                  </h3>
                  <ul class="mt-3 space-y-2">
                    <li v-for="item in layer.items" :key="item" class="flex items-start gap-2 text-sm text-neutral-600">
                      <i class="fa fa-check-circle text-success mt-0.5 text-xs"></i>
                      {{ item }}
                    </li>
                  </ul>
                </div>
              </div>
            </section>

            <!-- Monitoring -->
            <section id="monitoring" class="rounded-2xl border border-neutral-200 bg-white p-6 sm:p-8">
              <h2 class="text-2xl font-bold text-neutral-900">监控与链路追踪</h2>
              <p class="mt-2 text-neutral-500">完善的监控体系，确保系统可观测性。</p>

              <div class="mt-6 grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2 mb-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-orange-100">
                      <i class="fa fa-chart-line text-orange-500"></i>
                    </div>
                    <span class="font-semibold text-neutral-800">SkyWalking</span>
                  </div>
                  <p class="text-sm text-neutral-600">分布式链路追踪，追踪请求在各个服务间的调用关系</p>
                  <div class="mt-3 rounded bg-neutral-50 px-2 py-1 text-xs text-neutral-500">
                    访问: http://skywalking:8080
                  </div>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2 mb-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-green-100">
                      <i class="fa fa-chart-bar text-green-500"></i>
                    </div>
                    <span class="font-semibold text-neutral-800">Prometheus</span>
                  </div>
                  <p class="text-sm text-neutral-600">指标采集与存储，支持自定义指标和告警规则</p>
                  <div class="mt-3 rounded bg-neutral-50 px-2 py-1 text-xs text-neutral-500">
                    端口: 9090
                  </div>
                </div>
                <div class="rounded-xl border border-neutral-200 p-4">
                  <div class="flex items-center gap-2 mb-3">
                    <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-purple-100">
                      <i class="fa fa-tachometer-alt text-purple-500"></i>
                    </div>
                    <span class="font-semibold text-neutral-800">Grafana</span>
                  </div>
                  <p class="text-sm text-neutral-600">可视化仪表盘，支持多种数据源和自定义面板</p>
                  <div class="mt-3 rounded bg-neutral-50 px-2 py-1 text-xs text-neutral-500">
                    端口: 3000
                  </div>
                </div>
              </div>
            </section>

            <!-- Navigation -->
            <div class="flex items-center justify-between rounded-2xl border border-neutral-200 bg-neutral-50 p-4">
              <router-link to="/docs/guide" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                <i class="fa fa-arrow-left"></i>
                快速入门
              </router-link>
              <router-link to="/docs/deployment" class="flex items-center gap-2 text-sm text-neutral-600 hover:text-primary transition">
                部署指南
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
  { id: 'overview', title: '架构概览' },
  { id: 'services', title: '微服务拓扑' },
  { id: 'communication', title: '服务通信' },
  { id: 'clients', title: '多客户端' },
  { id: 'tech-stack', title: '技术选型' },
  { id: 'ha', title: '高可用设计' },
  { id: 'monitoring', title: '监控追踪' },
]

const clients = [
  { name: 'Web (Vue 3)', class: 'text-blue-600' },
  { name: 'React Admin', class: 'text-cyan-600' },
  { name: 'Electron', class: 'text-teal-600' },
  { name: 'uni-app', class: 'text-green-600' },
  { name: 'SwiftUI iOS', class: 'text-indigo-600' },
  { name: 'SwiftUI macOS', class: 'text-purple-600' },
  { name: 'Kotlin Android', class: 'text-pink-600' },
  { name: 'WPF .NET Windows', class: 'text-blue-700' },
  { name: 'Go CLI', class: 'text-cyan-700' },
]

const services = [
  { name: 'gateway-service', tech: 'Spring Cloud Gateway + WebFlux', port: ':8080', desc: '统一入口、JWT 鉴权、路由分发、限流' },
  { name: 'platform-service', tech: 'Spring Boot 4.0.6 + MyBatis', port: ':8081', desc: '核心业务：用户/文件/目录树/配额/收藏/回收站' },
  { name: 'shortage-service', tech: 'FastAPI + Uvicorn (Python)', port: ':8000', desc: '文件 I/O：分片上传/流式下载/缩略图/凭证' },
  { name: 'notification-service', tech: 'Spring Boot + Thymeleaf', port: ':8082', desc: '通知服务：邮件/短信/站内信/推送路由' },
  { name: 'subscription-service', tech: 'Spring Boot + MyBatis', port: ':8083', desc: '订阅服务：配额管理/用量统计/计费' },
  { name: 'im-platform', tech: 'Spring Boot + MyBatis', port: '-', desc: 'IM 业务：消息/会话/群组管理' },
  { name: 'im-server', tech: 'Netty WebSocket', port: '-', desc: '长连接推送：万级并发、多端登录' },
  { name: 'file-worker', tech: 'Python (RabbitMQ Consumer)', port: '-', desc: '异步任务：病毒扫描/转码/缩略图/内容提取' },
]

const communications = [
  { type: 'HTTP REST', icon: 'fa fa-globe', iconClass: 'text-info', bgClass: 'bg-info/10', desc: '同步调用，适用于服务间同步请求响应场景', example: 'Gateway → Platform Service' },
  { type: '内部 API', icon: 'fa fa-lock', iconClass: 'text-success', bgClass: 'bg-success/10', desc: '服务间内部调用，需携带 X-Internal 头', example: 'Platform → File Service' },
  { type: 'RabbitMQ', icon: 'fa fa-envelope', iconClass: 'text-warning', bgClass: 'bg-warning/10', desc: '异步消息，适用于文件处理任务、欢迎邮件等', example: '文件处理任务队列' },
  { type: 'WebSocket', icon: 'fa fa-comments', iconClass: 'text-purple-500', bgClass: 'bg-purple-50', desc: '实时推送，适用于 IM 消息推送', example: 'IM 消息实时推送' },
  { type: 'WebRTC', icon: 'fa fa-phone', iconClass: 'text-danger', bgClass: 'bg-danger/10', desc: '音视频通话，适用于 P2P 音视频传输', example: '音视频通话' },
]

const clientDetails = [
  {
    name: 'Web 前端',
    platform: '浏览器 / H5',
    icon: 'fa fa-globe',
    iconClass: 'text-blue-500',
    bgClass: 'bg-blue-50',
    tech: 'Vue 3 + Vite + Tailwind CSS + Pinia',
    features: ['响应式设计', 'PWA 支持', '深色模式', '文件拖拽上传', '视频流播放'],
  },
  {
    name: 'React 管理后台',
    platform: '浏览器',
    icon: 'fa fa-cog',
    iconClass: 'text-cyan-500',
    bgClass: 'bg-cyan-50',
    tech: 'React 19 + Ant Design 6 + TypeScript',
    features: ['数据可视化', '用户管理', '系统监控', '日志审计', '权限管理'],
  },
  {
    name: 'Electron 桌面端',
    platform: 'Windows / macOS / Linux',
    icon: 'fa fa-desktop',
    iconClass: 'text-teal-500',
    bgClass: 'bg-teal-50',
    tech: 'Electron + React + macFUSE',
    features: ['虚拟磁盘挂载', '本地缓存', '增量同步', '系统托盘', '全局快捷键'],
  },
  {
    name: 'uni-app 移动端',
    platform: 'iOS / Android / 小程序',
    icon: 'fa fa-mobile-alt',
    iconClass: 'text-green-500',
    bgClass: 'bg-green-50',
    tech: 'uni-app (Vue 3) + uView Plus',
    features: ['一套代码多端', '原生插件扩展', '离线缓存', '扫码上传', '分享集成'],
  },
  {
    name: 'SwiftUI iOS',
    platform: 'iOS 15+',
    icon: 'fa fa-mobile',
    iconClass: 'text-indigo-500',
    bgClass: 'bg-indigo-50',
    tech: 'SwiftUI + Combine + CoreData',
    features: ['原生 Swift 性能', 'Face ID 解锁', 'Files App 集成', '后台下载', 'Widget 支持'],
  },
  {
    name: 'SwiftUI macOS',
    platform: 'macOS 12+',
    icon: 'fa fa-laptop',
    iconClass: 'text-purple-500',
    bgClass: 'bg-purple-50',
    tech: 'SwiftUI + AppKit + CoreData',
    features: ['Finder 扩展', 'Spotlight 索引', 'Touch Bar 支持', '键盘快捷键', 'macFUSE 磁盘挂载'],
  },
  {
    name: 'Kotlin Android',
    platform: 'Android 8+',
    icon: 'fa fa-android',
    iconClass: 'text-green-600',
    bgClass: 'bg-green-100',
    tech: 'Kotlin + Jetpack Compose + Room',
    features: ['Material Design 3', '生物识别解锁', 'SAF 文件访问', 'WorkManager 后台', 'Jetpack Widget'],
  },
  {
    name: 'WPF .NET Windows',
    platform: 'Windows 10/11',
    icon: 'fa fa-windows',
    iconClass: 'text-blue-600',
    bgClass: 'bg-blue-100',
    tech: 'WPF + .NET 8.0 + C#',
    features: ['WinRT 通知', 'Windows Hello', 'NTFS 磁盘挂载', '任务栏进度', 'Jump List'],
  },
  {
    name: 'Go CLI',
    platform: 'Linux / macOS / Windows',
    icon: 'fa fa-terminal',
    iconClass: 'text-cyan-600',
    bgClass: 'bg-cyan-100',
    tech: 'Go 1.21 + Cobra + Resty',
    features: ['无依赖部署', '管道支持', 'Shell 自动补全', '配置管理', 'SFTP 协议'],
  },
]

const clientComparison = [
  { name: 'Web', file: true, sync: true, offline: false, native: false, mount: false },
  { name: 'React Admin', file: true, sync: false, offline: false, native: false, mount: false },
  { name: 'Electron', file: true, sync: true, offline: true, native: true, mount: true },
  { name: 'uni-app', file: true, sync: true, offline: true, native: true, mount: false },
  { name: 'SwiftUI iOS', file: true, sync: true, offline: true, native: true, mount: false },
  { name: 'SwiftUI macOS', file: true, sync: true, offline: true, native: true, mount: true },
  { name: 'Kotlin Android', file: true, sync: true, offline: true, native: true, mount: false },
  { name: 'WPF .NET', file: true, sync: true, offline: true, native: true, mount: true },
  { name: 'Go CLI', file: true, sync: false, offline: false, native: false, mount: false },
]

const backendTechs = [
  { name: 'Spring Boot', version: '4.0.6', icon: 'fa fa-leaf', iconClass: 'text-success', bgClass: 'bg-success/10', desc: 'Java 微服务框架', reason: '生态成熟，社区活跃' },
  { name: 'Spring Cloud Gateway', version: '2023.0', icon: 'fa fa-gateway', iconClass: 'text-orange-500', bgClass: 'bg-orange-100', desc: '响应式 API 网关', reason: '非阻塞高并发，集成 Spring Security' },
  { name: 'FastAPI', version: '0.110+', icon: 'fa fa-bolt', iconClass: 'text-primary', bgClass: 'bg-primary/10', desc: 'Python 异步 I/O 框架', reason: '异步 I/O 性能优异，文件处理生态好' },
  { name: 'MyBatis', version: '3.0.4', icon: 'fa fa-database', iconClass: 'text-purple-500', bgClass: 'bg-purple-50', desc: 'SQL 映射框架', reason: 'SQL 可控，复杂查询灵活' },
  { name: 'Netty', version: '4.1', icon: 'fa fa-network-wired', iconClass: 'text-danger', bgClass: 'bg-danger/10', desc: '高性能 NIO 框架', reason: '万级并发，长连接首选' },
]

const frontendTechs = [
  { client: 'Web 用户端', tech: 'Vue 3 + Vite + Tailwind CSS + Pinia', reason: '开发效率高，生态丰富' },
  { client: 'Web 管理端', tech: 'React 19 + Ant Design 6', reason: '后台管理场景成熟方案' },
  { client: '桌面端', tech: 'Electron + React', reason: '跨平台，虚拟磁盘集成' },
  { client: '跨端移动端', tech: 'uni-app (Vue 3)', reason: '一套代码多端部署' },
  { client: 'iOS 原生', tech: 'SwiftUI', reason: 'Apple 生态原生体验' },
  { client: 'Android 原生', tech: 'Kotlin + Jetpack Compose', reason: 'Google 推荐现代方案' },
  { client: 'macOS 原生', tech: 'SwiftUI + AppKit', reason: '深度系统集成' },
  { client: 'Windows 原生', tech: 'WPF + .NET 8.0', reason: 'Windows 生态最佳实践' },
  { client: 'CLI 工具', tech: 'Go + Cobra', reason: '高性能，无依赖部署' },
]

const haLayers = [
  { name: '网关层', color: 'bg-orange-500', items: ['Nginx 反向代理 + 多实例部署', '健康检查自动摘除故障节点', 'Keepalived VRRP 高可用'] },
  { name: '服务层', color: 'bg-green-500', items: ['无状态设计，支持水平扩展', 'Sentinel 熔断降级，防止雪崩', 'Nacos 服务发现，动态路由'] },
  { name: '数据层', color: 'bg-red-500', items: ['MySQL 主从复制 + 读写分离', 'Redis 哨兵/集群模式', 'RabbitMQ 镜像队列', 'MinIO 纠删码模式'] },
  { name: '监控层', color: 'bg-cyan-500', items: ['SkyWalking 全链路追踪', 'Prometheus 指标采集', 'Grafana 可视化监控', 'AlertManager 告警通知'] },
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
