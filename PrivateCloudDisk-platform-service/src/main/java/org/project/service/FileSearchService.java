package org.project.service;

import org.project.model.dto.FileSearchRequest;
import org.project.model.vo.FileSearchVo;

public interface FileSearchService {
    /**
     *
     * @param request
     * @return
     */
    FileSearchVo search(FileSearchRequest request);
}
