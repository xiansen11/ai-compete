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

import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.KnowledgeBaseMapper;
import com.nageoffer.ai.ragent.rag.core.vector.VectorStoreAdmin;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceId;
import com.nageoffer.ai.ragent.rag.core.vector.VectorSpaceSpec;
import com.nageoffer.ai.ragent.rag.util.S3BucketNameResolver;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CompetitionKnowledgeBaseInitializerTests {

    @Test
    void runCreatesKnowledgeBaseBucketAndVectorSpaceWhenMissing() throws Exception {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        VectorStoreAdmin vectorStoreAdmin = mock(VectorStoreAdmin.class);
        S3Client s3Client = mock(S3Client.class);
        when(mapper.selectById(any())).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(null);
        when(vectorStoreAdmin.vectorSpaceExists(any(VectorSpaceId.class))).thenReturn(false);
        CompetitionKnowledgeBaseInitializer initializer =
                new CompetitionKnowledgeBaseInitializer(mapper, vectorStoreAdmin, s3Client);

        initializer.run(null);

        verify(mapper, times(4)).insert(any(KnowledgeBaseDO.class));
        verify(s3Client, times(4)).createBucket(anyConsumer());
        verify(vectorStoreAdmin, times(4)).ensureVectorSpace(any(VectorSpaceSpec.class));
    }

    @Test
    void runUpdatesExistingKnowledgeBaseAndSkipsCreateWhenBucketExists() throws Exception {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        VectorStoreAdmin vectorStoreAdmin = mock(VectorStoreAdmin.class);
        S3Client s3Client = mock(S3Client.class);
        KnowledgeBaseDO existing = KnowledgeBaseDO.builder()
                .id("kb-guide")
                .embeddingModel("qwen-emb-siliconflow")
                .collectionName("competition_guide_admin")
                .build();
        when(mapper.selectById(any())).thenReturn(existing);
        when(vectorStoreAdmin.vectorSpaceExists(any(VectorSpaceId.class))).thenReturn(true);
        when(s3Client.createBucket(anyConsumer()))
                .thenThrow(BucketAlreadyOwnedByYouException.builder().message("exists").build());
        CompetitionKnowledgeBaseInitializer initializer =
                new CompetitionKnowledgeBaseInitializer(mapper, vectorStoreAdmin, s3Client);

        initializer.run(null);

        verify(mapper, times(4)).updateById(any(KnowledgeBaseDO.class));
        verify(mapper, never()).insert(any(KnowledgeBaseDO.class));
        verify(s3Client, times(4)).createBucket(anyConsumer());
        verify(vectorStoreAdmin, never()).ensureVectorSpace(any(VectorSpaceSpec.class));

        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(mapper, times(4)).updateById(captor.capture());
        assertTrue(captor.getAllValues().stream().allMatch(each -> "qwen-emb-8b".equals(each.getEmbeddingModel())));
    }

    @Test
    void runUsesCollectionNamesAsBucketNames() throws Exception {
        KnowledgeBaseMapper mapper = mock(KnowledgeBaseMapper.class);
        VectorStoreAdmin vectorStoreAdmin = mock(VectorStoreAdmin.class);
        S3Client s3Client = mock(S3Client.class);
        when(mapper.selectById(any())).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(null);
        when(vectorStoreAdmin.vectorSpaceExists(any(VectorSpaceId.class))).thenReturn(false);
        CompetitionKnowledgeBaseInitializer initializer =
                new CompetitionKnowledgeBaseInitializer(mapper, vectorStoreAdmin, s3Client);

        initializer.run(null);

        ArgumentCaptor<KnowledgeBaseDO> captor = ArgumentCaptor.forClass(KnowledgeBaseDO.class);
        verify(mapper, times(4)).insert(captor.capture());
        assertTrue(captor.getAllValues().stream().anyMatch(each -> "competition_guide_admin".equals(each.getCollectionName())));
        assertTrue(captor.getAllValues().stream().anyMatch(each -> "competition_arena_rules".equals(each.getCollectionName())));
        assertTrue(captor.getAllValues().stream().anyMatch(each -> "competition_tech_pitfalls".equals(each.getCollectionName())));
        assertTrue(captor.getAllValues().stream().anyMatch(each -> "competition_excellent_works".equals(each.getCollectionName())));
        assertEquals(4, captor.getAllValues().size());
        verify(s3Client, times(4)).createBucket(anyConsumer());
    }

    @Test
    void bucketResolverConvertsCollectionNameToS3SafeBucketName() {
        String resolved = S3BucketNameResolver.resolve("competition_guide_admin");
        assertEquals(resolved, S3BucketNameResolver.resolve("competition_guide_admin"));
        assertTrue(resolved.startsWith("competition-guide-admin-"));
        assertTrue(resolved.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$"));
        assertTrue(!resolved.contains("_"));
    }

    @SuppressWarnings("unchecked")
    private Consumer<CreateBucketRequest.Builder> anyConsumer() {
        return any(Consumer.class);
    }
}
