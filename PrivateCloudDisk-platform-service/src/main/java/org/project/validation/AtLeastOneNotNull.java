package org.project.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AtLeastOneNotNullValidator.class)
@Documented
public @interface AtLeastOneNotNull {

    String message() default "至少需要提供一个字段";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    // 需要检查的字段名数组
    String[] fieldNames();
}
