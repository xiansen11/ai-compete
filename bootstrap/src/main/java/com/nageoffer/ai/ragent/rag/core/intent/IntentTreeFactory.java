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

import com.nageoffer.ai.ragent.rag.enums.IntentKind;
import com.nageoffer.ai.ragent.rag.enums.IntentLevel;
import com.nageoffer.ai.ragent.rag.enums.PreferredSource;

import java.util.List;

public class IntentTreeFactory {

    private static final String KB_GUIDE = "kb-guide";
    private static final String KB_RULE = "kb-rule";
    private static final String KB_PITFALL = "kb-pitfall";
    private static final String KB_EXEMPLAR = "kb-exemplar";

    public static List<IntentNode> buildIntentTree() {
        IntentNode guide = domain("competition-guide", KB_GUIDE, "办事指南咨询", List.of(
                category("competition-guide-admin", KB_GUIDE, "赛务办理", "competition-guide", List.of(
                        topic("competition-guide-registration", KB_GUIDE, "报名资格", "competition-guide-admin",
                                "{\"stage\":\"校赛\"}", "{\"slots\":[\"province\",\"year\",\"stage\"]}", "报名、参赛资格、人员限制", PreferredSource.KB,
                                List.of("怎么报名参赛", "组队人数有限制吗")),
                        topic("competition-guide-team", KB_GUIDE, "组队规则", "competition-guide-admin",
                                null, "{\"slots\":[\"province\",\"year\"]}", "组队、跨校、指导老师规则", PreferredSource.KB,
                                List.of("可以跨校组队吗", "指导老师能带几队")),
                        topic("competition-guide-faq", KB_GUIDE, "赛务FAQ", "competition-guide-admin",
                                null, "{\"slots\":[\"province\",\"year\"]}", "FAQ、注意事项、材料提交", PreferredSource.KB,
                                List.of("报名回执什么时候交", "FAQ 里怎么说"))
                )),
                category("competition-guide-policy", KB_GUIDE, "晋级与政策", "competition-guide", List.of(
                        topic("competition-guide-province", KB_GUIDE, "省赛政策", "competition-guide-policy",
                                "{\"province\":\"dynamic\"}", "{\"slots\":[\"province\",\"year\"]}", "赛区补充通知、地方政策", PreferredSource.KB,
                                List.of("江苏赛区有什么补充要求", "广东赛区报名截止到什么时候")),
                        topic("competition-guide-national", KB_GUIDE, "国赛资格", "competition-guide-policy",
                                "{\"stage\":\"国赛资格\"}", "{\"slots\":[\"province\",\"year\",\"stage\"]}", "晋级国赛、推荐名额、排名规则", PreferredSource.KB,
                                List.of("江苏二等奖能进国赛吗", "每校入围国赛不超过几支"))
                ))
        ));

        IntentNode rule = domain("competition-rule", KB_RULE, "赛场规则咨询", List.of(
                category("competition-rule-game", KB_RULE, "赛项规则", "competition-rule", List.of(
                        topic("competition-rule-track", KB_RULE, "赛项规则", "competition-rule-game",
                                null, "{\"slots\":[\"category\",\"sub_track\"]}", "技术规则、规程、任务要求", PreferredSource.KB,
                                List.of("智能巡检赛项规则是什么", "挑战赛任务怎么判定完成")),
                        topic("competition-rule-score", KB_RULE, "评分扣分", "competition-rule-game",
                                null, "{\"slots\":[\"category\",\"sub_track\"]}", "扣分项、超时、碰撞处罚", PreferredSource.KB,
                                List.of("撞到路障怎么扣分", "超时每分钟扣多少分"))
                )),
                category("competition-rule-device", KB_RULE, "设备与场地", "competition-rule", List.of(
                        topic("competition-rule-device-limit", KB_RULE, "设备限制", "competition-rule-device",
                                "{\"rule_type\":\"设备限制\"}", "{\"slots\":[\"category\",\"sub_track\"]}", "传感器限制、器材要求", PreferredSource.KB,
                                List.of("智能巡检能用激光雷达吗", "允许哪些传感器")),
                        topic("competition-rule-track-param", KB_RULE, "赛道参数", "competition-rule-device",
                                "{\"rule_type\":\"赛道参数\"}", "{\"slots\":[\"category\",\"sub_track\"]}", "赛道长宽、障碍参数、图纸尺寸", PreferredSource.KB,
                                List.of("赛道尺寸是多少", "障碍物高度有要求吗"))
                ))
        ));

        IntentNode pitfall = domain("competition-pitfall", KB_PITFALL, "技术问题排障", List.of(
                category("competition-pitfall-env", KB_PITFALL, "环境与编译", "competition-pitfall", List.of(
                        topic("competition-pitfall-env-setup", KB_PITFALL, "环境配置", "competition-pitfall-env",
                                "{\"error_type\":\"环境配置\"}", "{\"slots\":[\"tech_stack\",\"hardware\",\"error_type\"]}", "环境安装、依赖配置、驱动问题", PreferredSource.KB,
                                List.of("Jetson 环境怎么配", "ROS2 依赖怎么装")),
                        topic("competition-pitfall-build", KB_PITFALL, "编译报错", "competition-pitfall-env",
                                "{\"error_type\":\"编译报错\"}", "{\"slots\":[\"tech_stack\",\"hardware\",\"error_type\"]}", "CMake、链接库、编译失败", PreferredSource.KB,
                                List.of("ROS log4cxx 编译错误", "CMakeLists.txt 缺少库怎么改"))
                )),
                category("competition-pitfall-dev", KB_PITFALL, "算法与硬件", "competition-pitfall", List.of(
                        topic("competition-pitfall-ros", KB_PITFALL, "ROS导航", "competition-pitfall-dev",
                                "{\"tech_stack\":\"ROS2\"}", "{\"slots\":[\"tech_stack\",\"hardware\",\"error_type\"]}", "ROS2、Nav2、导航相关问题", PreferredSource.KB,
                                List.of("Nav2 常见报错怎么处理", "ROS2 节点起不来怎么办")),
                        topic("competition-pitfall-vision", KB_PITFALL, "视觉部署", "competition-pitfall-dev",
                                "{\"tech_stack\":\"PyTorch\"}", "{\"slots\":[\"tech_stack\",\"hardware\",\"error_type\"]}", "YOLO、OpenCV、边缘部署优化", PreferredSource.KB,
                                List.of("YOLO 怎么部署到 Jetson", "巡线识别不稳定怎么调")),
                        topic("competition-pitfall-driver", KB_PITFALL, "硬件驱动", "competition-pitfall-dev",
                                null, "{\"slots\":[\"tech_stack\",\"hardware\",\"error_type\"]}", "开发板驱动、相机、电机、传感器", PreferredSource.KB,
                                List.of("树莓派摄像头驱动怎么装", "电机控制板怎么接"))
                ))
        ));

        IntentNode exemplar = domain("competition-exemplar", KB_EXEMPLAR, "高分材料参考", List.of(
                category("competition-exemplar-doc", KB_EXEMPLAR, "材料模板", "competition-exemplar", List.of(
                        topic("competition-exemplar-report", KB_EXEMPLAR, "技术报告模板", "competition-exemplar-doc",
                                "{\"doc_type\":\"技术报告\"}", "{\"slots\":[\"doc_type\",\"award_level\",\"project_direction\"]}", "技术报告结构、章节模板、排版", PreferredSource.KB,
                                List.of("技术报告该怎么写", "高分报告通常有哪些章节")),
                        topic("competition-exemplar-innovation", KB_EXEMPLAR, "创新点写法", "competition-exemplar-doc",
                                "{\"doc_type\":\"技术报告\"}", "{\"slots\":[\"doc_type\",\"award_level\",\"project_direction\"]}", "创新点组织方式、专家视角表达", PreferredSource.KB,
                                List.of("创新赛技术创新点怎么写", "智能假肢项目怎么突出创新"))
                )),
                category("competition-exemplar-defense", KB_EXEMPLAR, "答辩参考", "competition-exemplar", List.of(
                        topic("competition-exemplar-ppt", KB_EXEMPLAR, "答辩PPT结构", "competition-exemplar-defense",
                                "{\"doc_type\":\"答辩PPT\"}", "{\"slots\":[\"doc_type\",\"award_level\",\"project_direction\"]}", "答辩结构、讲述顺序、PPT大纲", PreferredSource.KB,
                                List.of("答辩 PPT 怎么排", "专家最关注哪几页")),
                        topic("competition-exemplar-case", KB_EXEMPLAR, "往届高分案例", "competition-exemplar-defense",
                                null, "{\"slots\":[\"doc_type\",\"award_level\",\"project_direction\"]}", "获奖案例、一等奖经验、参考材料", PreferredSource.KB,
                                List.of("有没有往届一等奖案例", "金奖作品常见亮点是什么"))
                ))
        ));

        IntentNode realtime = systemDomain("competition-realtime", "实时外部信息", IntentKind.MCP, List.of(
                mcpCategory("competition-realtime-web", "官网搜索/实时公告", "competition-realtime", "web_search"),
                mcpCategory("competition-realtime-github", "GitHub 源码检索", "competition-realtime", "git_source"),
                mcpCategory("competition-realtime-paper", "Arxiv/Scholar 论文检索", "competition-realtime", "paper_search"),
                mcpCategory("competition-realtime-site", "定向站点抓取", "competition-realtime", "caairobot_site")
        ));

        IntentNode system = systemDomain("competition-system", "系统交互", IntentKind.SYSTEM, List.of(
                simpleNode("competition-system-welcome", "欢迎与介绍", "competition-system", IntentKind.SYSTEM, PreferredSource.KB,
                        List.of("你是谁", "你能做什么")),
                simpleNode("competition-system-chat", "通用对话", "competition-system", IntentKind.SYSTEM, PreferredSource.KB,
                        List.of("你好", "谢谢"))
        ));

        return List.of(guide, rule, pitfall, exemplar, realtime, system);
    }

