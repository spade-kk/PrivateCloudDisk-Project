package org.project.im.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 群组角色枚举
 * <p>
 * 定义群组成员的权限层级：
 * <ul>
 *   <li>OWNER：群主，拥有所有权限（解散群、转让群、踢人、禁言等）</li>
 *   <li>ADMIN：管理员，可踢人、禁言、审批加群</li>
 *   <li>MEMBER：普通成员，仅可发消息、退出群</li>
 *   <li>MUTED：被禁言成员，不可发消息</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum GroupRole {

    /** 群主 */
    OWNER(1, "群主"),

    /** 管理员 */
    ADMIN(2, "管理员"),

    /** 普通成员 */
    MEMBER(3, "成员"),

    /** 被禁言成员 */
    MUTED(4, "禁言中");

    private final int code;
    private final String description;

    public static GroupRole fromCode(int code) {
        for (GroupRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return MEMBER;
    }
}