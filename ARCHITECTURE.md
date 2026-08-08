# BBC SMS — Architecture cible

**Bayo Bilingual Complex – School Management System**
Backend **Spring Boot** · Frontend **Angular** · Base **PostgreSQL** · Déploiement **Cloud (SaaS)** · UX **temps réel**

---

## 1. Principes directeurs

| Principe | Décision |
|---|---|
| Réactivité | « Temps réel » = mises à jour **push** à l'écran (présence live, notifs parents, dashboards). On garde **Spring MVC bloquant** (simple, transactions faciles) et on pousse les events via **WebSocket/SSE**. Pas de WebFlux/R2DBC (surdimensionné pour une école). |
| Découpage | **Monolithe modulaire** (un seul déployable, modules isolés par package). On ne part PAS en microservices : une école = charge modérée, transactions financières fortes. |
| Multi-tenant | SaaS prêt pour **plusieurs écoles** : isolation par `school_id` (discriminator) sur chaque table dès le départ, même si une seule école au lancement. |
| Sécurité | RBAC piloté par la **matrice de permissions** existante (`rôle × module → none/read/write`) + JWT. |
| Bilingue | FR/EN géré côté front (i18n) ; données libellées stockées en `{fr, en}` (JSONB) là où c'est du contenu. |

---

## 2. Stack technique

