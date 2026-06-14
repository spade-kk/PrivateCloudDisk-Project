// ============================================================
// 在线用户页面
// ============================================================
import { useEffect } from 'react'
import { Table, Button, Tag, Popconfirm, Empty, message } from 'antd'
import { ReloadOutlined, LogoutOutlined, EyeOutlined } from '@ant-design/icons'
import { useSystemStore } from '@/stores/systemStore'
import { kickOnlineUserApi } from '@/api/system'
import PageHeader from '@/components/PageHeader'
import type { OnlineUser } from '@/types/api'
import type { ColumnsType } from 'antd/es/table'

export default function OnlineUsersPage() {
  const { onlineUsers, fetchOnlineUsers, loading } = useSystemStore()

  useEffect(() => {
    fetchOnlineUsers()
  }, [fetchOnlineUsers])

  const handleKick = async (sessionId: string) => {
    try {
      const res = await kickOnlineUserApi(sessionId)
      if (res.data.code === 200) {
        message.success('已踢出该用户')
        fetchOnlineUsers()
      } else {
        message.error(res.data.message || '操作失败')
      }
    } catch {
      message.error('操作失败')
    }
  }

  const columns: ColumnsType<OnlineUser> = [
    { title: '用户名', dataIndex: 'name', key: 'name', width: 120 },
    { title: 'IP 地址', dataIndex: 'ip', key: 'ip', width: 140 },
    { title: '设备', dataIndex: 'device', key: 'device', width: 120 },
    { title: '浏览器', dataIndex: 'browser', key: 'browser', width: 140, ellipsis: true },
    {
      title: '登录时间',
      dataIndex: 'loginAt',
      key: 'loginAt',
      width: 170,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '最后活动',
      dataIndex: 'lastActivity',
      key: 'lastActivity',
      width: 170,
      render: (text: string) => new Date(text).toLocaleString('zh-CN'),
    },
    {
      title: '状态',
      key: 'status',
      width: 80,
      render: () => <Tag color="green">在线</Tag>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_: unknown, record: OnlineUser) => (
        <Popconfirm
          title="确定踢出该用户?"
          description="用户将被强制下线"
          onConfirm={() => handleKick(record.sessionId)}
          okText="确定"
          cancelText="取消"
        >
          <Button type="link" danger size="small" icon={<LogoutOutlined />}>
            踢出
          </Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="在线用户"
        subtitle={`当前 ${onlineUsers.length} 人在线`}
        icon={<EyeOutlined />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={fetchOnlineUsers}>
            刷新
          </Button>
        }
      />
      <Table
        columns={columns}
        dataSource={onlineUsers}
        rowKey="sessionId"
        loading={loading.onlineUsers}
        pagination={false}
        locale={{ emptyText: <Empty description="暂无在线用户" /> }}
      />
    </div>
  )
}