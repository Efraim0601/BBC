# Site institutionnel de Bayo Bilingual Complex.
# Pas d'etape de build : le site est du HTML/CSS/JS statique, on copie tel quel.
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY . /usr/share/nginx/html

# Les fichiers de configuration du conteneur n'ont rien a faire dans la racine servie.
RUN rm -f /usr/share/nginx/html/Dockerfile \
          /usr/share/nginx/html/.dockerignore \
          /usr/share/nginx/html/nginx.conf \
    && find /usr/share/nginx/html -name '*.md' -delete \
    && rm -rf /usr/share/nginx/html/tests

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -q -O /dev/null http://localhost/ || exit 1
