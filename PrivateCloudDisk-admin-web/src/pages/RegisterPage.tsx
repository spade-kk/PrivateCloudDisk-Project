// ============================================================
// 管理员注册页面
// 集成 Cloudflare Turnstile 验证码 + 邮箱验证码
// 参考 Vue 前端 RegisterView.vue 实现
// ============================================================
import { useState, useEffect, useCallback, useRef } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Card, Typography, Space, message, theme, Tag, Alert, Progress } from 'antd'
import {
  UserOutlined,
  LockOutlined,
  CloudServerOutlined,
  EyeInvisibleOutlined,
  EyeTwoTone,
  MailOutlined,
  KeyOutlined,
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  //SafetyCertificateOutlined,
  //SendOutlined,
} from '@ant-design/icons'
import { useAuthStore } from '@/stores/authStore'
import { sendVerificationCodeApi } from '@/api/auth'
import TurnstileWidget from '@/components/TurnstileWidget'
import type { RegisterRequest } from '@/types/api'

const { Title, Text } = Typography

const TURNSTILE_SITE_KEY = import.meta.env.VITE_TURNSTILE_SITE_KEY || ''

export default function RegisterPage() {
  const [loading, setLoading] = useState(false)
  const [captchaToken, setCaptchaToken] = useState('')
  const [captchaError, setCaptchaError] = useState('')
  const [formError, setFormError] = useState('')
  //const [showPassword, setShowPassword] = useState(false)
  const [verificationCountdown, setVerificationCountdown] = useState(0)
  const [sendingCode, setSendingCode] = useState(false)
  const verificationTimerRef = useRef<ReturnType<typeof setInterval> | null>(null)

  const navigate = useNavigate()
  const { register, isLoggedIn } = useAuthStore()
  const { token: themeToken } = theme.useToken()
  const [form] = Form.useForm()

  // 已登录也允许访问注册页面（不跳转）
  // 但如果已经登录，给一个提示

  // 清理定时器
  useEffect(() => {
    return () => {
      if (verificationTimerRef.current) {
        clearInterval(verificationTimerRef.current)
      }
    }
  }, [])

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

  // 发送邮箱验证码
  const handleSendCode = async () => {
    setFormError('')
    const email = form.getFieldValue('email')

    if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setFormError('请先输入有效邮箱地址')
      return
    }

    setSendingCode(true)
    try {
      await sendVerificationCodeApi(email)
      message.success('验证码已发送')

      setVerificationCountdown(60)
      if (verificationTimerRef.current) clearInterval(verificationTimerRef.current)
      verificationTimerRef.current = setInterval(() => {
        setVerificationCountdown((prev) => {
          if (prev <= 1) {
            if (verificationTimerRef.current) {
              clearInterval(verificationTimerRef.current)
              verificationTimerRef.current = null
            }
            return 0
          }
          return prev - 1
        })
      }, 1000)
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '发送验证码失败'
      setFormError(msg)
    } finally {
      setSendingCode(false)
    }
  }

  // 提交注册
  const handleSubmit = async (values: {
    name: string
    email: string
    code: string
    account: string
    password: string
  }) => {
    setFormError('')

    // 校验
    if (!/^[a-zA-Z0-9]{2,10}$/.test(values.name)) {
      setFormError('用户名必须是 2-10 位数字或字母')
      return
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email)) {
      setFormError('请输入有效邮箱地址')
      return
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$/.test(values.password)) {
      setFormError('密码必须是 8-15 位，且包含字母和数字')
      return
    }
    if (!captchaToken) {
      setCaptchaError('请先完成安全验证')
      return
    }

    const registerData: RegisterRequest = {
      account: values.account,
      name: values.name,
      email: values.email,
      password: values.password,
      code: values.code,
      captcha_token: captchaToken,
      captcha_action: 'register',
    }

    setLoading(true)
    try {
      const result = await register(registerData)
      if (result.success) {
        message.success('注册成功，请登录')
        navigate('/login', { replace: true })
      } else {
        setFormError(result.message || '注册失败')
        resetCaptcha()
      }
    } catch {
      setFormError('网络错误，请稍后重试')
      resetCaptcha()
    } finally {
      setLoading(false)
    }
  }

  // 密码强度计算
  const passwordValue = Form.useWatch('password', form) || ''
  const passwordScore = (() => {
    let score = 0
    if (passwordValue.length >= 8) score += 1
    if (/[A-Za-z]/.test(passwordValue) && /\d/.test(passwordValue)) score += 1
    if (passwordValue.length >= 12) score += 1
    return score
  })()
  const passwordStrengthText = !passwordValue ? '密码强度' : passwordScore >= 3 ? '强' : passwordScore === 2 ? '中' : '弱'
  const passwordStrengthPercent = !passwordValue ? 0 : (passwordScore / 3) * 100

  const captchaStatusText = !TURNSTILE_SITE_KEY
    ? '未配置'
    : captchaToken
      ? '验证通过'
      : captchaError
        ? '验证失败'
        : '等待验证'

  const captchaStatusColor = !TURNSTILE_SITE_KEY
    ? 'default'
    : captchaToken
      ? 'success'
      : captchaError
        ? 'error'
        : 'default'

  const submitDisabled = loading || !captchaToken || !TURNSTILE_SITE_KEY

  // 密码强度颜色
  const strengthStrokeColor = passwordScore <= 1 ? '#ff4d4f' : passwordScore === 2 ? '#faad14' : '#52c41a'

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0d9488 0%, #667eea 100%)',
        padding: 24,
      }}
    >
      <Card
        style={{
          width: 480,
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
          <Text type="secondary">管理员账号注册</Text>
        </div>

        {/* 已登录提示 */}
        {isLoggedIn && (
          <Alert
            message="您已登录，仍可注册新管理员账号"
            type="info"
            showIcon
            style={{ marginBottom: 16 }}
          />
        )}

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

        {/* 注册表单 */}
        <Form
          form={form}
          name="admin-register"
          size="large"
          onFinish={handleSubmit}
          autoComplete="off"
          initialValues={{ account: '', name: '', email: '', code: '', password: '' }}
        >
          {/* 用户名 */}
          <Form.Item
            name="name"
            rules={[
              { required: true, message: '请输入用户名' },
              { pattern: /^[a-zA-Z0-9]{2,10}$/, message: '2-10 位数字或字母' },
            ]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="2-10 位数字或字母"
              onChange={() => setFormError('')}
            />
          </Form.Item>

          {/* 邮箱 */}
          <Form.Item
            name="email"
            rules={[
              { required: true, message: '请输入邮箱地址' },
              { type: 'email', message: '请输入有效邮箱地址' },
            ]}
          >
            <Input
              prefix={<MailOutlined />}
              placeholder="请输入邮箱地址"
              onChange={() => setFormError('')}
            />
          </Form.Item>

          {/* 邮箱验证码 */}
          <Form.Item
            name="code"
            rules={[{ required: true, message: '请输入验证码' }]}
          >
            <Input
              prefix={<KeyOutlined />}
              placeholder="请输入验证码"
              onChange={() => setFormError('')}
              suffix={
                <Button
                  type="link"
                  size="small"
                  disabled={verificationCountdown > 0}
                  loading={sendingCode}
                  onClick={handleSendCode}
                  style={{ padding: 0, fontWeight: 600 }}
                >
                  {verificationCountdown > 0 ? `${verificationCountdown}s` : '获取验证码'}
                </Button>
              }
            />
          </Form.Item>

          {/* 密码 */}
          <Form.Item
            name="password"
            rules={[
              { required: true, message: '请输入密码' },
              { pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d]{8,15}$/, message: '8-15 位，包含字母和数字' },
            ]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="8-15 位，包含字母和数字"
              iconRender={(visible) =>
                visible ? <EyeTwoTone /> : <EyeInvisibleOutlined />
              }
              onChange={() => setFormError('')}
            />
          </Form.Item>

          {/* 密码强度指示 */}
          {passwordValue && (
            <div style={{ marginTop: -16, marginBottom: 16 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Progress
                  percent={passwordStrengthPercent}
                  strokeColor={strengthStrokeColor}
                  showInfo={false}
                  size="small"
                  style={{ flex: 1, marginBottom: 0 }}
                />
                <Text style={{ fontSize: 12, minWidth: 54, textAlign: 'right' }}>
                  {passwordStrengthText}
                </Text>
              </div>
            </div>
          )}

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
                  创建账号前完成 Turnstile 校验
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
                action="register"
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

          {/* 提交按钮 */}
          <Form.Item style={{ marginBottom: 12 }}>
            <Button
              type="primary"
              htmlType="submit"
              loading={loading}
              disabled={submitDisabled}
              block
              style={{ height: 44, background: '#0d9488', borderColor: '#0d9488' }}
            >
              {loading ? '正在创建账号...' : '创建管理员账号'}
            </Button>
          </Form.Item>
        </Form>

        <div style={{ textAlign: 'center' }}>
          <Space split={<Text type="secondary">|</Text>}>
            <Text type="secondary">已有账号？</Text>
            <Link to="/login" style={{ fontWeight: 600 }}>
              立即登录
            </Link>
          </Space>
        </div>
      </Card>
    </div>
  )
}