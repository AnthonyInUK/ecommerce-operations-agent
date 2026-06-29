# 腾讯云 CVM 部署：电商运营异常分析 Agent

本文只部署电商运营异常分析 Agent（`AssistantAgent` Java 项目），不包含合规/全链路 Python 项目。

目标域名：

```text
https://compliance.anthonyai.cn/
```

## 目标架构

```text
浏览器
  -> https://compliance.anthonyai.cn/agent-console/index.html
腾讯云 CVM
  -> Docker Compose
     -> caddy              HTTPS / 自动证书 / 反向代理
     -> assistant-agent    Spring Boot + React 控制台 + H2 文件库
     -> redis              健康检查 / 缓存 / 运行时依赖
```

## 1. 腾讯云准备

推荐配置：

- CVM：Ubuntu 22.04/24.04
- 规格：2C4G 起步，首次 Docker 多阶段构建更稳；2G 容易 OOM
- 系统盘：50GB 起
- 安全组：
  - `22/tcp`：只放你的办公 IP
  - `80/tcp`：开放给公网，Caddy 申请证书需要
  - `443/tcp`：开放给公网，浏览器 HTTPS 访问
  - `18080/tcp`：不用对公网开放；如需临时排障，只放你的办公 IP

域名 DNS：

在 `anthonyai.cn` 的 DNS 控制台添加：

```text
记录类型：A
主机记录：compliance
记录值：<腾讯云 CVM 公网 IP>
TTL：默认
```

确认解析：

```bash
dig compliance.anthonyai.cn +short
```

## 2. 安装 Docker

在 CVM 上执行：

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git

curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER
newgrp docker

docker version
docker compose version
```

## 3. 拉代码并配置环境

```bash
git clone <你的仓库地址> AssistantAgent
cd AssistantAgent

cp deploy/.env.tencent.example .env.tencent
vi .env.tencent
```

至少填写一个模型 key。推荐 DeepSeek：

```env
MODEL_PROVIDER=deepseek
SPRING_AI_MODEL_CHAT=openai
SPRING_AI_DASHSCOPE_ENABLED=false
MODEL_NAME=deepseek-chat
DEEPSEEK_API_KEY=你的真实key
```

如果用 DashScope：

```env
MODEL_PROVIDER=dashscope
SPRING_AI_MODEL_CHAT=dashscope
SPRING_AI_DASHSCOPE_ENABLED=true
MODEL_NAME=qwen-max
DASHSCOPE_API_KEY=你的真实key
```

## 4. 启动

```bash
docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  up -d --build
```

首次构建会拉 Node/Maven/JDK 基础镜像并编译多模块，可能需要 10-20 分钟。

## 5. 验证

```bash
docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  ps

curl http://127.0.0.1:18080/actuator/health
curl https://compliance.anthonyai.cn/actuator/health
```

期望：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

浏览器访问：

```text
https://compliance.anthonyai.cn/agent-console/index.html
```

## 6. 手动触发运营异常分析

```bash
curl -X POST http://127.0.0.1:18080/api/ecommerce/triggers/gmv-drop-watch/run-once
```

也可以直接问答：

```bash
curl -X POST http://127.0.0.1:18080/api/ecommerce/answer \
  -H "Content-Type: application/json" \
  -d '{"question":"2018-08-29 华东 GMV 为什么跌了？","session_id":"demo"}'
```

## 7. 日志和运维

```bash
docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  logs -f assistant-agent
```

重启：

```bash
docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  restart assistant-agent
```

停止：

```bash
docker compose \
  --env-file .env.tencent \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  -f deploy/docker-compose.tencentcloud.yml \
  -f deploy/docker-compose.domain.yml \
  down
```

## 8. 数据说明

腾讯云默认部署使用内置 demo warehouse：

```env
APP_BOOTSTRAP_DEMO_DATA=true
APP_BOOTSTRAP_OLIST_ANALYTICS=false
APP_PREFER_OLIST_ANALYTICS=false
```

这样首次部署不会因为 Olist 全量 CSV/DWD/ADS 表缺失而启动失败。

如果后面要启用 Olist 真实公开数据链：

1. 把完整 CSV 放到服务器项目的 `data/olist-csv/`
2. 修改 `.env.tencent`：

```env
APP_BOOTSTRAP_OLIST_RAW_SCHEMA=true
APP_BOOTSTRAP_OLIST_ANALYTICS=true
APP_PREFER_OLIST_ANALYTICS=true
```

3. 重新启动 compose。

## 9. 安全建议

- 只公网开放 `80/443`，不要公网开放 `18080`。
- 如果只是自用，可以在腾讯云安全组里把 `443` 限制成你的办公 IP。
- 需要账号密码时，可以在 Caddy 前面加 Basic Auth；当前配置暂未开启。
