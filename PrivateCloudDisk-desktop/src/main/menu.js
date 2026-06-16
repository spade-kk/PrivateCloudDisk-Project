/**
 * src/main/menu.js - 应用菜单栏模块
 *
 * macOS 菜单栏 / Windows 窗口菜单
 */
const { Menu, app, shell, dialog } = require('electron')

/**
 * 构建应用菜单
 * @param {BrowserWindow} mainWindow
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
      submenu: [
        {
          label: '上传文件',
          accelerator: 'CmdOrCtrl+U',
          click: () => mainWindow.webContents.send('menu:upload')
        },
        {
          label: '新建文件夹',
          accelerator: 'CmdOrCtrl+Shift+N',
          click: () => mainWindow.webContents.send('menu:new-folder')
        },
        { type: 'separator' },
        isMac ? { role: 'close', label: '关闭窗口' } : { role: 'quit', label: '退出' }
      ]
    },

    // 编辑
    {
      label: '编辑',
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
      submenu: [
        {
          label: '刷新',
          accelerator: 'CmdOrCtrl+R',
          click: () => mainWindow.webContents.reload()
        },
        {
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
          label: '开发者工具',
          accelerator: 'F12',
          click: () => mainWindow.webContents.toggleDevTools()
        }
      ]
    },

    // 导航
    {
      label: '导航',
      submenu: [
        {
          label: '首页',
          accelerator: 'CmdOrCtrl+1',
          click: () => mainWindow.webContents.send('menu:navigate', '/home')
        },
        {
          label: '收藏',
          accelerator: 'CmdOrCtrl+2',
          click: () => mainWindow.webContents.send('menu:navigate', '/favorites')
        },
        {
          label: '回收站',
          accelerator: 'CmdOrCtrl+3',
          click: () => mainWindow.webContents.send('menu:navigate', '/trash')
        },
        {
          label: '搜索',
          accelerator: 'CmdOrCtrl+F',
          click: () => mainWindow.webContents.send('menu:navigate', '/search')
        },
        { type: 'separator' },
        {
          label: '返回上级',
          accelerator: 'CmdOrCtrl+Up',
          click: () => mainWindow.webContents.send('menu:go-back')
        }
      ]
    },

    // 帮助
    {
      label: '帮助',
      submenu: [
        {
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
          label: '检查更新',
          click: () => mainWindow.webContents.send('menu:check-update')
        },
        { type: 'separator' },
        {
          label: '访问官网',
          click: () => shell.openExternal('https://privateclouddisk.local')
        }
      ]
    }
  ]

  const menu = Menu.buildFromTemplate(template)
  Menu.setApplicationMenu(menu)
}

module.exports = { buildMenu }