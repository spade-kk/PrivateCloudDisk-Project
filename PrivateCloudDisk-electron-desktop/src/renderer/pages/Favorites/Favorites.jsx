/**
 * pages/Favorites/Favorites.jsx - 收藏夹页面
 *
 * 后端: Spring Boot FileStarController
 * 收藏列表返回: FileStarEntity { star_id, file_id, starred_at }
 * 需要额外调用 getFileDetail 获取文件名/大小
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { Table, Button, message, Space, Popconfirm, Empty, Tag } from 'antd'
import { StarFilled, DeleteOutlined, FileOutlined, DownloadOutlined } from '@ant-design/icons'
import { getStarList, removeStar } from '@/api/star'
import { getFileDetail } from '@/api/file'
import { requestOperationToken, downloadFile } from '@/api/download'
import { formatFileSize, formatTime } from '@/utils/helper'
import './Favorites.css'

export default function FavoritesPage() {
  const navigate = useNavigate()
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [downloadingId, setDownloadingId] = useState(null)

  const loadData = useCallback(async (p) => {
    setLoading(true)
    const currentPage = p || page
    try {
      const res = await getStarList({ page: currentPage, pageSize })
      // 后端返回: { code: 200, data: [FileStarEntity, ...] }
      const starList = Array.isArray(res.data) ? res.data : []
      setTotal(starList.length)

      // 并行获取所有文件的详情
      const enriched = await Promise.all(
        starList.map(async (star) => {
          try {
            const fileRes = await getFileDetail(star.file_id)
            const fileData = fileRes.data
            return {
              key: star.star_id,
              star_id: star.star_id,
              file_id: star.file_id,
              name: fileData.file_name || fileData.name || '未知文件',
              size: fileData.file_size || fileData.size || 0,
              file_type: fileData.file_type || fileData.type,
              starred_at: star.starred_at,
              uploaded_time: fileData.uploaded_time || fileData.updated_at
            }
          } catch {
            // 文件可能已删除, 仍然显示但标记为失效
            return {
              key: star.star_id,
              star_id: star.star_id,
              file_id: star.file_id,
              name: '[文件已不存在]',
              size: 0,
              file_type: '',
              starred_at: star.starred_at,
              uploaded_time: null,
              deleted: true
            }
          }
        })
      )
      setData(enriched)
      setPage(currentPage)
    } catch (e) {
      message.error('加载收藏列表失败')
    } finally {
      setLoading(false)
    }
  }, [page, pageSize])

  useEffect(() => {
    loadData(1)
  }, [])

  // 取消收藏
  const handleRemoveStar = async (record) => {
    try {
      await removeStar(record.file_id)
      message.success('已取消收藏')
      loadData(page)
    } catch (e) {
      message.error(e.message || '操作失败')
    }
  }

  // 下载
  const handleDownload = async (record) => {
    if (record.deleted) return
    setDownloadingId(record.star_id)
    try {
      const opRes = await requestOperationToken({
        file_id: record.file_id,
        operation_type: 'download'
      })
      const opToken = opRes.data?.operation_token || opRes.data
      await downloadFile(record.file_id, opToken, record.name, () => {})
      message.success('下载完成')
    } catch (e) {
      message.error(e.message || '下载失败')
    } finally {
      setDownloadingId(null)
    }
  }

  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      ellipsis: true,
      render: (text, record) => (
        <div
          className="favorite-name-cell"
          style={{ cursor: record.deleted ? 'default' : 'pointer', opacity: record.deleted ? 0.5 : 1 }}
          onClick={() => !record.deleted && navigate(`/file/${record.file_id}`)}
        >
          <StarFilled className="star-icon-small" />
          <FileOutlined className="file-icon" />
          <span>{text}</span>
        </div>
      )
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 120,
      render: (size) => size ? formatFileSize(size) : '-'
    },
    {
      title: '收藏时间',
      dataIndex: 'starred_at',
      key: 'starred_at',
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
            icon={<DownloadOutlined />}
            disabled={record.deleted}
            loading={downloadingId === record.star_id}
            onClick={() => handleDownload(record)}
          >
            下载
          </Button>
          <Popconfirm
            title="确定取消收藏？"
            onConfirm={() => handleRemoveStar(record)}
            okText="确定"
            cancelText="取消"
          >
            <Button type="link" size="small" danger icon={<DeleteOutlined />}>
              取消收藏
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ]

  return (
    <div className="favorites-page">
      <div className="favorites-header">
        <h2><StarFilled style={{ color: '#faad14' }} /> 收藏夹</h2>
      </div>

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
        locale={{ emptyText: <Empty description="收藏夹为空" /> }}
      />
    </div>
  )
}