// ============================================================
// 安全事件管理页面
// ============================================================
import { useEffect, useState } from 'react'
import { Table, Button, Space, Tag, Select, Modal, Input, Empty, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  SafetyOutlined, ReloadOutlined, CheckCircleOutlined,
  ExclamationCircleOutlined, WarningOutlined,
} from '@ant-design/icons'
import { useSecurityStore } from '@/stores/securityStore'
import PageHeader from '@/components/PageHeader'
import type { SecurityEvent } from '@/types/api'

const { Option } = Select
const { TextArea } = Input

const severityConfig: Record<string, { color: string; icon: React.ReactNode }> = {
  LOW: { color: 'blue', icon: <SafetyOutlined /> },
  MEDIUM: { color: 'orange', icon: <WarningOutlined /> },
  HIGH: { color: 'red', icon: <ExclamationCircleOutlined /> },
  CRITICAL: { color: '#ff4d4f', icon: <ExclamationCircleOutlined /> },
}

const typeLabels: Record<string, string> = {
  LOGIN_FAILURE: '登录失败',
  BRUTE_FORCE: '暴力破解',
  SUSPICIOUS_IP: '可疑IP',
  UNAUTHORIZED_ACCESS: '未授权访问',
  VIRUS_DETECTED: '病毒检测',
  CONFIG_CHANGE: '配置变更',
}

export default function SecurityEventsPage() {
  const {
    events, total, page, pageSize, loading, severity, type,
    fetchEvents, handleEvent, setPage, setPageSize, setSeverity, setType,
  } = useSecurityStore()

  const [resolutionModal, setResolutionModal] = useState(false)
  const [currentEventId, setCurrentEventId] = useState<string | null>(null)
  const [resolutionText, setResolutionText] = useState('')

  useEffect(() => {
    fetchEvents()
  }, [fetchEvents])

  const handleOpenResolution = (eventId: string) => {
    setCurrentEventId(eventId)
    setResolutionText('')
    setResolutionModal(true)
  }

  const handleSubmitResolution = async () => {
    if (!currentEventId || !resolutionText.trim()) return
    const ok = await handleEvent(currentEventId, resolutionText)
    if (ok) {
      message.success('已处理该事件')
      setResolutionModal(false)
    } else {
      message.error('处理失败')
    }
  }

  const columns: ColumnsType<SecurityEvent> = [
    {
      title: '严重级别',
      dataIndex: 'severity',
      key: 'severity',
      width: 100,
      render: (s: string) => {
        const cfg = severityConfig[s] || severityConfig.LOW
        return <Tag color={cfg.color} icon={cfg.icon}>{s}</Tag>
      },
    },
    {
      title: '事件类型',
      dataIndex: 'type',
      key: 'type',
      width: 120,
      render: (t: string) => typeLabels[t] || t,
    },
    { title: '用户', dataIndex: 'userName', key: 'userName', width: 100, ellipsis: true },
    { title: 'IP 地址', dataIndex: 'ip', key: 'ip', width: 140 },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'handled',
      key: 'handled',
      width: 80,
      render: (handled: boolean) =>
        handled ? <Tag color="green" icon={<CheckCircleOutlined />}>已处理</Tag>
          : <Tag color="red">未处理</Tag>,
    },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: SecurityEvent) =>
        !record.handled && (
          <Button type="primary" size="small" onClick={() => handleOpenResolution(record.id)}>
            处理
          </Button>
        ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="安全事件"
        subtitle={`共 ${total} 个事件`}
        icon={<SafetyOutlined />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={fetchEvents}>刷新</Button>
        }
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Select
          placeholder="严重级别"
          value={severity}
          onChange={(v) => setSeverity(v)}
          allowClear
          style={{ width: 120 }}
        >
          <Option value="LOW">低</Option>
          <Option value="MEDIUM">中</Option>
          <Option value="HIGH">高</Option>
          <Option value="CRITICAL">严重</Option>
        </Select>
        <Select
          placeholder="事件类型"
          value={type}
          onChange={(v) => setType(v)}
          allowClear
          style={{ width: 140 }}
        >
          {Object.entries(typeLabels).map(([k, v]) => (
            <Option key={k} value={k}>{v}</Option>
          ))}
        </Select>
      </Space>

      <Table
        columns={columns}
        dataSource={events}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
        locale={{ emptyText: <Empty description="暂无安全事件" /> }}
      />

      <Modal
        title="处理安全事件"
        open={resolutionModal}
        onOk={handleSubmitResolution}
        onCancel={() => setResolutionModal(false)}
        okText="提交"
        cancelText="取消"
      >
        <TextArea
          rows={4}
          placeholder="请输入处理方式或备注..."
          value={resolutionText}
          onChange={(e) => setResolutionText(e.target.value)}
        />
      </Modal>
    </div>
  )
}