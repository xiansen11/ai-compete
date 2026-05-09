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

package com.nageoffer.ai.ragent.admin.controller.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompetitionStatsVO {

    private Long competitionId;
    private String competitionName;

    private Long totalQuestions;
    private Double avgResponseTimeMs;

    private Long toolCallCount;
    private Map<String, Long> toolUsageBreakdown;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    private Long activeUsersCount;

    private List<HotProblemVO> topProblems;
    private List<UserActivityPoint> userActivityTrend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HotProblemVO {
        private Long problemId;
        private String problemTitle;
        private Long questionCount;
        private Double avgSatisfactionScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class UserActivityPoint {
        private LocalDateTime timestamp;
        private Integer activeUserCount;
        private Integer questionCount;
    }
}
