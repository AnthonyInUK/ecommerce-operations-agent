#!/usr/bin/env python3
"""Compare model-backed ecommerce Agent behavior through public demo APIs.

The script measures the end-to-end Agent outcome rather than calling model APIs
directly: intent routing, tool chain, root cause structure, notification draft
and latency. That keeps the comparison aligned with the product behavior shown
in interviews.
"""

from __future__ import annotations

import argparse
import json
import os
import time
from datetime import datetime
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


CASES = [
    {
        "id": "standard_gmv",
        "question": "昨天 GMV 多少？",
        "expected_intent": "gmv_query",
        "expected_path": "fast",
        "expected_tools": ["GmvQueryTool"],
    },
    {
        "id": "order_structure",
        "question": "昨天订单量和客单价怎么样？",
        "expected_intent": "order_query",
        "expected_path": "fast",
        "expected_tools": ["OrderQueryTool"],
    },
    {
        "id": "user_metric",
        "question": "昨天 DAU 和活跃买家多少？",
        "expected_intent": "user_metric",
        "expected_path": "fast",
        "expected_tools": ["UserMetricTool"],
    },
    {
        "id": "root_cause",
        "question": "2018-08-29 华东 GMV 为什么跌了？",
        "expected_intent": "root_cause",
        "expected_path": "deep",
        "expected_tools": [
            "RegionPerformanceQueryTool",
            "OrderQueryTool",
            "UserMetricTool",
            "CategoryRankTool",
            "FunnelAnalysisTool",
            "RefundAnalysisTool",
        ],
    },
    {
        "id": "dispatch_prompt",
        "group": "core",
        "question": "2018-08-29 华东 GMV 为什么跌了？请给出责任分发和通知草稿",
        "expected_intent": "root_cause",
        "expected_path": "deep",
        "expected_tools": ["OrderQueryTool", "CategoryRankTool", "RefundAnalysisTool"],
    },
    {
        "id": "notify_decision",
        "group": "open_judgement",
        "question": "2018-08-29 华东 GMV 为什么跌了？这个异常该不该发飞书？请说明可信度和是否需要人工确认",
        "expected_intent": "root_cause",
        "expected_path": "deep",
        "expected_tools": ["RegionPerformanceQueryTool", "OrderQueryTool", "CategoryRankTool"],
        "open_checks": ["notification_decision"],
    },
    {
        "id": "owner_priority",
        "group": "open_judgement",
        "question": "2018-08-29 华东 GMV 为什么跌了？哪些负责人应该优先处理？请按优先级排序",
        "expected_intent": "root_cause",
        "expected_path": "deep",
        "expected_tools": ["OrderQueryTool", "CategoryRankTool", "UserMetricTool"],
        "open_checks": ["owner_priority"],
    },
    {
        "id": "cause_priority",
        "group": "open_judgement",
        "question": "2018-08-29 华东 GMV 为什么跌了？如果品类和用户都下滑，主因怎么排序？",
        "expected_intent": "root_cause",
        "expected_path": "deep",
        "expected_tools": ["OrderQueryTool", "CategoryRankTool", "UserMetricTool"],
        "open_checks": ["cause_priority"],
    },
]


def request_json(method: str, url: str, payload: dict[str, Any] | None = None, timeout: float = 30.0) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload, ensure_ascii=False).encode("utf-8")
    request = Request(url, data=body, method=method)
    request.add_header("Accept", "application/json")
    if body is not None:
        request.add_header("Content-Type", "application/json; charset=utf-8")
    try:
        with urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except HTTPError as exc:
        detail = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} failed with HTTP {exc.code}: {detail}") from exc
    except URLError as exc:
        raise RuntimeError(f"{method} {url} failed: {exc.reason}") from exc


def as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def text_contains_any(value: Any, candidates: list[str]) -> bool:
    text = json.dumps(value, ensure_ascii=False)
    return any(candidate in text for candidate in candidates)


def route_priority_value(route: dict[str, Any]) -> int:
    raw = str(route.get("priority") or "")
    if raw.startswith("P") and raw[1:].isdigit():
        return int(raw[1:])
    return 99


