package org.project.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * 自定义密码格式校验注解。
 * <p>
 * 同时支持两种密码格式：
 * <ol>
 *   <li><b>原始密码</b>：8-128 位，至少包含一个字母和一个数字，允许特殊字符</li>
 *   <li><b>PBKDF2-SHA256 预哈希密码</b>：64 位十六进制字符串（前端 60 万次迭代后传输）</li>
 * </ol>
 * <p>
 * 使用示例：
 * <pre>{@code
 * @ValidPassword
 * private String password;
 * }</pre>
 *
 * @see PasswordValidator 实际校验逻辑
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = PasswordValidator.class)
@Documented
public @interface ValidPassword {

    String message() default "密码格式不正确：原始密码需8-128位且包含字母和数字，或为64位十六进制预哈希密码";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}