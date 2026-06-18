package org.project.control;

import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.FileSearchRequest;
import org.project.model.vo.FileSearchVO;
import org.project.service.FileSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/files")
@RequiredArgsConstructor
public class FileSearchController extends BaseController {

    @Autowired
    private FileSearchService searchService;

    /**
     * 全文搜索文件
     * <p>接口层职责：提取 Request DTO 参数 → 调用业务层 → 返回 VO
     */
    @GetMapping("/advanced-search")
    public JsonResult<FileSearchVO> searchFiles(
            FileSearchRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        FileSearchVO result = searchService.search(
                request.getKeyword(),
                request.getPage(),
                request.getSize(),
                request.getSortField(),
                request.isAsc(),
                user_id,
                request.getFilters(),
                request.getHighlightFields(),
                request.getSearchAfter());
        return new JsonResult<>(OK, result);
    }
}
