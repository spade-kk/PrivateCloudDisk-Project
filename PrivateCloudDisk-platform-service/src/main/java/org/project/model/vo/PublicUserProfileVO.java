package org.project.model.vo;

import lombok.Data;
import java.util.List;

/** [SPACE-COLLAB-USER-02] 成员邀请/资料页最小公开资料，禁止携带密码、手机号和邮箱。 */
@Data
public class PublicUserProfileVO {
    private String userId;
    private String username;
    private String account;
    private String avatarPath;
    // 公开仓库用户主页兼容字段：协作资料接口不填充仓库列表。
    private String displayName;
    private String avatar;
    private List<PublicSpaceDetailVO> repositories;
}
