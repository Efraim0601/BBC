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

The authenticated acceptance run was completed on 2026-08-13 against an isolated Docker tenant created from this branch. No production tenant or production data was used or changed.

Test endpoints:

- Frontend: `http://localhost:8093`
- Backend: `http://localhost:8092`
- Compose project: `bbc-academic-live` with a separate database volume

Seeded personas and data:

- `primary.teacher` — primary titular for `CE1-Live`
- `french.teacher` — secondary teacher responsible only for Français in `4eme-Live`
- `math.teacher` — secondary teacher responsible only for Mathématiques in `4eme-Live`
- `secondary.form` — secondary class titular for `4eme-Live`
- `CE1-Live` with one student; `4eme-Live` with two students; `5eme-Live` with no students
- Français and Mathématiques assigned to `4eme-Live`; French and Math teachers assigned as the responsible teachers

### Authenticated academic checks

| Scenario | Result |
|---|---|
| French teacher opens the academic screen for `4eme-Live` | PASS — the class roster and Sequence 1 grade sheet load instead of the former access/zero-student state |
| French teacher sees available subjects | PASS — only Français is available; Mathématiques is not exposed |
| French teacher saves a grade for a French assessment | PASS — Benoit received 15/20 and the saved value remained visible after reload |
| French teacher requests Mathématiques directly | PASS — API returns 403 with the precise subject-assignment denial |
| French teacher requests report-card/PV management data | PASS — API returns 403; subject teachers cannot access class-wide management data |
| Primary/form-teacher session access | PASS — the active academic session is visible to authenticated teachers |

### Timetable checks

| Scenario | Result |
|---|---|
| Class-subject assignment resolves the teacher automatically | PASS — 4ème Français resolves to French Teacher and Mathématiques to Math Teacher |
| Attempt to replace the responsible teacher while saving a slot | PASS — backend rejects the wrong teacher with a 400 and identifies the canonical teacher |
| Attempt to double-book French Teacher in another class at the same period | PASS — backend rejects it with `TIMETABLE_TEACHER_CONFLICT` and identifies the existing class/subject |
| Publish the timetable | PASS — initial room-resource blocker was explicit; after registering the rooms, the version published successfully |
| Teacher personal timetable API | PASS — `/api/timetable/teachers/me` returns the published French Teacher schedule |
| Teacher personal timetable in the browser | PASS after fix — read-only teachers now see “Your published schedule” and their own published slots; the UI no longer calls the manager-only arbitrary-teacher endpoint or displays “Access denied” |

### Attendance checks

| Scenario | Result |
|---|---|
| Primary policy/model | PASS — `CE1-Live` uses DAILY; marking Present, finalizing, and analytics returned 100% for one expected roll call |
| Secondary policy/model | PASS — `4eme-Live` uses PERIOD; the published Tuesday timetable produced P1 Français and P2 Mathématiques sessions |
| Secondary teacher roster scope | PASS — French Teacher can open the 4ème roster for P1 Français; the roster contains both enrolled students and no unrelated class |
| Secondary attendance write/finalize | PASS — Benoit was marked Late and Chantal Absent; the session finalized successfully |
| Attendance analytics | PASS — analytics reported 4 expected student-period rows, 1 late, 1 absent, 2 unmarked, and 25% attendance for the tested range |
| Browser attendance flow | PASS — the French teacher can select the date, class, and published period and sees the finalized roster/status history |
| Invalid level-model combinations | PASS — primary DAILY→PERIOD and secondary PERIOD→DAILY changes are rejected with explicit validation messages |

### Fresh-school provisioning fixes made during live testing

The first-run acceptance tenant exposed two real provisioning gaps that ordinary tests missed because they reused existing seed data:

- Attendance policies and attendance action grants were absent for schools created after Flyway completed. `V116` backfills existing schools, and `ProductionBootstrap.seedAttendanceDefaults` provisions new schools with DAILY primary/maternelle defaults, PERIOD secondary defaults, management grants, scoped teacher grants, and `SESSION_VIEW`.
- Teachers could not load the academic roster because the fresh-school bootstrap omitted the teacher `students:read` module grant. `V117` backfills that grant while the existing scope policy still filters the actual roster by class and subject.

The timetable browser test also found and fixed a UI/API mismatch: the teacher-facing tab used the manager-only `/teachers/{id}` endpoint. Read-only teachers now use `/teachers/me`, while managers retain the teacher selector for administrative inspection.

The acceptance tenant was disposable and was stopped/removed after the test. The user’s existing application/database was not modified.
