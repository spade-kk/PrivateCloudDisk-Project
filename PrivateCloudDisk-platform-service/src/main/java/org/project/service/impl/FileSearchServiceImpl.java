package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MultiMatchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;
    private static final Set<String> EXCLUDED_CONTENT_SOURCE_FIELDS = Set.of(
            "content_text",
            "content_chunks",
            "ocr_text"
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
                ? executeUnifiedKeywordSearch(request)
                : executeBasicMetadataSearch(request);

        FileSearchVo result = new FileSearchVo();
        result.setTotal(resolveTotal(response, hasKeyword));
        result.setHits(toHits(response));
        result.setAggregations(resolveAggregations(response));
        result.setSearchAfter(resolveSearchAfter(response));
        return result;
    }

    private SearchResponse executeUnifiedKeywordSearch(FileSearchRequest request) {
        SearchSourceBuilder sourceBuilder = baseSource(request);
        sourceBuilder.query(buildSearchQuery(request, true));
        sourceBuilder.collapse(new CollapseBuilder("file_id"));
        sourceBuilder.aggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        addMetadataAggregations(sourceBuilder);
        return search(new SearchRequest(basicIndexName, contentIndexName).source(sourceBuilder));
    }

    private SearchResponse executeBasicMetadataSearch(FileSearchRequest request) {
        SearchSourceBuilder sourceBuilder = baseSource(request);
        sourceBuilder.query(buildSearchQuery(request, false));
        addMetadataAggregations(sourceBuilder);
        return search(new SearchRequest(basicIndexName).source(sourceBuilder));
    }

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

    private BoolQueryBuilder buildSearchQuery(FileSearchRequest request, boolean includeContentFields) {
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        applyPermissionAndMetadataFilters(boolQuery, request);

        if (hasText(request.getKeyword())) {
            if (includeContentFields) {
                BoolQueryBuilder keywordQuery = QueryBuilders.boolQuery()
                        .should(metadataKeywordQuery(request.getKeyword()))
                        .should(contentKeywordQuery(request.getKeyword()))
                        .minimumShouldMatch(1);
                boolQuery.must(keywordQuery);
            } else {
                boolQuery.must(metadataKeywordQuery(request.getKeyword()));
            }
        } else {
            boolQuery.must(QueryBuilders.matchAllQuery());
        }

        return boolQuery;
    }

    private QueryBuilder metadataKeywordQuery(String keyword) {
        return QueryBuilders.multiMatchQuery(keyword)
                .field("filename", 5f)
                .field("filename.keyword", 8f)
                .field("summary", 2f)
                .field("tags", 3f)
                .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                .fuzziness(Fuzziness.AUTO);
    }

    private QueryBuilder contentKeywordQuery(String keyword) {
        BoolQueryBuilder contentQuery = QueryBuilders.boolQuery()
                .should(QueryBuilders.multiMatchQuery(keyword)
                        .field("content_text", 4f)
                        .field("ocr_text", 3f)
                        .field("image_labels", 3f)
                        .field("summary", 1.5f)
                        .field("filename", 2f)
                        .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                        .fuzziness(Fuzziness.AUTO))
                .should(QueryBuilders.nestedQuery(
                        "content_chunks",
                        QueryBuilders.matchQuery("content_chunks.text", keyword),
                        ScoreMode.Max
                ))
                .minimumShouldMatch(1);
        return contentQuery;
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
            fields = List.of("filename", "summary", "content_text", "ocr_text");
        }
        fields.stream()
                .map(this::normalizeHighlightField)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(field -> highlight.field(new HighlightBuilder.Field(field).fragmentSize(160).numOfFragments(3)));

        sourceBuilder.highlighter(highlight);
    }

    private void addMetadataAggregations(SearchSourceBuilder sourceBuilder) {
        TermsAggregationBuilder fileCategoryAgg = AggregationBuilders
                .terms("file_category_agg")
                .field("file_category")
                .size(20)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        TermsAggregationBuilder tagsAgg = AggregationBuilders
                .terms("tags_agg")
                .field("tags")
                .size(30)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        TermsAggregationBuilder extAgg = AggregationBuilders
                .terms("file_ext_agg")
                .field("file_ext")
                .size(30)
                .subAggregation(AggregationBuilders.cardinality("unique_file_count").field("file_id"));
        sourceBuilder.aggregation(fileCategoryAgg);
        sourceBuilder.aggregation(tagsAgg);
        sourceBuilder.aggregation(extAgg);
    }

    private SearchResponse search(SearchRequest searchRequest) {
        try {
            return client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ServiceException("OpenSearch 文件搜索失败: " + e.getMessage());
        }
    }

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
            case "fileType", "file_type", "mimeType" -> "file_type";
            case "nodeId", "node_id", "parentId" -> "node_id";
            case "status" -> "status";
            case "tag", "tags" -> "tags";
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
            case "summary", "filename", "content_text", "ocr_text" -> rawField;
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
