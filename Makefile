# HealthSync backend - one-command stack control.
# Chay `make` la up toan bo (MySQL, Redis, Qdrant, Ollama, MailDev, Backend).

.DEFAULT_GOAL := up
.PHONY: up down restart logs ps clean rebuild help

## up      - Khoi dong toan bo stack (mac dinh)
up:
	./up.sh up

## down    - Dung va xoa container (giu nguyen du lieu)
down:
	./up.sh down

## restart - Dung roi khoi dong lai
restart:
	./up.sh restart

## logs    - Xem log tat ca service
logs:
	./up.sh logs

## ps      - Xem trang thai container
ps:
	./up.sh ps

## rebuild - Build lai image backend roi khoi dong lai
rebuild:
	docker compose --profile database --profile rag build --no-cache be
	./up.sh up

## clean   - Xoa ca volume (MAT TOAN BO DU LIEU)
clean:
	./up.sh clean

## help    - Liet ke cac lenh
help:
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/^## /  make /'
