/**
 * src/main/main.js - Electron 主进程入口
 *
 * 职责:
 * - 应用生命周期管理
 * - 窗口创建与管理
 * - 菜单栏注册
 * - 系统托盘
 * - IPC 通信注册
 * - 自动更新检查
 * - 单实例锁
 */
const { app, BrowserWindow, dialog } = require('electron')
const path = require('path')
const { createMainWindow } = require('./window')
const { buildMenu } = require('./menu')
const { createTray, destroyTray } = require('./tray')
const { registerIpcHandlers } = require('./ipc')
const { checkForUpdates } = require('./updater')
const { registerVDIpcHandlers, cleanupVD } = require('./virtual-disk/ipc-bridge')
const splashManager = require('./splash')

// ==================== 单实例锁 ====================
const gotTheLock = app.requestSingleInstanceLock()
if (!gotTheLock) {
  app.quit()
} else {
  app.on('second-instance', () => {
    const win = BrowserWindow.getAllWindows()[0]
    if (win) {
      if (win.isMinimized()) win.restore()
      win.focus()
    }
  })
}

// ==================== 全局变量 ====================
let mainWindow = null

// ==================== 应用就绪 ====================
app.whenReady().then(async () => {
  // 1. 立即显示启动页 (无边框窗口, 置顶显示)
  splashManager.show()
  splashManager.updateStatus('正在初始化核心模块...')

  // 设置超时保护: 如果 15 秒内主窗口未就绪, 强制关闭启动页
  splashManager.setTimeout(() => {
    console.warn('[Main] 启动超时, 强制显示主窗口')
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.show()
    }
  })

  // 2. 注册 IPC 处理器
  splashManager.updateStatus('正在加载通信模块...')
  registerIpcHandlers()

  // 注册虚拟磁盘 IPC 处理器
  registerVDIpcHandlers()
  splashManager.updateStatus('正在初始化虚拟磁盘...')

  // 3. 创建主窗口 (show: false, 后台加载)
  splashManager.updateStatus('正在准备界面...')
  mainWindow = createMainWindow()

  // 4. 主窗口 DOM 就绪后, 关闭启动页并显示主窗口
  //    使用 'ready-to-show' 确保渲染完成, 避免白屏闪烁
  mainWindow.once('ready-to-show', () => {
    splashManager.close(mainWindow)
  })

  // 如果页面加载失败, 也关闭启动页
  mainWindow.webContents.on('did-fail-load', () => {
    splashManager.close(mainWindow)
  })

  // 5. 构建菜单栏
  buildMenu(mainWindow)

  // 6. 创建系统托盘
  createTray(mainWindow)

  // 7. 检查自动更新 (生产环境)
  if (!process.env.NODE_ENV || process.env.NODE_ENV !== 'development') {
    setTimeout(() => checkForUpdates(mainWindow), 5000)
  }
})

// ==================== 生命周期事件 ====================

app.on('window-all-closed', () => {
  destroyTray()
  if (process.platform !== 'darwin') {
    app.quit()
  }
})

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) {
    mainWindow = createMainWindow()
  }
})

app.on('before-quit', async () => {
  destroyTray()
  await cleanupVD()
})

// ==================== 全局异常处理 ====================

process.on('uncaughtException', (error) => {
  console.error('[Main Process] 未捕获异常:', error)
  dialog.showErrorBox('应用程序错误', error.message || '发生未知错误')
})

process.on('unhandledRejection', (reason) => {
  console.error('[Main Process] 未处理的 Promise 拒绝:', reason)
})