# Tesseract OCR 使用示例

## 📋 功能概述

本项目已集成 **Tesseract OCR** 引擎，实现了智能的图片文字识别功能。

### 核心特性

✅ **自动检测**：自动识别上传文件是否为图片格式  
✅ **智能触发**：仅对图片文件启用 OCR 识别  
✅ **多语言支持**：默认支持中英文混合识别  
✅ **无缝集成**：与现有文档解析流程完全兼容  

---

## 🚀 快速开始

### 1. 准备 OCR 语言数据

在应用运行目录创建 `tessdata` 文件夹并下载语言数据：

```bash
# 创建目录
mkdir tessdata

# 下载中文和英文语言数据
# 下载地址：https://github.com/tesseract-ocr/tessdata
# - chi_sim.traineddata (简体中文)
# - eng.traineddata (英文)
```

**文件结构**：
```
ragent-main/
└── tessdata/
    ├── chi_sim.traineddata    # 简体中文
    └── eng.traineddata        # 英文
```

### 2. 确认配置

检查 `application.yaml` 配置：

```yaml
ocr:
  tesseract:
    enabled: true
    datapath: ./tessdata
    language: chi_sim+eng
```

### 3. 启动应用

```bash
cd ragent-main
mvn spring-boot:run -pl bootstrap
```

**查看日志确认 OCR 已启用**：
```
INFO  - Tesseract OCR 已配置 - 数据路径：./tessdata, 语言：chi_sim+eng, 启用状态：true
```

---

## 📸 使用示例

### 示例 1：上传测试图片

```bash
# 创建测试图片（包含中文文字）
# 可以使用截图工具或准备一张现有的图片

# 上传到知识库
curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks/upload" \
  -F "pipelineId=1" \
  -F "file=@test-image.png" \
  -F "metadata={\"type\":\"ocr-test\",\"description\":\"测试 OCR 识别\"}"
```

### 示例 2：批量上传多张图片

```bash
# 上传多张图片
for img in images/*.png; do
  curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks/upload" \
    -F "pipelineId=1" \
    -F "file=@$img" \
    -F "metadata={\"source\":\"batch-upload\"}"
done
```

### 示例 3：混合上传（图片 + 文档）

```bash
# 同时上传图片和 PDF 文档
# 系统会自动选择解析器：
# - 图片 → OCR 识别
# - PDF → Tika 解析

curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks/upload" \
  -F "pipelineId=1" \
  -F "file=@document.pdf" \
  -F "metadata={}"

curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks/upload" \
  -F "pipelineId=1" \
  -F "file=@image.png" \
  -F "metadata={}"
```

---

## 🔍 工作流程

```
上传文件
    ↓
类型检测（ImageTypeDetector）
    ↓
是图片？
    ├─ YES → OCR 解析器 (OcrDocumentParser)
    │         ↓
    │      Tesseract 识别
    │         ↓
    │      提取文字
    │
    └─ NO  → Tika 解析器 (TikaDocumentParser)
              ↓
           常规解析
              ↓
           提取文字
```

### 自动路由逻辑

