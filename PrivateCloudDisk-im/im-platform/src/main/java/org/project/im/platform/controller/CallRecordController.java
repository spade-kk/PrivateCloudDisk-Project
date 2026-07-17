package org.project.im.platform.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.project.im.common.dto.CallRecordDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.service.CallRecordService;
import org.springframework.validation.annotation.Validated;
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
@RequestMapping("/im/calls")
@RequiredArgsConstructor
@Validated
@Tag(name = "通话记录", description = "WebRTC 视频/语音通话记录查询")
public class CallRecordController {

    private final CallRecordService callRecordService;

    private static final String UUID_REGEX =
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$";

    @GetMapping("/history")
    @Operation(summary = "查询用户通话记录列表")
    public Result<List<CallRecordDTO>> getCallHistory(
            @Parameter(description = "用户 ID")
            @RequestParam @NotBlank(message = "用户ID不能为空")
            @Pattern(regexp = UUID_REGEX, message = "用户ID必须是有效的UUID格式") String userId,
            @Parameter(description = "页码（从 1 开始）")
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码最小为1") int page,
            @Parameter(description = "每页大小")
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页大小最小为1") int size) {
        return callRecordService.getCallHistory(userId, page, size);
    }

    @GetMapping("/{callId}")
    @Operation(summary = "查询通话记录详情")
    public Result<CallRecordDTO> getCallDetail(
            @Parameter(description = "通话 ID")
            @PathVariable @NotBlank(message = "通话ID不能为空") String callId) {
        return callRecordService.getCallDetail(callId);
    }

    @DeleteMapping("/batch")
    @Operation(summary = "批量删除通话记录")
    public Result<Void> deleteCallRecords(
            @Parameter(description = "通话记录 ID 列表")
            @RequestBody @NotEmpty(message = "ID列表不能为空") List<Long> ids) {
        return callRecordService.deleteCallRecords(ids);
    }
}
