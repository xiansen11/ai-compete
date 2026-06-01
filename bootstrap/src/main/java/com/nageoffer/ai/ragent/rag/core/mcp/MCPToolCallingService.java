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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.framework.context.UserContext;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.framework.convention.ChatTool;
import com.nageoffer.ai.ragent.framework.convention.ToolCall;
import com.nageoffer.ai.ragent.framework.convention.ToolCallChatResult;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import com.nageoffer.ai.ragent.rag.core.intent.NodeScore;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * Selects MCP tools with native LLM tool calling and executes the selected tools.
 */
@Slf4j
@Service
public class MCPToolCallingService {

    private static final String TOOL_SELECTION_SYSTEM_PROMPT = """
            You are a tool selection router for MCP tools.
            Select zero or more tools from the provided tool list to answer the user question.
            Prefer the most specific tool available. For direct web page content requests, prefer a fetch/content tool over a keyword search tool when such a tool is available.
            Return native tool calls only. Do not invent tool names or arguments.
            """;

    private final LLMService llmService;
    private final MCPToolRegistry mcpToolRegistry;
    @Qualifier("mcpBatchThreadPoolExecutor")
    private final Executor mcpBatchExecutor;

    public MCPToolCallingService(LLMService llmService,
                                 MCPToolRegistry mcpToolRegistry,
                                 @Qualifier("mcpBatchThreadPoolExecutor") Executor mcpBatchExecutor) {
        this.llmService = llmService;
        this.mcpToolRegistry = mcpToolRegistry;
        this.mcpBatchExecutor = mcpBatchExecutor;
    }

    @Value("${rag.mcp.tool-calling.enabled:true}")
    private boolean enabled;

    @Value("${rag.mcp.tool-calling.max-tools:64}")
    private int maxTools;

    @Value("${rag.mcp.tool-calling.tool-choice:auto}")
    private String toolChoice;

    @Value("${rag.mcp.tool-calling.excluded-tools:}")
    private String excludedToolsConfig;

    public List<MCPResponse> selectAndExecute(String question, List<NodeScore> mcpIntentScores) {
        if (!enabled) {
            log.info("MCP tool calling disabled");
            return List.of();
        }

        Set<String> excludedTools = resolveExcludedTools();
        Set<String> constrainedToolIds = resolveConstrainedToolIds(mcpIntentScores);
        List<MCPTool> tools = mcpToolRegistry.listAllTools().stream()
                .filter(tool -> tool != null && StrUtil.isNotBlank(tool.getToolId()))
                .filter(tool -> !excludedTools.contains(tool.getToolId()))
                .filter(tool -> constrainedToolIds.isEmpty() || constrainedToolIds.contains(tool.getToolId()))
                .limit(Math.max(1, maxTools))
                .toList();
        if (CollUtil.isEmpty(tools)) {
            log.warn("No registered MCP tools are available for tool calling");
            return List.of();
        }

        ToolCallChatResult selection = selectTools(question, mcpIntentScores, tools);
        if (selection == null) {
            return List.of();
        }
        if (CollUtil.isEmpty(selection.getToolCalls())) {
            if (StrUtil.isNotBlank(selection.getContent())) {
                return List.of(MCPResponse.success("llm_tool_selection", selection.getContent()));
            }
            log.info("LLM selected no MCP tools for question: {}", question);
            return List.of();
        }

        log.info("LLM selected MCP tools: {}", selection.getToolCalls().stream()
                .map(ToolCall::getName)
                .collect(Collectors.joining(",")));

        List<CompletableFuture<MCPResponse>> futures = selection.getToolCalls().stream()
                .map(toolCall -> CompletableFuture.supplyAsync(
                        () -> executeToolCall(question, toolCall),
                        mcpBatchExecutor
                ))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .toList();
    }

