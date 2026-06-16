/**
 * src/main/updater.js - 自动更新模块
 *
 * 使用 electron-updater 实现自动更新
 * 通过 generic provider 从自建服务器拉取更新
 */
const { autoUpdater } = require('electron-updater')
const { dialog } = require('electron')

/**
 * 检查更新并通知用户
 * @param {BrowserWindow} mainWindow
 */
function checkForUpdates(mainWindow) {
  // 配置更新源
  autoUpdater.autoDownload = false
  autoUpdater.autoInstallOnAppQuit = true

  // 检查更新
  autoUpdater.checkForUpdatesAndNotify().catch(err => {
    console.log('[Updater] 检查更新失败:', err.message)
  })

  // 发现新版本
  autoUpdater.on('update-available', (info) => {
    dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '发现新版本',
      message: `PrivateCloudDisk v${info.version} 可用`,
      detail: `当前版本: ${require('electron').app.getVersion()}\n\n是否立即下载更新?`,
      buttons: ['立即下载', '稍后提醒'],
      defaultId: 0,
      cancelId: 1
    }).then(({ response }) => {
      if (response === 0) {
        autoUpdater.downloadUpdate()
      }
    })
  })

  // 下载进度
  autoUpdater.on('download-progress', (progress) => {
    mainWindow.webContents.send('update:download-progress', progress.percent)
    mainWindow.setProgressBar(progress.percent / 100)
  })

  // 下载完成
  autoUpdater.on('update-downloaded', () => {
    mainWindow.setProgressBar(-1)
    dialog.showMessageBox(mainWindow, {
      type: 'info',
      title: '更新已下载',
      message: '更新已下载完成，是否立即重启安装?',
      buttons: ['立即重启', '稍后重启'],
      defaultId: 0
    }).then(({ response }) => {
      if (response === 0) {
        autoUpdater.quitAndInstall()
      }
    })
  })

  // 更新错误
  autoUpdater.on('error', (error) => {
    console.error('[Updater] 更新错误:', error)
  })
}

module.exports = { checkForUpdates }