/**
 * utils/platform.js - 跨平台适配工具
 *
 * 提供统一的平台检测和条件编译适配能力，
 * 解决微信小程序、支付宝小程序、百度小程序、字节小程序等平台间的差异。
 *
 * uni-app 条件编译原理：
 *   编译时根据 -p 参数（如 mp-weixin / mp-alipay）注入不同的全局变量，
 *   使用 #ifdef / #ifndef 预处理指令实现平台差异化代码。
 *
 * 平台特性对比：
 * ┌──────────────┬──────────┬──────────┬──────────┬──────────┐
 * │ 特性          │ 微信小程序 │ 支付宝小程序│ 百度小程序 │ 字节小程序 │
 * ├──────────────┼──────────┼──────────┼──────────┼──────────┤
 * │ getSystemInfo │ 同步     │ 同步     │ 同步     │ 同步     │
 * │ 蓝牙          │ 支持     │ 支持     │ 不支持   │ 不支持   │
 * │ 订阅消息      │ 支持     │ 不支持   │ 不支持   │ 不支持   │
 * │ 支付          │ 微信支付 │ 支付宝支付│ 百度支付 │ 抖音支付 │
 * │ 登录          │ wx.login │ my.getAuthCode│ swan.login│ tt.login │
 * │ 文件系统      │ wx.getFileSystem│ my.getFileSystem│ swan.getFileSystem│ tt.getFileSystem│
 * │ 导航栏自定义  │ 支持     │ 不支持(需配置)│ 支持   │ 支持     │
 * │ 分包加载      │ 支持     │ 支持     │ 支持     │ 支持     │
 * │ 暗黑模式      │ 支持     │ 不支持   │ 不支持   │ 不支持   │
 * └──────────────┴──────────┴──────────┴──────────┴──────────┘
 */

// ============================================================
// 平台常量
// ============================================================

/** 平台标识枚举 */
export const Platform = {
  WECHAT: 'mp-weixin',
  ALIPAY: 'mp-alipay',
  BAIDU: 'mp-baidu',
  TOUTIAO: 'mp-toutiao',
  H5: 'h5',
  APP: 'app',
}

/** 平台中文名称映射 */
export const PlatformNames = {
  [Platform.WECHAT]: '微信小程序',
  [Platform.ALIPAY]: '支付宝小程序',
  [Platform.BAIDU]: '百度小程序',
  [Platform.TOUTIAO]: '字节小程序',
  [Platform.H5]: 'H5 网页',
  [Platform.APP]: '移动 App',
}

// ============================================================
// 平台检测（运行时）
// ============================================================

/** 缓存的平台信息 */
let _platformInfo = null

/**
 * 获取当前平台信息（带缓存）
 * @returns {{ platform: string, name: string, brand: string, model: string, system: string, version: string, SDKVersion: string }}
 */
export function getPlatformInfo() {
  if (_platformInfo) return _platformInfo

  try {
    const info = uni.getSystemInfoSync()
    _platformInfo = {
      platform: info.platform || 'unknown',
      name: PlatformNames[info.platform] || '未知平台',
      brand: info.brand || '',
      model: info.model || '',
      system: info.system || '',
      version: info.version || '',
      SDKVersion: info.SDKVersion || '',
      pixelRatio: info.pixelRatio || 1,
      screenWidth: info.screenWidth || 375,
      screenHeight: info.screenHeight || 667,
      statusBarHeight: info.statusBarHeight || 0,
      safeArea: info.safeArea || null,
      // 是否为刘海屏设备
      hasNotch: (info.safeArea?.top || 0) > 20,
    }
  } catch (e) {
    console.warn('[Platform] getSystemInfoSync 失败:', e)
    _platformInfo = {
      platform: 'unknown',
      name: '未知平台',
      brand: '',
      model: '',
      system: '',
      version: '',
      SDKVersion: '',
      pixelRatio: 1,
      screenWidth: 375,
      screenHeight: 667,
      statusBarHeight: 0,
      safeArea: null,
      hasNotch: false,
    }
  }

  return _platformInfo
}

/**
 * 判断当前是否为微信小程序
 * @returns {boolean}
 */
