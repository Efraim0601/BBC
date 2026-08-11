# Computed trimester and annual results — implementation handoff

## 0. Implementer directive

Read this document completely before changing code. Implement the entire scope on a new worktree branch created from `codex/report-card-fidelity` at or after commit `97287c2`.

This is not a cosmetic “show T1” patch. The defect crosses grade-entry authority, period dependencies, immutable report-card snapshots, stale drafts, report-card UX, PDFs, class statistics, and downstream correction safety. Preserve those boundaries.

Do not:

- require staff to manually create, validate, or publish one S1 and one S2 report card for every student before T1 can be calculated;
- add raw grade entry to `T1_RESULT`, `T2_RESULT`, `T3_RESULT`, or `ANNUAL`;
- hard-code `S1 + S2`, `S3 + S4`, or `S5 + S6` in the calculation service; use `academic_reporting_period_dependency`;
- mutate a persisted report-card snapshot JSON in place;
- delete the existing empty T1 draft from the live database;
- manually add/drop database columns;
- reintroduce per-action publication/validation/review windows;
- hide a source/workflow problem behind a generic “complete the information” message.

The implementation is complete only after backend tests, frontend tests, production builds, Docker deployment on ports 8085/8084, live browser verification with the existing 4ème data, and a coherent commit history.

## 1. User-visible objective

When staff enter sequence grades, the report-card screen must immediately calculate the relevant trimester from the sequences configured inside it.

For the standard configuration:

- T1 is calculated from S1 and S2;
- T2 is calculated from S3 and S4;
- T3 is calculated from S5 and S6;
- Annual is calculated from T1, T2, and T3;
- dependency weights come from `academic_reporting_period_dependency`;
- subject coefficients come from the class curriculum relationship, not the subject default;
- a report-card document lifecycle is separate from the academic result calculation lifecycle.

The staff member must be able to see provisional calculations while grade packets are still in progress, but only academically complete, authoritative inputs may become an official draft, validated report card, PDF, batch artifact, or published parent document.

## 2. Reproduced live defect

### 2.1 Environment

- Frontend: `http://localhost:8085/academic`
- Backend: `http://localhost:8084`
- Frontend container: `bbcomplex-simplified-frontend`
- Backend container: `bbcomplex-simplified-backend`
- Production-simulation database container: `bbcomplex-prodtest-db`
- Database host port: `5436`
- Current Flyway version before this work: `85`
- Login: `admin/admin`

### 2.2 Exact live scope

- Academic session: `2026-2027`
- Class: `4eme A`
- Student: `AMANTA EBOLO MARIE`
- Matricule: `BBC-1615`
- Reporting products: S1, S2, and `T1_RESULT`
- T1 dependencies: S1 weight `0.5`, S2 weight `0.5`

### 2.3 Existing sequence results

| Subject | Coef | S1 | S2 | Expected T1 |
|---|---:|---:|---:|---:|
| Etude de texte | 2 | 12.00 | 11.00 | 11.50 |
| Français | 3 | 11.00 | 12.00 | 11.50 |
| Géographie | 4 | 15.00 | 14.00 | 14.50 |
| Histoire | 4 | 5.00 | 17.00 | 11.00 |
| Orientation conseil | 1 | 11.00 | 16.00 | 13.50 |
| Sciences | 3 | 15.00 | 12.00 | 13.50 |
| Travail manuel | 1 | 17.00 | 17.00 | 17.00 |

The sequence previews correctly return:

- S1 overall average: `11.666666666667 / 20`;
- S2 overall average: `13.944444444444 / 20`.

The expected T1 weighted total is:

```text
(11.50 × 2)
+ (11.50 × 3)
+ (14.50 × 4)
+ (11.00 × 4)
+ (13.50 × 1)
+ (13.50 × 3)
+ (17.00 × 1)
= 230.50
```

Total coefficient is `18`, so the expected T1 average is:

```text
230.50 / 18 = 12.805555555556
```

Display it as `12.81 / 20`; retain the engine's unrounded decimal internally.

### 2.4 Actual broken result

Selecting T1 currently displays:

- formula banner `S1 × 0.5 + S2 × 0.5`;
- an incorrect header `BULLETIN — SÉQUENCE 1`;
- state `DRAFT` / awaiting validation;
- no subject lines;
- average `0`;
- generic repeated blocker text;
- a `Valider le bulletin` button even though the draft cannot be validly validated.

The database contains one old T1 draft for this student:

- bulletin version id: `8b42d40f-79f8-4c60-b481-8a309c5b1302`;
- state: `DRAFT`;
- average: `0`;
- blockers:
  - `S1:FROZEN_SNAPSHOT_REQUIRED`;
  - `S2:FROZEN_SNAPSHOT_REQUIRED`;
  - `Aucune note calculable dans les périodes précédentes`.

There are no S1 or S2 `bulletin_version` rows. The sequence report screen is correctly calculating transient previews directly from grades.

All seven S1 subject grade packets and all seven S2 subject grade packets are `ACCEPTED`. Therefore the academic inputs are already authoritative and T1 must be calculable without creating child bulletin documents.

## 3. Root cause

### 3.1 Backend source policy is wrong

`BulletinSnapshotService.calculatePeriod(...)` handles a computed period by calling `frozenChild(...)` for every dependency.

Current behavior:

- a trimester accepts only a `VALIDATED` or `PUBLISHED` child sequence `bulletin_version`;
- Annual accepts only `PUBLISHED` trimester `bulletin_version` rows;
- accepted grade packets are ignored;
- if no child bulletin version exists, `FROZEN_SNAPSHOT_REQUIRED` is emitted.

