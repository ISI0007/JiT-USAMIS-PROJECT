"""LSTM — Academic Trend Forecasting.

Forecasts a future value of a per-semester time series (enrollment counts, course
demand, average cohort performance) from the preceding window. Useful for
registrar / academic-planning dashboards.

Model: single-layer LSTM encoder producing a fixed-size hidden state, then a
linear head that predicts the next value. Inputs are scaled with the training
mean/std persisted in the model metadata.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import numpy as np

_TORCH_AVAILABLE = True
try:
    import torch
    import torch.nn as nn
except Exception:  # pragma: no cover
    _TORCH_AVAILABLE = False
    torch = None  # type: ignore
    nn = None  # type: ignore


def torch_available() -> bool:
    return _TORCH_AVAILABLE


if _TORCH_AVAILABLE:

    class LSTMNet(nn.Module):
        def __init__(self, hidden: int = 32):
            super().__init__()
            self.lstm = nn.LSTM(input_size=1, hidden_size=hidden, batch_first=True)
            self.head = nn.Linear(hidden, 1)

        def forward(self, x):
            out, (h, _c) = self.lstm(x)
            return self.head(h[-1]).squeeze(-1)

else:  # pragma: no cover
    class LSTMNet:  # type: ignore
        def __init__(self, *a, **k):
            raise RuntimeError("torch is not installed")


def make_windows(series: np.ndarray, window: int) -> tuple:
    """Turn a 1-D series into (X, y) sliding windows. X shape (n-window, window, 1)."""
    series = np.asarray(series, dtype=np.float32).ravel()
    if len(series) <= window:
        return np.empty((0, window, 1), dtype=np.float32), np.empty((0,), dtype=np.float32)
    X, y = [], []
    for i in range(len(series) - window):
        X.append(series[i:i + window])
        y.append(series[i + window])
    return np.asarray(X, dtype=np.float32)[..., None], np.asarray(y, dtype=np.float32)


@dataclass
class TrainedLSTM:
    net: object
    mu: float
    sigma: float
    window: int
    version: str
    metrics: dict

    def _scale(self, arr):
        return (arr - self.mu) / self.sigma

    def _unscale(self, arr):
        return arr * self.sigma + self.mu

    def forecast(self, recent: np.ndarray, steps: int = 1) -> list:
        """Autoregressively forecast `steps` future values from a recent window."""
        window = np.asarray(recent, dtype=np.float32).ravel()
        if len(window) < self.window:
            raise ValueError(f"need at least {self.window} history points")
        buf = list(window[-self.window:])
        out = []
        self.net.eval()
        for _ in range(steps):
            x = torch.from_numpy(self._scale(np.asarray(buf[-self.window:], dtype=np.float32))[None, :, None])
            with torch.no_grad():
                pred = float(self.net(x).item())
            val = float(self._unscale(np.asarray([pred]))[0])
            buf.append(val)
            out.append(val)
        return out


def train(series: np.ndarray, window: int = 4, *, epochs: int = 400, lr: float = 5e-3,
          patience: int = 30, seed: int = 42, version: str = "1.0.0-synth") -> TrainedLSTM:
    if not _TORCH_AVAILABLE:
        raise RuntimeError("torch is not installed")
    torch.manual_seed(seed)
    np.random.seed(seed)

    series = np.asarray(series, dtype=np.float32).ravel()
    mu = float(series.mean())
    sigma = float(series.std()) or 1.0
    scaled = (series - mu) / sigma

    X, y = make_windows(scaled, window)
    if len(X) < 8:
        raise ValueError("series too short to train an LSTM")

    cut = int(len(X) * 0.8)
    Xtr = torch.from_numpy(X[:cut]); ytr = torch.from_numpy(y[:cut])
    Xval = torch.from_numpy(X[cut:]); yval = y[cut:]

    net = LSTMNet()
    opt = torch.optim.Adam(net.parameters(), lr=lr)
    loss_fn = torch.nn.MSELoss()

    best, best_state, waited = float("inf"), None, 0
    for _epoch in range(epochs):
        net.train(); opt.zero_grad()
        loss = loss_fn(net(Xtr), ytr)
        loss.backward(); opt.step()
        net.eval()
        with torch.no_grad():
            vp = net(Xval).numpy()
        vrmse = float(np.sqrt(np.mean((vp - yval) ** 2)))
        if vrmse < best - 1e-4:
            best, waited = vrmse, 0
            best_state = {k: v.clone() for k, v in net.state_dict().items()}
        else:
            waited += 1
            if waited >= patience:
                break
    if best_state is not None:
        net.load_state_dict(best_state)
    net.eval()

    return TrainedLSTM(net=net, mu=mu, sigma=sigma, window=window, version=version,
                       metrics={"val_rmse_scaled": round(best, 5)})


def save(trained: TrainedLSTM, model_path, meta_path) -> None:
    import json
    from pathlib import Path
    model_path, meta_path = Path(model_path), Path(meta_path)
    model_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({"state_dict": trained.net.state_dict(), "mu": trained.mu,
                "sigma": trained.sigma, "window": trained.window}, model_path)
    meta_path.write_text(json.dumps({
        "model": "lstm_enrollment", "version": trained.version, "framework": "pytorch",
        "window": trained.window, "metrics": trained.metrics, "trained_on": "synthetic",
    }, indent=2), encoding="utf-8")


def load(model_path, meta_path) -> Optional[TrainedLSTM]:
    if not _TORCH_AVAILABLE:
        return None
    import json
    from pathlib import Path
    model_path, meta_path = Path(model_path), Path(meta_path)
    if not model_path.exists() or not meta_path.exists():
        return None
    try:
        blob = torch.load(model_path, map_location="cpu", weights_only=False)
        meta = json.loads(meta_path.read_text(encoding="utf-8"))
        net = LSTMNet()
        net.load_state_dict(blob["state_dict"])
        net.eval()
        return TrainedLSTM(net=net, mu=float(blob["mu"]), sigma=float(blob["sigma"]),
                           window=int(blob["window"]), version=meta.get("version", "unknown"),
                           metrics=meta.get("metrics", {}))
    except Exception:
        return None
