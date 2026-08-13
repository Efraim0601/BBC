# Teacher academic access control — implementation and verification report

## Scope

Branch: `feature/academic-teacher-access-control`

This branch adds one server-side academic access resolver/policy and applies it to curriculum subjects, grade entry, session assessments and grades, subject comments, competency marks, report-card inputs, bulletins/PV, snapshots, batch jobs, report-card PDFs, and official report-card documents.

The policy is tenant-, session-, active-enrollment-, subject-, and effective-date-aware. Primary/Nursery subjects resolve to the dated class titulaire. Secondary subjects resolve to exactly one active `RESPONSIBLE` assignment. Secondary titulaires receive read-only class results by default and only receive edit authority through a responsible assignment or an explicit delegation. Collections are filtered before returning, and direct resource operations re-resolve the same scope server-side.

The canonical assignment tables remain `class_teacher_assignment`, `academic_curriculum_subject`, and `academic_class_subject_teacher`. Timetable resolution continues to consume `TeachingAssignmentResolver`, including its missing/ambiguous assignment and double-booking protections.

## Delivered surfaces

- Flyway `V115__academic_teacher_access_control.sql` adds the scoped, audited, dated `academic_access_delegation` model, overlap protection, indexes, and coarse action grants.
- `AcademicAccessPolicyService` provides structured capabilities and stable denial codes, including enrollment mismatch, missing teacher-account link, out-of-session dates, class-result denial, subject denial, packet denial, and council-input denial.
- Delegation readiness, impact preview, create/revoke, and teacher-scope APIs are exposed under `/api/academic-access` and `/api/academic/me/scope`.
- Setup selectors display employee name, employee code, linked username, and role. Curriculum/teaching configuration is separate from access exceptions. Primary subject-teacher editing is blocked server-side and shown as inherited/locked in the UI.
- Academic UI adds filtered My grade sheets and a separate read-only Class overview mode.
- Existing append-only audit infrastructure records delegation and teaching-assignment changes.
- Accepted/locked historical grades remain immutable; only permitted mutable drafts can be changed or transferred.

## Verification

All commands were run from this branch on 2026-08-13.

| Check | Result |
|---|---|
| `npm ci --ignore-scripts --no-audit --no-fund` | PASS — clean install, 546 packages |
| `npm run build -- --progress=false --verbose` | PASS — Angular bundle generated; one pre-existing NG8107 warning in `staff.ts` |
| `npm run test:ci` | PASS — 10 files, 22 tests |
| `mvn -q -DskipTests compile` | PASS |
| Focused policy/action/migration/resolver tests | PASS with `-Dnet.bytebuddy.experimental=true` |
| Final post-review policy hardening regression set | PASS — policy/action tests and backend compilation |
| Full backend `mvn -q -DargLine="-Dnet.bytebuddy.experimental=true" test` | PASS — 29 test files, 84 tests, 0 failures, 0 errors, 0 skipped |
| Flyway integration | PASS — fresh PostgreSQL containers migrated through v115 |
| `docker compose config` | PASS |
| `docker compose build backend frontend` | PASS — both images built |
| Isolated Docker smoke stack | PASS — database healthy, backend `/actuator/health` 200, frontend `/` 200, unauthenticated academic access/scope routes 401 |
| `git diff --check` | PASS |

The backend focused tests require the Byte Buddy experimental flag only because the available runner is Java 25 while the repository’s Mockito/Byte Buddy version officially supports through Java 23. The same flag was used for the full suite; it does not change application behavior.

## Live acceptance status

No production tenant or production data was used or changed. The Docker acceptance smoke used a separate Compose project, network, and volumes and was removed after verification. A full authenticated teacher-persona browser acceptance run against a live tenant was not available in this workspace; deterministic authenticated principal coverage is included in backend policy/integration tests, while the live smoke verified startup, route authentication, and frontend serving.
