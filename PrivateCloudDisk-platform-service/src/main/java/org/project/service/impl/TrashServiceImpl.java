package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.project.config.RabbitMQConifgure;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.TrashTargetMapper;
import org.project.model.dto.message.FileDeleteMessageDTO;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.TrashTargetEntity;
import org.project.service.DirectoryTreeService;
import org.project.service.FileService;
import org.project.service.TrashService;
import org.project.service.ex.DeleteException;
import org.project.service.ex.FileNotExistException;
import org.project.service.ex.InsertException;
import org.project.service.ex.NodeNotExistException;
import org.project.service.ex.NodeStatusException;
import org.project.service.ex.UpdateException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 回收站服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrashServiceImpl implements TrashService {
    
    private final TrashTargetMapper trashTargetMapper;
    private final FileMapper fileMapper;
    private final FileService fileService;
    private final DirectoryTreeService directoryTreeService;
    private final FolderNodeMapper folderNodeMapper;
    private final RabbitTemplate rabbitTemplate;
    
    @Override
    @Transactional
    public void moveToTrash(UUID target_id, UUID user_id, TrashTargetEntity.TargetType target_type) {
        if (trashTargetMapper.findTrashTargetByTargetId(target_id, user_id) != null) {
            return;
        }

        TrashTargetEntity trashTarget = switch (target_type) {
            case file -> buildTrashFile(target_id, user_id);
            case folder -> buildTrashFolder(target_id, user_id);
        };

        int rows = trashTargetMapper.insertTrashTarget(trashTarget);
        if (rows != 1) {
            throw new InsertException("移动目标到回收站失败");
        }

        if (target_type == TrashTargetEntity.TargetType.file) {
            fileService.deleteFileToTrash(target_id, user_id);
        } else {
            directoryTreeService.deleteFolderNodeToTrashByNodeId(target_id, user_id);
        }

        log.info("目标已移动到回收站: userId={}, targetId={}, targetType={}",
                user_id, trashTarget.getTarget_id(), trashTarget.getTarget_type());
    }
    
    @Override
    @Transactional
    public void restoreFromTrash(Long trash_id, UUID user_id) {
        // 查询回收站记录
        TrashTargetEntity trashTarget = trashTargetMapper.findTrashTargetById(trash_id, user_id);
        if (trashTarget == null) {
            throw new FileNotExistException();
        }

        int rows;
        if (trashTarget.getTarget_type() == TrashTargetEntity.TargetType.file) {
            rows = fileMapper.updateUserFileParentNodeIdById(trashTarget.getTarget_id(), trashTarget.getOriginal_node_id(), user_id);
            rows += fileMapper.updateUserFileStatusById(trashTarget.getTarget_id(), FileEntity.FileStatus.active, user_id);
            if (rows != 2) {
                throw new UpdateException("恢复文件失败");
            }
        } else {
            rows = folderNodeMapper.updateFolderNodeParentIdByIdAndUserId(trashTarget.getOriginal_node_id(), trashTarget.getTarget_id(), user_id);
            rows += folderNodeMapper.updateFolderNodeStatusByIdAndUserId(FolderNodeEntity.NodeStatus.active, trashTarget.getTarget_id(), user_id);
            if (rows != 2) {
                throw new UpdateException("恢复文件夹失败");
            }
        }

        // 删除回收站记录
        trashTargetMapper.deleteTrashTarget(trash_id, user_id);

        log.info("目标已从回收站恢复: userId={}, targetId={}, targetType={}",
                user_id, trashTarget.getTarget_id(), trashTarget.getTarget_type());
    }
    
    @Override
    @Transactional
    public void permanentDelete(Long trash_id, UUID user_id) {
        // 查询回收站记录
        TrashTargetEntity trashTarget = trashTargetMapper.findTrashTargetById(trash_id, user_id);
        if (trashTarget == null) {
            throw new FileNotExistException();
        }

        if (trashTarget.getTarget_type() == TrashTargetEntity.TargetType.file) {
            permanentDeleteFile(trashTarget, user_id);
        } else {
            permanentDeleteFolder(trashTarget, user_id);
        }

        // 删除回收站记录
        int rows = trashTargetMapper.deleteTrashTarget(trash_id, user_id);
        if (rows != 1) {
            throw new DeleteException("删除回收站记录失败");
        }

        log.info("目标已彻底删除: userId={}, targetId={}, targetType={}",
                user_id, trashTarget.getTarget_id(), trashTarget.getTarget_type());
    }
    
    @Override
    @Transactional
    public void emptyTrash(UUID user_id) {
        List<TrashTargetEntity> trashFiles = trashTargetMapper.findTrashTargetsByUserId(user_id, 0, Integer.MAX_VALUE);
        
        for (TrashTargetEntity trashFile : trashFiles) {
            permanentDelete(trashFile.getTrash_id(), user_id);
        }
        
        log.info("回收站已清空: userId={}", user_id);
    }
    
    @Override
    public List<TrashTargetEntity> getTrashTargets(UUID user_id, Integer page, Integer pageSize) {
        int offset = (page - 1) * pageSize;
        return trashTargetMapper.findTrashTargetsByUserId(user_id, offset, pageSize);
    }
    
    @Override
    public Integer countTrashTargets(UUID user_id) {
        return trashTargetMapper.countTrashTargetsByUserId(user_id);
    }
    
    @Override
    public TrashTargetEntity getTrashTargetById(Long trash_id, UUID user_id) {
        TrashTargetEntity trashTarget = trashTargetMapper.findTrashTargetById(trash_id, user_id);
        if (trashTarget == null) {
            throw new FileNotExistException();
        }
        return trashTarget;
    }

    private TrashTargetEntity buildTrashFile(UUID file_id, UUID user_id) {
        FileEntity file = fileMapper.findUserFileById(file_id, user_id);
        if (file == null || fileMapper.isFileDeleted(file_id, user_id)) {
            throw new FileNotExistException();
        }

        TrashTargetEntity trashTarget = newTrashTarget(file_id, user_id, TrashTargetEntity.TargetType.file);
        trashTarget.setTarget_name(file.getName());
        trashTarget.setFile_type(file.getType());
        trashTarget.setTarget_size(file.getSize());
        trashTarget.setOriginal_node_id(file.getNode_id());
        return trashTarget;
    }

    private TrashTargetEntity buildTrashFolder(UUID node_id, UUID user_id) {
        FolderNodeEntity folder = folderNodeMapper.findFolderNodeByIdAndUserId(node_id, user_id);
        if (folder == null || folderNodeMapper.isFolderDeleted(node_id, user_id)) {
            throw new NodeNotExistException("文件夹不存在");
        }
        if (folder.getParent_id() == null) {
            throw new NodeStatusException("根目录不能移入回收站");
        }

        TrashTargetEntity trashTarget = newTrashTarget(node_id, user_id, TrashTargetEntity.TargetType.folder);
        trashTarget.setTarget_name(folder.getName());
        trashTarget.setFile_type("folder");
        trashTarget.setTarget_size(0L);
        trashTarget.setOriginal_node_id(folder.getParent_id());
        return trashTarget;
    }

    private TrashTargetEntity newTrashTarget(UUID target_id, UUID user_id, TrashTargetEntity.TargetType target_type) {
        TrashTargetEntity trashTarget = new TrashTargetEntity();
        trashTarget.setTarget_id(target_id);
        trashTarget.setUser_id(user_id);
        trashTarget.setTarget_type(target_type);
        trashTarget.setDeleted_at(LocalDateTime.now());
        trashTarget.setExpires_at(LocalDateTime.now().plusDays(30));
        return trashTarget;
    }

    private void permanentDeleteFile(TrashTargetEntity trashTarget, UUID user_id) {
        FileEntity file = fileMapper.findUserFileById(trashTarget.getTarget_id(), user_id);
        String storagePath = file == null ? null : file.getStorage_path();

        int rows = fileMapper.updateUserFileStatusById(trashTarget.getTarget_id(), FileEntity.FileStatus.deleted, user_id);
        if (rows != 1) {
            throw new DeleteException("彻底删除文件失败");
        }

        FileDeleteMessageDTO message = FileDeleteMessageDTO.builder()
                .messageId(UUID.randomUUID().toString())
                .fileId(trashTarget.getTarget_id().toString())
                .userId(user_id.toString())
                .storagePath(storagePath)
                .fileSize(trashTarget.getTarget_size())
                .fromTrash(true)
                .createdAt(LocalDateTime.now())
                .build();

        rabbitTemplate.convertAndSend(
                RabbitMQConifgure.FILE_DELETE_EXCHANGE,
                RabbitMQConifgure.FILE_DELETE_ROUTING_KEY,
                message);
    }

    private void permanentDeleteFolder(TrashTargetEntity trashTarget, UUID user_id) {
        int rows = folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.deleted,
                trashTarget.getTarget_id(),
                user_id
        );
        if (rows != 1) {
            throw new DeleteException("彻底删除文件夹失败");
        }
    }
}
