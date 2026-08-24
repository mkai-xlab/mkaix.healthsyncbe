@echo off
REM Stop the full HealthSync stack. Volume data is preserved.
cd /d "%~dp0"

echo Stopping all containers...
docker compose --profile database --profile rag down

echo.
echo Stopped. MySQL, Qdrant and Ollama data is still kept in volumes.
echo.
pause
