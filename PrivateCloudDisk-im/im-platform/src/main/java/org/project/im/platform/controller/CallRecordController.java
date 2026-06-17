package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.CallRecordDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.CallRecordService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通话记录控制器
 * <p>
 * 提供通话记录相关的 REST API：
 * <ul>
 *   <li>查询通话历史</li>
 *   <li>查询通话详情</li>
 *   <li>删除通话记录</li>
 * </ul>
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@RestController
@RequestMapping("/api/v1/calls")
@RequiredArgsConstructor
@Tag(name = "通话记录", description = "WebRTC 视频/语音通话记录查询")
public class CallRecordController {

    private final CallRecordService callRecordService;

    @GetMapping("/history")
    @Operation(summary = "查询用户通话记录列表")
    public Result<List<CallRecordDTO>> getCallHistory(
            @Parameter(description = "用户 ID") @RequestParam String userId,
            @Parameter(description = "页码（从 1 开始）") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        return callRecordService.getCallHistory(userId, page, size);
    }

    @GetMapping("/{callId}")
    @Operation(summary = "查询通话记录详情")
    public Result<CallRecordDTO> getCallDetail(
            @Parameter(description = "通话 ID") @PathVariable String callId) {
        return callRecordService.getCallDetail(callId);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除通话记录")
    public Result<Void> deleteCallRecords(
            @Parameter(description = "通话记录 ID 列表") @RequestBody List<Long> ids) {
        return callRecordService.deleteCallRecords(ids);
    }
}