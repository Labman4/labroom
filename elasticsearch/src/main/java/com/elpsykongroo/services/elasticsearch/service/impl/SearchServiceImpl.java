/*
 * Copyright 2022-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.elpsykongroo.services.elasticsearch.service.impl;

import com.elpsykongroo.base.domain.search.QueryParam;
import com.elpsykongroo.base.utils.JsonUtils;
import com.elpsykongroo.services.elasticsearch.dto.SearchResult;
import com.elpsykongroo.services.elasticsearch.service.SearchService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.opensearch.client.opensearch._types.FieldValue;
import org.opensearch.client.opensearch._types.query_dsl.BoolQuery;
import org.opensearch.client.opensearch._types.query_dsl.ExistsQuery;
import org.opensearch.client.opensearch._types.query_dsl.IdsQuery;
import org.opensearch.client.opensearch._types.query_dsl.MatchAllQuery;
import org.opensearch.client.opensearch._types.query_dsl.MultiMatchQuery;
import org.opensearch.client.opensearch._types.query_dsl.QueryStringQuery;
import org.opensearch.client.opensearch._types.query_dsl.TermQuery;
import org.opensearch.data.client.osc.NativeQuery;
import org.opensearch.data.client.osc.NativeQueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.NoSuchIndexException;
import org.springframework.data.elasticsearch.ResourceNotFoundException;
import org.springframework.data.elasticsearch.core.AbstractElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHitSupport;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchScrollHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class SearchServiceImpl implements SearchService {
    @Autowired
    private ElasticsearchOperations operations;

    private static NativeQueryBuilder getQuery(QueryParam queryParam) {
        NativeQueryBuilder nativeQueryBuilder;
        if (StringUtils.isNotEmpty(queryParam.getParam()) ||
                (queryParam.getIds() !=null && !queryParam.getIds().isEmpty()) ||
                (StringUtils.isNotEmpty(queryParam.getField()) && StringUtils.isNotEmpty(queryParam.getOperation())) ||
                (queryParam.getQueryStringParam() != null && !queryParam.getQueryStringParam().isEmpty())) {
            if ("update".equals(queryParam.getOperation())) {
                return null;
            }
            if (queryParam.isFuzzy()) {
              MultiMatchQuery multiMatchQuery = new MultiMatchQuery.Builder()
                      .query(queryParam.getParam())
                      .fields(queryParam.getFields())
                      .fuzziness("auto")
                      .build();
                nativeQueryBuilder = NativeQuery.builder().withQuery(q -> q.multiMatch(multiMatchQuery));
            } else if (queryParam.isBoolQuery()) {
                List<org.opensearch.client.opensearch._types.query_dsl.Query> queries = new ArrayList<>();
                if ("exist".equals(queryParam.getOperation())) {
                    ExistsQuery existsQuery = new ExistsQuery.Builder().field(queryParam.getField()).build();
                    queries.add(existsQuery.toQuery());
                } else {
                    List<String> fields = queryParam.getFields();
                    List<String> params = queryParam.getQueryStringParam();
                    List<QueryStringQuery> queryStringQueries = new ArrayList<>();
                    for (int i = 0; i < queryParam.getQueryStringParam().size(); i++) {
                        queryStringQueries.add(new QueryStringQuery.Builder()
                                .query(params.get(i)).fields(fields.get(i))
                                .build());
                    }
                    queryStringQueries.forEach(queryStringQuery -> queries.add(queryStringQuery.toQuery()));
                }
                BoolQuery boolQuery = null;
                if ("filter".equals(queryParam.getBoolType())) {
                    boolQuery = new BoolQuery.Builder().filter(queries).build();
                } else if ("should".equals(queryParam.getBoolType())) {
                    boolQuery = new BoolQuery.Builder().should(queries).build();
                } else if ("must_not".equals(queryParam.getBoolType())) {
                    boolQuery = new BoolQuery.Builder().mustNot(queries).build();
                } else {
                    boolQuery = new BoolQuery.Builder().must(queries).build();
                }
                nativeQueryBuilder = NativeQuery.builder().withQuery(boolQuery.toQuery());
            } else if (queryParam.isIdsQuery()) {
                IdsQuery idsQuery = new IdsQuery.Builder().values(queryParam.getIds()).build();
                nativeQueryBuilder =  NativeQuery.builder().withQuery(q -> q.ids(idsQuery));
            } else {
                TermQuery termQuery = new TermQuery.Builder()
                      .value(FieldValue.of(queryParam.getParam()))
                      .field(queryParam.getField()).build();
                nativeQueryBuilder =  NativeQuery.builder().withQuery(q -> q.term(termQuery));
            }
        } else {
            MatchAllQuery matchAllQuery = new MatchAllQuery.Builder().build();
            nativeQueryBuilder = NativeQuery.builder().withQuery(q -> q.matchAll(matchAllQuery));
        }
        return nativeQueryBuilder;
    }

    @Override
    public String query(QueryParam queryParam) {
//        Pageable pageable = null;
//        if (StringUtils.isNotBlank(queryParam.getOrder())) {
//            Sort sort = Sort.by(Sort.Direction.DESC, queryParam.getOrderBy());
//            if ("1".equals(queryParam.getOrder())) {
//                sort = Sort.by(Sort.Direction.ASC, queryParam.getOrderBy());
//            }
//            pageable = PageRequest.of(
//                    Integer.parseInt(queryParam.getPageNumber()),
//                    Integer.parseInt(queryParam.getPageSize()), sort);
//        }
        try {
            if ("count".equals(queryParam.getOperation())) {
                long count = 0;
                try {
                    count = operations.count(getQuery(queryParam).build(), queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                } catch (Exception e) {
                    if (log.isErrorEnabled()) {
                        log.error("count error:{}", e.getMessage());
                    }
                }
                return String.valueOf(count);
            } else if ("delete".equals(queryParam.getOperation())) {
                queryParam.getIds().forEach(id -> operations.delete(id, IndexCoordinates.of(queryParam.getIndex())));
                return "";
            } else if ("deleteQuery".equals(queryParam.getOperation())) {
                ByQueryResponse response = operations.delete(getQuery(queryParam).build(), queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                return String.valueOf(response.getDeleted());
            }
            else if ("save".equals(queryParam.getOperation())) {
                return operations.save(queryParam.getEntity(), IndexCoordinates.of(queryParam.getIndex())).toString();
            } else if ("update".equals(queryParam.getOperation())) {
                UpdateQuery updateQuery = UpdateQuery.builder(queryParam.getIds().get(0))
                        .withParams(queryParam.getUpdateParam())
                        .withScript(queryParam.getScript())
                        .withScriptType(ScriptType.INLINE)
                        .build();
                return operations.update(updateQuery, IndexCoordinates.of(queryParam.getIndex())).getResult().toString();
            } else if ("updateQuery".equals(queryParam.getOperation())) {
                UpdateQuery updateQuery = UpdateQuery.builder(getQuery(queryParam).build())
                        .withIndex(queryParam.getIndex())
                        .withParams(queryParam.getUpdateParam())
                        .withScriptType(ScriptType.INLINE)
                        .withScript(queryParam.getScript())
                        .build();
                ByQueryResponse byQueryResponse = operations.updateByQuery(updateQuery, IndexCoordinates.of(queryParam.getIndex()));
                return String.valueOf(byQueryResponse.getTotal());
            } else {
                SearchResult searchResult = new SearchResult();
                String scrollid = queryParam.getScrollId();
                if (!"0".equals(scrollid) && StringUtils.isNotBlank(scrollid)) {
                    AbstractElasticsearchTemplate template = (AbstractElasticsearchTemplate)operations;
                    SearchScrollHits scroll = null;
                    try {
                        if ("1".equals(queryParam.getScrollId())) {
                            scroll = template.searchScrollStart(10000, getQuery(queryParam).build(), queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                        } else {
                            scroll = template.searchScrollContinue(queryParam.getScrollId(), 10000, queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                        }
                    } catch (ResourceNotFoundException e) {
                        if (log.isErrorEnabled()) {
                            log.error("searchScroll error:{}", e.getMessage());
                        }
                    } finally {
                        if (queryParam.getScrollId() != null
                                && !"0".equals(queryParam.getScrollId())
                                && !"1".equals(queryParam.getScrollId())) {
                            template.searchScrollClear(queryParam.getScrollId());
                        }
                    }
                    if (scroll != null) {
                        String scrollId = scroll.getScrollId();
                        if (!scroll.hasSearchHits()) {
                            template.searchScrollClear(scrollId);
                        }
                        searchResult.setScrollId(scroll.getScrollId());
                        searchResult.setHits(SearchHitSupport.unwrapSearchHits(scroll.getSearchHits()));
                    }
                } else {
                    NativeQueryBuilder nativeQueryBuilder = getQuery(queryParam);
                    SearchHits<?> searchHits;
                    NativeQueryBuilder searchAfterBuilder = nativeQueryBuilder
                            .withPageable(PageRequest.of(0, 100))
                            .withSort(Sort.by(
                                    Sort.Order.asc("timestamp"),
                                    Sort.Order.asc("_id")
                            ));
                    if (StringUtils.isNotBlank(queryParam.getOrder())) {
                        if ("desc".equals(queryParam.getOrder())) {
                            searchAfterBuilder = nativeQueryBuilder.
                                    withPageable(PageRequest.of(0,100))
                                    .withSort(Sort.by(
                                            Sort.Order.desc("timestamp"), // 换成你自己的字段
                                            Sort.Order.desc("_id")
                                    ));
                        }
                    }
                    if(StringUtils.isNotBlank(queryParam.getTimestamp())
                            && StringUtils.isNotBlank(queryParam.getId())) {
                        List<Object> searchAfter = new ArrayList<>();
                        searchAfter.add(Long.parseLong(queryParam.getTimestamp()));
                        searchAfter.add(queryParam.getId());
                        searchAfterBuilder.withSearchAfter(searchAfter);
                        searchHits = operations.search(searchAfterBuilder.build(),
                                queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                    } else {
                        searchHits = operations.search(searchAfterBuilder.build(),
                                queryParam.getType(), IndexCoordinates.of(queryParam.getIndex()));
                    }
                    if (!searchHits.isEmpty()) {
                        SearchHit<?> lastHit =
                                searchHits.getSearchHits()
                                        .get(searchHits.getSearchHits().size() - 1);
                        searchResult.setSearchAfter(lastHit.getSortValues());
                    }
                    searchResult.setHits(SearchHitSupport.unwrapSearchHits(searchHits.getSearchHits()));
                }
                return JsonUtils.toJson(searchResult);
            }
        } catch (NoSuchIndexException e) {
            return "";
        }
    }
}