def evaluate_notification_decision(notification_draft: dict[str, Any], evidence_confidence: dict[str, Any]) -> tuple[bool, str]:
    recommendation = str(notification_draft.get("notify_recommendation") or "")
    confidence = str(notification_draft.get("confidence") or evidence_confidence.get("overall") or "")
    has_manual_gate = isinstance(notification_draft.get("manual_confirmation_required"), bool)
    has_auto_send_policy = isinstance(notification_draft.get("auto_send_allowed"), bool)
    allowed_recommendations = {"send", "review_before_send", "log_only", "do_not_send"}
    ok = recommendation in allowed_recommendations and bool(confidence) and has_manual_gate and has_auto_send_policy
    detail = f"recommendation={recommendation or '-'}, confidence={confidence or '-'}, manual_gate={has_manual_gate}"
    return ok, detail


def evaluate_owner_priority(action_routing: list[Any]) -> tuple[bool, str]:
    routes = [route for route in action_routing if isinstance(route, dict)]
    p0_routes = [route for route in routes if route_priority_value(route) == 0]
    has_platform = text_contains_any(routes, ["平台运营"])
    has_category = text_contains_any(routes, ["类目", "行业运营", "商家运营"])
    has_action = all(bool(str(route.get("recommended_action") or route.get("next_action") or "")) for route in p0_routes)
    sorted_by_priority = [route_priority_value(route) for route in routes] == sorted(route_priority_value(route) for route in routes)
    ok = len(routes) >= 3 and len(p0_routes) >= 2 and has_platform and has_category and has_action
    detail = f"routes={len(routes)}, p0={len(p0_routes)}, has_platform={has_platform}, has_category={has_category}, sorted={sorted_by_priority}"
    return ok, detail


def evaluate_cause_priority(sections: list[Any], action_routing: list[Any], decision_trace: list[Any]) -> tuple[bool, str]:
    signal_keys = [
        str(section.get("key") or "")
        for section in sections
        if isinstance(section, dict) and str(section.get("status") or "") == "signal"
    ]
    has_category_signal = "category_drag" in signal_keys
    has_user_signal = "user_scale" in signal_keys
    has_business_evidence = "business_evidence" in signal_keys
    has_owner_route = text_contains_any(action_routing, ["类目", "增长", "平台运营"])
    has_explanation_trace = len([trace for trace in decision_trace if isinstance(trace, dict)]) >= 3
    ok = has_category_signal and has_user_signal and has_owner_route and has_explanation_trace
    detail = (
        f"signals={','.join(signal_keys) or '-'}, "
        f"category={has_category_signal}, user={has_user_signal}, business_evidence={has_business_evidence}"
    )
    return ok, detail


def evaluate_open_checks(root_cause: dict[str, Any], case: dict[str, Any]) -> tuple[bool | None, dict[str, str]]:
    checks = case.get("open_checks") or []
    if not checks:
        return None, {}

    action_routing = as_list(root_cause.get("action_routing"))
    notification_draft = as_dict(root_cause.get("notification_draft"))
    evidence_confidence = as_dict(root_cause.get("evidence_confidence"))
    sections = as_list(root_cause.get("sections"))
    decision_trace = as_list(root_cause.get("decision_trace"))

    results: dict[str, bool] = {}
    details: dict[str, str] = {}
    for check in checks:
        if check == "notification_decision":
            results[check], details[check] = evaluate_notification_decision(notification_draft, evidence_confidence)
        elif check == "owner_priority":
            results[check], details[check] = evaluate_owner_priority(action_routing)
        elif check == "cause_priority":
            results[check], details[check] = evaluate_cause_priority(sections, action_routing, decision_trace)
        else:
            results[check], details[check] = False, "unknown check"

    return all(results.values()), details


def provider_from_runtime(runtime: dict[str, Any], provider: str, model: str) -> tuple[str, str]:
    # The runtime endpoint currently focuses on business guardrails, so env args
    # remain the source of truth for comparison metadata.
    return provider, model


