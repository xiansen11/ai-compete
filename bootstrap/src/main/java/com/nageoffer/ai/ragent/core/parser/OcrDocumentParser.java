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

package com.nageoffer.ai.ragent.core.parser;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

/**
 * Tesseract OCR 文档解析器
 * <p>
 * 使用 Tesseract OCR 引擎进行图片文字识别
 * 支持多种图片格式：PNG、JPG、JPEG、GIF、BMP、WEBP 等
 * </p>
 */
@Slf4j
@Component
public class OcrDocumentParser implements DocumentParser {

    private final ITesseract tesseract;

    @Value("${ocr.tesseract.datapath:./tessdata}")
    private String datapath;

    @Value("${ocr.tesseract.language:chi_sim+eng}")
    private String language;

    @Value("${ocr.tesseract.enabled:true}")
    private boolean enabled;

    public OcrDocumentParser() {
        this.tesseract = new Tesseract();
    }

    @Override
    public String getParserType() {
        return ParserType.OCR.getType();
    }

    @Override
    public ParseResult parse(byte[] content, String mimeType, Map<String, Object> options) {
        if (!enabled) {
            log.warn("OCR 功能未启用，跳过识别");
            return ParseResult.ofText("");
        }

        if (content == null || content.length == 0) {
            return ParseResult.ofText("");
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) {
                log.error("无法读取图片内容，可能已损坏或格式不支持");
                throw new ServiceException("无效的图片格式");
            }

            String text = tesseract.doOCR(image);
            log.info("OCR 识别成功，提取文本长度：{}", text.length());
            return ParseResult.ofText(text);
        } catch (TesseractException e) {
            log.error("Tesseract OCR 识别失败：{}", e.getMessage(), e);
            throw new ServiceException("OCR 识别失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("图片处理失败：{}", e.getMessage(), e);
            throw new ServiceException("图片处理失败：" + e.getMessage());
        }
    }

    @Override
    public String extractText(InputStream stream, String fileName) {
        if (!enabled) {
            log.warn("OCR 功能未启用，跳过识别");
            return "";
        }

        try {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                log.error("无法读取图片文件：{}", fileName);
                throw new ServiceException("无效的图片文件：" + fileName);
            }

            String text = tesseract.doOCR(image);
            log.info("从图片提取文本成功：{}, 文本长度：{}", fileName, text.length());
            return text;
        } catch (TesseractException e) {
            log.error("从图片提取文本失败：{}", fileName, e);
            throw new ServiceException("OCR 识别失败：" + e.getMessage());
        } catch (Exception e) {
            log.error("读取图片文件失败：{}", fileName, e);
            throw new ServiceException("读取图片文件失败：" + fileName);
        }
    }

    @Override
    public boolean supports(String mimeType) {
        if (!enabled) {
            return false;
        }
        return mimeType != null && (
                mimeType.startsWith("image/") ||
                        "image/png".equals(mimeType) ||
                        "image/jpeg".equals(mimeType) ||
                        "image/jpg".equals(mimeType) ||
                        "image/gif".equals(mimeType) ||
                        "image/bmp".equals(mimeType) ||
                        "image/webp".equals(mimeType) ||
                        "image/tiff".equals(mimeType)
        );
    }

    /**
     * 配置 Tesseract 实例
     * 在应用启动时调用
     */
    public void configure() {
        tesseract.setDatapath(datapath);
        tesseract.setLanguage(language);
        log.info("Tesseract OCR 已配置 - 数据路径：{}, 语言：{}, 启用状态：{}", datapath, language, enabled);
    }
}