    private static IntentNode domain(String id, String kbId, String name, List<IntentNode> children) {
        return IntentNode.builder()
                .id(id)
                .kbId(kbId)
                .name(name)
                .level(IntentLevel.DOMAIN)
                .kind(IntentKind.KB)
                .children(children)
                .preferredSource(PreferredSource.KB)
                .build();
    }

    private static IntentNode category(String id, String kbId, String name, String parentId, List<IntentNode> children) {
        return IntentNode.builder()
                .id(id)
                .kbId(kbId)
                .name(name)
                .parentId(parentId)
                .level(IntentLevel.CATEGORY)
                .kind(IntentKind.KB)
                .children(children)
                .preferredSource(PreferredSource.KB)
                .build();
    }

    private static IntentNode topic(String id, String kbId, String name, String parentId,
                                    String metadataFilter, String slotSchema, String routingHint,
                                    PreferredSource preferredSource, List<String> examples) {
        return IntentNode.builder()
                .id(id)
                .kbId(kbId)
                .name(name)
                .parentId(parentId)
                .level(IntentLevel.TOPIC)
                .kind(IntentKind.KB)
                .collectionName(collectionNameFor(kbId))
                .examples(examples)
                .metadataFilterTemplate(metadataFilter)
                .slotSchemaJson(slotSchema)
                .routingHint(routingHint)
                .preferredSource(preferredSource)
                .build();
    }

