# BBC SMS full-school lifecycle E2E report

Run: `2026-08-14-full-school`  
Candidate worktree: `C:\Users\joe tech\.codex\worktrees\full-school-e2e`  
Candidate branch: `feature/full-school-lifecycle-e2e-2026-08`  
Authoritative runtime: isolated Compose project `bbc-full-e2e` only (`8100` UI, `8101` API, private `bbc-full-e2e-db-1`, Mailpit `8125`). The dirty primary worktree and the older `8096/8095` stack were preserved and were not used as acceptance evidence.

## Executive disposition

Final verification addendum (2026-08-15): the previously pending production-scale read/preview run is now measured with partial coverage in `qa/e2e-runs/2026-08-14-full-school/final/performance-report.md`. The sanitized production-like upgrade remains blocked by the documented V77 lineage fork, and Gate 14 remains in progress. The e2e-only second-school fixture now supplies a strictly profile/property-gated cross-tenant subcheck; the final backend suite, source hygiene, candidate health/Flyway, and performance-stack isolation checks are green as recorded below.

The candidate is **CONDITIONAL GO for the exercised lifecycle, not a full Definition-of-Done PASS**. Gates 0–13 are evidenced as PASS after the V141–V144 continuation. Gate 14 remains IN PROGRESS because the required exhaustive persona matrix is not complete; its e2e-only second-school/cross-tenant subcheck is now green and the bounded policy-cache apply/expiry check is green. The realistic-scale performance work is measured but partial, and the sanitized production-like upgrade/restore/rollback rehearsal remains blocked, so release readiness is not claimed.

No open P0/P1 was observed after the fixes below. P2/P3 ownership/disposition and the two release-readiness blockers remain explicit follow-up work.

## Gate summary

| Gate | Result | Evidence |
|---:|---|---|
| 0–10 | PASS | [`gate-ledger.md`](qa/e2e-runs/2026-08-14-full-school/gate-ledger.md), gates 0–10 |
| 11 | PASS for exercised linked-child/auth/invitation/reset/read/ack journeys | [`10-parent-portal/evidence.md`](qa/e2e-runs/2026-08-14-full-school/10-parent-portal/evidence.md) |
| 12 | PASS for exercised operational lifecycles and Direction dashboard reconciliation | [`11-daily-operations/evidence.md`](qa/e2e-runs/2026-08-14-full-school/11-daily-operations/evidence.md) |
| 13 | PASS | [`12-promotion/evidence.md`](qa/e2e-runs/2026-08-14-full-school/12-promotion/evidence.md) |
| 14 | IN PROGRESS | [`13-permission-sweep/evidence.md`](qa/e2e-runs/2026-08-14-full-school/13-permission-sweep/evidence.md) |

## Final V144 build and runtime proof

- Maven package: `mvn -q -DskipTests package`, exit 0.
- Packaged jar SHA-256: `973A62BAEFA6B4641FBFE39C5273992C64DEB867DFC3A5F9145813E71F85C507`.
- The jar contains `V140__promotion_workspace_school_scope.sql` through `V144__principal_academic_legacy_bootstrap_alignment.sql` under `BOOT-INF/classes/db/migration/`.
- Candidate Docker build completed from the candidate context. Image manifest: `sha256:429efd2a78765be7574d09b6bd874dc6bc169a9658ebe07a5f37e6386b3055b8`.
- Backend restart was `docker compose ... -p bbc-full-e2e up -d --no-deps backend`; startup logged validation of 111 migrations, migration from v143 to v144, and `now at version v144`.
- `/actuator/health` on `http://localhost:8101` returned HTTP 200, `{"status":"UP"}`.
- Numeric read-only Flyway ordering: rank 111/version 144, rank 110/version 143, rank 109/version 142; all successful. The earlier apparent rank mismatch was a harness sorting `installed_rank` lexicographically, not a database mismatch.
- V144 live legacy principal grants: exactly 3 (`ACADEMIC_GRADE_PACKET_REVIEW`, `ACADEMIC_REPORT_CARD_VALIDATE`, `ACADEMIC_REPORT_CARD_PUBLISH`). V143 live V2 principal dashboard grants: exactly 6. No grade-edit, curriculum-edit, or teaching-assignment-management grant was added.

## Defects fixed and regression coverage

1. Fresh bootstrap setup denial (P0-01): the setup controller now leaves action/scope authorization to the V2 service boundary. Fresh `POST /api/setup/sections` returned 201; ordinary role templates were not broadened.
2. Inherited-scope safe-template preview denial (P0-02): `NullNode` is normalized as absent scope payload. Fresh API and 8100 UI previews returned 200 with no inherited-scope error.
3. Parent safe operational surfaces (V141/V142): linked-child attendance, discipline, health, events, messages, and message acknowledgement are exposed through the parent policy boundary. V142 supplies only the narrow `PARENT_MESSAGES_ACK` role authority.
4. Parent public invitation acceptance tenant context: the audit operation now runs inside the guardian school tenant context and restores/clears context afterward. Invitation acceptance and reset journeys passed through the supported API and Mailpit flow.
5. Direction dashboard read alignment (V143): read-only student, attendance, and finance source authorities were added for the principal oversight profile. No finance collection/edit authority was added. The 8100 dashboard now reconciles live source APIs.
6. Fresh-bootstrap academic legacy compatibility (V144): the three legacy principal workflow grants are seeded after a fresh tenant is created; V144 covers existing tenants. The fresh production-bootstrap integration passed 8/8.
7. Authorization contract tests were corrected to assert contextual actions at the service boundary (`StudentService`, `EnrollmentService`, `GuardianService`, timetable services) rather than weakening authorization or adding context-free controller bypasses.

## GJ-07 delegation and scoped authorization proof

The candidate delegation guard was corrected narrowly: `AcademicAccessController` keeps only the staff envelope, while `AcademicAccessDelegationService` and `AcademicAccessPolicyService` resolve the resource-aware manager/viewer decision. A live isolated-8101 regression proved admin create `201`, teacher/accountant create `403 ACADEMIC_ACCESS_DELEGATE_DENIED`, parent create `403 FORBIDDEN`, invalid class/subject `400 SUBJECT_NOT_ASSIGNED_TO_CLASS`, Aicha ordinary grade-entry `403 POLICY_RULE_MISSING` before delegation, `200` during the dated grant, expired scope capabilities false/source `NONE`, revoke `200`, and `403 POLICY_RULE_MISSING` after revoke. The canonical 9-row teacher assignment was byte-for-byte unchanged (SHA-256 `3d599006fe965fc0edf06076f7a9f77b7abd58ef5d5372044a57091332589848`); active grants read back as 0 and revoked history as 1.

The live `409 DELEGATION_OVERLAP` diagnosis was a service defect: the insert omitted the separate `approved_by` binding and broad integrity-error handling mislabeled any persistence failure as overlap. The fix binds requester and approver separately, narrows overlap mapping, returns `DELEGATION_PERSISTENCE_CONFLICT` for other persistence conflicts, and explicitly types the nullable status list filter. Focused backend regression is `24/24` (11 policy tests, 13 controller/service contract tests), zero failures/errors/skips. No ordinary role grant, migration, direct DB mutation, or nurse-specific change was made. Full exact statuses and correlations are in [`13-permission-sweep/evidence.md`](qa/e2e-runs/2026-08-14-full-school/13-permission-sweep/evidence.md).

## Promotion proof

The published annual prerequisite contains 24 accepted S1–S6 packets and approved annual conduct. Annual snapshots published with averages 15, 16, and 17. The real commit-preview route is `/journey/progression/batches/{id}/commit/preview` and returned HTTP 200 with `COMMITTED` replay blockers after commit. Preview returned HTTP 200; stale override returned HTTP 409; valid manual override returned HTTP 200 and changed only Marius to `REPEAT` with a required reason; commit returned HTTP 200 `COMMITTED`; identical replay returned HTTP 200 `COMMITTED` without duplicates. Exact IDs, register hash, and source/target reconciliation are in the promotion evidence file.

## Automated verification

- Focused V143/V144/security contracts: 13/13 passed.
- Fresh `ProductionBootstrapIntegrationTest`: 8/8 passed with Testcontainers PostgreSQL 16.14 and 111 validated migrations.
- Full backend `mvn test`: **164 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS; 02:58; finished 2026-08-15T12:13:55+01:00**. This includes the five production-profile/test-property fixture-boundary tests.
- The full suite exercised fresh Testcontainers migration through v144, computed academic results, accounting, finance reporting, family management, migration contracts, policy contracts, and all current authorization contract tests.

## Required deliverables

- Environment and gate ledger: [`qa/e2e-runs/2026-08-14-full-school/00-environment/manifest.md`](qa/e2e-runs/2026-08-14-full-school/00-environment/manifest.md), [`gate-ledger.md`](qa/e2e-runs/2026-08-14-full-school/gate-ledger.md)
- Module system reference: [`BBC_SMS_SYSTEM_REFERENCE.md`](BBC_SMS_SYSTEM_REFERENCE.md)
- Permission acceptance matrix: [`BBC_SMS_PERMISSION_ACCEPTANCE_MATRIX.md`](BBC_SMS_PERMISSION_ACCEPTANCE_MATRIX.md)
- Calculation and reconciliation: [`BBC_SMS_DATA_AND_CALCULATION_RECONCILIATION.md`](BBC_SMS_DATA_AND_CALCULATION_RECONCILIATION.md)
- Migration/release readiness: [`BBC_SMS_MIGRATION_AND_RELEASE_READINESS.md`](BBC_SMS_MIGRATION_AND_RELEASE_READINESS.md)
- Detailed module evidence: `qa/e2e-runs/2026-08-14-full-school/*/evidence.md`
- Final route/test/fixture/performance/defect pack: `qa/e2e-runs/2026-08-14-full-school/final/`
- Defect register: [`defects/defect-register.md`](qa/e2e-runs/2026-08-14-full-school/defects/defect-register.md)

