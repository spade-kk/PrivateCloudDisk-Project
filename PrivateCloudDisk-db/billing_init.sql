-- ============================================================
-- PrivateCloudDisk 计费与订阅服务 - 数据库初始化脚本
-- 企业级计费系统: 订阅管理 + 按量计费 + 支付 + 发票 + 优惠券
-- ============================================================

-- 如果使用独立数据库，取消下面注释
-- CREATE DATABASE IF NOT EXISTS private_cloud_disk_billing DEFAULT CHARACTER SET utf8mb4;
-- USE private_cloud_disk_billing;

-- ============================================================
-- 1. pcd_subscription_plan_table (订阅计划表)
-- 定义系统支持的订阅计划: 免费版/专业版/企业版
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_subscription_plan_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `plan_code`             VARCHAR(64)     NOT NULL UNIQUE COMMENT '计划编码 (free/pro/enterprise)',
    `plan_name`             VARCHAR(128)    NOT NULL COMMENT '计划名称',
    `plan_tier`             TINYINT         NOT NULL DEFAULT 0 COMMENT '套餐等级 (0=免费, 1=专业, 2=企业)',
    `description`           TEXT            COMMENT '计划描述',
    `storage_limit_bytes`   BIGINT          NOT NULL DEFAULT 0 COMMENT '存储配额(字节)',
    `max_file_size_bytes`   BIGINT          NOT NULL DEFAULT 0 COMMENT '单文件最大大小(字节)',
    `max_share_links`       INT             NOT NULL DEFAULT 0 COMMENT '最大分享链接数',
    `max_download_speed`    INT             NOT NULL DEFAULT 0 COMMENT '最大下载速度(KB/s, 0=不限)',
    `features_json`         JSON            COMMENT '功能权限列表(JSON)',
    `price_monthly`         DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '月付价格(元)',
    `price_yearly`          DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '年付价格(元)',
    `price_quarterly`       DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '季付价格(元)',
    `overage_unit_price`    DECIMAL(10,4)   NOT NULL DEFAULT 0.0000 COMMENT '超额单价(元/GB/天)',
    `trial_days`            INT             NOT NULL DEFAULT 0 COMMENT '试用天数',
    `sort_order`            INT             NOT NULL DEFAULT 0 COMMENT '排序序号',
    `is_active`             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订阅计划表';

-- 默认计划数据
INSERT INTO `pcd_subscription_plan_table` (`plan_code`, `plan_name`, `plan_tier`, `description`, `storage_limit_bytes`, `max_file_size_bytes`, `max_share_links`, `max_download_speed`, `features_json`, `price_monthly`, `price_yearly`, `price_quarterly`, `overage_unit_price`, `trial_days`, `sort_order`) VALUES
('free',          '免费版',   0, '基础存储，适合个人日常使用',               10737418240,   1073741824,     10,      0,     '{"video_preview":true,"ai_tagging":false,"advanced_search":false,"api_access":false,"priority_support":false}',            0.00,  0.00,   0.00,   0.1000, 0,  1),
('pro',           '专业版',   1, '大容量存储，适合专业用户和团队',             1099511627776, 10737418240,    100,     0,     '{"video_preview":true,"ai_tagging":true,"advanced_search":true,"api_access":true,"priority_support":false}',                29.90, 299.00, 89.90,  0.0800, 7,  2),
('enterprise',    '企业版',   2, '无限存储，适合企业和组织',                   0,             0,              999999,  0,     '{"video_preview":true,"ai_tagging":true,"advanced_search":true,"api_access":true,"priority_support":true,"sso":true}',     99.90, 999.00, 299.00, 0.0500, 14, 3);

