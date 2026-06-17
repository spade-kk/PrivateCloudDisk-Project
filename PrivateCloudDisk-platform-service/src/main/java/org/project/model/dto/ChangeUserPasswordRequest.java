package org.project.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.project.validation.ValidPassword;

/**
 * 修改密码请求 DTO。
 * <p>
 * 密码字段同时支持原始密码和 PBKDF2-SHA256 预哈希密码两种格式。
 */
@Data
public class ChangeUserPasswordRequest {
    @NotBlank(message = "旧密码不能为空")
    @ValidPassword
    @JsonAlias("old_password")
    String user_password;
    @NotBlank(message = "新密码不能为空")
    @ValidPassword
    String new_password;
}
