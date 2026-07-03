/**
 * pages/Login/Login.jsx - 登录页面（企业级重构）
 *
 * 设计语言：白 + 蓝 主色调，现代企业级 SaaS 登录页
 *
 * 企业级窗口行为：
 *   - 登录窗口固定尺寸 900×560，不可放大缩小，不可最大化
 *   - 标题栏仅含最小化 + 关闭按钮（企业级登录窗口惯例）
 *   - 登录成功后切换为可调整大小的主窗口
 *
 * 布局（桌面端）:
 *  ┌──────────────────────────────────────────────────────┐
 *  │  [Logo] 私有云                           [─] [✕]    │
 *  ├─────────────────────┬────────────────────────────────┤
 *  │                     │                                │
 *  │   品牌展示区         │   登录表单                      │
 *  │   Logo + 标语       │   - 账号输入                    │
 *  │   企业私有云盘       │   - 密码输入                    │
 *  │                     │   - 记住账号                    │
 *  │                     │   - 登录按钮                    │
 *  │                     │   - 注册链接                    │
 *  │                     │                                │
 *  └─────────────────────┴────────────────────────────────┘
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Checkbox, message } from 'antd'
import { UserOutlined, LockOutlined, CloudOutlined, SafetyOutlined } from '@ant-design/icons'
import { useUserStore } from '@/store/userStore'
import { getRememberLogin, getSavedAccount, setRememberLogin, setSavedAccount } from '@/utils/storage'
import { hashPasswordForTransmission } from '@/utils/crypto'
import './Login.css'

// 登录窗口固定尺寸（企业级应用标准）
const LOGIN_WINDOW_WIDTH = 900
const LOGIN_WINDOW_HEIGHT = 560

// ==================== 窗口控制按钮 SVG 图标 ====================

/** 最小化图标 */
const MinimizeIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <rect x="1" y="5.5" width="10" height="1" rx="0.5" fill="currentColor" />
  </svg>
)

/** 关闭图标 */
const CloseIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <path d="M1 1L11 11M11 1L1 11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
)

export default function LoginPage() {
  const navigate = useNavigate()
  const { doLogin, loading } = useUserStore()
  const [form] = Form.useForm()

  // ==================== 窗口控制：固定尺寸 + 不可调整 ====================
  useEffect(() => {
    if (!window.electronAPI) return

    console.log('electronAPI:', window.electronAPI);
console.log('setResizable type:', typeof window.electronAPI?.setResizable);

    // 设置为固定尺寸，不可调整大小，不可最大化
    window.electronAPI.setResizable(false)
    window.electronAPI.setResizable(false)
    window.electronAPI.setSize(LOGIN_WINDOW_WIDTH, LOGIN_WINDOW_HEIGHT)

    return () => {
      // 离开登录页时恢复窗口可调整
      window.electronAPI.setResizable(true)
      window.electronAPI.setMaximizable(true)
    }
  }, [])

  // ==================== 认证检查 ====================
  useEffect(() => {
    // 检查是否已登录
    const token = useUserStore.getState().token
    if (token) {
      navigate('/home', { replace: true })
      return
    }

    // 恢复记住的账号信息
    const remembered = getRememberLogin()
    const savedAccount = getSavedAccount()
    if (remembered && savedAccount) {
      form.setFieldsValue({ account: savedAccount, remember: true })
    }
  }, [])

  // ==================== 登录 ====================
  const handleLogin = async (values) => {
    try {
      // v5.0: PBKDF2-SHA256 密码预哈希（60万次迭代）
      const hashedPassword = await hashPasswordForTransmission(values.password)

      await doLogin({
        account: values.account,
        phone_number: values.phone_number,
        password: hashedPassword
      })

      // 记住密码
      if (values.remember) {
        setRememberLogin(true)
        setSavedAccount(values.account)
      } else {
        setRememberLogin(false)
        localStorage.removeItem('pcd_saved_account')
      }

      message.success('登录成功')
      navigate('/home', { replace: true })
    } catch (e) {
      message.error(e.message || '登录失败，请检查账号和密码')
    }
  }

  // ==================== 窗口控制 ====================
  const handleMinimize = useCallback(() => {
    window.electronAPI?.minimizeWindow()
  }, [])

  const handleClose = useCallback(() => {
    window.electronAPI?.closeWindow()
  }, [])

  return (
    <div className="login-page">
      {/* 自定义标题栏 */}
      <div className="login-titlebar">
        <div className="login-titlebar__logo">
          <CloudOutlined style={{ fontSize: 16, color: '#1a73e8' }} />
          <span>私有云</span>
        </div>

        {/* 窗口控制按钮 */}
        <div className="login-titlebar__controls">
          <button
            className="login-titlebar__btn"
            onClick={handleMinimize}
            aria-label="最小化"
            title="最小化"
          >
            <MinimizeIcon />
          </button>
          <button
            className="login-titlebar__btn login-titlebar__btn--close"
            onClick={handleClose}
            aria-label="关闭"
            title="关闭"
          >
            <CloseIcon />
          </button>
        </div>
      </div>

      {/* 登录主体 */}
      <div className="login-container">
        {/* 左侧：品牌展示区 */}
        <div className="login-brand">
          <div className="login-brand__content">
            {/* Logo 和 产品名 */}
            <div className="login-brand__logo">
              <CloudOutlined style={{ fontSize: 52, color: '#ffffff' }} />
            </div>
            <h1 className="login-brand__title">私有云</h1>
            <p className="login-brand__subtitle">企业私有云盘</p>

            {/* 特性列表 */}
            <div className="login-brand__features">
              <div className="login-brand__feature">
                <SafetyOutlined style={{ fontSize: 16 }} />
                <span>企业级数据安全保障</span>
              </div>
              <div className="login-brand__feature">
                <CloudOutlined style={{ fontSize: 16 }} />
                <span>全平台无缝同步</span>
              </div>
              <div className="login-brand__feature">
                <UserOutlined style={{ fontSize: 16 }} />
                <span>团队协作，高效办公</span>
              </div>
            </div>
          </div>

          {/* 背景装饰 */}
          <div className="login-brand__decoration" />
        </div>

        {/* 右侧：登录表单 */}
        <div className="login-form-wrapper">
          <div className="login-form__header">
            <h2>欢迎回来</h2>
            <p>登录您的私有云账号</p>
          </div>

          <Form
            form={form}
            onFinish={handleLogin}
            size="large"
            autoComplete="off"
            className="login-form"
          >
            <Form.Item
              name="account"
              rules={[{ required: true, message: '请输入账号' }]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#9aa0a6' }} />}
                placeholder="账号"
                autoFocus
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6位' }
              ]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#9aa0a6' }} />}
                placeholder="密码"
              />
            </Form.Item>

            <div className="login-form__options">
              <Form.Item name="remember" valuePropName="checked" noStyle>
                <Checkbox>记住账号</Checkbox>
              </Form.Item>
              <a className="login-form__forgot">忘记密码？</a>
            </div>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                className="login-form__submit"
              >
                登录
              </Button>
            </Form.Item>
          </Form>

          <div className="login-form__footer">
            <span>还没有账号？</span>
            <Link to="/register">立即注册</Link>
          </div>
        </div>
      </div>
    </div>
  )
}