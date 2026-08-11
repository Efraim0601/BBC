# Batch report-card generation diagnostics and recovery — implementation handoff

## 0. Implementer directive

Read this document completely before changing code. Implement the entire scope on a new worktree branch created from `codex/report-card-fidelity` at or after the commit that adds this handoff.

This is not merely a request to print the existing `error` column higher on the page. The reproduced problem crosses product policy, report-card lifecycle, eligibility checks, asynchronous job behavior, error contracts, retry semantics, archive semantics, localization, accessibility, and live operator guidance.

Do not:

- weaken the parent-publication boundary by placing a merely validated report card in a parent-visible official archive;
- silently treat a T1 publication as an S1 publication, or otherwise substitute one reporting period for another;
- create a report-card snapshot automatically when the administrator clicks batch generation;
- launch a job that is already known to have zero eligible students;
- show raw backend exception text as the primary user message;
- tell the user that both validated and published report cards are accepted while the worker accepts only published snapshots;
- retry every blocked row without rechecking whether its prerequisite changed;
- present a ZIP containing only a manifest or empty companion documents as a successful report-card archive;
- manually add or drop database columns; use a new additive Flyway migration if the chosen structured-result design requires schema changes;
- use `window.alert`, `window.confirm`, or `window.prompt` for any decision or recovery flow.

The implementation is complete only after focused backend and frontend tests, production builds, Docker deployment on the current acceptance ports, and live browser verification using the reproduced 4eme A/S1 student.

## 1. User-visible objective

An administrator must know whether a class is ready before starting batch generation. If one or more students are not ready, the screen must state exactly:

- who is affected;
- which selected milestone is affected;
- what lifecycle state currently exists;
- why that state is not eligible;
- where to go and what to do next;
- whether retrying now would change anything.

When a batch finishes, its result summary and primary reason must be visible without scrolling. A successful archive must mean that at least one actual student report-card PDF was generated. Technical failures and business prerequisites must remain visibly different.

For the official batch currently exposed in `Academic -> Batch generation`, only `PUBLISHED` snapshots are eligible. That rule matches the worker's parent-visible document registration and must be stated consistently in the API and UI.

## 2. Reproduced incident

### 2.1 Environment

- Frontend: `http://localhost:8085/academic`
- Backend: `http://localhost:8084`
- Database container: `bbcomplex-prodtest-db`
- Database: `bbc_sms`, host port `5436`
- Login used for browser reproduction: `admin/admin`
- Reproduction date: 2026-08-11

### 2.2 User-reported job

| Field | Value |
|---|---|
| Job id | `5d214176-d918-4a7b-93d4-0d0fb2b86f42` |
| Displayed prefix | `5d214176` |
| Session id | `1e84c67b-3648-4a73-8f87-19c398aae171` |
| Reporting period | `S1` (`c01fe125-f504-40e2-ae0a-96afe4502d1d`) |
| Class | `4eme A` (`5c96adba-acd9-4049-97e0-38ce20c55a90`) |
| Job status | `COMPLETED_ERRORS` |
| Total / processed | `1 / 1` |
| Published | `0` |
| Blocked | `1` |
| Technical errors | `0` |
| Archive advertised | yes, `1855 B` |
| Job-level last error | none |

### 2.3 Blocked item

| Field | Value |
|---|---|
| Item id | `0f9d2cf0-3445-4bf3-ac48-e0a0e7170d1e` |
| Student | `AMANTA EBOLO MARIE` |
| Matricule | `BBC-1615` |
| Student id | `80918b40-ca0d-4e4c-8f83-bea009b33504` |
| Status | `BLOCKED` |
| Attempts | `1` |
| Persisted error text | `No validated or published snapshot` |
| Snapshot / document / file | none |

### 2.4 Snapshot evidence

For this student and scope:

- S1 has no `bulletin_version` row;
- S2 has no `bulletin_version` row;
- the former T1 draft `8b42d40f-79f8-4c60-b481-8a309c5b1302` is `SUPERSEDED`;
- its replacement T1 version `1af4d802-...` is `PUBLISHED` with average `12.8056`;
- no T2, T3, or Annual version exists.

The selected batch period was S1. A published T1 snapshot cannot satisfy an S1 batch because report-card snapshots are period-specific immutable documents. The worker's decision to block this student is therefore correct under a published-only official export policy.

### 2.5 Independent live reproduction

A second batch was launched through the live UI with the same class and S1 scope. It created job prefix `5c830d8c` and produced the same result: one blocked student and the same raw reason.

The detail table did contain the reason, but its top edge was approximately `736 px` while the browser viewport was `720 px` high. The visible completed-job card stopped after the archive and retry buttons. Consequently, a user looking at the completion summary saw no reason and no cue that more information existed below the fold.

The second diagnostic job is test evidence only. Do not use its presence as an implementation fixture or delete historical jobs as part of this work.

## 3. Root-cause analysis

### 3.1 The underlying business prerequisite is legitimate

`ReportCardBatchJobWorker.processItem(...)` looks up an eligible snapshot for the exact student, class, and reporting period. The query at the time of reproduction requires:

```sql
v.state = 'PUBLISHED'
```

The resulting PDF is then registered through `OfficialDocumentService` with audience `PARENT`. A merely validated staff document must not become a parent-visible official document before publication.

Therefore the official asynchronous batch should remain published-only unless a separate staff-preview export mode is explicitly designed.

### 3.2 The method name and message contradict the actual query

The worker method is named `publishedOrValidated(...)`, and the blocked text says `No validated or published snapshot`, but the SQL accepts only `PUBLISHED`.

That creates three conflicting sources of truth:

