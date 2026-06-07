package org.project.model.vo;

import org.project.model.entity.*;

import java.util.List;

public final class VoMapper {
    private VoMapper() {
    }

    public static FileVO toFileVO(FileEntity entity) {
        if(entity == null) {
            return null;
        }
        FileVO vo = new FileVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setSize(entity.getSize());
        vo.setUploaded_time(entity.getUploaded_time());
        vo.setNode_id(entity.getNode_id());
        vo.setTotal_chunks(entity.getTotal_chunks());
        return vo;
    }

    public static FolderNodeVO toFolderNodeVO(FolderNodeEntity entity) {
        if(entity == null) {
            return null;
        }
        FolderNodeVO vo = new FolderNodeVO();
        vo.setNode_id(entity.getNode_id());
        vo.setParent_id(entity.getParent_id());
        vo.setName(entity.getName());
        vo.setCreate_time(entity.getCreate_time());
        return vo;
    }

    public static NodeVO toNodeVO(NodeEntity entity) {
        if(entity == null) {
            return null;
        }
        NodeVO vo = new NodeVO();
        vo.setNode_id(entity.getNode_id());
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
        vo.setId(entity.getId());
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
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setType(entity.getType());
        vo.setSize(entity.getSize());
        vo.setUser_id(entity.getUser_id());
        vo.setUploaded_time(entity.getUploaded_time());
        vo.setChecksum(entity.getChecksum());
        vo.setNode_id(entity.getNode_id());
        vo.setTotal_chunks(entity.getTotal_chunks());
        vo.setStorage_path(entity.getStorage_path());
        return vo;
    }

    public static UploadsSessionInternalVO toUploadsSessionInternalVO(UploadsSessionEntity entity) {
        if(entity == null) {
            return null;
        }
        UploadsSessionInternalVO vo = new UploadsSessionInternalVO();
        vo.setUploads_id(entity.getUploads_id());
        vo.setUser_id(entity.getUser_id());
        vo.setFile_name(entity.getFile_name());
        vo.setStarting_time(entity.getStarting_time());
        vo.setEndding_time(entity.getEndding_time());
        vo.setFile_size(entity.getFile_size());
        vo.setChunks_max_size(entity.getChunks_max_size());
        vo.setTotal_chunks(entity.getTotal_chunks());
        vo.setFile_checksum(entity.getFile_checksum());
        vo.setFile_type(entity.getFile_type());
        vo.setNode_id(entity.getNode_id());
        vo.setStatus(entity.getStatus());
        return vo;
    }

    public static UploadsChunkInternalVO toUploadsChunkInternalVO(UploadsChunkEntity entity) {
        if(entity == null) {
            return null;
        }
        UploadsChunkInternalVO vo = new UploadsChunkInternalVO();
        vo.setUploads_id(entity.getUploads_id());
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
        vo.setUser_id(entity.getUser_id());
        vo.setTotal_capacity(entity.getTotal_capacity());
        vo.setUsed_capacity(entity.getUsed_capacity());
        vo.setFile_count(entity.getFile_count());
        vo.setVersion(entity.getVersion());
        vo.setCreated_at(entity.getCreated_at());
        vo.setUpdated_at(entity.getUpdated_at());
        return vo;
    }
}
