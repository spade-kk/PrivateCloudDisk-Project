package org.project.control;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.project.control.result.JsonResult;
import org.project.model.dto.CreateUploadsSessionRequest;
import org.project.security.ApiAbuseProtectionService;
import org.project.service.UploadsService;
import org.project.util.ClientIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/uploads")
public class UploadsController extends BaseController {
    @Autowired
    private UploadsService uploadsService;

    @Autowired
    private ApiAbuseProtectionService apiAbuseProtectionService;
    /**
     * 处理上传会话创建的请求
     * @param createUploadsSessionRequest 创建上传会话请求体参数Json对象
     * @return JsonResult data String
     */
    @PostMapping("/")
    public JsonResult<String> createUploadsSession(
            @RequestHeader("X-User-Id") String user_id,
            @Valid @RequestBody CreateUploadsSessionRequest createUploadsSessionRequest,
            HttpServletRequest request)
    {
        apiAbuseProtectionService.checkUploadSessionCreate(
                user_id,
                createUploadsSessionRequest.getNode_id(),
                ClientIpUtil.resolveClientIp(request)
        );

        String uploads_id = uploadsService.createUploadsSession(
                createUploadsSessionRequest.getTotal_chunks(),
                createUploadsSessionRequest.getFile_size(),
                createUploadsSessionRequest.getFile_checksum(),
                createUploadsSessionRequest.getChunks_max_size(),
                createUploadsSessionRequest.getFile_name(),
                createUploadsSessionRequest.getFile_type(),
                user_id,
                createUploadsSessionRequest.getNode_id());

        return new JsonResult<String>(OK, uploads_id);
    }
}
