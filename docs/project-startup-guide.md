# AI-Compete 服务器 Docker 部署手册

本文档说明如何在服务器上以“全部使用 Docker”的方式部署 `ai-compete` 项目。

部署范围包含：

- PostgreSQL
- Redis
- RocketMQ
- Milvus
- RustFS
- 本地 `mcp-server`
- 外部 MCP 辅助服务
- 后端 `bootstrap`
- 前端 `frontend`

本文档对应本次补齐的容器化文件：

- [docker-compose.prod.yml](/E:/Code/java/ai-compete/docker-compose.prod.yml)
- [bootstrap/Dockerfile](/E:/Code/java/ai-compete/bootstrap/Dockerfile)
- [mcp-server/Dockerfile](/E:/Code/java/ai-compete/mcp-server/Dockerfile)
- [frontend/Dockerfile](/E:/Code/java/ai-compete/frontend/Dockerfile)
- [frontend/nginx.conf](/E:/Code/java/ai-compete/frontend/nginx.conf)

## 1. 部署架构

服务器上会启动以下容器：

- `postgres`
- `redis`
- `rocketmq-namesrv`
- `rocketmq-broker`
- `rustfs`
- `etcd`
- `milvus`
- `attu`
- `mcp-server`
- `open-web-search`
- `arxiv-mcp`
- `google-scholar-mcp`
- `backend`
- `frontend`

对外主要访问入口：

- 前端：`80`
- 后端：`9090`
- 本地 MCP Server：`9099`
- Open Web Search MCP：`3000`
- Arxiv MCP：`8001`
- Google Scholar MCP：`8002`
- RocketMQ NameServer：`9876`
- RustFS：`9000`
- RustFS Console：`9001`
- Milvus：`19530`
- Attu：`8000`

## 2. 服务器要求

建议服务器最低配置：

- CPU：4 核以上
- 内存：16GB 以上
- 磁盘：80GB 以上
- 操作系统：Linux
- Docker Engine：24+
- Docker Compose Plugin：2+

建议提前开放端口：

- `80`
- `9090`
- `9099`
- `3000`
- `8000`
- `8001`
- `8002`
- `9000`
- `9001`
- `9876`
- `19530`

如果你只希望公网暴露前端，建议只放行 `80`，其它端口通过安全组或防火墙限制到内网。

## 3. 代码准备

在服务器上拉取代码：

```bash
git clone https://github.com/xiansen11/ai-compete.git
cd ai-compete
```

建议切到你要部署的分支或提交。

## 4. 生产部署文件

本次部署主要使用：

- [docker-compose.prod.yml](/E:/Code/java/ai-compete/docker-compose.prod.yml)

这个文件会：

- 构建前端镜像
- 构建后端镜像
- 构建 `mcp-server` 镜像
- 启动 PostgreSQL、Redis、RocketMQ、Milvus、RustFS
- 启动 Open Web Search / Arxiv / Google Scholar MCP 服务

## 5. 环境变量准备

### 5.1 新建 `.env`

在项目根目录新建 `.env` 文件，例如：

```bash
cat > .env <<'EOF'
POSTGRES_DB=ragent
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres

RUSTFS_ACCESS_KEY_ID=rustfsadmin
RUSTFS_SECRET_ACCESS_KEY=rustfsadmin

BAILIAN_API_KEY=你的百炼Key
SILICONFLOW_API_KEY=
SEARCH_API_KEY=
GITHUB_TOKEN=
GITHUB_PERSONAL_ACCESS_TOKEN=

OCR_TESSERACT_ENABLED=false
EOF
```

### 5.2 必填变量

至少应设置：

- `BAILIAN_API_KEY`

当前系统默认模型：

- Chat：`qwen3-max`
- Embedding：`qwen-emb-8b`，实际走 `Bailian text-embedding-v4`
- Rerank：`qwen3-rerank`

所以如果没有 `BAILIAN_API_KEY`，核心 AI 能力无法正常工作。

