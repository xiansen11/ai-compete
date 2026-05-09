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
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.xml.sax.InputSource;

@Slf4j
@Component
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
    public MCPTool getToolDefinition() {
        Map<String, MCPTool.ParameterDef> parameters = new HashMap<>();
        parameters.put("query", MCPTool.ParameterDef.builder()
                .description("论文搜索关键词（支持英文效果最佳）")
                .type("string")
                .required(true)
                .build());
        parameters.put("field", MCPTool.ParameterDef.builder()
                .description("搜索字段：title(标题)/abstract(摘要)/all(全部，默认)")
                .type("string")
                .required(false)
                .defaultValue("all")
                .enumValues(List.of("title", "abstract", "all"))
                .build());
        parameters.put("limit", MCPTool.ParameterDef.builder()
                .description("返回论文数量（默认5，最大10）")
                .type("number")
                .required(false)
                .defaultValue(5)
                .build());
        parameters.put("year_start", MCPTool.ParameterDef.builder()
                .description("起始年份（可选，用于过滤较新的论文）")
                .type("number")
                .required(false)
                .build());
        parameters.put("sort_by", MCPTool.ParameterDef.builder()
                .description("排序方式：relevance(相关性默认)/citations(引用数)/date(日期)")
                .type("string")
                .required(false)
                .defaultValue("relevance")
                .enumValues(List.of("relevance", "citations", "date"))
                .build());

        return MCPTool.builder()
                .toolId(TOOL_ID)
                .description("在arXiv、Semantic Scholar等学术平台搜索AI/ML领域的学术论文。适用于查找最新研究成果、经典论文、特定方法的原始论文等。")
                .parameters(parameters)
                .requireUserId(false)
                .build();
    }

    @Override
    public MCPResponse execute(MCPRequest request) {
        Map<String, Object> params = request.getParameters();
        String query = (String) params.get("query");
        String field = (String) params.getOrDefault("field", "all");
        int limit = params.get("limit") != null ? ((Number) params.get("limit")).intValue() : 5;
        Integer yearStart = params.get("year_start") != null ? ((Number) params.get("year_start")).intValue() : null;
        String sortBy = (String) params.getOrDefault("sort_by", "relevance");

        log.info("执行论文搜索 - 查询: {}, 字段: {}, 数量: {}", query, field, limit);

        try {
            String results;
            switch (preferredSource.toLowerCase()) {
                case "arxiv":
                    results = searchArXiv(query, limit);
                    break;
                case "crossref":
                    results = searchCrossRef(query, field, limit, yearStart, sortBy);
                    break;
                default:
                    results = searchSemanticScholar(query, field, limit, yearStart, sortBy);
                    break;
            }

            return MCPResponse.success(TOOL_ID, results);

        } catch (Exception e) {
            log.error("论文搜索执行失败", e);
            return MCPResponse.error(TOOL_ID, "SEARCH_ERROR", "论文搜索服务暂不可用: " + e.getMessage());
        }
    }

    private String searchSemanticScholar(String query, String field, int limit, Integer yearStart, String sortBy) throws IOException {
        String baseUrl = "https://api.semanticscholar.org/graph/v1/paper/search";

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        urlBuilder.append("&limit=").append(Math.min(limit, 10));
        urlBuilder.append("&fields=title,authors,year,abstract,citationCount,url,publicationVenue,openAccessPdf");

        if (yearStart != null) {
            urlBuilder.append("&year=").append(yearStart).append("-2030");
        }

        if ("citations".equals(sortBy)) {
            urlBuilder.append("&sort=citationCount:desc");
        } else if ("date".equals(sortBy)) {
            urlBuilder.append("&sort=publicationDate:desc");
        }

        Request httpRequest = new Request.Builder().url(urlBuilder.toString()).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Semantic Scholar API请求失败: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            return formatSemanticScholarResults(body);
        }
    }

    private String formatSemanticScholarResults(String json) {
        StringBuilder result = new StringBuilder();
        result.append("## 学术论文搜索结果\n\n");
        result.append("*数据来源: Semantic Scholar*\n\n");

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("data")) {
                var papers = root.getAsJsonArray("data");
                int total = root.has("total") ? root.get("total").getAsInt() : papers.size();

                result.append(String.format("**共找到 %d 篇相关论文**\n\n", total));

                for (int i = 0; i < papers.size(); i++) {
                    var paper = papers.get(i).getAsJsonObject();

                    String title = paper.has("title") ? paper.get("title").getAsString() : "无标题";
                    int year = paper.has("year") && !paper.get("year").isJsonNull() ? paper.get("year").getAsInt() : 0;
                    int citations = paper.has("citationCount") ? paper.get("citationCount").getAsInt() : 0;
                    String url = paper.has("url") ? paper.get("url").getAsString() : "";
                    String abstractText = paper.has("abstract") && !paper.get("abstract").isJsonNull() ? paper.get("abstract").getAsString() : "无摘要";

                    result.append(String.format("### %d. %s\n", i + 1, title));
                    result.append(String.format("- **年份**: %d | **引用次数**: %d\n", year, citations));
                    result.append(String.format("- [查看详情](%s)\n", url));

                    if (paper.has("openAccessPdf") && !paper.get("openAccessPdf").isJsonNull()) {
                        var pdf = paper.getAsJsonObject("openAccessPdf");
                        if (pdf.has("url")) {
                            result.append(String.format("- [PDF下载](%s)\n", pdf.get("url").getAsString()));
                        }
                    }

                    result.append(String.format("\n**摘要**: %s\n\n", abstractText.length() > 300 ?
                            abstractText.substring(0, 300) + "..." : abstractText));

                    if (paper.has("authors")) {
                        var authors = paper.getAsJsonArray("authors");
                        if (authors.size() > 0) {
                            result.append("**作者**: ");
                            StringBuilder authorList = new StringBuilder();
                            for (int j = 0; j < Math.min(authors.size(), 5); j++) {
                                var author = authors.get(j).getAsJsonObject();
                                String authorName = author.has("name") ? author.get("name").getAsString() : "";
                                authorList.append(authorName);
                                if (j < Math.min(authors.size(), 5) - 1) {
                                    authorList.append(", ");
                                }
                            }
                            if (authors.size() > 5) {
                                authorList.append(" 等");
                            }
                            result.append(authorList).append("\n\n");
                        }
                    }

                    result.append("---\n\n");
                }
            } else if (root.has("error")) {
                result.append(String.format("搜索出错: %s\n", root.get("error").getAsString()));
            } else {
                result.append("*未找到相关论文*\n");
            }

        } catch (Exception e) {
            log.warn("格式化Semantic Scholar结果失败", e);
            result.append("（解析搜索结果时出现错误）\n");
        }

        return result.toString();
    }

    private String searchArXiv(String query, int limit) throws IOException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = String.format(
                "http://export.arxiv.org/api/query?search_query=all:%s&start=0&max_results=%d&sortBy=relevance&sortOrder=descending",
                encodedQuery,
                Math.min(limit, 10)
        );

        Request httpRequest = new Request.Builder().url(url).build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("arXiv API请求失败: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            return formatArXivResults(body);
        }
    }

    private String formatArXivResults(String xml) {
        StringBuilder result = new StringBuilder();
        result.append("## arXiv论文搜索结果\n\n");

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));

            NodeList entries = doc.getElementsByTagName("entry");
            result.append(String.format("**共找到 %d 篇论文**\n\n", entries.getLength()));

            for (int i = 0; i < entries.getLength(); i++) {
                Node entry = entries.item(i);
                if (entry.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) entry;

                    String title = getTextContent(element, "title");
                    String summary = getTextContent(element, "summary");
                    String published = getTextContent(element, "published");
                    String id = getTextContent(element, "id");

                    result.append(String.format("### %d. %s\n", i + 1, title.trim()));
                    result.append(String.format("- **发布时间**: %s\n", published.length() >= 10 ? published.substring(0, 10) : published));

                    if (id.contains("arxiv.org/abs/")) {
                        String arxivId = id.replace("http://arxiv.org/abs/", "");
                        result.append(String.format("- [arXiv](%s) | [PDF](https://arxiv.org/pdf/%s)\n", id, arxivId));
                    }

                    result.append(String.format("\n**摘要**: %s\n\n", summary.length() > 300 ?
                            summary.substring(0, 300) + "..." : summary));

                    result.append("---\n\n");
                }
            }

        } catch (Exception e) {
            log.warn("解析arXiv XML失败", e);
            result.append("（解析arXiv结果时出错）\n");
        }

        return result.toString();
    }

    private String searchCrossRef(String query, String field, int limit, Integer yearStart, String sortBy) throws IOException {
        String baseUrl = "https://api.crossref.org/works";

        StringBuilder urlBuilder = new StringBuilder(baseUrl);
        urlBuilder.append("?query=").append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        urlBuilder.append("&rows=").append(Math.min(limit, 10));
        urlBuilder.append("&select=DOI,title,author,published-print,container-title,abstract,link");

        if (yearStart != null) {
            urlBuilder.append("&filter=from-pub-date:").append(yearStart);
        }

        if ("citations".equals(sortBy)) {
            urlBuilder.append("&order=is-referenced-by-count");
        } else if ("date".equals(sortBy)) {
            urlBuilder.append("&order=published");
        }

        urlBuilder.append("&sort=relevance");

        Request httpRequest = new Request.Builder()
                .url(urlBuilder.toString())
                .header("User-Agent", "AI-Compete/1.0 (mailto:support@aicompete.example.com)")
                .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("CrossRef API请求失败: " + response.code());
            }

            String body = response.body() != null ? response.body().string() : "";
            return formatCrossRefResults(body);
        }
    }

    private String formatCrossRefResults(String json) {
        StringBuilder result = new StringBuilder();
        result.append("## CrossRef学术搜索结果\n\n");
        result.append("*数据来源: CrossRef*\n\n");

        try {
            JsonObject root = gson.fromJson(json, JsonObject.class);

            if (root.has("message")) {
                var message = root.getAsJsonObject("message");
                var items = message.getAsJsonArray("items");
                int total = message.has("total-results") ? message.get("total-results").getAsInt() : items.size();

                result.append(String.format("**共找到 %d 条记录**\n\n", total));

                for (int i = 0; i < items.size(); i++) {
                    var item = items.get(i).getAsJsonObject();

                    String title = "无标题";
                    if (item.has("title") && item.getAsJsonArray("title").size() > 0) {
                        title = item.getAsJsonArray("title").get(0).getAsString();
                    }

                    String doi = item.has("DOI") ? item.get("DOI").getAsString() : "";
                    String venue = "";
                    if (item.has("container-title") && item.getAsJsonArray("container-title").size() > 0) {
                        venue = item.getAsJsonArray("container-title").get(0).getAsString();
                    }

                    result.append(String.format("### %d. %s\n", i + 1, title));
                    if (!venue.isBlank()) {
                        result.append(String.format("- **期刊/会议**: %s\n", venue));
                    }
                    if (!doi.isBlank()) {
                        result.append(String.format("- **DOI**: %s\n", doi));
                        result.append(String.format("- [查看详情](https://doi.org/%s)\n", doi));
                    }

                    result.append("\n---\n\n");
                }
            }

        } catch (Exception e) {
            log.warn("格式化CrossRef结果失败", e);
            result.append("（解析CrossRef结果时出现错误）\n");
        }

        return result.toString();
    }

    private String getTextContent(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : "";
    }
}
