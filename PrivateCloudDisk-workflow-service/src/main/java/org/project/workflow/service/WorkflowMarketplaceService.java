package org.project.workflow.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.project.workflow.exception.WorkflowApiException;
import org.project.workflow.model.WorkflowMarketplaceModels.ImportRequest;
import org.project.workflow.model.WorkflowMarketplaceModels.MarketplaceRow;
import org.project.workflow.model.WorkflowMarketplaceModels.ReviewRequest;
import org.project.workflow.model.WorkflowMarketplaceModels.ReviewRow;
import org.project.workflow.model.WorkflowModels.CreateWorkflowRequest;
import org.project.workflow.model.WorkflowModels.WorkflowRow;
import org.project.workflow.repository.WorkflowMarketplaceMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 工作流模板审核、导入与评分服务。导入始终创建新工作流，不共享可变实例。 */
@Service
public class WorkflowMarketplaceService {
    private final WorkflowMarketplaceMapper mapper;
    private final WorkflowService workflowService;
    private final ObjectMapper objectMapper;

    public WorkflowMarketplaceService(
            WorkflowMarketplaceMapper mapper,
            WorkflowService workflowService,
            ObjectMapper objectMapper
    ) {
        this.mapper = mapper;
        this.workflowService = workflowService;
        this.objectMapper = objectMapper;
    }

    public void submit(String workflowId, String userId) {
        requireUuid(workflowId);
        requireUuid(userId);
        if (mapper.submit(workflowId, userId) < 1) {
            throw conflict("只有本人已发布的工作流才能提交市场审核");
        }
    }

    public List<MarketplaceRow> list(String category, String query, int page, int size) {
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() > 120) {
            throw invalid("搜索关键字不能超过 120 个字符");
        }
        return mapper.list(
                category == null ? "" : category,
                safeQuery,
                safeSize,
                (Math.max(page, 1) - 1) * safeSize
        );
    }

    @Transactional
    public WorkflowRow importTemplate(
            String templateId,
            String userId,
            String spaceId,
            ImportRequest request
    ) {
        requireUuid(templateId);
        var source = mapper.findTemplate(templateId);
        if (source == null) {
            throw new WorkflowApiException(
                    "WF-MARKET-NOT-FOUND", HttpStatus.NOT_FOUND, "工作流模板不存在或已下架"
            );
        }
        Map<String, Object> graph = readGraph(source.graphJson());
        WorkflowRow created = workflowService.create(
                userId,
                spaceId,
                new CreateWorkflowRequest(
                        request.name(),
                        request.slug(),
                        source.description(),
                        source.dslText(),
                        graph
                )
        );
        mapper.incrementInstall(templateId);
        return created;
    }

    @Transactional
    public void rate(
            String workflowId,
            String userId,
            ReviewRequest request
    ) {
        requireUuid(workflowId);
        requireUuid(userId);
        if (mapper.findTemplate(workflowId) == null) {
            throw new WorkflowApiException(
                    "WF-MARKET-NOT-FOUND", HttpStatus.NOT_FOUND, "工作流模板不存在或已下架"
            );
        }
        mapper.upsertReview(
                workflowId,
                userId,
                request.rating(),
                request.comment() == null ? "" : request.comment().trim()
        );
        mapper.refreshRating(workflowId);
    }

    public List<ReviewRow> reviews(String workflowId, int page, int size) {
        requireUuid(workflowId);
        int safeSize = Math.max(1, Math.min(size, 100));
        return mapper.reviews(workflowId, safeSize, (Math.max(page, 1) - 1) * safeSize);
    }

    public void review(String workflowId, String status) {
        requireUuid(workflowId);
        if (!Set.of("APPROVED", "REJECTED").contains(status)
                || mapper.review(workflowId, status) != 1) {
            throw invalid("工作流市场审核状态无效");
        }
    }

    private Map<String, Object> readGraph(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private static void requireUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (Exception exception) {
            throw invalid("资源标识无效");
        }
    }

    private static WorkflowApiException invalid(String message) {
        return new WorkflowApiException(
                "WF-MARKET-REQUEST-INVALID", HttpStatus.UNPROCESSABLE_ENTITY, message
        );
    }

    private static WorkflowApiException conflict(String message) {
        return new WorkflowApiException("WF-MARKET-CONFLICT", HttpStatus.CONFLICT, message);
    }
}
