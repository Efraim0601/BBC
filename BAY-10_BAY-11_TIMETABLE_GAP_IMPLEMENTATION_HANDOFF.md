# Timetable authority, BAY-10 academics, and BAY-11 progression

## Exhaustive implementation handoff after code, database, Linear, and live-application audit

Audit date: 2026-08-09  
Repository: `C:\Users\joe tech\bbcomplex`  
Starting branch: `feature/BAY-11-student-journey-promotions`  
Starting commit: `80dfbc0 feat(BAY-10): complete academic reporting and bulletins`

This document is the authoritative execution plan for the next implementation task. It corrects the earlier conclusion that BAY-10 and BAY-11 were complete. The current branch contains a substantial working foundation and useful demo data, but the full Linear acceptance criteria are not yet met. Do not rebuild working features blindly. Preserve them, migrate them to the target design below, and close each gap with automated and live evidence.

## 1. Handoff contract

The implementing agent must:

1. Start from the current working tree, including the seven uncommitted timetable/class-subject files listed below.
2. Preserve unrelated user files and generated artifacts. Do not delete, reset, or overwrite them.
3. Read this document completely before changing code.
4. Read the referenced Linear issues before implementation: BAY-13 and BAY-52–57; BAY-10 and BAY-33–38, BAY-66, BAY-67; BAY-11 and BAY-39–43.
5. Treat this document's clarified product decisions as superseding older ticket wording where they conflict, especially the old timetable teacher dropdown/exception wording.
6. Implement in dependency order. Academic publication must be trustworthy before promotion consumes Annual results.
7. Add migrations after the current latest migration, `V58__durable_bulletin_batch_jobs.sql`. Never rewrite an applied migration.
8. Keep backward compatibility only through explicit migration adapters. Do not keep two writable sources of truth.
9. Use precise, bilingual, structured user errors. Never expose a hash, database constraint name, stack trace, or generic “invalid/conflict” message without an actionable explanation.
10. Use the existing UX conventions requested by the product owner: visible bordered inputs, required markers, inline red validation, disabled-field explanations, custom confirmation modals, consequence summaries, loading/empty/success/error states, and keyboard-accessible controls.
11. Verify with backend tests, frontend tests/build, migration startup, API smoke tests, browser flows, generated-document inspection, and Docker deployment at `http://localhost:8082`.
12. Update Linear only from evidence. Do not mark a story Done merely because its tables or endpoints exist.

### 1.1 Current working-tree changes that must be preserved

The working tree currently modifies:

- `backend/src/main/java/com/bbc/sms/timetable/TimetableController.java`
- `backend/src/main/java/com/bbc/sms/timetable/TimetableService.java`
- `backend/src/main/java/com/bbc/sms/timetable/TimetableSlotRepository.java`
- `backend/src/main/java/com/bbc/sms/timetable/dto/TimetableDtos.java`
- `frontend/src/app/features/setup/academic-setup.ts`
- `frontend/src/app/features/timetable/timetable.api.ts`
- `frontend/src/app/features/timetable/timetable.ts`

These changes already do three useful things:

- expose `GET /api/timetable/classes/{classId}/subject-teachers`;
- resolve a primary subject to the class homeroom teacher and a secondary subject to the current RESPONSIBLE class-subject teacher;
- auto-fill and disable the teacher field in the timetable slot modal, rejecting a mismatched teacher in the backend.

Retain this behavior as a short-term compatibility layer, then move it behind the unified assignment resolver specified below.

Untracked presentation/report artifacts in the repository root, `output/`, and `tmp/` belong to the user. Leave them alone.

## 2. Scope and fixed product decisions

### 2.1 Teacher assignment is edited in one place

The timetable must not be a competing teacher-assignment editor.

- Primary/nursery: a session-aware homeroom assignment is the default and locked teacher for every subject in that class.
- Secondary: the session-aware RESPONSIBLE teacher on the class-subject relationship is the locked teacher for that subject.
- The timetable slot editor shows the resolved teacher as a disabled field with a link or clear instruction to edit the assignment in `Settings → Academics → Class subjects / Teaching assignments`.
- The backend ignores a client-supplied teacher as authority. It resolves the canonical teacher and either stores that value or rejects an explicit mismatch with a structured field error.
- A real one-day substitution is not a hidden exception in the timetable slot. It is a separate dated, reasoned, permission-controlled, audited substitution workflow that preserves the original teacher.
- Grade packets, report-card teacher names, teacher dashboards, teacher timetable views, and attendance period ownership must all call the same resolver.

### 2.2 Coefficient is owned by session + class + subject

- `subject.defaultCoefficient` is a suggestion shown only while adding the subject to a class.
- The authoritative coefficient used in calculations and report cards is the published curriculum relationship for `academicSession + class + subject`.
- Legacy `subject_class_coef` must never override an existing session curriculum coefficient.
- Historical bulletins retain the coefficient copied into their immutable snapshot.

### 2.3 “Required subject” is a completeness rule, not a promotion gate

- A required curriculum subject needs valid evidence for result validation: a scored grade, an accepted absence outcome according to policy, or an exemption.
- It does not mean that the student must individually reach that subject's reference threshold to be promoted.
- The default promotion recommendation uses the overall published Annual average across all included curriculum subjects and the final council evidence.
- A future school may add an explicit subject-gate rule as a separately named, versioned promotion condition. Do not infer it from `mandatory` or `passThreshold`.

### 2.4 Standard academic result structure

For a standard Cameroon three-trimester session:

- S1 is the first Sequence result in T1.
- T1 Result uses S1 + S2 and may include an explicitly configured trimester COMP/evaluation component.
- S3 is the first Sequence result in T2.
- T2 Result uses S3 + S4 and may include COMP.
- S5 is the first Sequence result in T3.
- T3 Result uses S5 + S6 and may include COMP.
- Annual Result uses the three validated/published trimester result snapshots according to the configured annual policy, equal weights by default.
- T3 Result and Annual Result are distinct records, snapshots, publication actions, documents, URLs, and correction histories even if the school publishes them on the same day.
- Period dates and workflow windows are configured by the administrator. A generator may propose dates but must not silently split a year and apply the result.

### 2.5 Promotion and enrollment semantics

- A preview is read-only and creates no batch or enrollment.
- A saved review batch freezes the rule, path, curriculum/result evidence, and candidate set versions used to calculate it.
- A final admin decision may match or override the recommendation. Override requires a dedicated permission and a non-empty reason.
- Commit creates a future `PLANNED` enrollment. It must not immediately remove the student from the still-open source session.
- Source enrollment becomes `COMPLETED` when the source session closes or at the configured transition effective date. The target enrollment becomes `ACTIVE` when the target session opens/effective date is reached.
- Promotion consumes a published Annual bulletin snapshot only. Legacy/manual averages may be used by a one-time migration tool, never as an automatic runtime fallback.

## 3. Evidence-backed current-state audit

### 3.1 Repository and runtime baseline

- Docker is running: frontend `:8082`, backend `:8083`, PostgreSQL `:5434`.
- The current branch contains timetable migration V44, progression V45, and academic/reporting migrations V46–V58.
- There are only a few integration/unit tests, with no meaningful calculation, report lifecycle, PDF golden, timetable race/conflict, promotion transaction, or end-to-end coverage.
- Live session `2026-2027` is OPEN; `2027-2028` is DRAFT; `2025-2026` is ARCHIVED.
- Live `2026-2027` has three terms and ten reporting periods: S1, S2, T1_RESULT, S3, S4, T2_RESULT, S5, S6, T3_RESULT, ANNUAL.
- All live workflow windows currently span approximately the entire academic year. They do not represent real entry/submission/review/validation/publication/correction phases.
- Live data contains curriculum subjects, teacher assignments, assessments, grades, accepted packets, bulletin versions, attendance adjustments, conduct rows, and batch jobs. This is useful acceptance data but not proof of full requirements.

