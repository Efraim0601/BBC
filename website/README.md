# Site institutionnel — Bayo Bilingual Complex

Site vitrine public de l'établissement : présentation, programmes, admissions, contact.
**100 % statique** (HTML, CSS, JavaScript sans dépendance), servi par nginx.

Il est volontairement séparé de l'application de gestion scolaire (`frontend/`) :
le site doit rester en ligne, rapide et modifiable par le secrétariat même si
l'application, la base de données ou l'API sont arrêtées pour maintenance.

---

## Contenu

| Fichier | Page |
|---|---|
| `index.html` | Accueil — projet éducatif, cycles, espace parents |
| `a-propos.html` | L'école — mission, valeurs, équipe, cadre |
| `programmes.html` | Programmes — sous-systèmes, maternelle / primaire / secondaire, évaluation |
| `admissions.html` | Admissions — procédure, dossier, frais, pré-inscription, FAQ |
| `contact.html` | Contact — coordonnées, horaires, formulaire, accès |
| `connexion.html` | Connexion — adossée à l'API du système de gestion scolaire |
| `404.html` | Page d'erreur |
| `assets/css/site.css` | Feuille de style unique (jetons repris de la charte de l'app) |
| `assets/js/site.js` | Bascule FR/EN, menu mobile, formulaires |
| `assets/js/anim-init.js` | Amorçage des animations, chargé avant le premier rendu |
| `assets/js/animations.js` | Bannière animée, apparitions au défilement, bandeau d'annonces |
| `assets/js/auth.js` | Connexion et mot de passe oublié, contre l'API existante |
| `assets/js/vendor/` | GSAP 3.13 et ScrollTrigger, servis depuis le site ([détail](assets/js/vendor/README.md)) |
| `assets/img/bbc-logo.png` | Logo (copie de `frontend/public/bbc-logo.png`) |
| `tests/animations.test.mjs` | Tests d'exécution (jsdom) de la bascule de langue et des animations |
| `tests/login.test.mjs` | Tests d'exécution de la connexion (session écrite, erreurs, replis) |

---

## Aperçu local

Sans Docker, depuis ce dossier :

```bash
python3 -m http.server 8082
# → http://localhost:8082
```

Avec Docker, depuis la racine du dépôt :

```bash
docker compose -f docker-compose.website.yml up -d --build
# → http://localhost:8082
docker compose -f docker-compose.website.yml down
```

---

## Bilingue FR / EN

Il n'y a **pas de dictionnaire JavaScript** : chaque texte porte ses deux versions
directement dans le HTML, ce qui permet de corriger une phrase sans ouvrir
`site.js`.

```html
<h3 data-fr="Nos valeurs" data-en="Our values">Nos valeurs</h3>
```

Variantes disponibles :

| Attribut | Effet |
|---|---|
| `data-fr` / `data-en` | remplace le texte de l'élément |
| `data-fr-html` / `data-en-html` | remplace le contenu HTML (texte contenant un lien) |
| `data-fr-placeholder` / `data-en-placeholder` | attribut `placeholder` d'un champ |
| `data-fr-aria` / `data-en-aria` | attribut `aria-label` |
| `data-fr-content` / `data-en-content` | attribut `content` (balises `<meta>`) |
| `data-title-fr` / `data-title-en` sur `<html>` | titre de l'onglet |

Le français est écrit en dur dans le HTML : si le JavaScript ne s'exécute pas,
le site reste entièrement lisible. La langue choisie est mémorisée dans le
`localStorage` du visiteur (clé `bbc-site-lang`).

**Règle à respecter en modifiant une page :** corriger les *trois* endroits —
le texte visible, `data-fr` et `data-en`. Un texte visible différent de `data-fr`
réapparaîtrait au premier changement de langue.

---

## Connexion

`connexion.html` n'est pas une page de connexion décorative : elle appelle
**le même endpoint que l'application** (`POST /api/auth/login`) et écrit
**les mêmes clés de session** que `frontend/src/app/core/auth.service.ts` :

| Clé `localStorage` | Contenu |
|---|---|
| `bbc-access` | jeton d'accès |
| `bbc-refresh` | jeton de rafraîchissement |
| `bbc-user` | profil utilisateur sérialisé |
| `bbc-access-expires-at` | date absolue d'expiration (`Date.now() + expiresInMs`) |

L'application, au chargement, restaure la session depuis ces clés :
l'utilisateur qui se connecte ici arrive **déjà connecté** dans l'application.
L'aiguillage reprend celui de l'écran de connexion existant — un parent va sur
`/app/parent`, tout autre rôle sur `/app/parcours`.

Le formulaire « mot de passe oublié » appelle de même `POST /api/auth/forgot-password`
et affiche la réponse du serveur, volontairement identique que le compte
existe ou non.

### La contrainte qui commande toute l'architecture

`localStorage` est **cloisonné par origine**. Une connexion faite sur
`bbcomplex.com` est invisible depuis `app.bbcomplex.com` : l'utilisateur
verrait « connexion réussie », puis l'application lui redemanderait ses
identifiants, sans erreur nulle part.

**Le site et l'application doivent donc être servis depuis le même hôte et le
même port.** C'est ce que fait `nginx.conf` :

| Chemin | Sert |
|---|---|
| `/` | le site institutionnel (ce dossier) |
| `/app/` | l'application, proxifiée vers le conteneur `frontend` |
| `/api/`, `/ws/` | l'API et le temps réel, proxifiés vers le conteneur `backend` |

Deux pièges de configuration sont désamorcés dans `nginx.conf`, et commentés
sur place parce qu'ils se réintroduisent facilement :

1. les trois `location` portent `^~`. Sans lui, la règle de cache
   `location ~* \.(css|js)$` — une regex — l'emporterait sur un préfixe
   simple, et les fichiers de l'application seraient cherchés dans la racine
   du site : **application blanche** ;
2. les cibles de `proxy_pass` passent par une variable et un `resolver`. Avec
   un nom littéral, nginx refuse de démarrer tant que le conteneur `backend`
   n'existe pas — ce qui casserait l'aperçu du site seul.

L'application doit être construite avec `--base-href /app/`, sinon elle
demande ses fichiers à la racine. `frontend/Dockerfile` accepte pour cela
l'argument `BASE_HREF`, dont la valeur par défaut (`/`) ne change rien aux
déploiements existants.

### Aperçu complet, site + application + API

```bash
docker compose -f docker-compose.yml -f docker-compose.demo.yml \
  -f docker-compose.website-integrated.yml -p bbc-integre up -d --build
```

- http://localhost:8082 — le site
- http://localhost:8082/app/ — l'application
- http://localhost:8082/api/ — l'API

En mode démo, `principal` / `password` permet d'essayer la connexion de bout
en bout.

### Si l'API n'est pas joignable

Le site reste entièrement consultable ; seule la connexion échoue, avec le
message « Serveur injoignable ». C'est le comportement attendu de l'aperçu
lancé avec `docker-compose.website.yml` seul, qui ne démarre ni l'API ni la
base.

---

## Animations (GSAP)

La bannière est animée avec **GSAP 3.13 + ScrollTrigger**, servis depuis
`assets/js/vendor/` et non depuis un CDN : la CSP du site n'autorise que
`script-src 'self'`, et une connexion instable ne doit pas priver la page
d'accueil de sa bannière.

Ce qui bouge :

| Élément | Animation |
|---|---|
| Bannière d'accueil | titre découpé mot à mot, apparition en cascade du texte, de la carte et de ses puces |
| Décor de bannière | trois halos lumineux qui dérivent en continu, avec parallaxe au défilement |
| Chiffres clés | comptage progressif jusqu'à la valeur affichée |
| Bandeau supérieur | trois annonces qui défilent en boucle |
| Bandeaux des pages intérieures | titre mot à mot, deux halos |
| Sections | apparition au défilement, par groupes (`ScrollTrigger.batch`) |
| En-tête | ombre au défilement, barre de progression de lecture dorée |
| Boutons et cartes | micro-interactions au survol |

**Le contenu ne dépend jamais des animations.** Trois protections, chacune
couverte par un test :

1. `prefers-reduced-motion: reduce` → aucune animation, aucun masquage, aucun décor ;
2. GSAP absent (fichier manquant, réseau coupé) → `anim-init.js` lève le masquage
   au bout de 2 s, la page s'affiche normalement ;
3. une erreur pendant la mise en place → tout est rendu visible et les animations
   sont abandonnées.

Le décor (halos, barre de progression) est **créé en JavaScript**, pas écrit dans
les pages : le HTML reste du contenu éditorial relisible par le secrétariat.

### Contrainte à connaître avant de modifier

`site.js` réécrit le contenu des éléments à chaque changement de langue, ce qui
détruit le découpage en mots du titre. `animations.js` écoute l'évènement
`bbc:langchange` et le refait. Deux conséquences :

- l'ordre des `<script>` en bas de page compte : `site.js` **avant**
  `animations.js`, sinon le titre est découpé puis écrasé ;
- un texte animé ne doit jamais contenir d'élément qu'on veut conserver, à moins
  de le déclarer avec `data-fr-html` / `data-en-html` (le titre d'accueil et son
  `<em>` doré fonctionnent ainsi).

### Tests

```bash
cd website
docker run --rm -v "$PWD":/site:ro -w /tmp node:20-alpine sh -c \
  "npm i --silent --no-fund --no-audit jsdom && cp /site/tests/animations.test.mjs . \
   && SITE_DIR=/site node animations.test.mjs"
```

Les tests vérifient le découpage du titre, la préservation du `<em>`, les
compteurs, le bandeau d'annonces, la survie des astérisques « champ requis » au
changement de langue, et les trois chemins de repli ci-dessus.

La suite de connexion se lance de la même façon, en remplaçant
`animations.test.mjs` par `login.test.mjs`. Elle fige le contrat de session
(les quatre clés `localStorage`), l'aiguillage parent / personnel, et le
comportement en cas d'identifiants refusés, de serveur injoignable ou de
réponse incomplète.

---

## À compléter avant la mise en ligne

Le contenu rédactionnel est prêt. Les données factuelles ci-dessous sont des
**valeurs d'attente** et doivent être remplacées par l'établissement :

| Valeur d'attente | Où | À remplacer par |
|---|---|---|
| `+237 6 XX XX XX XX` et `tel:+2376XXXXXXXX` | toutes les pages | le numéro du secrétariat |
| `BP 0000 — Douala, Cameroun` | pied de page, `contact.html` | l'adresse postale et physique réelle |
| `contact@bbcomplex.com`, `admissions@bbcomplex.com` | toutes les pages | les boîtes réellement relevées |
| `— FCFA` (grille tarifaire) | `admissions.html#frais` | les montants 2026–2027 de l'économat |
| Horaires des services | `contact.html` | les horaires réels |
| Plan d'accès (encadré gris) | `contact.html` | une carte, une fois l'adresse confirmée |

Remplacement en une passe, depuis la racine du dépôt :

```bash
cd website
grep -rl "6 XX XX XX XX" . --include='*.html' | xargs sed -i 's/6 XX XX XX XX/6 12 34 56 78/g'
grep -rl "tel:+2376XXXXXXXX" . --include='*.html' | xargs sed -i 's/tel:+2376XXXXXXXX/tel:+237612345678/g'
```

Les chiffres mis en avant sur l'accueil (`3` cycles, `2` sous-systèmes, `FR · EN`,
`24/7`) sont vérifiables et n'inventent ni effectif, ni taux de réussite. Pour
afficher un effectif ou un taux d'admission aux examens, modifier le bloc
`<dl class="stats">` de `index.html` — en n'y mettant que des chiffres réels.