### 5.3 OCR 说明

当前 Compose 默认：

```text
OCR_TESSERACT_ENABLED=false
```

原因是容器里还没有内置 `tessdata` 语言包。如果你要在服务器上启用 OCR，需要额外准备 `tessdata` 和 Tesseract 运行资源；当前这套生产 Compose 先按“关闭 OCR”部署更稳。

## 6. 数据库初始化方式

PostgreSQL 容器会在首次启动时自动执行：

- [schema_pg.sql](/E:/Code/java/ai-compete/resources/database/schema_pg.sql)
- [init_data_pg.sql](/E:/Code/java/ai-compete/resources/database/init_data_pg.sql)

入口脚本位置：

- [01-schema.sql](/E:/Code/java/ai-compete/resources/docker/postgres-init/01-schema.sql)
- [02-init-data.sql](/E:/Code/java/ai-compete/resources/docker/postgres-init/02-init-data.sql)

也就是说，第一次 `docker compose up` 时会自动建库建表并导入初始化数据。

注意：

- 只有 PostgreSQL 数据卷是第一次创建时才会自动执行初始化脚本
- 如果你已经有旧数据卷，脚本不会再次自动跑

## 7. 构建并启动整套服务

在项目根目录执行：

```bash
docker compose -f docker-compose.prod.yml up -d --build
```

第一次启动会比较慢，因为会：

- 构建前端镜像
- 构建后端镜像
- 构建 `mcp-server` 镜像
- 拉取 PostgreSQL / Redis / RocketMQ / Milvus / RustFS 等基础镜像

## 8. 查看启动状态

查看容器状态：

```bash
docker compose -f docker-compose.prod.yml ps
```

查看后端日志：

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

查看前端日志：

```bash
docker compose -f docker-compose.prod.yml logs -f frontend
```

查看 `mcp-server` 日志：

```bash
docker compose -f docker-compose.prod.yml logs -f mcp-server
```

查看数据库日志：

```bash
docker compose -f docker-compose.prod.yml logs -f postgres
```

## 9. 启动成功的验证方法

### 9.1 验证前端

浏览器访问：

```text
http://服务器IP/
```

### 9.2 验证后端

执行：

```bash
curl http://服务器IP:9090/api/ragent/knowledge-base/page?current=1\&size=5
```

### 9.3 验证 `mcp-server`

执行：

```bash
curl http://服务器IP:9099/mcp
```

说明：

- MCP 端点未必返回友好的 HTML 页面
- 重点是端口可达、服务已启动

### 9.4 验证 MCP 辅助服务

分别检查：

```bash
curl http://服务器IP:3000/mcp
curl http://服务器IP:8001/mcp
curl http://服务器IP:8002/mcp
```

### 9.5 验证对象存储和向量服务

浏览器访问：

- `http://服务器IP:9001` RustFS Console
- `http://服务器IP:8000` Attu

## 10. 前端代理说明

线上前端不需要单独配置 API 地址。

原因是 [frontend/nginx.conf](/E:/Code/java/ai-compete/frontend/nginx.conf) 已经把：

```text
/api/
```

反向代理到：

```text
http://backend:9090/api/
```

因此浏览器访问流程是：

1. 用户访问 `frontend`
2. 前端页面里的 `/api/ragent/*` 请求进入 Nginx
3. Nginx 转发到 `backend:9090/api/ragent/*`

## 11. 服务间调用关系

Compose 内部服务名已经在 `docker-compose.prod.yml` 里配置好。

后端会自动使用：

- `postgres:5432`
- `redis:6379`
- `rocketmq-namesrv:9876`
- `milvus:19530`
- `rustfs:9000`
- `mcp-server:9099`
- `open-web-search:3000`
- `arxiv-mcp:8001`
- `google-scholar-mcp:8002`

因此容器内不需要再手动改成 `127.0.0.1`。

## 12. 生产环境推荐暴露策略

如果是正式上线，建议：

