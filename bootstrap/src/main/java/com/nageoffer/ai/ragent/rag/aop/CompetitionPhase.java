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

package com.nageoffer.ai.ragent.rag.aop;

public enum CompetitionPhase {
    PRE("赛前", 20),
    DURING("赛中", 10),
    POST("赛后", 15);

    private final String description;
    private final int defaultQps;

    CompetitionPhase(String description, int defaultQps) {
        this.description = description;
        this.defaultQps = defaultQps;
    }

    public String getDescription() {
        return description;
    }

    public int getDefaultQps() {
        return defaultQps;
    }
}
