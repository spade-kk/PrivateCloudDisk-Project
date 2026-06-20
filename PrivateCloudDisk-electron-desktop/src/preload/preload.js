/**
 * src/preload/preload.js - Electron 预加载脚本
 *
 * 通过 contextBridge 安全地暴露主进程 API 给渲染进程
 * 遵循 Electron 安全最佳实践: contextIsolation + sandbox
 */
const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('electronAPI', {
  // ==================== 对话框 ====================

  /** 打开文件选择对话框 */
  openFileDialog: (options) => ipcRenderer.invoke('dialog:openFile', options),

  /** 打开目录选择对话框 */
  openFolderDialog: () => ipcRenderer.invoke('dialog:openFolder'),

  /** 保存文件对话框 */
  saveFileDialog: (options) => ipcRenderer.invoke('dialog:saveFile', options),

  // ==================== 文件操作 ====================

  /** 读取整个文件 */
  readFile: (filePath) => ipcRenderer.invoke('file:read', filePath),

  /** 写入文件 */
  writeFile: (filePath, data) => ipcRenderer.invoke('file:write', { filePath, data }),

  /** 读取文件分片 (用于分片上传) */
  readFileChunk: (filePath, start, end) =>
    ipcRenderer.invoke('file:readChunk', { filePath, start, end }),

  /** 获取文件信息 */
  getFileStat: (filePath) => ipcRenderer.invoke('file:stat', filePath),

  // ==================== 系统 ====================

  /** 获取系统信息 */
  getSystemInfo: () => ipcRenderer.invoke('system:info'),

  /** 获取应用路径 */
  getAppPath: (name) => ipcRenderer.invoke('app:getPath', name),

  // ==================== 外部链接 ====================

  /** 在浏览器中打开链接 */
  openExternal: (url) => ipcRenderer.invoke('shell:openExternal', url),

  /** 在文件管理器显示 */
  showItemInFolder: (filePath) => ipcRenderer.invoke('shell:showItemInFolder', filePath),

  // ==================== 窗口 ====================

  minimizeWindow: () => ipcRenderer.send('window:minimize'),
  maximizeWindow: () => ipcRenderer.send('window:maximize'),
  closeWindow: () => ipcRenderer.send('window:close'),
  setTitle: (title) => ipcRenderer.send('window:setTitle', title),

  // ==================== 通知 ====================

  showNotification: (options) => ipcRenderer.invoke('notification:show', options),

  // ==================== 虚拟磁盘 ====================

  /** 挂载虚拟磁盘 */
  mountVirtualDisk: (options) => ipcRenderer.invoke('vd:mount', options),

  /** 卸载虚拟磁盘 */
  unmountVirtualDisk: () => ipcRenderer.invoke('vd:unmount'),

  /** 获取虚拟磁盘状态 */
  getVDStatus: () => ipcRenderer.invoke('vd:getStatus'),

  /** 获取虚拟磁盘统计 */
  getVDStats: () => ipcRenderer.invoke('vd:stats'),

  /** 全量同步 */
  syncVDAll: () => ipcRenderer.invoke('vd:syncAll'),

  /** 清空缓存 */
  clearVDCache: () => ipcRenderer.invoke('vd:clearCache'),

  /** 检查虚拟磁盘功能可用性 */
  checkVDAvailability: () => ipcRenderer.invoke('vd:checkAvailability'),

  /** 更新认证 */
  updateVDAuth: (auth) => ipcRenderer.invoke('vd:updateAuth', auth),

  // ==================== 事件监听 ====================

  /** 监听主进程事件 */
  on: (channel, callback) => {
    const validChannels = [
      'menu:upload',
      'menu:new-folder',
      'menu:navigate',
      'menu:go-back',
      'menu:check-update',
      'update:download-progress',
      'vd:event'
    ]
    if (validChannels.includes(channel)) {
      const subscription = (event, ...args) => callback(...args)
      ipcRenderer.on(channel, subscription)
      // 返回取消监听函数
      return () => ipcRenderer.removeListener(channel, subscription)
    }
  },

  /** 移除监听 */
  removeAllListeners: (channel) => {
    ipcRenderer.removeAllListeners(channel)
  }
})