package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.project.model.entity.FileStarEntity;

import java.util.List;

@Mapper
public interface FileStarMapper {
    
    /**
     * 添加文件收藏
     */
    int insertFileStar(FileStarEntity fileStar);
    
    /**
     * 取消文件收藏
     */
    int deleteFileStar(String user_id, String file_id);
    
    /**
     * 查询用户是否收藏了文件
     */
    FileStarEntity findFileStarByUserIdAndFileId(String user_id, String file_id);
    
    /**
     * 查询用户所有收藏的文件ID
     */
    List<String> findStarredFileIdsByUserId(String user_id);
    
    /**
     * 查询用户收藏的文件列表（分页）
     */
    List<FileStarEntity> findStarredFilesByUserId(String user_id, Integer offset, Integer limit);
    
    /**
     * 统计用户收藏的文件数量
     */
    Integer countStarredFilesByUserId(String user_id);
}
