package org.project.service.impl;

import jakarta.annotation.Resource;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.mapper.FileMapper;
import org.project.mapper.FileStarMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.ShareLinkMapper;
import org.project.mapper.ShareResourceMapper;
import org.project.mapper.TagMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.FileService;
import org.project.service.ex.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.project.model.entity.FileEntity.FileStatus.*;

@Service
public class FileServiceImpl implements FileService {
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private FileStarMapper fileStarMapper;
    @Autowired
    private TagMapper tagMapper;
    @Autowired
    private ShareResourceMapper shareResourceMapper;
    @Autowired
    private ShareLinkMapper shareLinkMapper;

    @Override
    public UUID createMergingFile(String file_name, String file_type, long file_size, UUID user_id, UUID node_id, String file_checksum, int total_chunks) {
        FolderNodeEntity node = directoryTreeService.findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        // 检查文件名是否存在
        FileEntity fileData = findUserFileByNameAndNodeIdIfExist(file_name, node_id, user_id);
        if(fileData != null) {
            throw new FileNameDuplicatedException("文件名字已存在");
        }

        // 实现文件创建的逻辑
        fileData = new FileEntity();
        fileData.setName(file_name);
        fileData.setType(file_type);
        fileData.setUser_id(user_id);
        fileData.setSize(file_size);
        fileData.setChecksum(file_checksum);
        fileData.setNode_id(node_id);
        fileData.setTotal_chunks(total_chunks);
        fileData.setStorage_path(null);
        fileData.setStatus(FileEntity.FileStatus.merging);
        //设置上传时间
        fileData.setUploaded_time(LocalDateTime.now());
        // 生成文件的ID
        UUID file_id = UUID.randomUUID();
        fileData.setId(file_id);
        // 调用Mapper插入数据
        Integer rows = fileMapper.insertFile(fileData);
        if(rows!= 1) {
            throw new InsertException();
        }

        return file_id;
    }

    @Override
    public void mergedFile(UUID file_id, String storage_path, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileById(file_id, user_id);
        if (fileEntity == null || fileMapper.isFileDeleted(file_id, user_id)) {
            throw new FileNotExistException();
        }

        Integer rows1 = fileMapper.updateUserFileStatusById(file_id, FileEntity.FileStatus.merged, user_id);
        Integer rows2 = fileMapper.updateUserFileStoragePath(file_id, storage_path, user_id);
        if(rows1 != 1 || rows2 != 1) {
            throw new UpdateException("文件合并完成状态更新失败");
        }
    }

    @Override
    public List<FileEntity> queryUserActiveFilesByNodeId(UUID node_id, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = directoryTreeService.findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }

