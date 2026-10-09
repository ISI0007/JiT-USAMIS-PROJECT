"""Runtime configuration, read from the environment (never hard-coded)."""
from __future__ import annotations

import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
MODELS_DIR = Path(os.environ.get("AI_MODELS_DIR", BASE_DIR / "models"))

# Shared secret the Java backend must present. Empty => service refuses all
# authenticated calls (fail-closed) rather than running open.
SERVICE_TOKEN = os.environ.get("AI_SERVICE_TOKEN", "")

# Bound to loopback by default: only the Java backend on the same host should
# reach the AI service. Override deliberately for a private network deployment.
HOST = os.environ.get("AI_HOST", "127.0.0.1")
PORT = int(os.environ.get("AI_PORT", "8099"))

# Model artifact file names (under MODELS_DIR)
MLP_MODEL_FILE = "mlp_performance.pt"
MLP_META_FILE = "mlp_performance.json"
LSTM_MODEL_FILE = "lstm_enrollment.pt"
LSTM_META_FILE = "lstm_enrollment.json"
GRAPH_META_FILE = "graph_recommender.json"

# Feature contract for the MLP — order is part of the model ABI. Do not reorder
# without retraining; the JSON meta records this list for the client.
MLP_FEATURES = [
    "prior_gpa",
    "attendance_rate",
    "assignment_avg",
    "quiz_avg",
    "credits_attempted",
    "num_prior_courses",
    "failure_count",
    "year_of_study",
]

RISK_THRESHOLD = 60.0  # predicted final score below this => "at risk"
