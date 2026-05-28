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

package com.nageoffer.ai.ragent.rag.core.mcp.client;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class MCPClientPropertiesTests {

    @Test
    void bindsExtendedServerConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("rag.mcp.servers[0].name", "github")
                .withProperty("rag.mcp.servers[0].url", "https://api.githubcopilot.com")
                .withProperty("rag.mcp.servers[0].endpoint", "/mcp")
                .withProperty("rag.mcp.servers[0].enabled", "false")
                .withProperty("rag.mcp.servers[0].optional", "true")
                .withProperty("rag.mcp.servers[0].tool-prefix", "github")
                .withProperty("rag.mcp.servers[0].required-env[0]", "GITHUB_PERSONAL_ACCESS_TOKEN")
                .withProperty("rag.mcp.servers[0].headers.Authorization", "Bearer token");

        MCPClientProperties properties = Binder.get(environment)
                .bind("rag.mcp", MCPClientProperties.class)
                .orElseThrow(() -> new IllegalStateException("rag.mcp binding failed"));
        MCPClientProperties.ServerConfig server = properties.getServers().get(0);

        assertEquals("github", server.getName());
        assertFalse(server.isEnabled());
        assertEquals("github", server.getToolPrefix());
        assertEquals("GITHUB_PERSONAL_ACCESS_TOKEN", server.getRequiredEnv().get(0));
        assertEquals("Bearer token", server.getHeaders().get("Authorization"));
    }
}