This incorrectly couples two different concerns:

1. Academic result authority: grades entered, submitted, reviewed, and accepted in grade packets.
2. Document authority: a report-card snapshot created, validated, printed, and published.

A calculated trimester is a mathematical result of its child periods. It is not a manually entered period and it must not depend on staff first publishing child documents.

### 3.2 Preview favors a stale draft

`BulletinSnapshotService.preview(...)` returns the latest `DRAFT`, `RETURNED`, `VALIDATED`, or `PUBLISHED` snapshot before calculating current inputs.

Once the empty T1 draft was created, every later preview returned that frozen empty JSON. Accepted S1/S2 grades could never become visible.

### 3.3 Draft creation persists blocked results

`calculate(...)` currently persists a `DRAFT` even when calculation blockers exist. This creates unusable durable rows and presents a validation action for a report that was never ready.

### 3.4 Draft validation does not detect source drift

`validate(...)` checks blockers frozen inside the stored JSON, but it does not compare the draft against current grades, grade packet workflow, curriculum coefficients, attendance, conduct, dependencies, or class statistics.

A draft can therefore become stale while still appearing validable.

### 3.5 Frontend is sequence-centric

The report header is hard-coded as `BULLETIN — SÉQUENCE {{ b.sequence }}`. For a calculated product, `periodSequence(...)` keeps the previously selected sequence number. T1 is therefore mislabeled as Sequence 1.

The table also hides child-period marks in a small line inside the subject cell instead of presenting proper S1/S2/T1 columns.

### 3.6 Blocker communication is incomplete

`bulletinBlockerLabel(...)` does not understand `FROZEN_SNAPSHOT_REQUIRED` or grade-packet workflow states. Multiple distinct failures collapse into the same generic text.

## 4. Non-negotiable product decisions

### 4.1 Calculation inputs and document states are separate

- Sequence grades and their grade-packet workflow are the source for academic calculations.
- Sequence, trimester, and annual report cards are immutable document snapshots of a calculation at a point in time.
- A child report card is not required to calculate its parent period.
- Creating or publishing a T1 report card must not create S1/S2 report cards as a side effect.

### 4.2 Computed periods never accept raw marks

- Grade entry remains available only for `SEQUENCE` periods.
- `TERM_RESULT` and `ANNUAL_RESULT` remain read-only formula products.
- The configured dependency graph is the only source of parent/child relationships and weights.

### 4.3 Provisional visibility, authoritative issuance

The current result preview may show mathematically available marks from packets in `DRAFT`, `SUBMITTED`, or `RETURNED`, but it must label them provisional and expose exact workflow blockers.

An official draft may be created/refreshed only when:

- every required dependency exists;
- every required class-subject grade packet in the underlying sequences is `ACCEPTED` or `LOCKED`;
- every mandatory assessment has an accepted scored/absent/exempt state according to existing engine rules;
- required subject remarks exist;
- all calculation blockers are resolved;
- existing administrative prerequisites required by the current report-card workflow are satisfied or explicitly surfaced before draft creation.

Do not convert a missing, absent, returned, or unreviewed value to zero.

### 4.4 Immutable official evidence

- Preview is always calculated from current sources.
- A persisted draft freezes its result and trace.
- `VALIDATED` and `PUBLISHED` snapshots remain immutable.
- If sources change after a draft is created, mark the relationship as stale in the response; do not overwrite JSON in place.
- Refreshing a stale draft creates a replacement row and marks the old draft `SUPERSEDED`.
- Existing history remains queryable and auditable.

### 4.5 No unnecessary schema migration

This design should use existing columns:

- `bulletin_version.snapshot_json` for result and source trace;
- `snapshot_hash` for deterministic source/result comparison;
- `supersedes_id` for draft replacement;
- `state`, `version`, and existing timestamps for lifecycle/optimistic locking;
- `audit_event` through `AuditService` for refresh history.

No schema change is expected. Extend the JSON payload backward-compatibly. If a genuinely unavoidable database change is discovered, add Flyway `V86__...sql` and migration tests; never alter the live database manually.

## 5. Domain behavior by product

### 5.1 Sequence

Inputs:

- class curriculum subjects active for the session/date;
- applicable assessment definitions for class + subject + sequence;
- student grades and value statuses;
- class-subject coefficient;
- subject remark configuration and comment;
- class-subject responsible teacher metadata;
- class/period/subject grade-packet workflow state.

Output:

- one line for every expected curriculum subject;
- normalized sequence mark per subject;
- coefficient and weighted total;
- evidence list per assessment;
- workflow/readiness issues;
- overall average and class statistics.

### 5.2 Trimester

Inputs:

- dependency rows for the selected `TERM_RESULT`;
- live child sequence calculations;
- dependency weights and optional flags;
- current class curriculum coefficient;
- trimester attendance/conduct/report inputs.

Output per subject:

- child period marks, for example S1 and S2;
- weighted trimester mark calculated with dependency weights;
- class coefficient;
- weighted subject total;
- calculated appreciation;
- trace of the child period and packet versions used.

Do not copy the last sequence remark and silently label it as a trimester teacher remark. Preserve child remarks in evidence. Until a dedicated trimester subject-remark workflow exists, use the computed appreciation for the T1 appreciation column and retain source remarks with their sequence labels in trace/detail UI.

### 5.3 Annual

Inputs:

- configured dependencies of `ANNUAL_RESULT`;
- live T1/T2/T3 calculations, each recursively sourced from sequences;
- configured term weights;
- class curriculum coefficient;
- annual attendance/conduct/council inputs.

