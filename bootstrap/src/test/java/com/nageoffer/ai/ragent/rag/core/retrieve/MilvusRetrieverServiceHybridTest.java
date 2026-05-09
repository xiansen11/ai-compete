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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 混合检索功能单元测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>RetrieveRequest 参数扩展验证</li>
 *   <li>HybridSearchWeights 配置类测试</li>
 *   <li>混合检索开关逻辑测试</li>
 * </ul>
 */
@DisplayName("混合检索功能单元测试")
class MilvusRetrieverServiceHybridTest {

    @Test
    @DisplayName("测试 RetrieveRequest 默认值 - 混合检索默认关闭")
    void testRetrieveRequestDefaults() {
        RetrieveRequest request = RetrieveRequest.builder()
                .query("测试查询")
                .topK(5)
                .build();

        assertNull(request.getUseHybridSearch(), "useHybridSearch 默认应为 null");
        assertNull(request.getWeights(), "weights 默认应为 null");
        assertEquals(5, request.getTopK());
        assertEquals("测试查询", request.getQuery());
    }

    @Test
    @DisplayName("测试 RetrieveRequest 启用混合检索")
    void testRetrieveRequestWithHybridEnabled() {
        RetrieveRequest.HybridSearchWeights weights = RetrieveRequest.HybridSearchWeights.builder()
                .vectorWeight(0.7f)
                .bm25Weight(0.3f)
                .rrfK(60)
                .build();

        RetrieveRequest request = RetrieveRequest.builder()
                .query("RTX-4090显卡性能")
                .topK(10)
                .useHybridSearch(true)
                .weights(weights)
                .build();

        assertTrue(request.getUseHybridSearch(), "应启用混合检索");
        assertNotNull(request.getWeights(), "权重配置不应为空");
        assertEquals(0.7f, request.getWeights().getVectorWeight());
        assertEquals(0.3f, request.getWeights().getBm25Weight());
        assertEquals(60, request.getWeights().getRrfK());
    }

    @Test
    @DisplayName("测试 HybridSearchWeights 默认值")
    void testHybridSearchWeightsDefaults() {
        RetrieveRequest.HybridSearchWeights weights = RetrieveRequest.HybridSearchWeights.builder().build();

        assertNull(weights.getVectorWeight(), "vectorWeight 默认应为 null");
        assertNull(weights.getBm25Weight(), "bm25Weight 默认应为 null");
        assertEquals(60, weights.getRrfK(), "rrfK 默认应为 60");
    }

    @Test
    @DisplayName("测试 HybridSearchWeights 自定义 RRF K 值")
    void testHybridSearchWeightsCustomRrfK() {
        RetrieveRequest.HybridSearchWeights weights = RetrieveRequest.HybridSearchWeights.builder()
                .rrfK(100)
                .build();

        assertEquals(100, weights.getRrfK());
    }

    @Test
    @DisplayName("测试 RetrieveRequest 使用 Builder 模式构建完整请求")
    void testRetrieveRequestFullBuilder() {
        RetrieveRequest request = RetrieveRequest.builder()
                .query("iPhone 15 Pro Max 参数对比")
                .topK(8)
                .collectionName("product_kb")
                .useHybridSearch(true)
                .weights(RetrieveRequest.HybridSearchWeights.builder()
                        .vectorWeight(0.6f)
                        .bm25Weight(0.4f)
                        .rrfK(60)
                        .build())
                .build();

        assertEquals("iPhone 15 Pro Max 参数对比", request.getQuery());
        assertEquals(8, request.getTopK());
        assertEquals("product_kb", request.getCollectionName());
        assertTrue(request.getUseHybridSearch());
        assertNotNull(request.getWeights());
        assertEquals(0.6f, request.getWeights().getVectorWeight());
        assertEquals(0.4f, request.getWeights().getBm25Weight());
    }

    @Test
    @DisplayName("测试专有名词和型号查询场景")
    void testSpecialTerminologyQueries() {
        String[] queries = {
                "RTX-4090 显卡性能评测",
                "iPhone 15 Pro Max 价格",
                "HTTP/2 协议详解",
                "Kubernetes Pod 调度策略",
                "订单号 ORD-2024-001 状态查询"
        };

        for (String query : queries) {
            RetrieveRequest request = RetrieveRequest.builder()
                    .query(query)
                    .useHybridSearch(true)
                    .topK(5)
                    .build();

            assertNotNull(request);
            assertTrue(request.getUseHybridSearch(),
                    "专有名词查询应使用混合检索: " + query);
        }
    }
}
