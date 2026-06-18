<template>
  <div class="flex min-h-screen flex-col bg-neutral-50">
    <!-- 顶部导航栏 -->
    <header class="border-b border-neutral-200 bg-white">
      <div class="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
        <div class="flex items-center gap-3">
          <div class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary">
            <i class="fa fa-cloud text-white"></i>
          </div>
          <span class="text-lg font-bold text-neutral-700">PrivateCloudDisk</span>
        </div>
        <div class="flex items-center gap-3">
          <router-link
            to="/login"
            class="rounded-lg border border-neutral-200 px-4 py-1.5 text-sm text-neutral-600 hover:bg-neutral-50"
          >
            登录
          </router-link>
          <router-link
            to="/register"
            class="rounded-lg bg-primary px-4 py-1.5 text-sm text-white hover:bg-primary/90"
          >
            注册
          </router-link>
        </div>
      </div>
    </header>

    <!-- 主内容区 -->
    <main class="flex flex-1 items-start justify-center px-4 py-8 sm:py-16">
      <div class="w-full max-w-2xl">
        <!-- 加载状态 -->
        <div v-if="loading" class="flex flex-col items-center py-20">
          <div class="h-10 w-10 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
          <p class="mt-4 text-neutral-500">正在加载分享...</p>
        </div>

        <!-- 错误状态 -->
        <div v-else-if="errorMessage" class="rounded-xl bg-white p-10 text-center shadow-sm">
          <div class="mb-4 flex justify-center">
            <div class="flex h-16 w-16 items-center justify-center rounded-full bg-danger/10">
              <i class="fa fa-exclamation-triangle text-3xl text-danger"></i>
            </div>
          </div>
          <h2 class="text-xl font-bold text-neutral-700">{{ errorMessage }}</h2>
          <p class="mt-2 text-neutral-500">{{ errorDetail }}</p>
          <button
            @click="router.push('/')"
            class="mt-6 rounded-lg bg-primary px-6 py-2 text-white hover:bg-primary/90"
          >
            返回首页
          </button>
        </div>

        <!-- 密码输入界面 -->
        <div v-else-if="showPasswordScreen" class="rounded-xl bg-white p-8 shadow-sm sm:p-10">
          <div class="mb-6 text-center">
            <div class="mb-4 flex justify-center">
              <div class="flex h-16 w-16 items-center justify-center rounded-full bg-primary/10">
                <i class="fa fa-lock text-3xl text-primary"></i>
              </div>
            </div>
            <h2 class="text-xl font-bold text-neutral-700">{{ shareInfo?.share_name || '分享文件' }}</h2>
            <p class="mt-1 text-neutral-500">
              来自 {{ shareInfo?.owner_name || '未知用户' }} 的分享
            </p>
            <div class="mt-3 flex items-center justify-center gap-4 text-xs text-neutral-400">
              <span>
                <i :class="shareInfo?.share_target_type === 'folder' ? 'fa fa-folder' : 'fa fa-file'" class="mr-1"></i>
                {{ shareInfo?.target_name || '未知资源' }}
              </span>
              <span v-if="shareInfo?.target_size">
                <i class="fa fa-database mr-1"></i>{{ formatSize(shareInfo.target_size) }}
              </span>
            </div>
          </div>

          <div class="mx-auto max-w-sm">
            <p class="mb-3 text-center text-sm text-neutral-500">
              请输入提取码以访问此分享内容
            </p>
            <div class="flex gap-2">
              <input
                v-model="passwordInput"
                type="text"
                class="flex-1 rounded-lg border border-neutral-200 px-4 py-2.5 text-center text-lg tracking-widest focus:ring-2 focus:ring-primary/30"
                :class="{ 'border-danger': passwordError }"
                placeholder="请输入提取码"
                maxlength="20"
                @keyup.enter="submitPassword"
              />
              <button
                @click="submitPassword"
                :disabled="passwordVerifying || !passwordInput"
                class="rounded-lg bg-primary px-6 py-2.5 text-white hover:bg-primary/90 disabled:opacity-50"
              >
                {{ passwordVerifying ? '验证中...' : '提取文件' }}
              </button>
            </div>
            <p v-if="passwordError" class="mt-2 text-center text-sm text-danger">{{ passwordError }}</p>
          </div>
        </div>

        <!-- 分享内容展示界面 -->
        <div v-else-if="shareContent" class="rounded-xl bg-white shadow-sm">
          <!-- 分享信息卡片 -->
          <div class="border-b border-neutral-100 p-6">
            <div class="flex items-start justify-between">
              <div class="min-w-0 flex-1">
                <h1 class="truncate text-xl font-bold text-neutral-700">
                  {{ shareContent.share_name || '分享内容' }}
                </h1>
                <div class="mt-2 flex flex-wrap items-center gap-3 text-sm text-neutral-500">
                  <span class="flex items-center gap-1">
                    <i class="fa fa-user-circle"></i>
                    {{ shareContent.owner_name || '未知用户' }}
                  </span>
                  <span class="flex items-center gap-1">
                    <i :class="shareContent.share_target_type === 'folder' ? 'fa fa-folder' : 'fa fa-file'"></i>
                    {{ shareContent.target_name || '未知资源' }}
                  </span>
                  <span v-if="shareContent.target_size" class="flex items-center gap-1">
                    <i class="fa fa-database"></i>
                    {{ formatSize(shareContent.target_size) }}
                  </span>
                  <span v-if="shareContent.file_type" class="flex items-center gap-1">
                    <i class="fa fa-file-o"></i>
                    {{ shareContent.file_type }}
                  </span>
                </div>
              </div>
              <div class="flex shrink-0 items-center gap-2">
                <button
                  v-if="shareContent.share_target_type === 'file'"
                  @click="downloadFile"
                  class="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-white hover:bg-primary/90"
                >
                  <i class="fa fa-download"></i> 下载
                </button>
              </div>
            </div>
          </div>

          <!-- 文件列表 / 文件夹内容 -->
          <div class="p-6">
            <!-- 单文件展示 -->
            <div v-if="shareContent.share_target_type === 'file'" class="flex flex-col items-center py-10">
              <div class="mb-4 flex h-20 w-20 items-center justify-center rounded-xl bg-primary/10">
                <i class="fa fa-file text-4xl text-primary"></i>
              </div>
              <p class="text-lg font-medium text-neutral-700">{{ shareContent.target_name }}</p>
              <p class="mt-1 text-sm text-neutral-500">
                {{ shareContent.file_type || '未知类型' }} — {{ formatSize(shareContent.target_size) }}
              </p>
              <button
                @click="downloadFile"
                class="mt-6 flex items-center gap-2 rounded-lg bg-primary px-6 py-2.5 text-white hover:bg-primary/90"
              >
                <i class="fa fa-download"></i> 下载文件
              </button>
            </div>

            <!-- 文件夹内容展示 -->
            <div v-else>
              <!-- 面包屑导航 -->
              <div v-if="folderBreadcrumb.length > 1" class="mb-4 flex items-center gap-1 text-sm">
                <button
                  v-for="(crumb, idx) in folderBreadcrumb"
                  :key="crumb.node_id"
                  @click="navigateToFolder(crumb.node_id)"
                  class="rounded px-1.5 py-0.5"
                  :class="idx === folderBreadcrumb.length - 1 ? 'font-medium text-neutral-700' : 'text-primary hover:bg-primary/5'"
                >
                  {{ crumb.name }}
                </button>
                <span v-if="folderBreadcrumb.length > 1" class="text-neutral-300 mx-0.5">
                  <i class="fa fa-angle-right text-xs"></i>
                </span>
              </div>

              <div class="mb-4 flex items-center gap-2 text-sm text-neutral-500">
                <i class="fa fa-folder-open"></i>
                <span>文件夹内容</span>
                <span v-if="folderContents.length > 0" class="text-neutral-400">
                  ({{ folderContents.length }} 项)
                </span>
              </div>

              <div v-if="folderContentsLoading" class="flex justify-center py-10">
                <div class="h-8 w-8 animate-spin rounded-full border-4 border-primary border-t-transparent"></div>
              </div>

              <div v-else-if="folderContents.length === 0" class="py-10 text-center text-neutral-400">
                <i class="fa fa-folder-open mb-2 text-4xl"></i>
                <p>此文件夹为空</p>
              </div>

              <div v-else class="space-y-1">
                <div
                  v-for="item in folderContents"
                  :key="item.file_id || item.node_id"
                  class="flex items-center gap-3 rounded-lg px-3 py-2.5 hover:bg-neutral-50"
                >
                  <div
                    class="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg"
                    :class="item.item_type === 'folder' ? 'bg-warning/10' : 'bg-primary/10'"
                  >
                    <i
                      :class="item.item_type === 'folder' ? 'fa fa-folder text-warning' : 'fa fa-file text-primary'"
                    ></i>
                  </div>
                  <div class="min-w-0 flex-1">
                    <p
                      class="truncate text-sm font-medium"
                      :class="item.item_type === 'folder' ? 'cursor-pointer text-primary hover:underline' : 'text-neutral-700'"
                      @click="item.item_type === 'folder' && navigateToFolder(item.node_id!)"
                    >
                      {{ item.name }}
                    </p>
                    <p class="text-xs text-neutral-400">
                      {{ item.item_type === 'file' ? formatSize(item.size) : '文件夹' }}
                      <span v-if="item.file_type"> · {{ item.file_type }}</span>
                    </p>
                  </div>
                  <button
                    v-if="item.item_type === 'file'"
                    @click="downloadSharedFileItem(item.file_id!)"
                    class="flex items-center gap-1 rounded-lg border border-primary px-3 py-1 text-xs text-primary hover:bg-primary/5"
                  >
                    <i class="fa fa-download"></i> 下载
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </main>

    <!-- 底部 -->
    <footer class="border-t border-neutral-200 bg-white py-4 text-center text-xs text-neutral-400">
      PrivateCloudDisk — 安全、私密的个人云存储
    </footer>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  getShareInfoApi,
  verifySharePasswordApi,
  getShareContentApi,
  getSharedFolderChildrenApi,
  formatFileSize,
  type ShareAccessInfo,
  type ShareContent,
  type ShareContentItem,
} from '@/api/modules/shares'