Output per subject:

- T1, T2, and T3 marks;
- annual mark;
- coefficient and annual weighted total;
- annual overall average, class statistics, and promotion evidence.

Annual calculation must not require T1/T2/T3 report cards to be published. Official annual issuance still freezes all source evidence.

## 6. Calculation contract

### 6.1 Dependency traversal

Introduce a request-scoped calculation context with:

- a visited-period set for cycle protection;
- a cache keyed by `(studentId, reportingPeriodId)`;
- a dependency cache keyed by parent period;
- a packet-readiness cache keyed by `(classId, sequencePeriodId)`;
- deterministic ordering.

Pseudo-flow:

```text
calculateCurrent(student, period, context)
  if cached -> return cached
  if period in visited -> DEPENDENCY_CYCLE
  if SEQUENCE -> calculateSequenceFromGradesAndPackets
  if TERM_RESULT or ANNUAL_RESULT:
    load dependencies ordered by display_order then child code
    recursively calculate every child period
    combine each subject through AcademicCalculationEngine
    namespace child issues with child period code
  cache and return
```

Do not call `withClassStatistics(...)` recursively for every child. Calculate raw child products first; compute parent class statistics once for the selected product. Reuse the request cache while calculating peers.

### 6.2 Formula authority

For every parent:

```text
subjectResult = Σ(childSubjectMark × dependencyWeight)
                / Σ(includedDependencyWeight)
```

Use `AcademicCalculationEngine.term(...)` and `AcademicCalculationEngine.annual(...)`; extend their inputs only if needed. Do not round child values before aggregation.

For a missing required child subject result:

- keep available component values visible;
- set the final parent mark to `null` or the engine's provisional value according to existing engine semantics;
- mark the line and product incomplete;
- never silently renormalize away a required dependency unless the dependency row is explicitly `optional=true`.

### 6.3 Subject set

The expected subject set is the active class curriculum for the academic session, ordered by curriculum display order and subject code.

- Include a line even if no mark exists yet.
- Do not derive the subject list only from recorded grades.
- Do not include subjects from another subsystem/language path.
- Preserve subject grouping metadata.

### 6.4 Coefficient authority

Use `academic_curriculum_subject.coefficient` for the student's enrolled class/session.

`subject.coef` is only the default used when creating a curriculum assignment. It must not override an existing class-subject coefficient.

### 6.5 Rounding

- Preserve the engine's high-precision decimal during calculations and snapshot hashing.
- Round only for display/PDF formatting.
- Display subject marks and averages with at most two decimal places.
- Do not display values such as `11.666666666667` in staff UI or PDFs.

### 6.6 Rank and class statistics

- Calculate every peer with the same current formula and source-readiness rules.
- Only complete/authoritative peers count in official rank and success statistics.
- A provisional preview may show a provisional rank only if clearly labeled; otherwise show `—`.
- Use competition ranking for ties, matching `AcademicCalculationEngine.competitionRanks(...)`.
- Ensure one student's stale draft cannot affect current class statistics.

## 7. Grade-packet readiness contract

For each expected class-subject row in every underlying sequence, resolve `academic_grade_packet`.

| Packet state | Show marks in preview | Academic input readiness | Official draft allowed |
|---|---|---|---|
| missing | yes, if raw values exist, with warning | BLOCKED | no |
| DRAFT | yes | PROVISIONAL | no |
| SUBMITTED | yes | PROVISIONAL / pending review | no |
| RETURNED | yes | BLOCKED / correction required | no |
| ACCEPTED | yes | READY | yes, if no other blockers |
| LOCKED | yes | READY | yes, if no other blockers |

Add deterministic packet trace entries containing at least:

- packet id;
- class id;
- child reporting-period id and code;
- subject code;
- packet status;
- packet version;
- responsible teacher/assignment provenance where available;
- submitted/reviewed timestamps where available.

The trace is evidence. It is not rendered as a technical hash dump in the normal UI.

## 8. Persisted snapshot and stale-draft behavior

### 8.1 Preview selection

Refactor `preview(studentId, periodId)` to follow this order:

1. Calculate a deterministic current preview from current sources.
2. Locate the relevant latest persisted version.
3. If the latest version is `PUBLISHED` or `VALIDATED` and no active correction draft exists, return the official frozen version with metadata identifying it as official.
4. If the latest active draft/returned version has the same deterministic snapshot hash as the current calculation, return the persisted draft with `versionRelation=CURRENT`.
5. If the draft hash differs, return the current calculation as `PREVIEW`, plus metadata identifying the stale draft and the refresh action.
6. If no persisted version exists, return current `PREVIEW`.

The existing empty T1 draft must therefore stop hiding the current T1 calculation immediately after deployment.

### 8.2 Deterministic current hash

Build the hash from the serialized snapshot payload with stable ordering. It must include:

- period id/version/type/policy;
- dependency id/version/order/weight/optional flag;
- enrollment and class;
- curriculum row ids/versions/coefficients;
- assessment ids/versions;
- grade ids/versions/statuses/marks;
- packet ids/versions/statuses;
- subject comments and workflow statuses;
- teacher-assignment provenance;
- child calculated source hashes;
- attendance/conduct/report inputs;
- class statistics inputs/results;
- profile/template/branding evidence already present;
- calculation formula version.

Do not include generated timestamps, random ids, map iteration order, or other unstable values in the hash input.

### 8.3 Draft creation

Refactor `calculate(studentId, periodId)` so it does not persist a blocked draft.

