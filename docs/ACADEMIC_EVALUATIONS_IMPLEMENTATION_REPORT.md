# Academic evaluations and computed-result milestones — implementation report

Date: 2026-08-10<br>
Worktree: `C:\Users\joe tech\.codex\worktrees\c0aa\bbcomplex`<br>
Starting ref/HEAD: `codex/report-card-fidelity` / `a30ef58`<br>
Push: none

## Delivered

- Added shared sequence-only guards. Only `SEQUENCE` periods S1–S6 can own editable assessments, raw marks, or teacher submission. T1/T2/T3 and Annual are persisted computed milestones.
- Added the canonical `academic_curriculum_subject` scope query used by evaluation setup, grade entry, teacher resolution, and report-card coefficient lookup.
- Added preview-first whole-class evaluation defaults with `ONE_SEQUENCE` and `ALL_SEQUENCES` modes, one row per applicable assigned subject, class coefficient, localized label, editable code/name/barème/weight, read-only resolved teacher, row status, field errors, and totals.
- Added transactional, idempotent apply through `Idempotency-Key`, generation batches, stale-preview detection, duplicate preservation, and actionable validation messages.
- Added canonical assessment update/delete guards, grade-presence protection, computed-period read/write rejection, and direct legacy grade endpoint retirement in favor of canonical grade packets.
- Made the secondary competency path additive and canonical: legacy models/marks remain available as compatibility mirrors, while `academic_assessment`/`academic_grade` are authoritative. Locale is derived from class subsystem; teacher identity is resolved from responsible class-subject or homeroom assignment.
- Kept report-card snapshots immutable and changed computed result calculation to use configured dependency weights and published child snapshots for Annual results. Report-card subject coefficients come from the session/class curriculum relationship.
- Made readiness and publication-window behavior period-type aware. Computed milestones show `NOT_APPLICABLE` for raw teacher entry/submission windows.
- Added the Evaluations settings UX, sequence-only grade-entry selector, computed-result bulletin panel/formula display, and the existing attendance/council route remained compatible.

## Schema and data transitions

- `V77__unify_sequence_evaluations_and_backfill_secondary.sql`
  - Adds generation-batch audit storage, additive legacy-link columns, and explicit secondary migration conflicts.
  - Promotes latest non-retired secondary sequence definitions and marks only when the subject is assigned in `academic_curriculum_subject`.
  - Does not update or rewrite bulletin snapshots.
- Seed files were renumbered to avoid the production V77 collision: V78–V81 preserve the existing demo fixtures.
- `db/seed/V82__promote_seed_secondary_to_canonical.sql` promotes legacy definitions seeded after V77 through the same canonical path, so blank demo deployments exercise the transition without direct database edits.
- The next unused production migration number was verified as V77. No production or simulation schema was edited manually.

## Main files

Backend:

- `AcademicPeriodRules.java`
- `CurriculumQueryService.java`
- `AssessmentDefaultsService.java`
- `AcademicController.java`, `AcademicDtos.java`, `AcademicAssessment.java`, `AcademicGrade.java`, `AcademicAssessmentRepository.java`
- `SessionAcademicService.java`, `GradeEntryService.java`, `BulletinSnapshotService.java`
- `AcademicSessionService.java`, `AcademicWindowPolicyService.java`
- `secondary/SecondaryCompetencyService.java`, `secondary/SecondaryCompetencyDtos.java`
- `V77__unify_sequence_evaluations_and_backfill_secondary.sql`
- `V78__seed_secondary_report_card_fidelity.sql` through `V82__promote_seed_secondary_to_canonical.sql`
- `AcademicPeriodRulesTest.java`

Frontend:

- `features/setup/assessment-defaults/assessment-defaults.ts`
- `features/setup/academic-setup.ts`
- `features/academic/academic.api.ts`
- `features/academic/academic.ts`
- `features/settings/foundation-settings.ts`

## Commits

- `4a5ed17` — `feat: unify canonical sequence evaluations`
- `5ec855a` — `feat: add evaluation review and computed result UX`

## Verification

Backend:

- `mvn -DskipTests package` — passed.
- `mvn test '-DargLine=-Dnet.bytebuddy.experimental=true'` — 25 tests passed, 0 failures/errors. This includes a fresh Testcontainers PostgreSQL migration from empty schema through production V77.
- The Byte Buddy flag is required in this environment because the repository runs on Java 25 with the pinned Byte Buddy version.