1. The SQL and parent audience enforce published-only.
2. The method and failure string imply validated-or-published.
3. The frontend explanatory copy explicitly promises validated-or-published.

The legacy synchronous `ReportCardBatchService.render(...)` also falls back from `PUBLISHED` to `VALIDATED`, while the durable asynchronous worker does not. Two endpoints branded as batch generation therefore apply different eligibility policies.

### 3.3 Eligibility is discovered only after a job is created

`ReportCardBatchJobService.create(...)` validates class, period, and active roster, then inserts a queued row for every student. It does not determine whether any exact-period published snapshot exists.

For the reproduced scope, the server knew before queueing that zero of one students could produce an official PDF. Nevertheless it created a job, ran it, marked it completed with issues, and built an archive.

### 3.4 The summary omits item outcomes

The top summary contains counts and an optional job-level `lastError`. A business blocker is stored on the item, not on `job.lastError`, so no explanation appears in the summary.

The table renders `item.error` only after the summary and action row. On the reproduced viewport, that table was below the fold. There is no primary reason, “details below” cue, expanded blocker section, focus change, or repair action.

### 3.5 The only reason is raw internal English

`BulletinBatchItemView` exposes only a free-text `error`. The Angular template prints it directly. It contains neither a stable error code nor structured context such as current snapshot state or repair target. It cannot be translated reliably and cannot produce an actionable route.

### 3.6 Retry is not a recovery workflow

`retry(...)` requeues all rows in `ERROR` or `BLOCKED`, clears the error, and resets the job. It does not re-evaluate eligibility before enabling or launching. Clicking retry without publishing S1 only repeats the same block and increments attempts.

Business blockers and transient technical failures need different actions:

- a technical error may be retryable immediately;
- a missing publication must be repaired elsewhere first and only becomes retryable after a new eligibility check.

### 3.7 Empty archive semantics are misleading

`buildArchive(...)` always creates and stores a ZIP, even when no student PDF exists. This is why a `1855 B` archive is offered beside `Published 0`. The file can contain a manifest and generated companion artifacts, but it is not an archive of generated report cards.

### 3.8 `COMPLETED_ERRORS` is too coarse for presentation

The database status becomes `COMPLETED_ERRORS` when either an error or a blocker exists. In the reported case, `error_items=0`; no technical operation failed. The UI then shows “Completed with issues,” which does not tell the user whether the system crashed or a publication prerequisite is missing.

The persisted status may remain for backward compatibility, but the API/UI must derive a clear result category and human label from the counters and reason codes.

## 4. Product decisions

### 4.1 Official batch eligibility is published-only

The existing durable batch creates official PDFs and registers them for the parent audience. Its eligibility rule must be:

```text
exact school + exact student + exact enrollment class + exact reporting period
+ latest active bulletin_version in state PUBLISHED
```

Rename the lookup to `latestPublishedSnapshot(...)`. Every UI explanation, API capability, message, test, manifest, and method name must say `published`, not `validated or published`.

### 4.2 Validated staff exports are a separate concern

Do not quietly expand the official batch to accept `VALIDATED`. If staff need a pre-publication proof bundle later, design a distinct `STAFF PREVIEW` mode that:

- is clearly labeled as non-official;
- carries a visible draft/validated watermark;
- registers no parent-audience document;
- never appears in the parent portal;
- has separate permissions and audit semantics.

That mode is a non-goal for this fix.

### 4.3 Period identity is strict

S1, S2, T1, T2, T3, and Annual are distinct reporting products. A publication for one never satisfies another. The reason panel must name the selected product so the operator understands why an existing T1 report does not make S1 ready.

### 4.4 Batch generation does not create or publish snapshots

The batch exports existing official evidence. It must never auto-create, validate, or publish report-card versions. The repair flow sends the administrator to the report-card lifecycle screen with the affected scope preselected.

### 4.5 Preflight is authoritative but time-sensitive

The server must calculate readiness before generation and again inside the worker. Preflight improves guidance but is not a substitute for execution-time checks; another user can supersede or change state between preview and execution.

### 4.6 Business blockers and technical failures remain separate

- `BLOCKED`: a deterministic business prerequisite is unmet; normally needs user action outside the job.
- `ERROR`: PDF, storage, document registration, database, or unexpected technical work failed; may be immediately retryable.
- `PUBLISHED`: an exact published snapshot produced and registered a PDF successfully.

Never convert an unexpected exception into a business blocker.

## 5. Target operator flow

### 5.1 Select scope

1. Open `Academic`.
2. Open `Batch generation`.
3. Select the class.
4. Select the milestone/reporting period.
5. The page automatically requests readiness; no job is created yet.

### 5.2 Readiness panel

Place a full-width readiness panel directly below the scope selectors and above the generation button.

For the reproduced case it must say, in French mode, the equivalent of:

```text
S1 report-card batch — 4eme A

0 ready to generate
1 student needs publication

AMANTA EBOLO MARIE (BBC-1615)
No S1 report card has been published for this student.
Open Report cards, create or refresh the S1 draft, complete its required
information, validate it, then publish it. Return here and recheck readiness.
```

Actions:

- primary: `Open S1 report card`;
- secondary: `Recheck readiness`;
- optional: `Show all students` when the class is large.

The repair action must open the report-card tab with session, class, period, and student preselected. If query-parameter hydration does not exist, implement it as part of this scope.

### 5.3 Zero ready

When `readyCount == 0`:

- disable or replace `Start generation`;
- show `No published report card is ready` next to the disabled control, not only in a tooltip;
- do not call job creation;
- do not create a database job or archive;
- keep repair and recheck actions visible.

### 5.4 Partially ready

When at least one student is ready and at least one is blocked:

