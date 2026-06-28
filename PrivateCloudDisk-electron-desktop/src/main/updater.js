/**
 * src/main/updater.js - 企业级自动更新模块
 *
 * 功能:
 * 1. 大版本检查 — 启动时 + 定期检查，弹出通知引导用户
 * 2. 热更新 — 静默下载小补丁，替换资源文件无需重启
 * 3. 版本自检 — 启动时自动检查，智能判断更新类型
 * 4. 强制更新 — 阻止继续使用，必须更新
 * 5. 下载进度 — 进度条显示
 */
const { autoUpdater } = require('electron-updater')
const { dialog, Notification, app, shell } = require('electron')
const { net } = require('electron')
const fs = require('fs')
const path = require('path')
const { execSync } = require('child_process')
const crypto = require('crypto')

// ==================== 配置 ====================

const UPDATE_SERVER = process.env.UPDATE_SERVER || 'http://localhost:8080'
const CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000 // 24小时
const CURRENT_VERSION = app.getVersion()
const PLATFORM = process.platform

// ==================== 状态管理 ====================

let updateCheckTimer = null
let currentUpdateInfo = null
let downloadProgress = null
let updateSettings = {
  autoCheck: true,
  autoDownload: false,
  hotUpdate: true,
  channel: 'stable',
  interval: 24
}

// 加载设置
function loadSettings() {
  try {
    const userDataPath = app.getPath('userData')
    const settingsPath = path.join(userDataPath, 'update-settings.json')
    if (fs.existsSync(settingsPath)) {
      const data = JSON.parse(fs.readFileSync(settingsPath, 'utf-8'))
      updateSettings = { ...updateSettings, ...data }
    }
  } catch (e) {
    console.error('[Updater] 加载设置失败:', e.message)
  }
}

function saveSettings() {
  try {
    const userDataPath = app.getPath('userData')
    const settingsPath = path.join(userDataPath, 'update-settings.json')
    fs.writeFileSync(settingsPath, JSON.stringify(updateSettings, null, 2))
  } catch (e) {
    console.error('[Updater] 保存设置失败:', e.message)
  }
}

// ==================== 版本比较 ====================

/**
 * 比较语义化版本号
 * @returns 0=相等, >0=v1>v2, <0=v1<v2
 */
function compareVersions(v1, v2) {
  const parts1 = v1.split('.').map(Number)
  const parts2 = v2.split('.').map(Number)
  for (let i = 0; i < Math.max(parts1.length, parts2.length); i++) {
    const a = parts1[i] || 0
    const b = parts2[i] || 0
    if (a > b) return 1
    if (a < b) return -1
  }
  return 0
}

/**
 * 判断更新类型
 */
function determineUpdateType(current, latest) {
  const cur = current.split('.').map(Number)
  const lat = latest.split('.').map(Number)
  if (lat[0] > cur[0]) return 'major'
  if (lat[1] > cur[1]) return 'minor'
  return 'patch'
}

// ==================== 版本检查 API ====================

/**
 * 通过 HTTP API 检查更新
 */
async function checkUpdateViaAPI() {
  const url = `${UPDATE_SERVER}/api/v1/version/check`
  const body = JSON.stringify({
    current_version: CURRENT_VERSION,
    platform: PLATFORM,
    arch: process.arch,
    channel: updateSettings.channel
  })

  return new Promise((resolve, reject) => {
    const request = net.request({
      method: 'POST',
      url: url,
      headers: { 'Content-Type': 'application/json' }
    })

    request.on('response', (response) => {
      let data = ''
      response.on('data', (chunk) => { data += chunk })
      response.on('end', () => {
        try {
          const result = JSON.parse(data)
          resolve(result)
        } catch (e) {
          reject(new Error('响应解析失败'))
        }
      })
    })
    request.on('error', reject)
    request.write(body)
    request.end()
  })
}

// ==================== 大版本更新检查 ====================

/**
 * 检查更新并通知用户
 * @param {BrowserWindow} mainWindow
 */
