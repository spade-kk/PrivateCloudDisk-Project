package org.project.model.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员登录响应 VO
 */
@Data
public class AdminLoginVO {
    private String accessToken;
    private String refreshToken;
    private AdminUserVO adminInfo;
}