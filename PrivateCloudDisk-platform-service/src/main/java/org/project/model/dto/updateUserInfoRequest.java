package org.project.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class updateUserInfoRequest {
    @NotBlank
    @Email(message = "邮箱格式不正确")
    String new_email;

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$",
            message = "手机号格式不正确")
    String new_phone_number;

    @NotBlank
    @Pattern( regexp = "^[a-zA-Z0-9]{2,10}$",
            message = "用户名必须是2-10位数字或字母")
    String new_name;
}
