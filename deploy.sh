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

# docker compose reads .env natively for the containers. The script itself needs
# DOMAIN (cert CN) and USE_SSLIP; read them safely without sourcing the file.
read_env() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '"'\''' | xargs || true; }
if [ -f .env ]; then
  [ -z "${DOMAIN:-}" ]    && DOMAIN="$(read_env DOMAIN)"
  [ -z "${USE_SSLIP:-}" ] && USE_SSLIP="$(read_env USE_SSLIP)"
fi
DOMAIN="${DOMAIN:-localhost}"
USE_SSLIP="${USE_SSLIP:-0}"
HTTPS_PORT="20443"
CERT_DIR="./certs-tls"
COMPOSE="docker compose -f docker-compose.server.yml"

is_ipv4() { printf '%s' "$1" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; }

# sslip.io support — give a bare IP a real DNS name without owning a domain.
# <ip>.sslip.io (dotted) and <ip-with-dashes>.sslip.io both resolve straight back
# to <ip>, so the browser gets a hostname and the cert a proper DNS SAN. Two ways:
#   • USE_SSLIP=1 with a bare-IP DOMAIN          → derive <ip-dashes>.sslip.io
#   • DOMAIN already set to a *.sslip.io name     → used as-is (IP recovered for SAN)
EFFECTIVE_DOMAIN="${DOMAIN}"
SSLIP_IP=""
case "${USE_SSLIP}" in 1|true|yes|on|TRUE|YES|ON) SSLIP_ON=1 ;; *) SSLIP_ON=0 ;; esac

if [ "${SSLIP_ON}" = "1" ] && is_ipv4 "${DOMAIN}"; then
  SSLIP_IP="${DOMAIN}"
  EFFECTIVE_DOMAIN="${DOMAIN//./-}.sslip.io"
  echo "→ sslip.io activé : ${DOMAIN} → ${EFFECTIVE_DOMAIN}"
elif printf '%s' "${DOMAIN}" | grep -qiE '\.sslip\.io\.?$'; then
  # DOMAIN is already an sslip.io hostname — recover the embedded IPv4 for the SAN.
  label="${DOMAIN%.}"; label="${label%.sslip.io}"; label="${label##*.}"
  if printf '%s' "${label}" | grep -qE '^[0-9]+-[0-9]+-[0-9]+-[0-9]+$'; then
    SSLIP_IP="${label//-/.}"
  else
    SSLIP_IP="$(printf '%s' "${DOMAIN}" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+' | tail -1)"
  fi
  echo "→ Domaine sslip.io détecté : ${EFFECTIVE_DOMAIN}${SSLIP_IP:+ (IP ${SSLIP_IP})}"
fi

# Keep CORS in step with the host the browser actually uses. When sslip.io
# rewrites the host, advertise BOTH origins (sslip name + bare IP) — unless the
# caller already pinned PUBLIC_ORIGIN in the shell environment.
if [ "${EFFECTIVE_DOMAIN}" != "${DOMAIN}" ] && [ -z "${PUBLIC_ORIGIN:-}" ]; then
  PUBLIC_ORIGIN="https://${EFFECTIVE_DOMAIN}:${HTTPS_PORT}"
  [ -n "${SSLIP_IP}" ] && PUBLIC_ORIGIN="${PUBLIC_ORIGIN},https://${SSLIP_IP}:${HTTPS_PORT}"
  export PUBLIC_ORIGIN
  echo "→ PUBLIC_ORIGIN (CORS) = ${PUBLIC_ORIGIN}"
fi

echo "──────────────────────────────────────────────────────────────"
echo "  BBC SMS — déploiement serveur  (domaine: ${EFFECTIVE_DOMAIN})"
echo "──────────────────────────────────────────────────────────────"

# 1) Self-signed certificate. Regenerated when missing OR when it no longer covers
#    the current host (e.g. you just switched a bare IP over to sslip.io).
need_cert=0
if [ ! -s "${CERT_DIR}/server.crt" ] || [ ! -s "${CERT_DIR}/server.key" ]; then
  need_cert=1
elif ! openssl x509 -in "${CERT_DIR}/server.crt" -noout -text 2>/dev/null \
        | grep -qiF "${EFFECTIVE_DOMAIN}"; then
  echo "→ Le certificat existant ne couvre pas ${EFFECTIVE_DOMAIN} — régénération…"
  need_cert=1
fi

if [ "${need_cert}" = "1" ]; then
  # A bare IP goes in an IP: SAN, a hostname in a DNS: entry. With sslip.io we add
  # both the DNS name and the underlying IP so either way of connecting validates.
  if is_ipv4 "${EFFECTIVE_DOMAIN}"; then
    SAN="IP:${EFFECTIVE_DOMAIN},DNS:localhost,IP:127.0.0.1"
  else
    SAN="DNS:${EFFECTIVE_DOMAIN},DNS:localhost,IP:127.0.0.1"
    [ -n "${SSLIP_IP}" ] && SAN="DNS:${EFFECTIVE_DOMAIN},IP:${SSLIP_IP},DNS:localhost,IP:127.0.0.1"
  fi
  echo "→ Génération du certificat auto-signé (CN=${EFFECTIVE_DOMAIN}, SAN=${SAN}, 825 j)…"
  mkdir -p "${CERT_DIR}"
  openssl req -x509 -nodes -newkey rsa:2048 -days 825 \
    -keyout "${CERT_DIR}/server.key" -out "${CERT_DIR}/server.crt" \
    -subj "/CN=${EFFECTIVE_DOMAIN}" \
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
echo "    Frontend : https://${EFFECTIVE_DOMAIN}:${HTTPS_PORT}"
echo "    (certificat auto-signé — avertissement navigateur attendu)"
echo "──────────────────────────────────────────────────────────────"
${COMPOSE} ps
