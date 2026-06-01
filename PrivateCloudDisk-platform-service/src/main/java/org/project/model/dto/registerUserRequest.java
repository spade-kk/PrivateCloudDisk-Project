package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class registerUserRequest {

    @NotBlank(message = "手机号不能为空")
    @Pattern( regexp = "^1[3-9]\\d{9}$",
              message = "手机号格式不正确")
    String phone_number;

    @NotBlank(message = "密码不能为空")
    @Pattern( regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,15}$",
              message = "密码必须是8-15位，包含字母、数字")
    String password;

    @NotBlank(message = "验证码不能为空")
    @Pattern( regexp = "^[a-zA-Z0-9]{6}$",
              message = "验证码必须是6位数字或字母")
    String code;

    @NotBlank(message = "用户名不能为空")
    @Pattern( regexp = "^[a-zA-Z0-9]{2,10}$",
              message = "用户名必须是2-10位数字或字母")
    String name;
}
