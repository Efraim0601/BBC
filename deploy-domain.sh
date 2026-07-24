#!/usr/bin/env bash
# ============================================================================
#  BBC SMS — déploiement DOMAINE réel avec certificat Let's Encrypt (fiable).
#  Variante de deploy.sh pour un domaine possédé (ex: bbcomplex.com) plutôt
#  qu'une IP nue : ports standard 80/443, certificat signé par Let's Encrypt
#  (aucun avertissement navigateur), renouvellement automatique.
#
#  Prérequis : DOMAIN (dans .env) doit déjà pointer vers l'IP publique de ce
#  serveur (enregistrement DNS A chez le registrar) — sinon la validation
#  ACME HTTP-01 échouera.
#
#  Config (.env) : DOMAIN, LETSENCRYPT_EMAIL, BBC_ADMIN_PASSWORD,
#  BBC_JWT_SECRET, PUBLIC_ORIGIN…
#  Usage : ./deploy-domain.sh   (ou : make deploy-domain)
#  Sûr à relancer après chaque mise à jour (idempotent).
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")"

COMPOSE="docker compose -f docker-compose.letsencrypt.yml"
NGINX_TEMPLATE="./frontend/nginx.letsencrypt.conf"
NGINX_GENERATED="./nginx-domain.generated.conf"

# 0) Pull le code le plus récent avant de construire quoi que ce soit.
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
        exit 1
      fi
      export BBC_DEPLOY_REEXEC=1
      exec bash "$0" "$@"
    fi
    echo "→ Déjà à jour ($(git rev-parse --short HEAD))."
  else
    echo "⚠ git fetch a échoué (réseau ?). Poursuite avec le code local."
  fi
fi

