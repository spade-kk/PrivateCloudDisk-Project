/**
 * components/FileList.jsx - 文件列表组件
 *
 * 功能: 文件/文件夹展示 / 右键菜单 / 重命名 / 删除 / 移动 / 新建文件夹 / 下载 / 收藏
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { Table, Button, Breadcrumb, Dropdown, Modal, Input, Select, Tree, message, Space, Tooltip, Empty } from 'antd'
import {
  HomeOutlined, FolderOutlined, FileOutlined, DownloadOutlined, DeleteOutlined,
  EditOutlined, StarOutlined, StarFilled, MoreOutlined, UploadOutlined,
  FolderAddOutlined, ScissorOutlined, ReloadOutlined, AppstoreOutlined, UnorderedListOutlined
} from '@ant-design/icons'
import { useAppStore } from '@/store/appStore'
import { getChildNodesPaged, createFolder, renameNode, moveNode, deleteNode, getFolderFiles } from '@/api/node'
import { getFileDetail, renameFile, moveFile, deleteFile } from '@/api/file'
import { moveFileToTrash, moveFolderToTrash } from '@/api/trash'
import { addStar, removeStar, getStarStatus } from '@/api/star'
import { requestOperationToken, downloadFile, downloadFolder } from '@/api/download'
import { formatFileSize, formatTime, getFileCategory, getFileExtension } from '@/utils/helper'
import UploadDialog from './UploadDialog'
import './FileList.css'

export default function FileList() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const {
    currentNodeId, currentNodeName, directoryStack,
    pushDirectory, popDirectory, navigateToDirectory,
    setQuota, quota
  } = useAppStore()

  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(20)
  const [total, setTotal] = useState(0)
  const [sortBy, setSortBy] = useState('name')
  const [sortOrder, setSortOrder] = useState('asc')
  const [selectedRowKeys, setSelectedRowKeys] = useState([])
  const [viewMode, setViewMode] = useState('list')

  // 弹窗状态
  const [renameVisible, setRenameVisible] = useState(false)
  const [renameRecord, setRenameRecord] = useState(null)
  const [newName, setNewName] = useState('')
  const [renameLoading, setRenameLoading] = useState(false)

  const [moveVisible, setMoveVisible] = useState(false)
  const [moveRecord, setMoveRecord] = useState(null)
  const [targetFolderId, setTargetFolderId] = useState('root')
  const [moveLoading, setMoveLoading] = useState(false)

  const [createFolderVisible, setCreateFolderVisible] = useState(false)
  const [folderName, setFolderName] = useState('')
  const [createFolderLoading, setCreateFolderLoading] = useState(false)

  const [uploadVisible, setUploadVisible] = useState(false)

  const [starStatus, setStarStatus] = useState({})
  const [downloadingId, setDownloadingId] = useState(null)
  const [downloadProgress, setDownloadProgress] = useState(0)
  const [downloadingFolderId, setDownloadingFolderId] = useState(null)
  const [folderDownloadProgress, setFolderDownloadProgress] = useState(0)

  // 加载文件列表
  const loadData = useCallback(async (p) => {
    setLoading(true)
    const currentPage = p || page
    try {
      const params = { page: currentPage, pageSize, sortBy, sortOrder }
      const res = await getChildNodesPaged(currentNodeId, params)
      // 后端返回: { code: 200, data: { items: [...], total: N, page: N, pageSize: N, totalPages: N } }
      const pageData = res.data
      const items = (pageData?.items || []).map(item => ({
        key: item.node_id,
        id: item.node_id,
        name: item.node_name,
        size: item.node_size || 0,
        isFile: item.node_type === 'FILE',
        created_at: item.created_at,
        updated_at: item.updated_at
      }))
      setData(items)
      setTotal(pageData?.total || 0)
      setPage(pageData?.page || currentPage)
    } catch (e) {
      message.error('加载文件列表失败')
    } finally {
      setLoading(false)
    }
  }, [currentNodeId, pageSize, sortBy, sortOrder, page])

  // 加载收藏状态
  const loadStarStatus = useCallback(async (items) => {
    const statusMap = {}
    await Promise.all(items.filter(i => i.isFile).map(async (item) => {
      try {
        const res = await getStarStatus(item.id)
        statusMap[item.id] = res.data === true
      } catch { /* ignore */ }
    }))
    setStarStatus(prev => ({ ...prev, ...statusMap }))
  }, [])

  useEffect(() => {
    loadData(1)
  }, [currentNodeId, sortBy, sortOrder])

  useEffect(() => {
    if (data.length > 0) loadStarStatus(data)
  }, [data])

  // 进入文件夹
  const handleEnterFolder = (record) => {
    if (record.isFile) {
      navigate(`/file/${record.id}`)
      return
    }
    pushDirectory({ id: record.id, name: record.name })
  }

  // 面包屑导航
  const handleBreadcrumb = (index) => {
    navigateToDirectory(index)
  }

  // 重命名
  const handleRenameClick = (record) => {
    setRenameRecord(record)
    setNewName(record.name)
    setRenameVisible(true)
  }

  const handleRenameOk = async () => {
    if (!newName.trim() || newName === renameRecord.name) {
      setRenameVisible(false)
      return
    }
    setRenameLoading(true)
    try {
      if (renameRecord.isFile) {
        await renameFile(renameRecord.id, { file_new_name: newName.trim() })
      } else {
        await renameNode(renameRecord.id, { new_node_name: newName.trim() })
      }
      message.success('重命名成功')
      setRenameVisible(false)
      loadData(page)
    } catch (e) {
      message.error(e.message || '重命名失败')
    } finally {
      setRenameLoading(false)
    }
  }

  // 删除 (移入回收站)
  const handleDelete = (record) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要将 "${record.name}" 移入回收站吗？`,
      okText: '确定',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          if (record.isFile) {
            await moveFileToTrash(record.id)
          } else {
            await moveFolderToTrash(record.id)
          }
          message.success('已移入回收站')
          loadData(page)
        } catch (e) {
          message.error(e.message || '删除失败')
        }
      }
    })
  }

  // 移动
  const handleMoveClick = (record) => {
    setMoveRecord(record)
    setTargetFolderId('root')
    setMoveVisible(true)
  }

  const handleMoveOk = async () => {
    if (!moveRecord || !targetFolderId) return
    setMoveLoading(true)
    try {
      if (moveRecord.isFile) {
        await moveFile(moveRecord.id, { target_node_id: targetFolderId })
      } else {
        await moveNode(moveRecord.id, { target_position: targetFolderId })
      }
      message.success('移动成功')
      setMoveVisible(false)
      loadData(page)
    } catch (e) {
      message.error(e.message || '移动失败')
    } finally {
      setMoveLoading(false)
    }
  }

  // 新建文件夹
  const handleCreateFolder = async () => {
    if (!folderName.trim()) return
    setCreateFolderLoading(true)
    try {
      await createFolder({ folder_name: folderName.trim(), node_id: currentNodeId })
      message.success('文件夹创建成功')
      setCreateFolderVisible(false)
      setFolderName('')
      loadData(page)
    } catch (e) {
      message.error(e.message || '创建失败')
    } finally {
      setCreateFolderLoading(false)
    }
  }

  // 下载
  const handleDownload = async (record) => {
    if (!record.isFile) return
    setDownloadingId(record.id)
    setDownloadProgress(0)
    try {
      const opRes = await requestOperationToken({
        file_id: record.id,
        operation_type: 'download'
      })
      const opToken = opRes.data?.operation_token || opRes.data
      const savePath = await downloadFile(record.id, opToken, record.name, (pct) => {
        setDownloadProgress(pct)
      })
      message.success(`下载完成: ${savePath}`)
      try { window.electronAPI?.showItemInFolder?.(savePath) } catch { /* ignore */ }
    } catch (e) {
      message.error(e.message || '下载失败')
    } finally {
      setDownloadingId(null)
      setDownloadProgress(0)
    }
  }

  // 文件夹下载
  const handleDownloadFolder = async (record) => {
    if (record.isFile) return
    setDownloadingFolderId(record.id)
    setFolderDownloadProgress(0)
    try {
      // 1. 获取文件夹下所有文件信息
      const filesRes = await getFolderFiles(record.id)
      const files = filesRes.data || []
      if (!files || files.length === 0) {
        message.warning('该文件夹为空，无需下载')
        return
      }
      setFolderDownloadProgress(10)
      message.loading({ content: `正在打包 ${files.length} 个文件...`, key: 'folder-download', duration: 0 })

      // 2. 调用文件夹下载 API
      const savePath = await downloadFolder(record.id, record.name, files, (pct) => {
        setFolderDownloadProgress(10 + Math.round(pct * 0.9))
      })
      
      message.destroy('folder-download')
      message.success(`文件夹下载完成: ${savePath || record.name}`)
      try { window.electronAPI?.showItemInFolder?.(savePath) } catch { /* ignore */ }
    } catch (e) {
      message.destroy('folder-download')
      message.error(e.message || '文件夹下载失败')
    } finally {
      setDownloadingFolderId(null)
      setFolderDownloadProgress(0)
    }
  }

  // 收藏/取消收藏
  const handleToggleStar = async (record) => {
    if (!record.isFile) return
    const isStarred = starStatus[record.id]
    try {
      if (isStarred) {
        await removeStar(record.id)
        setStarStatus(prev => ({ ...prev, [record.id]: false }))
        message.success('已取消收藏')
      } else {
        await addStar(record.id)
        setStarStatus(prev => ({ ...prev, [record.id]: true }))
        message.success('已收藏')
      }
    } catch (e) {
      message.error(e.message || '操作失败')
    }
  }

  // 右键菜单项
  const getContextMenuItems = (record) => {
    const isStarred = starStatus[record.id]
    const items = [
      { key: 'open', label: record.isFile ? '打开' : '进入', icon: record.isFile ? <FileOutlined /> : <FolderOutlined />, onClick: () => handleEnterFolder(record) }
    ]
    if (record.isFile) {
      items.push({ key: 'download', label: '下载', icon: <DownloadOutlined />, onClick: () => handleDownload(record) })
    } else {
      items.push({ key: 'download-folder', label: '下载文件夹', icon: <DownloadOutlined />, onClick: () => handleDownloadFolder(record) })
    }
    items.push(
      { type: 'divider' },
      { key: 'rename', label: '重命名', icon: <EditOutlined />, onClick: () => handleRenameClick(record) },
      { key: 'move', label: '移动', icon: <ScissorOutlined />, onClick: () => handleMoveClick(record) }
    )
    if (record.isFile) {
      items.push({ key: 'star', label: isStarred ? '取消收藏' : '收藏', icon: isStarred ? <StarFilled /> : <StarOutlined />, onClick: () => handleToggleStar(record) })
    }
    items.push(
      { type: 'divider' },
      { key: 'delete', label: '删除', icon: <DeleteOutlined />, danger: true, onClick: () => handleDelete(record) }
    )
    return items
  }

  // 表格列定义
  const columns = [
    {
      title: '名称',
      dataIndex: 'name',
      key: 'name',
      sorter: true,
      ellipsis: true,
      render: (text, record) => (
        <div
          className="file-name-cell"
          onDoubleClick={() => handleEnterFolder(record)}
          style={{ cursor: 'pointer' }}
        >
          {record.isFile ? <FileOutlined className="file-icon" /> : <FolderOutlined className="folder-icon" />}
          <span className="file-name">{text}</span>
          {starStatus[record.id] && <StarFilled className="star-icon-small" />}
        </div>
      )
    },
    {
      title: '大小',
      dataIndex: 'size',
      key: 'size',
      width: 120,
      sorter: true,
      render: (size, record) => record.isFile ? formatFileSize(size) : '-'
    },
    {
      title: '修改时间',
      dataIndex: 'updated_at',
      key: 'updated_at',
      width: 180,
      sorter: true,
      render: (t) => t ? formatTime(t) : '-'
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      render: (_, record) => (
        <Space>
          {record.isFile ? (
            <Tooltip title="下载">
              <Button
                type="text"
                size="small"
                icon={<DownloadOutlined />}
                loading={downloadingId === record.id}
                onClick={() => handleDownload(record)}
              />
            </Tooltip>
          ) : (
            <Tooltip title="下载文件夹">
              <Button
                type="text"
                size="small"
                icon={<DownloadOutlined />}
                loading={downloadingFolderId === record.id}
                onClick={() => handleDownloadFolder(record)}
              />
            </Tooltip>
          )}
          <Dropdown menu={{ items: getContextMenuItems(record) }} trigger={['click']}>
            <Button type="text" size="small" icon={<MoreOutlined />} />
          </Dropdown>
        </Space>
      )
    }
  ]

  return (
    <div className="file-list-container">
      {/* 面包屑导航 */}
      <div className="file-list-header">
        <Breadcrumb className="directory-breadcrumb">
          {directoryStack.map((dir, index) => (
            <Breadcrumb.Item key={dir.id}>
              {index === 0 ? (
                <a onClick={() => handleBreadcrumb(index)}><HomeOutlined /> {dir.name}</a>
              ) : index < directoryStack.length - 1 ? (
                <a onClick={() => handleBreadcrumb(index)}>{dir.name}</a>
              ) : (
                <span>{dir.name}</span>
              )}
            </Breadcrumb.Item>
          ))}
        </Breadcrumb>

        {/* 工具栏 */}
        <div className="file-list-toolbar">
          <Space>
            <Button icon={<UploadOutlined />} onClick={() => setUploadVisible(true)}>上传</Button>
            <Button icon={<FolderAddOutlined />} onClick={() => { setFolderName(''); setCreateFolderVisible(true) }}>新建文件夹</Button>
            <Button icon={<ReloadOutlined />} onClick={() => loadData(page)}>刷新</Button>
            <Button
              icon={viewMode === 'list' ? <AppstoreOutlined /> : <UnorderedListOutlined />}
              onClick={() => setViewMode(viewMode === 'list' ? 'grid' : 'list')}
            />
          </Space>
        </div>
      </div>

      {/* 文件表格 */}
      <Table
        className="file-table"
        columns={columns}
        dataSource={data}
        loading={loading}
        rowKey="key"
        size="middle"
        rowSelection={{
          selectedRowKeys,
          onChange: setSelectedRowKeys
        }}
        pagination={{
          current: page,
          pageSize,
          total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '20', '50', '100'],
          showTotal: (t) => `共 ${t} 项`,
          onChange: (p, ps) => {
            setPage(p)
            setPageSize(ps)
            loadData(p)
          }
        }}
        onChange={(pagination, filters, sorter) => {
          if (sorter.field) {
            setSortBy(sorter.field === 'updated_at' ? 'time' : sorter.field === 'size' ? 'size' : 'name')
            setSortOrder(sorter.order === 'ascend' ? 'asc' : 'desc')
          }
        }}
        locale={{ emptyText: <Empty description="此文件夹为空" /> }}
        onRow={(record) => ({
          onDoubleClick: () => handleEnterFolder(record),
          onContextMenu: (e) => {
            e.preventDefault()
            // context menu handled by Dropdown
          }
        })}
      />

      {/* 重命名弹窗 */}
      <Modal
        title="重命名"
        open={renameVisible}
        onOk={handleRenameOk}
        onCancel={() => setRenameVisible(false)}
        confirmLoading={renameLoading}
        okText="确定"
        cancelText="取消"
      >
        <Input
          value={newName}
          onChange={(e) => setNewName(e.target.value)}
          onPressEnter={handleRenameOk}
          autoFocus
        />
      </Modal>

      {/* 移动弹窗 */}
      <Modal
        title={`移动: ${moveRecord?.name || ''}`}
        open={moveVisible}
        onOk={handleMoveOk}
        onCancel={() => setMoveVisible(false)}
        confirmLoading={moveLoading}
        okText="移动"
      >
        <p>选择目标文件夹:</p>
        <Select
          value={targetFolderId}
          onChange={setTargetFolderId}
          style={{ width: '100%' }}
          options={[
            { value: 'root', label: '根目录' }
          ]}
        />
      </Modal>

      {/* 新建文件夹弹窗 */}
      <Modal
        title="新建文件夹"
        open={createFolderVisible}
        onOk={handleCreateFolder}
        onCancel={() => setCreateFolderVisible(false)}
        confirmLoading={createFolderLoading}
        okText="创建"
      >
        <Input
          placeholder="输入文件夹名称"
          value={folderName}
          onChange={(e) => setFolderName(e.target.value)}
          onPressEnter={handleCreateFolder}
          autoFocus
        />
      </Modal>

      {/* 上传弹窗 */}
      <UploadDialog
        visible={uploadVisible}
        onClose={() => { setUploadVisible(false); loadData(page) }}
      />
    </div>
  )
}