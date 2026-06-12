# Qwen vs DeepSeek Open Judgement Comparison

Generated at: 2026-06-11 18:08:00

## What Changed

This round extends the model comparison from standard ecommerce QA into more open business judgement cases:

- `notify_decision`: should this anomaly be sent to Feishu, and does it require manual confirmation?
- `owner_priority`: which business owners should handle it first?
- `cause_priority`: when category and user metrics both decline, how should root causes be ranked?

These cases are still evaluated through the product API, so the comparison measures the stability of the full Agent business chain: intent routing, Tool chain, root cause structure, notification decision, responsibility routing and response format.

## Result

| Provider | Model | Core Passed | Open Judgement Passed | Average Latency | Notes |
| --- | --- | ---: | ---: | ---: | --- |
| DeepSeek | `deepseek-chat` | 5/5 | 3/3 | 163 ms | All checks passed. Open judgement fields were stable. |
| DashScope | `qwen-max` | 5/5 | 3/3 | 92 ms | All checks passed. Faster in this run, but latency is not yet conclusive. |

## Key Finding

Both models passed the current business-chain checks. This means the project is now fairly robust to provider switching for the existing workflow.

However, this does **not** prove that one model is better at open CodeAct reasoning. The current `/api/ecommerce/answer` path already has strong deterministic business builders: root cause sections, notification draft, action routing and confidence fields are mostly produced by system logic. So the model comparison is currently better understood as:

> Can the same ecommerce Agent workflow remain stable after replacing the model provider?

The answer is yes for this round.

## What The New Cases Actually Verify

- Notification decision is usable when `notification_draft.notify_recommendation`, `confidence`, `manual_confirmation_required` and `auto_send_allowed` are all present.
- Owner priority is usable when `action_routing` has enough responsible roles, includes platform/category ownership and exposes priority tags such as `P0`.
- Cause priority is usable when root cause `sections` identify category and user signals, and the decision trace gives enough structured evidence.

## Remaining Gap

For a stricter CodeAct benchmark, the next version should add a route where the model has to choose analysis steps more freely:

- Model decides which Tool to call first based on anomaly type.
- Model generates or edits a small execution plan.
- Model must return a strict JSON decision object.
- Evaluation checks whether the generated plan, Tool calls and final business decision match expected behaviour.

Until then, this comparison should be presented as a **provider-switching and workflow-stability benchmark**, not as a pure model reasoning leaderboard.

## CodeAct Planning Benchmark Update

`scripts/codeact_planning_comparison.py` now adds the stricter benchmark described above. It calls the model provider directly instead of `/api/ecommerce/answer`, then asks the model to produce a strict planning JSON with:

- selected tools
- execution plan
- cause ranking
- action routing
- notification decision

Latest DeepSeek result:

| Provider | Model | Planning Cases | Result | Notes |
| --- | --- | ---: | --- | --- |
| DeepSeek | `deepseek-chat` | 3 | Passed 3/3 | The model selected relevant tools, produced multi-step plans, ranked causes, sorted P0/P1/P2 responsibility routing and required manual confirmation before Feishu notification. |

DashScope/Qwen direct planning comparison was not completed in this run because the provider returned an account availability error. The workflow-stability benchmark for Qwen remains available in `model_comparison_qwen_open.md`.
