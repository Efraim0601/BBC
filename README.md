# BBC SMS — Bayo Bilingual Complex (School Management System)

Application de gestion scolaire bilingue (FR/EN), multi-tenant, **temps réel**.
**Spring Boot** (Java 21) · **Angular 21** (zoneless, Signals) · **PostgreSQL** · **Docker**.

> L'architecture détaillée est dans [ARCHITECTURE.md](ARCHITECTURE.md). Ce dépôt en est l'implémentation de référence (socle exécutable + modules cœur).
> Guide utilisateur — un tutoriel par module, pas à pas, captures d’écran à l’appui : [GUIDE_UTILISATEUR.md](GUIDE_UTILISATEUR.md) · version interactive bilingue dans l’app : menu **Aide** ou `/guide/`. Il est généré : voir [tools/guide/README.md](tools/guide/README.md).

---

## Démarrage rapide (Docker)

Deux modes, **une seule commande** chacun (via le `Makefile`) :

| Mode | Commande | Base de données |
|---|---|---|
| **Démo** | `make demo` | schéma **+ jeu de données de démonstration** |
| **Production** | `make prod` | schéma **seul**, *aucune donnée* (mode par défaut) |

```bash
make demo    # ou : docker compose -f docker-compose.yml -f docker-compose.demo.yml up -d --build
make prod    # ou : docker compose up -d --build
make reset   # vide le volume DB — à lancer pour basculer entre démo et prod
make down    # arrête les conteneurs (conserve la base)
```

- Frontend : http://localhost:8081
- API + Swagger : http://localhost:8080/swagger-ui.html
- Postgres : localhost:**5433** (`bbc` / `bbc`)

### Mode démo — comptes de démonstration (mot de passe : `password`)
- `principal` — tous modules, Finance en lecture seule, gère la matrice de permissions
- `econome` — Finance complète (CRUD + config frais), situation/débiteurs
- `parent1` — portail parent (2 enfants liés), notes, présence, frais, suggestions

### Mode production
`make prod` applique **uniquement le schéma** (migrations `db/migration`, aucune donnée de
démonstration). Au **tout premier démarrage sur une base vide**, le backend crée
automatiquement **un établissement + un compte administrateur** (rôle `principal`, accès
complet à tous les modules, y compris **Paramètres**) à partir duquel vous configurez ensuite
toute l'application.

```bash
cp .env.example .env          # puis éditez BBC_ADMIN_PASSWORD (et le nom de l'établissement)
make prod
# Connexion : BBC_ADMIN_USERNAME (défaut « admin ») / BBC_ADMIN_PASSWORD
```

L'amorçage est **ignoré** en mode démo et dès qu'un compte existe (il ne s'exécute qu'une
fois). Si `BBC_ADMIN_PASSWORD` n'est pas défini, la base reste vide (un avertissement le
rappelle dans les logs). Pensez aussi à surcharger `BBC_JWT_SECRET` et `BBC_CORS_ORIGINS`
pour un déploiement réel.

> Le jeu de démo vit dans `db/seed` et n'est chargé que par le profil Spring `demo`
> (`SPRING_PROFILES_ACTIVE=demo`, activé par `docker-compose.demo.yml`).

---

## Déploiement serveur (HTTPS auto-signé)

Pour un serveur, `docker-compose.server.yml` lance la **production derrière nginx en TLS**
(certificat **auto-signé**) avec des ports dans la tranche **20000-30000** (pour éviter les
conflits). Seul le frontend est public ; backend et base sont liés à `127.0.0.1`.

| Service | Port | Exposition |
|---|---|---|
| Frontend HTTPS | **20443** | public |
| Frontend HTTP (→ 301 HTTPS) | **20080** | public |
| Backend API | **28080** | `127.0.0.1` (le SPA passe par le proxy nginx) |
| PostgreSQL | **25432** | `127.0.0.1` (admin/psql) |

```bash
cp .env.example .env     # éditez DOMAIN, BBC_ADMIN_PASSWORD, BBC_JWT_SECRET…
./deploy.sh              # = make deploy
```

