/**
 * components/TitleBar.jsx - 自定义标题栏组件
 *
 * 企业级自定义标题栏，实现与 VS Code / 百度网盘 / 网易云音乐类似的
 * 无边框窗口标题栏体验。
 *
 * 功能：
 *   - Windows: 左侧 Logo + 自定义菜单栏，右侧窗口控制按钮（最小化/最大化/关闭）
 *   - macOS:   左侧留出原生红绿灯空间，居中窗口标题
 *   - 响应窗口最大化/还原事件，切换按钮图标
 *   - 支持拖拽移动窗口（-webkit-app-region: drag）
 *
 * 设计参考：
 *   - VS Code: 自定义标题栏 + 菜单栏集成
 *   - 百度网盘: Logo + 标题 + 窗口控制按钮
 *   - 网易云音乐: 左侧 Logo + 右侧窗口控制
 */
import React, { useState, useEffect, useCallback, useRef } from 'react'
import { CloudOutlined } from '@ant-design/icons'
import './TitleBar.css'

// ==================== 窗口控制按钮 SVG 图标 ====================

/** 最小化图标 */
const MinimizeIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <rect x="1" y="5.5" width="10" height="1" fill="currentColor" />
  </svg>
)

/** 最大化图标 */
const MaximizeIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <rect x="1.5" y="1.5" width="9" height="9" rx="0.5" stroke="currentColor" strokeWidth="1" />
  </svg>
)

/** 还原图标（窗口已最大化时显示） */
const RestoreIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <rect x="3" y="0.5" width="8" height="8" rx="0.5" stroke="currentColor" strokeWidth="1" />
    <rect x="0.5" y="3.5" width="8" height="8" rx="0.5" fill="var(--titlebar-bg)" stroke="currentColor" strokeWidth="1" />
  </svg>
)

/** 关闭图标 */
const CloseIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <path d="M1 1L11 11M11 1L1 11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
)

// ==================== 主组件 ====================

