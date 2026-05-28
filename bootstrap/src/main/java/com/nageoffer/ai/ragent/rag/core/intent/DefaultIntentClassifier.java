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

package com.nageoffer.ai.ragent.rag.core.intent;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.chat.LLMService;
import com.nageoffer.ai.ragent.infra.util.LLMResponseCleaner;
import com.nageoffer.ai.ragent.rag.core.prompt.PromptTemplateLoader;
import com.nageoffer.ai.ragent.rag.dao.entity.IntentNodeDO;
import com.nageoffer.ai.ragent.rag.dao.mapper.IntentNodeMapper;
import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import com.nageoffer.ai.ragent.rag.enums.PreferredSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.nageoffer.ai.ragent.rag.constant.RAGConstant.INTENT_CLASSIFIER_PROMPT_PATH;

@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultIntentClassifier implements IntentClassifier, IntentNodeRegistry {

    private final LLMService llmService;
    private final IntentNodeMapper intentNodeMapper;
    private final PromptTemplateLoader promptTemplateLoader;
    private final IntentTreeCacheManager intentTreeCacheManager;

    @Override
    public IntentNode getNodeById(String id) {
        if (StrUtil.isBlank(id)) {
            return null;
        }
        return loadIntentTreeData().id2Node().get(id);
    }

    @Override
    public List<NodeScore> classifyTargets(String question) {
        IntentTreeData treeData = loadIntentTreeData();
        if (CollUtil.isEmpty(treeData.leafNodes())) {
            return List.of();
        }

        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system(buildPrompt(treeData.leafNodes())),
                        ChatMessage.user(question)
                ))
                .temperature(0.1D)
                .topP(0.3D)
                .thinking(false)
                .build();

        String raw = llmService.chat(request);
        try {
            JsonArray results = parseResultArray(raw);
            List<NodeScore> scores = new ArrayList<>();
            for (JsonElement element : results) {
                if (!element.isJsonObject()) {
                    continue;
                }
                JsonObject item = element.getAsJsonObject();
                if (!item.has("id") || !item.has("score")) {
                    continue;
                }

                String id = item.get("id").getAsString();
                IntentNode node = treeData.id2Node().get(id);
                if (node == null) {
                    log.warn("Intent classifier returned unknown node id: {}", id);
                    continue;
                }

                scores.add(new NodeScore(node, item.get("score").getAsDouble()));
            }

            scores.sort(Comparator.comparingDouble(NodeScore::getScore).reversed());
            log.info("Intent classify question: {}\n{}", question, summarizeScores(scores));
            return scores;
        } catch (Exception ex) {
            log.warn("Failed to parse intent classifier response: {}", raw, ex);
            return List.of();
        }
    }

    private IntentTreeData loadIntentTreeData() {
        List<IntentNode> roots = intentTreeCacheManager.getIntentTreeFromCache();
        if (CollUtil.isEmpty(roots)) {
            roots = loadIntentTreeFromDB();
            if (CollUtil.isNotEmpty(roots)) {
                intentTreeCacheManager.saveIntentTreeToCache(roots);
            }
        }

        if (CollUtil.isEmpty(roots)) {
            return new IntentTreeData(List.of(), List.of(), Map.of());
        }

        List<IntentNode> allNodes = flatten(roots);
        List<IntentNode> leafNodes = allNodes.stream()
                .filter(IntentNode::isLeaf)
                .collect(Collectors.toList());
        Map<String, IntentNode> id2Node = allNodes.stream()
                .collect(Collectors.toMap(IntentNode::getId, each -> each));
        return new IntentTreeData(allNodes, leafNodes, id2Node);
    }

    private List<IntentNode> flatten(List<IntentNode> roots) {
        List<IntentNode> result = new ArrayList<>();
        Deque<IntentNode> stack = new ArrayDeque<>(roots);
        while (!stack.isEmpty()) {
            IntentNode node = stack.pop();
            result.add(node);
            if (CollUtil.isNotEmpty(node.getChildren())) {
                for (IntentNode child : node.getChildren()) {
                    stack.push(child);
                }
            }
        }
        return result;
    }

    private JsonArray parseResultArray(String raw) {
        String cleaned = LLMResponseCleaner.stripMarkdownCodeFence(raw);
        JsonElement root = JsonParser.parseString(cleaned);
        if (root.isJsonArray()) {
            return root.getAsJsonArray();
        }
        if (root.isJsonObject() && root.getAsJsonObject().has("results")) {
            return root.getAsJsonObject().getAsJsonArray("results");
        }
        throw new IllegalStateException("Unexpected classifier response format");
    }

    private String buildPrompt(List<IntentNode> leafNodes) {
        StringBuilder sb = new StringBuilder();
        for (IntentNode node : leafNodes) {
            sb.append("- id=").append(node.getId()).append('\n');
            sb.append("  path=").append(node.getFullPath()).append('\n');
            sb.append("  description=").append(StrUtil.blankToDefault(node.getDescription(), "")).append('\n');

            if (node.isMCP()) {
                sb.append("  type=MCP\n");
                if (StrUtil.isNotBlank(node.getMcpToolId())) {
                    sb.append("  toolId=").append(node.getMcpToolId()).append('\n');
                }
            } else if (node.isSystem()) {
                sb.append("  type=SYSTEM\n");
            } else {
                sb.append("  type=KB\n");
            }

            if (CollUtil.isNotEmpty(node.getExamples())) {
                sb.append("  examples=").append(String.join(" / ", node.getExamples())).append('\n');
            }
            if (StrUtil.isNotBlank(node.getRoutingHint())) {
                sb.append("  routingHint=").append(node.getRoutingHint()).append('\n');
            }
            if (node.getPreferredSource() != null) {
                sb.append("  preferredSource=").append(node.getPreferredSource().name()).append('\n');
            }
            sb.append('\n');
        }
        return promptTemplateLoader.render(
                INTENT_CLASSIFIER_PROMPT_PATH,
                Map.of("intent_list", sb.toString())
        );
    }

    private List<IntentNode> loadIntentTreeFromDB() {
        List<IntentNodeDO> rows = intentNodeMapper.selectList(
                Wrappers.lambdaQuery(IntentNodeDO.class)
                        .eq(IntentNodeDO::getDeleted, 0)
        );
        if (CollUtil.isEmpty(rows)) {
            return List.of();
        }

        Map<String, IntentNode> id2Node = new HashMap<>();
        for (IntentNodeDO each : rows) {
            IntentNode node = IntentNode.builder()
                    .id(each.getIntentCode())
                    .kbId(each.getKbId())
                    .name(each.getName())
                    .description(each.getDescription())
                    .level(IntentLevel.fromCode(each.getLevel()))
                    .parentId(each.getParentCode())
                    .examples(parseExamples(each.getExamples()))
                    .children(new ArrayList<>())
                    .fullPath("")
                    .kind(IntentKind.fromCode(each.getKind()))
                    .collectionName(each.getCollectionName())
                    .mcpToolId(each.getMcpToolId())
                    .topK(each.getTopK())
                    .promptSnippet(each.getPromptSnippet())
                    .promptTemplate(each.getPromptTemplate())
                    .paramPromptTemplate(each.getParamPromptTemplate())
                    .metadataFilterTemplate(each.getMetadataFilterTemplate())
                    .slotSchemaJson(each.getSlotSchemaJson())
                    .routingHint(each.getRoutingHint())
                    .preferredSource(parsePreferredSource(each.getPreferredSource()))
                    .build();
            id2Node.put(node.getId(), node);
        }

        List<IntentNode> roots = new ArrayList<>();
        for (IntentNode node : id2Node.values()) {
            if (StrUtil.isBlank(node.getParentId())) {
                roots.add(node);
                continue;
            }
            IntentNode parent = id2Node.get(node.getParentId());
            if (parent == null) {
                roots.add(node);
                continue;
            }
            if (parent.getChildren() == null) {
                parent.setChildren(new ArrayList<>());
            }
            parent.getChildren().add(node);
        }

        fillFullPath(roots, null);
        return roots;
    }

    private void fillFullPath(List<IntentNode> nodes, IntentNode parent) {
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        for (IntentNode node : nodes) {
            node.setFullPath(parent == null ? node.getName() : parent.getFullPath() + " > " + node.getName());
            if (CollUtil.isNotEmpty(node.getChildren())) {
                fillFullPath(node.getChildren(), node);
            }
        }
    }

    private PreferredSource parsePreferredSource(String raw) {
        try {
            return PreferredSource.fromValue(raw);
        } catch (Exception ex) {
            return PreferredSource.KB;
        }
    }

    private List<String> parseExamples(String raw) {
        if (StrUtil.isBlank(raw)) {
            return new ArrayList<>();
        }
        try {
            return JSONUtil.toList(JSONUtil.parseArray(raw), String.class);
        } catch (Exception ex) {
            return new ArrayList<>();
        }
    }

    private String summarizeScores(List<NodeScore> scores) {
        return JSONUtil.toJsonPrettyStr(
                scores.stream()
                        .map(each -> Map.of(
                                "id", each.getNode().getId(),
                                "path", each.getNode().getFullPath(),
                                "score", each.getScore()
                        ))
                        .toList()
        );
    }

    private record IntentTreeData(
            List<IntentNode> allNodes,
            List<IntentNode> leafNodes,
            Map<String, IntentNode> id2Node
    ) {
    }
}
