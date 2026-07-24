# BBC SMS — one-command launch helpers.
.PHONY: prod demo down reset logs ps deploy redeploy server-down server-logs deploy-domain domain-down domain-logs seed-test-roles

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

## deploy / redeploy — SERVER deployment: self-signed TLS, ports 20000-30000.
##   Generates the cert if missing, rebuilds images and recreates containers.
deploy redeploy:
	./deploy.sh

## server-down — stop the server stack (keep data).
server-down:
	docker compose -f docker-compose.server.yml down

## server-logs — follow the server backend logs.
server-logs:
	docker compose -f docker-compose.server.yml logs -f backend

## deploy-domain — DOMAIN deployment: trusted Let's Encrypt TLS, ports 80/443.
##   Requires DOMAIN's DNS A record to already point at this server.
deploy-domain:
	./deploy-domain.sh

## domain-down — stop the domain stack (keep data).
domain-down:
	docker compose -f docker-compose.letsencrypt.yml down

## domain-logs — follow the domain backend logs.
domain-logs:
	docker compose -f docker-compose.letsencrypt.yml logs -f backend

## seed-test-roles — create demo.* login accounts for every role (DB direct).
seed-test-roles:
	./tools/seed-test-roles.sh
