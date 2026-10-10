@echo off
REM ============================================================
REM  USAMIS - stop.bat
REM  Stops the AI service and the Java app started by run.bat.
REM  The PostgreSQL service is left running (it is a shared
REM  system service; stop it manually only if you mean to).
REM ============================================================
setlocal
echo Stopping USAMIS AI service (window: USAMIS-AI) ...
taskkill /FI "WINDOWTITLE eq USAMIS-AI*" /T /F >nul 2>&1

echo Stopping USAMIS Java app (window: USAMIS-APP) ...
taskkill /FI "WINDOWTITLE eq USAMIS-APP*" /T /F >nul 2>&1

echo Stopping any listener on :8099 ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8099" ^| findstr "LISTENING"') do taskkill /PID %%p /F >nul 2>&1

echo Stopping any listener on :8080 (USAMIS app) ...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do taskkill /PID %%p /F >nul 2>&1

echo Done. (PostgreSQL left running.)