- button text must be `Generate N published report cards`;
- clicking opens an application confirmation modal;
- modal states that N PDFs will be generated and M students will be excluded;
- modal groups blocked reasons and allows viewing affected students;
- Cancel sends no request;
- Confirm sends the preview token/fingerprint and starts only the explicitly eligible set, while preserving a diagnostic record for excluded students according to the API contract below.

Choose one consistent implementation:

1. Preferred: create job items for the whole roster using the preflight outcome, with ready rows queued and known blockers terminal from creation. This preserves a complete audit of class scope without wasting worker attempts.
2. Acceptable: create items only for ready students and persist the excluded preflight rows in a separate structured job result. Do not silently omit excluded students.

The preferred approach requires no duplicate job for the blockers and makes the final manifest complete.

### 5.5 Fully ready

When every active student has an exact-period published snapshot:

- show `N of N published report cards ready`;
- allow immediate start;
- create one item per student;
- keep the current asynchronous progress behavior.

### 5.6 Completion

On completion, put the outcome callout before counters and actions. It must be visible in the initial viewport and announced through an `aria-live` region.

Examples:

```text
Generated 24 of 25 report cards.
1 student was skipped because the S1 report card is not published.
[Review blocked student] [Download 24 report cards]
```

```text
No report card was generated.
The S1 report card for AMANTA EBOLO MARIE is not published.
[Open S1 report card] [Recheck readiness]
```

Do not rely on the row table as the only explanation.

## 6. Backend API contract

### 6.1 Add a preflight endpoint

Add an endpoint adjacent to the durable batch endpoints, for example:

```http
POST /api/academic/bulletin-batch-jobs/preview
Content-Type: application/json

{
  "classId": "5c96adba-acd9-4049-97e0-38ce20c55a90",
  "reportingPeriodId": "c01fe125-f504-40e2-ae0a-96afe4502d1d",
  "locale": "fr"
}
```

Use POST because the response is a calculated scope snapshot and the request may later contain options. It remains read-only and must not create audit/job rows.

Suggested response:

```json
{
  "policy": "PUBLISHED_ONLY",
  "academicSessionId": "...",
  "academicSessionLabel": "2026-2027",
  "classId": "...",
  "className": "4eme A",
  "reportingPeriodId": "...",
  "reportingPeriodCode": "S1",
  "reportingPeriodLabel": "Sequence 1",
  "totalStudents": 1,
  "readyStudents": 0,
  "blockedStudents": 1,
  "reasonCounts": [
    { "code": "REPORT_NOT_CREATED", "count": 1 }
  ],
  "rows": [
    {
      "studentId": "80918b40-ca0d-4e4c-8f83-bea009b33504",
      "studentName": "AMANTA EBOLO MARIE",
      "matricule": "BBC-1615",
      "eligibility": "BLOCKED",
      "code": "REPORT_NOT_CREATED",
      "currentState": "NONE",
      "messageKey": "academic.batch.reportNotCreated",
      "messageArgs": { "periodCode": "S1" },
      "repairTarget": {
        "route": "/academic",
        "query": {
          "mode": "bulletin",
          "classId": "...",
          "reportingPeriodId": "...",
          "studentId": "..."
        }
      },
      "snapshot": null
    }
  ],
  "scopeFingerprint": "...",
  "generatedAt": "..."
}
```

### 6.2 Fingerprint the preview

Create a deterministic `scopeFingerprint` from at least:

- tenant/school;
- session;
- class;
- reporting period;
- ordered active roster student/enrollment ids and versions/statuses;
- each student's selected candidate snapshot id, version, state, hash, and lifecycle timestamp;
- policy version.

The create request must include the fingerprint returned by preview:

```json
{
  "classId": "...",
  "reportingPeriodId": "...",
  "locale": "fr",
  "scopeFingerprint": "...",
  "includeReadyStudentsWhenPartiallyBlocked": true
}
```

At creation, recompute eligibility transactionally:

- if scope is unchanged, create the job;
- if changed, return `409 BATCH_SCOPE_CHANGED` with a fresh preview payload;
- the frontend replaces the stale preview and asks the user to review it again;
- never use the client-provided row list as authority.

### 6.3 Structured reason model

Every preflight row and terminal item must expose stable fields:

- `code`;
- `category`: `BUSINESS_BLOCKER` or `TECHNICAL_ERROR`;
- `messageKey` and `messageArgs`, or a server-localized friendly message plus code;
- `currentState` where relevant;
- `retryableNow`;
- `repairTarget` where relevant;
- optional `correlationId` for technical errors;
- optional technical detail restricted to authorized diagnostics.

Minimum codes:

| Code | Category | Meaning | Immediate action |
|---|---|---|---|
| `REPORT_NOT_CREATED` | blocker | no exact-period version exists | open report card and create draft |
| `REPORT_DRAFT` | blocker | latest active exact-period version is draft | complete and validate |
| `REPORT_RETURNED` | blocker | report was returned for correction | correct, revalidate, publish |
| `REPORT_VALIDATED_NOT_PUBLISHED` | blocker | validated but not parent-visible | publish report card |
| `REPORT_SUPERSEDED_ONLY` | blocker | only superseded history exists | create/refresh active version |
| `REPORT_STALE` | blocker | active version no longer matches current source | refresh through report-card workflow |
| `REPORT_PUBLICATION_REVOKED` | blocker | publication is no longer eligible if supported | resolve correction/publication state |
| `SNAPSHOT_UNREADABLE` | technical | persisted snapshot cannot be loaded | inspect correlation id/support |
| `PDF_RENDER_FAILED` | technical | PDF rendering failed | retry after technical resolution |
| `DOCUMENT_REGISTRATION_FAILED` | technical | official-document registration failed | retry safely/idempotently |
| `STORAGE_FAILED` | technical | object storage failed | retry after storage recovery |
| `UNEXPECTED_GENERATION_ERROR` | technical | uncategorized exception | inspect correlation id |

