"""USAMIS AI service package.

Deep-learning analytics for the Jinling Institute of Technology academic MIS.
Kept import-light: heavy frameworks (torch) are imported lazily inside the
modules that need them so the FastAPI app can start and report health even if a
model artifact is missing.
"""

__version__ = "1.0.0"
