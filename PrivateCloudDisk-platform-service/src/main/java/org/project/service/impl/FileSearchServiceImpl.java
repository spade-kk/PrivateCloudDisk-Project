package org.project.service.impl;

import lombok.RequiredArgsConstructor;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.*;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.bucket.terms.TermsAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.opensearch.search.sort.SortOrder;
import org.project.model.dto.FileSearchRequest;
import org.project.model.vo.FileSearchVo;
import org.project.service.FileSearchService;
import org.project.service.ex.ServiceException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FileSearchServiceImpl implements FileSearchService {

    private final RestHighLevelClient client;
    private static final String INDEX_NAME = "pc_files_basic";

    public FileSearchVo search(FileSearchRequest request) {
        SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();

        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();

        // 1️⃣ 权限过滤
        if (request.getTenantId() != null) {
            boolQuery.filter(QueryBuilders.termQuery("tenant_id", request.getTenantId()));
        }
        if (request.getUserId() != null) {
            boolQuery.filter(QueryBuilders.termQuery("user_id", request.getUserId()));
        }
        if (request.getStatus() != null) {
            boolQuery.filter(QueryBuilders.termQuery("status", request.getStatus()));
        }

        if (request.getFilters() != null) {
            request.getFilters().forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    boolQuery.filter(QueryBuilders.termQuery(key, value));
                }
            });
        }

        // 2️⃣ 全文搜索
        if (request.getKeyword() != null && !request.getKeyword().isBlank()) {
            MultiMatchQueryBuilder multiMatch = QueryBuilders.multiMatchQuery(request.getKeyword())
                    .field("filename", 5f)
                    .field("content_text", 2f)
                    .field("ocr_text", 2f)
                    .field("tags", 3f)
                    .field("image_labels", 3f)
                    .field("summary")
                    .type(MultiMatchQueryBuilder.Type.BEST_FIELDS)
                    .fuzziness(Fuzziness.AUTO);

            boolQuery.must(multiMatch);
        }

        sourceBuilder.query(boolQuery);

        // 3️⃣ 分页
        sourceBuilder.from((request.getPage() - 1) * request.getSize());
        sourceBuilder.size(request.getSize());

        // 4️⃣ 排序
        if (request.getSortField() != null) {
            SortOrder order = request.isAsc() ? SortOrder.ASC : SortOrder.DESC;
            sourceBuilder.sort(request.getSortField(), order);
        }

        // 5️⃣ 高亮
        if (request.getHighlightFields() != null && !request.getHighlightFields().isEmpty()) {
            HighlightBuilder highlight = new HighlightBuilder();
            request.getHighlightFields().forEach(field -> highlight.field(field));
            highlight.preTags("<em>").postTags("</em>");
            sourceBuilder.highlighter(highlight);
        }

        // 6️⃣ 聚合统计示例（文件类型和标签分布）
        TermsAggregationBuilder fileCategoryAgg = AggregationBuilders.terms("file_category_agg").field("file_category.keyword");
        TermsAggregationBuilder tagsAgg = AggregationBuilders.terms("tags_agg").field("tags.keyword");
        sourceBuilder.aggregation(fileCategoryAgg);
        sourceBuilder.aggregation(tagsAgg);

        SearchRequest searchRequest = new SearchRequest(INDEX_NAME);
        searchRequest.source(sourceBuilder);

        SearchResponse response;
        try{
            response = client.search(searchRequest, RequestOptions.DEFAULT);
        } catch (IOException e) {
            throw new ServiceException("Search Io Exception");
        }

        // 7️⃣ 解析结果
        FileSearchVo result = new FileSearchVo();
        result.setTotal(response.getHits().getTotalHits().value);

        List<Map<String, Object>> hits = Arrays.stream(response.getHits().getHits())
                .map(hit -> {
                    Map<String, Object> source = new HashMap<>(hit.getSourceAsMap());
                    // 高亮
                    if (hit.getHighlightFields() != null) {
                        source.put("_highlight", hit.getHighlightFields().entrySet().stream()
                                .collect(Collectors.toMap(
                                        Map.Entry::getKey,
                                        e -> Arrays.stream(e.getValue().fragments())
                                                .map(f -> f.string())
                                                .collect(Collectors.joining(" "))
                                )));
                    }
                    return source;
                })
                .collect(Collectors.toList());
        result.setHits(hits);

        // 8️⃣ 聚合结果
        Map<String, Map<String, Long>> aggs = new HashMap<>();
        var fileCatAgg = response.getAggregations().get("file_category_agg");
        if (fileCatAgg instanceof org.opensearch.search.aggregations.bucket.terms.Terms) {
            Map<String, Long> map = ((org.opensearch.search.aggregations.bucket.terms.Terms) fileCatAgg)
                    .getBuckets().stream()
                    .collect(Collectors.toMap(
                            b -> b.getKeyAsString(),
                            b -> b.getDocCount()
                    ));
            aggs.put("file_category", map);
        }

        var tagsAggResp = response.getAggregations().get("tags_agg");
        if (tagsAggResp instanceof org.opensearch.search.aggregations.bucket.terms.Terms) {
            Map<String, Long> map = ((org.opensearch.search.aggregations.bucket.terms.Terms) tagsAggResp)
                    .getBuckets().stream()
                    .collect(Collectors.toMap(
                            b -> b.getKeyAsString(),
                            b -> b.getDocCount()
                    ));
            aggs.put("tags", map);
        }

        result.setAggregations(aggs);

        return result;
    }
}