package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.project.control.result.JsonResult;
import org.project.mapper.DirectoryClosureMapper;
import org.project.mapper.FolderNodeMapper;
import org.project.mapper.PathNodeInfo;
import org.project.model.dto.CreateFolderNodeRequest;
import org.project.model.dto.FolderFileInfo;
import org.project.model.dto.LazyUploadSessionRequest;
import org.project.model.dto.LazyUploadSessionResponse;
import org.project.model.dto.MoveNodeRequest;
import org.project.model.dto.RenameNodeRequest;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.model.vo.FolderNodeVO;
import org.project.model.vo.NodeVO;
import org.project.model.vo.PageResultVO;
import org.project.model.vo.PathChildrenVO;
import org.project.model.vo.VoMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.SpaceService;
import org.project.service.UploadsService;
import org.project.service.UserService;
import org.project.service.ex.NodeNotExistException;
import org.project.service.ex.OverstepAuthorityException;
import org.project.util.ClientIpUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@Validated
@RequestMapping("/business/nodes")
public class NodeController extends BaseController {
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private UserService userService;
    @Autowired
    private DirectoryClosureMapper directoryClosureMapper;
    @Autowired
    private FolderNodeMapper folderNodeMapper;
    @Autowired
    private UploadsService uploadsService;

    @GetMapping("/root")
    public JsonResult<FolderNodeVO> findRootNode(@RequestHeader("X-User-Id") String user_id) {
        FolderNodeEntity rootFolderNode = userService.findRootFolderNodeByUserId(UUID.fromString(user_id));
        return new JsonResult<>(OK, VoMapper.toFolderNodeVO(rootFolderNode));
    }

    @GetMapping({"/{node_id}", "/{node_id}/"})
    public JsonResult<FolderNodeVO> findNodeById(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        FolderNodeEntity node = directoryTreeService.queryUserFolderNodeById(UUID.fromString(node_id), UUID.fromString(user_id));

        return new JsonResult<>(OK, VoMapper.toFolderNodeVO(node));
    }

    /**
     * 递归获取文件夹下所有文件信息（用于文件夹下载）
     * 通过目录闭包表查询所有子孙节点下的活跃文件，返回文件元数据含存储路径和相对路径
     */
    @GetMapping("/{node_id}/files")
    public JsonResult<List<FolderFileInfo>> getFolderFilesRecursive(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        UUID rootNodeId = UUID.fromString(node_id);
        UUID userId = UUID.fromString(user_id);

        List<FileEntity> files = directoryTreeService.findActiveFilesRecursive(rootNodeId, userId);

        if (files.isEmpty()) {
            return new JsonResult<>(OK, List.of());
        }

        // 收集所有唯一的文件节点ID
        List<UUID> nodeIds = files.stream()
                .map(FileEntity::getNode_id)
                .distinct()
                .toList();

        // 查询所有文件节点的祖先链路径
        List<PathNodeInfo> pathInfos = directoryClosureMapper.selectAncestorPaths(
                nodeIds, userId, rootNodeId);

        // 构建 nodeId -> 路径片段 的映射（按 descendant 分组，depth DESC 排序）
        Map<UUID, String> nodePathMap = new java.util.HashMap<>();
        Map<UUID, List<PathNodeInfo>> grouped = pathInfos.stream()
                .collect(java.util.stream.Collectors.groupingBy(PathNodeInfo::getDescendantId));

        for (Map.Entry<UUID, List<PathNodeInfo>> entry : grouped.entrySet()) {
            // depth DESC，所以先排序
            List<PathNodeInfo> sorted = entry.getValue().stream()
                    .sorted(java.util.Comparator.comparingInt(PathNodeInfo::getDepth).reversed())
                    .toList();
            String path = sorted.stream()
                    .map(PathNodeInfo::getNodeName)
                    .collect(java.util.stream.Collectors.joining("/"));
            nodePathMap.put(entry.getKey(), path);
        }

        List<FolderFileInfo> result = files.stream()
                .map(f -> {
                    String nodePath = nodePathMap.getOrDefault(f.getNode_id(), "");
                    String relativePath = nodePath.isEmpty()
                            ? f.getName()
                            : nodePath + "/" + f.getName();
                    return new FolderFileInfo(
                            f.getId().toString(),
                            f.getName(),
                            f.getSize(),
                            f.getStorage_path(),
                            f.getNode_id().toString(),
                            relativePath);
                })
                .toList();

        return new JsonResult<>(OK, result);
    }

