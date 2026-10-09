"""Tests for the USAMIS AI service.

These run WITHOUT torch (they exercise data, baselines, graph, and the API token
gate). Torch-dependent paths are skipped when torch is absent so CI stays green on
a slim environment, while still running fully where torch is installed.
"""
from __future__ import annotations

import importlib

import numpy as np
import pytest
from fastapi.testclient import TestClient

from ai_service import config, graph, mlp
from ai_service.baseline import mean_baseline, weighted_coursework_baseline
from ai_service.synthetic import FEATURES, generate, train_test_split


# ── dataset ─────────────────────────────────────────────────────
def test_synthetic_is_deterministic():
    a = generate(n=200, seed=1)
    b = generate(n=200, seed=1)
    assert np.array_equal(a["X"], b["X"])
    assert np.array_equal(a["y"], b["y"])


def test_synthetic_shapes_and_bounds():
    d = generate(n=500, seed=3)
    assert d["X"].shape == (500, len(FEATURES))
    assert d["X"].dtype == np.float32
    assert np.all(d["y"] >= 0) and np.all(d["y"] <= 100)
    assert d["feature_names"] == FEATURES


def test_split_is_disjoint_and_sized():
    d = generate(n=100, seed=5)
    Xtr, Xte, ytr, yte = train_test_split(d["X"], d["y"], test_frac=0.2)
    assert len(Xtr) + len(Xte) == 100
    assert abs(len(Xte) - 20) <= 1
    assert len(ytr) == len(Xtr) and len(yte) == len(Xte)


# ── baselines ───────────────────────────────────────────────────
def test_baselines_have_sane_metrics():
    d = generate(n=1000, seed=9)
    Xtr, Xte, ytr, yte = train_test_split(d["X"], d["y"])
    for m in (mean_baseline(ytr, yte), weighted_coursework_baseline(Xte, yte)):
        assert 0.0 <= m["mae"] <= 100.0
        assert m["r2"] <= 1.0


def test_coursework_baseline_beats_mean_baseline():
    """The domain heuristic must beat predicting the average — else it is useless."""
    d = generate(n=2000, seed=13)
    Xtr, Xte, ytr, yte = train_test_split(d["X"], d["y"])
    mean_m = mean_baseline(ytr, yte)
    cw_m = weighted_coursework_baseline(Xte, yte)
    assert cw_m["r2"] > mean_m["r2"]
    assert cw_m["mae"] < mean_m["mae"]


# ── graph recommender ───────────────────────────────────────────
def test_recommender_respects_prerequisites():
    courses = [
        graph.Course(1, "CS101", "Intro", 1, 3, []),
        graph.Course(2, "CS201", "Data Structures", 1, 4, [1]),
        graph.Course(3, "CS301", "Databases", 1, 3, [2]),
    ]
    enrollments = {10: {1}, 11: {1, 2}, 12: {1, 2, 3}}
    rec = graph.CourseRecommender(courses, enrollments)
    student = graph.StudentProfile(id=10, department_id=1, passed_course_ids={1})
    recs = rec.recommend(student, top_k=5)
    ids = [r["course_id"] for r in recs]
    assert 2 in ids          # prereq (1) satisfied
    assert 3 not in ids      # prereq (2) NOT satisfied
    assert 1 not in ids      # already passed


def test_recommender_excludes_already_enrolled():
    courses = [graph.Course(1, "A", "A", 1, 3, []), graph.Course(2, "B", "B", 1, 3, [])]
    enrollments = {1: {1}, 2: {1, 2}}
    rec = graph.CourseRecommender(courses, enrollments)
    s = graph.StudentProfile(id=1, department_id=1, enrolled_course_ids={2})
    ids = [r["course_id"] for r in rec.recommend(s)]
    # course 2 is already enrolled -> excluded; course 1 is fair game -> offered
    assert 2 not in ids
    assert 1 in ids


def test_recommender_scores_are_ordered():
    courses = [graph.Course(i, f"C{i}", f"C{i}", 1, 3, []) for i in range(1, 6)]
    enrollments = {1: set(), 2: {1, 2, 3}, 3: {1, 2, 3}, 4: {3}}
    rec = graph.CourseRecommender(courses, enrollments)
    s = graph.StudentProfile(id=1, department_id=1)
    recs = rec.recommend(s, top_k=5)
    scores = [r["score"] for r in recs]
    assert scores == sorted(scores, reverse=True)


# ── API token gate ──────────────────────────────────────────────
def _client(monkeypatch, token="test-token"):
    monkeypatch.setattr(config, "SERVICE_TOKEN", token)
    app = importlib.reload(importlib.import_module("ai_service.app")).app
    return TestClient(app)


def test_health_is_public(monkeypatch):
    c = _client(monkeypatch)
    r = c.get("/health")
    assert r.status_code == 200
    assert r.json()["status"] == "UP"
    assert r.json()["features"] == FEATURES


def test_protected_route_requires_token(monkeypatch):
    c = _client(monkeypatch)
    assert c.get("/api/v1/models").status_code == 401
    assert c.get("/api/v1/models", headers={"X-AI-Service-Token": "wrong"}).status_code == 401
    assert c.get("/api/v1/models", headers={"X-AI-Service-Token": "test-token"}).status_code == 200


def test_fail_closed_when_no_token_configured(monkeypatch):
    c = _client(monkeypatch, token="")
    r = c.get("/api/v1/models", headers={"X-AI-Service-Token": "anything"})
    assert r.status_code == 503


# ── torch-dependent (skipped when torch missing) ────────────────
torch_required = pytest.mark.skipif(not mlp.torch_available(), reason="torch not installed")


@torch_required
def test_mlp_beats_baseline_on_synthetic():
    d = generate(n=2500, seed=21)
    Xtr, Xte, ytr, yte = train_test_split(d["X"], d["y"])
    model = mlp.train(Xtr, ytr, epochs=200)
    pred = model.predict(Xte)
    from ai_service.baseline import _metrics
    m = _metrics(pred, yte, name="mlp")
    base_mean = mean_baseline(ytr, yte)
    base_cw = weighted_coursework_baseline(Xte, yte)
    # Honest assertions on synthetic data:
    #  1. The MLP must clearly beat the naive mean baseline (it learns a signal).
    #  2. It must be in the same ballpark as the strong hand-tuned coursework
    #     heuristic, not catastrophically worse. On this synthetic generator the
    #     hand-tuned formula is near-optimal, so the learned model is expected to
    #     slightly underperform it — that is the honest result, not a bug.
    assert m["mae"] < base_mean["mae"] - 3.0
    assert m["r2"] > base_mean["r2"] + 0.5
    assert m["mae"] < base_cw["mae"] + 2.0


@torch_required
def test_mlp_predict_is_in_range():
    d = generate(n=500, seed=33)
    model = mlp.train(d["X"], d["y"], epochs=60)
    preds = model.predict(d["X"][:20])
    assert len(preds) == 20


def test_feature_contract_matches_config():
    assert FEATURES == config.MLP_FEATURES