`deploy.sh` (script de **(re)déploiement**, à relancer après chaque mise à jour) :
1. génère le certificat auto-signé dans `certs-tls/` au premier lancement (CN/SAN = `DOMAIN`) ;
2. **reconstruit les images et recrée les conteneurs** (`up -d --build --force-recreate`) ;
3. purge les images orphelines et attend que l'API réponde.

```bash
make redeploy      # idem deploy.sh — pour appliquer une mise à jour
make server-down   # arrête la stack serveur (conserve les données)
make server-logs   # logs backend
```

> Le certificat étant auto-signé, le navigateur affichera un avertissement (normal). Pour le
> régénérer : supprimez `certs-tls/` puis relancez `./deploy.sh`. La stack serveur tourne dans
> le projet Docker isolé `bbc-server` (volume dédié).

---

## Déploiement domaine (HTTPS Let's Encrypt)

Pour un **nom de domaine possédé** (ex: `bbcomplex.com`), `docker-compose.letsencrypt.yml`
lance la production derrière nginx avec un **certificat Let's Encrypt fiable** (aucun
avertissement navigateur), sur les ports standard **80/443**. Backend et base restent liés à
`127.0.0.1`.

| Service | Port | Exposition |
|---|---|---|
| Frontend HTTPS | **443** | public |
| Frontend HTTP (→ 301 HTTPS, sauf défi ACME) | **80** | public |
| Backend API | **28081** | `127.0.0.1` |
| PostgreSQL | **25433** | `127.0.0.1` |

**Préalable** : l'enregistrement DNS **A** de `DOMAIN` (et `www.DOMAIN` si utilisé) doit déjà
pointer vers l'IP publique de ce serveur — sinon la validation ACME HTTP-01 échoue.

```bash
cp .env.example .env     # éditez DOMAIN=bbcomplex.com, LETSENCRYPT_EMAIL, BBC_ADMIN_PASSWORD…
./deploy-domain.sh       # = make deploy-domain
```

`deploy-domain.sh` (sûr à relancer après chaque mise à jour) :
1. vérifie que `DOMAIN` résout bien vers l'IP publique de ce serveur ;
2. génère la conf nginx à partir du template (`frontend/nginx.letsencrypt.conf`) ;
3. au premier lancement : certificat temporaire → démarrage → demande du vrai certificat
   Let's Encrypt (webroot) → rechargement nginx ;
4. relances suivantes : rebuild + recrée les conteneurs (le certificat existant est réutilisé) ;
5. installe un cron hebdomadaire qui recharge nginx (le conteneur `certbot` renouvelle le
   certificat en tâche de fond, nginx doit relire le fichier).

```bash
make deploy-domain  # applique une mise à jour (idem ./deploy-domain.sh)
make domain-down    # arrête la stack domaine (conserve les données)
make domain-logs    # logs backend
```

> La stack domaine tourne dans le projet Docker isolé `bbc-prod` (volume dédié, distinct de
> `bbc-server`). Les deux stacks peuvent coexister mais **pas sur les mêmes ports** — le domaine
> utilise 80/443/28081/25433, le mode IP auto-signé utilise 20080/20443/28080/25432.

---

## Développement local

### Backend
```bash
cd backend
# Postgres requis (ou: docker compose up db)
mvn spring-boot:run                              # production : schéma seul
SPRING_PROFILES_ACTIVE=demo mvn spring-boot:run  # démo : schéma + données
```
Flyway applique le schéma (`db/migration`) au premier lancement ; le profil `demo` ajoute
en plus le jeu de données de `db/seed`.

### Frontend
```bash
cd frontend
npm install
npm start          # http://localhost:4200  (proxy /api et /ws -> :8080)
```

---

## Architecture du code

```
BBC/
├── ARCHITECTURE.md          # cahier d'architecture
├── docker-compose.yml
├── backend/                 # Spring Boot — monolithe modulaire
│   └── src/main/java/com/bbc/sms/
│       ├── platform/        # transverse : security (JWT), tenant, realtime (WebSocket), common
│       ├── identity/        # auth, utilisateurs, matrice de permissions
│       ├── student/         # élèves (CRUD)
│       ├── staff/           # personnel / RH
│       ├── attendance/      # présence temps réel + endpoint lecteur d'empreintes
│       ├── finance/         # frais, paiements, dépenses, synthèse revenus
│       └── academic/        # notes
│   └── src/main/resources/db/migration/   # V1 schéma, V2 seed (Flyway)
├── frontend/                # Angular 21
│   └── src/app/
│       ├── core/            # auth, interceptor, guards, realtime (STOMP), i18n, models
│       ├── layout/          # shell (app-bar) + apps-home
│       └── features/        # login, dashboard, students, attendance, finance
└── tools/simulate-device.sh # simulateur de lecteur d'empreintes
```

