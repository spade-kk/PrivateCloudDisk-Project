/**
 * components/SearchBar.jsx - 搜索栏组件 (Header 中)
 *
 * 支持: 关键字搜索 + 回车跳转到搜索页
 */
import React, { useState } from 'react'
import { Input } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import './SearchBar.css'

export default function SearchBar() {
  const navigate = useNavigate()
  const [keyword, setKeyword] = useState('')

  const handleSearch = () => {
    if (keyword.trim()) {
      navigate(`/search?keyword=${encodeURIComponent(keyword.trim())}`)
    }
  }

  return (
    <Input
      className="search-bar-input"
      placeholder="搜索文件..."
      prefix={<SearchOutlined />}
      value={keyword}
      onChange={e => setKeyword(e.target.value)}
      onPressEnter={handleSearch}
      allowClear
    />
  )
}