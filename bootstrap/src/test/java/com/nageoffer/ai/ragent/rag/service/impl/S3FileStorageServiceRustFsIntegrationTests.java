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

package com.nageoffer.ai.ragent.rag.service.impl;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import com.nageoffer.ai.ragent.rag.util.S3BucketNameResolver;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("integration")
class S3FileStorageServiceRustFsIntegrationTests {

    @Test
    void uploadAndReadBackFromLocalRustFs() throws Exception {
        String bucketName = S3BucketNameResolver.resolve("competition_guide_admin");
        try (S3Client s3Client = newS3Client();
             S3Presigner presigner = newPresigner()) {
            ensureBucketExists(s3Client, bucketName);
            S3FileStorageService service = new S3FileStorageService(s3Client, presigner);
            byte[] content = "rustfs integration upload".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "integration.txt",
                    "text/plain",
                    content
            );

            var stored = service.upload(bucketName, file);

            assertNotNull(stored);
            assertTrue(stored.getUrl().startsWith("s3://" + bucketName + "/"));
            try (InputStream inputStream = service.openStream(stored.getUrl())) {
                assertArrayEquals(content, inputStream.readAllBytes());
            }
            service.deleteByUrl(stored.getUrl());
        }
    }

    private void ensureBucketExists(S3Client s3Client, String bucketName) {
        try {
            s3Client.createBucket(builder -> builder.bucket(bucketName));
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ex) {
            // bucket already exists
        }
    }

    private S3Client newS3Client() {
        return S3Client.builder()
                .endpointOverride(URI.create("http://127.0.0.1:9000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("rustfsadmin", "rustfsadmin")
                ))
                .forcePathStyle(true)
                .build();
    }

    private S3Presigner newPresigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("http://127.0.0.1:9000"))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("rustfsadmin", "rustfsadmin")
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }
}
