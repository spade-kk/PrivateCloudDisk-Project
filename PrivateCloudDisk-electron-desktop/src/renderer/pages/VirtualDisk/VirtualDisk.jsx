/**
 * renderer/pages/VirtualDisk/VirtualDisk.jsx - 虚拟磁盘管理页面
 *
 * 功能:
 * - 挂载/卸载虚拟磁盘
 * - 实时显示挂载状态、同步状态、缓存统计
 * - 管理缓存配额和同步设置
 * - 查看同步事件日志
 * - WebDAV 虚拟磁盘挂载/卸载 (无需额外内核扩展)
 * - 挂载指南 (macOS Finder / Windows 网络驱动器 / Linux davfs2)
 */

import React, { useState, useEffect, useCallback, useRef } from 'react'
import {
  Card, Button, Space, Select, Slider, Switch, Divider,
  Statistic, Tag, Progress, Table, message, Modal, Alert,
  Typography, Descriptions, Spin, Timeline, Badge, Tooltip, InputNumber
} from 'antd'
import {
  CloudServerOutlined, CloudUploadOutlined, CloudDownloadOutlined,
  SyncOutlined, DeleteOutlined, SettingOutlined, FolderOpenOutlined,
  PlayCircleOutlined, PauseCircleOutlined, ExclamationCircleOutlined,
  CheckCircleOutlined, HddOutlined, ApiOutlined, ReloadOutlined,
  InfoCircleOutlined
} from '@ant-design/icons'
import dayjs from 'dayjs'
import './VirtualDisk.css'

const { Title, Text } = Typography

// ==================== 常量 ====================

const DEFAULT_MOUNT_POINT = '~/PrivateCloudDisk'
const CACHE_SIZE_OPTIONS = [
  { label: '1 GB', value: 1 * 1024 * 1024 * 1024 },
  { label: '2 GB', value: 2 * 1024 * 1024 * 1024 },
  { label: '5 GB', value: 5 * 1024 * 1024 * 1024 },
  { label: '10 GB', value: 10 * 1024 * 1024 * 1024 },
  { label: '20 GB', value: 20 * 1024 * 1024 * 1024 },
  { label: '50 GB', value: 50 * 1024 * 1024 * 1024 }
]

// ==================== 工具函数 ====================

