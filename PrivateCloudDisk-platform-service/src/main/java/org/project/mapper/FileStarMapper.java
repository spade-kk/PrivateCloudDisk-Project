package org.project.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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
    int deleteFileStar(@Param("user_id") String user_id, @Param("file_id") String file_id);
    
    /**
     * 查询用户是否收藏了文件
     */
    FileStarEntity findFileStarByUserIdAndFileId(@Param("user_id") String user_id, @Param("file_id") String file_id);
    
    /**
     * 查询用户所有收藏的文件ID
     */
    List<String> findStarredFileIdsByUserId(@Param("user_id") String user_id);
    
    /**
     * 查询用户收藏的文件列表（分页）
     */
    List<FileStarEntity> findStarredFilesByUserId(@Param("user_id") String user_id, @Param("offset") Integer offset, @Param("limit") Integer limit);
    
    /**
     * 统计用户收藏的文件数量
     */
    Integer countStarredFilesByUserId(@Param("user_id") String user_id);
}
