/**
 * components/Sidebar.jsx - 侧边栏导航组件
 *
 * 导航项: 首页 / 收藏 / 回收站 / 搜索 / 设置
 * 底部: 用户头像 + 配额信息
 */
import React, { useState } from 'react'
import { useNavigate, useLocation } from 'react-router-dom'
import { Menu, Avatar, Dropdown, Progress, Modal, Tooltip } from 'antd'
import {
  HomeOutlined, StarOutlined, DeleteOutlined, SearchOutlined,
  SettingOutlined, UserOutlined, LogoutOutlined, UploadOutlined,
  MenuFoldOutlined, MenuUnfoldOutlined, FolderAddOutlined, CloudOutlined
} from '@ant-design/icons'
import { useUserStore } from '@/store/userStore'
import { useAppStore } from '@/store/appStore'
import { formatFileSize } from '@/utils/helper'
import './Sidebar.css'

export default function Sidebar() {
  const navigate = useNavigate()
  const location = useLocation()
  const { profile, displayName, avatarUrl, logout } = useUserStore()
  const { sidebarCollapsed, toggleSidebar, quota, resetDirectory } = useAppStore()
  const [folderModalVisible, setFolderModalVisible] = useState(false)

  const currentPath = location.pathname === '/' ? '/home' : location.pathname

  const menuItems = [
    { key: '/home', icon: <HomeOutlined />, label: '首页' },
    { key: '/favorites', icon: <StarOutlined />, label: '收藏' },
    { key: '/trash', icon: <DeleteOutlined />, label: '回收站' },
    { key: '/search', icon: <SearchOutlined />, label: '搜索' },
    { key: '/settings', icon: <SettingOutlined />, label: '设置' }
  ]

  const onMenuClick = ({ key }) => {
    if (key === '/home') resetDirectory()
    navigate(key)
  }

  const handleLogout = () => {
    Modal.confirm({
      title: '确认退出',
      content: '确定要退出登录吗？',
      okText: '退出',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: () => {
        logout()
        navigate('/login', { replace: true })
      }
    })
  }

  const quotaPercent = quota
    ? Math.round((quota.used_capacity / quota.total_capacity) * 100)
    : 0

  const userMenuItems = [
    { key: 'profile', icon: <UserOutlined />, label: '个人信息', onClick: () => navigate('/profile') },
    { key: 'settings', icon: <SettingOutlined />, label: '设置', onClick: () => navigate('/settings') },
    { type: 'divider' },
    { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', danger: true, onClick: handleLogout }
  ]

  return (
    <div className="sidebar">
      {/* Logo */}
      <div className="sidebar-logo">
        <CloudOutlined style={{ fontSize: 24, color: '#1a73e8' }} />
        {!sidebarCollapsed && <span className="logo-text">PrivateCloudDisk</span>}
      </div>

      {/* 快捷操作 */}
      {!sidebarCollapsed && (
        <div className="sidebar-actions">
          <Tooltip title="上传文件">
            <button className="action-btn primary" data-action="upload">
              <UploadOutlined /> 上传文件
            </button>
          </Tooltip>
          <Tooltip title="新建文件夹">
            <button className="action-btn" onClick={() => setFolderModalVisible(true)}>
              <FolderAddOutlined /> 新建文件夹
            </button>
          </Tooltip>
        </div>
      )}

      {/* 导航菜单 */}
      <Menu
        mode="inline"
        selectedKeys={[currentPath]}
        items={menuItems}
        onClick={onMenuClick}
        className="sidebar-menu"
        inlineCollapsed={sidebarCollapsed}
      />

      {/* 侧边栏折叠按钮 */}
      <div className="sidebar-toggle" onClick={toggleSidebar}>
        {sidebarCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
      </div>

      {/* 用户信息 */}
      <div className="sidebar-user">
        <Dropdown menu={{ items: userMenuItems }} placement="topRight" trigger={['click']}>
          <div className="user-info">
            <Avatar
              size={32}
              src={avatarUrl()}
              icon={<UserOutlined />}
              style={{ backgroundColor: '#1a73e8' }}
            />
            {!sidebarCollapsed && (
              <div className="user-detail">
                <span className="user-name">{displayName()}</span>
                <span className="user-account">{profile?.account || ''}</span>
              </div>
            )}
          </div>
        </Dropdown>
      </div>

      {/* 配额信息 */}
      {!sidebarCollapsed && quota && (
        <div className="sidebar-quota">
          <div className="quota-header">
            <span>存储空间</span>
            <span>{formatFileSize(quota.used_capacity)} / {formatFileSize(quota.total_capacity)}</span>
          </div>
          <Progress
            percent={quotaPercent}
            size="small"
            strokeColor={quotaPercent > 90 ? '#ea4335' : '#1a73e8'}
            showInfo={false}
          />
        </div>
      )}
    </div>
  )
}