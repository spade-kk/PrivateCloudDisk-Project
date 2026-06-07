package org.project.mapper;

import org.project.model.entity.UploadsSessionEntity;

public interface UploadsMapper {
    /**
     * 根据上传会话ID查询上传会话数据
     * @param uploads_id 上传会话ID
     * @return 上传会话数据
     */
    UploadsSessionEntity findUploadsSessionById(String uploads_id);

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
    int updateUploadsSessionStatusById(UploadsSessionEntity.UploadsSessionStatus newStatus, String uploads_id);

    /**
     * 根据上传会话ID删除上传会话数据
     * @param uploads_id 上传会话ID
     * @return 删除了数据行数
     */
    int deleteUploadsSessionById(String uploads_id);
}
