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

package com.nageoffer.ai.ragent.mcp.skill;

import java.util.Map;

public interface SkillExecutor {

    SkillDefinition getSkillDefinition();

    SkillResult execute(SkillContext context);

    default boolean supports(String skillName) {
        return getSkillDefinition().metadata().name().equals(skillName);
    }

    record SkillContext(
        String skillName,
        String userQuery,
        Map<String, Object> parameters
    ) {}

    record SkillResult(
        boolean success,
        String output,
        String errorMessage
    ) {
        public static SkillResult success(String output) {
            return new SkillResult(true, output, null);
        }

        public static SkillResult error(String message) {
            return new SkillResult(false, null, message);
        }
    }
}