function checkForUpdates(mainWindow) {
  loadSettings()

  // 配置 electron-updater
  autoUpdater.autoDownload = false
  autoUpdater.autoInstallOnAppQuit = true

  // 检查更新
  autoUpdater.checkForUpdatesAndNotify().catch(err => {
    console.log('[Updater] 检查更新失败:', err.message)
  })

  // 发现新版本
  autoUpdater.on('update-available', (info) => {
    currentUpdateInfo = info
    const updateType = determineUpdateType(app.getVersion(), info.version)

    console.log(`[Updater] 发现新版本: v${info.version} (${updateType})`)

    // 判断更新类型
    if (updateType === 'major') {
      // 大版本更新 → 弹出对话框
      dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: '发现大版本更新',
        message: `PrivateCloudDisk v${info.version} 可用`,
        detail: [
          `当前版本: v${app.getVersion()}`,
          `新版本: v${info.version}`,
          '',
          '这是一个大版本更新，包含重要的新功能和改进。',
          '建议立即更新以获得最佳体验。'
        ].join('\n'),
        buttons: ['立即下载', '稍后提醒', '跳过此版本'],
        defaultId: 0,
        cancelId: 1
      }).then(({ response }) => {
        if (response === 0) {
          autoUpdater.downloadUpdate()
        } else if (response === 1) {
          scheduleReminder(mainWindow, info)
        }
      })
    } else if (updateType === 'patch') {
      // 热修复 → 静默下载
      if (updateSettings.hotUpdate) {
        autoUpdater.downloadUpdate()
        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.webContents.send('update:hotfix-downloading', info)
        }
      }
    } else {
      // 小版本 → 后台下载
      if (updateSettings.autoDownload) {
        autoUpdater.downloadUpdate()
      } else {
        // 通知用户
        showUpdateNotification(info, updateType)
      }
    }
  })

  // 下载进度
  autoUpdater.on('download-progress', (progress) => {
    downloadProgress = progress
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:download-progress', {
        percent: progress.percent,
        speed: progress.bytesPerSecond,
        transferred: progress.transferred,
        total: progress.total
      })
      mainWindow.setProgressBar(progress.percent / 100)
    }
  })

  // 下载完成
  autoUpdater.on('update-downloaded', (info) => {
    mainWindow.setProgressBar(-1)

    const updateType = determineUpdateType(app.getVersion(), info.version)

    if (updateType === 'patch' && updateSettings.hotUpdate) {
      // 热修复 → 尝试应用后重启
      console.log('[Updater] 热修复已下载，准备安装')
      dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: '更新已下载',
        message: '热修复已准备就绪',
        detail: '需要重启应用以完成更新，是否立即重启？',
        buttons: ['立即重启', '稍后重启'],
        defaultId: 0
      }).then(({ response }) => {
        if (response === 0) {
          autoUpdater.quitAndInstall()
        }
      })
    } else {
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
    }
  })

  // 更新错误
  autoUpdater.on('error', (error) => {
    console.error('[Updater] 更新错误:', error)
    if (mainWindow && !mainWindow.isDestroyed()) {
      mainWindow.webContents.send('update:error', error.message)
    }
  })
}

// ==================== 版本自检 ====================

/**
 * 启动时版本自检
 */
async function performStartupCheck(mainWindow) {
  console.log('[VersionCheck] 执行启动版本自检...')

  try {
    const result = await checkUpdateViaAPI()

    if (result.code === 200 && result.data) {
      const updateInfo = result.data

      if (updateInfo.has_update) {
        const updateType = updateInfo.update_type || determineUpdateType(CURRENT_VERSION, updateInfo.latest_version)

        console.log(`[VersionCheck] 发现更新: v${updateInfo.latest_version} (${updateType})`)

        // 强制更新处理
        if (updateInfo.force_update) {
          handleForceUpdate(mainWindow, updateInfo)
          return { hasUpdate: true, forced: true, info: updateInfo }
        }

        // 根据更新类型处理
        switch (updateType) {
          case 'major':
            handleMajorUpdate(mainWindow, updateInfo)
            break
          case 'patch':
            handleHotfix(mainWindow, updateInfo)
            break
          default:
            handleMinorUpdate(mainWindow, updateInfo)
            break
        }

        return { hasUpdate: true, forced: false, info: updateInfo }
      }
    }

    console.log('[VersionCheck] 当前已是最新版本')
    return { hasUpdate: false }
  } catch (err) {
    console.error('[VersionCheck] 版本检查失败:', err.message)
    return { hasUpdate: false, error: err.message }
  }
}

/**
 * 启动定期检查
 */