const route = useRoute()
const router = useRouter()

const shareToken = route.params.token as string

// 状态
const loading = ref(true)
const errorMessage = ref('')
const errorDetail = ref('')
const showPasswordScreen = ref(false)
const shareInfo = ref<ShareAccessInfo | null>(null)
const shareContent = ref<ShareContent | null>(null)
const accessToken = ref('')

// 密码输入
const passwordInput = ref('')
const passwordError = ref('')
const passwordVerifying = ref(false)

// 文件夹内容
const folderContents = ref<ShareContentItem[]>([])
const folderContentsLoading = ref(false)
const currentFolderNodeId = ref<string | null>(null)
const folderBreadcrumb = ref<{ node_id: string; name: string }[]>([])

const formatSize = (bytes: number) => formatFileSize(bytes)

/**
 * 初始化加载分享信息
 */
onMounted(async () => {
  try {
    const info = await getShareInfoApi(shareToken)
    shareInfo.value = info

    if (info.is_revoked) {
      errorMessage.value = '该分享链接已被撤销'
      errorDetail.value = '分享者已取消此分享，链接已失效'
      loading.value = false
      return
    }

    if (info.is_expired) {
      errorMessage.value = '该分享链接已过期'
      errorDetail.value = '分享链接超过了有效期，请联系分享者重新分享'
      loading.value = false
      return
    }

    if (info.has_password) {
      showPasswordScreen.value = true
      loading.value = false
      return
    }

    // 无密码，直接获取访问令牌并加载内容
    await autoAccess()
  } catch (e: any) {
    errorMessage.value = '分享链接不存在'
    errorDetail.value = '请检查链接是否正确，或联系分享者'
    loading.value = false
  }
})

