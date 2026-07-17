// ============================================================
// Docker 管理页面
// 容器管理、镜像管理、日志查看
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Descriptions,
  Progress, Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Badge, Tooltip, Statistic, Row, Col,
} from 'antd'
import {
  ReloadOutlined, PlayCircleOutlined, PauseCircleOutlined, StopOutlined,
  DeleteOutlined, FileTextOutlined, ContainerOutlined, InboxOutlined,
  SyncOutlined, SearchOutlined,
} from '@ant-design/icons'
import { useOpsMonitorStore } from '@/stores/opsMonitorStore'
import type { DockerContainer, DockerImage } from '@/api/opsMonitor'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'

const { Text, Paragraph } = Typography
const { TextArea } = Input

// 格式化字节
function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

// 容器状态映射
const containerStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  RUNNING: { color: 'green', icon: <PlayCircleOutlined /> },
  STOPPED: { color: 'red', icon: <StopOutlined /> },
  PAUSED: { color: 'orange', icon: <PauseCircleOutlined /> },
  RESTARTING: { color: 'blue', icon: <SyncOutlined spin /> },
  EXITED: { color: 'default', icon: <StopOutlined /> },
  DEAD: { color: 'red', icon: <StopOutlined /> },
}

export default function DockerManagePage() {
  const {
    containers, containersTotal, currentContainer, containerLogs,
    images, imagesTotal, loading, error,
    fetchContainers, fetchContainerDetail, fetchContainerLogs, doContainerAction,
    fetchImages, doRemoveImage,
  } = useOpsMonitorStore()

  const [activeTab, setActiveTab] = useState('containers')
  const [logModalVisible, setLogModalVisible] = useState(false)
  const [detailModalVisible, setDetailModalVisible] = useState(false)
  const [searchName, setSearchName] = useState('')
  const [statusFilter, setStatusFilter] = useState<string | undefined>()

  const loadContainers = useCallback(() => {
    fetchContainers({ page: 1, pageSize: 50, name: searchName || undefined, status: statusFilter })
  }, [fetchContainers, searchName, statusFilter])

  const loadImages = useCallback(() => {
    fetchImages({ page: 1, pageSize: 50 })
  }, [fetchImages])

  useEffect(() => {
    loadContainers()
  }, [loadContainers])

  useEffect(() => {
    if (activeTab === 'images') loadImages()
  }, [activeTab, loadImages])

  // 容器操作
  const handleContainerAction = async (containerId: string, action: 'start' | 'stop' | 'restart' | 'pause' | 'unpause') => {
    const actionLabels: Record<string, string> = {
      start: '启动', stop: '停止', restart: '重启', pause: '暂停', unpause: '恢复',
    }
    const success = await doContainerAction(containerId, action)
    if (success) {
      message.success(`${actionLabels[action]}成功`)
      loadContainers()
    } else {
      message.error(`${actionLabels[action]}失败`)
    }
  }

  // 查看日志
  const handleViewLogs = async (containerId: string) => {
    await fetchContainerLogs(containerId, { tail: 500 })
    setLogModalVisible(true)
  }

  // 查看详情
  const handleViewDetail = async (containerId: string) => {
    await fetchContainerDetail(containerId)
    setDetailModalVisible(true)
  }

  // 删除镜像
  const handleRemoveImage = async (imageId: string) => {
    const success = await doRemoveImage(imageId)
    if (success) {
      message.success('镜像已删除')
    } else {
      message.error('删除失败')
    }
  }

  // 汇总统计
  const summary = useMemo(() => {
    const running = containers.filter((c) => c.status === 'RUNNING').length
    const stopped = containers.filter((c) => c.status === 'STOPPED' || c.status === 'EXITED').length
    const unhealthy = containers.filter((c) => c.healthStatus === 'UNHEALTHY').length
    return { running, stopped, unhealthy, total: containers.length }
  }, [containers])

  // 容器列表列定义
  const containerColumns: ColumnsType<DockerContainer> = [
    {
      title: '容器名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (name: string, record: DockerContainer) => (
        <Space>
          <Badge status={record.status === 'RUNNING' ? 'success' : record.status === 'RESTARTING' ? 'processing' : 'error'} />
          <a onClick={() => handleViewDetail(record.containerId)}>{name}</a>
        </Space>
      ),
    },
    {
      title: '镜像',
      dataIndex: 'image',
      key: 'image',
      width: 200,
      render: (image: string, record: DockerContainer) => (
        <Tooltip title={`${image}:${record.imageTag}`}>
          <Text ellipsis style={{ maxWidth: 180 }}>{image}:{record.imageTag}</Text>
        </Tooltip>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const cfg = containerStatusMap[status] || { color: 'default', icon: null }
        return <Tag color={cfg.color} icon={cfg.icon}>{status}</Tag>
      },
    },
    {
      title: '健康',
      dataIndex: 'healthStatus',
      key: 'healthStatus',
      width: 90,
      render: (hs: string) => {
        const colorMap: Record<string, string> = { HEALTHY: 'green', UNHEALTHY: 'red', STARTING: 'blue', NONE: 'default' }
        return <Tag color={colorMap[hs] || 'default'}>{hs}</Tag>
      },
    },
    {
      title: 'CPU',
      dataIndex: 'cpuUsage',
      key: 'cpuUsage',
      width: 100,
      render: (v: number) => (
        <Progress percent={Math.round(v * 100)} size="small" showInfo={false} />
      ),
    },
    {
      title: '内存',
      dataIndex: 'memoryUsageBytes',
      key: 'memoryUsageBytes',
      width: 120,
      render: (used: number, record: DockerContainer) => (
        <Space direction="vertical" size={0} style={{ width: '100%' }}>
          <Text style={{ fontSize: 11 }}>{formatBytes(used)}</Text>
          <Progress
            percent={record.memoryLimitBytes > 0 ? (used / record.memoryLimitBytes) * 100 : 0}
            size="small" showInfo={false}
          />
        </Space>
      ),
    },
    {
      title: '端口',
      dataIndex: 'ports',
      key: 'ports',
      width: 180,
      render: (ports: DockerContainer['ports']) => ports.length === 0 ? '-' : ports.map((p) => (
        <Tag key={`${p.hostPort}:${p.containerPort}`} style={{ marginBottom: 2 }}>
          {p.hostPort}:{p.containerPort}/{p.protocol}
        </Tag>
      )),
    },
    {
      title: '节点',
      dataIndex: 'nodeName',
      key: 'nodeName',
      width: 120,
    },
    {
      title: '重启次数',
      dataIndex: 'restarts',
      key: 'restarts',
      width: 90,
      render: (v: number) => v > 5 ? <Tag color="red">{v}</Tag> : v,
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_: unknown, record: DockerContainer) => (
        <Space size="small">
          {record.status === 'STOPPED' || record.status === 'EXITED' ? (
            <Tooltip title="启动"><Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleContainerAction(record.containerId, 'start')} /></Tooltip>
          ) : null}
          {record.status === 'RUNNING' ? (
            <>
              <Tooltip title="暂停"><Button size="small" icon={<PauseCircleOutlined />} onClick={() => handleContainerAction(record.containerId, 'pause')} /></Tooltip>
              <Tooltip title="停止"><Button size="small" icon={<StopOutlined />} onClick={() => handleContainerAction(record.containerId, 'stop')} /></Tooltip>
              <Tooltip title="重启"><Button size="small" icon={<SyncOutlined />} onClick={() => handleContainerAction(record.containerId, 'restart')} /></Tooltip>
            </>
          ) : null}
          {record.status === 'PAUSED' ? (
            <Tooltip title="恢复"><Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleContainerAction(record.containerId, 'unpause')} /></Tooltip>
          ) : null}
          <Tooltip title="日志"><Button size="small" icon={<FileTextOutlined />} onClick={() => handleViewLogs(record.containerId)} /></Tooltip>
        </Space>
      ),
    },
  ]

  // 镜像列表列定义
  const imageColumns: ColumnsType<DockerImage> = [
    { title: '仓库', dataIndex: 'repository', key: 'repository', width: 200, ellipsis: true },
    { title: '标签', dataIndex: 'tag', key: 'tag', width: 120, render: (t: string) => <Tag>{t}</Tag> },
    { title: '大小', dataIndex: 'size', key: 'size', width: 100, render: (v: number) => formatBytes(v) },
    { title: '创建时间', dataIndex: 'created', key: 'created', width: 160 },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, record: DockerImage) => (
        <Popconfirm title="确定删除此镜像？" onConfirm={() => handleRemoveImage(record.imageId)}>
          <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      {/* 汇总卡片 */}
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="容器总数" value={summary.total} prefix={<ContainerOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="运行中" value={summary.running} valueStyle={{ color: '#3f8600' }} prefix={<PlayCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="已停止" value={summary.stopped} valueStyle={{ color: summary.stopped > 0 ? '#cf1322' : undefined }} prefix={<StopOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="异常" value={summary.unhealthy} valueStyle={{ color: summary.unhealthy > 0 ? '#cf1322' : '#3f8600' }} prefix={<InboxOutlined />} /></Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      {/* 标签页 */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        items={[
          {
            key: 'containers',
            label: `容器 (${containersTotal})`,
            children: (
              <Card
                extra={
                  <Space>
                    <Input
                      placeholder="搜索容器名称"
                      prefix={<SearchOutlined />}
                      allowClear
                      style={{ width: 200 }}
                      value={searchName}
                      onChange={(e) => setSearchName(e.target.value)}
                      onPressEnter={loadContainers}
                    />
                    <Select
                      placeholder="状态"
                      allowClear
                      style={{ width: 120 }}
                      value={statusFilter}
                      onChange={(v) => setStatusFilter(v)}
                      options={[
                        { label: '运行中', value: 'RUNNING' },
                        { label: '已停止', value: 'STOPPED' },
                        { label: '已暂停', value: 'PAUSED' },
                        { label: '已退出', value: 'EXITED' },
                      ]}
                    />
                    <Button icon={<ReloadOutlined />} onClick={loadContainers}>刷新</Button>
                  </Space>
                }
              >
                <Spin spinning={loading}>
                  {containers.length === 0 && !loading ? (
                    <Empty description="暂无容器" />
                  ) : (
                    <Table
                      dataSource={containers}
                      columns={containerColumns}
                      rowKey="containerId"
                      pagination={{ pageSize: 20, showSizeChanger: true, showTotal: (t) => `共 ${t} 个容器` }}
                      size="middle"
                      scroll={{ x: 1400 }}
                    />
                  )}
                </Spin>
              </Card>
            ),
          },
          {
            key: 'images',
            label: `镜像 (${imagesTotal})`,
            children: (
              <Card extra={<Button icon={<ReloadOutlined />} onClick={loadImages}>刷新</Button>}>
                <Spin spinning={loading}>
                  {images.length === 0 && !loading ? (
                    <Empty description="暂无镜像" />
                  ) : (
                    <Table
                      dataSource={images}
                      columns={imageColumns}
                      rowKey="imageId"
                      pagination={{ pageSize: 20, showSizeChanger: true }}
                      size="middle"
                    />
                  )}
                </Spin>
              </Card>
            ),
          },
        ]}
      />

      {/* 容器详情弹窗 */}
      <Modal
        title="容器详情"
        open={detailModalVisible}
        onCancel={() => setDetailModalVisible(false)}
        footer={null}
        width={700}
        destroyOnClose
      >
        {currentContainer && (
          <Descriptions bordered size="small" column={2}>
            <Descriptions.Item label="名称">{currentContainer.name}</Descriptions.Item>
            <Descriptions.Item label="ID">{currentContainer.containerId}</Descriptions.Item>
            <Descriptions.Item label="镜像">{currentContainer.image}:{currentContainer.imageTag}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={containerStatusMap[currentContainer.status]?.color}>{currentContainer.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="健康状态">{currentContainer.healthStatus}</Descriptions.Item>
            <Descriptions.Item label="重启次数">{currentContainer.restarts}</Descriptions.Item>
            <Descriptions.Item label="CPU 使用">{currentContainer.cpuUsage.toFixed(2)}</Descriptions.Item>
            <Descriptions.Item label="内存使用">{formatBytes(currentContainer.memoryUsageBytes)}</Descriptions.Item>
            <Descriptions.Item label="网络接收">{formatBytes(currentContainer.networkRxBytes)}</Descriptions.Item>
            <Descriptions.Item label="网络发送">{formatBytes(currentContainer.networkTxBytes)}</Descriptions.Item>
            <Descriptions.Item label="端口">
              {currentContainer.ports.map((p) => <Tag key={p.hostPort}>{p.hostPort}:{p.containerPort}/{p.protocol}</Tag>)}
            </Descriptions.Item>
            <Descriptions.Item label="挂载卷">
              {currentContainer.volumes.map((v) => <Tag key={v.hostPath}>{v.hostPath} → {v.containerPath}</Tag>)}
            </Descriptions.Item>
            <Descriptions.Item label="环境变量" span={2}>
              {currentContainer.env.map((e) => <Tag key={e.key}>{e.key}={e.value}</Tag>)}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>

      {/* 日志弹窗 */}
      <Modal
        title="容器日志"
        open={logModalVisible}
        onCancel={() => setLogModalVisible(false)}
        footer={null}
        width={800}
        destroyOnClose
      >
        <div style={{
          background: '#1e1e1e', color: '#d4d4d4', padding: 16,
          borderRadius: 6, maxHeight: 500, overflow: 'auto',
          fontFamily: 'monospace', fontSize: 12, lineHeight: 1.6,
          whiteSpace: 'pre-wrap',
        }}>
          {containerLogs.length === 0 ? (
            <Text style={{ color: '#888' }}>暂无日志</Text>
          ) : (
            containerLogs.map((log, idx) => (
              <div key={idx} style={{ color: log.stream === 'stderr' ? '#f48771' : '#d4d4d4' }}>
                <Text style={{ color: '#6a9955' }}>{log.timestamp}</Text> {log.message}
              </div>
            ))
          )}
        </div>
      </Modal>
    </div>
  )
}