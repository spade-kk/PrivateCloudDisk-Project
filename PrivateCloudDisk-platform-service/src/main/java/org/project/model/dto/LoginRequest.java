package org.project.model.dto;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.project.validation.AtLeastOneNotNull;

@AtLeastOneNotNull(fieldNames = {"phone_number", "account"}, message = "手机号和账号至少一个不能为空")
@Data
public class LoginRequest {

    @Pattern(regexp = "^[a-zA-Z0-9_]{4,16}$",
            message = "账号格式不正确")
    private String account;

    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    private String phone_number;

    @NotBlank(message = "密码不能为空")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,15}$",
            message = "密码必须是8-15位，包含字母、数字")
    private String password;
}
