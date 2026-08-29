package org.project.control;

import org.project.control.result.JsonResult;
import org.project.mapper.UserMapper;
import org.project.model.entity.UserEntity;
import org.project.model.vo.PublicUserProfileVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * [USER-DIRECTORY-20260810] 平台统一用户公开资料和搜索接口。
 * IM、空间协作、公开空间用户主页均复用本控制器，不在各业务微服务复制用户表查询。
 */
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
                                                        @RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(required = false) Integer size,
                                                        @RequestHeader("X-User-Id") UUID viewerId) {
        if (keyword == null || keyword.trim().length() < 2) return new JsonResult<>(OK, List.of());
        // 保留 limit 旧参数语义；新调用方可使用 size + page，响应仍为 List，避免破坏空间协作旧客户端。
        int safeSize = Math.min(Math.max(size == null ? limit : size, 1), 100);
        int safePage = Math.max(page, 1);
        int offset = (safePage - 1) * safeSize;
        return new JsonResult<>(OK, userMapper.searchPublicUsers(keyword.trim(), offset, safeSize)
                .stream().map(this::toPublic).toList());
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