在 [`ParserNode.selectParser()`](file:///e:/code/java/ragent-main/ragent-main/bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/ParserNode.java#L254-L280) 中实现：

```java
private DocumentParser selectParser(String mimeType, String fileName) {
    boolean isImage = ImageTypeDetector.isImage(mimeType, fileName);
    
    if (isImage) {
        log.debug("检测到图片文件，使用 OCR 解析器");
        DocumentParser ocrParser = parserSelector.select(ParserType.OCR.getType());
        if (ocrParser != null && ocrParser instanceof OcrDocumentParser) {
            ((OcrDocumentParser) ocrParser).configure();
            return ocrParser;
        }
        log.warn("OCR 解析器不可用，回退到 Tika 解析器");
    }
    
    log.debug("使用 Tika 解析器");
    return parserSelector.select(ParserType.TIKA.getType());
}
```

---

## 📊 支持的图片格式

| 格式 | 扩展名 | MIME 类型 | 支持状态 |
|------|--------|-----------|----------|
| PNG | `.png` | `image/png` | ✅ 完全支持 |
| JPEG | `.jpg`, `.jpeg` | `image/jpeg` | ✅ 完全支持 |
| GIF | `.gif` | `image/gif` | ✅ 完全支持 |
| BMP | `.bmp` | `image/bmp` | ✅ 完全支持 |
| WebP | `.webp` | `image/webp` | ✅ 完全支持 |
| TIFF | `.tiff`, `.tif` | `image/tiff` | ✅ 完全支持 |

---

## 🎯 最佳实践

### 1. 图片质量优化

**推荐**：
- 分辨率：300-600 DPI
- 格式：PNG 或高质量 JPEG
- 对比度：文字清晰，背景干净
- 方向：文字水平排列

**不推荐**：
- ❌ 模糊/低分辨率图片
- ❌ 手写体（识别率低）
- ❌ 倾斜/旋转的文字
- ❌ 复杂背景

### 2. 性能调优

```yaml
# JVM 参数建议（识别大量图片时）
JAVA_OPTS="-Xms2g -Xmx4g -XX:+UseG1GC"
```

### 3. 错误处理

**常见错误及解决方案**：

| 错误信息 | 原因 | 解决方案 |
|---------|------|----------|
| `找不到 tessdata` | 语言数据文件缺失 | 下载 `.traineddata` 文件到正确目录 |
| `OCR 识别失败` | 图片格式不支持 | 检查图片格式是否在支持列表中 |
| `识别结果为空` | 图片质量差或文字不清晰 | 提高图片质量，确保文字清晰 |
| `中文乱码` | 缺少中文语言包 | 下载 `chi_sim.traineddata` |

---

## 🔧 高级配置

### 自定义语言

修改 `application.yaml`：

```yaml
ocr:
  tesseract:
    language: eng  # 仅英文
    # language: chi_tra+eng  # 繁体中文 + 英文
    # language: jpn+eng  # 日文 + 英文
```

### 禁用 OCR

```yaml
ocr:
  tesseract:
    enabled: false  # 完全禁用 OCR 功能
```

### 自定义数据路径

```yaml
ocr:
  tesseract:
    datapath: /opt/tesseract/tessdata  # 绝对路径
```

---

## 📝 日志示例

**成功识别**：
```
DEBUG - 检测到图片文件，使用 OCR 解析器 - MIME: image/png, 文件名：test.png
INFO  - Tesseract OCR 已配置 - 数据路径：./tessdata, 语言：chi_sim+eng, 启用状态：true
INFO  - OCR 识别成功，提取文本长度：1234
DEBUG - 解析文本长度=1234
```

**回退到 Tika**：
```
DEBUG - 使用 Tika 解析器 - MIME: application/pdf, 文件名：document.pdf
```

---

## 🧪 测试建议

### 单元测试

```java
@SpringBootTest
public class OcrDocumentParserTest {
    
    @Autowired
    private OcrDocumentParser ocrParser;
    
    @Test
    public void testChineseImageRecognition() throws Exception {
        byte[] imageBytes = Files.readAllBytes(Paths.get("test-chinese.png"));
        ParseResult result = ocrParser.parse(imageBytes, "image/png", null);
        
        assertNotNull(result);
        assertTrue(result.text().length() > 0);
        System.out.println("识别结果：" + result.text());
    }
}
```

---

## 📚 相关文档

- [OCR 配置指南](./ocr-configuration-guide.md)
- [Tika 文档解析器](../bootstrap/src/main/java/com/nageoffer/ai/ragent/core/parser/TikaDocumentParser.java)
- [ParserNode 源码](../bootstrap/src/main/java/com/nageoffer/ai/ragent/ingestion/node/ParserNode.java)

---

## 🛠️ 故障排查

### 检查清单

- [ ] tessdata 目录是否存在
- [ ] 语言数据文件是否完整
- [ ] `application.yaml` 配置是否正确
- [ ] 日志中是否有 "Tesseract OCR 已配置" 信息
- [ ] 图片格式是否支持
- [ ] JVM 内存是否充足

### 获取帮助

查看完整日志：
```bash
# 启用 DEBUG 日志
logging:
  level:
    com.nageoffer.ai.ragent.core.parser: DEBUG
    com.nageoffer.ai.ragent.ingestion.node: DEBUG
```

---

**创建时间**: 2026-04-18  
**维护者**: RAGent Team
