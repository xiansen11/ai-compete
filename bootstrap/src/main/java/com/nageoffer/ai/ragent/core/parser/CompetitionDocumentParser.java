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

package com.nageoffer.ai.ragent.core.parser;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CompetitionDocumentParser implements DocumentParser {

    private static final String PARSER_TYPE = "COMPETITION";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".pdf", ".md", ".docx", ".xlsx", ".json", ".yaml", ".yml"
    );

    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile("```(\\w*)\\n([\\s\\S]*?)```");
    private static final Pattern MATH_FORMULA_PATTERN = Pattern.compile("\\$\\$([\\s\\S]*?)\\$\\$|\\$([^$]+)\\$");

    private final Tika tika = new Tika();

    @Override
    public String getParserType() {
        return PARSER_TYPE;
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        try {
            String text = extractText(new ByteArrayInputStream(content), "competition_document");

            Map<String, Object> metadata = extractCompetitionMetadata(text);
            List<String> codeBlocks = extractCodeBlocks(text);
            List<String> mathFormulas = extractMathFormulas(text);

            if (!codeBlocks.isEmpty()) {
                metadata.put("code_blocks", codeBlocks);
                metadata.put("has_code_blocks", true);
            }

            if (!mathFormulas.isEmpty()) {
                metadata.put("math_formulas", mathFormulas);
                metadata.put("has_math_formulas", true);
            }

            log.info("竞赛文档解析完成 - 文本长度: {}, 代码块数: {}, 公式数: {}",
                    text.length(), codeBlocks.size(), mathFormulas.size());

            return ParseResult.of(text, metadata);

        } catch (Exception e) {
            log.error("竞赛文档解析失败", e);
            throw new RuntimeException("文档解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        try {
            AutoDetectParser parser = new AutoDetectParser();
            BodyContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();

            if (fileName != null) {
                metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
            }

            parser.parse(stream, handler, metadata);

            String text = handler.toString();

            text = postProcessText(text);

            return text;

        } catch (IOException e) {
            log.error("读取文档流失败", e);
            throw new RuntimeException("文档读取失败", e);
        } catch (Exception e) {
            log.error("使用Tika解析文档失败", e);
            throw new RuntimeException("Tika解析异常: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (mimeType == null) {
            return false;
        }

        return switch (mimeType.toLowerCase()) {
            case "application/pdf",
                 "text/markdown",
                 "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                 "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                 "application/json",
                 "application/x-yaml",
                 "text/yaml" -> true;
            default -> false;
        };
    }

    private String postProcessText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        text = text.replaceAll("[\\r\\n]{3,}", "\n\n");
        text = text.replaceAll("\\s{2,}", " ");
        text = text.trim();

        return text;
    }

    private Map<String, Object> extractCompetitionMetadata(String text) {
        Map<String, Object> metadata = new HashMap<>();

        metadata.put("document_type", detectDocumentType(text));
        metadata.put("estimated_difficulty", estimateDifficulty(text));
        metadata.put("detected_categories", detectCategories(text));
        metadata.put("text_statistics", calculateTextStatistics(text));

        return metadata;
    }

    private String detectDocumentType(String text) {
        String lowerText = text.toLowerCase();

        if (lowerText.contains("赛题") || lowerText.contains("题目描述") || lowerText.contains("problem statement")) {
            return "PROBLEM_STATEMENT";
        } else if (lowerText.contains("评分标准") || lowerText.contains("scoring criteria") || lowerText.contains("evaluation")) {
            return "SCORING_GUIDE";
        } else if (lowerText.contains("样例输入") || lowerText.contains("sample input") || lowerText.contains("示例")) {
            return "SAMPLE_DATA";
        } else if (lowerText.contains("算法") || lowerText.contains("algorithm") || lowerText.contains("模型")) {
            return "ALGORITHM_GUIDE";
        } else if (lowerText.contains("数据集") || lowerText.contains("dataset") || lowerText.contains("数据说明")) {
            return "DATASET_DESCRIPTION";
        } else {
            return "GENERAL";
        }
    }

    private String estimateDifficulty(String text) {
        String lowerText = text.toLowerCase();

        int complexityScore = 0;

        if (lowerText.contains("深度学习") || lowerText.contains("deep learning") || lowerText.contains("transformer")) {
            complexityScore += 3;
        }
        if (lowerText.contains("强化学习") || lowerText.contains("reinforcement learning")) {
            complexityScore += 3;
        }
        if (lowerText.contains("图神经网络") || lowerText.contains("gnn") || lowerText.contains("graph neural")) {
            complexityScore += 2;
        }
        if (lowerText.contains("多模态") || lowerText.contains("multimodal")) {
            complexityScore += 2;
        }
        if (lowerText.contains("时间序列") || lowerText.contains("time series")) {
            complexityScore += 1;
        }
        if (lowerText.contains("机器学习") || lowerText.contains("machine learning")) {
            complexityScore += 1;
        }

        if (complexityScore >= 5) {
            return "HARD";
        } else if (complexityScore >= 2) {
            return "MEDIUM";
        } else {
            return "EASY";
        }
    }

    private List<String> detectCategories(String text) {
        String lowerText = text.toLowerCase();
        List<String> categories = new ArrayList<>();

        if (lowerText.contains("自然语言处理") || lowerText.contains("nlp") || lowerText.contains("文本分类")) {
            categories.add("NLP");
        }
        if (lowerText.contains("计算机视觉") || lowerText.contains("cv") || lowerText.contains("图像")) {
            categories.add("CV");
        }
        if (lowerText.contains("推荐系统") || lowerText.contains("recommendation")) {
            categories.add("RECOMMENDATION");
        }
        if (lowerText.contains("语音") || lowerText.contains("speech") || lowerText.contains("audio")) {
            categories.add("SPEECH");
        }
        if (lowerText.contains("多模态") || lowerText.contains("multimodal")) {
            categories.add("MULTIMODAL");
        }

        if (categories.isEmpty()) {
            categories.add("GENERAL");
        }

        return categories;
    }

    private Map<String, Object> calculateTextStatistics(String text) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("char_count", text.length());
        stats.put("word_count", text.split("\\s+").length);
        stats.put("line_count", text.split("\n").length);

        long codeBlockCount = CODE_BLOCK_PATTERN.matcher(text).results().count();
        stats.put("code_block_count", codeBlockCount);

        long formulaCount = MATH_FORMULA_PATTERN.matcher(text).results().count();
        stats.put("formula_count", formulaCount);

        return stats;
    }

    private List<String> extractCodeBlocks(String text) {
        List<String> codeBlocks = new ArrayList<>();
        Matcher matcher = CODE_BLOCK_PATTERN.matcher(text);

        while (matcher.find()) {
            String language = matcher.group(1);
            String code = matcher.group(2).trim();

            if (code.length() > 10) {
                codeBlocks.add(code);
            }
        }

        return codeBlocks;
    }

    private List<String> extractMathFormulas(String text) {
        List<String> formulas = new ArrayList<>();
        Matcher matcher = MATH_FORMULA_PATTERN.matcher(text);

        while (matcher.find()) {
            String formula = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            formulas.add(formula.trim());
        }

        return formulas;
    }
}
