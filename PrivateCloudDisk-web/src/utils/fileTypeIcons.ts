// ============================================================
// fileTypeIcons.ts — 文件浏览器统一类型图标目录
// ============================================================
// AUDIT FIX [1.1-1.12/2.1-2.25]：网格、列表、缩略图占位不再各自维护
// 后缀与图标库分支；所有节点通过同一套优先级解析类型。
// 这是纯前端展示目录，不改变文件元数据、上传协议或后端存储路径。
// ============================================================

export type FileIconKind =
  | 'file'
  | 'folder'
  | 'folder-special'
  | 'image'
  | 'video'
  | 'audio'
  | 'pdf'
  | 'word'
  | 'excel'
  | 'powerpoint'
  | 'archive'
  | 'binary'
  | 'code'
  | 'config'
  | 'database'
  | 'markdown'
  | 'text'
  | 'docker'
  | 'git'
  | 'ci'
  | 'ide'
  | 'build'
  | 'cache'
  | 'test'
  | 'cloud'
  | 'terraform'
  | 'kubernetes'
  | 'workflow'
  | 'package'
  | 'license'
  | 'unknown'

export interface FileIconDescriptor {
  kind: FileIconKind
  label: string
  glyph: string
  color: string
  /** 本地注册的 VS Code Icons 名称；未知类型不设置此字段。 */
  iconName?: string
  /** 兼容仍使用 Font Awesome 的非文件浏览器旧组件。 */
  faClass: string
  legacyColorClass: string
  dynamicSvg?: string
}

type IconDefinition = Omit<FileIconDescriptor, 'dynamicSvg'>

const definitions: Record<FileIconKind, IconDefinition> = {
  file: { kind: 'file', label: '文件', glyph: 'FILE', color: '#64748b', iconName: 'vscode-icons:default-file', faClass: 'fa-file-o', legacyColorClass: 'text-neutral-400' },
  folder: { kind: 'folder', label: '文件夹', glyph: '', color: '#2563eb', iconName: 'vscode-icons:default-folder', faClass: 'fa-folder', legacyColorClass: 'text-primary' },
  'folder-special': { kind: 'folder-special', label: '特殊目录', glyph: 'DIR', color: '#2563eb', iconName: 'vscode-icons:default-folder', faClass: 'fa-folder-open', legacyColorClass: 'text-primary' },
  image: { kind: 'image', label: '图片', glyph: 'IMG', color: '#8b5cf6', iconName: 'vscode-icons:file-type-image', faClass: 'fa-file-image-o', legacyColorClass: 'text-purple-500' },
  video: { kind: 'video', label: '视频', glyph: 'PLAY', color: '#e11d48', iconName: 'vscode-icons:file-type-video', faClass: 'fa-file-video-o', legacyColorClass: 'text-red-500' },
  audio: { kind: 'audio', label: '音频', glyph: 'AUDIO', color: '#db2777', iconName: 'vscode-icons:file-type-audio', faClass: 'fa-file-audio-o', legacyColorClass: 'text-pink-500' },
  pdf: { kind: 'pdf', label: 'PDF 文档', glyph: 'PDF', color: '#dc2626', iconName: 'vscode-icons:file-type-pdf2', faClass: 'fa-file-pdf-o', legacyColorClass: 'text-danger' },
  word: { kind: 'word', label: 'Word 文档', glyph: 'W', color: '#2563eb', iconName: 'vscode-icons:file-type-word', faClass: 'fa-file-word-o', legacyColorClass: 'text-blue-600' },
  excel: { kind: 'excel', label: 'Excel 表格', glyph: 'X', color: '#16a34a', iconName: 'vscode-icons:file-type-excel', faClass: 'fa-file-excel-o', legacyColorClass: 'text-green-600' },
  powerpoint: { kind: 'powerpoint', label: 'PowerPoint 演示文稿', glyph: 'P', color: '#ea580c', iconName: 'vscode-icons:file-type-powerpoint', faClass: 'fa-file-powerpoint-o', legacyColorClass: 'text-orange-500' },
  archive: { kind: 'archive', label: '压缩包', glyph: 'ZIP', color: '#ca8a04', iconName: 'vscode-icons:file-type-zip', faClass: 'fa-file-archive-o', legacyColorClass: 'text-yellow-600' },
  binary: { kind: 'binary', label: '二进制文件', glyph: 'BIN', color: '#475569', iconName: 'vscode-icons:default-file', faClass: 'fa-file-o', legacyColorClass: 'text-neutral-500' },
  code: { kind: 'code', label: '代码文件', glyph: '{}', color: '#0f766e', iconName: 'vscode-icons:file-type-source', faClass: 'fa-file-code-o', legacyColorClass: 'text-teal-600' },
  config: { kind: 'config', label: '配置文件', glyph: 'CFG', color: '#0891b2', iconName: 'vscode-icons:file-type-config', faClass: 'fa-file-code-o', legacyColorClass: 'text-cyan-600' },
  database: { kind: 'database', label: '数据库文件', glyph: 'SQL', color: '#0369a1', iconName: 'vscode-icons:file-type-sql', faClass: 'fa-database', legacyColorClass: 'text-sky-700' },
  markdown: { kind: 'markdown', label: 'Markdown 文档', glyph: 'MD', color: '#334155', iconName: 'vscode-icons:file-type-markdown', faClass: 'fa-file-text-o', legacyColorClass: 'text-slate-600' },
  text: { kind: 'text', label: '文本文件', glyph: 'TXT', color: '#64748b', iconName: 'vscode-icons:default-file', faClass: 'fa-file-text-o', legacyColorClass: 'text-neutral-500' },
  docker: { kind: 'docker', label: 'Docker 文件', glyph: 'DO', color: '#2496ed', iconName: 'vscode-icons:file-type-docker', faClass: 'fa-cube', legacyColorClass: 'text-sky-500' },
  git: { kind: 'git', label: 'Git 文件', glyph: 'GIT', color: '#f05032', iconName: 'vscode-icons:file-type-git', faClass: 'fa-code-fork', legacyColorClass: 'text-orange-600' },
  ci: { kind: 'ci', label: 'CI/CD 配置', glyph: 'CI', color: '#6e40c9', iconName: 'vscode-icons:file-type-gitlab', faClass: 'fa-cogs', legacyColorClass: 'text-purple-600' },
  ide: { kind: 'ide', label: 'IDE 配置', glyph: 'IDE', color: '#7c3aed', iconName: 'vscode-icons:file-type-vscode', faClass: 'fa-desktop', legacyColorClass: 'text-violet-600' },
  build: { kind: 'build', label: '构建目录或配置', glyph: 'BUILD', color: '#f59e0b', iconName: 'vscode-icons:file-type-config', faClass: 'fa-wrench', legacyColorClass: 'text-amber-500' },
  cache: { kind: 'cache', label: '缓存目录', glyph: 'CACHE', color: '#64748b', iconName: 'vscode-icons:folder-type-library', faClass: 'fa-database', legacyColorClass: 'text-slate-500' },
  test: { kind: 'test', label: '测试目录', glyph: 'TEST', color: '#16a34a', iconName: 'vscode-icons:folder-type-test', faClass: 'fa-check-square-o', legacyColorClass: 'text-green-600' },
  cloud: { kind: 'cloud', label: '云平台配置', glyph: 'CLOUD', color: '#0284c7', iconName: 'vscode-icons:file-type-config', faClass: 'fa-cloud', legacyColorClass: 'text-sky-600' },
  terraform: { kind: 'terraform', label: 'Terraform 配置', glyph: 'TF', color: '#7c3aed', iconName: 'vscode-icons:file-type-terraform', faClass: 'fa-cloud', legacyColorClass: 'text-violet-600' },
  kubernetes: { kind: 'kubernetes', label: 'Kubernetes/Helm 配置', glyph: 'K8S', color: '#326ce5', iconName: 'vscode-icons:folder-type-kubernetes', faClass: 'fa-cubes', legacyColorClass: 'text-blue-600' },
  workflow: { kind: 'workflow', label: '工作流文件', glyph: 'FLOW', color: '#0f766e', iconName: 'vscode-icons:file-type-config', faClass: 'fa-sitemap', legacyColorClass: 'text-teal-600' },
  package: { kind: 'package', label: '依赖包配置', glyph: 'PKG', color: '#cb3837', iconName: 'vscode-icons:file-type-package', faClass: 'fa-cubes', legacyColorClass: 'text-red-600' },
  license: { kind: 'license', label: '许可证文件', glyph: 'MIT', color: '#475569', iconName: 'vscode-icons:file-type-license', faClass: 'fa-balance-scale', legacyColorClass: 'text-slate-600' },
  unknown: { kind: 'unknown', label: '未知类型文件', glyph: 'FILE', color: '#64748b', faClass: 'fa-file-o', legacyColorClass: 'text-neutral-400' },
}