- 对公网暴露：`80`
- 对内网或受限 IP 暴露：`9090` `9099` `3000` `8000` `8001` `8002` `9001` `19530` `9876`
- 非必要时不要把数据库 `5432` 直接暴露公网

如果你有 Nginx / Traefik / 云负载均衡，建议把 `frontend` 挂到域名上，例如：

- `https://your-domain.com`

## 13. 更新部署

如果代码有更新，重新部署步骤：

```bash
git pull
docker compose -f docker-compose.prod.yml up -d --build
```

如果只想重建某个服务，例如后端：

```bash
docker compose -f docker-compose.prod.yml up -d --build backend
```

## 14. 停止服务

停止整套服务：

```bash
docker compose -f docker-compose.prod.yml down
```

停止并删除数据卷：

```bash
docker compose -f docker-compose.prod.yml down -v
```

注意：

- `down -v` 会删除 PostgreSQL、Redis、Milvus、RustFS 等持久化数据
- 生产环境不要随便执行

## 15. 常见问题

### 15.1 后端启动失败，提示 AI Key 问题

优先检查：

- `.env` 中 `BAILIAN_API_KEY` 是否已填写
- Key 是否有效

### 15.2 文档上传成功但分块失败

优先检查：

- `backend` 是否是最新镜像
- RocketMQ 是否正常
- Milvus / RustFS 是否正常
- `BAILIAN_API_KEY` 是否有效

查看日志：

```bash
docker compose -f docker-compose.prod.yml logs -f backend
```

### 15.3 数据库没有初始化

常见原因：

- PostgreSQL 数据卷已经存在

处理思路：

1. 先确认容器首次启动时是否执行过初始化脚本
2. 如果是全新环境，可以删除 PostgreSQL 数据卷后重新启动
3. 如果是已有生产库，不要删卷，改为手动执行 SQL

### 15.4 OCR 识别不可用

当前 Compose 默认关闭 OCR：

```text
OCR_TESSERACT_ENABLED=false
```

如果必须开启 OCR，需要额外做：

1. 在镜像中安装 Tesseract 运行依赖
2. 准备 `tessdata` 目录
3. 将 `chi_sim.traineddata`、`eng.traineddata` 挂载到容器
4. 将 `OCR_TESSERACT_ENABLED` 改为 `true`

### 15.5 前端可以打开，但接口 502/504

优先检查：

- `backend` 是否已健康启动
- `frontend` Nginx 是否已经启动
- `backend` 容器日志是否有异常

### 15.6 MCP 服务不可用

优先检查：

- `mcp-server`
- `open-web-search`
- `arxiv-mcp`
- `google-scholar-mcp`

执行：

```bash
docker compose -f docker-compose.prod.yml ps
docker compose -f docker-compose.prod.yml logs -f mcp-server
```

## 16. 核心文件清单

本次服务器 Docker 部署相关文件：

- [docker-compose.prod.yml](/E:/Code/java/ai-compete/docker-compose.prod.yml)
- [bootstrap/Dockerfile](/E:/Code/java/ai-compete/bootstrap/Dockerfile)
- [mcp-server/Dockerfile](/E:/Code/java/ai-compete/mcp-server/Dockerfile)
- [frontend/Dockerfile](/E:/Code/java/ai-compete/frontend/Dockerfile)
- [frontend/nginx.conf](/E:/Code/java/ai-compete/frontend/nginx.conf)
- [.dockerignore](/E:/Code/java/ai-compete/.dockerignore)
- [application.yaml](/E:/Code/java/ai-compete/bootstrap/src/main/resources/application.yaml)
- [docker-compose.yml](/E:/Code/java/ai-compete/docker-compose.yml)
- [rocketmq-stack-5.2.0.compose.yaml](/E:/Code/java/ai-compete/resources/docker/rocketmq-stack-5.2.0.compose.yaml)
- [milvus-stack-2.6.6.compose.yaml](/E:/Code/java/ai-compete/resources/docker/milvus-stack-2.6.6.compose.yaml)
