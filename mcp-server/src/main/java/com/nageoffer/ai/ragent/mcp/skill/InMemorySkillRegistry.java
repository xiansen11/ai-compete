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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Component
public class InMemorySkillRegistry implements SkillRegistry {

    private final Map<String, SkillDefinition> skills = new ConcurrentHashMap<>();

    @Override
    public void register(SkillDefinition skill) {
        skills.put(skill.metadata().name(), skill);
        log.info("Skill registered: {}", skill.metadata().name());
    }

    @Override
    public void unregister(String name) {
        skills.remove(name);
        log.info("Skill unregistered: {}", name);
    }

    @Override
    public Optional<SkillDefinition> get(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    @Override
    public Optional<SkillMetadata> getMetadata(String name) {
        return get(name).map(SkillDefinition::metadata);
    }

    @Override
    public List<SkillMetadata> listAllMetadata() {
        return skills.values().stream()
                .map(SkillDefinition::metadata)
                .toList();
    }

    @Override
    public List<SkillDefinition> findByTag(String tag) {
        return skills.values().stream()
                .filter(skill -> skill.metadata().tags() != null &&
                        skill.metadata().tags().contains(tag))
                .toList();
    }

    @Override
    public List<SkillDefinition> findByAllowedTool(String tool) {
        return skills.values().stream()
                .filter(skill -> skill.metadata().allowedTools() != null &&
                        skill.metadata().allowedTools().contains(tool))
                .toList();
    }

    @Override
    public List<SkillDefinition> listAll() {
        return new ArrayList<>(skills.values());
    }
}