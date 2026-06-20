/**
 * components/Breadcrumb.jsx - 面包屑导航组件
 *
 * 显示当前目录路径, 支持点击跳转到任意层级目录
 */
import React from 'react'
import { Breadcrumb as AntBreadcrumb } from 'antd'
import { HomeOutlined } from '@ant-design/icons'
import { useAppStore } from '@/store/appStore'
import './Breadcrumb.css'

export default function Breadcrumb() {
  const { directoryStack, navigateToDirectory } = useAppStore()

  const items = directoryStack.map((item, index) => ({
    title: index === 0 ? <><HomeOutlined /> {item.name}</> : item.name,
    key: item.id,
    onClick: () => navigateToDirectory(index)
  }))

  return (
    <AntBreadcrumb
      className="breadcrumb-nav"
      items={items.map((item, i) => ({
        key: item.key,
        title: (
          <span
            className={`breadcrumb-item ${i === items.length - 1 ? 'current' : ''}`}
            onClick={i < items.length - 1 ? item.onClick : undefined}
          >
            {item.title}
          </span>
        )
      }))}
    />
  )
}