- Calculate first.
- If academic/readiness blockers exist, return structured `BULLETIN_NOT_READY`; create no row.
- If an identical active draft exists, return it idempotently.
- If a different active draft exists, return structured `BULLETIN_DRAFT_STALE`; require the refresh endpoint.
- If a validated/published version governs the product, preserve it and require the correction flow rather than creating an unrelated draft.
- Otherwise persist one immutable `DRAFT` with the full current payload and trace.

### 8.4 Refresh endpoint

Add:

```http
POST /api/academic/bulletin-snapshots/{draftId}/refresh
Content-Type: application/json

{
  "version": 0,
  "reason": "Accepted S1 and S2 grades are now available"
}
```

Behavior:

1. Authorize the student/class and write role.
2. Require state `DRAFT` or `RETURNED`.
3. Require nonblank reason.
4. Enforce optimistic version.
5. Recalculate current sources.
6. Reject with precise blockers if current inputs are still not ready for an official draft.
7. Mark the old draft `SUPERSEDED` without changing its snapshot JSON/hash.
8. Create a replacement `DRAFT` with `supersedes_id=old.id`.
9. Carry forward the general appreciation unless the user explicitly clears it.
10. Record `BULLETIN_DRAFT_REFRESHED` through `AuditService`, including old/new ids and hashes, period/student, old/new average, and reason.
11. Return the replacement view.

Do not delete or update the old JSON. The existing T1 draft is the live acceptance case for this endpoint.

### 8.5 Validation and publication guards

Before validating a draft:

- recalculate the current deterministic hash;
- compare it with the draft hash;
- if different, reject with code `BULLETIN_DRAFT_STALE`, include the current/stored version metadata, and direct the UI to refresh;
- then enforce existing conduct/attendance/administrative prerequisites;
- never validate a stale or blocked draft.

Before publishing a validated report:

- ensure no source correction has made the validated evidence stale;
- if stale, require the existing explicit correction workflow;
- publish exactly the validated frozen evidence, not an implicit recalculation.

## 9. API response design

Keep the existing endpoint paths and append backward-compatible response fields. Do not force consumers to infer product type from `S1` string parsing.

Add to `BulletinSnapshotView`:

```text
reportingPeriodType: SEQUENCE | TERM_RESULT | ANNUAL_RESULT
product: SEQUENCE | TERM | ANNUAL
workflowMeta: BulletinWorkflowMetaView
issues: BulletinIssueView[]
```

Recommended nested DTOs:

```text
BulletinWorkflowMetaView
  inputReadiness: READY | PROVISIONAL | BLOCKED
  versionRelation: NONE | CURRENT | STALE | OFFICIAL
  currentSourceHash: string
  persistedVersionId: UUID?
  persistedVersionState: string?
  persistedVersionNumber: long?
  persistedSnapshotHash: string?
  persistedAverage: decimal?
  refreshRequired: boolean
  dependencies: DependencyReadinessView[]
  capabilities: BulletinCapabilitiesView

DependencyReadinessView
  periodId: UUID
  code: string
  label: string
  periodType: string
  weight: decimal
  optional: boolean
  readiness: READY | PROVISIONAL | BLOCKED
  expectedPacketCount: int
  acceptedPacketCount: int
  lockedPacketCount: int
  submittedPacketCount: int
  draftPacketCount: int
  returnedPacketCount: int
  missingPacketCount: int

BulletinCapabilitiesView
  canCreateDraft: boolean
  canRefreshDraft: boolean
  canValidate: boolean
  canPublish: boolean
  validationBlockers: string[]

BulletinIssueView
  code: string
  severity: INFO | WARNING | ERROR
  periodCode: string?
  subjectCode: string?
  messageFr: string
  messageEn: string
  repairTarget: string?
```

Keep the legacy flat `blockers` list during migration, but build the UI from structured `issues` when available.

Example stale-draft T1 preview after the fix:

```json
{
  "id": null,
  "reportingPeriodCode": "T1_RESULT",
  "reportingPeriodType": "TERM_RESULT",
  "product": "TERM",
  "state": "PREVIEW",
  "average": 12.805555555556,
  "complete": true,
  "lines": [
    {
      "subjectCode": "HIST",
      "coefficient": 4,
      "periodMarks": [
        { "periodCode": "S1", "mark": 5.0 },
        { "periodCode": "S2", "mark": 17.0 }
      ],
      "mark": 11.0,
      "weighted": 44.0
    }
  ],
  "workflowMeta": {
    "inputReadiness": "READY",
    "versionRelation": "STALE",
    "persistedVersionId": "8b42d40f-79f8-4c60-b481-8a309c5b1302",
    "persistedVersionState": "DRAFT",
    "persistedAverage": 0,
    "refreshRequired": true,
    "capabilities": {
      "canCreateDraft": false,
      "canRefreshDraft": true,
      "canValidate": false,
      "canPublish": false
    }
  }
}
```

## 10. Backend implementation map

### 10.1 `BulletinSnapshotService.java`

Refactor responsibilities instead of adding another conditional around `frozenChild(...)`.

Required changes:

1. Replace computed-period `frozenChild(...)` input selection with recursive current-source calculation.
2. Retain frozen version loading only for reading official historical documents and correction ancestry.
3. Add calculation context/caching/cycle protection.
4. Add packet readiness and namespaced issues.
5. Make `preview(...)` current-source-first and stale-aware.
6. Make draft creation blocker-safe and idempotent.
7. Add immutable refresh replacement.
8. Add hash comparison to validation/publication.
9. Extend snapshot trace with current dependency and packet evidence.
10. Ensure T1/T2/T3/Annual all use the same generic dependency traversal.

