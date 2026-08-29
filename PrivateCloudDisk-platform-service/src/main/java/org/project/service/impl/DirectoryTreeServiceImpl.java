package org.project.service.impl;

import org.project.context.SpaceContextHolder;
import org.project.mapper.DirectoryClosureMapper;
import org.project.mapper.TrashTargetMapper;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.mapper.FileMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.model.vo.PageResultVO;
import org.project.security.RedisRateLimiterService;
import org.project.service.DirectoryTreeService;
import org.project.service.PathCacheService;
import org.project.service.ex.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DirectoryTreeServiceImpl implements DirectoryTreeService {
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private DirectoryClosureMapper directoryClosureMapper;
    @Autowired
    private FileMapper fileMapper;
    @Autowired
    private PathCacheService pathCacheService;
    @Autowired
    private RedisRateLimiterService rateLimiterService;
    private final int MAX_DIRECTORY_DEPTH = 15;
    private final int MAX_CHILDREN_PER_FOLDER = 1000;
    private final int MAX_FOLDERS_PER_USER = 100000;
    private final int MAX_PATH_LENGTH = 1024;
    private static final Duration RATE_LIMIT_WINDOW = Duration.ofMinutes(1);
    private static final int RATE_LIMIT_MAX_FOLDER_CREATE = 100;

    @Override
    @Transactional
    public void createFolderNode(UUID user_id, UUID node_id, String name) {
        // 速率限制检查
        checkFolderCreateRateLimit(user_id);

        FolderNodeEntity folderNodeEntity = new FolderNodeEntity();
        folderNodeEntity.setUser_id(user_id);
        folderNodeEntity.setStatus(FolderNodeEntity.NodeStatus.active);
        folderNodeEntity.setCreate_time(LocalDateTime.now().toString());
        folderNodeEntity.setName(name);
        folderNodeEntity.setNode_id(UUID.randomUUID());
        folderNodeEntity.setParent_id(null);
        /*
         * 需求：空间管理能力全量集成（五-1）。
         * 原行为：目录只记录创建用户；新行为：额外写入请求空间，原 user_id 审计语义保留。
         */
        folderNodeEntity.setSpace_id(SpaceContextHolder.getSpaceId());

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

            // 检查子节点数量限制
            int childCount = folderNodeMapper.countChildrenByNodeId(node_id, user_id);
            if (childCount >= MAX_CHILDREN_PER_FOLDER) {
                throw new FolderChildrenLimitExceededException("文件夹子节点已达上限: " + MAX_CHILDREN_PER_FOLDER);
            }

            // 检查用户文件夹总量
            int totalFolders = folderNodeMapper.countUserFolders(user_id);
            if (totalFolders >= MAX_FOLDERS_PER_USER) {
                throw new FolderQuotaExceededException("用户文件夹数量已达上限: " + MAX_FOLDERS_PER_USER);
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

        // 使路径缓存失效
        pathCacheService.invalidateUser(user_id);
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
        if (parentId == null || parentId.isBlank()) {
            throw new IllegalArgumentException("父目录 ID 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        // The legacy mapper already applies the current SpaceContext and filters
        // deleted/trashed nodes.  Keep that security boundary and perform the
        // optional presentation filtering here until the dedicated SQL page
        // query is introduced.  Returning null here used to make every internal
        // api:file.list request fail with a platform-side NullPointerException.
        List<NodeEntity> all = findUserNodesByNodeId(UUID.fromString(parentId.trim()), userId);
        String normalizedKeyword = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String normalizedType = fileType == null ? "" : fileType.trim().toLowerCase(Locale.ROOT);

        List<NodeEntity> filtered = all.stream()
                .filter(node -> normalizedKeyword.isEmpty()
                        || (node.getNode_name() != null
                        && node.getNode_name().toLowerCase(Locale.ROOT).contains(normalizedKeyword)))
                .filter(node -> normalizedType.isEmpty()
                        || (node.getNode_type() != null
                        && normalizedType.equals(node.getNode_type().name().toLowerCase(Locale.ROOT))))
                .sorted(nodeComparator(sortBy, sortOrder))
                .toList();

        int safePage = page == null ? 1 : Math.max(1, page);
        int safePageSize = pageSize == null ? 20 : Math.min(200, Math.max(1, pageSize));
        long fromLong = (long) (safePage - 1) * safePageSize;
        int from = fromLong >= filtered.size() ? filtered.size() : (int) fromLong;
        int to = Math.min(filtered.size(), from + safePageSize);
        return new PageResultVO<>(
                filtered.subList(from, to), (long) filtered.size(), safePage, safePageSize);
    }

    private static Comparator<NodeEntity> nodeComparator(String sortBy, String sortOrder) {
        String field = sortBy == null ? "name" : sortBy.trim().toLowerCase(Locale.ROOT);
        Comparator<NodeEntity> comparator = switch (field) {
            case "size", "node_size" -> Comparator.comparing(
                    NodeEntity::getNode_size, Comparator.nullsFirst(Long::compareTo));
            case "type", "node_type" -> Comparator.comparing(
                    node -> node.getNode_type() == null ? "" : node.getNode_type().name());
            default -> Comparator.comparing(
                    NodeEntity::getNode_name, Comparator.nullsFirst(String.CASE_INSENSITIVE_ORDER));
        };
        Comparator<NodeEntity> stableId = Comparator.comparing(
                node -> node.getNode_id() == null ? "" : node.getNode_id().toString());
        Comparator<NodeEntity> ordered = comparator.thenComparing(stableId);
        return "desc".equalsIgnoreCase(sortOrder) ? ordered.reversed() : ordered;
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

        // 使路径缓存失效
        pathCacheService.invalidateUser(user_id);
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

        // 使路径缓存失效
        pathCacheService.invalidateUser(user_id);
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
        // 使路径缓存失效
        pathCacheService.invalidateUser(user_id);
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

    // ==================== 懒创建文件夹路径 ====================

    @Override
    @Transactional
    public UUID ensureFolderPath(UUID userId, UUID parentNodeId, String relativePath) {
        // 1. 路径校验
        validatePath(relativePath);

        // 2. 检查父节点是否存在
        FolderNodeEntity parentNode = findUserFolderNodeIfExist(parentNodeId, userId);
        if (parentNode == null) {
            throw new ParentNodeNotExistException("父节点不存在: " + parentNodeId);
        }

        // 3. 尝试从缓存获取
        if (relativePath.isEmpty()) {
            return parentNodeId;
        }

        UUID cached = pathCacheService.getRelativePath(userId, parentNodeId, relativePath);
        if (cached != null) {
            // 验证缓存是否仍然有效
            FolderNodeEntity cachedNode = findUserFolderNodeIfExist(cached, userId);
            if (cachedNode != null) {
                return cached;
            }
            // 缓存失效，继续创建
        }

        // 4. 拆路径，逐级 ensure
        String[] parts = relativePath.split("/");
        validatePathDepth(parts.length, parentNodeId, userId);

        UUID currentParentId = parentNodeId;
        for (String folderName : parts) {
            if (folderName.isEmpty()) continue;
            currentParentId = ensureSingleFolder(userId, currentParentId, folderName);
        }

        // 5. 缓存结果
        pathCacheService.cacheRelativePath(userId, parentNodeId, relativePath, currentParentId);

        return currentParentId;
    }

    @Override
    @Transactional
    public UUID ensureFolderPath(UUID userId, String breadcrumbPath) {
        // 1. 路径校验
        validatePath(breadcrumbPath);

        // 2. 标准化路径
        String normalized = breadcrumbPath.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            // 返回根节点
            FolderNodeEntity root = folderNodeMapper.findRootFolderNodeByUserId(userId);
            if (root == null) {
                throw new NodeNotExistException("根节点不存在");
            }
            return root.getNode_id();
        }

        // 3. 尝试从缓存获取
        String cacheKey = "/" + normalized;
        UUID cached = pathCacheService.getAbsolutePath(userId, cacheKey);
        if (cached != null) {
            FolderNodeEntity cachedNode = findUserFolderNodeIfExist(cached, userId);
            if (cachedNode != null) {
                return cached;
            }
        }

        // 4. 拆路径：跳过根节点名称，从根节点的子节点开始
        String[] parts = normalized.split("/");
        validatePathDepth(parts.length, null, userId);

        // 获取根节点
        FolderNodeEntity rootNode = folderNodeMapper.findRootFolderNodeByUserId(userId);
        if (rootNode == null) {
            throw new NodeNotExistException("根节点不存在");
        }

        // 如果路径第一部分是根节点名则跳过
        int startIdx = 0;
        if (parts.length > 0 && parts[0].equals(rootNode.getName())) {
            startIdx = 1;
        }

        UUID currentParentId = rootNode.getNode_id();
        for (int i = startIdx; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            currentParentId = ensureSingleFolder(userId, currentParentId, parts[i]);
        }

        // 5. 缓存结果
        pathCacheService.cacheAbsolutePath(userId, cacheKey, currentParentId);

        return currentParentId;
    }

    /**
     * 确保单个文件夹存在于指定父节点下。幂等：已存在直接返回。
     */
    private UUID ensureSingleFolder(UUID userId, UUID parentId, String folderName) {
        // 1. 幂等检查：是否已存在同名文件夹
        FolderNodeEntity existing = folderNodeMapper.findFolderNodeByParentIdAndName(parentId, folderName, userId);
        if (existing != null) {
            return existing.getNode_id();
        }

        // 2. 速率限制检查（业务层：成功创建才计数）
        checkFolderCreateRateLimit(userId);

        // 3. 宽度限制检查
        int childCount = folderNodeMapper.countChildrenByNodeId(parentId, userId);
        if (childCount >= MAX_CHILDREN_PER_FOLDER) {
            throw new FolderChildrenLimitExceededException(
                    "文件夹 '" + folderName + "' 子节点已达上限: " + MAX_CHILDREN_PER_FOLDER);
        }

        // 4. 总量限制检查
        int totalFolders = folderNodeMapper.countUserFolders(userId);
        if (totalFolders >= MAX_FOLDERS_PER_USER) {
            throw new FolderQuotaExceededException(
                    "用户文件夹数量已达上限: " + MAX_FOLDERS_PER_USER);
        }

        // 5. 深度限制检查
        int currentDepth = directoryClosureMapper.getMaxDepthToNode(parentId, userId);
        if (currentDepth + 1 > MAX_DIRECTORY_DEPTH) {
            throw new PathTooDeepException("目录深度超过最大限制: " + MAX_DIRECTORY_DEPTH);
        }

        // 6. 创建文件夹
        FolderNodeEntity folderNode = new FolderNodeEntity();
        folderNode.setUser_id(userId);
        folderNode.setStatus(FolderNodeEntity.NodeStatus.active);
        folderNode.setCreate_time(LocalDateTime.now().toString());
        folderNode.setName(folderName);
        folderNode.setNode_id(UUID.randomUUID());
        folderNode.setParent_id(parentId);
        // 需求：空间管理能力全量集成（五-1），路径式创建与普通创建使用同一空间上下文。
        folderNode.setSpace_id(SpaceContextHolder.getSpaceId());

        Integer rows = folderNodeMapper.insertFolderNode(folderNode);
        if (rows != 1) {
            throw new InsertException("创建目录失败: " + folderName);
        }

        // 7. 插入闭包关系
        directoryClosureMapper.insertRelationsFromParent(folderNode.getNode_id(), userId, parentId);
        directoryClosureMapper.insertSelf(folderNode.getNode_id(), userId);

        return folderNode.getNode_id();
    }

    // ==================== 路径 → node_id 解析（纯查询，不创建） ====================

    @Override
    public UUID resolveAbsolutePathToNodeId(UUID userId, String absolutePath) {
        // 1. 路径校验
        validatePath(absolutePath);

        // 2. 标准化路径
        String normalized = absolutePath.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            // 返回根节点
            FolderNodeEntity root = folderNodeMapper.findRootFolderNodeByUserId(userId);
            if (root == null) {
                throw new NodeNotExistException("根节点不存在");
            }
            return root.getNode_id();
        }

        // 3. 尝试从缓存获取
        String cacheKey = "/" + normalized;
        UUID cached = pathCacheService.getAbsolutePath(userId, cacheKey);
        if (cached != null) {
            FolderNodeEntity cachedNode = findUserFolderNodeIfExist(cached, userId);
            if (cachedNode != null) {
                return cached;
            }
        }

        // 4. 获取根节点
        FolderNodeEntity rootNode = folderNodeMapper.findRootFolderNodeByUserId(userId);
        if (rootNode == null) {
            throw new NodeNotExistException("根节点不存在");
        }

        // 5. 拆路径，逐级查找
        String[] parts = normalized.split("/");
        validatePathDepth(parts.length, null, userId);

        // 如果路径第一部分是根节点名则跳过
        int startIdx = 0;
        if (parts.length > 0 && parts[0].equals(rootNode.getName())) {
            startIdx = 1;
        }

        UUID currentParentId = rootNode.getNode_id();
        for (int i = startIdx; i < parts.length; i++) {
            String folderName = parts[i];
            if (folderName.isEmpty()) continue;
            FolderNodeEntity child = folderNodeMapper.findFolderNodeByNameAndParentId(
                    folderName, currentParentId, userId);
            if (child == null) {
                throw new NodeNotExistException("路径节点不存在: " + folderName + " (完整路径: " + cacheKey + ")");
            }
            currentParentId = child.getNode_id();
        }

        // 6. 缓存结果
        pathCacheService.cacheAbsolutePath(userId, cacheKey, currentParentId);

        return currentParentId;
    }

    @Override
    public UUID resolveRelativePathToNodeId(UUID userId, UUID parentNodeId, String relativePath) {
        // 1. 路径校验
        validatePath(relativePath);

        // 2. 标准化
        String normalized = relativePath.trim();
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty()) {
            return parentNodeId;
        }

        // 3. 检查父节点是否存在
        FolderNodeEntity parentNode = findUserFolderNodeIfExist(parentNodeId, userId);
        if (parentNode == null) {
            throw new ParentNodeNotExistException("父节点不存在: " + parentNodeId);
        }

        // 4. 尝试从缓存获取
        UUID cached = pathCacheService.getRelativePath(userId, parentNodeId, normalized);
        if (cached != null) {
            FolderNodeEntity cachedNode = findUserFolderNodeIfExist(cached, userId);
            if (cachedNode != null) {
                return cached;
            }
        }

        // 5. 拆路径，逐级查找
        String[] parts = normalized.split("/");
        validatePathDepth(parts.length, parentNodeId, userId);

        UUID currentParentId = parentNodeId;
        for (String folderName : parts) {
            if (folderName.isEmpty()) continue;
            FolderNodeEntity child = folderNodeMapper.findFolderNodeByNameAndParentId(
                    folderName, currentParentId, userId);
            if (child == null) {
                throw new NodeNotExistException("路径节点不存在: " + folderName
                        + " (父节点: " + currentParentId + ")");
            }
            currentParentId = child.getNode_id();
        }

        // 6. 缓存结果
        pathCacheService.cacheRelativePath(userId, parentNodeId, normalized, currentParentId);

        return currentParentId;
    }

    // ==================== 路径校验 ====================

    private void validatePath(String path) {
        if (path == null) {
            throw new IllegalArgumentException("路径不能为空");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new PathTooLongException("路径长度超过限制: " + MAX_PATH_LENGTH + " chars");
        }
        // 禁止路径中包含非法字符
        if (path.contains("..") || path.contains("\\")) {
            throw new IllegalArgumentException("路径包含非法字符");
        }
    }

    private void validatePathDepth(int partsLength, UUID parentNodeId, UUID userId) {
        int baseDepth = 0;
        if (parentNodeId != null) {
            baseDepth = directoryClosureMapper.getMaxDepthToNode(parentNodeId, userId);
        }
        if (baseDepth + partsLength > MAX_DIRECTORY_DEPTH) {
            throw new PathTooDeepException("路径深度超过最大限制: " + MAX_DIRECTORY_DEPTH);
        }
    }

    // ==================== 速率限制 ====================

    private void checkFolderCreateRateLimit(UUID userId) {
        String rateLimitKey = "pcd:rate-limit:folder:create:" + userId;
        long current = rateLimiterService.increment(rateLimitKey, RATE_LIMIT_WINDOW);
        if (current > RATE_LIMIT_MAX_FOLDER_CREATE) {
            throw new RateLimitExceededException(
                    "文件夹创建速率超限: " + RATE_LIMIT_MAX_FOLDER_CREATE + "/min，请稍后再试");
        }
    }
}