export default function TitleBar({ title = '私有云' }) {
  const [isMaximized, setIsMaximized] = useState(false)
  const [isMac, setIsMac] = useState(false)
  const [menuTemplate, setMenuTemplate] = useState([])
  const [activeMenuIndex, setActiveMenuIndex] = useState(-1)
  const menuRef = useRef(null)

  // ==================== 初始化 ====================
  useEffect(() => {
    const init = async () => {
      try {
        // 获取平台信息
        if (window.electronAPI?.getPlatform) {
          const platform = await window.electronAPI.getPlatform()
          setIsMac(platform === 'darwin')
        }

        // 获取窗口最大化状态
        if (window.electronAPI?.isMaximized) {
          const maximized = await window.electronAPI.isMaximized()
          setIsMaximized(maximized)
        }

        // 获取菜单模板（Windows 平台）
        if (window.electronAPI?.getMenuTemplate) {
          const { template } = await window.electronAPI.getMenuTemplate()
          setMenuTemplate(template)
        }
      } catch (e) {
        console.warn('[TitleBar] 初始化失败:', e)
      }
    }
    init()
  }, [])

  // ==================== 监听窗口最大化/还原事件 ====================
  useEffect(() => {
    if (!window.electronAPI?.on) return

    const cleanup = window.electronAPI.on('window:maximizeChange', (maximized) => {
      setIsMaximized(maximized)
    })

    return () => {
      if (typeof cleanup === 'function') cleanup()
    }
  }, [])

  // ==================== 点击外部关闭菜单 ====================
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setActiveMenuIndex(-1)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [])

  // ==================== 窗口控制 ====================
  const handleMinimize = useCallback(() => {
    window.electronAPI?.minimizeWindow()
  }, [])

  const handleMaximize = useCallback(() => {
    window.electronAPI?.maximizeWindow()
  }, [])

  const handleClose = useCallback(() => {
    window.electronAPI?.closeWindow()
  }, [])

  // ==================== 菜单操作 ====================
  const handleMenuClick = useCallback((index) => {
    setActiveMenuIndex(prev => prev === index ? -1 : index)
  }, [])

  const handleMenuHover = useCallback((index) => {
    if (activeMenuIndex >= 0) {
      setActiveMenuIndex(index)
    }
  }, [activeMenuIndex])

  const handleMenuItemClick = useCallback(async (e, item) => {
    e.stopPropagation()

    if (item.type === 'separator') return

    // 关闭菜单
    setActiveMenuIndex(-1)

    if (!item.id) return

    // 导航类菜单项通过自定义事件触发
    if (item.id.startsWith('nav-')) {
      const pathMap = {
        'nav-home': '/home',
        'nav-favorites': '/favorites',
        'nav-trash': '/trash',
        'nav-search': '/search',
      }
      if (pathMap[item.id]) {
        window.dispatchEvent(new CustomEvent('titlebar:navigate', { detail: pathMap[item.id] }))
      }
      if (item.id === 'nav-back') {
        window.dispatchEvent(new CustomEvent('titlebar:go-back'))
      }
      return
    }

    // 上传文件
    if (item.id === 'upload') {
      window.dispatchEvent(new CustomEvent('titlebar:upload'))
      return
    }

    // 新建文件夹
    if (item.id === 'new-folder') {
      window.dispatchEvent(new CustomEvent('titlebar:new-folder'))
      return
    }

    // 检查更新
    if (item.id === 'check-update') {
      window.dispatchEvent(new CustomEvent('titlebar:check-update'))
      return
    }

    // 其他菜单项通过 IPC 执行
    if (window.electronAPI?.executeMenuAction) {
      try {
        await window.electronAPI.executeMenuAction(item.id)
      } catch (e) {
        console.warn('[TitleBar] 菜单操作失败:', item.id, e)
      }
    }
  }, [])

  // ==================== 渲染 ====================
  return (
    <div className={`titlebar ${isMac ? 'titlebar--mac' : ''}`}>
      {/* 左侧：Logo + 应用名称 */}
      <div className="titlebar-logo">
        <CloudOutlined style={{ fontSize: 16, color: '#1a73e8' }} />
        <span>私有云</span>
      </div>

      {/* 自定义菜单栏（仅 Windows） */}
      {!isMac && menuTemplate.length > 0 && (
        <div className="titlebar-menu" ref={menuRef}>
          {menuTemplate.map((menu, index) => (
            <div
              key={menu.id}
              className={`titlebar-menu__item ${activeMenuIndex === index ? 'titlebar-menu__item--active' : ''}`}
              onClick={() => handleMenuClick(index)}
              onMouseEnter={() => handleMenuHover(index)}
            >
              {menu.label}
              {/* 下拉子菜单 */}
              {menu.submenu && menu.submenu.length > 0 && (
                <div className="titlebar-menu__submenu">
                  {menu.submenu.map((item, subIndex) => {
                    if (item.type === 'separator') {
                      return (
                        <div
                          key={`sep-${subIndex}`}
                          className="titlebar-menu__submenu-item titlebar-menu__submenu-item--separator"
                        />
                      )
                    }
                    return (
                      <div
                        key={item.id || subIndex}
                        className="titlebar-menu__submenu-item"
                        onClick={(e) => handleMenuItemClick(e, item)}
                      >
                        <span>{item.label}</span>
                        {item.accelerator && (
                          <span className="titlebar-menu__submenu-accelerator">
                            {item.accelerator}
                          </span>
                        )}
                      </div>
                    )
                  })}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {/* 居中：窗口标题 */}
      <div className="titlebar-title">{title}</div>

      {/* 右侧：窗口控制按钮（仅 Windows） */}
      {!isMac && (
        <div className="titlebar-controls">
          <button
            className="titlebar-controls__btn"
            onClick={handleMinimize}
            aria-label="最小化"
            title="最小化"
          >
            <MinimizeIcon />
          </button>
          <button
            className="titlebar-controls__btn"
            onClick={handleMaximize}
            aria-label={isMaximized ? '还原' : '最大化'}
            title={isMaximized ? '还原' : '最大化'}
          >
            {isMaximized ? <RestoreIcon /> : <MaximizeIcon />}
          </button>
          <button
            className="titlebar-controls__btn titlebar-controls__btn--close"
            onClick={handleClose}
            aria-label="关闭"
            title="关闭"
          >
            <CloseIcon />
          </button>
        </div>
      )}
    </div>
  )
}