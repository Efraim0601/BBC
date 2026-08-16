# BBC SMS migration and release readiness

## Candidate artifact

- Source: `feature/full-school-lifecycle-e2e-2026-08` in the candidate worktree.
- Jar: `backend/target/bbc-sms-0.1.0.jar`, SHA-256 `973A62BAEFA6B4641FBFE39C5273992C64DEB867DFC3A5F9145813E71F85C507`.
- Jar resources include V140, V141, V142, V143, and V144.
- Docker image: `bbc-full-e2e-backend`, manifest `sha256:429efd2a78765be7574d09b6bd874dc6bc169a9658ebe07a5f37e6386b3055b8`.
- Live health: HTTP 200 on `8101`; UI served from `8100`.

## Fresh-install evidence

- Fresh Testcontainers PostgreSQL 16.14 runs validated 111 migrations and applied through v144.
- `ProductionBootstrapIntegrationTest`: 8/8.
- Full backend suite: 177/177, zero failures/errors/skips; `BUILD SUCCESS`, finished `2026-08-15T17:36:07+01:00` (supersedes the earlier 159/159 interim run).
- No manual DDL/DML was used to apply V144. Flyway applied it during backend startup.

## Upgrade evidence and blocker

The available `output/local_backup_2026-08-10.dump` was examined only in a separately named PostgreSQL 16.14 container; it was never restored into `bbc-full-e2e`. The restore completed with no archive errors and contained only metadata/counts were read back: one school, five users, 25 enrollments, 23 classes, four timetable versions, and two promotion registers. Its Flyway history ends at rank/version 80.

The source archive is the primary-worktree file `C:\Users\joe tech\bbcomplex\output\local_backup_2026-08-10.dump`, SHA-256 `F7E443E88373EB494C4262C9CF17E07B2DEF783757E28C1BB47FF71B736643B`. A read-only `pg_restore -l` inspection identified PostgreSQL 16.14 custom-format metadata and 917 TOC entries. No candidate-compatible archive or approved bridge/baseline strategy was found in either worktree.

The exact forward-only startup result against that disposable restore was a clean application exit before migration:

```text
Database: jdbc:postgresql://172.17.0.4:5432/bbc_upgrade_audit (PostgreSQL 16.14)
Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version 77
-> Applied to database : -2113849035
-> Resolved locally    : -95452447
```

This is a migration-lineage fork, not a V140/V144 packaging problem: the restored history describes V77 as the demo seed `seed secondary report card fidelity`, while the candidate’s production location contains `V77__unify_sequence_evaluations_and_backfill_secondary.sql`; the candidate’s V78–V80 demo seeds are in `db/seed`, not the production location. No Flyway repair, checksum update, manual DDL, or manual DML was used. The separate legacy `bbc_prod` archive is also divergent and is not a candidate-compatible sanitized upgrade fixture.

Therefore the production-like upgrade requirement remains **BLOCKED / NOT PASS** pending an authorized sanitized backup whose lineage is compatible with this candidate, or an explicitly approved bridge/baseline design. Required steps once supplied: restore to a disposable database, record pre-upgrade counts and Flyway version, deploy the exact candidate, let Flyway upgrade without manual fixes, compare relationships/encoding/accounting/audit counts, restart for a no-op Flyway check, and rehearse restore/rollback.

## Integrity and encoding

- Live v144 history: rank 111, version 144, success true; v143 and v142 immediately precede it.
- V143/V144 principal authority counts match the intended narrow changes.
- Posted journal balance probe: zero unbalanced entries.
- UTF-8 fixture verifier passed for French labels and accented student/subject data; report-card/PDF/export evidence is in the academic and academic-setup evidence files.
- The earlier `installed_rank` mismatch was a read-only harness ordering bug (`DESC` on a textual value); numeric ordering resolved it to rank 111/version 144.

## Performance and resilience

