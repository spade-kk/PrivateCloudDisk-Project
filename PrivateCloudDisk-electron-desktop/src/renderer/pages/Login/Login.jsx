/**
 * pages/Login/Login.jsx - 登录页面
 *
 * 功能:
 * - 账号密码登录
 * - 记住密码
 * - 跳转注册页
 */
import React, { useState, useEffect } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { Form, Input, Button, Checkbox, message, Card } from 'antd'
import { UserOutlined, LockOutlined, CloudOutlined } from '@ant-design/icons'
import { useUserStore } from '@/store/userStore'
import { getRememberLogin, getSavedAccount, setRememberLogin, setSavedAccount } from '@/utils/storage'
import { hashPasswordForTransmission } from '@/utils/crypto'
import './Login.css'

export default function LoginPage() {
  const navigate = useNavigate()
  const { doLogin, loading } = useUserStore()
  const [form] = Form.useForm()

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

  return (
    <div className="login-page">
      <Card className="login-card" bordered={false}>
        <div className="login-header">
          <CloudOutlined className="login-logo" />
          <h1>PrivateCloudDisk</h1>
          <p>企业私有云盘</p>
        </div>

        <Form
          form={form}
          onFinish={handleLogin}
          size="large"
          autoComplete="off"
        >
          <Form.Item
            name="account"
            rules={[{ required: true, message: '请输入账号' }]}
          >
            <Input
              prefix={<UserOutlined />}
              placeholder="账号"
              autoFocus
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              prefix={<LockOutlined />}
              placeholder="密码"
            />
          </Form.Item>

          <Form.Item name="remember" valuePropName="checked">
            <Checkbox>记住账号</Checkbox>
          </Form.Item>

          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>

        <div className="login-footer">
          <span>还没有账号？</span>
          <Link to="/register">立即注册</Link>
        </div>
      </Card>

      <div className="login-copyright">
        PrivateCloudDisk v1.0.0
      </div>
    </div>
  )
}