Credentials, bearer tokens, invitation/reset tokens, SMTP secrets, and raw mailbox bodies are intentionally excluded from the report; only redacted mailbox references and statuses are retained in the evidence files.

## Release blockers and next actions

- **Production-like upgrade:** the available local backup was restored only into a disposable PostgreSQL 16.14 container. It is at Flyway v80 but has the demo-seed V77 lineage; the exact candidate stopped at validation with applied checksum `-2113849035` versus resolved `-95452447`. No repair or manual database edit was used. A sanitized candidate-compatible source backup, or an approved lineage bridge/baseline design, is required before this gate can be PASS.
 - **Performance/resilience:** the separate bounded 15x6 run still records 1,000 students, 50 performance classes, 100 staff, 11,660 attendance sessions, and 20 read/preview groups at 0.0 error rate; attendance analytics p95/p99 was 40,723.20 ms and dashboard students p95/p99 was 4,814.69 ms. The new bounded finance follow-up measured student-options unfiltered p95 163.24 ms and no-match p95 988.01 ms over ten samples. It isolated the prior `COMPLETED_WITH_BLOCKERS` result to 25 `NO_ACTIVE_FEE_PLAN` rows, then used the supported accountant/cashier personas to generate 25 charge results, post/replay/close a CASH payment with zero variance, cancel/read back a 25-item report batch, and publish the full 50-class current-session timetable fixture. Full concurrent mutation percentiles, successful report-card content, and broader resilience remain partial. See `final/performance-report.md`.
 - **Gate 14:** the e2e-only second-school fixture is now strictly gated by `@Profile("e2e & !prod")`, `BBC_E2E_FIXTURES_ENABLED=true`, and the normal `PERMISSION_MANAGE` guard. Production-only, production-plus-e2e, and e2e-with-property-false contract tests register no fixture controller; live acceptance evidence is `401` unauthenticated, `200` bootstrap-admin idempotent read-back, prior clean-context ordinary-role fixture write `403`, and cross-tenant student read `404`. Clean-session direct route/read checks for the in-scope personas, including isolated FAM-A parent UI, are green; the full per-persona console/network inventory and remaining golden-journey matrix are still open. The bounded policy-cache apply/expiry check is green; keep the final release decision CONDITIONAL until the remaining results exist. Exact fixture evidence: `qa/e2e-runs/2026-08-14-full-school/final/gate14-second-tenant-fixture-evidence.md`.

## Defect register and interrupted-run recovery

| ID | Severity | Root cause | Fix/regression evidence | Current disposition |
|---|---|---|---|---|
| OBS-P0-01 | P0 | Fresh bootstrap setup actions were rejected by a controller-level authorization check. | V124 and the SetupController/SetupService boundary fix; fresh section setup returned HTTP 201; ordinary role templates were unchanged. | CLOSED; owner: candidate engineering; acceptance: fresh setup on a clean database. |
| OBS-P0-02 | P0 | Permission Policy V2 safe-template preview treated a JSON `NullNode` as a populated inherited-scope object. | Null normalization in AccessControlService plus Angular error-state reset; API/UI preview returned HTTP 200 and `changes=0`; frontend regression is recorded. | CLOSED; owner: candidate engineering; acceptance: inherited-scope preview on fresh and migrated data. |
| V141/V142 parent operations | P1 | Parent operational reads and acknowledgement lacked the required narrow action/scope authorities. | Forward-only migrations, parent service scope checks, linked-child 200s, unrelated-child ack 403, and idempotent replay 200. | CLOSED for exercised Gate 11; owner: candidate engineering. |
| V143/V144 principal alignment | P1 | Direction dashboard read sources and fresh principal legacy academic workflow grants were absent/misaligned. | V143/V144, fresh bootstrap 8/8, live v144 grant counts, Direction API/UI read-back, and 159/159 full backend suite. | CLOSED for exercised journeys; ordinary mutation authorities remain absent. |
| Authorization contract assertions | P1 | Historical controller tests asserted contextual actions at the wrong layer after authorization was correctly moved to resource-resolving services. | The first focused run failed on stale `ENROLLMENT_VIEW` controller assertion; the next failed on stale `GUARDIAN_VIEW`; a third failed on stale `TIMETABLE_SUBSTITUTION_VIEW`. Assertions were moved to EnrollmentService, GuardianService, TimetableService, and TimetableVersionService. Final focused suite 13/13 and full suite 159/159. | CLOSED; harness/test expectation defect, not an application bypass. |
| Finance preview/quote rollback-only error | P1 | Preview/read paths caught a posting-period exception after a nested transaction had marked the shared transaction rollback-only, producing HTTP 500. | `AccountingPeriodService.findOpenForDate` provides a non-throwing preview lookup; focused accounting/preview tests passed 4/4 and corrected scale retest passed 5x2 with zero errors. | CLOSED; narrow service fix and final Maven regression. |
| Production-like upgrade rehearsal | P2 | Disposable rehearsal reached the exact V77 checksum/lineage fork (`-2113849035` applied vs `-95452447` resolved) before any migration; the available archives are not candidate-compatible sanitized fixtures. | Fresh install and restart are green; no Flyway repair or unsafe restore was attempted. | BLOCKED; owner: release/DB; acceptance: authorized sanitized backup or approved bridge restored to a disposable DB, forward Flyway upgrade, count/encoding/accounting/rollback comparison. |
 | Realistic-scale performance | P2 | Full reference-plan resilience coverage was not fully executed. | Separate disposable 15x6 run measured 1,000 students, 50 performance classes, 100 staff, 11,660 attendance sessions, and 20 read/preview groups at 0.0 error rate. The follow-up measured finance student-options, isolated `NO_ACTIVE_FEE_PLAN`, generated 25/25 charges after an API-only valid fixture, proved same-key replay, read a 25-item background job after a disposable backend restart, completed cashier collection/replay/close, cancelled/read back a 25-item report batch, and published the full 50-class current-session timetable fixture. | IN PROGRESS; owner: QA/performance; full concurrent mutation percentiles, successful report-card content, and broader resilience remain. |
| Exhaustive Gate 14 matrix | P2 | The e2e-only second-school fixture now covers the authorized cross-tenant subcheck, but the full per-persona console/network inventory and complete golden-journey matrix were not fully executed. | Strict `e2e & !prod` profile/property boundary, production-negative contract tests, normal `PERMISSION_MANAGE` guard, live `401`/bootstrap `200`/ordinary-role `403`/cross-tenant `404`, clean-session UI checks, bounded write probes, and cache timing are green; the nurse case is explicitly out of scope. | IN PROGRESS; owner: QA/security; acceptance: exhaustive clean-session direct API/UI read-write matrix and complete golden-journey proof. |

The last interrupted V140/V141 checkpoint therefore ended in a recoverable test-harness failure, not an unverified migration: the candidate jar and Docker image were rebuilt, the isolated backend was restarted, Flyway applied V144, the final Maven suite passed 159/159, `git diff --check` exited 0, and 8101/8100 remained healthy after the disposable 8102 performance stack was removed. The nurse-specific UI/API discrepancy is explicitly deferred/out of scope and is not included in the acceptance claim.

## Final verification checkpoint — 2026-08-15T10:35:25+01:00

- The final required backend suite completed `159/159` with zero failures, errors, or skips; Maven reported `BUILD SUCCESS` after `02:48`.
- The authoritative `bbc-full-e2e` stack was verified after the suite: UI `8100` HTTP 200, backend health `8101` HTTP 200 with `{"status":"UP"}`, and Flyway history tail `111|144|true` through `107|140|true`.
- The disposable performance resources were cleaned up explicitly; no `bbc-perf-*` containers remained and port `8102` was closed. `git diff --check` exited 0 with only LF/CRLF normalization warnings.
- Final disposition remains CONDITIONAL rather than full PASS: Gate 14 is still in progress, the production-like upgrade is blocked by the V77 checksum-lineage mismatch, and full reference-scale performance/resilience is partial. Nurse is deferred/out of scope and excluded from the acceptance claim.

## Final disposable performance continuation - 2026-08-15

- The disposable scale fixture reached 1,000 students, 50 performance classes, 100 staff, and 11,660 expected attendance sessions. Using the assigned teacher context, six sequences x three subjects x 25 students produced 450 grade rows/cells across 18 view/save groups, all HTTP 200; view p50/p95/p99 was `54.32/113.98/113.98 ms` and save was `123.41/231.88/231.88 ms`.
- The bounded read/preview follow-up recorded the slow attendance-analytics path explicitly: six requests timed out at the eight-second bound, then all six completed in the bounded 60-second rerun at p95/p99 `38326.70 ms`. Dashboard students p95/p99 was `4798.31 ms`; corrected collection quote p95/p99 was `72.61 ms`. The finance persona run stayed within its 90-second hard bound and recorded six successful quotes, six posted collections, exact-key replay, and balanced cashier close.
- Restart durability was verified with job `b2464e2b-0754-45f3-b8d1-ebcf37532ed9`: HTTP 200 queued, only the disposable 8102 backend restarted, health returned HTTP 200, Flyway validated 111 migrations at schema 144, and the job read back HTTP 200 as `COMPLETED_ERRORS` with 25/25 processed and blocked, zero item errors, archive available, and 25 `BLOCKED` items. This is durable job recovery under the expected missing-snapshot fixture blocker, not successful report-card content generation.
- The disposable role/harness issue was diagnosed at the legacy primary-role check (`AcademicController.java:244-247` and `PermissionService.java:40-50`), corrected by using the assigned teacher and restoring `principal` as disposable admin primary. No ordinary application role was broadened. The disposable containers, volumes, and network were then removed; 8102 refused connections while 8100/8101 and acceptance Flyway V144 remained healthy.
- Final release disposition remains CONDITIONAL, not full Definition-of-Done PASS: Gate 14 is still `IN PROGRESS`, the production-like upgrade remains `BLOCKED` at the V77 checksum-lineage fork, and full performance/resilience remains `IN PROGRESS` for full mutation percentiles, successful report-card content, and broader concurrency/resilience. The 50-class published-density subcheck is closed PASS in the latest continuation. Nurse remains deferred/out of scope.

