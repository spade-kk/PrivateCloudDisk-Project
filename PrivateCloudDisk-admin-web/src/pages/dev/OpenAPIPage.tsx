// ============================================================
// OpenAPI 对接页面
// 导入/导出 OpenAPI 规范、客户端代码生成
// ============================================================
import { useEffect, useState, useCallback, useMemo } from 'react'
import {
  Card, Table, Tag, Space, Button, Modal, Input, Select, Form, Upload,
  Spin, Alert, Empty, Tabs, Typography, message, Statistic, Row, Col, Tooltip, Descriptions, Divider,
} from 'antd'
import {
  ReloadOutlined, PlusOutlined, LinkOutlined, DownloadOutlined,
  ImportOutlined, ExportOutlined, CodeOutlined, FileTextOutlined,
  CheckCircleOutlined, WarningOutlined, CloseCircleOutlined, SyncOutlined,
  ApiOutlined, CopyOutlined, ThunderboltOutlined,
} from '@ant-design/icons'
import { useDevToolsStore } from '@/stores/devToolsStore'
import type { ColumnsType } from 'antd/es/table'

const { Text, Paragraph } = Typography
const { TextArea } = Input

const SUPPORTED_LANGUAGES = [
  { label: 'TypeScript / Axios', value: 'typescript-axios' },
  { label: 'TypeScript / Fetch', value: 'typescript-fetch' },
  { label: 'Java', value: 'java' },
  { label: 'Python', value: 'python' },
  { label: 'Go', value: 'go' },
  { label: 'Rust', value: 'rust' },
  { label: 'C#', value: 'csharp' },
  { label: 'Kotlin', value: 'kotlin' },
]

