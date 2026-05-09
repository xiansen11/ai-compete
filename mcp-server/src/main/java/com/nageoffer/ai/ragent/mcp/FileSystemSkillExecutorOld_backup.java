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

import java.nio.file.Path;
import java.util.Map;

@Slf4j
public class FileSystemSkillExecutor implements SkillExecutor {

    private final Path skillRootPath;
    private final SkillMetadata metadata;
    private final SkillProperties properties;
    private final ScriptExecutor scriptExecutor;

    public FileSystemSkillExecutor(Path skillRootPath, SkillMetadata metadata, SkillProperties properties) {
        this.skillRootPath = skillRootPath;
        this.metadata = metadata;
        this.properties = properties;
        this.scriptExecutor = new ScriptExecutor(properties);
    }

    @Override
    public SkillMetadata getMetadata() {
        return metadata;
    }

    @Override
    public SkillResponse execute(SkillRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            Map<String, String> resolvedEnv = resolveEnv();

            ScriptExecutor.ScriptExecutionResult result = scriptExecutor.execute(
                    metadata.getScript(),
                    request.getParameters(),
                    resolvedEnv,
                    skillRootPath
            );

            long executionTime = System.currentTimeMillis() - startTime;

            if (result.isTimeout()) {
                return SkillResponse.error("TIMEOUT",
                        "Script execution timeout after " + metadata.getScript().getTimeoutMs() + "ms");
            }

            if (!result.isSuccess()) {
                return SkillResponse.error("EXECUTION_ERROR",
                        "Script failed with exit code " + result.exitCode() + ": " + result.error());
            }

            String output = result.output();
            if (metadata.getOutput() != null && metadata.getOutput().getFormat().equals("json")) {
                output = formatJsonOutput(output);
            }

            return SkillResponse.builder()
                    .success(true)
                    .textResult(output)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            log.error("Skill execution failed: {}", metadata.getName(), e);
            return SkillResponse.error("EXECUTION_ERROR", e.getMessage());
        }
    }

    private Map<String, String> resolveEnv() {
        Map<String, String> resolved = new java.util.HashMap<>();

        if (metadata.getEnv() != null) {
            for (Map.Entry<String, String> entry : metadata.getEnv().entrySet()) {
                String value = entry.getValue();
                if (value != null && value.startsWith("${") && value.endsWith("}")) {
                    String envKey = value.substring(2, value.length() - 1);
                    String envValue = properties.getEnvSecrets().get(envKey);
                    if (envValue == null) {
                        envValue = System.getenv(envKey);
                    }
                    resolved.put(entry.getKey(), envValue != null ? envValue : "");
                } else {
                    resolved.put(entry.getKey(), value);
                }
            }
        }

        return resolved;
    }

    private String formatJsonOutput(String jsonOutput) {
        if (metadata.getOutput() == null || metadata.getOutput().getTemplate() == null) {
            return jsonOutput;
        }

        try {
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(jsonOutput)
                    .getAsJsonObject();

            String template = metadata.getOutput().getTemplate();
            for (var entry : json.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                String value = entry.getValue().isJsonNull() ? "" :
                        entry.getValue().isJsonArray() ? entry.getValue().toString() :
                                entry.getValue().getAsString();
                template = template.replace(placeholder, value);
            }

            return template;
        } catch (Exception e) {
            log.warn("Failed to format JSON output", e);
            return jsonOutput;
        }
    }
}