Do not use a hash, stack trace, SQL text, or exception class as the friendly message.

### 6.4 Candidate-state resolution

Create one reusable eligibility service used by:

- preflight;
- job creation;
- worker execution-time validation;
- retry readiness;
- legacy endpoint alignment.

Suggested name: `ReportCardBatchEligibilityService`.

For each active enrollment:

1. Resolve all exact-period versions ordered by lifecycle recency.
2. If an active current `PUBLISHED` version exists, mark `READY` and capture immutable evidence.
3. Otherwise classify the most useful current state, not merely the newest arbitrary row.
4. Preserve history in diagnostics but never select `SUPERSEDED` as export evidence.
5. Confirm the snapshot enrollment belongs to the selected class/session.
6. Confirm the snapshot remains readable and allowed by the current correction/publication rules.

The eligibility result should carry snapshot id, snapshot version, snapshot hash, published timestamp, and state. The worker must compare these with the queued evidence before rendering. If they changed, reclassify rather than exporting an unintended version silently.

### 6.5 Item persistence

The current free-text `error` is insufficient. Implement one of these designs:

Preferred additive migration:

- `bulletin_batch_item.result_code varchar(64) null`;
- `bulletin_batch_item.result_details jsonb not null default '{}'::jsonb`;
- retain `error` as a legacy/debug summary, not UI contract;
- backfill existing `BLOCKED` rows whose exact error is `No validated or published snapshot` to `REPORT_NOT_PUBLISHED_LEGACY` or the best state derivable by a safe migration/service read;
- do not attempt complex tenant-wide lifecycle inference inside SQL migration if it risks an incorrect backfill;
- use the next unused Flyway version after checking the branch at implementation time (the investigated branch was at V85 before this handoff).

Alternative without migration:

- derive structured results on read through the eligibility service;
- preserve historic error text;
- document why durable reason/evidence is not required.

The preferred design is more auditable and supports correct historical manifests. If implemented, add migration and compatibility tests and never modify the database manually.

### 6.6 Job view aggregation

Extend `BulletinBatchJobView` with presentation-safe aggregate fields, for example:

- `resultCategory`: `RUNNING`, `SUCCESS`, `PARTIAL`, `BLOCKED`, `FAILED`, `CANCELLED`;
- `headlineCode`;
- `headlineArgs`;
- `reasonCounts`;
- `studentArchiveAvailable`;
- `diagnosticReportAvailable`;
- `retryableErrorItems`;
- `nowEligibleBlockedItems`;
- `stillBlockedItems`.

The existing database status can remain `COMPLETED_ERRORS` to avoid unnecessary migration, but the UI must never infer a vague label from it alone.

For the reported job, a backward-compatible read should produce approximately:

```text
resultCategory = BLOCKED
headline = No S1 report card was generated; 1 student has no published S1 report card
studentArchiveAvailable = false
diagnosticReportAvailable = true
```

### 6.7 Creation behavior

Refactor `ReportCardBatchJobService.create(...)`:

1. Validate tenant, authorization, class, period, and roster.
2. Recompute eligibility through the common service.
3. Validate fingerprint.
4. If `readyCount == 0`, return a structured `409 BATCH_NOT_READY` with preview; create no job.
5. If partial and the explicit partial-generation flag is absent, return `409 BATCH_PARTIALLY_READY` with preview.
6. Create the job and item rows using the current eligibility evidence.
7. Queue ready rows only.
8. Persist known blocked rows directly as `BLOCKED`, with `attempts=0`, structured code/details, and no misleading worker attempt.
9. Start worker after commit only when at least one queued item exists.
10. Audit the requested scope, ready count, blocked count, policy, and fingerprint without logging grades.

### 6.8 Worker behavior

Refactor `ReportCardBatchJobWorker`:

- rename `publishedOrValidated(...)` to `latestPublishedSnapshot(...)` or remove it in favor of the eligibility service;
- validate the exact queued snapshot evidence again before rendering;
- if publication disappeared or evidence changed, mark a structured blocker and refresh counts;
- use the queued immutable snapshot id when still eligible, not an unrelated newer arbitrary row;
- map known PDF/storage/document failures to structured technical codes;
- preserve a correlation id for unexpected errors;
- make registration/storage retry idempotent so retry does not create duplicate official documents;
- keep tenant and class boundaries in every query;
- never expose technical exception text directly to the normal user UI.

### 6.9 Retry endpoints

Replace blind `Retry failed rows` semantics with two explicit operations:

1. `Recheck blocked students`
   - reruns eligibility;
   - does not increment attempts for still-blocked rows;
   - returns updated counts and reasons;
   - queues only rows that have become eligible;
   - captures the newly selected published snapshot evidence.

2. `Retry technical errors`
   - applies only to `ERROR` rows marked retryable;
   - preserves previous attempt history in audit/details;
   - does not touch business-blocked rows.

An optional per-student endpoint may support the same two actions. The API must reject an inappropriate operation with a structured conflict response rather than resetting everything.

### 6.10 Archive behavior

Define two artifact concepts:

- `studentArchive`: contains one or more successfully generated official student PDFs plus a manifest;
- `diagnosticReport`: CSV/JSON containing every roster row, result code, friendly localized description, snapshot evidence, and repair scope. It contains no sensitive stack trace.

Rules:

