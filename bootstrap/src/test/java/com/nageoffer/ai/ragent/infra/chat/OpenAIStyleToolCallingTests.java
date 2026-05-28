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

package com.nageoffer.ai.ragent.infra.chat;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.ChatTool;
import com.nageoffer.ai.ragent.framework.convention.ToolCallChatResult;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAIStyleToolCallingTests {

    @Test
    void requestBodyContainsToolsAndToolChoice() {
        TestClient client = new TestClient();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(ChatMessage.user("search ai contest")))
                .enableTools(true)
                .toolChoice("auto")
                .tools(List.of(ChatTool.builder()
                        .function(ChatTool.FunctionDef.builder()
                                .name("openweb_search")
                                .description("Search the web")
                                .parameters(Map.of("type", "object"))
                                .build())
                        .build()))
                .build();

        JsonObject body = client.exposeRequestBody(request, target());

        assertTrue(body.has("tools"));
        assertEquals("auto", body.get("tool_choice").getAsString());
        assertEquals("openweb_search", body.getAsJsonArray("tools")
                .get(0).getAsJsonObject()
                .getAsJsonObject("function")
                .get("name").getAsString());
    }

    @Test
    void parsesToolCallsFromOpenAICompatibleResponse() throws Exception {
        TestClient client = new TestClient();
        JsonObject response = new Gson().fromJson("""
                {
                  "choices": [
                    {
                      "message": {
                        "content": null,
                        "tool_calls": [
                          {
                            "id": "call_1",
                            "type": "function",
                            "function": {
                              "name": "openweb_search",
                              "arguments": "{\\"query\\":\\"ai contest\\",\\"limit\\":10}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """, JsonObject.class);

        Method method = AbstractOpenAIStyleChatClient.class.getDeclaredMethod("extractToolCallResult", JsonObject.class);
        method.setAccessible(true);
        ToolCallChatResult result = (ToolCallChatResult) method.invoke(client, response);

        assertEquals(1, result.getToolCalls().size());
        assertEquals("openweb_search", result.getToolCalls().get(0).getName());
        assertEquals("ai contest", result.getToolCalls().get(0).getArguments().get("query"));
        assertEquals("10", result.getToolCalls().get(0).getArguments().get("limit").toString());
    }

    private static ModelTarget target() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setModel("test-model");
        return new ModelTarget("test-model", candidate, provider);
    }

    private static class TestClient extends AbstractOpenAIStyleChatClient {

        private TestClient() {
            super(new OkHttpClient(), Runnable::run);
        }

        private JsonObject exposeRequestBody(ChatRequest request, ModelTarget target) {
            return buildRequestBody(request, target, false);
        }

        @Override
        public String provider() {
            return "test";
        }

        @Override
        public String chat(ChatRequest request, ModelTarget target) {
            return "";
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback, ModelTarget target) {
            return null;
        }
    }
}
