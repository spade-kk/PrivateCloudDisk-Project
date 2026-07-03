package org.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 标签创建请求 DTO。
 */
@Data
public class TagCreateRequest {

    /** 标签名字 */
    @NotBlank(message = "标签名字不能为空")
    private String tag_name;

    /** 标签Color */
    @NotBlank(message = "标签Color不能为空")
    private String tag_color;
}