- if `publishedItems == 0`, do not advertise or label any ZIP as a report-card archive;
- allow `Download diagnostic report` if useful;
- if partial, the student archive contains only successful PDFs and a clear `generation-report.csv` for exclusions;
- generate class statistics, PV, honor certificates, and other companion official artifacts only from actual successful snapshots and only when their semantics remain valid;
- do not create blank official companion PDFs for a zero-success job;
- report archive size in a friendly unit (`1.8 KB`, `2.4 MB`), not only raw bytes;
- keep SHA-256 and technical evidence in an expandable audit/detail area, not the primary summary.

### 6.11 Legacy synchronous endpoint

`POST /api/academic/classes/{classId}/bulletin-batch` currently has different eligibility behavior. Choose and document one migration path:

- preferred: mark it deprecated, remove all frontend use, and make it delegate to the same published-only eligibility service until removal;
- if an external contract requires it, preserve the endpoint but enforce the same published-only policy and structured manifest.

Do not leave one path accepting `VALIDATED` while another accepts only `PUBLISHED`.

## 7. Frontend implementation map

### 7.1 API models and methods

In `frontend/src/app/features/academic/academic.api.ts`:

- add `BulletinBatchPreviewView` and row/reason/repair-target types;
- extend job and item views with result code, category, message arguments, retryability, current state, repair target, and artifact capabilities;
- add `previewBulletinBatch(...)`;
- update `createBulletinBatchJob(...)` with fingerprint and partial confirmation;
- add separate `recheckBlockedBatchItems(...)` and `retryBatchErrors(...)` calls;
- retain compatibility defaults for old jobs whose structured fields are absent;
- type `CANCELLED` if the backend can return it; the current union omits it despite backend support.

### 7.2 Scope changes trigger preview

In `frontend/src/app/features/academic/academic.ts`:

- load preflight whenever selected class or reporting period changes;
- cancel/ignore stale requests when selection changes quickly;
- show a bordered skeleton/loading state rather than leaving the action ambiguous;
- clear the previous scope's job/readiness details immediately;
- disable generation until current preview completes;
- never use a prior class/period fingerprint for a new request.

### 7.3 Readiness card design

The card must visually separate:

- selected class and milestone;
- published-only policy explanation;
- readiness count;
- grouped blockers;
- affected-student detail;
- repair and recheck actions;
- generation action.

Inputs and controls need visible borders, hover/focus states, and labels. Mandatory scope selectors should show red borders and inline messages after attempted submission if missing.

Do not make the user infer that an unbordered label is an input. Follow the form UX standard already requested across the application.

### 7.4 Friendly message mapping

Map stable reason codes to French and English. Message composition must include period and student context where relevant.

Examples:

| Code | French intent | English intent |
|---|---|---|
| `REPORT_NOT_CREATED` | Aucun bulletin S1 n'a encore été créé pour cet élève. | No S1 report card has been created for this student. |
| `REPORT_DRAFT` | Le bulletin S1 est encore en brouillon. Finalisez-le puis validez-le. | The S1 report card is still a draft. Complete and validate it. |
| `REPORT_RETURNED` | Le bulletin S1 a été retourné pour correction. | The S1 report card was returned for correction. |
| `REPORT_VALIDATED_NOT_PUBLISHED` | Le bulletin S1 est validé mais pas encore publié. Publiez-le avant l'export officiel. | The S1 report card is validated but not published. Publish it before official export. |
| `REPORT_STALE` | Le bulletin S1 doit être actualisé avant publication. | The S1 report card must be refreshed before publication. |
| `PDF_RENDER_FAILED` | Le PDF n'a pas pu être créé. Réessayez; si le problème persiste, communiquez la référence. | The PDF could not be created. Retry; if it persists, share the reference. |

The raw `error` string may appear only inside a role-protected expandable technical detail, and only if it does not leak sensitive data.

### 7.5 Repair navigation

Implement query-parameter hydration on `/academic` if it is not already complete:

```text
/academic?mode=bulletin&classId=<id>&reportingPeriodId=<id>&studentId=<id>
```

On arrival:

1. load the relevant session from the period;
2. select `Report cards`;
3. select class;
4. select reporting period;
5. select student;
6. load the current preview/version;
7. focus the lifecycle/readiness callout;
8. preserve a return link to the original batch scope when practical.

Do not route merely to the generic Academic page and make the user rediscover all selections.

### 7.6 Completion card

Reorder the current markup:

1. status and headline;
2. friendly reason summary and repair actions;
3. progress/counts;
4. appropriate artifact and retry/recheck actions;
5. visible affected-student list or expandable section;
6. technical/audit detail last.

For blocked or partial results, default the affected-student list to expanded when the count is small. For large classes, show the first rows and an explicit `Show all N` action.

### 7.7 Status labels

Derive labels from `resultCategory`:

- `SUCCESS`: `Generation complete — N report cards`;
- `PARTIAL`: `Generation partially complete — M students need action`;
- `BLOCKED`: `Generation not started/completed — report cards need publication`;
- `FAILED`: `Generation failed — technical intervention may be required`;
- `CANCELLED`: `Generation cancelled`.

Do not label a blocker-only job as an error.

### 7.8 Action rules

- Show `Download report cards` only when `studentArchiveAvailable` is true.
- Show `Download diagnostic report` under its own label when available.
- Show `Recheck blocked students` only when blocked rows exist.
- Enable it regardless of cached readiness because the action itself is the recheck, but do not call it `retry`.
- Show `Retry technical errors` only for retryable technical errors.
- Disable buttons while their request is running and prevent duplicate submission.
- Preserve existing custom modal patterns for partial confirmation.
- Announce completion and refreshed readiness through `aria-live`.

### 7.9 Responsive and accessible behavior