-- ============================================================
-- 2. pcd_user_subscription_table (用户订阅表)
-- 记录每个用户当前的订阅状态
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_user_subscription_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `plan_id`               BIGINT          NOT NULL COMMENT '订阅计划ID',
    `status`                VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/EXPIRED/CANCELLED/GRACE_PERIOD',
    `billing_cycle`         VARCHAR(16)     NOT NULL DEFAULT 'MONTHLY' COMMENT '计费周期: MONTHLY/QUARTERLY/YEARLY',
    `start_date`            DATETIME        NOT NULL COMMENT '订阅开始时间',
    `end_date`              DATETIME        NOT NULL COMMENT '订阅到期时间',
    `auto_renew`            TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否自动续费',
    `cancelled_at`          DATETIME        COMMENT '取消时间',
    `trial_started_at`      DATETIME        COMMENT '试用开始时间',
    `trial_ended_at`        DATETIME        COMMENT '试用结束时间',
    `last_billing_date`     DATETIME        COMMENT '上次扣费日期',
    `next_billing_date`     DATETIME        COMMENT '下次扣费日期',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY `uk_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_end_date` (`end_date`),
    KEY `idx_next_billing_date` (`next_billing_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户订阅表';

-- ============================================================
-- 3. pcd_order_table (订单表)
-- 核心订单数据，保证ACID
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_order_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `order_no`              VARCHAR(64)     NOT NULL UNIQUE COMMENT '订单号 (业务主键)',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `order_type`            VARCHAR(32)     NOT NULL COMMENT '订单类型: SUBSCRIPTION/OVERAGE/UPGRADE/RENEWAL',
    `plan_id`               BIGINT          COMMENT '关联订阅计划ID',
    `billing_cycle`         VARCHAR(16)     COMMENT '计费周期: MONTHLY/QUARTERLY/YEARLY',
    `amount_original`       DECIMAL(10,2)   NOT NULL COMMENT '原始金额(元)',
    `amount_discount`       DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '优惠金额(元)',
    `amount_payable`        DECIMAL(10,2)   NOT NULL COMMENT '应付金额(元)',
    `amount_paid`           DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '实付金额(元)',
    `currency`              VARCHAR(8)      NOT NULL DEFAULT 'CNY' COMMENT '币种',
    `status`                VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PAID/PROCESSING/COMPLETED/CANCELLED/REFUNDED/EXPIRED',
    `payment_method`        VARCHAR(32)     COMMENT '支付方式: ALIPAY/WECHAT/APPLE_IAP',
    `payment_channel`       VARCHAR(32)     COMMENT '支付渠道: ALIPAY_WAP/ALIPAY_QR/WECHAT_JSAPI/WECHAT_NATIVE/APPLE_IAP',
    `third_party_trade_no`  VARCHAR(128)    COMMENT '第三方交易号',
    `coupon_id`             BIGINT          COMMENT '使用的优惠券ID',
    `coupon_code`           VARCHAR(64)     COMMENT '使用的优惠券码',
    `refund_amount`         DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '退款金额(元)',
    `refund_reason`         VARCHAR(512)    COMMENT '退款原因',
    `refunded_at`           DATETIME        COMMENT '退款时间',
    `paid_at`               DATETIME        COMMENT '支付时间',
    `expired_at`            DATETIME        COMMENT '订单过期时间',
    `remark`                VARCHAR(512)    COMMENT '备注',
    `extra_params`          JSON            COMMENT '扩展参数(JSON)',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_order_type` (`order_type`),
    KEY `idx_created_at` (`created_at`),
    KEY `idx_paid_at` (`paid_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ============================================================
-- 4. pcd_payment_callback_log_table (支付回调日志表)
-- 记录所有第三方支付回调，用于对账和排错
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_payment_callback_log_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `order_no`              VARCHAR(64)     NOT NULL COMMENT '订单号',
    `payment_method`        VARCHAR(32)     NOT NULL COMMENT '支付方式',
    `callback_type`         VARCHAR(32)     NOT NULL COMMENT '回调类型: PAYMENT/REFUND',
    `third_party_trade_no`  VARCHAR(128)    COMMENT '第三方交易号',
    `callback_raw`          TEXT            COMMENT '回调原始数据',
    `callback_status`       VARCHAR(32)     NOT NULL COMMENT '回调处理状态: SUCCESS/FAILED/RETRYING',
    `error_message`         VARCHAR(1024)   COMMENT '错误信息',
    `retry_count`           INT             NOT NULL DEFAULT 0 COMMENT '重试次数',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_order_no` (`order_no`),
    KEY `idx_callback_status` (`callback_status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付回调日志表';

-- ============================================================
-- 5. pcd_invoice_table (发票表)
-- 电子发票申请和开具
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_invoice_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `invoice_no`            VARCHAR(64)     NOT NULL UNIQUE COMMENT '发票号',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `order_id`              BIGINT          NOT NULL COMMENT '关联订单ID',
    `invoice_type`          VARCHAR(32)     NOT NULL DEFAULT 'ELECTRONIC' COMMENT '发票类型: ELECTRONIC/PAPER',
    `invoice_title_type`    VARCHAR(32)     NOT NULL COMMENT '抬头类型: PERSONAL/ENTERPRISE',
    `invoice_title`         VARCHAR(256)    NOT NULL COMMENT '发票抬头',
    `tax_no`                VARCHAR(64)     COMMENT '纳税人识别号',
    `invoice_amount`        DECIMAL(10,2)   NOT NULL COMMENT '发票金额(元)',
    `tax_amount`            DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '税额(元)',
    `status`                VARCHAR(32)     NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/ISSUED/FAILED',
    `file_url`              VARCHAR(512)    COMMENT '发票文件URL(PDF)',
    `email`                 VARCHAR(256)    COMMENT '接收发票邮箱',
    `remark`                VARCHAR(512)    COMMENT '备注',
    `issued_at`             DATETIME        COMMENT '开具时间',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='发票表';

-- ============================================================
-- 6. pcd_coupon_table (优惠券模板表)
-- 优惠券/促销码管理
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_coupon_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `coupon_code`           VARCHAR(64)     NOT NULL UNIQUE COMMENT '优惠券码',
    `coupon_name`           VARCHAR(128)    NOT NULL COMMENT '优惠券名称',
    `coupon_type`           VARCHAR(32)     NOT NULL COMMENT '类型: DISCOUNT/FIXED_AMOUNT/TRIAL_EXTEND',
    `discount_percent`      DECIMAL(5,2)    COMMENT '折扣百分比 (如 80.00 表示打8折)',
    `fixed_amount`          DECIMAL(10,2)   COMMENT '固定减免金额(元)',
    `min_order_amount`      DECIMAL(10,2)   NOT NULL DEFAULT 0.00 COMMENT '最低订单金额(元)',
    `max_discount_amount`   DECIMAL(10,2)   COMMENT '最大优惠金额(元)',
    `applicable_plans`      VARCHAR(512)    COMMENT '适用计划(逗号分隔, 空=全部)',
    `applicable_user_level` VARCHAR(32)     COMMENT '适用用户等级: NEW_USER/ALL',
    `total_quantity`        INT             NOT NULL DEFAULT 0 COMMENT '总发行量',
    `used_quantity`         INT             NOT NULL DEFAULT 0 COMMENT '已使用量',
    `per_user_limit`        INT             NOT NULL DEFAULT 1 COMMENT '每人限用次数',
    `valid_from`            DATETIME        NOT NULL COMMENT '有效期开始',
    `valid_to`              DATETIME        NOT NULL COMMENT '有效期结束',
    `is_active`             TINYINT(1)      NOT NULL DEFAULT 1 COMMENT '是否启用',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    KEY `idx_coupon_code` (`coupon_code`),
    KEY `idx_valid_from_to` (`valid_from`, `valid_to`),
    KEY `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='优惠券模板表';

-- 默认优惠券: 新用户首月 5 折
INSERT INTO `pcd_coupon_table` (`coupon_code`, `coupon_name`, `coupon_type`, `discount_percent`, `min_order_amount`, `max_discount_amount`, `applicable_plans`, `applicable_user_level`, `total_quantity`, `per_user_limit`, `valid_from`, `valid_to`) VALUES
('NEWUSER50', '新用户首月5折', 'DISCOUNT', 50.00, 0.00, 50.00, 'pro,enterprise', 'NEW_USER', 10000, 1, '2024-01-01 00:00:00', '2030-12-31 23:59:59');

-- ============================================================
-- 7. pcd_user_coupon_table (用户优惠券领取/使用记录表)
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_user_coupon_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `coupon_id`             BIGINT          NOT NULL COMMENT '优惠券ID',
    `coupon_code`           VARCHAR(64)     NOT NULL COMMENT '优惠券码',
    `status`                VARCHAR(32)     NOT NULL DEFAULT 'UNUSED' COMMENT '状态: UNUSED/USED/EXPIRED',
    `order_id`              BIGINT          COMMENT '使用的订单ID',
    `used_at`               DATETIME        COMMENT '使用时间',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '领取时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_coupon_id` (`coupon_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户优惠券表';

-- ============================================================
-- 8. pcd_usage_record_table (用量记录表)
-- 按量计费：记录用户超额使用量
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_usage_record_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `record_date`           DATE            NOT NULL COMMENT '记录日期',
    `storage_used_bytes`    BIGINT          NOT NULL DEFAULT 0 COMMENT '当日存储用量(字节)',
    `storage_limit_bytes`   BIGINT          NOT NULL DEFAULT 0 COMMENT '当日存储配额(字节)',
    `storage_overage_bytes` BIGINT          NOT NULL DEFAULT 0 COMMENT '超额存储量(字节)',
    `traffic_used_bytes`    BIGINT          NOT NULL DEFAULT 0 COMMENT '当日流量用量(字节)',
    `traffic_limit_bytes`   BIGINT          NOT NULL DEFAULT 0 COMMENT '当日流量配额(字节)',
    `traffic_overage_bytes` BIGINT          NOT NULL DEFAULT 0 COMMENT '超额流量(字节)',
    `overage_cost`          DECIMAL(10,4)   NOT NULL DEFAULT 0.0000 COMMENT '超额费用(元)',
    `is_billed`             TINYINT(1)      NOT NULL DEFAULT 0 COMMENT '是否已计费',
    `billing_order_id`      BIGINT          COMMENT '关联计费订单ID',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY `uk_user_date` (`user_id`, `record_date`),
    KEY `idx_is_billed` (`is_billed`),
    KEY `idx_record_date` (`record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用量记录表';

-- ============================================================
-- 9. pcd_billing_event_table (计费事件表)
-- 审计日志: 记录所有计费相关事件
-- ============================================================
CREATE TABLE IF NOT EXISTS `pcd_billing_event_table` (
    `id`                    BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    `user_id`               CHAR(36)        NOT NULL COMMENT '用户UUID',
    `event_type`            VARCHAR(64)     NOT NULL COMMENT '事件类型: SUBSCRIPTION_CREATED/SUBSCRIPTION_RENEWED/SUBSCRIPTION_EXPIRED/ORDER_CREATED/ORDER_PAID/ORDER_REFUNDED/INVOICE_ISSUED/COUPON_USED/OVERAGE_BILLED',
    `event_data`            JSON            COMMENT '事件数据(JSON)',
    `operator`              VARCHAR(64)     COMMENT '操作者: SYSTEM/USER_ID/ADMIN_ID',
    `created_at`            DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY `idx_user_id` (`user_id`),
    KEY `idx_event_type` (`event_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='计费事件表';

-- ============================================================
-- Seata AT 模式所需的 undo_log 表
-- 如果计费服务使用独立数据库，需要执行此表
-- ============================================================
CREATE TABLE IF NOT EXISTS `undo_log` (
    `id`            BIGINT(20)   NOT NULL AUTO_INCREMENT,
    `branch_id`     BIGINT(20)   NOT NULL,
    `xid`           VARCHAR(128) NOT NULL,
    `context`       VARCHAR(128) NOT NULL,
    `rollback_info` LONGBLOB     NOT NULL,
    `log_status`    INT(11)      NOT NULL,
    `log_created`   DATETIME     NOT NULL,
    `log_modified`  DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_unionkey` (`xid`, `branch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;