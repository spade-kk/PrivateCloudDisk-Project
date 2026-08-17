package org.project.control;

import io.swagger.v3.core.util.Json;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.project.control.result.JsonResult;
import org.project.model.dto.CreateUploadsSessionRequest;
import org.project.model.vo.UploadSessionConcurrencyVO;
import org.project.model.vo.UploadSessionCreateVO;
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
     * @return JsonResult data 上传会话 ID 与当前并发槽位快照；旧客户端仍可从 uploads_id 读取会话 ID
     */
    @PostMapping("/")
    public JsonResult<UploadSessionCreateVO> createUploadsSession(
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

        UploadSessionConcurrencyVO concurrency = uploadsService.queryUploadConcurrency(UUID.fromString(user_id));
        UploadSessionCreateVO response = new UploadSessionCreateVO();
        response.setUploads_id(uploads_id.toString());
        response.setMax_concurrent_sessions(concurrency.getMax_concurrent_sessions());
        response.setActive_session_count(concurrency.getActive_session_count());
        response.setRemaining_concurrent_sessions(concurrency.getRemaining_concurrent_sessions());
        return new JsonResult<>(OK, response);
    }

    /**
     * 查询当前用户/空间范围内的活跃上传会话及剩余并发槽位。
     * 列表由服务层转换为脱敏摘要，不返回 checksum、用户内部字段或物理存储路径。
     */
    @GetMapping("/active")
    public JsonResult<UploadSessionConcurrencyVO> queryActiveUploadsSessions(
            @RequestHeader("X-User-Id") String user_id) {
        return new JsonResult<>(OK, uploadsService.queryUploadConcurrency(UUID.fromString(user_id)));
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
