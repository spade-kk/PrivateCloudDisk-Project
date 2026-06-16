/**
 * src/main/splash.js - 启动闪屏管理器
 *
 * 实现类似 Android Studio / 百度网盘等专业应用的启动画面:
 * - 无边框透明窗口, 居中显示, 置顶
 * - 应用 Logo + 名称 + 加载动画
 * - 淡入淡出过渡效果
 * - 超时保护 (防止卡死在启动页)
 * - macOS/Windows 平台适配
 *
 * 生命周期:
 *   app.whenReady()
 *       │
 *       ├── showSplash()          ← 立即显示启动页
 *       ├── createMainWindow()     ← 后台创建主窗口
 *       │       │
 *       │       ├── 'ready-to-show' → closeSplash() + mainWindow.show()
 *       │       │
 *       │       └── (超时 15s) → closeSplash() 强制关闭
 *       │
 *       └── 主窗口正常显示, 平滑过渡
 */

const { BrowserWindow, screen, app } = require('electron')
const path = require('path')

// ==================== 配置常量 ====================

const SPLASH_CONFIG = {
  /** 窗口宽度 */
  width: 480,
  /** 窗口高度 */
  height: 400,
  /** 是否可以调整大小 */
  resizable: false,
  /** 无边框 */
  frame: false,
  /** 透明背景 (实现圆角效果) */
  transparent: true,
  /** 始终置顶 */
  alwaysOnTop: true,
  /** 不在任务栏显示 (Windows) */
  skipTaskbar: true,
  /** 背景色 (透明窗口下的底色) */
  backgroundColor: '#00000000',
  /** 等待主窗口就绪的最大超时 (ms) */
  timeout: 15000,
  /** 淡出动画持续时间 (ms) */
  fadeOutDuration: 400,
  /** 最小显示时长 (ms, 防止闪过) */
  minDisplayDuration: 800,
  /** webPreferences */
  webPreferences: {
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false
  }
}

// ==================== 工具函数 ====================

/**
 * 获取 splash.html 的文件路径
 * 开发环境: 项目根目录 resources/splash.html
 * 生产环境: process.resourcesPath/splash.html (通过 extraResources 配置)
 */
function getSplashPath() {
  if (app.isPackaged) {
    return path.join(process.resourcesPath, 'splash.html')
  }
  // 开发模式: splash.js 在 src/main/, 向上一级到项目根, 再进 resources/
  return path.join(__dirname, '..', '..', 'resources', 'splash.html')
}

/**
 * 计算窗口居中坐标
 */
function getCenterPosition(width, height) {
  const primaryDisplay = screen.getPrimaryDisplay()
  const { width: screenWidth, height: screenHeight } = primaryDisplay.workAreaSize
  return {
    x: Math.round((screenWidth - width) / 2),
    y: Math.round((screenHeight - height) / 2)
  }
}

// ==================== SplashManager ====================

class SplashManager {
  constructor() {
    /** @type {BrowserWindow|null} */
    this.window = null
    /** 启动页显示的时间戳 */
    this.showTimestamp = 0
    /** 超时定时器 */
    this.timeoutTimer = null
    /** 是否正在关闭中 (防止重复关闭) */
    this.isClosing = false
    /** 关闭回调 */
    this._onClose = null
  }

  /**
   * 显示启动页
   * @param {object} options
   * @param {function} [options.onClose] - 启动页关闭时的回调
   * @returns {BrowserWindow}
   */
  show(options = {}) {
    if (this.window && !this.window.isDestroyed()) {
      console.warn('[Splash] 启动页已存在, 跳过重复创建')
      return this.window
    }

    this._onClose = options.onClose || null
    this.isClosing = false
    this.showTimestamp = Date.now()

    // 计算居中位置
    const position = getCenterPosition(SPLASH_CONFIG.width, SPLASH_CONFIG.height)

    // 创建无边框透明窗口
    this.window = new BrowserWindow({
      ...SPLASH_CONFIG,
      x: position.x,
      y: position.y,
      webPreferences: {
        ...SPLASH_CONFIG.webPreferences,
        // 启动页不需要 preload, 因为所有内容都是纯展示的 HTML
      }
    })

    // 加载 splash.html
    const splashPath = getSplashPath()
    console.log(`[Splash] 加载启动页: ${splashPath}`)
    this.window.loadFile(splashPath)

    // 页面加载完成后执行淡入动画
    this.window.webContents.on('did-finish-load', () => {
      if (this.window && !this.window.isDestroyed()) {
        this.window.webContents.executeJavaScript(`
          document.body.classList.add('splash-visible')
        `)
      }
    })

    // 防止用户手动关闭启动页 (通过 Cmd+W 等)
    this.window.on('close', (e) => {
      if (!this.isClosing) {
        // 如果不是程序主动关闭，阻止关闭
        // e.preventDefault()  取消阻止，允许用户在极端情况下关闭
      }
    })

    // 窗口关闭后的清理
    this.window.on('closed', () => {
      this._cleanup()
    })

    // 防止启动页获得焦点后遮挡其他应用 (macOS)
    if (process.platform === 'darwin') {
      this.window.setVisibleOnAllWorkspaces(true)
      // 设置为浮动窗口级别，但低于 pop-up-menu
      this.window.setAlwaysOnTop(true, 'floating')
    }

    // 在 Windows 上，确保启动页不会抢焦点
    if (process.platform === 'win32') {
      this.window.setAlwaysOnTop(true, 'screen-saver')
    }

    console.log('[Splash] 启动页已显示')
    return this.window
  }