### Backend
- **Java 21** (LTS) + **Spring Boot 3.3+**
- **Spring Web MVC** (REST, stack servlet bloquante)
- **Spring Data JPA / Hibernate** + **Postgres**
- **Spring Security** (JWT access + refresh, méthode `@PreAuthorize`)
- **Spring WebSocket (STOMP)** pour le push temps réel + **SSE** pour les flux simples
- **Flyway** (migrations versionnées de la base)
- **MapStruct** (mapping entité ↔ DTO), **Lombok**
- **springdoc-openapi** (Swagger UI auto)
- **Testcontainers** (tests d'intégration sur un vrai Postgres)
- PDF : **OpenPDF / JasperReports** (bulletins, reçus de paiement)

### Frontend
- **Angular 21 (LTS)** — l'avant-dernière version à date (juin 2026 ; la dernière est Angular 22, sortie le 3 juin 2026). Angular 21 est en **support long terme jusqu'en mai 2027** → meilleur compromis stabilité/écosystème pour démarrer un projet.
- **Standalone components** (défaut), **Signals stables**, architecture **zoneless** (sans `zone.js`) + `ChangeDetectionStrategy.OnPush`
- **Angular Material** ou **PrimeNG** (tables, dialogs — proches du design Odoo-like actuel)
- **Tailwind CSS** (le prototype est déjà en Tailwind → réutilisable tel quel)
- **@ngx-translate** pour FR/EN
- **RxJS** uniquement pour les flux temps réel (WebSocket/SSE) ; le reste en Signals (`httpResource`/`rxResource` pour les chargements)
- **STOMP over SockJS** client pour le WebSocket

> Note de version : on fige **Angular 21.x**. La montée vers Angular 22 (qui stabilise Signal Forms) se fera plus tard via `ng update`, une fois le projet posé.

### Infra / Cloud
- **Docker** (image backend + image Nginx servant le front buildé)
- **Postgres managé** (RDS / Cloud SQL / Supabase / Neon)
- **Reverse proxy** Nginx/Traefik (TLS, WebSocket upgrade)
- **CI/CD** GitHub Actions/GitLab CI (build, tests Testcontainers, push image, deploy)
- Stockage fichiers (photos élèves, PDF) : **S3-compatible** (S3 / Cloud Storage / MinIO)

---

## 3. Architecture backend — monolithe modulaire

Un seul `.jar`, découpé par **modules métier** (un package racine par module). Chaque module = sa couche `api` (controllers/DTO), `domain` (entités + services), `repo`. Les modules ne s'appellent qu'à travers des **interfaces de service**, jamais les repos d'un autre module.

```
com.bbc.sms
├── platform/            # transverse : sécurité, tenant, websocket, exceptions, i18n
│   ├── security/        # JWT, filtres, RBAC (matrice de permissions)
│   ├── tenant/          # résolution school_id (multi-tenant)
│   ├── realtime/        # hub WebSocket/STOMP + SSE + Postgres LISTEN/NOTIFY
│   └── audit/           # journal d'audit (qui a modifié quoi)
│
├── identity/            # utilisateurs, rôles, groupes, matrice de permissions
├── student/             # élèves, inscriptions, parcours scolaire, sections/classes
├── staff/               # personnel (EMPLOYEES) : enseignants N:N matières/classes
├── attendance/          # présence temps réel (lecteurs d'empreintes), retards
├── academic/            # notes (séquences), bulletins, appréciations, validation
├── finance/             # frais, tranches, paiements, dépenses, reçus
├── timetable/           # emploi du temps par classe (grille 6×9)
├── events/              # événements + notifications parents
├── discipline/          # incidents, sanctions
├── parentportal/        # portail parent (lecture notes/présence/finance, suggestions)
└── reporting/           # rapports, exports PDF/Excel
```

**Couches dans chaque module**
```
Controller (REST/DTO, validation)  →  Service (logique + transactions)  →  Repository (JPA)  →  Entity
```
- Les **DTO** ne sont jamais les entités (sécurité + stabilité d'API).
- Transactions au niveau **Service** (`@Transactional`).
- Toute écriture importante publie un **event applicatif** (Spring `ApplicationEventPublisher`) → le module `realtime` le relaie aux clients connectés.

> Migration future possible : si un module devient lourd (ex. `reporting`), il s'extrait en service séparé sans réécrire le reste. Le monolithe modulaire garde cette porte ouverte.

---

## 4. Modèle de données PostgreSQL (extraits clés)

Schéma relationnel classique, **chaque table porte `school_id`** (multi-tenant). Migrations via Flyway.

**Tables principales** (dérivées du prototype `data.jsx`) :

- `school` (tenant), `academic_year`
- `app_user` (login) ↔ `employee` (1:1) · `role`, `permission_grant (role_id, module, level)`
- `section` (pri-fr, pri-en, sec-fr, sec-en) → `school_class` (SIL…Terminale, Form 1…Upper Sixth),
  qui porte la progression : `grade_order`, `next_class_id`, `terminal`
- `subject` (catalogue, libellés `jsonb {fr,en}`), `teacher_subject` (N:N), `teacher_class` (N:N)
- `student` (classe courante) + `journey_entry` (parcours année par année, redoublements)
- **Fin d'année** : `promotion_rule` (seuil, zone conseil, redoublements max) ·
  `promotion_batch` + `promotion_decision` (proposition automatique, décision retenue, motif de l'arbitrage) ·
  `year_closure` et les archives datées `grade_archive`, `bulletin_validation_archive`, `student_fee_archive`
- `parent` ↔ `parent_student` (N:N, un parent ↔ plusieurs enfants)
- `fee_config (level, subsystem, total, tranches jsonb)` · `invoice` · `payment (method, tranche, amount, receipt_no)`
- `expense`
- `attendance_record (student_id, date, status[present|late|absent], check_in_time, late_minutes, source[fingerprint|manual])`
- `grade (student_id, subject_id, sequence, mark)` · `report_card` + `appreciation` + `validation`
- `timetable_slot (class_id, day, slot, subject_id, teacher_id, room)`
- `event (title, type, date, audience, target_classes)` · `notification (recipient, channel, status)`
- `discipline_incident (student_id, type, description, sanction)`
- `audit_log`

**Conseils Postgres**
- `jsonb` pour les libellés bilingues et les structures souples (tranches, target_classes).
- Index sur `(school_id, …)` partout + index sur les colonnes de filtre fréquentes (`attendance(school_id, date)`, `payment(school_id, date)`).
- Contraintes d'intégrité réelles (FK, `CHECK` sur les statuts) — c'est une app financière.
- Montants en **entiers FCFA** (pas de float).

---

## 5. Temps réel — le cœur de la « réactivité »

Trois mécanismes complémentaires :

| Besoin | Techno | Exemple |
|---|---|---|
| Push bidirectionnel ciblé | **WebSocket + STOMP** | Présence qui s'affiche élève par élève au passage du badge ; tableau de bord live |
| Flux unidirectionnel simple | **SSE** | Compteur de paiements du jour, notifications |
| Réactivité DB → app | **Postgres `LISTEN/NOTIFY`** | Un trigger sur `attendance_record` notifie le backend, qui rediffuse via WebSocket |

**Flux présence temps réel (cas emblématique)**
```
Lecteur empreinte (sur site)
   → Agent local (petit service) → POST /api/attendance/checkin (HTTPS, token device)
      → Service attendance enregistre + publie AttendanceRecordedEvent
         → realtime hub envoie sur le topic /topic/school/{id}/attendance
            → Angular (abonné) met à jour la Signal → l'UI se rafraîchit instantanément
```

**Topics STOMP (par tenant, pour l'isolation)**
- `/topic/school/{schoolId}/attendance`
- `/topic/school/{schoolId}/payments`
- `/user/queue/notifications` (notifs parent ciblées)

Sécurité WebSocket : handshake authentifié par JWT, abonnement filtré par `school_id` + permissions.

---

## 6. Sécurité & permissions (RBAC)

La matrice `permission_grant` (`rôle × module → none|read|write`) est **éditable** depuis Paramètres.
Les **rôles personnalisés** (`role.builtin = false`) se créent via `POST /api/settings/roles`.

**Rôle Parent (ACL ligne à ligne)** :
- La matrice refuse d’accorder aux parents les modules staff (académique, élèves, finance…).
- Les contrôleurs staff exigent `@perm.staffOnly()` en plus de `@perm.can(...)`.
- Le portail `/api/parent/**` filtre strictement via `parent_student` (`assertOwnership`).

**Administrateurs de section** (V43) :

L’admin principal (`principal`) délègue chaque cycle à un relais : `admin_maternelle`,
`admin_primary`, `admin_secondary`. Trois rôles plutôt qu’un rôle + une colonne, parce que
le code de rôle voyage déjà dans le JWT : la section se déduit **sans lecture en base** à
chaque requête (`platform.security.SectionRoles`).

La matrice ne sait pas exprimer un cloisonnement par cycle — elle raisonne par module. Le
verrou tient donc à trois pièces qui se complètent :

| Pièce | Rôle |
|---|---|
| `ParcoursContext.sectionLock()` | Section imposée à la requête, posée par `JwtAuthFilter`. Contrairement au parcours, elle ne vient pas du client : un en-tête `X-Parcours` absent n’affranchit de rien. |
| `AccessScopeService` | Généralise l’ancien `TeacherScopeService` : « les classes que ce compte peut voir » — celles assignées pour un enseignant, celles du cycle pour un admin de section. Tous les modules déjà cloisonnés pour l’enseignant le sont donc aussi pour lui, sans modification. |
| `@perm.schoolWide()` | Ferme les réglages école-entière : matrice, rôles, profil, SMTP, calendrier, catalogues, dépenses, clôture d’année. |

Conséquences volontaires, à connaître avant d’y toucher :
- **Élèves sans classe** : visibles de l’admin de leur section — ce sont ses inscriptions du
  jour, et les masquer les rendrait impossibles à placer.
- **Personnel sans section** (économat, intendance) : visible de tous les admins de section ;
  il ne relève d’aucun cycle.
- **Dépenses** : hors périmètre d’un admin de cycle. `FinanceSummary.section` le signale à
  l’écran, faute de quoi un solde amputé passerait pour celui de l’établissement.
- **Nomination** : `Paramètres → Administrateurs` (`/api/settings/admins`), réservé à l’admin
  principal. Le module Personnel refuse d’*ajouter* un rôle privilégié — sans quoi il
  offrirait le chemin de traverse que cet écran ferme.

**Catalogues & calendrier** (V30) :
- `discipline_catalog` — motifs / sanctions éditables par établissement.
- `school.school_start_time` / `school_end_time` — seuil de retard.
- `school_holiday` — jours sans retard au pointage.
- Fiche élève : `father_*` / `mother_*` / `guardian_*` (+ contact legacy `parent_name`/`parent_phone`).


- **Authentification** : JWT *access* (court, ~15 min) + *refresh* (cookie httpOnly). Login = `employee` ou `parent`.
- **Autorisation** :
  - Filtrage des **modules visibles** côté front (déjà fait par `pagesForRole`).
  - Garde réelle côté backend : `@PreAuthorize("@perm.can('finance','write')")` sur chaque endpoint — **ne jamais** se fier au front.
  - Règles métier fines : *Principal → Finance en lecture seule*, *Économe → Finance write*, *Parent → uniquement ses enfants*.
- **Multi-tenant** : un `TenantFilter` injecte `school_id` depuis le token ; un filtre Hibernate l'applique automatiquement à toutes les requêtes (defense in depth).
- **Audit** : toute écriture sensible (paiement, note, permission) → `audit_log`.

---

## 7. Architecture frontend Angular

```
src/app
├── core/            # auth, intercepteurs HTTP (JWT), guards, service WebSocket/SSE, i18n
├── shared/          # composants UI réutilisables (boutons, tables, modals, badges)
├── layout/          # app-bar, apps-home, role switcher (cf. app.jsx)
└── features/
    ├── dashboard/  presence/  students/  staff/  academic/
    ├── finance/    timetable/ events/    discipline/
    ├── reports/    settings/  parent-portal/
```

- **Angular 21 (LTS)**, **standalone components** + **lazy loading** par feature (`loadComponent`).
- **Zoneless** (sans `zone.js`) → la détection de changement suit les Signals : plus rapide et plus prévisible.
- **État avec Signals** : un service par domaine expose des `signal()`/`computed()` ; le WebSocket pousse → on `set()` la signal → l'UI réagit. Pas besoin de NgRx pour cette taille (option si ça grossit).
- **`OnPush`** partout (perf).
- **Guards** : `authGuard` + `permissionGuard(module, level)` mappés sur la même matrice que le backend.
- **i18n** : `@ngx-translate`, bascule FR/EN instantanée (le bouton existe déjà).
- **Réutilisation directe du prototype** : le HTML/Tailwind de `BBC_SMS_standalone.html` se transpose composant par composant (le design est déjà fait).

---

## 8. Intégration lecteurs d'empreintes (SaaS)

Le matériel est **sur site**, l'app est **dans le cloud** → on intercale un **agent local** :

```
[Lecteur USB/réseau] → [Agent local léger sur un PC de l'école]
   • lit l'empreinte, résout l'élève (cache local)
   • POST sécurisé (token device) vers l'API cloud
   • file d'attente locale si internet coupé → renvoi à la reconnexion
```
- Endpoint dédié `POST /api/devices/{id}/attendance` avec **clé d'API device** (séparée des comptes humains).
- **Idempotence** (clé `device + timestamp`) pour éviter les doublons après reconnexion.
- Tolérance aux coupures : l'agent bufferise (la connexion internet d'une école camerounaise peut être instable).

---

## 9. Reporting & documents

- **Bulletins scolaires** (FR/EN, par séquence/trimestre) et **reçus de paiement** : génération **PDF serveur** (JasperReports ou OpenPDF) → stockés sur S3, lien signé temporaire.
- **Exports Excel** (listes élèves, états financiers) via Apache POI.
- Rapports agrégés (revenus 30 j, taux de présence) : requêtes SQL/vues matérialisées + cache.

---

## 10. Qualité, observabilité, exploitation

- **Tests** : unitaires (services), intégration **Testcontainers** (Postgres réel), e2e front (Playwright/Cypress).
- **Observabilité** : Spring Boot Actuator + Micrometer → Prometheus/Grafana ; logs structurés (JSON).
- **Sauvegardes** : backups Postgres automatiques (managé) + test de restauration.
- **Config** : `application.yml` par profil (`dev`, `prod`) + secrets via variables d'env / vault.
- **Migrations** : Flyway lancé au démarrage (jamais `hibernate ddl-auto=update` en prod).

---

## 11. Vue de déploiement (Cloud)

```
                    Internet
                        │  (TLS)
                 ┌──────┴───────┐
                 │ Nginx/Traefik│  (TLS, WS upgrade)
                 └──┬────────┬──┘
        /api, /ws   │        │  /  (fichiers statiques)
            ┌───────┴──┐   ┌─┴────────────┐
            │ Spring   │   │ Front Angular│
            │ Boot JAR │   │ (build Nginx)│
            └────┬─────┘   └──────────────┘
        ┌────────┼─────────┐
   ┌────┴───┐ ┌──┴───┐ ┌───┴────┐
   │Postgres│ │ S3   │ │ (futur)│
   │ managé │ │ files│ │ Redis  │  ← cache/sessions si besoin
   └────────┘ └──────┘ └────────┘

   École ── Agent local (présence) ──HTTPS──► /api/devices/...
```

---

## 12. Feuille de route (phasage)

1. **Socle** : Spring Boot + Postgres + Flyway + JWT + multi-tenant + module `identity` (rôles & matrice de permissions) + shell Angular (login, app-bar, apps-home, i18n).
2. **Référentiels** : `student`, `staff`, `timetable` (CRUD + design repris du prototype).
3. **Finance** : frais, paiements, reçus PDF, dépenses, dashboard revenus.
4. **Présence temps réel** : WebSocket + agent local + endpoint device + UI live.
5. **Académique** : notes, bulletins, validation, génération PDF.
6. **Événements + Portail parent** : notifications, suggestions, accès parent restreint à ses enfants.
7. **Discipline, Rapports, Settings avancés**, durcissement sécurité, observabilité.

---

### En une phrase
**Monolithe modulaire Spring Boot MVC + JPA/Postgres**, rendu « réactif » par **WebSocket/STOMP + SSE (+ Postgres LISTEN/NOTIFY)**, exposé à un **front Angular à base de Signals**, le tout **multi-tenant** et conteneurisé pour le **Cloud** — l'approche la plus simple qui livre une vraie UX temps réel sans la complexité de WebFlux/R2DBC.
