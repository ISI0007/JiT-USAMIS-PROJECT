"""Train and persist the USAMIS AI models from the synthetic generator.

Run from the ai-service directory:
    python train_models.py

It prints an honest comparison of the MLP against two naive baselines so the
value of the learned model is visible, not asserted.
"""
from __future__ import annotations

import json
import sys

import numpy as np

from ai_service import config, lstm, mlp
from ai_service.baseline import mean_baseline, weighted_coursework_baseline
from ai_service.synthetic import generate, train_test_split


def main() -> int:
    print("== USAMIS AI model training ==")
    data = generate(n=4000, seed=20261009)
    X, y = data["X"], data["y"]
    Xtr, Xte, ytr, yte = train_test_split(X, y, test_frac=0.2, seed=7)

    print(f"dataset: {len(X)} rows, {X.shape[1]} features: {data['feature_names']}")

    # Baselines first — the bar the model must clear.
    base_mean = mean_baseline(ytr, yte)
    base_cw = weighted_coursework_baseline(Xte, yte)
    print("baseline mean      :", json.dumps(base_mean))
    print("baseline coursework:", json.dumps(base_cw))

    if not mlp.torch_available():
        print("torch is not installed — cannot train the MLP. Install requirements first.")
        return 2

    print("training MLP ...")
    model = mlp.train(Xtr, ytr, version="1.0.0-synth")
    from ai_service.baseline import _metrics
    test_pred = model.predict(Xte)
    test_metrics = _metrics(test_pred, yte, name="mlp_test")
    print("mlp test           :", json.dumps(test_metrics))

    # Honesty guard: a model that "beats" the data-generating process is leaking.
    if test_metrics["r2"] > 0.97:
        print("WARNING: R^2 > 0.97 on noisy synthetic data suggests leakage — investigate.")

    mlp.save(model, config.MODELS_DIR / config.MLP_MODEL_FILE,
             config.MODELS_DIR / config.MLP_META_FILE)
    print(f"saved MLP -> {config.MODELS_DIR / config.MLP_MODEL_FILE}")

    # ── LSTM series: semester enrollment with trend + seasonality + noise ──
    rng = np.random.default_rng(11)
    t = np.arange(24)
    series = 300 + 25 * np.sin(t / 2.0) + 8 * t + rng.normal(0, 10, len(t))
    lmodel = lstm.train(series, window=4, version="1.0.0-synth")
    lstm.save(lmodel, config.MODELS_DIR / config.LSTM_MODEL_FILE,
              config.MODELS_DIR / config.LSTM_META_FILE)
    print("LSTM metrics       :", json.dumps(lmodel.metrics))
    print(f"saved LSTM -> {config.MODELS_DIR / config.LSTM_MODEL_FILE}")

    print("done.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
