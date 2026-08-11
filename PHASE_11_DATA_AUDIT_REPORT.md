# Phase 11 read-only data audit

Captured from the deployed clean remediation branch on 2026-08-10 (Africa/Lagos), after Flyway V75 and the live timetable publication. The queries below were read-only. No unrelated/manual enrollment, batch, or historical timetable row was deleted or guessed at. The separate school report-card fidelity work is not included in this baseline remediation.

## Remediation counts

| Audit | Before (V74) | After (V75/live) | Interpretation |
| --- | ---: | ---: | --- |
| Latest Flyway schema | V74 | **V75** | V75 is the additive recovery migration; applied successfully to the live database and to an empty PostgreSQL database in the backend suite. |
| Committed batches without a promotion register | 2 | **0** | V75 repaired both historical committed batches. `commit()` is also idempotent and repairs a missing register on a committed retry. |
| `promotion_register` rows | 0 | **2** | One immutable register now exists for each committed batch. |
| Future ACTIVE `PROMOTION` enrollments | 4 | **4** | These are legacy 2027-2028 rows whose exact source enrollments are already `COMPLETED`; they were intentionally not rewritten. |
| Future PLANNED `PROMOTION` enrollments | 0 | **0** | There were no eligible source-`ACTIVE` legacy rows to migrate. New promotion commits create PLANNED targets while the source remains ACTIVE. |
| Exact promotion-decision links on the audited legacy targets | 0 | **4** | V75 backfilled only the exact committed decision links; no unrelated enrollment was linked. |
| PLANNED transition events | 0 | **0** | No fabricated history was created because the four legacy sources were already completed. |
| Published timetable assignment drift | 7 on old V1 | **0 on V2** | V1 remains ARCHIVED for audit history. V2 is the current PUBLISHED version with 14 slots across 4 classes. |
| Current-session assignment discrepancy rows | — | **0** | The live canonical teacher assignments agree with the published V2 snapshot. |

## Baseline data findings retained for operator review

| Audit | Result | Interpretation |
| --- | ---: | --- |
| Missing primary homeroom assignments | 0 | No current curriculum row is missing its dated primary authority. |
| Overlapping active secondary RESPONSIBLE assignments | 0 | The V65 effective-date invariant is clean. |
| Duplicate subject groups | 0 | No duplicate normalized group code was found. |
| Curriculum/legacy coefficient differences | 0 | Current curriculum coefficients agree with legacy defaults where both exist. |
| Grades without reporting-period/assessment scope | 0 | No unscopeable grade rows were found. |
| Published bulletin snapshots with trace | 1 | New trace-enabled snapshot coverage is present; 60 historical snapshots predate the trace schema and remain unchanged. |
| Published progression graphs with no edges | 0 | The live published graph is structurally populated. |
| Duplicate DRAFT promotion batches | 1 group | Two pre-existing demo batches named `Promotion Session 2026-2027` remain an admin repair item. They were not deleted or merged. |
| Versioned report-card templates | 12 seeded reference rows | V73 provides FR/EN, primary/secondary, sequence/term/annual reference designs without rewriting older templates. |
| Published branding baselines | 1 FR row | V73 seeds a published branding version from the current school profile; snapshot evidence stores its immutable id/hash. |
| Append-only profile-photo assets | Seeded from current photos | V74 preserves exact bytes and SHA-256 metadata for future snapshot/PDF rendering. |

## Live route evidence

| Route | Evidence |
| --- | --- |
| `/academic` → Grade entry → CE1/S6 | The session/class-scoped roster contains exactly 1 active student (`Attendance Demo`), shows `1/1`, and does not show legacy students who are not enrolled in the selected session. |
| `/academic` → Bulletin → CE1/S6 | The roster contains 1 student. A stale/non-enrolled selection clears the bulletin and print state. Missing grades and remarks are shown as localized actionable messages; stable blocker codes remain in structured data and are not exposed as the primary UX. |
| `/journey` | A principal with journey-write capability sees **Promotions de fin d’année**; a read-only principal does not. |
| `/journey/promotions` | The workspace loads with 23/23 classes configured. Committed batch review shows a repaired `Registre de promotion` and SHA-256 register evidence. |
| `/timetable` | V2 is PUBLISHED and V1 is ARCHIVED. CE1 resolves to homeroom MBAH Junior; 6ème resolves Mathématiques to Paul Nkomo and Français to Amina Bello. Published teacher controls are disabled. |
| Timetable exports | CSV, ICS, XLSX, and PDF each returned HTTP 200 from the published V2 API. |

## Automated and deployment evidence

- Backend: 5 test files, **22 tests passed, 0 failures, 0 errors**. This includes fresh PostgreSQL/Testcontainers coverage for session rosters, stale-student safety, missing-register recovery, concurrent/idempotent commit, PLANNED target creation, exactly-once later activation, journey permissions, and version-scoped timetable publication. The empty database applied all 67 available migrations through V75.
- Frontend: 5 test files, **10 tests passed**.
- The final clean branch is free of tracked generated artifacts, `node_modules`, `output/`, `tmp/`, and presentation build files. The final Docker recreation and exact image/container timestamps are recorded in the handoff accompanying this report.

## Unresolved items

1. Four legacy future `ACTIVE` `PROMOTION` enrollments remain in 2027-2028. Their source enrollments are `COMPLETED`, so automatic conversion to PLANNED would be unsafe; an operator must decide their historical correction.
2. The two duplicate pre-existing DRAFT promotion batches remain for administrative review. They were not deleted or merged.

These rows are explicitly not marked complete. The remediation preserves audit/history and prevents new promotion commits from reproducing the unsafe legacy semantics.
