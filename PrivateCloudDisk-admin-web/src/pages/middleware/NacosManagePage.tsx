// ============================================================
// Nacos 管理页面
// 服务管理、配置管理、命名空间、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form,
  Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Switch, Tooltip,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  CloudServerOutlined, SettingOutlined, LinkOutlined, SearchOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  AppstoreOutlined, FileTextOutlined,
} from '@ant-design/icons'
import { useMiddlewareStore } from '@/stores/middlewareStore'
import type { NacosService, NacosInstance, NacosConfig } from '@/api/middleware'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { TextArea } = Input

const healthStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  HEALTHY: { color: 'green', icon: <CheckCircleOutlined /> },
  UNHEALTHY: { color: 'red', icon: <CloseCircleOutlined /> },
  UNKNOWN: { color: 'default', icon: <WarningOutlined /> },
}

export default function NacosManagePage() {
  const {
    nacosServices, nacosServicesTotal, nacosCurrentService,
    nacosInstances, nacosConfigs, nacosConfigsTotal, nacosCurrentConfig,
    nacosConsoleUrl, loading, error, namespaces,
    fetchNacosServices, fetchNacosServiceDetail, fetchNacosInstances,
    fetchNacosConfigs, fetchNacosConfigDetail,
    doCreateNacosConfig, doUpdateNacosConfig, doDeleteNacosConfig, doPublishNacosConfig,
    fetchNacosConsoleUrl, fetchNacosNamespaces,
  } = useMiddlewareStore()

  const [activeTab, setActiveTab] = useState('services')
  const [searchService, setSearchService] = useState('')
  const [configModalVisible, setConfigModalVisible] = useState(false)
  const [configDetailVisible, setConfigDetailVisible] = useState(false)
  const [configForm] = Form.useForm()
  const [editingConfigId, setEditingConfigId] = useState<string | null>(null)

  const loadServices = useCallback(() => {
    fetchNacosServices({ page: 1, pageSize: 50, serviceName: searchService || undefined })
  }, [fetchNacosServices, searchService])

  const loadConfigs = useCallback(() => {
    fetchNacosConfigs({ page: 1, pageSize: 50 })
  }, [fetchNacosConfigs])

  useEffect(() => { loadServices(); fetchNacosNamespaces() }, [loadServices, fetchNacosNamespaces])
  useEffect(() => {
    if (activeTab === 'configs') loadConfigs()
  }, [activeTab, loadConfigs])

  const handleViewService = async (serviceId: string) => {
    await fetchNacosServiceDetail(serviceId)
    await fetchNacosInstances(serviceId)
  }

  const handleViewConfig = async (configId: string) => {
    await fetchNacosConfigDetail(configId)
    setConfigDetailVisible(true)
  }

  const handleCreateConfig = () => {
    setEditingConfigId(null)
    configForm.resetFields()
    setConfigModalVisible(true)
  }

  const handleEditConfig = (config: NacosConfig) => {
    setEditingConfigId(config.configId)
    configForm.setFieldsValue({
      dataId: config.dataId,
      group: config.group,
      namespaceId: config.namespaceId,
      content: config.content,
      type: config.type,
      description: config.description,
    })
    setConfigModalVisible(true)
  }

  const handleSaveConfig = async () => {
    try {
      const values = await configForm.validateFields()
      let success: boolean
      if (editingConfigId) {
        success = await doUpdateNacosConfig(editingConfigId, values)
      } else {
        success = await doCreateNacosConfig(values)
      }
      if (success) {
        message.success(editingConfigId ? '配置已更新' : '配置已创建')
        setConfigModalVisible(false)
        loadConfigs()
      }
    } catch {}
  }

  const handleDeleteConfig = async (configId: string) => {
    await doDeleteNacosConfig(configId)
    loadConfigs()
  }

  const handlePublishConfig = async (configId: string) => {
    const success = await doPublishNacosConfig(configId)
    if (success) message.success('配置已发布')
    else message.error('发布失败')
  }

  const handleOpenConsole = async () => {
    await fetchNacosConsoleUrl()
    if (nacosConsoleUrl) window.open(nacosConsoleUrl, '_blank')
  }

  const serviceColumns: ColumnsType<NacosService> = [
    {
      title: '服务名', dataIndex: 'serviceName', key: 'serviceName', width: 200,
      render: (n: string, r: NacosService) => <a onClick={() => handleViewService(r.serviceId)}>{n}</a>,
    },
    { title: '分组', dataIndex: 'groupName', key: 'groupName', width: 120, render: (g: string) => <Tag>{g}</Tag> },
    { title: '集群数', dataIndex: 'clusterCount', key: 'clusterCount', width: 80 },
    { title: '实例数', dataIndex: 'instanceCount', key: 'instanceCount', width: 80 },
    { title: '健康实例', dataIndex: 'healthyInstanceCount', key: 'healthyInstanceCount', width: 90,
      render: (v: number, r: NacosService) => (
        <Text style={{ color: v < r.instanceCount ? '#d48806' : '#3f8600' }}>{v}</Text>
      ),
    },
    { title: '保护阈值', dataIndex: 'protectThreshold', key: 'protectThreshold', width: 90 },
    { title: '选择器', dataIndex: 'selector', key: 'selector', width: 120, ellipsis: true },
    { title: '元数据', dataIndex: 'metadata', key: 'metadata', width: 150,
      render: (m: Record<string, string>) => Object.entries(m || {}).map(([k, v]) => (
        <Tag key={k} style={{ marginBottom: 2 }}>{k}: {v}</Tag>
      )),
    },
  ]

  const instanceColumns: ColumnsType<NacosInstance> = [
    { title: 'IP', dataIndex: 'ip', key: 'ip', width: 130, render: (ip: string) => <Text code>{ip}</Text> },
    { title: '端口', dataIndex: 'port', key: 'port', width: 70 },
    { title: '权重', dataIndex: 'weight', key: 'weight', width: 70 },
    {
      title: '健康', dataIndex: 'healthy', key: 'healthy', width: 80,
      render: (v: boolean) => <Tag color={v ? 'green' : 'red'}>{v ? '健康' : '不健康'}</Tag>,
    },
    { title: '启用', dataIndex: 'enabled', key: 'enabled', width: 70, render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} /> },
    { title: '临时', dataIndex: 'ephemeral', key: 'ephemeral', width: 70, render: (v: boolean) => v ? '是' : '否' },
    { title: '集群', dataIndex: 'clusterName', key: 'clusterName', width: 100 },
    { title: '服务名', dataIndex: 'serviceName', key: 'serviceName', width: 150 },
    { title: '元数据', dataIndex: 'metadata', key: 'metadata', width: 150, render: (m: Record<string, string>) => (
      Object.entries(m || {}).map(([k, v]) => <Tag key={k} style={{ marginBottom: 2 }}>{k}: {v}</Tag>)
    )},
  ]

  const configColumns: ColumnsType<NacosConfig> = [
    { title: 'Data ID', dataIndex: 'dataId', key: 'dataId', width: 200, render: (v: string, r: NacosConfig) => <a onClick={() => handleViewConfig(r.configId)}>{v}</a> },
    { title: '分组', dataIndex: 'group', key: 'group', width: 120, render: (g: string) => <Tag>{g}</Tag> },
    { title: '类型', dataIndex: 'type', key: 'type', width: 100, render: (t: string) => <Tag color="blue">{t}</Tag> },
    { title: '命名空间', dataIndex: 'namespaceId', key: 'namespaceId', width: 120, render: (v: string) => v || 'public' },
    { title: '描述', dataIndex: 'description', key: 'description', width: 200, ellipsis: true },
    { title: '更新时间', dataIndex: 'updateTime', key: 'updateTime', width: 160 },
    {
      title: '操作', key: 'actions', width: 200,
      render: (_: unknown, r: NacosConfig) => (
        <Space size="small">
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEditConfig(r)}>编辑</Button>
          <Button size="small" onClick={() => handlePublishConfig(r.configId)}>发布</Button>
          <Popconfirm title="确定删除？" onConfirm={() => handleDeleteConfig(r.configId)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="服务数" value={nacosServicesTotal} prefix={<AppstoreOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="配置数" value={nacosConfigsTotal} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="命名空间" value={namespaces.length} prefix={<CloudServerOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 Nacos 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'services',
          label: `服务管理 (${nacosServicesTotal})`,
          children: (
            <>
              <Card
                style={{ marginBottom: 16 }}
                extra={
                  <Space>
                    <Input placeholder="搜索服务名" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
                      value={searchService} onChange={(e) => setSearchService(e.target.value)} onPressEnter={loadServices} />
                    <Button icon={<ReloadOutlined />} onClick={loadServices}>刷新</Button>
                  </Space>
                }
              >
                <Spin spinning={loading}>
                  {nacosServices.length === 0 && !loading ? (
                    <Empty description="暂无服务" />
                  ) : (
                    <Table dataSource={nacosServices} columns={serviceColumns} rowKey="serviceId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1000 }} />
                  )}
                </Spin>
              </Card>

              {/* 服务实例列表 */}
              {nacosCurrentService && (
                <Card title={`${nacosCurrentService.serviceName} - 实例列表`} extra={<Button size="small" onClick={() => useMiddlewareStore.getState().reset()}>关闭</Button>}>
                  <Table dataSource={nacosInstances} columns={instanceColumns} rowKey="instanceId" pagination={false} size="middle" scroll={{ x: 1000 }} />
                </Card>
              )}
            </>
          ),
        },
        {
          key: 'configs',
          label: `配置管理 (${nacosConfigsTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<PlusOutlined />} type="primary" onClick={handleCreateConfig}>创建配置</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadConfigs}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {nacosConfigs.length === 0 && !loading ? (
                  <Empty description="暂无配置" />
                ) : (
                  <Table dataSource={nacosConfigs} columns={configColumns} rowKey="configId" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 配置创建/编辑弹窗 */}
      <Modal title={editingConfigId ? '编辑配置' : '创建配置'} open={configModalVisible} onOk={handleSaveConfig} onCancel={() => setConfigModalVisible(false)} width={600} destroyOnClose>
        <Form form={configForm} layout="vertical">
          <Form.Item name="dataId" label="Data ID" rules={[{ required: true, message: '请输入 Data ID' }]}>
            <Input placeholder="application.properties" />
          </Form.Item>
          <Form.Item name="group" label="Group" initialValue="DEFAULT_GROUP">
            <Input placeholder="DEFAULT_GROUP" />
          </Form.Item>
          <Form.Item name="namespaceId" label="命名空间">
            <Select placeholder="public" allowClear options={namespaces.map((ns) => ({ label: ns.namespaceShowName || ns.namespaceId, value: ns.namespaceId }))} />
          </Form.Item>
          <Form.Item name="type" label="配置类型" initialValue="properties">
            <Select options={[
              { label: 'properties', value: 'properties' },
              { label: 'yaml', value: 'yaml' },
              { label: 'json', value: 'json' },
              { label: 'xml', value: 'xml' },
              { label: 'text', value: 'text' },
            ]} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input placeholder="配置描述" />
          </Form.Item>
          <Form.Item name="content" label="配置内容" rules={[{ required: true, message: '请输入配置内容' }]}>
            <TextArea rows={10} placeholder="输入配置内容..." />
          </Form.Item>
        </Form>
      </Modal>

      {/* 配置详情弹窗 */}
      <Modal title="配置详情" open={configDetailVisible} onCancel={() => setConfigDetailVisible(false)} footer={null} width={700} destroyOnClose>
        {nacosCurrentConfig && (
          <Descriptions bordered size="small" column={1}>
            <Descriptions.Item label="Data ID">{nacosCurrentConfig.dataId}</Descriptions.Item>
            <Descriptions.Item label="Group">{nacosCurrentConfig.group}</Descriptions.Item>
            <Descriptions.Item label="类型"><Tag color="blue">{nacosCurrentConfig.type}</Tag></Descriptions.Item>
            <Descriptions.Item label="命名空间">{nacosCurrentConfig.namespaceId || 'public'}</Descriptions.Item>
            <Descriptions.Item label="描述">{nacosCurrentConfig.description || '-'}</Descriptions.Item>
            <Descriptions.Item label="内容">
              <pre style={{ maxHeight: 400, overflow: 'auto', background: '#f5f5f5', padding: 12, borderRadius: 4, fontSize: 12 }}>
                {nacosCurrentConfig.content}
              </pre>
            </Descriptions.Item>
          </Descriptions>
        )}
      </Modal>
    </div>
  )
}