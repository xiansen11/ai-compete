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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class GitSourceCodeTool implements MCPToolExecutor {

    private static final String TOOL_ID = "git_source_parse";

    @Value("${rag.mcp.builtin-tools.git-source-parse.github-token:}")
    private String githubToken;

    @Value("${rag.mcp.builtin-tools.git-source-parse.max-file-size:1048576}")
    private long maxFileSize;

    private final Gson gson = new Gson();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private static final Pattern GITHUB_URL_PATTERN =
            Pattern.compile("^https?://(?:www\\.)?github\\.com/([^/]+)/([^/]+)(?:/(.*))?$");

    private static final Pattern GITEE_URL_PATTERN =
            Pattern.compile("^https?://(?:www\\.)?gitee\\.com/([^/]+)/([^/]+)(?:/(.*))?$");

    @Override
    public MCPTool getToolDefinition() {
        Map<String, MCPTool.ParameterDef> parameters = new HashMap<>();
        parameters.put("repo_url", MCPTool.ParameterDef.builder()
                .description("代码仓库URL（支持GitHub和Gitee）")
                .type("string")
                .required(true)
                .build());
        parameters.put("file_path", MCPTool.ParameterDef.builder()
                .description("要分析的文件路径（可选，不指定则返回仓库结构概览）")
                .type("string")
                .required(false)
                .build());
        parameters.put("analysis_type", MCPTool.ParameterDef.builder()
                .description("分析类型：structure(结构概览)/code(代码内容)/dependencies(依赖关系)")
                .type("string")
                .required(false)
                .defaultValue("structure")
                .enumValues(List.of("structure", "code", "dependencies"))
                .build());

        return MCPTool.builder()
                .toolId(TOOL_ID)
                .description("解析GitHub/Gitee等平台的公开源码仓库，提取代码结构、函数定义、类依赖关系等信息。适用于学习优秀开源项目的架构设计、理解第三方库的实现细节。")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPResponse execute(MCPRequest request) {
        Map<String, Object> params = request.getParameters();
        String repoUrl = (String) params.get("repo_url");
        String filePath = (String) params.get("file_path");
        String analysisType = (String) params.getOrDefault("analysis_type", "structure");

        log.info("执行Git源码解析 - 仓库: {}, 文件: {}, 类型: {}", repoUrl, filePath, analysisType);

        try {
            String result;

            Matcher githubMatcher = GITHUB_URL_PATTERN.matcher(repoUrl);
            Matcher giteeMatcher = GITEE_URL_PATTERN.matcher(repoUrl);

            if (githubMatcher.matches()) {
                String owner = githubMatcher.group(1);
                String repo = githubMatcher.group(2);
                result = parseGitHubRepo(owner, repo, filePath, analysisType);
            } else if (giteeMatcher.matches()) {
                String owner = giteeMatcher.group(1);
                String repo = giteeMatcher.group(2);
                result = parseGiteeRepo(owner, repo, filePath, analysisType);
            } else {
                return MCPResponse.error(TOOL_ID, "UNSUPPORTED_PLATFORM", "不支持的代码托管平台，目前仅支持GitHub和Gitee");
            }

            return MCPResponse.success(TOOL_ID, result);

        } catch (Exception e) {
            log.error("Git源码解析执行失败", e);
            return MCPResponse.error(TOOL_ID, "PARSE_ERROR", "源码解析失败: " + e.getMessage());
        }
    }

    private String parseGitHubRepo(String owner, String repo, String path, String analysisType) throws IOException {
        String baseUrl = "https://api.github.com";

        if (path == null || path.isBlank()) {
            return fetchRepoStructure(baseUrl, owner, repo);
        }

        switch (analysisType.toLowerCase()) {
            case "code":
                return fetchFileContent(baseUrl, owner, repo, path);
            case "dependencies":
                return analyzeDependencies(baseUrl, owner, repo, path);
            default:
                if (path.endsWith(".py") || path.endsWith(".java") || path.endsWith(".js")) {
                    return analyzeCodeStructure(baseUrl, owner, repo, path);
                } else {
                    return fetchFileContent(baseUrl, owner, repo, path);
                }
        }
    }

    private String fetchRepoStructure(String baseUrl, String owner, String repo) throws IOException {
        String url = String.format("%s/repos/%s/%s/git/trees/main?recursive=1", baseUrl, owner, repo);

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (githubToken != null && !githubToken.isBlank()) {
            requestBuilder.header("Authorization", "token " + githubToken);
        }

        Response response = httpClient.newCall(requestBuilder.build()).execute();
        try {
            if (!response.isSuccessful()) {
                if (response.code() == 404) {
                    url = String.format("%s/repos/%s/%s/git/trees/master?recursive=1", baseUrl, owner, repo);
                    Request.Builder retryBuilder = new Request.Builder().url(url);
                    if (githubToken != null && !githubToken.isBlank()) {
                        retryBuilder.header("Authorization", "token " + githubToken);
                    }
                    response.close();
                    response = httpClient.newCall(retryBuilder.build()).execute();
                }

                if (!response.isSuccessful()) {
                    throw new IOException("获取仓库结构失败: HTTP " + response.code());
                }
            }

            String body = response.body() != null ? response.body().string() : "";
            return formatRepositoryTree(body, owner, repo);
        } finally {
            response.close();
        }
    }

    private String formatRepositoryTree(String json, String owner, String repo) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("# 仓库结构: %s/%s\n\n", owner, repo));

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("tree")) {
                var tree = root.getAsJsonArray("tree");
                Map<String, List<String>> dirContents = new LinkedHashMap<>();

                for (var item : tree) {
                    var node = item.getAsJsonObject();
                    String path = node.has("path") ? node.get("path").getAsString() : "";
                    String type = node.has("type") ? node.get("type").getAsString() : "";

                    if ("blob".equals(type)) {
                        String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/')) : ".";
                        dirContents.computeIfAbsent(dir, k -> new ArrayList<>()).add(path);
                    }
                }

                for (var entry : dirContents.entrySet()) {
                    result.append(String.format("## %s\n\n", ".".equals(entry.getKey()) ? "(根目录)" : entry.getKey()));
                    for (String file : entry.getValue()) {
                        result.append(String.format("- `%s`\n", file));
                    }
                    result.append("\n");
                }

                result.append(String.format("\n**总计**: %d 个文件\n", tree.size()));
            }

            result.append(String.format("\n[访问GitHub页面](https://github.com/%s/%s)\n", owner, repo));

        } catch (Exception e) {
            log.warn("格式化仓库树失败", e);
            result.append("（格式化仓库结构时出错）\n");
        }

        return result.toString();
    }

    private String fetchFileContent(String baseUrl, String owner, String repo, String path) throws IOException {
        String encodedPath = URLEncoder.encode(path, StandardCharsets.UTF_8);
        String url = String.format("%s/repos/%s/%s/contents/%s", baseUrl, owner, repo, encodedPath);

        Request.Builder requestBuilder = new Request.Builder().url(url);
        if (githubToken != null && !githubToken.isBlank()) {
            requestBuilder.header("Authorization", "token " + githubToken);
        }

        try (Response response = httpClient.newCall(requestBuilder.build()).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取文件内容失败: HTTP " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            return formatFileContent(body, path);
        }
    }

    private String formatFileContent(String json, String filePath) {
        StringBuilder result = new StringBuilder();
        result.append(String.format("# 文件: `%s`\n\n", filePath));

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("content")) {
                String content = root.get("content").getAsString();
                String decoded = new String(Base64.getDecoder().decode(content));

                long size = root.has("size") ? root.get("size").getAsLong() : 0;
                result.append(String.format("**文件大小**: %d bytes\n\n", size));

                if (size > maxFileSize) {
                    result.append(String.format("> 文件过大，仅显示前%d字符:\n\n", (int) maxFileSize));
                    result.append("```\n");
                    result.append(decoded.substring(0, (int) maxFileSize));
                    result.append("\n```\n");
                } else {
                    String lang = detectLanguage(filePath);
                    result.append(String.format("```%s\n", lang));
                    result.append(decoded);
                    result.append("\n```\n");
                }
            }

        } catch (Exception e) {
            log.warn("格式化文件内容失败", e);
            result.append("（读取文件内容时出错）\n");
        }

        return result.toString();
    }

    private String analyzeCodeStructure(String baseUrl, String owner, String repo, String path) throws IOException {
        String content = fetchFileContent(baseUrl, owner, repo, path);
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("# 代码结构分析: `%s`\n\n", path));

        analysis.append("## 函数/方法定义\n\n");

        Pattern funcPattern = Pattern.compile(
                "(?:def|function|func|public|private|protected)\\s+(\\w+)\\s*\\([^)]*\\)"
        );

        Matcher matcher = funcPattern.matcher(content);
        while (matcher.find()) {
            analysis.append(String.format("- **%s**()\n", matcher.group(1)));
        }

        analysis.append("\n## 类定义\n\n");
        Pattern classPattern = Pattern.compile("(?:class|struct)\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        while (classMatcher.find()) {
            analysis.append(String.format("- **%s**\n", classMatcher.group(1)));
        }

        analysis.append("\n## 导入依赖\n\n");
        Pattern importPattern = Pattern.compile("^(?:import|from|require|using)\\s+(.+)$", Pattern.MULTILINE);
        Matcher importMatcher = importPattern.matcher(content);
        while (importMatcher.find()) {
            analysis.append(String.format("- `%s`\n", importMatcher.group(1).trim()));
        }

        analysis.append("\n---\n");
        analysis.append("*完整代码内容请使用 analysis_type=code 获取*\n");

        return analysis.toString();
    }

    private String analyzeDependencies(String baseUrl, String owner, String repo, String path) throws IOException {
        StringBuilder deps = new StringBuilder();
        deps.append(String.format("# 依赖分析: `%s`\n\n", path));

        String[] filesToCheck = {"requirements.txt", "package.json", "pom.xml", "build.gradle", "Cargo.toml", "go.mod"};

        for (String file : filesToCheck) {
            try {
                String content = fetchFileContent(baseUrl, owner, repo, file);
                deps.append(String.format("## %s\n\n%s\n---\n\n", file, content));
            } catch (IOException e) {
                log.debug("未找到依赖文件: {}", file);
            }
        }

        if (deps.toString().split("---").length <= 1) {
            deps.append("*未找到标准的依赖声明文件*\n");
        }

        return deps.toString();
    }

    private String parseGiteeRepo(String owner, String repo, String path, String analysisType) {
        return String.format(
                "# Gitee仓库解析\n\n**仓库**: %s/%s\n**路径**: %s\n**类型**: %s\n\n> Gitee API功能有限，建议使用GitHub链接获取更完整的分析结果。\n\n## 建议操作\n1. 访问 https://gitee.com/%s/%s 查看仓库详情\n2. 如需详细代码分析，可提供GitHub镜像地址\n",
                owner, repo, path, analysisType, owner, repo);
    }

    private String detectLanguage(String filePath) {
        String ext = filePath.contains(".") ? filePath.substring(filePath.lastIndexOf('.')) : "";
        switch (ext.toLowerCase()) {
            case ".py": return "python";
            case ".java": return "java";
            case ".js": return "javascript";
            case ".ts": return "typescript";
            case ".cpp": case ".c": case ".cc": return "cpp";
            case ".h": case ".hpp": return "c";
            case ".rs": return "rust";
            case ".go": return "go";
            case ".rb": return "ruby";
            case ".php": return "php";
            case ".swift": return "swift";
            case ".kt": return "kotlin";
            case ".scala": return "scala";
            case ".r": return "r";
            case ".m": return "objectivec";
            case ".sh": case ".bash": return "bash";
            case ".sql": return "sql";
            case ".html": return "html";
            case ".css": return "css";
            case ".json": return "json";
            case ".xml": return "xml";
            case ".yaml": case ".yml": return "yaml";
            case ".md": return "markdown";
            default: return "text";
        }
    }
}
