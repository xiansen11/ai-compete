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

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.mcp.core.MCPToolDefinition;
import com.nageoffer.ai.ragent.mcp.core.MCPToolExecutor;
import com.nageoffer.ai.ragent.mcp.core.MCPToolRequest;
import com.nageoffer.ai.ragent.mcp.core.MCPToolResponse;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SkillMCPToolAdapter implements MCPToolExecutor {

    private final SkillExecutor skillExecutor;
    private final SkillRegistry skillRegistry;
    private MCPToolDefinition toolDefinition;

    @PostConstruct
    public void init() {
        this.toolDefinition = convertToMCPToolDefinition(skillExecutor.getMetadata());
        skillRegistry.register(skillExecutor);
        log.info("SkillMCPToolAdapter initialized for skill: {}", skillExecutor.getMetadata().getName());
    }

    @Override
    public MCPToolDefinition getToolDefinition() {
        return toolDefinition;
    }

    @Override
    public MCPToolResponse execute(MCPToolRequest request) {
        try {
            SkillMetadata metadata = skillExecutor.getMetadata();

            if (!checkPermission(metadata.getPermission(), request)) {
                return MCPToolResponse.error(
                        getToolId(),
                        "PERMISSION_DENIED",
                        "您没有权限调用此技能"
                );
            }

            Long userId = parseUserId(request.getUserId());

            SkillRequest skillRequest = SkillRequest.builder()
                    .skillName(metadata.getName())
                    .userId(userId)
                    .userRole(getCurrentUserRole())
                    .parameters(request.getParameters())
                    .build();

            long startTime = System.currentTimeMillis();
            SkillResponse response = skillExecutor.execute(skillRequest);
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("Skill executed - skill: {}, user: {}, time: {}ms, success: {}",
                    metadata.getName(), userId, executionTime, response.isSuccess());

            if (response.isSuccess()) {
                return MCPToolResponse.success(getToolId(), response.getTextResult());
            } else {
                return MCPToolResponse.error(
                        getToolId(),
                        response.getErrorCode(),
                        response.getErrorMessage()
                );
            }
        } catch (Exception e) {
            log.error("Skill execution failed: {}", getToolId(), e);
            return MCPToolResponse.error(getToolId(), "EXECUTION_ERROR", e.getMessage());
        }
    }

    private boolean checkPermission(Permission permission, MCPToolRequest request) {
        if (permission == null || !permission.isRequireAdmin()) {
            return true;
        }

        String userRole = getCurrentUserRole();
        if (userRole == null) {
            return false;
        }

        if (permission.getAllowedRoles() != null &&
                !permission.getAllowedRoles().isEmpty() &&
                permission.getAllowedRoles().contains(userRole.toUpperCase())) {
            return true;
        }

        if (permission.getAllowedUsers() != null &&
                !permission.getAllowedUsers().isEmpty()) {
            Long userId = parseUserId(request.getUserId());
            if (userId != null && permission.getAllowedUsers().contains(String.valueOf(userId))) {
                return true;
            }
        }

        return false;
    }

    private String getCurrentUserRole() {
        try {
            if (StpUtil.isLogin()) {
                Object role = StpUtil.getSession().get("role");
                if (role != null) {
                    return role.toString().toUpperCase();
                }
                return StpUtil.getRoleList().stream()
                        .findFirst()
                        .map(String::toUpperCase)
                        .orElse("USER");
            }
        } catch (Exception e) {
            log.debug("Could not get user role from Sa-Token", e);
        }
        return null;
    }

    private Long parseUserId(String userIdStr) {
        if (userIdStr == null || userIdStr.isBlank()) {
            try {
                if (StpUtil.isLogin()) {
                    return StpUtil.getLoginIdAsLong();
                }
            } catch (Exception ignored) {
            }
            return null;
        }
        try {
            return Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private MCPToolDefinition convertToMCPToolDefinition(SkillMetadata metadata) {
        Map<String, MCPToolDefinition.ParameterDef> parameters = new LinkedHashMap<>();

        if (metadata.getParameters() != null) {
            for (SkillParameter param : metadata.getParameters()) {
                parameters.put(param.getName(), MCPToolDefinition.ParameterDef.builder()
                        .description(param.getDescription())
                        .type(param.getType())
                        .required(param.isRequired())
                        .defaultValue(param.getDefaultValue())
                        .enumValues(param.getEnumValues())
                        .build());
            }
        }

        boolean requireUserId = metadata.getPermission() != null && metadata.getPermission().isRequireAdmin();

        return MCPToolDefinition.builder()
                .toolId(metadata.getName())
                .description(metadata.getDescription())
                .parameters(parameters)
                .requireUserId(requireUserId)
                .build();
    }
}