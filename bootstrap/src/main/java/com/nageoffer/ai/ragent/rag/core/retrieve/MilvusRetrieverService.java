/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.retrieve;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.infra.embedding.EmbeddingService;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.data.BaseVector;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.SearchResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus")
public class MilvusRetrieverService implements RetrieverService {

    private final EmbeddingService embeddingService;
    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties ragDefaultProperties;

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest retrieveParam) {
        boolean useHybrid = shouldUseHybridSearch(retrieveParam);

        if (useHybrid) {
            log.debug("请求混合检索，但当前SDK版本暂不支持Milvus混合检索API，回退到纯向量检索，query={}", retrieveParam.getQuery());
        } else {
            log.debug("使用纯向量检索，query={}", retrieveParam.getQuery());
        }

        return vectorOnlyRetrieve(retrieveParam);
    }

    @Override
    public List<RetrievedChunk> retrieveByVector(float[] vector, RetrieveRequest retrieveParam) {
        return vectorOnlyRetrieve(vector, retrieveParam);
    }

    private List<RetrievedChunk> vectorOnlyRetrieve(RetrieveRequest retrieveParam) {
        List<Float> emb = embeddingService.embed(retrieveParam.getQuery());
        float[] vec = toArray(emb);
        float[] norm = normalize(vec);

        return vectorOnlyRetrieve(norm, retrieveParam);
    }

    private List<RetrievedChunk> vectorOnlyRetrieve(float[] vector, RetrieveRequest retrieveParam) {
        List<BaseVector> vectors = List.of(new FloatVec(vector));

        Map<String, Object> params = new HashMap<>();
        params.put("metric_type", ragDefaultProperties.getMetricType());
        params.put("ef", 128);

        SearchReq req = SearchReq.builder()
                .collectionName(
                        StrUtil.isBlank(retrieveParam.getCollectionName()) ? ragDefaultProperties.getCollectionName() : retrieveParam.getCollectionName()
                )
                .annsField("embedding")
                .data(vectors)
                .topK(retrieveParam.getTopK())
                .searchParams(params)
                .outputFields(List.of("id", "content", "metadata"))
                .build();

        SearchResp resp = milvusClient.search(req);
        List<List<SearchResp.SearchResult>> results = resp.getSearchResults();

        if (results == null || results.isEmpty()) {
            return List.of();
        }

        return results.get(0).stream()
                .map(r -> new RetrievedChunk(
                        Objects.toString(r.getEntity().get("id"), ""),
                        Objects.toString(r.getEntity().get("content"), ""),
                        r.getScore()))
                .collect(Collectors.toList());
    }

    private boolean shouldUseHybridSearch(RetrieveRequest request) {
        if (request.getUseHybridSearch() != null) {
            return request.getUseHybridSearch();
        }
        return Boolean.TRUE.equals(ragDefaultProperties.getHybridSearchEnabled());
    }

    private static float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.get(i);
        return arr;
    }

    private static float[] normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += x * x;
        double len = Math.sqrt(sum);
        float[] nv = new float[v.length];
        for (int i = 0; i < v.length; i++) nv[i] = (float) (v[i] / len);
        return nv;
    }
}
