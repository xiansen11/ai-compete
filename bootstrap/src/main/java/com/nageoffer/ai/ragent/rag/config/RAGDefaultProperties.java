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

package com.nageoffer.ai.ragent.rag.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * RAG 系统默认配置
 *
 * <p>
 * 用于管理 RAG 系统的默认向量数据库配置，包括集合名称、向量维度和度量类型等
 * </p>
 *
 * <pre>
 * 示例配置：
 *
 * rag:
 *   default:
 *     collection-name: default_collection
 *     dimension: 768
 *     metric-type: COSINE
 * </pre>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "rag.default")
public class RAGDefaultProperties {

    /**
     * 默认向量集合名称
     * <p>
     * 用于指定在向量数据库中存储向量数据的默认集合（Collection）名称
     */
    private String collectionName;

    /**
     * 向量维度
     * <p>
     * 指定向量的维数，需要与所使用的 Embedding 模型输出维度保持一致
     * 例如：2048、4096 等
     */
    private Integer dimension;

    /**
     * 向量相似度度量类型
     * <p>
     * 用于计算向量之间相似度的度量方法，常见取值：
     * <ul>
     *   <li>{@code COSINE}：余弦相似度</li>
     *   <li>{@code L2}：欧氏距离</li>
     *   <li>{@code IP}：内积</li>
     * </ul>
     */
    private String metricType;

    // ===== 混合检索配置（向量 + BM25）=====

    /**
     * 是否启用混合检索（向量 + BM25）
     * <p>
     * 默认：false（保持向后兼容，不影响现有功能）
     * 启用后，检索将同时使用向量和BM25两种方式，并通过RRF算法融合结果
     */
    private Boolean hybridSearchEnabled = false;

    /**
     * 默认向量检索权重（0.0-1.0）
     * <p>
     * 仅在使用 WeightedRanker 时生效
     * 表示在融合结果中，向量检索结果的权重占比
     */
    private Float hybridVectorWeight = 0.6f;

    /**
     * 默认BM25检索权重（0.0-1.0）
     * <p>
     * 仅在使用 WeightedRanker 时生效
     * 表示在融合结果中，BM25关键词检索结果的权重占比
     */
    private Float hybridBm25Weight = 0.4f;

    /**
     * RRF算法k参数（默认60）
     * <p>
     * 仅在使用 RRFRanker 时生效
     * 用于平滑排名分数，值越大对低排名结果的惩罚越小
     * 推荐范围：40-100
     */
    private Integer rrfK = 60;

    /**
     * BM25参数k1（词频饱和参数，默认1.2）
     * <p>
     * 控制词频饱和度，值越大词频的影响越线性
     * 推荐范围：1.0-2.0
     */
    private Float bm25K1 = 1.2f;

    /**
     * BM25参数b（文档长度归一化参数，默认0.75）
     * <p>
     * 控制文档长度归一化的程度，值越大长文档的惩罚越重
     * 推荐范围：0.5-1.0
     */
    private Float bm25B = 0.75f;
}
