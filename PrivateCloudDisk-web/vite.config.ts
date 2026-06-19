// ============================================================
// Vite 构建配置 — 企业级生产部署优化
// ============================================================
// 生产构建 (vite build) 策略：
//   JS/TS  — Terser 压缩，去除所有注释（含 /*! 许可注释），
//            混淆变量名，移除 console/debugger，移除纯函数调用副作用标记
//   CSS    — esbuild 原生压缩（默认去除 CSS 注释），
//            结合 cssnano 深度优化（calc 简化、颜色压缩、z-index 重排）
//   Vue    — 模板编译内联 preload 提示，SFC 中 JS/CSS 同上
//   静态资源 — Gzip + Brotli 预压缩，生成 .gz / .br 文件供 Nginx 静态服务
//   代码分割 — 按框架(vendor-vue)、UI库(vendor-element)、工具(vendor-utils)分层
//   输出     — 文件名带 hash 用于缓存破坏，入口文件 index.html 不缓存
//
// 开发环境 (vite dev)：
//   保留所有注释、sourcemap、不压缩，便于调试
// ============================================================

import { fileURLToPath, URL } from 'node:url'
import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import viteCompression from 'vite-plugin-compression'
import JavaScriptObfuscator from 'javascript-obfuscator'
import cssnano from 'cssnano'

