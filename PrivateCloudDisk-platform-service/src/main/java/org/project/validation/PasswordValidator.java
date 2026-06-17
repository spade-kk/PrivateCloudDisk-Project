package org.project.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.regex.Pattern;

/**
 * {@link ValidPassword} 注解的校验器实现。
 * <p>
 * 支持两种密码格式的校验：
 * <ul>
 *   <li><b>原始密码</b>：8-128 位，至少包含一个字母和一个数字，允许常见特殊字符</li>
 *   <li><b>PBKDF2-SHA256 预哈希密码</b>：严格 64 位十六进制字符串（小写 a-f 或大写 A-F，数字 0-9）</li>
 * </ul>
 * <p>
 * 校验策略：先尝试匹配预哈希格式（高优先级），不匹配则尝试原始密码格式。
 * 这样可以避免将 64 位纯字母数字的原始密码误判为预哈希格式。
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    /**
     * PBKDF2-SHA256 预哈希密码格式：64 位十六进制字符串
     * <p>
     * 前端使用 PBKDF2-SHA256（60 万次迭代）生成 256 位哈希，
     * 十六进制编码后为 64 个字符。
     */
    private static final Pattern PRE_HASHED_PATTERN =
            Pattern.compile("^[a-fA-F0-9]{64}$");

    /**
     * 原始密码格式：8-128 位，至少包含一个字母和一个数字
     * <p>
     * 允许的字符集：字母、数字、以及常见特殊字符
     * 注意：不包含空格和控制字符，防止注入攻击
     */
    private static final Pattern RAW_PASSWORD_ALPHA =
            Pattern.compile("[A-Za-z]");
    private static final Pattern RAW_PASSWORD_DIGIT =
            Pattern.compile("\\d");
    private static final Pattern RAW_PASSWORD_INVALID =
            Pattern.compile("[^A-Za-z0-9!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?]");

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        // null 值由 @NotBlank 处理，此处直接放行
        if (password == null || password.isEmpty()) {
            return true;
        }

        // 格式一：PBKDF2-SHA256 预哈希密码（64 位十六进制）
        if (PRE_HASHED_PATTERN.matcher(password).matches()) {
            return true;
        }

        // 格式二：原始密码（8-128 位，含字母+数字+允许的特殊字符）
        if (password.length() < 8 || password.length() > 128) {
            return false;
        }
        if (RAW_PASSWORD_INVALID.matcher(password).find()) {
            return false;
        }
        if (!RAW_PASSWORD_ALPHA.matcher(password).find()) {
            return false;
        }
        if (!RAW_PASSWORD_DIGIT.matcher(password).find()) {
            return false;
        }

        return true;
    }
}