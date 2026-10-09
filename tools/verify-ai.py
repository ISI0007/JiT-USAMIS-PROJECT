#!/usr/bin/env python3
"""USAMIS AI module smoke test (runs against the live Java + AI services).

Exercises the end-to-end path: AI service health, token gate, Java RBAC on
/api/ai/*, a real prediction round-trip, and prediction durability. Prints a
pass/fail summary and exits non-zero on any failure.

Usage:
    python tools/verify-ai.py --base http://127.0.0.1:8080/usamis --ai http://127.0.0.1:8099

Prereqs: USAMIS running with the AI migration applied, the AI service running and
models trained, and ai.properties configured with a matching token.
"""
from __future__ import annotations

import argparse
import json
import sys
import time
import urllib.error
import urllib.request

OK = 0
FAIL = 0


def check(name: str, cond: bool, detail: str = "") -> None:
    global OK, FAIL
    if cond:
        OK += 1
        print(f"  [PASS] {name}  {detail}")
    else:
        FAIL += 1
        print(f"  [FAIL] {name}  {detail}")


def request(url: str, method: str = "GET", body=None, headers=None, cookie=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    req.add_header("X-Forwarded-For", f"10.77.{int(time.time()) % 250}.7")
    if headers:
        for k, v in headers.items():
            req.add_header(k, v)
    if cookie:
        req.add_header("Cookie", cookie)
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, r.read().decode(), r.headers
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode(), e.headers
    except Exception as e:  # noqa: BLE001
        return -1, str(e), {}


def login(api: str, user: str, pw: str):
    status, body, headers = request(f"{api}/auth/login", "POST",
                                    {"username": user, "password": pw})
    cookie = headers.get("Set-Cookie", "") if headers else ""
    cookie = cookie.split(";")[0] if cookie else None
    return status, body, cookie


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="http://127.0.0.1:8080/usamis")
    ap.add_argument("--ai", default="http://127.0.0.1:8099")
    ap.add_argument("--token", default=None, help="AI service token (default: read from env AI_SERVICE_TOKEN)")
    args = ap.parse_args()
    api = f"{args.base}/api"

    import os
    token = args.token or os.environ.get("AI_SERVICE_TOKEN", "test-token")

    print("\n=== USAMIS AI module smoke test ===")
    print(f"Base: {args.base}  AI: {args.ai}")

    # 0. AI service public health
    print("\n[ai service]")
    st, body, _ = request(f"{args.ai}/health")
    check("GET /health", st == 200, f"HTTP {st}")
    try:
        h = json.loads(body)
        check("status UP", h.get("status") == "UP", f"status={h.get('status')}")
        check("mlp loaded", h.get("models", {}).get("mlp_performance") is True,
              f"models={h.get('models')}")
    except Exception as e:  # noqa: BLE001
        check("health JSON", False, str(e))

    # 1. token gate
    st, _, _ = request(f"{args.ai}/api/v1/models")
    check("models w/o token -> 401", st == 401, f"HTTP {st}")
    st, _, _ = request(f"{args.ai}/api/v1/models", headers={"X-AI-Service-Token": token})
    check("models w/ token -> 200", st == 200, f"HTTP {st}")

    # 2. direct prediction (service-level)
    payload = {"student_id": 1, "features": {
        "prior_gpa": 3.2, "attendance_rate": 0.9, "assignment_avg": 82.0,
        "quiz_avg": 78.0, "credits_attempted": 18, "num_prior_courses": 6,
        "failure_count": 0, "year_of_study": 2}}
    st, body, _ = request(f"{args.ai}/api/v1/predict/performance", "POST", payload,
                          {"X-AI-Service-Token": token})
    check("predict -> 200", st == 200, f"HTTP {st}")
    try:
        p = json.loads(body)
        check("predicted_score in range", 0 <= p.get("predicted_score", -1) <= 100,
              f"score={p.get('predicted_score')}")
        check("has model_version", bool(p.get("model_version")), f"v={p.get('model_version')}")
    except Exception as e:  # noqa: BLE001
        check("predict JSON", False, str(e))

    # 3. Java-side auth + RBAC
    print("\n[java ai endpoints]")
    st, _, _ = request(f"{api}/ai/insights")
    check("anon /ai/insights -> 401", st == 401, f"HTTP {st}")

    st_admin, body_admin, ck_admin = login(api, "admin001", "admin123")
    check("admin login", st_admin == 200, f"HTTP {st_admin}")
    st_stu, _, ck_stu = login(api, "stu001", "stu123")
    check("student login", st_stu == 200, f"HTTP {st_stu}")

    st, _, _ = request(f"{api}/ai/status", cookie=ck_admin)
    check("admin /ai/status -> 200", st == 200, f"HTTP {st}")
    st, _, _ = request(f"{api}/ai/status", cookie=ck_stu)
    check("student /ai/status -> 403", st == 403, f"HTTP {st}")
    st, _, _ = request(f"{api}/ai/insights", cookie=ck_stu)
    check("student /ai/insights -> 403", st == 403, f"HTTP {st}")
    st, _, _ = request(f"{api}/ai/insights", cookie=ck_admin)
    check("admin /ai/insights -> 200", st == 200, f"HTTP {st}")

    # 4. prediction round-trip through Java (persisted)
    st, body, _ = request(f"{api}/ai/predict/1", "POST", {}, cookie=ck_admin, headers={"X-Forwarded-For": "10.77.9.9"})
    check("admin predict student 1 -> 200", st == 200, f"HTTP {st}")
    try:
        d = json.loads(body).get("data", {})
        check("insight has score", "predictedScore" in d, f"score={d.get('predictedScore')}")
    except Exception as e:  # noqa: BLE001
        check("predict payload", False, str(e))

    st, body, _ = request(f"{api}/ai/insights?limit=10", cookie=ck_admin)
    try:
        n = len(json.loads(body).get("data", []))
        check("prediction persisted", n >= 1, f"rows={n}")
    except Exception as e:  # noqa: BLE001
        check("insights list", False, str(e))

    # 5. student self-scope: student can predict own record only
    st, _, _ = request(f"{api}/ai/predict/1", "POST", {}, cookie=ck_stu)
    check("student predict others -> 403", st == 403, f"HTTP {st}")

    print()
    if FAIL == 0:
        print(f"ALL GREEN: {OK}/{OK + FAIL} checks passed")
        return 0
    print(f"{FAIL} FAILED, {OK} passed")
    return 1


if __name__ == "__main__":
    sys.exit(main())
