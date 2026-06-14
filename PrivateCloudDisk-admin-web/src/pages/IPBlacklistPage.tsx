// ============================================================
// IP 黑名单管理页面
// ============================================================
import { useEffect, useState, useCallback } from 'react'
import { Table, Button, Space, Tag, Input, Modal, Popconfirm, Empty, message } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { GlobalOutlined, PlusOutlined, ReloadOutlined, DeleteOutlined } from '@ant-design/icons'
import { getIPBlacklistApi, addIPToBlacklistApi, removeIPFromBlacklistApi } from '@/api/security'
import PageHeader from '@/components/PageHeader'

interface IPEntry {
  ip: string
  reason: string
  addedAt: string
}

export default function IPBlacklistPage() {
  const [list, setList] = useState<IPEntry[]>([])
  const [loading, setLoading] = useState(false)
  const [modalOpen, setModalOpen] = useState(false)
  const [newIP, setNewIP] = useState('')
  const [newReason, setNewReason] = useState('')
  const [adding, setAdding] = useState(false)

  const fetchList = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getIPBlacklistApi()
      if (res.data.code === 200) {
        setList(res.data.data || [])
      }
    } catch {
      // ignore
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchList()
  }, [fetchList])

  const handleAdd = async () => {
    if (!newIP.trim()) {
      message.warning('请输入 IP 地址')
      return
    }
    setAdding(true)
    try {
      const res = await addIPToBlacklistApi(newIP.trim(), newReason.trim() || '手动添加')
      if (res.data.code === 200) {
        message.success('已添加到黑名单')
        setModalOpen(false)
        setNewIP('')
        setNewReason('')
        fetchList()
      } else {
        message.error(res.data.message || '添加失败')
      }
    } catch {
      message.error('操作失败')
    } finally {
      setAdding(false)
    }
  }

  const handleRemove = async (ip: string) => {
    try {
      const res = await removeIPFromBlacklistApi(ip)
      if (res.data.code === 200) {
        message.success('已移除')
        fetchList()
      }
    } catch {
      message.error('移除失败')
    }
  }

  const columns: ColumnsType<IPEntry> = [
    {
      title: 'IP 地址',
      dataIndex: 'ip',
      key: 'ip',
      width: 200,
      render: (ip: string) => <Tag color="red">{ip}</Tag>,
    },
    { title: '封禁原因', dataIndex: 'reason', key: 'reason', ellipsis: true },
    {
      title: '添加时间',
      dataIndex: 'addedAt',
      key: 'addedAt',
      width: 180,
      render: (t: string) => new Date(t).toLocaleString('zh-CN'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: IPEntry) => (
        <Popconfirm
          title="确定移除此 IP?"
          onConfirm={() => handleRemove(record.ip)}
          okText="移除"
          cancelText="取消"
        >
          <Button type="link" danger size="small" icon={<DeleteOutlined />}>移除</Button>
        </Popconfirm>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="IP 黑名单"
        subtitle="管理被封禁的 IP 地址"
        icon={<GlobalOutlined />}
        actions={
          <Space>
            <Button icon={<ReloadOutlined />} onClick={fetchList}>刷新</Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={() => setModalOpen(true)}>
              添加 IP
            </Button>
          </Space>
        }
      />

      <Table
        columns={columns}
        dataSource={list}
        rowKey="ip"
        loading={loading}
        pagination={false}
        locale={{ emptyText: <Empty description="黑名单为空" /> }}
      />

      <Modal
        title="添加 IP 到黑名单"
        open={modalOpen}
        onOk={handleAdd}
        onCancel={() => setModalOpen(false)}
        confirmLoading={adding}
        okText="添加"
        cancelText="取消"
      >
        <Space direction="vertical" style={{ width: '100%' }} size="middle">
          <div>
            <div style={{ marginBottom: 4 }}>IP 地址</div>
            <Input
              placeholder="例如: 192.168.1.100"
              value={newIP}
              onChange={(e) => setNewIP(e.target.value)}
            />
          </div>
          <div>
            <div style={{ marginBottom: 4 }}>封禁原因</div>
            <Input
              placeholder="例如: 暴力破解攻击"
              value={newReason}
              onChange={(e) => setNewReason(e.target.value)}
            />
          </div>
        </Space>
      </Modal>
    </div>
  )
}