# Chaîne de production du guide utilisateur

Le guide n'est pas écrit à la main : il est **généré** à partir du contenu bilingue de
ce dossier et de captures d'écran prises automatiquement sur la pile de démonstration.

Sorties produites :

| Fichier | Rôle |
|---|---|
| `frontend/public/guide/index.html` | guide interactif bilingue, servi par l'app (menu **Aide**, URL `/guide/`) |
| `frontend/public/guide/atelier.html` | support d'atelier projetable (URL `/guide/atelier.html`) |
| `frontend/public/guide/img/*.webp` | 154 captures — `fr-*` et `en-*` |
| `GUIDE_UTILISATEUR.md` | même contenu, version française, lisible dans le dépôt |

## Fichiers

| Fichier | Contenu |
|---|---|
| `content.py` | métadonnées + assemblage des chapitres en parties |
| `chapters_start.py` | ch. 1–2 : prise en main, rôles et permissions |
| `chapters_setup.py` | ch. 3–5 : paramètres, élèves, personnel |
| `chapters_teaching.py` | ch. 6–10 : présence, académique, discipline, cahier de textes, emploi du temps |
| `chapters_ops.py` | ch. 11–17 : finance, événements, correspondance, fournitures, parcours, santé, documents |
| `chapters_steering.py` | ch. 18–22 : pilotage, portail parent, rentrée, FAQ, annexes |
| `build.py` | générateur (HTML + Markdown) |
| `style.css`, `guide.js` | mise en forme et comportements du guide web |
| `seed-demo.py` | jeu de données de documentation injecté dans la pile démo |
| `capture.js` | campagne de captures (Puppeteer) |
| `atelier.py` / `build-atelier.py` | déroulé et génération du support d'atelier projetable |
| `atelier.css`, `atelier.js` | mise en forme et navigation des diapositives |
| `smoke.py` | 48 contrôles d'API, module par module, sur la pile de démonstration |
| `smoke-ui.js` | ouvre chaque écran des trois rôles et relève erreurs JS et appels en échec |
| `preview.js` / `preview-http.js` | rendu du guide en images (fichier local / servi par l'app) |
| `preview-atelier.js` | rendu de diapositives choisies, pour relecture |
| `check-anchors.js` | vérifie que les 44 liens du sommaire (FR + EN) tombent juste |
| `check-mobile.js` | détecte tout débordement horizontal en 420 / 768 / 1024 px |

## Modifier le texte

1. Éditez le chapitre concerné dans `chapters_*.py`. Chaque texte porte une clé `fr` et
   une clé `en`.
2. `python3 tools/guide/build.py`
3. Relisez `frontend/public/guide/index.html` (ouverture directe dans un navigateur).

Balisage disponible dans les textes : `**gras**`, `` `code` ``, et `__libellé__` pour un
libellé exact de l'interface (rendu en pastille dans le guide web, en gras en Markdown).

Types de blocs : `p`, `h`, `list`, `note` (`info` | `tip` | `warn` | `limit`), `steps`
(procédure numérotée, une capture par étape possible), `figure`, `table`, `check`
(fiche de test de fin de chapitre).

`build.py` signale en fin d'exécution toute capture référencée mais absente du dossier
`img/`.

## Refaire les captures d'écran

Les captures sont prises sur la **pile de démonstration**, jamais en production.

```bash
# 1. Pile démo propre + jeu de données de documentation
make reset && make demo                  # attendre le démarrage du backend
python3 tools/guide/seed-demo.py         # élèves, notes, paiements, incidents…

# 2. Campagne de captures (une par langue)
for L in fr en; do
  docker run --rm --network host \
    -e LANG_UI=$L -e NODE_PATH=/home/pptruser/node_modules \
    -v "$PWD/tools/guide:/work:ro" \
    -v "$PWD/frontend/public/guide/img:/out" \
    -w /home/pptruser --entrypoint node \
    ghcr.io/puppeteer/puppeteer:latest /work/capture.js
done

# 3. Régénérer le guide
python3 tools/guide/build.py
```

Variables utiles pour `capture.js` :

| Variable | Effet |
|---|---|
| `BASE` | origine de l'application (défaut `http://localhost:8081`) |
| `LANG_UI` | `fr` ou `en` |
| `SECTIONS` | limite la campagne, ex. `SECTIONS=finance,parent` |
| `ONLY` | ne garde que les vues dont le nom commence par ce préfixe |
| `PORTAL_URL` | adresse du portail public d'inscription du personnel (capture 170) |

Le dossier de sortie doit être accessible en écriture par le conteneur
(`chmod 777 frontend/public/guide/img` avant, puis `chown -R root:root` après).

## Publication

`frontend/public/` est copié dans l'image Nginx au moment du `docker build` : le guide
mis à jour n'est visible dans l'application **qu'après reconstruction du frontend**
(`make demo`, `./deploy.sh` ou `./deploy-domain.sh` selon l'environnement).

## Avant un atelier ou une mise en production

Deux contrôles automatisés, à lancer sur la pile de démonstration une fois
`seed-demo.py` passé :

```bash
# 1. API : 48 vérifications, module par module (sortie non nulle si échec)
python3 tools/guide/smoke.py http://localhost:8080

# 2. Écrans : chaque module des trois rôles, erreurs JS et appels en échec
docker run --rm --network host -e BASE=http://localhost:8081 \
  -e NODE_PATH=/home/pptruser/node_modules -v "$PWD/tools/guide:/work:ro" \
  -w /home/pptruser --entrypoint node \
  ghcr.io/puppeteer/puppeteer:latest /work/smoke-ui.js
```

Le second contrôle repère notamment les écrans blancs : une erreur d'exécution
Angular ne remonte pas côté serveur, mais laisse la zone de contenu vide.

## Régénérer le support d'atelier

```bash
python3 tools/guide/build-atelier.py
```

Le déroulé (minutage, démonstrations, exercices, pièges) vit dans `atelier.py`.
Les captures sont celles du guide : ajouter une vue au support suppose qu'elle
existe déjà dans `img/` — sinon `build-atelier.py` la signale en fin d'exécution.
