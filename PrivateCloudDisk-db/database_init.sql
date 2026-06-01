
/* 
    登录数据库 /usr/local/mysql-8.0.31-macos12-arm64/bin/mysql -u root -p
    创建项目主数据库 
*/
CREATE DATABASE private_cloud_disk;

/* 创建数据库表格 */
USE private_cloud_disk;

CREATE TABLE pcd_user_info_table (
    user_name               VARCHAR(120)    NOT NULL        COMMENT '用户名',
    user_id                 VARCHAR(36)     NOT NULL PRIMARY KEY,
    user_phone_number       VARCHAR(50)     NOT NULL UNIQUE,
    user_image_path         VARCHAR(512)                    COMMENT '用户头像路径',
    user_password           VARCHAR(70)     NOT NULL        COMMENT '用户密码',
    user_account            VARCHAR(70)     NOT NULL UNIQUE COMMENT '用户账号',
    user_email              VARCHAR(70)     UNIQUE          COMMENT '用户邮箱',
) COMMENT='用户信息表';

CREATE TABLE pcd_file_info_table (
    file_name               VARCHAR(150)    NOT NULL                COMMENT '文件名称',
    file_uploaded_time      TIMESTAMP       NOT NULL DEFAULT NOW()  COMMENT '文件上传时间',
    file_size               BIGINT          NOT NULL                COMMENT '文件大小',
    file_type               VARCHAR(60)     NOT NULL                COMMENT '文件类型',
    file_author_id          VARCHAR(36)     NOT NULL                COMMENT '文件作者ID',
    FOREIGN KEY (file_author_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    file_id                 VARCHAR(36)     NOT NULL PRIMARY KEY,
    file_checksum           VARCHAR(256)    NOT NULL                COMMENT '文件校验值',
    file_total_chunks       INT             NOT NULL                COMMENT '文件切片数目', --新增字段
    file_node_id            VARCHAR(36)     NOT NULL                COMMENT '文件所在目录节点ID',
    chunk_storage_path      VARCHAR(512)    NOT NULL                COMMENT '文件切片存储路径前缀',
    FOREIGN KEY (file_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE
) COMMENT='文件信息表';

CREATE TABLE pcd_sharing_Link_mange_table (
    sharing_link_id                     VARCHAR(36)     NOT NULL PRIMARY KEY,
    sharing_link_path                   VARCHAR(512)    NOT NULL                  COMMENT '分享链接路径',
    sharing_link_file_id                VARCHAR(36)     NOT NULL                  COMMENT '分享链接关联的文件ID',
    FOREIGN KEY (sharing_link_file_id) REFERENCES pcd_file_info_table(file_id) ON DELETE CASCADE,
    sharing_link_valid_starting_time    TIMESTAMP       NOT NULL    DEFAULT NOW() COMMENT '分享链接有效开始时间',
    sharing_link_valid_endding_time     TIMESTAMP       NOT NULL                  COMMENT '分享链接有效结束时间',
    sharing_link_password               VARCHAR(60)                               COMMENT '分享链接密码'
) COMMENT='文件分享链接管理表';

/*

    用户上传一个文件 这个文件会被分割成不同的小部分 我们把这个小部分叫做切片 用户不是一次性把一个文件所有的内容传给我们的服务器 而是一个一个上传文件的切片
    给我们的服务器 服务器收到一个文件所有的切片数据之后才会做整理处理
    如果此时用户又上传一个同名文件同类型文件在同一个目录下服务器怎么做处理？
    如果用户从想要上传一个文件 但是上传切片是隔一天上传一个 怎么定义操作的超时时间？
    如果用户只上传了一部分文件切片数据就突然不见了 怎么回滚整个操作？
    服务器怎么知道用户什么时候上传完毕所有的文件切片？
    用户上传一个文件这一整个过程是一个文件上传的操作 我们需要一个东西去记录这个上传文件的操作
    我们把这个记录的东西叫做上传会话 用户上传一个文件的过程都是一个上传的会话它用于跟踪此次上传文件的操作

    用户想要下载一个文件 也不是一次性下载完所有整个的文件数据 而是一部分一部分接受文件切片
    服务器怎么知道用户是否全部接受完毕所有的文件切片数据？
    服务器怎么处理用户不断重复申请接受同一个索引切片数据？
    服务器怎么定义用户此次下载文件操作的超时时间？
    我们把这个记录的东西叫做下载会话 用户下载一个文件的过程都是一个下载的会话它用于跟踪此次下载文件的操作

 */

-- 文件上传的上传会话表 管理单文件的上传流程 主要是用来跟踪保障整个切片上传流程正确进行
CREATE TABLE pcd_uploads_session_table (
    uploads_id              VARCHAR(36)     NOT NULL PRIMARY KEY,
    uploads_user_id         VARCHAR(36)     NOT NULL                                                COMMENT '上传用户ID',
    FOREIGN KEY (uploads_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    uploads_total_chunks    INT             NOT NULL                                                COMMENT '上传切片总数',
    uploads_starting_time   TIMESTAMP       NOT NULL                            DEFAULT NOW()       COMMENT '上传开始时间',
    uploads_endding_time    TIMESTAMP       NOT NULL                                                COMMENT '上传结束时间',
    uploads_file_size       BIGINT          NOT NULL                                                COMMENT '文件大小',
    uploads_file_checksum   VARCHAR(256)    NOT NULL                                                COMMENT '文件校验值',
    uploads_chunks_max_size INT             NOT NULL                                                COMMENT '切片最大大小',
    uploads_file_name       VARCHAR(150)    NOT NULL                                                COMMENT '文件名称',
    uploads_file_type       VARCHAR(60)     NOT NULL                                                COMMENT '文件类型',
    uploads_node_id         VARCHAR(36)     NOT NULL                                                COMMENT '文件所在目录节点ID',
    uploads_status          ENUM('uploading', 'merging', 'completed', 'failed') DEFAULT 'uploading' COMMENT '上传状态',
    FOREIGN KEY (uploads_node_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE
) COMMENT='文件上传会话表';

CREATE TABLE pcd_upload_chunks_table (
    chunk_uploads_id    VARCHAR(36)  NOT NULL                                                COMMENT '关联上传会话ID',
    FOREIGN KEY (chunk_uploads_id) REFERENCES pcd_uploads_session_table(uploads_id) ON DELETE CASCADE,
    chunk_index         INT          NOT NULL                                                COMMENT '切片索引',
    chunk_status        ENUM('pending' ,'uploading', 'uploaded', 'failed') DEFAULT 'pending' COMMENT '切片状态',
    chunk_storage_path  VARCHAR(512) NOT NULL                                                COMMENT '切片存储路径',
    chunk_uploaded_time TIMESTAMP    NOT NULL                              DEFAULT NOW()     COMMENT '切片上传时间',
    PRIMARY KEY (chunk_uploads_id, chunk_index)
) COMMENT='文件切片表';

-- 有点类似于文件夹的元数据表 准确来说是每一条表记录加上关联字段构成了一个目录结构表
-- 可以准确的描述整个文件夹的嵌套结构 利用node_id代替物化路径 提高了安全性和简化了客户端
-- node_status 节点的状态能够解决高并发的服务器环境下导致的异常 类似于逻辑锁 操作时锁定节点
CREATE TABLE pcd_directory_tree_table (
    node_id          VARCHAR(36)     NOT NULL PRIMARY KEY,
    node_user_id     VARCHAR(36)     NOT NULL          COMMENT '所属用户ID',
    FOREIGN KEY (node_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    node_parent_id   VARCHAR(36)                       COMMENT '父节点ID，根节点为NULL',
    FOREIGN KEY (node_parent_id) REFERENCES pcd_directory_tree_table(node_id) ON DELETE CASCADE,
    node_name        VARCHAR(200)    NOT NULL          COMMENT '节点名称',
    node_create_time TIMESTAMP       NOT NULL          COMMENT '节点创建时间'      DEFAULT NOW(),
    node_status      ENUM('lock', 'active', 'pending') COMMENT '节点状态'         DEFAULT 'active'
) COMMENT='节点目录树表';

CREATE TABLE pcd_user_quota_table (
    quota_id              BIGINT          PRIMARY KEY AUTO_INCREMENT,
    quota_user_id         VARCHAR(36)     NOT NULL UNIQUE COMMENT '用户ID，关联用户表',
    FOREIGN KEY (quota_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    quota_total_capacity  BIGINT          NOT NULL DEFAULT 10737418240 COMMENT '总额度（字节），默认10GB = 10*1024^3',
    quota_used_capacity   BIGINT          NOT NULL DEFAULT 0 COMMENT '已用容量（字节）',
    quota_file_count      INT             NOT NULL DEFAULT 0 COMMENT '已上传文件数量',
    quota_version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    quota_created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    quota_updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_id (quota_user_id)
) COMMENT='用户存储配额表';

CREATE TABLE pcd_user_quota_log_table (
    quota_log_id            BIGINT        PRIMARY KEY AUTO_INCREMENT,
    quota_log_user_id       VARCHAR(36)   NOT NULL COMMENT '用户ID，关联用户表',
    FOREIGN KEY (quota_log_user_id) REFERENCES pcd_user_info_table(user_id) ON DELETE CASCADE,
    quota_log_change_type   VARCHAR(20)   NOT NULL COMMENT '变更类型：EXPAND-扩容，REDUCE-缩容，FILE_UPLOAD-文件上传，FILE_DELETE-文件删除',
    quota_log_change_bytes  BIGINT        NOT NULL COMMENT '变更字节数（正为增加，负为减少）',
    quota_log_before_total  BIGINT        COMMENT '变更前总额度',
    quota_log_after_total   BIGINT        COMMENT '变更后总额度',
    quota_log_before_used   BIGINT        COMMENT '变更前已用',
    quota_log_after_used    BIGINT        COMMENT '变更后已用',
    quota_log_operator      VARCHAR(50)   COMMENT '操作人（管理员或系统）' DEFAULT 'SYSTEM',
    quota_log_created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id_time (quota_log_user_id, quota_log_created_at)
) COMMENT='配额变更日志';