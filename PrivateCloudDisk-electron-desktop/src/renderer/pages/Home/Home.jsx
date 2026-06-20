/**
 * pages/Home/Home.jsx - 首页 (文件浏览主页面)
 *
 * 集成: FileList 组件 + 拖拽上传 + 快捷操作
 */
import React, { useState, useEffect } from 'react'
import { useLocation } from 'react-router-dom'
import { message } from 'antd'
import FileList from '@/components/FileList'
import UploadDialog from '@/components/UploadDialog'
import { useAppStore } from '@/store/appStore'
import './Home.css'

export default function HomePage() {
  const location = useLocation()
  const { resetDirectory } = useAppStore()
  const [uploadVisible, setUploadVisible] = useState(false)

  useEffect(() => {
    // 每次进入首页重置导航 (如果是通过侧边栏点击)
    if (location.pathname === '/home' || location.pathname === '/') {
      resetDirectory()
    }
  }, [location.pathname])

  // 监听菜单上传事件
  useEffect(() => {
    const cleanup = window.electronAPI?.on?.('menu:upload', () => {
      setUploadVisible(true)
    })
    return () => cleanup?.()
  }, [])

  // 监听新建文件夹事件
  useEffect(() => {
    const cleanup = window.electronAPI?.on?.('menu:new-folder', () => {
      // 触发 FileList 中的新建文件夹
      const createBtn = document.querySelector('[data-action="new-folder"]')
      createBtn?.click()
    })
    return () => cleanup?.()
  }, [])

  return (
    <div className="home-page">
      <FileList />
      <UploadDialog
        visible={uploadVisible}
        onClose={() => setUploadVisible(false)}
      />
    </div>
  )
}