"""FastAPI application — the network surface of the USAMIS AI service.

Security: every /api/v1/* route requires the shared service token header
(X-AI-Service-Token). The token is compared in constant time. If no token is
configured the service fails closed (all authenticated routes return 503).
"""
from __future__ import annotations

import hmac
import logging
from functools import lru_cache
from typing import Optional

import numpy as np
from fastapi import Depends, FastAPI, Header, HTTPException
from pydantic import BaseModel, Field

from . import __version__, config, graph, lstm, mlp
from .synthetic import FEATURES, generate

log = logging.getLogger("usamis.ai")
logging.basicConfig(level=logging.INFO)

app = FastAPI(
    title="USAMIS AI Service",
    version=__version__,
    description="Deep-learning analytics for the Jinling Institute of Technology academic MIS.",
)


# ══════════════════════════════════════════════════════════════
#  AUTH — shared service token, checked by the Java backend only
# ══════════════════════════════════════════════════════════════
def require_token(x_ai_service_token: Optional[str] = Header(default=None)) -> None:
    if not config.SERVICE_TOKEN:
        # Fail closed: no token configured means the operator has not secured
        # the service yet, so refuse to serve predictions rather than run open.
        raise HTTPException(status_code=503, detail="AI service token not configured")
    provided = x_ai_service_token or ""
    if not hmac.compare_digest(provided, config.SERVICE_TOKEN):
        raise HTTPException(status_code=401, detail="Invalid service token")


# ══════════════════════════════════════════════════════════════
#  LAZY MODEL REGISTRY — loads on first use, caches thereafter
# ══════════════════════════════════════════════════════════════
@lru_cache(maxsize=1)
def get_mlp():
    return mlp.load(config.MODELS_DIR / config.MLP_MODEL_FILE,
                    config.MODELS_DIR / config.MLP_META_FILE)


@lru_cache(maxsize=1)
def get_lstm():
    return lstm.load(config.MODELS_DIR / config.LSTM_MODEL_FILE,
                     config.MODELS_DIR / config.LSTM_META_FILE)


def reload_models() -> None:
    get_mlp.cache_clear()
    get_lstm.cache_clear()


# ══════════════════════════════════════════════════════════════
#  SCHEMAS
# ══════════════════════════════════════════════════════════════
class PerformanceRequest(BaseModel):
    student_id: int = Field(..., description="students.id (opaque to this service)")
    features: dict = Field(..., description="feature name -> value, see /health")


class PerformanceResponse(BaseModel):
    student_id: int
    predicted_score: float
    risk_probability: float
    at_risk: bool
    model_name: str
    model_version: str
    trained_on: str
    metrics: dict
    top_factors: list


class ForecastRequest(BaseModel):
    series: list = Field(..., description="historical values, oldest first")
    label: str = "enrollment"
    steps: int = Field(3, ge=1, le=12)


class ForecastResponse(BaseModel):
    label: str
    horizon: int
    forecast: list
    model_name: str
    model_version: str


class RecommendRequest(BaseModel):
    student_id: int
    department_id: int
    passed_course_ids: list = []
    enrolled_course_ids: list = []
    courses: list = []
    enrollments: list = []
    top_k: int = Field(5, ge=1, le=20)


class TrainRequest(BaseModel):
    rows: list = Field(..., description="list of {features: {...}, final_score: number}")
    version: str = "1.0.0-retrained"


# ══════════════════════════════════════════════════════════════
#  ROUTES
# ══════════════════════════════════════════════════════════════
@app.get("/health")
def health():
    """Public liveness probe — reports which models are actually loaded."""
    m = get_mlp()
    l = get_lstm()
    return {
        "status": "UP",
        "service": "usamis-ai",
        "version": __version__,
        "token_configured": bool(config.SERVICE_TOKEN),
        "features": FEATURES,
        "models": {
            "mlp_performance": bool(m),
            "lstm_enrollment": bool(l),
        },
        "mlp_metrics": (m.metrics if m else None),
    }


@app.get("/api/v1/models", dependencies=[Depends(require_token)])
def list_models():
    m = get_mlp()
    l = get_lstm()
    return {"models": [
        {"name": "mlp_performance", "version": (m.version if m else None),
         "loaded": bool(m), "metrics": (m.metrics if m else None), "trained_on": "synthetic"},
        {"name": "lstm_enrollment", "version": (l.version if l else None),
         "loaded": bool(l), "metrics": (l.metrics if l else None), "trained_on": "synthetic"},
        {"name": "graph_course_recommender", "version": "1.0.0", "loaded": True,
         "metrics": None, "trained_on": "relational"},
    ]}


def _feature_vector(features: dict) -> np.ndarray:
    missing = [f for f in FEATURES if f not in features]
    if missing:
        raise HTTPException(status_code=422, detail=f"missing features: {missing}")
    return np.asarray([[float(features[f]) for f in FEATURES]], dtype=np.float32)


