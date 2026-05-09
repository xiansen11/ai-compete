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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionProblemDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.CompetitionProblemMapper;
import com.nageoffer.ai.ragent.knowledge.service.CompetitionProblemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionProblemServiceImpl implements CompetitionProblemService {

    private final CompetitionProblemMapper problemMapper;

    @Override
    public Page<CompetitionProblemDO> pageProblem(ProblemPageQuery query) {
        LambdaQueryWrapper<CompetitionProblemDO> wrapper = new LambdaQueryWrapper<CompetitionProblemDO>()
                .eq(query.competitionId() != null, CompetitionProblemDO::getCompetitionId, query.competitionId())
                .eq(query.difficulty() != null, CompetitionProblemDO::getDifficulty, query.difficulty())
                .eq(query.category() != null, CompetitionProblemDO::getCategory, query.category())
                .eq(CompetitionProblemDO::getIsVisible, true)
                .orderByAsc(CompetitionProblemDO::getSortOrder);

        return problemMapper.selectPage(
                new Page<>(query.pageNum(), query.pageSize()),
                wrapper
        );
    }

    @Override
    public CompetitionProblemDO getProblemById(Long id) {
        return problemMapper.selectById(id);
    }

    @Override
    public Long createProblem(ProblemCreateCommand command) {
        CompetitionProblemDO problem = CompetitionProblemDO.builder()
                .competitionId(command.competitionId())
                .knowledgeBaseId(command.knowledgeBaseId())
                .title(command.title())
                .description(command.description())
                .difficulty(command.difficulty() != null ? command.difficulty() : "MEDIUM")
                .category(command.category())
                .subCategory(command.subCategory())
                .timeLimit(command.timeLimit() != null ? command.timeLimit() : 0)
                .memoryLimit(command.memoryLimit() != null ? command.memoryLimit() : 0)
                .scoringCriteria(command.scoringCriteria())
                .sampleInput(command.sampleInput())
                .sampleOutput(command.sampleOutput())
                .constraints(command.constraints())
                .tags(command.tags())
                .sortOrder(command.sortOrder() != null ? command.sortOrder() : 0)
                .isVisible(command.isVisible() != null ? command.isVisible() : true)
                .build();

        problemMapper.insert(problem);
        log.info("创建赛题成功，赛题ID：{}，标题：{}，所属竞赛：{}", problem.getId(), command.title(), command.competitionId());
        return problem.getId();
    }

    @Override
    public void updateProblem(ProblemUpdateCommand command) {
        CompetitionProblemDO problem = CompetitionProblemDO.builder()
                .id(command.id())
                .competitionId(command.competitionId())
                .knowledgeBaseId(command.knowledgeBaseId())
                .title(command.title())
                .description(command.description())
                .difficulty(command.difficulty())
                .category(command.category())
                .subCategory(command.subCategory())
                .timeLimit(command.timeLimit())
                .memoryLimit(command.memoryLimit())
                .scoringCriteria(command.scoringCriteria())
                .sampleInput(command.sampleInput())
                .sampleOutput(command.sampleOutput())
                .constraints(command.constraints())
                .tags(command.tags())
                .sortOrder(command.sortOrder())
                .isVisible(command.isVisible())
                .build();

        problemMapper.updateById(problem);
        log.info("更新赛题成功，赛题ID：{}", command.id());
    }

    @Override
    public void deleteProblem(Long id) {
        problemMapper.deleteById(id);
        log.info("删除赛题成功，赛题ID：{}", id);
    }
}
