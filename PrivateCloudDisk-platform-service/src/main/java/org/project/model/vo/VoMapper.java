package org.project.model.vo;

import org.project.model.entity.*;

import java.time.LocalDateTime;
import java.util.List;

public final class VoMapper {
    private VoMapper() {
    }

    // ==================== Share 相关转换方法 ====================

    public static ShareLinkVO toShareLinkVO(ShareLinkEntity entity) {
        if (entity == null) {
            return null;
        }
        ShareLinkVO vo = new ShareLinkVO();
        vo.setShare_id(entity.getShare_id().toString());
        vo.setShare_token(entity.getShare_token());
        vo.setShare_url("/share/" + entity.getShare_token());
        vo.setShare_target_type(entity.getShare_target_type().name());
        vo.setShare_name(entity.getShare_name());
        vo.setTarget_name(entity.getTarget_name());
        vo.setTarget_size(entity.getTarget_size());
        vo.setFile_type(entity.getFile_type());
        vo.setShare_has_password(entity.getShare_has_password());
        vo.setShare_expires_at(entity.getShare_expires_at());
        vo.setShare_view_count(entity.getShare_view_count());
        vo.setShare_status(entity.getShare_status().name());
        vo.setShare_created_at(entity.getShare_created_at());
        return vo;
    }

    public static List<ShareLinkVO> toShareLinkVOList(List<ShareLinkEntity> entities) {
        if (entities == null) {
            return List.of();
        }
        return entities.stream().map(VoMapper::toShareLinkVO).toList();
    }

    public static ShareAccessInfoVO toShareAccessInfoVO(ShareLinkEntity entity) {
        if (entity == null) {
            return null;
        }
        ShareAccessInfoVO vo = new ShareAccessInfoVO();
        vo.setShare_token(entity.getShare_token());
        vo.setShare_name(entity.getShare_name());
        vo.setShare_target_type(entity.getShare_target_type().name());
        vo.setTarget_name(entity.getTarget_name());
        vo.setTarget_size(entity.getTarget_size());
        vo.setFile_type(entity.getFile_type());
        vo.setOwner_name(entity.getOwner_name());
        vo.setHas_password(entity.getShare_has_password());
        vo.setIs_expired(entity.getShare_status() == ShareLinkEntity.ShareStatus.expired ||
                (entity.getShare_expires_at() != null && entity.getShare_expires_at().isBefore(LocalDateTime.now())));
        vo.setIs_revoked(entity.getShare_status() == ShareLinkEntity.ShareStatus.revoked);
        vo.setExpires_at(entity.getShare_expires_at());
        vo.setCreated_at(entity.getShare_created_at());
        return vo;
    }
    
    // ==================== 新增方法 ====================
    
    public static TrashTargetVO toTrashTargetVO(TrashTargetEntity entity) {
        if(entity == null) {
            return null;
        }
        TrashTargetVO vo = new TrashTargetVO();
        vo.setTrash_id(entity.getTrash_id());
        vo.setTarget_id(entity.getTarget_id().toString());
        vo.setTarget_name(entity.getTarget_name());
        vo.setTarget_type(entity.getTarget_type());
        vo.setFile_type(entity.getFile_type());
        vo.setTarget_size(entity.getTarget_size());
        vo.setOriginal_node_id(entity.getOriginal_node_id().toString());
        vo.setDeleted_at(entity.getDeleted_at());
        vo.setExpires_at(entity.getExpires_at());
        return vo;
    }
    
    public static List<TrashTargetVO> toTrashTargetVOList(List<TrashTargetEntity> entities) {
        if(entities == null) {
            return List.of();
        }
        return entities.stream().map(VoMapper::toTrashTargetVO).toList();
    }
    
    public static FileStarVO toFileStarVO(FileStarEntity entity) {
        if(entity == null) {
            return null;
        }
        FileStarVO vo = new FileStarVO();
        vo.setStar_id(entity.getStar_id());
        vo.setTarget_type(entity.getTarget_type() != null ? entity.getTarget_type().name() : null);
        // 根据类型设置目标ID
        if (entity.getTarget_type() == FileStarEntity.TargetType.file && entity.getFile_id() != null) {
            vo.setTarget_id(entity.getFile_id().toString());
        } else if (entity.getTarget_type() == FileStarEntity.TargetType.folder && entity.getNode_id() != null) {
            vo.setTarget_id(entity.getNode_id().toString());
        }
        vo.setTarget_name(entity.getTarget_name());
        vo.setTarget_size(entity.getTarget_size());
        vo.setFile_type(entity.getFile_type());
        vo.setFile_status(entity.getFile_status());
        vo.setStarred_at(entity.getStarred_at());
        return vo;
    }
    
    public static List<FileStarVO> toFileStarVOList(List<FileStarEntity> entities) {
        if(entities == null) {
            return List.of();
        }
        return entities.stream().map(VoMapper::toFileStarVO).toList();
    }
    
