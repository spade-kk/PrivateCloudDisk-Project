package org.project.im.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 好友关系对外 DTO，不暴露内部请求与审核字段。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FriendDTO implements Serializable {

    private String friendId;
    /** 仅返回可公开展示的好友资料，不返回邮箱、手机号等账号敏感字段。 */
    private String username;
    private String account;
    private String avatarPath;
    private String remark;
    private Boolean starred;
    private Boolean online;
    private Integer commonSpaceCount;
    private Integer commonGroupCount;
    private Integer status;
    private LocalDateTime createdAt;
}