const extensionToKind: Record<string, FileIconKind> = {}
const extensionToVscodeIcon: Record<string, string> = {}

function mapExtensions(kind: FileIconKind, extensions: string[]) {
  for (const extension of extensions) extensionToKind[extension.toLowerCase().replace(/^\./, '')] = kind
}

function mapVscodeIcons(iconName: string, extensions: string[]) {
  for (const extension of extensions) extensionToVscodeIcon[extension.toLowerCase().replace(/^\./, '')] = `vscode-icons:${iconName}`
}

mapExtensions('image', ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico', 'tif', 'tiff', 'avif', 'heic', 'heif', 'raw', 'psd', 'ai', 'eps'])
mapExtensions('video', ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv', 'm4v', 'mpeg', 'mpg', '3gp', 'm2ts', 'mts', 'vob'])
mapExtensions('audio', ['mp3', 'wav', 'flac', 'aac', 'm4a', 'wma', 'opus', 'aiff', 'aif', 'oga', 'mid', 'midi'])
mapExtensions('pdf', ['pdf'])
mapExtensions('word', ['doc', 'docx', 'docm', 'dot', 'dotx', 'odt', 'rtf'])
mapExtensions('excel', ['xls', 'xlsx', 'xlsm', 'xlt', 'xltx', 'ods', 'csv', 'tsv'])
mapExtensions('powerpoint', ['ppt', 'pptx', 'pptm', 'pot', 'potx', 'odp', 'key'])
mapExtensions('archive', ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz', 'tbz', 'zst', 'lz', 'lz4', 'cab', 'iso', 'dmg'])
mapExtensions('binary', ['exe', 'dll', 'so', 'dylib', 'bin', 'app', 'deb', 'rpm', 'msi', 'wasm', 'o', 'a', 'class', 'pyc'])
mapExtensions('markdown', ['md', 'markdown', 'mdown', 'mkdn', 'mdx', 'rst', 'adoc', 'asciidoc'])
mapExtensions('database', ['sql', 'sqlite', 'sqlite3', 'db', 'db3', 'mdb', 'accdb', 'dump', 'prisma'])
mapExtensions('text', ['txt', 'text', 'log', 'nfo', 'readme', 'tex', 'rtx', 'srt', 'vtt', 'diff', 'patch'])
mapExtensions('config', ['yaml', 'yml', 'json', 'json5', 'jsonc', 'xml', 'xsd', 'xsl', 'xslt', 'toml', 'ini', 'cfg', 'conf', 'properties', 'env', 'hcl', 'gradle', 'cmake', 'make', 'lock'])
mapExtensions('terraform', ['tf', 'tfvars', 'tfstate'])
mapExtensions('kubernetes', ['helm', 'crd'])
mapExtensions('workflow', ['cloudflow', 'flow', 'workflow'])
mapExtensions('package', ['pom', 'sbt', 'ivy', 'gem', 'lockfile'])

// VS Code Icons 后缀目录：命中后由 FileTypeIcon 通过 Iconify offline 渲染，
// 未列入精选子集的极少数语言仍使用 code/config 的图标库基础图标。
mapVscodeIcons('file-type-python', ['py', 'pyw', 'pyc'])
mapVscodeIcons('file-type-jupyter', ['ipynb'])
mapVscodeIcons('file-type-js', ['js', 'mjs', 'cjs'])
mapVscodeIcons('file-type-reactjs', ['jsx'])
// vscode-icons 的精选离线集未单独拆出 React TS 变体，复用 React 官方 Atom 图标保持语言语义不变。
mapVscodeIcons('file-type-reactjs', ['tsx'])
mapVscodeIcons('file-type-typescript-official', ['ts'])
mapVscodeIcons('file-type-java', ['java', 'class'])
mapVscodeIcons('file-type-go', ['go'])
mapVscodeIcons('file-type-rust', ['rs'])
mapVscodeIcons('file-type-c', ['c', 'h'])
mapVscodeIcons('file-type-cpp', ['cpp', 'cc', 'cxx'])
mapVscodeIcons('file-type-cppheader', ['hpp'])
mapVscodeIcons('file-type-csharp', ['cs'])
mapVscodeIcons('file-type-php', ['php'])
mapVscodeIcons('file-type-ruby', ['rb'])
mapVscodeIcons('file-type-swift', ['swift'])
mapVscodeIcons('file-type-kotlin', ['kt', 'kts'])
mapVscodeIcons('file-type-scala', ['scala'])
mapVscodeIcons('file-type-objectivec', ['m'])
mapVscodeIcons('file-type-objectivecpp', ['mm'])
mapVscodeIcons('file-type-perl', ['pl', 'pm'])
mapVscodeIcons('file-type-shell', ['sh', 'bash', 'zsh', 'fish'])
mapVscodeIcons('file-type-powershell', ['ps1'])
mapVscodeIcons('file-type-powershell', ['bat', 'cmd'])
mapVscodeIcons('file-type-lua', ['lua'])
mapVscodeIcons('file-type-r', ['r'])
mapVscodeIcons('file-type-sql', ['sql'])
mapVscodeIcons('file-type-html', ['html', 'htm'])
mapVscodeIcons('file-type-css', ['css'])
mapVscodeIcons('file-type-scss', ['scss'])
mapVscodeIcons('file-type-sass', ['sass'])
mapVscodeIcons('file-type-less', ['less'])
mapVscodeIcons('file-type-stylus', ['styl'])
mapVscodeIcons('file-type-vue', ['vue'])
mapVscodeIcons('file-type-svelte', ['svelte'])
mapVscodeIcons('file-type-xml', ['xml', 'xaml'])
mapVscodeIcons('file-type-dartlang', ['dart'])
mapVscodeIcons('file-type-elixir', ['ex', 'exs'])
mapVscodeIcons('file-type-erlang', ['erl', 'hrl'])
mapVscodeIcons('file-type-clojure', ['clj', 'cljc'])
mapVscodeIcons('file-type-clojurescript', ['cljs'])
mapVscodeIcons('file-type-groovy', ['groovy', 'gradle'])
mapVscodeIcons('file-type-protobuf', ['proto'])
mapVscodeIcons('file-type-solidity', ['sol'])
mapVscodeIcons('file-type-source', ['pas', 'pp', 'vb', 'lisp', 'lsp', 'cl', 'scm', 'sed', 'coq'])
mapVscodeIcons('file-type-fsharp', ['fs', 'fsx'])
mapVscodeIcons('file-type-julia', ['jl'])
mapVscodeIcons('file-type-assembly', ['asm', 's'])
mapVscodeIcons('file-type-zig', ['zig'])
mapVscodeIcons('file-type-pony', ['pony'])
mapVscodeIcons('file-type-nim', ['nim'])
mapVscodeIcons('file-type-verilog', ['v'])
mapVscodeIcons('file-type-vhdl', ['vhdl'])
mapVscodeIcons('file-type-vala', ['vala'])
mapVscodeIcons('file-type-cobol', ['cob', 'cbl'])
mapVscodeIcons('file-type-fortran', ['f90', 'f95', 'f03', 'for', 'f'])
mapVscodeIcons('file-type-haskell', ['hs', 'lhs'])
mapVscodeIcons('file-type-idris', ['idr'])
mapVscodeIcons('file-type-agda', ['agda'])
mapVscodeIcons('file-type-lean', ['lean'])
mapVscodeIcons('file-type-matlab', ['mat'])
mapVscodeIcons('file-type-racket', ['rkt'])
mapVscodeIcons('file-type-tcl', ['tcl'])
mapVscodeIcons('file-type-awk', ['awk'])
mapVscodeIcons('file-type-yaml-official', ['yaml', 'yml'])
mapVscodeIcons('file-type-json-official', ['json', 'json5', 'jsonc'])
mapVscodeIcons('file-type-toml', ['toml'])
mapVscodeIcons('file-type-ini', ['ini', 'cfg', 'conf'])
mapVscodeIcons('file-type-dotenv', ['env'])
mapVscodeIcons('file-type-markdown', ['md', 'markdown', 'mdown', 'mkdn', 'mdx', 'rst', 'adoc', 'asciidoc'])
mapVscodeIcons('file-type-pdf2', ['pdf'])
mapVscodeIcons('file-type-word', ['doc', 'docx', 'docm', 'dot', 'dotx', 'odt', 'rtf'])
mapVscodeIcons('file-type-excel', ['xls', 'xlsx', 'xlsm', 'xlt', 'xltx', 'ods', 'csv', 'tsv'])
mapVscodeIcons('file-type-powerpoint', ['ppt', 'pptx', 'pptm', 'pot', 'potx', 'odp', 'key'])
mapVscodeIcons('file-type-zip', ['zip', 'rar', '7z', 'tar', 'gz', 'bz2', 'xz', 'tgz', 'tbz', 'zst', 'lz', 'lz4', 'cab', 'iso', 'dmg'])
mapVscodeIcons('file-type-image', ['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico', 'tif', 'tiff', 'avif', 'heic', 'heif', 'raw', 'psd', 'ai', 'eps'])
mapVscodeIcons('file-type-video', ['mp4', 'webm', 'ogg', 'mov', 'avi', 'mkv', 'wmv', 'flv', 'm4v', 'mpeg', 'mpg', '3gp', 'm2ts', 'mts', 'vob'])
mapVscodeIcons('file-type-audio', ['mp3', 'wav', 'flac', 'aac', 'm4a', 'wma', 'opus', 'aiff', 'aif', 'oga', 'mid', 'midi'])
mapVscodeIcons('file-type-terraform', ['tf', 'tfvars', 'tfstate'])
mapVscodeIcons('file-type-helm', ['helm', 'crd'])

mapExtensions('code', [
  'py', 'pyw', 'ipynb', 'js', 'mjs', 'cjs', 'jsx', 'ts', 'tsx', 'java',
  'go', 'rs', 'c', 'h', 'cpp', 'cc', 'cxx', 'hpp', 'cs', 'php', 'rb',
  'swift', 'kt', 'kts', 'scala', 'm', 'mm', 'pl', 'pm', 'sh', 'bash', 'zsh',
  'fish', 'bat', 'cmd', 'ps1', 'lua', 'r', 'html', 'htm', 'css', 'scss',
  'sass', 'less', 'styl', 'vue', 'svelte', 'xaml', 'dart', 'ex', 'exs',
  'erl', 'hrl', 'clj', 'cljs', 'cljc', 'groovy', 'proto', 'sol', 'pas', 'pp',
  'vb', 'fs', 'fsx', 'jl', 'lisp', 'lsp', 'cl', 'asm', 's', 'zig', 'pony',
  'nim', 'v', 'vhdl', 'vala', 'cob', 'cbl', 'f90', 'f95', 'f03', 'for',
  'f', 'hs', 'lhs', 'idr', 'agda', 'lean', 'v', 'coq', 'mat', 'rkt', 'scm',
  'ss', 'tcl', 'awk', 'sed', 'sol', 'bicep', 'j2', 'jinja', 'mustache',
])

const specialFileToKind: Record<string, FileIconKind> = {}

function mapSpecialFiles(kind: FileIconKind, names: string[]) {
  for (const name of names) specialFileToKind[name.toLowerCase()] = kind
}

mapSpecialFiles('docker', [
  'dockerfile', 'docker-compose.yml', 'docker-compose.yaml', 'docker-compose.override.yml',
  '.dockerignore',
])
mapSpecialFiles('markdown', ['readme', 'readme.md', 'readme.txt', 'readme.rst'])
mapSpecialFiles('license', ['license', 'license.md', 'license.txt', 'copying', 'notice'])
mapSpecialFiles('build', ['makefile', 'gnumakefile', 'cmakelists.txt', 'cmakecache.txt', 'cmake_install.cmake', 'meson.build', 'meson_options.txt', 'configure.ac', 'makefile.am', 'build.xml'])
mapSpecialFiles('git', ['.gitignore', '.gitattributes', '.gitmodules', '.gitkeep', '.gitconfig'])
mapSpecialFiles('package', [
  'cargo.toml', 'cargo.lock', 'go.mod', 'go.sum', 'go.work', 'package.json', 'package-lock.json',
  'yarn.lock', 'pnpm-lock.yaml', 'npm-shrinkwrap.json', 'pom.xml', 'build.gradle', 'settings.gradle',
  'gradle.properties', 'build.gradle.kts', 'settings.gradle.kts', 'pyproject.toml', 'setup.py', 'setup.cfg',
  'requirements.txt', 'pipfile', 'pipfile.lock', 'poetry.lock', 'composer.json', 'composer.lock',
  'gemfile', 'gemfile.lock', 'podfile', 'podfile.lock', 'package.resolved', 'berksfile', 'cheffile',
  'policyfile.rb', 'procfile', 'procfile.*',
])
mapSpecialFiles('config', [
  'tsconfig.json', 'jsconfig.json', 'vite.config.js', 'vite.config.ts', 'vitest.config.js', 'vitest.config.ts',
  'webpack.config.js', 'webpack.config.ts', 'babel.config.js', 'babel.config.cjs', 'babel.config.json', '.babelrc',
  'eslint.config.js', 'eslint.config.cjs', '.eslintrc', '.eslintrc.js', '.eslintrc.json', 'prettier.config.js',
  'prettier.config.cjs', '.prettierrc', '.prettierrc.json', '.prettierrc.js', '.prettierrc.toml', 'rollup.config.js',
  'rollup.config.ts', 'jest.config.js', 'jest.config.ts', 'jest.config.cjs', 'postcss.config.js', 'postcss.config.cjs',
  '.postcssrc', 'tailwind.config.js', 'tailwind.config.ts', 'tailwind.config.cjs', 'next.config.js', 'next.config.mjs',
  'next.config.ts', 'nuxt.config.js', 'nuxt.config.ts', 'angular.json', 'angular-cli.json', 'vue.config.js',
  'vue.config.cjs', '.npmrc', '.nvmrc', '.node-version', '.browserslistrc', '.stylelintrc', '.stylelintrc.json',
  'stylelint.config.js', '.editorconfig', '.prettierignore', '.eslintignore', '.npmignore', 'application.properties',
  'application.yml', 'application.yaml', 'application-dev.yml', 'application-prod.yml', 'bootstrap.yml',
  'bootstrap.properties', 'logback.xml', 'logback-spring.xml', 'log4j.properties', 'log4j2.xml', 'nginx.conf',
  'httpd.conf', 'apache2.conf', '.htaccess', 'php.ini', 'my.cnf', 'redis.conf', 'supervisor.conf', '.bashrc',
  '.zshrc', '.profile', '.vimrc', '.env', '.env.example', '.env.local', '.env.development', '.env.production',
  '.env.test', '.service', '.socket', '.timer', 'vagrantfile', '.ruby-version', '.python-version',
  '.terraform.lock.hcl', '.terraform-version', 'configure.ac', '.clang-format', '.clang-tidy', '.flake8', '.pylintrc',
  'pyrightconfig.json', 'mypy.ini', 'ruff.toml', '.pre-commit-config.yaml', '.goreleaser.yaml', '.golangci.yml',
  '.yamllint', '.markdownlint.json', '.lintstagedrc',
])
mapSpecialFiles('ci', ['jenkinsfile', '.gitlab-ci.yml'])
mapSpecialFiles('terraform', ['.terraform.lock.hcl', '.terraform-version'])
mapSpecialFiles('kubernetes', ['chart.yaml', 'values.yaml', '.helmignore'])
mapSpecialFiles('workflow', ['.cloudflow', 'workflow.yaml', 'workflow.yml'])

const specialFileToVscodeIcon: Record<string, string> = {}

function mapSpecialFileIcons(iconName: string, names: string[]) {
  for (const name of names) specialFileToVscodeIcon[name.toLowerCase()] = `vscode-icons:${iconName}`
}

mapSpecialFileIcons('file-type-docker', ['dockerfile', 'docker-compose.yml', 'docker-compose.yaml', 'docker-compose.override.yml', '.dockerignore'])
mapSpecialFileIcons('file-type-markdown', ['readme', 'readme.md', 'readme.txt', 'readme.rst'])
mapSpecialFileIcons('file-type-license', ['license', 'license.md', 'license.txt', 'copying', 'notice'])
mapSpecialFileIcons('file-type-config', ['makefile', 'gnumakefile', 'cmakelists.txt', 'cmakecache.txt', 'cmake_install.cmake', 'meson.build', 'meson_options.txt', 'configure.ac', 'makefile.am', 'build.xml'])
mapSpecialFileIcons('file-type-git', ['.gitignore', '.gitattributes', '.gitmodules', '.gitkeep', '.gitconfig'])
mapSpecialFileIcons('file-type-cargo', ['cargo.toml', 'cargo.lock'])
mapSpecialFileIcons('file-type-go', ['go.mod', 'go.sum', 'go.work'])
mapSpecialFileIcons('file-type-npm', ['package.json', 'package-lock.json', 'npm-shrinkwrap.json'])
mapSpecialFileIcons('file-type-yarn', ['yarn.lock'])
mapSpecialFileIcons('file-type-pnpm', ['pnpm-lock.yaml'])
mapSpecialFileIcons('file-type-gradle', ['build.gradle', 'settings.gradle', 'gradle.properties', 'build.gradle.kts', 'settings.gradle.kts'])
mapSpecialFileIcons('file-type-python', ['pyproject.toml', 'setup.py', 'setup.cfg', 'requirements.txt', 'pipfile', 'pipfile.lock', 'poetry.lock'])
mapSpecialFileIcons('file-type-php', ['composer.json', 'composer.lock'])
mapSpecialFileIcons('file-type-ruby', ['gemfile', 'gemfile.lock'])
mapSpecialFileIcons('file-type-swift', ['podfile', 'podfile.lock', 'package.resolved'])
mapSpecialFileIcons('file-type-jenkins', ['jenkinsfile'])
mapSpecialFileIcons('file-type-gitlab', ['.gitlab-ci.yml'])
mapSpecialFileIcons('file-type-terraform', ['.terraform.lock.hcl', '.terraform-version'])
mapSpecialFileIcons('file-type-helm', ['chart.yaml', 'values.yaml', '.helmignore'])
mapSpecialFileIcons('file-type-vite', ['vite.config.js', 'vite.config.ts'])
mapSpecialFileIcons('file-type-vite', ['vitest.config.js', 'vitest.config.ts'])
mapSpecialFileIcons('file-type-webpack', ['webpack.config.js', 'webpack.config.ts'])
mapSpecialFileIcons('file-type-eslint', ['eslint.config.js', 'eslint.config.cjs', '.eslintrc', '.eslintrc.js', '.eslintrc.json'])
mapSpecialFileIcons('file-type-prettier', ['prettier.config.js', 'prettier.config.cjs', '.prettierrc', '.prettierrc.json', '.prettierrc.js', '.prettierrc.toml'])
mapSpecialFileIcons('file-type-jest', ['jest.config.js', 'jest.config.ts', 'jest.config.cjs'])
mapSpecialFileIcons('file-type-config', ['tsconfig.json', 'jsconfig.json', 'babel.config.js', 'babel.config.cjs', 'babel.config.json', '.babelrc', 'postcss.config.js', 'postcss.config.cjs', '.postcssrc', 'tailwind.config.js', 'tailwind.config.ts', 'tailwind.config.cjs', 'next.config.js', 'next.config.mjs', 'next.config.ts', 'nuxt.config.js', 'nuxt.config.ts', 'angular.json', 'angular-cli.json', 'vue.config.js', 'vue.config.cjs'])
mapSpecialFileIcons('file-type-vscode', ['.vscode/settings.json', '.vscode/launch.json', '.vscode/tasks.json'])
mapSpecialFileIcons('file-type-circleci', ['.circleci/config.yml'])
mapSpecialFileIcons('file-type-travis', ['.travis.yml'])
mapSpecialFileIcons('file-type-nuxt', ['nuxt.config.js', 'nuxt.config.ts'])

const specialDirectoryToVscodeIcon: Record<string, string> = {}

function mapSpecialDirectoryIcons(iconName: string, names: string[]) {
  for (const name of names) specialDirectoryToVscodeIcon[name.toLowerCase()] = `vscode-icons:${iconName}`
}

// AUDIT FIX [4.1-4.6/5.1-5.14]：特殊目录使用 VS Code Icons 的目录语义图标，
// 普通目录仍由 default-folder 渲染；映射只按目录名精确命中，不污染同名文件。
mapSpecialDirectoryIcons('folder-type-python', [
  '__pycache__', '.pytest_cache', '.mypy_cache', '.ruff_cache', '.tox', '.nox', '.nox_sessions',
  '.ipynb_checkpoints', '.ipython', '.jupyter',
])
mapSpecialDirectoryIcons('folder-type-node', [
  'node_modules', '.pnpm-store', '.yarn', '.yarn-cache', '.pnpm-state', '.bun', '.npm', '.nvm',
])
mapSpecialDirectoryIcons('folder-type-git', [
  '.git', '.svn', '.hg', '.bzr', '.git-lfs', '.git-annex', '.git-fat', '.git-media', '.git-annex-tmp',
])
mapSpecialDirectoryIcons('folder-type-github', ['.github', '.harness', '.gitea', '.gitee'])
mapSpecialDirectoryIcons('file-type-circleci', ['.circleci'])
mapSpecialDirectoryIcons('file-type-travis', ['.travis'])
mapSpecialDirectoryIcons('file-type-gitlab', ['.gitlab'])
mapSpecialDirectoryIcons('folder-type-vscode', ['.vscode', '.vscode-server'])
mapSpecialDirectoryIcons('folder-type-idea', ['.idea', '.eclipse', '.settings', '.project', '.classpath'])
mapSpecialDirectoryIcons('folder-type-dist', [
  'build', 'dist', 'target', 'out', 'bin', 'obj', 'cmake-build-debug', 'cmake-build-release',
])
mapSpecialDirectoryIcons('folder-type-library', [
  'vendor', '.bundle', '.bundle_cache', '.cargo', '.gradle', '.mvn', '.nuxt', '.next', '.angular',
  '.output', '.nitro', '.swiftpm', '.build', '.kotlin', '.scala-build', '.metals', '.ammonite',
  '.mill', '.rustup', '.cargo-husky',
])
mapSpecialDirectoryIcons('folder-type-test', [
  'coverage', '.nyc_output', '.jest-cache', '.jest_cache', '.playwright', '.cypress', '__tests__',
  'test', 'tests', 'spec', 'e2e', 'fixtures', 'mocks', 'snapshots', '.test-results', '.allure-results',
])
mapSpecialDirectoryIcons('folder-type-story', ['.storybook', '.changeset', '.husky'])
mapSpecialDirectoryIcons('folder-type-cypress', ['.cypress'])
mapSpecialDirectoryIcons('folder-type-kubernetes', ['.helm', '.k8s'])
mapSpecialDirectoryIcons('folder-type-docker', ['.docker', '.devcontainer'])
mapSpecialDirectoryIcons('folder-type-package', [
  '.terraform', '.serverless', '.aws-sam', '.sam', '.amplify', '.firebase', '.vercel', '.netlify', '.now',
  '.blitz', '.redwood', '.docusaurus', '.vuepress', '.dumi', '.gatsby', '.gridsome', '.sapper',
  '.parcel-cache', '.turbo', '.cache-loader', '.babel-cache', '.webpack-cache', '.typedoc', '.docz',
  '.wrangler', '.cloudflare', '.miniflare', '.deta', '.deno', '.kaggle', '.keras', '.tensorboard',
  '.mlflow', '.dvc', '.terraform', '.pypirc', '.config', '.local', '.nix-profile', '.nix-defexpr',
  '.guix-profile', '.homebrew', '.linuxbrew', '.cache',
])

export const FILE_ICON_SPECIAL_DIRECTORY_ICON_COUNT = Object.keys(specialDirectoryToVscodeIcon).length

const directoryKindFallbackIcon: Partial<Record<FileIconKind, string>> = {
  cache: 'vscode-icons:folder-type-library',
  package: 'vscode-icons:folder-type-package',
  git: 'vscode-icons:folder-type-git',
  ci: 'vscode-icons:folder-type-github',
  ide: 'vscode-icons:folder-type-vscode',
  build: 'vscode-icons:folder-type-dist',
  test: 'vscode-icons:folder-type-test',
  cloud: 'vscode-icons:folder-type-package',
  workflow: 'vscode-icons:folder-type-story',
  terraform: 'vscode-icons:folder-type-package',
  kubernetes: 'vscode-icons:folder-type-kubernetes',
}

// AUDIT FIX [3.1-3.26/4.2-4.10]：在不改变文件节点尺寸的前提下，为语言和配置文件
// 提供稳定的品牌缩写与颜色；特殊文件名仍由上面的精确匹配优先处理。
const languageMetadata: Record<string, { glyph: string; label: string; color: string }> = {
  py: { glyph: 'PY', label: 'Python 文件', color: '#3776ab' }, pyw: { glyph: 'PY', label: 'Python 文件', color: '#3776ab' },
  pyc: { glyph: 'PY', label: 'Python 字节码', color: '#3776ab' }, ipynb: { glyph: 'NB', label: 'Jupyter Notebook', color: '#f37626' },
  js: { glyph: 'JS', label: 'JavaScript 文件', color: '#f7df1e' }, mjs: { glyph: 'JS', label: 'JavaScript 模块', color: '#f7df1e' }, cjs: { glyph: 'JS', label: 'CommonJS 文件', color: '#f7df1e' },
  jsx: { glyph: '⚛', label: 'React JSX 文件', color: '#61dafb' }, ts: { glyph: 'TS', label: 'TypeScript 文件', color: '#3178c6' }, tsx: { glyph: '⚛', label: 'React TSX 文件', color: '#3178c6' },
  java: { glyph: 'JAVA', label: 'Java 文件', color: '#ed8b00' }, class: { glyph: 'JAVA', label: 'Java 字节码', color: '#ed8b00' },
  go: { glyph: 'GO', label: 'Go 文件', color: '#00add8' }, rs: { glyph: 'RS', label: 'Rust 文件', color: '#ce422b' },
  c: { glyph: 'C', label: 'C 文件', color: '#5c6bc0' }, h: { glyph: 'C', label: 'C 头文件', color: '#5c6bc0' }, cpp: { glyph: 'C++', label: 'C++ 文件', color: '#00599c' }, cc: { glyph: 'C++', label: 'C++ 文件', color: '#00599c' }, cxx: { glyph: 'C++', label: 'C++ 文件', color: '#00599c' }, hpp: { glyph: 'C++', label: 'C++ 头文件', color: '#00599c' },
  cs: { glyph: 'C#', label: 'C# 文件', color: '#68217a' }, php: { glyph: 'PHP', label: 'PHP 文件', color: '#777bb4' }, rb: { glyph: 'RB', label: 'Ruby 文件', color: '#cc342d' },
  swift: { glyph: 'SW', label: 'Swift 文件', color: '#f05138' }, kt: { glyph: 'KT', label: 'Kotlin 文件', color: '#7f52ff' }, kts: { glyph: 'KT', label: 'Kotlin Script 文件', color: '#7f52ff' }, scala: { glyph: 'SC', label: 'Scala 文件', color: '#dc322f' },
  m: { glyph: 'M', label: 'Objective-C 或 Matlab 文件', color: '#5b5bd6' }, mm: { glyph: 'M++', label: 'Objective-C++ 文件', color: '#438eff' }, pl: { glyph: 'PL', label: 'Perl 文件', color: '#39457e' }, pm: { glyph: 'PL', label: 'Perl 模块', color: '#39457e' },
  sh: { glyph: '$', label: 'Shell 脚本', color: '#4eaa25' }, bash: { glyph: '$', label: 'Bash 脚本', color: '#4eaa25' }, zsh: { glyph: '$', label: 'Zsh 脚本', color: '#4eaa25' }, fish: { glyph: '$', label: 'Fish 脚本', color: '#4eaa25' }, bat: { glyph: 'BAT', label: 'Batch 脚本', color: '#4d4d4d' }, cmd: { glyph: 'CMD', label: 'Windows CMD 脚本', color: '#4d4d4d' }, ps1: { glyph: 'PS', label: 'PowerShell 脚本', color: '#5391fe' },
  lua: { glyph: 'LUA', label: 'Lua 文件', color: '#000080' }, r: { glyph: 'R', label: 'R 文件', color: '#276dc3' }, sql: { glyph: 'SQL', label: 'SQL 文件', color: '#336791' },
  html: { glyph: 'HTML', label: 'HTML 文件', color: '#e34f26' }, htm: { glyph: 'HTML', label: 'HTML 文件', color: '#e34f26' }, css: { glyph: 'CSS', label: 'CSS 文件', color: '#1572b6' }, scss: { glyph: 'SCSS', label: 'SCSS 文件', color: '#c6538c' }, sass: { glyph: 'SASS', label: 'Sass 文件', color: '#c6538c' }, less: { glyph: 'LESS', label: 'Less 文件', color: '#1d365d' }, styl: { glyph: 'STYL', label: 'Stylus 文件', color: '#ff6347' },
  vue: { glyph: 'VUE', label: 'Vue 文件', color: '#42b883' }, svelte: { glyph: 'SV', label: 'Svelte 文件', color: '#ff3e00' }, xaml: { glyph: 'XAML', label: 'XAML 文件', color: '#0c54c2' }, dart: { glyph: 'DART', label: 'Dart 文件', color: '#0175c2' },
  ex: { glyph: 'EX', label: 'Elixir 文件', color: '#6e4a7e' }, exs: { glyph: 'EX', label: 'Elixir Script 文件', color: '#6e4a7e' }, erl: { glyph: 'ERL', label: 'Erlang 文件', color: '#a90533' }, hrl: { glyph: 'ERL', label: 'Erlang 头文件', color: '#a90533' },
  clj: { glyph: 'CLJ', label: 'Clojure 文件', color: '#5881d8' }, cljs: { glyph: 'CLJS', label: 'ClojureScript 文件', color: '#5881d8' }, cljc: { glyph: 'CLJC', label: 'Clojure Common 文件', color: '#5881d8' }, groovy: { glyph: 'GRV', label: 'Groovy 文件', color: '#4298b8' }, proto: { glyph: 'PROTO', label: 'Protobuf 文件', color: '#4285f4' }, sol: { glyph: 'SOL', label: 'Solidity 文件', color: '#363636' }, pas: { glyph: 'PAS', label: 'Pascal 文件', color: '#e3f171' }, pp: { glyph: 'PAS', label: 'Pascal 文件', color: '#e3f171' },
  vb: { glyph: 'VB', label: 'Visual Basic 文件', color: '#945db7' }, fs: { glyph: 'FS', label: 'F# 文件', color: '#378bba' }, fsx: { glyph: 'FS', label: 'F# Script 文件', color: '#378bba' }, jl: { glyph: 'JL', label: 'Julia 文件', color: '#9558b2' }, lisp: { glyph: 'LISP', label: 'Lisp 文件', color: '#3fb68b' }, lsp: { glyph: 'LSP', label: 'Lisp 文件', color: '#3fb68b' }, cl: { glyph: 'CL', label: 'Common Lisp 文件', color: '#3fb68b' },
  asm: { glyph: 'ASM', label: 'Assembly 文件', color: '#6e4c13' }, s: { glyph: 'ASM', label: 'Assembly 文件', color: '#6e4c13' }, zig: { glyph: 'ZIG', label: 'Zig 文件', color: '#f7a41d' }, pony: { glyph: 'PONY', label: 'Pony 文件', color: '#504b4b' }, nim: { glyph: 'NIM', label: 'Nim 文件', color: '#ffe953' }, v: { glyph: 'V', label: 'Verilog 文件', color: '#b2b7f8' }, vhdl: { glyph: 'VHDL', label: 'VHDL 文件', color: '#543978' }, vala: { glyph: 'VALA', label: 'Vala 文件', color: '#7239b3' },
  cob: { glyph: 'COB', label: 'COBOL 文件', color: '#005ca5' }, cbl: { glyph: 'COB', label: 'COBOL 文件', color: '#005ca5' }, f90: { glyph: 'F90', label: 'Fortran 文件', color: '#734f96' }, f95: { glyph: 'F95', label: 'Fortran 文件', color: '#734f96' }, f03: { glyph: 'F03', label: 'Fortran 文件', color: '#734f96' }, for: { glyph: 'F', label: 'Fortran 文件', color: '#734f96' }, f: { glyph: 'F', label: 'Fortran 文件', color: '#734f96' }, hs: { glyph: 'HS', label: 'Haskell 文件', color: '#5e5086' }, lhs: { glyph: 'HS', label: 'Literate Haskell 文件', color: '#5e5086' }, idr: { glyph: 'IDR', label: 'Idris 文件', color: '#b30000' }, agda: { glyph: 'AGDA', label: 'Agda 文件', color: '#315665' }, lean: { glyph: 'LEAN', label: 'Lean 文件', color: '#633f8c' }, coq: { glyph: 'COQ', label: 'Coq 文件', color: '#d04a00' }, mat: { glyph: 'MAT', label: 'Matlab 文件', color: '#e16737' }, rkt: { glyph: 'RKT', label: 'Racket 文件', color: '#3c5caa' }, scm: { glyph: 'SCM', label: 'Scheme 文件', color: '#1e4a8a' }, tcl: { glyph: 'TCL', label: 'Tcl 文件', color: '#e4cc98' }, awk: { glyph: 'AWK', label: 'AWK 脚本', color: '#4b5563' }, sed: { glyph: 'SED', label: 'sed 脚本', color: '#4b5563' },
  yaml: { glyph: 'YAML', label: 'YAML 配置', color: '#cb171e' }, yml: { glyph: 'YAML', label: 'YAML 配置', color: '#cb171e' }, json: { glyph: 'JSON', label: 'JSON 配置', color: '#f0b429' }, json5: { glyph: 'JSON5', label: 'JSON5 配置', color: '#f0b429' }, jsonc: { glyph: 'JSONC', label: 'JSONC 配置', color: '#f0b429' }, xml: { glyph: 'XML', label: 'XML 配置', color: '#f16529' }, toml: { glyph: 'TOML', label: 'TOML 配置', color: '#9c4221' }, ini: { glyph: 'INI', label: 'INI 配置', color: '#64748b' }, cfg: { glyph: 'CFG', label: '配置文件', color: '#0891b2' }, conf: { glyph: 'CONF', label: '配置文件', color: '#0891b2' }, properties: { glyph: 'PROP', label: 'Properties 配置', color: '#0891b2' }, env: { glyph: 'ENV', label: '环境配置', color: '#64748b' }, hcl: { glyph: 'HCL', label: 'HCL 配置', color: '#844fba' }, gradle: { glyph: 'GR', label: 'Gradle 配置', color: '#02303a' }, cmake: { glyph: 'CMAKE', label: 'CMake 配置', color: '#064f8c' },
}

const specialDirectoryToKind: Record<string, FileIconKind> = {}

function mapSpecialDirectories(kind: FileIconKind, names: string[]) {
  for (const name of names) specialDirectoryToKind[name.toLowerCase()] = kind
}

mapSpecialDirectories('cache', [
  '__pycache__', '.pytest_cache', '.mypy_cache', '.ruff_cache', '.tox', '.nox', '.nox_sessions', '.ipynb_checkpoints',
  '.pnpm-store', '.yarn-cache', '.parcel-cache', '.turbo', '.cache', '.prettier-cache', '.eslintcache', '.stylelintcache',
  '.vitest', '.vite', '.rollup.cache', '.rpt2_cache', '.tsbuildinfo', '.cache-loader', '.babel-cache', '.webpack-cache',
  '.bsp-cache', '.coverage', '.nyc_output', '.jest-cache', '.jest_cache', '.mocha_cache', '.karma_cache', '.test-results',
  '.allure-results', '.puppeteer-cache', '.electron-cache', '.deno-cache', '.node-gyp', '.python-eggs', '.eggs',
  '.pytest_cache', '.codecov', '.cargo-target', '.gradle-build', '.sbt-boot', '.bloop', '.cache-loader',
])
mapSpecialDirectories('package', [
  'node_modules', '.yarn', '.cargo', '.gradle', '.mvn', '.bundle', '.bundle_cache', 'vendor', '.nuxt', '.next',
  '.angular', '.output', '.nitro', '.swiftpm', '.build', '.kotlin', '.scala-build', '.metals', '.bsp', '.ammonite',
  '.pnpm-state', '.bun', '.npm', '.nvm', '.yarn-cache', '.cargo-husky',
])
mapSpecialDirectories('git', ['.git', '.svn', '.hg', '.bzr', '.git-lfs', '.git-annex', '.git-fat', '.git-media', '.git-annex-tmp'])
mapSpecialDirectories('ci', ['.github', '.circleci', '.travis', '.gitlab', '.harness', '.gitea', '.gitee', '.devcontainer', '.docker', '.changeset', '.husky'])
mapSpecialDirectories('ide', ['.vscode', '.idea', '.eclipse', '.settings', '.project', '.classpath', '.vscode-server', '.devbox', '.devenv'])
mapSpecialDirectories('build', ['build', 'dist', 'target', 'out', 'bin', 'obj', 'cmake-build-debug', 'cmake-build-release', '.electron-builder', '.terraform'])
mapSpecialDirectories('test', ['coverage', '.nyc_output', '.jest-cache', '.playwright', '.cypress', '__tests__', 'test', 'tests', 'spec', 'e2e', 'fixtures', 'mocks', 'snapshots', '.test-results', '.allure-results'])
mapSpecialDirectories('cloud', [
  '.serverless', '.aws-sam', '.sam', '.amplify', '.firebase', '.vercel', '.netlify', '.now', '.blitz', '.redwood',
  '.docusaurus', '.vuepress', '.dumi', '.gatsby', '.gridsome', '.sapper', '.wrangler', '.cloudflare', '.miniflare',
  '.deta', '.deno', '.kaggle', '.keras', '.tensorboard', '.mlflow', '.dvc', '.pypirc', '.local', '.config',
  '.nix-profile', '.nix-defexpr', '.guix-profile', '.homebrew', '.linuxbrew',
])
mapSpecialDirectories('cloud', ['.typedoc', '.docz', '.ipython', '.jupyter'])
mapSpecialDirectories('package', ['.rustup', '.mill'])
mapSpecialDirectories('workflow', ['.storybook', '.changeset'])

export const FILE_ICON_EXTENSION_COUNT = Object.keys(extensionToKind).length
export const FILE_ICON_SPECIAL_FILE_COUNT = Object.keys(specialFileToKind).length
export const FILE_ICON_SPECIAL_DIRECTORY_COUNT = Object.keys(specialDirectoryToKind).length

function basename(value: string): string {
  return value.replace(/\\/g, '/').split('/').filter(Boolean).pop() || value
}

function extension(value: string): string {
  const name = basename(value).toLowerCase()
  const dotIndex = name.lastIndexOf('.')
  if (dotIndex <= 0 || dotIndex === name.length - 1) return ''
  return name.slice(dotIndex + 1).replace(/[^a-z0-9]/gi, '').toLowerCase()
}

function escapeXml(value: string): string {
  return value.replace(/[&<>"']/g, (character) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&apos;' })[character] || character)
}

function hashColor(value: string): string {
  const palette = ['#2563eb', '#7c3aed', '#db2777', '#dc2626', '#ea580c', '#ca8a04', '#16a34a', '#0d9488', '#0891b2', '#4f46e5', '#9333ea', '#be123c', '#c2410c', '#65a30d', '#15803d', '#0369a1', '#4338ca', '#a21caf', '#9f1239', '#57534e']
  let hash = 0
  for (const character of value) hash = (hash * 31 + character.charCodeAt(0)) >>> 0
  return palette[hash % palette.length]
}

export function fileExtensionAbbreviation(fileName: string): string {
  const ext = extension(fileName).replace(/[^a-z0-9]/gi, '').toUpperCase()
  // 无扩展名按约定显示 FILE；有扩展名最多显示前三个字符。
  return ext ? ext.slice(0, 3) : 'FILE'
}

/**
 * 生成未知后缀的无外部依赖 SVG。
 * AUDIT FIX [6.1-6.24]：后缀经过字符白名单清洗与 XML 转义，结果按后缀稳定缓存。
 */
const dynamicSvgCache = new Map<string, string>()
export function createDynamicFileSvg(fileName: string): string {
  const cacheKey = fileExtensionAbbreviation(fileName)
  const cached = dynamicSvgCache.get(cacheKey)
  if (cached) return cached
  const glyph = escapeXml(cacheKey)
  const color = hashColor(cacheKey.toLowerCase())
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32" role="img" aria-label="${glyph} 文件"><path fill="${color}" d="M7 2h12l6 6v22H7z"/><path fill="#fff" opacity=".28" d="M19 2v7h6z"/><rect x="8.5" y="15" width="15" height="8" rx="2" fill="#fff" fill-opacity=".2"/><text x="16" y="21" text-anchor="middle" font-family="Arial,sans-serif" font-size="6.4" font-weight="700" fill="#fff">${glyph}</text></svg>`
  const dataUri = `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`
  dynamicSvgCache.set(cacheKey, dataUri)
  return dataUri
}

function cloneDefinition(kind: FileIconKind): FileIconDescriptor {
  return { ...definitions[kind] }
}

function specialFileKind(fileName: string, path: string): FileIconKind | undefined {
  const lowerName = fileName.toLowerCase()
  const lowerPath = path.replace(/\\/g, '/').toLowerCase()
  if (/^dockerfile(?:\..+)?$/.test(lowerName)) return 'docker'
  if (/^jenkinsfile(?:\..+)?$/.test(lowerName)) return 'ci'
  if (/^vagrantfile(?:\..+)?$/.test(lowerName)) return 'config'
  if (/^procfile(?:\..+)?$/.test(lowerName)) return 'workflow'
  if (lowerPath.includes('.github/workflows/')) return 'ci'
  if (lowerPath.includes('.vscode/')) return 'ide'
  if (lowerPath.includes('.idea/')) return 'ide'
  return specialFileToKind[lowerName]
}

function specialFileIconName(fileName: string, path: string): string | undefined {
  const lowerName = basename(fileName).toLowerCase()
  const lowerPath = path.replace(/\\/g, '/').toLowerCase()
  if (/^dockerfile(?:\..+)?$/.test(lowerName)) return 'vscode-icons:file-type-docker'
  if (/^jenkinsfile(?:\..+)?$/.test(lowerName)) return 'vscode-icons:file-type-jenkins'
  if (lowerPath.includes('.github/workflows/')) return 'vscode-icons:folder-type-github'
  if (lowerPath.includes('.vscode/')) return 'vscode-icons:file-type-vscode'
  if (lowerPath.includes('.idea/')) return 'vscode-icons:folder-type-idea'
  return specialFileToVscodeIcon[lowerName]
}

function kindFromMime(mimeType: string): FileIconKind | undefined {
  const mime = mimeType.toLowerCase()
  if (mime.startsWith('image/')) return 'image'
  if (mime.startsWith('video/')) return 'video'
  if (mime.startsWith('audio/')) return 'audio'
  if (mime === 'application/pdf') return 'pdf'
  if (mime.includes('word')) return 'word'
  if (mime.includes('spreadsheet') || mime.includes('excel')) return 'excel'
  if (mime.includes('presentation') || mime.includes('powerpoint')) return 'powerpoint'
  if (mime.includes('zip') || mime.includes('compressed') || mime.includes('archive')) return 'archive'
  if (mime.startsWith('text/')) return 'text'
  return undefined
}

export function resolveFileTypeIcon(options: {
  fileName: string
  path?: string
  isDirectory?: boolean
  mimeType?: string
}): FileIconDescriptor {
  const fileName = options.fileName || ''
  const path = options.path || fileName
  if (options.isDirectory) {
    const directoryName = basename(fileName).toLowerCase()
    const directoryKind = specialDirectoryToKind[directoryName]
    const descriptor = cloneDefinition(directoryKind ? 'folder-special' : 'folder')
    descriptor.iconName = specialDirectoryToVscodeIcon[directoryName] || (directoryKind ? directoryKindFallbackIcon[directoryKind] : descriptor.iconName)
    if (directoryKind) {
      descriptor.kind = 'folder-special'
      descriptor.glyph = definitions[directoryKind].glyph
      descriptor.label = `${fileName} 特殊目录`
      descriptor.color = definitions[directoryKind].color
      descriptor.faClass = definitions[directoryKind].faClass
      descriptor.legacyColorClass = definitions[directoryKind].legacyColorClass
    }
    return descriptor
  }

  const specialKind = specialFileKind(basename(fileName), path)
  const fileExtension = extension(fileName)
  const knownKind = specialKind || extensionToKind[fileExtension] || kindFromMime(options.mimeType || '')
  if (knownKind) {
    const descriptor = cloneDefinition(knownKind)
    let iconName = specialFileIconName(fileName, path) || extensionToVscodeIcon[fileExtension]
    if (fileExtension === 'm' && /(^|\/)\s*(matlab|octave)(\/|$)/i.test(path)) iconName = 'vscode-icons:file-type-matlab'
    descriptor.iconName = iconName || descriptor.iconName
    const metadata = !specialKind ? languageMetadata[fileExtension] : undefined
    if (metadata) {
      descriptor.glyph = metadata.glyph
      descriptor.label = metadata.label
      descriptor.color = metadata.color
    }
    return descriptor
  }

  const descriptor = cloneDefinition('unknown')
  descriptor.glyph = fileExtensionAbbreviation(fileName)
  descriptor.label = `${descriptor.glyph} 文件`
  descriptor.dynamicSvg = createDynamicFileSvg(fileName)
  descriptor.color = hashColor(descriptor.glyph.toLowerCase())
  return descriptor
}

export function getFileIconDefinition(fileName: string, isDirectory = false, path = fileName, mimeType = ''): FileIconDescriptor {
  return resolveFileTypeIcon({ fileName, isDirectory, path, mimeType })
}

export const FILE_ICON_EXTENSION_MAP = extensionToKind
export const FILE_ICON_SPECIAL_FILE_MAP = specialFileToKind
export const FILE_ICON_SPECIAL_DIRECTORY_MAP = specialDirectoryToKind
export const FILE_ICON_VSCODE_EXTENSION_MAP = extensionToVscodeIcon
export const FILE_ICON_VSCODE_SPECIAL_FILE_MAP = specialFileToVscodeIcon
export const FILE_ICON_VSCODE_SPECIAL_DIRECTORY_MAP = specialDirectoryToVscodeIcon