  /**
   * 等待主窗口就绪后关闭启动页
   * 带淡出动画 + 超时保护
   * @param {BrowserWindow} mainWindow - 即将显示的主窗口
   * @returns {Promise<void>}
   */
  async close(mainWindow = null) {
    if (this.isClosing) return
    if (!this.window || this.window.isDestroyed()) return

    this.isClosing = true

    // 确保至少显示了 minDisplayDuration 毫秒
    const elapsed = Date.now() - this.showTimestamp
    if (elapsed < SPLASH_CONFIG.minDisplayDuration) {
      await this._sleep(SPLASH_CONFIG.minDisplayDuration - elapsed)
    }

    // 清除超时定时器
    if (this.timeoutTimer) {
      clearTimeout(this.timeoutTimer)
      this.timeoutTimer = null
    }

    console.log('[Splash] 正在关闭启动页, 过渡到主窗口...')

    try {
      if (this.window && !this.window.isDestroyed()) {
        // 执行淡出动画
        await this.window.webContents.executeJavaScript(`
          new Promise((resolve) => {
            document.body.classList.add('splash-fade-out')
            setTimeout(resolve, ${SPLASH_CONFIG.fadeOutDuration})
          })
        `)

        // 等待动画完成
        await this._sleep(SPLASH_CONFIG.fadeOutDuration + 50)
      }
    } catch {
      // 如果页面已关闭或 JS 执行失败, 直接关闭
    }

    // 如果传入了主窗口, 先显示主窗口再关闭启动页 (视觉上更平滑)
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.show()
      mainWindow.focus()
    }

    // 关闭启动页窗口
    if (this.window && !this.window.isDestroyed()) {
      this.window.destroy()
    }
  }

  /**
   * 设置超时回调
   * 如果主窗口在超时时间内未就绪, 自动关闭启动页
   * @param {function} onTimeout - 超时回调
   */
  setTimeout(onTimeout) {
    if (this.timeoutTimer) {
      clearTimeout(this.timeoutTimer)
    }

    this.timeoutTimer = setTimeout(() => {
      console.warn('[Splash] 主窗口就绪超时, 强制关闭启动页')
      if (onTimeout) {
        onTimeout()
      }
      this.close()
    }, SPLASH_CONFIG.timeout)
  }

  /**
   * 更新启动页上的加载状态文字
   * 用于显示初始化进度 (如 "正在加载配置..." → "正在连接服务器...")
   * @param {string} text
   */
  updateStatus(text) {
    if (!this.window || this.window.isDestroyed()) return
    try {
      this.window.webContents.executeJavaScript(`
        (function() {
          var el = document.getElementById('splash-status')
          if (el) { el.textContent = ${JSON.stringify(text)}; el.classList.add('status-visible') }
        })()
      `)
    } catch { /* 忽略 */ }
  }

  /**
   * 更新加载进度条
   * @param {number} percent - 0-100
   */
  updateProgress(percent) {
    if (!this.window || this.window.isDestroyed()) return
    try {
      this.window.webContents.executeJavaScript(`
        (function() {
          var bar = document.getElementById('splash-progress-bar')
          if (bar) { bar.style.width = '${Math.min(100, Math.max(0, percent))}%' }
        })()
      `)
    } catch { /* 忽略 */ }
  }

  /**
   * 内部清理
   */
  _cleanup() {
    if (this.timeoutTimer) {
      clearTimeout(this.timeoutTimer)
      this.timeoutTimer = null
    }
    this.window = null
    this.isClosing = false

    if (this._onClose) {
      this._onClose()
      this._onClose = null
    }
  }

  _sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms))
  }
}

// ==================== 单例导出 ====================

module.exports = new SplashManager()