### 3.2 Timetable: what works now

- Class roster includes HOMEROOM and DEPARTMENTAL models.
- Primary and secondary grids exist.
- Published schedules can feed period attendance.
- A basic class/teacher/room conflict service exists.
- Live CE1 slot modal shows MBAH Junior in a disabled teacher field and explains that it is inherited.
- Live class-subject mappings resolve 6ème French/English to Amina Bello, Mathematics to Paul Nkomo, and PC/SVT to Daniel Etoa.
- Mismatched teacher payloads are rejected and missing teacher payloads are canonicalized by the current uncommitted backend code.

### 3.3 Timetable: real remaining gaps against BAY-52–57

#### BAY-52 — schedule structure and versions

- No first-class session-aware timetable version or copy/archive model.
- No immutable published version snapshot; published slots are interpreted using today's mutable class-subject assignment.
- Bell periods are essentially school-wide mutable rows; there is no versioned day/break/timezone model by session/level/subsystem.
- No safe “copy previous version/session”, preview diff, or effective date.
- Class-level DRAFT/PUBLISHED flags do not provide a complete master-version model.

#### BAY-53/BAY-54 — homeroom and departmental assignment

- Primary homeroom is still edited in the timetable while secondary assignments are edited in Academic setup. This remains two UX sources.
- `timetable_class_config` and `academic_class_subject_teacher` can disagree. Live CE1 demonstrates this: the curriculum assignment contains Claire Tchana while timetable resolves MBAH Junior.
- Effective dates are not used by the current resolver.
- Multiple active RESPONSIBLE rows can exist; the resolver chooses the first by ordering rather than reporting an invalid assignment.
- The legacy `assignTeacher` endpoint still writes `teacher_class` / `teacher_subject`, creating another writable source.
- Teacher department, qualification, workload, and availability rules are absent.

#### BAY-55 — conflict prevention

- Current database uniqueness protects the teacher stored on the slot, not the effective teacher after an assignment changes.
- Changing an assignment can silently create a teacher collision in already-published schedules.
- Concurrent draft saves are not protected by a complete database-safe/advisory-lock strategy.
- Room is a string; there is no room entity, capacity, availability, or resource conflict model.
- Error responses are inconsistent and sometimes generic/mixed-language.

#### BAY-56/BAY-57 — views, substitutions, documents

- No dated substitution/cancellation workflow.
- No authoritative published master schedule view across classes and rooms.
- Teacher dashboard is not a complete personalized current/published schedule with substitutions.
- No deterministic iCal/XLSX export or full metadata/hash manifest.
- No golden/export regression tests.

### 3.4 BAY-10 academic/report cards: what works now

- Session, terms, and the ten reporting-period records exist.
- Session-scoped curriculum, subject groups, class-subject coefficient, responsible teacher, assessments, grades, subject remarks, grade packets, bulletin versions, attendance/conduct inputs, PDF generation, and durable batch tables/services exist.
- The live Academic screen exposes bulletin, grade entry, attendance/council, PV, and batch areas.
- A live batch exported a PDF/ZIP successfully.
- Parent raw-grade access is blocked, and a published-bulletin path exists.
- Profile/photo fields and several reference-template concepts have partial DTO/rendering support.

### 3.5 BAY-10: real remaining gaps against BAY-33–38, BAY-66, BAY-67

#### BAY-66 — periods and windows

- No distinct teacher-submission window.
- Timezone is not an explicit part of window evaluation/configuration.
- “Create standard structure” silently computes equal date splits; preview is not an editable proposal and has no fingerprint/concurrency protection.
- No containment/order validation across session → term → reporting period → workflow phases.
- A half-configured open/close pair can disable inheritance and create an unusable effective window.
- Window policy does not consistently enforce session/period lifecycle status.
- Submission currently reuses grade-entry policy because no submission action exists.
- No effective-window/readiness endpoint with `configuredAt`, effective source, current phase, next transition, and blockers.
- No audited emergency override permission and reason.
- Live window labels say “configured” while every phase is broadly open for the entire year; the UI does not warn that the structure is operationally unsafe.

#### BAY-33/BAY-34 — grade entry and teacher workflow

- Legacy and new writable grade APIs coexist.
- A direct session-grade endpoint can bypass the canonical responsible assignment/packet workflow.
- Editing a grade/comment immediately supersedes a published bulletin before a corrected replacement is published.
- Academic teacher authorization still uses legacy teacher-class/teacher-subject fallbacks rather than the same resolver as timetable.
- Batch save is all-or-generic-error; no row-level outcome, stable idempotency key, autosave state, or safe retry contract.
- Packet transitions are weakly enforced. IN_REVIEW is absent or not modeled consistently; invalid state jumps are possible; return reason is not guaranteed by backend validation.
- Subject remark requirement is not included in validation readiness.
- Optimistic versions are incomplete for remarks/roster rows.
- No complete packet history or teacher/reviewer queue with deadlines and blockers.

#### BAY-35 — calculation and snapshot

- Term and Annual calculations recursively read mutable child data instead of consuming frozen validated/published child snapshots.
- Optional trimester COMP is not modeled in the calculation graph.
- Configured calculation-policy strings are mostly ignored; equal-weight behavior is hardcoded.
- EXEMPT/ABSENT handling can produce a zero-value subject that still carries coefficient weight. This is mathematically wrong for exempted subjects.
- Missing-subject readiness is not evaluated per curriculum relationship, so an omitted mandatory curriculum subject can disappear from the calculation without blocking validation.
- The legacy `subject_class_coef` can override the session curriculum coefficient. This violates the fixed coefficient rule.
- Ranking uses rounded averages and recursive per-student calculation, producing potential tie/rank errors and N+1 work.
- Success threshold and appreciations are hardcoded instead of versioned/localized policy.
- Snapshot traceability is incomplete: source assessment/grade IDs and versions, curriculum version, teacher assignment version, formula version, template version, and exact attendance/conduct evidence are not all frozen.
- Student identity/photo, school branding/signatories, subject/group ranks, class profile, and number of passed subjects are incomplete.

#### BAY-67 — attendance, conduct, honors, council

- Finalized attendance is aggregated, but expected coverage, missing-session warnings, and complete duration/source evidence are not part of readiness/snapshot.
- Zero duration may silently pass.
- Draft/unapproved conduct data can enter a bulletin, and approval is not always a validation blocker.
- Adjustment approval/source breakdown is incomplete in the shared DTO and document.
- The live attendance/council screen can display APPROVED rows while still presenting “Save draft”, which obscures lifecycle and permissions.

#### BAY-36/BAY-37/BAY-38 — templates, publication, batch

