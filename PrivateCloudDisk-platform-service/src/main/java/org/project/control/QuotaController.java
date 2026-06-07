package org.project.control;

import org.project.control.result.JsonResult;
import org.project.mapper.QuotaMapper;
import org.project.model.entity.QuotaEntity;
import org.project.model.vo.QuotaVO;
import org.project.model.vo.VoMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/quotas")
public class QuotaController extends BaseController {
    @Autowired
    private QuotaMapper quotaMapper;

    @GetMapping("/me")
    public JsonResult<QuotaVO> queryMyQuota(@RequestHeader("X-User-Id") String user_id) {
        QuotaEntity quota = quotaMapper.findQuotaByUserId(user_id);
        return new JsonResult<>(OK, VoMapper.toQuotaVO(quota));
    }
}
