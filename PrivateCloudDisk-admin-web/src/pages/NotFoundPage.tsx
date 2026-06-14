// ============================================================
// 404 未找到页面
// ============================================================
import { Button, Result, Space } from 'antd'
import { useNavigate } from 'react-router-dom'
import { HomeOutlined, ArrowLeftOutlined } from '@ant-design/icons'

export default function NotFoundPage() {
  const navigate = useNavigate()

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: '#f5f5f5',
      }}
    >
      <Result
        status="404"
        title="404"
        subTitle="抱歉，您访问的页面不存在。"
        extra={
          <Space>
            <Button
              type="primary"
              icon={<HomeOutlined />}
              onClick={() => navigate('/dashboard', { replace: true })}
            >
              返回仪表盘
            </Button>
            <Button
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate(-1)}
            >
              返回上一页
            </Button>
          </Space>
        }
      />
    </div>
  )
}