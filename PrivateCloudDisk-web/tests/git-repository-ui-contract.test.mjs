/**
 * [REQ-GIT-UIUX-20260816] Git 公开仓库页面的静态交付契约。
 * 真实 Git HTTP、浏览器 Blob 与响应式交互应在已部署的 Git/Platform/Storage 环境中做 E2E 验收；
 * 此处保证构建前不会回退为平铺列表、JSON 二进制预览或线性提交列表。
 */
import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'

const read = (path) => readFile(new URL(`../${path}`, import.meta.url), 'utf8')
const readGitService = (path) => readFile(new URL(`../../PrivateCloudDisk-git-service/${path}`, import.meta.url), 'utf8')

test('Git 公开仓库使用独立工作区、双栏文件树和移动端回退布局', async () => {
  const [panel, workspace, tree, viewer] = await Promise.all([
    read('src/views/public-space/GitRepositoryPanel.vue'),
    read('src/components/git/GitCodeWorkspace.vue'),
    read('src/components/git/GitFileTree.vue'),
    read('src/components/git/GitFileViewer.vue'),
  ])
  assert.match(panel, /GitCodeWorkspace/)
  assert.match(panel, /resource_type=git/)
  assert.match(workspace, /git-code-workspace__body/)
  assert.match(workspace, /@media \(max-width:1023px\)/)
  assert.match(tree, /visibleRows/)
  assert.match(tree, /@keydown="onKeydown"/)
  assert.match(viewer, /getGitRawBlobApi/)
  assert.match(viewer, /getGitBlameApi/)
  assert.match(viewer, /MarkdownPreview/)
  assert.match(viewer, /PdfPreview/)
})

test('提交历史以父子关系 Git Graph 呈现并支持分页、筛选和 Diff', async () => {
  const [graph, graphModel, api] = await Promise.all([
    read('src/components/git/GitCommitGraph.vue'),
    read('src/utils/gitCommitGraph.ts'),
    read('src/api/modules/git.ts'),
  ])
  assert.match(graph, /buildGitGraphRows/)
  assert.match(graph, /fetchPage\(true\)/)
  assert.match(graph, /getGitDiffApi/)
  assert.match(graph, /filePath/)
  assert.match(graphModel, /commit\.parents/)
  assert.match(graphModel, /GitGraphEdge/)
  assert.match(api, /path\?: string/)
  assert.match(api, /getGitDiffApi/)
})

test('Git Service 将 JSON 文本预览、原始文件流与 ZIP 归档分离并保持权限边界', async () => {
  const [content, auth, config] = await Promise.all([
    readGitService('internal/api/repository_content.go'),
    readGitService('internal/auth/authorization.go'),
    readGitService('internal/config/config.go'),
  ])
  assert.match(content, /ReadBlobPreview/)
  assert.match(content, /readRawBlob/)
  assert.match(content, /auth\.Fetch/)
  assert.match(content, /StreamRawBlob/)
  assert.match(content, /downloadArchive/)
  assert.match(content, /manager\.Archive/)
  assert.match(auth, /AllowPublicBrowse && space\.AllowPublicDownload/)
  assert.match(config, /GIT_MAX_RAW_FILE_BYTES/)
})

test('仓库页面以真实数据呈现 MR、自动化和洞察，不回填虚构 Star 或 Fork', async () => {
  const [panel, api, domain] = await Promise.all([
    read('src/views/public-space/GitRepositoryPanel.vue'),
    read('src/api/modules/git.ts'),
    readGitService('internal/domain/models.go'),
  ])
  assert.match(panel, /pcd\.git\.push\.completed\.v1/)
  assert.match(panel, /listGitMergeRequestCommentsApi/)
  assert.match(panel, /getGitRepositoryInsightsApi/)
  assert.doesNotMatch(panel, /Star 数|Fork 数/)
  assert.match(api, /createGitMergeRequestCommentApi/)
  assert.match(domain, /type RepositoryInsights struct/)
})
