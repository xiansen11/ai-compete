-- ============================================
-- AI-Compete智赛通 - 竞赛模块数据库表结构
-- 版本: V2.0
-- 创建日期: 2026-04-20
-- 说明: 新增竞赛管理和赛题管理相关表
-- ============================================

-- ===== 1. 竞赛信息表 =====
CREATE TABLE IF NOT EXISTS competition (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL COMMENT '竞赛名称',
    description TEXT COMMENT '竞赛描述',
    cover_url VARCHAR(500) COMMENT '封面图片URL',
    start_time TIMESTAMP COMMENT '开始时间',
    end_time TIMESTAMP COMMENT '结束时间',
    status VARCHAR(20) NOT NULL DEFAULT 'UPCOMING' COMMENT '状态：UPCOMING/ONGOING/ENDED',
    config JSONB DEFAULT '{}' COMMENT '竞赛配置（评分权重、提交格式等）',
    default_knowledge_base_id BIGINT COMMENT '默认知识库ID',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_competition_status ON competition(status);
CREATE INDEX idx_competition_time_range ON competition(start_time, end_time);

COMMENT ON TABLE competition IS '竞赛信息表';

-- ===== 2. 赛题信息表 =====
CREATE TABLE IF NOT EXISTS competition_problem (
    id BIGSERIAL PRIMARY KEY,
    competition_id BIGINT NOT NULL COMMENT '关联竞赛ID',
    knowledge_base_id BIGINT COMMENT '关联知识库ID',
    title VARCHAR(255) COMMENT '赛题标题',
    description TEXT COMMENT '赛题详细描述',
    difficulty VARCHAR(20) DEFAULT 'MEDIUM' COMMENT '难度：EASY/MEDIUM/HARD',
    category VARCHAR(100) COMMENT '分类：NLP/CV/推荐系统/多模态等',
    sub_category VARCHAR(100) COMMENT '子分类：文本分类/目标检测/序列推荐等',
    time_limit INT DEFAULT 0 COMMENT '时间限制（分钟），0表示无限制',
    memory_limit INT DEFAULT 0 COMMENT '内存限制（MB），0表示无限制',
    scoring_criteria TEXT COMMENT '评分标准说明',
    sample_input TEXT COMMENT '样例输入',
    sample_output TEXT COMMENT '样例输出',
    constraints TEXT COMMENT '约束条件与注意事项',
    tags VARCHAR(500) DEFAULT '[]'::jsonb COMMENT '标签（JSON数组格式）',
    sort_order INT DEFAULT 0 COMMENT '排序序号',
    is_visible BOOLEAN DEFAULT true COMMENT '是否对参赛者可见',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_competition_problem_competition FOREIGN KEY (competition_id)
        REFERENCES competition(id) ON DELETE CASCADE
);

CREATE INDEX idx_competition_problem_competition_id ON competition_problem(competition_id);
CREATE INDEX idx_competition_problem_difficulty ON competition_problem(difficulty);
CREATE INDEX idx_competition_problem_category ON competition_problem(category);
CREATE INDEX idx_competition_problem_knowledge_base_id ON competition_problem(knowledge_base_id);

COMMENT ON TABLE competition_problem IS '赛题信息表';

-- ===== 3. 初始化示例数据（可选）=====
-- INSERT INTO competition (name, description, status, config) VALUES
-- ('2026 AI算法挑战赛', '面向高校学生的AI算法竞赛', 'UPCOMING', '{"max_team_size": 3}'::jsonb);
