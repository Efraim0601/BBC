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
| `404.html` | Page d'erreur |
| `assets/css/site.css` | Feuille de style unique (jetons repris de la charte de l'app) |
| `assets/js/site.js` | Bascule FR/EN, menu mobile, formulaires |
| `assets/img/bbc-logo.png` | Logo (copie de `frontend/public/bbc-logo.png`) |

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

Le site prend le domaine principal, l'application de gestion scolaire passe sur
un sous-domaine :

| Domaine | Sert |
|---|---|
| `bbcomplex.com`, `www.bbcomplex.com` | le site institutionnel (ce dossier) |
| `app.bbcomplex.com` | l'application de gestion scolaire (`frontend/`) |

1. **DNS** — créer l'enregistrement `A` pour `app.bbcomplex.com` pointant vers le
   serveur, et attendre sa propagation. Sans cela, Let's Encrypt ne peut pas
   émettre le certificat et l'application deviendrait injoignable.
2. **Déployer** :

   ```bash
   docker compose --env-file .env.vps-production -p bbc-production \
     -f docker-compose.server.yml \
     -f docker-compose.vps-production.yml \
     -f docker-compose.website-production.yml up -d --build
   ```

   Cet override ajoute le service `website` et fait pointer Caddy sur
   `Caddyfile.production.website`, qui déclare les deux domaines.
3. **Vérifier** `https://bbcomplex.com` (site) puis `https://app.bbcomplex.com`
   (connexion à l'application).
4. **Revenir en arrière** en cas de problème : relancer sans le troisième fichier
   `-f`, ce qui restaure `Caddyfile.production` et l'application sur le domaine
   principal.

Tant que l'enregistrement DNS `app` n'existe pas, les liens « Espace parents »
du site pointent vers une adresse qui ne résout pas encore. Deux options :
créer le DNS avant la mise en ligne, ou remplacer temporairement
`https://app.bbcomplex.com` par l'URL actuelle de l'application.

---

## Accessibilité et performances

- Lien d'évitement, `aria-label` sur les navigations, `aria-pressed` sur la
  bascule de langue, `aria-expanded` sur le menu mobile.
- Contrastes conformes AA sur les fonds navy et or de la charte.
- `prefers-reduced-motion` respecté ; feuille de style d'impression.
- Aucune bibliothèque tierce : une seule feuille de style, un seul script.
  Les polices Manrope et Fraunces viennent de Google Fonts, comme dans
  l'application — les remplacer par des polices auto-hébergées si l'établissement
  souhaite se passer de toute requête externe.
