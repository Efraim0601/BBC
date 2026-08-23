# ---- build stage ----
FROM node:20-alpine AS build
WORKDIR /app

# Corporate TLS proxies (MITM) make npm fail with UNABLE_TO_VERIFY_LEAF_SIGNATURE.
# Drop your company's root CA(s) as .crt (PEM) files into frontend/certs/ and they
# are trusted here via NODE_EXTRA_CA_CERTS — WITHOUT disabling TLS verification.
# If certs/ only contains .gitkeep, this is a no-op (works on open networks).
COPY certs/ /usr/local/share/ca-certificates/
RUN cat /usr/local/share/ca-certificates/*.crt > /tmp/ca.pem 2>/dev/null || true

COPY package*.json ./
RUN if [ -s /tmp/ca.pem ]; then export NODE_EXTRA_CA_CERTS=/tmp/ca.pem; echo "→ using corporate CA"; fi; \
    npm install

COPY . .

# Base href de l'application. Par defaut « / » : rien ne change pour les
# deploiements existants. Le site institutionnel sert l'application sous /app/
# (meme origine, pour que la session ouverte sur la page de connexion du site
# soit celle de l'application) et passe donc BASE_HREF=/app/.
ARG BASE_HREF=/
RUN if [ -s /tmp/ca.pem ]; then export NODE_EXTRA_CA_CERTS=/tmp/ca.pem; fi; \
    npm run build -- --base-href "${BASE_HREF}"

# ---- serve stage ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/bbc-sms/browser /usr/share/nginx/html
EXPOSE 80
