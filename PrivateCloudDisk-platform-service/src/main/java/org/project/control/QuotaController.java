package org.project.control;

import org.project.control.result.JsonResult;
import org.project.context.SpaceContextHolder;
import org.project.mapper.SpaceQuotaMapper;
import org.project.model.vo.QuotaVO;
import org.project.model.vo.SpaceQuotaVO;
import org.project.service.SpaceOperation;
import org.project.service.SpacePermissionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/business/quotas")
public class QuotaController extends BaseController {
    @Autowired
    private SpaceQuotaMapper spaceQuotaMapper;
    @Autowired
    private SpacePermissionService spacePermissionService;

    @GetMapping("/me")
    public JsonResult<QuotaVO> queryMyQuota(@RequestHeader("X-User-Id") String user_id) {
        UUID userId = UUID.fromString(user_id);
        SpaceContextHolder.SpaceContext existingContext = SpaceContextHolder.get();
        final SpaceContextHolder.SpaceContext context = existingContext == null
                ? spacePermissionService.resolveContext(userId, null)
                : existingContext;
        spacePermissionService.requireOperation(context, SpaceOperation.VIEW);

        SpaceQuotaVO current = spaceQuotaMapper.findAllVisibleSpaceQuotas(userId).stream()
                .filter(item -> context.spaceId().equals(item.getSpace_id()))
                .findFirst()
                .orElse(null);
        if (current == null) {
            return new JsonResult<>(OK, null);
        }

        // 需求五-10：保持原 /me 响应结构不变，仅把数据源切换为当前空间。
        QuotaVO quota = new QuotaVO();
        quota.setUser_id(user_id);
        quota.setTotal_capacity(current.getTotal_quota());
        quota.setUsed_capacity(current.getUsed_quota());
        quota.setFile_count(current.getFile_count());
        quota.setVersion(0);
        return new JsonResult<>(OK, quota);
    }

    /**
     * 查询当前用户有查看权限的全部空间配额。
     *
     * <p>需求五-10：该接口不依赖 X-Space-Id，固定返回包含默认“我的网盘”的数组。</p>
     */
    @GetMapping("/space-quotas")
    public JsonResult<List<SpaceQuotaVO>> queryAllSpaceQuotas(
            @RequestHeader("X-User-Id") String user_id) {
        return new JsonResult<>(OK,
                spaceQuotaMapper.findAllVisibleSpaceQuotas(UUID.fromString(user_id)));
    }
}
