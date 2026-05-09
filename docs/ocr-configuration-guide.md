# Tesseract OCR 配置指南

## 📦  tessdata 语言数据文件准备

Tesseract OCR 需要语言数据文件才能进行文字识别。本项目默认使用**中英文混合识别**。

### 方法一：手动下载（推荐）

1. **创建 tessdata 目录**
   ```bash
   # 在项目根目录或应用运行目录创建 tessdata 文件夹
   mkdir tessdata
   ```

2. **下载语言数据文件**
   
   从以下地址下载所需的语言包：
   - **简体中文**：https://github.com/tesseract-ocr/tessdata/raw/main/chi_sim.traineddata
   - **英文**：https://github.com/tesseract-ocr/tessdata/raw/main/eng.traineddata
   - **繁体中文**：https://github.com/tesseract-ocr/tessdata/raw/main/chi_tra.traineddata

3. **放置文件**
   ```
   ragent-main/
   └── tessdata/
       ├── chi_sim.traineddata    # 简体中文（必需）
       └── eng.traineddata        # 英文（必需）
   ```

### 方法二：使用 Maven 依赖自动获取

Tess4J 依赖已经包含了部分语言数据，但可能不是最新版本。如果自动识别失败，请使用方法一手动下载。

---

## ⚙️  配置说明

在 `application.yaml` 中的配置项：

```yaml
ocr:
  tesseract:
    enabled: true              # 是否启用 OCR 功能
    datapath: ./tessdata       # tessdata 目录路径（相对于应用运行目录）
    language: chi_sim+eng      # 识别语言（+ 表示混合识别）
```

### 常用语言代码：

| 语言 | 代码 | 说明 |
|------|------|------|
| 简体中文 | `chi_sim` | 识别简体中文字符 |
| 英文 | `eng` | 识别英文字符 |
| 繁体中文 | `chi_tra` | 识别繁体中文字符 |
| 日文 | `jpn` | 识别日文字符 |
| 韩文 | `kor` | 识别韩文字符 |
| 混合识别 | `chi_sim+eng` | 同时识别中英文 |

---

## 🔍  验证安装

启动应用后，查看日志确认 OCR 配置成功：

```
INFO  - Tesseract OCR 已配置 - 数据路径：./tessdata, 语言：chi_sim+eng, 启用状态：true
```

---

## 📸  支持的图片格式

- PNG (.png)
- JPEG (.jpg, .jpeg)
- GIF (.gif)
- BMP (.bmp)
- WebP (.webp)
- TIFF (.tiff, .tif)

---

## 🚀  使用示例

### 上传测试图片

```bash
curl -X POST "http://localhost:9090/api/ragent/ingestion/tasks/upload" \
  -F "pipelineId=1" \
  -F "file=@test-image.png" \
  -F "metadata={\"type\":\"ocr-test\"}"
```

### 自动触发逻辑

- **图片文件** → 自动使用 OCR 识别
- **PDF/Word 等文档** → 使用 Tika 解析
- **扫描版 PDF** → 当前使用 Tika（未来可增强为 OCR）

---

## ⚠️  注意事项

1. **路径问题**：`datapath` 配置的是**运行时路径**，不是源码路径
   - 开发环境：可以放在项目根目录
   - 生产环境：需要放在应用部署目录

2. **文件权限**：确保应用有读取 tessdata 目录的权限

3. **内存占用**：OCR 识别会消耗较多内存，建议 JVM 堆内存至少 2GB

4. **识别精度**：
   - 打印体文字：识别精度高（95%+）
   - 手写体：识别精度较低（60-80%）
   - 模糊/低分辨率图片：可能识别失败

---

## 🔧  故障排查

### 问题 1：OCR 识别失败，提示找不到 tessdata

**解决方案**：
```bash
# 检查 tessdata 目录是否存在
ls -la ./tessdata

# 确认语言文件是否存在
ls -la ./tessdata/*.traineddata
```

### 问题 2：中文识别乱码

**解决方案**：
- 确认已下载 `chi_sim.traineddata`
- 检查 `language` 配置是否包含 `chi_sim`
- 重新下载语言文件（可能文件损坏）

### 问题 3：识别速度慢

**优化建议**：
- 降低图片分辨率（建议 300-600 DPI）
- 使用 JPEG 格式而非 PNG（文件更小）
- 考虑使用 GPU 加速的 Tesseract 版本

---

## 📚  相关资源

- [Tesseract 官方文档](https://tesseract-ocr.github.io/)
- [Tess4J GitHub](https://github.com/nguyenq/tes4j)
- [语言数据下载](https://github.com/tesseract-ocr/tessdata)

---

**创建时间**: 2026-04-18
**维护者**: RAGent Team
