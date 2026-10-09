"""Naive baselines the deep model must beat to justify its existence.

A model that cannot beat "predict the mean" or "average the coursework" is not
adding value. These functions are used by train_models.py and the tests so the
reported metrics include an honest reference point.
"""
from __future__ import annotations

import numpy as np


def mean_baseline(y_train: np.ndarray, y_test: np.ndarray) -> dict:
    """Predict the training mean for every test row."""
    pred = np.full_like(y_test, float(np.mean(y_train)))
    return _metrics(pred, y_test, name="mean_baseline")


def weighted_coursework_baseline(X_test: np.ndarray, y_test: np.ndarray) -> dict:
    """Hand-tuned linear blend of the coursework columns (a domain heuristic).

    Uses prior_gpa(0), attendance(1), assignment(2), quiz(3) — the same signals
    a human advisor would eyeball, with no learned parameters.
    """
    prior_gpa = X_test[:, 0]
    attendance = X_test[:, 1]
    assignment = X_test[:, 2]
    quiz = X_test[:, 3]
    pred = np.clip(
        0.34 * (prior_gpa / 4.0) * 100.0
        + 0.18 * attendance * 100.0
        + 0.24 * assignment
        + 0.24 * quiz,
        0.0, 100.0,
    )
    return _metrics(pred, y_test, name="coursework_baseline")


def _metrics(pred: np.ndarray, y_true: np.ndarray, name: str) -> dict:
    pred = np.asarray(pred, dtype=np.float64)
    y_true = np.asarray(y_true, dtype=np.float64)
    err = pred - y_true
    mae = float(np.mean(np.abs(err)))
    rmse = float(np.sqrt(np.mean(err ** 2)))
    ss_res = float(np.sum(err ** 2))
    ss_tot = float(np.sum((y_true - np.mean(y_true)) ** 2)) or 1.0
    r2 = 1.0 - ss_res / ss_tot
    # Classification view: at-risk = final score < 60
    truth_risk = y_true < 60.0
    pred_risk = pred < 60.0
    tp = int(np.sum(truth_risk & pred_risk))
    fp = int(np.sum(~truth_risk & pred_risk))
    fn = int(np.sum(truth_risk & ~pred_risk))
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    recall = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = (2 * precision * recall / (precision + recall)) if (precision + recall) else 0.0
    return {
        "name": name,
        "mae": round(mae, 4),
        "rmse": round(rmse, 4),
        "r2": round(r2, 4),
        "risk_precision": round(precision, 4),
        "risk_recall": round(recall, 4),
        "risk_f1": round(f1, 4),
    }
