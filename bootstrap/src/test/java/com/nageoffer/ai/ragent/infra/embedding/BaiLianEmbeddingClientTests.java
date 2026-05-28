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

package com.nageoffer.ai.ragent.infra.embedding;

import com.google.gson.Gson;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaiLianEmbeddingClientTests {

    private final Gson gson = new Gson();

    @Test
    void embedBatchCallsCompatibleEmbeddingEndpoint() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {
                              "data": [
                                { "embedding": [0.1, 0.2], "index": 0 },
                                { "embedding": [0.3, 0.4], "index": 1 }
                              ]
                            }
                            """));
            server.start();

            BaiLianEmbeddingClient client = new BaiLianEmbeddingClient(new okhttp3.OkHttpClient());
            ModelTarget target = buildTarget(server, "test-key");

            List<List<Float>> vectors = client.embedBatch(List.of("alpha", "beta"), target);

            assertEquals(2, vectors.size());
            assertEquals(List.of(0.1F, 0.2F), vectors.get(0));
            assertEquals(List.of(0.3F, 0.4F), vectors.get(1));

            RecordedRequest request = server.takeRequest();
            assertEquals("POST", request.getMethod());
            assertEquals("/compatible-mode/v1/embeddings", request.getPath());
            assertEquals("Bearer test-key", request.getHeader("Authorization"));

            @SuppressWarnings("unchecked")
            var body = gson.fromJson(request.getBody().readUtf8(), java.util.Map.class);
            assertEquals("text-embedding-v4", body.get("model"));
            assertEquals("float", body.get("encoding_format"));
            assertEquals(1536.0, body.get("dimensions"));
            assertEquals(List.of("alpha", "beta"), body.get("input"));
        }
    }

    @Test
    void embedBatchThrowsWhenApiReturnsError() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse()
                    .setResponseCode(401)
                    .setHeader("Content-Type", "application/json")
                    .setBody("""
                            {"message":"Invalid API key"}
                            """));
            server.start();

            BaiLianEmbeddingClient client = new BaiLianEmbeddingClient(new okhttp3.OkHttpClient());
            ModelTarget target = buildTarget(server, "bad-key");

            ModelClientException ex = assertThrows(
                    ModelClientException.class,
                    () -> client.embedBatch(List.of("alpha"), target)
            );

            assertTrue(ex.getMessage().contains("HTTP 401"));
        }
    }

    private ModelTarget buildTarget(MockWebServer server, String apiKey) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(server.url("/").toString());
        provider.setApiKey(apiKey);
        provider.setEndpoints(java.util.Map.of("embedding", "/compatible-mode/v1/embeddings"));

        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen-emb-8b");
        candidate.setProvider("bailian");
        candidate.setModel("text-embedding-v4");
        candidate.setDimension(1536);

        return new ModelTarget(candidate.getId(), candidate, provider);
    }
}
