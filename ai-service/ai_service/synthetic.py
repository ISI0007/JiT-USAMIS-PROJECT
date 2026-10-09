"""Deterministic synthetic academic data generator.

WHY synthetic: real student records are privacy-sensitive and were not available
for initial development. This generator produces a *reproducible* dataset with a
known ground-truth relationship so the MLP can be trained and — importantly —
honestly evaluated against a naive baseline before anyone claims real accuracy.

The generating process is deliberately noisy: a student's final score is a
weighted blend of latent ability, attendance, coursework, and Gaussian noise.
That means an honest model should clear the baseline but NOT reach ~0.99 R^2 —
if it did, the data would be leaking.
"""
from __future__ import annotations

import numpy as np

# Feature order is fixed and shared with config.MLP_FEATURES.
FEATURES = [
    "prior_gpa",         # 0.0 .. 4.0
    "attendance_rate",   # 0.0 .. 1.0
    "assignment_avg",    # 0 .. 100
    "quiz_avg",          # 0 .. 100
    "credits_attempted", # 0 .. 40
    "num_prior_courses", # 0 .. 20
    "failure_count",     # 0 .. 6
    "year_of_study",     # 1 .. 6
]


def _latent_ability(rng: np.random.Generator, n: int) -> np.ndarray:
    """Latent student ability ~ Beta distribution (skewed toward the middle)."""
    return rng.beta(5.0, 5.0, size=n)  # centred near 0.5


def generate(n: int = 4000, seed: int = 20261009) -> dict:
    """Return {'X': (n,8) float array, 'y': (n,) scores, 'feature_names': FEATURES}."""
    rng = np.random.default_rng(seed)
    ability = _latent_ability(rng, n)

    prior_gpa = np.clip(ability * 4.0 + rng.normal(0, 0.25, n), 0.0, 4.0)
    attendance_rate = np.clip(0.55 + 0.4 * ability + rng.normal(0, 0.08, n), 0.0, 1.0)
    assignment_avg = np.clip(45 + 50 * ability + rng.normal(0, 7, n), 0.0, 100.0)
    quiz_avg = np.clip(42 + 52 * ability + rng.normal(0, 9, n), 0.0, 100.0)
    credits_attempted = np.clip(rng.integers(12, 40, n), 1, 40)
    num_prior_courses = np.clip(rng.integers(0, 20, n), 0, 20)
    # Weaker students fail more — failure_count correlates negatively with ability
    failure_count = np.clip(rng.poisson((1.0 - ability) * 2.2), 0, 6)
    year_of_study = np.clip(rng.integers(1, 6, n), 1, 6)

    X = np.column_stack([
        prior_gpa, attendance_rate, assignment_avg, quiz_avg,
        credits_attempted, num_prior_courses, failure_count, year_of_study,
    ]).astype(np.float32)

    # Ground-truth score: weights sum to ~1.0 across the strongest signals.
    base = (
        0.34 * (prior_gpa / 4.0) * 100.0
        + 0.18 * attendance_rate * 100.0
        + 0.24 * assignment_avg
        + 0.24 * quiz_avg
    )
    penalty = 3.0 * failure_count
    y = np.clip(base - penalty + rng.normal(0, 4.0, n), 0.0, 100.0).astype(np.float32)

    return {"X": X, "y": y, "feature_names": list(FEATURES)}


def train_test_split(X: np.ndarray, y: np.ndarray, test_frac: float = 0.2, seed: int = 7):
    """Deterministic shuffle-and-split (no sklearn dependency needed here)."""
    rng = np.random.default_rng(seed)
    idx = rng.permutation(len(X))
    cut = int(len(X) * (1.0 - test_frac))
    tr, te = idx[:cut], idx[cut:]
    return X[tr], X[te], y[tr], y[te]