export default function OpenAPIPage() {
  const {
    openAPISpecs, openAPIClientCode, loading, error,
    fetchOpenAPISpecs, doImportOpenAPISpec, doExportOpenAPISpec, fetchOpenAPIClientCode,
  } = useDevToolsStore()

  const [importModalVisible, setImportModalVisible] = useState(false)
  const [codeModalVisible, setCodeModalVisible] = useState(false)
  const [exportContent, setExportContent] = useState('')
  const [exportModalVisible, setExportModalVisible] = useState(false)
  const [importForm] = Form.useForm()
  const [codeForm] = Form.useForm()
  const [selectedServiceId, setSelectedServiceId] = useState('')

  useEffect(() => { fetchOpenAPISpecs() }, [fetchOpenAPISpecs])

  const handleImport = async () => {
    try {
      const values = await importForm.validateFields()
      const success = await doImportOpenAPISpec(values)
      if (success) {
        message.success('OpenAPI 规范导入成功')
        setImportModalVisible(false)
        importForm.resetFields()
        fetchOpenAPISpecs()
      } else {
        message.error('导入失败')
      }
    } catch {}
  }

  const handleExport = async (serviceId: string) => {
    const content = await doExportOpenAPISpec(serviceId)
    if (content) {
      setExportContent(content)
      setExportModalVisible(true)
    }
  }

  const handleGenerateCode = async () => {
    try {
      const values = await codeForm.validateFields()
      setSelectedServiceId(values.serviceId)
      await fetchOpenAPIClientCode(values.serviceId, values.language)
      setCodeModalVisible(true)
    } catch {}
  }

  const handleCopyCode = () => {
    navigator.clipboard.writeText(openAPIClientCode)
    message.success('已复制到剪贴板')
  }

  const handleDownloadCode = () => {
    const blob = new Blob([openAPIClientCode], { type: 'text/plain' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = 'api-client-code.txt'
    a.click()
    URL.revokeObjectURL(url)
  }

  const specColumns: ColumnsType<any> = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 200 },
    { title: '版本', dataIndex: 'version', key: 'version', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '规范', dataIndex: 'specVersion', key: 'specVersion', width: 120, render: (v: string) => <Tag color="blue">{v}</Tag> },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 100,
      render: (s: string) => {
        const colorMap: Record<string, string> = { AVAILABLE: 'green', UNAVAILABLE: 'red', ERROR: 'orange' }
        return <Tag color={colorMap[s] || 'default'}>{s}</Tag>
      },
    },
    { title: '接口数', dataIndex: 'pathCount', key: 'pathCount', width: 80 },
    { title: '描述', dataIndex: 'description', key: 'description', width: 200, ellipsis: true },
    {
      title: '操作', key: 'actions', width: 200,
      render: (_: unknown, r: any) => (
        <Space size="small">
          <Button size="small" icon={<ExportOutlined />} onClick={() => handleExport(r.id)}>导出</Button>
          <Button size="small" icon={<CodeOutlined />} onClick={() => { codeForm.setFieldsValue({ serviceId: r.id }); }}>生成代码</Button>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="OpenAPI 规范" value={openAPISpecs.length} prefix={<ApiOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="可用" value={openAPISpecs.filter((s) => s.status === 'AVAILABLE').length} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="总接口数" value={openAPISpecs.reduce((s, spec) => s + spec.pathCount, 0)} prefix={<FileTextOutlined />} /></Card>
        </Col>
        <Col xs={12} sm={6}>
          <Card size="small"><Statistic title="支持语言" value={SUPPORTED_LANGUAGES.length} prefix={<CodeOutlined />} /></Card>
        </Col>
      </Row>

      {error && <Alert message={error} type="error" showIcon closable style={{ marginBottom: 16 }} />}

      <Card
        title="OpenAPI 规范管理"
        extra={
          <Space>
            <Button icon={<ImportOutlined />} type="primary" onClick={() => setImportModalVisible(true)}>导入规范</Button>
            <Button icon={<ReloadOutlined />} onClick={fetchOpenAPISpecs}>刷新</Button>
          </Space>
        }
      >
        <Spin spinning={loading}>
          {openAPISpecs.length === 0 && !loading ? (
            <Empty description="暂无 OpenAPI 规范，请导入" />
          ) : (
            <Table dataSource={openAPISpecs} columns={specColumns} rowKey="id" pagination={false} size="middle" />
          )}
        </Spin>
      </Card>

      {/* 代码生成 */}
      <Card title="客户端代码生成" style={{ marginTop: 16 }}>
        <Form form={codeForm} layout="inline">
          <Form.Item name="serviceId" label="API 规范" rules={[{ required: true }]}>
            <Select placeholder="选择规范" style={{ width: 250 }} options={openAPISpecs.map((s) => ({ label: `${s.name} (${s.version})`, value: s.id }))} />
          </Form.Item>
          <Form.Item name="language" label="目标语言" initialValue="typescript-axios" rules={[{ required: true }]}>
            <Select style={{ width: 200 }} options={SUPPORTED_LANGUAGES} />
          </Form.Item>
          <Form.Item>
            <Button type="primary" icon={<ThunderboltOutlined />} onClick={handleGenerateCode} loading={loading}>
              生成代码
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {/* 导入弹窗 */}
      <Modal title="导入 OpenAPI 规范" open={importModalVisible} onOk={handleImport} onCancel={() => { setImportModalVisible(false); importForm.resetFields() }} destroyOnClose>
        <Form form={importForm} layout="vertical">
          <Form.Item name="name" label="名称" rules={[{ required: true }]}>
            <Input placeholder="我的 API" />
          </Form.Item>
          <Form.Item name="url" label="OpenAPI 规范 URL" rules={[{ required: true, type: 'url' }]}>
            <Input placeholder="https://example.com/api/openapi.json" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <TextArea rows={3} placeholder="服务描述" />
          </Form.Item>
        </Form>
      </Modal>

      {/* 导出弹窗 */}
      <Modal title="导出 OpenAPI 规范" open={exportModalVisible} onCancel={() => setExportModalVisible(false)} footer={[
        <Button key="copy" icon={<CopyOutlined />} onClick={() => { navigator.clipboard.writeText(exportContent); message.success('已复制'); }}>复制</Button>,
        <Button key="download" icon={<DownloadOutlined />} onClick={() => {
          const blob = new Blob([exportContent], { type: 'application/json' })
          const url = URL.createObjectURL(blob)
          const a = document.createElement('a')
          a.href = url; a.download = 'openapi.json'; a.click()
          URL.revokeObjectURL(url)
        }}>下载</Button>,
        <Button key="close" onClick={() => setExportModalVisible(false)}>关闭</Button>,
      ]} width={700} destroyOnClose>
        <pre style={{ maxHeight: 500, overflow: 'auto', background: '#f5f5f5', padding: 16, borderRadius: 4, fontSize: 12 }}>
          {exportContent}
        </pre>
      </Modal>

      {/* 代码展示弹窗 */}
      <Modal title="生成的客户端代码" open={codeModalVisible} onCancel={() => setCodeModalVisible(false)} footer={[
        <Button key="copy" icon={<CopyOutlined />} onClick={handleCopyCode}>复制</Button>,
        <Button key="download" type="primary" icon={<DownloadOutlined />} onClick={handleDownloadCode}>下载</Button>,
      ]} width={800} destroyOnClose>
        <pre style={{ maxHeight: 500, overflow: 'auto', background: '#1e1e1e', color: '#d4d4d4', padding: 16, borderRadius: 4, fontSize: 12, lineHeight: 1.5 }}>
          {openAPIClientCode || '请选择规范和语言后点击生成'}
        </pre>
      </Modal>
    </div>
  )
}