- The PDF is one hand-coded renderer, not a controlled template-version system selected by school/subsystem/product/locale.
- School logo, ministry headers, stamps, signatures, profile photo policy, and reference-derived Primary/Secondary sequence/term/annual variants are not versioned configuration.
- No deterministic golden visual suite.
- Bulletin validation can jump from DRAFT without complete accepted-packet/readiness evidence.
- Publish changes state but does not atomically render/issue the official document, persist its checksum, append audit, and enqueue parent notification.
- Correction semantics are unsafe: the old published version should remain available until the replacement is published, then become SUPERSEDED.
- Some GET paths calculate/persist snapshots, violating read-only HTTP semantics.
- Batch generation lacks a true eligibility preview, cancellation, complete resumability/history, and manifest/PV linkage to the exact snapshots.
- The Academic screen visibly mixes the milestone selector with legacy `— / Seq.1 … Seq.6` buttons and fallback behavior. This is confusing and can route users to different data models.

### 3.6 BAY-11 promotion/journey: what works now

- Source/target session selection, threshold configuration, one target mapping, preview/review, recommendation, manual final decision, commit, and journey rows exist.
- Live data proves both automated recommendations and a manual override with reason.
- Commit is transactional at a basic level and creates target enrollments.
- Published Annual evidence is preferred when available.

### 3.7 BAY-11: real remaining gaps against BAY-39–43

#### BAY-39 — progression graph

- Only one target is stored per source class; there are no allowed alternatives, edge types/order, graph version, copy-prior workflow, preview diff, or immutable snapshot.
- No cycle, self-loop, duplicate-edge, subsystem, rank, skipped-level, deleted-class, or terminal-branch validation.
- Optimistic request version is not enforced on path save.
- Live configuration is incomplete (13/19 classes). Form 1 maps directly to Form 5 because Form 2–4 do not exist in the catalog; the UI does not flag this as a skip requiring explicit intent.

#### BAY-40 — automated recommendation

- Rules are only global thresholds plus “require average”; no versioned scoped conditions or ordered explanation.
- No optional attendance, discipline, incomplete-evidence, administrative hold, or council-policy condition.
- Hidden defaults 10/8 apply if a rule is absent instead of blocking or clearly labeling a default.
- Published Annual is preferred but manual Journey/legacy grades are used as automatic runtime fallback.
- The exact rule/path/evidence versions are not frozen with the recommendation.

#### BAY-41 — review and override

- “Preview” persists a DRAFT batch.
- Override permission is not separate from general promotion review.
- Alternate target is not validated against an allowed progression edge.
- No durable batch list, filter, search, candidate detail drawer, selected-row action, recalculate-with-diff, or decision audit timeline.
- Reloading the page loses discoverability of prior draft/committed batches.
- The frontend creates a new random idempotency key on each preview click, so an actual retry is not idempotent.

#### BAY-42 — commit

- Target session status, class capacity, finance/admin holds, and evidence version drift are not fully checked.
- There are no row locks/advisory locks protecting concurrent commits.
- Source enrollments are completed and target enrollments activated immediately, which is wrong while the source year is still open.
- No explicit PLANNED → ACTIVE transition.
- No promotion register, manifest, outbox, correction/compensation, cancellation, or operator recovery workflow.
- Journey upsert can overwrite a same-year row instead of appending immutable events.

#### BAY-43 — journey projection

- Journey is still primarily mutable manually entered `journey_entry` rows, not an authoritative projection of enrollments, transfers, promotion decisions, and published bulletins.
- Manual entries can be deleted rather than voided with an audit reason.
- No parent-safe journey endpoint with redaction and official-document links.
- In the live principal session, the Journey page did not expose the promotion action, although the direct route is reachable. Permission/discoverability behavior must be corrected and tested.

## 4. Target domain model and source-of-truth rules

### 4.1 Academic context

Every operational record must carry or derive an explicit:

- school;
- academic session;
- class and the student's enrollment in that session;
- term, where applicable;
- reporting period/milestone;
- published curriculum version;
- responsible teaching assignment version;
- actor and audit timestamp.

Never derive historical context from “current year”, today's class, or a mutable catalog label.

### 4.2 Unified teaching assignments

Add a service boundary such as `TeachingAssignmentResolver` and make it the only authority used by timetable, grades, attendance ownership, reports, and teacher dashboards.

Recommended persistence:

1. `class_teacher_assignment`
   - `id`, `school_id`, `academic_session_id`, `class_id`, `teacher_id`;
   - role `HOMEROOM`, `ASSISTANT`, or another explicitly supported class role;
   - `effective_from`, `effective_to`, status, version, created/updated actor/time.
2. `class_subject_teacher_assignment`
   - `id`, `school_id`, `academic_session_id`, `class_id`, `curriculum_subject_id`, `teacher_id`;
   - role `RESPONSIBLE` or `ASSISTANT`;
   - effective dates, status, source, version, audit.
3. Unique/exclusion rules that allow exactly one effective HOMEROOM per class and exactly one effective RESPONSIBLE per curriculum subject at a date.

Resolver input must be `sessionId + classId + subjectId + effectiveDate`. Resolver output must include teacher ID/name/code, role, assignment ID/version, source, effective range, and an actionable unresolved/conflict reason.

For primary, resolve HOMEROOM regardless of subject. For secondary, resolve RESPONSIBLE for the curriculum subject. If zero or multiple valid assignments exist, do not guess; block the workflow and explain where to repair it.

Migrate current `timetable_class_config`, `academic_class_subject_teacher`, and legacy `teacher_class`/`teacher_subject` values. Produce a migration discrepancy report before disabling legacy writes.

### 4.3 Versioned curriculum

Introduce a curriculum header/version for each session + class:

- lifecycle `DRAFT`, `PUBLISHED`, `ARCHIVED`;
- version number and copied-from reference;
- subject groups and curriculum-subject rows belong to the version;
- one published version may be effective for a date/range;
- coefficient, max score, display order, completeness requirement, remark requirement, group, and optional reference threshold belong to the relationship;
- publishing validates duplicate group codes, duplicate subject rows, missing coefficient/max score, and teacher assignment completeness;
- assessments and result snapshots reference the exact curriculum version/row.

Prevent the duplicate live groups `Langues` and `Sciences` through a normalized unique key and a merge/remediation migration.

### 4.4 Reporting periods and workflow windows

Keep Session → Term → Reporting Period hierarchy, but add explicit workflow phases:

- GRADE_ENTRY;
- TEACHER_SUBMISSION;
- REVIEW;
- VALIDATION;
- PUBLICATION;
- CORRECTION.

Store `timestamptz`, school timezone, open/close as an inseparable pair, version, actor, and override history. Inheritance is allowed only when both child values are null. Effective policy returns the source level (`PERIOD`, `TERM`, `SESSION`), configured and effective values, current state, next transition, and blockers.

Validate:

- open < close;
- child dates remain inside parent/session dates;
- grade entry precedes submission/review/validation;
- publication cannot begin before validation is possible;
- correction begins no earlier than publication unless an explicit policy allows it;
- term result dependencies close before term validation;
- Annual dependencies are validated/published before Annual validation;
- no incompatible overlap for the same action/scope;
- session/term/period status permits the action.

### 4.5 Result dependency graph

Represent calculation inputs explicitly rather than by string conventions:

- reporting period dependency rows identify child result periods and their weights/order;
- optional COMP is an assessment component owned by the term-result period, not a fake sequence;
- standard template proposal creates S1/S2 → T1, S3/S4 → T2, S5/S6 → T3, T1/T2/T3 → Annual;
- proposal is editable before apply and carries a fingerprint of session/term versions;
- apply is transactional, reasoned, and rejects stale fingerprints.

### 4.6 Timetable versions

Add immutable schedule versions rather than reinterpreting published rows:

