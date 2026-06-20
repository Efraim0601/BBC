#!/usr/bin/env bash
# ============================================================================
#  BBC SMS — server (re)deployment.
#  - pulls the latest code from Git BEFORE building anything,
#  - generates a self-signed TLS certificate on first run,
#  - rebuilds the images and force-recreates the containers,
#  - cleans up dangling images and waits for the API to be healthy.
#  Safe to re-run after every code update (that's the point).
#
#  Config (env or .env): DOMAIN, BBC_ADMIN_PASSWORD, BBC_JWT_SECRET, PUBLIC_ORIGIN…
#  Usage:  ./deploy.sh           (or: make deploy / make redeploy)
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

# 0) Pull the latest code BEFORE building anything. If the pull updates this very
#    script, re-exec it once so the new version runs (guarded against a loop).
if [ -z "${BBC_DEPLOY_REEXEC:-}" ] && [ -d .git ] && command -v git >/dev/null 2>&1; then
  branch="$(git rev-parse --abbrev-ref HEAD 2>/dev/null || echo main)"
  echo "→ Recherche de mises à jour Git (branche ${branch})…"
  if git fetch --quiet origin "${branch}" 2>/dev/null; then
    local_rev="$(git rev-parse HEAD)"
    remote_rev="$(git rev-parse "origin/${branch}" 2>/dev/null || echo "")"
    if [ -n "${remote_rev}" ] && [ "${local_rev}" != "${remote_rev}" ]; then
      echo "→ Mise à jour disponible (${local_rev:0:7} → ${remote_rev:0:7}) — git pull…"
      if ! git pull --ff-only origin "${branch}"; then
        echo "✗ Fast-forward impossible (modifications locales ou divergence)." >&2
        echo "  Réglez le conflit puis relancez ./deploy.sh." >&2
        exit 1
      fi
      echo "→ Code mis à jour. Redémarrage du script…"
      export BBC_DEPLOY_REEXEC=1
      exec bash "$0" "$@"          # re-run the freshly pulled deploy.sh
    fi
    echo "→ Déjà à jour ($(git rev-parse --short HEAD))."
  else
    echo "⚠ git fetch a échoué (réseau ?). Poursuite avec le code local."
  fi
fi

# docker compose reads .env natively for the containers. The script itself only
# needs DOMAIN (for the cert CN); read it safely without sourcing the file.
if [ -z "${DOMAIN:-}" ] && [ -f .env ]; then
  DOMAIN="$(grep -E '^DOMAIN=' .env | tail -1 | cut -d= -f2- | tr -d '"'\''' | xargs || true)"
fi
DOMAIN="${DOMAIN:-localhost}"
HTTPS_PORT="20443"
CERT_DIR="./certs-tls"
COMPOSE="docker compose -f docker-compose.server.yml"

echo "──────────────────────────────────────────────────────────────"
echo "  BBC SMS — déploiement serveur  (domaine: ${DOMAIN})"
echo "──────────────────────────────────────────────────────────────"

# 1) Self-signed certificate (generated once; delete certs-tls/ to renew).
if [ ! -s "${CERT_DIR}/server.crt" ] || [ ! -s "${CERT_DIR}/server.key" ]; then
  # A bare IP must go in an IP: SAN entry, a hostname in a DNS: entry.
  if printf '%s' "${DOMAIN}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
    SAN="IP:${DOMAIN},DNS:localhost,IP:127.0.0.1"
  else
    SAN="DNS:${DOMAIN},DNS:localhost,IP:127.0.0.1"
  fi
  echo "→ Génération du certificat auto-signé (CN=${DOMAIN}, SAN=${SAN}, 825 j)…"
  mkdir -p "${CERT_DIR}"
  openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout "${CERT_DIR}/server.key" -out "${CERT_DIR}/server.crt" \
    -subj "/CN=${DOMAIN}" \
    -addext "subjectAltName=${SAN}"
  chmod 600 "${CERT_DIR}/server.key"
else
  echo "→ Certificat existant réutilisé (${CERT_DIR}/server.crt)."
fi

# 2) Build images and recreate containers.
echo "→ Build des images et recréation des conteneurs…"
${COMPOSE} up -d --build --force-recreate --remove-orphans

# 3) Drop dangling images left by the rebuild.
docker image prune -f >/dev/null 2>&1 || true

# 4) Wait for the API to answer.
echo -n "→ Attente du backend "
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    -X POST http://127.0.0.1:28080/api/auth/login \
    -H 'Content-Type: application/json' -d '{"username":"_","password":"_"}' 2>/dev/null || true)
  if [ "${code}" = "401" ] || [ "${code}" = "200" ]; then echo " OK"; break; fi
  echo -n "."; sleep 2
done

echo "──────────────────────────────────────────────────────────────"
echo "  ✓ Déployé."
echo "    Frontend : https://${DOMAIN}:${HTTPS_PORT}"
echo "    (certificat auto-signé — avertissement navigateur attendu)"
echo "──────────────────────────────────────────────────────────────"
${COMPOSE} ps
