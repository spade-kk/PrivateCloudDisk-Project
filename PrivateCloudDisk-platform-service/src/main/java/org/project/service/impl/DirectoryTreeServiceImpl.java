package org.project.service.impl;

import org.project.mapper.DirectoryClosureMapper;
import org.project.mapper.TrashTargetMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.model.vo.PageResultVO;
import org.project.service.DirectoryTreeService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class DirectoryTreeServiceImpl implements DirectoryTreeService {
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private DirectoryClosureMapper directoryClosureMapper;
    @Autowired
    private FileMapper fileMapper;
    private final int MAX_DIRECTORY_DEPTH = 15;

    @Override
    @Transactional
    public void createFolderNode(UUID user_id, UUID node_id, String name) {
        FolderNodeEntity folderNodeEntity = new FolderNodeEntity();
        folderNodeEntity.setUser_id(user_id);
        folderNodeEntity.setStatus(FolderNodeEntity.NodeStatus.active);
        folderNodeEntity.setCreate_time(LocalDateTime.now().toString());
        folderNodeEntity.setName(name);
        folderNodeEntity.setNode_id(UUID.randomUUID());
        folderNodeEntity.setParent_id(null);

        if(node_id != null) {
            FolderNodeEntity parentNode = findUserFolderNodeIfExist(node_id, user_id);
            if(parentNode == null) {
                throw new ParentNodeNotExistException("父节点不存在");
            }

            // 检查目录深度是否超过最大深度
            int currentDepth = directoryClosureMapper.getMaxDepthToNode(node_id, user_id);
            if(1 + currentDepth > MAX_DIRECTORY_DEPTH) {
                throw new InsertException("目录深度超过最大深度");
            }

            folderNodeEntity.setParent_id(node_id);
            // 待处理状态
            folderNodeEntity.setStatus(FolderNodeEntity.NodeStatus.pending);
            // 锁定父节点
            folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                    FolderNodeEntity.NodeStatus.lock,
                    node_id,
                    user_id
            );
        }
        Integer rows = folderNodeMapper.insertFolderNode(folderNodeEntity);
        if(rows != 1) {
            throw new InsertException("创建目录失败");
        }

        // 插入闭包关系
        directoryClosureMapper.insertRelationsFromParent(folderNodeEntity.getNode_id(), user_id, folderNodeEntity.getParent_id());
        // 插入自引用
        directoryClosureMapper.insertSelf(folderNodeEntity.getNode_id(), user_id);
        // 更新节点状态 ACTIVE UNLOCK
        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.active,
                folderNodeEntity.getNode_id(),
                user_id
        );
    }

    @Override
    public void activeFolderNode(UUID node_id, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        // 检查节点状态
        if(node.getStatus() != FolderNodeEntity.NodeStatus.pending) {
            throw new NodeStatusException("节点状态错误");
        }

        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.active,
                node_id,
                user_id
        );
        // 解锁父节点
        folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.active,
                node.getParent_id(),
                user_id
        );
    }

    @Override
    public List<NodeEntity> findUserNodesByNodeId(UUID node_id, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }


        List<NodeEntity> nodeList = new ArrayList<>();
        List<FileEntity> fileDataList =fileMapper.findUserActiveFilesByNodeId(node_id, user_id);
        List<FolderNodeEntity> folderNodeEntityList = folderNodeMapper.findFolderNodesByIdAndUserId(node_id, user_id);

        for(FolderNodeEntity folderNodeEntity : folderNodeEntityList) {
            NodeEntity nodeData = new NodeEntity();
            nodeData.setNode_id(folderNodeEntity.getNode_id());
            nodeData.setNode_name(folderNodeEntity.getName());
            nodeData.setNode_type(NodeEntity.NodeType.FOLDER);
            nodeList.add(nodeData);
        }
        for(FileEntity fileData : fileDataList) {
            NodeEntity nodeData = new NodeEntity();
            nodeData.setNode_id(fileData.getId());
            nodeData.setNode_name(fileData.getName());
            nodeData.setNode_type(NodeEntity.NodeType.FILE);
            nodeData.setNode_size(fileData.getSize());
            nodeList.add(nodeData);
        }

        return nodeList;
    }

    @Override
    public FolderNodeEntity queryUserFolderNodeById(UUID node_id, UUID user_id) {
        FolderNodeEntity folderNodeEntity = findUserFolderNodeIfExist(node_id, user_id);
        if (folderNodeEntity == null) {
            throw new NodeNotExistException("节点不存在");
        }
        return folderNodeEntity;
    }

    @Override
    public PageResultVO<NodeEntity> findUserNodesByNodeIdPaged(
            String parentId, String keyword, String fileType,
            String sortBy, String sortOrder, Integer page, Integer pageSize, UUID userId) {
        return null;
    }

    @Override
    @Transactional
    public void moveNodeByNodeId(UUID node_id, UUID target_position, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }

        FolderNodeEntity targetNode = findUserFolderNodeIfExist(target_position, user_id);
        if(targetNode == null) {
            throw new ParentNodeNotExistException("目标父节点不存在");
        }
        // 检查是否是目标节点 如果是 则直接返回
        if(targetNode.getNode_id().equals(node_id)) {
            return;
        }
        // 检查是否是子节点 如果要做移动操作 则不能移动到其子节点 但是后续可以做目录旋转操作
        if(directoryClosureMapper.isDescendant(node_id, target_position, user_id) > 0) {
            throw new NodeMoveException("不能将文件夹移动到其子节点");
        }
        // 检查目录深度是否超过最大深度
        int newParentDepth = directoryClosureMapper.getMaxDepthToNode(target_position, user_id);
        int currentDepth = directoryClosureMapper.getMaxDepthToNode(node_id, user_id);
        if(newParentDepth + 1 + currentDepth > MAX_DIRECTORY_DEPTH) {
            throw new NodeMoveException("目录深度超过最大深度");
        }

        Integer rows = folderNodeMapper.updateFolderNodeParentIdByIdAndUserId(
                target_position,
                node_id,
                user_id
        );
        if(rows != 1) {
            throw new UpdateException("文件夹移动失败");
        }

        // 更新闭包关系
        directoryClosureMapper.deleteExternalRelationsForMove(node_id, target_position, user_id);
        directoryClosureMapper.insertRelationsForMove(node_id, target_position, user_id);
    }
    @Override
    public void updateNodeNameByNodeId(UUID node_id, String new_node_name, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }

        Integer rows =  folderNodeMapper.updateFolderNodeNameByIdAndUserId(
                new_node_name,
                node_id,
                user_id
        );
        if(rows != 1) {
            throw new UpdateException("文件夹重命名失败");
        }
    }

    //不要忘了开启事务回滚
    @Override
    @Transactional
    public void deleteFolderNodeByNodeId(UUID node_id, UUID user_id) {

        FolderNodeEntity node = folderNodeMapper.findFolderNodeByIdAndUserId(node_id, user_id);
        if(node == null){
            throw new NodeNotExistException("节点不存在");
        }

        FolderNodeEntity.NodeStatus actualStatus = node.getStatus();
        String validStatus = folderNodeMapper.selectFolderEffectiveStatus(node_id, user_id);
        if(actualStatus == FolderNodeEntity.NodeStatus.deleted || FolderNodeEntity.NodeStatus.valueOf(validStatus) == FolderNodeEntity.NodeStatus.deleted) {
            return;
        }

        if(actualStatus == FolderNodeEntity.NodeStatus.lock) {
            throw new NodeStatusException("文件夹被Locked 这个文件夹可能现在正在有文件上传");
        }
//
//        // 删除闭包关系
//        directoryClosureMapper.deleteClosureRowsBySubtree(node_id);

        Integer rows = folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.deleted,
                node_id,
                user_id
        );
        if(rows != 1) {
            throw new UpdateException("文件夹删除失败");
        }
        //发布消息 文件夹子文件物理删除是异步处理业务
    }

    @Override
    @Transactional
    public void deleteFolderNodeToTrashByNodeId(UUID node_id, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(node_id, user_id);
        if(node == null) {
            throw new NodeNotExistException("节点不存在");
        }

        FolderNodeEntity.NodeStatus status = node.getStatus();
        if(status == FolderNodeEntity.NodeStatus.lock) {
            throw new NodeStatusException("文件夹被Locked 这个文件夹可能现在正在有文件上传");
        }

        Integer rows = folderNodeMapper.updateFolderNodeStatusByIdAndUserId(
                FolderNodeEntity.NodeStatus.trashed,
                node_id,
                user_id
        );
        if(rows != 1) {
            throw new UpdateException("文件夹删除到回收站失败");
        }
    }

    /**
     * 私有方法为服务的工具方法不做任何异常的抛出处理 成功就返回有效数据 不成功就返回null或者无效数据
     * 异常抛出和处理是 业务层函数应该做的事情 工具函数严格意义上来不做业务的保证异常的处理 应该交给业务层函数来调用根据返回结果自行决定异常的抛出和处理
     */
    //根据节点ID查询用户文件夹节点是否有效存在 如果不存在或者状态为trashed deleted 则返回null表示不存在
    @Override
    public FolderNodeEntity findUserFolderNodeIfExist(UUID node_id, UUID user_id) {
        FolderNodeEntity node = folderNodeMapper.findFolderNodeByIdAndUserId(node_id, user_id);

        if(node == null) return null;

        //先查自己节点的真实状态
        FolderNodeEntity.NodeStatus actualStatus = node.getStatus();
        if(actualStatus == FolderNodeEntity.NodeStatus.trashed || actualStatus == FolderNodeEntity.NodeStatus.deleted) {
            return null;
        }
        boolean isDeleted = folderNodeMapper.isFolderDeleted(node_id, user_id);
        if(isDeleted) {
            return null;
        }
        return node;
    }

    @Override
    public List<FileEntity> findActiveFilesRecursive(UUID nodeId, UUID userId) {
        // 检查节点是否存在
        FolderNodeEntity node = findUserFolderNodeIfExist(nodeId, userId);
        if (node == null) {
            throw new NodeNotExistException("节点不存在");
        }
        return fileMapper.findActiveFilesByDescendantNodes(nodeId, userId);
    }

    //获取指定文件夹节点的实际状态
    @Override
    public FolderNodeEntity.NodeStatus getFolderNodeActualStatus(UUID node_id, UUID user_id) {
        FolderNodeEntity node = folderNodeMapper.findFolderNodeByIdAndUserId(node_id, user_id);
        if(node == null) return null;

        return node.getStatus();
    }
    //获取指定文件夹节点的有效状态
    @Override
    public FolderNodeEntity.NodeStatus getFolderNodeValidStatus(UUID node_id, UUID user_id) {
        // 检查节点是否存在
        FolderNodeEntity node = folderNodeMapper.findFolderNodeByIdAndUserId(node_id, user_id);
        if(node == null) return null;
        //有效状态取决与祖父节点目录的状态 受祖父目录节点状态的影响 有效节点状态是可以被继承的
        //查询祖父节点状态 如果祖父节点状态为trashed deleted 则当前节点状态为trashed deleted
        //通过目录闭包关系查询祖父节点状态 因为祖父节点的查询涉及到跨级目录查询 所以需要使用目录闭包关系
        //如果节点的祖父节点状态为trashed 那么节点有效状态也为trashed 如果祖父节点状态有一个为deleted
        //那么节点有效状态为deleted 但是如果自己节点的真实状态为trashed 则节点有效状态为trashed 这个叫回收站隔离
        //放入回收站的文件夹 不能被查询 CURD 操作 同样的它也被回收站隔离了失去了正常状态 也失去了目录结构继承关系 不会受到
        //父节点deleted的影响
        FolderNodeEntity.NodeStatus actualStatus = node.getStatus();
        if(actualStatus == FolderNodeEntity.NodeStatus.trashed || actualStatus == FolderNodeEntity.NodeStatus.deleted) {
            return actualStatus;
        }
        String validStatus = folderNodeMapper.selectFolderEffectiveStatus(node_id, user_id);
        //Deleted > Trashed > Active
        return FolderNodeEntity.NodeStatus.valueOf(validStatus);
    }
}
