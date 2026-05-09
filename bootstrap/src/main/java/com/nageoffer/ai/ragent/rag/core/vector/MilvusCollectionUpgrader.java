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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.DescribeCollectionReq;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "milvus")
public class MilvusCollectionUpgrader {

    private final MilvusClientV2 milvusClient;
    private final RAGDefaultProperties properties;

    private static final String TEXT_FIELD_NAME = "text";

    public void upgradeCollectionForHybridSearch(String collectionName) {
        if (collectionName == null || collectionName.isBlank()) {
            log.warn("Collection名称为空，跳过升级");
            return;
        }

        try {
            log.info("开始检查并升级 Collection: {}", collectionName);

            HasCollectionReq hasReq = HasCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            boolean exists = milvusClient.hasCollection(hasReq);
            if (!exists) {
                log.warn("Collection {} 不存在，跳过升级", collectionName);
                return;
            }

            boolean needsUpgrade = checkNeedsUpgrade(collectionName);
            if (!needsUpgrade) {
                log.info("Collection {} 已支持混合检索，无需升级", collectionName);
                return;
            }

            log.info("Collection {} 需要升级以支持混合检索，但当前SDK版本不支持动态添加字段，请重建Collection", collectionName);
            log.info("建议：删除旧Collection后重新创建包含text字段的Collection，以支持BM25全文检索");

        } catch (Exception e) {
            log.error("升级 Collection {} 失败: {}", collectionName, e.getMessage(), e);
            throw new RuntimeException("Collection 升级失败: " + e.getMessage(), e);
        }
    }

    private boolean checkNeedsUpgrade(String collectionName) {
        try {
            DescribeCollectionReq descReq = DescribeCollectionReq.builder()
                    .collectionName(collectionName)
                    .build();
            var desc = milvusClient.describeCollection(descReq);
            List<String> fieldNames = desc.getFieldNames();

            boolean hasTextField = fieldNames.stream()
                    .anyMatch(TEXT_FIELD_NAME::equals);

            if (!hasTextField) {
                log.info("检测到 Collection {} 缺少 text 字段，需要升级", collectionName);
                return true;
            }

            log.info("Collection {} 已包含 text 字段", collectionName);
            return false;

        } catch (Exception e) {
            log.warn("检查 Collection {} Schema 失败: {}，默认需要升级", collectionName, e.getMessage());
            return true;
        }
    }
}
