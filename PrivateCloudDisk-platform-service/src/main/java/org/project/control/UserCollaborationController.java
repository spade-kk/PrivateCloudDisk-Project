package org.project.control;

import org.project.control.result.JsonResult;
import org.project.mapper.UserMapper;
import org.project.model.entity.UserEntity;
import org.project.model.vo.PublicUserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** [SPACE-COLLAB-USER-03] 用户公开资料和邀请搜索接口。 */
@RestController
@RequestMapping("/business/users")
public class UserCollaborationController extends BaseController {
    @Autowired
    private UserMapper userMapper;

    @GetMapping("/{userId}/profile")
    public JsonResult<PublicUserProfileVO> profile(@PathVariable UUID userId,
                                                   @RequestHeader("X-User-Id") UUID viewerId) {
        return new JsonResult<>(OK, toPublic(userMapper.findUserById(userId)));
    }

    @GetMapping("/search")
    public JsonResult<List<PublicUserProfileVO>> search(@RequestParam("q") String keyword,
                                                        @RequestParam(defaultValue = "20") int limit,
                                                        @RequestHeader("X-User-Id") UUID viewerId) {
        if (keyword == null || keyword.trim().length() < 2) return new JsonResult<>(OK, List.of());
        int safeLimit = Math.min(Math.max(limit, 1), 20);
        return new JsonResult<>(OK, userMapper.searchPublicUsers(keyword.trim(), safeLimit).stream().map(this::toPublic).toList());
    }

    private PublicUserProfileVO toPublic(UserEntity entity) {
        if (entity == null) return null;
        PublicUserProfileVO vo = new PublicUserProfileVO();
        vo.setUserId(entity.getId() == null ? null : entity.getId().toString());
        vo.setUsername(entity.getName());
        vo.setAccount(entity.getAccount());
        vo.setAvatarPath(entity.getImage_path());
        return vo;
    }
}
