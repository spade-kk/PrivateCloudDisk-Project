package org.project.model.vo;

/**
 * [REQ-GIT-PERM-9.4] Git Service 查询团队/企业空间成员关系时使用的最小内部响应。
 * 不返回成员资料，避免把空间成员信息扩散到 Git Service。
 */
public record InternalGitTeamMembershipVO(
        boolean member,
        String teamId,
        String userId,
        String role
) {
}
