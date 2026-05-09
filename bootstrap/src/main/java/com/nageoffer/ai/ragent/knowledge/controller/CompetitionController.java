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

package com.nageoffer.ai.ragent.knowledge.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.CompetitionVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionDO;
import com.nageoffer.ai.ragent.knowledge.service.CompetitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompetitionController {

    private final CompetitionService competitionService;

    @PostMapping("/competition")
    public Result<Long> createCompetition(@RequestBody CompetitionCreateRequest request) {
        return Results.success(competitionService.createCompetition(
                new CompetitionService.CompetitionCreateCommand(
                        request.getName(),
                        request.getDescription(),
                        request.getCoverUrl(),
                        request.getStartTime(),
                        request.getEndTime(),
                        request.getConfig(),
                        request.getDefaultKnowledgeBaseId()
                )
        ));
    }

    @PutMapping("/competition/{id}")
    public Result<Void> updateCompetition(@PathVariable Long id,
                                          @RequestBody CompetitionUpdateRequest request) {
        competitionService.updateCompetition(
                new CompetitionService.CompetitionUpdateCommand(
                        id,
                        request.getName(),
                        request.getDescription(),
                        request.getCoverUrl(),
                        request.getStartTime(),
                        request.getEndTime(),
                        request.getStatus(),
                        request.getConfig(),
                        request.getDefaultKnowledgeBaseId()
                )
        );
        return Results.success();
    }

    @DeleteMapping("/competition/{id}")
    public Result<Void> deleteCompetition(@PathVariable Long id) {
        competitionService.deleteCompetition(id);
        return Results.success();
    }

    @GetMapping("/competition/{id}")
    public Result<CompetitionVO> getCompetition(@PathVariable Long id) {
        CompetitionDO competition = competitionService.getCompetitionById(id);
        return Results.success(convertToVO(competition));
    }

    @GetMapping("/competition/page")
    public Result<IPage<CompetitionVO>> pageCompetition(CompetitionPageRequest request) {
        IPage<CompetitionDO> page = competitionService.pageCompetition(
                new CompetitionService.CompetitionPageQuery(
                        request.getName(),
                        request.getStatus(),
                        request.getPageNum(),
                        request.getPageSize()
                )
        );

        IPage<CompetitionVO> voPage = page.convert(this::convertToVO);
        return Results.success(voPage);
    }

    private CompetitionVO convertToVO(CompetitionDO DO) {
        if (DO == null) {
            return null;
        }
        return CompetitionVO.builder()
                .id(DO.getId())
                .name(DO.getName())
                .description(DO.getDescription())
                .coverUrl(DO.getCoverUrl())
                .startTime(DO.getStartTime())
                .endTime(DO.getEndTime())
                .status(DO.getStatus())
                .config(DO.getConfig())
                .defaultKnowledgeBaseId(DO.getDefaultKnowledgeBaseId())
                .createdAt(DO.getCreatedAt())
                .updatedAt(DO.getUpdatedAt())
                .build();
    }
}
