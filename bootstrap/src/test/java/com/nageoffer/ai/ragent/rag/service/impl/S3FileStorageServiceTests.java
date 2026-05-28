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

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class S3FileStorageServiceTests {

    private HttpServer server;
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile byte[] requestBody = new byte[0];
    private volatile String requestMethod = "";

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void multipartUploadUsesPresignedStreamingPath() throws Exception {
        startServer(200);
        S3Client s3Client = mock(S3Client.class);
        try (S3Presigner presigner = newPresigner()) {
            S3FileStorageService service = new S3FileStorageService(s3Client, presigner);
            byte[] content = "hello upload".getBytes(StandardCharsets.UTF_8);
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "sample.txt",
                    "text/plain",
                    content
            );

            var stored = service.upload("kb-guide", file);

            assertEquals(1, requestCount.get());
            assertEquals("PUT", requestMethod);
            assertArrayEquals(content, requestBody);
            assertTrue(stored.getUrl().startsWith("s3://kb-guide/"));
            assertEquals("sample.txt", stored.getOriginalFilename());
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    @Test
    void inputStreamAndBytesUploadUsePresignedStreamingPath() throws Exception {
        startServer(200);
        S3Client s3Client = mock(S3Client.class);
        try (S3Presigner presigner = newPresigner()) {
            S3FileStorageService service = new S3FileStorageService(s3Client, presigner);
            byte[] inputStreamContent = "stream body".getBytes(StandardCharsets.UTF_8);
            byte[] bytesContent = "bytes body".getBytes(StandardCharsets.UTF_8);

            var storedFromStream = service.upload(
                    "kb-rule",
                    new ByteArrayInputStream(inputStreamContent),
                    inputStreamContent.length,
                    "stream.md",
                    "text/markdown"
            );

            assertEquals(1, requestCount.get());
            assertEquals("PUT", requestMethod);
            assertArrayEquals(inputStreamContent, requestBody);
            assertTrue(storedFromStream.getUrl().startsWith("s3://kb-rule/"));

            var storedFromBytes = service.upload("kb-rule", bytesContent, "bytes.txt", "text/plain");

            assertEquals(2, requestCount.get());
            assertArrayEquals(bytesContent, requestBody);
            assertTrue(storedFromBytes.getUrl().startsWith("s3://kb-rule/"));
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    @Test
    void uploadFailureDoesNotFallbackToSdkPutObject() throws Exception {
        startServer(500);
        S3Client s3Client = mock(S3Client.class);
        try (S3Presigner presigner = newPresigner()) {
            S3FileStorageService service = new S3FileStorageService(s3Client, presigner);
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "broken.txt",
                    "text/plain",
                    "boom".getBytes(StandardCharsets.UTF_8)
            );

            IOException ex = assertThrows(IOException.class, () -> service.upload("kb-pitfall", file));

            assertTrue(ex.getMessage().contains("S3 流式上传失败"));
            assertEquals(1, requestCount.get());
            verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        }
    }

    private void startServer(int statusCode) throws IOException {
        requestCount.set(0);
        requestBody = new byte[0];
        requestMethod = "";
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/", this::handleExchange);
        server.setExecutor(null);
        this.responseStatusCode = statusCode;
        server.start();
    }

    private int responseStatusCode;

    private void handleExchange(HttpExchange exchange) throws IOException {
        requestCount.incrementAndGet();
        requestMethod = exchange.getRequestMethod();
        requestBody = exchange.getRequestBody().readAllBytes();
        byte[] body = responseStatusCode >= 200 && responseStatusCode < 300
                ? new byte[0]
                : "upload failed".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(responseStatusCode, body.length);
        try (OutputStream outputStream = exchange.getResponseBody()) {
            outputStream.write(body);
        }
    }

    private S3Presigner newPresigner() {
        return S3Presigner.builder()
                .endpointOverride(URI.create("http://127.0.0.1:" + server.getAddress().getPort()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("test-access-key", "test-secret-key")
                ))
                .build();
    }
}
