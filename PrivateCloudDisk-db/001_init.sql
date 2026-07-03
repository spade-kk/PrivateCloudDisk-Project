-- ============================================================================
-- PrivateCloudDisk Notification Service - 数据库初始化脚本
-- 数据库: private_cloud_disk (共用)
-- 表前缀: pcd_notification_
-- ============================================================================

-- 1. 消息模板表
CREATE TABLE IF NOT EXISTS `pcd_notification_templates` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(100) NOT NULL COMMENT '模板唯一标识，如 welcome_email, verification_sms',
    `name` VARCHAR(200) NOT NULL COMMENT '模板名称',
    `channel` VARCHAR(50) NOT NULL COMMENT '渠道: email, sms, push, wechat_mp, alipay_mp, webpush',
    `lang` VARCHAR(10) NOT NULL DEFAULT 'zh-CN' COMMENT '语言: zh-CN, en-US, ja-JP',
    `title` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '标题模板，支持 {{.var}} 变量',
    `body` TEXT NOT NULL COMMENT '正文模板，支持 {{.var}} 变量',
    `html_body` TEXT COMMENT 'HTML 模板（邮件渠道专用）',
    `variables_json` JSON COMMENT '模板变量定义: [{"name":"code","type":"string","required":true}]',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code_channel_lang` (`code`, `channel`, `lang`),
    INDEX `idx_channel` (`channel`),
    INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息模板表';

-- 2. 通知记录表
CREATE TABLE IF NOT EXISTS `pcd_notification_records` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `event_id` VARCHAR(128) NOT NULL COMMENT '事件唯一ID，用于幂等',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `channel` VARCHAR(50) NOT NULL COMMENT '渠道',
    `type` VARCHAR(50) NOT NULL COMMENT '通知类型: verification, welcome, share, system, etc.',
    `title` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '通知标题',
    `body` TEXT NOT NULL COMMENT '通知正文',
    `recipient` VARCHAR(500) NOT NULL COMMENT '接收者标识（邮箱/手机号/deviceToken）',
    `template_code` VARCHAR(100) DEFAULT '' COMMENT '使用的模板 CODE',
    `status` VARCHAR(20) NOT NULL DEFAULT 'pending' COMMENT '状态: pending, processing, sent, delivered, failed, cancelled, aggregated',
    `priority` INT NOT NULL DEFAULT 5 COMMENT '优先级: 0=低, 5=正常, 10=高',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '已重试次数',
    `max_retries` INT NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    `error_msg` VARCHAR(1000) DEFAULT '' COMMENT '错误信息',
    `aggregation_id` VARCHAR(64) DEFAULT '' COMMENT '聚合批次ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_event_id_channel` (`event_id`, `channel`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_channel_status` (`channel`, `status`),
    INDEX `idx_created_at` (`created_at`),
    INDEX `idx_aggregation_id` (`aggregation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知记录表';

-- 3. 送达日志表
CREATE TABLE IF NOT EXISTS `pcd_notification_delivery_logs` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `notification_id` BIGINT NOT NULL COMMENT '关联通知记录ID',
    `event_id` VARCHAR(128) NOT NULL COMMENT '事件ID',
    `channel` VARCHAR(50) NOT NULL COMMENT '渠道',
    `status` VARCHAR(20) NOT NULL COMMENT '状态: sent, delivered, failed',
    `provider_response` TEXT COMMENT '第三方服务商原始响应',
    `error_msg` VARCHAR(1000) DEFAULT '' COMMENT '错误信息',
    `duration_ms` BIGINT NOT NULL DEFAULT 0 COMMENT '发送耗时（毫秒）',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_notification_id` (`notification_id`),
    INDEX `idx_event_id` (`event_id`),
    INDEX `idx_channel` (`channel`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='送达日志表';

-- 4. 用户通知偏好表
CREATE TABLE IF NOT EXISTS `pcd_notification_preferences` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `channel` VARCHAR(50) NOT NULL COMMENT '渠道',
    `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用该渠道',
    `dnd_start` VARCHAR(5) NOT NULL DEFAULT '22:00' COMMENT '免打扰开始 HH:MM',
    `dnd_end` VARCHAR(5) NOT NULL DEFAULT '07:00' COMMENT '免打扰结束 HH:MM',
    `dnd_enabled` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否开启免打扰',
    `max_per_day` INT NOT NULL DEFAULT 50 COMMENT '每日最大推送数',
    `quiet_hours_json` JSON COMMENT '静音时段: ["22:00-07:00"]',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_channel` (`user_id`, `channel`),
    INDEX `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知偏好表';

-- 5. 设备订阅表
CREATE TABLE IF NOT EXISTS `pcd_notification_device_subscriptions` (
    `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `device_token` VARCHAR(512) NOT NULL COMMENT '设备 Token (APNs/FCM/WebPush)',
    `platform` VARCHAR(20) NOT NULL COMMENT '平台: ios, android, web',
    `app_version` VARCHAR(20) DEFAULT '' COMMENT 'App 版本',
    `is_active` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否活跃',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_device` (`user_id`, `device_token`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_platform` (`platform`),
    INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备订阅表';

-- 6. 聚合窗口表
CREATE TABLE IF NOT EXISTS `pcd_notification_aggregation_windows` (
    `id` VARCHAR(64) PRIMARY KEY COMMENT '聚合窗口ID (UUID)',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `channel` VARCHAR(50) NOT NULL COMMENT '渠道',
    `type` VARCHAR(50) NOT NULL COMMENT '通知类型',
    `record_ids` JSON COMMENT '聚合的通知记录ID列表',
    `count` INT NOT NULL DEFAULT 0 COMMENT '聚合数量',
    `status` VARCHAR(20) NOT NULL DEFAULT 'open' COMMENT '状态: open, closed, sent',
    `window_start` DATETIME NOT NULL COMMENT '窗口开始时间',
    `window_end` DATETIME NOT NULL COMMENT '窗口结束时间',
    `sent_at` DATETIME COMMENT '实际发送时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_window_end` (`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息聚合窗口表';

-- ============================================================================
-- 初始模板数据
-- ============================================================================
INSERT INTO `pcd_notification_templates` (`code`, `name`, `channel`, `lang`, `title`, `body`, `html_body`, `variables_json`, `is_active`) VALUES
-- 邮件模板
('welcome_email', '欢迎邮件', 'email', 'zh-CN',
 '欢迎加入私有云！',
 '亲爱的 {{.userName}}，\n\n欢迎加入私有云！您的专属私有云存储已准备就绪。\n\n开始探索：{{.loginUrl}}\n\n如有任何问题，请随时联系我们。\n\n私有云团队',
 '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0}.container{max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden}.header{background:#2563EB;color:#fff;padding:40px;text-align:center}.header h1{margin:0;font-size:24px}.content{padding:40px}.content p{color:#333;font-size:16px;line-height:1.6}.btn{display:inline-block;background:#2563EB;color:#fff;padding:12px 32px;border-radius:6px;text-decoration:none;font-size:16px;margin:20px 0}.footer{text-align:center;padding:20px;color:#999;font-size:12px}</style></head><body><div class="container"><div class="header"><h1>欢迎加入私有云</h1></div><div class="content"><p>亲爱的 {{.userName}}，</p><p>欢迎加入私有云！您的专属私有云存储已准备就绪。</p><a href="{{.loginUrl}}" class="btn">开始探索</a><p>如有任何问题，请随时联系我们。</p></div><div class="footer"><p>私有云团队</p></div></div></body></html>',
 '[{"name":"userName","type":"string","required":true},{"name":"loginUrl","type":"string","required":true}]', 1),

('verification_email', '验证码邮件', 'email', 'zh-CN',
 '私有云验证码：{{.code}}',
 '您的验证码是：{{.code}}\n\n有效期：{{.expireMinutes}} 分钟\n用途：{{.purposeText}}\n\n如非本人操作，请忽略此邮件。\n\n私有云团队',
 '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0}.container{max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden}.header{background:#2563EB;color:#fff;padding:40px;text-align:center}.header h1{margin:0;font-size:24px}.content{padding:40px;text-align:center}.code{font-size:36px;font-weight:bold;color:#2563EB;letter-spacing:8px;margin:30px 0}.footer{text-align:center;padding:20px;color:#999;font-size:12px}</style></head><body><div class="container"><div class="header"><h1>私有云验证码</h1></div><div class="content"><p>您的验证码是：</p><div class="code">{{.code}}</div><p>有效期：{{.expireMinutes}} 分钟</p><p>用途：{{.purposeText}}</p><p style="color:#999">如非本人操作，请忽略此邮件。</p></div><div class="footer"><p>私有云团队</p></div></div></body></html>',
 '[{"name":"code","type":"string","required":true},{"name":"expireMinutes","type":"int","required":true},{"name":"purposeText","type":"string","required":true}]', 1),

-- 短信模板
('welcome_sms', '欢迎短信', 'sms', 'zh-CN',
 '',
 '【私有云】{{.userName}}，欢迎加入私有云！您的专属云存储已就绪，立即登录体验。',
 NULL,
 '[{"name":"userName","type":"string","required":true}]', 1),

('verification_sms', '验证码短信', 'sms', 'zh-CN',
 '',
 '【私有云】您的验证码是 {{.code}}，{{.expireMinutes}} 分钟内有效。如非本人操作，请忽略。',
 NULL,
 '[{"name":"code","type":"string","required":true},{"name":"expireMinutes","type":"int","required":true}]', 1),

-- 推送模板
('share_notify', '分享通知', 'push', 'zh-CN',
 '{{.senderName}} 与你分享了文件',
 '{{.senderName}} 分享了 {{.fileCount}} 个文件给你',
 NULL,
 '[{"name":"senderName","type":"string","required":true},{"name":"fileCount","type":"int","required":true}]', 1),

('system_notify', '系统通知', 'push', 'zh-CN',
 '系统通知',
 '{{.message}}',
 NULL,
 '[{"name":"message","type":"string","required":true}]', 1),

-- 英文模板
('welcome_email', 'Welcome Email', 'email', 'en-US',
 'Welcome to PrivateCloud!',
 'Dear {{.userName}},\n\nWelcome to PrivateCloud! Your private cloud storage is ready.\n\nGet started: {{.loginUrl}}\n\nIf you have any questions, feel free to reach out.\n\nPrivateCloud Team',
 '<!DOCTYPE html><html><head><meta charset="UTF-8"><style>body{font-family:Arial,sans-serif;background:#f5f5f5;margin:0;padding:0}.container{max-width:600px;margin:0 auto;background:#fff;border-radius:8px;overflow:hidden}.header{background:#2563EB;color:#fff;padding:40px;text-align:center}.header h1{margin:0;font-size:24px}.content{padding:40px}.content p{color:#333;font-size:16px;line-height:1.6}.btn{display:inline-block;background:#2563EB;color:#fff;padding:12px 32px;border-radius:6px;text-decoration:none;font-size:16px;margin:20px 0}.footer{text-align:center;padding:20px;color:#999;font-size:12px}</style></head><body><div class="container"><div class="header"><h1>Welcome to PrivateCloud</h1></div><div class="content"><p>Dear {{.userName}},</p><p>Welcome to PrivateCloud! Your private cloud storage is ready.</p><a href="{{.loginUrl}}" class="btn">Get Started</a><p>If you have any questions, feel free to reach out.</p></div><div class="footer"><p>PrivateCloud Team</p></div></div></body></html>',
 '[{"name":"userName","type":"string","required":true},{"name":"loginUrl","type":"string","required":true}]', 1),

('verification_sms', 'Verification SMS', 'sms', 'en-US',
 '',
 '[PrivateCloud] Your verification code is {{.code}}, valid for {{.expireMinutes}} minutes.',
 NULL,
 '[{"name":"code","type":"string","required":true},{"name":"expireMinutes","type":"int","required":true}]', 1)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`);