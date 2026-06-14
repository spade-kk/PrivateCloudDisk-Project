// ============================================================
// 页面标题通用组件
// ============================================================
import { Typography, Space, Divider } from 'antd'
import type { ReactNode } from 'react'

const { Title, Text } = Typography

interface PageHeaderProps {
  title: string
  subtitle?: string
  icon?: ReactNode
  extra?: ReactNode
  actions?: ReactNode
}

export default function PageHeader({ title, subtitle, icon, extra, actions }: PageHeaderProps) {
  return (
    <div style={{ marginBottom: 24 }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          flexWrap: 'wrap',
          gap: 12,
        }}
      >
        <Space align="center" size={12}>
          {icon}
          <div>
            <Title level={4} style={{ margin: 0 }}>
              {title}
            </Title>
            {subtitle && (
              <Text type="secondary" style={{ fontSize: 14 }}>
                {subtitle}
              </Text>
            )}
          </div>
        </Space>
        <Space wrap>
          {extra}
          {actions}
        </Space>
      </div>
      <Divider style={{ marginTop: 16, marginBottom: 0 }} />
    </div>
  )
}