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

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class CompetitionUpdateRequest {

    @Size(max = 255, message = "竞赛名称长度不能超过255个字符")
    private String name;

    @Size(max = 2000, message = "竞赛描述长度不能超过2000个字符")
    private String description;

    private String coverUrl;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private String status;

    private Map<String, Object> config;

    private Long defaultKnowledgeBaseId;
}
