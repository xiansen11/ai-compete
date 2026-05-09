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

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSystemSkillLoader {

    private final SkillProperties skillProperties;
    private final SkillRegistry skillRegistry;

    @PostConstruct
    public void loadAllSkills() {
        if (skillProperties.getBasePaths() == null || skillProperties.getBasePaths().isEmpty()) {
            log.warn("No skill base paths configured");
            return;
        }

        for (String basePath : skillProperties.getBasePaths()) {
            loadSkillsFromPath(Path.of(basePath));
        }

        log.info("Loaded {} skills from disk", skillRegistry.listAll().size());
    }

    private void loadSkillsFromPath(Path basePath) {
        if (!Files.exists(basePath)) {
            log.warn("Skill base path does not exist: {}", basePath);
            return;
        }

        try (Stream<Path> paths = Files.walk(basePath)) {
            paths.filter(Files::isDirectory)
                    .filter(p -> Files.exists(p.resolve("SKILL.md")))
                    .forEach(this::loadSkill);
        } catch (IOException e) {
            log.error("Failed to walk skill path: {}", basePath, e);
        }
    }

    private void loadSkill(Path skillDir) {
        Path skillMdPath = skillDir.resolve("SKILL.md");
        try {
            String content = Files.readString(skillMdPath);
            SkillDefinition skill = SkillParser.parse(skillMdPath, content);
            skillRegistry.register(skill);
            log.info("Skill loaded successfully: {} from {}", skill.metadata().name(), skillDir);
        } catch (Exception e) {
            log.error("Failed to load skill from path: {}", skillDir, e);
        }
    }
}