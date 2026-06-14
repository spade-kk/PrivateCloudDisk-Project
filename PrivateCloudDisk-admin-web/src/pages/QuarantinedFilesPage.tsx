// ============================================================
// 隔离文件管理页面（病毒文件）
// ============================================================
import { useEffect, useState, useCallback } from 'react'
import { Table, Button, Space, Tag, Input, Popconfirm, Empty, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  ExclamationCircleOutlined, SearchOutlined, ReloadOutlined,
  UndoOutlined, DeleteOutlined,
} from '@ant-design/icons'
import { getQuarantinedFilesApi, restoreQuarantinedFileApi, adminDeleteFileApi } from '@/api/files'
import PageHeader from '@/components/PageHeader'
import type { FileNode, FileFilterParams } from '@/types/api'

export default function QuarantinedFilesPage() {
  const [files, setFiles] = useState<FileNode[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [loading, setLoading] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [searchText, setSearchText] = useState('')

  const fetchFiles = useCallback(async () => {
    setLoading(true)
    try {
      const params: FileFilterParams = { page, pageSize }
      if (keyword) params.keyword = keyword
      const res = await getQuarantinedFilesApi(params)
      if (res.data.code === 200) {
        const data = res.data.data
        setFiles(data.records || data.list || [])
        setTotal(data.total || 0)
      }
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }, [page, pageSize, keyword])

  useEffect(() => {
    fetchFiles()
  }, [fetchFiles])

  const handleSearch = () => setKeyword(searchText)

  const handleRestore = async (fileId: string) => {
    try {
      const res = await restoreQuarantinedFileApi(fileId)
      if (res.data.code === 200) {
        message.success('已恢复文件')
        fetchFiles()
      } else {
        message.error(res.data.message || '恢复失败')
      }
    } catch {
      message.error('操作失败')
    }
  }

  const handleDelete = async (fileId: string) => {
    try {
      const res = await adminDeleteFileApi(fileId)
      if (res.data.code === 200) {
        message.success('已删除文件')
        fetchFiles()
      }
    } catch {
      message.error('删除失败')
    }
  }

  const columns: ColumnsType<FileNode> = [
    { title: '文件名', dataIndex: 'nodeName', key: 'nodeName', width: 200, ellipsis: true },
    { title: '所有用户', dataIndex: 'ownerName', key: 'ownerName', width: 120 },
    { title: '大小', dataIndex: 'size', key: 'size', width: 100, render: (s: number) => s ? `${(s / 1024 / 1024).toFixed(2)} MB` : '-' },
    { title: 'MIME', dataIndex: 'mimeType', key: 'mimeType', width: 140, ellipsis: true },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: () => <Tag color="red" icon={<ExclamationCircleOutlined />}>已隔离</Tag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      render: (_: unknown, record: FileNode) => (
        <Space size="small">
          <Popconfirm title="确认恢复此文件?" onConfirm={() => handleRestore(record.nodeId)} okText="恢复" cancelText="取消">
            <Button type="link" size="small" icon={<UndoOutlined />}>恢复</Button>
          </Popconfirm>
          <Popconfirm title="确定永久删除此文件?" onConfirm={() => handleDelete(record.nodeId)} okText="删除" okType="danger" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="隔离文件"
        subtitle="病毒扫描发现的受感染文件"
        icon={<ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={fetchFiles}>刷新</Button>
        }
      />

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
      </Space>

      <Table
        columns={columns}
        dataSource={files}
        rowKey="nodeId"
        loading={loading}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          showTotal: (t) => `共 ${t} 条`,
          onChange: (p, ps) => { setPage(p); setPageSize(ps); },
        }}
        locale={{ emptyText: <Empty description="暂无隔离文件，系统状态良好" /> }}
      />
    </div>
  )
}