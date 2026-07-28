package org.project.model.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 分享访问令牌 VO（密码验证通过后返回，v2）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShareAccessTokenVO {
    /** 访问令牌（JWT，短期有效，仅用于此次分享的只读访问） */
    private String access_token;

    /** 分享名称 */
    private String share_name;

    /** 资源数量 */
    private Integer resource_count;
}