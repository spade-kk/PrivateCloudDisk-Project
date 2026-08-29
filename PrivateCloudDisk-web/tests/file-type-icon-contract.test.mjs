import assert from 'node:assert/strict'
import test from 'node:test'
import { readFile } from 'node:fs/promises'
import { pathToFileURL } from 'node:url'
import path from 'node:path'

const sourceRoot = path.resolve(process.cwd(), 'src')
const icons = await import(pathToFileURL(path.join(sourceRoot, 'utils/fileTypeIcons.ts')).href)
const registrySource = await readFile(path.join(sourceRoot, 'utils/vscodeFileIconRegistry.ts'), 'utf8')

test('文件图标目录覆盖扩展名、特殊文件和特殊目录', () => {
  assert.ok(icons.FILE_ICON_EXTENSION_COUNT >= 100)
  assert.ok(icons.FILE_ICON_SPECIAL_FILE_COUNT >= 80)
  assert.ok(icons.FILE_ICON_SPECIAL_DIRECTORY_COUNT >= 80)
})

test('VS Code Icons 直接使用 npm 离线 JSON，不维护重复数据集', () => {
  assert.match(registrySource, /@iconify-json\/vscode-icons\/icons\.json/)
  assert.doesNotMatch(registrySource, /vscodeFileIconData/)
})

test('特殊文件名优先于普通后缀，并识别路径级 CI/IDE 文件', () => {
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'Dockerfile.override.yml' }).kind, 'docker')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'Dockerfile.override.yml' }).iconName, 'vscode-icons:file-type-docker')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'build.yml', path: '.github/workflows/build.yml' }).kind, 'ci')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'build.yml', path: '.github/workflows/build.yml' }).iconName, 'vscode-icons:folder-type-github')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'settings.json', path: '.vscode/settings.json' }).kind, 'ide')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'settings.json', path: '.vscode/settings.json' }).iconName, 'vscode-icons:file-type-vscode')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'README.md' }).kind, 'markdown')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'README.md' }).iconName, 'vscode-icons:file-type-markdown')
})

test('媒体、Office、二进制和目录类型稳定解析', () => {
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'cover.png' }).kind, 'image')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'clip.mp4' }).kind, 'video')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'sound.flac' }).kind, 'audio')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'brief.docx' }).kind, 'word')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'table.xlsx' }).kind, 'excel')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'app.exe' }).kind, 'binary')
  assert.equal(icons.resolveFileTypeIcon({ fileName: '__pycache__', isDirectory: true }).kind, 'folder-special')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'node_modules', isDirectory: true }).glyph, 'PKG')
})

test('语言与配置文件保留可辨识的品牌缩写，特殊文件名仍优先', () => {
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'main.py' }).glyph, 'PY')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'main.ts' }).glyph, 'TS')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'main.go' }).glyph, 'GO')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'config.yaml' }).glyph, 'YAML')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'package.json' }).kind, 'package')
  assert.equal(icons.resolveFileTypeIcon({ fileName: '.typedoc', isDirectory: true }).kind, 'folder-special')
})

test('识别的语言与工具类型全部使用本地 VS Code Icons，未知类型才使用动态 SVG', () => {
  const samples = [
    ['main.PY', false, 'vscode-icons:file-type-python'],
    ['component.tsx', false, 'vscode-icons:file-type-reactjs'],
    ['script.BAT', false, 'vscode-icons:file-type-powershell'],
    ['server.go', false, 'vscode-icons:file-type-go'],
    ['styles.css', false, 'vscode-icons:file-type-css'],
    ['vite.config.ts', false, 'vscode-icons:file-type-vite'],
    ['.git', true, 'vscode-icons:folder-type-git'],
    ['node_modules', true, 'vscode-icons:folder-type-node'],
  ]
  for (const [fileName, isDirectory, iconName] of samples) {
    assert.equal(icons.resolveFileTypeIcon({ fileName, isDirectory }).iconName, iconName)
  }
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'movie.ts' }).kind, 'code')
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'movie.ts' }).iconName, 'vscode-icons:file-type-typescript-official')
  assert.ok(Object.keys(icons.FILE_ICON_VSCODE_EXTENSION_MAP).length >= 100)
})

