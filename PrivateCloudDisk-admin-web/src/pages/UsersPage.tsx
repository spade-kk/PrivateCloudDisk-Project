// ============================================================
// 用户管理 - 用户列表页面
// ============================================================
import { useEffect, useState, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Table, Button, Space, Tag, Input, Select, Modal, message,
  Switch, Tooltip, Avatar, Typography, Popconfirm, Dropdown, Empty,
} from 'antd'
import type { MenuProps } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  UserOutlined, SearchOutlined, ReloadOutlined, ExportOutlined,
  DeleteOutlined, StopOutlined, CheckCircleOutlined,
  MailOutlined, PhoneOutlined, MoreOutlined, EyeOutlined,
  TeamOutlined, ExclamationCircleOutlined, SafetyOutlined,
} from '@ant-design/icons'
import { useUsersStore } from '@/stores/usersStore'
import PageHeader from '@/components/PageHeader'
import { exportUsersApi } from '@/api/users'
import type { User } from '@/types/api'

const { Text } = Typography
const { Option } = Select

export default function UsersPage() {
  const {
    users, total, page, pageSize, loading, statusFilter,
    selectedRowKeys, fetchUsers, toggleUserStatus, updateUserRole,
    removeUser, batchAction, setPage, setPageSize, setKeyword,
    setStatusFilter, setSelectedRowKeys,
  } = useUsersStore()
  const navigate = useNavigate()

  const [searchText, setSearchText] = useState('')
  const [exporting, setExporting] = useState(false)

  useEffect(() => {
    fetchUsers()
  }, [fetchUsers])

  // 搜索
  const handleSearch = useCallback(() => {
    setKeyword(searchText)
  }, [searchText, setKeyword])

  // 导出用户
  const handleExport = async () => {
    setExporting(true)
    try {
      const res = await exportUsersApi({ page: 1, pageSize: 999999 })
      const blob = res.data as unknown as Blob
      const url = window.URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `users_export_${new Date().toISOString().slice(0, 10)}.xlsx`
      a.click()
      window.URL.revokeObjectURL(url)
      message.success('导出成功')
    } catch {
      message.error('导出失败')
    } finally {
      setExporting(false)
    }
  }

  // 批量操作菜单
  const batchMenuItems: MenuProps['items'] = [
    {
      key: 'enable',
      label: '批量启用',
      icon: <CheckCircleOutlined />,
      onClick: () => batchAction('enable', selectedRowKeys),
    },
    {
      key: 'disable',
      label: '批量禁用',
      icon: <StopOutlined />,
      onClick: () => batchAction('disable', selectedRowKeys),
    },
    { type: 'divider' },
    {
      key: 'delete',
      label: '批量删除',
      icon: <DeleteOutlined />,
      danger: true,
      onClick: () => {
        Modal.confirm({
          title: '确认批量删除',
          icon: <ExclamationCircleOutlined />,
          content: `确定要删除 ${selectedRowKeys.length} 个用户吗？此操作不可恢复。`,
          okText: '确认删除',
          okType: 'danger',
          cancelText: '取消',
          onOk: () => batchAction('delete', selectedRowKeys),
        })
      },
    },
  ]

  // 格式化存储大小
  const formatBytes = (bytes: number) => {
    if (!bytes) return '-'
    const k = 1024
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB']
    const i = Math.floor(Math.log(bytes) / Math.log(k))
    return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }

  const columns: ColumnsType<User> = [
    {
      title: '用户',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (name: string, record: User) => (
        <Space>
          <Avatar src={record.imagePath} icon={<UserOutlined />} size="small" />
          <div>
            <Text strong style={{ display: 'block' }}>{name || record.account}</Text>
            <Text type="secondary" style={{ fontSize: 12 }}>{record.email || record.phoneNumber || '-'}</Text>
          </div>
        </Space>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Tag color={status === 'ACTIVE' ? 'green' : status === 'DISABLED' ? 'red' : 'orange'}>
          {status === 'ACTIVE' ? '正常' : status === 'DISABLED' ? '已禁用' : '已暂停'}
        </Tag>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 100,
      render: (role: string, record: User) => (
        <Select
          value={role}
          size="small"
          style={{ width: 100 }}
          onChange={(value) => updateUserRole(record.userId, value)}
        >
          <Option value="USER">普通用户</Option>
          <Option value="VIP">VIP</Option>
          <Option value="ADMIN">管理员</Option>
        </Select>
      ),
    },
    {
      title: '存储用量',
      key: 'storage',
      width: 140,
      render: (_: unknown, record: User) => (
        <div>
          <Text style={{ fontSize: 13 }}>{formatBytes(record.storageUsed)}</Text>
          <Text type="secondary" style={{ fontSize: 11, marginLeft: 4 }}>
            / {formatBytes(record.storageLimit)}
          </Text>
        </div>
      ),
    },
    {
      title: '文件数',
      dataIndex: 'fileCount',
      key: 'fileCount',
      width: 80,
      render: (count: number) => count?.toLocaleString() || '-',
    },
    {
      title: '验证',
      key: 'verified',
      width: 80,
      render: (_: unknown, record: User) => (
        <Space size={4}>
          {record.emailVerified && <Tooltip title="邮箱已验证"><MailOutlined style={{ color: '#52c41a' }} /></Tooltip>}
          {record.phoneVerified && <Tooltip title="手机已验证"><PhoneOutlined style={{ color: '#52c41a' }} /></Tooltip>}
          {record.twoFactorEnabled && <Tooltip title="已开启2FA"><SafetyOutlined /></Tooltip>}
        </Space>
      ),
    },
    {
      title: '注册时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      fixed: 'right',
      render: (_: unknown, record: User) => (
        <Space size="small">
          <Tooltip title="查看详情">
            <Button type="text" size="small" icon={<EyeOutlined />}
              onClick={() => navigate(`/users/${record.userId}`)} />
          </Tooltip>
          <Tooltip title={record.status === 'ACTIVE' ? '禁用' : '启用'}>
            <Switch
              size="small"
              checked={record.status === 'ACTIVE'}
              onChange={(checked) =>
                toggleUserStatus(record.userId, checked ? 'ACTIVE' : 'DISABLED')
              }
            />
          </Tooltip>
          <Popconfirm
            title="确定删除此用户?"
            description="此操作不可恢复"
            onConfirm={() => removeUser(record.userId)}
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
        title="用户管理"
        subtitle={`共 ${total} 个用户`}
        icon={<TeamOutlined />}
        actions={
          <Space wrap>
            <Button icon={<ReloadOutlined />} onClick={() => fetchUsers()}>
              刷新
            </Button>
            <Button icon={<ExportOutlined />} onClick={handleExport} loading={exporting}>
              导出
            </Button>
            {selectedRowKeys.length > 0 && (
              <Dropdown menu={{ items: batchMenuItems }}>
                <Button>
                  批量操作 ({selectedRowKeys.length}) <MoreOutlined />
                </Button>
              </Dropdown>
            )}
          </Space>
        }
      />

      {/* 筛选栏 */}
      <Space wrap style={{ marginBottom: 16 }}>
        <Input
          placeholder="搜索用户名/邮箱/手机号"
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
          placeholder="用户状态"
          value={statusFilter}
          onChange={(val) => setStatusFilter(val)}
          allowClear
          style={{ width: 120 }}
        >
          <Option value="ACTIVE">正常</Option>
          <Option value="DISABLED">已禁用</Option>
          <Option value="SUSPENDED">已暂停</Option>
        </Select>
      </Space>

      <Table
        columns={columns}
        dataSource={users}
        rowKey="userId"
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
        locale={{ emptyText: <Empty description="暂无用户数据" /> }}
      />
    </div>
  )
}