---

## Sécurité & multi-tenant

- **JWT** access (15 min) + refresh (7 j). Le token porte `sid` (school id) : chaque requête est **scellée au tenant** via `TenantContext`.
- **RBAC** par matrice `rôle × module → none|read|write`, appliquée côté serveur :
  `@PreAuthorize("@perm.can('finance','write')")`. Le front masque l'UI mais **ne décide jamais** — le backend tranche.
- Mot de passe haché **BCrypt**.

---

## Temps réel (la « réactivité »)

1. Le frontend ouvre une connexion **STOMP/SockJS** sur `/ws` (authentifiée par le JWT).
2. Il s'abonne à `/topic/school/{schoolId}/attendance`.
3. Un pointage (lecteur d'empreintes ou saisie manuelle) → le backend enregistre puis
   `RealtimeService.broadcast(...)` → le tableau de présence se met à jour **instantanément** (Signal).

### Démo du flux temps réel
1. Ouvrir l'app, se connecter, créer quelques élèves (module Élèves) — noter leurs matricules.
2. Ouvrir la page **Présence** (laisser l'onglet ouvert).
3. Récupérer l'id du lecteur : `SELECT id FROM device;`
4. Lancer le simulateur (Git Bash) :
   ```bash
   ./tools/simulate-device.sh http://localhost:8080 <DEVICE_ID> dev-key-bbc-portal-a
   ```
   Les lignes apparaissent en direct, sans rafraîchir la page.

---

## État d'implémentation

| Domaine | État |
|---|---|
| M1 Tableau de bord | ✅ KPIs adaptatifs (effectif, présence, finance) |
| M2 Élèves | ✅ CRUD (parcours/liaison familiale à enrichir) |
| M3 Présence biométrique | ✅ temps réel + endpoint device idempotent (rapport mensuel via M10) |
| M4 Académique | ✅ notes + **bulletins** (moyennes/rang/PV/validation/blocage si dette) |
| M5 Finance | ✅ paiements, dépenses, synthèse, **config frais, situation, débiteurs** |
| M6 Personnel / RH | ✅ CRUD back + front |
| M7 Emploi du temps | ✅ grille + édition + **détection de conflits** |
| M8 Événements | ✅ CRUD + notification parents (simulée) + push |
| M9 Discipline | ✅ incidents + sanctions graduées |
| M10 Rapports | ✅ bilan financier, présence mensuelle, démographie |
| M11 Paramètres | ✅ **éditeur de matrice de permissions** (application immédiate) |
| Portail parent | ✅ enfants, notes, présence, frais, **boîte à suggestions** |
| Auth JWT + refresh + RBAC matrice + multi-tenant | ✅ socle |
| Présence : temps réel (WebSocket/STOMP) | ✅ |
| **Reste à faire** : génération **PDF** (bulletins/reçus), **SMS/WhatsApp** réels (point `notify` en place), import CSV, enrôlement biométrique AES-256, parcours scolaire/timeline, graphiques (Recharts) | ⏳ |

Les modules restants suivent le **patron** déjà en place (entity → repository → service `@Transactional` + `TenantContext` → controller `@PreAuthorize`, et côté front : `*.api.ts` + composant standalone Signals).

---

## Tests

> ⚠️ Aucune classe de test n'est encore écrite — seules les dépendances (JUnit 5,
> Spring Security Test, **Testcontainers**) sont configurées dans le `pom.xml`.
> `mvn test` exécute donc actuellement 0 test.

Validation du build (compile back + front sans toucher au poste, via Docker) :

```bash
docker compose build         # mvn package (backend) + ng build (frontend)
```

Prochaine étape qualité : ajouter des tests d'intégration Testcontainers
(contexte Spring, login JWT, RBAC `@perm.can`, isolation multi-tenant).
