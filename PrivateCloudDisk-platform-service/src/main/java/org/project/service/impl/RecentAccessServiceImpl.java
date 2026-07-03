package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.mapper.RecentAccessMapper;
import org.project.model.entity.RecentAccessEntity;
import org.project.model.vo.RecentAccessVO;
import org.project.service.RecentAccessService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 最近访问记录服务实现
 *
 * <p>去重策略：同一用户 + 同一文件/文件夹 + 同一访问类型，只更新访问时间。
 * 每种类型最多保留 MAX_RECORDS_PER_TYPE 条记录。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecentAccessServiceImpl implements RecentAccessService {

    private final RecentAccessMapper recentAccessMapper;

    @Override
    public void recordAccess(UUID user_id, String target_id, String target_type,
                             String access_type, String file_name, Long file_size, String file_type) {
        UUID targetUuid = UUID.fromString(target_id);
        boolean isFile = "file".equals(target_type);

        RecentAccessEntity entity = new RecentAccessEntity();
        entity.setRa_user_id(user_id);
        entity.setRa_target_type(target_type);
        if (isFile) {
            entity.setRa_file_id(targetUuid);
        } else {
            entity.setRa_node_id(targetUuid);
        }
        entity.setRa_access_type(access_type);
        entity.setRa_file_name(file_name);
        entity.setRa_file_size(file_size != null ? file_size : 0L);
        entity.setRa_file_type(file_type != null ? file_type : "");
        entity.setRa_accessed_at(LocalDateTime.now());

        // 尝试更新现有记录
        int updated = recentAccessMapper.updateAccessTime(entity);
        if (updated == 0) {
            // 不存在则插入
            recentAccessMapper.insert(entity);
        }

        // 清理超出限制的旧记录
        int count = recentAccessMapper.countByUserIdAndType(user_id, access_type);
        if (count > MAX_RECORDS_PER_TYPE) {
            recentAccessMapper.deleteOldRecords(user_id, access_type, MAX_RECORDS_PER_TYPE);
        }

        log.debug("记录访问: userId={}, targetId={}, type={}, accessType={}",
                user_id, target_id, target_type, access_type);
    }

    @Override
    public List<RecentAccessVO> getRecentAccess(UUID user_id, String access_type, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<RecentAccessEntity> entities = recentAccessMapper.findByUserIdAndType(user_id, access_type, offset, pageSize);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public List<RecentAccessVO> getAllRecentAccess(UUID user_id, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        List<RecentAccessEntity> entities = recentAccessMapper.findByUserId(user_id, offset, pageSize);
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ==================== VO 转换 ====================

    private RecentAccessVO toVO(RecentAccessEntity entity) {
        RecentAccessVO vo = new RecentAccessVO();
        vo.setRa_id(entity.getRa_id());
        if ("file".equals(entity.getRa_target_type())) {
            vo.setTarget_id(entity.getRa_file_id() != null ? entity.getRa_file_id().toString() : null);
        } else {
            vo.setTarget_id(entity.getRa_node_id() != null ? entity.getRa_node_id().toString() : null);
        }
        vo.setTarget_type(entity.getRa_target_type());
        vo.setAccess_type(entity.getRa_access_type());
        vo.setTarget_name(entity.getRa_file_name());
        vo.setTarget_size(entity.getRa_file_size());
        vo.setFile_type(entity.getRa_file_type());
        vo.setAccessed_at(entity.getRa_accessed_at());
        return vo;
    }
}