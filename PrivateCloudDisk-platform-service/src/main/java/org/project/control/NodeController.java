package org.project.control;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.project.control.result.JsonResult;
import org.project.model.dto.CreateFolderNodeRequest;
import org.project.model.dto.FolderFileInfo;
import org.project.model.dto.MoveNodeRequest;
import org.project.model.dto.NodeQueryDTO;
import org.project.model.dto.RenameNodeRequest;
import org.project.model.entity.FileEntity;
import org.project.model.entity.FolderNodeEntity;
import org.project.model.entity.NodeEntity;
import org.project.model.vo.FolderNodeVO;
import org.project.model.vo.NodeVO;
import org.project.model.vo.PageResultVO;
import org.project.model.vo.VoMapper;
import org.project.service.DirectoryTreeService;
import org.project.service.UserService;
import org.project.service.ex.OverstepAuthorityException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@Validated
@RequestMapping("/business/nodes")
public class NodeController extends BaseController {
    @Autowired
    private DirectoryTreeService directoryTreeService;
    @Autowired
    private UserService userService;

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
     * 通过目录闭包表查询所有子孙节点下的活跃文件，返回文件元数据含存储路径
     */
    @GetMapping("/{node_id}/files")
    public JsonResult<List<FolderFileInfo>> getFolderFilesRecursive(
            @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
                    message = "node_id必须是有效的UUID格式")
            @PathVariable String node_id,
            @RequestHeader("X-User-Id") String user_id) {
        List<FileEntity> files = directoryTreeService.findActiveFilesRecursive(
                UUID.fromString(node_id), UUID.fromString(user_id));
        
        List<FolderFileInfo> result = files.stream()
                .map(f -> new FolderFileInfo(
                        f.getId().toString(),
                        f.getName(),
                        f.getSize(),
                        f.getStorage_path()))
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
        
        NodeQueryDTO query = new NodeQueryDTO();
        query.setParentId(node_id);
        query.setKeyword(keyword);
        query.setFileType(fileType);
        query.setSortBy(sortBy);
        query.setSortOrder(sortOrder);
        query.setPage(page);
        query.setPageSize(pageSize);
        
        PageResultVO<NodeEntity> result = directoryTreeService.findUserNodesByNodeIdPaged(query, UUID.fromString(user_id));
        
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
        directoryTreeService.createFolderNode(UUID.fromString(user_id), UUID.fromString(request.getNode_id()), request.getFolder_name());
        return new JsonResult<>(OK);
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
