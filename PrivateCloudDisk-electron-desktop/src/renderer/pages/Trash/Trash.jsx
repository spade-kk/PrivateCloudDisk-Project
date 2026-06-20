/**
 * pages/Trash/Trash.jsx - 回收站页面
 *
 * 后端: Spring Boot TrashController
 * 返回: TrashTargetVO { trash_id, target_id, target_name, target_type, target_size, file_type, original_node_id, deleted_at, expires_at }
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Table, Button, Modal, message, Space, Popconfirm, Empty, Tag, Alert } from 'antd'
import { DeleteOutlined, UndoOutlined, FolderOutlined, FileOutlined } from '@ant-design/icons'
import { getTrashList, getTrashStats, restoreFromTrash, permanentDelete } from '@/api/trash'
import { formatFileSize, formatTime } from '@/utils/helper'
import './Trash.css'

export default function TrashPage() {
  const navigate = useNavigate()
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [trashCount, setTrashCount] = useState(0)
  const [trashSize, setTrashSize] = useState(0)

  const loadData = useCallback(async (p) => {
    setLoading(true)
    const currentPage = p || page
    try {
      const [listRes, statsRes] = await Promise.all([
        getTrashList({ page: currentPage, size: pageSize }),
        getTrashStats()
      ])
      // 后端返回: { code: 200, data: [TrashTargetVO, ...] }  (非分页, 直接返回列表)
      const listData = Array.isArray(listRes.data) ? listRes.data : []
      const items = listData.map(item => ({
        key: item.trash_id,
        id: item.trash_id,
        target_id: item.target_id,
        name: item.target_name,
        size: item.target_size || 0,
        isFile: item.target_type === 'file',
        file_type: item.file_type,
        deleted_at: item.deleted_at,
        expires_at: item.expires_at
      }))
      setData(items)
      setTotal(listData.length)
      setPage(currentPage)

      // 统计信息
      setTrashCount(statsRes.data || 0)
      setTrashSize(0) // 后端 count 接口返回 Integer count
    } catch (e) {
      message.error('加载回收站失败')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    loadData(1)
  }, [])

  // 恢复
  const handleRestore = async (record) => {
    try {
      await restoreFromTrash(record.id)
      message.success('已恢复')
      loadData(page)
    } catch (e) {
      message.error(e.message || '恢复失败')
    }
  }

  // 彻底删除
  const handlePermanentDelete = async (record) => {
    try {
      await permanentDelete(record.id)
      message.success('已彻底删除')
      loadData(page)
    } catch (e) {
      message.error(e.message || '删除失败')
    }
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (text, record) => (
        <div className="trash-name-cell">
          {record.isFile ? <FileOutlined className="file-icon" /> : <FolderOutlined className="folder-icon" />}
          <span>{text}</span>
        </div>
      )
    },
    {
      title: '类型',
      dataIndex: 'isFile',
      key: 'type',
      width: 100,
      render: (isFile, record) => (
        <Tag color={isFile ? 'blue' : 'orange'}>{isFile ? '文件' : '文件夹'}</Tag>
      )
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 120,
      render: (size, record) => record.isFile ? formatFileSize(size) : '-'
    },
    {
      title: '删除时间',
      dataIndex: 'deleted_at',
      key: 'deleted_at',
      width: 180,
      render: (t) => t ? formatTime(t) : '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 160,
      render: (_, record) => (
        <Space>
          <Button
            type="link"
            size="small"
            icon={<UndoOutlined />}
            onClick={() => handleRestore(record)}
          >
            恢复
          </Button>
          <Popconfirm
            title="确定彻底删除？"
            description="删除后不可恢复"
            onConfirm={() => handlePermanentDelete(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              彻底删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="trash-page">
      <div className="trash-header">
        <h2>回收站</h2>
        <div className="trash-stats">
          <span>共 {trashCount} 项</span>
        </div>
      </div>

      <Alert
        message="回收站中的文件将在 30 天后自动清理"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />

      <Table
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="key"
        size="middle"
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50'],
          showTotal: (t) => `共 ${t} 项`,
          onChange: (p, ps) => {
            setPage(p)
            setPageSize(ps)
            loadData(p)
          }
        }}
        locale={{ emptyText: <Empty description="回收站为空" /> }}
      />
    </div>
  )
}