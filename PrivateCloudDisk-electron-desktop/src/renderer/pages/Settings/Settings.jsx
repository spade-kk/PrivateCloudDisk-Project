/**
 * pages/Settings/Settings.jsx - 设置页面
 */
import React, { useState, useEffect } from 'react'
import { Card, Select, Switch, Button, Slider, message, Modal, Descriptions } from 'antd'
import { DeleteOutlined, InfoCircleOutlined } from '@ant-design/icons'
import { getSettings, setSettings } from '@/utils/storage'
import { formatFileSize } from '@/utils/helper'
import './Settings.css'

export default function SettingsPage() {
  const [settings, setSettingsState] = useState({
    theme: 'light',
    chunkSize: 5 * 1024 * 1024,
    autoLaunch: false,
    minimizeToTray: true,
    previewQuality: 'high'
  })
  const [cacheSize, setCacheSize] = useState('12.5 MB')
  const [systemInfo, setSystemInfo] = useState({})

  useEffect(() => {
    const saved = getSettings()
    if (saved && Object.keys(saved).length > 0) {
      setSettingsState(prev => ({ ...prev, ...saved }))
    }

    // 获取系统信息
    window.electronAPI?.getSystemInfo?.().then(setSystemInfo).catch(() => {})
  }, [])

  const updateSetting = (key, value) => {
    const updated = { ...settings, [key]: value }
    setSettingsState(updated)
    setSettings(updated)
  }

  const handleClearCache = () => {
    Modal.confirm({
      title: '清除缓存',
      content: '确定清除所有本地缓存吗？不会删除已下载的文件。',
      okText: '清除',
      cancelText: '取消',
      onOk: () => {
        setCacheSize('0 MB')
        message.success('缓存已清除')
      }
    })
  }

  return (
    <div className="settings-page">
      <div className="settings-layout">
        <Card title="通用设置" bordered={false}>
          <div className="setting-item">
            <div className="setting-label">
              <span>主题</span>
              <span className="setting-desc">选择应用外观主题</span>
            </div>
            <Select
              value={settings.theme}
              onChange={v => updateSetting('theme', v)}
              style={{ width: 140 }}
              options={[
                { value: 'light', label: '浅色' },
                { value: 'dark', label: '深色' },
                { value: 'system', label: '跟随系统' }
              ]}
            />
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span>上传分片大小</span>
              <span className="setting-desc">影响上传速度和稳定性</span>
            </div>
            <Select
              value={settings.chunkSize}
              onChange={v => updateSetting('chunkSize', v)}
              style={{ width: 140 }}
              options={[
                { value: 1 * 1024 * 1024, label: '1 MB' },
                { value: 2 * 1024 * 1024, label: '2 MB' },
                { value: 5 * 1024 * 1024, label: '5 MB' },
                { value: 10 * 1024 * 1024, label: '10 MB' }
              ]}
            />
          </div>

          <div className="setting-item">
            <div className="setting-label">
              <span>图片预览质量</span>
              <span className="setting-desc">缩略图和预览清晰度</span>
            </div>
            <Select
              value={settings.previewQuality}
              onChange={v => updateSetting('previewQuality', v)}
              style={{ width: 140 }}
              options={[
                { value: 'low', label: '低' },
                { value: 'medium', label: '中' },
                { value: 'high', label: '高' }
              ]}
            />
          </div>
        </Card>

        <Card title="托盘设置" bordered={false}>
          <div className="setting-item">
            <div className="setting-label">
              <span>最小化到托盘</span>
              <span className="setting-desc">关闭窗口时最小化到系统托盘</span>
            </div>
            <Switch
              checked={settings.minimizeToTray}
              onChange={v => updateSetting('minimizeToTray', v)}
            />
          </div>
        </Card>

        <Card title="缓存" bordered={false}>
          <div className="setting-item">
            <div className="setting-label">
              <span>缓存大小</span>
              <span className="setting-desc">当前本地缓存占用</span>
            </div>
            <div className="cache-actions">
              <span>{cacheSize}</span>
              <Button icon={<DeleteOutlined />} danger size="small" onClick={handleClearCache}>清除缓存</Button>
            </div>
          </div>
        </Card>

        <Card title="关于" bordered={false}>
          <Descriptions column={1} size="small">
            <Descriptions.Item label="版本号">v1.0.0</Descriptions.Item>
            <Descriptions.Item label="Electron">{systemInfo.electronVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="Node.js">{systemInfo.nodeVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="Chromium">{systemInfo.chromeVersion || '-'}</Descriptions.Item>
            <Descriptions.Item label="平台">{systemInfo.platform || '-'}</Descriptions.Item>
            <Descriptions.Item label="架构">{systemInfo.arch || '-'}</Descriptions.Item>
          </Descriptions>
        </Card>
      </div>
    </div>
  )
}