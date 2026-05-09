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
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionDO;
import com.nageoffer.ai.ragent.knowledge.dao.mapper.CompetitionMapper;
import com.nageoffer.ai.ragent.knowledge.service.CompetitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompetitionServiceImpl implements CompetitionService {

    private final CompetitionMapper competitionMapper;

    @Override
    public Page<CompetitionDO> pageCompetition(CompetitionPageQuery query) {
        LambdaQueryWrapper<CompetitionDO> wrapper = new LambdaQueryWrapper<CompetitionDO>()
                .like(query.name() != null, CompetitionDO::getName, query.name())
                .eq(query.status() != null, CompetitionDO::getStatus, query.status())
                .orderByDesc(CompetitionDO::getCreatedAt);

        return competitionMapper.selectPage(
                new Page<>(query.pageNum(), query.pageSize()),
                wrapper
        );
    }

    @Override
    public CompetitionDO getCompetitionById(Long id) {
        return competitionMapper.selectById(id);
    }

    @Override
    public Long createCompetition(CompetitionCreateCommand command) {
        CompetitionDO competition = CompetitionDO.builder()
                .name(command.name())
                .description(command.description())
                .coverUrl(command.coverUrl())
                .startTime(command.startTime())
                .endTime(command.endTime())
                .status("UPCOMING")
                .config(command.config())
                .defaultKnowledgeBaseId(command.defaultKnowledgeBaseId())
                .build();

        competitionMapper.insert(competition);
        log.info("创建竞赛成功，竞赛ID：{}，名称：{}", competition.getId(), command.name());
        return competition.getId();
    }

    @Override
    public void updateCompetition(CompetitionUpdateCommand command) {
        CompetitionDO competition = CompetitionDO.builder()
                .id(command.id())
                .name(command.name())
                .description(command.description())
                .coverUrl(command.coverUrl())
                .startTime(command.startTime())
                .endTime(command.endTime())
                .status(command.status())
                .config(command.config())
                .defaultKnowledgeBaseId(command.defaultKnowledgeBaseId())
                .build();

        competitionMapper.updateById(competition);
        log.info("更新竞赛成功，竞赛ID：{}", command.id());
    }

    @Override
    public void deleteCompetition(Long id) {
        competitionMapper.deleteById(id);
        log.info("删除竞赛成功，竞赛ID：{}", id);
    }
}
