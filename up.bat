@echo off
REM Start the full HealthSync stack: MySQL, Redis, Qdrant, Ollama, MailDev, Backend.
cd /d "%~dp0"

REM The compose network is declared as "external", so it is not created
REM automatically. Check first, create only when missing.
echo [1/3] Checking network knee-oa-net...
docker network inspect knee-oa-net >nul 2>&1
if errorlevel 1 (
    echo       Not found. Creating it...
    docker network create knee-oa-net >nul
) else (
    echo       Already exists. Skipping.
)

REM Both profiles are required. Without them MySQL, Qdrant and Ollama stay down.
echo [2/3] Building and starting containers (first run may take a few minutes)...
docker compose --profile database --profile rag up -d --build
if errorlevel 1 (
    echo.
    echo ERROR: Startup failed. Make sure Docker Desktop is running.
    pause
    exit /b 1
)

echo [3/3] Container status:
docker compose --profile database --profile rag ps

echo.
echo   Backend   http://localhost:8000/api/v1
echo   Swagger   http://localhost:8000/api/v1/swagger-ui.html
echo   MailDev   http://localhost:1080
echo   Qdrant    http://localhost:6333/dashboard
echo.
pause
