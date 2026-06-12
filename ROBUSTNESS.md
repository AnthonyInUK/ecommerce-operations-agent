# Robustness Rules

## 这份文档在讲什么

这份文档不讲“系统多强”，而讲：

**当 Agent 开始主动跑日报和异常巡检后，系统怎么尽量不失控。**

也就是把 Week E 后半段已经落地的几条鲁棒性规则，用产品语言讲清楚。

## 1. 主动链分成两类，不混做一条

当前主动能力拆成两条 trigger：

- `daily_report`
- `gmv_drop_watch`

它们的分工是：

- `daily_report`：稳定汇总昨日经营概览
- `gmv_drop_watch`：发现异常后补一版 root cause

这样做的鲁棒性价值是：

- 报表不会因为异常分析链复杂而变脆
- 异常巡检也不会把日报语义搞混

## 2. 日报和异常巡检都先走“固定格式”

当前这两条主动链都不是开放式自由回答，而是：

- 固定输入
- 固定工具链
- 固定输出结构

业务上这意味着：

> 主动链追求的是稳定交付，不是开放创作。

## 3. 异常触发已经从单一阈值升级成多基线判断

当前 `gmv_drop_watch` 不再只看：

- 昨天 vs 前一天

而是同时看：

- 昨天 vs 前一天
- 昨天 vs 上周同一天
- 昨天 vs 最近几天均值
- 去年同期（若可用）
- 节假日 / 活动期特殊阈值

业务上这条规则的意义是：

> 系统不是“看到跌就叫”，而是尽量先区分真实异常和正常经营波动。

## 4. 启动校验不只是看功能在不在，还看格式有没有漂移

当前校验器除了检查 trigger 是否注册成功，还会检查：

- 日报是否仍然走 `send_notification`
- 日报是否仍然带区域 / 品类摘要
- 异常巡检是否仍然带 day/week/rolling/yoy 这些关键元数据

对应实现：

- [DailyReportTriggerValidator.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/validator/DailyReportTriggerValidator.java)
- [GmvDropWatchTriggerValidator.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/validator/GmvDropWatchTriggerValidator.java)

业务上可以讲成：

> 不是系统“还能跑”就算过关，而是关键交付格式也要被守住。

## 5. 防重复推送是主动链鲁棒性的第一层

主动链最常见的问题不是算错，而是：

- 同一条消息反复发
- 把外部通知通道刷爆

当前已经通过去重指纹做了第一层收口：

- 相同标题 + 正文
- 窗口期内抑制重复发送

所以当前更稳的地方在于：

> 同一条异常或日报，不会因为重复触发马上刷屏。

## 6. 高频只读链路现在有明确缓存策略

当前查询入口：

- [JdbcWarehouseQueryService.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/config/JdbcWarehouseQueryService.java)

已经补了进程内 TTL cache，并且把边界变成配置项：

- 大盘
- 区域
- 品类
- 订单结构
- 用户概览
- 漏斗
- 退款分类拆解
- 默认 TTL：`120s`
- 默认最大条目：`512`
- 默认不缓存空结果，避免 Olist 导入或 ads 重建过程中把“暂时无数据”固化住

业务上可以讲成：

> 日报、异常巡检和 root cause 里反复读取的高频指标，不会每一步都重复查一遍库，而是先走一层很轻的短 TTL 缓存。

## 7. root cause 现在支持部分降级

`gmv_root_cause` 现在不是某一个 Tool 失败就整条失败，而是采用：

- 单个证据 Tool 失败：保留已完成证据，返回 `degraded=true`
- 失败维度写入 `degradations`
- 前端 / 日报 / 异常巡检复用同一份结构化结果
- 结论里把失败维度标成“证据不足”，而不是假装分析完整

业务上可以讲成：

> 如果漏斗或退款这类补充证据暂时不可用，系统仍然会给出区域、订单、品类等已确认结论，同时明确告诉你哪块证据缺失。

## 8. Prompt Injection 现在有风险输入检测和审计日志

当前问答入口会先经过：

- [PromptInjectionGuard.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/security/PromptInjectionGuard.java)
- [SecurityAuditLogger.java](/Users/anthony/Desktop/llm/advanced_agent/AssistantAgent/assistant-agent-start/src/main/java/com/alibaba/assistant/agent/start/security/SecurityAuditLogger.java)

已落地规则：

- 命中“忽略之前规则 / 打印系统提示词 / 删表 / 绕过规则”等高风险表达时直接拒绝
- 拒绝事件写入 `prompt_security_audit`
- 即使审计表暂时不可用，也会写入应用日志

业务上可以讲成：

> 这不是完整安全网关，但已经把最常见的越权问法从“交给模型自由发挥”改成了“入口先拦截并留痕”。

## 9. 当前“单机可靠”但还不是“分布式强一致”

Week E 现在的鲁棒性，更多是：

- 不轻易误触发
- 不轻易重复发
- 不允许写操作
- 给主动任务设执行边界
- 单个 Tool 失败时返回部分结论
- 高风险输入先拦截并审计

但它还没有完全做到：

- 多节点分布式强一致去重
- 完整死信/补偿机制
- 复杂故障后的自动恢复编排
- 外部 Redis / Caffeine / 多级缓存治理

所以现在更准确的说法是：

> Week E 已经补到了“单机可靠运行层”，但还不是完整 SRE 级别的分布式主动系统。

## 一句话版

当前 Week E 的鲁棒性规则可以压成一句话：

> 主动链分工明确、输出固定、异常判断有多基线、通知默认去重并带持久化指纹、外部通道失败可降级、root cause 可返回部分结论、本地查询有短 TTL 缓存、风险输入会被拦截审计，关键格式由启动校验守住。