- version header by session and configured scope (school/subsystem is preferable if publication is coordinated; otherwise class version with cross-class conflict checks);
- version number, DRAFT/PUBLISHED/ARCHIVED, effective range, timezone, copied-from, actor/time, reason;
- versioned teaching days, bell periods, breaks, and slots;
- each slot stores class, subject/curriculum row, canonical assigned teacher, assignment ID/version, room resource, and period;
- publishing freezes these values and checks conflicts against every simultaneously effective published schedule;
- changing a teaching assignment does not rewrite a published schedule. The UI reports drift and offers a new draft version/rebase.

Add room/resource tables with code, name, capacity, active/effective dates, and availability. Add teacher availability and optional workload/qualification configuration. Use transaction locks or PostgreSQL advisory locks around `(session/version/day/period/class|teacher|room)` conflict keys so concurrent saves cannot pass the same pre-check.

### 4.7 Bulletin lifecycle and immutable snapshot

Use an explicit lifecycle:

1. grade packets DRAFT → SUBMITTED → IN_REVIEW → RETURNED or ACCEPTED;
2. bulletin/result DRAFT/CALCULATED → READY → VALIDATED → PUBLISHED → SUPERSEDED;
3. correction creates a new DRAFT version referencing the published version;
4. the original remains current and downloadable until the replacement publish transaction succeeds;
5. replacement publish supersedes the original atomically.

Validation creates/finalizes the immutable calculation snapshot. Publication atomically:

- verifies window/readiness and snapshot version;
- renders the selected template deterministically;
- stores document metadata, checksum, template/version, locale, and snapshot ID;
- marks it issued/published;
- appends audit;
- writes an outbox event for parent notification.

No GET endpoint may calculate, mutate, or create versions.

### 4.8 Promotion graph, rules, decisions, and enrollment transition

Model progression as versioned data:

- graph version for source session → target session;
- source class node with zero or more ordered edges;
- edge kinds `DEFAULT`, `ALTERNATIVE`, `TERMINAL` as appropriate;
- explicit terminal/graduation node state;
- version, copied-from graph, publish/freeze lifecycle and audit.

Model promotion rules as versioned ordered conditions scoped by school/subsystem/level/class. The default rule is overall published Annual average. Optional conditions can include finalized attendance coverage/absence threshold, approved conduct, incomplete evidence, council decision, capacity, and administrative holds. Every recommendation must return an ordered explanation containing evidence values, thresholds, pass/fail/not-applicable, source IDs/versions, and resulting recommendation.

Use explicit final decisions `PROMOTE`, `REPEAT`, `REVIEW`, `GRADUATE`, and any approved transfer/alternative type. Save reviewer, timestamp, target, comment, override flag/reason, rule/graph/evidence versions, and optimistic version.

## 5. Detailed implementation sequence

Each phase below must end with tests and a small live acceptance checkpoint. Do not defer all integration until the end.

### Phase 0 — protect the baseline and convert the audit into executable checks

Backend:

1. Start the current Docker stack and record container/image/health state.
2. Run existing backend tests and capture failures before changes.
3. Add characterization tests for current canonical teacher behavior:
   - CE1 subject resolves homeroom teacher;
   - 6ème subject resolves RESPONSIBLE teacher;
   - client teacher mismatch returns structured 4xx;
   - null teacher is canonicalized;
   - no assignment and duplicate assignment are explicit blockers.
4. Add characterization tests for coefficient precedence and expose the current legacy override failure.
5. Add characterization tests for correction, term/annual calculation, exempt-only subject, preview side effects, and enrollment transition. These tests should initially demonstrate the gaps.

Frontend:

1. Add tests that the timetable teacher field is disabled for both HOMEROOM and DEPARTMENTAL classes.
2. Add tests that changing subject refreshes teacher name and that missing assignment disables Save with an inline repair message.
3. Add tests for the current milestone/legacy-sequence duplication and then remove the duplication in the relevant later phase.

Deliverable: a baseline evidence note with current pass/fail counts. Do not mark Linear stories complete.

### Phase 1 — unified teaching assignments and safe timetable authority

Database/migration:

1. Create versioned/effective class and class-subject assignment tables or evolve the existing table to the exact target constraints.
2. Backfill from current academic assignment first, then homeroom config; create a discrepancy table/report for conflicting values such as CE1.
3. Normalize exactly one current/effective HOMEROOM and RESPONSIBLE assignment. Do not silently choose among duplicates.
4. Add audit and optimistic version columns/constraints.
5. Make legacy assignment tables read-only from application code after migration; retain read adapter only while verifying backfill.

Backend:

1. Implement `TeachingAssignmentResolver` with a single repository query and explicit unresolved/conflict result types.
2. Replace custom resolution in `TimetableService`, `GradeEntryService`, packet authorization, report-card teacher display, teacher schedule, and attendance period ownership.
3. Remove or deprecate the legacy timetable `assignTeacher` write endpoint. Return a clear migration/deprecation error if an old client calls it.
4. Save the resolved assignment ID/version and teacher ID on timetable slots/snapshots.
5. Before changing an assignment, calculate impact: draft schedules can refresh; published schedules produce a drift warning and require a new schedule version.
6. Add separate substitution endpoints using dated occurrence, original/replacement teacher, class/subject/slot, reason, approval/audit, and conflict validation.

Frontend:

1. Create a clear `Teaching assignments` area inside `Settings → Scolarité → Matières par classe`.
2. For primary classes, show one bordered Homeroom teacher selector above the subject rows; each subject row shows “Inherited from homeroom”. Do not show five redundant editable teacher selectors.
3. For secondary classes, show one responsible teacher selector per subject and effective dates where enabled.
4. Show duplicate/missing assignments as a red blocking card with direct repair action.
5. Remove teacher assignment editing from the timetable. The class header may show a read-only summary and a deep link to settings.
6. Keep the timetable slot teacher field disabled, visible, and explained.
7. Add a separate “Substitutions” action/view, never a hidden unlocked dropdown.

Tests/live gate:

- Verify primary and secondary assignment changes update only draft schedules and future derived views.
- Verify published schedule retains its original teacher and reports drift.
- Verify teacher grade packet, timetable, report card, and attendance ownership agree for the same class/subject/date.
- Verify duplicate assignment is impossible at DB/API level.

### Phase 2 — timetable versions, periods, rooms, conflict safety, and exports

Database/backend:

1. Add timetable version headers and migrate V44 slots into version 1 without losing published status.
2. Add versioned teaching-day, bell-period, break, timezone, room/resource, availability, and effective-date records.
3. Implement create draft, copy previous, compare/diff, publish, reopen-as-new-version, and archive. Published rows are immutable.
4. Enforce class, effective teacher, room, availability, effective range, and version conflicts in a transaction with deterministic lock order/advisory locks.
5. Return `409` with stable error code and conflict details: resource type/name, class, teacher/room, day, period, existing slot, and repair suggestion.
6. Generate master/class/teacher/room projections from the selected published version plus dated substitutions/cancellations.
7. Add deterministic PDF, CSV, XLSX, and iCal exports with version, session, timezone, generated time, checksum, and filter metadata.

Frontend:

1. Add visible version selector/status banner and consequences of publishing.
2. Use a custom publish modal summarizing number of classes/slots, unresolved assignments, conflicts, and effective dates.
3. Add master, class, teacher, room, and substitutions tabs with responsive day/period views.
4. Never make the user discover a missing teacher only after Save. Disable the slot Save and link to the assignment repair screen.
5. Explain locked published state and provide “Create new draft from version N”, not in-place editing.

Tests/live gate:

- Race two conflicting writes and prove only one commits.
- Change an assignment after publication and prove historical teacher schedule is unchanged.
- Export the same version twice and compare deterministic content/checksum, excluding explicitly documented generated timestamps if any.
- Feed secondary attendance only from the currently effective published schedule.

### Phase 3 — safe period/window configuration and live 2026–2027 setup

Database/backend:

1. Add TEACHER_SUBMISSION and explicit timezone fields/policy.
2. Add reporting-period dependency/component rows and version/fingerprint fields.
3. Implement a pure `preview standard structure` endpoint. Return proposed terms/periods/dependencies/windows, warnings, and source fingerprint. It must not write.
4. Accept edited proposal payload on apply. Validate fingerprint, dates, dependency graph, window order/containment, duplicate codes, and current lifecycle in one transaction.
5. Implement effective policy/readiness endpoint by action and context.
6. Add emergency override permission and endpoint requiring reason, scope, expiration, and audit; surface the override in every affected response.

Frontend:

1. Replace the current one-click equal split with a wizard:
   - Step 1 session/term dates;
   - Step 2 S1–S6 and T1/T2/T3/Annual result dates;
   - Step 3 dependency/weight/optional COMP settings;
   - Step 4 workflow windows;
   - Step 5 validation summary/diff;
   - Step 6 custom confirmation and transactional apply.
2. Every datetime field has border, timezone label, required marker, inline validation, inherited-source badge, and effective value preview.
3. Show a phase timeline and “open now / opens in / closed” banner in Academic screens.
4. Warn prominently if every window spans the full year.
5. Distinguish “configured here” from “inherited from term/session”.

Live configuration gate for `2026-2027`:

1. Keep existing term boundaries unless the product owner changes them.
2. Configure S1/S2/T1, S3/S4/T2, S5/S6/T3, and Annual as distinct dependencies.
3. Configure realistic non-overlapping entry, submission, review, validation, publication, and correction windows for all ten milestones.
4. Keep T3 and Annual separate even if publication dates overlap.
5. Store timezone and display the exact converted values.
6. Capture screenshots/API output and document who can act in each phase.

### Phase 4 — curriculum versions, subject groups, assessments, and teacher packets

Database/backend:

1. Add curriculum header/version and migrate current session curriculum rows/groups.
2. Merge duplicate live groups safely; reject duplicate normalized code within a curriculum version.
3. Make curriculum coefficient authoritative. Legacy coefficient is fallback only when creating migration draft rows and never during current calculation once a curriculum row exists.
4. Version assessments against reporting period + curriculum subject. Support score, max, weight, display order, mandatory, component type including term COMP, and lifecycle.
5. Close direct writable grade endpoints or route them through the same packet/authorization/window service.
6. Implement roster autosave/batch response with one outcome per row, stable idempotency key, optimistic version, and retry-safe semantics.
7. Enforce the packet state machine and persist every transition/reviewer/return reason.
8. Include required subject remarks in readiness.
9. Provide teacher/reviewer queue endpoints with counts, deadlines, packet state, missing scores/remarks, and action permissions.

Frontend:

1. Split Academic into coherent workspaces rather than mixing legacy and new controls:
   - Teacher gradebook;
   - Review queue;
   - Result validation/publication;
   - Report-card documents/batches;
   - Attendance/conduct/council inputs.
2. Remove the legacy sequence buttons once a reporting period is selected. One milestone selector controls all data.
3. Gradebook rows show student image/name/matricule, each assessment, status selector, inline error, saved/dirty/saving state, subject remark, and packet blockers.
4. A teacher sees only canonical assignments effective for the period/class.
5. Return/review dialogs require reasons where applicable and explain consequences.
6. On failed batch save, retain valid edits and highlight only failed rows.

Tests/live gate:

- Teacher cannot edit another teacher's packet, including direct API calls.
- Homeroom teacher behavior is consistent for primary.
- Submission closes editing; return reopens; accept locks packet.
- Required remark blocks acceptance/validation; optional remark does not.
- Duplicate subject groups and duplicate responsible assignments are rejected with field-level errors.

### Phase 5 — authoritative calculation engine and snapshots

Backend calculation service:

1. Separate pure calculation from persistence. Build immutable input DTOs and deterministic output.
2. Normalize every scored assessment to the curriculum max/report scale before applying configured weights.
3. Define status policy explicitly:
   - MISSING blocks required completeness;
   - EXEMPT removes that assessment from its denominator;
   - a fully exempt subject is excluded from the overall coefficient denominator and labeled EXEMPT;
   - ABSENT follows a versioned school policy, defaulting to zero only when an expected assessment is confirmed as unexcused;
   - pending/justification state blocks final validation where required.
4. Sequence subject result is weighted normalized assessment result.
5. Term subject result consumes frozen accepted/validated S1+S2 equivalents and optional configured COMP. It must not silently reread mutable sequence grades after validation.
6. Annual subject result consumes frozen T1/T2/T3 result snapshots using configured weights.
7. Overall average uses the exact published curriculum relationship coefficient.
8. Keep full decimal precision internally. Round only display fields.
9. Use standard-competition ranking based on unrounded comparable values. Calculate class cohort once, not recursively per student.
10. Calculate subject ranks, group subtotals/ranks, overall rank, class min/max/mean, success percentage, and count passed/expected according to explicit policies.
11. Localize appreciation/mention through versioned ranges.
12. Create complete snapshot evidence:
    - student identity and profile-image asset/version;
    - enrollment/session/class;
    - curriculum/group/subject rows and coefficients;
    - assessment/grade/status/source/version IDs;
    - teacher assignment IDs/versions and persisted remarks;
    - child result snapshot IDs/versions;
    - formula/policy version;
    - attendance coverage/source/adjustments;
    - approved conduct/honors/council evidence;
    - school/template/locale/signatory references;
    - calculation timestamp and actor.

Readiness:

1. Implement one shared readiness service for UI, validation, publication, batch, and promotion.
2. Return blockers grouped by student, subject, packet, attendance, conduct, dependency, window, curriculum, and template.
3. Use stable codes plus human messages and direct repair routes.

Tests/live gate:

- Fixed numerical vectors for every sequence/term/COMP/annual formula.
- Tie/rank tests using values that differ after the second decimal.
- Missing, absent, pending justification, partial exempt, and fully exempt tests.
- Prove a legacy coefficient cannot override class-curriculum coefficient.
- Prove changing a child grade cannot mutate an already validated term/annual snapshot.
- Prove T3 and Annual produce distinct snapshots and documents.

### Phase 6 — attendance, conduct, council, and official inputs

Backend:

1. Aggregate only FINALIZED attendance sessions within the reporting-period date range.
2. Use DAILY expected sessions for primary and published timetable occurrences/durations for secondary.
3. Return expected count/duration, finalized coverage, missing dates/periods, present/late/absent totals, justified/unjustified duration, and source IDs.
4. Make zero/unknown duration an explicit warning/blocker according to policy, never silently valid.
5. Keep administrative adjustment as a request/approval workflow with reason, evidence reference, actor, reviewer, and audit.
6. Enforce DRAFT → SUBMITTED → APPROVED/RETURNED for conduct/honors/council input.
7. Only APPROVED inputs enter a validated bulletin.
8. Snapshot the exact input version and source breakdown.

Frontend:

1. Replace ambiguous status/action combinations. An APPROVED card must show locked data and a “Request correction” action, not “Save draft”.
2. Show official attendance versus adjustments separately and the resulting total.
3. Show expected coverage and missing attendance sessions before bulletin validation.
4. Provide roster-style bulk absence-hours input where the school uses manual migration/correction, with justified and total values, inline validation, and audit reason.
5. Show council decision separately from promotion final decision while linking the evidence.

