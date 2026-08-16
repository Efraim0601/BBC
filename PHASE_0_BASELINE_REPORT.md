# Phase 0 baseline evidence

Captured after checkpoint commit `a988c92` on 2026-08-09 before implementation changes.

## Repository

- Starting commit: `80dfbc0 feat(BAY-10): complete academic reporting and bulletins`
- Checkpoint commit: `a988c92 chore: checkpoint audited timetable and reporting baseline`
- Protected timetable/class-subject files were included unchanged in the checkpoint.
- Existing untracked presentation, report, `output/`, and `tmp/` artifacts were included unchanged.
- Worktree is detached at the checkpoint commit; no reset or branch rewrite was performed.

## Runtime baseline

Docker Compose is healthy:

| Service | State | Host port |
| --- | --- | --- |
| `bbcomplex-db-1` | Up / healthy | PostgreSQL `5433` |
| `bbcomplex-backend-1` | Up | API `8080` |
| `bbcomplex-frontend-1` | Up | UI `8082` |

`GET http://localhost:8080/actuator/health` returned `{"status":"UP"}`.

Live sessions are `2026-2027=OPEN/current`, `2027-2028=DRAFT`, and `2025-2026=ARCHIVED`.
The starting capture had the ten expected milestones (`S1`, `S2`, `T1_RESULT`, `S3`, `S4`,
`T2_RESULT`, `S5`, `S6`, `T3_RESULT`, `ANNUAL`), but the workflow phases were broad and
operationally unsafe. Subsequent additive migrations V60 and V64 configure explicit
non-overlapping 2026–2027 phase windows; this report intentionally preserves the original
baseline observation rather than rewriting it as post-change evidence.

## Existing automated baseline

- Backend test inventory: two integration classes and eight `@Test` methods.
- No existing characterization tests cover canonical teacher resolution, assignment ambiguity,
  coefficient precedence, immutable correction, frozen child-result calculation, pure promotion
  preview, or `PLANNED` enrollment transitions.
- Local Maven execution is not a usable baseline runner in this environment: the configured
  Java 25 installation is incompatible with the current Lombok/compiler combination; the fallback
  Java 17 runtime cannot compile the Maven `release 21` target. The Docker build image includes
  Temurin 21 and remains the verification runner for backend compilation.
- The system frontend image is running; the host shell does not expose `npm`, so the bundled
  Node runtime or Docker frontend build is the verification runner for frontend tests/builds.

## Phase 0 acceptance checks to add

1. Canonical homeroom/responsible teacher resolution for primary and secondary classes.
2. Explicit missing/ambiguous assignment blockers and client-teacher mismatch errors.
3. Session curriculum coefficient precedence over catalog and legacy class defaults.
4. Pure calculation vectors for sequence, term, optional COMP, T3, and Annual products.
5. Immutable published/correction behavior, pure promotion preview, and `PLANNED` enrollment
   transition semantics.

This report is evidence of the starting state only; it is not a completion claim for BAY-10,
BAY-11, BAY-13, or any child story.
