# Model Comparison

Generated at: 2026-06-11 17:46:11

## Scope

This report evaluates the ecommerce Agent through its public demo APIs, not by calling model providers directly. The measured object is the end-to-end business chain: intent routing, path selection, Tool evidence, root cause structure, action routing, notification draft and latency.

Note: standard fast-path questions use current demo seed dates, while the root-cause showcase uses the fixed Olist anomaly date `2018-08-29`.

## Current Provider

- Provider: `dashscope`
- Model: `qwen-max`
- Passed core checks: `5/5`

## dashscope / qwen-max Result

| Case | Intent OK | Path OK | Tool OK | Root Cause Stable | Notification Usable | Format Stable | Latency |
| --- | --- | --- | --- | --- | --- | --- | ---: |
| standard_gmv | Y | Y | Y | - | Y | Y | 20 ms |
| order_structure | Y | Y | Y | - | Y | Y | 11 ms |
| user_metric | Y | Y | Y | - | Y | Y | 19 ms |
| root_cause | Y | Y | Y | Y | Y | Y | 666 ms |
| dispatch_prompt | Y | Y | Y | Y | Y | Y | 80 ms |

## Detailed Notes

### standard_gmv

- Question: 昨天 GMV 多少？
- Actual intent: `gmv_query`
- Actual path: `fast`
- Tool chain: `GmvQueryTool`
- Action routing count: `0`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### order_structure

- Question: 昨天订单量和客单价怎么样？
- Actual intent: `order_query`
- Actual path: `fast`
- Tool chain: `OrderQueryTool`
- Action routing count: `0`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### user_metric

- Question: 昨天 DAU 和活跃买家多少？
- Actual intent: `user_metric`
- Actual path: `fast`
- Tool chain: `UserMetricTool`
- Action routing count: `0`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### root_cause

- Question: 2018-08-29 华东 GMV 为什么跌了？
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

### dispatch_prompt

- Question: 2018-08-29 华东 GMV 为什么跌了？请给出责任分发和通知草稿
- Actual intent: `root_cause`
- Actual path: `deep`
- Tool chain: `RegionPerformanceQueryTool -> OrderQueryTool -> UserMetricTool -> CategoryRankTool -> FunnelAnalysisTool -> RefundAnalysisTool`
- Action routing count: `5`
- Cost note: 未接入 token usage 统计；当前只记录响应时间。

## DeepSeek Result

| Provider | Model | Status | Why |
| --- | --- | --- | --- |
| deepseek | deepseek-chat | ready_to_run | DeepSeek uses Spring AI's OpenAI-compatible provider. Set `MODEL_PROVIDER=deepseek`, `SPRING_AI_MODEL_CHAT=openai`, `MODEL_NAME=deepseek-chat`, and `DEEPSEEK_API_KEY` locally. |
| deepseek | deepseek-reasoner | ready_to_run | Same provider wiring; use this model for reasoning-quality comparison when latency/cost are acceptable. |

## Interpretation

- 当前报告通过同一组业务 API 验证模型链路；DeepSeek 已补 OpenAI-compatible provider 配置，但需要本地 `.env` 提供有效 key 后再运行对比。
- 这个项目更应该比较“同一业务链路在不同模型下是否稳定”，而不是单独比较模型闲聊能力。
- 对比 DeepSeek 时，保持 Tool、DTO、workflow 不变，只替换 ChatModel provider，再复用本报告脚本做 A/B。