def evaluate_case(result: dict[str, Any], case: dict[str, Any], elapsed_ms: int) -> dict[str, Any]:
    root_cause = as_dict(result.get("root_cause"))
    tags = as_dict(result.get("tags"))
    tool_chain = [str(item) for item in as_list(result.get("tool_chain"))]
    action_routing = as_list(root_cause.get("action_routing"))
    notification_draft = as_dict(root_cause.get("notification_draft") or result.get("notification_draft"))
    response_text = json.dumps(result, ensure_ascii=False)
    open_check_ok, open_check_details = evaluate_open_checks(root_cause, case)

    expected_tools = case["expected_tools"]
    tool_hit_count = sum(1 for tool in expected_tools if tool in tool_chain)
    path_type = str(result.get("path_type", ""))
    intent = str(tags.get("intent") or result.get("intent") or infer_intent(result, tool_chain))
    root_cause_stable = None
    if case["expected_path"] == "deep":
        root_cause_stable = bool(root_cause.get("summary")) and len(action_routing) >= 3
    notification_usable = bool(notification_draft.get("title")) and bool(notification_draft.get("body")) if case["expected_path"] == "deep" else True
    format_stable = result.get("success") is True and isinstance(result.get("tool_chain"), list)

    return {
        "case_id": case["id"],
        "question": case["question"],
        "success": result.get("success") is True,
        "intent": intent,
        "intent_ok": intent == case["expected_intent"] if intent else case["expected_intent"] in response_text,
        "path_type": path_type,
        "path_ok": path_type == case["expected_path"],
        "tool_chain": tool_chain,
        "tool_ok": tool_hit_count == len(expected_tools),
        "root_cause_stable": root_cause_stable,
        "action_routing_count": len(action_routing),
        "notification_usable": notification_usable,
        "open_check_ok": open_check_ok,
        "open_check_details": open_check_details,
        "model_sensitivity": "medium" if case.get("group") == "open_judgement" else "low",
        "format_stable": format_stable,
        "elapsed_ms": elapsed_ms,
        "cost_note": "未接入 token usage 统计；当前只记录响应时间。",
    }


def infer_intent(result: dict[str, Any], tool_chain: list[str]) -> str:
    if as_dict(result.get("root_cause")):
        return "root_cause"
    if "OrderQueryTool" in tool_chain:
        return "order_query"
    if "UserMetricTool" in tool_chain:
        return "user_metric"
    if "GmvQueryTool" in tool_chain:
        return "gmv_query"
    if "CategoryRankTool" in tool_chain:
        return "category_rank"
    if "RegionPerformanceQueryTool" in tool_chain:
        return "region_compare"
    return ""


def score_mark(value: bool | None) -> str:
    if value is None:
        return "-"
    return "Y" if value else "N"