## Terminal source and runtime verification - 2026-08-15T11:07:16+01:00

- The final backend `mvn test` against the candidate source completed `159` tests, `0` failures, `0` errors, `0` skipped; `BUILD SUCCESS`; total `02:57`; fresh Testcontainers validated/applied 111 migrations through V144, including fresh bootstrap `8/8` and finance preview/quote coverage.
- The packaged `backend/target/bbc-sms-0.1.0.jar` contains V140, V141, V142, V143, and V144 under `BOOT-INF/classes/db/migration/`.
- Post-suite acceptance verification remained green: UI `8100` HTTP 200, backend health `8101` HTTP 200 with `{"status":"UP"}`, acceptance Flyway tail `111|144|true` through `107|140|true`; port `8102` refused connections and no `bbc-perf-*` resources remained.
- `git diff --check` exited 0 with only LF/CRLF normalization warnings. This is the final executable verification checkpoint; the release remains CONDITIONAL because Gate 14, the V77 production-like upgrade, and full reference-scale performance/resilience are not complete. Nurse remains deferred/out of scope.

## Latest bounded scale continuation — 2026-08-15

- The first full-density timetable runner attempt returned HTTP `401` because the harness supplied `admin/Admin123!`; the disposable fixture's supported bootstrap credential is `admin/admin`. The corrected rerun authenticated and completed. This was a harness-input issue only; no application authorization or ordinary role was broadened.
- In the isolated disposable stack, 50/50 `PERF-001`–`PERF-050` classes received supported homeroom/access-control resolution, 50/50 rooms, and 1,500/1,500 HTTP-200 slots across five days × six periods. Version `d38f6995-d7b5-4bf2-b7e2-8a5feb399813` (`versionNo=6`) published HTTP 200 with `slotCount=1500`, `classCount=50`; master read-back returned 1,500 rows and 50 distinct classes.
- The global conflict endpoint returned 23 baseline conflicts only among `SEC-EN-F1-A`, `SEC-FR-4E-A`, and `SEC-FR-6E-A`; no `PERF-*` class was involved (`perfConflictCount=0`). The 50-class published-density subcheck is PASS. Full performance/resilience remains IN PROGRESS for mutation percentiles, successful report-card content, and broader concurrency/resilience. Exact evidence: `qa/e2e-runs/2026-08-14-full-school/final/full-timetable-density-evidence.md`.
- After read-back, the three disposable containers, two volumes, and `bbc-perf-audit-net` were removed by exact name; `8102` refused connections. Acceptance remained untouched and green: UI `8100` HTTP 200, backend `8101` HTTP 200, Flyway tail `111|144|true` through `107|140|true`.

## Latest final source, fixture boundary, and runtime verification — 2026-08-15T12:14+01:00

- The final candidate backend `mvn test` completed `164/164`, zero failures/errors/skips, `BUILD SUCCESS`, total `02:58`, finished `2026-08-15T12:13:55+01:00`. The new `E2eFixtureControllerContractTest` completed `5/5`, including production-only, production-plus-e2e, and disabled-property negatives.
- The final packaged jar is `backend/target/bbc-sms-0.1.0.jar`, SHA-256 `846CB841D228601D9D7EC25B4A6DB85A9A3064E9B902239EE65BA8C67A6D09CD`; it contains `E2eFixtureController.class` and migrations V140–V144 under `BOOT-INF/classes`.
- After the stricter-profile backend rebuild, acceptance `POST /api/e2e/fixtures/second-school` returned `401` without authentication; bootstrap admin login and idempotent `BBC-E2E-B` fixture read-back returned `200` with stable IDs. The prior clean-context ordinary-role write denial (`403 FORBIDDEN`) and original-school student isolation (`404 NOT_FOUND`) remain the exact cross-tenant denial evidence; tokens and passwords are omitted.
- Acceptance runtime remains green: UI `8100=200`, API `8101=200 {"status":"UP"}`, Flyway tail `111|144|true` through `107|140|true`, and both `BBC-E2E` and `BBC-E2E-B` are present. No `bbc-perf-*` containers, volumes, or networks remain; `8102` is connection-refused. `git diff --check` exits `0` with only LF/CRLF normalization warnings.
- Final disposition is still CONDITIONAL: Gate 14 is `IN PROGRESS` for exhaustive persona/UI/network/golden-journey coverage, production-like upgrade is `BLOCKED` at the V77 checksum-lineage fork, and full performance/resilience is `IN PROGRESS`. Nurse remains deferred/out of scope.

## Final delegation-source regression and runtime checkpoint - 2026-08-15T12:54+01:00

- The final candidate-source backend suite completed `170` tests, `0` failures, `0` errors, and `0` skipped; Maven reported `BUILD SUCCESS` at `2026-08-15T12:53:52+01:00`. Fresh Testcontainers validated/applied all 111 migrations through V144. This includes `AcademicAccessPolicyServiceTest` (11/11), `PermissionActionControllerContractTest` (13/13), and `E2eFixtureControllerContractTest` (5/5).
- The delegation guard remains narrow at `AcademicAccessController.java:21-22` (`@perm.staffOnly()` only); resource-aware manager/viewer decisions are enforced in `AcademicAccessPolicyService` and invoked by `AcademicAccessDelegationService`. The live matrix remains: admin create `201`; ordinary teacher/accountant `403 ACADEMIC_ACCESS_DELEGATE_DENIED`; ordinary parent `403 FORBIDDEN`; invalid class/subject `400 SUBJECT_NOT_ASSIGNED_TO_CLASS`; Aicha ordinary grade entry `403 POLICY_RULE_MISSING` before, `200` during, and `403 POLICY_RULE_MISSING` after revoke; expired capability source `NONE` with all target capabilities false; revoke `200`; canonical assignment unchanged.
- Final acceptance read-back is green: UI `8100=200`, API health `8101={"status":"UP"}`, Flyway tail `111|144|true` through `107|140|true`, packaged jar contains the delegation classes, test-only fixture controller, and V140-V144 migrations, `git diff --check` exited `0`, no disposable `bbc-perf-*` resources remain, and `8102` is connection-refused.
- Release disposition is unchanged and explicit: GJ-07 is PASS for the exercised delegation scope; Gate 14 remains `IN PROGRESS` for exhaustive clean-session UI/network/golden-journey coverage; production-like upgrade remains `BLOCKED` at the V77 checksum-lineage fork; full performance/resilience remains partial. Nurse remains deferred/out of scope.

## Latest Parent/registrar closure and final candidate verification - 2026-08-15T14:10+01:00

The remaining registrar optional class-lookup caveat was reproduced and classified on the isolated acceptance stack. Registrar `GET /api/setup/classes` returned HTTP `403 FORBIDDEN` because it is the academic-structure administration route and the registrar correctly lacks `ACADEMIC_STRUCTURE_VIEW`; the fixture already contained 9 classes. The forward-only fix added `GET /api/students/class-options`, guarded by the registrar's existing school-scoped `STUDENT_PROFILE_CREATE` plus `staffOnly`. The live result is registrar `200` with 9 options, while primary teacher `403 FORBIDDEN`; the original setup route remains `403` for both. This proves the fix is a narrow UI/API contract correction, not an ordinary-role authorization expansion.

The rebuilt 8100 UI was retested with fresh sequential login/logout sessions. Registrar `/students`, `/students/new`, and `/students/import-family` rendered the expected student screens and class options with zero new console errors. Parent `/parent` rendered 3 children with zero errors. Accountant `/staff` redirected to `/apps` and `/finance` rendered with zero errors. Bursar `/staff` redirected to `/apps` and `/finance/reports` rendered the contextual report headings with zero errors. The prior shared-tab/multi-persona browser batch timeout/reset is harness-only and excluded from evidence; nurse remains out of scope.

Parent's earlier API responses were also classified: `financeVisible=false` children legitimately returned `403 POLICY_RULE_MISSING` for fees, confidential health reads legitimately returned `403 POLICY_RULE_MISSING`, and the child without a published bulletin returned `404 NOT_FOUND`. Explicit optional-read error fallbacks in `frontend/src/app/features/parent/parent.ts` closed the UI discrepancy without changing backend policy.

Verification after the changes: focused frontend regression `6` files/`15` tests passed; registrar backend contract `14/14` passed; full candidate Maven suite `171/171` passed with zero failures/errors/skips at `2026-08-15T14:00:56+01:00`; candidate backend/frontend Docker images were rebuilt and recreated; `8101` health is HTTP 200 `{"status":"UP"}` and Flyway validated 111 migrations with schema V144 up to date. Gate 14 remains `IN PROGRESS` for the still-incomplete exhaustive reference-plan UI/network/golden-journey matrix. The production-like upgrade remains non-destructively `BLOCKED` at the documented V77 checksum-lineage fork, and performance/resilience remains partial. No 8096 evidence, nurse evidence, Flyway repair, or manual DB mutation is included.

## Post-continuation final regression — 2026-08-15T11:31:17+01:00