export default defineConfig(({ command, mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiProxyTarget = env.VITE_API_PROXY_TARGET || 'http://127.0.0.1:8080'
  const isDevelopment = command === 'serve'
  const isProduction = command === 'build'

  return {
    // ============================================================
    // 插件
    // ============================================================
    plugins: [
      vue({
        // Vue SFC 编译优化
        template: {
          compilerOptions: {
            // 生产环境移除 HTML 注释
            comments: !isProduction,
          },
        },
      }),

      // 开发工具仅在开发环境启用
      isDevelopment && vueDevTools(),

      // === Gzip 预压缩（生产环境） ===
      // 生成 .gz 文件，Nginx 配置 gzip_static on 即可直接返回
      isProduction &&
        viteCompression({
          algorithm: 'gzip',
          ext: '.gz',
          threshold: 1024, // 小于 1KB 的文件不压缩
          deleteOriginFile: false, // 保留原始文件
        }),

      // === Brotli 预压缩（生产环境） ===
      // 生成 .br 文件，比 gzip 压缩率高 15-25%
      // Nginx 需安装 ngx_brotli 模块
      isProduction &&
        viteCompression({
          algorithm: 'brotliCompress',
          ext: '.br',
          threshold: 1024,
          deleteOriginFile: false,
        }),

      // === 代码混淆（生产环境，仅 crypto 加密模块） ===
      // 自定义 Vite 插件：在 transform 阶段对 crypto.ts 进行 AST 级深度混淆。
      // 工作在 Rollup 打包之前，确保混淆后的模块仍能被 Rollup 正确解析
      // import/export 关系，避免跨 chunk 引用被破坏。
      isProduction &&
        (function vitePluginObfuscateCrypto() {
          const cryptoModuleRE = /[\\/]src[\\/]utils[\\/]crypto\.ts$/
          return {
            name: 'vite-plugin-obfuscate-crypto',
            enforce: 'post' as const,
            // 仅处理生产构建
            apply: 'build' as const,
            transform(code: string, id: string) {
              // 仅混淆 crypto.ts 模块
              if (!cryptoModuleRE.test(id)) return null

              try {
                const result = JavaScriptObfuscator.obfuscate(code, {
                  compact: true,
                  // 控制流扁平化：将 if/else/for 等结构化控制流转换为
                  // switch-case，使代码执行路径难以静态分析
                  controlFlowFlattening: true,
                  controlFlowFlatteningThreshold: 0.75,
                  // 死代码注入：在函数中随机插入无用代码块，增加阅读难度
                  deadCodeInjection: true,
                  deadCodeInjectionThreshold: 0.4,
                  // 字符串数组编码：所有字符串字面量存入单一数组，通过索引引用，
                  // 彻底消除可搜索的字符串常量（如 pepper 片段、密钥前缀等）
                  stringArray: true,
                  stringArrayThreshold: 1, // 编码所有字符串
                  stringArrayEncoding: ['rc4'], // RC4 加密字符串数组
                  stringArrayIndexShift: true, // 索引偏移
                  stringArrayRotate: true, // 数组旋转
                  stringArrayShuffle: true, // 数组打乱
                  stringArrayCallsTransform: true, // 字符串数组调用变换
                  stringArrayCallsTransformThreshold: 0.5,
                  stringArrayWrappersCount: 1,
                  stringArrayWrappersChainedCalls: true,
                  // 拆分字符串字面量，进一步消除可搜索字符串
                  splitStrings: true,
                  splitStringsChunkLength: 10,
                  // 自防御：检测代码是否被格式化/篡改，触发后使代码无法运行
                  selfDefending: true,
                  // 调试保护：检测 DevTools 打开，触发 debugger 循环
                  debugProtection: true,
                  debugProtectionInterval: 2000,
                  // 禁用 console 输出
                  disableConsoleOutput: true,
                  // 标识符名生成器：hexadecimal → 无意义的十六进制名称
                  identifierNamesGenerator: 'hexadecimal',
                  // 不重命名全局变量（避免破坏 Web Crypto API 引用）
                  renameGlobals: false,
                  // 保留导出函数名，确保其他模块可以正常 import
                  reservedNames: [
                    'hashPasswordForTransport',
                    'pbkdf2HashPassword',
                    'hmacSign',
                    'aesEncrypt',
                    'aesDecrypt',
                    'generateRandomBytes',
                    'generateRandomHex',
                    'generateRandomBase64',
                    'evaluatePasswordStrength',
                    'getPasswordStrengthLabel',
                    'PASSWORD_MIN_LENGTH',
                    'PASSWORD_MAX_LENGTH',
                    'PasswordStrength',
                  ],
                  // 转换对象键名
                  transformObjectKeys: true,
                  // 简化表达式
                  simplify: true,
                  // 数字转表达式（如 1 → 0x1）
                  numbersToExpressions: false,
                  // 不转换 Unicode 转义序列
                  unicodeEscapeSequence: false,
                  // 不生成 source map（避免泄露原始代码结构）
                  sourceMap: false,
                })

                return {
                  code: result.getObfuscatedCode(),
                  map: null,
                }
              } catch (e) {
                console.error('Failed to obfuscate crypto.ts:', e)
                return null // 混淆失败时回退到原始代码
              }
            },
          }
        })(),
    ].filter(Boolean),

    // ============================================================
    // 路径别名
    // ============================================================
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },

    // ============================================================
    // 开发服务器
    // ============================================================
    server: {
      host: '0.0.0.0',
      port: 5500,
      proxy: {
        '/api': {
          target: apiProxyTarget,
          changeOrigin: true,
        },
      },
    },

    // ============================================================
    // CSS 处理
    // ============================================================
    css: {
      // 生产环境启用 cssnano 深度优化
      postcss: isProduction
        ? {
            plugins: [
              cssnano({
                preset: [
                  'default',
                  {
                    // 去除所有 CSS 注释（包括 /*! 重要注释 */）
                    discardComments: { removeAll: true },
                    // 合并相邻的 @ 规则
                    mergeRules: true,
                    // 合并长时间运行的选择器
                    mergeLonghand: true,
                    // 规范化属性值（如颜色值统一为最短形式）
                    colormin: true,
                    // 简化 calc() 表达式
                    calc: true,
                    // 去除空的 @ 规则
                    discardEmpty: true,
                    // 规范化 z-index 值
                    zindex: true,
                    // 压缩字体权重表示
                    minifyFontValues: true,
                    // 压缩渐变表示
                    minifyGradients: true,
                    // 规范化选择器
                    normalizeWhitespace: true,
                    // 排序 CSS 声明以提升 gzip 压缩率
                    cssDeclarationSorter: { order: 'alphabetical' },
                    // 合并 @font-face 中相同字体族的规则
                    mergeFontFace: true,
                    // 转换颜色函数为更短形式
                    convertColors: true,
                    // 去除不必要的转义字符
                    normalizeCharset: true,
                    // 去除重复的 @charset 规则
                    discardDuplicates: true,
                    // 去除被覆盖的 @keyframes
                    discardOverridden: true,
                    // 规范化显示值
                    normalizeDisplayValues: true,
                    // 简化位置值
                    normalizePositions: true,
                    // 规范化重复值
                    normalizeRepeatStyle: true,
                    // 简化时间值
                    normalizeTimingFunctions: true,
                    // 规范化 Unicode 表示
                    normalizeUnicode: true,
                    // 排序值以提升压缩率
                    orderedValues: true,
                    // 合并唯一的键帧选择器
                    uniqueSelectors: true,
                  },
                ],
              }),
            ],
          }
        : undefined,
    },

    // ============================================================
    // 构建配置
    // ============================================================
    build: {
      // === 目标浏览器 ===
      // 设置合适的 target 影响语法降级程度
      target: 'es2020',

      // === Source Map ===
      // 生产环境禁用 source map（防止源码泄露，减小体积）
      sourcemap: false,

      // === 压缩器 ===
      // 使用 Terser 进行 JS 压缩（比 esbuild 压缩更彻底）
      minify: 'terser',

      // Terser 压缩选项
      terserOptions: {
        // 混淆压缩配置
        compress: {
          // 移除 console.* 调用
          drop_console: true,
          // 移除 debugger 语句
          drop_debugger: true,
          // 纯函数调用（无副作用）可被移除
          pure_funcs: ['console.log', 'console.info', 'console.debug', 'console.warn'],
          // 移除未使用的函数参数
          unused: true,
          // 移除无法到达的代码
          dead_code: true,
          // 合并连续的 var 声明
          join_vars: true,
          // 布尔值上下文简化
          booleans: true,
          // 条件编译优化
          conditionals: true,
          // 比较运算优化
          comparisons: true,
          // 求值表达式优化
          evaluate: true,
          // if 语句简化
          if_return: true,
          // 内联函数调用
          inline: 2,
          // 循环优化
          loops: true,
          // 属性访问简化
          properties: true,
          // 递归调用优化
          reduce_vars: true,
          // 副作用检测
          side_effects: true,
          // switch 语句优化
          switches: true,
          // 类型转换优化
          typeofs: true,
        },
        // 混淆变量名和函数名
        mangle: {
          // 保留的关键字不混淆（避免破坏外部 API）
          reserved: [],
          // 混淆顶层作用域的名称
          toplevel: true,
        },
        // 输出格式
        format: {
          // === 去除所有注释（包括 /*! 许可注释） ===
          comments: false,
          // 美化输出（生产环境必须为 false）
          beautify: false,
          // 缩进级别（生产环境应设为 0，不保留缩进）
          indent_level: 0,
          // 保留引号类型（不做转换）
          quote_style: 0,
          // 不保留括号
          keep_quoted_props: false,
          // 使用 ASCII 编码
          ascii_only: true,
          // 不输出分号
          semicolons: true,
          // 内联脚本
          inline_script: false,
          // 包装函数
          wrap_func_args: false,
        },
        // 保留类名和函数名（防止破坏反射调用）
        keep_classnames: false,
        keep_fnames: false,
        // 保留的顶级变量
        toplevel: true,
        // 模块格式
        module: true,
      },

      // === 代码分割 ===
      rollupOptions: {
        output: {
          // 手动代码分割策略 — 按框架/库分层，最大化浏览器缓存命中率
          manualChunks: {
            // Vue 核心框架（vue, vue-router, pinia）
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            // Element Plus UI 组件库
            'vendor-element': ['element-plus'],
            // 工具库（axios, fingerprintjs, hls.js, gsap）
            'vendor-utils': ['axios', '@fingerprintjs/fingerprintjs', 'hls.js', 'gsap'],
          },

          // === 输出文件命名规则 ===
          // 入口 JS 文件：assets/js/[name].[hash].js
          entryFileNames: 'assets/js/[name].[hash:12].js',
          // 代码分割的 chunk 文件：assets/js/[name].[hash].js
          chunkFileNames: 'assets/js/[name].[hash:12].js',
          // 静态资源（CSS/图片/字体等）：assets/[ext]/[name].[hash].[ext]
          assetFileNames: (assetInfo: any) => {
            const info = assetInfo.name?.split('.') || []
            const ext = info[info.length - 1]
            const name = info.slice(0, -1).join('.')

            // CSS 文件放在 assets/css/ 目录
            if (/\.(css|scss|sass|less)$/i.test(assetInfo.name || '')) {
              return `assets/css/[name].[hash:12].[ext]`
            }
            // 图片文件放在 assets/img/ 目录
            if (/\.(png|jpe?g|gif|svg|webp|ico|bmp)$/i.test(assetInfo.name || '')) {
              return `assets/img/[name].[hash:12].[ext]`
            }
            // 字体文件放在 assets/fonts/ 目录
            if (/\.(woff2?|eot|ttf|otf)$/i.test(assetInfo.name || '')) {
              return `assets/fonts/[name].[hash:12].[ext]`
            }
            // 其他资源
            return `assets/[ext]/[name].[hash:12].[ext]`
          },
        },
      },

      // === 大文件警告阈值 ===
      // 设为 1MB，避免合理的 chunk 分包产生不必要的警告
      chunkSizeWarningLimit: 1024,

      // === CSS 处理 ===
      // CSS 代码分割（按需加载的异步组件 CSS 独立分包）
      cssCodeSplit: true,
      // CSS 压缩目标（使用 esbuild 原生 CSS 压缩，自动去除注释）
      cssMinify: 'esbuild',

      // === 资源内联阈值 ===
      // 小于 4KB 的资源内联为 base64（减少 HTTP 请求）
      assetsInlineLimit: 4096,

      // === 构建产物清空 ===
      // 每次构建前清空 dist 目录
      emptyOutDir: true,

      // === 构建报告 ===
      // 生成构建分析报告（可选，用于 CI 体积监控）
      reportCompressedSize: false,
    },

    // ============================================================
    // esbuild 配置 — 仅在开发环境使用 esbuild 转换
    // ============================================================
    esbuild: {
      // 开发环境保留注释
      // 生产环境由 Terser 接管，此处配置不影响
      legalComments: isDevelopment ? 'inline' : 'none',
      // 移除所有 console 和 debugger
      drop: isProduction ? ['console', 'debugger'] : [],
    },
  }
})