### Phase 7 — template system, publication, parent access, batches, and PV

Database/backend:

1. Add template family/version selected by school + subsystem + product (`SEQUENCE`, `TERM`, `ANNUAL`) + locale.
2. Add versioned school branding/assets/signatories: logos, ministry/delegation text, bilingual names, address/phones, stamps/signatures, principal/class-master titles.
3. Implement reference-derived Primary and Secondary layouts, including:
   - student photo and identity block;
   - subject, sequence/term/annual marks, coefficient, weighted total, ranks where configured;
   - subject-specific teacher remark and responsible teacher name;
   - group subtotal rows;
   - class profile/statistics;
   - attendance/discipline/work distinctions;
   - council decision and signature/visa zones;
   - bilingual headers where template requires.
4. Embed Unicode fonts and images deterministically. Paginate without clipped rows or orphan headers.
5. Implement publish transaction and correction/supersession exactly as section 4.7.
6. Parent endpoints return only published official versions/documents; raw grades, drafts, reviewer notes, and internal blockers remain hidden. Publication action window does not automatically delete a parent's already-issued document; if the school needs a visibility expiry, model it separately.
7. Implement pure batch eligibility preview before starting a job.
8. Persist job/item states, stable idempotency, retry failed/blocked items, cancel queued work, resume after process restart, and retain history.
9. ZIP and PV manifest must list exact bulletin snapshot/document IDs, versions, checksum, result, and reason for each student.

Frontend:

1. Add template/branding preview in Settings with explicit version publishing.
2. Result workspace shows readiness count, blockers, validate, publish, correction, and document history separately.
3. Batch screen starts with eligibility preview and filters eligible/blocked students before confirmation.
4. History survives reload and supports archive/PV download.
5. All destructive or irreversible actions use custom modals with counts, consequences, reason where required, and exact target session/period/class.

Tests/live gate:

- Golden visual tests for each reference family/product/locale with profile photo.
- Unicode/accent tests and multi-page long-subject/long-name cases.
- Publish transaction rollback test when rendering/storage/outbox fails.
- Parent authorization tests for draft, published, superseded, and cross-family access.
- Batch restart/retry/cancel/idempotency tests.

### Phase 8 — progression graph and versioned promotion rules

Database/backend:

1. Add graph header/version and multiple edge rows with default/alternative/terminal semantics.
2. Add class hierarchy rank/grade-level metadata where needed. Missing school classes remain a configuration blocker; a skipped-level edge requires explicit “allow skip” flag and reason.
3. Validate cycles, self-loop, subsystem/locale compatibility, duplicates, deleted/inactive target, terminal outgoing edge, and optimistic version.
4. Add copy-from-prior-session with pure preview/diff and transactional apply.
5. Add versioned promotion rule set/conditions and publish/freeze lifecycle.
6. Recommendation engine must require the published Annual snapshot and return an evidence tree. Remove automatic runtime fallback to Journey/legacy grades after the migration report is accepted.
7. Define no-rule behavior as a blocker or an explicitly labeled published default rule; never use a hidden 10/8.

Frontend:

1. Replace the flat one-target table with a progression editor that shows class order, default target, alternatives, terminal state, and warnings.
2. Highlight all unmapped classes and invalid/skip/cycle edges before publishing.
3. Add copy/preview/diff flow with custom confirmation.
4. Rule builder shows human-readable condition order and a sample/explanation preview.

Live gate:

- Complete all real FR/EN class mappings for the catalog that exists.
- Explicitly resolve whether missing Form 2–4 and Lower Sixth should be created or whether a documented skip is genuinely intended. Do not silently retain Form 1 → Form 5.
- Publish a graph/rule version for 2026–2027 → 2027–2028.

### Phase 9 — promotion preview, review, override, commit, and recovery

Backend:

1. `POST /promotion-previews` is pure/read-only and accepts source/target/filter plus graph/rule versions. It returns a fingerprint and candidate evidence; it creates no rows.
2. `POST /promotion-batches` saves a reviewed preview using its fingerprint and caller-supplied stable idempotency key.
3. Add batch list/detail/filter endpoints and decision history.
4. Candidate DTO includes student/photo/matricule/source class, Annual document/result/version/average/rank, attendance/conduct/council summary, recommendation explanation, default/allowed targets, blockers, and final decision.
5. Add dedicated permissions such as `PROMOTION_CONFIGURE`, `PROMOTION_REVIEW`, `PROMOTION_OVERRIDE`, `PROMOTION_COMMIT`, and `PROMOTION_CORRECT`.
6. Enforce target selection against the frozen allowed edges. Override requires permission and reason. Use optimistic versions.
7. Recalculate returns diff and marks stale decisions; it never silently overwrites reviewed decisions.
8. Commit preview rechecks source enrollment, Annual evidence/version, graph/rule versions, target session status, capacity, holds, duplicate target enrollment, concurrent batch, and decision completeness.
9. Commit selected rows transactionally with deterministic locks and an idempotency key. Specify and test all-or-nothing behavior for the selected commit set.
10. Create PLANNED target enrollment with transition date; leave OPEN source enrollment ACTIVE. Activation/finalization runs on session transition/effective date and is idempotent.
11. Persist immutable promotion event and outbox event. Generate promotion register/manifest.
12. Implement cancel-before-commit and correction/compensation after commit. Never delete history.

Frontend:

1. Review workspace has batch history and status filters on entry.
2. Stepper: choose sessions → read-only preview → save review batch → resolve blockers/review candidates → commit preview → confirmation → progress/result.
3. Candidate table supports search/filter, selected rows, recommendation/final-decision comparison, and a detail drawer with evidence.
4. Override is visually explicit, requires reason inline, and records who/when.
5. Commit modal lists exact counts by Promote/Repeat/Graduate/Blocked, source/target sessions, effective date, capacity/hold warnings, and irreversible consequences/recovery path.
6. After commit, show register download, per-student outcome, and links to student journeys.

Tests/live gate:

- Automatic promote/repeat/review boundary values.
- Manual override regardless of average, with and without permission/reason.
- Invalid alternate target, stale graph/rule/Annual, target capacity, hold, duplicate enrollment, concurrent commit, and retry idempotency.
- Prove preview creates no database rows.
- Prove commit while source session OPEN creates PLANNED target and keeps source ACTIVE.
- Prove transition activation happens once.

### Phase 10 — authoritative journey projection and parent-safe history

Backend:

1. Build journey projection from immutable enrollment, transfer, promotion, graduation, and published bulletin/document events.
2. Migrate manual historical rows into append-only `journey_note`/legacy evidence with provenance.
3. Replace delete with void/correct requiring permission/reason and retaining original content.
4. Return official document links and result summaries by academic session.
5. Provide internal and parent-safe DTOs. Redact internal override reasons, reviewer notes, holds, and drafts from parent access.

Frontend:

1. Journey route is discoverable according to permission; authorized users see the promotion action and unauthorized users get a clear explanation, not a hidden reachable route.
2. Timeline distinguishes Enrollment, Transfer, Published result, Promotion decision, Graduation, and Manual historical note.
3. Every system event links to its source record/document and cannot be edited as free text.
4. Manual note correction uses an audited modal.

Tests/live gate:

- Timeline reconstructs all historical session/class/result transitions.
- No system event can be deleted or overwritten.
- Parent sees only published, redacted history for their linked children.

### Phase 11 — migration, cleanup, performance, documentation, and Linear closure

1. Run a dry-run data audit reporting:
   - conflicting/missing teacher assignments;
   - duplicate subject groups;
   - curriculum/legacy coefficient differences;
   - assessments/grades without scope;
   - bulletins whose dependencies/snapshot evidence are incomplete;
   - progression gaps/skips/cycles;
   - duplicate draft promotion batches;
   - immediate future ACTIVE enrollments created by old promotion commits.
2. Apply deterministic remediation only where intent is unambiguous. Put ambiguous rows in an admin repair queue.
3. Remove frontend legacy controls and backend legacy write paths after successful migration. Keep read-only compatibility only where a documented historical page still needs it.
4. Add indexes and query tests for class rosters, teacher queues, class calculation, batch polling, promotion preview, and journey projection. Eliminate N+1 result/rank calculations.
5. Fix mojibake in source/UI and audit French/English labels and structured messages.
6. Update the in-app guide with exact setup and daily workflows.
7. Rebuild/redeploy Docker, check migrations and health, then run the full live acceptance script below.
8. Attach test output, API examples, screenshots, generated sample documents, checksums, and commit identifiers to the matching Linear stories.
9. Mark a story Done only when every acceptance item has corresponding evidence.

## 6. Required backend API behavior

Use the project's existing `/api` conventions, but converge on these resources and semantics.

### 6.1 Assignments

- list/save/version class homeroom assignments;
- list/save/version class-subject RESPONSIBLE assignments;
- resolve effective assignment for a date;
- impact preview before changing an assignment;
- substitution list/create/approve/cancel;
- structured errors `ASSIGNMENT_MISSING`, `ASSIGNMENT_AMBIGUOUS`, `ASSIGNMENT_OUT_OF_RANGE`, `PUBLISHED_SCHEDULE_DRIFT`.

### 6.2 Timetable

- version list/detail/diff/copy/publish/archive;
- versioned day/period/break/room/availability configuration;
- slot create/update/delete on DRAFT only;
- master/class/teacher/room projections for effective published version/date;
- conflict preview and strict save conflict;
- export endpoints with content disposition and manifest metadata.

### 6.3 Academic configuration

- reporting structure preview accepts no writes and returns fingerprint;
- apply requires edited proposal + fingerprint + reason;
- effective window/readiness by action/context;
- curriculum draft/copy/publish/archive;
- subject group and curriculum row CRUD on DRAFT only;
- assessment definitions on allowed period/curriculum state;
- teacher/reviewer queues.

### 6.4 Grades/results/publication

- roster packet GET contains row versions/readiness/window/assignment;
- batch/autosave returns per-row status;
- packet transition endpoint enforces state/reason/version;
- calculation preview is pure;
- validation persists immutable snapshot after readiness;
- publication issues document atomically;
- correction starts a new version while old one remains published;
- batch preview/start/status/retry/cancel/history/download;
- GET requests never cause calculation or state change.

### 6.5 Promotion/journey

- graph/rule preview/save/publish/history;
- pure promotion preview with fingerprint;
- batch create/list/detail;
- decision update/recalculate diff;
- commit preview and idempotent commit;
- cancel/correct/compensate/register;
- internal and parent-safe journey projections.

Every non-2xx response must use a shared envelope containing at least:

- stable `code`;
- localized/user-ready `message` or message key plus parameters;
- `fieldErrors` where applicable;
- `conflicts` with entity IDs and display labels;
- `blockers` with repair route/action;
- correlation ID;
- current/stale version when optimistic locking fails.

## 7. Required frontend information architecture and UX

### 7.1 Settings

`Settings → Scolarité` should contain separate, understandable sub-tabs:

1. Sections and classes.
2. Subject catalog defaults.
3. Class curriculum:
   - session and class selectors;
   - curriculum version/status;
   - add/remove class subjects;
   - class coefficient;
   - group/rules/display settings.
4. Teaching assignments:
   - primary homeroom;
   - secondary responsible teachers;
   - effective dates and validation.
5. Template/branding versions.

`Settings → Années & périodes` should contain:

1. Session lifecycle.
2. Terms.
3. Result structure/dependencies.
4. Workflow windows with effective inheritance/timeline.
5. Calendar/expected attendance generation with a human explanation and preview counts; no unexplained hash.

### 7.2 Timetable

1. Version/status/effective date at the top.
2. Class planner with locked resolved teacher.
3. Master/teacher/room/substitution views.
4. Conflict messages in context.
5. Published schedules read-only; new draft action explicit.

### 7.3 Academic

1. Teacher Gradebook.
2. Review Queue.
3. Results & Publication.
4. Attendance, Conduct & Council.
5. Documents & Batch Generation.

Each page uses the selected global academic session but visibly displays it. Class and milestone selectors must not be duplicated by legacy sequence controls.

### 7.4 Promotions and Journey

1. Promotion Configuration: graph and rule versions.
2. Promotion Batches: history, pure preview, review, commit.
3. Student Journey: immutable timeline and documents.

Use one action vocabulary consistently: Preview (no write), Save draft, Submit, Review, Accept/Return, Validate, Publish, Correct, Commit.

## 8. End-to-end operational flow to document and prove

### 8.1 Before the school year

1. Admin creates 2026–2027 and its three terms.
2. Admin previews, edits, and applies S1–S6, T1–T3 Result, Annual, dependencies, optional COMP policy, and all windows.
3. Admin configures school calendar/holidays and expected attendance generation.
4. Admin copies/creates and publishes class curriculum versions, coefficients, groups, and remark/completeness rules.
5. Admin assigns primary homeroom and secondary responsible teachers.
6. Admin creates timetable draft from the prior session, resolves conflicts, publishes immutable version.
7. Attendance sees DAILY sessions for primary and period occurrences for secondary.

### 8.2 During a sequence

1. Teacher dashboard shows only assigned packets whose entry window is open.
2. Teacher enters score/status and subject remark with autosave/row feedback.
3. Submission window permits submit; packet locks.
4. Reviewer accepts or returns with reason.
5. Attendance is finalized and any corrections approved.
6. Readiness shows exact blockers.
7. Authorized staff validates the result snapshot and publishes the official Sequence document.
8. Parent receives and can download only the published document.

### 8.3 At trimester end

1. S1 remains frozen; S2 and optional COMP are completed.
2. T1 calculation consumes frozen child evidence.
3. Class statistics, ranks, subject remarks, attendance, conduct, and council inputs are reviewed.
4. T1 snapshot is validated, published, batched, and included in PV.
5. The same pattern repeats for T2 and T3.

### 8.4 At year end

1. T3 Result is completed and published as its own product.
2. Annual calculation consumes T1/T2/T3 snapshots and produces a distinct Annual result/document.
3. Council evidence is approved; Annual is validated and published.
4. Promotion preview uses only the published Annual snapshot plus the published graph/rule versions.
5. Admin reviews automatic recommendation, optionally overrides with permission/reason, and chooses only allowed target.
6. Commit creates PLANNED enrollment for target session and immutable promotion/journey events.
7. Source enrollment remains ACTIVE until close/effective transition.
8. Session transition completes source and activates target once.
9. Parent journey shows the published Annual and final promotion outcome without internal notes.

## 9. Exact live acceptance route checklist

Use admin/admin unless a role-specific test says otherwise.

