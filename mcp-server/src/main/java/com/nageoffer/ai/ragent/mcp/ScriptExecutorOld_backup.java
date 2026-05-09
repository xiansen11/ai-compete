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

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class ScriptExecutor {

    private static final Map<String, String> INTERPRETER_COMMANDS = Map.of(
            "python3", "python3",
            "python", "python",
            "node", "node",
            "bash", "bash",
            "powershell", "powershell"
    );

    private final SkillProperties properties;

    public ScriptExecutor(SkillProperties properties) {
        this.properties = properties;
    }

    public ScriptExecutionResult execute(ScriptConfig config,
                                         Map<String, Object> parameters,
                                         Map<String, String> env,
                                         Path skillRootPath) {
        ProcessBuilder builder = new ProcessBuilder();

        Path workDir = skillRootPath.resolve(config.getWorkingDir());
        builder.directory(workDir.toFile());

        builder.command(buildCommand(config, parameters));

        Map<String, String> processEnv = new HashMap<>(System.getenv());
        if (env != null) {
            processEnv.putAll(env);
        }
        builder.environment().putAll(processEnv);

        builder.redirectErrorStream(false);

        try {
            Process process = builder.start();

            String input = serializeParameters(parameters);
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = process.getOutputStream()) {
                    os.write(input.getBytes(StandardCharsets.UTF_8));
                }
            }

            CompletableFuture<String> outputFuture = readStreamAsync(process.getInputStream());
            CompletableFuture<String> errorFuture = readStreamAsync(process.getErrorStream());

            long timeoutMs = config.getTimeoutMs() > 0 ? config.getTimeoutMs() : properties.getScriptTimeout();
            boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);

            if (!finished) {
                process.destroyForcibly();
                return ScriptExecutionResult.timeout();
            }

            String output = outputFuture.get(5, TimeUnit.SECONDS);
            String error = errorFuture.get(5, TimeUnit.SECONDS);

            return new ScriptExecutionResult(
                    process.exitValue(),
                    output,
                    error,
                    false
            );
        } catch (TimeoutException e) {
            log.error("Script execution timeout: {}", config.getEntry());
            return ScriptExecutionResult.timeout();
        } catch (Exception e) {
            log.error("Script execution failed: {}", config.getEntry(), e);
            return ScriptExecutionResult.error(e.getMessage());
        }
    }

    private List<String> buildCommand(ScriptConfig config, Map<String, Object> parameters) {
        List<String> cmd = new ArrayList<>();
        String interpreter = INTERPRETER_COMMANDS.getOrDefault(
                config.getInterpreter(), config.getInterpreter());
        cmd.add(interpreter);

        String entryPath = config.getEntry();
        if (entryPath.startsWith("./") || entryPath.startsWith(".\\")) {
            entryPath = entryPath.substring(2);
        }
        cmd.add(entryPath);

        if (parameters != null && !parameters.isEmpty()) {
            for (Map.Entry<String, Object> entry : parameters.entrySet()) {
                cmd.add("--" + entry.getKey());
                Object value = entry.getValue();
                cmd.add(value != null ? String.valueOf(value) : "");
            }
        }

        return cmd;
    }

    private String serializeParameters(Map<String, Object> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return null;
        }
        com.google.gson.Gson gson = new com.google.gson.Gson();
        return gson.toJson(parameters);
    }

    private CompletableFuture<String> readStreamAsync(InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
            } catch (IOException e) {
                log.warn("Failed to read stream", e);
            }
            return sb.toString();
        });
    }

    public record ScriptExecutionResult(
            int exitCode,
            String output,
            String error,
            boolean isTimeout
    ) {
        public static ScriptExecutionResult timeout() {
            return new ScriptExecutionResult(-1, null, null, true);
        }

        public static ScriptExecutionResult error(String message) {
            return new ScriptExecutionResult(-1, null, message, false);
        }

        public boolean isSuccess() {
            return !isTimeout && exitCode == 0;
        }
    }
}