- The final candidate backend `mvn test` rerun completed `159/159`, zero failures/errors/skips, `BUILD SUCCESS`, total `02:43`; fresh Testcontainers applied and validated all 111 migrations through V144, including production-bootstrap `8/8` and finance preview/quote coverage.
- Post-suite read-back remains green: acceptance UI `8100` HTTP 200, backend `8101` HTTP 200, Flyway `111|144|true` through `107|140|true`, packaged jar V140–V144 present, `8102` refused connections, and `git diff --check` exited 0 with only existing LF/CRLF normalization warnings.
## Final disposable restore/backend audit — 2026-08-15

The disposable restore/backend continuation completed only on `bbc-perf-*`. Its Flyway read-back was `111|144|true`; the corrected density runner published `1,500/1,500` slots for `50/50` performance classes with `perfConflictCount=0`. Exact disposable containers, volumes, and network were removed; 8102 is connection-refused. Acceptance UI 8100 and backend 8101 remained HTTP 200, with acceptance Flyway through V144.

The non-destructive upgrade source archive is `C:\Users\joe tech\bbcomplex\output\local_backup_2026-08-10.dump`, SHA-256 `F7E443E88373EB494C4262C9CF17E07B2DEF783757E28C1BB47FF71B736643B`; read-only `pg_restore -l` reported PostgreSQL 16.14 custom format and 917 TOC entries. The candidate still stops at the exact V77 checksum-lineage fork, so production-like upgrade remains `BLOCKED`; Gate 14 and full performance/resilience remain `IN PROGRESS`. Nurse is deferred/out of scope.

## Latest missing-performance closure - 2026-08-15T14:39+01:00

The remaining report-card-content performance subcheck is now green on the
disposable stack. The first run reproduced an async authorization defect: with
3/3 grade packets accepted, 25/25 council inputs approved, and 25/25 snapshots
published, 25 batch items failed with `Authentification requise.` because the
worker did not inherit the creating request's `SecurityContext`. The narrow
service/worker context fix is covered by a focused `2/2` regression and was
rebuilt into the candidate backend.

Corrected disposable run `20260815133914` completed in 5.12 seconds within its
180-second bound: batch job `62c9cfb6-e02a-4e09-b398-501dd1f08333` reached
`COMPLETED` with 25/25 published, zero blocked/errors, item read-back
`PUBLISHED:25`, and archive HTTP 200 (`599462` bytes; 25 report-card PDFs and
2 companion PDFs). Student-options latency and the
`COMPLETED_WITH_BLOCKERS`/`NO_ACTIVE_FEE_PLAN` behavior are also quantitatively
recorded; valid charge generation and same-key replay are green. The
performance gate remains partial only for full mutation/cancellation
percentiles and broader concurrency/resilience evidence. The exact disposable
stack was removed and 8102 is closed.

This does not change the final release disposition: Gates 0-13 are PASS for
the exercised scope, Gate 14 remains IN PROGRESS, and the non-destructive V77
production-like upgrade remains BLOCKED. No nurse evidence is included.

## Final source regression and requirement disposition - 2026-08-15T14:51+01:00

The final-source backend Maven suite passed `173/173` with zero
failures/errors/skips and fresh Testcontainers validated/applied all 111
migrations through V144. The new asynchronous report-card security-context
regression ran green inside that suite. Final acceptance checks are health
HTTP 200 on 8101 with Flyway `111|144|true` through V140, candidate jar
contents present, `git diff --check` clean apart from line-ending warnings,
and no disposable `bbc-perf-*` resources or 8102 listener.

The complete authoritative-plan disposition is now recorded in
`qa/e2e-runs/2026-08-14-full-school/final/requirement-disposition.md`.
It explicitly records Gates 0-13 as PASS for exercised scope, Gate 14 as
IN PROGRESS, performance/resilience as IN PROGRESS only for full
mutation/cancellation percentiles and broader resilience, and the
non-destructive V77 production-like upgrade as BLOCKED. Nurse-specific
evidence remains excluded.

## Superseding concurrent mutation and final-source verification - 2026-08-15T15:32+01:00

The final rebuilt candidate was applied to the acceptance backend. `8101`
health returned HTTP 200, the application started normally, and Flyway
reported current schema version `144` with no pending migration. The final
source Maven suite completed `175` tests with zero failures, errors, or skips,
`BUILD SUCCESS`, at `2026-08-15T15:32:04+01:00`; fresh Testcontainers
validated/applied all 111 migrations through V144.

The disposable concurrent mutation run `20260815142418` is now green for its
bounded API scope after fixing two real pre-fix races: charge generation
duplicate-key/aborted-transaction 500s and cashier-session optimistic-lock
500s. The rebuilt run completed 6/6 charge generations, 6/6 collection posts,
6/6 quotes, 6/6 batch creates, and 6/6 cancellations with zero errors or
timeouts, exact-key replay without duplication, and a closed cashier session.
The post-run log scan found no unhandled exception. Restart/read-back was also
verified on disposable 8102 only; its expected missing-snapshot blocker is
recorded separately. See
`qa/e2e-runs/2026-08-14-full-school/final/performance-concurrent-mutation-20260815142418.md`.

This closes the newly executed bounded finance mutation and restart subchecks,
but does not overclaim the full plan: Gate 14 remains `IN PROGRESS` because
the exhaustive clean-session persona/read-write/network matrix is not complete
(shared-storage multi-tab evidence is excluded), the reference-scale
performance requirement remains partial for UI-scale/broader resilience
coverage, and the non-destructive production-like upgrade remains `BLOCKED`
at the V77 checksum-lineage fork. Nurse remains deferred and is not included.

### Final disposable cleanup read-back - 2026-08-15T15:35+01:00

The disposable performance stack was cleaned up by exact name after evidence
capture: `bbc-perf-backend-audit`, `bbc-perf-mailpit-audit`,
`bbc-perf-db-audit`, the inspected disposable volumes, and
`bbc-perf-audit-net`. No disposable resource remains and 8102 is unavailable.
The acceptance stack was not touched: UI 8100 and API 8101 returned HTTP 200,
Mailpit 8125 remained healthy, and read-only Flyway history remained
`111|144|true` through `107|140|true`. `git diff --check` exited 0 with only
line-ending normalization warnings.

The release decision remains CONDITIONAL, not full Definition-of-Done PASS:
bounded finance mutation/restart evidence is closed for its exercised scope;
Gate 14 is still IN PROGRESS, broader UI-scale/performance resilience is still
IN PROGRESS, and the non-destructive V77 upgrade remains BLOCKED. Nurse is
deferred and excluded.

## Superseding Gate 14 negative expansion and final source verification - 2026-08-15T17:01:35+01:00

The candidate-only high-value permission harness ran against `bbc-full-e2e` API 8101 and returned `PASS`: 19 checks, 17 expected HTTP 403 denials, two server-filtered cross-class roster reads at HTTP 200/count 0, and zero unexpected results. It covered the still-missing teacher student-profile/withdraw/transfer/guardian boundaries, secondary grade/timetable/attendance scope, accountant setup/session/attendance, and parent staff/finance/student/bulletin enumeration. Nurse and the legacy 8096 stack were excluded.

The pre-term enrollment-history defect was corrected in `EnrollmentService.history()`: the policy context now uses the session-bounded effective date rather than `LocalDate.now()`. The focused regression passed `2/2`; live registrar history returned 200, and the primary-teacher withdrawal probe returned 403 `POLICY_RULE_MISSING`. Supported fixture cleanup returned `WITHDRAWN`, policy restore 200, soft student delete 204, and the final read-only active `G14-NEG-*` count was 0.

The source-triggered full backend Maven suite passed `176/176` with zero failures/errors/skips at 2026-08-15T17:00:30+01:00. The backend image build completed 19/19, the jar contains V140-V144, UI 8100 and API health 8101 are HTTP 200, Flyway is `144|true`, and `git diff --check` exits 0. The precise disposition remains conditional: Gates 0-13 and the measured Gate 14/API slice are green for exercised scope; exhaustive Gate 14 UI/persona/network/golden/state coverage and broader performance/resilience remain in progress; V77 is non-destructively blocked; nurse is deferred.

## Authoritative-plan completion audit addendum - 2026-08-15T15:45+01:00

A final audit against sections 24, 26, 27, 28, 29, 31, 32, and 33 found no
new functional P0/P1 defect, but it does not support a full PASS claim. The
candidate has green Gates 0-13 for exercised scope, 175/175 backend tests,
fresh V144 migration evidence, bounded concurrency/restart evidence, and
isolated acceptance runtime checks.

The exact remaining requirements are now recorded in
`qa/e2e-runs/2026-08-14-full-school/final/requirement-audit-20260815.md`:
Gate 14 still lacks the complete per-route/persona/read-write/console/network
and golden-journey inventory; a bounded admin-session sweep recorded 32/32
zero-console-diagnostic routes in `final/route-console-diagnostics.md`, but
the route index is not an exhaustive per-persona/state detail table; the 22 module pages now
have prose coverage plus a committed partial screenshot pack; broader
UI-scale/performance resilience is partial; and the
production-like upgrade is blocked by the V77 lineage fork. The primary
handoff and both divergent archives were verified read-only; no migration
repair, manual database mutation, unsupported tenant provisioning, shared-tab
evidence, or nurse evidence was used.

## Addendum: final UI action-boundary retest - 2026-08-15T16:31:25+01:00

The final candidate frontend rebuild/recreate completed successfully after the
route/action regression was added. The focused Angular run passed 3 files and
8 tests. On the isolated stack, UI `8100` and backend health `8101` returned
HTTP 200; the backend suite was not rerun because no backend source changed
after the already-green 175-test result.

With explicit logout/login boundaries in one browser tab and a cache-busting
query, Bursar `econome.a` was redirected to `/apps` for `/students/new` and
`/students/import-family`, each with zero console diagnostics. Cashier
`caissier.a` remained on `/finance/plans`; the heading rendered and both
`Nouveau brouillon` and `Create the first draft` were disabled, also with zero
console diagnostics. This matches the HTTP-200 capability read-back
`FEE_PLAN_DRAFT=DENY`. The earlier enabled-control observation was stale bundle
cache and is not authoritative.