### 10.2 New focused service/classes

Prefer extracting testable responsibilities rather than making `BulletinSnapshotService` larger.

Recommended package-private or service classes:

- `BulletinCurrentCalculationService`: dependency traversal and calculation context;
- `BulletinSourceReadinessService`: expected packet matrix, workflow status, issues, traces;
- `BulletinSnapshotFingerprintService`: deterministic payload serialization/hash comparison;
- or equivalent cohesive names consistent with the project.

Keep one source of truth for calculations. PV, preview, draft, PDF, batch, and promotion evidence must not each implement their own formula.

### 10.3 `AcademicDtos.java`

Add the period type/product, workflow metadata, dependency readiness, capabilities, structured issues, and refresh request DTO.

Jackson compatibility requirements:

- old snapshot JSON without new trace fields must still deserialize;
- new optional record/list fields must default safely;
- old official reports must remain printable;
- unknown future JSON fields must not make historical snapshots unreadable.

### 10.4 `AcademicController.java`

Add the refresh endpoint and use the same authorization/role policy as draft creation and validation.

Return structured errors through `ApiException.blockers(...)`, `ApiException.staleVersion(...)`, or a new equally structured helper. Never return a bare generic conflict for a known business state.

### 10.5 `BulletinVersionRepository.java`

Add explicit queries if needed for:

- latest active draft/returned version;
- latest validated version;
- latest published version;
- correction draft for an official version;
- versions ordered deterministically by `created_at DESC, id DESC`.

Do not treat `SUPERSEDED` as the active latest version merely because it was created most recently.

### 10.6 `GradeEntryService.java`

After save/submit/review transitions:

- current previews must naturally change through current-source calculation;
- active downstream drafts become detectably stale through hash comparison;
- validated/published downstream products remain frozen;
- grade correction must not silently replace an official T1/Annual result.

Add precise downstream impact handling if current code allows accepted sequence corrections while parent official products exist. At minimum, return the affected parent product codes/versions and direct staff to the explicit report correction flow. Do not silently supersede a published report.

### 10.7 `AcademicCalculationEngine.java`

Preserve the pure engine. Add tests rather than business/database lookups.

Verify:

- configured unequal dependency weights;
- missing required child;
- optional child inclusion;
- exempt result behavior;
- no premature rounding;
- annual from three term calculations;
- competition rank ties.

### 10.8 Snapshot trace

Extend the existing private snapshot trace with live dependency sources while retaining legacy `childSnapshots` compatibility.

Recommended trace shape:

```text
DependencySourceTrace
  childPeriodId
  childPeriodCode
  childPeriodVersion
  dependencyWeight
  optional
  sourceKind: LIVE_SEQUENCE | LIVE_TERM | LEGACY_FROZEN
  sourceHash
  packetTraces[]
```

For new T1 snapshots, `sourceKind` should be `LIVE_SEQUENCE`, not `LEGACY_FROZEN`.

Increment formula version from `AcademicCalculationEngine/v1` to a clear new version such as `AcademicCalculationEngine/v2-live-dependencies`. Existing snapshots retain their old formula version.

## 11. Blocker and repair catalog

Support at least these precise conditions:

| Code | User-facing meaning | Repair path |
|---|---|---|
| `GRADE_PACKET_MISSING` | no workflow packet exists for this class/sequence/subject | Academic → Grade entry → selected sequence/class/subject |
| `GRADE_PACKET_DRAFT` | grades are saved but not sent | open grade entry and send to management |
| `GRADE_PACKET_SUBMITTED` | waiting for management review | open grade entry as reviewer |
| `GRADE_PACKET_RETURNED` | reviewer requested correction | open returned grade sheet |
| `ASSESSMENT_NOT_CONFIGURED` | expected subject has no applicable evaluation | Academic → Configure assessments |
| `MISSING` | mandatory student mark/status is missing | exact sequence/class/subject row |
| `ABSENT` | absence needs the configured academic treatment | exact grade cell / attendance evidence |
| `REMARK_REQUIRED` | required subject remark is empty | exact sequence/class/subject row |
| `DEPENDENCY_MISSING` | parent period lacks required configured child | Settings → Sessions & terms |
| `DEPENDENCY_CYCLE` | invalid dependency graph | Settings → Sessions & terms |
| `BULLETIN_DRAFT_STALE` | saved draft no longer matches current sources | refresh draft modal |
| `CONDUCT_NOT_APPROVED` | class-council/conduct input is not approved | Academic → Report inputs |
| `FROZEN_SNAPSHOT_REQUIRED` | legacy draft created under obsolete child-document rule | refresh legacy draft |

Each UI message must name the period and subject when available. Examples:

- `S1 — Français: grades are saved, but they have not been sent to management.`
- `S2 — Géographie: grades are waiting for management approval.`
- `T1: this draft was created before the accepted S1/S2 results. Refresh it before validation.`

Do not display an MD5/SHA hash as a primary user message. Hashes may appear only in an expandable technical/audit detail.

## 12. Frontend implementation map

### 12.1 `academic.api.ts`

- Extend `BulletinSnapshotView` and `BulletinView` with period type/product, workflow metadata, issues, and capabilities.
- Add `refreshBulletinDraft(...)`.
- Preserve nullability for transient preview ids.
- Do not model every product as a required numeric `sequence`.

### 12.2 `academic.ts` report-card state

Extract the duplicated snapshot-to-view mapping used by `loadBulletin()` and `applySnapshot()` into one mapper.

Replace `periodSequence(...)` fallback behavior with product-aware metadata:

- sequence title from sequence label/code;
- term title from reporting-period label or governing term;
- annual title from reporting-period label.

### 12.3 Correct header

Render:

- `BULLETIN — SÉQUENCE 1` for S1;
- `BULLETIN — 1er TRIMESTRE` or configured T1 label for `T1_RESULT`;
- equivalent T2/T3 labels;
- `BULLETIN ANNUEL` for Annual.

Do not infer T1 as sequence 1.

### 12.4 Product-aware marks table

For a sequence:

| Subject | Mark | Coef | Total | Appreciation |
|---|---:|---:|---:|---|

For T1:

| Subject | S1 | S2 | T1 average | Coef | Total | Appreciation |
|---|---:|---:|---:|---:|---:|---|

For T2 and T3, derive the child column labels from configured dependencies.

For Annual:

| Subject | T1 | T2 | T3 | Annual average | Coef | Total | Appreciation |
|---|---:|---:|---:|---:|---:|---:|---|

Requirements:

- columns come from `periodMarks` and dependency metadata, not hard-coded period numbers;
- missing child value displays `—`, never `0`;
- marks use two-decimal display formatting;
- mobile layout remains readable with horizontal overflow or a deliberate stacked card layout;
- borders, headers, inputs, and actions remain visually obvious;
- print layout matches the selected product.

### 12.5 Readiness panel

Directly under the formula banner, show one friendly row/card per dependency:

```text
S1  7/7 subjects accepted  Ready
S2  7/7 subjects accepted  Ready
```

Other examples:

```text
S2  5 accepted · 1 submitted · 1 draft  Provisional
```

Clicking a problem row should navigate to grade entry with session, period, class, and subject query parameters when a single repair target exists.

### 12.6 Stale draft recovery UX

For the live stale T1 draft, show an amber callout:

```text
This saved T1 draft was created before the accepted S1 and S2 grades.
The values below show the latest calculation (12.81/20). Refresh the
draft before validation. The previous version will remain in history.
```

Actions:

- `Refresh draft` — primary;
- `View saved draft` — secondary, read-only, if supported by existing `byId` endpoint;
- no Validate button while stale.

`Refresh draft` opens an application modal, never `window.confirm()` or `prompt()`.

Modal content:

- product/student/class;
- old average and current average;
- explanation that the old draft will be superseded, not deleted;
- required reason with visible border;
- red border and inline message if reason is empty;
- Cancel performs no request;
- Confirm shows progress and prevents duplicate submission;
- success reloads the new draft and announces the new average.

### 12.7 Action rules

- `Create draft` appears only when there is no active draft and capabilities allow it.
- If not ready, show a disabled button with nearby exact repair messages; do not rely only on a tooltip.
- `Refresh draft` replaces Create/Validate when stale.
- `Validate` appears/enables only for a current complete draft.
- `Publish` remains available only for a validated current version and uses the existing custom reason modal.
- PDF generation remains available only for validated/published snapshots.
- Print of a provisional preview must carry a visible `PROVISIONAL / APERÇU` watermark and cannot masquerade as an official report.

### 12.8 Communication quality

Every state-changing success message must say what happened. Examples:

- `T1 draft refreshed from 7 accepted S1 subjects and 7 accepted S2 subjects. Average: 12.81/20.`
- `T1 draft validated. It is now frozen and ready for publication.`

Never show a raw hash without a label and explanation.

## 13. PDF, batch, PV, parent portal, and promotion integration

### 13.1 PDF

`ReportCardPdfService` must:

- use product-aware title;
- render S1/S2/T1 or T1/T2/T3/Annual columns from `periodMarks`;
- use class-subject coefficient;
- round for display only;
- preserve profile photo and existing branding/template evidence;
- render only persisted validated/published snapshots as official documents;
- remain able to print historical v1 snapshots.

### 13.2 Class PV

`classPv(...)` must use the same current calculation service and dependency graph. T1 PV must not require child bulletins. It must clearly distinguish complete/authoritative students from provisional/blocked students.

### 13.3 Batch generation

Batch draft/PDF generation must:

- use the same readiness checks;
- persist only complete authoritative snapshots;
- return per-student structured blockers;
- not generate empty zero-average T1 drafts;
- remain idempotent.

### 13.4 Parent portal

Parents continue to see only `PUBLISHED` snapshots. A new provisional preview or replacement draft must never become visible automatically.

### 13.5 Promotion/Student Journey

Promotion rules using annual averages must consume a published annual snapshot when an official decision requires frozen evidence. The live Annual preview can still show current calculations for staff planning, but it must not silently replace official promotion evidence.

## 14. Security, audit, and concurrency

- Reuse current tenant checks and teacher/admin scope.
- Preview remains read-only.
- Draft create/refresh/validate/publish requires existing write permissions.
- Refresh requires optimistic version.
- Two concurrent refresh requests must produce at most one active replacement draft.
- Audit create, refresh, validate, publish, correction, and supersession.
- Do not log student grades or full snapshot JSON in normal application logs.
- Correlation ids and structured API error codes must remain available.

## 15. Performance requirements

The current class-statistics path can become N × dependencies × subjects queries. The implementation must avoid making it worse.

At minimum:

- cache period/dependency data per request;
- batch packet readiness by class + sequence period;
- reuse child calculations for the selected student and peers;
- preserve deterministic ordering;
- add a query-count or practical class-size test if feasible;
- verify a 50–60 student class preview remains responsive.

Do not use persisted child bulletins as a performance cache; that recreates the semantic defect.

## 16. Backend test plan

