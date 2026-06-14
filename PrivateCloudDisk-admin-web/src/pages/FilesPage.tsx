// ============================================================
// 文件管理 - 所有文件页面
// ============================================================
import { useEffect, useState, useCallback } from 'react'
import {
  Table, Button, Space, Tag, Input, Select, Popconfirm,
  Tooltip, Empty, Drawer, Descriptions,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  FileOutlined, SearchOutlined, ReloadOutlined, DeleteOutlined,
  ExclamationCircleOutlined, CheckCircleOutlined, CloudSyncOutlined,
  LockOutlined, GlobalOutlined, EyeOutlined, FolderOutlined,
} from '@ant-design/icons'
import { useFilesStore } from '@/stores/filesStore'
import PageHeader from '@/components/PageHeader'
import type { FileNode } from '@/types/api'
const { Option } = Select

// 格式化文件大小
function formatFileSize(bytes: number): string {
  if (!bytes) return '-'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i]
}

// 文件类型图标颜色映射
const mimeColors: Record<string, string> = {
  image: '#52c41a',
  video: '#722ed1',
  audio: '#eb2f96',
  document: '#1890ff',
  pdf: '#ff4d4f',
  archive: '#faad14',
  code: '#13c2c2',
  spreadsheet: '#2f54eb',
  presentation: '#fa541c',
}

function getMimeColor(mimeType: string): string {
  for (const [key, color] of Object.entries(mimeColors)) {
    if (mimeType.startsWith(key)) return color
  }
  return '#8c8c8c'
}

