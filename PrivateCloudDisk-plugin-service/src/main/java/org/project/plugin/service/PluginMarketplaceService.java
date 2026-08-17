package org.project.plugin.service;

import lombok.RequiredArgsConstructor;
import org.project.plugin.exception.PluginApiException;
import org.project.plugin.model.MarketplacePluginRow;
import org.project.plugin.model.PluginRatingRequest;
import org.project.plugin.model.PluginRatingRow;
import org.project.plugin.model.PluginRow;
import org.project.plugin.repository.PluginManagementMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** 插件市场审核、检索、评分应用服务。市场展示只读取已审核的不可变版本。 */
@Service
@RequiredArgsConstructor
public class PluginMarketplaceService {
    private static final Set<String> TYPES = Set.of("", "CLOUD_PLUGIN", "LOCAL_PLUGIN");
    private final PluginManagementMapper mapper;

    @Transactional
    public void submit(String pluginId, String userId) {
        requireUuid(pluginId);
        requireUuid(userId);
        PluginRow plugin = mapper.findOwned(pluginId, userId);
        if (plugin == null || !"PUBLISHED".equals(plugin.status())
                || !"PUBLIC".equals(plugin.visibility())) {
            throw new PluginApiException(
                    "PLG-MARKET-SUBMIT-CONFLICT",
                    HttpStatus.CONFLICT,
                    "只有已发布且公开的插件才能提交市场审核"
            );
        }
        mapper.submitMarketplaceReview(pluginId, userId);
    }

    public List<MarketplacePluginRow> list(
            String type, String category, String query, int page, int size
    ) {
        String safeType = type == null ? "" : type;
        if (!TYPES.contains(safeType)) {
            throw invalid("插件类型筛选无效");
        }
        int safeSize = Math.max(1, Math.min(size, 100));
        String safeQuery = query == null ? "" : query.trim();
        if (safeQuery.length() > 120) {
            throw invalid("搜索关键字不能超过 120 个字符");
        }
        return mapper.listMarketplace(
                safeType,
                category == null ? "" : category,
                safeQuery,
                safeSize,
                (Math.max(page, 1) - 1) * safeSize
        );
    }

    @Transactional
    public void rate(String pluginId, String userId, PluginRatingRequest request) {
        requireUuid(pluginId);
        requireUuid(userId);
        if (mapper.countApprovedMarketplacePlugin(pluginId) != 1) {
            throw new PluginApiException(
                    "PLG-MARKET-NOT-FOUND",
                    HttpStatus.NOT_FOUND,
                    "市场插件不存在或已下架"
            );
        }
        mapper.upsertRating(
                pluginId,
                userId,
                request.rating(),
                request.comment() == null ? "" : request.comment().trim()
        );
    }

    public List<PluginRatingRow> ratings(String pluginId, int page, int size) {
        requireUuid(pluginId);
        int safeSize = Math.max(1, Math.min(size, 100));
        return mapper.listRatings(pluginId, safeSize, (Math.max(page, 1) - 1) * safeSize);
    }

    @Transactional
    public void review(String pluginId, String status) {
        requireUuid(pluginId);
        if (!Set.of("APPROVED", "REJECTED").contains(status)
                || mapper.reviewMarketplace(pluginId, status) != 1) {
            throw invalid("市场审核状态或插件状态无效");
        }
    }

    private static void requireUuid(String value) {
        try {
            UUID.fromString(value);
        } catch (Exception exception) {
            throw invalid("插件或用户标识无效");
        }
    }

    private static PluginApiException invalid(String message) {
        return new PluginApiException(
                "PLG-MARKET-REQUEST-INVALID",
                HttpStatus.UNPROCESSABLE_ENTITY,
                message
        );
    }
}
