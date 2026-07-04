package org.project.control;

import io.swagger.v3.core.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.project.control.result.JsonResult;
import org.project.model.dto.CreateUploadsSessionRequest;
import org.project.service.UploadsService;
import org.project.util.ClientIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/business/uploads")
public class UploadsController extends BaseController {
    @Autowired
    private UploadsService uploadsService;

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
        String clientIp = ClientIpUtil.resolveClientIp(request);

        UUID uploads_id = uploadsService.createUploadsSession(
                createUploadsSessionRequest.getTotal_chunks(),
                createUploadsSessionRequest.getFile_size(),
                createUploadsSessionRequest.getFile_checksum(),
                createUploadsSessionRequest.getChunks_max_size(),
                createUploadsSessionRequest.getFile_name(),
                createUploadsSessionRequest.getFile_type(),
                UUID.fromString(user_id),
                UUID.fromString(createUploadsSessionRequest.getNode_id()),
                clientIp);

        return new JsonResult<String>(OK, uploads_id.toString());
    }

    /**
     *
     * @param user_id
     * @param uploads_id
     * @return
     */
    @DeleteMapping("/{uploads_id}")
    public JsonResult<String> cancelUploadsSession(
            @RequestHeader("X-User-Id") String user_id,
            @PathVariable("uploads_id") String uploads_id )
    {
        uploadsService.cancelUploadSession(UUID.fromString(uploads_id), UUID.fromString(user_id));
        return new JsonResult<String>(OK, null);
    }

}
