package org.project.im.platform.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.im.common.dto.CallRecordDTO;
import org.project.im.common.dto.Result;
import org.project.im.platform.entity.ImCallRecord;
import org.project.im.platform.mapper.ImCallRecordMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 通话记录服务
 * <p>
 * 负责通话记录的持久化、查询和统计。
 * 通话过程中由信令服务器异步将记录写入数据库。
 * </p>
 *
 * @author PrivateCloudDisk Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CallRecordService {

    private final ImCallRecordMapper callRecordMapper;
    private final ObjectMapper objectMapper;

    /**
     * 保存通话记录（通话开始时调用）
     */
    @Transactional
    public void saveCallRecord(ImCallRecord record) {
        callRecordMapper.insert(record);
        log.info("保存通话记录: callId={}, caller={}, callee={}", record.getCallId(), record.getCallerId(), record.getCalleeId());
    }

    /**
     * 更新通话记录（通话结束时调用）
     */
    @Transactional
    public void updateCallRecord(ImCallRecord record) {
        callRecordMapper.update(record);
        log.info("更新通话记录: callId={}, status={}, duration={}s", record.getCallId(), record.getStatus(), record.getDuration());
    }

    /**
     * 查询用户通话记录列表
     */
    public Result<List<CallRecordDTO>> getCallHistory(String userId, int page, int size) {
        int offset = (page - 1) * size;
        List<ImCallRecord> records = callRecordMapper.selectByUserId(userId, offset, size);
        int total = callRecordMapper.countByUserId(userId);

        List<CallRecordDTO> dtos = records.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());

        return Result.<List<CallRecordDTO>>builder()
                .code(200)
                .message("success")
                .data(dtos)
                .build();
    }

    /**
     * 查询通话记录详情
     */
    public Result<CallRecordDTO> getCallDetail(String callId) {
        ImCallRecord record = callRecordMapper.selectByCallId(callId);
        if (record == null) {
            return Result.<CallRecordDTO>builder()
                    .code(404)
                    .message("通话记录不存在")
                    .build();
        }
        return Result.<CallRecordDTO>builder()
                .code(200)
                .message("success")
                .data(toDTO(record))
                .build();
    }

    /**
     * 删除通话记录
     */
    @Transactional
    public Result<Void> deleteCallRecords(List<Long> ids) {
        callRecordMapper.deleteByIds(ids);
        return Result.<Void>builder()
                .code(200)
                .message("删除成功")
                .build();
    }

    /**
     * 实体转 DTO
     */
    private CallRecordDTO toDTO(ImCallRecord record) {
        List<String> participantList = new ArrayList<>();
        if (record.getParticipants() != null) {
            try {
                participantList = objectMapper.readValue(record.getParticipants(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            } catch (JsonProcessingException e) {
                log.warn("解析参与者列表失败: {}", record.getParticipants());
                if (record.getCallerId() != null) participantList.add(record.getCallerId());
                if (record.getCalleeId() != null) participantList.add(record.getCalleeId());
            }
        }

        return CallRecordDTO.builder()
                .callId(record.getCallId())
                .roomId(record.getRoomId())
                .callType(record.getCallType())
                .callMode(record.getCallMode())
                .callerId(record.getCallerId())
                .calleeId(record.getCalleeId())
                .status(record.getStatus())
                .startTime(record.getStartTime())
                .endTime(record.getEndTime())
                .duration(record.getDuration())
                .rejectReason(record.getRejectReason())
                .participants(participantList)
                .videoEnabled(record.getVideoEnabled())
                .screenShareEnabled(record.getScreenShareEnabled())
                .hangupBy(record.getHangupBy())
                .createTime(record.getCreateTime())
                .build();
    }
}