def _explain(features: dict) -> list:
    """Cheap, honest factor attribution: sign + relative magnitude of each input.

    This is a directional explanation, not a Shapley value — it states which
    inputs push the prediction up or down, and how unusual they are. Labelled as
    such so nobody mistakes it for a causal claim.
    """
    refs = {  # (healthy_reference, spread) — rough, documented normalisers
        "prior_gpa": (3.0, 1.0), "attendance_rate": (0.9, 0.2),
        "assignment_avg": (80.0, 15.0), "quiz_avg": (78.0, 18.0),
        "credits_attempted": (24.0, 8.0), "num_prior_courses": (10.0, 5.0),
        "failure_count": (0.0, 1.5), "year_of_study": (2.0, 1.5),
    }
    out = []
    for f in FEATURES:
        ref, spread = refs.get(f, (0.0, 1.0))
        delta = (float(features[f]) - ref) / (spread or 1.0)
        if abs(delta) < 0.25:
            continue
        out.append({"factor": f, "direction": "up" if delta > 0 else "down",
                    "weight": round(abs(delta), 2)})
    out.sort(key=lambda r: r["weight"], reverse=True)
    return out[:4]


@app.post("/api/v1/predict/performance", response_model=PerformanceResponse,
          dependencies=[Depends(require_token)])
def predict_performance(req: PerformanceRequest):
    model = get_mlp()
    if model is None:
        raise HTTPException(status_code=503, detail="MLP model not trained — run train_models.py")
    X = _feature_vector(req.features)
    score = float(np.clip(model.predict(X)[0], 0.0, 100.0))
    # Risk probability: logistic over how far BELOW the pass threshold the score
    # is. score >> threshold -> gap very negative -> risk ~ 0; score << threshold ->
    # risk ~ 1. The /6.0 sharpens the transition around the threshold.
    gap = score - config.RISK_THRESHOLD
    risk = 1.0 / (1.0 + float(np.exp(gap / 6.0)))
    return PerformanceResponse(
        student_id=req.student_id,
        predicted_score=round(score, 2),
        risk_probability=round(risk, 5),
        at_risk=bool(score < config.RISK_THRESHOLD),
        model_name="mlp_performance",
        model_version=model.version,
        trained_on="synthetic",
        metrics=model.metrics,
        top_factors=_explain(req.features),
    )


@app.post("/api/v1/forecast/enrollment", response_model=ForecastResponse,
          dependencies=[Depends(require_token)])
def forecast_enrollment(req: ForecastRequest):
    model = get_lstm()
    if model is None:
        raise HTTPException(status_code=503, detail="LSTM model not trained — run train_models.py")
    series = np.asarray([float(v) for v in req.series], dtype=np.float32)
    if len(series) < model.window:
        raise HTTPException(status_code=422, detail=f"need >= {model.window} history points")
    try:
        fc = model.forecast(series, steps=req.steps)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e))
    return ForecastResponse(label=req.label, horizon=req.steps,
                            forecast=[round(float(v), 2) for v in fc],
                            model_name="lstm_enrollment", model_version=model.version)


@app.post("/api/v1/recommend/courses", dependencies=[Depends(require_token)])
def recommend_courses(req: RecommendRequest):
    rec = graph.build_from_rows(req.courses, req.enrollments)
    student = graph.StudentProfile(
        id=req.student_id, department_id=req.department_id,
        passed_course_ids=set(int(x) for x in req.passed_course_ids),
        enrolled_course_ids=set(int(x) for x in req.enrolled_course_ids),
    )
    recs = rec.recommend(student, top_k=req.top_k)
    return {"student_id": req.student_id, "recommendations": recs,
            "model_name": "graph_course_recommender", "model_version": "1.0.0"}


@app.post("/api/v1/train/performance", dependencies=[Depends(require_token)])
def train_performance(req: TrainRequest):
    """Retrain the MLP on a supplied dataset and hot-swap the served model.

    Admin-only at the Java layer (MANAGE_AI permission). Kept in the service so a
    data scientist can retrain without a Java redeploy. Guarded by the token.
    """
    if not mlp.torch_available():
        raise HTTPException(status_code=503, detail="torch not installed")
    if len(req.rows) < 100:
        raise HTTPException(status_code=422, detail="need at least 100 rows to retrain")
    try:
        X = np.asarray([[float(r["features"][f]) for f in FEATURES] for r in req.rows], dtype=np.float32)
        y = np.asarray([float(r["final_score"]) for r in req.rows], dtype=np.float32)
    except (KeyError, TypeError, ValueError) as e:
        raise HTTPException(status_code=422, detail=f"bad row payload: {e}")
    trained = mlp.train(X, y, version=req.version)
    mlp.save(trained, config.MODELS_DIR / config.MLP_MODEL_FILE,
             config.MODELS_DIR / config.MLP_META_FILE)
    reload_models()
    return {"status": "retrained", "version": req.version, "metrics": trained.metrics}


@app.post("/api/v1/bootstrap", dependencies=[Depends(require_token)])
def bootstrap_models():
    """Train + persist both models from the synthetic generator (dev convenience)."""
    data = generate()
    m = mlp.train(data["X"], data["y"])
    mlp.save(m, config.MODELS_DIR / config.MLP_MODEL_FILE, config.MODELS_DIR / config.MLP_META_FILE)
    # Synthetic semester series for the LSTM (smooth trend + seasonality + noise).
    rng = np.random.default_rng(11)
    t = np.arange(24)
    series = 300 + 25 * np.sin(t / 2.0) + 8 * t + rng.normal(0, 10, len(t))
    l = lstm.train(series, window=4)
    lstm.save(l, config.MODELS_DIR / config.LSTM_MODEL_FILE, config.MODELS_DIR / config.LSTM_META_FILE)
    reload_models()
    return {"status": "bootstrapped", "mlp": m.metrics, "lstm": l.metrics}
