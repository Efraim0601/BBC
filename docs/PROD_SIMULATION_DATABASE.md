# Production simulation database

This document describes the isolated database created from the read-only production replica for local production-like testing.

## Instances

| Component | Existing production/test copy | New simulation copy |
|---|---:|---:|
| PostgreSQL container | `bbcomplex-proddb` (5435) — source, untouched | `bbcomplex-prodtest-db` (5436) |
| Backend | current stack | `bbcomplex-prodtest-backend` (8084) |
| Browser entry point | current stack at `http://localhost:8082` | `http://localhost:8085` |
| Schema | source replica | current Flyway schema version 76 |

The existing `bbcomplex-proddb` and `bbcomplex-db-1` databases were not used as write targets. The new database has its own Docker volume: `bbcomplex_prodtest_pgdata`.

## Imported production data

The following counts were checked against the source replica after the load:

| Data set | Rows |
|---|---:|
| Students | 991 |
| Active students | 640 |
| Employees | 34 |
| Classes | 25 |
| Subjects | 29 |
| Roles | 12 |
| Login accounts | 10 |
| Module permission grants | 125 |
| Class-teacher links | 14 |
| Payment channels | 6 |
| Staff applications | 3 |
| Class resources | 2 items / 1 publication |
| Discipline incidents | 1 |
| Legacy parent-student links | 1 |
| Timetable slots | 2 |

Student, class, employee, subject, role, permission, payment, staff, resource, discipline, and legacy relationship rows retain their production IDs so cross-table references remain valid.

## Credential handling

Production password hashes were not copied. The generated changelog replaces them with local simulation credentials:

- `admin / admin` — principal account.
- `oumarou / password` — imported parent account linked to the one production parent-student relationship.
- Other imported accounts use `password` as the local password.

The production staff-portal token is not copied. The simulation school keeps the portal slug but has a null token.

The generated SQL is deliberately written under `tmp/`, which is ignored because it contains student and staff personal data. Only the generator is tracked:

[`build-prod-simulation-changelog.ps1`](../tools/build-prod-simulation-changelog.ps1)

## Current application scaffolding

The old production schema predates the current session-aware features. The changelog adds the minimum current-schema records required to exercise them:

- Current session `2025-2026`, `OPEN` and current. Its end date is extended to `2026-08-31` for local simulation so the current test date resolves to a session.
- Future target session `2026-2027`, `DRAFT`, for promotion testing.
- Three trimesters and ten reporting periods: `S1`, `S2`, `T1_RESULT`, `S3`, `S4`, `T2_RESULT`, `S5`, `S6`, `T3_RESULT`, and `ANNUAL`.
- Nine explicit dependencies: two sequences feed each trimester result, and the three trimester results feed the annual result.
- Explicit grade-entry, review, validation, publication, and correction windows for every reporting period.
- One current-session enrollment snapshot for each of the 640 active students.
- Three attendance policies: nursery daily, primary daily, secondary period/subject-based.
- Seven school-calendar rows for the current session.
- Nine timetable periods, session-aware class configurations, one published timetable version, two source slots, and one source room.
- A current-schema published promotion rule set carrying the source pass mark of 10/20. The current application’s review threshold is adapted to 8/20 from the source pass mark plus its one-point council margin; this is documented in the SQL comments.
- One published branding version and two generic enrollment-document templates without production logo/stamp assets.

## Data intentionally not fabricated

The source audit found no production rows for class-subject coefficient/curriculum relationships, attendance sessions/marks, fee definitions, fee assignments, payments, payroll transactions, grade records, bulletin snapshots, or class progression paths. Those tables remain empty in the simulation unless explicitly listed above as current-schema scaffolding.

In particular, no class progression map was invented. The Journey screen must still be used to configure and publish `6ème → 5ème`, `5ème → 4ème`, etc. before running an end-of-year promotion preview. This keeps the simulation honest about what was actually configured in production.

## Regenerate or reload

Run from the latest worktree. The source container must be running and the target must be a fresh simulation database:

```powershell
pwsh -File tools/build-prod-simulation-changelog.ps1 `
  -OutputPath tmp/prod_simulation_changelog.sql

pwsh -File tools/build-prod-simulation-changelog.ps1 `
  -OutputPath tmp/prod_simulation_changelog.sql `
  -Apply `
  -TargetContainer bbcomplex-prodtest-db `
  -TargetDatabase bbc_sms `
  -TargetUser bbc
```

The script reads only from `bbcomplex-proddb` and applies the generated SQL inside one transaction. Do not point `TargetContainer` at `bbcomplex-proddb`.

## Verification completed

- Flyway validated all 68 migrations and reported schema version 76.
- The isolated backend started successfully against port 5436 with the demo profile disabled.
- `admin/admin` login succeeded.
- Session and reporting-period endpoints returned two sessions and ten periods.
- Attendance policy, class roster, and analytics endpoints returned successfully.
- Timetable versions returned one published version with two slots.
- Setup endpoints returned 25 classes, 29 subjects, and an empty coefficient list matching the source audit.
- Parent login `oumarou/password` returned the linked child.
- Foreign-key checks found zero active students without an enrollment, zero guardian-link orphans, and zero timetable-version orphans.

Use `http://localhost:8085` for the simulation UI. The existing `http://localhost:8082` entry point continues to use the previous stack and is intentionally not switched to this database.

The simulation backend must allow the frontend origin with `BBC_CORS_ORIGINS=http://localhost:8085`. The backend API remains exposed on `8084`, but `8084` is not the browser origin; using it as the CORS origin causes browser login requests to return `403` even though direct API calls appear to work.
