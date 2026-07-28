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

    /**
     * 标签颜色。
     * 【需求十二】原行为仅校验非空，任意字符串可能进入 style 和数据库；
     * 新行为在 DTO 边界只接受六位 HEX，创建和更新接口复用本 DTO，影响范围仅限标签颜色。
     */
    @NotBlank(message = "标签Color不能为空")
    @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "标签颜色必须为六位HEX格式，例如#FF5733")
    private String tag_color;
}
