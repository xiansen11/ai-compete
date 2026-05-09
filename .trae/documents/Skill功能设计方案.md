# Skill 功能设计方案

## 一、技术架构

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Skill 技术架构                                    │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  ┌─────────────┐      ┌─────────────────┐      ┌──────────────────────┐  │
│  │ SkillLoader │ ──── │  SkillRegistry  │ ──── │  SkillCatalogService │  │
│  │ (扫描加载)   │      │   (注册表)       │      │   (技能目录服务)     │  │
│  └─────────────┘      └─────────────────┘      └──────────────────────┘  │
│                                │                         │                 │
│                                │                         ▼                 │
│                                │              ┌──────────────────────┐  │
│                                │              │  SystemPromptBuilder │  │
│                                │              │   (系统提示词构建)    │  │
│                                │              └──────────────────────┘  │
│                                │                                        │
│                                ▼                                        │
│                    ┌─────────────────┐                                  │
│                    │ MCPToolRegistry │                                  │
│                    │  (工具注册表)   │                                  │
│                    └────────┬────────┘                                  │
│                             │                                             │
│                             ▼                                             │
│                    ┌─────────────────┐                                   │
│                    │  SkillExecutor  │                                   │
│                    │  (技能执行器)    │                                   │
│                    └─────────────────┘                                   │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 二、核心组件

### 2.1 组件职责

| 组件                    | 职责                           |
| --------------------- | ---------------------------- |
| `SkillLoader`         | 启动时扫描 skills/ 目录，加载 SKILL.md |
| `SkillRegistry`       | 管理 Skill 注册与查询               |
| `SkillCatalogService` | 提供技能目录、生成系统提示词片段             |
| `MCPToolRegistry`     | 现有 MCP 工具注册表（已存在）            |
| `SkillExecutor`       | 技能执行器，组合调用 MCP 工具            |

### 2.2 类图

```
SkillLoader
  └─ 扫描目录 → 解析 YAML frontmatter → 注册到 SkillRegistry

SkillRegistry
  ├─ Map<String, SkillDefinition> skills
  ├─ register(SkillDefinition)
  ├─ get(String name): Optional<SkillDefinition>
  ├─ listAll(): List<SkillDefinition>
  └─ findByTag(String tag): List<SkillDefinition>

SkillCatalogService
  ├─ getSkillCatalog(): List<SkillMetadata>
  ├─ generateSystemPromptFragment(): String
  └─ loadSkill(String name): SkillDefinition

SkillExecutor
  └─ execute(SkillContext): SkillResult
      └─ 调用 MCPToolRegistry 获取工具 → 执行 → 整合结果
```

## 三、数据模型

### 3.1 SkillMetadata（技能元数据）

```java
public record SkillMetadata(
    String name,           // 技能唯一标识
    String description,     // 一句话描述（用于匹配）
    String version,
    List<String> allowedTools,  // 可调用的 MCP 工具列表
    List<String> tags      // 技能标签
) {}
```

### 3.2 SkillDefinition（技能定义）

```java
public record SkillDefinition(
    SkillMetadata metadata,
    String content         // 完整 SKILL.md 内容
) {}
```

### 3.3 SKILL.md 结构

```markdown
---
name: skill-name
description: 一句话描述技能能力
version: 1.0.0
allowed_tools: [tool1, tool2]
tags: [tag1, tag2]
---

# 技能标题

## 概述
...

## 工作流程
...

## 注意事项
...
```

## 四、SKILL.md 示例

### 4.1 知识库问答统计 Skill

```markdown
---
name: knowledge-qa-stats
description: > 
  提供知识库的问答统计数据，包括访问量、热门问题、响应时间等，
  帮助管理员了解知识库使用情况。
version: 1.0.0
allowed_tools: [knowledge_stats_query]
tags: [knowledge-base, statistics, admin]
---

# 知识库问答统计技能

## 概述
查询知识库的问答统计数据，帮助管理员了解知识库的使用情况。

## 核心能力
- 统计指定时间范围内的问答数量
- 展示热门问题排行榜
- 提供响应时间等性能指标

## 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| timeRange | string | 查询时间范围：7d, 30d, 90d, 1y |
| topN | integer | 返回前 N 条热门问题 |
| includeDetails | boolean | 是否包含每日详情 |

## 工作流程

1. 解析 timeRange 参数
2. 调用 knowledge_stats_query 执行查询
3. 格式化输出为 Markdown 报告

## 使用示例

用户问："查看最近一周的热门问题统计"
→ timeRange: "7d", topN: 10, includeDetails: false

## 注意事项

1. 只允许管理员角色调用
2. 查询时间范围不能超过 1 年
```

### 4.2 MySQL 员工分析 Skill

```markdown
---
name: mysql-employees-analysis
description: > 
  将中文业务问题转换为 SQL 查询并分析 MySQL employees 示例数据库。
version: 1.0.0
allowed_tools: [execute_sql]
tags: [database, mysql, sql]
---

# MySQL 员工数据库分析技能

## 数据库结构

| 表名 | 说明 |
|------|------|
| employees | 员工基本信息 |
| salaries | 薪资历史 |
| departments | 部门信息 |

## 工作流程

1. 理解用户业务需求
2. 构建 SQL 查询
3. 调用 execute_sql 执行
4. 解读结果并生成报告

## 注意事项

⚠️ `to_date = '9999-01-01'` 表示当前有效的记录
```

## 五、实施计划

### 阶段一：核心框架

1. `SkillMetadata`、`SkillDefinition` 数据类
2. `SkillRegistry` 接口 + 内存实现
3. `FileSystemSkillLoader` 文件系统加载器
4. `SkillCatalogService` 技能目录服务

### 阶段二：MCP 集成

1. `SkillExecutor` 技能执行器
2. 与现有 `MCPToolRegistry` 集成
3. 系统提示词构建

### 阶段三：示例 Skill

1. 知识库问答统计 Skill
2. MySQL 员工分析 Skill

## 六、文件结构

```
mcp-server/src/main/java/com/nageoffer/ai/ragent/mcp/skill/
├── SkillMetadata.java          # 技能元数据
├── SkillDefinition.java        # 技能定义
├── SkillRegistry.java         # 技能注册表接口
├── InMemorySkillRegistry.java # 内存实现
├── FileSystemSkillLoader.java # 文件系统加载器
├── SkillCatalogService.java   # 技能目录服务
├── SkillExecutor.java         # 技能执行器
└── SkillParser.java          # SKILL.md 解析器

skills/                        # Skill 存放目录
├── knowledge-qa-stats/
│   └── SKILL.md
└── mysql-employees-analysis/
    └── SKILL.md
```

