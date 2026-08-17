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
import { minify as terserMinify } from 'terser'
import cssnano from 'cssnano'
import fs from 'node:fs'
import path from 'node:path'

/**
 * Monaco 同源静态资源插件。
 * 原行为把完整 ESM 语言服务纳入 Rollup，生产构建在约 2GB 堆上 OOM；新行为在开发环境
 * 只读服务 npm 包的 min/vs，在生产构建结束后复制到独立静态目录，不参与 Terser 与压缩遍历。
 */
function selfHostedMonacoPlugin() {
  const sourceRoot = path.resolve(process.cwd(), 'node_modules/monaco-editor/min/vs')
  const webPrefix = '/vendor/monaco/vs/'
  const mimeTypes: Record<string, string> = {
    '.js': 'application/javascript; charset=utf-8',
    '.css': 'text/css; charset=utf-8',
    '.json': 'application/json; charset=utf-8',
    '.ttf': 'font/ttf',
    '.svg': 'image/svg+xml',
  }
  return {
    name: 'pcd-self-hosted-monaco',
    configureServer(server: any) {
      server.middlewares.use((request: any, response: any, next: () => void) => {
        const requestPath = String(request.url || '').split('?', 1)[0]
        if (!requestPath.startsWith(webPrefix)) return next()
        const relative = decodeURIComponent(requestPath.slice(webPrefix.length))
        const target = path.resolve(sourceRoot, relative)
        if (target !== sourceRoot && !target.startsWith(`${sourceRoot}${path.sep}`)) {
          response.statusCode = 403
          response.end('Forbidden')
          return
        }
        if (!fs.existsSync(target) || !fs.statSync(target).isFile()) return next()
        response.setHeader('Content-Type', mimeTypes[path.extname(target)] || 'application/octet-stream')
        response.setHeader('Cache-Control', 'no-cache')
        fs.createReadStream(target).pipe(response)
      })
    },
    closeBundle() {
      const targetRoot = path.resolve(process.cwd(), 'dist/vendor/monaco/vs')
      fs.mkdirSync(path.dirname(targetRoot), { recursive: true })
      fs.cpSync(sourceRoot, targetRoot, { recursive: true, force: true })
    },
  }
}

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
            async transform(code: string, id: string) {
              // 仅混淆 crypto.ts 模块
              if (!cryptoModuleRE.test(id)) return null

              try {
                /*
                 * 插件生态 Sprint 0 供应链修复：
                 * 原行为直接执行 javascript-obfuscator，其 multimatch/minimatch 依赖存在高危内存耗尽公告。
                 * 新行为保留原配置痕迹用于回溯，但使用项目既有 Terser 做最小化与标识符压缩。
                 * 客户端混淆从来不是密钥边界，真正安全性仍由 TLS、服务端哈希和限流承担。
                 */
                const historicalObfuscatorOptions = {
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
                }
                const result = await terserMinify(code, {
                  module: true,
                  compress: {
                    passes: 3,
                    dead_code: true,
                    drop_debugger: true,
                  },
                  mangle: {
                    module: true,
                    reserved: historicalObfuscatorOptions.reservedNames,
                  },
                  format: { comments: false },
                  sourceMap: false,
                })
                if (!result.code) throw new Error('Terser 未生成 crypto 模块代码')

                return {
                  code: result.code,
                  map: null,
                }
              } catch (e) {
                console.error('Failed to obfuscate crypto.ts:', e)
                return null // 混淆失败时回退到原始代码
              }
            },
          }
        })(),

      // === index.html 压缩（生产环境） ===
      // Vite 默认只注入 script/link 标签，不压缩 HTML 本身。
      // 通过 transformIndexHtml 钩子对最终输出的 index.html 进行压缩：
      //   1. 移除所有 HTML 注释（包括结构说明、CDN 预连接注释等）
      //   2. 移除标签间的空白字符（> < 之间的换行和缩进）
      //   3. 合并连续空白为单个空格
      //   4. 移除每行首尾空白
      // 压缩后 index.html 将从多行可读格式变为单行紧凑格式，
      // 体积减少约 30-50%，且不暴露任何注释中的架构信息。
      isProduction &&
        (function vitePluginMinifyHtml() {
          // HTML 压缩函数：移除注释 + 压缩空白
          const minifyHtml = (html: string): string => {
            return html
              // 移除 HTML 注释（<!-- ... -->），支持多行注释
              .replace(/<!--[\s\S]*?-->/g, '')
              // 移除标签间的空白（> 和 < 之间的换行/缩进）
              .replace(/>\s+</g, '><')
              // 合并连续空白字符（空格/制表符/换行）为单个空格
              .replace(/\s{2,}/g, ' ')
              // 移除每行首尾空白
              .replace(/^\s+|\s+$/gm, '')
              // 移除多余空行
              .replace(/\n\s*\n/g, '\n')
              .trim()
          }

          return {
            name: 'vite-plugin-minify-html',
            apply: 'build' as const,
            // transformIndexHub 在 Vite 注入 script/link 标签后调用
            // 此时 HTML 已包含所有资源引用，是最终产物
            transformIndexHtml(html: string) {
              return minifyHtml(html)
            },
          }
        })(),

      // === public/ 目录静态资源压缩（生产环境） ===
      // Vite 对 public/ 目录下的文件采用"原样复制"策略，不做任何处理。
      // 这意味着 public/ 中的 HTML 注释、CSS 注释、JS 注释等都会原封不动
      // 地部署到生产环境，造成信息泄露和体积浪费。
      //
      // ★★★ 企业级实现：精确区分 Vite 构建产物与 public/ 静态资源 ★★★
      //
      //   Vite 输出目录结构（dist/）：
      //   ├── index.html          ← Vite 生成（已由 vite-plugin-minify-html 处理）
      //   ├── assets/             ← Vite 构建产物（JS/CSS 已由 Terser/esbuild 深度压缩）
      //   │   ├── index-abc123.js   ★ 已压缩，正则二次处理会截断/损坏！
      //   │   └── index-def456.css  ★ 已压缩，正则二次处理会截断/损坏！
      //   ├── *.gz, *.br          ← vite-plugin-compression 生成的预压缩文件
      //   ├── favicon.ico         ← public/ 原样复制 → 需要处理
      //   ├── robots.txt          ← public/ 原样复制 → 需要处理
      //   └── subdir/             ← public/subdir/ 原样复制 → 需要处理
      //
      //   本插件策略：
      //     1. 通过 configResolved 钩子获取 Vite 的 assetsDir 配置（默认 "assets"）
      //     2. 在 closeBundle 中遍历 dist/ 时，精确跳过 Vite 构建产物目录
      //     3. 跳过 .gz / .br 预压缩文件
      //     4. 跳过 index.html（已由 vite-plugin-minify-html 处理）
      //     5. 仅处理 public/ 复制过来的原始静态资源
      isProduction &&
        (function vitePluginMinifyPublic() {
          // Vite 构建产物目录名（默认 "assets"，可通过 build.assetsDir 配置）
          let viteAssetsDir = 'assets'

          // CSS 压缩：移除注释 + 压缩空白
          const minifyCss = (content: string): string => {
            return content
              // 移除 CSS 注释（/* ... */），支持多行
              .replace(/\/\*[\s\S]*?\*\//g, '')
              // 移除空白
              .replace(/\s{2,}/g, ' ')
              .replace(/\s*([{}:;,])\s*/g, '$1')
              .replace(/;\s*}/g, '}')
              .trim()
          }

          // JS 压缩：移除注释 + 压缩空白
          const minifyJs = (content: string): string => {
            return content
              // 移除单行注释（但保留 URL 中的 //，如 https://）
              .replace(/\/\/(?!\s*[a-zA-Z]+:\/\/)[^\n]*/g, '')
              // 移除多行注释
              .replace(/\/\*[\s\S]*?\*\//g, '')
              // 压缩空白
              .replace(/\s{2,}/g, ' ')
              .replace(/\s*([{}();,:[\]])\s*/g, '$1')
              .trim()
          }

          // HTML 压缩（与 index.html 压缩逻辑一致）
          const minifyHtml = (content: string): string => {
            return content
              .replace(/<!--[\s\S]*?-->/g, '')
              .replace(/>\s+</g, '><')
              .replace(/\s{2,}/g, ' ')
              .replace(/^\s+|\s+$/gm, '')
              .replace(/\n\s*\n/g, '\n')
              .trim()
          }

          return {
            name: 'vite-plugin-minify-public',
            apply: 'build' as const,

            // ★ configResolved：在 Vite 解析完所有配置后调用，获取最终的 assetsDir
            configResolved(config) {
              viteAssetsDir = config.build.assetsDir
            },

            // closeBundle 在打包完成、所有文件写入磁盘后调用
            closeBundle() {
              const distDir = path.resolve(process.cwd(), 'dist')
              if (!fs.existsSync(distDir)) return

              // 文件扩展名 → 压缩函数 映射表
              const minifierMap: Record<string, (content: string) => string> = {
                '.html': minifyHtml,
                '.css': minifyCss,
                '.js': minifyJs,
              }

              // ★ 需要跳过的文件/目录集合
              const skipFiles = new Set(['index.html']) // 已由 vite-plugin-minify-html 处理
              const skipExtensions = new Set(['.gz', '.br']) // 预压缩文件，由 vite-plugin-compression 生成

              // 递归遍历 dist/ 目录，仅处理 public/ 来源的静态资源
              const walkDir = (dir: string, relativePath: string) => {
                const entries = fs.readdirSync(dir, { withFileTypes: true })
                for (const entry of entries) {
                  const fullPath = path.join(dir, entry.name)
                  const relPath = path.join(relativePath, entry.name)

                  if (entry.isDirectory()) {
                    // ★★★ 核心修复：跳过 Vite 构建产物目录（assets/）★★★
                    // 该目录下的 JS/CSS 已由 Terser/esbuild 深度压缩，
                    // 正则二次处理会破坏变量名、截断代码、引入语法错误
                    if (entry.name === viteAssetsDir) {
                      console.log(`  ⊘ skipped: ${relPath}/ (Vite build output, already minified by Terser/esbuild)`)
                      continue
                    }
                    walkDir(fullPath, relPath)
                  } else if (entry.isFile()) {
                    // 跳过已处理的文件（如 index.html）
                    if (skipFiles.has(entry.name)) continue

                    const ext = path.extname(entry.name).toLowerCase()
                    // 跳过预压缩文件（.gz / .br）
                    if (skipExtensions.has(ext)) continue

                    // 只处理有对应压缩函数的文件类型
                    const minifier = minifierMap[ext]
                    if (!minifier) continue

                    try {
                      const content = fs.readFileSync(fullPath, 'utf-8')
                      const minified = minifier(content)

                      if (minified !== content) {
                        fs.writeFileSync(fullPath, minified, 'utf-8')
                        const originalSize = Buffer.byteLength(content, 'utf-8')
                        const minifiedSize = Buffer.byteLength(minified, 'utf-8')
                        const saved = originalSize - minifiedSize
                        const pct = ((saved / originalSize) * 100).toFixed(1)
                        console.log(`  ✓ minified: ${relPath} (${originalSize} → ${minifiedSize} bytes, -${pct}%)`)
                      }
                    } catch {
                      // 跳过无法读取的文件（如二进制文件）
                    }
                  }
                }
              }

              console.log('\n📦 [vite-plugin-minify-public] Processing public/ static resources...')
              console.log(`  (excluding Vite build output: ${viteAssetsDir}/, .gz, .br, index.html)`)
              walkDir(distDir, '')
              console.log('✅ Public static resources minification complete.\n')
            },
          }
        })(),
      // 必须放在压缩/静态资源处理插件之后，使 Monaco 不进入主包和二次压缩流程。
      selfHostedMonacoPlugin(),
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
      allowedHosts: [
        'tests.hellomyservice.xyz',
        'localhost',
        '.myservice.xyz'  // 允许所有 myservice.xyz 子域名
      ]
    },

    // ============================================================
    // CSS 处理
    // ============================================================
    // css: {
    //   // 生产环境启用 cssnano 深度优化
    //   postcss: isProduction
    //     ? {
    //         plugins: [
    //           cssnano({
    //             preset: [
    //               'default',
    //               {
    //                 // 去除所有 CSS 注释（包括 /*! 重要注释 */）
    //                 discardComments: { removeAll: true },
    //                 // 合并相邻的 @ 规则
    //                 mergeRules: true,
    //                 // 合并长时间运行的选择器
    //                 mergeLonghand: true,
    //                 // 规范化属性值（如颜色值统一为最短形式）
    //                 colormin: true,
    //                 // 简化 calc() 表达式
    //                 calc: true,
    //                 // 去除空的 @ 规则
    //                 discardEmpty: true,
    //                 // 规范化 z-index 值
    //                 zindex: true,
    //                 // 压缩字体权重表示
    //                 minifyFontValues: true,
    //                 // 压缩渐变表示
    //                 minifyGradients: true,
    //                 // 规范化选择器
    //                 normalizeWhitespace: true,
    //                 // 排序 CSS 声明以提升 gzip 压缩率
    //                 cssDeclarationSorter: { order: 'alphabetical' },
    //                 // 合并 @font-face 中相同字体族的规则
    //                 mergeFontFace: true,
    //                 // 转换颜色函数为更短形式
    //                 convertColors: true,
    //                 // 去除不必要的转义字符
    //                 normalizeCharset: true,
    //                 // 去除重复的 @charset 规则
    //                 discardDuplicates: true,
    //                 // 去除被覆盖的 @keyframes
    //                 discardOverridden: true,
    //                 // 规范化显示值
    //                 normalizeDisplayValues: true,
    //                 // 简化位置值
    //                 normalizePositions: true,
    //                 // 规范化重复值
    //                 normalizeRepeatStyle: true,
    //                 // 简化时间值
    //                 normalizeTimingFunctions: true,
    //                 // 规范化 Unicode 表示
    //                 normalizeUnicode: true,
    //                 // 排序值以提升压缩率
    //                 orderedValues: true,
    //                 // 合并唯一的键帧选择器
    //                 uniqueSelectors: true,
    //               },
    //             ],
    //           }),
    //         ],
    //       }
    //     : undefined,
    // },

    // ============================================================
    // 构建配置
    // ============================================================
    build: {
      // === 目标浏览器 ===
      // 设置合适的 target 影响语法降级程度
      // AUDIT FIX [2.4]（需求一-7）:
      // 原行为只输出 ES2020，较旧 Safari 解析阶段即可白屏；新行为保留现代能力并显式覆盖 Safari 13。
      target: ['es2018', 'safari13'],
      cssTarget: 'safari13',

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
        // === 外部依赖（不打包进产物，运行时从 CDN 加载） ===
        // 以下库改为 CDN 动态加载，通过 src/utils/*Cdn.ts 加载器统一管理
        // 此处声明为 external 防止构建时残留打包引用导致 OOM 与产物膨胀
        external: [
          'markdown-it',
          'markdown-it-anchor',
          'markdown-it-emoji',
          'markdown-it-table-of-contents',
          'markdown-it-task-lists',
          'mermaid',
          'katex',
          'highlight.js',
          'dompurify',
        ],

        output: {
          // 手动代码分割策略 — 按框架/库分层，最大化浏览器缓存命中率
          manualChunks: {
            // Vue 核心框架（vue, vue-router, pinia）
            'vendor-vue': ['vue', 'vue-router', 'pinia'],
            // Element Plus UI 组件库
            'vendor-element': ['element-plus'],
            'vendor-workflow': [
              '@vue-flow/core',
              '@vue-flow/background',
              '@vue-flow/controls',
              'yaml',
            ],
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