function formatBytes(bytes) {
  if (!bytes || bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return (bytes / Math.pow(k, i)).toFixed(2) + ' ' + units[i]
}

function formatPercent(value) {
  return value ? `${value}%` : '0%'
}

// ==================== 组件 ====================

export default function VirtualDisk() {
  // 状态
  const [status, setStatus] = useState({
    isMounted: false,
    mountPoint: null,
    daemonRunning: false
  })
  const [stats, setStats] = useState(null)
  const [availabilityStatus, setAvailabilityStatus] = useState(null)
  const [loading, setLoading] = useState(true)
  const [mounting, setMounting] = useState(false)
  const [events, setEvents] = useState([])
  const [polling, setPolling] = useState(true)

  // 配置
  const [mountPath, setMountPath] = useState(DEFAULT_MOUNT_POINT)
  const [cacheSize, setCacheSize] = useState(5 * 1024 * 1024 * 1024)
  const [mountOnStartup, setMountOnStartup] = useState(false)

  const pollTimerRef = useRef(null)
  const eventsEndRef = useRef(null)

  // ==================== 初始化 ====================

  useEffect(() => {
    initStatus()
    checkAvailability()
    setupEventListeners()

    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current)
    }
  }, [])

  // 轮询状态
  useEffect(() => {
    if (polling && status.isMounted) {
      pollTimerRef.current = setInterval(fetchStats, 3000)
    } else {
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current)
        pollTimerRef.current = null
      }
    }
    return () => {
      if (pollTimerRef.current) clearInterval(pollTimerRef.current)
    }
  }, [polling, status.isMounted])

  // ==================== 数据获取 ====================

  const initStatus = async () => {
    try {
      const res = await window.electronAPI.getVDStatus()
      if (res.success) {
        setStatus(res.data)
        if (res.data.isMounted) {
          await fetchStats()
        }
      }
    } catch (e) {
      console.error('获取虚拟磁盘状态失败:', e)
    } finally {
      setLoading(false)
    }
  }

  const fetchStats = async () => {
    try {
      const res = await window.electronAPI.getVDStats()
      if (res.success) {
        setStats(res.data)
      }
    } catch (e) {
      // 静默处理
    }
  }

  const checkAvailability = async () => {
    try {
      const res = await window.electronAPI.checkVDAvailability()
      if (res.success) {
        setAvailabilityStatus(res.data)
      }
    } catch (e) {
      // 静默处理
    }
  }

  // ==================== 事件监听 ====================

  const setupEventListeners = () => {
    window.electronAPI.on('vd:event', (data) => {
      const { event, data: eventData } = data
      setEvents(prev => {
        const newEvents = [
          {
            id: Date.now(),
            event,
            data: eventData,
            timestamp: new Date().toISOString()
          },
          ...prev
        ].slice(0, 50)  // 最多保留 50 条
        return newEvents
      })

      // 处理特定事件
      switch (event) {
        case 'mounted':
          setStatus(prev => ({ ...prev, isMounted: true, mountPoint: eventData.mountPoint }))
          message.success(`虚拟磁盘已挂载到 ${eventData.mountPoint}`)
          break
        case 'unmounted':
          setStatus(prev => ({ ...prev, isMounted: false }))
          setStats(null)
          message.info('虚拟磁盘已卸载')
          break
        case 'daemon-exit':
          setStatus(prev => ({ ...prev, isMounted: false, daemonRunning: false }))
          message.error('虚拟磁盘守护进程意外退出')
          break
        case 'upload-complete':
          message.success(`文件已同步: ${eventData.relativePath}`)
          break
        case 'sync-error':
          message.error(`同步失败: ${eventData.relativePath}`)
          break
        default:
          break
      }
    })
  }

  // ==================== 操作 ====================

  const handleMount = async () => {
    setMounting(true)
    try {
      const res = await window.electronAPI.mountVirtualDisk({
        mountPoint: mountPath.replace('~', require('os').homedir()),
        cacheMaxSize: cacheSize,
        mountOnStartup
      })
      if (res.success) {
        setStatus(prev => ({ ...prev, isMounted: true, mountPoint: mountPath }))
        message.success('虚拟磁盘挂载成功')
      } else {
        message.error(res.error || '挂载失败')
      }
    } catch (e) {
      message.error(`挂载失败: ${e.message}`)
    } finally {
      setMounting(false)
    }
  }

  const handleUnmount = async () => {
    Modal.confirm({
      title: '确认卸载',
      icon: <ExclamationCircleOutlined />,
      content: '卸载虚拟磁盘后，本地文件系统将不再显示云端文件。确定要卸载吗？',
      okText: '确定卸载',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await window.electronAPI.unmountVirtualDisk()
          if (res.success) {
            setStatus(prev => ({ ...prev, isMounted: false }))
            setStats(null)
            message.success('虚拟磁盘已卸载')
          } else {
            message.error(res.error || '卸载失败')
          }
        } catch (e) {
          message.error(`卸载失败: ${e.message}`)
        }
      }
    })
  }

  const handleSyncAll = async () => {
    try {
      await window.electronAPI.syncVDAll()
      message.success('全量同步已触发')
    } catch (e) {
      message.error(`同步失败: ${e.message}`)
    }
  }

  const handleClearCache = async () => {
    Modal.confirm({
      title: '确认清空缓存',
      icon: <ExclamationCircleOutlined />,
      content: '清空缓存后，所有本地缓存文件将被删除，下次访问需要重新下载。确定要清空吗？',
      okText: '确定清空',
      okType: 'danger',
      cancelText: '取消',
      onOk: async () => {
        try {
          await window.electronAPI.clearVDCache()
          message.success('缓存已清空')
          await fetchStats()
        } catch (e) {
          message.error(`清空失败: ${e.message}`)
        }
      }
    })
  }

  const handleOpenMountPoint = () => {
    if (status.mountPoint) {
      window.electronAPI.showItemInFolder(
        status.mountPoint.replace('~', require('os').homedir())
      )
    }
  }

  // ==================== 事件渲染 ====================

  const getEventIcon = (event) => {
    const iconMap = {
      'mounted': <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      'unmounted': <InfoCircleOutlined style={{ color: '#faad14' }} />,
      'upload-progress': <CloudUploadOutlined style={{ color: '#1890ff' }} />,
      'upload-complete': <CheckCircleOutlined style={{ color: '#52c41a' }} />,
      'delete-complete': <DeleteOutlined style={{ color: '#ff4d4f' }} />,
      'folder-create-complete': <FolderOpenOutlined style={{ color: '#722ed1' }} />,
      'sync-error': <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
      'daemon-exit': <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
      'daemon-error': <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />
    }
    return iconMap[event] || <InfoCircleOutlined style={{ color: '#999' }} />
  }

  const getEventLabel = (event) => {
    const labelMap = {
      'mounted': '挂载成功',
      'unmounted': '已卸载',
      'upload-progress': '上传中',
      'upload-complete': '上传完成',
      'delete-complete': '删除完成',
      'folder-create-complete': '文件夹创建',
      'sync-error': '同步错误',
      'daemon-exit': '守护进程退出',
      'daemon-error': '守护进程错误'
    }
    return labelMap[event] || event
  }

  // ==================== 渲染 ====================

  if (loading) {
    return (
      <div className="vd-page">
        <Spin size="large" tip="加载中..." style={{ marginTop: 200 }}>
          <div style={{ height: 200 }} />
        </Spin>
      </div>
    )
  }

  return (
    <div className="vd-page">
      <div className="vd-header">
        <Title level={4} style={{ margin: 0 }}>
          <CloudServerOutlined /> 虚拟磁盘管理
        </Title>
        <Space>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => { initStatus(); checkAvailability(); }}
          >
            刷新
          </Button>
        </Space>
      </div>

      {/* WebDAV 可用性提示 */}
      {availabilityStatus && !availabilityStatus.available && (
        <Alert
          type="warning"
          showIcon
          message="虚拟磁盘功能不可用"
          description={
            <div>
              <p>{availabilityStatus.note || '当前平台不支持虚拟磁盘功能'}</p>
              {availabilityStatus.mountInstruction && (
                <Tag color="blue">{availabilityStatus.mountInstruction}</Tag>
              )}
            </div>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      {/* WebDAV 挂载指南 */}
      {availabilityStatus && availabilityStatus.available && (
        <Alert
          type="info"
          showIcon
          message="WebDAV 虚拟磁盘 — 零依赖，开箱即用"
          description={
            <div>
              <p>虚拟磁盘使用 WebDAV (RFC 4918) 协议，{availabilityStatus.platform} 原生支持，无需安装任何额外软件。</p>
              <Tag color="green">{availabilityStatus.mountInstruction}</Tag>
            </div>
          }
          style={{ marginBottom: 16 }}
        />
      )}

      <div className="vd-content">
        {/* 左侧：控制面板 */}
        <div className="vd-left">
          {/* 挂载控制 */}
          <Card
            title={<><PlayCircleOutlined /> 挂载控制</>}
            className="vd-card"
          >
            <div className="mount-control">
              <div className="mount-status">
                <Badge
                  status={status.isMounted ? 'processing' : 'default'}
                  text={status.isMounted ? '已挂载' : '未挂载'}
                />
                {status.mountPoint && (
                  <Text type="secondary" className="mount-path">
                    {status.mountPoint}
                  </Text>
                )}
              </div>

              <Descriptions size="small" column={1} style={{ marginTop: 16 }}>
                <Descriptions.Item label="挂载点">
                  <Text
                    editable={{
                      onChange: setMountPath,
                      autoSize: true
                    }}
                  >
                    {mountPath}
                  </Text>
                </Descriptions.Item>
                <Descriptions.Item label="缓存配额">
                  <Select
                    value={cacheSize}
                    onChange={setCacheSize}
                    style={{ width: 120 }}
                    options={CACHE_SIZE_OPTIONS}
                    disabled={status.isMounted}
                  />
                </Descriptions.Item>
                <Descriptions.Item label="开机自启">
                  <Switch
                    checked={mountOnStartup}
                    onChange={setMountOnStartup}
                  />
                </Descriptions.Item>
              </Descriptions>

              <Divider />

              <Space>
                {!status.isMounted ? (
                  <Button
                    type="primary"
                    icon={<PlayCircleOutlined />}
                    onClick={handleMount}
                    loading={mounting}
                    size="large"
                    block
                  >
                    挂载虚拟磁盘
                  </Button>
                ) : (
                  <>
                    <Button
                      danger
                      icon={<PauseCircleOutlined />}
                      onClick={handleUnmount}
                    >
                      卸载
                    </Button>
                    <Button
                      icon={<FolderOpenOutlined />}
                      onClick={handleOpenMountPoint}
                    >
                      在 Finder 中打开
                    </Button>
                  </>
                )}
              </Space>
            </div>
          </Card>

          {/* 同步控制 */}
          <Card
            title={<><SyncOutlined /> 同步管理</>}
            className="vd-card"
          >
            <Space direction="vertical" style={{ width: '100%' }}>
              <Button
                icon={<SyncOutlined />}
                onClick={handleSyncAll}
                disabled={!status.isMounted}
                block
              >
                立即全量同步
              </Button>
              <Button
                icon={<DeleteOutlined />}
                onClick={handleClearCache}
                danger
                block
              >
                清空本地缓存
              </Button>
            </Space>
          </Card>
        </div>

        {/* 右侧：统计面板 */}
        <div className="vd-right">
          {/* 统计卡片 */}
          {stats && (
            <Card
              title={<><HddOutlined /> 运行统计</>}
              className="vd-card"
              extra={
                <Switch
                  checkedChildren="自动刷新"
                  unCheckedChildren="暂停"
                  checked={polling}
                  onChange={setPolling}
                />
              }
            >
              <div className="stats-grid">
                <Card size="small" className="stat-card">
                  <Statistic
                    title="元数据节点"
                    value={stats.metadata?.totalNodes || 0}
                    prefix={<ApiOutlined />}
                  />
                </Card>
                <Card size="small" className="stat-card">
                  <Statistic
                    title="缓存文件"
                    value={stats.cache?.fileCount || 0}
                    prefix={<HddOutlined />}
                  />
                </Card>
                <Card size="small" className="stat-card">
                  <Statistic
                    title="缓存大小"
                    value={formatBytes(stats.cache?.currentSize || 0)}
                  />
                </Card>
                <Card size="small" className="stat-card">
                  <Statistic
                    title="命中率"
                    value={formatPercent(stats.cache?.hitRate)}
                    suffix="%"
                  />
                </Card>
                <Card size="small" className="stat-card">
                  <Statistic
                    title="脏节点"
                    value={stats.metadata?.dirtyNodes || 0}
                    valueStyle={{ color: (stats.metadata?.dirtyNodes || 0) > 0 ? '#ff4d4f' : undefined }}
                  />
                </Card>
                <Card size="small" className="stat-card">
                  <Statistic
                    title="打开文件数"
                    value={stats.openFiles || 0}
                  />
                </Card>
              </div>

              {stats.cache && (
                <div style={{ marginTop: 16 }}>
                  <div className="cache-usage">
                    <Text>缓存使用率</Text>
                    <Progress
                      percent={parseFloat(stats.cache.utilization)}
                      strokeColor={{
                        '0%': '#108ee9',
                        '70%': '#faad14',
                        '100%': '#ff4d4f'
                      }}
                    />
                  </div>
                </div>
              )}

              <Descriptions size="small" column={2} style={{ marginTop: 16 }}>
                <Descriptions.Item label="进程 PID">
                  <Tag color="blue">{stats.pid}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="运行时间">
                  {Math.floor(stats.uptime || 0)}s
                </Descriptions.Item>
                <Descriptions.Item label="内存使用">
                  {stats.memoryUsage ? formatBytes(stats.memoryUsage.rss) : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="挂载点">
                  <Text code>{stats.mountPoint || '-'}</Text>
                </Descriptions.Item>
              </Descriptions>
            </Card>
          )}

          {/* 事件日志 */}
          <Card
            title="事件日志"
            className="vd-card"
            style={{ marginTop: 16 }}
          >
            {events.length === 0 ? (
              <div className="empty-events">
                <Text type="secondary">暂无事件</Text>
              </div>
            ) : (
              <div className="events-list">
                <Timeline
                  items={events.map(e => ({
                    dot: getEventIcon(e.event),
                    children: (
                      <div className="event-item">
                        <div className="event-header">
                          <Tag>{getEventLabel(e.event)}</Tag>
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {dayjs(e.timestamp).format('HH:mm:ss')}
                          </Text>
                        </div>
                        {e.data && (
                          <Text type="secondary" style={{ fontSize: 12 }}>
                            {typeof e.data === 'string'
                              ? e.data
                              : e.data.relativePath || e.data.error || JSON.stringify(e.data)}
                          </Text>
                        )}
                      </div>
                    )
                  }))}
                />
                <div ref={eventsEndRef} />
              </div>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}