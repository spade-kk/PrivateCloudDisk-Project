/**
 * components/ContextMenu.jsx - 右键菜单组件
 *
 * 在点击位置显示自定义上下文菜单
 * 点击外部自动关闭
 */
import React, { useEffect, useRef } from 'react'

export default function ContextMenu({ x, y, items, onClose }) {
  const menuRef = useRef(null)

  useEffect(() => {
    const handleClick = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        onClose()
      }
    }
    const handleEsc = (e) => {
      if (e.key === 'Escape') onClose()
    }

    // 延迟绑定, 避免当前右键事件触发关闭
    setTimeout(() => {
      document.addEventListener('click', handleClick)
      document.addEventListener('contextmenu', handleClick)
      document.addEventListener('keydown', handleEsc)
    }, 0)

    return () => {
      document.removeEventListener('click', handleClick)
      document.removeEventListener('contextmenu', handleClick)
      document.removeEventListener('keydown', handleEsc)
    }
  }, [onClose])

  // 调整菜单位置, 防止超出视口
  const menuStyle = { left: x, top: y }
  if (typeof window !== 'undefined') {
    if (x + 200 > window.innerWidth) menuStyle.left = x - 200
    if (y + items.length * 36 + 8 > window.innerHeight) menuStyle.top = y - items.length * 36 - 8
  }

  return (
    <div ref={menuRef} className="context-menu" style={menuStyle}>
      {items.map((item, idx) => {
        if (item.type === 'divider') {
          return <div key={idx} className="context-menu-divider" />
        }
        return (
          <div
            key={item.key}
            className={`context-menu-item ${item.danger ? 'danger' : ''}`}
            onClick={() => { item.onClick?.(); onClose() }}
          >
            {item.icon}
            <span>{item.label}</span>
          </div>
        )
      })}
    </div>
  )
}