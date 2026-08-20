# BAY-11 — Progression & Student Journey Implementation Report

## Delivered outcome

BAY-11 now provides an end-of-year promotion workflow that is separate from ordinary in-year class transfers. Administrators can define the next class for every source class, configure average thresholds, preview explainable recommendations, override any recommendation with a mandatory reason, and commit an entire promotion batch transactionally into the next academic session.

The existing student Journey timeline remains available and now receives immutable promotion decisions after a batch is committed.

## Screens and complete test flow

### 1. Academic sessions

Go to **Settings → Academic years & periods**.

- Confirm the source session exists, for example `Session 2026-2027`.
- Confirm the next session exists, for example `Session 2027-2028`.
- The target session must begin after the source session ends.

### 2. Student Journey timeline

Go to **Journey** (`/journey`).

- Select a student to view their multi-year timeline, averages, ranks, repeats, and decisions.
- Click **End-of-year promotions** to open the dedicated promotion workspace.
- A committed promotion entry shows the system recommendation, final administrative decision, target class, and override reason.
- Committed promotion entries cannot be manually edited or deleted.

### 3. Promotion rules and class progression

Go to **Journey → End-of-year promotions → Rules & paths** (`/journey/promotions`).

1. Select the source and target sessions.
2. Configure the general decision rule:
   - `average >= promotion threshold`: recommend **Promote**;
   - `average < review threshold`: recommend **Repeat**;
   - average between the two thresholds: **Needs review**;
   - missing required final average: **Needs review**.
3. For every current class, select its exact next class.
4. Mark final-year classes as **Terminal**. Their recommendation becomes **Graduate** and no target class is required.

Mappings are session-specific. Changing the hierarchy for a later year does not reinterpret a previously committed decision.

### 4. Preview and review

Open **Review & commit**.

1. Select the source session and next session.
2. Enter a batch name.
3. Click **Preview decisions**.

Preview is non-destructive. It creates a draft batch and shows:

- the student and current class;
- final average and its evidence source;
- mapped next class;
- recommendation;
- human-readable explanation;
- final decision;
- counters for Promote, Repeat, Graduate, and Needs review.

### 5. Manual override

Click **Decide** beside a student.

- Choose Promote, Repeat, Hold back, or Graduate.
- Choose the target class when the decision requires an enrollment.
- Enter a mandatory reason.
- Apply the decision.

The override uses optimistic locking and records the actor, time, reason, original recommendation, and resulting decision. It cannot silently overwrite another administrator’s newer change.

### 6. Commit the promotion batch

Click **Commit batch** after all Needs-review rows are resolved.

The confirmation dialog explains that the action will close source-session enrollments and create target-session enrollments. A commit reason is mandatory.

The backend commits all candidates in one transaction:

- unresolved decisions block the whole batch;
- missing target classes block the whole batch;
- an existing active target-session enrollment blocks the whole batch with the student’s name;
- source enrollments become `COMPLETED`;
- Promote/Repeat/Hold decisions create one target-session enrollment;
- Graduate creates no target enrollment;
- each new enrollment points to the previous enrollment;
- the Journey timeline is updated with immutable decision evidence;
- the batch becomes read-only;
- retrying an already committed batch returns the committed result instead of duplicating enrollments.

## Backend implementation

Flyway migration `V45__student_progression_workflow.sql` adds:

- `class_progression_path` — source class/session to target class/session mapping;
- `promotion_rule` — versioned thresholds by session with optional subsystem/level scope;
- `promotion_batch` — draft/committed/cancelled batch lifecycle and idempotency key;
- `promotion_decision` — recommendation, final decision, evidence, override, target, and committed enrollment;
- promotion metadata on `journey_entry`;
- progression/review/commit action permissions.

REST APIs are under `/api/journey/progression`:

- `GET/POST/DELETE /paths`;
- `GET/POST /rules`;
- `POST /batches/preview`;
- `GET /batches/{id}`;
- `PATCH /decisions/{id}`;
- `POST /batches/{id}/commit`.

Strict server checks cover tenant ownership, chronological sessions, subsystem-compatible mappings, thresholds, missing mappings/averages, stale versions, unresolved decisions, duplicate target enrollment, and committed-batch immutability.

## Frontend implementation

The Angular promotion workspace provides:

- clearly bordered and labelled fields;
- visible required-field indicators and inline error states;
- separate configuration and review steps;
- exact source/target session selectors;
- class mapping table and terminal-class controls;
- threshold explanation;
- recommendation KPI cards;
- detailed recommendation evidence;
- custom override and commit dialogs;
- mandatory override/commit reasons;
- status badges and read-only committed batches;
- a direct link between the timeline and promotion workspace.

No native browser prompt/confirm dialog is used in the new workflow.

## Demo data installed locally

The Docker demo database contains:

- `Session 2027-2028` as a draft target session;
- a default rule: Promote from `10/20`, Repeat below `8/20`, review in between;
- progression mappings for the available French and English class hierarchy;
- final-average examples producing automatic Promote and Needs-review outcomes.

Demo data is operational data in the local Docker volume; it is not embedded in the production migration.

An isolated live-verification student is also available:

- matricule: `TEST-PROMO-001`;
- source: `Class 1`, session `2026-2027`, final average `14.50/20`;
- committed destination: `Class 2`, session `2027-2028`.

Use this student in **Journey** to inspect a committed promotion without changing the CE1 attendance demonstration roster.

## Verification

- Backend Docker image compiles on Java 21.
- Flyway validated 45 migrations and applied V45 successfully on PostgreSQL 16.
- Angular production build passes.
- Angular test suite passes: 4 files, 7 tests.
- API tests verify preview/commit separation, override reason, and optimistic version payloads.
- Backend integration suite passes: 6 tests, 0 failures, 0 errors. It covers mapping, automatic recommendation, manual Hold override, transactional commit, target enrollment creation, and immutable Journey history.
- Live browser verification confirmed session loading, rule display, persisted class mapping, readable recommendation explanations, mixed recommendation preview, mandatory manual-override fields, and non-destructive draft behavior.
- The manual override was exercised live for `SONE Aminatou`: recommendation **Needs review** was changed to **Hold**, target `CE1`, with a mandatory council reason. The draft was deliberately not committed so the CE1 attendance demo remains intact.
- A separate one-student live batch was committed for `TEST-PROMO-001`. Database verification confirmed the source enrollment is `COMPLETED`, the target `Class 2` enrollment is `ACTIVE`, and the Journey timeline displays `PROMOTE -> Class 2` with locked promotion evidence.

## Safety and audit behavior

- Preview never changes enrollment.
- Manual override always requires a reason.
- Commit always requires a reason.
- Committed batches and their Journey decisions are immutable.
- Corrections must be represented as a new audited workflow/compensating action; history is not deleted.
- The unique active enrollment constraint prevents duplicate placement in a target session.