1. `/settings`
   - Scolarité → Matières par classe / Curriculum;
   - verify session/class relationship coefficient;
   - verify primary homeroom and secondary subject teacher;
   - verify no duplicate groups and all required errors inline.
2. `/settings`
   - Années & périodes;
   - verify ten milestones, dependency graph, editable preview, all six workflow windows, inheritance source, timezone, and realistic live values;
   - cancel every confirmation and prove no mutation;
   - verify generation preview explains counts and identifiers.
3. `/timetable`
   - CE1: teacher is inherited homeroom and disabled;
   - 6ème: teacher changes by subject and is disabled;
   - published version remains unchanged after assignment edit;
   - teacher/room conflict gives exact details;
   - teacher and master views plus exports.
4. `/presence`
   - primary DAILY and secondary PERIOD behavior from current session/published timetable;
   - finalized coverage feeds academic input.
5. `/academic`
   - no legacy sequence duplicate controls;
   - teacher packet save/submit/review/return/accept;
   - subject remarks and inline blockers;
   - attendance/conduct approval;
   - sequence, term with COMP, T3, and Annual calculations;
   - validate/publish/correct lifecycle;
   - batch preview/job/history/download/PV;
   - generated PDF includes photo, class coefficient, subject remark, teacher, stats, attendance, council and branding.
6. Parent portal/API
   - published document visible;
   - drafts/raw grades/internal notes unavailable.
7. `/journey/promotions`
   - graph/rules version and complete mapping;
   - pure preview leaves row counts unchanged;
   - batch history survives reload;
   - automated decision explanation;
   - manual override with required permission/reason;
   - allowed alternate target only;
   - commit preview/blockers/idempotency/register;
   - PLANNED target enrollment semantics.
8. `/journey`
   - authorized promotion action visible;
   - authoritative immutable timeline and document links;
   - parent-safe projection tested with parent credentials.

## 10. Automated test matrix

### 10.1 Backend unit tests

- assignment resolution by model/date/role;
- sequence, term, optional COMP, annual formula;
- coefficient precedence;
- missing/absent/exempt policy;
- class statistics and standard-competition ranking;
- window inheritance/order/timezone;
- readiness blocker aggregation;
- progression graph validation;
- promotion rule explanation and boundaries;
- enrollment transition state machine.

### 10.2 Backend integration tests with PostgreSQL

- migration/backfill on representative legacy data;
- assignment uniqueness/effective overlap;
- concurrent timetable teacher/room/class collision;
- packet transition/optimistic lock/idempotency;
- correction while old published stays current;
- atomic publish rollback and outbox;
- batch restart/retry/cancel;
- pure preview no-write assertions;
- concurrent promotion commit and idempotent retry;
- PLANNED/ACTIVE/COMPLETED transitions;
- journey projection and parent redaction.

### 10.3 Frontend tests

- visible borders/labels/required markers and inline errors;
- disabled teacher with explanation/deep link;
- assignment, curriculum, and window forms;
- milestone-only academic context;
- row autosave partial failure behavior;
- packet/readiness/action-state rendering;
- custom confirmation cancel causes no request;
- batch history/reload/filter/detail;
- override reason/permission/target validation;
- structured API error mapping.

### 10.4 Browser end-to-end tests

Create role fixtures for admin, teacher, reviewer/principal, and parent. Cover the route checklist in section 9 with network/database assertions for no-write previews and canceled modals.

### 10.5 Document tests

- golden render for Primary Sequence/Term/Annual and Secondary Sequence/Term/Annual FR/EN variants;
- profile photo present and properly cropped;
- long student/subject/remark names;
- multi-page table header repeat and no clipping;
- Unicode French accents and bilingual headers;
- deterministic checksum or documented deterministic core;
- snapshot/template/version metadata matches manifest/PV.

## 11. Performance and security gates

- One class result calculation must fetch cohort inputs in bounded queries, not one recursive calculation per student.
- Batch worker must use bounded concurrency and transaction size.
- Every school/session/class/student query must enforce tenant/scope authorization.
- Teacher access derives from canonical effective assignment, never a client-supplied class/teacher ID alone.
- Parent access derives from active family links and published documents.
- Override, emergency window, publication, correction, timetable publish, substitution, promotion commit, and compensation require action permissions and audit.
- Logs and errors must not expose passwords, tokens, SQL, internal paths, or constraint names.

## 12. Completion gates by Linear story

### Timetable

- BAY-52: versioned session-aware structures, copy/archive/effective publication, immutable history.
- BAY-53: authoritative primary homeroom model, effective dates, assistants only if explicitly supported, same resolver everywhere.
- BAY-54: authoritative secondary responsible assignments, workload/availability/qualification, locked timetable field.
- BAY-55: strict class/teacher/room/availability/version/race validation with structured 409.
- BAY-56: master/class/teacher/room views and dated substitutions/cancellations.
- BAY-57: deterministic PDF/CSV/XLSX/iCal and regression evidence.

### BAY-10

- BAY-66: editable standard structure, dependencies, all workflow windows/timezone/inheritance/readiness/override, live configuration.
- BAY-33: only session/period/curriculum-scoped grade writes, exact traceability, row-safe autosave.
- BAY-34: canonical teacher packets, full state machine, persisted required remarks, queues/audit.
- BAY-35: pure authoritative formula/snapshots/ranks/stats/policies with correct coefficient and frozen evidence.
- BAY-36: controlled template versions and reference-derived deterministic documents with photo/remarks.
- BAY-37: strict readiness/lifecycle, atomic publication/outbox, safe correction, parent published-only access.
- BAY-38: pure eligibility preview, durable resumable/cancellable jobs, manifest/ZIP/PV and live Docker proof.
- BAY-67: finalized attendance coverage, approved adjustments/conduct/council and shared snapshot DTO.

### BAY-11

- BAY-39: versioned validated multi-edge progression graph, alternatives/terminal/copy/preview.
- BAY-40: versioned explainable rules using published Annual and optional approved evidence only.
- BAY-41: durable review workspace/history, dedicated override permission/reason, target validation, no enrollment changes.
- BAY-42: fully revalidated/locked/idempotent commit, PLANNED transition, immutable events/outbox/register/recovery.
- BAY-43: authoritative immutable journey projection and parent-safe access.

## 13. Final definition of done

The combined work is done only when:

1. Timetable, grade packets, reports, attendance ownership, and teacher dashboard agree on one teacher assignment for a date.
2. Published timetable history cannot change because a teacher assignment changes later.
3. Class curriculum coefficient is the value used in calculation and printed snapshot.
4. Standard S1–S6/T1–T3/Annual dependencies and realistic windows are configured in the live 2026–2027 session.
5. No old sequence selector or legacy writable grade path competes with reporting periods.
6. Sequence, term, T3, and Annual results are mathematically verified from frozen evidence.
7. Official report cards reproduce required reference content, include profile photo and subject remarks, and pass visual QA.
8. Publication and correction are transactional, audited, parent-safe, and document-backed.
9. Promotion preview is read-only, automated decisions are explainable, override is permissioned/reasoned, mapping is validated, and commit is idempotent.
10. Promotion into a future DRAFT session creates PLANNED enrollment without prematurely ending the current enrollment.
11. Journey is an immutable projection with parent-safe redaction.
12. All specified automated suites pass, Docker is healthy, live routes are exercised, and Linear contains evidence for every acceptance item.

Do not replace these gates with a statement that an endpoint, migration, button, or demo row exists. Completion is observable end-to-end behavior with historical integrity, correct calculations, usable UX, and repeatable tests.
