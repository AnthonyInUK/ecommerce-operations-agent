#!/usr/bin/env python3
"""Smoke test the ecommerce analysis demo endpoints.

This script intentionally uses only Python stdlib so it can run on a fresh
server after the Spring Boot app starts.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


DEFAULT_QUESTION = "2018-08-29 华东 GMV 为什么跌了？"


def request_json(method: str, url: str, payload: dict[str, Any] | None = None, timeout: float = 20.0) -> dict[str, Any]:
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


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def as_dict(value: Any) -> dict[str, Any]:
    return value if isinstance(value, dict) else {}


def as_list(value: Any) -> list[Any]:
    return value if isinstance(value, list) else []


def validate_root_cause(result: dict[str, Any], label: str) -> dict[str, Any]:
    require(result.get("success") is True, f"{label}: analysis success should be true")
    require(result.get("path_type") in {"deep", "trigger", "fast"}, f"{label}: unexpected path_type={result.get('path_type')}")

    root_cause = as_dict(result.get("root_cause"))
    require(bool(root_cause), f"{label}: root_cause is missing")
    require(bool(root_cause.get("summary")), f"{label}: root_cause.summary is missing")

    action_routing = as_list(root_cause.get("action_routing"))
    require(bool(action_routing), f"{label}: action_routing is missing")

    notification_draft = as_dict(root_cause.get("notification_draft"))
    require(bool(notification_draft.get("title")), f"{label}: notification_draft.title is missing")
    require(bool(notification_draft.get("body")), f"{label}: notification_draft.body is missing")

    confidence = as_dict(root_cause.get("evidence_confidence"))
    require(bool(confidence), f"{label}: evidence_confidence is missing")

    data_lineage = as_dict(root_cause.get("data_lineage"))
    require(bool(data_lineage), f"{label}: data_lineage is missing")

    tool_chain = as_list(result.get("tool_chain"))
    require(len(tool_chain) >= 6, f"{label}: expected multi-step tool_chain, got {tool_chain}")
    return root_cause


def run_answer_smoke(base_url: str, session_id: str, question: str) -> dict[str, Any]:
    started = time.time()
    result = request_json(
        "POST",
        f"{base_url}/api/ecommerce/answer",
        {"session_id": session_id, "question": question},
    )
    root_cause = validate_root_cause(result, "answer")
    elapsed_ms = int((time.time() - started) * 1000)
    print(f"[OK] answer root cause: elapsed={elapsed_ms}ms, routes={len(as_list(root_cause.get('action_routing')))}")
    print(f"     summary={str(root_cause.get('summary', ''))[:140]}")
    return result


def run_trigger_smoke(base_url: str) -> dict[str, Any]:
    started = time.time()
    result = request_json("POST", f"{base_url}/api/ecommerce/triggers/gmv-drop-watch/run-once")
    require(result.get("success") is True, f"trigger: success should be true, payload={result}")
    require(result.get("condition_passed") is True, f"trigger: condition_passed should be true, payload={result}")

    analysis = as_dict(result.get("analysis"))
    root_cause = validate_root_cause(analysis, "trigger.analysis")
    notification_draft = as_dict(result.get("notification_draft"))
    require(bool(notification_draft.get("title")), "trigger: top-level notification_draft.title is missing")

    elapsed_ms = int((time.time() - started) * 1000)
    print(
        "[OK] trigger root cause: "
        f"demo_report_date={result.get('demo_report_date')}, elapsed={elapsed_ms}ms, "
        f"routes={len(as_list(root_cause.get('action_routing')))}"
    )
    print(f"     draft={notification_draft.get('title')}")
    return result


def run_runtime_smoke(base_url: str) -> dict[str, Any]:
    result = request_json("GET", f"{base_url}/api/ecommerce/runtime")
    require(bool(as_dict(result.get("degradation_policy"))), "runtime: degradation_policy is missing")
    require(bool(as_dict(result.get("idempotency_policy"))), "runtime: idempotency_policy is missing")
    require(bool(as_dict(result.get("security_policy"))), "runtime: security_policy is missing")
    print("[OK] runtime rules: cache/degradation/idempotency/security are visible")
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description="Smoke test ecommerce Agent demo endpoints.")
    parser.add_argument("--base-url", default=os.getenv("API_BASE_URL", "http://localhost:18080"))
    parser.add_argument("--session-id", default="smoke-demo-session")
    parser.add_argument("--question", default=DEFAULT_QUESTION)
    parser.add_argument("--skip-trigger", action="store_true", help="Skip trigger endpoint when trigger is disabled.")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    try:
        print(f"[INFO] base_url={base_url}")
        run_runtime_smoke(base_url)
        run_answer_smoke(base_url, args.session_id, args.question)
        if not args.skip_trigger:
            run_trigger_smoke(base_url)
        print("[PASS] ecommerce demo smoke completed")
        return 0
    except Exception as exc:
        print(f"[FAIL] {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
