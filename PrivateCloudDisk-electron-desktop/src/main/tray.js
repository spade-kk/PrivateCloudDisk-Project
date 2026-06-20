/**
 * src/main/tray.js - 系统托盘模块
 *
 * 功能:
 * - 托盘图标显示
 * - 右键菜单 (显示/隐藏/退出)
 * - 文件上传进度通知
 */
const { Tray, Menu, app, nativeImage } = require('electron')
const path = require('path')

let tray = null

/**
 * 创建系统托盘
 * @param {BrowserWindow} mainWindow
 */
function createTray(mainWindow) {
  const iconPath = path.join(__dirname, '..', '..', 'resources', 'icons', 'tray-icon.png')

  try {
    const icon = nativeImage.createFromPath(iconPath)
    tray = new Tray(icon.resize({ width: 16, height: 16 }))

    tray.setToolTip('PrivateCloudDisk 企业云盘')

    const contextMenu = Menu.buildFromTemplate([
      {
        label: '显示主窗口',
        click: () => {
          if (mainWindow) {
            mainWindow.show()
            mainWindow.focus()
          }
        }
      },
      {
        label: '上传文件',
        click: () => mainWindow.webContents.send('menu:upload')
      },
      { type: 'separator' },
      {
        label: '检查更新',
        click: () => mainWindow.webContents.send('menu:check-update')
      },
      { type: 'separator' },
      {
        label: '退出',
        click: () => {
          app.isQuitting = true
          app.quit()
        }
      }
    ])

    tray.setContextMenu(contextMenu)

    // 托盘图标点击: 显示/隐藏窗口
    tray.on('click', () => {
      if (mainWindow) {
        if (mainWindow.isVisible()) {
          mainWindow.focus()
        } else {
          mainWindow.show()
        }
      }
    })

    // 双击托盘 → 显示窗口
    tray.on('double-click', () => {
      if (mainWindow) {
        mainWindow.show()
        mainWindow.focus()
      }
    })
  } catch (e) {
    console.error('[Tray] 创建托盘失败:', e.message)
  }
}

/**
 * 销毁托盘
 */
function destroyTray() {
  if (tray) {
    tray.destroy()
    tray = null
  }
}

module.exports = { createTray, destroyTray }