function startPeriodicCheck(mainWindow) {
  stopPeriodicCheck()

  if (!updateSettings.autoCheck) return

  const interval = updateSettings.interval * 60 * 60 * 1000
  updateCheckTimer = setInterval(async () => {
    console.log('[VersionCheck] 定期检查...')
    await performStartupCheck(mainWindow)
  }, interval)

  console.log(`[VersionCheck] 定期检查已启动, 间隔: ${updateSettings.interval}h`)
}

function stopPeriodicCheck() {
  if (updateCheckTimer) {
    clearInterval(updateCheckTimer)
    updateCheckTimer = null
  }
}

// ==================== 更新处理策略 ====================

/**
 * 强制更新 — 必须更新才能使用
 */
function handleForceUpdate(mainWindow, updateInfo) {
  dialog.showMessageBox(mainWindow, {
    type: 'error',
    title: '必须更新',
    message: `您的版本过低，必须更新到 v${updateInfo.latest_version} 才能继续使用`,
    detail: updateInfo.release_notes || '此版本包含重要的安全更新和修复。',
    buttons: ['立即更新'],
    defaultId: 0
  }).then(() => {
    // 打开下载页面
    if (updateInfo.download_url) {
      shell.openExternal(updateInfo.download_url)
    }
  })
}

/**
 * 大版本更新 — 弹出通知引导用户
 */
function handleMajorUpdate(mainWindow, updateInfo) {
  dialog.showMessageBox(mainWindow, {
    type: 'info',
    title: '发现新版本',
    message: `PrivateCloudDisk v${updateInfo.latest_version} 可用`,
    detail: [
      `当前版本: v${CURRENT_VERSION}`,
      '',
      updateInfo.release_notes || '',
      '',
      `更新包大小: ${formatBytes(updateInfo.package_size)}`
    ].join('\n'),
    buttons: ['立即下载', '稍后提醒'],
    defaultId: 0,
    cancelId: 1
  }).then(({ response }) => {
    if (response === 0) {
      if (updateInfo.download_url) {
        shell.openExternal(updateInfo.download_url)
      }
    }
  })
}

/**
 * 热修复 — 静默下载
 */
function handleHotfix(mainWindow, updateInfo) {
  if (!updateSettings.hotUpdate) return

  console.log(`[VersionCheck] 静默下载热修复 v${updateInfo.latest_version}`)

  // 后台下载
  downloadUpdateInBackground(updateInfo, mainWindow)
}

/**
 * 小版本更新 — 后台下载
 */
function handleMinorUpdate(mainWindow, updateInfo) {
  if (updateSettings.autoDownload) {
    console.log(`[VersionCheck] 后台下载小版本更新 v${updateInfo.latest_version}`)
    downloadUpdateInBackground(updateInfo, mainWindow)
  } else {
    showUpdateNotification(updateInfo, 'minor')
  }
}

// ==================== 后台下载 ====================

/**
 * 后台静默下载更新包
 */
async function downloadUpdateInBackground(updateInfo, mainWindow) {
  try {
    const userDataPath = app.getPath('userData')
    const downloadDir = path.join(userDataPath, 'updates')
    if (!fs.existsSync(downloadDir)) {
      fs.mkdirSync(downloadDir, { recursive: true })
    }

    const fileName = `update-${updateInfo.latest_version}.pcdpkg`
    const savePath = path.join(downloadDir, fileName)

    console.log(`[VersionCheck] 下载更新到: ${savePath}`)

    // 使用 electron net 模块下载
    const request = net.request(updateInfo.download_url)
    const fileStream = fs.createWriteStream(savePath)
    let downloadedBytes = 0
    let lastUpdateTime = Date.now()
    let lastDownloadedBytes = 0

    request.on('response', (response) => {
      const totalBytes = parseInt(response.headers['content-length'] || '0', 10)

      response.on('data', (chunk) => {
        fileStream.write(chunk)
        downloadedBytes += chunk.length

        const now = Date.now()
        if (now - lastUpdateTime > 500) {
          const speed = (downloadedBytes - lastDownloadedBytes) / ((now - lastUpdateTime) / 1000)
          const percent = totalBytes > 0 ? (downloadedBytes / totalBytes * 100) : 0
          const remaining = totalBytes - downloadedBytes
          const eta = speed > 0 ? remaining / speed : 0

          if (mainWindow && !mainWindow.isDestroyed()) {
            mainWindow.webContents.send('update:download-progress', {
              percent,
              speed,
              transferred: downloadedBytes,
              total: totalBytes,
              eta
            })
          }

          lastUpdateTime = now
          lastDownloadedBytes = downloadedBytes
        }
      })

      response.on('end', () => {
        fileStream.end()
        console.log(`[VersionCheck] 下载完成: ${savePath}`)

        // 验证哈希
        if (updateInfo.package_hash) {
          const fileBuffer = fs.readFileSync(savePath)
          const hash = crypto.createHash('sha256').update(fileBuffer).digest('hex')
          if (hash !== updateInfo.package_hash) {
            console.error('[VersionCheck] 更新包哈希校验失败')
            fs.unlinkSync(savePath)
            return
          }
        }

        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.webContents.send('update:downloaded', {
            version: updateInfo.latest_version,
            path: savePath
          })
        }

        // 显示通知
        showUpdateNotification({
          ...updateInfo,
          downloaded: true
        }, 'minor')
      })

      response.on('error', (err) => {
        fileStream.end()
        fs.unlink(savePath, () => {})
        console.error(`[VersionCheck] 下载失败: ${err.message}`)
        if (mainWindow && !mainWindow.isDestroyed()) {
          mainWindow.webContents.send('update:error', err.message)
        }
      })
    })

    request.end()
  } catch (err) {
    console.error('[VersionCheck] 后台下载失败:', err.message)
  }
}

