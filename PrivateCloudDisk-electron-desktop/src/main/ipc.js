/**
 * src/main/ipc.js - IPC 通信处理器
 *
 * 提供主进程与渲染进程间的安全通信桥接
 * 所有 IPC 通道通过 preload.js 暴露给渲染进程
 */
const { ipcMain, dialog, shell, app } = require('electron')
const path = require('path')
const fs = require('fs')
const os = require('os')

/**
 * 注册所有 IPC 处理器
 */
function registerIpcHandlers() {
  // ==================== 文件对话框 ====================

  /** 打开文件选择对话框 */
  ipcMain.handle('dialog:openFile', async (event, options = {}) => {
    const result = await dialog.showOpenDialog({
      title: '选择文件',
      properties: ['openFile'],
      filters: [
        { name: '所有文件', extensions: ['*'] },
        { name: '图片', extensions: ['jpg', 'jpeg', 'png', 'gif', 'webp', 'bmp'] },
        { name: '视频', extensions: ['mp4', 'avi', 'mov', 'mkv'] },
        { name: '文档', extensions: ['pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'] },
        { name: '压缩包', extensions: ['zip', 'rar', '7z', 'tar', 'gz'] }
      ],
      ...options
    })
    return result.canceled ? null : result.filePaths[0]
  })

  /** 打开目录选择对话框 */
  ipcMain.handle('dialog:openFolder', async () => {
    const result = await dialog.showOpenDialog({
      title: '选择目录',
      properties: ['openDirectory']
    })
    return result.canceled ? null : result.filePaths[0]
  })

  /** 保存文件对话框 */
  ipcMain.handle('dialog:saveFile', async (event, options = {}) => {
    const result = await dialog.showSaveDialog({
      title: '保存文件',
      defaultPath: options.defaultName || 'download',
      ...options
    })
    return result.canceled ? null : result.filePath
  })

  // ==================== 文件操作 ====================

  /** 读取文件内容 */
  ipcMain.handle('file:read', async (event, filePath) => {
    try {
      const buffer = await fs.promises.readFile(filePath)
      return buffer
    } catch (e) {
      throw new Error(`读取文件失败: ${e.message}`)
    }
  })

  /** 写入文件 */
  ipcMain.handle('file:write', async (event, { filePath, data }) => {
    try {
      await fs.promises.writeFile(filePath, Buffer.from(data))
      return true
    } catch (e) {
      throw new Error(`写入文件失败: ${e.message}`)
    }
  })

  /** 获取文件信息 */
  ipcMain.handle('file:stat', async (event, filePath) => {
    try {
      const stat = await fs.promises.stat(filePath)
      return {
        size: stat.size,
        isFile: stat.isFile(),
        isDirectory: stat.isDirectory(),
        createdAt: stat.birthtime,
        modifiedAt: stat.mtime
      }
    } catch (e) {
      throw new Error(`获取文件信息失败: ${e.message}`)
    }
  })

  /** 从文件路径读取分片数据 */
  ipcMain.handle('file:readChunk', async (event, { filePath, start, end }) => {
    try {
      const fd = await fs.promises.open(filePath, 'r')
      const length = end - start
      const buffer = Buffer.alloc(length)
      await fd.read(buffer, 0, length, start)
      await fd.close()
      return buffer
    } catch (e) {
      throw new Error(`读取分片失败: ${e.message}`)
    }
  })

  // ==================== 系统信息 ====================

  /** 获取系统信息 */
  ipcMain.handle('system:info', async () => {
    return {
      platform: process.platform,
      arch: process.arch,
      version: app.getVersion(),
      electronVersion: process.versions.electron,
      nodeVersion: process.versions.node,
      chromeVersion: process.versions.chrome,
      homeDir: os.homedir(),
      tmpDir: os.tmpdir(),
      hostname: os.hostname(),
      totalMemory: os.totalmem(),
      freeMemory: os.freemem(),
      cpus: os.cpus().length
    }
  })

  /** 获取应用数据目录 */
  ipcMain.handle('app:getPath', async (event, name) => {
    return app.getPath(name)
  })

  // ==================== 外部链接 ====================

  /** 在默认浏览器中打开链接 */
  ipcMain.handle('shell:openExternal', async (event, url) => {
    await shell.openExternal(url)
  })

  /** 在文件管理器中显示文件 */
  ipcMain.handle('shell:showItemInFolder', async (event, filePath) => {
    shell.showItemInFolder(filePath)
  })

  // ==================== 窗口控制 ====================

  /** 最小化窗口 */
  ipcMain.on('window:minimize', (event) => {
    const win = event.sender.getOwnerBrowserWindow()
    if (win) win.minimize()
  })

  /** 最大化/还原窗口 */
  ipcMain.on('window:maximize', (event) => {
    const win = event.sender.getOwnerBrowserWindow()
    if (win) {
      win.isMaximized() ? win.unmaximize() : win.maximize()
    }
  })

  /** 关闭窗口 */
  ipcMain.on('window:close', (event) => {
    const win = event.sender.getOwnerBrowserWindow()
    if (win) win.close()
  })

  /** 设置窗口标题 */
  ipcMain.on('window:setTitle', (event, title) => {
    const win = event.sender.getOwnerBrowserWindow()
    if (win) win.setTitle(title)
  })

  // ==================== 通知 ====================

  /** 显示系统通知 */
  ipcMain.handle('notification:show', async (event, { title, body }) => {
    const { Notification } = require('electron')
    if (Notification.isSupported()) {
      new Notification({ title, body }).show()
      return true
    }
    return false
  })
}

module.exports = { registerIpcHandlers }