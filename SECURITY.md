# Security Rules

## 这份文档在讲什么

这不是底层安全白皮书，而是当前这个电商分析 Agent 在 **Week E 第一版** 已经落地的几条可对外讲清楚的运行边界。

重点回答三个问题：

1. 主动链路能做什么，不能做什么
2. 为什么它不会直接改库或乱执行写操作
3. 当前版本的安全边界在哪，哪些地方仍然属于框架边界

## 1. 查询层默认只读

当前所有仓库查询入口统一收口在：

- [JdbcWarehouseQueryService.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/JdbcWarehouseQueryService.java)

已经落地的规则是：

- 只允许 `SELECT` / `WITH`
- 明确拦截：
  - `INSERT`
  - `UPDATE`
  - `DELETE`
  - `MERGE`
  - `DROP`
  - `ALTER`
  - `TRUNCATE`
  - `CREATE`
  - `REPLACE`
  - `CALL`

业务上可以直接讲成：

> Agent 现在能查数、能分析，但不能通过查询链改业务数据。

## 2. 主动触发链只暴露白名单业务动作

Week E 的主动链不是把所有能力都交给 trigger runtime，而是通过：

- [WeekEOperationsConfig.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/WeekEOperationsConfig.java)
- [EcommerceTriggerExecutor.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/EcommerceTriggerExecutor.java)

只注入当前需要的业务 Tool：

- `GmvQueryTool`
- `OrderQueryTool`
- `UserMetricTool`
- `RegionPerformanceQueryTool`
- `CategoryRankTool`
- `FunnelAnalysisTool`
- `RefundAnalysisTool`
- 以及 reply 工具

业务上可以讲成：

> 主动链路不是拿到整套系统权限，而是只拿到“查数、分析、推送”这条最小闭环所需能力。

## 3. 回复通道默认防重复推送，并且开始具备重启后幂等

当前飞书通道实现位于：

- [FeishuWebhookChannelDefinition.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/reply/FeishuWebhookChannelDefinition.java)
- [NotificationDeliveryGuard.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/reply/NotificationDeliveryGuard.java)

已落地规则：

- 对 `title + text` 生成指纹
- 在窗口期内重复消息默认抑制
- 指纹会同时落到：
  - 进程内内存
  - `notification_delivery_log` 持久化表
- 默认窗口：
  - `1800` 秒（30 分钟）

业务上可以讲成：

> 同一条日报或异常消息，不会因为重复触发把飞书刷屏；即使应用刚重启，也不会立刻把上一条消息再发一遍。

## 4. 回复通道失败时会先降级，不直接打断主动链

当前飞书通道在下面这个入口里处理：

- [FeishuWebhookChannelDefinition.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/reply/FeishuWebhookChannelDefinition.java)

已落地规则：

- webhook 没配置时，不直接让主动任务失败
- webhook 超时或返回非 2xx 时，不直接让主动任务失败
- 默认会降级到 `IDE_TEXT/log` 留痕
- 同时保留：
  - `degraded=true`
  - `degradeReason`

业务上可以讲成：

> 外部通知通道挂了，不代表日报和异常巡检就完全失明；它会先降级留下本地痕迹，而不是直接整条链路报错退出。

## 5. Trigger 执行有超时和最小重试

当前配置入口在：

- [application.yml](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/application.yml)
- [application-prod.yml](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/application-prod.yml)
- [application-reference.yml](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/resources/application-reference.yml)

当前 Week E 主动链约束：

- trigger execution timeout 已配置
- 默认最大重试次数为 `1`
- 默认重试延迟为 `1000ms`

业务上可以讲成：

> 主动任务不是无限重跑，也不会无上限卡住；它有最小执行时间边界和重试边界。

## 6. Prompt Injection 入口先拦截并审计

当前问答入口不是直接把所有用户输入交给分析链，而是先经过：

- [PromptInjectionGuard.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/security/PromptInjectionGuard.java)
- [SecurityAuditLogger.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/security/SecurityAuditLogger.java)

已落地规则：

- 命中高风险表达时直接拒绝，例如“忽略之前规则”“打印系统提示词”“删表”“绕过规则”
- 拒绝结果返回 `blocked=true`
- 风险输入写入 `prompt_security_audit`
- 审计表不可用时至少写应用日志

业务上可以讲成：

> Agent 不把明显越权的输入交给模型自由解释，而是在入口先拦截、拒绝并留下审计记录。

## 7. 当前仍然属于框架边界的地方

这个项目现在已经补了一层工程保护，但仍然有一些点要诚实说明：

- GraalVM 代码执行链本身的资源限制不是完全由我们这层强控
- 执行器底层的 CPU / 内存硬隔离仍更接近框架边界
- 当前保护更多集中在：
  - 触发层
  - 查询层
  - 推送层

也就是说，当前更准确的表达是：

> 我们已经把主动链路做成“最小可控”，但不是把底层沙箱安全问题一次性全部解决。

## 一句话版

当前 Week E 的安全边界可以压成一句话：

> Agent 可以主动查数、主动分析、主动推送，但默认只读、不随意暴露工具、不重复刷通知，会拦截高风险输入，并带最小超时、重试和审计边界。
