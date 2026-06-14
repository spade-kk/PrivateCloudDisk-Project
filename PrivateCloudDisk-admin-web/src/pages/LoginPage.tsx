// ============================================================
// 管理员登录页面
// 集成 Cloudflare Turnstile 验证码，参考 Vue 前端实现
// ============================================================
import { useState, useEffect, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Space, message, theme, Tag, Alert } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  CloudServerOutlined,
  EyeInvisibleOutlined,
  EyeTwoTone,
  //SafetyCertificateOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/authStore'
import TurnstileWidget from '@/components/TurnstileWidget'

const { Title, Text } = Typography

const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || '0x4AAAAAADf7cwlBLsuoXeH-'

export default function LoginPage() {
  const [loading, setLoading] = useState(false)
  const [captchaToken, setCaptchaToken] = useState('')
  const [captchaError, setCaptchaError] = useState('')
  const [formError, setFormError] = useState('')
  //const [showPassword, setShowPassword] = useState(false)
  const [rememberDevice, setRememberDevice] = useState(true)
  //const turnstileRef = useRef<{ reset: () => void } | null>(null)

  const navigate = useNavigate()
  const { login, isLoggedIn } = useAuthStore()
  const { token: themeToken } = theme.useToken()

  // 已登录则跳转到仪表盘
  useEffect(() => {
    if (isLoggedIn) {
      navigate('/dashboard', { replace: true })
    }
  }, [isLoggedIn, navigate])

  const handleCaptchaVerify = useCallback((token: string) => {
    setCaptchaToken(token)
    setCaptchaError('')
  }, [])

  const handleCaptchaExpired = useCallback(() => {
    setCaptchaToken('')
    setCaptchaError('验证已过期，请重新完成验证')
  }, [])

  const handleCaptchaError = useCallback(() => {
    setCaptchaToken('')
    setCaptchaError('验证组件加载失败，请刷新后重试')
  }, [])

  const resetCaptcha = useCallback(() => {
    setCaptchaToken('')
    setCaptchaError('')
  }, [])

  const handleSubmit = async (values: { account: string; password: string }) => {
    setFormError('')

    if (!captchaToken) {
      setCaptchaError('请先完成安全验证')
      return
    }

    setLoading(true)
    try {
      const result = await login(values.account, values.password, captchaToken)
      if (result.success) {
        if (rememberDevice) {
          localStorage.setItem('cloudDriveAdminTrustedDevice', '1')
        }
        message.success('登录成功')
        navigate('/dashboard', { replace: true })
      } else {
        setFormError(result.message || '登录失败')
        resetCaptcha()
      }
    } catch {
      setFormError('网络错误，请稍后重试')
      resetCaptcha()
    } finally {
      setLoading(false)
    }
  }

  const captchaStatusText = !TURNSTILE_SITE_KEY
    ? '未配置'
    : captchaToken
      ? '已通过'
      : captchaError
        ? '需重试'
        : '待验证'

  const captchaStatusColor = !TURNSTILE_SITE_KEY
    ? 'default'
    : captchaToken
      ? 'success'
      : captchaError
        ? 'error'
        : 'default'

  const submitDisabled = loading || !captchaToken || !TURNSTILE_SITE_KEY

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
        padding: 24,
      }}
    >
      <Card
        style={{
          width: 460,
          maxWidth: '100%',
          borderRadius: 12,
          boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
        }}
        styles={{ body: { padding: 40 } }}
      >
        {/* Logo & 标题 */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <CloudServerOutlined
            style={{ fontSize: 48, color: themeToken.colorPrimary, marginBottom: 16 }}
          />
          <Title level={3} style={{ margin: 0 }}>
            PrivateCloudDisk
          </Title>
          <Text type="secondary">管理员控制台</Text>
        </div>

        {/* 表单错误提示 */}
        {formError && (
          <Alert
            message={formError}
            type="error"
            showIcon
            closable
            onClose={() => setFormError('')}
            style={{ marginBottom: 16 }}
          />
        )}

        {/* 登录表单 */}
        <Form
          name="admin-login"
          size="large"
          onFinish={handleSubmit}
          autoComplete="off"
          initialValues={{ account: '', password: '' }}
        >
          <Form.Item
            name="account"
            rules={[{ required: true, message: '请输入管理员账号' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="管理员账号"
              autoFocus
              onChange={() => setFormError('')}
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
              iconRender={(visible) =>
                visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />
              }
              onChange={() => setFormError('')}
            />
          </Form.Item>

          {/* 记住设备 */}
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              marginBottom: 16,
              fontSize: 13,
            }}
          >
            <label
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                gap: 8,
                cursor: 'pointer',
                fontWeight: 600,
                color: '#334155',
              }}
            >
              <input
                type="checkbox"
                checked={rememberDevice}
                onChange={(e) => setRememberDevice(e.target.checked)}
                style={{ accentColor: themeToken.colorPrimary }}
              />
              信任此设备
            </label>
            <Text type="secondary" style={{ fontSize: 12 }}>
              建议仅在个人设备启用
            </Text>
          </div>

          {/* Cloudflare Turnstile 验证码 */}
          <div
            style={{
              border: '1px solid #e2e8f0',
              borderRadius: 8,
              background: '#f8fafc',
              padding: 16,
              marginBottom: 16,
            }}
          >
            <div
              style={{
                display: 'flex',
                alignItems: 'flex-start',
                justifyContent: 'space-between',
                marginBottom: 12,
              }}
            >
              <div>
                <div style={{ fontWeight: 700, fontSize: 14, color: '#334155' }}>
                  安全验证
                </div>
                <div style={{ fontSize: 13, color: '#64748b' }}>
                  Cloudflare Turnstile 防护
                </div>
              </div>
              <Tag
                color={captchaStatusColor}
                style={{ fontWeight: 700, fontSize: 12 }}
              >
                {captchaStatusText}
              </Tag>
            </div>

            <div
              style={{
                minHeight: 74,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                borderRadius: 8,
                border: '1px solid #e2e8f0',
                background: '#fff',
                overflow: 'hidden',
              }}
            >
              <TurnstileWidget
                siteKey={TURNSTILE_SITE_KEY}
                action="login"
                theme="light"
                size="normal"
                onVerify={handleCaptchaVerify}
                onExpired={handleCaptchaExpired}
                onError={handleCaptchaError}
              />
            </div>

            {captchaError && (
              <div style={{ marginTop: 8, fontSize: 12, color: '#ff4d4f' }}>
                <ExclamationCircleOutlined style={{ marginRight: 4 }} />
                {captchaError}
              </div>
            )}

            {captchaToken && (
              <div style={{ marginTop: 8, fontSize: 12, color: '#52c41a' }}>
                <CheckCircleOutlined style={{ marginRight: 4 }} />
                验证通过
              </div>
            )}
          </div>

          <Form.Item style={{ marginBottom: 12 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              disabled={submitDisabled}
              block
              style={{ height: 44 }}
            >
              {loading ? '正在登录...' : '登录管理后台'}
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <Space split={<Text type="secondary">|</Text>}>
            <Link to="/register" style={{ fontWeight: 600 }}>
              注册管理员账号
            </Link>
            <Text type="secondary">还没有账号？</Text>
          </Space>
        </div>
      </Card>
    </div>
  )
}