/**
 * 无密码时自动获取访问令牌
 */
const autoAccess = async () => {
  try {
    // 无密码分享，送空密码
    const token = await verifySharePasswordApi(shareToken, '')
    accessToken.value = token
    await loadShareContent()
  } catch (e: any) {
    errorMessage.value = '无法访问分享内容'
    errorDetail.value = e?.message || '请稍后重试'
    loading.value = false
  }
}

/**
 * 提交密码验证
 */
const submitPassword = async () => {
  if (!passwordInput.value || passwordVerifying.value) return
  passwordError.value = ''
  passwordVerifying.value = true

  try {
    const hashedPassword = await preHashPassword(passwordInput.value)
    const token = await verifySharePasswordApi(shareToken, hashedPassword)
    accessToken.value = token
    showPasswordScreen.value = false
    await loadShareContent()
  } catch (e: any) {
    passwordError.value = e?.message || '提取码错误，请重试'
  } finally {
    passwordVerifying.value = false
  }
}

/**
 * 客户端 PBKDF2-SHA256 密码预哈希
 * 使用固定盐值，与 CreateShareDialog 保持一致。
 * 服务端额外使用 BCrypt 二次哈希，提供实际安全性。
 */
async function preHashPassword(rawPassword: string): Promise<string> {
  const encoder = new TextEncoder()
  const salt = encoder.encode('pcd-share-salt-v1')
  const keyMaterial = await crypto.subtle.importKey(
    'raw',
    encoder.encode(rawPassword),
    'PBKDF2',
    false,
    ['deriveBits']
  )
  const derivedBits = await crypto.subtle.deriveBits(
    {
      name: 'PBKDF2',
      salt,
      iterations: 100000,
      hash: 'SHA-256',
    },
    keyMaterial,
    256
  )
  const hashArray = Array.from(new Uint8Array(derivedBits))
  return hashArray.map((b) => b.toString(16).padStart(2, '0')).join('')
}