This action-boundary subcheck is PASS for exercised scope. Overall completion
remains conditional: Gate 14's exhaustive clean-session matrix, route/state
documentation, and broader performance/resilience remain incomplete; the
production-like upgrade remains non-destructively blocked at V77. Nurse is
explicitly excluded.

## Addendum: secondary teacher API/UI discrepancy closure and final suite - 2026-08-15T17:36:07+01:00

The isolated candidate retest found and closed two real, narrow defects. The
secondary-teacher `/api/students` request had been returning HTTP 500 because
PostgreSQL rejects the old `SELECT DISTINCT` query's `ORDER BY` expressions;
`StudentService.teacherList` now uses an inner distinct projection with an
ordered outer projection. Separately, `/events` was admitted by a legacy module
guard even though the server capability read-back denied `EVENTS_VIEW`; the
route now uses `actionGuard('EVENTS_VIEW')`. The focused backend integration and
frontend guard regressions passed.

After rebuilding/restarting only `bbc-full-e2e`, a fresh explicit-login
`Secondaire`/`Francophone` session returned `/api/students` HTTP 200, retained
the intentional `/api/events` HTTP 403 `FORBIDDEN`, kept `/students` on route,
and redirected `/events` to `/apps`; both UI routes had zero new console
diagnostics. A bounded 33-route sweep used one fresh browser storage context,
explicit login boundaries, per-route timeouts, and asynchronous guard-settle
allowance. It is a corrected measured slice, not exhaustive Gate 14 evidence.

The required final backend suite then passed `177/177` with zero failures,
errors, or skips and `BUILD SUCCESS` at `2026-08-15T17:36:07+01:00`; fresh
Testcontainers validated/applied all 111 migrations through V144. Overall
completion remains conditional: Gate 14 exhaustive persona/read-write/
network/golden/state evidence, module-specific visual states, and broader
performance/resilience remain incomplete; V77 upgrade remains non-destructively
blocked by checksum lineage; unsupported tenant/prod actions remain blocked;
and nurse remains deferred/excluded.

The final runtime/artifact checks at this candidate state were also green:
8100 returned HTTP 200; 8101 health returned HTTP 200 with `{"status":"UP"}`;
the acceptance database reported Flyway `144|true`; the packaged jar contained
V140 through V144; `git diff --check` exited 0 with only existing line-ending
warnings; and no disposable `bbc-perf-*`/8102/8103 resources remained.

## Addendum: fresh browser state examples - 2026-08-15T18:04:10+01:00

The isolated browser evidence was extended with two fresh explicit-login
examples. François Mbarga in `Primaire`/`Francophone` was denied the direct
`/settings` navigation and redirected to `/apps`. Cashier `caissier.a` in
`Tous les parcours` could read `/finance/plans`, but the draft/editor/create
controls were disabled. The screenshots and exact disposition are in
`qa/e2e-runs/2026-08-14-full-school/final/screenshot-pack.md` and
`qa/e2e-runs/2026-08-14-full-school/final/module-state-capture-matrix.md`.

This narrows the visual-state gap but does not close the authoritative
module-by-module six-state, mobile, loading/error, or exhaustive Gate 14
persona matrix. Gate 14 remains `IN PROGRESS`; the nurse case remains
deferred/excluded, shared-tab evidence remains invalid, and the non-destructive
V77 upgrade remains blocked at its checksum-lineage fork.

## Final acceptance consistency disposition - 2026-08-15T18:04:10+01:00

The authoritative requirement audit is current in
`qa/e2e-runs/2026-08-14-full-school/final/requirement-audit-20260815.md`.
Gates 0-13 and the measured API/action regressions remain green for exercised
scope. The bounded disposable scale/performance run, fresh browser denied and
locked examples, final acceptance health/Flyway read-back, packaged migration
check, diff check, and disposable cleanup are all recorded. Gate 14's
exhaustive persona/read-write/network/state/golden matrix and UI-scale/broader
resilience remain `IN PROGRESS`; V77 remains non-destructively `BLOCKED`; the
nurse case and unsupported tenant/prod operations remain excluded. Overall
status is `CONDITIONAL`, not complete.

## Addendum: clean-session persona and responsive evidence - 2026-08-15T18:24:48+01:00

The final evidence now includes a bounded nine-persona route/read/redirect
slice and four responsive representative journeys at `390x844`. Primary
attendance and grade entry, Cashier collection, and Parent portal all rendered
without horizontal overflow and with zero captured console warnings/errors.
The clean Direction student-list recheck rendered 28 rows, matching the API;
the earlier zero-row observation was a stale/under-settled browser harness
artifact and did not justify a source change.

This strengthens but does not close the authoritative Gate 14 requirement:
full UI writes, direct resource-scope cases, denied-write no-mutation,
policy-cache timing, complete network/console inventory, every-module state
matrix, and browser golden journeys remain open. Overall status remains
`CONDITIONAL`; V77, unsupported tenant/prod operations, and nurse remain
blocked/excluded as previously documented.

## Addendum: optional-read UI/API discrepancy closure - 2026-08-15T18:51:06+01:00

The isolated 8101 API diagnosis found expected Direction 403 responses for
ClassKit setup classes/subjects, Alerts, and the optional student-detail
guardian/photo reads, while the required student profile returned 200. The
remaining browser `$n` errors were caused by missing frontend error handlers,
not by a need to broaden ordinary-role permissions.

The candidate frontend now renders explicit unavailable states for denied
ClassKit and Alerts reads and contains the denied student guardian read. The
three focused regressions passed `3/3`; Angular production build passed; and
the rebuilt 8100 image was recreated. A fresh explicit Direction session
verified `/classkit`, `/alerts`, and Amina's student detail with the expected
states and zero new console errors on each route. The exact request/status
matrix is in `qa/e2e-runs/2026-08-14-full-school/13-permission-sweep/evidence.md`.

This closes the identified optional-read UI/API defects for the exercised scope.
Gate 14 remains `IN PROGRESS` for the authoritative exhaustive UI/write/
resource-scope/network/golden/state inventory; V77 remains non-destructively
blocked; unsupported tenant/prod actions remain excluded; and the nurse case
remains deferred and excluded.

## Latest Gate 14 status — parent-route boundary — 2026-08-15T19:26:31+01:00

The parent-route UI/API discrepancy is now closed for the measured scope. A
fresh Accountant API login returned `403 FORBIDDEN` for
`/parent/children`, `/parent/payment-channels`, and `/parent/suggestions`, but
the unguarded UI route still rendered an empty parent shell. The narrow
frontend correction adds `parentGuard` to `/parent`, with `9/9` focused guard
tests green. The frontend image was rebuilt from the candidate worktree and
only the 8100 frontend container was recreated.

Fresh post-build route evidence is complete for Cashier, Bursar, and FAM-A
Parent (`32/32` each, zero timeouts/errors/warnings), plus targeted `/parent`
checks for Direction, registrar, nursery, both Secondary personas, and
Accountant. All staff roles redirected to `/apps`; FAM-A stayed on `/parent`.
Gate 14 remains `IN PROGRESS` because the plan still requires the broader
per-persona write/resource-scope/denied-write/no-mutation/cache/network,
golden-journey, and module-state/mobile matrix. V77 remains a non-destructive
checksum-lineage blocker; performance remains partial; unsupported
tenant/prod actions and nurse are excluded.

### Gate 14 measured finance/parent UI boundary slice - 2026-08-15T19:46:02+01:00

The fresh explicit-session slice is recorded in
`qa/e2e-runs/2026-08-14-full-school/final/gate14-ui-boundary-slice-20260815.md`.
Bursar Finance Plans was available while student-create and Parent routes
redirected; Cashier Collections was available, Finance Plans creation controls
were disabled, and student-create/Parent routes redirected; FAM-A Parent
rendered three linked children while direct student/staff/finance routes
redirected. Every checked route had zero browser diagnostics. No completed UI
write or database mutation occurred, and the browser tab was logged out and
finalized. Gate 14 remains `IN PROGRESS` for the exhaustive matrix; this slice
does not change the V77 blocker, performance partial status, or nurse exclusion.

### Gate 14 permission-policy frontend regression - 2026-08-15T19:51:42+01:00

The new `frontend/src/app/features/settings/access-control-workspace-scope-ui.spec.ts`
passed `3/3` in the focused Angular run. It covers non-mutating safe-template
preview, reason/explicit high-risk confirmation gating, and stale-policy preview
error recovery. This closes the component-test gap for the permission workspace;
the exhaustive Gate 14 browser/state matrix remains open.

The section-32 deliverable audit is now consolidated in
`qa/e2e-runs/2026-08-14-full-school/final/deliverable-completion-matrix.md`.
It maps all 15 required artifacts and preserves the conditional dispositions:
Gate 14, complete visual/mobile coverage, and broader performance remain open;
the production-like V77 upgrade remains blocked without an approved compatible
lineage; and nurse remains excluded.

The complete frontend suite then passed `23` test files / `51` tests with zero
failures. The Angular NG8102 warning at `access-control-workspace.ts:131` is
non-fatal and pre-existing; no production behavior or acceptance database was
changed by this test-only addition.

## Fresh Gate 14 persona route-boundary extension - 2026-08-15T20:08:52+01:00

The new evidence file
`qa/e2e-runs/2026-08-14-full-school/final/gate14-fresh-persona-route-boundaries-20260815.md`
records a fresh one-tab sequential UI run on 8100. Bursar and Cashier finance
boundaries, Registrar student/import routes, Maternelle teacher academic/
attendance/coursebook/timetable routes, and Secondary/Francophone teacher
academic/attendance/coursebook/timetable/ClassKit routes were exercised. The
corresponding denied or redirected routes, zero browser diagnostics, actual
parcours selections, and the absence of completed UI writes are recorded.

