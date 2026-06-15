# 可观测性栈（Prometheus + Grafana）

一键起一套本地监控，抓取并可视化 Assistant Agent 的 **LLM 韧性层指标** 和 **SQL 审计指标**。

## 前置

- 已安装 Docker / Docker Compose
- 应用已启动并暴露 `/actuator/prometheus`（已在 `application.yml` 中开启）

## 步骤

**1. 启动应用（默认 8080 端口）**

```bash
cd assistant-agent-start
export DASHSCOPE_API_KEY=your-key
mvn spring-boot:run
```

> 若你用 README 里的电商 demo 命令（`--server.port=18080`），请把 `prometheus.yml` 里的
> 抓取目标从 `8080` 改成 `18080`。

验证指标已暴露：

```bash
curl -s localhost:8080/actuator/prometheus | grep llm_resilience
```

**2. 启动监控栈**

```bash
cd deploy/observability
docker compose up -d
```

**3. 打开 Grafana**

浏览器访问 <http://localhost:3000>（匿名免登录），左侧 Dashboards 里已预置
**“Assistant Agent 可观测 (LLM 韧性 & SQL 审计)”**。

- Prometheus 控制台：<http://localhost:9090>（可在 Status → Targets 确认 `assistant-agent` 为 UP）

**4. 产生数据**

在 Agent 控制台（`/agent-console/index.html`）发几个分析请求，或跑一次 GMV 异常巡检。
LLM 调用会推高 `llm_resilience_*`，数仓查询会体现在 `sql_audit_recent_count`，
刷新仪表盘即可看到曲线。

## 面板说明

| 面板 | 指标 | 看什么 |
|------|------|--------|
| LLM 总调用 / 成功 / 降级兜底 / 熔断短路 | `llm_resilience_*_total` | 韧性层的总体健康度 |
| LLM 调用结果（累计） | success / failure / fallback / timeout | 成功率与失败构成 |
| 保护动作（累计） | retries / short_circuited / rate_limited / bulkhead_rejected | 各层保护被触发了多少 |
| SQL 审计缓存条数 | `sql_audit_recent_count` | 最近被审计的数仓查询数量 |
| 调用速率 | `rate(llm_resilience_calls_total[1m])` | 每分钟调用量 |

## 关停

```bash
docker compose down
```
