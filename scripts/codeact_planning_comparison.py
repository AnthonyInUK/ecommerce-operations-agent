#!/usr/bin/env python3
"""Compare model planning quality for open CodeAct-style ecommerce cases.

This benchmark intentionally does not call `/api/ecommerce/answer`. It asks the
model to produce a strict execution plan JSON from a tool catalog, then checks
whether the model can choose tools, rank causes, route owners and decide whether
to notify. That makes it more model-sensitive than the deterministic workflow
stability checks in `model_comparison.py`.
"""

from __future__ import annotations

import argparse
import json
import os
import re
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


TOOL_CATALOG = [
    {
        "name": "RegionPerformanceQueryTool",
        "business_use": "确认异常是否集中在指定区域，或是否同步影响大盘。",
        "good_for": ["gmv", "region_gmv", "regional_drop"],
    },
    {
        "name": "OrderQueryTool",
        "business_use": "拆 GMV = 支付订单量 x 客单价，判断是单量、客单价还是支付口径变化。",
        "good_for": ["gmv", "order_count", "avg_order_value"],
    },
    {
        "name": "UserMetricTool",
        "business_use": "查看 DAU、活跃买家、买家激活率，判断用户规模是否先走弱。",
        "good_for": ["gmv", "active_users", "growth"],
    },
    {
        "name": "CategoryRankTool",
        "business_use": "定位拖累品类、商品和商家，是类目/行业运营接手的关键证据。",
        "good_for": ["gmv", "category_gmv", "product", "seller"],
    },
    {
        "name": "FunnelAnalysisTool",
        "business_use": "拆浏览、下单、支付漏斗，判断是否是转化链路承接问题。",
        "good_for": ["conversion_rate", "growth", "gmv"],
    },
    {
        "name": "RefundAnalysisTool",
        "business_use": "查看退款金额、退款订单和售后压力，判断成交质量是否被售后吞掉。",
        "good_for": ["refund_rate", "after_sales", "gmv"],
    },
]


CASES = [
    {
        "id": "codeact_notify_decision",
        "question": "2018-08-29 华东 GMV 明显下滑，这个异常该不该发飞书？请先设计分析步骤，再给通知建议。",
        "expected_tools": ["RegionPerformanceQueryTool", "OrderQueryTool", "CategoryRankTool"],
        "expected_owner_keywords": ["平台运营", "类目", "经营分析"],
        "expected_cause_keywords": ["区域", "订单", "品类"],
        "expected_notification": {"manual_confirmation_required": True},
    },
    {
        "id": "codeact_owner_priority",
        "question": "2018-08-29 华东 GMV 下滑，哪些负责人应该优先处理？请按 P0/P1/P2 输出，并说明每个负责人查什么。",
        "expected_tools": ["OrderQueryTool", "CategoryRankTool", "UserMetricTool"],
        "expected_owner_keywords": ["平台运营", "经营分析", "类目"],
        "expected_cause_keywords": ["订单", "品类", "用户"],
        "expected_notification": {"manual_confirmation_required": True},
    },
    {
        "id": "codeact_cause_priority",
        "question": "如果华东 GMV 下滑时，品类和用户规模都下滑，主因怎么排序？请给出工具调用计划和责任分发。",
        "expected_tools": ["OrderQueryTool", "UserMetricTool", "CategoryRankTool"],
        "expected_owner_keywords": ["类目", "增长", "平台运营"],
        "expected_cause_keywords": ["品类", "用户", "订单"],
        "expected_notification": {"manual_confirmation_required": True},
    },
]


SYSTEM_PROMPT = """你是一个 CodeAct 电商运营分析 Agent。

你不能直接编造结论。你需要先根据问题选择工具、设计执行计划，再输出结构化业务决策。

只允许输出一个 JSON 对象，不要 Markdown，不要解释 JSON 之外的文字。JSON schema:
{
  "intent": "root_cause | notification_decision | owner_priority | cause_priority",
  "selected_tools": ["ToolName"],
  "execution_plan": [
    {"step": 1, "tool": "ToolName", "why": "为什么先查这个", "expected_evidence": "期望拿到什么证据"}
  ],
  "cause_ranking": [
    {"rank": 1, "cause": "原因名称", "evidence_needed": "需要什么证据", "owner_role": "业务负责人", "priority": "P0|P1|P2"}
  ],
  "action_routing": [
    {"owner_role": "业务负责人", "priority": "P0|P1|P2", "problem": "具体问题", "suggested_action": "先做什么"}
  ],
  "notification_decision": {
    "recommendation": "send | review_before_send | log_only | do_not_send",
    "confidence": "high | medium | low",
    "manual_confirmation_required": true,
    "reason": "为什么这样建议"
  }
}

优先级规则：
- P0: 主责，先处理，通常是最可能解释异常或需要统筹影响面的角色。
- P1: 协同，提供重要补充证据。
- P2: 观察项，当前证据不足或影响较弱。
"""


