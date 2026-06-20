# BBC SMS — one-command launch helpers.
.PHONY: prod demo down reset logs ps

## prod  — PRODUCTION: clean schema, NO demo data (create your first admin yourself).
prod:
	docker compose up -d --build

## demo  — DEMO: full sample dataset + logins principal/econome/parent1 (password: "password").
demo:
	docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build

## down  — stop containers, keep the database.
down:
	docker compose down

## reset — wipe everything incl. the database volume. Run this when switching prod <-> demo.
reset:
	docker compose down -v

## logs  — follow backend logs.
logs:
	docker compose logs -f backend

## ps    — show stack status.
ps:
	docker compose ps
