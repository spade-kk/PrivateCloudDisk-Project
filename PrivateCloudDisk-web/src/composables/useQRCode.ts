// ============================================================
// useQRCode.ts — 前端 QR 二维码生成 composable（企业级 Canvas 渲染）
// ============================================================
// 核心设计：
// - 后端只提供授权链接，前端使用 qrcode 库生成二维码
// - Canvas 渲染：二维码绘制到 Canvas 上，中央嵌入企业品牌 Logo
// - 使用 H 级纠错（30% 容错），确保 Logo 遮挡后仍可正常扫码
// - 防拖拽/防下载：Canvas 元素禁止右键、禁止拖拽、禁止选中
// - 支持自动刷新、手动刷新、测试模式
// - 支持自动刷新：二维码过期后自动重新获取链接并生成
// - 支持手动刷新：点击刷新按钮重新获取授权
// - 内置设备授权轮询：二维码生成后自动轮询授权状态

//
// 流程：
// 1. 调用后端接口获取 device_code + user_code + verification_uri
// 2. 前端使用 qrcode 库渲染二维码到 Canvas + 绘制中心 Logo
// 3. 开始轮询 /oauth2/device/token 检查授权状态
// 4. 用户扫码确认后，轮询获得 tokens，完成登录
// ============================================================

import { ref, onBeforeUnmount } from 'vue'
import QRCode from 'qrcode'
import { deviceAuthInitApi, deviceAuthPollApi } from '@/api/modules/auth'

export interface QRCodeState {
  qrCodeUrl: string
  userCode: string
  deviceCode: string
  expiresAt: number
}

export interface QRCodeOptions {
  /** 轮询间隔（毫秒），默认 3000 */
  pollInterval?: number
  /** 二维码过期时间（秒），默认 600 */
  expiresIn?: number
  /** 客户端 ID */
  clientId?: string
  /** 二维码宽度（像素），默认 256 */
  width?: number
  /** 中心 Logo 占二维码的比例，默认 0.22（22%） */
  logoRatio?: number
  /** Logo 图片路径，默认使用 /favicon.svg */
  logoUrl?: string
}

/** 预加载 Logo 图片，避免每次渲染都重新加载 */
let logoImageCache: HTMLImageElement | null = null
let logoImageLoading = false
let logoImageError = false

function loadLogoImage(logoUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    // 命中缓存
    if (logoImageCache && !logoImageError) {
      resolve(logoImageCache)
      return
    }
    // 之前加载失败过，直接 reject
    if (logoImageError) {
      reject(new Error('Logo 图片加载失败'))
      return
    }
    // 正在加载中，等待
    if (logoImageLoading && logoImageCache) {
      resolve(logoImageCache)
      return
    }

    logoImageLoading = true
    const img = new Image()
    img.crossOrigin = 'anonymous'

    img.onload = () => {
      logoImageCache = img
      logoImageLoading = false
      resolve(img)
    }
    img.onerror = () => {
      logoImageError = true
      logoImageLoading = false
      reject(new Error('Logo 图片加载失败'))
    }
    img.src = logoUrl
  })
}

