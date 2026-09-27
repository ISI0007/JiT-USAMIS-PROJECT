@echo off
setlocal

REM USAMIS one-click Docker deployment for Windows.
REM Make sure Docker Desktop is running before executing this file.

cd /d "%~dp0"

if not exist ".env" (
    copy /Y ".env.example" ".env" >nul
    echo Created .env from .env.example.
    echo Review the database password before using this deployment in production.
)

echo Building and starting USAMIS...
docker compose up --build -d
if errorlevel 1 (
    echo Deployment failed. Check that Docker Desktop is running.
    exit /b 1
)

echo.
echo USAMIS is starting at:
echo http://localhost:8080/usamis/
echo.
echo Demo administrator account:
echo Username: admin001
echo Password: admin123
echo.
docker compose ps
endlocal