def load_dotenv(path: Path) -> None:
    if not path.exists():
        return
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        key = key.strip()
        value = value.strip().strip('"').strip("'")
        os.environ[key] = value


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def text_contains_any(value: Any, candidates: list[str]) -> bool:
    text = json.dumps(value, ensure_ascii=False)
    return any(candidate in text for candidate in candidates)


def priority_value(value: Any) -> int:
    raw = str(value or "")
    if raw.startswith("P") and raw[1:].isdigit():
        return int(raw[1:])
    return 99


def extract_json_object(text: str) -> dict[str, Any]:
    text = text.strip()
    if text.startswith("```"):
        text = re.sub(r"^```(?:json)?", "", text).strip()
        text = re.sub(r"```$", "", text).strip()
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        match = re.search(r"\{.*\}", text, re.DOTALL)
        if not match:
            raise
        return json.loads(match.group(0))


def provider_config(provider: str, model: str) -> tuple[str, str, str]:
    if provider == "deepseek":
        api_key = os.getenv("DEEPSEEK_API_KEY", "")
        base_url = os.getenv("DEEPSEEK_BASE_URL", "https://api.deepseek.com").rstrip("/")
        return api_key, base_url, model or os.getenv("MODEL_NAME", "deepseek-chat")
    if provider == "dashscope":
        api_key = os.getenv("DASHSCOPE_API_KEY", "")
        base_url = os.getenv("DASHSCOPE_OPENAI_BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1").rstrip("/")
        return api_key, base_url, model or os.getenv("MODEL_NAME") or os.getenv("DASHSCOPE_MODEL", "qwen-max")
    raise ValueError(f"Unsupported provider: {provider}")


def chat_completion(provider: str, model: str, question: str, timeout: float = 60.0) -> tuple[dict[str, Any], int]:
    api_key, base_url, resolved_model = provider_config(provider, model)
    if not api_key or "replace-me" in api_key:
        raise RuntimeError(f"{provider} API key is not configured")

    user_prompt = {
        "question": question,
        "available_tools": TOOL_CATALOG,
        "business_context": {
            "known_anomaly": "2018-08-29 华东 GMV 从 3841.30 下降到 1870.87",
            "known_signals": [
                "订单量从 40 下降到 13",
                "家居品类从前一日贡献较高变为当前日无贡献",
                "DAU 从 10 下降到 8，活跃买家从 5 下降到 4",
                "漏斗转化基本稳定",
                "退款压力存在但不是第一主因",
            ],
            "notify_policy": "中可信及以下必须人工确认后再发飞书；高可信才建议直接通知。",
        },
    }
    payload = {
        "model": resolved_model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": json.dumps(user_prompt, ensure_ascii=False)},
        ],
        "temperature": 0.1,
        "response_format": {"type": "json_object"},
    }
    body = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(f"{base_url}/chat/completions", data=body, method="POST")
    request.add_header("Authorization", f"Bearer {api_key}")
    request.add_header("Content-Type", "application/json; charset=utf-8")
    request.add_header("Accept", "application/json")

    started = time.time()
    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{provider} chat completion failed with HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"{provider} chat completion failed: {exc.reason}") from exc

    elapsed_ms = int((time.time() - started) * 1000)
    completion = json.loads(raw)
    content = completion["choices"][0]["message"]["content"]
    return extract_json_object(content), elapsed_ms


def evaluate_plan(plan: dict[str, Any], case: dict[str, Any], elapsed_ms: int) -> dict[str, Any]:
    selected_tools = [str(tool) for tool in as_list(plan.get("selected_tools"))]
    execution_plan = as_list(plan.get("execution_plan"))
    cause_ranking = as_list(plan.get("cause_ranking"))
    action_routing = as_list(plan.get("action_routing"))
    notification_decision = plan.get("notification_decision") if isinstance(plan.get("notification_decision"), dict) else {}

    expected_tools = case["expected_tools"]
    tool_hit_count = sum(1 for tool in expected_tools if tool in selected_tools)
    plan_tools = [str(step.get("tool")) for step in execution_plan if isinstance(step, dict)]
    plan_tool_hit_count = sum(1 for tool in expected_tools if tool in plan_tools)
    priorities = [priority_value(route.get("priority")) for route in action_routing if isinstance(route, dict)]

    manual_expected = case.get("expected_notification", {}).get("manual_confirmation_required")
    manual_actual = notification_decision.get("manual_confirmation_required")
    notification_ok = (
        notification_decision.get("recommendation") in {"send", "review_before_send", "log_only", "do_not_send"}
        and notification_decision.get("confidence") in {"high", "medium", "low"}
        and (manual_expected is None or manual_actual == manual_expected)
    )

    return {
        "case_id": case["id"],
        "question": case["question"],
        "tool_selection_ok": tool_hit_count >= min(2, len(expected_tools)),
        "execution_plan_ok": len(execution_plan) >= 3 and plan_tool_hit_count >= min(2, len(expected_tools)),
        "cause_ranking_ok": len(cause_ranking) >= 3 and text_contains_any(cause_ranking, case["expected_cause_keywords"]),
        "owner_routing_ok": len(action_routing) >= 3 and text_contains_any(action_routing, case["expected_owner_keywords"]),
        "priority_sorted": priorities == sorted(priorities),
        "notification_ok": notification_ok,
        "selected_tools": selected_tools,
        "priorities": priorities,
        "notification": notification_decision,
        "elapsed_ms": elapsed_ms,
        "raw_plan": plan,
    }


