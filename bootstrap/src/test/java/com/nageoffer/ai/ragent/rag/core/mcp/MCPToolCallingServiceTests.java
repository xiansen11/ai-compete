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

package com.nageoffer.ai.ragent.rag.core.mcp;

import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.ToolCall;
import com.nageoffer.ai.ragent.framework.convention.ToolCallChatResult;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.infra.chat.StreamCancellationHandle;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MCPToolCallingServiceTests {

    @Test
    void executesOnlyToolsSelectedByLlmToolCalls() throws Exception {
        RecordingRegistry registry = new RecordingRegistry();
        RecordingExecutor executor = new RecordingExecutor();
        registry.executor = executor;

        MCPToolCallingService service = new MCPToolCallingService(
                new ToolSelectingLlm(),
                registry,
                Runnable::run
        );
        setField(service, "enabled", true);
        setField(service, "maxTools", 64);
        setField(service, "toolChoice", "auto");

        List<MCPResponse> responses = service.selectAndExecute("search ai contest", List.of());

        assertEquals(1, responses.size());
        assertTrue(responses.get(0).isSuccess());
        assertEquals("openweb_search", executor.calledToolId);
        assertEquals("ai contest", executor.calledParams.get("query"));
    }

    @Test
    void skipsUnknownToolsSelectedByLlm() throws Exception {
        MCPToolCallingService service = new MCPToolCallingService(
                new UnknownToolSelectingLlm(),
                new RecordingRegistry(),
                Runnable::run
        );
        setField(service, "enabled", true);
        setField(service, "maxTools", 64);
        setField(service, "toolChoice", "auto");

        List<MCPResponse> responses = service.selectAndExecute("search ai contest", List.of());

        assertEquals(1, responses.size());
        assertEquals("TOOL_NOT_FOUND", responses.get(0).getErrorCode());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static class ToolSelectingLlm implements LLMService {

        @Override
        public String chat(ChatRequest request) {
            return "";
        }

        @Override
        public ToolCallChatResult chatWithTools(ChatRequest request) {
            assertEquals(1, request.getTools().size());
            return ToolCallChatResult.builder()
                    .toolCalls(List.of(ToolCall.builder()
                            .name("openweb_search")
                            .arguments(Map.of("query", "ai contest"))
                            .build()))
                    .build();
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            return null;
        }
    }

    private static class UnknownToolSelectingLlm extends ToolSelectingLlm {

        @Override
        public ToolCallChatResult chatWithTools(ChatRequest request) {
            return ToolCallChatResult.builder()
                    .toolCalls(List.of(ToolCall.builder()
                            .name("missing_tool")
                            .arguments(Map.of())
                            .build()))
                    .build();
        }
    }

    private static class RecordingRegistry implements MCPToolRegistry {

        private RecordingExecutor executor;

        @Override
        public void register(MCPToolExecutor executor) {
        }

        @Override
        public void unregister(String toolId) {
        }

        @Override
        public Optional<MCPToolExecutor> getExecutor(String toolId) {
            if ("openweb_search".equals(toolId) && executor != null) {
                return Optional.of(executor);
            }
            return Optional.empty();
        }

        @Override
        public List<MCPTool> listAllTools() {
            return List.of(MCPTool.builder()
                    .toolId("openweb_search")
                    .description("Search the web")
                    .parameters(Map.of("query", MCPTool.ParameterDef.builder()
                            .type("string")
                            .description("Search query")
                            .required(true)
                            .build()))
                    .build());
        }

        @Override
        public List<MCPToolExecutor> listAllExecutors() {
            return executor == null ? List.of() : List.of(executor);
        }

        @Override
        public boolean contains(String toolId) {
            return getExecutor(toolId).isPresent();
        }

        @Override
        public int size() {
            return 1;
        }
    }

    private static class RecordingExecutor implements MCPToolExecutor {

        private String calledToolId;
        private Map<String, Object> calledParams;

        @Override
        public MCPTool getToolDefinition() {
            return MCPTool.builder()
                    .toolId("openweb_search")
                    .description("Search the web")
                    .parameters(Map.of())
                    .build();
        }

        @Override
        public MCPResponse execute(MCPRequest request) {
            this.calledToolId = request.getToolId();
            this.calledParams = request.getParameters();
            return MCPResponse.success(request.getToolId(), "ok");
        }
    }
}