export default function FilesPage() {
  const {
    files, total, page, pageSize, loading, nodeType,
    virusScanStatus, selectedRowKeys, fetchFiles, removeFile,
    setPage, setPageSize, setKeyword, setNodeType, setVirusScanStatus,
    setSelectedRowKeys,
  } = useFilesStore()

  const [searchText, setSearchText] = useState('')
  const [detailDrawer, setDetailDrawer] = useState(false)
  const [selectedFile, setSelectedFile] = useState<FileNode | null>(null)

  useEffect(() => {
    fetchFiles()
  }, [fetchFiles])

  const handleSearch = useCallback(() => {
    setKeyword(searchText)
  }, [searchText, setKeyword])

  const showDetail = (file: FileNode) => {
    setSelectedFile(file)
    setDetailDrawer(true)
  }

  const columns: ColumnsType<FileNode> = [
    {
      title: '文件名',
      dataIndex: 'nodeName',
      key: 'nodeName',
      width: 260,
      render: (name: string, record: FileNode) => (
        <Space>
          {record.nodeType === 'FOLDER' ? (
            <FolderOutlined style={{ color: '#faad14', fontSize: 18 }} />
          ) : (
            <FileOutlined style={{ color: getMimeColor(record.mimeType), fontSize: 18 }} />
          )}
          <a onClick={() => showDetail(record)} style={{ maxWidth: 200, display: 'inline-block', overflow: 'hidden', textOverflow: 'ellipsis' }}>
            {name}
          </a>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'nodeType',
      key: 'nodeType',
      width: 80,
      render: (type: string) => (
        <Tag color={type === 'FOLDER' ? 'blue' : 'default'}>
          {type === 'FOLDER' ? '文件夹' : '文件'}
        </Tag>
      ),
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 100,
      render: (size: number) => formatFileSize(size),
    },
    {
      title: '所有者',
      dataIndex: 'ownerName',
      key: 'ownerName',
      width: 120,
      ellipsis: true,
    },
    {
      title: '病毒扫描',
      dataIndex: 'virusScanStatus',
      key: 'virusScanStatus',
      width: 110,
      render: (status: string) => {
        const config: Record<string, { color: string; text: string; icon: React.ReactNode }> = {
          CLEAN: { color: 'green', text: '安全', icon: <CheckCircleOutlined /> },
          INFECTED: { color: 'red', text: '感染', icon: <ExclamationCircleOutlined /> },
          PENDING: { color: 'orange', text: '扫描中', icon: <CloudSyncOutlined /> },
          UNKNOWN: { color: 'default', text: '未知', icon: null },
        }
        const c = config[status] || config.UNKNOWN
        return <Tag color={c.color} icon={c.icon}>{c.text}</Tag>
      },
    },
    {
      title: '标记',
      key: 'flags',
      width: 80,
      render: (_: unknown, record: FileNode) => (
        <Space size={4}>
          {record.isEncrypted && <Tooltip title="已加密"><LockOutlined style={{ color: '#faad14' }} /></Tooltip>}
          {record.isPublic && <Tooltip title="公开"><GlobalOutlined style={{ color: '#1890ff' }} /></Tooltip>}
        </Space>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_: unknown, record: FileNode) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="text" size="small" icon={<EyeOutlined />}
              onClick={() => showDetail(record)} />
          </Tooltip>
          <Popconfirm
            title="确定删除此文件?"
            description="此操作不可恢复"
            onConfirm={() => removeFile(record.nodeId)}
            okText="删除"
            okType="danger"
            cancelText="取消"
          >
            <Button type="text" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="文件管理"
        subtitle={`共 ${total} 个文件/文件夹`}
        icon={<FileOutlined />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={() => fetchFiles()}>
            刷新
          </Button>
        }
      />

      {/* 筛选栏 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder="搜索文件名"
          prefix={<SearchOutlined />}
          value={searchText}
          onChange={(e) => setSearchText(e.target.value)}
          onPressEnter={handleSearch}
          style={{ width: 240 }}
          allowClear
          onClear={() => { setSearchText(''); setKeyword(''); }}
        />
        <Button type="primary" onClick={handleSearch}>搜索</Button>
        <Select
          placeholder="文件类型"
          value={nodeType}
          onChange={(val) => setNodeType(val)}
          allowClear
          style={{ width: 120 }}
        >
          <Option value="FILE">文件</Option>
          <Option value="FOLDER">文件夹</Option>
        </Select>
        <Select
          placeholder="病毒状态"
          value={virusScanStatus}
          onChange={(val) => setVirusScanStatus(val)}
          allowClear
          style={{ width: 120 }}
        >
          <Option value="CLEAN">安全</Option>
          <Option value="INFECTED">已感染</Option>
          <Option value="PENDING">扫描中</Option>
          <Option value="UNKNOWN">未知</Option>
        </Select>
      </Space>

      <Table
        columns={columns}
        dataSource={files}
        rowKey="nodeId"
        loading={loading}
        rowSelection={{
          selectedRowKeys,
          onChange: (keys) => setSelectedRowKeys(keys as string[]),
        }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          pageSizeOptions: ['10', '20', '50', '100'],
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
        scroll={{ x: 1200 }}
        locale={{ emptyText: <Empty description="暂无文件数据" /> }}
      />

      {/* 文件详情抽屉 */}
      <Drawer
        title="文件详情"
        open={detailDrawer}
        onClose={() => setDetailDrawer(false)}
        width={480}
      >
        {selectedFile && (
          <Descriptions column={1} bordered size="small">
            <Descriptions.Item label="文件名">{selectedFile.nodeName}</Descriptions.Item>
            <Descriptions.Item label="类型">
              <Tag color={selectedFile.nodeType === 'FOLDER' ? 'blue' : 'default'}>
                {selectedFile.nodeType === 'FOLDER' ? '文件夹' : '文件'}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="大小">{formatFileSize(selectedFile.size)}</Descriptions.Item>
            <Descriptions.Item label="MIME 类型">{selectedFile.mimeType || '-'}</Descriptions.Item>
            <Descriptions.Item label="扩展名">{selectedFile.extension || '-'}</Descriptions.Item>
            <Descriptions.Item label="所有者">{selectedFile.ownerName}</Descriptions.Item>
            <Descriptions.Item label="是否公开">
              {selectedFile.isPublic ? <Tag color="blue">是</Tag> : <Tag>否</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="是否加密">
              {selectedFile.isEncrypted ? <Tag color="orange">是</Tag> : <Tag>否</Tag>}
            </Descriptions.Item>
            <Descriptions.Item label="病毒状态">
              <Tag color={
                selectedFile.virusScanStatus === 'CLEAN' ? 'green' :
                selectedFile.virusScanStatus === 'INFECTED' ? 'red' :
                selectedFile.virusScanStatus === 'PENDING' ? 'orange' : 'default'
              }>
                {selectedFile.virusScanStatus}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="下载次数">{selectedFile.downloadCount}</Descriptions.Item>
            <Descriptions.Item label="创建时间">
              {new Date(selectedFile.createdAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
            <Descriptions.Item label="更新时间">
              {new Date(selectedFile.updatedAt).toLocaleString('zh-CN')}
            </Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  )
}