def failed_row(case: dict[str, Any], error: Exception) -> dict[str, Any]:
    message = str(error)
    redacted = re.sub(r"sk-[A-Za-z0-9_-]+", "sk-***", message)
    return {
        "case_id": case["id"],
        "question": case["question"],
        "tool_selection_ok": False,
        "execution_plan_ok": False,
        "cause_ranking_ok": False,
        "owner_routing_ok": False,
        "priority_sorted": False,
        "notification_ok": False,
        "selected_tools": [],
        "priorities": [],
        "notification": {},
        "elapsed_ms": 0,
        "error": redacted,
        "raw_plan": {},
    }


def mark(value: bool) -> str:
    return "Y" if value else "N"


def render_markdown(provider: str, model: str, rows: list[dict[str, Any]]) -> str:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    passed = sum(
        1
        for row in rows
        if row["tool_selection_ok"]
        and row["execution_plan_ok"]
        and row["cause_ranking_ok"]
        and row["owner_routing_ok"]
        and row["priority_sorted"]
        and row["notification_ok"]
    )
    lines = [
        "# CodeAct Planning Comparison",
        "",
        f"Generated at: {generated_at}",
        "",
        "## Scope",
        "",
        "This report calls the model provider directly and asks it to produce a strict CodeAct-style planning JSON. It does not use `/api/ecommerce/answer`, so it is more sensitive to model planning quality than the workflow-stability benchmark.",
        "",
        f"- Provider: `{provider}`",
        f"- Model: `{model}`",
        f"- Passed planning checks: `{passed}/{len(rows)}`",
        "",
        "## Result",
        "",
        "| Case | Tool Selection | Execution Plan | Cause Ranking | Owner Routing | Priority Sorted | Notification | Latency |",
        "| --- | --- | --- | --- | --- | --- | --- | ---: |",
    ]
    for row in rows:
        lines.append(
            "| {case_id} | {tool} | {plan} | {cause} | {owner} | {priority} | {notify} | {latency} ms |".format(
                case_id=row["case_id"],
                tool=mark(row["tool_selection_ok"]),
                plan=mark(row["execution_plan_ok"]),
                cause=mark(row["cause_ranking_ok"]),
                owner=mark(row["owner_routing_ok"]),
                priority=mark(row["priority_sorted"]),
                notify=mark(row["notification_ok"]),
                latency=row["elapsed_ms"],
            )
        )

    failed = [row for row in rows if row.get("error")]
    if failed:
        lines.extend([
            "",
            "## Provider Errors",
            "",
            "These failures are provider/runtime availability problems, not business workflow failures.",
            "",
        ])
        for row in failed:
            lines.append(f"- `{row['case_id']}`: {row['error']}")

    lines.extend(["", "## Details", ""])
    for row in rows:
        lines.extend([
            f"### {row['case_id']}",
            "",
            f"- Question: {row['question']}",
            f"- Error: `{row.get('error', '-')}`",
            f"- Selected tools: `{ ' -> '.join(row['selected_tools']) or '-' }`",
            f"- Owner priorities: `{row['priorities']}`",
            f"- Notification decision: `{json.dumps(row['notification'], ensure_ascii=False)}`",
            "",
            "```json",
            json.dumps(row["raw_plan"], ensure_ascii=False, indent=2),
            "```",
            "",
        ])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Compare provider planning quality with open CodeAct-style cases.")
    parser.add_argument("--provider", choices=["dashscope", "deepseek"], default=os.getenv("MODEL_PROVIDER", "dashscope"))
    parser.add_argument("--model", default=os.getenv("MODEL_NAME") or os.getenv("DASHSCOPE_MODEL", "qwen-max"))
    parser.add_argument("--output", default="")
    parser.add_argument("--dotenv", default=".env")
    args = parser.parse_args()

    load_dotenv(Path(args.dotenv))
    provider = args.provider
    _, _, model = provider_config(provider, args.model)
    rows: list[dict[str, Any]] = []
    for case in CASES:
        try:
            plan, elapsed_ms = chat_completion(provider, model, case["question"])
            rows.append(evaluate_plan(plan, case, elapsed_ms))
        except Exception as exc:
            rows.append(failed_row(case, exc))
        print(
            f"[{case['id']}] tool={mark(rows[-1]['tool_selection_ok'])} "
            f"plan={mark(rows[-1]['execution_plan_ok'])} cause={mark(rows[-1]['cause_ranking_ok'])} "
            f"owner={mark(rows[-1]['owner_routing_ok'])} priority={mark(rows[-1]['priority_sorted'])} "
            f"notify={mark(rows[-1]['notification_ok'])} latency={rows[-1]['elapsed_ms']}ms"
        )

    output = Path(args.output or f"model_comparison_codeact_{provider}.md")
    output.write_text(render_markdown(provider, model, rows), encoding="utf-8")
    print(f"[OK] wrote {output}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