---

## Formulaires

Un site statique n'a pas de serveur pour recevoir un `POST`. Les deux formulaires
(pré-inscription et contact) **composent un e-mail prérempli** vers le secrétariat :
le visiteur relit puis envoie depuis son propre logiciel de messagerie.

Pour brancher un traitement réel plus tard, remplacer `setupMailForms()` dans
`assets/js/site.js` par un `fetch()` vers l'endpoint retenu. Le balisage n'a pas
à changer : chaque champ porte déjà un `name` et un libellé `data-label-fr` /
`data-label-en`.

---

## Mise en production

Le site et l'application partagent **une seule origine** — c'est ce qui rend la
page de connexion utilisable (voir la section « Connexion » ci-dessus) :

| Chemin sur le domaine public | Sert |
|---|---|
| `/` | le site institutionnel |
| `/app/` | l'application de gestion scolaire |
| `/api/`, `/ws/` | l'API et le temps réel |

Ce que cela suppose, quel que soit le serveur :

1. reconstruire l'application avec `BASE_HREF=/app/` ;
2. placer le conteneur du site **devant** l'application et l'API, et ne plus
   publier celles-ci directement ;
3. faire terminer TLS par le composant déjà en place, en le faisant pointer sur
   le conteneur du site.

`docker-compose.website-integrated.yml` fait exactement cela pour un stack bâti
sur `docker-compose.yml`, et c'est la configuration qui a été vérifiée : site,
application sous `/app/`, connexion réelle contre l'API.

