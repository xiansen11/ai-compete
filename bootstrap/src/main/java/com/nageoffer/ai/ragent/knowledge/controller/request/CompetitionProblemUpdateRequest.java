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

package com.nageoffer.ai.ragent.knowledge.controller.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CompetitionProblemUpdateRequest {

    private Long competitionId;

    private Long knowledgeBaseId;

    @Size(max = 255, message = "赛题标题长度不能超过255个字符")
    private String title;

    @Size(max = 10000, message = "赛题描述长度不能超过10000个字符")
    private String description;

    private String difficulty;

    private String category;

    private String subCategory;

    private Integer timeLimit;

    private Integer memoryLimit;

    @Size(max = 5000, message = "评分标准长度不能超过5000个字符")
    private String scoringCriteria;

    private String sampleInput;

    private String sampleOutput;

    @Size(max = 2000, message = "约束条件长度不能超过2000个字符")
    private String constraints;

    private List<String> tags;

    private Integer sortOrder;

    private Boolean isVisible;
}
