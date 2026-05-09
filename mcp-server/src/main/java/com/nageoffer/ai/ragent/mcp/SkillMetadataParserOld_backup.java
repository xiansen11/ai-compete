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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class SkillMetadataParser {

    private static final String FRONT_MATTER_DELIMITER = "---";
    private static final Gson GSON = new Gson();

    public static SkillMetadata parse(Path skillMdPath) throws IOException {
        String content = Files.readString(skillMdPath);
        return parseContent(content);
    }

    public static SkillMetadata parseContent(String content) {
        int firstDelimiter = content.indexOf(FRONT_MATTER_DELIMITER);
        int secondDelimiter = content.indexOf(FRONT_MATTER_DELIMITER, firstDelimiter + 3);

        if (firstDelimiter == -1 || secondDelimiter == -1) {
            throw new IllegalArgumentException("Invalid SKILL.md format: missing front matter delimiters");
        }

        String yamlContent = content.substring(firstDelimiter + 3, secondDelimiter).trim();
        JsonObject root = JsonParser.parseString(yamlContent).getAsJsonObject();

        return parseMetadata(root);
    }

    private static SkillMetadata parseMetadata(JsonObject root) {
        SkillMetadataBuilder builder = SkillMetadata.builder();

        builder.name(getString(root, "name", "unknown"));
        builder.version(getString(root, "version", "1.0.0"));
        builder.displayName(getString(root, "displayName", builder.name()));
        builder.description(getString(root, "description", ""));
        builder.author(getString(root, "author", "unknown"));
        builder.icon(getString(root, "icon", null));

        String createdAt = getString(root, "createdAt", null);
        String updatedAt = getString(root, "updatedAt", null);
        if (createdAt != null) {
            builder.createdAt(parseInstant(createdAt));
        }
        if (updatedAt != null) {
            builder.updatedAt(parseInstant(updatedAt));
        }

        JsonObject permissionObj = getObject(root, "permission");
        if (permissionObj != null) {
            builder.permission(parsePermission(permissionObj));
        } else {
            builder.permission(Permission.builder().requireAdmin(false).build());
        }

        builder.parameters(parseParameters(root));
        builder.script(parseScriptConfig(root));
        builder.env(parseEnv(root));
        builder.resources(parseResources(root));
        builder.output(parseOutputConfig(root));

        return builder.build();
    }

    private static Permission parsePermission(JsonObject obj) {
        PermissionBuilder builder = Permission.builder();
        builder.requireAdmin(getBoolean(obj, "requireAdmin", true));

        List<String> allowedUsers = new ArrayList<>();
        if (obj.has("allowedUsers") && obj.get("allowedUsers").isJsonArray()) {
            obj.getAsJsonArray("allowedUsers").forEach(e -> allowedUsers.add(e.getAsString()));
        }
        builder.allowedUsers(allowedUsers);

        List<String> allowedRoles = new ArrayList<>();
        if (obj.has("allowedRoles") && obj.get("allowedRoles").isJsonArray()) {
            obj.getAsJsonArray("allowedRoles").forEach(e -> allowedRoles.add(e.getAsString()));
        }
        builder.allowedRoles(allowedRoles);

        return builder.build();
    }

    private static List<SkillParameter> parseParameters(JsonObject root) {
        List<SkillParameter> params = new ArrayList<>();
        if (!root.has("parameters") || !root.get("parameters").isJsonArray()) {
            return params;
        }

        root.getAsJsonArray("parameters").forEach(e -> {
            JsonObject paramObj = e.getAsJsonObject();
            SkillParameter param = SkillParameter.builder()
                    .name(getString(paramObj, "name", ""))
                    .type(getString(paramObj, "type", "string"))
                    .description(getString(paramObj, "description", ""))
                    .required(getBoolean(paramObj, "required", false))
                    .defaultValue(getDefaultValue(paramObj))
                    .enumValues(parseEnumValues(paramObj))
                    .build();
            params.add(param);
        });

        return params;
    }

    private static Object getDefaultValue(JsonObject obj) {
        if (!obj.has("default")) {
            return null;
        }
        var defaultVal = obj.get("default");
        if (defaultVal.isJsonNull()) {
            return null;
        }
        if (defaultVal.isJsonPrimitive()) {
            var prim = defaultVal.getAsJsonPrimitive();
            if (prim.isNumber()) {
                return prim.getAsNumber();
            }
            if (prim.isBoolean()) {
                return prim.getAsBoolean();
            }
            return prim.getAsString();
        }
        return defaultVal.toString();
    }

    private static List<String> parseEnumValues(JsonObject obj) {
        List<String> enums = new ArrayList<>();
        if (!obj.has("enumValues") || !obj.get("enumValues").isJsonArray()) {
            return enums;
        }
        obj.getAsJsonArray("enumValues").forEach(e -> enums.add(e.getAsString()));
        return enums;
    }

    private static ScriptConfig parseScriptConfig(JsonObject root) {
        ScriptConfigBuilder builder = ScriptConfig.builder();
        if (!root.has("script") || !root.get("script").isJsonObject()) {
            return builder.build();
        }

        JsonObject scriptObj = root.getAsJsonObject("script");
        builder.entry(getString(scriptObj, "entry", ""));
        builder.interpreter(getString(scriptObj, "interpreter", "python3"));
        builder.workingDir(getString(scriptObj, "workingDir", "."));
        builder.timeoutMs(getLong(scriptObj, "timeout", 30000));
        builder.retry(getInt(scriptObj, "retry", 2));

        return builder.build();
    }

    private static Map<String, String> parseEnv(JsonObject root) {
        Map<String, String> env = new HashMap<>();
        if (!root.has("env") || !root.get("env").isJsonObject()) {
            return env;
        }

        root.getAsJsonObject("env").entrySet().forEach(e -> {
            env.put(e.getKey(), e.getValue().getAsString());
        });

        return env;
    }

    private static List<Resource> parseResources(JsonObject root) {
        List<Resource> resources = new ArrayList<>();
        if (!root.has("resources") || !root.get("resources").isJsonArray()) {
            return resources;
        }

        root.getAsJsonArray("resources").forEach(e -> {
            JsonObject resObj = e.getAsJsonObject();
            Resource res = Resource.builder()
                    .path(getString(resObj, "path", ""))
                    .description(getString(resObj, "description", ""))
                    .build();
            resources.add(res);
        });

        return resources;
    }

    private static OutputConfig parseOutputConfig(JsonObject root) {
        OutputConfigBuilder builder = OutputConfig.builder();
        if (!root.has("output") || !root.get("output").isJsonObject()) {
            return builder.format("markdown").build();
        }

        JsonObject outputObj = root.getAsJsonObject("output");
        builder.format(getString(outputObj, "format", "markdown"));

        int templateStart = root.toString().indexOf("template:");
        String template = "";
        if (templateStart != -1) {
            int yamlStart = root.toString().indexOf("---");
            int yamlEnd = root.toString().indexOf("---", yamlStart + 3);
            if (yamlEnd != -1) {
                String yamlContent = root.toString().substring(yamlStart + 9, yamlEnd);
                int backtickPos = yamlContent.indexOf("```");
                if (backtickPos != -1) {
                    template = yamlContent.substring(0, backtickPos).trim();
                }
            }
        }
        builder.template(template);

        return builder.build();
    }

    private static String getString(JsonObject obj, String key, String defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsString();
    }

    private static boolean getBoolean(JsonObject obj, String key, boolean defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsBoolean();
    }

    private static int getInt(JsonObject obj, String key, int defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsInt();
    }

    private static long getLong(JsonObject obj, String key, long defaultValue) {
        if (!obj.has(key) || obj.get(key).isJsonNull()) {
            return defaultValue;
        }
        return obj.get(key).getAsLong();
    }

    private static JsonObject getObject(JsonObject obj, String key) {
        if (!obj.has(key) || obj.get(key).isJsonNull() || !obj.get(key).isJsonObject()) {
            return null;
        }
        return obj.get(key).getAsJsonObject();
    }

    private static Instant parseInstant(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                return Instant.from(DateTimeFormatter.ISO_DATE_TIME.parse(dateStr));
            } catch (Exception ex) {
                return Instant.now();
            }
        }
    }
}