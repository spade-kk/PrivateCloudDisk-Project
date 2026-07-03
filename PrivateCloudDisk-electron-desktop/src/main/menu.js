/**
 * src/main/menu.js - 应用菜单栏模块
 *
 * macOS: 系统原生菜单栏（屏幕顶部）
 * Windows: 自定义菜单栏（集成在标题栏中，通过 IPC 与渲染进程通信）
 */
const { Menu, app, shell, dialog, ipcMain } = require('electron')

/**
 * 构建应用菜单模板
 * Windows 平台下返回渲染进程友好的菜单数据结构
 * macOS 平台下构建原生菜单
 *
 * @param {BrowserWindow} mainWindow
 * @returns {{ template: Array, isMac: boolean }} 菜单数据
 */
function buildMenu(mainWindow) {
  const isMac = process.platform === 'darwin'

  const template = [
    // macOS 应用菜单
    ...(isMac ? [{
      label: app.name,
      submenu: [
        { role: 'about', label: '关于 PrivateCloudDisk' },
        { type: 'separator' },
        { role: 'services', label: '服务' },
        { type: 'separator' },
        { role: 'hide', label: '隐藏' },
        { role: 'hideOthers', label: '隐藏其他' },
        { role: 'unhide', label: '显示全部' },
        { type: 'separator' },
        { role: 'quit', label: '退出 PrivateCloudDisk' }
      ]
    }] : []),

    // 文件
    {
      label: '文件',
      id: 'file',
      submenu: [
        {
          id: 'upload',
          label: '上传文件',
          accelerator: 'CmdOrCtrl+U',
          click: () => mainWindow.webContents.send('menu:upload')
        },
        {
          id: 'new-folder',
          label: '新建文件夹',
          accelerator: 'CmdOrCtrl+Shift+N',
          click: () => mainWindow.webContents.send('menu:new-folder')
        },
        { type: 'separator' },
        isMac
          ? { id: 'close', role: 'close', label: '关闭窗口' }
          : { id: 'quit', role: 'quit', label: '退出' }
      ]
    },

    // 编辑
    {
      label: '编辑',
      id: 'edit',
      submenu: [
        { role: 'undo', label: '撤销' },
        { role: 'redo', label: '重做' },
        { type: 'separator' },
        { role: 'cut', label: '剪切' },
        { role: 'copy', label: '复制' },
        { role: 'paste', label: '粘贴' },
        { role: 'selectAll', label: '全选' }
      ]
    },

    // 视图
    {
      label: '视图',
      id: 'view',
      submenu: [
        {
          id: 'reload',
          label: '刷新',
          accelerator: 'CmdOrCtrl+R',
          click: () => mainWindow.webContents.reload()
        },
        {
          id: 'force-reload',
          label: '强制刷新',
          accelerator: 'CmdOrCtrl+Shift+R',
          click: () => mainWindow.webContents.reloadIgnoringCache()
        },
        { type: 'separator' },
        { role: 'resetZoom', label: '重置缩放' },
        { role: 'zoomIn', label: '放大' },
        { role: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { role: 'togglefullscreen', label: '全屏' },
        { type: 'separator' },
        {
          id: 'devtools',
          label: '开发者工具',
          accelerator: 'F12',
          click: () => mainWindow.webContents.toggleDevTools()
        }
      ]
    },

    // 导航
    {
      label: '导航',
      id: 'navigate',
      submenu: [
        {
          id: 'nav-home',
          label: '首页',
          accelerator: 'CmdOrCtrl+1',
          click: () => mainWindow.webContents.send('menu:navigate', '/home')
        },
        {
          id: 'nav-favorites',
          label: '收藏',
          accelerator: 'CmdOrCtrl+2',
          click: () => mainWindow.webContents.send('menu:navigate', '/favorites')
        },
        {
          id: 'nav-trash',
          label: '回收站',
          accelerator: 'CmdOrCtrl+3',
          click: () => mainWindow.webContents.send('menu:navigate', '/trash')
        },
        {
          id: 'nav-search',
          label: '搜索',
          accelerator: 'CmdOrCtrl+F',
          click: () => mainWindow.webContents.send('menu:navigate', '/search')
        },
        { type: 'separator' },
        {
          id: 'nav-back',
          label: '返回上级',
          accelerator: 'CmdOrCtrl+Up',
          click: () => mainWindow.webContents.send('menu:go-back')
        }
      ]
    },

    // 帮助
    {
      label: '帮助',
      id: 'help',
      submenu: [
        {
          id: 'about',
          label: '关于',
          click: () => {
            dialog.showMessageBox(mainWindow, {
              type: 'info',
              title: '关于 PrivateCloudDisk',
              message: `PrivateCloudDisk Desktop v${app.getVersion()}`,
              detail: '企业私有云盘桌面客户端\n\n支持 macOS / Windows / Linux',
              buttons: ['确定']
            })
          }
        },
        {
          id: 'check-update',
          label: '检查更新',
          click: () => mainWindow.webContents.send('menu:check-update')
        },
        { type: 'separator' },
        {
          id: 'website',
          label: '访问官网',
          click: () => shell.openExternal('https://privateclouddisk.local')
        }
      ]
    }
  ]

  if (isMac) {
    // macOS: 使用原生菜单栏（屏幕顶部）
    const menu = Menu.buildFromTemplate(template)
    Menu.setApplicationMenu(menu)
  } else {
    // Windows: 隐藏原生菜单栏，使用自定义标题栏菜单
    Menu.setApplicationMenu(null)
  }

  return { template, isMac }
}

/**
 * 执行菜单项操作
 * 渲染进程通过 IPC 调用此函数来执行菜单点击
 */
ipcMain.handle('menu:execute', (event, menuId) => {
  // 菜单 action 映射表
  // 编辑类操作通过 webContents 直接执行
  const win = event.sender.getOwnerBrowserWindow()
  if (!win) return

  const actions = {
    'undo': () => win.webContents.undo(),
    'redo': () => win.webContents.redo(),
    'cut': () => win.webContents.cut(),
    'copy': () => win.webContents.copy(),
    'paste': () => win.webContents.paste(),
    'selectAll': () => win.webContents.selectAll(),
    'resetZoom': () => win.webContents.setZoomLevel(0),
    'zoomIn': () => win.webContents.setZoomLevel(win.webContents.getZoomLevel() + 0.5),
    'zoomOut': () => win.webContents.setZoomLevel(win.webContents.getZoomLevel() - 0.5),
    'togglefullscreen': () => win.setFullScreen(!win.isFullScreen()),
    'reload': () => win.webContents.reload(),
    'force-reload': () => win.webContents.reloadIgnoringCache(),
    'devtools': () => win.webContents.toggleDevTools(),
    'close': () => win.close(),
    'quit': () => app.quit(),
  }

  const action = actions[menuId]
  if (action) {
    action()
  }
})

module.exports = { buildMenu }

// ==================== 菜单 IPC 通信 ====================

/**
 * 渲染进程获取菜单模板
 * Windows 平台用于自定义标题栏菜单渲染
 */
ipcMain.handle('menu:getTemplate', (event) => {
  const win = event.sender.getOwnerBrowserWindow()
  if (!win) return { isMac: process.platform === 'darwin', template: [] }

  // 重新构建菜单模板（不含 click 回调，纯数据结构）
  const isMac = process.platform === 'darwin'

  // 提取菜单的纯数据结构（不含 click 回调）
  const extractMenuData = (submenu) => {
    return submenu
      .filter(item => item.type !== 'separator' || item.label) // 保留分隔符
      .map(item => {
        if (item.type === 'separator') return { type: 'separator' }
        return {
          id: item.role || item.id || '',
          label: item.label || '',
          accelerator: item.accelerator || '',
          type: item.type || '',
          submenu: item.submenu ? extractMenuData(item.submenu) : undefined
        }
      })
  }

  const template = [
    {
      label: '文件',
      id: 'file',
      submenu: [
        { id: 'upload', label: '上传文件', accelerator: 'Ctrl+U' },
        { id: 'new-folder', label: '新建文件夹', accelerator: 'Ctrl+Shift+N' },
        { type: 'separator' },
        isMac
          ? { id: 'close', label: '关闭窗口' }
          : { id: 'quit', label: '退出' }
      ]
    },
    {
      label: '编辑',
      id: 'edit',
      submenu: [
        { id: 'undo', label: '撤销', accelerator: 'Ctrl+Z' },
        { id: 'redo', label: '重做', accelerator: 'Ctrl+Y' },
        { type: 'separator' },
        { id: 'cut', label: '剪切', accelerator: 'Ctrl+X' },
        { id: 'copy', label: '复制', accelerator: 'Ctrl+C' },
        { id: 'paste', label: '粘贴', accelerator: 'Ctrl+V' },
        { id: 'selectAll', label: '全选', accelerator: 'Ctrl+A' }
      ]
    },
    {
      label: '视图',
      id: 'view',
      submenu: [
        { id: 'reload', label: '刷新', accelerator: 'Ctrl+R' },
        { id: 'force-reload', label: '强制刷新', accelerator: 'Ctrl+Shift+R' },
        { type: 'separator' },
        { id: 'resetZoom', label: '重置缩放' },
        { id: 'zoomIn', label: '放大' },
        { id: 'zoomOut', label: '缩小' },
        { type: 'separator' },
        { id: 'togglefullscreen', label: '全屏', accelerator: 'F11' },
        { type: 'separator' },
        { id: 'devtools', label: '开发者工具', accelerator: 'F12' }
      ]
    },
    {
      label: '导航',
      id: 'navigate',
      submenu: [
        { id: 'nav-home', label: '首页', accelerator: 'Ctrl+1' },
        { id: 'nav-favorites', label: '收藏', accelerator: 'Ctrl+2' },
        { id: 'nav-trash', label: '回收站', accelerator: 'Ctrl+3' },
        { id: 'nav-search', label: '搜索', accelerator: 'Ctrl+F' },
        { type: 'separator' },
        { id: 'nav-back', label: '返回上级', accelerator: 'Ctrl+Up' }
      ]
    },
    {
      label: '帮助',
      id: 'help',
      submenu: [
        { id: 'about', label: '关于' },
        { id: 'check-update', label: '检查更新' },
        { type: 'separator' },
        { id: 'website', label: '访问官网' }
      ]
    }
  ]

  return { isMac, template }
})

/** 获取平台信息 */
ipcMain.handle('window:getPlatform', () => {
  return process.platform
})