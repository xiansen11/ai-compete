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

package com.nageoffer.ai.ragent.knowledge.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceSpec;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import com.nageoffer.ai.ragent.rag.util.S3BucketNameResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.util.List;

@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class CompetitionKnowledgeBaseInitializer implements ApplicationRunner {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final VectorStoreAdmin vectorStoreAdmin;
    private final S3Client s3Client;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void run(ApplicationArguments args) {
        int ensured = 0;
        for (CompetitionKnowledgeBase each : defaults()) {
            ensureKnowledgeBase(each);
            ensureBucket(each);
            ensureVectorSpace(each);
            ensured++;
        }
        log.info("Competition knowledge bases initialized, ensured {} bases", ensured);
    }

    private void ensureKnowledgeBase(CompetitionKnowledgeBase spec) {
        KnowledgeBaseDO existing = knowledgeBaseMapper.selectById(spec.id());
        if (existing == null) {
            existing = knowledgeBaseMapper.selectOne(
                    Wrappers.lambdaQuery(KnowledgeBaseDO.class)
                            .eq(KnowledgeBaseDO::getCollectionName, spec.collectionName())
                            .eq(KnowledgeBaseDO::getDeleted, 0)
                            .last("limit 1")
            );
        }

        if (existing == null) {
            KnowledgeBaseDO created = KnowledgeBaseDO.builder()
                    .id(spec.id())
                    .name(spec.name())
                    .embeddingModel("qwen-emb-8b")
                    .collectionName(spec.collectionName())
                    .kbType(spec.kbType())
                    .description(spec.description())
                    .routingKeywordsJson(spec.routingKeywordsJson())
                    .metadataSchemaJson(spec.metadataSchemaJson())
                    .defaultPipelineProfile(spec.defaultPipelineProfile())
                    .createdBy("system")
                    .updatedBy("system")
                    .deleted(0)
                    .build();
            knowledgeBaseMapper.insert(created);
            return;
        }

        existing.setName(spec.name());
        existing.setKbType(spec.kbType());
        existing.setDescription(spec.description());
        existing.setRoutingKeywordsJson(spec.routingKeywordsJson());
        existing.setMetadataSchemaJson(spec.metadataSchemaJson());
        existing.setDefaultPipelineProfile(spec.defaultPipelineProfile());
        existing.setUpdatedBy("system");
        knowledgeBaseMapper.updateById(existing);
    }

    private void ensureVectorSpace(CompetitionKnowledgeBase spec) {
        VectorSpaceId spaceId = VectorSpaceId.builder()
                .logicalName(spec.collectionName())
                .build();
        if (vectorStoreAdmin.vectorSpaceExists(spaceId)) {
            return;
        }
        vectorStoreAdmin.ensureVectorSpace(VectorSpaceSpec.builder()
                .spaceId(spaceId)
                .remark(spec.name())
                .build());
    }

    private void ensureBucket(CompetitionKnowledgeBase spec) {
        createBucket(S3BucketNameResolver.resolve(spec.collectionName()));
    }

    private void createBucket(String bucketName) {
        try {
            s3Client.createBucket(builder -> builder.bucket(bucketName));
            log.info("Created default RustFS bucket: {}", bucketName);
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ex) {
            log.info("RustFS bucket already exists: {}", bucketName);
        }
    }

    private List<CompetitionKnowledgeBase> defaults() {
        return List.of(
                new CompetitionKnowledgeBase(
                        "kb-guide",
                        "办事指南库",
                        "competition_guide_admin",
                        "GUIDE",
                        "行政教务类资料，覆盖报名、组队、赛区政策、晋级规则与赛务FAQ。",
                        "[\"通知\",\"报名\",\"组队\",\"FAQ\",\"赛区\",\"资格\",\"晋级\",\"国赛\"]",
                        "{\"common\":[\"kb_type\",\"year\",\"source_type\",\"source_name\",\"file_type\"],\"fields\":[\"province\",\"stage\",\"faq_type\"]}",
                        "guide_hierarchical_clean"
                ),
                new CompetitionKnowledgeBase(
                        "kb-rule",
                        "赛场规则库",
                        "competition_arena_rules",
                        "RULE",
                        "赛项竞技类规则，覆盖技术规程、评分扣分、设备限制、赛道与图纸参数。",
                        "[\"规则\",\"规程\",\"评分\",\"扣分\",\"赛道\",\"图纸\",\"设备限制\",\"技术规则\"]",
                        "{\"common\":[\"kb_type\",\"year\",\"source_type\",\"source_name\",\"file_type\"],\"fields\":[\"category\",\"sub_track\",\"rule_type\"]}",
                        "rule_layout_table"
                ),
                new CompetitionKnowledgeBase(
                        "kb-pitfall",
                        "技术踩坑库",
                        "competition_tech_pitfalls",
                        "PITFALL",
                        "算法工程类资料，覆盖环境配置、ROS开发、视觉算法、硬件驱动、源码与日志排障。",
                        "[\"报错\",\"ROS\",\"ROS2\",\"CMake\",\"驱动\",\"配置\",\"日志\",\"源码\",\"Nav2\",\"log4cxx\"]",
                        "{\"common\":[\"kb_type\",\"year\",\"source_type\",\"source_name\",\"file_type\"],\"fields\":[\"tech_stack\",\"hardware\",\"error_type\"]}",
                        "pitfall_ast_code"
                ),
                new CompetitionKnowledgeBase(
                        "kb-exemplar",
                        "满分作业库",
                        "competition_excellent_works",
                        "EXEMPLAR",
                        "经验参考类资料，覆盖技术报告、答辩PPT、创新点写法与往届获奖作品。",
                        "[\"技术报告\",\"答辩\",\"PPT\",\"一等奖\",\"金奖\",\"创新点\",\"获奖\",\"论文参考\"]",
                        "{\"common\":[\"kb_type\",\"year\",\"source_type\",\"source_name\",\"file_type\"],\"fields\":[\"doc_type\",\"award_level\",\"project_direction\"]}",
                        "exemplar_overlap_summary"
                )
        );
    }

    private record CompetitionKnowledgeBase(String id,
                                            String name,
                                            String collectionName,
                                            String kbType,
                                            String description,
                                            String routingKeywordsJson,
                                            String metadataSchemaJson,
                                            String defaultPipelineProfile) {
    }
}
