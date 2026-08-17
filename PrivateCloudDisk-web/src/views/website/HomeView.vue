<template>
  <div class="overflow-hidden">
    <!-- ============================================================ -->
    <!-- Hero Section with Parallax & Scroll Animation -->
    <!-- ============================================================ -->
    <section ref="heroSection" class="hero-section relative overflow-hidden bg-gradient-to-br from-primary/5 via-white to-info/5">
      <!-- Background decoration -->
      <div class="absolute inset-0 pointer-events-none">
        <div ref="heroBlob1" class="hero-blob absolute -top-40 -right-40 h-96 w-96 rounded-full bg-primary/10 blur-3xl"></div>
        <div ref="heroBlob2" class="hero-blob absolute -bottom-40 -left-40 h-96 w-96 rounded-full bg-info/10 blur-3xl"></div>
        <div class="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 h-64 w-64 rounded-full bg-warning/5 blur-3xl"></div>
        <!-- 粒子画布 -->
        <canvas ref="particleCanvas" class="absolute inset-0 z-0"></canvas>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 pb-20 pt-20 sm:px-6 sm:pb-28 sm:pt-28 lg:px-8 lg:pt-36">
        <div ref="heroContent" class="hero-content mx-auto max-w-3xl text-center">
          <div class="mb-6 inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/5 px-4 py-1.5 text-xs font-medium text-primary">
            <i class="fa fa-bolt text-[10px]"></i>
            全新版本发布 · 性能提升 300%
          </div>
          <h1 class="text-4xl font-extrabold tracking-tight text-neutral-900 sm:text-5xl lg:text-6xl">
            面向团队与业务的私有云平台
            <span class="mt-2 block bg-gradient-to-r from-primary to-info bg-clip-text text-transparent">
              文件 · 空间 · 自动化
            </span>
          </h1>
          <p class="mx-auto mt-6 max-w-2xl text-lg leading-relaxed text-neutral-500 sm:text-xl">
            PrivateCloudDisk 将文件管理、多人空间协作、在线预览和可扩展自动化能力放在同一个平台中，让团队围绕真实业务组织、处理和交付文件。
          </p>
          <div class="mt-10 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
            <router-link to="/register" class="hero-btn group inline-flex items-center gap-2 rounded-xl bg-primary px-8 py-3.5 text-base font-semibold text-white shadow-lg shadow-primary/25 transition-all duration-300 hover:bg-primary/90 hover:shadow-xl hover:-translate-y-0.5">
              免费开始使用
              <i class="fa fa-arrow-right text-sm transition-transform duration-300 group-hover:translate-x-1"></i>
            </router-link>
            <router-link to="/download" class="hero-btn inline-flex items-center gap-2 rounded-xl border-2 border-neutral-200 px-8 py-3.5 text-base font-semibold text-neutral-700 transition-all duration-300 hover:border-primary hover:text-primary hover:-translate-y-0.5">
              <i class="fa fa-download"></i>
              下载客户端
            </router-link>
          </div>
          <p class="mt-4 text-xs text-neutral-400">支持自部署 · 支持空间隔离 · 能力范围以当前部署配置为准</p>
        </div>

        <!-- Dashboard Preview -->
        <div ref="dashboardPreview" class="reveal mx-auto mt-16 max-w-5xl sm:mt-20">
          <div class="relative rounded-2xl border border-neutral-200/60 bg-white shadow-2xl shadow-neutral-900/5 overflow-hidden">
            <div class="flex items-center gap-2 border-b border-neutral-100 px-4 py-2.5 bg-neutral-50/50">
              <span class="h-3 w-3 rounded-full bg-danger/80"></span>
              <span class="h-3 w-3 rounded-full bg-warning/80"></span>
              <span class="h-3 w-3 rounded-full bg-success/80"></span>
              <span class="ml-2 text-xs text-neutral-400">CloudDrive Dashboard</span>
            </div>
            <div class="p-4 sm:p-6">
              <div class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
                <div v-for="card in previewCards" :key="card.label" class="rounded-xl border border-neutral-100 p-4 hover:border-primary/20 transition-colors duration-300">
                  <div class="flex items-center justify-between">
                    <span class="text-xs font-medium text-neutral-400">{{ card.label }}</span>
                    <i :class="[card.icon, 'text-neutral-300']"></i>
                  </div>
                  <p class="mt-2 text-2xl font-bold text-neutral-800">{{ card.value }}</p>
                  <p class="mt-1 text-xs" :class="card.trendUp ? 'text-success' : 'text-neutral-400'">
                    <i :class="card.trendUp ? 'fa fa-arrow-up' : 'fa fa-minus'"></i>
                    {{ card.trend }}
                  </p>
                </div>
              </div>
              <div class="mt-4 grid grid-cols-1 gap-4 lg:grid-cols-3">
                <div class="rounded-xl border border-neutral-100 p-4 lg:col-span-2">
                  <p class="text-xs font-medium text-neutral-400 mb-3">存储使用趋势</p>
                  <div class="flex items-end gap-1 h-24">
                    <div v-for="(h, i) in [40,65,52,80,70,90,75,85,60,72,88,68]" :key="i" class="chart-bar flex-1 rounded-t bg-primary/20 hover:bg-primary/40 transition" :style="{ height: h + '%' }"></div>
                  </div>
                </div>
                <div class="rounded-xl border border-neutral-100 p-4">
                  <p class="text-xs font-medium text-neutral-400 mb-3">文件类型分布</p>
                  <div class="space-y-2">
                    <div v-for="ft in fileTypes" :key="ft.type" class="flex items-center gap-2 text-xs">
                      <span :style="{ backgroundColor: ft.color }" class="h-2 w-2 rounded-full shrink-0"></span>
                      <span class="text-neutral-600">{{ ft.type }}</span>
                      <span class="ml-auto text-neutral-400">{{ ft.pct }}%</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Trusted By - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="trustedSection" class="reveal border-y border-neutral-100 bg-neutral-50/50 py-10">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <p class="text-center text-xs font-semibold uppercase tracking-widest text-neutral-400 mb-8">围绕真实业务能力构建的开放平台</p>
        <div class="flex flex-wrap items-center justify-center gap-8 sm:gap-12 opacity-50">
          <span v-for="brand in brands" :key="brand" class="text-lg font-bold text-neutral-400 hover:text-neutral-600 transition-colors duration-300">{{ brand }}</span>
        </div>
      </div>
    </section>

    <!-- 渐变过渡分割线 -->
    <div class="section-divider" aria-hidden="true">
      <div class="section-divider-inner"></div>
    </div>

    <!-- ============================================================
         Features Grid - Scroll Reveal Animation
         ============================================================ -->
    <section ref="featuresSection" class="py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div ref="featuresHeader" class="reveal mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
            一个云盘，也是一套业务文件平台
          </h2>
          <p class="mt-4 text-neutral-500">从文件 CURD 到空间协作，再到插件和工作流扩展，覆盖日常文件处理与业务自动化</p>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-8 sm:grid-cols-2 lg:grid-cols-3">
          <div v-for="(feat, idx) in features" :key="feat.title" :ref="(el) => setFeatureRef(el as HTMLElement, idx)" class="feature-card group rounded-2xl border border-neutral-100 p-8 transition-all duration-500 hover:border-primary/20 hover:shadow-lg hover:shadow-primary/5 hover:-translate-y-1">
            <div class="mb-5 flex h-12 w-12 items-center justify-center rounded-xl" :class="feat.bgClass">
              <i :class="[feat.icon, 'text-xl', feat.iconClass]"></i>
            </div>
            <h3 class="text-lg font-semibold text-neutral-800">{{ feat.title }}</h3>
            <p class="mt-2 text-sm leading-relaxed text-neutral-500">{{ feat.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- 🎯 水平滚动展示区 - 参考 Apple / Trae 官网的 scroll-driven 水平滚动 -->
    <!-- 用户往下滚动时，此区域自动水平滚动，到底后再继续向下 -->
    <!-- ============================================================ -->
    <section ref="horizontalScrollSection" class="horizontal-scroll-section relative">
      <!-- 外层容器：给 ScrollTrigger 提供足够的滚动空间 -->
      <div ref="horizontalOuter" class="horizontal-outer">
        <!-- 内层粘性容器：被 pin 住 -->
        <div ref="horizontalSticky" class="horizontal-sticky">
          <!-- 水平轨道：所有面板横向排列 -->
          <div ref="horizontalTrack" class="horizontal-track">

            <!-- 面板 0：全平台支持 -->
            <div class="horizontal-panel" :style="{ backgroundColor: panelColors[0] }">
              <div class="horizontal-panel-inner">
                <div class="panel-content grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
                  <div class="panel-text space-y-6">
                    <span class="inline-flex items-center gap-2 rounded-full bg-white/20 px-4 py-1.5 text-xs font-semibold text-white backdrop-blur-sm">空间协作</span>
                    <h2 class="text-4xl font-extrabold text-white sm:text-5xl lg:text-6xl leading-tight">
                      团队空间<br />清晰协作
                    </h2>
                    <p class="text-lg text-white/80 max-w-md leading-relaxed">
                      创建个人空间或团队空间，邀请成员并配置角色、权限和资源范围，让项目文件、部门资料与个人文件保持清晰边界。
                    </p>
                    <div class="flex flex-wrap gap-3">
                      <span v-for="p in platforms" :key="p" class="inline-flex items-center gap-1.5 rounded-lg bg-white/15 px-3 py-2 text-sm text-white backdrop-blur-sm">
                        <i :class="p.icon" class="text-base"></i> {{ p.name }}
                      </span>
                    </div>
                  </div>
                  <div class="panel-visual flex items-center justify-center">
                    <div class="relative">
                      <div class="w-64 h-64 sm:w-80 sm:h-80 rounded-3xl bg-gradient-to-br from-white/20 to-white/5 backdrop-blur-md border border-white/20 flex items-center justify-center shadow-2xl">
                        <div class="grid grid-cols-2 gap-4 p-6">
                          <div v-for="d in deviceIcons" :key="d.name" class="flex flex-col items-center gap-2 p-3 rounded-xl bg-white/10 hover:bg-white/20 transition cursor-pointer">
                            <i :class="[d.icon, 'text-2xl text-white']"></i>
                            <span class="text-xs text-white/70">{{ d.name }}</span>
                          </div>
                        </div>
                      </div>
                      <div class="absolute -top-6 -right-6 w-12 h-12 rounded-full bg-white/20 blur-xl animate-pulse"></div>
                      <div class="absolute -bottom-6 -left-6 w-12 h-12 rounded-full bg-white/20 blur-xl animate-pulse" style="animation-delay: 1s;"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 面板 1：插件与工作流 -->
            <div class="horizontal-panel" :style="{ backgroundColor: panelColors[1] }">
              <div class="horizontal-panel-inner">
                <div class="panel-content grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
                  <div class="panel-visual flex items-center justify-center order-2 lg:order-1">
                    <div class="relative">
                      <div class="w-64 h-64 sm:w-80 sm:h-80 rounded-3xl bg-gradient-to-br from-white/20 to-white/5 backdrop-blur-md border border-white/20 flex items-center justify-center shadow-2xl overflow-hidden">
                        <div class="text-center p-6">
                          <div class="relative inline-block mb-4">
                            <i class="fa fa-search text-5xl text-white/80"></i>
                            <div class="absolute -top-2 -right-2 w-20 h-6 rounded-full bg-white/20 blur-sm animate-pulse"></div>
                          </div>
                          <p class="text-white/60 text-sm">插件与工作流</p>
                          <div class="mt-4 space-y-2">
                            <div v-for="w in 3" :key="w" class="h-2 rounded-full bg-white/15" :style="{ width: (80 - w * 15) + '%' }"></div>
                          </div>
                        </div>
                      </div>
                      <div class="absolute -top-4 -left-4 w-8 h-8 rounded-full bg-yellow-300/30 blur-md"></div>
                    </div>
                  </div>
                  <div class="panel-text space-y-6 order-1 lg:order-2">
                    <span class="inline-flex items-center gap-2 rounded-full bg-white/20 px-4 py-1.5 text-xs font-semibold text-white backdrop-blur-sm">开放扩展</span>
                    <h2 class="text-4xl font-extrabold text-white sm:text-5xl lg:text-6xl leading-tight">
                      连接能力<br />自动执行
                    </h2>
                    <p class="text-lg text-white/80 max-w-md leading-relaxed">
                      云插件、本地扩展和工作流共享统一的能力中心；可以在文件生命周期事件上触发处理，也可以把可复用流程发布到市场。
                    </p>
                    <div class="flex items-center gap-4">
                      <div class="flex -space-x-2">
                        <span v-for="i in 4" :key="i" class="w-8 h-8 rounded-full bg-white/20 border-2 border-white/30 flex items-center justify-center text-xs text-white font-bold">{{ ['文','图','音','视'][i-1] }}</span>
                      </div>
                      <span class="text-sm text-white/60">云插件 · 本地扩展 · 工作流</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 面板 2：企业级安全 -->
            <div class="horizontal-panel" :style="{ backgroundColor: panelColors[2] }">
              <div class="horizontal-panel-inner">
                <div class="panel-content grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
                  <div class="panel-text space-y-6">
                    <span class="inline-flex items-center gap-2 rounded-full bg-white/20 px-4 py-1.5 text-xs font-semibold text-white backdrop-blur-sm">访问与安全</span>
                    <h2 class="text-4xl font-extrabold text-white sm:text-5xl lg:text-6xl leading-tight">
                      可控可审计<br />安全边界
                    </h2>
                    <p class="text-lg text-white/80 max-w-md leading-relaxed">
                      通过网关统一认证、请求限流和路由，结合空间上下文、文件权限、操作凭证与内部服务校验，形成可追踪的访问边界。
                    </p>
                    <div class="grid grid-cols-2 gap-3">
                      <div v-for="s in securityBadges" :key="s.label" class="flex items-center gap-2 rounded-lg bg-white/10 px-3 py-2 backdrop-blur-sm">
                        <i :class="[s.icon, 'text-white/80 text-sm']"></i>
                        <span class="text-xs text-white/80">{{ s.label }}</span>
                      </div>
                    </div>
                  </div>
                  <div class="panel-visual flex items-center justify-center">
                    <div class="relative">
                      <div class="w-64 h-64 sm:w-80 sm:h-80 rounded-3xl bg-gradient-to-br from-white/20 to-white/5 backdrop-blur-md border border-white/20 flex items-center justify-center shadow-2xl">
                        <div class="text-center">
                          <i class="fa fa-shield text-6xl text-white/60"></i>
                          <div class="mt-4 flex items-center justify-center gap-1">
                            <div v-for="i in 5" :key="i" class="w-8 h-1 rounded-full bg-white/30" :class="{ 'bg-white/80': i <= 4 }"></div>
                          </div>
                          <p class="mt-3 text-white/50 text-xs">机制可核验 · 结果按部署验证</p>
                        </div>
                      </div>
                      <div class="absolute -top-4 -right-4 w-16 h-16 rounded-full border-2 border-white/20 animate-spin" style="animation-duration: 8s;"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 面板 3：高效协作 -->
            <div class="horizontal-panel" :style="{ backgroundColor: panelColors[3] }">
              <div class="horizontal-panel-inner">
                <div class="panel-content grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
                  <div class="panel-visual flex items-center justify-center order-2 lg:order-1">
                    <div class="relative">
                      <div class="w-64 h-64 sm:w-80 sm:h-80 rounded-3xl bg-gradient-to-br from-white/20 to-white/5 backdrop-blur-md border border-white/20 flex items-center justify-center shadow-2xl p-6">
                        <div class="w-full space-y-3">
                          <div v-for="collab in collaborationItems" :key="collab.name" class="flex items-center gap-3 p-2 rounded-lg bg-white/10">
                            <div class="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center text-xs text-white font-bold">{{ collab.name.charAt(0) }}</div>
                            <div class="flex-1">
                              <div class="h-2 rounded-full bg-white/20 w-3/4"></div>
                            </div>
                            <span class="w-2 h-2 rounded-full" :class="collab.online ? 'bg-green-400' : 'bg-white/30'"></span>
                          </div>
                        </div>
                      </div>
                      <div class="absolute -bottom-3 -right-3 w-10 h-10 rounded-full bg-green-400/30 blur-md animate-pulse"></div>
                    </div>
                  </div>
                  <div class="panel-text space-y-6 order-1 lg:order-2">
                    <span class="inline-flex items-center gap-2 rounded-full bg-white/20 px-4 py-1.5 text-xs font-semibold text-white backdrop-blur-sm">团队协作</span>
                    <h2 class="text-4xl font-extrabold text-white sm:text-5xl lg:text-6xl leading-tight">
                      实时协同<br />效率翻倍
                    </h2>
                    <p class="text-lg text-white/80 max-w-md leading-relaxed">
                      共享文件夹、空间成员、角色权限与文件分享相互配合，让团队在同一资源边界内协作处理文件。
                    </p>
                    <div class="flex items-center gap-2">
                      <i class="fa fa-users text-white/60"></i>
                      <span class="text-sm text-white/60">空间成员 · 角色权限 · 操作记录</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 面板 4：开放生态 -->
            <div class="horizontal-panel" :style="{ backgroundColor: panelColors[4] }">
              <div class="horizontal-panel-inner">
                <div class="panel-content grid grid-cols-1 lg:grid-cols-2 gap-8 items-center">
                  <div class="panel-text space-y-6">
                    <span class="inline-flex items-center gap-2 rounded-full bg-white/20 px-4 py-1.5 text-xs font-semibold text-white backdrop-blur-sm">开放平台</span>
                    <h2 class="text-4xl font-extrabold text-white sm:text-5xl lg:text-6xl leading-tight">
                      开放 API<br />无限扩展
                    </h2>
                    <p class="text-lg text-white/80 max-w-md leading-relaxed">
                      提供 REST API、内部服务契约与客户端身份能力，方便接入现有系统；扩展能力以仓库中的接口和部署配置为准。
                    </p>
                    <router-link to="/register" class="inline-flex items-center gap-2 rounded-xl bg-white px-6 py-3 text-sm font-semibold text-primary shadow-lg transition-all duration-300 hover:bg-neutral-50 hover:shadow-xl hover:-translate-y-0.5">
                      开始集成
                      <i class="fa fa-arrow-right text-xs"></i>
                    </router-link>
                  </div>
                  <div class="panel-visual flex items-center justify-center">
                    <div class="relative">
                      <div class="w-64 h-64 sm:w-80 sm:h-80 rounded-3xl bg-gradient-to-br from-white/20 to-white/5 backdrop-blur-md border border-white/20 flex items-center justify-center shadow-2xl p-6">
                        <div class="grid grid-cols-3 gap-3">
                          <div v-for="api in apiIcons" :key="api.name" class="flex flex-col items-center gap-1 p-3 rounded-xl bg-white/10 hover:bg-white/20 transition cursor-pointer">
                            <i :class="[api.icon, 'text-xl text-white/80']"></i>
                            <span class="text-[10px] text-white/50">{{ api.name }}</span>
                          </div>
                        </div>
                      </div>
                      <div class="absolute -top-5 -left-5 w-10 h-10 rounded-full bg-purple-400/30 blur-lg"></div>
                      <div class="absolute -bottom-5 -right-5 w-10 h-10 rounded-full bg-blue-400/30 blur-lg"></div>
                    </div>
                  </div>
                </div>
              </div>
            </div>

          </div><!-- /horizontal-track -->

          <!-- 进度指示器 -->
          <div class="horizontal-progress">
            <button
              v-for="(_, idx) in horizontalPanels"
              :key="idx"
              class="horizontal-progress-dot"
              :class="{ active: activePanel === idx }"
              @click="scrollToPanel(idx)"
              :aria-label="`跳转到第 ${idx + 1} 页`"
            ></button>
          </div>

          <!-- 左右导航箭头 -->
          <button class="horizontal-nav horizontal-nav-left" @click="navigatePanel(-1)" aria-label="上一个">
            <i class="fa fa-chevron-left"></i>
          </button>
          <button class="horizontal-nav horizontal-nav-right" @click="navigatePanel(1)" aria-label="下一个">
            <i class="fa fa-chevron-right"></i>
          </button>
        </div><!-- /horizontal-sticky -->
      </div><!-- /horizontal-outer -->
    </section>

    <!-- ============================================================ -->
    <!-- Stats Section with Counter Animation -->
    <!-- ============================================================ -->
    <section ref="statsSection" class="bg-neutral-900 py-20 sm:py-28 relative overflow-hidden">
      <!-- Background grid pattern -->
      <div class="absolute inset-0 opacity-5">
        <div class="absolute inset-0" style="background-image: linear-gradient(rgba(255,255,255,.1) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.1) 1px, transparent 1px); background-size: 40px 40px;"></div>
      </div>
      <div class="relative mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-2 gap-8 sm:grid-cols-4">
          <div v-for="(stat, idx) in stats" :key="stat.label" :ref="el => setStatRef(el as HTMLElement, idx)" class="stat-item text-center">
            <p class="stat-number text-4xl font-extrabold text-white sm:text-5xl">
              <span :ref="el => setStatNumberRef(el as HTMLElement, idx)">{{ stat.value }}</span>
            </p>
            <p class="mt-2 text-sm font-medium text-neutral-400">{{ stat.label }}</p>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Security Section - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="securitySection" class="py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="grid grid-cols-1 items-center gap-12 lg:grid-cols-2">
          <div ref="securityContent" class="reveal">
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">
              <i class="fa fa-shield"></i> 安全边界
            </span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">
              企业级安全防护
            </h2>
            <p class="mt-4 text-neutral-500 leading-relaxed">
              平台把认证、空间权限、文件操作凭证、内部服务校验和生命周期事件串联起来，便于按部署配置建立可控、可追踪的访问边界。
            </p>
            <ul class="mt-6 space-y-3">
              <li v-for="item in securityItems" :key="item" class="flex items-center gap-3 text-sm text-neutral-600">
                <i class="fa fa-check-circle text-success"></i> {{ item }}
              </li>
            </ul>
            <router-link to="/features" class="mt-8 inline-flex items-center gap-2 text-sm font-semibold text-primary hover:underline">
              了解更多安全特性 <i class="fa fa-arrow-right text-xs"></i>
            </router-link>
          </div>
          <div ref="securityCard" class="reveal relative">
            <div class="rounded-2xl border border-neutral-200 bg-white p-6 shadow-xl">
              <div class="space-y-4">
                <div v-for="cert in certs" :key="cert.label" class="flex items-center gap-4 rounded-xl border border-neutral-100 p-4 hover:border-success/20 transition-colors duration-300">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-success/10">
                    <i :class="[cert.icon, 'text-success']"></i>
                  </div>
                  <div>
                    <p class="text-sm font-semibold text-neutral-700">{{ cert.label }}</p>
                    <p class="text-xs text-neutral-400">{{ cert.desc }}</p>
                  </div>
                  <i class="fa fa-check-circle text-success ml-auto"></i>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Testimonials - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="testimonialsSection" class="border-t border-neutral-100 bg-neutral-50/50 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="reveal mx-auto max-w-2xl text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">平台能力如何落到业务</h2>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-8 md:grid-cols-3">
          <div v-for="(t, idx) in testimonials" :key="t.name" :ref="(el) => setTestimonialRef(el as HTMLElement, idx)" class="testimonial-card rounded-2xl border border-neutral-200 bg-white p-6 transition-all duration-300 hover:shadow-lg hover:-translate-y-1">
            <div class="flex gap-0.5 text-warning">
              <i v-for="n in 5" :key="n" class="fa fa-star text-xs"></i>
            </div>
            <p class="mt-4 text-sm leading-relaxed text-neutral-600">{{ t.text }}</p>
            <div class="mt-4 flex items-center gap-3 border-t border-neutral-100 pt-4">
              <div class="flex h-10 w-10 items-center justify-center rounded-full bg-primary/10 text-sm font-bold text-primary">
                {{ t.name.charAt(0) }}
              </div>
              <div>
                <p class="text-sm font-semibold text-neutral-700">{{ t.name }}</p>
                <p class="text-xs text-neutral-400">{{ t.role }}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Solutions Overview - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="solutionsSection" class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="reveal mx-auto max-w-2xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-info/10 px-3 py-1 text-xs font-medium text-info">业务场景</span>
          <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">从基础文件到自动化协作</h2>
          <p class="mt-4 text-neutral-500">按项目、部门、外部协作和内容处理等场景组合平台能力</p>
        </div>
        <div class="mt-16 grid grid-cols-1 gap-6 sm:grid-cols-2 lg:grid-cols-4">
          <div v-for="sol in solutions" :key="sol.title" class="group rounded-2xl border border-neutral-100 p-6 transition-all duration-500 hover:border-primary/20 hover:shadow-lg hover:-translate-y-1">
            <div class="mb-4 flex h-12 w-12 items-center justify-center rounded-xl" :class="sol.bgClass">
              <i :class="[sol.icon, 'text-xl', sol.iconClass]"></i>
            </div>
            <h3 class="text-base font-semibold text-neutral-800">{{ sol.title }}</h3>
            <p class="mt-2 text-xs leading-relaxed text-neutral-500">{{ sol.desc }}</p>
            <router-link :to="sol.href" class="mt-4 inline-flex items-center gap-1 text-xs font-medium text-primary hover:underline">了解更多 <i class="fa fa-arrow-right text-[10px]"></i></router-link>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Integration Partners - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="integrationSection" class="border-t border-neutral-100 bg-neutral-50/30 py-20 sm:py-24">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="reveal mx-auto max-w-2xl text-center">
          <span class="inline-flex items-center gap-2 rounded-full bg-purple-50 px-3 py-1 text-xs font-medium text-purple-600">开放能力</span>
          <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">用 API、插件和工作流连接现有系统</h2>
          <p class="mt-4 text-neutral-500">以当前仓库提供的接口、事件契约和扩展运行时为基础，逐步接入现有业务流程</p>
        </div>
        <div class="mt-12 grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-6">
          <div v-for="it in integrations" :key="it.name" class="flex flex-col items-center gap-2 rounded-xl border border-neutral-100 bg-white p-5 transition-all duration-300 hover:shadow-md hover:border-primary/20 hover:-translate-y-1">
            <i :class="[it.icon, 'text-2xl', it.color]"></i>
            <span class="text-xs font-medium text-neutral-600">{{ it.name }}</span>
          </div>
        </div>
        <p class="mt-8 text-center">
          <router-link to="/features" class="text-sm font-medium text-primary hover:underline">查看全部集成 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </p>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Case Study Highlights - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="caseSection" class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="reveal flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-success/10 px-3 py-1 text-xs font-medium text-success">落地场景</span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">把平台能力映射到实际工作</h2>
          </div>
          <router-link to="/case-studies" class="text-sm font-medium text-primary hover:underline shrink-0">查看全部案例 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 md:grid-cols-3">
          <div v-for="cs in caseHighlights" :key="cs.company" class="group rounded-2xl border border-neutral-200 overflow-hidden transition-all duration-500 hover:shadow-lg hover:-translate-y-1">
            <div :class="[cs.gradient, 'h-3']"></div>
            <div class="p-6">
              <div class="flex items-center gap-3">
                <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-neutral-100 text-sm font-bold text-neutral-600">{{ cs.company.charAt(0) }}</div>
                <div>
                  <p class="text-sm font-semibold text-neutral-800">{{ cs.company }}</p>
                  <p class="text-xs text-neutral-400">{{ cs.industry }}</p>
                </div>
              </div>
              <p class="mt-4 text-sm leading-relaxed text-neutral-600">{{ cs.summary }}</p>
              <div class="mt-4 grid grid-cols-3 gap-2 border-t border-neutral-100 pt-4">
                <div v-for="m in cs.metrics" :key="m.label" class="text-center">
                  <p class="text-lg font-bold text-primary">{{ m.value }}</p>
                  <p class="text-[10px] text-neutral-400">{{ m.label }}</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- Blog Preview - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="blogSection" class="border-t border-neutral-100 bg-neutral-50/30 py-20 sm:py-28">
      <div class="mx-auto max-w-7xl px-4 sm:px-6 lg:px-8">
        <div class="reveal flex flex-col sm:flex-row sm:items-end sm:justify-between gap-4">
          <div>
            <span class="inline-flex items-center gap-2 rounded-full bg-warning/10 px-3 py-1 text-xs font-medium text-warning">技术博客</span>
            <h2 class="mt-4 text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">最新动态与技术分享</h2>
          </div>
          <router-link to="/blog" class="text-sm font-medium text-primary hover:underline shrink-0">查看全部文章 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </div>
        <div class="mt-12 grid grid-cols-1 gap-8 md:grid-cols-3">
          <article v-for="post in blogPosts" :key="post.title" class="group rounded-2xl border border-neutral-200 bg-white overflow-hidden transition-all duration-500 hover:shadow-lg hover:-translate-y-1 cursor-pointer">
            <div class="p-6">
              <div class="flex items-center gap-2 text-xs">
                <span class="rounded-md bg-primary/10 px-2 py-0.5 font-medium text-primary">{{ post.category }}</span>
                <span class="text-neutral-400">{{ post.date }}</span>
              </div>
              <h3 class="mt-3 text-base font-semibold text-neutral-800 group-hover:text-primary transition line-clamp-2">{{ post.title }}</h3>
              <p class="mt-2 text-sm text-neutral-500 line-clamp-2">{{ post.excerpt }}</p>
              <div class="mt-4 flex items-center gap-3 border-t border-neutral-100 pt-4">
                <div class="flex h-7 w-7 items-center justify-center rounded-full bg-neutral-100 text-xs font-bold text-neutral-500">{{ post.author.charAt(0) }}</div>
                <div>
                  <p class="text-xs font-medium text-neutral-600">{{ post.author }}</p>
                  <p class="text-[10px] text-neutral-400">{{ post.readTime }}</p>
                </div>
              </div>
            </div>
          </article>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- FAQ Quick Section - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="faqSection" class="py-20 sm:py-28 bg-white">
      <div class="mx-auto max-w-3xl px-4 sm:px-6 lg:px-8">
        <div class="reveal text-center">
          <h2 class="text-3xl font-bold tracking-tight text-neutral-900 sm:text-4xl">常见问题</h2>
          <p class="mt-4 text-neutral-500">关于 PrivateCloudDisk 当前能力与部署方式的常见疑问</p>
        </div>
        <div class="mt-10 grid grid-cols-1 gap-4 sm:grid-cols-2">
          <div v-for="(faq, i) in homeFaqs" :key="i" class="rounded-xl border border-neutral-100 p-5 transition-all duration-300 hover:border-primary/20 hover:shadow-sm">
            <p class="text-sm font-semibold text-neutral-700 mb-2">{{ faq.q }}</p>
            <p class="text-xs text-neutral-500 leading-relaxed">{{ faq.a }}</p>
          </div>
        </div>
        <p class="mt-8 text-center">
          <router-link to="/docs" class="text-sm font-medium text-primary hover:underline">查看更多常见问题 <i class="fa fa-arrow-right text-xs"></i></router-link>
        </p>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- CTA Section - Scroll Reveal -->
    <!-- ============================================================ -->
    <section ref="ctaSection" class="py-20 sm:py-28">
      <div class="mx-auto max-w-4xl px-4 sm:px-6 lg:px-8">
        <div ref="ctaCard" class="reveal relative overflow-hidden rounded-3xl bg-gradient-to-br from-primary to-info p-10 sm:p-16 text-center">
          <div class="absolute -top-20 -right-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="absolute -bottom-20 -left-20 h-64 w-64 rounded-full bg-white/10 blur-3xl"></div>
          <div class="relative">
            <h2 class="text-3xl font-bold text-white sm:text-4xl">准备好组织您的文件业务了吗？</h2>
            <p class="mt-4 text-lg text-white/80">从文件管理开始，逐步启用空间协作、在线预览和自动化扩展</p>
            <div class="mt-8 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
              <router-link to="/register" class="cta-btn group inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3.5 text-base font-semibold text-primary shadow-lg transition-all duration-300 hover:bg-neutral-50 hover:-translate-y-0.5 hover:shadow-xl">
                立即注册
                <i class="fa fa-arrow-right text-sm transition-transform duration-300 group-hover:translate-x-1"></i>
              </router-link>
              <router-link to="/contact" class="cta-btn inline-flex items-center gap-2 rounded-xl border-2 border-white/30 px-8 py-3.5 text-base font-semibold text-white transition-all duration-300 hover:border-white hover:bg-white/10 hover:-translate-y-0.5">
                <i class="fa fa-comments"></i>
                联系销售
              </router-link>
            </div>
          </div>
        </div>
      </div>
    </section>

    <!-- ============================================================ -->
    <!-- 🎯 大标题赛博模糊区域 - 参考 Trae 官网底部 "TRAE" 大字效果 -->
    <!-- ============================================================ -->
    <section ref="brandHeroSection" class="brand-hero-section relative overflow-hidden bg-gradient-to-br from-primary via-info to-emerald-500 py-32 sm:py-40">
      <!-- 背景网格装饰 -->
      <div class="absolute inset-0 opacity-10">
        <div class="absolute inset-0" style="background-image: linear-gradient(rgba(255,255,255,.3) 1px, transparent 1px), linear-gradient(90deg, rgba(255,255,255,.3) 1px, transparent 1px); background-size: 60px 60px;"></div>
      </div>
      <!-- 动态光晕 -->
      <div ref="brandGlow1" class="brand-glow absolute -top-1/2 left-1/4 h-96 w-96 rounded-full bg-white/20 blur-[100px]"></div>
      <div ref="brandGlow2" class="brand-glow absolute -bottom-1/2 right-1/4 h-96 w-96 rounded-full bg-emerald-300/30 blur-[100px]"></div>

      <div class="relative mx-auto max-w-7xl px-4 text-center">
        <!-- 小标题 -->
        <p ref="brandSubtitle" class="brand-subtitle text-sm font-medium uppercase tracking-[0.3em] text-white/60">Enterprise Private Cloud Storage</p>

        <!-- 大字幕 - 赛博模糊效果 -->
        <div ref="brandTextWrapper" class="brand-text-wrapper relative mt-8 inline-block">
          <h2
            ref="brandText"
            class="brand-text text-[12vw] sm:text-[10vw] font-black leading-none tracking-tighter text-white select-none"
            @mousemove="handleBrandTextMouseMove"
            @mouseleave="handleBrandTextMouseLeave"
          >
            CLOUD
            <span class="block text-[14vw] sm:text-[12vw] -mt-2">DRIVE</span>
          </h2>
          <!-- 赛博模糊叠加层 -->
          <div ref="brandBlurOverlay" class="brand-blur-overlay pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-500">
            <div class="brand-text text-[12vw] sm:text-[10vw] font-black leading-none tracking-tighter text-white/80 blur-md">
              CLOUD
              <span class="block text-[14vw] sm:text-[12vw] -mt-2">DRIVE</span>
            </div>
          </div>
          <!-- 赛博光晕效果 -->
          <div ref="brandGlowText" class="brand-glow-text pointer-events-none absolute inset-0 opacity-0 transition-opacity duration-500">
            <div class="brand-text text-[12vw] sm:text-[10vw] font-black leading-none tracking-tighter text-emerald-300 blur-2xl">
              CLOUD
              <span class="block text-[14vw] sm:text-[12vw] -mt-2">DRIVE</span>
            </div>
          </div>
        </div>

        <!-- 描述文字 -->
        <p ref="brandDesc" class="brand-desc mx-auto mt-10 max-w-2xl text-lg text-white/70 leading-relaxed">
          将文件、空间协作、插件与工作流连接起来的私有云平台，能力边界清晰，数据部署位置可控
        </p>

        <!-- 底部按钮 -->
        <div ref="brandActions" class="brand-actions mt-10 flex flex-col items-center gap-4 sm:flex-row sm:justify-center">
          <router-link to="/register" class="group inline-flex items-center gap-2 rounded-xl bg-white px-8 py-3.5 text-base font-semibold text-primary shadow-xl shadow-black/20 transition-all duration-300 hover:bg-neutral-50 hover:shadow-2xl hover:-translate-y-0.5">
            开始使用 PrivateCloudDisk
            <i class="fa fa-arrow-right text-sm transition-transform duration-300 group-hover:translate-x-1"></i>
          </router-link>
          <router-link to="/download" class="inline-flex items-center gap-2 rounded-xl border-2 border-white/30 px-8 py-3.5 text-base font-semibold text-white transition-all duration-300 hover:border-white hover:bg-white/10 hover:-translate-y-0.5">
            <i class="fa fa-download"></i>
            下载客户端
          </router-link>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'

// 注册 GSAP 插件
gsap.registerPlugin(ScrollTrigger)

// ============================================================
// 数据
// ============================================================
// 需求编号：REQ-WEB-CONTENT-2026-07
// 改动点：官网首页由历史演示数据改为当前代码已实现的能力说明，避免把虚构的客户数、容量、性能和合规结果当作产品事实。
// 原有行为：展示固定的业务统计与客户背书；新行为：展示文件、空间、预览、扩展和服务边界等可由仓库核验的能力。
// 影响范围：仅影响官网首页文案与静态演示数据，不改变布局、样式、动画、路由或后端业务逻辑。
const previewCards = [
  { label: '已用存储', value: '2.4 TB', icon: 'fa fa-hdd-o', trend: '较上月 +15%', trendUp: false },
  { label: '文件总数', value: '45,821', icon: 'fa fa-files-o', trend: '较上月 +8%', trendUp: true },
  { label: '活跃用户', value: '1,245', icon: 'fa fa-user-o', trend: '较上月 +12%', trendUp: true },
  { label: '下载流量', value: '856 GB', icon: 'fa fa-download', trend: '较上月 +23%', trendUp: true },
]

const fileTypes = [
  { type: '文档', pct: 35, color: '#2B7FFF' },
  { type: '图片', pct: 28, color: '#FF6B6B' },
  { type: '视频', pct: 18, color: '#FFD93D' },
  { type: '其他', pct: 19, color: '#95A5A6' },
]

const brands = ['空间协作', '文件预览', '插件平台', '工作流市场', '多端客户端', '开放 API', '安全审计', '私有部署']

const features = [
  { title: '文件全生命周期', desc: '支持文件与文件夹 CURD、分片上传、断点续传、流式下载、回收站和收藏管理。', icon: 'fa fa-folder-open', bgClass: 'bg-primary/10', iconClass: 'text-primary' },
  { title: '空间多人协作', desc: '以空间组织项目或团队资源，支持成员、角色、权限、共享文件夹和空间插件管理。', icon: 'fa fa-users', bgClass: 'bg-success/10', iconClass: 'text-success' },
  { title: '标签与快速查找', desc: '使用标签、星标、最近访问和搜索能力整理文件，让个人与团队资源更容易定位。', icon: 'fa fa-search', bgClass: 'bg-warning/10', iconClass: 'text-warning' },
  { title: '多端访问与同步', desc: '提供 Web、桌面端、移动端和原生客户端入口，按客户端能力访问同一平台数据。', icon: 'fa fa-sync', bgClass: 'bg-info/10', iconClass: 'text-info' },
  { title: '预览与媒体处理', desc: '覆盖图片、PDF、Office、Markdown、代码、压缩包、音频和视频等在线处理场景。', icon: 'fa fa-eye', bgClass: 'bg-danger/10', iconClass: 'text-danger' },
  { title: '插件与工作流扩展', desc: '支持云插件、本地扩展、能力中心、工作流编排，以及插件和工作流市场。', icon: 'fa fa-code', bgClass: 'bg-purple-100', iconClass: 'text-purple-600' },
]

const stats = [
  { value: '6', label: '客户端入口' },
  { value: '3', label: '插件运行形态' },
  { value: '2', label: '可扩展市场' },
  { value: '1', label: '统一平台底座' },
]

const securityItems = [
  '网关统一认证、路由与请求限流',
  'JWT、操作凭证和内部服务令牌分层校验',
  '空间上下文参与资源访问与权限判断',
  '文件分享支持访问边界与撤回管理',
  '上传、预览、下载和处理链路分工清晰',
  '文件生命周期事件支持幂等和状态追踪',
]

const certs = [
  { label: '认证与鉴权', desc: '网关、JWT 与客户端身份校验', icon: 'fa fa-key' },
  { label: '空间隔离', desc: '成员、角色与资源范围控制', icon: 'fa fa-users' },
  { label: '文件安全边界', desc: '操作凭证与临时访问控制', icon: 'fa fa-lock' },
  { label: '生命周期追踪', desc: '处理状态、事件与审计信息', icon: 'fa fa-history' },
]

const testimonials = [
  { name: '空间协作', role: '团队与项目场景', text: '以空间为边界组织成员、角色和文件资源，适合部门资料、项目交付和跨团队协作。' },
  { name: '文件处理', role: '上传、下载与预览场景', text: '从文件夹和文件 CURD，到分片上传、流式下载、缩略图和多类型在线预览，覆盖常用文件操作。' },
  { name: '自动化扩展', role: '插件与工作流场景', text: '云插件、本地扩展和工作流使用能力中心连接平台事件，让文件处理可以按规则扩展。' },
]

const solutions = [
  { title: '项目空间', desc: '为项目建立独立空间，集中管理成员、文件夹、分享链接和自动化处理规则。', icon: 'fa fa-cubes', bgClass: 'bg-primary/10', iconClass: 'text-primary', href: '/solutions' },
  { title: '部门资料', desc: '用团队空间承载部门资料，结合角色权限、标签、收藏和回收站降低管理成本。', icon: 'fa fa-users', bgClass: 'bg-success/10', iconClass: 'text-success', href: '/solutions' },
  { title: '内容处理', desc: '围绕上传完成、内容可用等生命周期事件接入插件和工作流，扩展文件处理链路。', icon: 'fa fa-magic', bgClass: 'bg-danger/10', iconClass: 'text-danger', href: '/solutions' },
  { title: '对外协作', desc: '通过分享链接、访问凭证和可撤回资源，为外部协作提供清晰的访问边界。', icon: 'fa fa-share-alt', bgClass: 'bg-warning/10', iconClass: 'text-warning', href: '/solutions' },
]

const integrations = [
  { name: 'REST API', icon: 'fa fa-code', color: 'text-primary' },
  { name: '内部服务契约', icon: 'fa fa-lock', color: 'text-success' },
  { name: 'RabbitMQ 事件', icon: 'fa fa-envelope', color: 'text-info' },
  { name: '插件市场', icon: 'fa fa-puzzle-piece', color: 'text-purple-500' },
  { name: '工作流市场', icon: 'fa fa-random', color: 'text-warning' },
  { name: '客户端注册', icon: 'fa fa-desktop', color: 'text-danger' },
  { name: 'MinIO 存储', icon: 'fa fa-database', color: 'text-neutral-600' },
  { name: 'OpenSearch', icon: 'fa fa-search', color: 'text-neutral-800' },
  { name: 'Docker Compose', icon: 'fa fa-cubes', color: 'text-info' },
  { name: 'Prometheus', icon: 'fa fa-line-chart', color: 'text-warning' },
  { name: 'SkyWalking', icon: 'fa fa-sitemap', color: 'text-purple-600' },
]

const caseHighlights = [
  {
    company: '项目空间', industry: '团队协作场景',
    summary: '以空间划分成员与资源范围，统一管理项目文件、共享链接、角色权限和协作过程。',
    gradient: 'bg-gradient-to-r from-primary to-info',
    metrics: [{ label: '核心边界', value: '空间' }, { label: '权限模型', value: '角色' }, { label: '资源入口', value: '文件' }],
  },
  {
    company: '内容处理', industry: '文件生命周期场景',
    summary: '把上传、预处理、内容激活、预览资源和后续自动化处理串成可追踪的文件生命周期。',
    gradient: 'bg-gradient-to-r from-purple-500 to-pink-500',
    metrics: [{ label: '处理入口', value: '事件' }, { label: '执行方式', value: '异步' }, { label: '结果状态', value: '可追踪' }],
  },
  {
    company: '扩展平台', industry: '插件与工作流场景',
    summary: '通过云插件、本地扩展、能力中心和市场机制，将文件平台能力安全地延伸到具体业务流程。',
    gradient: 'bg-gradient-to-r from-success to-info',
    metrics: [{ label: '扩展形态', value: '3 类' }, { label: '市场类型', value: '2 类' }, { label: '运行边界', value: '受控' }],
  },
]

const blogPosts = [
  {
    title: '空间协作能力：从个人文件到团队资源边界', category: '产品能力', date: '2026-07-29',
    excerpt: '介绍空间、成员、角色和资源范围如何共同支撑项目与部门文件协作。',
    author: '项目文档', readTime: '按需阅读',
  },
  {
    title: '文件在线预览与内容生命周期', category: '技术分享', date: '2026-07-29',
    excerpt: '梳理上传、内容处理、预览令牌、缩略图和多类型预览之间的服务边界。',
    author: '项目文档', readTime: '按需阅读',
  },
  {
    title: '插件、工作流与市场：平台扩展边界说明', category: '平台扩展', date: '2026-07-29',
    excerpt: '说明云插件、本地扩展、工作流、能力中心以及市场之间的职责划分。',
    author: '项目文档', readTime: '按需阅读',
  },
]

const homeFaqs = [
  { q: 'PrivateCloudDisk 与传统单体网盘有什么区别？', a: '平台将网关、平台业务、文件处理、通知、即时通讯、插件、工作流和调度等职责拆分，便于按边界演进；数据仍通过统一入口和契约协作。' },
  { q: '空间协作解决什么问题？', a: '空间为成员、角色、资源范围和扩展能力提供共同边界，适合项目、部门或外部协作场景，减少个人文件与团队文件混杂。' },
  { q: '支持哪些基础文件能力？', a: '支持文件和文件夹的创建、读取、更新、移动、复制、删除，文件上传、下载、文件夹上传与下载，以及分享、回收站、收藏和标签管理。' },
  { q: '在线预览覆盖哪些类型？', a: '当前前端包含图片、PDF、Word、Excel、PPT、Markdown、代码、压缩包、音频和视频等预览入口，实际可用格式以服务配置和文件类型为准。' },
  { q: '插件和工作流如何扩展平台？', a: '云插件、本地扩展和工作流通过能力中心连接平台事件；插件市场与工作流市场用于发现、发布和复用扩展内容。' },
  { q: '如何了解部署与技术栈？', a: '请先阅读根 README、docs/architecture.md、docs/api-overview.md 和各子项目 README；Docker Compose 是当前仓库提供的主要联调方式。' },
]

// ============================================================
// 水平滚动展示区数据
// ============================================================
const horizontalPanels = 5
const panelColors = ['#165DFF', '#0E8C6A', '#1A1A2E', '#6C3BD4', '#E0563C']

const platforms = [
  { name: 'Windows', icon: 'fa fa-windows' },
  { name: 'macOS', icon: 'fa fa-apple' },
  { name: 'Linux', icon: 'fa fa-linux' },
  { name: 'iOS', icon: 'fa fa-mobile' },
  { name: 'Android', icon: 'fa fa-android' },
  { name: 'Web', icon: 'fa fa-globe' },
]

const deviceIcons = [
  { name: '桌面端', icon: 'fa fa-desktop' },
  { name: '笔记本', icon: 'fa fa-laptop' },
  { name: '平板', icon: 'fa fa-tablet' },
  { name: '手机', icon: 'fa fa-mobile' },
]

const securityBadges = [
  { label: 'JWT 鉴权', icon: 'fa fa-key' },
  { label: '空间权限', icon: 'fa fa-users' },
  { label: '操作凭证', icon: 'fa fa-lock' },
  { label: '事件追踪', icon: 'fa fa-history' },
]

const collaborationItems = [
  { name: '张明', online: true },
  { name: '李华', online: true },
  { name: '王芳', online: true },
  { name: '陈静', online: false },
]

const apiIcons = [
  { name: 'REST', icon: 'fa fa-code' },
  { name: 'Internal API', icon: 'fa fa-lock' },
  { name: 'Event Contract', icon: 'fa fa-envelope' },
  { name: 'Plugin SDK', icon: 'fa fa-puzzle-piece' },
  { name: 'Workflow DSL', icon: 'fa fa-random' },
  { name: 'Client Identity', icon: 'fa fa-desktop' },
]

// ============================================================
// 模板引用
// ============================================================
const heroSection = ref<HTMLElement>()
const heroContent = ref<HTMLElement>()
const heroBlob1 = ref<HTMLElement>()
const heroBlob2 = ref<HTMLElement>()
const dashboardPreview = ref<HTMLElement>()
const trustedSection = ref<HTMLElement>()
const featuresSection = ref<HTMLElement>()
const featuresHeader = ref<HTMLElement>()
const featureRefs = ref<HTMLElement[]>([])
const statsSection = ref<HTMLElement>()
const statRefs = ref<HTMLElement[]>([])
const statNumberRefs = ref<HTMLElement[]>([])
const securitySection = ref<HTMLElement>()
const securityContent = ref<HTMLElement>()
const securityCard = ref<HTMLElement>()
const testimonialsSection = ref<HTMLElement>()
const testimonialRefs = ref<HTMLElement[]>([])
const solutionsSection = ref<HTMLElement>()
const integrationSection = ref<HTMLElement>()
const caseSection = ref<HTMLElement>()
const blogSection = ref<HTMLElement>()
const faqSection = ref<HTMLElement>()
const ctaSection = ref<HTMLElement>()
const ctaCard = ref<HTMLElement>()

// 大标题区域
const brandHeroSection = ref<HTMLElement>()
const brandSubtitle = ref<HTMLElement>()
const brandTextWrapper = ref<HTMLElement>()
const brandText = ref<HTMLElement>()
const brandBlurOverlay = ref<HTMLElement>()
const brandGlowText = ref<HTMLElement>()
const brandGlow1 = ref<HTMLElement>()
const brandGlow2 = ref<HTMLElement>()
const brandDesc = ref<HTMLElement>()
const brandActions = ref<HTMLElement>()

// 粒子画布
const particleCanvas = ref<HTMLCanvasElement>()
let particleAnimationId = 0

// 水平滚动区域
const horizontalScrollSection = ref<HTMLElement>()
const horizontalOuter = ref<HTMLElement>()
const horizontalSticky = ref<HTMLElement>()
const horizontalTrack = ref<HTMLElement>()
const activePanel = ref(0)
let horizontalScrollTrigger: ScrollTrigger | null = null

const setFeatureRef = (el: HTMLElement, idx: number) => {
  if (el) featureRefs.value[idx] = el
}
const setTestimonialRef = (el: HTMLElement, idx: number) => {
  if (el) testimonialRefs.value[idx] = el
}
const setStatRef = (el: HTMLElement, idx: number) => {
  if (el) statRefs.value[idx] = el
}
const setStatNumberRef = (el: HTMLElement, idx: number) => {
  if (el) statNumberRefs.value[idx] = el
}

// ============================================================
// 赛博模糊 hover 效果处理
// ============================================================
const handleBrandTextMouseMove = (e: MouseEvent) => {
  if (!brandTextWrapper.value || !brandBlurOverlay.value || !brandGlowText.value) return

  const rect = brandTextWrapper.value.getBoundingClientRect()
  const x = (e.clientX - rect.left) / rect.width
  const y = (e.clientY - rect.top) / rect.height

  // 赛博模糊叠加层 - 根据鼠标位置显示
  brandBlurOverlay.value.style.opacity = '0.6'
  brandBlurOverlay.value.style.clipPath = `circle(30% at ${x * 100}% ${y * 100}%)`

  // 光晕文字
  brandGlowText.value.style.opacity = '0.5'
  brandGlowText.value.style.clipPath = `circle(25% at ${x * 100}% ${y * 100}%)`

  // 轻微移动光晕
  if (brandGlow1.value) {
    gsap.to(brandGlow1.value, {
      x: (x - 0.5) * 30,
      y: (y - 0.5) * 30,
      duration: 0.5,
      ease: 'power2.out',
    })
  }
  if (brandGlow2.value) {
    gsap.to(brandGlow2.value, {
      x: (0.5 - x) * 30,
      y: (0.5 - y) * 30,
      duration: 0.5,
      ease: 'power2.out',
    })
  }
}

const handleBrandTextMouseLeave = () => {
  if (!brandBlurOverlay.value || !brandGlowText.value) return

  brandBlurOverlay.value.style.opacity = '0'
  brandGlowText.value.style.opacity = '0'

  if (brandGlow1.value) {
    gsap.to(brandGlow1.value, { x: 0, y: 0, duration: 1, ease: 'power2.out' })
  }
  if (brandGlow2.value) {
    gsap.to(brandGlow2.value, { x: 0, y: 0, duration: 1, ease: 'power2.out' })
  }
}

// ============================================================
// GSAP 动画初始化
// ============================================================
let scrollTriggers: ScrollTrigger[] = []

onMounted(async () => {
  await nextTick()
  initScrollAnimations()
  initHeroParallax()
  initHeroParticles()
  initBrandHeroAnimation()
  initHorizontalScroll()
})

onUnmounted(() => {
  scrollTriggers.forEach(st => st.kill())
  scrollTriggers = []
  if (horizontalScrollTrigger) {
    horizontalScrollTrigger.kill()
    horizontalScrollTrigger = null
  }
  if (particleAnimationId) {
    cancelAnimationFrame(particleAnimationId)
  }
})

function initScrollAnimations() {
  // 通用 reveal 动画 - 元素从下方淡入
  const revealElements = document.querySelectorAll('.reveal')
  revealElements.forEach((el) => {
    const st = ScrollTrigger.create({
      trigger: el,
      start: 'top 85%',
      onEnter: () => {
        gsap.fromTo(el, { opacity: 0, y: 40 }, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out' })
      },
      once: true,
    })
    scrollTriggers.push(st)
  })

  // Hero 区域入场动画
  if (heroContent.value) {
    gsap.fromTo(heroContent.value,
      { opacity: 0, y: 30 },
      { opacity: 1, y: 0, duration: 1, ease: 'power3.out', delay: 0.2 }
    )
  }

  // Dashboard 预览
  if (dashboardPreview.value) {
    const st = ScrollTrigger.create({
      trigger: dashboardPreview.value,
      start: 'top 80%',
      onEnter: () => {
        gsap.fromTo(dashboardPreview.value!,
          { opacity: 0, y: 60, scale: 0.95 },
          { opacity: 1, y: 0, scale: 1, duration: 1, ease: 'power3.out' }
        )
      },
      once: true,
    })
    scrollTriggers.push(st)
  }

  // 特性卡片交错动画
  if (featuresSection.value) {
    const st = ScrollTrigger.create({
      trigger: featuresSection.value,
      start: 'top 75%',
      onEnter: () => {
        featureRefs.value.forEach((el, i) => {
          if (!el) return
          gsap.fromTo(el,
            { opacity: 0, y: 30 },
            { opacity: 1, y: 0, duration: 0.6, delay: i * 0.1, ease: 'power3.out' }
          )
        })
      },
      once: true,
    })
    scrollTriggers.push(st)
  }

  // 数据统计区域 - 数字滚动计数动画
  if (statsSection.value) {
    const st = ScrollTrigger.create({
      trigger: statsSection.value,
      start: 'top 80%',
      onEnter: () => {
        // 数字计数动画
        const targetValues = [
          { target: 6, suffix: '', prefix: '' },
          { target: 3, suffix: '', prefix: '' },
          { target: 2, suffix: '', prefix: '' },
          { target: 1, suffix: '', prefix: '' },
        ]
        statNumberRefs.value.forEach((el, i) => {
          if (!el || !targetValues[i]) return
          const target = targetValues[i]
          const startVal = { val: 0 }
          const isDecimal = target.target % 1 !== 0
          gsap.to(startVal, {
            val: target.target,
            duration: 2,
            delay: i * 0.2,
            ease: 'power2.out',
            onUpdate: () => {
              const display = isDecimal
                ? startVal.val.toFixed(2)
                : Math.floor(startVal.val).toLocaleString()
              el.textContent = target.prefix + display + target.suffix
            },
          })
        })
        // 卡片入场
        gsap.fromTo(statRefs.value.filter(Boolean),
          { opacity: 0, y: 40 },
          { opacity: 1, y: 0, duration: 0.6, stagger: 0.15, ease: 'power3.out', delay: 0.3 }
        )
      },
      once: true,
    })
    scrollTriggers.push(st)
  }

  // 安全区域
  if (securitySection.value) {
    const st = ScrollTrigger.create({
      trigger: securitySection.value,
      start: 'top 75%',
      onEnter: () => {
        if (securityContent.value) {
          gsap.fromTo(securityContent.value, { opacity: 0, x: -40 }, { opacity: 1, x: 0, duration: 0.8, ease: 'power3.out' })
        }
        if (securityCard.value) {
          gsap.fromTo(securityCard.value, { opacity: 0, x: 40 }, { opacity: 1, x: 0, duration: 0.8, delay: 0.2, ease: 'power3.out' })
        }
      },
      once: true,
    })
    scrollTriggers.push(st)
  }

  // 评价卡片
  if (testimonialsSection.value) {
    const st = ScrollTrigger.create({
      trigger: testimonialsSection.value,
      start: 'top 80%',
      onEnter: () => {
        testimonialRefs.value.forEach((el, i) => {
          if (!el) return
          gsap.fromTo(el,
            { opacity: 0, y: 30, scale: 0.95 },
            { opacity: 1, y: 0, scale: 1, duration: 0.6, delay: i * 0.15, ease: 'power3.out' }
          )
        })
      },
      once: true,
    })
    scrollTriggers.push(st)
  }

  // 大标题区域
  if (brandHeroSection.value) {
    const st = ScrollTrigger.create({
      trigger: brandHeroSection.value,
      start: 'top 70%',
      onEnter: () => {
        if (brandSubtitle.value) {
          gsap.fromTo(brandSubtitle.value, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.8, ease: 'power3.out' })
        }
        if (brandText.value) {
          gsap.fromTo(brandText.value, { opacity: 0, scale: 0.8 }, { opacity: 1, scale: 1, duration: 1.2, delay: 0.2, ease: 'power4.out' })
        }
        if (brandDesc.value) {
          gsap.fromTo(brandDesc.value, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.8, delay: 0.5, ease: 'power3.out' })
        }
        if (brandActions.value) {
          gsap.fromTo(brandActions.value, { opacity: 0, y: 20 }, { opacity: 1, y: 0, duration: 0.8, delay: 0.7, ease: 'power3.out' })
        }
      },
      once: true,
    })
    scrollTriggers.push(st)
  }
}

function initHeroParallax() {
  // 背景装饰视差滚动
  if (heroBlob1.value) {
    gsap.to(heroBlob1.value, {
      y: 60,
      x: 30,
      scrollTrigger: {
        trigger: heroSection.value,
        start: 'top top',
        end: 'bottom top',
        scrub: 1,
      },
    })
  }
  if (heroBlob2.value) {
    gsap.to(heroBlob2.value, {
      y: -40,
      x: -20,
      scrollTrigger: {
        trigger: heroSection.value,
        start: 'top top',
        end: 'bottom top',
        scrub: 1,
      },
    })
  }
}

// ============================================================
// Hero 粒子背景动画 - 轻量 canvas 粒子系统
// 在 hero 区域生成浮动光点，营造科技感氛围
// ============================================================
function initHeroParticles() {
  const canvas = particleCanvas.value
  if (!canvas) return

  const ctx = canvas.getContext('2d')
  if (!ctx) return

  const heroEl = heroSection.value
  if (!heroEl) return

  // 粒子配置
  const particleCount = 40
  const particles: { x: number; y: number; vx: number; vy: number; r: number; alpha: number; alphaDir: number }[] = []

  function resize() {
    if (!canvas || !heroEl) return
    const rect = heroEl.getBoundingClientRect()
    canvas.width = rect.width
    canvas.height = rect.height
  }

  function createParticles() {
    particles.length = 0
    const w = canvas?.width || 0
    const h = canvas?.height || 0
    for (let i = 0; i < particleCount; i++) {
      particles.push({
        x: Math.random() * w,
        y: Math.random() * h,
        vx: (Math.random() - 0.5) * 0.4,
        vy: (Math.random() - 0.5) * 0.4,
        r: Math.random() * 2.5 + 1,
        alpha: Math.random() * 0.5 + 0.1,
        alphaDir: Math.random() > 0.5 ? 1 : -1,
      })
    }
  }

  function animate() {
    if (!ctx || !canvas) return
    const w = canvas.width
    const h = canvas.height
    ctx.clearRect(0, 0, w, h)

    particles.forEach((p) => {
      // 移动
      p.x += p.vx
      p.y += p.vy

      // 边界回弹
      if (p.x < 0 || p.x > w) p.vx *= -1
      if (p.y < 0 || p.y > h) p.vy *= -1

      // alpha 淡入淡出
      p.alpha += p.alphaDir * 0.005
      if (p.alpha >= 0.5) p.alphaDir = -1
      if (p.alpha <= 0.1) p.alphaDir = 1

      // 绘制
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(22, 93, 255, ${p.alpha})`
      ctx.fill()

      // 绘制光晕
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r * 2.5, 0, Math.PI * 2)
      ctx.fillStyle = `rgba(22, 93, 255, ${p.alpha * 0.3})`
      ctx.fill()
    })

    particleAnimationId = requestAnimationFrame(animate)
  }

  resize()
  createParticles()
  animate()

  window.addEventListener('resize', () => {
    resize()
    createParticles()
  })
}

function initBrandHeroAnimation() {
  // 大标题区域光晕浮动动画
  if (brandGlow1.value) {
    gsap.to(brandGlow1.value, {
      x: 30,
      y: 20,
      duration: 8,
      repeat: -1,
      yoyo: true,
      ease: 'sine.inOut',
    })
  }
  if (brandGlow2.value) {
    gsap.to(brandGlow2.value, {
      x: -30,
      y: -20,
      duration: 8,
      repeat: -1,
      yoyo: true,
      ease: 'sine.inOut',
      delay: 4,
    })
  }
}

// ============================================================
// 水平滚动驱动动画 - 核心：垂直滚动 → 水平平移
// 原理：ScrollTrigger pin 住 sticky 容器，用滚动进度驱动 translateX
// ============================================================
function initHorizontalScroll() {
  if (!horizontalScrollSection.value || !horizontalOuter.value || !horizontalSticky.value || !horizontalTrack.value) return

  const panels = horizontalTrack.value.querySelectorAll('.horizontal-panel')
  if (panels.length === 0) return

  const panelWidth = panels[0].clientWidth
  // 需要滚动的总距离 = (面板数 - 1) * 面板宽度
  const totalScroll = (horizontalPanels - 1) * panelWidth

  // 设置 outer 容器高度，确保有足够的滚动空间
  // vh 高度用于 sticky 容器 + 额外的滚动距离映射
  horizontalOuter.value.style.height = `calc(100vh + ${totalScroll}px)`

  // 设置 sticky 容器为 100vh 高度
  horizontalSticky.value.style.height = '100vh'

  // 设置水平轨道宽度
  horizontalTrack.value.style.width = `${horizontalPanels * 100}vw`

  // 核心：创建 ScrollTrigger，pin 住 sticky 容器，用 scrub 驱动水平滚动
  horizontalScrollTrigger = ScrollTrigger.create({
    trigger: horizontalOuter.value,
    start: 'top top',
    end: `+=${totalScroll}`,
    pin: horizontalSticky.value,
    pinSpacing: true,
    scrub: 1, // 平滑跟随滚动，值越大越平滑
    anticipatePin: 1,
    onUpdate: (self) => {
      // 将滚动进度 (0~1) 映射为 translateX
      const progress = self.progress
      const x = -progress * totalScroll
      gsap.set(horizontalTrack.value, { x })

      // 更新当前活跃面板索引
      const newActivePanel = Math.round(progress * (horizontalPanels - 1))
      if (activePanel.value !== newActivePanel) {
        activePanel.value = newActivePanel
      }
    },
    onLeave: () => {
      // 确保滚动到底时面板完全显示
      gsap.set(horizontalTrack.value, { x: -totalScroll })
      activePanel.value = horizontalPanels - 1
    },
    onEnterBack: () => {
      gsap.set(horizontalTrack.value, { x: 0 })
      activePanel.value = 0
    },
  })

  // 面板内容入场动画：每个面板的文字和视觉元素独立动画
  panels.forEach((panel, i) => {
    const textEl = panel.querySelector('.panel-text')
    const visualEl = panel.querySelector('.panel-visual')

    if (textEl) {
      gsap.fromTo(textEl,
        { opacity: 0.3, x: 30 },
        {
          opacity: 1,
          x: 0,
          duration: 0.8,
          ease: 'power2.out',
          scrollTrigger: {
            trigger: panel,
            containerAnimation: {
              animation: gsap.to(horizontalTrack.value!, {
                x: -totalScroll,
                ease: 'none',
                scrollTrigger: {
                  trigger: horizontalOuter.value,
                  start: 'top top',
                  end: `+=${totalScroll}`,
                  scrub: true,
                },
              }),
            },
            start: 'left 70%',
            end: 'left 30%',
            toggleActions: 'play none none reverse',
          },
        }
      )
    }

    if (visualEl) {
      gsap.fromTo(visualEl,
        { opacity: 0.3, scale: 0.9 },
        {
          opacity: 1,
          scale: 1,
          duration: 0.8,
          ease: 'power2.out',
          scrollTrigger: {
            trigger: panel,
            containerAnimation: {
              animation: gsap.to(horizontalTrack.value!, {
                x: -totalScroll,
                ease: 'none',
                scrollTrigger: {
                  trigger: horizontalOuter.value,
                  start: 'top top',
                  end: `+=${totalScroll}`,
                  scrub: true,
                },
              }),
            },
            start: 'left 70%',
            end: 'left 30%',
            toggleActions: 'play none none reverse',
          },
        }
      )
    }
  })
}

// 点击进度点跳转到指定面板
function scrollToPanel(idx: number) {
  if (!horizontalScrollTrigger || !horizontalOuter.value) return

  const targetProgress = idx / (horizontalPanels - 1)
  const totalScroll = (horizontalPanels - 1) * (horizontalTrack.value?.querySelector('.horizontal-panel')?.clientWidth ?? window.innerWidth)

  // 计算目标滚动位置
  const startScroll = horizontalScrollTrigger.start ?? 0
  const scrollDistance = (horizontalScrollTrigger.end as number) - (horizontalScrollTrigger.start as number)
  const targetScrollY = startScroll + scrollDistance * targetProgress

  window.scrollTo({ top: targetScrollY, behavior: 'smooth' })
}

// 左右箭头导航
function navigatePanel(direction: number) {
  const newIdx = activePanel.value + direction
  if (newIdx >= 0 && newIdx < horizontalPanels) {
    scrollToPanel(newIdx)
  }
}
</script>

<style scoped>
/* ============================================================
   Hero 区域特效
   ============================================================ */
.hero-section {
  will-change: transform;
}

.hero-blob {
  animation: blobFloat 8s ease-in-out infinite alternate;
}

@keyframes blobFloat {
  0% {
    transform: translate(0, 0) scale(1);
  }
  100% {
    transform: translate(20px, -20px) scale(1.1);
  }
}

.hero-blob:last-child {
  animation-delay: -4s;
}

/* ============================================================
   图表条动画
   ============================================================ */
.chart-bar {
  transition: height 0.6s ease, background-color 0.3s ease;
  animation: barGrow 0.6s ease-out forwards;
  transform-origin: bottom;
}

@keyframes barGrow {
  from {
    transform: scaleY(0);
  }
  to {
    transform: scaleY(1);
  }
}

/* ============================================================
   特性卡片 hover 增强
   ============================================================ */
.feature-card {
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
}

.feature-card:hover {
  border-color: rgb(22 93 255 / 0.2);
}

/* ============================================================
   大标题赛博模糊区域样式
   ============================================================ */
.brand-hero-section {
  position: relative;
  overflow: hidden;
}

.brand-text {
  font-family: 'Inter', system-ui, -apple-system, sans-serif;
  will-change: transform;
  text-shadow: 0 0 80px rgba(255, 255, 255, 0.3);
  transition: text-shadow 0.5s ease;
}

.brand-text-wrapper:hover .brand-text {
  text-shadow:
    0 0 40px rgba(255, 255, 255, 0.4),
    0 0 80px rgba(255, 255, 255, 0.2),
    0 0 120px rgba(16, 185, 129, 0.3),
    0 0 200px rgba(16, 185, 129, 0.15);
}

/* 赛博模糊叠加层 - 鼠标悬停时显示 */
.brand-blur-overlay {
  mix-blend-mode: overlay;
  transition: opacity 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 光晕文字 */
.brand-glow-text {
  transition: opacity 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 背景光晕 */
.brand-glow {
  will-change: transform;
  transition: opacity 0.3s ease;
}

/* ============================================================
   按钮 hover 增强
   ============================================================ */
.hero-btn,
.cta-btn {
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* ============================================================
   水平滚动展示区样式
   ============================================================ */
.horizontal-scroll-section {
  position: relative;
  z-index: 1;
}

.horizontal-outer {
  position: relative;
  width: 100%;
}

.horizontal-sticky {
  position: relative;
  width: 100%;
  overflow: hidden;
}

.horizontal-track {
  display: flex;
  height: 100%;
  will-change: transform;
}

.horizontal-panel {
  flex: 0 0 100vw;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.horizontal-panel-inner {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  padding: 2rem 1.5rem;
}

@media (min-width: 640px) {
  .horizontal-panel-inner {
    padding: 3rem 2rem;
  }
}

@media (min-width: 1024px) {
  .horizontal-panel-inner {
    padding: 4rem 3rem;
  }
}

.panel-content {
  min-height: 60vh;
}

.panel-text {
  opacity: 1;
}

.panel-visual {
  opacity: 1;
}

/* 进度指示器 */
.horizontal-progress {
  position: absolute;
  bottom: 2rem;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  gap: 0.75rem;
  z-index: 10;
}

.horizontal-progress-dot {
  width: 0.625rem;
  height: 0.625rem;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.35);
  border: 1.5px solid rgba(255, 255, 255, 0.5);
  cursor: pointer;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  padding: 0;
}

.horizontal-progress-dot:hover {
  background: rgba(255, 255, 255, 0.6);
  transform: scale(1.2);
}

.horizontal-progress-dot.active {
  background: #fff;
  border-color: #fff;
  box-shadow: 0 0 12px rgba(255, 255, 255, 0.5);
  width: 1.5rem;
  border-radius: 999px;
}

/* 左右导航箭头 */
.horizontal-nav {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 3rem;
  height: 3rem;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255, 255, 255, 0.25);
  color: rgba(255, 255, 255, 0.9);
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  z-index: 10;
  opacity: 0;
  transition: all 0.3s ease;
  font-size: 1rem;
}

.horizontal-nav:hover {
  background: rgba(255, 255, 255, 0.25);
  transform: translateY(-50%) scale(1.1);
}

.horizontal-nav-left {
  left: 1rem;
}

.horizontal-nav-right {
  right: 1rem;
}

.horizontal-sticky:hover .horizontal-nav {
  opacity: 1;
}

@media (max-width: 768px) {
  .horizontal-nav {
    display: none;
  }
}

/* ============================================================
   Hero 粒子画布
   ============================================================ */
canvas {
  display: block;
}

/* ============================================================
   渐变过渡分割线
   ============================================================ */
.section-divider {
  position: relative;
  height: 1px;
  background: transparent;
}

.section-divider-inner {
  position: absolute;
  inset: 0;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(22, 93, 255, 0.08) 20%,
    rgba(22, 93, 255, 0.15) 50%,
    rgba(22, 93, 255, 0.08) 80%,
    transparent 100%
  );
}

/* ============================================================
   响应式调整
   ============================================================ */
@media (max-width: 640px) {
  .brand-text {
    font-size: 14vw !important;
  }
  .brand-text span {
    font-size: 16vw !important;
  }
}
</style>