The first stale Bursar credential and the initial generic Primary parcours
selector were corrected as harness inputs and are not application defects.
This is `PASS` for the measured route/read/UI-boundary slice only. Gate 14 is
still `IN PROGRESS` for exhaustive writes, resource-scope/no-mutation proof,
policy-cache timing, complete network/console inventory, golden journeys, and
module state/mobile coverage. V77 remains non-destructively blocked and nurse
remains excluded.

## Independent calculation workbook - 2026-08-15

The section-32 calculation-workbook deliverable is now available at
`outputs/019ffffe-5397-7a32-842e-2134a9e52c2a/BBC_SMS_CALCULATION_RECONCILIATION.xlsx`.
It contains six rendered sheets with formula-driven observed-versus-expected
academic, finance, operations, parent-safe, and aggregate checks. All controls
are `PASS`, and the exported formula-error scan matched zero cells. This closes
the calculation-workbook artifact for the measured scope only; the overall
release remains conditional because Gate 14, visual/mobile coverage,
broader performance/resilience, and V77 remain open or blocked.

## Representative visual error-state captures - 2026-08-15T20:25:36+01:00

Fresh Direction/8100 evidence now includes visible ClassKit-unavailable,
Alerts-unavailable, and Student-detail policy-denied/empty-family states at
`qa/e2e-runs/2026-08-14-full-school/final/screenshots/`. All three routes had
zero browser console logs, and the session was explicitly logged out/finalized.
This narrows the visual documentation gap but does not close the exhaustive
module six-state/mobile matrix or Gate 14.

## Registrar permitted UI write/read-back - 2026-08-15T20:33:42+01:00

Fresh isolated 8100 UI evidence now includes a real Registrar registration
write for synthetic `UI-REG-20260815-01 Student`, matricule `BBC-1037`, placed
in `PRI-FR-CE1-A`, with a `NO_PORTAL` guardian. The UI displayed
`Inscription terminée`; the detail read-back route contained UUID
`6d6e30dc-62a2-440b-8773-7abd253f998b` and the API read-back was HTTP 200.
Registrar delete correctly returned HTTP 403 `POLICY_RULE_MISSING`; supported
scoped bootstrap-admin preview/apply, HTTP 204 cleanup, original policy
restore, and active-roster zero-match read-back are recorded in the linked
evidence file. The browser had zero console logs and was explicitly logged
out/finalized. This is one measured Gate 14 write slice, not exhaustive Gate
14 closure. Nurse remains excluded; V77 remains non-destructively blocked.

## Coursebook teacher UI/API correction and write - 2026-08-15T20:52:35+01:00

The fresh Aïcha Mvondo Coursebook retest found and closed a real UI/API
mismatch: `/coursebook` was visible but its academic-setup class/subject
lookups returned HTTP 403, leaving no selector options while scoped Coursebook
reads were allowed. The candidate now exposes scoped Coursebook class/subject
lookups with teacher/parcours and `COURSEBOOK_VIEW` enforcement, and the UI
uses them without broadening academic setup authority.

Focused frontend coverage `1/1`, backend controller contract `14/14`, Docker
build/redeploy, and Flyway V144 startup were green. Fresh UI showed
`MAT-FR-MS-A` and four subjects, created/read back a synthetic Coursebook
entry, deleted it through the UI, and returned to one original entry. API
read-back was HTTP 200 with zero synthetic matches; browser logs were zero and
the session was logged out/finalized. This closes the Coursebook discrepancy
and one teacher write slice only; Gate 14, broader performance/resilience, and
non-destructive V77 upgrade remain open/blocked. Nurse remains excluded.

## Final-source verification and current disposition - 2026-08-15T21:01:44+01:00

The final candidate source was rechecked after the Coursebook correction:

- Backend `mvn test`: **177/177** green, zero failures/errors/skips, `BUILD SUCCESS`; fresh Testcontainers applied 111 migrations through V144.
- Frontend `pnpm exec ng test --watch=false`: **24 test files / 52 tests** green, exit 0.
- `bbc-full-e2e`: UI 8100 HTTP 200; backend health 8101 HTTP 200 with `{"status":"UP"}`; live Flyway count 111 with V140-V144 successful.
- `git diff --check`: exit 0 with only existing LF/CRLF warnings; no `bbc-perf-*` container or 8102/8103 listener remains.

The system is conditionally ready for functional review, not a final production-release PASS. Gate 14 remains `IN PROGRESS` for exhaustive clean-context persona/UI read-write/resource-scope, denied-write/no-mutation, network/console, golden-journey, and module state/mobile evidence. Broader performance/resilience is partial, and the V77 production-like upgrade is blocked non-destructively by the recorded checksum-lineage mismatch and absent approved compatible fixture. Cross-tenant limitations are explicit; nurse is excluded by request.

## Direction academic read-envelope correction - 2026-08-15T21:20:40+01:00

The isolated 8100/8101 run reproduced and closed a Direction academic
selector mismatch. In the all-parcours UI, `GET /api/setup/classes` and
`GET /api/setup/subjects` were previously HTTP 403 before the V2 service
decision, leaving `/academic` with no classes. `SetupController` now uses a
staff-only envelope for reads while retaining the parcours-plus-staff envelope
for writes; `SetupService` still enforces `ACADEMIC_STRUCTURE_VIEW` and
resource scope. No ordinary role authority, migration, or database row was
changed.

`PermissionActionControllerContractTest` passed `15/15`; the candidate images
rebuilt and `bbc-full-e2e` restarted with health HTTP 200 and Flyway V144.
Direction reads are HTTP 200 with 9 classes and 30 subjects, while the
ordinary secondary teacher remains HTTP 403 `POLICY_RULE_MISSING`. A fresh
explicit browser logout/login and all-parcours selection showed all nine class
options; selecting `SEC-FR-4E-A` rendered the class-scoped empty read state and
browser diagnostics were zero. The unsupported `getDevLogs`/`inputValue`
calls were harness-only and recovered via supported diagnostics/DOM calls.

This closes the reproduced defect for exercised scope. The overall plan is
still conditional: Gate 14's exhaustive persona/resource/no-mutation/network/
state/mobile matrix, broader performance/resilience, and the non-destructive
V77 upgrade blocker remain outstanding; nurse is out of scope.

## Final-source verification and current disposition - 2026-08-15T21:31:53+01:00

- Backend full suite: 178/178 green, zero failures/errors/skips, BUILD
  SUCCESS at 2026-08-15T21:30:04+01:00; fresh Testcontainers reached Flyway
  V144 after 111 migrations.
- Frontend full suite: 24 test files and 52/52 tests green, exit 0. The
  existing Angular NG8102 warning at
  frontend/src/app/features/settings/access-control-workspace.ts:131 is
  non-fatal and did not fail the suite.
- Acceptance runtime: 8100 HTTP 200; 8101 health HTTP 200 with status UP;
  live Flyway count 111, latest V144, all successful.
- Hygiene/isolation: git diff --check exit 0 with only LF/CRLF warnings; no
  disposable bbc-perf resources and no 8102/8103 listeners.

This is final-source verification, not a full-plan completion claim. Gates
0-13 remain PASS for exercised scope. Gate 14 exhaustive clean-context
persona/read-write/resource/no-mutation/network/console/golden/state/mobile
coverage remains IN PROGRESS; broader performance/resilience remains partial;
the non-destructive V77 upgrade remains BLOCKED by checksum lineage and the
absence of an approved compatible backup; cross-tenant limitations remain
explicit; nurse remains excluded.

## Current source/runtime checkpoint - 2026-08-15T22:16:44+01:00

The candidate now has final post-rebuild evidence for the measured Gate 14
route/mobile/UTF-8 slice in
`qa/e2e-runs/2026-08-14-full-school/final/gate14-sequential-persona-route-mobile-20260815.md`.
Nine fresh explicit persona sessions each completed `32/32` route probes with
no detected 404 and zero new browser diagnostics. Mobile representative routes
at `390x844` had no horizontal overflow. The UTF-8 API script passed and the
rebuilt Cashier Collections UI reported 13 real `é` characters with zero
`Ã`/`Â`/replacement codepoints.

Final automated/runtime checks remain green: backend `mvn test` 178/178,
frontend 52/52, acceptance 8100/8101 HTTP 200 with live Flyway V144, and no
disposable performance resources. This is not a full-plan completion claim:
Gate 14's exhaustive write/resource/no-mutation/network/state/golden matrix is
still `IN PROGRESS`, performance/resilience is partial, and V77 is blocked
non-destructively by checksum lineage and lack of an approved compatible
backup. Nurse and the old 8096 stack remain excluded.

## V145 bootstrap student setup correction - 2026-08-15T22:47:17+01:00

The last reproduced Gate 14 setup blocker was resolved narrowly. A fresh
bootstrap admin previously had DENY capabilities for `STUDENT_PROFILE_CREATE`
and `STUDENT_IMPORT`; `POST /api/students` returned HTTP 403 `FORBIDDEN`, and
the two UI routes redirected away from their forms. The forward-only V145
migration grants only those two actions to the existing emergency bootstrap
user rule. It does not alter ordinary role templates or nurse scope.

The focused tests passed `9/9`; the packaged jar includes V145; the rebuilt
candidate backend applied V145; and the live Flyway row is `145|t`. Fresh
explicit logout/login browser verification kept `/students/new` and
`/students/import-family` on their requested URLs with forms rendered. Invalid
non-mutating payloads returned HTTP 400 validation responses. The live role
template query still has these actions only for `registrar`.

