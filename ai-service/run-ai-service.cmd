@echo off
REM USAMIS AI service launcher (Windows)
REM Runtime lives on D:\usamis-ai (C: drive is low on space):
REM   venv       -> D:\usamis-ai\venv
REM   pip cache  -> D:\usamis-ai\pip-cache
REM   temp       -> D:\usamis-ai\tmp
REM   models     -> D:\usamis-ai\models
setlocal
set "PY=D:\usamis-ai\venv\Scripts\python.exe"
set "MODELS=D:\usamis-ai\models"
set "PIP_CACHE_DIR=D:\usamis-ai\pip-cache"
set "TMP=D:\usamis-ai\tmp"
set "TEMP=D:\usamis-ai\tmp"

if not exist "%PY%" (
  echo [error] venv not found at %PY% — create it with:
  echo   py -m venv D:\usamis-ai\venv
  echo   D:\usamis-ai\venv\Scripts\python -m pip install -r requirements.txt
  exit /b 1
)

if not exist "D:\usamis-ai\models\mlp_performance.pt" (
  echo [info] no trained model found - running train_models.py
  "%PY%" train_models.py
)

set "AI_MODELS_DIR=%MODELS%"
"%PY%" -m uvicorn ai_service.app:app --host 127.0.0.1 --port 8099
endlocal