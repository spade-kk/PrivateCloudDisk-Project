package org.project.model.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理员审计日志数据类
 */
@Data
public class AdminAuditLogEntity implements Serializable {
    private Long auditId;
    private UUID auditAdminId;
    private String auditAdminName;
    private String auditAdminRole;
    private String auditAction;
    private String auditResource;
    private String auditResourceId;
    private String auditDetail;
    private String auditRequestMethod;
    private String auditRequestPath;
    private String auditClientIp;
    private String auditUserAgent;
    private String auditStatus;         // SUCCESS / FAILURE
    private String auditErrorMessage;
    private LocalDateTime auditCreatedAt;
}