read_env() { grep -E "^$1=" .env | tail -1 | cut -d= -f2- | tr -d '"'\''' | xargs || true; }
if [ -f .env ]; then
  [ -z "${DOMAIN:-}" ]            && DOMAIN="$(read_env DOMAIN)"
  [ -z "${LETSENCRYPT_EMAIL:-}" ] && LETSENCRYPT_EMAIL="$(read_env LETSENCRYPT_EMAIL)"
fi

if [ -z "${DOMAIN:-}" ] || printf '%s' "${DOMAIN}" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "✗ DOMAIN doit être un vrai nom de domaine (pas une IP) dans .env — ex: bbcomplex.com" >&2
  echo "  Pour une IP nue, utilisez plutôt ./deploy.sh (certificat auto-signé)." >&2
  exit 1
fi
if [ -z "${LETSENCRYPT_EMAIL:-}" ]; then
  echo "✗ LETSENCRYPT_EMAIL requis dans .env (contact pour les alertes d'expiration Let's Encrypt)." >&2
  exit 1
fi

echo "──────────────────────────────────────────────────────────────"
echo "  BBC SMS — déploiement domaine (Let's Encrypt)  —  ${DOMAIN}"
echo "──────────────────────────────────────────────────────────────"

# 1) Vérifier que le DNS pointe bien vers ce serveur avant de tenter l'ACME.
SERVER_IP="$(curl -s -4 --max-time 5 https://icanhazip.com || true)"
resolve() { getent ahostsv4 "$1" 2>/dev/null | awk '{print $1}' | head -1; }
DOMAIN_IP="$(resolve "${DOMAIN}")"
if [ -z "${DOMAIN_IP}" ]; then
  echo "✗ ${DOMAIN} ne résout à aucune IP pour l'instant. Configurez l'enregistrement DNS A" >&2
  echo "  chez votre registrar vers ${SERVER_IP:-<IP publique de ce serveur>} puis relancez." >&2
  exit 1
fi
if [ -n "${SERVER_IP}" ] && [ "${DOMAIN_IP}" != "${SERVER_IP}" ]; then
  echo "✗ ${DOMAIN} résout vers ${DOMAIN_IP}, mais ce serveur a l'IP ${SERVER_IP}." >&2
  echo "  Corrigez l'enregistrement DNS A puis relancez." >&2
  exit 1
fi
echo "→ DNS OK : ${DOMAIN} → ${DOMAIN_IP}"

WWW_OK=0
WWW_IP="$(resolve "www.${DOMAIN}")"
if [ -n "${WWW_IP}" ] && [ "${WWW_IP}" = "${DOMAIN_IP}" ]; then
  WWW_OK=1
  echo "→ DNS OK : www.${DOMAIN} → ${WWW_IP}"
else
  echo "→ www.${DOMAIN} ne résout pas vers ce serveur — certificat émis pour ${DOMAIN} seul."
fi

# CORS/PUBLIC_ORIGIN par défaut si non fixé explicitement.
if [ -z "${PUBLIC_ORIGIN:-}" ]; then
  PUBLIC_ORIGIN="https://${DOMAIN}"
  [ "${WWW_OK}" = "1" ] && PUBLIC_ORIGIN="${PUBLIC_ORIGIN},https://www.${DOMAIN}"
  export PUBLIC_ORIGIN
fi

# 2) Générer la conf nginx à partir du template (substitution du domaine).
if [ "${WWW_OK}" = "1" ]; then
  sed "s/__DOMAIN__/${DOMAIN}/g" "${NGINX_TEMPLATE}" > "${NGINX_GENERATED}"
else
  sed "s/__DOMAIN__/${DOMAIN}/g" "${NGINX_TEMPLATE}" \
    | sed "s/ www\.${DOMAIN}//g" > "${NGINX_GENERATED}"
fi
echo "→ Conf nginx générée (${NGINX_GENERATED})."

CERT_DOMAIN_ARGS=(-d "${DOMAIN}")
[ "${WWW_OK}" = "1" ] && CERT_DOMAIN_ARGS+=(-d "www.${DOMAIN}")

# 3) Le certificat existe-t-il déjà dans le volume certbot-etc ?
CERT_EXISTS=0
if ${COMPOSE} run --rm --entrypoint sh certbot -c \
     "test -f /etc/letsencrypt/live/${DOMAIN}/fullchain.pem" >/dev/null 2>&1; then
  CERT_EXISTS=1
  echo "→ Certificat existant réutilisé pour ${DOMAIN}."
fi

if [ "${CERT_EXISTS}" = "0" ]; then
  # 3a) Certificat factice (auto-signé, 1 jour) pour que nginx puisse démarrer
  #     avant que le vrai certificat n'existe.
  echo "→ Génération d'un certificat temporaire (le temps d'obtenir le vrai)…"
  ${COMPOSE} run --rm --entrypoint sh certbot -c "
    mkdir -p /etc/letsencrypt/live/${DOMAIN} &&
    openssl req -x509 -nodes -newkey rsa:2048 -days 1 \
      -keyout /etc/letsencrypt/live/${DOMAIN}/privkey.pem \
      -out /etc/letsencrypt/live/${DOMAIN}/fullchain.pem \
      -subj '/CN=${DOMAIN}'
  "

  # 3b) Démarrer la stack (nginx sert désormais le certificat factice).
  echo "→ Démarrage de la stack (certificat temporaire)…"
  ${COMPOSE} up -d --build --remove-orphans

  # 3c) Retirer le certificat factice puis demander le vrai via webroot.
  ${COMPOSE} run --rm --entrypoint sh certbot -c \
    "rm -rf /etc/letsencrypt/live/${DOMAIN} /etc/letsencrypt/archive/${DOMAIN} /etc/letsencrypt/renewal/${DOMAIN}.conf"

  echo "→ Demande du certificat Let's Encrypt pour : ${CERT_DOMAIN_ARGS[*]}…"
  ${COMPOSE} run --rm --entrypoint certbot certbot certonly \
    --webroot -w /var/www/certbot \
    --email "${LETSENCRYPT_EMAIL}" --agree-tos --no-eff-email \
    --rsa-key-size 2048 --non-interactive \
    "${CERT_DOMAIN_ARGS[@]}"

  echo "→ Certificat obtenu. Rechargement de nginx…"
  ${COMPOSE} exec frontend nginx -s reload
else
  # 4) Build/recrée les conteneurs normalement (mise à jour de code).
  echo "→ Build des images et recréation des conteneurs…"
  ${COMPOSE} up -d --build --force-recreate --remove-orphans
  ${COMPOSE} exec frontend nginx -s reload 2>/dev/null || true
fi

docker image prune -f >/dev/null 2>&1 || true

# 5) Attendre que l'API réponde.
echo -n "→ Attente du backend "
for _ in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' \
    -X POST http://127.0.0.1:28081/api/auth/login \
    -H 'Content-Type: application/json' -d '{"username":"_","password":"_"}' 2>/dev/null || true)
  if [ "${code}" = "401" ] || [ "${code}" = "200" ]; then echo " OK"; break; fi
  echo -n "."; sleep 2
done

# 6) Renouvellement : cron hôte idempotent qui recharge nginx chaque semaine
#    (le conteneur certbot renouvelle le fichier ; nginx doit relire le cert).
#    Robustesse set -e/pipefail : pas de crontab existant → crontab -l exit 1.
CRON_LINE="0 3 * * 1 cd $(pwd) && ${COMPOSE} exec frontend nginx -s reload >/dev/null 2>&1"
{
  crontab -l 2>/dev/null | grep -vF "docker-compose.letsencrypt.yml exec frontend nginx -s reload" || true
  echo "${CRON_LINE}"
} | crontab -
echo "→ Cron de rechargement nginx hebdomadaire installé (lundi 03h00)."

echo "──────────────────────────────────────────────────────────────"
echo "  ✓ Déployé."
echo "    Frontend : https://${DOMAIN}"
[ "${WWW_OK}" = "1" ] && echo "               https://www.${DOMAIN}"
echo "──────────────────────────────────────────────────────────────"
${COMPOSE} ps
