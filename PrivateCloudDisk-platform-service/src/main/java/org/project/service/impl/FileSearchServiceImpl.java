package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.DisMaxQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.opensearch.index.query.functionscore.ScoreFunctionBuilders;
import org.opensearch.search.SearchHit;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.Terms;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.aggregations.metrics.Cardinality;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.collapse.CollapseBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortOrder;
import org.project.model.dto.FileSearchRequest;
import org.project.model.vo.FileSearchVo;
import org.project.service.FileSearchService;
import org.project.service.ex.ServiceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文件搜索服务实现（智能多阶段搜索）
 *
 * <p>搜索策略：基于 keyword 的语义分析，采用多阶段加权查询，
 * 充分利用 OpenSearch 的全文搜索能力，实现智能化的文件检索。</p>
 *
 * <h3>搜索优先级（从高到低）：</h3>
 * <ol>
 *   <li>文件名精确匹配 (filename.keyword) — weight: 15</li>
 *   <li>文件名前缀/部分匹配 (filename.ngram) — weight: 10</li>
 *   <li>文件名中文分词匹配 (filename) — weight: 8</li>
 *   <li>文件扩展名匹配 (file_ext.text) — weight: 7</li>
 *   <li>文件类别匹配 (file_category.text) — weight: 6</li>
 *   <li>关键词提示匹配 (keyword_hints) — weight: 5</li>
 *   <li>标签匹配 (tags) — weight: 4</li>
 *   <li>图片标签匹配 (image_labels) — weight: 4</li>
 *   <li>摘要匹配 (summary) — weight: 3</li>
 *   <li>内容摘要匹配 (content_snippet) — weight: 2</li>
 *   <li>全文内容匹配 (content_text) — weight: 1.5</li>
 *   <li>OCR 文字匹配 (ocr_text) — weight: 1.5</li>
 *   <li>内容分块匹配 (content_chunks.text) — weight: 1</li>
 * </ol>
 *
 * <h3>关键词分类：</h3>
 * <ul>
 *   <li>文件扩展名 (.pdf, .docx, jpg) → 优先匹配 file_ext</li>
 *   <li>文件类型描述 (图片, 视频, 文档) → 优先匹配 file_category + keyword_hints</li>
 *   <li>普通关键词 → 全字段匹配，按优先级排序</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>
 * GET /business/files/advanced-search?keyword=报告&page=1&size=20
 *   → 优先匹配文件名含"报告"的文件，再匹配内容含"报告"的文件
 *
 * GET /business/files/advanced-search?keyword=pdf&page=1&size=20
 *   → 优先匹配扩展名为 pdf 的文件
 *
 * GET /business/files/advanced-search?keyword=图片&page=1&size=20
 *   → 匹配所有图片类文件（file_category=image）
 *
 * GET /business/files/advanced-search?keyword=人&page=1&size=20
 *   → 先匹配文件名含"人"的文件，再匹配 image_labels 含 "person" 的图片
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 包含大文本字段的 source 过滤（搜索时排除以减少传输量） */
    private static final Set<String> EXCLUDED_CONTENT_SOURCE_FIELDS = Set.of(
            "content_text",
            "content_chunks",
            "ocr_text"
    );

    /** 常见文件扩展名模式 */
    private static final Pattern EXT_PATTERN = Pattern.compile(
            "^(pdf|docx?|xlsx?|pptx?|txt|md|csv|jpe?g|png|gif|webp|bmp|svg|" +
            "mp[34]|mov|mkv|avi|wav|zip|rar|7z|tar|gz|py|java|js|ts|html|css|" +
            "json|xml|ya?ml|sql)$",
            Pattern.CASE_INSENSITIVE
    );

    /** 中文文件类型描述关键词 */
    private static final Set<String> FILE_TYPE_KEYWORDS = Set.of(
            "图片", "照片", "图像", "截图", "截屏", "动图",
            "视频", "影片", "电影",
            "音频", "音乐", "声音", "歌曲",
            "文档", "文件",
            "表格", "电子表格", "Excel",
            "演示文稿", "幻灯片", "PPT",
            "压缩包", "压缩文件",
            "代码", "源代码", "脚本",
            "PDF", "Word"
    );

    private final RestHighLevelClient client;

    @Value("${opensearch.file-basic-index:pcd_file_basic}")
    private String basicIndexName;

    @Value("${opensearch.file-content-index:pcd_file_content}")
    private String contentIndexName;

    @Override
    public FileSearchVo search(FileSearchRequest request) {
        normalizePagination(request);

        boolean hasKeyword = hasText(request.getKeyword());
        SearchResponse response = hasKeyword
                ? executeIntelligentKeywordSearch(request)
                : executeBasicMetadataSearch(request);

        FileSearchVo result = new FileSearchVo();
        result.setTotal(resolveTotal(response, hasKeyword));
        result.setHits(toHits(response));
        result.setAggregations(resolveAggregations(response));
        result.setSearchAfter(resolveSearchAfter(response));
        return result;
    }

    // ==================== 核心搜索逻辑 ====================

    /**
     * 智能关键词搜索
     *
     * <p>根据关键词类型，自动选择最优的搜索策略：
     * 如果是文件扩展名 → 优先基础索引搜索
     * 如果是文件类型描述 → 优先基础索引搜索（file_category + keyword_hints）
     * 如果是普通关键词 → 双索引联合搜索，文件名优先、内容兜底</p>
     */
    private SearchResponse executeIntelligentKeywordSearch(FileSearchRequest request) {
        String keyword = request.getKeyword().trim().toLowerCase(Locale.ROOT);

        // 检测关键词类型
        KeywordType keywordType = classifyKeyword(keyword);

        SearchSourceBuilder sourceBuilder = baseSource(request);

        if (keywordType == KeywordType.FILE_EXTENSION) {
            // 扩展名搜索 → 只查基础索引，优先 file_ext
            String ext = keyword.replaceFirst("^\\.", "");
            sourceBuilder.query(buildExtSearchQuery(request, ext));
            addMetadataAggregations(sourceBuilder);
            return search(new SearchRequest(basicIndexName).source(sourceBuilder));
        }

        if (keywordType == KeywordType.FILE_TYPE_DESC) {
            // 文件类型描述搜索 → 只查基础索引，优先 file_category + keyword_hints
            sourceBuilder.query(buildFileTypeDescQuery(request, keyword));
            addMetadataAggregations(sourceBuilder);
            return search(new SearchRequest(basicIndexName).source(sourceBuilder));
        }

        // 普通关键词 → 双索引联合搜索，智能加权
        sourceBuilder.query(buildSmartSearchQuery(request));
        sourceBuilder.collapse(new CollapseBuilder("file_id"));
        sourceBuilder.aggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        addContentMetadataAggregations(sourceBuilder);
        return search(new SearchRequest(basicIndexName, contentIndexName).source(sourceBuilder));
    }

    /**
     * 无关键词的基础元数据搜索
     */
    private SearchResponse executeBasicMetadataSearch(FileSearchRequest request) {
        SearchSourceBuilder sourceBuilder = baseSource(request);
        sourceBuilder.query(buildPermissionFilterQuery(request));
        addMetadataAggregations(sourceBuilder);
        return search(new SearchRequest(basicIndexName).source(sourceBuilder));
    }

    // ==================== 关键词分类 ====================

    /**
     * 关键词类型枚举
     */
    private enum KeywordType {
        /** 普通关键词（全字段匹配） */
        GENERAL,
        /** 文件扩展名（如 pdf, docx, jpg） */
        FILE_EXTENSION,
        /** 文件类型描述（如 图片, 视频, 文档） */
        FILE_TYPE_DESC,
    }

    /**
     * 分类关键词类型
     */
    private KeywordType classifyKeyword(String keyword) {
        // 检测是否为扩展名
        if (EXT_PATTERN.matcher(keyword).matches()) {
            return KeywordType.FILE_EXTENSION;
        }
        // 检测是否为文件类型描述
        if (FILE_TYPE_KEYWORDS.contains(keyword)
                || FILE_TYPE_KEYWORDS.stream().anyMatch(keyword::contains)) {
            return KeywordType.FILE_TYPE_DESC;
        }
        return KeywordType.GENERAL;
    }

    // ==================== 查询构建 ====================

    /**
     * 扩展名搜索查询
     */
    private BoolQueryBuilder buildExtSearchQuery(FileSearchRequest request, String ext) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        applyPermissionAndMetadataFilters(boolQuery, request);

        // 扩展名精确匹配 + 文件名模糊匹配
        boolQuery.must(QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery("file_ext", ext).boost(15f))
                .should(QueryBuilders.matchQuery("filename", ext).boost(5f))
                .should(QueryBuilders.matchQuery("filename.ngram", ext).boost(3f))
                .minimumShouldMatch(1));
        return boolQuery;
    }

    /**
     * 文件类型描述搜索查询
     */
    private BoolQueryBuilder buildFileTypeDescQuery(FileSearchRequest request, String keyword) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        applyPermissionAndMetadataFilters(boolQuery, request);

        boolQuery.must(QueryBuilders.boolQuery()
                .should(QueryBuilders.matchQuery("keyword_hints", keyword).boost(10f))
                .should(QueryBuilders.matchQuery("file_category.text", keyword).boost(8f))
                .should(QueryBuilders.matchQuery("tags", keyword).boost(5f))
                .should(QueryBuilders.matchQuery("filename", keyword).boost(3f))
                .minimumShouldMatch(1));
        return boolQuery;
    }

    /**
     * 智能多阶段搜索查询（核心）
     *
     * <p>使用 dis_max 组合多个子查询，取最高分的子查询得分，
     * 确保每个文件的得分由其最匹配的字段决定。</p>
     *
     * <p>搜索阶段顺序：</p>
     * <ol>
     *   <li>文件名精确匹配（keyword 字段）</li>
     *   <li>文件名部分匹配（ngram 字段）</li>
     *   <li>文件名中文分词匹配</li>
     *   <li>文件扩展名 + 类型 + 类别匹配</li>
     *   <li>关键词提示 + 标签匹配</li>
     *   <li>图片标签匹配</li>
     *   <li>摘要 + 内容摘要匹配</li>
     *   <li>全文内容 + OCR 匹配</li>
     * </ol>
     */
    private BoolQueryBuilder buildSmartSearchQuery(FileSearchRequest request) {
        String keyword = request.getKeyword();

        BoolQueryBuilder rootQuery = QueryBuilders.boolQuery();
        applyPermissionAndMetadataFilters(rootQuery, request);

        // 使用 dis_max 取各个子查询的最好得分
        DisMaxQueryBuilder disMaxQuery = QueryBuilders.disMaxQuery()
                .tieBreaker(0.3f);  // 有多项匹配时给予额外加分

        // ---- 阶段 1: 文件名精确匹配 ----
        disMaxQuery.add(QueryBuilders.boolQuery()
                .should(QueryBuilders.termQuery("filename.keyword", keyword).boost(15f))
                .should(QueryBuilders.matchPhraseQuery("filename.keyword", keyword).boost(12f)));

        // ---- 阶段 2: 文件名 ngram 部分匹配 ----
        disMaxQuery.add(QueryBuilders.matchQuery("filename.ngram", keyword)
                .boost(10f)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 3: 文件名中文分词匹配 ----
        disMaxQuery.add(QueryBuilders.matchQuery("filename", keyword)
                .boost(8f)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 4: 文件扩展名/类型/类别匹配 ----
        disMaxQuery.add(QueryBuilders.multiMatchQuery(keyword)
                .field("file_ext.text", 7f)
                .field("file_type.text", 6f)
                .field("file_category.text", 6f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS));

        // ---- 阶段 5: 关键词提示 + 标签匹配 ----
        disMaxQuery.add(QueryBuilders.multiMatchQuery(keyword)
                .field("keyword_hints", 5f)
                .field("keyword_hints.keyword", 5f)
                .field("tags", 4f)
                .field("tags.keyword", 4f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 6: 图片标签匹配 ----
        disMaxQuery.add(QueryBuilders.multiMatchQuery(keyword)
                .field("image_labels", 4f)
                .field("image_labels.keyword", 4f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 7: 摘要 + 内容摘要匹配 ----
        disMaxQuery.add(QueryBuilders.multiMatchQuery(keyword)
                .field("summary", 3f)
                .field("content_snippet", 2f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 8: 全文内容 + OCR 匹配 ----
        disMaxQuery.add(QueryBuilders.multiMatchQuery(keyword)
                .field("content_text", 1.5f)
                .field("ocr_text", 1.5f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO));

        // ---- 阶段 9: 内容分块 nested 匹配 ----
        disMaxQuery.add(QueryBuilders.nestedQuery(
                "content_chunks",
                QueryBuilders.matchQuery("content_chunks.text", keyword)
                        .fuzziness(Fuzziness.AUTO),
                ScoreMode.Max
        ).boost(1f));

        rootQuery.must(disMaxQuery);
        return rootQuery;
    }

    /**
     * 仅权限过滤查询（无关键词时用）
     */
    private BoolQueryBuilder buildPermissionFilterQuery(FileSearchRequest request) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
        applyPermissionAndMetadataFilters(boolQuery, request);
        boolQuery.must(QueryBuilders.matchAllQuery());
        return boolQuery;
    }

    // ==================== 基础查询构建 ====================

    private SearchSourceBuilder baseSource(FileSearchRequest request) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
        sourceBuilder.from((request.getPage() - 1) * request.getSize());
        sourceBuilder.size(request.getSize());
        sourceBuilder.fetchSource(null, EXCLUDED_CONTENT_SOURCE_FIELDS.toArray(String[]::new));
        sourceBuilder.trackTotalHits(true);

        applySort(sourceBuilder, request);
        applyHighlight(sourceBuilder, request);
        return sourceBuilder;
    }

    private void applyPermissionAndMetadataFilters(BoolQueryBuilder boolQuery, FileSearchRequest request) {
        if (hasText(request.getTenantId())) {
            boolQuery.filter(QueryBuilders.termQuery("tenant_id", request.getTenantId()));
        }
        if (hasText(request.getUserId())) {
            boolQuery.filter(QueryBuilders.termQuery("user_id", request.getUserId()));
        }
        if (hasText(request.getStatus())) {
            boolQuery.filter(QueryBuilders.termQuery("status", normalizeStatus(request.getStatus())));
        }

        if (request.getFilters() == null) {
            return;
        }

        request.getFilters().forEach((rawKey, rawValue) -> {
            if (!hasText(rawKey) || !hasText(rawValue)) {
                return;
            }
            String field = normalizeFilterField(rawKey);
            if (field == null) {
                return;
            }
            boolQuery.filter(QueryBuilders.termQuery(field, normalizeFilterValue(field, rawValue)));
        });
    }

    private void applySort(SearchSourceBuilder sourceBuilder, FileSearchRequest request) {
        String sortField = normalizeSortField(request.getSortField());
        if (sortField == null) {
            sourceBuilder.sort("_score", SortOrder.DESC);
            sourceBuilder.sort("updated_at", SortOrder.DESC);
            return;
        }
        SortOrder order = request.isAsc() ? SortOrder.ASC : SortOrder.DESC;
        sourceBuilder.sort(sortField, order);
        sourceBuilder.sort("_score", SortOrder.DESC);
    }

    private void applyHighlight(SearchSourceBuilder sourceBuilder, FileSearchRequest request) {
        HighlightBuilder highlight = new HighlightBuilder()
                .preTags("<em>")
                .postTags("</em>")
                .requireFieldMatch(false);

        List<String> fields = request.getHighlightFields();
        if (fields == null || fields.isEmpty()) {
            fields = List.of("filename", "summary", "keyword_hints", "content_snippet", "content_text", "ocr_text");
        }
        fields.stream()
                .map(this::normalizeHighlightField)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(field -> highlight.field(
                        new HighlightBuilder.Field(field)
                                .fragmentSize(160)
                                .numOfFragments(3)));

        sourceBuilder.highlighter(highlight);
    }

    private void addMetadataAggregations(SearchSourceBuilder sourceBuilder) {
        TermsAggregationBuilder fileCategoryAgg = AggregationBuilders
                .terms("file_category_agg")
                .field("file_category")
                .size(20)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        TermsAggregationBuilder extAgg = AggregationBuilders
                .terms("file_ext_agg")
                .field("file_ext")
                .size(30)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        sourceBuilder.aggregation(fileCategoryAgg);
        sourceBuilder.aggregation(extAgg);
    }

    /**
     * 内容索引专用聚合（包含 image_labels 等仅在 content_index 中存在的字段）
     */
    private void addContentMetadataAggregations(SearchSourceBuilder sourceBuilder) {
        addMetadataAggregations(sourceBuilder);
        TermsAggregationBuilder imageLabelsAgg = AggregationBuilders
                .terms("image_labels_agg")
                .field("image_labels.keyword")
                .size(30)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        sourceBuilder.aggregation(imageLabelsAgg);
    }

    // ==================== 查询执行 ====================

    private SearchResponse search(SearchRequest searchRequest) {
        try {
            return client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ServiceException("OpenSearch 文件搜索失败: " + e.getMessage());
        }
    }

    // ==================== 结果转换 ====================

    private List<Map<String, Object>> toHits(SearchResponse response) {
        return Arrays.stream(response.getHits().getHits())
                .map(hit -> {
                    Map<String, Object> source = new LinkedHashMap<>(hit.getSourceAsMap());
                    source.put("_index", hit.getIndex());
                    source.put("_score", hit.getScore());
                    Map<String, String> highlights = resolveHighlights(hit);
                    if (!highlights.isEmpty()) {
                        source.put("_highlight", highlights);
                    }
                    return source;
                })
                .collect(Collectors.toList());
    }

    private Map<String, String> resolveHighlights(SearchHit hit) {
        if (hit.getHighlightFields() == null || hit.getHighlightFields().isEmpty()) {
            return Map.of();
        }
        return hit.getHighlightFields().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Arrays.stream(entry.getValue().fragments())
                                .map(fragment -> fragment.string())
                                .collect(Collectors.joining(" ")),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
    }

    private long resolveTotal(SearchResponse response, boolean collapsed) {
        if (collapsed && response.getAggregations() != null) {
            Cardinality cardinality = response.getAggregations().get("unique_file_count");
            if (cardinality != null) {
                return cardinality.getValue();
            }
        }
        return response.getHits().getTotalHits() == null ? 0 : response.getHits().getTotalHits().value;
    }

    private Map<String, Map<String, Long>> resolveAggregations(SearchResponse response) {
        Map<String, Map<String, Long>> aggregations = new HashMap<>();
        if (response.getAggregations() == null) {
            return aggregations;
        }
        putTermsAggregation(aggregations, response, "file_category_agg", "file_category");
        putTermsAggregation(aggregations, response, "tags_agg", "tags");
        putTermsAggregation(aggregations, response, "file_ext_agg", "file_ext");
        putTermsAggregation(aggregations, response, "image_labels_agg", "image_labels");
        return aggregations;
    }

    private void putTermsAggregation(
            Map<String, Map<String, Long>> target,
            SearchResponse response,
            String aggregationName,
            String resultName
    ) {
        Terms terms = response.getAggregations().get(aggregationName);
        if (terms == null) {
            return;
        }
        Map<String, Long> map = terms.getBuckets().stream()
                .collect(Collectors.toMap(
                        Terms.Bucket::getKeyAsString,
                        bucket -> {
                            Cardinality unique = bucket.getAggregations().get("unique_file_count");
                            return unique == null ? bucket.getDocCount() : unique.getValue();
                        },
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        target.put(resultName, map);
    }

    private String resolveSearchAfter(SearchResponse response) {
        SearchHit[] hits = response.getHits().getHits();
        if (hits.length == 0 || hits[hits.length - 1].getSortValues() == null) {
            return null;
        }
        Object[] sortValues = hits[hits.length - 1].getSortValues();
        if (sortValues.length == 0) {
            return null;
        }
        return Arrays.stream(sortValues)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    // ==================== 参数规范化 ====================

    private void normalizePagination(FileSearchRequest request) {
        if (request.getPage() < DEFAULT_PAGE) {
            request.setPage(DEFAULT_PAGE);
        }
        if (request.getSize() <= 0) {
            request.setSize(DEFAULT_SIZE);
        }
        if (request.getSize() > MAX_SIZE) {
            request.setSize(MAX_SIZE);
        }
    }

    private String normalizeFilterField(String rawKey) {
        return switch (rawKey) {
            case "fileExt", "file_ext", "ext" -> "file_ext";
            case "fileCategory", "file_category", "category" -> "file_category";
            case "fileType", "file_type", "mimeType", "mime_type" -> "file_type";
            case "nodeId", "node_id", "parentId", "parent_id" -> "node_id";
            case "tenantId", "tenant_id" -> "tenant_id";
            case "status" -> "status";
            case "tag", "tags" -> "tags.keyword";
            default -> rawKey.matches("[a-zA-Z0-9_.]+") ? rawKey : null;
        };
    }

    private String normalizeSortField(String rawField) {
        if (!hasText(rawField)) {
            return null;
        }
        return switch (rawField) {
            case "filename", "name", "fileName" -> "filename.keyword";
            case "createdAt", "created_at", "uploaded_time" -> "created_at";
            case "updatedAt", "updated_at" -> "updated_at";
            case "size", "size_bytes", "fileSize" -> "size_bytes";
            case "fileExt", "file_ext" -> "file_ext";
            case "fileCategory", "file_category" -> "file_category";
            case "score", "_score" -> "_score";
            default -> null;
        };
    }

    private String normalizeHighlightField(String rawField) {
        if (!hasText(rawField)) {
            return null;
        }
        return switch (rawField) {
            case "fileName", "name" -> "filename";
            case "content", "contentText" -> "content_text";
            case "ocr", "ocrText" -> "ocr_text";
            case "hints", "keywordHints" -> "keyword_hints";
            case "snippet" -> "content_snippet";
            case "summary", "filename", "keyword_hints", "content_snippet", "content_text", "ocr_text" -> rawField;
            default -> null;
        };
    }

    private String normalizeFilterValue(String field, String value) {
        if ("status".equals(field)) {
            return normalizeStatus(value);
        }
        if ("file_ext".equals(field)) {
            return value.toLowerCase(Locale.ROOT).replaceFirst("^\\.", "");
        }
        return value;
    }

    private String normalizeStatus(String status) {
        if ("AVAILABLE".equalsIgnoreCase(status) || "ACTIVE".equalsIgnoreCase(status)) {
            return "active";
        }
        if ("DELETED".equalsIgnoreCase(status)) {
            return "deleted";
        }
        return status.toLowerCase(Locale.ROOT);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}