The final backend suite passed `179/179`, zero failures/errors/skips, BUILD
SUCCESS. The acceptance stack is healthy at 8100/8101, Mailpit is at 8125, and
no disposable performance resources use 8102/8103. This closes the specific
bootstrap student setup slice. It does not close the full plan: Gate 14
remains `IN PROGRESS` for exhaustive persona/resource/no-mutation,
network/console, cache, golden-journey, and module state/mobile coverage;
broader performance/resilience is partial; V77 is non-destructively BLOCKED by
checksum lineage and the missing approved compatible backup; cross-tenant
limits remain explicit; nurse remains excluded.

## Teacher academic scope UI/API correction - 2026-08-15T23:09:00+01:00

The next missing executable Gate 14 check reproduced a real teacher UI/API
mismatch. A Secondary `form_teacher` was correctly denied the administrative
`/api/setup/classes` and `/api/setup/subjects` reads with HTTP 403, but the
academic UI was calling those endpoints instead of the authorized
`/api/academic/me/scope` endpoint, which returned 9 subjects across 3 assigned
classes.

The frontend correction is in `frontend/src/app/features/academic/academic.ts`
and is covered by `frontend/src/app/features/teacher-scope-ui.spec.ts`. The
focused test passed `3/3`; the post-change full frontend suite passed `24 test
files / 53 tests`; the candidate frontend image rebuilt from the candidate
context; and the isolated 8100 service was recreated.

Fresh explicit logout/login browser evidence shows all three assigned class
options. Selecting `SEC-EN-F1-A` loads four subjects and a 3-student S1 grade
sheet, with zero browser diagnostics. The temporary persona sessions were
logged out and finalized. This closes the reproduced teacher academic
selector defect for exercised scope without broadening ordinary authorization.

The plan is still conditional, not complete: Gate 14's exhaustive
write/resource/no-mutation/cache/network/console/golden/module matrix remains
`IN PROGRESS`; performance/resilience remains partial; V77 is a documented
non-destructive blocker; cross-tenant limitations remain explicit; and nurse
remains excluded.

## Bounded finance performance correction - 2026-08-15T22:22:00+01:00

The remaining finance percentile discrepancy was isolated to the disposable
fixture and harness. The prior `COMPLETED_WITH_BLOCKERS` result had no active
fee plan, and the runner used bootstrap admin for a finance-only options call.
The application returned the expected `403 POLICY_RULE_MISSING`; the runner's
list assumption then caused a harness `AttributeError`.

After correcting the runner and creating an active disposable `PERF-001` plan
with six enrollments, the bounded six-sample run completed within 90 seconds:
charge generation `6/6 COMPLETED` with zero blockers/failures, collection quote
`6/6 HTTP 200`, collection post `6/6 POSTED`, and report-card batch create
`6/6 QUEUED`, all with zero measured request errors/timeouts. Exact-key replay
returned the original receipt and cashier close was `CLOSED`. The full evidence
is `final/performance-finance-active-plan-20260815.md`.

This closes the under-fixtured finance API percentile subcheck. The plan is
still conditional because UI-scale responsiveness/progress/cancellation,
broader resilience/permutation coverage, exhaustive Gate 14 coverage, and the
non-destructive V77 upgrade remain open or blocked. Nurse and the 8096 stack
remain excluded.
## Read-only invariant audit continuation - 2026-08-15T23:35:41+01:00

The isolated acceptance database passed the new reproducible read-only
invariant audit: `run-readonly-invariant-audit.py` returned exit `0`, `19/19`
checks PASS, and zero violations. It covers the reference-plan session,
enrollment, guardian, curriculum, responsible assignment, delegation,
timetable, attendance, assessment, snapshot-structure, finance allocation,
sequence, journal, accounting-period, promotion, audit/Journey, and
source-event invariants. Acceptance Flyway remained `145|true`; no SQL mutation,
Flyway repair, checksum edit, or production action was used. The two harness
interpretation corrections and the temporal-property limitation are recorded
in `qa/e2e-runs/2026-08-14-full-school/final/readonly-invariant-audit-20260815.md`.

## Explicit golden-journey matrix - 2026-08-16

`qa/e2e-runs/2026-08-14-full-school/final/golden-journey-matrix-20260816.md`
now maps every step of GJ-01 through GJ-07 to the exact Gate 4-13 evidence,
fixture identifiers, HTTP results, and read-only reconciliation. The seven
journeys are `PASS` for the exercised fixture scope, including the annual
promotion preview/override/commit/replay and dated-delegation no-authority-
drift proof. This closes the explicit journey-documentation gap only; it does
not claim the exhaustive Gate 14 persona/action/resource/state/mobile matrix.

The current candidate runtime remains V145 (`145|true`), with 8100/8101
healthy, Mailpit on 8125, and no disposable 8102 resources. The release
disposition remains `CONDITIONAL, not complete`: exhaustive Gate 14 and
broader performance/resilience are still open, the non-destructive V77
production-like upgrade is blocked by checksum lineage, cross-tenant limits
remain explicit, and nurse is excluded.

## Gate 14 cross-product scope/no-mutation update - 2026-08-15T22:57:50Z

The candidate-only harness
`qa/e2e-runs/2026-08-14-full-school/13-permission-sweep/run-gate14-scope-cross-product-20260816.py`
completed `71/71 PASS` across the nine staff/parent contexts in scope,
including FAM-C's finance-visible parent context. It adds assigned/same-class/
other-parcours/unrelated-child resource probes, server-side zero-row filtering,
direct denied writes, and a before/after hash proving the denied teacher writes
did not mutate student, enrollment, or guardian state. Exact outcomes are in
`final/gate14-scope-cross-product-20260816.md`.

This closes the expanded API scope slice only. The overall plan remains
conditional because exhaustive clean-browser write/state/mobile/network
coverage and broader performance/resilience are still incomplete, while V77
remains a non-destructive upgrade blocker. Nurse remains excluded.

## Gate 14 fresh-session UI boundary update - 2026-08-16

`qa/e2e-runs/2026-08-14-full-school/final/gate14-fresh-ui-boundaries-20260816.md`
records a bounded fresh-session UI slice for ten non-nurse personas. The
browser used explicit logout before each subsequent login and explicit
parcours/context selection, so shared-tab/localStorage state was not treated
as independent persona evidence. The slice is green for the observed route
boundaries, secondary teacher grade-entry read-back, finance-role control
states, and FAM-A/FAM-C parent finance visibility, with zero browser
diagnostics. Gate 14 remains open for exhaustive module/action/state/mobile/
network coverage; nurse remains deferred and excluded.

## Gate 14 clean-session route-index update - 2026-08-16

`qa/e2e-runs/2026-08-14-full-school/final/gate14-clean-route-index-20260816.md`
records all 35 Section 31 routes for ten non-nurse personas using explicit
logout/login boundaries and parcours selection. Every route row completed
without probe exceptions or captured browser warning/error diagnostics. A
settled FAM-A retest classified the initial blank-at-160-ms rows as harness
timing and confirmed the expected `/apps` redirects. The stale-tab/
unsupported-evaluation attempt and bootstrap-admin context-selection attempt
are explicitly excluded.

This closes route-index accounting and clean-session redirect evidence only;
the full Gate 14 action/resource/state/mobile/network/golden matrix remains
open. Nurse remains deferred and excluded.

## Bounded UI responsiveness/performance update - 2026-08-16

The new read-only UI probe in
`qa/e2e-runs/2026-08-14-full-school/final/performance-ui-readonly-20260816.md`
completed five samples for six representative routes in 28.686 seconds, with
zero browser diagnostics and conservative p95/p99 settled-display values of
688–935 ms. The `/students` early blank frame was independently settled and
classified as bootstrap timing, not a route failure. This closes a normal-
fixture UI slice only; realistic-scale UI, progress/cancellation, and broader
resilience/permutation requirements remain open.

## Gate 14 live policy-preview safety update - 2026-08-16

The fresh Direction `/access-control` session previewed the safe Direction
template with policy version `9083`, zero changes, three affected users, 66
preserved exceptions, risk warnings, and visible reason/confirmation gating.
The apply control stayed disabled and no policy mutation was sent; browser
diagnostics were zero. Evidence:
`qa/e2e-runs/2026-08-14-full-school/final/gate14-policy-live-preview-20260816.md`.
This is a measured Permission Policy V2 slice, not full Gate 14 closure.

## Reusable browser E2E core and optional-read corrections - 2026-08-16

The candidate now has a committed Section 28.3 browser runner at
`qa/browser-e2e/run-golden-journeys.mjs`. It launches real Chromium with a new
browser context/storage state for each of the seven required non-nurse personas:
bootstrap admin, Primary teacher, Secondary subject teacher, Secondary titular,
accountant, cashier, and parent. The runner creates a unique student/family
fixture through supported APIs and cleans it through supported relationship,
guardian, and student APIs, restoring its temporary class-scoped cleanup policy.

Final run `BROWSER-1786838801645-3ddba7` passed `7/7` personas and `14/14`
positive/forbidden route outcomes. The parent read-back asserted
`linkedChildText=true` for `E2E-6B764331 Browser`. Cleanup returned relationship
`204`, guardian deactivation `200`, student soft-delete `204`, and policy restore
`200`; a read-only database check found zero active `E2E-*` browser fixtures and
zero active relationships. Evidence is in
`qa/e2e-runs/2026-08-14-full-school/final/gate14-browser-e2e-20260816.md` and
the adjacent JSON.

