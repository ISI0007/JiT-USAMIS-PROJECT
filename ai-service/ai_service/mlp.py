"""MLP — Student Performance Prediction.

Predicts a student's final grade (0-100) from prior academic signals, and derives
a binary "at risk" flag (predicted < RISK_THRESHOLD). Also exposes the trained
network's penultimate activations as a compact embedding, which the graph
recommender reuses for course suggestions.

Architecture (small on purpose — 4k rows does not justify a deep net):
    in(8) -> Linear(64) -> GELU -> Dropout -> Linear(32) -> GELU -> Linear(1)
Trained with MSE loss, Adam, early-stopping on a validation split.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

import numpy as np

from .config import MLP_FEATURES, RISK_THRESHOLD

_TORCH_AVAILABLE = True
try:  # keep module importable without torch so the API can start degraded
    import torch
    import torch.nn as nn
except Exception:  # pragma: no cover - exercised only when torch is absent
    _TORCH_AVAILABLE = False
    torch = None  # type: ignore
    nn = None  # type: ignore


def torch_available() -> bool:
    return _TORCH_AVAILABLE


if _TORCH_AVAILABLE:

    class MLPNet(nn.Module):
        def __init__(self, in_dim: int = len(MLP_FEATURES)):
            super().__init__()
            self.backbone = nn.Sequential(
                nn.Linear(in_dim, 64), nn.GELU(), nn.Dropout(0.10),
                nn.Linear(64, 32), nn.GELU(),
            )
            self.head = nn.Linear(32, 1)

        def forward(self, x, return_embedding: bool = False):
            emb = self.backbone(x)
            out = self.head(emb).squeeze(-1)
            if return_embedding:
                return out, emb
            return out

else:  # pragma: no cover
    class MLPNet:  # type: ignore
        def __init__(self, *a, **k):
            raise RuntimeError("torch is not installed")


@dataclass
class Normalizer:
    """Standardisation stats persisted alongside the model (part of the ABI)."""
    mean: np.ndarray
    std: np.ndarray

    @classmethod
    def fit(cls, X: np.ndarray) -> "Normalizer":
        mean = X.mean(axis=0)
        std = X.std(axis=0)
        std[std < 1e-6] = 1.0
        return cls(mean=mean.astype(np.float32), std=std.astype(np.float32))

    def transform(self, X: np.ndarray) -> np.ndarray:
        return (X - self.mean) / self.std


@dataclass
class TrainedMLP:
    net: object
    normalizer: Normalizer
    version: str
    metrics: dict
    feature_names: list

    def predict(self, X: np.ndarray, embeddings: bool = False):
        Xn = self.normalizer.transform(np.asarray(X, dtype=np.float32))
        t = torch.from_numpy(Xn)
        self.net.eval()
        with torch.no_grad():
            if embeddings:
                pred, emb = self.net(t, return_embedding=True)
                return pred.numpy(), emb.numpy()
            return self.net(t).numpy()

    def risk_flags(self, scores: np.ndarray) -> np.ndarray:
        return (np.asarray(scores) < RISK_THRESHOLD).astype(int)


def train(X: np.ndarray, y: np.ndarray, *, epochs: int = 300, lr: float = 3e-3,
          patience: int = 25, seed: int = 42, version: str = "1.0.0-synth") -> TrainedMLP:
    """Train the MLP with early stopping; returns a TrainedMLP ready to persist."""
    if not _TORCH_AVAILABLE:
        raise RuntimeError("torch is not installed")

    from .synthetic import train_test_split
    from .baseline import _metrics

    torch.manual_seed(seed)
    np.random.seed(seed)

    Xtr, Xval, ytr, yval = train_test_split(X, y, test_frac=0.2, seed=seed)
    normalizer = Normalizer.fit(Xtr)

    Xtr_t = torch.from_numpy(normalizer.transform(Xtr))
    ytr_t = torch.from_numpy(ytr.astype(np.float32))
    Xval_t = torch.from_numpy(normalizer.transform(Xval))
    yval_np = yval.astype(np.float32)

    net = MLPNet(in_dim=X.shape[1])
    opt = torch.optim.Adam(net.parameters(), lr=lr, weight_decay=1e-5)
    loss_fn = torch.nn.MSELoss()

    best_val, best_state, best_epoch, waited = float("inf"), None, -1, 0
    for epoch in range(epochs):
        net.train()
        opt.zero_grad()
        pred = net(Xtr_t)
        loss = loss_fn(pred, ytr_t)
        loss.backward()
        opt.step()

        net.eval()
        with torch.no_grad():
            val_pred = net(Xval_t).numpy()
        val_rmse = float(np.sqrt(np.mean((val_pred - yval_np) ** 2)))
        if val_rmse < best_val - 1e-4:
            best_val, best_epoch, waited = val_rmse, epoch, 0
            best_state = {k: v.clone() for k, v in net.state_dict().items()}
        else:
            waited += 1
            if waited >= patience:
                break

    if best_state is not None:
        net.load_state_dict(best_state)

    trained = TrainedMLP(net=net, normalizer=normalizer, version=version,
                         metrics={}, feature_names=list(MLP_FEATURES))
    trained.net.eval()
    with torch.no_grad():
        val_scores = trained.net(Xval_t).numpy()
    trained.metrics = _metrics(val_scores, yval_np, name="mlp_validation")
    trained.metrics["best_epoch"] = best_epoch
    trained.metrics["val_size"] = int(len(Xval))
    return trained


def save(trained: TrainedMLP, model_path, meta_path) -> None:
    """Persist weights + normaliser + metrics. Metadata is human-readable JSON."""
    import json
    from pathlib import Path
    model_path, meta_path = Path(model_path), Path(meta_path)
    model_path.parent.mkdir(parents=True, exist_ok=True)
    torch.save({
        "state_dict": trained.net.state_dict(),
        "in_dim": len(trained.feature_names),
        "mean": trained.normalizer.mean,
        "std": trained.normalizer.std,
    }, model_path)
    meta_path.write_text(json.dumps({
        "model": "mlp_performance",
        "version": trained.version,
        "framework": "pytorch",
        "feature_names": trained.feature_names,
        "risk_threshold": RISK_THRESHOLD,
        "metrics": trained.metrics,
        "trained_on": "synthetic",
    }, indent=2), encoding="utf-8")


def load(model_path, meta_path) -> Optional[TrainedMLP]:
    """Load a persisted model; returns None if artifacts are missing/corrupt."""
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
        net = MLPNet(in_dim=blob["in_dim"])
        net.load_state_dict(blob["state_dict"])
        net.eval()
        norm = Normalizer(mean=np.asarray(blob["mean"], dtype=np.float32),
                          std=np.asarray(blob["std"], dtype=np.float32))
        return TrainedMLP(net=net, normalizer=norm, version=meta.get("version", "unknown"),
                          metrics=meta.get("metrics", {}),
                          feature_names=meta.get("feature_names", list(MLP_FEATURES)))
    except Exception:
        return None
