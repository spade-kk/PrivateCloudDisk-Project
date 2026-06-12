package org.project.control;

import lombok.RequiredArgsConstructor;
import org.project.control.result.JsonResult;
import org.project.model.dto.FileSearchRequest;
import org.project.model.vo.FileSearchVo;
import org.project.service.FileSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/business/files")
@RequiredArgsConstructor
public class FileSearchController extends BaseController {

    @Autowired
    private FileSearchService searchService;

    @GetMapping("/advanced-search")
    public JsonResult<FileSearchVo> searchFiles(
            FileSearchRequest request,
            @RequestHeader("X-User-Id") String user_id) {
        request.setUserId(user_id);
        FileSearchVo result = searchService.search(request);
        return new JsonResult<>(OK, result);
    }
}