export function isWechat() {
  return getPlatformInfo().platform === Platform.WECHAT
}

/**
 * 判断当前是否为支付宝小程序
 * @returns {boolean}
 */
export function isAlipay() {
  return getPlatformInfo().platform === Platform.ALIPAY
}

/**
 * 判断当前是否为百度小程序
 * @returns {boolean}
 */
export function isBaidu() {
  return getPlatformInfo().platform === Platform.BAIDU
}

/**
 * 判断当前是否为字节小程序
 * @returns {boolean}
 */
export function isToutiao() {
  return getPlatformInfo().platform === Platform.TOUTIAO
}

/**
 * 判断当前是否为小程序环境（任一平台）
 * @returns {boolean}
 */
export function isMiniProgram() {
  const p = getPlatformInfo().platform
  return [Platform.WECHAT, Platform.ALIPAY, Platform.BAIDU, Platform.TOUTIAO].includes(p)
}

/**
 * 判断当前是否为 H5 环境
 * @returns {boolean}
 */
export function isH5() {
  return getPlatformInfo().platform === Platform.H5
}

// ============================================================
// 平台适配 API 封装
// ============================================================

/**
 * 获取安全区域信息（适配刘海屏/底部指示条）
 *
 * 不同平台获取安全区域的方式不同：
 * - 微信小程序：uni.getSystemInfoSync().safeArea
 * - 支付宝小程序：my.getSystemInfoSync().safeArea（部分版本不支持）
 * - H5：CSS env(safe-area-inset-*)
 *
 * @returns {{ top: number, bottom: number, left: number, right: number, width: number, height: number }}
 */
export function getSafeArea() {
  const info = getPlatformInfo()
  if (info.safeArea) {
    return info.safeArea
  }
  // 回退默认值
  return {
    top: info.statusBarHeight || 0,
    bottom: info.screenHeight,
    left: 0,
    right: info.screenWidth,
    width: info.screenWidth,
    height: info.screenHeight - (info.statusBarHeight || 0),
  }
}

/**
 * 获取状态栏高度（适配各平台差异）
 * @returns {number} 状态栏高度（px）
 */
export function getStatusBarHeight() {
  const info = getPlatformInfo()
  return info.statusBarHeight || 0
}

/**
 * 获取导航栏总高度（状态栏 + 导航栏）
 * @returns {number} 导航栏总高度（px）
 */
export function getNavBarHeight() {
  const statusBarHeight = getStatusBarHeight()
  // 胶囊按钮高度 + 上下间距
  // #ifdef MP-WEIXIN
  try {
    const menuButton = uni.getMenuButtonBoundingClientRect()
    return menuButton.bottom + (menuButton.top - statusBarHeight)
  } catch (e) {
    return statusBarHeight + 44
  }
  // #endif
  // #ifndef MP-WEIXIN
  return statusBarHeight + 44
  // #endif
}

/**
 * 平台适配的存储读取（处理各平台存储差异）
 *
 * 微信小程序：uni.getStorageSync 默认同步
 * 支付宝小程序：my.getStorageSync 需注意 key 大小写敏感
 * 百度小程序：swan.getStorageSync 存储上限 10MB
 * 字节小程序：tt.getStorageSync 存储上限 10MB
 *
 * @param {string} key - 存储键
 * @param {*} [defaultValue=null] - 默认值
 * @returns {*}
 */
export function platformGetStorage(key, defaultValue = null) {
  try {
    const raw = uni.getStorageSync(key)
    if (raw === '' || raw === undefined || raw === null) return defaultValue
    // 尝试 JSON 解析，失败则返回原始值
    try {
      return JSON.parse(raw)
    } catch {
      return raw
    }
  } catch (e) {
    console.warn(`[Platform] getStorage("${key}") 失败:`, e)
    return defaultValue
  }
}

/**
 * 平台适配的存储写入（处理各平台存储差异）
 * @param {string} key - 存储键
 * @param {*} value - 存储值
 * @returns {boolean} 是否写入成功
 */
export function platformSetStorage(key, value) {
  try {
    const payload = typeof value === 'string' ? value : JSON.stringify(value)
    uni.setStorageSync(key, payload)
    return true
  } catch (e) {
    console.error(`[Platform] setStorage("${key}") 失败:`, e)
    return false
  }
}