    private ToolCallChatResult selectTools(String question, List<NodeScore> mcpIntentScores, List<MCPTool> tools) {
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(TOOL_SELECTION_SYSTEM_PROMPT),
                        ChatMessage.user(buildSelectionUserPrompt(question, mcpIntentScores))
                ))
                .enableTools(true)
                .tools(tools.stream().map(this::toChatTool).toList())
                .toolChoice(StrUtil.blankToDefault(toolChoice, "auto"))
                .temperature(0D)
                .build();
        try {
            return llmService.chatWithTools(request);
        } catch (Exception e) {
            log.error("LLM MCP tool selection failed", e);
            return null;
        }
    }

    private String buildSelectionUserPrompt(String question, List<NodeScore> mcpIntentScores) {
        StringBuilder builder = new StringBuilder();
        builder.append("User question:\n").append(question).append("\n\n");
        if (CollUtil.isNotEmpty(mcpIntentScores)) {
            builder.append("Coarse MCP intents:\n");
            for (NodeScore score : mcpIntentScores) {
                IntentNode node = score.getNode();
                if (node == null) {
                    continue;
                }
                builder.append("- ")
                        .append(node.getName())
                        .append(" (id=")
                        .append(node.getId())
                        .append(", score=")
                        .append(score.getScore())
                        .append(")");
                if (StrUtil.isNotBlank(node.getDescription())) {
                    builder.append(": ").append(node.getDescription());
                }
                builder.append('\n');
            }
        }
        return builder.toString();
    }

    private Set<String> resolveExcludedTools() {
        if (StrUtil.isBlank(excludedToolsConfig)) {
            return Set.of();
        }
        return Arrays.stream(excludedToolsConfig.split(","))
                .map(String::trim)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private Set<String> resolveConstrainedToolIds(List<NodeScore> mcpIntentScores) {
        if (CollUtil.isEmpty(mcpIntentScores)) {
            return Set.of();
        }
        return mcpIntentScores.stream()
                .map(NodeScore::getNode)
                .filter(node -> node != null && StrUtil.isNotBlank(node.getMcpToolId()))
                .map(IntentNode::getMcpToolId)
                .filter(mcpToolRegistry::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private ChatTool toChatTool(MCPTool tool) {
        return ChatTool.builder()
                .function(ChatTool.FunctionDef.builder()
                        .name(tool.getToolId())
                        .description(StrUtil.blankToDefault(tool.getDescription(), tool.getToolId()))
                        .parameters(toJsonSchema(tool))
                        .build())
                .build();
    }

    private Map<String, Object> toJsonSchema(MCPTool tool) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        if (tool.getParameters() != null) {
            for (Map.Entry<String, MCPTool.ParameterDef> entry : tool.getParameters().entrySet()) {
                MCPTool.ParameterDef def = entry.getValue();
                Map<String, Object> prop = new LinkedHashMap<>();
                prop.put("type", StrUtil.blankToDefault(def.getType(), "string"));
                if (StrUtil.isNotBlank(def.getDescription())) {
                    prop.put("description", def.getDescription());
                }
                if (def.getEnumValues() != null && !def.getEnumValues().isEmpty()) {
                    prop.put("enum", def.getEnumValues());
                }
                if (def.getDefaultValue() != null) {
                    prop.put("default", def.getDefaultValue());
                }
                properties.put(entry.getKey(), prop);
                if (def.isRequired()) {
                    required.add(entry.getKey());
                }
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    private MCPResponse executeToolCall(String question, ToolCall toolCall) {
        String toolId = toolCall.getName();
        Optional<MCPToolExecutor> executorOpt = mcpToolRegistry.getExecutor(toolId);
        if (executorOpt.isEmpty()) {
            log.warn("LLM selected unknown MCP tool: {}", toolId);
            return MCPResponse.error(toolId, "TOOL_NOT_FOUND", "Tool does not exist: " + toolId);
        }

        try {
            MCPRequest request = MCPRequest.builder()
                    .toolId(toolId)
                    .userId(UserContext.getUserId())
                    .userQuestion(question)
                    .parameters(toolCall.getArguments() != null ? toolCall.getArguments() : new LinkedHashMap<>())
                    .build();
            return executorOpt.get().execute(request);
        } catch (Exception e) {
            log.error("MCP tool execution failed, toolId={}", toolId, e);
            return MCPResponse.error(toolId, "EXECUTION_ERROR", "Tool call failed: " + e.getMessage());
        }
    }
}
