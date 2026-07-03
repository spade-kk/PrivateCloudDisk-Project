/**
 * pages/Register/Register.jsx - 注册页面（企业级实现）
 *
 * 设计语言：白 + 蓝 主色调，与登录页风格统一
 *
 * 企业级窗口行为：
 *   - 注册窗口固定尺寸 900×600，不可放大缩小，不可最大化
 *   - 标题栏仅含最小化 + 关闭按钮
 *   - 注册成功或返回登录后恢复窗口可调整
 *
 * 布局（桌面端）:
 *  ┌──────────────────────────────────────────────────────┐
 *  │  [Logo] 私有云                           [─] [✕]    │
 *  ├─────────────────────┬────────────────────────────────┤
 *  │                     │                                │
 *  │   品牌展示区         │   注册表单                      │
 *  │   Logo + 标语       │   - 账号输入                    │
 *  │   企业私有云盘       │   - 手机号输入                  │
 *  │                     │   - 密码输入                    │
 *  │                     │   - 确认密码                    │
 *  │                     │   - 注册按钮                    │
 *  │                     │   - 登录链接                    │
 *  │                     │                                │
 *  └─────────────────────┴────────────────────────────────┘
 */
import React, { useState, useEffect, useCallback } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, message } from 'antd'
import {
  UserOutlined, LockOutlined, PhoneOutlined, CloudOutlined,
  SafetyOutlined, CheckCircleOutlined
} from '@ant-design/icons'
import { useUserStore } from '@/store/userStore'
import { hashPasswordForTransmission } from '@/utils/crypto'
import './Register.css'

// 注册窗口固定尺寸（比登录稍高以容纳更多表单字段）
const REGISTER_WINDOW_WIDTH = 900
const REGISTER_WINDOW_HEIGHT = 600

// ==================== 窗口控制按钮 SVG 图标 ====================

const MinimizeIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <rect x="1" y="5.5" width="10" height="1" rx="0.5" fill="currentColor" />
  </svg>
)

const CloseIcon = () => (
  <svg width="12" height="12" viewBox="0 0 12 12" fill="none">
    <path d="M1 1L11 11M11 1L1 11" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
  </svg>
)

export default function RegisterPage() {
  const navigate = useNavigate()
  const { doRegister, loading } = useUserStore()
  const [form] = Form.useForm()

  // ==================== 窗口控制：固定尺寸 + 不可调整 ====================
  useEffect(() => {
    if (!window.electronAPI) return

    window.electronAPI.setResizable(false)
    window.electronAPI.setMaximizable(false)
    window.electronAPI.setSize(REGISTER_WINDOW_WIDTH, REGISTER_WINDOW_HEIGHT)

    return () => {
      window.electronAPI.setResizable(true)
      window.electronAPI.setMaximizable(true)
    }
  }, [])

  // ==================== 注册 ====================
  const handleRegister = async (values) => {
    try {
      const hashedPassword = await hashPasswordForTransmission(values.password)

      await doRegister({
        account: values.account,
        phone_number: values.phone_number,
        password: hashedPassword
      })

      message.success('注册成功，请登录')
      navigate('/login', { replace: true })
    } catch (e) {
      message.error(e.message || '注册失败，请稍后重试')
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
    <div className="register-page">
      {/* 自定义标题栏 */}
      <div className="register-titlebar">
        <div className="register-titlebar__logo">
          <CloudOutlined style={{ fontSize: 16, color: '#1a73e8' }} />
          <span>私有云</span>
        </div>

        {/* 窗口控制按钮 */}
        <div className="register-titlebar__controls">
          <button
            className="register-titlebar__btn"
            onClick={handleMinimize}
            aria-label="最小化"
            title="最小化"
          >
            <MinimizeIcon />
          </button>
          <button
            className="register-titlebar__btn register-titlebar__btn--close"
            onClick={handleClose}
            aria-label="关闭"
            title="关闭"
          >
            <CloseIcon />
          </button>
        </div>
      </div>

      {/* 注册主体 */}
      <div className="register-container">
        {/* 左侧：品牌展示区 */}
        <div className="register-brand">
          <div className="register-brand__content">
            <div className="register-brand__logo">
              <CheckCircleOutlined style={{ fontSize: 52, color: '#ffffff' }} />
            </div>
            <h1 className="register-brand__title">创建账号</h1>
            <p className="register-brand__subtitle">加入企业私有云盘</p>

            <div className="register-brand__features">
              <div className="register-brand__feature">
                <CloudOutlined style={{ fontSize: 16 }} />
                <span>海量存储空间</span>
              </div>
              <div className="register-brand__feature">
                <SafetyOutlined style={{ fontSize: 16 }} />
                <span>企业级安全加密</span>
              </div>
              <div className="register-brand__feature">
                <UserOutlined style={{ fontSize: 16 }} />
                <span>团队协作共享</span>
              </div>
            </div>
          </div>

          <div className="register-brand__decoration" />
        </div>

        {/* 右侧：注册表单 */}
        <div className="register-form-wrapper">
          <div className="register-form__header">
            <h2>注册</h2>
            <p>创建您的私有云账号</p>
          </div>

          <Form
            form={form}
            onFinish={handleRegister}
            size="large"
            autoComplete="off"
            className="register-form"
          >
            <Form.Item
              name="account"
              rules={[
                { required: true, message: '请输入账号' },
                { min: 3, message: '账号至少3个字符' },
                { max: 32, message: '账号最多32个字符' },
                { pattern: /^[a-zA-Z0-9_]+$/, message: '账号只能包含字母、数字和下划线' }
              ]}
            >
              <Input
                prefix={<UserOutlined style={{ color: '#9ca3af' }} />}
                placeholder="账号（字母、数字、下划线）"
                autoFocus
              />
            </Form.Item>

            <Form.Item
              name="phone_number"
              rules={[
                { required: true, message: '请输入手机号' },
                { pattern: /^1[3-9]\d{9}$/, message: '请输入正确的手机号' }
              ]}
            >
              <Input
                prefix={<PhoneOutlined style={{ color: '#9ca3af' }} />}
                placeholder="手机号"
              />
            </Form.Item>

            <Form.Item
              name="password"
              rules={[
                { required: true, message: '请输入密码' },
                { min: 6, message: '密码至少6位' },
                { max: 32, message: '密码最多32位' }
              ]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#9ca3af' }} />}
                placeholder="密码（6-32位）"
              />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              dependencies={['password']}
              rules={[
                { required: true, message: '请确认密码' },
                ({ getFieldValue }) => ({
                  validator(_, value) {
                    if (!value || getFieldValue('password') === value) {
                      return Promise.resolve()
                    }
                    return Promise.reject(new Error('两次输入的密码不一致'))
                  }
                })
              ]}
            >
              <Input.Password
                prefix={<LockOutlined style={{ color: '#9ca3af' }} />}
                placeholder="确认密码"
              />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                block
                className="register-form__submit"
              >
                注册
              </Button>
            </Form.Item>
          </Form>

          <div className="register-form__footer">
            <span>已有账号？</span>
            <Link to="/login">立即登录</Link>
          </div>
        </div>
      </div>
    </div>
  )
}