# HealthSync backend - one-command stack control.
# Running plain `make` starts everything: MySQL, Redis, Qdrant, Ollama, MailDev, Backend.

# Both profiles are required. Without them MySQL, Qdrant and Ollama stay down.
COMPOSE := docker compose --profile database --profile rag
NETWORK := knee-oa-net

.DEFAULT_GOAL := up
.PHONY: up down restart logs ps rebuild clean network help

## up      - Start the full stack (default)
up: network
	$(COMPOSE) up -d --build
	$(COMPOSE) ps

## down    - Stop and remove containers (volume data is kept)
down:
	$(COMPOSE) down

## restart - Stop, then start again
restart: down up

## logs    - Follow logs from every service
logs:
	$(COMPOSE) logs -f --tail=100

## ps      - Show container status
ps:
	$(COMPOSE) ps

## rebuild - Rebuild the backend image from scratch, then start
rebuild: network
	$(COMPOSE) build --no-cache be
	$(COMPOSE) up -d
	$(COMPOSE) ps

## clean   - Remove containers AND volumes (DESTROYS ALL DATA)
clean:
	$(COMPOSE) down -v

# The compose network is declared as "external", so it is not created
# automatically. Check first, create only when missing.
network:
	@docker network inspect $(NETWORK) >/dev/null 2>&1 || docker network create $(NETWORK)

## help    - List available commands
help:
	@grep -E '^## ' $(MAKEFILE_LIST) | sed 's/^## /  make /'
