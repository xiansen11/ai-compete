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

package com.nageoffer.ai.ragent.knowledge.service.impl;

import cn.hutool.core.util.StrUtil;
import com.nageoffer.ai.ragent.core.parser.DocumentParser;
import com.nageoffer.ai.ragent.core.parser.DocumentParserSelector;
import com.nageoffer.ai.ragent.core.parser.ParserType;
import com.nageoffer.ai.ragent.knowledge.controller.request.KnowledgeDocumentUploadRequest;
import com.nageoffer.ai.ragent.knowledge.dao.entity.KnowledgeBaseDO;
import com.nageoffer.ai.ragent.knowledge.enums.KnowledgeBaseType;
import com.nageoffer.ai.ragent.knowledge.service.KnowledgeDocumentRoutingService;
import com.nageoffer.ai.ragent.rag.util.FileTypeDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class HeuristicKnowledgeDocumentRoutingService implements KnowledgeDocumentRoutingService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final Set<String> PROVINCES = Set.of(
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
            "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "香港", "澳门", "台湾"
    );

    private final DocumentParserSelector parserSelector;

    @Override
    public RoutingDecision route(KnowledgeBaseDO currentKb,
                                 KnowledgeDocumentUploadRequest request,
                                 MultipartFile file) {
        KnowledgeBaseType explicitTarget = parseExplicitTarget(request);
        if (explicitTarget != null) {
            Map<String, Object> metadata = extractMetadata(buildRoutingCorpus(request, file), fileTypeOf(request, file), explicitTarget);
            return new RoutingDecision(explicitTarget, 1.0D, "管理员显式指定目标库", metadata, false);
        }

        KnowledgeBaseType fallback = currentKb == null ? KnowledgeBaseType.GUIDE : KnowledgeBaseType.fromValue(currentKb.getKbType());
        if (Boolean.FALSE.equals(request.getAutoRoute())) {
            Map<String, Object> metadata = extractMetadata(buildRoutingCorpus(request, file), fileTypeOf(request, file), fallback);
            return new RoutingDecision(fallback, 1.0D, "沿用当前知识库，未启用自动路由", metadata, false);
        }

        String corpus = buildRoutingCorpus(request, file);
        ScoredType scoredType = scoreKbType(corpus);
        KnowledgeBaseType target = scoredType.type() == null ? fallback : scoredType.type();
        double confidence = scoredType.confidence();
        boolean needsReview = confidence < 0.60D;
        Map<String, Object> metadata = extractMetadata(corpus, fileTypeOf(request, file), target);
        String reason = scoredType.reason().isEmpty()
                ? "自动路由未命中明显语义特征，使用默认知识库"
                : "自动路由命中：" + String.join("、", scoredType.reason());
        return new RoutingDecision(target, confidence, reason, metadata, needsReview);
    }

    private KnowledgeBaseType parseExplicitTarget(KnowledgeDocumentUploadRequest request) {
        if (!StrUtil.isNotBlank(request.getTargetKbType())) {
            return null;
        }
        return KnowledgeBaseType.fromValue(request.getTargetKbType());
    }

    private String buildRoutingCorpus(KnowledgeDocumentUploadRequest request, MultipartFile file) {
        List<String> parts = new ArrayList<>();
        if (file != null && StrUtil.isNotBlank(file.getOriginalFilename())) {
            parts.add(file.getOriginalFilename());
        }
        if (StrUtil.isNotBlank(request.getSourceLocation())) {
            parts.add(request.getSourceLocation());
        }
        String sampledText = extractSampleText(file);
        if (StrUtil.isNotBlank(sampledText)) {
            parts.add(sampledText);
        }
        return String.join("\n", parts);
    }

    private String extractSampleText(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return "";
        }
        try {
            String fileName = file.getOriginalFilename();
            String detectedType = FileTypeDetector.detectType(fileName, file.getContentType());
            if (isPlainTextFile(detectedType)) {
                byte[] bytes = file.getBytes();
                int length = Math.min(bytes.length, 12000);
                return new String(bytes, 0, length, StandardCharsets.UTF_8);
            }
            DocumentParser parser = parserSelector.select(ParserType.TIKA.getType());
            try (InputStream inputStream = file.getInputStream()) {
                String text = parser.extractText(inputStream, fileName);
                return StrUtil.maxLength(text, 6000);
            }
        } catch (Exception ex) {
            log.debug("Sample extract for routing failed: {}", ex.getMessage());
            return "";
        }
    }

    private boolean isPlainTextFile(String fileType) {
        String normalized = fileType == null ? "" : fileType.toLowerCase(Locale.ROOT);
        return Set.of("markdown", "md", "txt", "log", "py", "cpp", "java", "json", "yaml", "yml", "ipynb").contains(normalized);
    }

    private String fileTypeOf(KnowledgeDocumentUploadRequest request, MultipartFile file) {
        String fileName = file != null ? file.getOriginalFilename() : request.getSourceLocation();
        String mimeType = file != null ? file.getContentType() : null;
        return FileTypeDetector.detectType(fileName, mimeType);
    }

    private ScoredType scoreKbType(String rawCorpus) {
        String corpus = rawCorpus == null ? "" : rawCorpus.toLowerCase(Locale.ROOT);
        Map<KnowledgeBaseType, Integer> scores = new LinkedHashMap<>();
        Map<KnowledgeBaseType, List<String>> reasons = new LinkedHashMap<>();
        for (KnowledgeBaseType each : KnowledgeBaseType.values()) {
            scores.put(each, 0);
            reasons.put(each, new ArrayList<>());
        }

        addScore(corpus, scores, reasons, KnowledgeBaseType.GUIDE, 3, "通知", "报名", "组队", "faq", "赛区", "资格", "晋级", "回执", "补充通知");
        addScore(corpus, scores, reasons, KnowledgeBaseType.RULE, 3, "规则", "规程", "评分", "扣分", "赛道", "图纸", "障碍", "设备限制", "技术规则");
        addScore(corpus, scores, reasons, KnowledgeBaseType.PITFALL, 3, "报错", "error", "ros", "ros2", "cmake", "驱动", "配置", "日志", "源码", "nav2", "log4cxx");
        addScore(corpus, scores, reasons, KnowledgeBaseType.EXEMPLAR, 3, "技术报告", "答辩", "ppt", "一等奖", "金奖", "创新点", "获奖", "论文参考");

        int bestScore = -1;
        int secondScore = -1;
        KnowledgeBaseType bestType = null;
        for (Map.Entry<KnowledgeBaseType, Integer> entry : scores.entrySet()) {
            int score = entry.getValue();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                bestType = entry.getKey();
            } else if (score > secondScore) {
                secondScore = score;
            }
        }

        if (bestType == null || bestScore <= 0) {
            return new ScoredType(null, 0.35D, List.of());
        }

        double confidence = secondScore < 0
                ? 0.95D
                : Math.min(0.95D, 0.50D + (bestScore - secondScore) * 0.10D + bestScore * 0.03D);
        return new ScoredType(bestType, confidence, reasons.get(bestType));
    }

    private void addScore(String corpus,
                          Map<KnowledgeBaseType, Integer> scores,
                          Map<KnowledgeBaseType, List<String>> reasons,
                          KnowledgeBaseType type,
                          int weight,
                          String... keywords) {
        for (String keyword : keywords) {
            if (corpus.contains(keyword.toLowerCase(Locale.ROOT))) {
                scores.computeIfPresent(type, (k, v) -> v + weight);
                reasons.get(type).add(keyword);
            }
        }
    }

    private Map<String, Object> extractMetadata(String corpus, String fileType, KnowledgeBaseType kbType) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("kb_type", kbType.name());
        metadata.put("source_type", "UPLOAD");
        metadata.put("file_type", fileType);
        metadata.put("source_name", inferSourceName(corpus));

        String year = extractYear(corpus);
        if (year != null) {
            metadata.put("year", year);
        }
        String province = extractProvince(corpus);
        if (province != null) {
            metadata.put("province", province);
        }

        switch (kbType) {
            case GUIDE -> fillGuideMetadata(corpus, metadata);
            case RULE -> fillRuleMetadata(corpus, metadata);
            case PITFALL -> fillPitfallMetadata(corpus, metadata);
            case EXEMPLAR -> fillExemplarMetadata(corpus, metadata);
            default -> {
            }
        }
        return metadata;
    }

    private void fillGuideMetadata(String corpus, Map<String, Object> metadata) {
        metadata.putIfAbsent("stage", containsAny(corpus, "国赛", "全国总决赛", "国赛资格") ? "国赛资格"
                : containsAny(corpus, "省赛", "选拔赛") ? "省赛"
                : containsAny(corpus, "校赛") ? "校赛" : null);
        metadata.putIfAbsent("faq_type", containsAny(corpus, "faq", "常见问题") ? "FAQ" : null);
    }

    private void fillRuleMetadata(String corpus, Map<String, Object> metadata) {
        metadata.put("category", containsAny(corpus, "创新赛", "创新") ? "创新"
                : containsAny(corpus, "应用赛", "智能巡检", "智能家居") ? "应用"
                : containsAny(corpus, "挑战赛", "四足", "无人驾驶") ? "挑战" : null);
        metadata.put("sub_track", containsAny(corpus, "智能巡检") ? "智能巡检"
                : containsAny(corpus, "智能家居") ? "智能家居"
                : containsAny(corpus, "四足") ? "四足仿生"
                : containsAny(corpus, "无人驾驶", "自动驾驶") ? "自动驾驶" : null);
        metadata.put("rule_type", containsAny(corpus, "评分", "扣分") ? "评分标准"
                : containsAny(corpus, "设备限制") ? "设备限制"
                : containsAny(corpus, "赛道", "图纸") ? "赛道参数" : "赛项规则");
    }

    private void fillPitfallMetadata(String corpus, Map<String, Object> metadata) {
        metadata.put("tech_stack", containsAny(corpus, "ros2", "nav2") ? "ROS2"
                : containsAny(corpus, "ros") ? "ROS"
                : containsAny(corpus, "opencv") ? "OpenCV"
                : containsAny(corpus, "pytorch", "yolo") ? "PyTorch" : null);
        metadata.put("hardware", containsAny(corpus, "jetson") ? "Jetson"
                : containsAny(corpus, "树莓派", "raspberry pi") ? "树莓派"
                : containsAny(corpus, "旭日x3", "x3") ? "旭日X3" : null);
        metadata.put("error_type", containsAny(corpus, "编译", "cmake", "log4cxx") ? "编译报错"
                : containsAny(corpus, "显存", "oom", "out of memory") ? "显存溢出"
                : containsAny(corpus, "安装", "配置", "环境") ? "环境配置" : null);
    }

    private void fillExemplarMetadata(String corpus, Map<String, Object> metadata) {
        metadata.put("doc_type", containsAny(corpus, "ppt", "答辩") ? "答辩PPT"
                : containsAny(corpus, "技术报告") ? "技术报告"
                : containsAny(corpus, "商业计划书") ? "商业计划书" : null);
        metadata.put("award_level", containsAny(corpus, "金奖") ? "金奖"
                : containsAny(corpus, "一等奖") ? "国赛一等奖" : null);
        metadata.put("project_direction", containsAny(corpus, "医疗") ? "医疗辅助"
                : containsAny(corpus, "假肢") ? "智能假肢"
                : containsAny(corpus, "机器人") ? "机器人项目" : null);
    }

    private boolean containsAny(String corpus, String... tokens) {
        for (String token : tokens) {
            if (corpus.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String inferSourceName(String corpus) {
        if (StrUtil.isBlank(corpus)) {
            return "unknown";
        }
        String[] lines = corpus.split("[\\r\\n]+");
        return lines.length == 0 ? "unknown" : StrUtil.maxLength(lines[0].trim(), 120);
    }

    private String extractYear(String corpus) {
        Matcher matcher = YEAR_PATTERN.matcher(corpus);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String extractProvince(String corpus) {
        for (String province : PROVINCES) {
            if (corpus.contains(province)) {
                return province;
            }
        }
        return null;
    }

    private record ScoredType(KnowledgeBaseType type, double confidence, List<String> reason) {
    }
}