        return fileMapper.findUserActiveFilesByNodeId(node_id, user_id);
    }

    @Override
    public FileEntity queryUserFileById(UUID file_id, UUID user_id) {
        FileEntity fileData = findUserFileByIdIfActive(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        return fileData;
    }

    @Override
    public void updateFileName(UUID file_id, String file_new_name, UUID user_id) {
        // 检查文件是否存在
        FileEntity fileData = findUserFileByIdIfActive(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }

        // 实现文件名称更新的逻辑
        Integer rows = fileMapper.updateUserFileNameById(file_id, file_new_name, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件名称更新失败");
        }
    }

    @Override
    public void moveFileByFileId(UUID file_id, UUID target_node_id, UUID user_id) {
        // 检查文件是否存在
        FileEntity fileData = findUserFileByIdIfActive(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }

        FolderNodeEntity targetNode = directoryTreeService.findUserFolderNodeIfExist(target_node_id, user_id);
        if(targetNode == null) {
            throw new NodeNotExistException("目标节点不存在");
        }

        // 实现文件移动的逻辑
        Integer rows = fileMapper.updateUserFileParentNodeIdById(file_id, target_node_id, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件移动失败");
        }
    }

    @Override
    public void deleteFileByFileId(UUID file_id, UUID user_id) {
        // 检查文件是否存在
        FileEntity fileData = findUserFileByIdIfActive(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        // 实现文件删除的逻辑 把文件状态设置为deleted
        Integer rows = fileMapper.updateUserFileStatusById(file_id, FileEntity.FileStatus.deleted, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件删除失败");
        }
        // 发布消息... 文件夹子文件物理删除是异步处理业务
    }

    @Override
    public void deleteFileToTrash(UUID file_id, UUID user_id) {
        // 检查文件是否存在
        FileEntity fileData = findUserFileByIdIfActive(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }
        // 实现文件删除的逻辑 把文件状态设置为trashed
        Integer rows = fileMapper.updateUserFileStatusById(file_id, FileEntity.FileStatus.trashed, user_id);
        if(rows!= 1) {
            throw new UpdateException("文件删除移动到垃圾站失败");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeDeleteFileByFileId(UUID file_id, UUID user_id) {
        FileEntity fileData = fileMapper.findUserFileById(file_id, user_id);
        if(fileData == null) {
            throw new FileNotExistException();
        }

        if(!fileData.getStatus().equals(deleted)) {
            throw new FileStatusException("完全删除文件记录 文件状态异常 非deleted");
        }

        /*
         * AUDIT FIX [3.2] 永久删除关联数据一致性修复：
         * 原行为仅删除文件元数据，标签与多资源分享表没有文件外键，可能产生孤儿引用；
         * 新行为在同一事务中按“分享资源 -> 空分享 -> 标签 -> 收藏 -> 文件元数据”顺序清理。
         * 注意：回收站/软删除仍只修改状态，不进入本方法，原有关联关系会完整保留。
         */
        List<UUID> affectedShareIds = shareResourceMapper.findShareIdsByFileId(file_id);
        shareResourceMapper.deleteByFileId(file_id);
        if (affectedShareIds != null && !affectedShareIds.isEmpty()) {
            shareLinkMapper.deleteEmptySharesByIds(affectedShareIds);
        }
        tagMapper.deleteAllByFileId(file_id);
        fileStarMapper.deleteAllByFileId(file_id);

        Integer rows = fileMapper.deleteUserFileById(file_id, user_id);
        if(rows != 1) {
            throw new DeleteException("SQL Delete Error");
        }
    }

    @Override
    public FileEntity findUserFileByIdIfActive(UUID file_id, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileById(file_id, user_id);
        if (fileEntity == null) return null;

        if (!fileEntity.getStatus().equals(active)) return null;

        if (fileMapper.isFileDeleted(file_id, user_id)) return null;
        return fileEntity;
    }

    @Override
    public FileEntity findUserFileByIdIfExist(UUID file_id, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileById(file_id, user_id);
        if (fileEntity == null) return null;

        if (fileMapper.isFileDeleted(file_id, user_id)) return null;
        return fileEntity;
    }

    @Override
    public FileEntity.FileStatus getFileValidStatus(UUID file_id, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileById(file_id, user_id);
        if(fileEntity == null) return null;

        if(fileEntity.getStatus().equals(active)) {
            String effectiveStatus = fileMapper.selectFileEffectiveStatus(file_id, user_id);

            return FileEntity.FileStatus.valueOf(effectiveStatus);
        }

        return fileEntity.getStatus();
    }

    @Override
    public FileEntity findUserFileByNameAndNodeIdIfExist(String file_name, UUID node_id, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileByNodeIdAndName(node_id, file_name, user_id);
        if (fileEntity == null) return null;

        if (fileMapper.isFileDeleted(fileEntity.getId(), user_id)) return null;
        return fileEntity;
    }

    @Override
    public void updateFileStatus(UUID file_id, String status, UUID user_id) {
        FileEntity fileEntity = fileMapper.findUserFileById(file_id, user_id);
        if (fileEntity == null || fileMapper.isFileDeleted(file_id, user_id)) {
            throw new FileNotExistException();
        }

        // 定义状态常量（确保与调用方传入的字符串完全一致）
        final String MERGE_FAILED = "merge_failed";
        final String SCAN_FAILED  = "scan_failed";
        final String REJECT       = "reject";
        final String SCANNING     = "scanning";

        if(MERGE_FAILED.equals(status) && fileEntity.getStatus().equals(FileEntity.FileStatus.merging)) {
            fileMapper.updateUserFileStatusById(file_id, merge_failed, user_id);
            return;
        } else if(SCAN_FAILED.equals(status) && fileEntity.getStatus().equals(FileEntity.FileStatus.scanning)) {
            fileMapper.updateUserFileStatusById(file_id, scan_failed, user_id);
            return;
        } else if(REJECT.equals(status) && fileEntity.getStatus().equals(FileEntity.FileStatus.scanning)) {
            fileMapper.updateUserFileStatusById(file_id, reject, user_id);
            return;
        } else if(SCANNING.equals(status) && fileEntity.getStatus().equals(FileEntity.FileStatus.merged)) {
            fileMapper.updateUserFileStatusById(file_id, scanning, user_id);
            return;
        }

        throw new FileStatusException("File State Error");
    }
}
