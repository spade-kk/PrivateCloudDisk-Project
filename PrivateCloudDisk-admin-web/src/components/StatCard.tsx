// ============================================================
// 统计卡片通用组件
// ============================================================
import { Card, Statistic, Typography, Space } from 'antd'
import { ArrowUpOutlined, ArrowDownOutlined } from '@ant-design/icons'

const { Text } = Typography

interface StatCardProps {
  title: string
  value: number | string
  prefix?: React.ReactNode
  suffix?: React.ReactNode
  precision?: number
  trend?: number // 正数上升，负数下降，0 不变
  trendLabel?: string
  loading?: boolean
  style?: React.CSSProperties
  onClick?: () => void
}

export default function StatCard({
  title,
  value,
  prefix,
  suffix,
  precision,
  trend,
  trendLabel,
  loading,
  style,
  onClick,
}: StatCardProps) {
  return (
    <Card
      hoverable={!!onClick}
      loading={loading}
      onClick={onClick}
      style={{ borderRadius: 8, ...style }}
    >
      <Statistic
        title={<Text type="secondary">{title}</Text>}
        value={value}
        precision={precision}
        prefix={prefix}
        suffix={suffix}
        valueStyle={{ fontSize: 28, fontWeight: 600 }}
      />
      {trend !== undefined && (
        <Space style={{ marginTop: 8 }}>
          {trend > 0 ? (
            <Text type="success">
              <ArrowUpOutlined /> +{trend}%
            </Text>
          ) : trend < 0 ? (
            <Text type="danger">
              <ArrowDownOutlined /> {trend}%
            </Text>
          ) : (
            <Text type="secondary">持平</Text>
          )}
          {trendLabel && <Text type="secondary">{trendLabel}</Text>}
        </Space>
      )}
    </Card>
  )
}