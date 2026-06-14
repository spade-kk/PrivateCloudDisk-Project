// ============================================================
// 系统配置页面
// ============================================================
import { useEffect, useState } from 'react'
import {
  Form, Input, InputNumber, Switch, Button, Select, Tabs,
  message, Spin,
} from 'antd'
import { SaveOutlined, ReloadOutlined, ToolOutlined, SafetyOutlined, MailOutlined, CloudServerOutlined, SettingOutlined } from '@ant-design/icons'
import { useSystemStore } from '@/stores/systemStore'
import PageHeader from '@/components/PageHeader'
const { Option } = Select

export default function SystemConfigPage() {
  const { config, loading, fetchConfig, updateConfig } = useSystemStore()
  const [saving, setSaving] = useState(false)
  const [basicForm] = Form.useForm()
  const [securityForm] = Form.useForm()
  const [storageForm] = Form.useForm()
  const [notificationForm] = Form.useForm()

  useEffect(() => {
    fetchConfig()
  }, [fetchConfig])

  useEffect(() => {
    if (config) {
      basicForm.setFieldsValue(config)
      securityForm.setFieldsValue(config)
      storageForm.setFieldsValue(config)
      notificationForm.setFieldsValue(config)
    }
  }, [config, basicForm, securityForm, storageForm, notificationForm])

  const handleSave = async (values: Record<string, unknown>) => {
    setSaving(true)
    try {
      const ok = await updateConfig(values)
      if (ok) {
        message.success('配置已保存')
      } else {
        message.error('保存失败')
      }
    } catch {
      message.error('保存失败')
    } finally {
      setSaving(false)
    }
  }

  const tabItems = [
    {
      key: 'basic',
      label: <span><ToolOutlined /> 基本设置</span>,
      children: (
        <Spin spinning={loading.config}>
          <Form
            form={basicForm}
            layout="vertical"
            onFinish={handleSave}
            style={{ maxWidth: 640 }}
          >
            <Form.Item label="站点名称" name="siteName">
              <Input placeholder="PrivateCloudDisk" />
            </Form.Item>
            <Form.Item label="站点描述" name="siteDescription">
              <Input.TextArea rows={3} placeholder="企业级私有云盘系统" />
            </Form.Item>
            <Form.Item label="联系邮箱" name="contactEmail">
              <Input placeholder="admin@example.com" />
            </Form.Item>
            <Form.Item label="维护模式" name="maintenanceMode" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Form.Item label="允许注册" name="enableRegistration" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Form.Item label="启用人机验证" name="enableCaptcha" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存基本设置
            </Button>
          </Form>
        </Spin>
      ),
    },
    {
      key: 'security',
      label: <span><SafetyOutlined /> 安全策略</span>,
      children: (
        <Spin spinning={loading.config}>
          <Form
            form={securityForm}
            layout="vertical"
            onFinish={handleSave}
            style={{ maxWidth: 640 }}
          >
            <Form.Item label="会话超时(秒)" name="sessionTimeout">
              <InputNumber min={300} max={86400} style={{ width: 200 }} addonAfter="秒" />
            </Form.Item>
            <Form.Item label="最大登录尝试次数" name="maxLoginAttempts">
              <InputNumber min={1} max={20} style={{ width: 200 }} />
            </Form.Item>
            <Form.Item label="密码最小长度" name="passwordMinLength">
              <InputNumber min={6} max={32} style={{ width: 200 }} />
            </Form.Item>
            <Form.Item label="要求特殊字符" name="passwordRequireSpecialChar" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Form.Item label="启用双因素认证" name="enable2FA" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Form.Item label="病毒扫描" name="virusScanEnabled" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Form.Item label="上传时自动扫描" name="autoScanOnUpload" valuePropName="checked">
              <Switch checkedChildren="开启" unCheckedChildren="关闭" />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存安全设置
            </Button>
          </Form>
        </Spin>
      ),
    },
    {
      key: 'storage',
      label: <span><CloudServerOutlined /> 存储设置</span>,
      children: (
        <Spin spinning={loading.config}>
          <Form
            form={storageForm}
            layout="vertical"
            onFinish={handleSave}
            style={{ maxWidth: 640 }}
          >
            <Form.Item label="最大文件大小(MB)" name="maxFileSize">
              <InputNumber min={1} max={10240} style={{ width: 200 }} addonAfter="MB" />
            </Form.Item>
            <Form.Item label="允许的文件类型" name="allowedFileTypes">
              <Select mode="tags" placeholder="输入允许的文件扩展名，如 .pdf" style={{ width: '100%' }} />
            </Form.Item>
            <Form.Item label="最大上传并发数" name="maxUploadConcurrency">
              <InputNumber min={1} max={20} style={{ width: 200 }} />
            </Form.Item>
            <Form.Item label="MinIO 端点" name="minioEndpoint">
              <Input placeholder="http://minio:9000" />
            </Form.Item>
            <Form.Item label="MinIO 存储桶" name="minioBucket">
              <Input placeholder="privateclouddisk" />
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存存储设置
            </Button>
          </Form>
        </Spin>
      ),
    },
    {
      key: 'notification',
      label: <span><MailOutlined /> 通知设置</span>,
      children: (
        <Spin spinning={loading.config}>
          <Form
            form={notificationForm}
            layout="vertical"
            onFinish={handleSave}
            style={{ maxWidth: 640 }}
          >
            <Form.Item label="SMTP 主机" name="smtpHost">
              <Input placeholder="smtp.example.com" />
            </Form.Item>
            <Form.Item label="短信提供商" name="smsProvider">
              <Select placeholder="选择短信提供商" allowClear>
                <Option value="aliyun">阿里云短信</Option>
                <Option value="tencent">腾讯云短信</Option>
                <Option value="huawei">华为云短信</Option>
              </Select>
            </Form.Item>
            <Button type="primary" htmlType="submit" icon={<SaveOutlined />} loading={saving}>
              保存通知设置
            </Button>
          </Form>
        </Spin>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="系统配置"
        subtitle="管理全局系统配置参数"
        icon={<SettingOutlined />}
        actions={
          <Button icon={<ReloadOutlined />} onClick={fetchConfig}>
            重新加载
          </Button>
        }
      />

      <Tabs items={tabItems} />
    </div>
  )
}