/**
 * 加载分享内容
 */
const loadShareContent = async () => {
  loading.value = true
  try {
    const content = await getShareContentApi(shareToken, accessToken.value)
    shareContent.value = content

    if (content.share_target_type === 'folder') {
      // 初始化面包屑
      folderBreadcrumb.value = [{ node_id: '', name: content.target_name || '分享文件夹' }]
      await loadFolderContents(null)
    }
  } catch (e: any) {
    errorMessage.value = '加载分享内容失败'
    errorDetail.value = e?.message || '请刷新页面重试'
  } finally {
    loading.value = false
  }
}

/**
 * 加载文件夹内容
 */
const loadFolderContents = async (node_id: string | null) => {
  folderContentsLoading.value = true
  try {
    const items = await getSharedFolderChildrenApi(shareToken, node_id, accessToken.value)
    folderContents.value = items
    currentFolderNodeId.value = node_id
  } catch (e) {
    console.error('加载文件夹内容失败:', e)
    folderContents.value = []
  } finally {
    folderContentsLoading.value = false
  }
}

/**
 * 导航到子文件夹
 */
const navigateToFolder = async (node_id: string) => {
  // 如果点击的是面包屑中的某一级
  const idx = folderBreadcrumb.value.findIndex((c) => c.node_id === node_id)
  if (idx >= 0) {
    // 截断面包屑
    folderBreadcrumb.value = folderBreadcrumb.value.slice(0, idx + 1)
    // 如果点击的是根节点（node_id 为空），传 null
    const targetId = node_id || null
    await loadFolderContents(targetId)
    return
  }

  // 进入子文件夹，需要先获取名称
  await loadFolderContents(node_id)
  // 从当前内容中找到名称并添加到面包屑
  const targetItem = folderContents.value.find((item) => item.node_id === node_id)
  if (targetItem) {
    folderBreadcrumb.value.push({ node_id: node_id, name: targetItem.name })
  }
}

/**
 * 下载分享的文件（单文件分享）
 */
const downloadFile = async () => {
  if (!shareContent.value || shareContent.value.share_target_type !== 'file') return
  const fileId = shareContent.value.share_file_id
  if (!fileId) return
  await downloadSharedFileItem(fileId)
}

/**
 * 下载文件夹内的文件
 */
const downloadSharedFileItem = async (file_id: string) => {
  try {
    const resp = await fetch(
      `/api/v1/public/shares/${shareToken}/files/${file_id}/download`,
      {
        headers: { 'X-Share-Access-Token': accessToken.value }
      }
    )
    if (!resp.ok) throw new Error('下载失败')
    const data = await resp.json()
    // 如果有实际下载 URL，触发下载；否则提示
    if (data.data?.download_url) {
      window.open(data.data.download_url, '_blank')
    } else {
      console.log('文件信息:', data.data)
    }
  } catch (e) {
    console.error('下载文件失败:', e)
  }
}
</script>