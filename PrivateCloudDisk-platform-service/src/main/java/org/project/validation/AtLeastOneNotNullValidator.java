package org.project.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.BeanWrapperImpl;

public class AtLeastOneNotNullValidator implements ConstraintValidator<AtLeastOneNotNull, Object> {

    private String[] fieldNames;

    @Override
    public void initialize(AtLeastOneNotNull constraintAnnotation) {
        this.fieldNames = constraintAnnotation.fieldNames();
    }

    @Override
    public boolean isValid(Object value, ConstraintValidatorContext context) {
        if (value == null) {
            return true; // 通常由 @NotNull 保证不为 null
        }
        BeanWrapperImpl wrapper = new BeanWrapperImpl(value);
        for (String fieldName : fieldNames) {
            Object propertyValue = wrapper.getPropertyValue(fieldName);
            if (propertyValue != null) {
                // 如果需要校验非空字符串，可进一步判断
                return true;
            }
        }
        // 全部为 null，校验失败
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(
                        context.getDefaultConstraintMessageTemplate())
                .addPropertyNode(fieldNames[0]) // 将错误绑定到第一个字段
                .addConstraintViolation();
        return false;
    }
}
