@echo off
REM Dung toan bo stack HealthSync. Du lieu trong volume van duoc giu.
cd /d "%~dp0"

echo Dang dung toan bo container...
docker compose --profile database --profile rag down

echo.
echo Da dung. Du lieu MySQL/Qdrant/Ollama van con trong volume.
echo.
pause
