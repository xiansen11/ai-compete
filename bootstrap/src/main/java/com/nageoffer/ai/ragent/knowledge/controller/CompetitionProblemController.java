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
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionProblemCreateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionProblemPageRequest;
import com.nageoffer.ai.ragent.knowledge.controller.request.CompetitionProblemUpdateRequest;
import com.nageoffer.ai.ragent.knowledge.controller.vo.CompetitionProblemVO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionProblemDO;
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionDO;
import com.nageoffer.ai.ragent.knowledge.service.CompetitionProblemService;
import com.nageoffer.ai.ragent.knowledge.service.CompetitionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CompetitionProblemController {

    private final CompetitionProblemService problemService;
    private final CompetitionService competitionService;

    @PostMapping("/competition-problem")
    public Result<Long> createProblem(@RequestBody CompetitionProblemCreateRequest request) {
        return Results.success(problemService.createProblem(
                new CompetitionProblemService.ProblemCreateCommand(
                        request.getCompetitionId(),
                        request.getKnowledgeBaseId(),
                        request.getTitle(),
                        request.getDescription(),
                        request.getDifficulty(),
                        request.getCategory(),
                        request.getSubCategory(),
                        request.getTimeLimit(),
                        request.getMemoryLimit(),
                        request.getScoringCriteria(),
                        request.getSampleInput(),
                        request.getSampleOutput(),
                        request.getConstraints(),
                        request.getTags(),
                        request.getSortOrder(),
                        request.getIsVisible()
                )
        ));
    }

    @PutMapping("/competition-problem/{id}")
    public Result<Void> updateProblem(@PathVariable Long id,
                                      @RequestBody CompetitionProblemUpdateRequest request) {
        problemService.updateProblem(
                new CompetitionProblemService.ProblemUpdateCommand(
                        id,
                        request.getCompetitionId(),
                        request.getKnowledgeBaseId(),
                        request.getTitle(),
                        request.getDescription(),
                        request.getDifficulty(),
                        request.getCategory(),
                        request.getSubCategory(),
                        request.getTimeLimit(),
                        request.getMemoryLimit(),
                        request.getScoringCriteria(),
                        request.getSampleInput(),
                        request.getSampleOutput(),
                        request.getConstraints(),
                        request.getTags(),
                        request.getSortOrder(),
                        request.getIsVisible()
                )
        );
        return Results.success();
    }

    @DeleteMapping("/competition-problem/{id}")
    public Result<Void> deleteProblem(@PathVariable Long id) {
        problemService.deleteProblem(id);
        return Results.success();
    }

    @GetMapping("/competition-problem/{id}")
    public Result<CompetitionProblemVO> getProblem(@PathVariable Long id) {
        CompetitionProblemDO problem = problemService.getProblemById(id);
        return Results.success(convertToVO(problem));
    }

    @GetMapping("/competition-problem/page")
    public Result<IPage<CompetitionProblemVO>> pageProblem(CompetitionProblemPageRequest request) {
        IPage<CompetitionProblemDO> page = problemService.pageProblem(
                new CompetitionProblemService.ProblemPageQuery(
                        request.getCompetitionId(),
                        request.getDifficulty(),
                        request.getCategory(),
                        request.getPageNum(),
                        request.getPageSize()
                )
        );

        IPage<CompetitionProblemVO> voPage = page.convert(this::convertToVO);
        return Results.success(voPage);
    }

    private CompetitionProblemVO convertToVO(CompetitionProblemDO DO) {
        if (DO == null) {
            return null;
        }

        String competitionName = null;
        if (DO.getCompetitionId() != null) {
            CompetitionDO competition = competitionService.getCompetitionById(DO.getCompetitionId());
            if (competition != null) {
                competitionName = competition.getName();
            }
        }

        return CompetitionProblemVO.builder()
                .id(DO.getId())
                .competitionId(DO.getCompetitionId())
                .competitionName(competitionName)
                .knowledgeBaseId(DO.getKnowledgeBaseId())
                .title(DO.getTitle())
                .description(DO.getDescription())
                .difficulty(DO.getDifficulty())
                .category(DO.getCategory())
                .subCategory(DO.getSubCategory())
                .timeLimit(DO.getTimeLimit())
                .memoryLimit(DO.getMemoryLimit())
                .scoringCriteria(DO.getScoringCriteria())
                .sampleInput(DO.getSampleInput())
                .sampleOutput(DO.getSampleOutput())
                .constraints(DO.getConstraints())
                .tags(DO.getTags())
                .sortOrder(DO.getSortOrder())
                .isVisible(DO.getIsVisible())
                .createdAt(DO.getCreatedAt())
                .updatedAt(DO.getUpdatedAt())
                .build();
    }
}
