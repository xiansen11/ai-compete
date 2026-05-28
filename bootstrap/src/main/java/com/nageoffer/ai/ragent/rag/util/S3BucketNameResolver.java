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

package com.nageoffer.ai.ragent.rag.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class S3BucketNameResolver {

    private static final int MAX_BUCKET_LENGTH = 63;
    private static final int HASH_LENGTH = 8;
    private static final int MAX_BASE_LENGTH = MAX_BUCKET_LENGTH - HASH_LENGTH - 1;

    private S3BucketNameResolver() {
    }

    public static String resolve(String rawName) {
        if (rawName == null || rawName.isBlank()) {
            throw new IllegalArgumentException("bucket name cannot be blank");
        }
        String trimmed = rawName.trim();
        if (isValidBucketName(trimmed)) {
            return trimmed;
        }

        String sanitized = trimmed.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.-]", "-")
                .replaceAll("[.-]{2,}", "-")
                .replaceAll("^[^a-z0-9]+", "")
                .replaceAll("[^a-z0-9]+$", "");
        if (sanitized.isBlank()) {
            sanitized = "kb";
        }
        if (looksLikeIpv4(sanitized)) {
            sanitized = "kb-" + sanitized.replace('.', '-');
        }
        if (sanitized.length() > MAX_BASE_LENGTH) {
            sanitized = sanitized.substring(0, MAX_BASE_LENGTH);
        }
        sanitized = sanitized.replaceAll("[^a-z0-9]+$", "");
        if (sanitized.length() < 3) {
            sanitized = (sanitized + "-kb").substring(0, 3);
        }

        return sanitized + "-" + shortHash(trimmed);
    }

    private static boolean isValidBucketName(String value) {
        if (value.length() < 3 || value.length() > MAX_BUCKET_LENGTH) {
            return false;
        }
        if (!value.equals(value.toLowerCase(Locale.ROOT))) {
            return false;
        }
        if (!value.matches("^[a-z0-9][a-z0-9.-]*[a-z0-9]$")) {
            return false;
        }
        if (value.contains("..") || value.contains(".-") || value.contains("-.")) {
            return false;
        }
        return !looksLikeIpv4(value);
    }

    private static boolean looksLikeIpv4(String value) {
        return value.matches("^\\d+\\.\\d+\\.\\d+\\.\\d+$");
    }

    private static String shortHash(String rawName) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawName.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, HASH_LENGTH / 2);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
