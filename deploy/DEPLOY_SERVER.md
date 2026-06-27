# 云服务器部署（无域名 / IP 访问）

把电商运营 Agent 部署到一台云服务器，用公网 IP 访问。**纯内部工具、不接第三方
OAuth/Webhook 的场景下不需要域名。**

## 架构（单服务，简单）

后端 Spring Boot（端口 18080）**同时提供 REST API + React 控制台**：
- 控制台静态资源打包在 jar 内，访问 `/agent-console/`
- 控制台调用同源 `/api/...`，因此**换 IP 无需改任何前端地址**
- 内嵌 H2 文件库 + Olist 数据，无外部数据库依赖

```
浏览器 ──http──▶ 服务器IP:18080
                    │
              ┌─────▼─────────────────┐
              │ assistant-agent (JVM)  │
              │  /agent-console/  (UI) │
              │  /api/...        (REST)│
              │  H2 file db + Olist    │
              └────────────────────────┘
```

## 一、买服务器

- **香港/海外轻量服务器，2核4G 起**（免备案；2G 内存构建会 OOM）
- 镜像选 Ubuntu 22.04 或自带 Docker 的镜像
- **安全组放行端口 18080**（或你映射的 `SERVER_PORT`）

## 二、部署（服务器只需 Docker）

用自包含多阶段构建（`Dockerfile.standalone`）——服务器**无需装 Node/Maven/JDK**：

```bash
git clone <你的仓库> && cd <项目目录>

cp deploy/.env.example .env
#   编辑 .env，至少填 DASHSCOPE_API_KEY（或切 DeepSeek，见 deploy/README.md）

docker compose \
  -f deploy/docker-compose.yml \
  -f deploy/docker-compose.standalone.yml \
  up -d --build
```

首次构建较久（拉 Maven 依赖 + 编译多模块 + 构建 UI，约 10–20 分钟）。

打开：`http://<服务器IP>:18080/agent-console/index.html`

> 改端口：`SERVER_PORT=8080 docker compose -f ... up -d`，访问 `http://IP:8080/...`

## 三、访问控制（公网 IP 必做）

内部工具放公网 IP = 任何人扫到 IP 都能访问，**必须加一层防护**，三选一：

| 方式 | 做法 | 适合 |
|------|------|------|
| **安全组白名单（推荐）** | 18080 端口只放行你的出口 IP | 自己/小团队用 |
| **SSH 隧道（最安全）** | 服务器只开 22；本地 `ssh -L 18080:localhost:18080 user@IP`，访问 `localhost:18080` | 纯自用/演示 |
| **加一层 Nginx Basic Auth** | 前置 nginx 做用户名密码 | 要对外给人看 |

不做防护别把 18080 开公网——`.env` 里有 LLM key，接口裸奔有风险。

## 四、HTTPS（可选）

无域名只能自签证书（浏览器提示不受信，但链路加密）。内部工具一般 http 即可；
要 HTTPS 就前置 Caddy/Nginx 挂自签证书。生产对外建议买域名后用 Let's Encrypt。

## 常用命令

```bash
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.standalone.yml logs -f
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.standalone.yml ps
docker compose -f deploy/docker-compose.yml -f deploy/docker-compose.standalone.yml down
```

> 现有 `deploy/start-prod.sh` 走"本地先 `mvn package` 再构建"的流程，适合本机有
> JDK17+Maven 的开发环境；服务器上推荐本文档的自包含构建，免装构建工具链。
