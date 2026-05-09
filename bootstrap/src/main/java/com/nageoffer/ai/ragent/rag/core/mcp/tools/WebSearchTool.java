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

package com.nageoffer.ai.ragent.rag.core.mcp.tools;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPRequest;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPResponse;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPTool;
import com.nageoffer.ai.ragent.rag.core.mcp.MCPToolExecutor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class WebSearchTool implements MCPToolExecutor {

    private static final String TOOL_ID = "web_search";

    @Value("${rag.mcp.builtin-tools.web-search.api-key:}")
    private String apiKey;

    @Value("${rag.mcp.builtin-tools.web-search.provider:google}")
    private String searchProvider;

    private final Gson gson = new Gson();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public MCPTool getToolDefinition() {
        Map<String, MCPTool.ParameterDef> parameters = new HashMap<>();
        parameters.put("query", MCPTool.ParameterDef.builder()
                .description("搜索查询关键词")
                .type("string")
                .required(true)
                .build());
        parameters.put("num_results", MCPTool.ParameterDef.builder()
                .description("返回结果数量（默认5，最大10）")
                .type("number")
                .required(false)
                .defaultValue(5)
                .enumValues(List.of("5", "10"))
                .build());

        return MCPTool.builder()
                .toolId(TOOL_ID)
                .description("搜索互联网上的最新技术资料、论文、博客文章、官方文档等。适用于查找最新的算法实现、库使用方法、API文档等。")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPResponse execute(MCPRequest request) {
        Map<String, Object> params = request.getParameters();
        String query = (String) params.get("query");
        int numResults = params.get("num_results") != null ?
                ((Number) params.get("num_results")).intValue() : 5;

        log.info("执行全网搜索 - 查询: {}, 结果数: {}", query, numResults);

        try {
            String searchResults = performSearch(query, Math.min(numResults, 10));
            return MCPResponse.success(TOOL_ID, searchResults);
        } catch (Exception e) {
            log.error("全网搜索执行失败", e);
            return MCPResponse.error(TOOL_ID, "SEARCH_ERROR", "搜索服务暂时不可用: " + e.getMessage());
        }
    }

    private String performSearch(String query, int numResults) throws IOException {
        switch (searchProvider.toLowerCase()) {
            case "bing":
                return searchWithBing(query, numResults);
            case "duckduckgo":
                return searchWithDuckDuckGo(query, numResults);
            default:
                return searchWithGoogle(query, numResults);
        }
    }

    private String searchWithGoogle(String query, int numResults) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            return generateMockSearchResults(query, numResults);
        }

        String url = String.format(
                "https://www.googleapis.com/customsearch/v1?key=%s&cx=017576662512468239146:omuauf_lfve&q=%s&num=%d",
                apiKey,
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                numResults
        );

        Request httpRequest = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Google API请求失败: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseGoogleSearchResults(responseBody);
        }
    }

    private String parseGoogleSearchResults(String json) {
        StringBuilder result = new StringBuilder();
        result.append("## 搜索结果\n\n");

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("items")) {
                var items = root.getAsJsonArray("items");
                for (int i = 0; i < items.size(); i++) {
                    var item = items.get(i).getAsJsonObject();
                    String title = item.has("title") ? item.get("title").getAsString() : "无标题";
                    String link = item.has("link") ? item.get("link").getAsString() : "";
                    String snippet = item.has("snippet") ? item.get("snippet").getAsString() : "";

                    result.append(String.format("**%d. %s**\n", i + 1, title));
                    result.append(String.format("   %s\n", link));
                    result.append(String.format("   %s\n\n", snippet));
                }
            }
        } catch (Exception e) {
            log.warn("解析Google搜索结果失败", e);
            result.append("（解析结果时出现错误）\n");
        }

        return result.toString();
    }

    private String searchWithBing(String query, int numResults) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            return generateMockSearchResults(query, numResults);
        }

        String url = String.format(
                "https://api.bing.microsoft.com/v7.0/search?q=%s&count=%d",
                URLEncoder.encode(query, StandardCharsets.UTF_8),
                numResults
        );

        Request httpRequest = new Request.Builder()
                .url(url)
                .header("Ocp-Apim-Subscription-Key", apiKey)
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Bing API请求失败: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseBingSearchResults(responseBody);
        }
    }

    private String parseBingSearchResults(String json) {
        StringBuilder result = new StringBuilder();
        result.append("## Bing搜索结果\n\n");

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            if (root.has("webPages")) {
                var webPages = root.getAsJsonObject("webPages");
                if (webPages.has("value")) {
                    var items = webPages.getAsJsonArray("value");
                    for (int i = 0; i < items.size(); i++) {
                        var item = items.get(i).getAsJsonObject();
                        String name = item.has("name") ? item.get("name").getAsString() : "无标题";
                        String itemUrl = item.has("url") ? item.get("url").getAsString() : "";
                        String snippet = item.has("snippet") ? item.get("snippet").getAsString() : "";

                        result.append(String.format("**%d. %s**\n", i + 1, name));
                        result.append(String.format("   %s\n", itemUrl));
                        result.append(String.format("   %s\n\n", snippet));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析Bing搜索结果失败", e);
            result.append("（解析结果时出现错误）\n");
        }

        return result.toString();
    }

    private String searchWithDuckDuckGo(String query, int numResults) throws IOException {
        String url = String.format(
                "https://api.duckduckgo.com/?q=%s&format=json&no_html=1",
                URLEncoder.encode(query, StandardCharsets.UTF_8)
        );

        Request httpRequest = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("DuckDuckGo API请求失败: " + response.code());
            }

            String responseBody = response.body() != null ? response.body().string() : "";
            return parseDuckDuckGoResults(responseBody);
        }
    }

    private String parseDuckDuckGoResults(String json) {
        StringBuilder result = new StringBuilder();
        result.append("## DuckDuckGo搜索结果\n\n");

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);
            String abstractText = root.has("Abstract") ? root.get("Abstract").getAsString() : "";

            if (!abstractText.isBlank()) {
                result.append("**摘要**: ").append(abstractText).append("\n\n");
            }

            if (root.has("RelatedTopics")) {
                var topics = root.getAsJsonArray("RelatedTopics");
                int count = 0;
                for (var topic : topics) {
                    if (count >= 5) break;
                    var topicObj = topic.getAsJsonObject();
                    String text = topicObj.has("Text") ? topicObj.get("Text").getAsString() : "";
                    String topicUrl = topicObj.has("FirstURL") ? topicObj.get("FirstURL").getAsString() : "";

                    if (!text.isBlank()) {
                        result.append(String.format("- %s [%s]\n", text, topicUrl));
                        count++;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("解析DuckDuckGo搜索结果失败", e);
            result.append("（解析结果时出现错误）\n");
        }

        return result.toString();
    }

    private String generateMockSearchResults(String query, int numResults) {
        StringBuilder result = new StringBuilder();
        result.append("## 搜索结果（模拟模式）\n\n");
        result.append("> 注意: 当前未配置搜索API密钥，以下为模拟结果。请在application.yaml中配置`rag.mcp.builtin-tools.web-search.api-key`以启用真实搜索。\n\n");

        String[] mockTitles = {
                String.format("%s - 官方文档或教程", query),
                String.format("%s - GitHub开源项目", query),
                String.format("%s - 技术博客详解", query),
                String.format("%s - Stack Overflow讨论", query),
                String.format("%s - 最佳实践指南", query),
                String.format("%s - 性能优化技巧", query),
                String.format("%s - 常见问题解答", query),
                String.format("%s - 最新版本更新日志", query),
                String.format("%s - 示例代码仓库", query),
                String.format("%s - 相关论文和研究", query)
        };

        String[] mockUrls = {
                "https://example.com/docs",
                "https://github.com/example/project",
                "https://blog.example.com/article",
                "https://stackoverflow.com/questions/12345",
                "https://example.com/best-practices"
        };

        for (int i = 0; i < Math.min(numResults, mockTitles.length); i++) {
            result.append(String.format("**%d. %s**\n", i + 1, mockTitles[i]));
            result.append(String.format("   %s\n\n", mockUrls[i % mockUrls.length]));
        }

        result.append("\n---\n");
        result.append("*以上为模拟数据，配置API Key后可获取真实搜索结果*");

        return result.toString();
    }
}
