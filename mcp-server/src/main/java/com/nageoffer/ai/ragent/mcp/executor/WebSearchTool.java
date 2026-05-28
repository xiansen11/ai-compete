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

package com.nageoffer.ai.ragent.mcp.executor;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.nageoffer.ai.ragent.mcp.core.MCPToolDefinition;
import com.nageoffer.ai.ragent.mcp.core.MCPToolExecutor;
import com.nageoffer.ai.ragent.mcp.core.MCPToolRequest;
import com.nageoffer.ai.ragent.mcp.core.MCPToolResponse;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.mcp.builtin-tools.web-search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebSearchTool implements MCPToolExecutor {

    private static final String TOOL_ID = "web_search";

    @Value("${rag.mcp.builtin-tools.web-search.api-key:}")
    private String apiKey;

    @Value("${rag.mcp.builtin-tools.web-search.provider:google}")
    private String provider;

    private final Gson gson = new Gson();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build();

    @Override
    public MCPToolDefinition getToolDefinition() {
        Map<String, MCPToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();
        parameters.put("query", MCPToolDefinition.ParameterDef.builder()
                .description("Search query")
                .type("string")
                .required(true)
                .build());
        parameters.put("num_results", MCPToolDefinition.ParameterDef.builder()
                .description("Number of search results, default 5 and max 10")
                .type("number")
                .defaultValue(5)
                .enumValues(List.of("5", "10"))
                .build());
        return MCPToolDefinition.builder()
                .toolId(TOOL_ID)
                .description("Search the public web for technical docs, articles, tutorials, and recent references.")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPToolResponse execute(MCPToolRequest request) {
        String query = request.getStringParameter("query");
        Number count = request.getParameter("num_results");
        int numResults = count != null ? Math.min(count.intValue(), 10) : 5;
        if (query == null || query.isBlank()) {
            return MCPToolResponse.error(TOOL_ID, "INVALID_PARAMS", "query is required");
        }
        try {
            return MCPToolResponse.success(TOOL_ID, search(query, numResults));
        } catch (Exception e) {
            log.warn("Web search failed, query={}, reason={}", query, e.getMessage());
            return MCPToolResponse.error(TOOL_ID, "SEARCH_ERROR", e.getMessage());
        }
    }

    private String search(String query, int numResults) throws IOException {
        if (apiKey == null || apiKey.isBlank()) {
            return mockResults(query, numResults);
        }
        if ("bing".equalsIgnoreCase(provider)) {
            return searchBing(query, numResults);
        }
        return searchGoogle(query, numResults);
    }

    private String searchGoogle(String query, int numResults) throws IOException {
        String url = "https://www.googleapis.com/customsearch/v1?key=%s&cx=017576662512468239146:omuauf_lfve&q=%s&num=%d"
                .formatted(apiKey, URLEncoder.encode(query, StandardCharsets.UTF_8), numResults);
        return formatGoogleResults(fetch(url, null));
    }

    private String searchBing(String query, int numResults) throws IOException {
        String url = "https://api.bing.microsoft.com/v7.0/search?q=%s&count=%d"
                .formatted(URLEncoder.encode(query, StandardCharsets.UTF_8), numResults);
        return formatBingResults(fetch(url, Map.of("Ocp-Apim-Subscription-Key", apiKey)));
    }

    private String fetch(String url, Map<String, String> headers) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (headers != null) {
            headers.forEach(builder::header);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private String formatGoogleResults(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        StringBuilder result = new StringBuilder("## Search Results\n\n");
        if (root != null && root.has("items")) {
            var items = root.getAsJsonArray("items");
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i).getAsJsonObject();
                appendResult(result, i + 1, value(item, "title"), value(item, "link"), value(item, "snippet"));
            }
        }
        return result.toString();
    }

    private String formatBingResults(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        StringBuilder result = new StringBuilder("## Bing Search Results\n\n");
        if (root != null && root.has("webPages")) {
            var items = root.getAsJsonObject("webPages").getAsJsonArray("value");
            for (int i = 0; i < items.size(); i++) {
                var item = items.get(i).getAsJsonObject();
                appendResult(result, i + 1, value(item, "name"), value(item, "url"), value(item, "snippet"));
            }
        }
        return result.toString();
    }

    private void appendResult(StringBuilder result, int index, String title, String url, String snippet) {
        result.append(index).append(". ").append(title).append('\n');
        result.append("   ").append(url).append('\n');
        result.append("   ").append(snippet).append("\n\n");
    }

    private String value(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : "";
    }

    private String mockResults(String query, int numResults) {
        StringBuilder result = new StringBuilder("## Search Results (mock mode)\n\n");
        result.append("No search API key is configured; set SEARCH_API_KEY to enable live results.\n\n");
        for (int i = 1; i <= numResults; i++) {
            appendResult(result, i, query + " reference " + i, "https://example.com/result-" + i,
                    "Mock search result for local development.");
        }
        return result.toString();
    }
}
