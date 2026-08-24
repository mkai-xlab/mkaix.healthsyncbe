@echo off
REM Khoi dong toan bo stack HealthSync (MySQL, Redis, Qdrant, Ollama, MailDev, Backend).
cd /d "%~dp0"

echo [1/3] Tao network knee-oa-net (bo qua neu da co)...
docker network create knee-oa-net >nul 2>&1

echo [2/3] Build va khoi dong container (lan dau co the mat vai phut)...
docker compose --profile database --profile rag up -d --build
if errorlevel 1 (
    echo.
    echo LOI: Khoi dong that bai. Kiem tra Docker Desktop da chay chua.
    pause
    exit /b 1
)

echo [3/3] Trang thai:
docker compose --profile database --profile rag ps

echo.
echo   Backend   http://localhost:8000/api/v1
echo   Swagger   http://localhost:8000/api/v1/swagger-ui.html
echo   MailDev   http://localhost:1080
echo   Qdrant    http://localhost:6333/dashboard
echo.
pause