> **À adapter avant tout déploiement réel.** Le stack en service sur ce serveur
> est `docker-compose.letsencrypt.yml` (projet `bbc-prod`, nginx + certbot),
> dans lequel c'est le conteneur `frontend` qui termine TLS. Y insérer le site
> demande de déplacer la terminaison TLS vers le conteneur du site, ou d'ajouter
> les trois `location` ci-dessus à `nginx-domain.generated.conf`. Cette
> variante n'a pas été écrite ici parce qu'elle n'a pas pu être vérifiée : au
> moment de la rédaction, `bbcomplex.com` résout vers `191.215.38.141`, alors
> que ce serveur répond en `72.62.211.104` — le domaine public n'est pas servi
> par cette machine.

### Revenir en arrière

Le site n'ajoute qu'un conteneur devant les autres. Relancer le stack sans
`-f docker-compose.website-integrated.yml` restaure l'application sur le
domaine, telle qu'avant.

---

## Accessibilité et performances

- Lien d'évitement, `aria-label` sur les navigations, `aria-pressed` sur la
  bascule de langue, `aria-expanded` sur le menu mobile.
- Contrastes conformes AA sur les fonds navy et or de la charte.
- `prefers-reduced-motion` respecté : ni animation, ni décor, ni masquage.
- Feuille de style d'impression (bandeaux, menus et boutons retirés).
- Une seule bibliothèque tierce (GSAP), servie depuis le site, jamais depuis un CDN.
  Les polices Manrope et Fraunces viennent de Google Fonts, comme dans
  l'application — les remplacer par des polices auto-hébergées si l'établissement
  souhaite se passer de toute requête externe.
