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

package com.nageoffer.ai.ragent.rag.core.mcp.client;

import com.nageoffer.ai.ragent.rag.core.mcp.MCPRequest;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteMCPToolExecutorTests {

    @Test
    void executeUsesRemoteToolIdWhenLocalToolIdIsPrefixed() {
        RecordingClient client = new RecordingClient();
        MCPTool localTool = MCPTool.builder()
                .toolId("github_search_code")
                .description("Search code")
                .parameters(Map.of())
                .build();
        RemoteMCPToolExecutor executor = new RemoteMCPToolExecutor(client, localTool, "search_code");

        var response = executor.execute(MCPRequest.builder()
                .toolId("github_search_code")
                .parameters(Map.of("query", "HttpMCPClient"))
                .build());

        assertTrue(response.isSuccess());
        assertEquals("search_code", client.calledToolName);
        assertEquals("HttpMCPClient", client.calledArguments.get("query"));
    }

    private static class RecordingClient implements MCPClient {
        private String calledToolName;
        private Map<String, Object> calledArguments;

        @Override
        public boolean initialize() {
            return true;
        }

        @Override
        public List<MCPTool> listTools() {
            return List.of();
        }

        @Override
        public String callTool(String toolName, Map<String, Object> arguments) {
            this.calledToolName = toolName;
            this.calledArguments = arguments;
            return "ok";
        }
    }
}
