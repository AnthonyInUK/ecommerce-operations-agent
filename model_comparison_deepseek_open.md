# Model Comparison

Generated at: 2026-06-11 18:02:15

## Scope

This report evaluates the ecommerce Agent through its public demo APIs, not by calling model providers directly. The measured object is the end-to-end business chain: intent routing, path selection, Tool evidence, root cause structure, action routing, notification draft and latency.

Note: standard fast-path questions use current demo seed dates, while the root-cause showcase uses the fixed Olist anomaly date `2018-08-29`.

## Current Provider

- Provider: `deepseek`
- Model: `deepseek-chat`
- Passed core checks: `8/8`

## deepseek / deepseek-chat Result

| Case | Intent OK | Path OK | Tool OK | Root Cause Stable | Notification Usable | Open Judgement OK | Format Stable | Sensitivity | Latency |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: |
| standard_gmv | Y | Y | Y | - | Y | - | Y | low | 29 ms |
| order_structure | Y | Y | Y | - | Y | - | Y | low | 375 ms |
| user_metric | Y | Y | Y | - | Y | - | Y | low | 5 ms |
| root_cause | Y | Y | Y | Y | Y | - | Y | low | 496 ms |
| dispatch_prompt | Y | Y | Y | Y | Y | - | Y | low | 71 ms |
| notify_decision | Y | Y | Y | Y | Y | Y | Y | medium | 91 ms |
| owner_priority | Y | Y | Y | Y | Y | Y | Y | medium | 138 ms |
| cause_priority | Y | Y | Y | Y | Y | Y | Y | medium | 102 ms |

## Detailed Notes

### standard_gmv

- Question: 昨天 GMV 多少？
- Actual intent: `gmv_query`
- Actual path: `fast`
- Tool chain: `GmvQueryTool`
- Action routing count: `0`
- Open judgement checks: `-`
- Open check detail: `-`
- Model sensitivity: `low`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### order_structure

- Question: 昨天订单量和客单价怎么样？
- Actual intent: `order_query`
- Actual path: `fast`
- Tool chain: `OrderQueryTool`
- Action routing count: `0`
- Open judgement checks: `-`
- Open check detail: `-`
- Model sensitivity: `low`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### user_metric

- Question: 昨天 DAU 和活跃买家多少？
- Actual intent: `user_metric`
- Actual path: `fast`
- Tool chain: `UserMetricTool`
- Action routing count: `0`
- Open judgement checks: `-`
- Open check detail: `-`
- Model sensitivity: `low`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### root_cause

- Question: 2018-08-29 华东 GMV 为什么跌了？
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Open judgement checks: `-`
- Open check detail: `-`
- Model sensitivity: `low`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### dispatch_prompt

- Question: 2018-08-29 华东 GMV 为什么跌了？请给出责任分发和通知草稿
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Open judgement checks: `-`
- Open check detail: `-`
- Model sensitivity: `low`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### notify_decision

- Question: 2018-08-29 华东 GMV 为什么跌了？这个异常该不该发飞书？请说明可信度和是否需要人工确认
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Open judgement checks: `Y`
- Open check detail: `{"notification_decision": "recommendation=review_before_send, confidence=medium, manual_gate=True"}`
- Model sensitivity: `medium`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### owner_priority

- Question: 2018-08-29 华东 GMV 为什么跌了？哪些负责人应该优先处理？请按优先级排序
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Open judgement checks: `Y`
- Open check detail: `{"owner_priority": "routes=5, p0=4, has_platform=True, has_category=True, sorted=False"}`
- Model sensitivity: `medium`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### cause_priority

- Question: 2018-08-29 华东 GMV 为什么跌了？如果品类和用户都下滑，主因怎么排序？
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Open judgement checks: `Y`
- Open check detail: `{"cause_priority": "signals=region,order_structure,user_scale,category_drag,business_evidence, category=True, user=True, business_evidence=True"}`
- Model sensitivity: `medium`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

## DeepSeek Result

| Provider | Model | Status | Why |
| --- | --- | --- | --- |
| deepseek | deepseek-chat | ready_to_run | DeepSeek uses Spring AI's OpenAI-compatible provider. Set `MODEL_PROVIDER=deepseek`, `SPRING_AI_MODEL_CHAT=openai`, `MODEL_NAME=deepseek-chat`, and `DEEPSEEK_API_KEY` locally. |
| deepseek | deepseek-reasoner | ready_to_run | Same provider wiring; use this model for reasoning-quality comparison when latency/cost are acceptable. |

## Interpretation

- 标准快路径/深路径主要验证 Tool 编排和业务 DTO 稳定性，因此对模型差异的敏感度较低。
- `open_judgement` cases 会检查通知建议、责任分发和主因排序等结构化字段，比单纯看回答文本更适合这个项目。
- 如果要进一步比较真正开放 CodeAct 能力，下一步应加入“由模型自主选择 Tool/生成执行代码”的链路，而不是只复用确定性业务 builder。