The runner exposed and the candidate closed two UI/API issues. First, the
accountant `/finance` screen called admin-only `GET /api/setup/classes` and
received `403`; `FinanceComponent` now uses finance-scoped
`GET /api/finance/v2/charges/context`. Its focused regression is `1/1`, and the
final frontend suite is `25 files / 55 tests` green after the 8100 image rebuild.
Second, the parent shell unconditionally called `GET /api/settings/school` and
received `403`; it now checks the authoritative `SCHOOL_PROFILE_VIEW` capability
before loading the staff/settings resource. The focused parent regression is
`3/3`. The disposable student has no published bulletin, so
`GET /api/parent/children/{id}/bulletins/latest → 404` is an expected optional
not-yet-published response, explicitly classified and contained by the UI.

External Google-font 404/request-failed events and Mailpit credential lookup
are recorded separately as fixture/asset noise, not application authorization
failures. Two earlier interrupted active browser fixtures were cleaned through
supported APIs; no direct DB mutation or Flyway repair was used.

This closes the reusable browser/session/route/fixture core slice. It does not
close the full plan: Gate 14's complete per-route × persona action/read-write,
resource/no-mutation, state/error/mobile/network, and stable golden-journey UI
write/read-back coverage remains `IN PROGRESS`; broader performance/resilience
remains partial; the V77 production-like upgrade remains non-destructively
`BLOCKED` by checksum lineage and lack of an approved compatible backup; and
nurse remains excluded at the user's direction.

## Eight-persona classified browser route-index update - 2026-08-16

The reusable browser runner now includes the bursar persona and still uses
separate browser storage per persona. Core run
`BROWSER-1786840135157-8baff9` passed `8/8` personas and `16/16`
positive/forbidden route outcomes. The 35-route index recorded `280/280`
observations. Protected optional role-boundary calls and known optional asset /
context responses are retained as exact classifications; affected-persona
follow-up runs reached zero blocking diagnostics. This closes the previously
missing accountant/bursar/parent route-disposition evidence, but the full
plan-level visual-state, mobile, and stable golden write/read-back matrix
remains open.

## Fresh module-state, responsive, and persona-boundary capture - 2026-08-16

The isolated `8100` browser completed a fresh bootstrap-admin capture of all
34 stable authenticated module routes at desktop size and at `390x844`. The
mobile artifact records `34/34` rows with `scrollWidth=390`, zero horizontal
overflow, zero captured console error/warning diagnostics, and a screenshot for
every route. The initial full-page screenshot tool failure after ten routes was
recovered with viewport screenshots and retained as harness/tooling noise.

Fresh explicit logout/login sessions added direct denied-route evidence for
Primary teacher `8/8`, Accountant `9/9`, Cashier `6/6`, Bursar `9/9`, and a
normalized disposable Parent `6/6`, all ending at `/apps` with zero
diagnostics. Cashier Finance Plans showed disabled write controls. The fresh
parent session rendered its linked child at desktop and mobile without
horizontal overflow; supported cleanup returned relationship `204`, guardian
deactivation `200`, student soft-delete `204`, and cleanup-policy restore
`200`. Exact screenshot/JSON artifacts are indexed in
`qa/e2e-runs/2026-08-14-full-school/final/screenshot-pack.md`.

This is PASS for the new route/state/mobile and persona-boundary slices only.
It does not close the plan-level every-module six-state cross-product, UI
write/read-back, resource/no-mutation browser checks, network inventory, or
full golden-step replay. The overall release disposition remains CONDITIONAL;
V77 remains non-destructively blocked and nurse remains excluded.

## Gate 14 secondary directory policy correction - V146 - 2026-08-16

The previously recorded empty Secondary/Francophone academic roster was
reproduced and traced to an application/policy mismatch, not merely missing
fixture data. `StudentService.roster()` had delegated through the
enrollment-history roster, which requires `ENROLLMENT_VIEW`; a form teacher
must instead receive active directory rows only after the existing
`STUDENT_DIRECTORY_VIEW` and assignment/parcours checks. The candidate now
uses a directory-specific active-enrollment read and V146 adds only
`form_teacher` `STUDENT_DIRECTORY_VIEW/ASSIGNED_CLASSES`. No
`ENROLLMENT_VIEW`, student-write, or other ordinary-role authority was added.

Fresh Testcontainers applied 113 migrations through V146 and
`SharedFoundationIntegrationTest#academicStudentRosterDoesNotRequireEnrollmentHistoryAuthority`
passed `1/1`. The isolated 8101 restart reported health HTTP `200`
`{"status":"UP"}` and Flyway `146|true`. In a fresh explicit logout/login
8100 session, Jean-Paul's `/academic` view showed `SEC-FR-4E-A` with
`4 élèves`, including `SECONDARY-7E9BADD8 Gate14UI`. The matching APIs returned
student directory `200`/4 and enrollment-history `200`/0.

The disposable fixture cleanup returned relationship `204`, guardian
deactivation `200`, student soft-delete `204`, temporary policy preview/apply
`200/200`, and original policy restore `200`. Post-cleanup roster was `200`/3,
the fixture was absent, the student was inactive, the guardian was
`INACTIVE`, the retained relationship carried `effective_to=2026-08-16`, and
no temporary policy rule remained. Exact evidence:
`qa/e2e-runs/2026-08-14-full-school/final/gate14-secondary-ui-action-v146-20260816.md`
and its adjacent JSON artifact.

This closes the secondary directory UI/API mismatch for the measured scope.
Gate 14 remains `IN PROGRESS` for the complete per-persona action/resource,
no-mutation, visual-state/mobile, network/console, and golden-journey matrix;
broader performance/resilience remains partial; the V77 upgrade remains
non-destructively blocked; and nurse remains explicitly excluded.

## Final-source build, package, and runtime audit - 2026-08-16

The final candidate source completed the full backend Maven suite with `180`
tests, zero failures/errors/skips, exit `0`, and `BUILD SUCCESS` in `04:54`.
The suite used fresh Testcontainers databases and applied the migration chain
through V146. A subsequent `mvn -DskipTests package` also exited `0`; both the
local jar and the deployed backend image contain the V140-V146 migration
resources, including V146.

The isolated Compose configuration validated with exit `0`. Final read-only
runtime checks were UI 8100 HTTP `200`, backend health 8101 HTTP `200` with
`{"status":"UP"}`, Mailpit 8125 HTTP `200`, and database Flyway
`113|146|true` / `146|true|form teacher directory assigned scope`. No 8102,
8103, or `bbc-perf` resources were running. `git diff --check` exited `0` with
only existing LF/CRLF normalization warnings.

The report remains CONDITIONAL/NO-GO: Gates 0-13 and the measured Gate 14
slices are green, but the complete Gate 14 action/resource/no-mutation/state/
network/golden UI cross-product and broader realistic performance/resilience
coverage remain incomplete. The production-like V77 upgrade remains
non-destructively blocked by checksum lineage and lack of an approved
candidate-compatible backup/bridge. Nurse remains deferred and excluded.
The current defect/blocker register is
`qa/e2e-runs/2026-08-14-full-school/final/defect-register.md`.

## Superseding V147 final-source audit - 2026-08-16

The candidate was rebuilt after adding the persisted school address to General
settings and the versioned document-branding read/PDF path, and after adding
the narrow V147 bootstrap-user document-design authority. Final checks are
green for the measured scope:

- backend focused contracts `3/3`; full Maven `182/182`, zero
  failures/errors/skips, fresh Testcontainers through V147, `BUILD SUCCESS`;
- frontend `25` files / `56/56` tests and production build exit `0`;
- backend/frontend Docker builds `19/19`, deployed jar includes V147;
- live 8101 health `200/UP`, 8100 `200`, Mailpit API `200`, Flyway `147|true`,
  `git diff --check` exit `0`, and no 8102/8103/performance resources;
- fresh explicit 8100 bootstrap-admin `Primaire`/`FR` session rendered the
  address field and the published v2 branding card with `E2E Acceptance
  Campus`; read-only DB/API evidence agrees.

The exact register is
`qa/e2e-runs/2026-08-14-full-school/final/final-source-verification-v147-20260816.md`.
The release remains **CONDITIONAL / NO-GO for production**: Gate 14's full
module/persona/state/action/resource/network/golden matrix is still
`IN PROGRESS`; realistic-scale UI/progress/cancellation/resilience evidence is
still partial; logo configurability and a logo-bearing official-document
read-back remain open under P1-04; and the production-like V77 upgrade remains
non-destructively blocked by checksum lineage and the absence of an approved
candidate-compatible backup/bridge. Cross-tenant production provisioning is
test-only/unsupported, and nurse remains deferred/excluded.

## Superseding production-critical V149 handoff - 2026-08-16

The current reduced-scope handoff is
`qa/e2e-runs/2026-08-14-full-school/final/production-critical-readiness-v149-20260816.md`.

Production-critical functional readiness is **PASS for the exercised scope**:
the isolated V149 package is live at 8100/8101, Flyway is successfully at
V149, backend Maven is `186/186`, frontend CI is `58/58`, the logo contract is
`5/5`, fresh non-nurse persona smoke is `10/10`, and the existing Gate 0–13
session/class/student-parent, timetable/conflict, attendance, academic,
finance, and promotion journeys have green fixture/read-back evidence.
Fresh Direction master-timetable read and fresh admin promotion UI checks also
passed with zero browser diagnostics. No 8096 evidence is used.

The full plan is not being represented as unconditionally complete: exhaustive
Gate 14 breadth and performance/resilience are deferred by the current scope;
V77 remains a non-destructive checksum-lineage/approved-upgrade-input blocker;
cross-tenant production provisioning remains test-only; and nurse remains
deferred/out of scope. No Flyway repair, checksum edit, manual DB mutation, or
nurse-specific acceptance change was made.

The requirement-by-requirement audit for plan sections 10–33 is now indexed
at `qa/e2e-runs/2026-08-14-full-school/final/plan-requirement-ledger-v149-20260816.md`.
It records every gate, golden journey, cross-cutting requirement, automated
regression family, route/reference deliverable, and the exact reasons the full
definition of done remains conditional.