// ============================================================
// 平台特性检测
// ============================================================

/**
 * 检测平台是否支持订阅消息
 * 仅微信小程序支持
 * @returns {boolean}
 */
export function supportsSubscribeMessage() {
  // #ifdef MP-WEIXIN
  return true
  // #endif
  return false
}

/**
 * 检测平台是否支持自定义导航栏
 * @returns {boolean}
 */
export function supportsCustomNavBar() {
  // #ifdef MP-ALIPAY
  // 支付宝小程序需在 app.json 中配置 transparentTitle: always
  return false
  // #endif
  return true
}

/**
 * 检测平台是否支持暗黑模式
 * 仅微信小程序自基础库 2.11.0 起支持
 * @returns {boolean}
 */
export function supportsDarkMode() {
  // #ifdef MP-WEIXIN
  const info = getPlatformInfo()
  return parseFloat(info.SDKVersion) >= 2.11
  // #endif
  return false
}

/**
 * 检测平台是否支持蓝牙
 * 仅微信小程序和支付宝小程序支持
 * @returns {boolean}
 */
export function supportsBluetooth() {
  // #ifdef MP-TOUTIAO || MP-BAIDU
  return false
  // #endif
  return true
}

// ============================================================
// 平台适配样式变量
// ============================================================

/**
 * 获取平台适配的 CSS 变量对象
 * 用于在 JS 中动态设置样式
 *
 * @returns {Object} CSS 变量集合
 */
export function getPlatformStyleVars() {
  const info = getPlatformInfo()
  const safeArea = getSafeArea()

  return {
    // 安全区域
    '--safe-area-top': `${safeArea.top}px`,
    '--safe-area-bottom': `${info.screenHeight - safeArea.bottom}px`,
    '--safe-area-left': `${safeArea.left}px`,
    '--safe-area-right': `${info.screenWidth - safeArea.right}px`,
    // 状态栏
    '--status-bar-height': `${info.statusBarHeight || 0}px`,
    // 导航栏
    '--nav-bar-height': `${getNavBarHeight()}px`,
    // 设备像素比
    '--device-pixel-ratio': String(info.pixelRatio || 1),
  }
}

// ============================================================
// 平台适配 Toast 提示
// ============================================================

/**
 * 平台适配的 Toast 提示
 *
 * 支付宝小程序：my.showToast 不支持 icon 为 'none' 的某些样式
 * 百度小程序：swan.showToast 的 duration 单位不同
 *
 * @param {string} title - 提示文本
 * @param {Object} [options] - 配置项
 * @param {string} [options.icon='none'] - 图标类型
 * @param {number} [options.duration=2000] - 持续时间（ms）
 */
export function platformToast(title, options = {}) {
  const { icon = 'none', duration = 2000 } = options

  // #ifdef MP-ALIPAY
  // 支付宝小程序不支持 icon: 'none'，使用空字符串
  uni.showToast({
    title,
    icon: icon === 'none' ? 'none' : icon,
    duration,
  })
  // #endif

  // #ifndef MP-ALIPAY
  uni.showToast({ title, icon, duration })
  // #endif
}

// ============================================================
// 平台适配页面跳转
// ============================================================

/**
 * 平台适配的页面跳转
 *
 * 微信小程序：navigateTo 最多 10 层，超过自动使用 redirectTo
 * 支付宝小程序：navigateTo 最多 5 层
 * 百度小程序：navigateTo 最多 10 层
 *
 * @param {string} url - 目标页面路径
 * @param {string} [method='navigateTo'] - 跳转方式
 */
export function platformNavigate(url, method = 'navigateTo') {
  const pages = getCurrentPages()
  const maxPages = isAlipay() ? 5 : 10

  if (method === 'navigateTo' && pages.length >= maxPages) {
    // 页面栈已满，使用 redirectTo
    uni.redirectTo({ url })
  } else {
    uni[method]({ url })
  }
}

/**
 * 获取当前页面栈深度
 * @returns {number}
 */
export function getCurrentPagesDepth() {
  try {
    const pages = getCurrentPages()
    return pages.length
  } catch (e) {
    return 0
  }
}