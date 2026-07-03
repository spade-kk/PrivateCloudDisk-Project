/**
 * src/main/window.js - 窗口管理模块
 *
 * 自定义标题栏设计：
 *   - Windows: frame: false + 自定义标题栏（Logo + 菜单栏 + 窗口控制按钮）
 *   - macOS: frame: false + titleBarStyle: hidden + 自定义标题栏（保留原生红绿灯）
 */
const { BrowserWindow, screen } = require('electron')
const path = require('path')

/** 默认窗口配置 */
const WINDOW_CONFIG = {
  width: 1280,
  height: 800,
  minWidth: 900,
  minHeight: 600,
  show: false,
  frame: false,                              // 无边框窗口，实现自定义标题栏
  titleBarStyle: 'hidden',                   // macOS: 隐藏原生标题栏但保留红绿灯按钮
  backgroundColor: '#f5f7fa',
  webPreferences: {
    preload: path.join(__dirname, '..', 'preload', 'preload.js'),
    contextIsolation: true,
    nodeIntegration: false,
    sandbox: false
  }
}

/**
 * 创建主窗口
 * @returns {BrowserWindow}
 */
function createMainWindow() {
  const isDev = process.env.NODE_ENV === 'development'

  const win = new BrowserWindow({
    ...WINDOW_CONFIG,
    icon: path.join(__dirname, '..', '..', 'resources', 'icons', 'icon.png')
  })

  // 居中显示
  win.center()

  // 窗口就绪后显示 (避免白屏)
  win.once('ready-to-show', () => {
    win.show()

    // 开发环境下打开 DevTools
    if (isDev) {
      win.webContents.openDevTools({ mode: 'detach' })
    }
  })

  // 加载页面
  if (isDev) {
    win.loadURL('http://localhost:5173')
  } else {
    win.loadFile(path.join(__dirname, '..', '..', 'build', 'renderer', 'index.html'))
  }

  // 窗口关闭时清理引用
  win.on('closed', () => {
    // 由 main.js 管理生命周期
  })

  return win
}

/**
 * 创建子窗口 (如文件详情)
 * @param {string} url - 加载的 URL
 * @param {object} options - 附加选项
 * @returns {BrowserWindow}
 */
function createSubWindow(url, options = {}) {
  const isDev = process.env.NODE_ENV === 'development'
  const { width = 900, height = 600, parent = null } = options

  const win = new BrowserWindow({
    width,
    height,
    parent,
    modal: !!parent,
    show: false,
    frame: false,
    titleBarStyle: 'hidden',
    backgroundColor: '#ffffff',
    webPreferences: {
      preload: path.join(__dirname, '..', 'preload', 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  })

  win.once('ready-to-show', () => win.show())

  if (isDev) {
    win.loadURL(`http://localhost:5173${url}`)
  } else {
    win.loadFile(path.join(__dirname, '..', '..', 'build', 'renderer', 'index.html'), {
      hash: url
    })
  }

  return win
}

module.exports = { createMainWindow, createSubWindow, WINDOW_CONFIG }