test('列出的特殊目录均有 VS Code Icons 语义图标且普通目录仍使用文件夹图标', () => {
  for (const directoryName of Object.keys(icons.FILE_ICON_SPECIAL_DIRECTORY_MAP)) {
    const descriptor = icons.resolveFileTypeIcon({ fileName: directoryName, isDirectory: true })
    assert.equal(descriptor.kind, 'folder-special')
    assert.match(descriptor.iconName, /^vscode-icons:/)
  }
  assert.equal(icons.resolveFileTypeIcon({ fileName: 'src', isDirectory: true }).iconName, 'vscode-icons:default-folder')
})

test('未知后缀生成稳定、无外部请求的 SVG data URI', () => {
  const first = icons.resolveFileTypeIcon({ fileName: 'example.sss' })
  const second = icons.resolveFileTypeIcon({ fileName: 'other.sss' })
  const noExtension = icons.resolveFileTypeIcon({ fileName: 'LICENSE.custom-format' })
  assert.equal(first.kind, 'unknown')
  assert.equal(first.glyph, 'SSS')
  assert.equal(first.dynamicSvg, second.dynamicSvg)
  assert.match(first.dynamicSvg, /^data:image\/svg\+xml;charset=UTF-8,/) 
  assert.equal(icons.fileExtensionAbbreviation('no-extension'), 'FILE')
  assert.equal(icons.fileExtensionAbbreviation('strange.a-b$cd'), 'ABC')
  assert.equal(icons.fileExtensionAbbreviation('strange.$$$'), 'FILE')
  assert.ok(noExtension.dynamicSvg)
  assert.doesNotMatch(decodeURIComponent(first.dynamicSvg), /<script|onload=/i)
})

const componentSource = await readFile(path.join(sourceRoot, 'components/file/FileTypeIcon.vue'), 'utf8')

test('[REQ-GIT-ICON-20260818] FileTypeIcon 提供 colorMode/customColor 与单色类，默认 full-color 保持兼容', () => {
  assert.match(componentSource, /colorMode\?: FileIconColorMode/)
  assert.match(componentSource, /customColor\?: string/)
  assert.match(componentSource, /export type FileIconColorMode = \'full-color\' \| \'monochrome\' \| \'monochrome-inverse\' \| \'github\'/)
  // 单色与反色类、主题灰/反色变量均已接入。
  assert.match(componentSource, /file-type-icon--mono/)
  assert.match(componentSource, /file-type-icon--inverse/)
  assert.match(componentSource, /--git-icon-color/)
  assert.match(componentSource, /--git-icon-color-inverse/)
  // 动态 SVG 走主题滤镜降级，库图标走 currentColor 覆盖，未套用会把主题灰压黑的 filter。
  assert.match(componentSource, /fill: currentColor !important/)
  assert.match(componentSource, /var\(--git-icon-filter/)
})

test('[REQ-GIT-ICON-20260818] Git 仓库页提供单色 provide 并定义主题灰变量，其他页面不受影响', async () => {
  const panel = await readFile(path.join(sourceRoot, 'views/public-space/GitRepositoryPanel.vue'), 'utf8')
  assert.match(panel, /provide\(\'gitIconColorMode\', \'monochrome\'\)/)
  assert.match(panel, /--git-icon-color-light:#57606a/)
  assert.match(panel, /--git-icon-color-dark:#c9d1d9/)
  assert.match(panel, /--git-icon-color:var\(--git-icon-color-light\)/)
  assert.match(panel, /:global\(\.dark\) \.git-repository-panel/)
  const fileTree = await readFile(path.join(sourceRoot, 'components/git/GitFileTree.vue'), 'utf8')
  assert.match(fileTree, /color-mode="monochrome"/)
  assert.match(fileTree, /git-file-tree__row\.is-selected[^}]*--git-icon-color:#0969da/)
  const viewer = await readFile(path.join(sourceRoot, 'components/git/GitFileViewer.vue'), 'utf8')
  assert.match(viewer, /color-mode="monochrome"/)
})