### 16.1 Pure engine tests

Extend `AcademicCalculationEngineTest` for:

1. T1 from two sequence values at `0.5/0.5`.
2. Unequal configured weights.
3. Missing required child blocks completeness.
4. Optional child does not block when absent.
5. Exempt subject behavior.
6. Annual from three calculated terms.
7. No premature rounding.
8. Competition ranking ties.

### 16.2 Service/integration fixture

Add a focused Testcontainers integration test, for example `BulletinComputedResultsIntegrationTest`, that creates:

- school/session/three terms;
- S1–S6, T1/T2/T3 result, and Annual periods;
- dependency rows;
- a 4ème class and at least two students;
- seven curriculum subjects with the live coefficients;
- one assessment per sequence/subject;
- grade rows matching the live example;
- packet rows in controlled states;
- required report inputs.

Cover:

1. S1/S2 accepted packets produce T1 without child bulletin versions.
2. T1 exact subject marks and overall `12.805555...`.
3. T1 trace contains S1/S2 packet ids/versions/statuses.
4. No S1/S2 bulletin rows are created as side effects.
5. Draft packet shows provisional T1 values but blocks official draft creation.
6. Submitted packet names pending review.
7. Returned packet names correction requirement.
8. Accepted/locked packets allow draft.
9. Missing required mark is not zero.
10. Class-subject coefficient overrides subject default.
11. Existing empty T1 draft yields current preview plus `versionRelation=STALE`.
12. Refresh supersedes old draft and creates one replacement.
13. Cancel is frontend-only and makes no request.
14. Concurrent/stale version refresh is rejected.
15. Validation rejects a changed source hash.
16. T2/T3 use configured children, not hard-coded S1/S2.
17. Annual recursively uses T1/T2/T3 calculations without published term bulletins.
18. Existing validated/published snapshots remain frozen and readable.
19. Parent-published endpoint returns only published evidence.
20. Class PV and batch use the same formula/readiness.

### 16.3 API error tests

Assert codes, blockers, and repair metadata for:

- `BULLETIN_NOT_READY`;
- `BULLETIN_DRAFT_STALE`;
- stale optimistic version;
- packet draft/submitted/returned/missing;
- missing dependency;
- conduct approval;
- unauthorized refresh.

## 17. Frontend test plan

Add focused Angular/Vitest tests for the academic report-card component/API.

Cover:

1. T1 header says trimester, not Sequence 1.
2. Annual header says Annual.
3. T1 renders S1, S2, T1 average, coefficient, total, appreciation columns.
4. Annual renders configured child columns.
5. Missing value renders `—`.
6. Values render to two decimals.
7. Dependency readiness counts are understandable.
8. Exact packet state messages render in French and English.
9. Stale draft callout shows old/current average.
10. Validate is absent/disabled while stale.
11. Refresh opens custom modal.
12. Empty reason gives red border and inline message.
13. Cancel sends no request.
14. Confirm sends id/version/reason once.
15. Successful refresh replaces preview with current draft.
16. Incomplete preview cannot create an official draft.
17. Snapshot mapping is implemented once, not duplicated.
18. Query-parameter repair link opens the correct grade-entry scope.
19. Official PDF/print buttons obey lifecycle state.
20. Mobile table remains usable.

Run:

```text
cd frontend
npm test -- --watch=false
npm run build
```

## 18. Exact live browser acceptance

Deploy the implementation into the existing acceptance stack and test through the UI, not only the API.

### 18.1 Baseline sequence checks

1. Open `http://localhost:8085/academic`.
2. Sign in with `admin/admin` if needed.
3. Open `Academic → Report cards`.
4. Select session `2026-2027`.
5. Select class `4eme A`.
6. Select `AMANTA EBOLO MARIE (BBC-1615)`.
7. Select S1.
8. Confirm the seven marks and average `11.67/20`.
9. Select S2.
10. Confirm the seven marks and average `13.94/20`.

### 18.2 T1 current result

1. Select T1.
2. Confirm the title is `1er trimestre`/configured T1 label, not Sequence 1.
3. Confirm readiness shows S1 `7/7 accepted` and S2 `7/7 accepted`.
4. Confirm every subject row shows S1, S2, and T1 values from section 2.3.
5. Confirm T1 average displays `12.81/20`.
6. Confirm coefficient total is `18` and weighted total is `230.50` where displayed.
7. Confirm the existing empty draft is reported as stale.
8. Confirm no Validate action is offered for the stale draft.
9. Confirm no generic repeated blocker appears.

### 18.3 Refresh the existing draft

1. Click `Refresh draft`.
2. Confirm the modal explains old average `0.00` and current `12.81`.
3. Submit empty reason and verify red border/inline error.
4. Click Cancel and verify no request/state change.
5. Reopen, enter a reason, and confirm.
6. Confirm success message names T1, accepted S1/S2 sources, and `12.81`.
7. Reload and confirm the replacement remains current.
8. Read-only DB verification: old id `8b42d40f-79f8-4c60-b481-8a309c5b1302` is `SUPERSEDED`; a new `DRAFT` references it through `supersedes_id`; old JSON/hash are unchanged.

### 18.4 Provisional workflow check

Use a separate subject/temporary test packet, then restore it:

1. Move one packet to DRAFT through the application workflow, not direct SQL.
2. Open T1.
3. Confirm marks remain visible as provisional.
4. Confirm the exact sequence/subject repair message.
5. Confirm create/refresh/validate is blocked.
6. Submit and accept the packet through the UI.
7. Confirm T1 becomes READY and stale metadata updates appropriately.