    @GetMapping("/{node_id}/children")
    public JsonResult<List<NodeVO>> findNodesByNodeId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id ) {
        List<NodeEntity> nodeList = directoryTreeService.findUserNodesByNodeId(UUID.fromString(node_id), UUID.fromString(user_id));
        return new JsonResult<>(OK, VoMapper.toNodeVOList(nodeList));
    }
    
    /**
     * 分页查询节点子节点（支持搜索、过滤、排序）
     */
    @GetMapping("/{node_id}/children/paged")
    public JsonResult<PageResultVO<NodeVO>> findNodesByNodeIdPaged(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String fileType,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortOrder,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize,
            @RequestHeader("X-User-Id") String user_id) {
        
        PageResultVO<NodeEntity> result = directoryTreeService.findUserNodesByNodeIdPaged(
                node_id, keyword, fileType, sortBy, sortOrder, page, pageSize, UUID.fromString(user_id));
        
        List<NodeVO> voList = VoMapper.toNodeVOList(result.getItems());
        PageResultVO<NodeVO> voResult = new PageResultVO<>(
                voList, 
                result.getTotal(), 
                result.getPage(), 
                result.getPageSize()
        );
        
        return new JsonResult<>(OK, voResult);
    }

    @PostMapping("/")
    public JsonResult<Void> createFolderNode(
            @Valid @RequestBody CreateFolderNodeRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        UUID userId = UUID.fromString(user_id);

        // 模式1：node_id + 相对路径 → 先 lazy ensure 路径，再在目标位置创建文件夹
        if (request.getRelative_path() != null && !request.getRelative_path().isBlank()) {
            UUID targetParentId = directoryTreeService.ensureFolderPath(
                    userId, UUID.fromString(request.getNode_id()), request.getRelative_path());
            directoryTreeService.createFolderNode(userId, targetParentId, request.getFolder_name());
            return new JsonResult<>(OK);
        }

        // 模式2：纯面包屑路径 → 先 lazy ensure 路径，再在目标位置创建文件夹
        if (request.getBreadcrumb_path() != null && !request.getBreadcrumb_path().isBlank()) {
            String fullPath = request.getBreadcrumb_path() + "/" + request.getFolder_name();
            // 直接通过面包屑路径逐级创建
            directoryTreeService.ensureFolderPath(userId, fullPath);
            return new JsonResult<>(OK);
        }

        // 模式3：原有 node_id 模式
        directoryTreeService.createFolderNode(userId, UUID.fromString(request.getNode_id()), request.getFolder_name());
        return new JsonResult<>(OK);
    }

    /**
     * 懒上传会话创建 — 混合模型（node_id + 相对路径 / 纯面包屑路径）。
     * <p>
     * 上传即创建路径：自动创建相对路径或面包屑路径中不存在的目录，
     * 然后创建上传会话，返回 uploads_id + 最终 node_id。
     */
    @PostMapping("/uploads/lazy")
    public JsonResult<LazyUploadSessionResponse> createLazyUploadSession(
            @Valid @RequestBody LazyUploadSessionRequest request,
            @RequestHeader("X-User-Id") String user_id,
            HttpServletRequest httpRequest) {
        String clientIp = ClientIpUtil.resolveClientIp(httpRequest);
        UUID userId = UUID.fromString(user_id);
        UUID parentNodeId = null;
        if (request.getParent_node_id() != null && !request.getParent_node_id().isBlank()) {
            parentNodeId = UUID.fromString(request.getParent_node_id());
        }

        LazyUploadSessionResponse response = uploadsService.createLazyUploadSession(
                request.getTotal_chunks(),
                request.getFile_size(),
                request.getFile_checksum(),
                request.getChunks_max_size(),
                request.getFile_name(),
                request.getFile_type(),
                userId,
                parentNodeId,
                request.getRelative_path(),
                request.getBreadcrumb_path(),
                clientIp);

        return new JsonResult<>(OK, response);
    }

    // ==================== 路径解析与路径查询接口 ====================

    /**
     * 路径 → node_id 转换接口。
     * 支持两种模式：
     * <ul>
     *   <li>绝对路径：{@code absolute_path}（如 "/my_disk/folder1/sub"）</li>
     *   <li>node_id + 相对路径：{@code node_id} + {@code relative_path}（如 node_id + "subfolder1/subfolder2"）</li>
     * </ul>
     * 返回目标节点的 node_id。
     */
    @GetMapping("/resolve-path")
    public JsonResult<Map<String, String>> resolvePathToNodeId(
            @RequestParam(required = false) String absolute_path,
            @RequestParam(required = false) String relative_path,
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @RequestParam(required = false) String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        UUID userId = UUID.fromString(user_id);
        UUID targetNodeId;

        if (absolute_path != null && !absolute_path.isBlank()) {
            // 模式1：绝对路径查询
            targetNodeId = directoryTreeService.resolveAbsolutePathToNodeId(userId, absolute_path);
        } else if (relative_path != null && !relative_path.isBlank() && node_id != null && !node_id.isBlank()) {
            // 模式2：node_id + 相对路径查询
            targetNodeId = directoryTreeService.resolveRelativePathToNodeId(
                    userId, UUID.fromString(node_id), relative_path);
        } else {
            throw new IllegalArgumentException("必须提供 absolute_path 或 (node_id + relative_path)");
        }

        Map<String, String> result = new HashMap<>();
        result.put("node_id", targetNodeId.toString());
        return new JsonResult<>(OK, result);
    }

    /**
     * 路径方式查询子节点（混合查询模型）。
     * 支持三种模式：
     * <ul>
     *   <li>绝对路径：{@code absolute_path}（如 "/my_disk/folder1/sub"）</li>
     *   <li>node_id + 相对路径：{@code node_id} + {@code relative_path}</li>
     *   <li>纯 node_id：{@code node_id}（不传路径参数时）</li>
     * </ul>
     * 返回 { node_id, children } 结构，其中 node_id 是解析后的目标节点 ID 供客户端保存。
     */
    @GetMapping("/children-by-path")
    public JsonResult<PathChildrenVO> getChildrenByPath(
            @RequestParam(required = false) String absolute_path,
            @RequestParam(required = false) String relative_path,
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @RequestParam(required = false) String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        UUID userId = UUID.fromString(user_id);
        UUID targetNodeId;

        if (absolute_path != null && !absolute_path.isBlank()) {
            // 模式1：绝对路径查询
            targetNodeId = directoryTreeService.resolveAbsolutePathToNodeId(userId, absolute_path);
        } else if (relative_path != null && !relative_path.isBlank() && node_id != null && !node_id.isBlank()) {
            // 模式2：node_id + 相对路径查询
            targetNodeId = directoryTreeService.resolveRelativePathToNodeId(
                    userId, UUID.fromString(node_id), relative_path);
        } else if (node_id != null && !node_id.isBlank()) {
            // 模式3：纯 node_id 查询
            targetNodeId = UUID.fromString(node_id);
        } else {
            throw new IllegalArgumentException("必须提供 absolute_path、relative_path+node_id 或 node_id");
        }

        List<NodeEntity> nodeList = directoryTreeService.findUserNodesByNodeId(targetNodeId, userId);
        PathChildrenVO result = new PathChildrenVO(
                targetNodeId.toString(),
                VoMapper.toNodeVOList(nodeList));
        return new JsonResult<>(OK, result);
    }

    @DeleteMapping({"/{node_id}", "/{node_id}/"})
    public JsonResult<Void> deleteNodeByNodeId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id ) {
        directoryTreeService.deleteFolderNodeByNodeId(UUID.fromString(node_id), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }

    @PatchMapping({"/{node_id}/position", "/{node_id}/position/"})
    public JsonResult<Void> moveNodeByNodeId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @Valid @RequestBody MoveNodeRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        directoryTreeService.moveNodeByNodeId(UUID.fromString(node_id), UUID.fromString(request.getTarget_position()), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }

    @PatchMapping({"/{node_id}/name", "/{node_id}/name/"})
    public JsonResult<Void> updateNodeNameByNodeId(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @Valid @RequestBody RenameNodeRequest request,
            @RequestHeader("X-User-Id") String user_id ) {
        directoryTreeService.updateNodeNameByNodeId(UUID.fromString(node_id), request.getNew_node_name(), UUID.fromString(user_id));
        return new JsonResult<>(OK);
    }
}