export function useQRCode(options: QRCodeOptions = {}) {
  const {
    pollInterval = 3000,
    expiresIn = 600,
    clientId = 'cloud-drive-web',
    width = 256,
    logoRatio = 0.22,
    logoUrl = '/favicon.svg',
  } = options

  /** 二维码原始 URL */
  const qrCodeUrl = ref<string>('')
  const qrCodeState = ref<QRCodeState | null>(null)
  const qrStatus = ref<'idle' | 'loading' | 'ready' | 'expired' | 'scanning' | 'confirmed' | 'error'>('idle')
  const qrCountdown = ref(expiresIn)
  const qrError = ref('')

  let pollTimer: ReturnType<typeof setInterval> | null = null
  let countdownTimer: ReturnType<typeof setInterval> | null = null
  let onSuccessCallback: ((token: string) => void) | null = null

  // ============================================================
  // 二维码 Canvas 渲染（含中心 Logo）
  // ============================================================

  /** 获取设备类型信息 */
  function getDeviceInfo(): string {
    const ua = navigator.userAgent
    const os = ua.includes('Mac') ? 'macOS' : ua.includes('Win') ? 'Windows'
      : ua.includes('Linux') ? 'Linux' : ua.includes('Android') ? 'Android'
      : ua.includes('iPhone') || ua.includes('iPad') ? 'iOS' : 'Unknown'
    const browser = ua.includes('Chrome') ? 'Chrome' : ua.includes('Safari') ? 'Safari'
      : ua.includes('Firefox') ? 'Firefox' : ua.includes('Edge') ? 'Edge' : 'Unknown'
    return `${os} / ${browser}`
  }

  /**
   * 将二维码渲染到 Canvas 上，并在中心绘制企业品牌 Logo
   *
   * 采用 H 级纠错（30% 容错），中心 Logo 区域约 22%，
   * 远小于 30% 容错上限，确保扫码成功率。
   */
  async function renderQRToCanvas(
    canvas: HTMLCanvasElement,
    url: string,
    canvasSize: number = width,
  ): Promise<void> {
    // 1. 使用 qrcode 库渲染二维码到 Canvas（H 级纠错）
    try {
      await QRCode.toCanvas(canvas, url, {
        width: canvasSize,
        margin: 2,
        color: {
          dark: '#0F172A',   // slate-900
          light: '#FFFFFF',
        },
        errorCorrectionLevel: 'H',
      }) 
    } catch (e) {
      console.error('[useQRCode] QRCode generation failed:', e)
      throw new Error('二维码生成失败')
    }
    const ctx = canvas.getContext('2d')
    if (!ctx) return

    // 2. 加载品牌 Logo
    let logoImg: HTMLImageElement | null = null
    try {
      logoImg = await loadLogoImage(logoUrl)
    } catch {
      // Logo 加载失败，只渲染二维码本身，不阻塞
    }

    if (!logoImg) return

    // 3. 计算中心 Logo 区域
    const logoAreaSize = canvasSize * logoRatio
    const logoCx = canvasSize / 2
    const logoCy = canvasSize / 2
    const bgX = logoCx - logoAreaSize / 2
    const bgY = logoCy - logoAreaSize / 2
    const bgRadius = logoAreaSize * 0.16

    // 4. 绘制白色圆角背景（覆盖二维码中心模块）
    ctx.save()
    ctx.beginPath()
    ctx.moveTo(bgX + bgRadius, bgY)
    ctx.lineTo(bgX + logoAreaSize - bgRadius, bgY)
    ctx.quadraticCurveTo(bgX + logoAreaSize, bgY, bgX + logoAreaSize, bgY + bgRadius)
    ctx.lineTo(bgX + logoAreaSize, bgY + logoAreaSize - bgRadius)
    ctx.quadraticCurveTo(bgX + logoAreaSize, bgY + logoAreaSize, bgX + logoAreaSize - bgRadius, bgY + logoAreaSize)
    ctx.lineTo(bgX + bgRadius, bgY + logoAreaSize)
    ctx.quadraticCurveTo(bgX, bgY + logoAreaSize, bgX, bgY + logoAreaSize - bgRadius)
    ctx.lineTo(bgX, bgY + bgRadius)
    ctx.quadraticCurveTo(bgX, bgY, bgX + bgRadius, bgY)
    ctx.closePath()
    ctx.fillStyle = '#FFFFFF'
    ctx.fill()

    // 白色背景微弱阴影
    ctx.shadowColor = 'rgba(15, 23, 42, 0.18)'
    ctx.shadowBlur = logoAreaSize * 0.12
    ctx.shadowOffsetX = 0
    ctx.shadowOffsetY = logoAreaSize * 0.04
    ctx.fill()
    ctx.restore()

    // 5. 绘制 Logo 图片（居中，留少量内边距）
    const logoPadding = logoAreaSize * 0.12
    const logoDrawSize = logoAreaSize - logoPadding * 2
    ctx.drawImage(
      logoImg,
      bgX + logoPadding,
      bgY + logoPadding,
      logoDrawSize,
      logoDrawSize,
    )
  }

  // ============================================================
  // 授权流程
  // ============================================================

  async function startQRCodeAuth(onSuccess?: (token: string) => void) {
    if (onSuccess) onSuccessCallback = onSuccess

    qrStatus.value = 'loading'
    qrError.value = ''

    try {
      const res = await deviceAuthInitApi(clientId, 'openid profile offline_access', getDeviceInfo())

      if (res.code !== 200 || !res.data) {
        throw new Error(res.message || '获取设备授权失败')
      }

      const { deviceCode, userCode, verificationUriComplete, expiresIn: _expiresIn } = res.data

      qrCodeState.value = {
        qrCodeUrl: verificationUriComplete,
        userCode,
        deviceCode,
        expiresAt: Date.now() + (_expiresIn || expiresIn) * 1000,
      }
      qrCodeUrl.value = verificationUriComplete
      qrStatus.value = 'ready'
      qrCountdown.value = _expiresIn || expiresIn

      startPolling(deviceCode)
      startCountdown()
    } catch (e: any) {
      qrStatus.value = 'error'
      qrError.value = e?.message || '获取授权二维码失败'
    }
  }

  /** 手动刷新二维码 */
  async function refreshQRCode() {
    stopPolling()
    stopCountdown()
    await startQRCodeAuth(onSuccessCallback || undefined)
  }

  // ============================================================
  // 测试模式：生成测试二维码
  // ============================================================

  /**
   * 生成测试二维码（用于开发调试，不依赖后端）
   * @param testUrl 测试 URL，默认使用本地测试地址
   */
  async function generateTestQRCode(testUrl?: string): Promise<string> {
    qrStatus.value = 'loading'
    qrError.value = ''

    try {
      const url = testUrl || 'https://clouddrive.example.com/auth/device?user_code=TEST-DEMO-CODE'

      qrCodeState.value = {
        qrCodeUrl: url,
        userCode: 'TEST-DEMO',
        deviceCode: 'test-device-code',
        expiresAt: Date.now() + expiresIn * 1000,
      }
      qrCodeUrl.value = url
      qrStatus.value = 'ready'
      qrCountdown.value = expiresIn

      startCountdown()
      return url
    } catch (e: any) {
      qrStatus.value = 'error'
      qrError.value = e?.message || '测试二维码生成失败'
      throw e
    }
  }

  // ============================================================
  // 轮询与倒计时
  // ============================================================

  function startPolling(deviceCode: string) {
    stopPolling()
    pollTimer = setInterval(async () => {
      try {
        const res = await deviceAuthPollApi(deviceCode)

        if (res.code === 200 && res.data) {
          // 授权成功
          qrStatus.value = 'confirmed'
          stopPolling()
          stopCountdown()
          onSuccessCallback?.(res.data.accessToken || res.data.access_token)
          return
        }

        if (res.code === 202) {
          // 仍在等待授权（authorization_pending）
          if (qrStatus.value !== 'scanning') {
            qrStatus.value = 'ready'
          }
          return
        }

        // 其他错误
        if (res.message?.includes('expired') || res.message?.includes('过期')) {
          qrStatus.value = 'expired'
          stopPolling()
          stopCountdown()
        }
      } catch {
        // 轮询失败静默处理，下次轮询继续
      }
    }, pollInterval)
  }

  function startCountdown() {
    stopCountdown()
    countdownTimer = setInterval(() => {
      if (qrCountdown.value > 0) {
        qrCountdown.value--
      } else {
        qrStatus.value = 'expired'
        stopCountdown()
        stopPolling()
      }
    }, 1000)
  }

  function stopPolling() {
    if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
  }

  function stopCountdown() {
    if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null }
  }

  /** 模拟扫码（开发调试用） */
  function markAsScanning() {
    if (qrStatus.value === 'ready') qrStatus.value = 'scanning'
  }

  /** 销毁 */
  function destroy() {
    stopPolling()
    stopCountdown()
    onSuccessCallback = null
  }

  onBeforeUnmount(() => destroy())

  return {
    qrCodeUrl,
    qrCodeState,
    qrStatus,
    qrCountdown,
    qrError,
    renderQRToCanvas,
    startQRCodeAuth,
    refreshQRCode,
    generateTestQRCode,
    markAsScanning,
    destroy,
  }
}