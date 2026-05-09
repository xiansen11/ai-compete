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

package com.nageoffer.ai.ragent.ingestion.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 图片类型检测工具类
 * <p>
 * 用于检测文件是否为图片格式，支持常见的图片格式
 * </p>
 */
@Slf4j
public class ImageTypeDetector {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            "png", "jpg", "jpeg", "gif", "bmp", "webp", "tiff", "tif"
    );

    private static final List<String> IMAGE_MIME_PREFIXES = Arrays.asList(
            "image/png",
            "image/jpeg",
            "image/jpg",
            "image/gif",
            "image/bmp",
            "image/webp",
            "image/tiff",
            "image/x-tiff"
    );

    private ImageTypeDetector() {
    }

    /**
     * 检测是否为图片格式
     *
     * @param mimeType MIME 类型
     * @return 是否为图片
     */
    public static boolean isImage(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return false;
        }
        return IMAGE_MIME_PREFIXES.stream()
                .anyMatch(prefix -> mimeType.toLowerCase().startsWith(prefix) || mimeType.toLowerCase().equals(prefix));
    }

    /**
     * 检测是否为图片格式（通过文件扩展名）
     *
     * @param fileName 文件名
     * @return 是否为图片
     */
    public static boolean isImageByExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String extension = getFileExtension(fileName);
        return IMAGE_EXTENSIONS.contains(extension.toLowerCase());
    }

    /**
     * 检测是否为图片格式（通过 MIME 类型或文件扩展名）
     *
     * @param mimeType MIME 类型
     * @param fileName 文件名
     * @return 是否为图片
     */
    public static boolean isImage(String mimeType, String fileName) {
        return isImage(mimeType) || isImageByExtension(fileName);
    }

    /**
     * 获取文件扩展名
     *
     * @param fileName 文件名
     * @return 文件扩展名（小写），如果没有扩展名则返回空字符串
     */
    public static String getFileExtension(String fileName) {
        if (!StringUtils.hasText(fileName)) {
            return "";
        }
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 获取标准的图片 MIME 类型
     *
     * @param fileName 文件名
     * @return MIME 类型，如果无法识别则返回 null
     */
    public static String getImageMimeType(String fileName) {
        String ext = getFileExtension(fileName);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            case "webp" -> "image/webp";
            case "tiff", "tif" -> "image/tiff";
            default -> null;
        };
    }

    /**
     * 获取所有支持的图片扩展名
     *
     * @return 图片扩展名列表
     */
    public static Set<String> getSupportedExtensions() {
        return IMAGE_EXTENSIONS;
    }

    /**
     * 获取所有支持的图片 MIME 类型前缀
     *
     * @return MIME 类型前缀列表
     */
    public static List<String> getSupportedMimeTypes() {
        return IMAGE_MIME_PREFIXES;
    }
}