- Keep the primary reason above the fold at common laptop viewport sizes.
- On mobile, render affected students as bordered cards or a deliberately scrollable table with visible column labels.
- Use more than color to distinguish blocked/error/success.
- Ensure focus moves to the result headline after asynchronous completion.
- Ensure repair/recheck controls are keyboard reachable.
- Give loading indicators accessible labels.
- Keep contrast and visible focus rings consistent with the rest of the application.

## 8. Backend implementation locations

The implementer must inspect current code before editing, but the confirmed touchpoints are:

- `backend/src/main/java/com/bbc/sms/academic/ReportCardBatchJobWorker.java`
  - published snapshot lookup;
  - terminal reason capture;
  - job counter/status update;
  - archive construction;
  - official document registration.
- `backend/src/main/java/com/bbc/sms/academic/ReportCardBatchJobService.java`
  - job creation;
  - item reads;
  - blind retry;
  - archive capability.
- `backend/src/main/java/com/bbc/sms/academic/ReportCardBatchService.java`
  - legacy synchronous policy mismatch.
- `backend/src/main/java/com/bbc/sms/academic/AcademicController.java`
  - preview, create, recheck, retry, and download contracts.
- academic DTO/request files for batch job views and items.
- database migration folder if structured fields are persisted.
- current security, tenant, audit, exception, storage, PDF, and official-document services; reuse them rather than bypassing them.

Suggested new backend units:

- `ReportCardBatchEligibilityService`;
- `BulletinBatchPreviewView` and nested records;
- `BulletinBatchResultCode` enum;
- request DTO for preview/create with fingerprint;
- focused eligibility and job integration tests.

Do not duplicate the lifecycle query independently in preview, worker, and retry.

## 9. Frontend implementation locations

Confirmed touchpoints:

- `frontend/src/app/features/academic/academic.ts`
  - current batch card around the `mode() === 'batch'` block;
  - start/poll/select/retry/download handlers;
  - status and item label helpers;
  - query-parameter hydration for the repair link.
- `frontend/src/app/features/academic/academic.api.ts`
  - job/item interfaces;
  - preview/create/recheck/retry API calls.
- existing shared modal, empty-state, card, icon, and notice components.
- academic component tests and API tests.

If the component is too large to keep the readiness and result flow maintainable, extract a focused standalone batch component. Do not perform a broad unrelated Academic-page rewrite.

## 10. Data, security, audit, and concurrency

### 10.1 Tenant and authorization

- Apply tenant scope to every snapshot, enrollment, class, period, item, and job query.
- Reuse `TeacherScopeService.assertClass(...)` and existing administrative permissions.
- A repair target must not grant access; the destination screen rechecks authorization.
- Do not expose other classes' students through preflight counts or search.

### 10.2 Audit

Audit at least:

- preflight is optional to audit; avoid noisy logs unless required;
- batch request with policy, class, period, fingerprint, ready/blocked counts;
- partial-generation confirmation;
- recheck result transitions;
- technical retry;
- archive/diagnostic artifact generation;
- cancellation through the existing flow.

Never log full report snapshot JSON or grades in normal application logs.

### 10.3 Concurrency

Cover these races:

- a report is published after preview but before create;
- a publication is corrected/superseded after preview but before worker processing;
- two users create the same batch scope concurrently;
- retry/recheck is clicked twice;
- official-document registration succeeds but the item update fails;
- archive construction starts while an item remains nonterminal.

Use fingerprint conflicts and idempotent document keys. Do not solve concurrency with broad table locks.

### 10.4 Historical compatibility

Existing jobs must remain readable and downloadable according to actual artifact content. For legacy rows:

- map the exact old blocker text to a friendly legacy reason;
- derive current repair guidance without rewriting history destructively;
- do not pretend an old empty ZIP contains report cards;
- preserve hashes, timestamps, item attempts, and generated documents.

## 11. Backend test plan

### 11.1 Eligibility service tests

Cover at least:

1. no exact-period version -> `REPORT_NOT_CREATED`;
2. S1 missing while T1 is published -> S1 remains blocked;
3. draft -> `REPORT_DRAFT`;
4. returned -> `REPORT_RETURNED`;
5. validated -> `REPORT_VALIDATED_NOT_PUBLISHED`;
6. published -> ready with snapshot evidence;
7. superseded-only history -> `REPORT_SUPERSEDED_ONLY`;
8. published snapshot for another class/enrollment -> not eligible;
9. published snapshot for another tenant -> never visible;
10. unreadable/corrupt snapshot -> technical classification;
11. multiple versions -> deterministic current eligible selection;
12. exact policy value is `PUBLISHED_ONLY`.

### 11.2 Preview endpoint tests

1. missing class/period gives field-specific validation.
2. unauthorized class is rejected.
3. empty roster is a friendly conflict.
4. zero ready returns rows/reason counts and creates no job/item.
5. partially ready returns correct counts and repair targets.
6. fully ready returns snapshot evidence and fingerprint.
7. locale does not change stable codes/fingerprint.
8. roster or snapshot lifecycle change changes fingerprint.

### 11.3 Job creation tests

1. zero-ready create returns `BATCH_NOT_READY` and inserts no rows.
2. partial create without confirmation returns `BATCH_PARTIALLY_READY`.
3. partial confirmed create queues ready rows and records blockers with attempts `0`.
4. all-ready create queues all students.
5. stale fingerprint returns `BATCH_SCOPE_CHANGED` and fresh preview.
6. create is tenant-safe and authorized.
7. asynchronous start occurs only after transaction commit.

### 11.4 Worker tests

1. queued published snapshot produces a PDF and parent-audience official document.
2. validated-only snapshot never produces a parent document.
3. publication state/evidence changed after queue -> structured blocker.
4. PDF exception -> `PDF_RENDER_FAILED`/`ERROR`.
5. storage exception -> `STORAGE_FAILED`/`ERROR`.
6. registration exception is idempotently retryable.
7. successful retry creates no duplicate official document.
8. counters and result category are correct for success, partial, blocked, and technical failure.

