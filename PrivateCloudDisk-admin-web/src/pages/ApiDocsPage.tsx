// ============================================================
// API 文档页面
// ============================================================
import { Card, Typography, Space, Tag, Descriptions } from 'antd'
import { ApiOutlined, LinkOutlined } from '@ant-design/icons'
import PageHeader from '@/components/PageHeader'

const { Title, Paragraph, Link } = Typography

export default function ApiDocsPage() {
  return (
    <div>
      <PageHeader
        title="API 文档"
        subtitle="系统 API 接口文档与参考"
        icon={<ApiOutlined />}
      />

      <Space direction="vertical" size="large" style={{ width: '100%' }}>
        <Card title="Swagger UI" style={{ borderRadius: 8 }}>
          <Paragraph>
            访问 Swagger UI 查看完整的 REST API 接口文档：
          </Paragraph>
          <Link href="/swagger-ui.html" target="_blank">
            <Space>
              <LinkOutlined />
              Swagger UI
            </Space>
          </Link>
        </Card>

        <Card title="API 端点概览" style={{ borderRadius: 8 }}>
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            {/* 认证 */}
            <div>
              <Title level={5}>
                <Tag color="red">POST</Tag> 认证接口
              </Title>
              <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label="管理员登录">
                  <code>/api/admin/auth/login</code>
                </Descriptions.Item>
                <Descriptions.Item label="管理员登出">
                  <code>/api/admin/auth/logout</code>
                </Descriptions.Item>
                <Descriptions.Item label="刷新 Token">
                  <code>/api/admin/auth/refresh</code>
                </Descriptions.Item>
                <Descriptions.Item label="获取管理员信息">
                  <code>/api/admin/auth/me</code>
                </Descriptions.Item>
              </Descriptions>
            </div>

            {/* 用户管理 */}
            <div>
              <Title level={5}>
                <Tag color="blue">GET</Tag> <Tag color="green">PUT</Tag> <Tag color="red">DELETE</Tag> 用户管理
              </Title>
              <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label="用户列表">
                  <code>GET /api/admin/users</code>
                </Descriptions.Item>
                <Descriptions.Item label="用户详情">
                  <code>GET /api/admin/users/:userId</code>
                </Descriptions.Item>
                <Descriptions.Item label="修改用户状态">
                  <code>PUT /api/admin/users/:userId/status</code>
                </Descriptions.Item>
                <Descriptions.Item label="修改用户角色">
                  <code>PUT /api/admin/users/:userId/role</code>
                </Descriptions.Item>
                <Descriptions.Item label="删除用户">
                  <code>DELETE /api/admin/users/:userId</code>
                </Descriptions.Item>
                <Descriptions.Item label="批量操作">
                  <code>POST /api/admin/users/batch</code>
                </Descriptions.Item>
              </Descriptions>
            </div>

            {/* 文件管理 */}
            <div>
              <Title level={5}>
                <Tag color="blue">GET</Tag> <Tag color="red">DELETE</Tag> 文件管理
              </Title>
              <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label="文件列表">
                  <code>GET /api/admin/files</code>
                </Descriptions.Item>
                <Descriptions.Item label="文件详情">
                  <code>GET /api/admin/files/:fileId</code>
                </Descriptions.Item>
                <Descriptions.Item label="删除文件">
                  <code>DELETE /api/admin/files/:fileId</code>
                </Descriptions.Item>
                <Descriptions.Item label="隔离文件列表">
                  <code>GET /api/admin/files/quarantined</code>
                </Descriptions.Item>
              </Descriptions>
            </div>

            {/* 系统 */}
            <div>
              <Title level={5}>
                <Tag color="blue">GET</Tag> <Tag color="green">PUT</Tag> 系统管理
              </Title>
              <Descriptions size="small" column={1} bordered>
                <Descriptions.Item label="系统概览">
                  <code>GET /api/admin/system/overview</code>
                </Descriptions.Item>
                <Descriptions.Item label="系统资源">
                  <code>GET /api/admin/system/resources</code>
                </Descriptions.Item>
                <Descriptions.Item label="系统配置">
                  <code>GET /api/admin/system/config</code>
                </Descriptions.Item>
                <Descriptions.Item label="更新配置">
                  <code>PUT /api/admin/system/config</code>
                </Descriptions.Item>
              </Descriptions>
            </div>
          </Space>
        </Card>
      </Space>
    </div>
  )
}