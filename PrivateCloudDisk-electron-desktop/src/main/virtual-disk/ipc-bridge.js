/**
 * virtual-disk/ipc-bridge.js - IPC 桥接层
 *
 * 将 VirtualDiskManager 的功能通过 Electron IPC 暴露给渲染进程。
 * 在 main.js 的 registerIpcHandlers 调用 registerVDIpcHandlers() 即可集成。
 *
 * 暴露的 IPC 通道:
 *   vd:mount        - 挂载虚拟磁盘
 *   vd:unmount      - 卸载虚拟磁盘
 *   vd:stats        - 获取统计信息
 *   vd:syncAll      - 全量同步
 *   vd:clearCache   - 清空缓存
 *   vd:checkAvailability - 检查虚拟磁盘功能可用性
 *   vd:getStatus    - 获取当前状态
 *   vd:updateAuth   - 更新认证信息
 */

const { ipcMain, BrowserWindow } = require('electron')
const { VirtualDiskManager } = require('./index')
const { logger } = require('./utils')

// 全局单例
let vdManager = null

/**
 * 注册虚拟磁盘相关的 IPC 处理器
 * @param {VirtualDiskManager} manager - VirtualDiskManager 实例 (可选, 不传则创建新实例)
 */
function registerVDIpcHandlers(manager) {
  vdManager = manager || new VirtualDiskManager()

  // ==================== 挂载 / 卸载 ====================

  ipcMain.handle('vd:mount', async (event, options) => {
    try {
      const result = await vdManager.mount(options)
      return { success: true, data: result }
    } catch (e) {
      logger.error('IPC', `vd:mount 失败: ${e.message}`)
      return { success: false, error: e.message }
    }
  })

  ipcMain.handle('vd:unmount', async () => {
    try {
      const result = await vdManager.unmount()
      return { success: true, data: result }
    } catch (e) {
      logger.error('IPC', `vd:unmount 失败: ${e.message}`)
      return { success: false, error: e.message }
    }
  })

  // ==================== 统计 / 操作 ====================

  ipcMain.handle('vd:stats', async () => {
    try {
      const stats = await vdManager.getStats()
      return { success: true, data: stats }
    } catch (e) {
      return { success: false, error: e.message }
    }
  })

  ipcMain.handle('vd:syncAll', async () => {
    try {
      const result = await vdManager.syncAll()
      return { success: true, data: result }
    } catch (e) {
      return { success: false, error: e.message }
    }
  })

  ipcMain.handle('vd:clearCache', async () => {
    try {
      const result = await vdManager.clearCache()
      return { success: true, data: result }
    } catch (e) {
      return { success: false, error: e.message }
    }
  })

  // ==================== 状态查询 ====================

  ipcMain.handle('vd:getStatus', () => {
    return {
      success: true,
      data: {
        isMounted: vdManager.isMounted,
        mountPoint: vdManager.mountPoint,
        daemonRunning: !!vdManager.daemonProcess && !vdManager.daemonProcess.killed,
        config: vdManager.config
      }
    }
  })

  ipcMain.handle('vd:checkAvailability', () => {
    return {
      success: true,
      data: VirtualDiskManager.checkAvailability()
    }
  })

  // ==================== 认证更新 ====================

  ipcMain.handle('vd:updateAuth', (event, { token, userId, apiBaseUrl }) => {
    vdManager.updateAuth({ token, userId, apiBaseUrl })
    return { success: true }
  })

  // ==================== 事件转发 (主进程 → 渲染进程) ====================

  // 监听虚拟磁盘事件并转发到渲染进程
  vdManager.on('mounted', (data) => {
    notifyAllWindows('vd:event', { event: 'mounted', data })
  })

  vdManager.on('unmounted', (data) => {
    notifyAllWindows('vd:event', { event: 'unmounted', data })
  })

  vdManager.on('daemon-exit', (data) => {
    notifyAllWindows('vd:event', { event: 'daemon-exit', data })
  })

  vdManager.on('daemon-error', (data) => {
    notifyAllWindows('vd:event', { event: 'daemon-error', data })
  })

  vdManager.on('upload-progress', (data) => {
    notifyAllWindows('vd:event', { event: 'upload-progress', data })
  })

  vdManager.on('upload-complete', (data) => {
    notifyAllWindows('vd:event', { event: 'upload-complete', data })
  })

  vdManager.on('sync-error', (data) => {
    notifyAllWindows('vd:event', { event: 'sync-error', data })
  })

  vdManager.on('delete-complete', (data) => {
    notifyAllWindows('vd:event', { event: 'delete-complete', data })
  })

  vdManager.on('folder-create-complete', (data) => {
    notifyAllWindows('vd:event', { event: 'folder-create-complete', data })
  })

  logger.info('IPC', '虚拟磁盘 IPC 处理器已注册')
}

/**
 * 向所有窗口广播事件
 */
function notifyAllWindows(channel, data) {
  const windows = BrowserWindow.getAllWindows()
  for (const win of windows) {
    if (!win.isDestroyed()) {
      win.webContents.send(channel, data)
    }
  }
}

/**
 * 获取 VirtualDiskManager 实例
 */
function getVDManager() {
  return vdManager
}

/**
 * 清理 (应用退出时调用)
 */
async function cleanupVD() {
  if (vdManager) {
    await vdManager.destroy()
    vdManager = null
  }
}

module.exports = {
  registerVDIpcHandlers,
  getVDManager,
  cleanupVD
}