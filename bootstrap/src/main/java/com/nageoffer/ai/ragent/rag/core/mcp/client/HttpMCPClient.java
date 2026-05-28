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

import com.nageoffer.ai.ragent.rag.core.mcp.MCPTool;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class HttpMCPClient implements MCPClient {

    private final String serverUrl;
    private final String endpoint;
    private final Map<String, String> headers;
    private final McpSyncClient client;

    public HttpMCPClient(String serverUrl, String endpoint) {
        this(serverUrl, endpoint, Map.of());
    }

    public HttpMCPClient(String serverUrl, String endpoint, Map<String, String> headers) {
        this.serverUrl = trimTrailingSlash(serverUrl);
        this.endpoint = endpoint == null || endpoint.isBlank() ? "/mcp" : endpoint;
        this.headers = sanitizeHeaders(headers);
        HttpClientStreamableHttpTransport transport = HttpClientStreamableHttpTransport.builder(this.serverUrl)
                .endpoint(this.endpoint)
                .customizeRequest(this::applyHeaders)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.client = McpClient.sync(transport)
                .clientInfo(new McpSchema.Implementation("ai-compete-bootstrap", "1.0.0"))
                .requestTimeout(Duration.ofSeconds(30))
                .initializationTimeout(Duration.ofSeconds(15))
                .build();
    }

    private void applyHeaders(java.net.http.HttpRequest.Builder requestBuilder) {
        headers.forEach(requestBuilder::header);
    }

    @Override
    public boolean initialize() {
        try {
            client.initialize();
            return client.isInitialized();
        } catch (Exception e) {
            log.warn("MCP initialize failed, server={}, endpoint={}, reason={}", serverUrl, endpoint, e.getMessage());
            return false;
        }
    }

    @Override
    public List<MCPTool> listTools() {
        McpSchema.ListToolsResult result = client.listTools();
        if (result == null || result.tools() == null) {
            return List.of();
        }
        return result.tools().stream().map(this::toMcpTool).toList();
    }

    @Override
    public String callTool(String toolName, Map<String, Object> arguments) {
        McpSchema.CallToolResult result = client.callTool(new McpSchema.CallToolRequest(
                toolName,
                arguments != null ? arguments : Map.of()));
        if (result == null) {
            return null;
        }
        String text = extractTextContent(result);
        if (Boolean.TRUE.equals(result.isError())) {
            throw new IllegalStateException(text == null || text.isBlank() ? "Remote tool returned an error" : text);
        }
        return text;
    }

    private MCPTool toMcpTool(McpSchema.Tool tool) {
        Map<String, MCPTool.ParameterDef> parameters = new HashMap<>();
        List<String> required = tool.inputSchema() != null && tool.inputSchema().required() != null
                ? tool.inputSchema().required()
                : List.of();

        if (tool.inputSchema() != null && tool.inputSchema().properties() != null) {
            tool.inputSchema().properties().forEach((name, property) -> {
                Map<String, Object> propertyMap = asMap(property);
                MCPTool.ParameterDef parameter = MCPTool.ParameterDef.builder()
                        .type(stringValue(propertyMap.get("type"), "string"))
                        .description(stringValue(propertyMap.get("description"), ""))
                        .required(required.contains(name))
                        .defaultValue(propertyMap.get("default"))
                        .enumValues(toStringList(propertyMap.get("enum")))
                        .build();
                parameters.put(name, parameter);
            });
        }

        return MCPTool.builder()
                .toolId(tool.name())
                .description(tool.description())
                .parameters(parameters)
                .mcpServerUrl(serverUrl + endpoint)
                .requireUserId(true)
                .build();
    }

    private String extractTextContent(McpSchema.CallToolResult result) {
        if (result.content() == null || result.content().isEmpty()) {
            return null;
        }
        return result.content().stream()
                .filter(McpSchema.TextContent.class::isInstance)
                .map(McpSchema.TextContent.class::cast)
                .map(McpSchema.TextContent::text)
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse(null);
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new HashMap<>();
            map.forEach((key, entryValue) -> result.put(String.valueOf(key), entryValue));
            return result;
        }
        return Map.of();
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(String::valueOf).toList();
        }
        return null;
    }

    private String stringValue(Object value, String defaultValue) {
        return value != null ? String.valueOf(value) : defaultValue;
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private Map<String, String> sanitizeHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        headers.forEach((name, value) -> {
            if (name != null && !name.isBlank() && value != null && !value.isBlank()) {
                result.put(name, value);
            }
        });
        return Map.copyOf(result);
    }
}
