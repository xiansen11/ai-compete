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
@ConditionalOnProperty(prefix = "rag.mcp.builtin-tools.paper-search", name = "enabled", havingValue = "true", matchIfMissing = true)
public class PaperSearchTool implements MCPToolExecutor {

    private static final String TOOL_ID = "paper_search";

    @Value("${rag.mcp.builtin-tools.paper-search.preferred-source:semantic-scholar}")
    private String preferredSource;

    private final Gson gson = new Gson();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build();

    @Override
    public MCPToolDefinition getToolDefinition() {
        Map<String, MCPToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();
        parameters.put("query", MCPToolDefinition.ParameterDef.builder()
                .description("Paper search query")
                .type("string")
                .required(true)
                .build());
        parameters.put("limit", MCPToolDefinition.ParameterDef.builder()
                .description("Number of papers to return, default 5 and max 10")
                .type("number")
                .defaultValue(5)
                .build());
        parameters.put("year_start", MCPToolDefinition.ParameterDef.builder()
                .description("Optional earliest publication year")
                .type("number")
                .build());
        parameters.put("sort_by", MCPToolDefinition.ParameterDef.builder()
                .description("Sort order")
                .type("string")
                .defaultValue("relevance")
                .enumValues(List.of("relevance", "citations", "date"))
                .build());
        return MCPToolDefinition.builder()
                .toolId(TOOL_ID)
                .description("Search academic papers from Semantic Scholar for AI and ML research references.")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPToolResponse execute(MCPToolRequest request) {
        String query = request.getStringParameter("query");
        Number limitValue = request.getParameter("limit");
        Number yearStart = request.getParameter("year_start");
        String sortBy = request.getStringParameter("sort_by");
        int limit = limitValue != null ? Math.min(limitValue.intValue(), 10) : 5;
        if (query == null || query.isBlank()) {
            return MCPToolResponse.error(TOOL_ID, "INVALID_PARAMS", "query is required");
        }
        try {
            return MCPToolResponse.success(TOOL_ID, searchSemanticScholar(query, limit,
                    yearStart != null ? yearStart.intValue() : null,
                    sortBy != null ? sortBy : "relevance"));
        } catch (Exception e) {
            log.warn("Paper search failed, source={}, query={}, reason={}", preferredSource, query, e.getMessage());
            return MCPToolResponse.error(TOOL_ID, "SEARCH_ERROR", e.getMessage());
        }
    }

    private String searchSemanticScholar(String query, int limit, Integer yearStart, String sortBy) throws IOException {
        StringBuilder url = new StringBuilder("https://api.semanticscholar.org/graph/v1/paper/search");
        url.append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        url.append("&limit=").append(limit);
        url.append("&fields=title,authors,year,abstract,citationCount,url,publicationVenue,openAccessPdf");
        if (yearStart != null) {
            url.append("&year=").append(yearStart).append("-");
        }
        if ("citations".equals(sortBy)) {
            url.append("&sort=citationCount:desc");
        } else if ("date".equals(sortBy)) {
            url.append("&sort=publicationDate:desc");
        }

        Request request = new Request.Builder().url(url.toString()).build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return formatResults(response.body() != null ? response.body().string() : "");
        }
    }

    private String formatResults(String json) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        StringBuilder result = new StringBuilder("## Academic Paper Results\n\n");
        result.append("*Source: Semantic Scholar*\n\n");
        if (root == null || !root.has("data")) {
            return result.append("No papers found.\n").toString();
        }
        var papers = root.getAsJsonArray("data");
        for (int i = 0; i < papers.size(); i++) {
            var paper = papers.get(i).getAsJsonObject();
            String title = value(paper, "title", "Untitled");
            String year = value(paper, "year", "unknown");
            String citations = value(paper, "citationCount", "0");
            String url = value(paper, "url", "");
            String abstractText = value(paper, "abstract", "");
            if (abstractText.length() > 300) {
                abstractText = abstractText.substring(0, 300) + "...";
            }
            result.append("### ").append(i + 1).append(". ").append(title).append('\n');
            result.append("- Year: ").append(year).append(" | Citations: ").append(citations).append('\n');
            if (!url.isBlank()) {
                result.append("- URL: ").append(url).append('\n');
            }
            if (!abstractText.isBlank()) {
                result.append("- Abstract: ").append(abstractText).append('\n');
            }
            result.append('\n');
        }
        return result.toString();
    }

    private String value(JsonObject object, String key, String defaultValue) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : defaultValue;
    }
}