The separate realistic synthetic run was **MEASURED with partial coverage** on disposable `bbc-perf-backend-audit`/`bbc-perf-db-audit` at `8102`, not on the acceptance database. It loaded 1,000 students, 50 performance classes, 100 staff, and 11,660 full-year attendance sessions. The bounded 15-iteration/6-worker run completed with 0.0 error rate for all 20 read/preview groups; attendance analytics was the slowest at p95/p99 40,723.20 ms, and dashboard students measured p95/p99 4,814.69 ms. The ten-sample finance follow-up measured student-options unfiltered p95 163.24 ms and no-match p95 988.01 ms. It isolated the earlier `COMPLETED_WITH_BLOCKERS` result to 25 `NO_ACTIVE_FEE_PLAN` rows, then created a valid API-only disposable class plan and generated 25/25 charges; same-key replay returned the original job without duplicates. The later supported accountant/cashier continuation generated/replayed charges, posted six CASH collections, closed the cashier session with zero variance, cancelled/read back report-card jobs, and published the full 50-class timetable-density fixture with `perfConflictCount=0`. A queued 25-item report-card job was read successfully after restarting only 8102, with 25 processed/blocked items and zero error items; successful report-card content generation remains unproven because the disposable fixture has no validated snapshots. Full mutation percentiles and broader concurrency/resilience remain open, so this is not a full performance PASS. Earlier Mailpit `UnknownHostException: mailpit` warnings were disposable fixture DNS noise; the rebuilt follow-up used an internal alias and recorded zero such warnings.

## Final release decision

**CONDITIONAL GO for functional candidate review; NO-GO for production release until the upgrade rehearsal, remaining mutation-heavy/resilience performance checks, and remaining Gate 14 matrix are complete.**

## Final disposable restore audit — 2026-08-15

- The disposable restore/backend continuation completed on the isolated `bbc-perf-*` resources only. The restored database reached Flyway `111|144|true`; the bounded full-density check published `1,500/1,500` slots across `50/50` performance classes with `perfConflictCount=0`.
- Cleanup removed `bbc-perf-backend-audit`, `bbc-perf-mailpit-audit`, and `bbc-perf-db-audit`, volumes `bbc-perf-db-audit` and `bbc-perf-documents-audit`, and network `bbc-perf-audit-net`. No disposable resource remains; port `8102` is connection-refused.
- Acceptance read-back after cleanup remained green: UI `8100` HTTP 200, backend `8101` HTTP 200, and acceptance Flyway through V144. This evidence does not close the production-like upgrade blocker or the partial performance/Gate 14 dispositions.

## Performance addendum - 2026-08-15T14:39+01:00

The disposable report-card-content check is now green after a narrow async
security-context fix: focused regression `2/2`, candidate rebuild/Flyway V144,
and run `20260815133914` produced 25/25 published report cards, zero item
errors, and an HTTP-200 archive containing 25 report-card PDFs plus 2
companions in 5.12 seconds. The student-options ten-sample measurements and
the `COMPLETED_WITH_BLOCKERS` diagnosis (`NO_ACTIVE_FEE_PLAN` before a
supported disposable fee-plan fixture) are recorded in the performance report.
The reference performance gate remains partial for full mutation/cancellation
percentiles and broader resilience; this does not alter the V77 upgrade
blocker or Gate 14 status.

Final-source verification after the fix passed `173/173` backend tests with
zero failures/errors/skips; fresh Testcontainers reached V144. Acceptance
health/Flyway remained green, the report-card worker and V140-V144 were present
in the jar, `git diff --check` had no whitespace errors, and the disposable
8102 stack was removed. The complete requirement-by-requirement disposition
is in `qa/e2e-runs/2026-08-14-full-school/final/requirement-disposition.md`.

## Superseding final-source and upgrade-lineage audit - 2026-08-15T15:45+01:00

The final candidate source verification supersedes the earlier interim test
counts: `mvn test` completed `175/175`, zero failures/errors/skips, with fresh
Testcontainers validating/applying all 111 migrations through V144. The
rebuilt acceptance image is the running `bbc-full-e2e-backend:latest`; startup
reported Flyway V144/no-op and 8101 health HTTP 200. The successful disposable
report-card-content run is separately recorded as 25/25 published items and a
working archive; bounded concurrent finance mutation and restart evidence is
in `final/performance-concurrent-mutation-20260815142418.md`.

