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

package com.nageoffer.ai.ragent.mcp.config;

import com.nageoffer.ai.ragent.mcp.core.MCPToolDefinition;
import com.nageoffer.ai.ragent.mcp.core.MCPToolExecutor;
import com.nageoffer.ai.ragent.mcp.core.MCPToolRegistry;
import com.nageoffer.ai.ragent.mcp.core.MCPToolRequest;
import com.nageoffer.ai.ragent.mcp.core.MCPToolResponse;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.HttpServletStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(McpServerProperties.class)
public class McpSdkServerConfiguration {

    @Bean
    public HttpServletStreamableServerTransportProvider mcpTransportProvider(McpServerProperties properties) {
        return HttpServletStreamableServerTransportProvider.builder()
                .mcpEndpoint(properties.getEndpoint())
                .build();
    }

    @Bean
    public ServletRegistrationBean<HttpServletStreamableServerTransportProvider> mcpServletRegistration(
            HttpServletStreamableServerTransportProvider transportProvider,
            McpServerProperties properties) {
        return new ServletRegistrationBean<>(transportProvider, properties.getEndpoint());
    }

    @Bean
    public McpSyncServer mcpSyncServer(
            HttpServletStreamableServerTransportProvider transportProvider,
            MCPToolRegistry toolRegistry) {
        List<McpServerFeatures.SyncToolSpecification> tools = toolRegistry.listAllExecutors().stream()
                .map(this::toSdkToolSpecification)
                .toList();

        return McpServer.sync(transportProvider)
                .serverInfo("ai-compete-mcp-server", "1.0.0")
                .tools(tools)
                .build();
    }

    private McpServerFeatures.SyncToolSpecification toSdkToolSpecification(MCPToolExecutor executor) {
        MCPToolDefinition definition = executor.getToolDefinition();
        return McpServerFeatures.SyncToolSpecification.builder()
                .tool(toSdkTool(definition))
                .callHandler((exchange, request) -> executeTool(executor, request))
                .build();
    }

    private McpSchema.Tool toSdkTool(MCPToolDefinition definition) {
        return McpSchema.Tool.builder()
                .name(definition.getToolId())
                .description(definition.getDescription())
                .inputSchema(toInputSchema(definition))
                .build();
    }

    private McpSchema.JsonSchema toInputSchema(MCPToolDefinition definition) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        Map<String, MCPToolDefinition.ParameterDef> parameters = definition.getParameters();
        if (parameters != null) {
            parameters.forEach((name, parameter) -> {
                Map<String, Object> property = new LinkedHashMap<>();
                property.put("type", normalizeType(parameter.getType()));
                property.put("description", parameter.getDescription());
                if (parameter.getDefaultValue() != null) {
                    property.put("default", parameter.getDefaultValue());
                }
                if (parameter.getEnumValues() != null && !parameter.getEnumValues().isEmpty()) {
                    property.put("enum", parameter.getEnumValues());
                }
                properties.put(name, property);
                if (parameter.isRequired()) {
                    required.add(name);
                }
            });
        }

        return new McpSchema.JsonSchema("object", properties, required, false, null, null);
    }

    private String normalizeType(String type) {
        if ("integer".equals(type)) {
            return "number";
        }
        return type == null || type.isBlank() ? "string" : type;
    }

    private McpSchema.CallToolResult executeTool(MCPToolExecutor executor, McpSchema.CallToolRequest sdkRequest) {
        long start = System.currentTimeMillis();
        try {
            MCPToolRequest request = MCPToolRequest.builder()
                    .toolId(sdkRequest.name())
                    .parameters(sdkRequest.arguments() != null ? new HashMap<>(sdkRequest.arguments()) : new HashMap<>())
                    .build();
            MCPToolResponse response = executor.execute(request);
            response.setCostMs(System.currentTimeMillis() - start);
            return toSdkResult(response);
        } catch (Exception e) {
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            return McpSchema.CallToolResult.builder()
                    .addTextContent(message)
                    .isError(true)
                    .build();
        }
    }

    private McpSchema.CallToolResult toSdkResult(MCPToolResponse response) {
        String text = response.isSuccess() ? response.getTextResult() : response.getErrorMessage();
        if (text == null || text.isBlank()) {
            text = response.isSuccess() ? "" : "Tool call failed";
        }

        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .addTextContent(text)
                .isError(!response.isSuccess());

        if (response.getData() != null && !response.getData().isEmpty()) {
            builder.structuredContent(response.getData());
        }
        if (response.getErrorCode() != null || response.getCostMs() > 0) {
            Map<String, Object> meta = new LinkedHashMap<>();
            if (response.getErrorCode() != null) {
                meta.put("errorCode", response.getErrorCode());
            }
            if (response.getCostMs() > 0) {
                meta.put("costMs", response.getCostMs());
            }
            builder.meta(meta);
        }
        return builder.build();
    }
}
