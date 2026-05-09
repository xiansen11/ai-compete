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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class SkillParser {

    private static final String FRONT_MATTER_DELIMITER = "---";

    public static SkillDefinition parse(Path skillMdPath, String content) {
        SkillMetadata metadata = parseFrontMatter(content);
        String skillContent = extractContent(content);
        return new SkillDefinition(metadata, skillContent);
    }

    public static SkillMetadata parseFrontMatter(String content) {
        int firstDelimiter = content.indexOf(FRONT_MATTER_DELIMITER);
        int secondDelimiter = content.indexOf(FRONT_MATTER_DELIMITER, firstDelimiter + 3);

        if (firstDelimiter == -1 || secondDelimiter == -1) {
            throw new IllegalArgumentException("Invalid SKILL.md format: missing front matter delimiters");
        }

        String yamlContent = content.substring(firstDelimiter + 3, secondDelimiter).trim();
        return parseYaml(yamlContent);
    }

    private static SkillMetadata parseYaml(String yaml) {
        String name = extractValue(yaml, "name:");
        String description = extractMultilineValue(yaml, "description:");
        String version = extractValue(yaml, "version:", "1.0.0");
        List<String> allowedTools = extractListValue(yaml, "allowed_tools:");
        List<String> tags = extractListValue(yaml, "tags:");

        return new SkillMetadata(name, description, version, allowedTools, tags);
    }

    private static String extractValue(String yaml, String key) {
        return extractValue(yaml, key, null);
    }

    private static String extractValue(String yaml, String key, String defaultValue) {
        String[] lines = yaml.split("\n");
        for (String line : lines) {
            if (line.trim().startsWith(key)) {
                return line.trim().substring(key.length()).trim();
            }
        }
        return defaultValue;
    }

    private static String extractMultilineValue(String yaml, String key) {
        int keyIndex = yaml.indexOf(key);
        if (keyIndex == -1) {
            return "";
        }

        int startQuote = yaml.indexOf("'", keyIndex);
        int endQuote = -1;
        if (startQuote != -1) {
            endQuote = yaml.indexOf("'", startQuote + 1);
        } else {
            startQuote = keyIndex + key.length();
            int endLine = yaml.indexOf("\n", startQuote);
            if (endLine == -1) {
                return yaml.substring(startQuote).trim();
            }
            return yaml.substring(startQuote, endLine).trim();
        }

        if (endQuote != -1) {
            return yaml.substring(startQuote + 1, endQuote);
        }
        return "";
    }

    private static List<String> extractListValue(String yaml, String key) {
        List<String> values = new ArrayList<>();
        int keyIndex = yaml.indexOf(key);
        if (keyIndex == -1) {
            return values;
        }

        int lineEnd = yaml.indexOf("\n", keyIndex);
        String line = lineEnd == -1 ? yaml.substring(keyIndex) : yaml.substring(keyIndex, lineEnd);

        int bracketStart = line.indexOf('[');
        int bracketEnd = line.indexOf(']');

        if (bracketStart == -1 || bracketEnd == -1) {
            return values;
        }

        String content = line.substring(bracketStart + 1, bracketEnd);
        String[] items = content.split(",");
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }

        return values;
    }

    private static String extractContent(String content) {
        int firstDelimiter = content.indexOf(FRONT_MATTER_DELIMITER);
        int secondDelimiter = content.indexOf(FRONT_MATTER_DELIMITER, firstDelimiter + 3);

        if (firstDelimiter == -1 || secondDelimiter == -1) {
            return content;
        }

        return content.substring(secondDelimiter + 3).trim();
    }
}