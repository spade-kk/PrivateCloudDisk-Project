// ============================================================
// OpenSearch 管理页面
// 索引管理、搜索分析、集群监控、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, InputNumber,
  Progress, Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Badge, Switch,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  LinkOutlined, SearchOutlined, CopyOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  DatabaseOutlined, FileTextOutlined, NodeIndexOutlined, ThunderboltOutlined,
  SettingOutlined, BarChartOutlined,
} from '@ant-design/icons'
import { useMiddlewareStore } from '@/stores/middlewareStore'
import type { OpenSearchIndex, OpenSearchDocument, OpenSearchCluster } from '@/api/middleware'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { TextArea } = Input

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

const healthStatusMap: Record<string, { color: string; icon: React.ReactNode }> = {
  GREEN: { color: 'green', icon: <CheckCircleOutlined /> },
  YELLOW: { color: 'orange', icon: <WarningOutlined /> },
  RED: { color: 'red', icon: <CloseCircleOutlined /> },
}

const indexStatusMap: Record<string, { color: string }> = {
  OPEN: { color: 'green' },
  CLOSED: { color: 'default' },
  READONLY: { color: 'orange' },
}

export default function OpenSearchManagePage() {
  const {
    opensearchIndices, opensearchIndicesTotal, opensearchDocuments,
    opensearchDocumentsTotal, opensearchCluster,
    opensearchConsoleUrl, loading, error,
    fetchOpenSearchIndices, fetchOpenSearchDocuments, fetchOpenSearchCluster,
    doCreateOpenSearchIndex, doDeleteOpenSearchIndex, doCloseOpenSearchIndex,
    doOpenOpenSearchIndex, doIndexOpenSearchDocument, doSearchOpenSearch,
    doDeleteOpenSearchDocument, fetchOpenSearchConsoleUrl,
  } = useMiddlewareStore()

  const [activeTab, setActiveTab] = useState('indices')
  const [selectedIndex, setSelectedIndex] = useState<string | null>(null)
  const [createIndexModalVisible, setCreateIndexModalVisible] = useState(false)
  const [searchModalVisible, setSearchModalVisible] = useState(false)
  const [searchResults, setSearchResults] = useState<OpenSearchDocument[]>([])
  const [searchTotal, setSearchTotal] = useState(0)
  const [indexForm] = Form.useForm()
  const [searchForm] = Form.useForm()
  const [searchIndexName, setSearchIndexName] = useState('')

  const loadIndices = useCallback(() => { fetchOpenSearchIndices({ page: 1, pageSize: 50 }) }, [fetchOpenSearchIndices])
  const loadDocuments = useCallback(() => {
    if (selectedIndex) fetchOpenSearchDocuments(selectedIndex, { page: 1, pageSize: 50 })
  }, [fetchOpenSearchDocuments, selectedIndex])
  const loadCluster = useCallback(() => { fetchOpenSearchCluster() }, [fetchOpenSearchCluster])

  useEffect(() => { loadIndices(); loadCluster() }, [loadIndices, loadCluster])
  useEffect(() => {
    if (activeTab === 'documents' && selectedIndex) loadDocuments()
  }, [activeTab, loadDocuments, selectedIndex])

  const handleCreateIndex = async () => {
    try {
      const values = await indexForm.validateFields()
      const success = await doCreateOpenSearchIndex(values)
      if (success) { message.success('索引创建成功'); setCreateIndexModalVisible(false); indexForm.resetFields(); loadIndices() }
      else message.error('创建失败')
    } catch {}
  }

  const handleDeleteIndex = async (indexName: string) => {
    const success = await doDeleteOpenSearchIndex(indexName)
    if (success) { message.success('索引已删除'); loadIndices() }
    else message.error('删除失败')
  }

  const handleCloseIndex = async (indexName: string) => {
    const success = await doCloseOpenSearchIndex(indexName)
    if (success) { message.success('索引已关闭'); loadIndices() }
    else message.error('关闭失败')
  }

  const handleOpenIndex = async (indexName: string) => {
    const success = await doOpenOpenSearchIndex(indexName)
    if (success) { message.success('索引已开启'); loadIndices() }
    else message.error('开启失败')
  }

  const handleSearch = async () => {
    try {
      const values = await searchForm.validateFields()
      const result = await doSearchOpenSearch(searchIndexName || values.index || '*', values.query)
      if (result) {
        setSearchResults(result.documents || [])
        setSearchTotal(result.total || 0)
      }
    } catch {}
  }

  const handleDeleteDocument = async (indexName: string, docId: string) => {
    const success = await doDeleteOpenSearchDocument(indexName, docId)
    if (success) { message.success('文档已删除'); loadDocuments() }
    else message.error('删除失败')
  }

  const handleOpenConsole = async () => {
    await fetchOpenSearchConsoleUrl()
    if (opensearchConsoleUrl) window.open(opensearchConsoleUrl, '_blank')
  }

  const handleBrowseIndex = (indexName: string) => {
    setSelectedIndex(indexName)
    setActiveTab('documents')
  }

  const summary = useMemo(() => {
    const totalDocs = opensearchIndices.reduce((s, i) => s + i.docsCount, 0)
    const totalSize = opensearchIndices.reduce((s, i) => s + i.storeSize, 0)
    const openIndices = opensearchIndices.filter((i) => i.status === 'OPEN').length
    return { totalDocs, totalSize, openIndices }
  }, [opensearchIndices])

  const indexColumns: ColumnsType<OpenSearchIndex> = [
    {
      title: '索引名称', dataIndex: 'indexName', key: 'indexName', width: 220, ellipsis: true,
      render: (n: string) => <a onClick={() => handleBrowseIndex(n)}>{n}</a>,
    },
    {
      title: '健康', dataIndex: 'health', key: 'health', width: 90,
      render: (h: string) => <Tag color={healthStatusMap[h]?.color} icon={healthStatusMap[h]?.icon}>{h}</Tag>,
    },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 90,
      render: (s: string) => <Tag color={indexStatusMap[s]?.color || 'default'}>{s}</Tag>,
    },
    { title: '文档数', dataIndex: 'docsCount', key: 'docsCount', width: 100, render: (v: number) => v.toLocaleString() },
    { title: '已删除', dataIndex: 'docsDeleted', key: 'docsDeleted', width: 80, render: (v: number) => v.toLocaleString() },
    { title: '存储大小', dataIndex: 'storeSize', key: 'storeSize', width: 110, render: (v: number) => formatBytes(v) },
    { title: '主分片', dataIndex: 'primaryShards', key: 'primaryShards', width: 80 },
    { title: '副分片', dataIndex: 'replicaShards', key: 'replicaShards', width: 80 },
    { title: 'UUID', dataIndex: 'uuid', key: 'uuid', width: 120, ellipsis: true, render: (v: string) => <Text code>{v?.substring(0, 12)}...</Text> },
    {
      title: '操作', key: 'actions', width: 220,
      render: (_: unknown, r: OpenSearchIndex) => (
        <Space size="small">
          <Button size="small" icon={<FileTextOutlined />} onClick={() => handleBrowseIndex(r.indexName)}>文档</Button>
          {r.status === 'OPEN' ? (
            <Button size="small" onClick={() => handleCloseIndex(r.indexName)}>关闭</Button>
          ) : (
            <Button size="small" onClick={() => handleOpenIndex(r.indexName)}>开启</Button>
          )}
          <Popconfirm title="确定删除此索引？" onConfirm={() => handleDeleteIndex(r.indexName)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const documentColumns: ColumnsType<OpenSearchDocument> = [
    { title: 'ID', dataIndex: 'id', key: 'id', width: 200, ellipsis: true },
    { title: '索引', dataIndex: 'index', key: 'index', width: 150, render: (v: string) => <Tag>{v}</Tag> },
    { title: '分数', dataIndex: 'score', key: 'score', width: 80, render: (v: number) => v?.toFixed(2) },
    { title: '内容', dataIndex: 'source', key: 'source', width: 300, ellipsis: true, render: (s: Record<string, unknown>) => (
      <Text code style={{ fontSize: 11 }}>{JSON.stringify(s).substring(0, 80)}...</Text>
    )},
    {
      title: '操作', key: 'actions', width: 100,
      render: (_: unknown, r: OpenSearchDocument) => (
        <Popconfirm title="确定删除？" onConfirm={() => handleDeleteDocument(r.index, r.id)}>
          <Button size="small" danger icon={<DeleteOutlined />} />
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="索引数" value={opensearchIndicesTotal} prefix={<DatabaseOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="文档总数" value={summary.totalDocs.toLocaleString()} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="存储总量" value={formatBytes(summary.totalSize)} prefix={<BarChartOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 OpenSearch 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {/* 集群状态 */}
      {opensearchCluster && (
        <Card title="集群状态" size="small" style={{ marginBottom: 16 }} extra={<Button size="small" icon={<ReloadOutlined />} onClick={loadCluster} />}>
          <Descriptions bordered size="small" column={{ xs: 1, sm: 2, md: 4 }}>
            <Descriptions.Item label="集群名称">{opensearchCluster.clusterName}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <Tag color={healthStatusMap[opensearchCluster.status]?.color}>{opensearchCluster.status}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="节点数">{opensearchCluster.nodeCount}</Descriptions.Item>
            <Descriptions.Item label="数据节点">{opensearchCluster.dataNodes}</Descriptions.Item>
            <Descriptions.Item label="索引数">{opensearchCluster.indices}</Descriptions.Item>
            <Descriptions.Item label="文档总数">{opensearchCluster.totalDocs.toLocaleString()}</Descriptions.Item>
            <Descriptions.Item label="存储总量">{formatBytes(opensearchCluster.totalStore)}</Descriptions.Item>
            <Descriptions.Item label="活跃分片">{opensearchCluster.activeShards}</Descriptions.Item>
            <Descriptions.Item label="未分配分片">{opensearchCluster.unassignedShards}</Descriptions.Item>
            <Descriptions.Item label="版本">{opensearchCluster.version}</Descriptions.Item>
          </Descriptions>
        </Card>
      )}

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'indices',
          label: `索引 (${opensearchIndicesTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<SearchOutlined />} onClick={() => { setSearchIndexName(''); setSearchModalVisible(true); searchForm.resetFields() }}>搜索</Button>
                  <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateIndexModalVisible(true)}>创建索引</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadIndices}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {opensearchIndices.length === 0 && !loading ? (
                  <Empty description="暂无索引" />
                ) : (
                  <Table dataSource={opensearchIndices} columns={indexColumns} rowKey="indexName" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" scroll={{ x: 1200 }} />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'documents',
          label: `文档${selectedIndex ? ` (${selectedIndex})` : ''}`,
          children: (
            <Card
              extra={
                <Space>
                  {selectedIndex ? (
                    <>
                      <Button icon={<SearchOutlined />} onClick={() => { setSearchIndexName(selectedIndex); setSearchModalVisible(true); searchForm.resetFields() }}>搜索</Button>
                      <Button icon={<ReloadOutlined />} onClick={loadDocuments}>刷新</Button>
                      <Button onClick={() => { setSelectedIndex(null); setActiveTab('indices') }}>返回索引</Button>
                    </>
                  ) : (
                    <Text type="secondary">请先从索引列表中选择一个索引</Text>
                  )}
                </Space>
              }
            >
              <Spin spinning={loading}>
                {!selectedIndex ? (
                  <Empty description="请选择一个索引查看文档" />
                ) : opensearchDocuments.length === 0 && !loading ? (
                  <Empty description="此索引中没有文档" />
                ) : (
                  <Table dataSource={opensearchDocuments} columns={documentColumns} rowKey="id" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 创建索引弹窗 */}
      <Modal title="创建索引" open={createIndexModalVisible} onOk={handleCreateIndex} onCancel={() => { setCreateIndexModalVisible(false); indexForm.resetFields() }} width={600} destroyOnClose>
        <Form form={indexForm} layout="vertical">
          <Form.Item name="indexName" label="索引名称" rules={[{ required: true, message: '请输入索引名称' }]}>
            <Input placeholder="my-index" />
          </Form.Item>
          <Form.Item name="shards" label="主分片数" initialValue={3}>
            <InputNumber min={1} max={100} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="replicas" label="副本数" initialValue={1}>
            <InputNumber min={0} max={10} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="mappings" label="映射定义 (JSON)">
            <TextArea rows={6} placeholder='{"properties": {"title": {"type": "text"}}}' />
          </Form.Item>
          <Form.Item name="settings" label="索引设置 (JSON)">
            <TextArea rows={6} placeholder='{"refresh_interval": "1s"}' />
          </Form.Item>
        </Form>
      </Modal>

      {/* 搜索弹窗 */}
      <Modal title={`搜索${searchIndexName ? ` - ${searchIndexName}` : ''}`} open={searchModalVisible} onOk={handleSearch} onCancel={() => setSearchModalVisible(false)} width={700} destroyOnClose>
        <Form form={searchForm} layout="vertical">
          {!searchIndexName && (
            <Form.Item name="index" label="索引" rules={[{ required: true }]}>
              <Select placeholder="选择索引" options={opensearchIndices.map((idx) => ({ label: idx.indexName, value: idx.indexName }))} />
            </Form.Item>
          )}
          <Form.Item name="query" label="查询 (Query DSL JSON)" rules={[{ required: true }]}>
            <TextArea rows={6} placeholder='{"query": {"match_all": {}}}' />
          </Form.Item>
        </Form>
        {searchResults.length > 0 && (
          <Table
            dataSource={searchResults}
            columns={documentColumns}
            rowKey="id"
            pagination={false}
            size="small"
            style={{ marginTop: 16 }}
            scroll={{ x: 800 }}
            title={() => <Text strong>搜索结果 ({searchTotal} 条)</Text>}
          />
        )}
      </Modal>
    </div>
  )
}