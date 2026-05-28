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
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "rag.mcp.builtin-tools.git-source-parse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GitSourceCodeTool implements MCPToolExecutor {

    private static final String TOOL_ID = "git_source_parse";
    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)(?:/(.*))?$");

    @Value("${rag.mcp.builtin-tools.git-source-parse.github-token:}")
    private String githubToken;

    @Value("${rag.mcp.builtin-tools.git-source-parse.max-file-size:1048576}")
    private long maxFileSize;

    private final Gson gson = new Gson();
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    @Override
    public MCPToolDefinition getToolDefinition() {
        Map<String, MCPToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();
        parameters.put("repo_url", MCPToolDefinition.ParameterDef.builder()
                .description("GitHub repository URL")
                .type("string")
                .required(true)
                .build());
        parameters.put("file_path", MCPToolDefinition.ParameterDef.builder()
                .description("Optional file path. If omitted, returns repository tree overview.")
                .type("string")
                .build());
        parameters.put("analysis_type", MCPToolDefinition.ParameterDef.builder()
                .description("Analysis type")
                .type("string")
                .defaultValue("structure")
                .enumValues(List.of("structure", "code", "dependencies"))
                .build());
        return MCPToolDefinition.builder()
                .toolId(TOOL_ID)
                .description("Parse GitHub source repositories and fetch repository structure or source file content.")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPToolResponse execute(MCPToolRequest request) {
        String repoUrl = request.getStringParameter("repo_url");
        String filePath = request.getStringParameter("file_path");
        if (repoUrl == null || repoUrl.isBlank()) {
            return MCPToolResponse.error(TOOL_ID, "INVALID_PARAMS", "repo_url is required");
        }
        var matcher = GITHUB_URL_PATTERN.matcher(repoUrl);
        if (!matcher.matches()) {
            return MCPToolResponse.error(TOOL_ID, "UNSUPPORTED_PLATFORM", "Only GitHub repository URLs are supported");
        }

        try {
            String owner = matcher.group(1);
            String repo = matcher.group(2).replaceAll("\\.git$", "");
            String result = filePath == null || filePath.isBlank()
                    ? fetchRepositoryTree(owner, repo)
                    : fetchFile(owner, repo, filePath);
            return MCPToolResponse.success(TOOL_ID, result);
        } catch (Exception e) {
            log.warn("Git source parse failed, repo={}, reason={}", repoUrl, e.getMessage());
            return MCPToolResponse.error(TOOL_ID, "PARSE_ERROR", e.getMessage());
        }
    }

    private String fetchRepositoryTree(String owner, String repo) throws IOException {
        IOException lastError = null;
        for (String branch : List.of("main", "master")) {
            try {
                String url = "https://api.github.com/repos/%s/%s/git/trees/%s?recursive=1".formatted(owner, repo, branch);
                return formatRepositoryTree(fetch(url), owner, repo, branch);
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw lastError != null ? lastError : new IOException("Repository tree not found");
    }

    private String fetchFile(String owner, String repo, String path) throws IOException {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8).replace("+", "%20");
        String url = "https://api.github.com/repos/%s/%s/contents/%s".formatted(owner, repo, encodedPath);
        JsonObject root = gson.fromJson(fetch(url), JsonObject.class);
        if (root == null || !root.has("content")) {
            throw new IOException("File content is empty");
        }

        long size = root.has("size") ? root.get("size").getAsLong() : 0L;
        String decoded = new String(Base64.getMimeDecoder().decode(root.get("content").getAsString()), StandardCharsets.UTF_8);
        if (size > maxFileSize) {
            decoded = decoded.substring(0, Math.min(decoded.length(), (int) maxFileSize));
        }

        return "# File: `%s`\n\nSize: %d bytes\n\n```%s\n%s\n```"
                .formatted(path, size, detectLanguage(path), decoded);
    }

    private String fetch(String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        if (githubToken != null && !githubToken.isBlank()) {
            builder.header("Authorization", "Bearer " + githubToken);
        }
        try (Response response = httpClient.newCall(builder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    private String formatRepositoryTree(String json, String owner, String repo, String branch) {
        JsonObject root = gson.fromJson(json, JsonObject.class);
        StringBuilder result = new StringBuilder("# Repository: %s/%s\n\nBranch: %s\n\n".formatted(owner, repo, branch));
        if (root != null && root.has("tree")) {
            var tree = root.getAsJsonArray("tree");
            int shown = 0;
            for (var item : tree) {
                if (shown >= 200) {
                    result.append("\n... truncated after 200 entries\n");
                    break;
                }
                var node = item.getAsJsonObject();
                result.append("- ").append(node.get("type").getAsString()).append(": `")
                        .append(node.get("path").getAsString()).append("`\n");
                shown++;
            }
            result.append("\nTotal entries: ").append(tree.size()).append('\n');
        }
        result.append("\nhttps://github.com/").append(owner).append('/').append(repo).append('\n');
        return result.toString();
    }

    private String detectLanguage(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".java")) return "java";
        if (lower.endsWith(".py")) return "python";
        if (lower.endsWith(".js")) return "javascript";
        if (lower.endsWith(".ts")) return "typescript";
        if (lower.endsWith(".json")) return "json";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "yaml";
        if (lower.endsWith(".md")) return "markdown";
        return "text";
    }
}
