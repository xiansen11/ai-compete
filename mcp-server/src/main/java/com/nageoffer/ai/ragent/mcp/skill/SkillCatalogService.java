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

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SkillCatalogService {

    private final SkillRegistry skillRegistry;

    public List<SkillMetadata> getSkillCatalog() {
        return skillRegistry.listAllMetadata();
    }

    public String generateSystemPromptFragment() {
        List<SkillMetadata> skills = skillRegistry.listAllMetadata();
        if (skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n可用技能：\n");
        for (SkillMetadata skill : skills) {
            sb.append("- ").append(skill.name())
                    .append(": ").append(skill.description()).append("\n");
        }
        return sb.toString();
    }

    public SkillDefinition loadSkill(String name) {
        return skillRegistry.get(name).orElse(null);
    }
}