    private static IntentNode systemDomain(String id, String name, IntentKind kind, List<IntentNode> children) {
        return IntentNode.builder()
                .id(id)
                .name(name)
                .level(IntentLevel.DOMAIN)
                .kind(kind)
                .children(children)
                .preferredSource(kind == IntentKind.MCP ? PreferredSource.MCP : PreferredSource.KB)
                .build();
    }

    private static IntentNode mcpCategory(String id, String name, String parentId, String toolId) {
        return IntentNode.builder()
                .id(id)
                .name(name)
                .parentId(parentId)
                .level(IntentLevel.TOPIC)
                .kind(IntentKind.MCP)
                .mcpToolId(toolId)
                .preferredSource(PreferredSource.MCP)
                .examples(List.of(name + "相关问题"))
                .build();
    }

    private static IntentNode simpleNode(String id, String name, String parentId, IntentKind kind,
                                         PreferredSource preferredSource, List<String> examples) {
        return IntentNode.builder()
                .id(id)
                .name(name)
                .parentId(parentId)
                .level(IntentLevel.CATEGORY)
                .kind(kind)
                .preferredSource(preferredSource)
                .examples(examples)
                .build();
    }

    private static String collectionNameFor(String kbId) {
        return switch (kbId) {
            case KB_GUIDE -> "competition_guide_admin";
            case KB_RULE -> "competition_arena_rules";
            case KB_PITFALL -> "competition_tech_pitfalls";
            case KB_EXEMPLAR -> "competition_excellent_works";
            default -> null;
        };
    }
}
