// ============================================================
// 审计日志页面
// ============================================================
import { useEffect, useState } from 'react'
import {
  Table, Button, Space, Tag, Input, Select, DatePicker, Empty, message, Descriptions, Drawer,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  AuditOutlined, SearchOutlined, ReloadOutlined, ExportOutlined,
  CheckCircleOutlined, CloseCircleOutlined, EyeOutlined,
} from '@ant-design/icons'
import { useAuditStore } from '@/stores/auditStore'
import { exportAuditLogsApi } from '@/api/audit'
import PageHeader from '@/components/PageHeader'
import type { AuditLog } from '@/types/api'

const { Option } = Select
const { RangePicker } = DatePicker

export default function AuditLogsPage() {
  const {
    logs, total, page, pageSize, loading, action, status,
    fetchLogs, setPage, setPageSize, setAction, setStatus,
    setDateRange,
  } = useAuditStore()

  const [userIdInput, setUserIdInput] = useState('')
  const [exporting, setExporting] = useState(false)
  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLog, setDetailLog] = useState<AuditLog | null>(null)

  // 用 ref 来追踪 userId 输入
  const { setUserId } = useAuditStore()

  useEffect(() => {
    fetchLogs()
  }, [fetchLogs])

  const handleSearch = () => {
    setUserId(userIdInput || null)
  }

  const handleDateChange = (_: unknown, dateStrings: [string, string]) => {
    setDateRange(dateStrings[0] || null, dateStrings[1] || null)
  }

  const handleExport = async () => {
    setExporting(true)
    try {
      const res = await exportAuditLogsApi({ page: 1, pageSize: 999999 })
      const blob = res.data as unknown as Blob
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `audit_logs_${new Date().toISOString().slice(0, 10)}.xlsx`
      a.click()
      window.URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch {
      message.error('导出失败')
    } finally {
      setExporting(false)
    }
  }

  const showDetail = (log: AuditLog) => {
    setDetailLog(log)
    setDetailOpen(true)
  }

  const columns: ColumnsType<AuditLog> = [
    { title: '用户', dataIndex: 'userName', key: 'userName', width: 100, ellipsis: true },
    { title: '操作', dataIndex: 'action', key: 'action', width: 120 },
    { title: '资源', dataIndex: 'resource', key: 'resource', width: 120 },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (s: string) => (
        <Tag color={s === 'SUCCESS' ? 'green' : 'red'}
          icon={s === 'SUCCESS' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}>
          {s === 'SUCCESS' ? '成功' : '失败'}
        </Tag>
      ),
    },
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130 },
    {
      title: '时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 170,
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 60,
      render: (_: unknown, record: AuditLog) => (
        <Button type="text" size="small" icon={<EyeOutlined />} onClick={() => showDetail(record)} />
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="审计日志"
        subtitle={`共 ${total} 条记录`}
        icon={<AuditOutlined />}
        actions={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchLogs}>刷新</Button>
            <Button icon={<ExportOutlined />} onClick={handleExport} loading={exporting}>导出</Button>
          </Space>
        }
      />

      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder="搜索用户 ID"
          prefix={<SearchOutlined />}
          value={userIdInput}
          onChange={(e) => setUserIdInput(e.target.value)}
          onPressEnter={handleSearch}
          style={{ width: 200 }}
          allowClear
          onClear={() => { setUserIdInput(''); setUserId(null); }}
        />
        <Button type="primary" onClick={handleSearch}>搜索</Button>
        <Select
          placeholder="操作类型"
          value={action}
          onChange={(v) => setAction(v)}
          allowClear
          style={{ width: 140 }}
        >
          <Option value="LOGIN">登录</Option>
          <Option value="LOGOUT">登出</Option>
          <Option value="UPLOAD">上传</Option>
          <Option value="DOWNLOAD">下载</Option>
          <Option value="DELETE">删除</Option>
          <Option value="CREATE">创建</Option>
          <Option value="UPDATE">更新</Option>
        </Select>
        <Select
          placeholder="状态"
          value={status}
          onChange={(v) => setStatus(v)}
          allowClear
          style={{ width: 100 }}
        >
          <Option value="SUCCESS">成功</Option>
          <Option value="FAILURE">失败</Option>
        </Select>
        <RangePicker onChange={handleDateChange} />
      </Space>

      <Table
        columns={columns}
        dataSource={logs}
        rowKey="id"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          pageSizeOptions: ['10', '20', '50', '100'],
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
        scroll={{ x: 1100 }}
        locale={{ emptyText: <Empty description="暂无审计日志" /> }}
      />

      <Drawer
        title="日志详情"
        open={detailOpen}
        onClose={() => setDetailOpen(false)}
        width={480}
      >
        {detailLog && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="用户">{detailLog.userName}</Descriptions.Item>
            <Descriptions.Item label="操作">{detailLog.action}</Descriptions.Item>
            <Descriptions.Item label="资源">{detailLog.resource}</Descriptions.Item>
            <Descriptions.Item label="资源 ID">{detailLog.resourceId}</Descriptions.Item>
            <Descriptions.Item label="详情">{detailLog.detail || '-'}</Descriptions.Item>
            <Descriptions.Item label="IP 地址">{detailLog.ip}</Descriptions.Item>
            <Descriptions.Item label="User Agent">{detailLog.userAgent}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={detailLog.status === 'SUCCESS' ? 'green' : 'red'}>
                {detailLog.status === 'SUCCESS' ? '成功' : '失败'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="时间">
              {new Date(detailLog.createdAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}