### 18.5 T2/T3/Annual smoke checks

With controlled mock data created through services/UI:

- assign multiple class subjects;
- create default assessments for S3–S6;
- enter and accept grades;
- verify T2 from S3/S4;
- verify T3 from S5/S6;
- verify Annual from T1/T2/T3;
- verify no child report card publication is required;
- verify class coefficients remain authoritative.

### 18.6 Official lifecycle

1. Complete/approve required conduct/report inputs.
2. Create or refresh a current T1 draft.
3. Validate it.
4. Generate official PDF.
5. Verify title/columns/profile photo/coefficient/average.
6. Publish with the existing custom reason modal.
7. Verify parent portal receives only the published frozen version.

## 19. Docker and deployment verification

1. Confirm clean branch before implementation.
2. Run backend focused tests, then full backend suite.
3. Run frontend tests and production build.
4. Rebuild backend/frontend images from the implementation branch.
5. Recreate only the acceptance application containers; keep the production-simulation database volume.
6. Confirm backend starts and Flyway remains at 85 unless an additive V86 was genuinely required.
7. Confirm health/API login.
8. Confirm frontend at 8085 and backend at 8084.
9. Run the complete browser acceptance above.
10. Inspect backend logs for errors/correlation ids.
11. Do not hand-edit database rows to make acceptance pass.

## 20. Suggested implementation phases and commits

### Phase 1 — tests and source-readiness model

- Add failing integration fixture for accepted S1/S2 → T1.
- Add packet readiness DTO/service and structured issues.
- Add pure engine edge tests.

Suggested commit: `test: define live computed-result contract`

### Phase 2 — current dependency calculation

- Implement recursive current-source traversal.
- Remove child bulletin prerequisite from term/annual calculation.
- Add deterministic source trace and request cache.
- Make PV use the same calculation.

Suggested commit: `feat: calculate terms from accepted sequence results`

### Phase 3 — snapshot lifecycle hardening

- Make preview current-source-first.
- Prevent blocked draft persistence.
- Add current/stale hash comparison.
- Add refresh endpoint, supersession, audit, and optimistic lock.
- Guard validation/publication against stale evidence.

Suggested commit: `feat: refresh stale report-card drafts safely`

### Phase 4 — report-card UX

- Add product-aware titles and tables.
- Add readiness panel and exact issue messages.
- Add custom refresh modal and capabilities-based actions.
- Remove duplicated snapshot mapping.

Suggested commit: `feat: explain computed report-card readiness`

### Phase 5 — documents, batch, and regression

- Update PDF/product columns.
- Verify batch/PV/parent/promotion boundaries.
- Add frontend and backend regressions.

Suggested commit: `test: verify trimester and annual report flow`

### Phase 6 — Docker/live acceptance

- Deploy acceptance stack.
- Exercise exact 4ème case.
- Refresh the legacy empty draft through the UI.
- Record commands, test counts, screenshots/DOM evidence, and final commit hashes.

Suggested commit only if deployment files/evidence are intentionally tracked: `chore: add computed-result acceptance overlay`

## 21. Definition of done

All statements must be true:

1. T1 calculates from configured S1/S2 dependencies without child bulletin versions.
2. T2 calculates from configured S3/S4 dependencies.
3. T3 calculates from configured S5/S6 dependencies.
4. Annual calculates from configured T1/T2/T3 dependencies.
5. No computed product accepts raw grade entry.
6. Accepted/locked packets are authoritative for official draft creation.
7. Draft/submitted/returned packets remain visible but explicitly provisional/blocked.
8. Missing values are never converted to zero.
9. Class-subject coefficient controls every computed total and PDF.
10. The live 4ème T1 preview displays the seven expected lines and `12.81/20`.
11. T1 is labeled as a trimester, never Sequence 1.
12. The old empty T1 draft no longer masks current results.
13. Stale drafts cannot be validated.
14. Refresh supersedes rather than deletes/mutates old evidence.
15. Blocked calculations create no unusable draft row.
16. Every known blocker names the relevant period/subject and repair action.
17. Dynamic report tables show source periods as real columns.
18. Preview, PV, batch, PDF, parent publication, and promotion use the correct common source/lifecycle boundary.
19. Historical validated/published snapshots remain readable and immutable.
20. No manual database schema/data workaround was used.
21. Backend tests pass.
22. Frontend tests and production build pass.
23. Docker acceptance on 8085/8084 passes.
24. The implementation branch has coherent commits and a clean worktree.

## 22. Non-goals

- Redesigning the entire academic period configuration wizard.
- Reintroducing action-specific date windows.
- Changing the six-sequence/three-trimester model.
- Adding raw T1/T2/T3/Annual grade-entry screens.
- Replacing the class-subject curriculum assignment feature.
- Rewriting all reference report-card templates beyond the product-aware data/header/columns required here.
- Automatically publishing any report.
- Deleting historical report versions.

## 23. Completion report required from the implementing task

The final report must include:

- branch name and all commit hashes;
- concise root-cause confirmation;
- files added/changed;
- whether a migration was needed and why;
- backend commands and test counts;
- frontend commands and test counts;
- Docker image/container/port details;
- Flyway version after deployment;
- exact live click path tested;
- exact 4ème S1/S2/T1 values observed;
- proof the stale draft was superseded, not deleted;
- screenshot or DOM evidence for T1 title, dynamic columns, readiness, and `12.81/20`;
- verification of T2/T3/Annual with mock data;
- confirmation that child bulletin publication is not required;
- confirmation that class-subject coefficients are used;
- any residual risk or explicitly deferred item.

