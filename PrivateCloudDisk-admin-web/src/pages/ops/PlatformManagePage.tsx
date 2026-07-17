// ============================================================
// 平台管理页面
// 系统配置、用户权限、审计日志、系统健康检查
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, Switch, InputNumber,
  Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Badge, TreeSelect,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  SettingOutlined, SafetyCertificateOutlined, AuditOutlined, HeartOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  UserOutlined, TeamOutlined, KeyOutlined, FileTextOutlined, DatabaseOutlined,
  SaveOutlined, RollbackOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { TextArea } = Input

// 模拟的系统配置数据
interface SystemConfig {
  key: string
  value: string
  type: 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'
  description: string
  group: string
  editable: boolean
  updatedAt: string
}

interface AuditLog {
  id: string
  userId: string
  username: string
  action: string
  resource: string
  resourceId: string
  detail: string
  ip: string
  userAgent: string
  status: 'SUCCESS' | 'FAILURE'
  createdAt: string
}

interface SystemHealth {
  component: string
  status: 'UP' | 'DOWN' | 'DEGRADED'
  details: string
  lastChecked: string
}

const mockConfigs: SystemConfig[] = [
  { key: 'system.name', value: 'PrivateCloudDisk Admin', type: 'STRING', description: '系统名称', group: '基础设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'system.version', value: '2.0.0', type: 'STRING', description: '系统版本', group: '基础设置', editable: false, updatedAt: '2026-07-01 10:00:00' },
  { key: 'system.logo', value: '/logo.png', type: 'STRING', description: '系统 Logo', group: '基础设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'security.session.timeout', value: '30', type: 'NUMBER', description: '会话超时(分钟)', group: '安全设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'security.password.minLength', value: '8', type: 'NUMBER', description: '密码最小长度', group: '安全设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'security.mfa.enabled', value: 'false', type: 'BOOLEAN', description: '多因素认证', group: '安全设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'upload.maxSize', value: '104857600', type: 'NUMBER', description: '上传大小限制(字节)', group: '存储设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'upload.allowedTypes', value: '["jpg","png","pdf","doc","docx","xlsx"]', type: 'JSON', description: '允许的文件类型', group: '存储设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'notification.email.enabled', value: 'true', type: 'BOOLEAN', description: '邮件通知', group: '通知设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
  { key: 'notification.sms.enabled', value: 'false', type: 'BOOLEAN', description: '短信通知', group: '通知设置', editable: true, updatedAt: '2026-07-01 10:00:00' },
]

const mockAuditLogs: AuditLog[] = [
  { id: '1', userId: '1', username: 'admin', action: 'LOGIN', resource: 'AUTH', resourceId: '-', detail: '用户登录', ip: '192.168.1.100', userAgent: 'Chrome/120.0', status: 'SUCCESS', createdAt: '2026-07-07 09:00:00' },
  { id: '2', userId: '1', username: 'admin', action: 'CREATE', resource: 'USER', resourceId: '10', detail: '创建用户', ip: '192.168.1.100', userAgent: 'Chrome/120.0', status: 'SUCCESS', createdAt: '2026-07-07 09:15:00' },
  { id: '3', userId: '2', username: 'operator', action: 'UPDATE', resource: 'FILE', resourceId: 'file-123', detail: '修改文件权限', ip: '192.168.1.101', userAgent: 'Firefox/121.0', status: 'SUCCESS', createdAt: '2026-07-07 09:30:00' },
  { id: '4', userId: '3', username: 'viewer', action: 'DELETE', resource: 'FILE', resourceId: 'file-456', detail: '删除文件', ip: '192.168.1.102', userAgent: 'Edge/120.0', status: 'FAILURE', createdAt: '2026-07-07 09:45:00' },
  { id: '5', userId: '1', username: 'admin', action: 'CONFIG_UPDATE', resource: 'SYSTEM', resourceId: '-', detail: '修改系统配置: security.session.timeout', ip: '192.168.1.100', userAgent: 'Chrome/120.0', status: 'SUCCESS', createdAt: '2026-07-07 10:00:00' },
]

const mockHealth: SystemHealth[] = [
  { component: '数据库', status: 'UP', details: 'PostgreSQL 15 - 连接池正常', lastChecked: '2026-07-07 10:00:00' },
  { component: 'Redis', status: 'UP', details: 'Redis 7.0 - 内存使用 45%', lastChecked: '2026-07-07 10:00:00' },
  { component: '文件存储', status: 'UP', details: 'MinIO - 所有节点正常', lastChecked: '2026-07-07 10:00:00' },
  { component: '消息队列', status: 'DEGRADED', details: 'RabbitMQ - 队列积压 1200 条', lastChecked: '2026-07-07 10:00:00' },
  { component: '搜索引擎', status: 'UP', details: 'OpenSearch 2.11 - 集群绿', lastChecked: '2026-07-07 10:00:00' },
  { component: '任务调度', status: 'UP', details: 'XXL-Job - 所有执行器正常', lastChecked: '2026-07-07 10:00:00' },
  { component: '服务注册', status: 'UP', details: 'Nacos 2.2 - 所有服务正常', lastChecked: '2026-07-07 10:00:00' },
  { component: '监控系统', status: 'UP', details: 'Prometheus + Grafana - 正常', lastChecked: '2026-07-07 10:00:00' },
]

export default function PlatformManagePage() {
  const [activeTab, setActiveTab] = useState('config')
  const [configs, setConfigs] = useState<SystemConfig[]>(mockConfigs)
  const [auditLogs] = useState<AuditLog[]>(mockAuditLogs)
  const [healthStatus] = useState<SystemHealth[]>(mockHealth)
  const [editingConfig, setEditingConfig] = useState<SystemConfig | null>(null)
  const [configModalVisible, setConfigModalVisible] = useState(false)
  const [configForm] = Form.useForm()
  const [searchConfig, setSearchConfig] = useState('')
  const [auditSearchUser, setAuditSearchUser] = useState('')

  const filteredConfigs = useMemo(() => {
    if (!searchConfig) return configs
    return configs.filter((c) =>
      c.key.toLowerCase().includes(searchConfig.toLowerCase()) ||
      c.description.toLowerCase().includes(searchConfig.toLowerCase())
    )
  }, [configs, searchConfig])

  const filteredAuditLogs = useMemo(() => {
    if (!auditSearchUser) return auditLogs
    return auditLogs.filter((l) => l.username.toLowerCase().includes(auditSearchUser.toLowerCase()))
  }, [auditLogs, auditSearchUser])

  const handleEditConfig = (config: SystemConfig) => {
    setEditingConfig(config)
    configForm.setFieldsValue({ value: config.value })
    setConfigModalVisible(true)
  }

  const handleSaveConfig = async () => {
    try {
      const values = await configForm.validateFields()
      if (editingConfig) {
        setConfigs((prev) => prev.map((c) =>
          c.key === editingConfig.key ? { ...c, value: values.value, updatedAt: new Date().toLocaleString() } : c
        ))
        message.success('配置已更新')
      }
      setConfigModalVisible(false)
    } catch {}
  }

  const configColumns: ColumnsType<SystemConfig> = [
    { title: '配置键', dataIndex: 'key', key: 'key', width: 220, render: (v: string) => <Text code>{v}</Text> },
    { title: '值', dataIndex: 'value', key: 'value', width: 200, ellipsis: true, render: (v: string, r: SystemConfig) => {
      if (r.type === 'BOOLEAN') return <Badge status={v === 'true' ? 'success' : 'default'} text={v === 'true' ? '启用' : '禁用'} />
      if (r.type === 'JSON') return <Text code style={{ fontSize: 11 }}>{v.substring(0, 50)}...</Text>
      return <Text>{v}</Text>
    }},
    { title: '类型', dataIndex: 'type', key: 'type', width: 90, render: (t: string) => <Tag color="blue">{t}</Tag> },
    { title: '描述', dataIndex: 'description', key: 'description', width: 180 },
    { title: '分组', dataIndex: 'group', key: 'group', width: 120, render: (g: string) => <Tag>{g}</Tag> },
    { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 160 },
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: SystemConfig) => r.editable ? (
        <Button size="small" icon={<EditOutlined />} onClick={() => handleEditConfig(r)}>编辑</Button>
      ) : <Text type="secondary">只读</Text>,
    },
  ]

  const auditLogColumns: ColumnsType<AuditLog> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 60 },
    { title: '用户', dataIndex: 'username', key: 'username', width: 100 },
    { title: '操作', dataIndex: 'action', key: 'action', width: 120, render: (a: string) => {
      const colorMap: Record<string, string> = {
        LOGIN: 'blue', CREATE: 'green', UPDATE: 'orange', DELETE: 'red', CONFIG_UPDATE: 'purple',
      }
      return <Tag color={colorMap[a] || 'default'}>{a}</Tag>
    }},
    { title: '资源', dataIndex: 'resource', key: 'resource', width: 100 },
    { title: '详情', dataIndex: 'detail', key: 'detail', width: 200, ellipsis: true },
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130, render: (v: string) => <Text code>{v}</Text> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (s: string) => <Tag color={s === 'SUCCESS' ? 'green' : 'red'}>{s}</Tag>,
    },
    { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 160 },
  ]

  const healthColumns: ColumnsType<SystemHealth> = [
    { title: '组件', dataIndex: 'component', key: 'component', width: 150 },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => {
        const colorMap: Record<string, string> = { UP: 'green', DOWN: 'red', DEGRADED: 'orange' }
        const iconMap: Record<string, React.ReactNode> = { UP: <CheckCircleOutlined />, DOWN: <CloseCircleOutlined />, DEGRADED: <WarningOutlined /> }
        return <Tag color={colorMap[s] || 'default'} icon={iconMap[s]}>{s}</Tag>
      },
    },
    { title: '详情', dataIndex: 'details', key: 'details', width: 300, ellipsis: true },
    { title: '最后检查', dataIndex: 'lastChecked', key: 'lastChecked', width: 160 },
  ]

  const healthSummary = useMemo(() => ({
    up: healthStatus.filter((h) => h.status === 'UP').length,
    degraded: healthStatus.filter((h) => h.status === 'DEGRADED').length,
    down: healthStatus.filter((h) => h.status === 'DOWN').length,
  }), [healthStatus])

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="配置项" value={configs.length} prefix={<SettingOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="审计日志" value={auditLogs.length} prefix={<AuditOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="健康组件" value={healthSummary.up} suffix={`/ ${healthStatus.length}`} valueStyle={{ color: '#3f8600' }} prefix={<HeartOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="异常组件" value={healthSummary.degraded + healthSummary.down} valueStyle={{ color: healthSummary.degraded + healthSummary.down > 0 ? '#cf1322' : '#3f8600' }} prefix={<WarningOutlined />} /></Card>
        </Col>
      </Row>

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'config',
          label: '系统配置',
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索配置" prefix={<Button type="text" icon={<SearchOutlined />} />} allowClear style={{ width: 250 }}
                    value={searchConfig} onChange={(e) => setSearchConfig(e.target.value)} />
                </Space>
              }
            >
              {filteredConfigs.length === 0 ? (
                <Empty description="暂无配置" />
              ) : (
                <Table dataSource={filteredConfigs} columns={configColumns} rowKey="key" pagination={false} size="middle" />
              )}
            </Card>
          ),
        },
        {
          key: 'audit',
          label: '审计日志',
          children: (
            <Card
              extra={
                <Space>
                  <Input placeholder="搜索用户" allowClear style={{ width: 200 }}
                    value={auditSearchUser} onChange={(e) => setAuditSearchUser(e.target.value)} />
                </Space>
              }
            >
              {filteredAuditLogs.length === 0 ? (
                <Empty description="暂无审计日志" />
              ) : (
                <Table dataSource={filteredAuditLogs} columns={auditLogColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1000 }} />
              )}
            </Card>
          ),
        },
        {
          key: 'health',
          label: '健康检查',
          children: (
            <Card>
              {healthStatus.length === 0 ? (
                <Empty description="暂无健康检查数据" />
              ) : (
                <Table dataSource={healthStatus} columns={healthColumns} rowKey="component" pagination={false} size="middle" />
              )}
            </Card>
          ),
        },
      ]} />

      {/* 配置编辑弹窗 */}
      <Modal title={`编辑配置: ${editingConfig?.key}`} open={configModalVisible} onOk={handleSaveConfig} onCancel={() => setConfigModalVisible(false)} destroyOnClose>
        {editingConfig && (
          <Form form={configForm} layout="vertical">
            <Descriptions bordered size="small" column={1} style={{ marginBottom: 16 }}>
              <Descriptions.Item label="配置键">{editingConfig.key}</Descriptions.Item>
              <Descriptions.Item label="描述">{editingConfig.description}</Descriptions.Item>
              <Descriptions.Item label="类型"><Tag color="blue">{editingConfig.type}</Tag></Descriptions.Item>
            </Descriptions>
            <Form.Item name="value" label="值" rules={[{ required: true }]}>
              {editingConfig.type === 'BOOLEAN' ? (
                <Select options={[{ label: '启用', value: 'true' }, { label: '禁用', value: 'false' }]} />
              ) : editingConfig.type === 'JSON' ? (
                <TextArea rows={5} />
              ) : editingConfig.type === 'NUMBER' ? (
                <Input type="number" />
              ) : (
                <Input />
              )}
            </Form.Item>
          </Form>
        )}
      </Modal>
    </div>
  )
}