The primary worktree does contain `output/PROD_DB_HANDOFF.md`,
`output/bbc_prod_2026-08-10.dump`, and `output/local_backup_2026-08-10.dump`.
The handoff explicitly describes the source production archive as a different
legacy lineage. The local backup was restored only into a disposable database
and failed candidate Flyway validation at V77 (`applied=-2113849035`,
`resolved=-95452447`). The separate `bbc_prod` archive is the old 56-table
production schema and has no candidate-compatible migration history. No
approved bridge/baseline or candidate-compatible sanitized backup is present.
Therefore the upgrade remains **BLOCKED**, and no Flyway repair, checksum
change, manual DDL/DML, or acceptance/production mutation is performed.

The remaining non-upgrade documentation/coverage gaps are deliberately
explicit: Gate 14's exhaustive per-route/persona/network/golden matrix and the
committed six-state screenshot pack required by section 29.1. See
`final/requirement-audit-20260815.md`.

## Superseding final candidate read-back - 2026-08-15T19:53:03+01:00

The authoritative current candidate evidence supersedes the interim counts at
the top of this file: the backend suite is `177/177` green, the frontend suite
is `23` test files / `51` tests green, UI `8100` returns HTTP 200, API `8101`
returns `{"status":"UP"}`, and acceptance Flyway is `144|true` with V140-V144
successful. The fresh Gate 14 Bursar/Cashier/FAM-A UI boundary slice is recorded
in `qa/e2e-runs/2026-08-14-full-school/final/gate14-ui-boundary-slice-20260815.md`.

The release decision remains **CONDITIONAL GO for functional candidate review**
and **NO-GO for production release**: exhaustive Gate 14 state/network/golden
coverage, broader UI-scale/resilience performance, and the V77 production-like
upgrade remain incomplete or blocked. No performance resources remain, no
Flyway repair/manual DB mutation was used, and nurse remains excluded.

## Superseding V145 candidate runtime/package checkpoint - 2026-08-16

The current candidate supersedes the earlier V144-only checkpoints in this
document. The packaged `backend/target/bbc-sms-0.1.0.jar` SHA-256 is
`1E95E5F759256B8B63EC6DF0C38DC37739BF2D91B8627EBF3878D9A95015F2C3`, and its
resources include V140 through V145, including
`V145__bootstrap_admin_student_profile_authority.sql`. The isolated images
currently running are `bbc-full-e2e-backend@sha256:841abe3d...` and
`bbc-full-e2e-frontend@sha256:2864db63...`.

The live acceptance database reports Flyway `145|true` at the tail, backend
health is `{"status":"UP"}`, and the UI endpoint on `8100` is HTTP 200. This
confirms the V145 bootstrap correction is packaged, scanned, applied, and
running; it does not alter the separate V77 production-like upgrade blocker.
The V77 blocker remains non-destructive: no repair, checksum edit, bridge,
manual DDL/DML, or production mutation was performed.

## Superseding V147 candidate verification - 2026-08-16

The current candidate source and deployed acceptance image were rebuilt after
the document-branding/address change. The backend Docker build completed
`19/19`, the deployed jar contains
`BOOT-INF/classes/db/migration/V147__bootstrap_admin_document_design_authority.sql`,
and startup reported Flyway validation of `114` migrations with schema version
`147` already current. A read-only query returned
`147|true|bootstrap admin document design authority`; no repair or manual
database change was used.

The final full backend suite is `182/182` green with fresh Testcontainers
reaching V147. The frontend is `25 files / 56 tests` green and its production
build exits `0`. The isolated runtime is healthy at `8100/8101/8125`, and no
`bbc-perf`, `8102`, or `8103` resource remains. Exact address/branding and
package evidence is in
`qa/e2e-runs/2026-08-14-full-school/final/final-source-verification-v147-20260816.md`.

This V147 result is a fresh-install/candidate-lineage PASS only. The separate
production-like V77 rehearsal remains **BLOCKED / NOT PASS**: the available
backup has the exact checksum fork `applied=-2113849035` versus
`resolved=-95452447`, and no approved candidate-compatible backup, bridge, or
baseline is available. Do not repair or mutate that archive until an authorized
upgrade strategy is supplied.