def render_markdown(provider: str, model: str, rows: list[dict[str, Any]], deepseek_rows: list[dict[str, Any]] | None = None) -> str:
    generated_at = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    passed = sum(
        1
        for row in rows
        if row["success"]
        and row["intent_ok"]
        and row["path_ok"]
        and row["tool_ok"]
        and row["format_stable"]
        and row["open_check_ok"] is not False
    )
    total = len(rows)
    lines = [
        "# Model Comparison",
        "",
        f"Generated at: {generated_at}",
        "",
        "## Scope",
        "",
        "This report evaluates the ecommerce Agent through its public demo APIs, not by calling model providers directly. The measured object is the end-to-end business chain: intent routing, path selection, Tool evidence, root cause structure, action routing, notification draft and latency.",
        "",
        "Note: standard fast-path questions use current demo seed dates, while the root-cause showcase uses the fixed Olist anomaly date `2018-08-29`.",
        "",
        "## Current Provider",
        "",
        f"- Provider: `{provider}`",
        f"- Model: `{model}`",
        f"- Passed core checks: `{passed}/{total}`",
        "",
        f"## {provider} / {model} Result",
        "",
        "| Case | Intent OK | Path OK | Tool OK | Root Cause Stable | Notification Usable | Open Judgement OK | Format Stable | Sensitivity | Latency |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | ---: |",
    ]
    for row in rows:
        lines.append(
            "| {case_id} | {intent_ok} | {path_ok} | {tool_ok} | {root_cause_stable} | {notification_usable} | {open_check_ok} | {format_stable} | {model_sensitivity} | {elapsed_ms} ms |".format(
                case_id=row["case_id"],
                intent_ok=score_mark(row["intent_ok"]),
                path_ok=score_mark(row["path_ok"]),
                tool_ok=score_mark(row["tool_ok"]),
                root_cause_stable=score_mark(row["root_cause_stable"]),
                notification_usable=score_mark(row["notification_usable"]),
                open_check_ok=score_mark(row["open_check_ok"]),
                format_stable=score_mark(row["format_stable"]),
                model_sensitivity=row["model_sensitivity"],
                elapsed_ms=row["elapsed_ms"],
            )
        )

    lines.extend([
        "",
        "## Detailed Notes",
        "",
    ])
    for row in rows:
        lines.extend([
            f"### {row['case_id']}",
            "",
            f"- Question: {row['question']}",
            f"- Actual intent: `{row['intent'] or 'unknown'}`",
            f"- Actual path: `{row['path_type']}`",
            f"- Tool chain: `{ ' -> '.join(row['tool_chain']) or '-' }`",
            f"- Action routing count: `{row['action_routing_count']}`",
            f"- Open judgement checks: `{score_mark(row['open_check_ok'])}`",
            f"- Open check detail: `{json.dumps(row['open_check_details'], ensure_ascii=False) if row['open_check_details'] else '-'}`",
            f"- Model sensitivity: `{row['model_sensitivity']}`",
            f"- Cost note: {row['cost_note']}",
            "",
        ])

    lines.extend([
        "## DeepSeek Result",
        "",
        "| Provider | Model | Status | Why |",
        "| --- | --- | --- | --- |",
        "| deepseek | deepseek-chat | ready_to_run | DeepSeek uses Spring AI's OpenAI-compatible provider. Set `MODEL_PROVIDER=deepseek`, `SPRING_AI_MODEL_CHAT=openai`, `MODEL_NAME=deepseek-chat`, and `DEEPSEEK_API_KEY` locally. |",
        "| deepseek | deepseek-reasoner | ready_to_run | Same provider wiring; use this model for reasoning-quality comparison when latency/cost are acceptable. |",
        "",
        "## Interpretation",
        "",
        "- 标准快路径/深路径主要验证 Tool 编排和业务 DTO 稳定性，因此对模型差异的敏感度较低。",
        "- `open_judgement` cases 会检查通知建议、责任分发和主因排序等结构化字段，比单纯看回答文本更适合这个项目。",
        "- 如果要进一步比较真正开放 CodeAct 能力，下一步应加入“由模型自主选择 Tool/生成执行代码”的链路，而不是只复用确定性业务 builder。",
    ])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Generate ecommerce Agent model comparison report.")
    parser.add_argument("--base-url", default=os.getenv("API_BASE_URL", "http://localhost:18080"))
    parser.add_argument("--provider", default=os.getenv("MODEL_PROVIDER", "dashscope"))
    parser.add_argument("--model", default=os.getenv("MODEL_NAME") or os.getenv("DASHSCOPE_MODEL", "qwen-max"))
    parser.add_argument("--output", default="model_comparison.md")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    request_json("GET", f"{base_url}/actuator/health", timeout=5)
    runtime = request_json("GET", f"{base_url}/api/ecommerce/runtime", timeout=10)
    provider, model = provider_from_runtime(runtime, args.provider, args.model)

    rows: list[dict[str, Any]] = []
    for case in CASES:
        started = time.time()
        result = request_json(
            "POST",
            f"{base_url}/api/ecommerce/answer",
            {"session_id": f"model-comparison-{case['id']}", "question": case["question"]},
            timeout=30,
        )
        elapsed_ms = int((time.time() - started) * 1000)
        rows.append(evaluate_case(result, case, elapsed_ms))

    output = Path(args.output)
    output.write_text(render_markdown(provider, model, rows), encoding="utf-8")
    print(f"[OK] wrote {output}")
    for row in rows:
        print(
            f"[{row['case_id']}] intent={score_mark(row['intent_ok'])} "
            f"path={score_mark(row['path_ok'])} tool={score_mark(row['tool_ok'])} "
            f"open={score_mark(row['open_check_ok'])} "
            f"format={score_mark(row['format_stable'])} latency={row['elapsed_ms']}ms"
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
