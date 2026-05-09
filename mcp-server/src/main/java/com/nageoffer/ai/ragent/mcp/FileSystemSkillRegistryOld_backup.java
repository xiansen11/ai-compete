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
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileSystemSkillRegistry implements SkillRegistry {

    private final SkillProperties properties;
    private final Map<String, SkillExecutor> executorMap = new ConcurrentHashMap<>();
    private final List<WatchService> watchServices = new ArrayList<>();
    private final ExecutorService watcherExecutor = Executors.newCachedThreadPool();

    @PostConstruct
    public void init() {
        loadSkillsFromDisk();
        if (properties.isHotReload()) {
            startFileWatchers();
        }
    }

    @PreDestroy
    public void destroy() {
        watchServices.forEach(ws -> {
            try {
                ws.close();
            } catch (IOException e) {
                log.error("Close watch service error", e);
            }
        });
        watcherExecutor.shutdownNow();
    }

    @Override
    public void register(SkillExecutor executor) {
        String skillName = executor.getMetadata().getName();
        executorMap.put(skillName, executor);
        log.info("Skill registered: {}", skillName);
    }

    @Override
    public void unregister(String skillName) {
        executorMap.remove(skillName);
        log.info("Skill unregistered: {}", skillName);
    }

    @Override
    public Optional<SkillExecutor> getExecutor(String skillName) {
        return Optional.ofNullable(executorMap.get(skillName));
    }

    @Override
    public List<SkillMetadata> listAllSkills() {
        return executorMap.values().stream()
                .map(SkillExecutor::getMetadata)
                .toList();
    }

    @Override
    public boolean contains(String skillName) {
        return executorMap.containsKey(skillName);
    }

    private void loadSkillsFromDisk() {
        if (properties.getBasePaths() == null || properties.getBasePaths().isEmpty()) {
            log.warn("No skill base paths configured");
            return;
        }

        for (String basePath : properties.getBasePaths()) {
            Path path = Path.of(basePath);
            if (!Files.exists(path)) {
                log.warn("Skill base path does not exist: {}", basePath);
                continue;
            }

            try (Stream<Path> paths = Files.walk(path)) {
                paths.filter(Files::isDirectory)
                        .filter(p -> Files.exists(p.resolve("SKILL.md")))
                        .forEach(this::loadSkill);
            } catch (IOException e) {
                log.error("Failed to walk skill path: {}", basePath, e);
            }
        }

        log.info("Loaded {} skills from disk", executorMap.size());
    }

    private void loadSkill(Path skillPath) {
        try {
            SkillMetadata metadata = SkillMetadataParser.parse(skillPath.resolve("SKILL.md"));
            SkillExecutor executor = new FileSystemSkillExecutor(skillPath, metadata, properties);
            executorMap.put(metadata.getName(), executor);
            log.info("Skill loaded successfully: {} from {}", metadata.getName(), skillPath);
        } catch (Exception e) {
            log.error("Failed to load skill from path: {}", skillPath, e);
        }
    }

    private void startFileWatchers() {
        if (properties.getBasePaths() == null) {
            return;
        }

        for (String basePath : properties.getBasePaths()) {
            Path path = Path.of(basePath);
            if (!Files.exists(path)) {
                continue;
            }

            try {
                WatchService watchService = FileSystems.getDefault().newWatchService();
                path.register(watchService,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE);

                watchServices.add(watchService);
                watcherExecutor.submit(() -> watchPath(watchService, path));
                log.info("Started file watcher for path: {}", basePath);
            } catch (IOException e) {
                log.error("Failed to start file watcher for path: {}", basePath, e);
            }
        }
    }

    private void watchPath(WatchService watchService, Path rootPath) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    Path changedPath = (Path) event.context();
                    if (changedPath.toString().equals("SKILL.md")) {
                        Path skillDir = key.watchable() instanceof Path ?
                                ((Path) key.watchable()).resolve(changedPath.getParent() != null ?
                                        changedPath.getParent() : Path.of("")) :
                                rootPath;

                        if (Files.exists(skillDir) && Files.exists(skillDir.resolve("SKILL.md"))) {
                            log.info("Skill file changed, reloading: {}", skillDir);
                            loadSkill(skillDir);
                        }
                    }
                }
                key.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error watching path", e);
            }
        }
    }
}