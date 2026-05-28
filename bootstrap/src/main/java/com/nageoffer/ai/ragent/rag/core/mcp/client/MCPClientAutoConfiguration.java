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
import com.nageoffer.ai.ragent.rag.core.mcp.MCPToolRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(MCPClientProperties.class)
public class MCPClientAutoConfiguration {

    private final MCPClientProperties properties;
    private final MCPToolRegistry toolRegistry;

    @PostConstruct
    public void init() {
        List<MCPClientProperties.ServerConfig> servers = properties.getServers();
        if (servers == null || servers.isEmpty()) {
            log.info("No MCP servers configured, skip remote tool registration");
            return;
        }

        for (MCPClientProperties.ServerConfig server : servers) {
            if (!server.isEnabled()) {
                log.info("MCP Server [{}] disabled, skip remote tool registration", server.getName());
                continue;
            }
            registerRemoteTools(server);
        }
    }

    private void registerRemoteTools(MCPClientProperties.ServerConfig server) {
        String serverName = server.getName();
        String serverUrl = server.getUrl();
        log.info("Connecting MCP Server: name={}, url={}", serverName, serverUrl);

        try {
            List<String> missingEnv = findMissingRequiredEnv(server.getRequiredEnv());
            if (!missingEnv.isEmpty()) {
                log.warn("MCP Server [{}] skipped, missing required env: {}", serverName, missingEnv);
                return;
            }

            HttpMCPClient mcpClient = new HttpMCPClient(serverUrl, server.getEndpoint(), server.getHeaders());
            boolean initialized = mcpClient.initialize();
            if (!initialized) {
                log.warn("MCP Server [{}] initialize failed, skip remote tool registration", serverName);
                return;
            }

            List<MCPTool> tools = mcpClient.listTools();
            if (tools.isEmpty()) {
                log.info("MCP Server [{}] returned no tools, skip remote tool registration", serverName);
                return;
            }
            log.info("MCP Server [{}] returned {} tools", serverName, tools.size());

            for (MCPTool tool : tools) {
                String remoteToolId = tool.getToolId();
                MCPTool localTool = withLocalToolId(tool, server.getToolPrefix());
                RemoteMCPToolExecutor executor = new RemoteMCPToolExecutor(mcpClient, localTool, remoteToolId);
                toolRegistry.register(executor);
                log.info("Registered remote MCP tool: toolId={}, remoteToolId={}, server={}",
                        localTool.getToolId(), remoteToolId, serverName);
            }
        } catch (Exception e) {
            log.warn("MCP Server [{}] unavailable, skip remote tool registration, reason={}",
                    serverName, e.getMessage());
        }
    }

    private List<String> findMissingRequiredEnv(List<String> requiredEnv) {
        if (requiredEnv == null || requiredEnv.isEmpty()) {
            return List.of();
        }
        return requiredEnv.stream()
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> {
                    String value = System.getenv(name);
                    return value == null || value.isBlank();
                })
                .toList();
    }

    private MCPTool withLocalToolId(MCPTool tool, String toolPrefix) {
        String localToolId = applyToolPrefix(tool.getToolId(), toolPrefix);
        if (java.util.Objects.equals(localToolId, tool.getToolId())) {
            return tool;
        }
        return MCPTool.builder()
                .toolId(localToolId)
                .description(tool.getDescription())
                .parameters(tool.getParameters())
                .requireUserId(tool.isRequireUserId())
                .mcpServerUrl(tool.getMcpServerUrl())
                .build();
    }

    private String applyToolPrefix(String toolId, String toolPrefix) {
        if (toolId == null || toolId.isBlank() || toolPrefix == null || toolPrefix.isBlank()) {
            return toolId;
        }
        String normalizedPrefix = toolPrefix.endsWith("_") ? toolPrefix : toolPrefix + "_";
        return toolId.startsWith(normalizedPrefix) ? toolId : normalizedPrefix + toolId;
    }
}