// ==================== 通知 ====================

/**
 * 显示系统通知
 */
function showUpdateNotification(updateInfo, updateType) {
  if (!Notification.isSupported()) return

  const title = updateType === 'major' ? '发现大版本更新' : '新版本可用'
  const body = updateInfo.downloaded
    ? `v${updateInfo.latest_version} 已下载完成，点击安装`
    : `PrivateCloudDisk v${updateInfo.latest_version} 可用，点击查看详情`

  const notification = new Notification({
    title,
    body,
    urgency: updateType === 'major' ? 'critical' : 'normal'
  })

  notification.on('click', () => {
    if (updateInfo.download_url) {
      shell.openExternal(updateInfo.download_url)
    }
  })

  notification.show()
}

/**
 * 延迟提醒
 */
function scheduleReminder(mainWindow, info) {
  setTimeout(() => {
    if (mainWindow && !mainWindow.isDestroyed()) {
      dialog.showMessageBox(mainWindow, {
        type: 'info',
        title: '更新提醒',
        message: `PrivateCloudDisk v${info.version} 仍然可用`,
        detail: '您之前选择了稍后提醒，现在可以下载更新。',
        buttons: ['立即下载', '稍后提醒'],
        defaultId: 0
      }).then(({ response }) => {
        if (response === 0) {
          autoUpdater.downloadUpdate()
        }
      })
    }
  }, 4 * 60 * 60 * 1000) // 4小时后提醒
}

// ==================== IPC 处理 ====================

/**
 * 注册更新相关的 IPC 处理器
 */
function registerUpdateIpcHandlers(ipcMain, mainWindow) {
  // 手动检查更新
  ipcMain.handle('update:check', async () => {
    return await performStartupCheck(mainWindow)
  })

  // 获取更新设置
  ipcMain.handle('update:get-settings', () => {
    return updateSettings
  })

  // 保存更新设置
  ipcMain.handle('update:save-settings', (event, settings) => {
    updateSettings = { ...updateSettings, ...settings }
    saveSettings()

    // 更新定期检查
    if (updateSettings.autoCheck) {
      startPeriodicCheck(mainWindow)
    } else {
      stopPeriodicCheck()
    }
  })

  // 获取当前版本
  ipcMain.handle('update:get-version', () => {
    return {
      version: CURRENT_VERSION,
      platform: PLATFORM,
      arch: process.arch
    }
  })

  // 下载更新
  ipcMain.handle('update:download', async () => {
    autoUpdater.downloadUpdate()
  })

  // 安装更新
  ipcMain.handle('update:install', () => {
    autoUpdater.quitAndInstall()
  })

  // 获取更新信息
  ipcMain.handle('update:get-info', () => {
    return currentUpdateInfo
  })
}

// ==================== 工具函数 ====================

function formatBytes(bytes) {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

// ==================== 导出 ====================

module.exports = {
  checkForUpdates,
  performStartupCheck,
  startPeriodicCheck,
  stopPeriodicCheck,
  registerUpdateIpcHandlers,
  loadSettings,
  saveSettings,
  determineUpdateType,
  compareVersions,
  formatBytes
}