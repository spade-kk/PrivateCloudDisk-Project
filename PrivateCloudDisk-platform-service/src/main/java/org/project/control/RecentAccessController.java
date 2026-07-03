package org.project.control;

import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.vo.RecentAccessVO;
import org.project.service.RecentAccessService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 最近访问记录控制器
 *
 * <p>API 设计：
 * <ul>
 *   <li>GET /business/recent                    — 获取所有最近访问（混合）</li>
 *   <li>GET /business/recent?type=upload        — 获取最近上传</li>
 *   <li>GET /business/recent?type=download      — 获取最近下载</li>
 *   <li>GET /business/recent?type=open          — 获取最近打开</li>
 * </ul>
 */
@RestController
@Validated
@RequiredArgsConstructor
@RequestMapping("/business/recent")
public class RecentAccessController extends BaseController {

    private final RecentAccessService recentAccessService;

    /**
     * 获取最近访问记录
     *
     * @param type 访问类型筛选：upload / download / open（为空则返回所有类型混合）
     */
    @GetMapping
    public JsonResult<List<RecentAccessVO>> getRecentAccess(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestHeader("X-User-Id") String user_id) {

        List<RecentAccessVO> records;
        if (type != null && !type.isEmpty()) {
            records = recentAccessService.getRecentAccess(UUID.fromString(user_id), type, page, pageSize);
        } else {
            records = recentAccessService.getAllRecentAccess(UUID.fromString(user_id), page, pageSize);
        }
        return new JsonResult<>(OK, records);
    }
}