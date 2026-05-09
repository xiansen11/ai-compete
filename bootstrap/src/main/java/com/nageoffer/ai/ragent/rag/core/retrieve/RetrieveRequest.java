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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 向量检索请求参数：
 * - 支持基础 query + topK
 * - 支持指定 Milvus collectionName
 * - 支持简单的 metadata 等值过滤（扩展用）
 * - 支持混合检索配置（向量 + BM25）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RetrieveRequest {

    /**
     * 用户自然语言问题 / 查询语句
     */
    private String query;

    /**
     * 返回 TopK，默认 5
     */
    @Builder.Default
    private int topK = 5;

    /**
     * 目标向量集合名称：
     * - 为空时走默认 Collection
     * - 非空时按指定 Collection 检索
     */
    private String collectionName;

    /**
     * 元数据等值过滤条件（扩展项）：
     * - key 为 metadata 字段名
     * - value 为匹配值
     * 实现层可以根据 Map 自动拼接 Milvus Expr（AND 连接）。
     * <p>
     * 例如：
     * {"biz_type": "ATTENDANCE", "env": "TEST"}
     */
    private Map<String, Object> metadataFilters;

    /**
     * 是否使用混合检索（向量 + BM25）
     * <ul>
     *   <li>null: 使用全局配置（rag.hybrid-search.enabled）</li>
     *   <li>true: 强制使用混合检索</li>
     *   <li>false: 强制使用纯向量检索</li>
     * </ul>
     */
    @Builder.Default
    private Boolean useHybridSearch = null;

    /**
     * 混合检索权重配置
     * 仅在 useHybridSearch=true 时生效
     */
    @Builder.Default
    private HybridSearchWeights weights = null;

    /**
     * 混合检索权重配置类
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HybridSearchWeights {

        /**
         * 向量检索权重（0.0-1.0）
         * 仅在使用 WeightedRanker 时生效
         */
        private Float vectorWeight;

        /**
         * BM25检索权重（0.0-1.0）
         * 仅在使用 WeightedRanker 时生效
         */
        private Float bm25Weight;

        /**
         * RRF算法k参数（默认60）
         * 仅在使用 RRFRanker 时生效
         */
        @Builder.Default
        private Integer rrfK = 60;
    }
}

