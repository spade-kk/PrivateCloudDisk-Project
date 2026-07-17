// ============================================================
// MinIO 管理页面
// Bucket 管理、对象管理、策略、控制台集成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, Upload,
  Progress, Spin, Alert, Empty, Tabs, Typography, Popconfirm, message, Statistic, Row, Col, Descriptions, Tooltip, Switch, Dropdown,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, DeleteOutlined, EditOutlined,
  LinkOutlined, SearchOutlined, UploadOutlined, DownloadOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined,
  InboxOutlined, FolderOutlined, FileOutlined, CopyOutlined, EyeOutlined,
  CloudServerOutlined, SettingOutlined,
} from '@ant-design/icons'
import { useMiddlewareStore } from '@/stores/middlewareStore'
import type { MinIOBucket, MinIOObject, MinIOPolicy } from '@/api/middleware'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { Dragger } = Upload

function formatBytes(bytes: number): string {
  if (!bytes || bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return `${(bytes / Math.pow(k, i)).toFixed(1)} ${sizes[i]}`
}

const accessModeMap: Record<string, { color: string }> = {
  PRIVATE: { color: 'red' },
  PUBLIC: { color: 'green' },
  CUSTOM: { color: 'blue' },
}

export default function MinIOManagePage() {
  const {
    minioBuckets, minioBucketsTotal, minioObjects, minioObjectsTotal,
    minioPolicies, minioConsoleUrl, loading, error,
    fetchMinIOBuckets, fetchMinIOObjects, fetchMinIOPolicies,
    doCreateMinIOBucket, doDeleteMinIOBucket, doSetMinIOBucketPolicy,
    doUploadMinIOObject, doDeleteMinIOObject, fetchMinIOConsoleUrl,
  } = useMiddlewareStore()

  const [activeTab, setActiveTab] = useState('buckets')
  const [selectedBucket, setSelectedBucket] = useState<string | null>(null)
  const [createModalVisible, setCreateModalVisible] = useState(false)
  const [uploadModalVisible, setUploadModalVisible] = useState(false)
  const [policyModalVisible, setPolicyModalVisible] = useState(false)
  const [objectPreviewVisible, setObjectPreviewVisible] = useState(false)
  const [previewContent, setPreviewContent] = useState('')
  const [bucketForm] = Form.useForm()
  const [policyForm] = Form.useForm()
  const [searchObject, setSearchObject] = useState('')

  const loadBuckets = useCallback(() => { fetchMinIOBuckets({ page: 1, pageSize: 50 }) }, [fetchMinIOBuckets])
  const loadObjects = useCallback(() => {
    if (selectedBucket) fetchMinIOObjects(selectedBucket, { page: 1, pageSize: 50, prefix: searchObject || undefined })
  }, [fetchMinIOObjects, selectedBucket, searchObject])
  const loadPolicies = useCallback(() => { fetchMinIOPolicies() }, [fetchMinIOPolicies])

  useEffect(() => { loadBuckets() }, [loadBuckets])
  useEffect(() => {
    if (activeTab === 'policies') loadPolicies()
    else if (activeTab === 'objects' && selectedBucket) loadObjects()
  }, [activeTab, loadPolicies, loadObjects, selectedBucket])

  const handleCreateBucket = async () => {
    try {
      const values = await bucketForm.validateFields()
      const success = await doCreateMinIOBucket(values)
      if (success) { message.success('Bucket 创建成功'); setCreateModalVisible(false); bucketForm.resetFields(); loadBuckets() }
      else message.error('创建失败')
    } catch {}
  }

  const handleDeleteBucket = async (bucketName: string) => {
    const success = await doDeleteMinIOBucket(bucketName)
    if (success) { message.success('Bucket 已删除'); loadBuckets() }
    else message.error('删除失败')
  }

  const handleSetPolicy = async () => {
    try {
      const values = await policyForm.validateFields()
      const success = await doSetMinIOBucketPolicy(selectedBucket!, values.policy)
      if (success) { message.success('策略已设置'); setPolicyModalVisible(false) }
      else message.error('设置失败')
    } catch {}
  }

  const handleUpload = async (file: File) => {
    const success = await doUploadMinIOObject(selectedBucket!, file)
    if (success) { message.success('上传成功'); loadObjects() }
    else message.error('上传失败')
    return false // 阻止默认上传行为
  }

  const handleDeleteObject = async (objectName: string) => {
    const success = await doDeleteMinIOObject(selectedBucket!, objectName)
    if (success) { message.success('对象已删除'); loadObjects() }
    else message.error('删除失败')
  }

  const handleOpenConsole = async () => {
    await fetchMinIOConsoleUrl()
    if (minioConsoleUrl) window.open(minioConsoleUrl, '_blank')
  }

  const handleBrowseBucket = (bucketName: string) => {
    setSelectedBucket(bucketName)
    setActiveTab('objects')
  }

  const handlePreviewObject = (obj: MinIOObject) => {
    // 模拟预览：显示对象元数据
    setPreviewContent(JSON.stringify({
      name: obj.name,
      size: formatBytes(obj.size),
      contentType: obj.contentType,
      lastModified: obj.lastModified,
      etag: obj.etag,
      versionId: obj.versionId,
      metadata: obj.metadata,
    }, null, 2))
    setObjectPreviewVisible(true)
  }

  const summary = useMemo(() => {
    const totalSize = minioBuckets.reduce((s, b) => s + b.size, 0)
    const totalObjects = minioBuckets.reduce((s, b) => s + b.objectCount, 0)
    return { totalSize, totalObjects }
  }, [minioBuckets])

  const bucketColumns: ColumnsType<MinIOBucket> = [
    {
      title: '名称', dataIndex: 'name', key: 'name', width: 200,
      render: (n: string) => <a onClick={() => handleBrowseBucket(n)}>{n}</a>,
    },
    { title: '区域', dataIndex: 'region', key: 'region', width: 100, render: (r: string) => <Tag>{r}</Tag> },
    {
      title: '访问权限', dataIndex: 'access', key: 'access', width: 100,
      render: (a: string) => <Tag color={accessModeMap[a]?.color || 'default'}>{a}</Tag>,
    },
    { title: '对象数', dataIndex: 'objectCount', key: 'objectCount', width: 90 },
    { title: '大小', dataIndex: 'size', key: 'size', width: 110, render: (v: number) => formatBytes(v) },
    {
      title: '版本控制', dataIndex: 'versioning', key: 'versioning', width: 90,
      render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
    },
    {
      title: '加密', dataIndex: 'encryption', key: 'encryption', width: 80,
      render: (v: boolean) => v ? <CheckCircleOutlined style={{ color: '#52c41a' }} /> : '-',
    },
    { title: '创建时间', dataIndex: 'creationDate', key: 'creationDate', width: 160 },
    {
      title: '操作', key: 'actions', width: 200,
      render: (_: unknown, r: MinIOBucket) => (
        <Space size="small">
          <Button size="small" icon={<FolderOutlined />} onClick={() => handleBrowseBucket(r.name)}>浏览</Button>
          <Button size="small" icon={<SettingOutlined />} onClick={() => { setSelectedBucket(r.name); setPolicyModalVisible(true); policyForm.setFieldsValue({ policy: r.access }) }}>权限</Button>
          <Popconfirm title="确定删除此 Bucket？" onConfirm={() => handleDeleteBucket(r.name)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const objectColumns: ColumnsType<MinIOObject> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 300, ellipsis: true, render: (n: string) => <Space><FileOutlined />{n}</Space> },
    { title: '大小', dataIndex: 'size', key: 'size', width: 110, render: (v: number) => formatBytes(v) },
    { title: '类型', dataIndex: 'contentType', key: 'contentType', width: 150, ellipsis: true, render: (t: string) => <Tag>{t || 'unknown'}</Tag> },
    { title: '最后修改', dataIndex: 'lastModified', key: 'lastModified', width: 160 },
    { title: 'ETag', dataIndex: 'etag', key: 'etag', width: 120, ellipsis: true, render: (v: string) => <Text code>{v?.substring(0, 12)}...</Text> },
    { title: '版本ID', dataIndex: 'versionId', key: 'versionId', width: 120, ellipsis: true },
    {
      title: '操作', key: 'actions', width: 140,
      render: (_: unknown, r: MinIOObject) => (
        <Space size="small">
          <Tooltip title="预览"><Button size="small" icon={<EyeOutlined />} onClick={() => handlePreviewObject(r)} /></Tooltip>
          <Tooltip title="下载"><Button size="small" icon={<DownloadOutlined />} /></Tooltip>
          <Popconfirm title="确定删除？" onConfirm={() => handleDeleteObject(r.name)}>
            <Button size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  const policyColumns: ColumnsType<MinIOPolicy> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 300, ellipsis: true },
    { title: '策略内容', dataIndex: 'policy', key: 'policy', width: 300, ellipsis: true, render: (v: string) => <Text code>{v?.substring(0, 60)}...</Text> },
    { title: '更新/创建时间', key: 'time', width: 160, render: (_: unknown, r: MinIOPolicy) => r.updatedAt || r.createdAt || '-' },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="Bucket 数" value={minioBucketsTotal} prefix={<InboxOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="对象总数" value={summary.totalObjects} prefix={<FileOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总存储量" value={formatBytes(summary.totalSize)} prefix={<CloudServerOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small">
            <Tooltip title="打开 MinIO 控制台">
              <Statistic title="控制台" value="打开" prefix={<LinkOutlined />} valueStyle={{ fontSize: 16, cursor: 'pointer' }} onClick={handleOpenConsole} />
            </Tooltip>
          </Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'buckets',
          label: `Buckets (${minioBucketsTotal})`,
          children: (
            <Card
              extra={
                <Space>
                  <Button icon={<PlusOutlined />} type="primary" onClick={() => setCreateModalVisible(true)}>创建 Bucket</Button>
                  <Button icon={<ReloadOutlined />} onClick={loadBuckets}>刷新</Button>
                </Space>
              }
            >
              <Spin spinning={loading}>
                {minioBuckets.length === 0 && !loading ? (
                  <Empty description="暂无 Bucket" />
                ) : (
                  <Table dataSource={minioBuckets} columns={bucketColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'objects',
          label: `对象${selectedBucket ? ` (${selectedBucket})` : ''}`,
          children: (
            <Card
              extra={
                <Space>
                  {selectedBucket ? (
                    <>
                      <Input placeholder="搜索对象" prefix={<SearchOutlined />} allowClear style={{ width: 200 }}
                        value={searchObject} onChange={(e) => setSearchObject(e.target.value)} onPressEnter={loadObjects} />
                      <Button icon={<UploadOutlined />} onClick={() => setUploadModalVisible(true)}>上传</Button>
                      <Button icon={<ReloadOutlined />} onClick={loadObjects}>刷新</Button>
                      <Button onClick={() => { setSelectedBucket(null); setActiveTab('buckets') }}>返回 Buckets</Button>
                    </>
                  ) : (
                    <Text type="secondary">请先从 Buckets 列表中选择一个 Bucket</Text>
                  )}
                </Space>
              }
            >
              <Spin spinning={loading}>
                {!selectedBucket ? (
                  <Empty description="请选择一个 Bucket 查看其对象" />
                ) : minioObjects.length === 0 && !loading ? (
                  <Empty description="此 Bucket 中没有对象" />
                ) : (
                  <Table dataSource={minioObjects} columns={objectColumns} rowKey="name" pagination={{ pageSize: 20, showSizeChanger: true }} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
        {
          key: 'policies',
          label: `策略 (${minioPolicies.length})`,
          children: (
            <Card extra={<Button icon={<ReloadOutlined />} onClick={loadPolicies}>刷新</Button>}>
              <Spin spinning={loading}>
                {minioPolicies.length === 0 && !loading ? (
                  <Empty description="暂无策略" />
                ) : (
                  <Table dataSource={minioPolicies} columns={policyColumns} rowKey="name" pagination={false} size="middle" />
                )}
              </Spin>
            </Card>
          ),
        },
      ]} />

      {/* 创建 Bucket 弹窗 */}
      <Modal title="创建 Bucket" open={createModalVisible} onOk={handleCreateBucket} onCancel={() => { setCreateModalVisible(false); bucketForm.resetFields() }} destroyOnClose>
        <Form form={bucketForm} layout="vertical">
          <Form.Item name="name" label="Bucket 名称" rules={[{ required: true, message: '请输入 Bucket 名称' }]}>
            <Input placeholder="my-bucket" />
          </Form.Item>
          <Form.Item name="region" label="区域" initialValue="us-east-1">
            <Select options={[
              { label: 'us-east-1', value: 'us-east-1' },
              { label: 'cn-north-1', value: 'cn-north-1' },
            ]} />
          </Form.Item>
          <Form.Item name="versioning" label="版本控制" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
          <Form.Item name="objectLocking" label="对象锁定" valuePropName="checked" initialValue={false}>
            <Switch />
          </Form.Item>
        </Form>
      </Modal>

      {/* 上传弹窗 */}
      <Modal title="上传文件" open={uploadModalVisible} onCancel={() => setUploadModalVisible(false)} footer={null} destroyOnClose>
        <Dragger
          multiple
          beforeUpload={(file) => { handleUpload(file); return false }}
          showUploadList={true}
        >
          <p className="ant-upload-drag-icon"><InboxOutlined /></p>
          <p className="ant-upload-text">点击或拖拽文件到此区域上传</p>
          <p className="ant-upload-hint">上传到 {selectedBucket}</p>
        </Dragger>
      </Modal>

      {/* 策略设置弹窗 */}
      <Modal title={`设置策略 - ${selectedBucket}`} open={policyModalVisible} onOk={handleSetPolicy} onCancel={() => setPolicyModalVisible(false)} destroyOnClose>
        <Form form={policyForm} layout="vertical">
          <Form.Item name="policy" label="访问权限" rules={[{ required: true }]}>
            <Select options={[
              { label: '私有 (PRIVATE)', value: 'PRIVATE' },
              { label: '公开读 (PUBLIC)', value: 'PUBLIC' },
              { label: '自定义 (CUSTOM)', value: 'CUSTOM' },
            ]} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 对象预览弹窗 */}
      <Modal title="对象详情" open={objectPreviewVisible} onCancel={() => setObjectPreviewVisible(false)} footer={null} width={600} destroyOnClose>
        <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 4, fontSize: 12, maxHeight: 400, overflow: 'auto' }}>
          {previewContent}
        </pre>
      </Modal>
    </div>
  )
}