### 11.5 Retry/recheck tests

1. still-blocked row remains blocked and attempts do not increase.
2. newly published row becomes queued and then succeeds.
3. recheck does not touch technical error rows.
4. technical retry does not touch blocked rows.
5. per-item action respects job/class/tenant.
6. duplicate concurrent action is idempotent or returns a clear conflict.

### 11.6 Artifact tests

1. zero success -> no student archive capability.
2. zero success -> diagnostic report is available and names blockers.
3. partial success -> ZIP contains exactly successful student PDFs plus generation report.
4. full success -> all PDFs and manifest are present.
5. no blank PV/statistics/honor artifact for zero snapshots.
6. file names are safe and deterministic.
7. manifest carries stable codes and snapshot evidence.
8. hash and size match stored bytes.

### 11.7 Migration tests, if applicable

- migrate a schema ending at the previous version;
- verify new nullable/default columns;
- verify legacy row remains readable;
- verify exact legacy reason mapping;
- run Flyway from an existing production-like database without manual SQL.

## 12. Frontend test plan

Add focused Angular/Vitest tests covering:

1. class/period change calls preview and does not create a job;
2. preview loading state is visible and bordered;
3. zero ready disables generation and gives an inline reason;
4. S1 blocker names S1 even when T1 is published;
5. `REPORT_NOT_CREATED` maps to friendly French and English;
6. validated-not-published says publish, not retry;
7. repair action routes with mode/class/period/student parameters;
8. destination hydrates all selections;
9. partial generation opens a custom confirmation modal;
10. modal Cancel sends no create request;
11. modal Confirm sends fingerprint and partial flag once;
12. stale-fingerprint response refreshes preview;
13. completion reason renders above counters/table;
14. focus/`aria-live` behavior announces completion;
15. affected students are expanded for a small blocked count;
16. raw internal error is not the primary message;
17. `Download report cards` is hidden for zero success;
18. diagnostic download has a distinct label;
19. recheck and technical retry are separate controls;
20. still-blocked recheck does not claim another attempt;
21. legacy `COMPLETED_ERRORS` with blocker-only counters is labeled blocked, not failed;
22. `CANCELLED` status is typed and rendered;
23. responsive blocked rows remain readable;
24. duplicate button clicks are prevented.

Run the project's actual frontend test command and a production build. Do not substitute only a TypeScript compile if the project has component tests.

## 13. Exact live acceptance flow

Deploy into the current Docker acceptance stack and verify through the browser.

### 13.1 Reproduced S1 preflight

1. Open `http://localhost:8085/academic`.
2. Sign in with `admin/admin` if needed.
3. Open `Batch generation`.
4. Select class `4eme A`.
5. Select milestone `S1` / Sequence 1.
6. Confirm the readiness card appears before generation.
7. Confirm it shows `0 ready`, `1 needs action`.
8. Confirm it names `AMANTA EBOLO MARIE (BBC-1615)`.
9. Confirm it says no S1 report card is published; it must not say merely “validated or published snapshot missing.”
10. Confirm the primary reason is visible without scrolling at a common laptop viewport.
11. Confirm `Start generation` cannot create a doomed job.
12. Read-only database verification: no new job/item was inserted by preflight or disabled-start attempts.

### 13.2 Repair navigation

1. Click `Open S1 report card` for AMANTA.
2. Confirm the Academic page opens the Report cards mode.
3. Confirm session, 4eme A, S1, and AMANTA are already selected.
4. Confirm the screen explains the current S1 lifecycle state.
5. Do not auto-create or auto-publish anything.

### 13.3 Validated-only boundary

Using normal UI/service workflows and test data:

1. Create/refresh and validate an S1 report card but do not publish it.
2. Return to batch preflight.
3. Confirm the reason changes to `validated but not published`.
4. Confirm official generation remains unavailable for that student.
5. Confirm no parent-audience document was created.

### 13.4 Published success

1. Publish the S1 report card through the normal custom confirmation flow.
2. Return to batch preflight and click `Recheck readiness`.
3. Confirm `1 of 1 ready`.
4. Start generation.
5. Confirm result says one report card generated.
6. Download the report-card archive.
7. Inspect ZIP: it contains the student's PDF and manifest/report.
8. Open PDF and confirm it is for S1, AMANTA, and 4eme A.
9. Confirm generated official document references the exact published S1 snapshot.

### 13.5 Partial class case

Create a controlled class fixture through application APIs/UI with at least two students:

- one exact-period report published;
- one report draft or validated-only.

Verify:

1. preflight says one ready and one blocked;
2. the confirmation modal describes both counts;
3. Cancel creates no job;
4. Confirm creates a partial job;
5. one PDF succeeds and one diagnostic blocker is recorded without a worker attempt;
6. completion reason is above the fold;
7. archive contains one student PDF, not an empty PDF for the blocked student;
8. diagnostic report contains both students and stable outcome codes;
9. after publishing the second report, recheck queues only that student;
10. final archive/document behavior is coherent and idempotent.

### 13.6 Technical error case

Use a safe test double/integration fixture rather than damaging live storage:

1. force one deterministic PDF or storage failure;
2. confirm it is labeled technical error, not publication blocker;
3. confirm a correlation/reference id is visible;
4. confirm `Retry technical errors` is the offered action;
5. restore the dependency and retry;
6. confirm one official document only.

### 13.7 Historical reported job

Open job `5d214176` from generation history:

- friendly status must be blocker-only, not a technical failure;
- it must name S1 and AMANTA;
- it must explain that no published S1 snapshot existed;
- the old `1855 B` artifact must not be labeled as a report-card archive when it has zero student PDFs;
- the row's original attempts/timestamps remain intact.

## 14. Docker and verification checklist

1. Confirm implementation branch starts from the handoff commit on `codex/report-card-fidelity`.
2. Inspect `git status`; preserve unrelated user work.
3. Add failing tests before or with each behavior change.
4. Run focused backend tests.
5. Run the complete backend suite appropriate to the repository.
6. Run frontend tests.
7. Run frontend production build.
8. Check Flyway migration ordering if a schema change was chosen.
9. Rebuild backend/frontend Docker images from the implementation branch.
10. Recreate only application containers; preserve the production-simulation database volume.
11. Confirm backend health/login on port 8084.
12. Confirm frontend on port 8085.
13. Execute the exact live acceptance flow above.
14. Inspect backend logs for uncaught errors and tenant/security warnings.
15. Verify no manual database edits were used to make the flow pass.
16. Record test counts, image/container identifiers, Flyway version, and live evidence in the completion report.

## 15. Suggested implementation phases and commits

### Phase 1 — define policy and eligibility contract

- add eligibility service and stable reason codes;
- add tests for every snapshot lifecycle state;
- rename misleading published/validated code;
- align the legacy path.

Suggested commit: `feat: define published batch eligibility`

### Phase 2 — preflight and safe creation

- add preview endpoint/DTOs;
- add scope fingerprint;
- block zero-ready job creation;
- support explicit partial generation;
- persist known blockers without worker attempts.

Suggested commit: `feat: preview report-card batch readiness`

### Phase 3 — structured outcomes and recovery

- add additive migration if selected;
- expose structured item/job result contracts;
- split blocker recheck from technical retry;
- harden worker evidence and idempotency.

Suggested commit: `feat: make batch failures actionable`

### Phase 4 — artifact semantics

- distinguish student archive and diagnostic report;
- suppress empty official archive/companions;
- update manifest and historical compatibility.

Suggested commit: `fix: avoid empty report-card archives`

### Phase 5 — operator UX

- build readiness card and partial confirmation modal;
- render above-fold completion explanation;
- add repair navigation/hydration;
- localize stable codes;
- improve responsive/accessibility behavior.

Suggested commit: `feat: guide batch report-card recovery`

### Phase 6 — regression and live acceptance

- complete backend/frontend regression suites;
- deploy Docker stack;
- exercise S1 zero-ready, validated-only, published success, partial, retry, and historical job cases;
- document results.

Suggested commit only for intentionally tracked acceptance documentation: `test: verify report-card batch lifecycle`

## 16. Definition of done

All statements must be true:

1. The official asynchronous batch accepts only exact-period `PUBLISHED` snapshots.
2. UI, API, method names, errors, manifest, and tests state the same published-only policy.
3. A published T1 never makes S1 ready.
4. Selecting class/period performs a read-only preflight before job creation.
5. Zero-ready scope creates no job and no archive.
6. Partial scope requires an explicit custom confirmation.
7. Known blockers are recorded without pretending a worker attempted PDF generation.
8. Every blocker has a stable code, friendly localized message, current lifecycle state, and repair target.
9. The primary completion reason is visible above the fold.
10. Raw backend error text is not the normal user-facing explanation.
11. Business blockers and technical errors have different labels/actions.
12. Rechecking a still-blocked row does not blindly increment attempts.
13. Newly eligible rows can be generated without rerunning already successful rows.
14. Technical retry is idempotent and does not duplicate official documents.
15. No report-card archive is advertised when zero student PDFs exist.
16. Diagnostic artifacts are labeled distinctly from report-card archives.
17. Blank companion official documents are not generated for zero-success jobs.
18. Existing historical jobs remain readable and auditable.
19. Job `5d214176` displays the real S1/AMANTA reason clearly.
20. Repair navigation opens the exact report-card scope.
21. Parent visibility remains publication-gated.
22. Tenant/class authorization applies to preview, create, read, repair, retry, and downloads.
23. Backend tests pass.
24. Frontend tests and production build pass.
25. Docker live acceptance on 8085/8084 passes.
26. Any schema change is additive Flyway only; no manual database DDL/DML workaround is used.
27. The implementation task ends with coherent commits and a clean worktree.

## 17. Non-goals

- Automatically creating, validating, or publishing report cards from batch generation.
- Treating trimester/annual products as substitutes for sequence products.
- Designing the optional staff-only validated proof-bundle mode.
- Redesigning report-card calculations, coefficients, grade entry, or trimester dependencies.
- Changing parent-portal publication rules.
- Rebuilding the entire Academic page outside the batch and repair-navigation scope.
- Deleting old jobs, item history, snapshots, documents, or archives.

## 18. Completion report required from the implementing task

The implementing agent's final report must include:

- branch name and commit hashes;
- concise root-cause confirmation;
- chosen official eligibility policy and why;
- files added/changed;
- whether a Flyway migration was required and its version;
- backend commands, test suites, and test counts;
- frontend commands, test suites, and build result;
- Docker images/containers/ports used;
- Flyway version after deployment;
- exact live click path tested;
- evidence for job `5d214176` historical rendering;
- evidence that S1 remains blocked by a published T1;
- evidence for zero-ready, validated-only, published-success, partial, recheck, and technical-retry flows;
- ZIP entry listing for a successful and partial archive;
- proof zero-success does not advertise a report-card archive;
- proof repair links hydrate the exact report-card scope;
- confirmation that no parent document is generated before publication;
- confirmation that no manual database change was used;
- residual risks and deliberately deferred non-goals.
