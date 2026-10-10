# USAMIS AI Service

Separate **Python FastAPI** micro-service that adds deep-learning analytics to the
existing Java/PostgreSQL **USAMIS** application, without touching its stack.

> Design rule: the AI service is **never exposed to the public internet** and
> **never receives the raw user session**. The Java backend authenticates the
> caller, checks permissions, then calls this service over the internal network
> with a shared service token. The service holds *no* database credentials of
> its own — features arrive in the request body.

## Endpoints

| Method | Path                | Purpose                                            |
|--------|---------------------|----------------------------------------------------|
| GET    | /health             | Liveness + which models are loaded                 |
| GET    | /api/v1/models      | Model registry (name, version, metrics)            |
| POST   | /api/v1/predict/performance | MLP — predict a student's final grade / risk |
| POST   | /api/v1/forecast/enrollment | LSTM — forecast enrollment / demand        |
| POST   | /api/v1/recommend/courses   | Graph-based course recommendations         |
| POST   | /api/v1/train/performance   | Retrain MLP on a supplied dataset (admin)  |

## Security model

1. `X-AI-Service-Token` must match `AI_SERVICE_TOKEN` (env). Constant-time compare.
2. Bind to `127.0.0.1` by default (`AI_HOST`). Only the Java backend should reach it.
3. No CORS. Request bodies carry aggregate features, not PII (student names are never sent).
4. Model artifacts live in `ai-service/models/` and are versioned, not committed when binary-heavy.

## Honesty about accuracy

The bundled models are trained on the deterministic **synthetic** generator in
`ai_service/synthetic.py` (documented below). Real predictive accuracy can only be
claimed after training on real historical records and evaluating on a held-out split.
Every response therefore carries `model_version` and `trained_on` so the UI can label
predictions as indicative, not authoritative.

## Run

```bash
cd ai-service
python -m venv .venv && . .venv/Scripts/activate   # Windows
pip install -r requirements.txt
python train_models.py                              # writes models/*.pt + *.json
uvicorn ai_service.app:app --host 127.0.0.1 --port 8099
```

## Test

```bash
pytest -q
```
