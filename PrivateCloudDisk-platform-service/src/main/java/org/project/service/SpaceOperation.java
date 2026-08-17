package org.project.service;

/**
 * 空间权限操作枚举。
 *
 * <p>需求：空间管理能力全量集成（三-2/3）。
 * 当前数据表保留 can_read/can_write/can_delete/can_share/can_manage，
 * 因而下载映射为读取、上传与编辑映射为写入；后续若扩展独立权限列，
 * 只需调整 SpacePermissionServiceImpl，不影响各业务接口。</p>
 */
public enum SpaceOperation {
    VIEW,
    READ,
    DOWNLOAD,
    UPLOAD,
    EDIT,
    DELETE,
    SHARE,
    MANAGE,
    MANAGE_MEMBERS,
    MANAGE_PLUGINS,
    MANAGE_SETTINGS
}
