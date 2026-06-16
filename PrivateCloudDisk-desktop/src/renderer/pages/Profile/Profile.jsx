/**
 * pages/Profile/Profile.jsx - 个人中心页面
 */
import React, { useState } from 'react'
import { Card, Form, Input, Button, Avatar, message, Upload, Modal, Descriptions } from 'antd'
import { UserOutlined, PhoneOutlined, MailOutlined, LockOutlined } from '@ant-design/icons'
import { useUserStore } from '@/store/userStore'
import { validateName, validatePhone, validateEmail } from '@/utils/validator'
import './Profile.css'

export default function ProfilePage() {
  const { profile, loading, doUpdateProfile, doChangePassword, doUploadAvatar, avatarUrl, displayName } = useUserStore()
  const [form] = Form.useForm()
  const [passwordForm] = Form.useForm()
  const [passwordVisible, setPasswordVisible] = useState(false)

  const handleUpdateProfile = async (values) => {
    try {
      const params = {}
      if (values.name && values.name !== profile?.name) params.new_name = values.name
      if (values.phone_number && values.phone_number !== profile?.phone_number) params.new_phone_number = values.phone_number
      if (values.email && values.email !== profile?.email) params.new_email = values.email
      if (Object.keys(params).length === 0) {
        message.info('没有修改任何信息')
        return
      }
      await doUpdateProfile(params)
      message.success('修改成功')
    } catch (e) {
      message.error(e.message || '修改失败')
    }
  }

  const handleChangePassword = async (values) => {
    try {
      await doChangePassword({
        user_password: values.old_password,
        new_password: values.new_password
      })
      message.success('密码修改成功')
      setPasswordVisible(false)
      passwordForm.resetFields()
    } catch (e) {
      message.error(e.message || '密码修改失败')
    }
  }

  const handleAvatarUpload = async (file) => {
    await doUploadAvatar(file)
    return false
  }

  return (
    <div className="profile-page">
      <div className="profile-layout">
        {/* 头像卡片 */}
        <Card className="profile-avatar-card" bordered={false}>
          <Upload showUploadList={false} beforeUpload={handleAvatarUpload}>
            <Avatar size={100} src={avatarUrl()} icon={<UserOutlined />} style={{ cursor: 'pointer', backgroundColor: '#1a73e8' }} />
          </Upload>
          <h3>{displayName()}</h3>
          <p className="profile-account">{profile?.account || '-'}</p>
        </Card>

        {/* 基本信息卡片 */}
        <Card className="profile-info-card" title="基本信息" bordered={false}>
          <Descriptions column={1} size="middle">
            <Descriptions.Item label="用户 ID">{profile?.id || '-'}</Descriptions.Item>
            <Descriptions.Item label="账号">{profile?.account || '-'}</Descriptions.Item>
          </Descriptions>

          <Form
            form={form}
            layout="vertical"
            onFinish={handleUpdateProfile}
            className="profile-form"
            initialValues={{
              name: profile?.name || '',
              phone_number: profile?.phone_number || '',
              email: profile?.email || ''
            }}
          >
            <Form.Item name="name" label="用户名" rules={[{ validator: (_, v) => validateName(v) ? Promise.reject(validateName(v)) : Promise.resolve() }]}>
              <Input prefix={<UserOutlined />} placeholder="输入用户名" />
            </Form.Item>
            <Form.Item name="phone_number" label="手机号" rules={[{ validator: (_, v) => v && validatePhone(v) ? Promise.reject(validatePhone(v)) : Promise.resolve() }]}>
              <Input prefix={<PhoneOutlined />} placeholder="输入手机号" />
            </Form.Item>
            <Form.Item name="email" label="邮箱" rules={[{ validator: (_, v) => v && validateEmail(v) ? Promise.reject(validateEmail(v)) : Promise.resolve() }]}>
              <Input prefix={<MailOutlined />} placeholder="输入邮箱" />
            </Form.Item>
            <Form.Item>
              <Button type="primary" htmlType="submit" loading={loading} block>保存修改</Button>
            </Form.Item>
          </Form>
        </Card>

        {/* 安全设置卡片 */}
        <Card className="profile-security-card" title="安全设置" bordered={false}>
          <Button icon={<LockOutlined />} onClick={() => setPasswordVisible(true)}>修改密码</Button>
        </Card>
      </div>

      {/* 修改密码弹窗 */}
      <Modal
        title="修改密码"
        open={passwordVisible}
        onCancel={() => { setPasswordVisible(false); passwordForm.resetFields() }}
        footer={null}
        destroyOnClose
      >
        <Form form={passwordForm} layout="vertical" onFinish={handleChangePassword}>
          <Form.Item name="old_password" label="当前密码" rules={[{ required: true, message: '请输入当前密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="输入当前密码" />
          </Form.Item>
          <Form.Item name="new_password" label="新密码" rules={[{ required: true, message: '请输入新密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="输入新密码" />
          </Form.Item>
          <Form.Item
            name="confirm_password"
            label="确认新密码"
            dependencies={['new_password']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('new_password') === value) return Promise.resolve()
                  return Promise.reject('两次密码不一致')
                }
              })
            ]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="再次输入新密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block>确认修改</Button>
        </Form>
      </Modal>
    </div>
  )
}