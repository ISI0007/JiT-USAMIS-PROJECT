@echo off
REM ============================================================
REM  USAMIS - run.bat
REM  Starts the full stack on Windows:
REM    1) PostgreSQL must already be running (service)
REM    2) AI micro-service on :8099 (loopback)
REM    3) Java app on :8080 (single public port)
REM  Usage:  run.bat            (start everything)
REM          run.bat ai         (AI service only)
REM          run.bat app        (Java app only)
REM ============================================================
setlocal
cd /d "%~dp0"

set AI_DIR=D:\usamis-ai
set AI_VENV=%AI_DIR%\venv\Scripts
set TOKEN=usamis-ai-dev-token-2026

if "%~1"=="ai"  goto :ai
if "%~1"=="app" goto :app

:all
echo [1/2] Starting AI service on 127.0.0.1:8099 ...
call :start_ai
timeout /t 3 /nobreak >nul
echo [2/2] Starting Java app on :8080 ...
call :start_app
goto :eof

:ai
call :start_ai
goto :eof

:app
call :start_app
goto :eof

:start_ai
if not exist "%AI_VENV%\python.exe" (
  echo   !! AI venv not found at %AI_VENV%
  echo      Create it once:  python -m venv %AI_DIR%\venv ^&^& %AI_VENV%\pip install -r ai-service\requirements.txt
  exit /b 1
)
REM AI_SERVICE_TOKEN must be in the SAME process that launches uvicorn.
start "USAMIS-AI" cmd /k "cd /d %AI_DIR% && set AI_SERVICE_TOKEN=%TOKEN% && %AI_VENV%\python.exe -m uvicorn ai_service.app:app --host 127.0.0.1 --port 8099"
echo   AI service launched (window: USAMIS-AI).
exit /b 0

:start_app
echo   Compiling + launching Java app (embedded Tomcat) ...
cd /d "%~dp0java"
if exist build.bat ( call build.bat ) else (
  echo   (no build.bat; assuming prebuilt target\ or using Maven/Gradle if present)
)
cd /d "%~dp0"
if exist java\run-app.cmd ( call java\run-app.cmd ) else (
  echo   Launch the app with your usual embedded-Tomcat main class (port 8080, context /usamis).
)
exit /b 0