    // ==================== 原有方法 ====================

    public static FileVO toFileVO(FileEntity entity) {
        if(entity == null) {
            return null;
        }
        FileVO vo = new FileVO();
        vo.setId(entity.getId().toString());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setSize(entity.getSize());
        vo.setUploaded_time(entity.getUploaded_time());
        vo.setNode_id(entity.getNode_id().toString());
        vo.setTotal_chunks(entity.getTotal_chunks());
        return vo;
    }

    public static FolderNodeVO toFolderNodeVO(FolderNodeEntity entity) {
        if(entity == null) {
            return null;
        }
        FolderNodeVO vo = new FolderNodeVO();
        vo.setNode_id(entity.getNode_id().toString());
        if(entity.getParent_id() == null) vo.setParent_id(null);
        else vo.setParent_id(entity.getParent_id().toString());
        vo.setName(entity.getName());
        vo.setCreate_time(entity.getCreate_time());
        return vo;
    }

    public static NodeVO toNodeVO(NodeEntity entity) {
        if(entity == null) {
            return null;
        }
        NodeVO vo = new NodeVO();
        vo.setNode_id(entity.getNode_id().toString());
        vo.setNode_type(entity.getNode_type());
        vo.setNode_name(entity.getNode_name());
        vo.setNode_size(entity.getNode_size());
        return vo;
    }

    public static List<NodeVO> toNodeVOList(List<NodeEntity> entities) {
        if(entities == null) {
            return List.of();
        }
        return entities.stream().map(VoMapper::toNodeVO).toList();
    }

    public static UserProfileVO toUserProfileVO(UserEntity entity) {
        if(entity == null) {
            return null;
        }
        UserProfileVO vo = new UserProfileVO();
        vo.setId(entity.getId().toString());
        vo.setAccount(entity.getAccount());
        vo.setPhone_number(entity.getPhone_number());
        vo.setEmail(entity.getEmail());
        vo.setName(entity.getName());
        vo.setImage_path(entity.getImage_path());
        return vo;
    }

    public static InternalFileMetadataVO toInternalFileMetadataVO(FileEntity entity) {
        if(entity == null) {
            return null;
        }
        InternalFileMetadataVO vo = new InternalFileMetadataVO();
        vo.setId(entity.getId().toString());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setSize(entity.getSize());
        vo.setUser_id(entity.getUser_id().toString());
        vo.setUploaded_time(entity.getUploaded_time());
        vo.setChecksum(entity.getChecksum());
        vo.setNode_id(entity.getNode_id().toString());
        vo.setTotal_chunks(entity.getTotal_chunks());
        vo.setStorage_path(entity.getStorage_path());
        return vo;
    }

    public static UploadsSessionInternalVO toUploadsSessionInternalVO(UploadsSessionEntity entity) {
        if(entity == null) {
            return null;
        }
        UploadsSessionInternalVO vo = new UploadsSessionInternalVO();
        vo.setUploads_id(entity.getUploads_id().toString());
        vo.setUser_id(entity.getUser_id().toString());
        vo.setFile_name(entity.getFile_name());
        vo.setStarting_time(entity.getStarting_time());
        vo.setEndding_time(entity.getEndding_time());
        vo.setFile_size(entity.getFile_size());
        vo.setChunks_max_size(entity.getChunks_max_size());
        vo.setTotal_chunks(entity.getTotal_chunks());
        vo.setFile_checksum(entity.getFile_checksum());
        vo.setFile_type(entity.getFile_type());
        vo.setNode_id(entity.getNode_id().toString());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    public static UploadsChunkInternalVO toUploadsChunkInternalVO(UploadsChunkEntity entity) {
        if(entity == null) {
            return null;
        }
        UploadsChunkInternalVO vo = new UploadsChunkInternalVO();
        vo.setUploads_id(entity.getUploads_id().toString());
        vo.setChunk_index(entity.getChunk_index());
        vo.setChunk_status(entity.getChunk_status());
        vo.setChunk_storage_path(entity.getChunk_storage_path());
        vo.setChunk_uploaded_time(entity.getChunk_uploaded_time());
        return vo;
    }

    public static QuotaVO toQuotaVO(QuotaEntity entity) {
        if(entity == null) {
            return null;
        }
        QuotaVO vo = new QuotaVO();
        vo.setUser_id(entity.getUser_id().toString());
        vo.setTotal_capacity(entity.getTotal_capacity());
        vo.setUsed_capacity(entity.getUsed_capacity());
        vo.setFile_count(entity.getFile_count());
        vo.setVersion(entity.getVersion());
        vo.setCreated_at(entity.getCreated_at());
        vo.setUpdated_at(entity.getUpdated_at());
        return vo;
    }
}
