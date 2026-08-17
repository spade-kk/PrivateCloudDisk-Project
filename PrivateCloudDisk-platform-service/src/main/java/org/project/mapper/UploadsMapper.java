package org.project.mapper;

import org.apache.ibatis.annotations.Param;
import org.project.model.entity.UploadsSessionEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface UploadsMapper {
    /**
     * 根据上传会话ID查询上传会话数据
     * @param uploads_id 上传会话ID
     * @return 上传会话数据
     */
    UploadsSessionEntity findUploadsSessionById(@Param("uploads_id") UUID uploads_id);

    /**
     *
     */
    List<UploadsSessionEntity> findUserActiveUploadsSession(@Param("user_id") UUID user_id);

    /**
     * 插入用户数据
     * @param uploadsSessionData
     * @return 插入了数据行数
     */
    int insertUploadsSession(UploadsSessionEntity uploadsSessionData);

    /**
     * 更新上传会话状态
     * @param newStatus 上传会话状态
     * @param uploads_id 上传会话ID
     * @return 更新了数据行数
     */
    int updateUploadsSessionStatusById(@Param("newStatus") UploadsSessionEntity.UploadsSessionStatus newStatus, @Param("uploads_id") UUID uploads_id);

    /**
     * 查询过期的上传会话（uploading 状态且超过过期时间）
     * @param expireTime 过期时间阈值
     * @return 过期上传会话列表
     */
    List<UploadsSessionEntity> findExpiredUploadsSessions(@Param("expireTime") LocalDateTime expireTime);

    /**
     * 查询已取消（canceled）状态的上传会话
     * @return 已取消的上传会话列表
     */
    List<UploadsSessionEntity> findCanceledUploadsSessions();

    /**
     * 根据上传会话ID删除上传会话数据
     * @param uploads_id 上传会话ID
     * @return 删除了数据行数
     */
    int deleteUploadsSessionById(@Param("uploads_id") UUID uploads_id);
}
