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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.intent.IntentNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class QuestionMetadataSlotExtractor {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(20\\d{2})");
    private static final Set<String> PROVINCES = Set.of(
            "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
            "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
            "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
            "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "香港", "澳门", "台湾"
    );

    private final ObjectMapper objectMapper;

    public Map<String, Object> extract(String question, IntentNode node) {
        Map<String, Object> filters = new LinkedHashMap<>();
        filters.putAll(parseTemplate(node == null ? null : node.getMetadataFilterTemplate()));
        if (!StringUtils.hasText(question)) {
            return filters;
        }

        String text = question.trim();
        if (node != null) {
            String path = node.getFullPath() == null ? "" : node.getFullPath();
            if (path.contains("办事指南")) {
                putGuideFilters(text, filters);
            } else if (path.contains("赛场规则")) {
                putRuleFilters(text, filters);
            } else if (path.contains("技术问题")) {
                putPitfallFilters(text, filters);
            } else if (path.contains("高分材料")) {
                putExemplarFilters(text, filters);
            }
        }
        return filters;
    }

    private Map<String, Object> parseTemplate(String json) {
        if (!StringUtils.hasText(json)) {
            return new LinkedHashMap<>();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, Object> normalized = new LinkedHashMap<>();
            parsed.forEach((key, value) -> {
                if (value != null && !"dynamic".equalsIgnoreCase(String.valueOf(value))) {
                    normalized.put(key, value);
                }
            });
            return normalized;
        } catch (Exception ignore) {
            return new LinkedHashMap<>();
        }
    }

    private void putGuideFilters(String text, Map<String, Object> filters) {
        String province = extractProvince(text);
        if (province != null) {
            filters.put("province", province);
        }
        String year = extractYear(text);
        if (year != null) {
            filters.put("year", year);
        }
        if (containsAny(text, "国赛", "全国总决赛", "晋级国赛")) {
            filters.put("stage", "国赛资格");
        } else if (containsAny(text, "省赛", "选拔赛")) {
            filters.putIfAbsent("stage", "省赛");
        } else if (containsAny(text, "校赛")) {
            filters.putIfAbsent("stage", "校赛");
        }
    }

    private void putRuleFilters(String text, Map<String, Object> filters) {
        if (containsAny(text, "创新赛")) {
            filters.put("category", "创新");
        } else if (containsAny(text, "应用赛", "智能巡检", "智能家居")) {
            filters.put("category", "应用");
        } else if (containsAny(text, "挑战赛", "四足", "无人驾驶", "自动驾驶")) {
            filters.put("category", "挑战");
        }

        if (containsAny(text, "智能巡检")) {
            filters.put("sub_track", "智能巡检");
        } else if (containsAny(text, "智能家居")) {
            filters.put("sub_track", "智能家居");
        } else if (containsAny(text, "四足")) {
            filters.put("sub_track", "四足仿生");
        } else if (containsAny(text, "无人驾驶", "自动驾驶")) {
            filters.put("sub_track", "自动驾驶");
        }
    }

    private void putPitfallFilters(String text, Map<String, Object> filters) {
        if (containsAny(text, "ros2", "nav2")) {
            filters.put("tech_stack", "ROS2");
        } else if (containsAny(text, "ros")) {
            filters.put("tech_stack", "ROS");
        } else if (containsAny(text, "opencv")) {
            filters.put("tech_stack", "OpenCV");
        } else if (containsAny(text, "yolo", "pytorch")) {
            filters.put("tech_stack", "PyTorch");
        }

        if (containsAny(text, "jetson")) {
            filters.put("hardware", "Jetson");
        } else if (containsAny(text, "树莓派", "raspberry pi")) {
            filters.put("hardware", "树莓派");
        } else if (containsAny(text, "旭日x3", "x3")) {
            filters.put("hardware", "旭日X3");
        }

        if (containsAny(text, "编译", "cmake", "log4cxx")) {
            filters.put("error_type", "编译报错");
        } else if (containsAny(text, "显存", "oom", "out of memory")) {
            filters.put("error_type", "显存溢出");
        } else if (containsAny(text, "环境", "安装", "配置")) {
            filters.put("error_type", "环境配置");
        }
    }

    private void putExemplarFilters(String text, Map<String, Object> filters) {
        if (containsAny(text, "技术报告")) {
            filters.put("doc_type", "技术报告");
        } else if (containsAny(text, "答辩", "ppt")) {
            filters.put("doc_type", "答辩PPT");
        }

        if (containsAny(text, "金奖")) {
            filters.put("award_level", "金奖");
        } else if (containsAny(text, "一等奖")) {
            filters.put("award_level", "国赛一等奖");
        }

        if (containsAny(text, "假肢")) {
            filters.put("project_direction", "智能假肢");
        } else if (containsAny(text, "医疗")) {
            filters.put("project_direction", "医疗辅助");
        }
    }

    private boolean containsAny(String text, String... tokens) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : tokens) {
            if (lower.contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private String extractProvince(String text) {
        for (String province : PROVINCES) {
            if (text.contains(province)) {
                return province;
            }
        }
        return null;
    }

    private String extractYear(String text) {
        Matcher matcher = YEAR_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1) : null;
    }
}