Frontend:

- `npm ci` — completed; npm reported the repository dependency audit summary (25 vulnerabilities).
- `npm run test:ci` — 5 test files and 10 tests passed.
- `npm run build` — passed. The only build warning is the existing `NG8107` optional-chain warning in `features/staff/staff.ts:1035`.

Docker/deployment:

- `docker build -t bbcomplex-backend:academic-evals ./backend` — passed.
- `docker build -t bbcomplex-frontend:academic-evals ./frontend` — passed.
- Live simulation URL: http://localhost:8085
- API health: http://localhost:8084/actuator/health → `UP`
- Running containers: `bbcomplex-prodtest-frontend`, `bbcomplex-prodtest-backend`, and the existing `bbcomplex-prodtest-db` on port 5436.
- The final frontend container is linked to the final backend container as `backend:8080` for the repository nginx configuration.
- Stopped rollback containers retained: `bbcomplex-prodtest-frontend-previous` and `bbcomplex-prodtest-backend-previous`.

Migration checks:

- Fresh Testcontainers database: Flyway validated 69 repository production migrations and reached V77.
- Production-clone simulation database: reached V77; current counts are 21 canonical assessments, 0 canonical grades, 3 generation batches, and 1 explicit `SUBJECT_NOT_ASSIGNED_TO_CLASS:MATH` conflict preserved from the pre-existing legacy MATH_S1 fixture for 4eme A.
- Fresh demo migration database using the versioned seed path: reached V82 with 1,008 canonical assessments, 288 canonical grades, 504 legacy models, 288 legacy marks, and 0 migration conflicts.

API smoke checks with `admin/admin`:

- Preview for session `2025-2026`, class `4eme A`, S1 returned 7 assigned subject rows and wrote nothing.
- Apply created 7 S1 rows; retrying the same idempotency key returned the existing result without duplicates. The same flow was exercised for S2 and S3, leaving 21 canonical assessment definitions.
- S1 assessment listing returned 7 rows; T1 assessment listing returned 0 rows.
- A valid raw assessment attempt for T1 returned coded `ASSESSMENT_SEQUENCE_ONLY`.
- T1 `GRADE_ENTRY` effective window returned `NOT_APPLICABLE` with blocker `COMPUTED_RESULT_PERIOD`.
- Dependency API returned S1/S2 → T1, S3/S4 → T2, S5/S6 → T3 at 0.5/0.5 and T1/T2/T3 → Annual at 0.33333 each.

## Exact browser verification path

1. Open http://localhost:8085 and sign in as `admin` / `admin`.
2. Go to `Paramètres` → `Évaluations` (the former competency surface is now an Evaluations alias).
3. Select session `2025-2026`, class `4eme A`, `Une séquence`, and `S2`; click `Préparer la revue`.
4. Confirm the 7-row whole-class review: assigned subjects only, class coefficients, editable bordered code/name/barème/weight fields, `Requis pour soumission`, resolved teacher shown read-only, and row totals/statuses.
5. Set a barème and weight to zero to confirm field-level red validation and disabled creation; restore valid values, click `Créer les évaluations`, then confirm in `Confirmer la création` → `Créer maintenant`.
6. Repeat the review for S3 and confirm the success notice and subsequent `0 à créer / 7 déjà présentes` state.
7. Go to `Académique` → `Saisie des notes`; the period selector contains exactly S1, S2, S3, S4, S5, S6. With `4eme A`, the canonical sheet loads the assigned teacher and roster.
8. Go to `Académique` → `Bulletin`, select T1, and confirm `Résultat calculé` with `Calcul : S1 × 0.5 + S2 × 0.5`. Select Annual and confirm the T1/T2/T3 weighted formula.
9. Go to `Paramètres` → `Années & périodes`; computed milestones show teacher entry/submission as not applicable, while sequence rows retain submission configuration.
10. Go to `Académique` → `Assiduité & conseil`; the 4eme A roster, attendance aggregate, correction-hours fields, council fields, and save/submit controls load without breaking the report-card/attendance interaction.
11. Browser console error log was empty after the live flow.

No raw UUIDs, hashes, SQL constraint names, or generic conflict-only messages are shown in the normal review UX. Preview/apply test data was created through API/UI paths; the blank demo fixture data was created only through versioned seed migrations.
