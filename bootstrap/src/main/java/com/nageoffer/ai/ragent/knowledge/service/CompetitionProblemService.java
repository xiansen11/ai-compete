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

package com.nageoffer.ai.ragent.knowledge.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nageoffer.ai.ragent.knowledge.dao.entity.CompetitionProblemDO;

public interface CompetitionProblemService {

    Page<CompetitionProblemDO> pageProblem(ProblemPageQuery query);

    CompetitionProblemDO getProblemById(Long id);

    Long createProblem(ProblemCreateCommand command);

    void updateProblem(ProblemUpdateCommand command);

    void deleteProblem(Long id);

    record ProblemPageQuery(
            Long competitionId,
            String difficulty,
            String category,
            Integer pageNum,
            Integer pageSize
    ) {}

    record ProblemCreateCommand(
            Long competitionId,
            Long knowledgeBaseId,
            String title,
            String description,
            String difficulty,
            String category,
            String subCategory,
            Integer timeLimit,
            Integer memoryLimit,
            String scoringCriteria,
            String sampleInput,
            String sampleOutput,
            String constraints,
            java.util.List<String> tags,
            Integer sortOrder,
            Boolean isVisible
    ) {}

    record ProblemUpdateCommand(
            Long id,
            Long competitionId,
            Long knowledgeBaseId,
            String title,
            String description,
            String difficulty,
            String category,
            String subCategory,
            Integer timeLimit,
            Integer memoryLimit,
            String scoringCriteria,
            String sampleInput,
            String sampleOutput,
            String constraints,
            java.util.List<String> tags,
            Integer sortOrder,
            Boolean isVisible
    ) {}
}
