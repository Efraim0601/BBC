# Implementation handoff: safe grade drafts, reusable session configuration, and reusable class curricula

## 1. Handoff metadata

- Prepared: 2026-08-10
- Repository worktree inspected: C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex
- Branch: codex/report-card-fidelity
- Baseline commit: db52b4e fix: show configured evaluations clearly
- Live test application: http://localhost:8085
- Live backend health endpoint: http://localhost:8084/actuator/health
- Intended implementer: a fresh Codex task using gpt-5.6-luna with max reasoning

This is an implementation contract. It records verified live behavior, required domain decisions, exact user flows, APIs, persistence changes, test cases, and deployment acceptance criteria. The implementer must read it completely before editing code.

## 2. Outcome

Deliver one coherent “academic session rollover and readiness” workflow with four user-visible results:

1. A management user never loses entered marks merely because a responsible teacher is missing. The grade sheet opens with an actionable warning, and “Save without sending” persists a draft. Sending remains blocked until a valid canonical assignment exists.
2. An administrator can reuse the previous session’s terms, result milestones, dependencies, and workflow-window policies in a new session. The copied proposal opens as an editable preview before anything is written.
3. Workflow dates are optional. A configured boundary is enforced; an omitted boundary imposes no restriction on that side. The UI must explicitly distinguish inheritance, no restriction, and date-limited access.
4. An administrator can reuse a previous session’s “Class subjects” setup, including class-specific coefficients and optionally responsible/homeroom teachers. The copy is preview-first, editable, non-destructive by default, transactional, audited, and idempotent.

The final workflow must communicate what is ready, what is only a warning, what blocks an action, why it blocks, and exactly where the user can repair it.

## 3. Verified current behavior

### 3.1 Live evidence

The live application was inspected with admin/admin.

- Paramètres → Scolarité → Matières par classe → session 2026-2027 → 4eme A currently contains only three curriculum subjects: Français (FRANC), Histoire (HIST), and Travail manuel (TMAN).
- Every 4eme A subject row currently shows the placeholder “Enseignant RESPONSIBLE”; no responsible teacher is selected.
- A read-only database check confirms that 2026-2027 / 4eme A has 3 curriculum rows and 0 active responsible-teacher assignments.
- The source session 2025-2026 / 4eme A has 7 curriculum rows and 7 active responsible-teacher assignments. This is an ideal real-world fixture for validating the reuse feature.
- Paramètres → Années & périodes → 2026-2027 shows an OPEN, current session with no reporting structure and readiness blockers for S1-S6, T1_RESULT-T3_RESULT, and ANNUAL.
- Session 2025-2026 has three terms and the expected S1-S6, T1_RESULT-T3_RESULT, and ANNUAL milestones.
- The 2025-2026 readiness panel reports TEACHER_WINDOW_NOT_CONFIGURED for S1-S6.
- The period-window modal currently exposes grade entry, review, validation, publication, and correction dates, but it does not expose teacher-submission dates even though readiness requires them.
- Empty dates currently mean “inherit”; if no usable parent pair exists, AcademicWindowPolicyService returns WINDOW_NOT_CONFIGURED and blocks the action.

### 3.2 Grade-entry root cause

The failure is not a generic grade validation problem.

- GradeEntryService.view calls assertResolvedSubject before returning the roster.
- GradeEntryService.save calls the same assertion before writing any grade.
- GradeEntryService.submit also calls the same assertion.
- TeachingAssignmentResolver correctly identifies the missing 4eme A / Français assignment as RESPONSIBLE_ASSIGNMENT_MISSING.
- academic_grade.teacher_id and academic_grade_packet.teacher_id are already nullable, so a management-owned draft can be persisted without inventing a teacher.
- The frontend maps every teacher-related assignment error to the same generic sentence and offers only “Try again”; it does not provide a direct repair action.

Consequently, a teacher assignment that disappears or is not yet configured can invalidate a sheet after the user has already typed marks. The current implementation treats a submission-readiness prerequisite as a draft-storage prerequisite.

### 3.3 Relevant existing components to reuse

- TeachingAssignmentResolver is already the canonical assignment resolver used by timetable and grade entry.
- academic_curriculum_subject is already the session + class + subject authority for curriculum and report-card coefficient.
- SetupService and SetupApi already manage subject groups, curriculum rows, class-specific coefficients, secondary RESPONSIBLE assignments, and primary HOMEROOM assignments.
- The settings and academic setup screens already accept sessionId and classId query parameters in several child components.
- AcademicSessionService already supports standard structure preview/apply with a fingerprint and audit event.
- AssessmentDefaultsService already demonstrates the desired preview/apply, stale-fingerprint, idempotency, batch-summary, and row-validation pattern.
- IdempotencyService, AuditService, optimistic versions, and Flyway are already available.

## 4. Non-negotiable domain decisions

### 4.1 Teacher authority and drafts

1. academic_curriculum_subject remains the subject authority.
2. TeachingAssignmentResolver remains the only teacher authority:
   - Nursery/primary uses the active dated HOMEROOM assignment.
   - Secondary uses exactly one active dated RESPONSIBLE assignment for the class and subject.
3. Never fall back to an arbitrary class teacher, timetable teacher, current user, or employee with the same subject.
4. A missing, ambiguous, inactive, or out-of-range assignment is:
   - a warning and submission blocker for management;
   - an authorization blocker for a restricted teacher account;
   - not a blocker to saving a management-entered draft.
5. “Save without sending” must persist the entered marks and comments with:
   - workflow_status DRAFT;
   - entered_by equal to the authenticated user;
   - teacher_id null while no assignment is resolved;
   - a DRAFT packet whose teacher_id is also null.
6. “Send to Management” requires a resolved canonical teacher. It must be disabled before the user clicks it, with a visible explanation and repair action.
7. Do not add a silent “submit as teacher” fallback. An explicit management “submit on behalf” policy is outside this delivery; it would require a separate permission, mandatory reason, and audit semantics.
8. After an assignment is repaired:
   - existing draft marks remain intact;
   - a DRAFT or RETURNED packet adopts the resolved teacher and assignment provenance on its next load/save/submit;
   - grades/comments in that packet receive the resolved teacher_id without changing entered_by;
   - SUBMITTED, ACCEPTED, and LOCKED historical packets are not silently reassigned.

### 4.2 Session structure

1. Session start/end dates, term start/end dates, and reporting-period start/end dates remain mandatory. Attendance aggregation, report-card calculations, enrollment history, and period membership depend on them.
2. Reuse copies configuration, not academic facts.
3. Never copy old primary keys, versions, state transitions, fingerprints, or published status.
4. Reporting dependencies are mapped by stable period code within the target session, never by source UUID.
5. A copied reporting period starts in DRAFT even if its source was published.
6. Copy is non-destructive by default:
   - create missing target rows;
   - keep existing target values;
   - show differences;
   - require an explicit row/field choice before an existing value is replaced;
   - never delete target rows as an implicit consequence of reuse.

### 4.3 Workflow-window meaning

Each action at each scope has an explicit mode:

- INHERIT: use the nearest parent rule. Allowed for term and reporting-period scopes.
- UNRESTRICTED: the action has no time restriction at that scope.
- LIMITED: enforce whichever boundaries are supplied.

LIMITED supports all useful combinations:

| Opens | Closes | Effective behavior |
|---|---|---|
| empty | empty | Invalid LIMITED input; use UNRESTRICTED instead |
| supplied | empty | Block before Opens; remain open afterward |
| empty | supplied | Open immediately; close at Closes |
| supplied | supplied | Open from Opens through Closes |

Additional rules:

- When both LIMITED boundaries exist, Closes must be strictly after Opens.
- Opening and closing instants are inclusive, matching current behavior.
- Emergency overrides continue to take precedence.
- GRADE_ENTRY and TEACHER_SUBMISSION remain NOT_APPLICABLE for TERM_RESULT and ANNUAL_RESULT.
- Session scope cannot use INHERIT because it has no parent.
- A valid UNRESTRICTED rule is ready; it must not produce WINDOW_NOT_CONFIGURED.
- An inherited rule that resolves to UNRESTRICTED is also ready.

### 4.4 Curriculum reuse

1. subject.coef is only the catalog default.
2. academic_curriculum_subject.coefficient is the report-card coefficient and must be copied as the class-specific value.
3. Subject groups are session-scoped and are mapped by normalized group code.
4. Class IDs and subject IDs are persistent school catalog identities and should be used when present. The preview must still report missing/deleted/mismatched rows rather than guessing.
5. Copying teachers is optional and selected explicitly.
6. Teacher-copy validation must check:
   - employee still exists in the same school;
   - employee is active;
   - employee level is compatible with the class;
   - the source assignment role matches the class model;
   - the rebased effective dates remain inside the target session;
   - subject qualification warnings are surfaced for secondary teachers.
7. Never copy enrollments, evaluations, marks, comments, grade packets, report-card snapshots, attendance sessions, timetable versions/slots, promotion decisions, or generated documents.

## 5. Target interaction model

\`\`\`mermaid
flowchart TD
    A["Create or select target session"] --> B{"Starting point"}
    B -->|"Standard"| C["Existing standard academic wizard"]
    B -->|"Reuse previous"| D["Select source and scopes"]
    D --> E["Rebase dates and map period codes"]
    E --> F["Editable structure/window diff"]
    F --> G["Transactional apply"]
    G --> H["Reuse class-subject curriculum"]
    H --> I["Editable class/subject/teacher diff"]
    I --> J["Transactional apply"]
    J --> K["Readiness dashboard"]
    K --> L["Generate sequence evaluations"]
    L --> M["Enter and save grade draft"]
    M --> N{"Teacher resolved?"}
    N -->|"No"| O["Keep draft; repair class-subject assignment"]
    O --> M
    N -->|"Yes"| P["Submit to management"]
\`\`\`

## 6. Exact grade-entry user flow

### 6.1 Management user with missing teacher

1. Open Académique.
2. Open Saisie des notes.
3. Select 4eme A.
4. Select a sequence such as S1.
5. Select Français.
6. The roster opens. It must not be replaced by a generic error card.
7. Above the roster, show an amber readiness card:

   “No responsible teacher is assigned to Français for 4eme A in session 2026-2027. You can enter and save a draft, but the sheet cannot be sent until the assignment is configured.”

8. The card contains:
   - status badge: Teacher assignment missing;
   - action: Save draft and configure teacher;
   - secondary action: Configure teacher;
   - exact navigation hint: Paramètres → Scolarité → Matières par classe;
   - no UUID, fingerprint, SQL, or raw constraint text.
9. The user enters marks.
10. Save without sending remains enabled.
11. On save:
   - persist all valid rows;
   - show “Draft saved. Teacher assignment is still required before sending.”;
   - retain the roster and values on screen;
   - keep Send to Management disabled.
12. If the user chooses Save draft and configure teacher:
   - save first;
   - only navigate after the save succeeds;
   - open /settings with tab=academic, subtab=class-subjects, sessionId, classId, subjectId/subjectCode, and a returnUrl containing mode=grade-entry, classId, periodId, and subjectCode.
13. The Class subjects screen opens with session 2026-2027 and 4eme A selected, and scrolls/focuses the Français assignment row.
14. The user selects the RESPONSIBLE teacher and confirms the existing assignment-impact modal.
15. A “Return to grade sheet” action returns to the exact class, period, and subject.
16. The roster reloads with all draft values preserved and the resolved teacher shown.
17. Send to Management becomes enabled when marks/comments and the teacher assignment are ready.

### 6.2 Restricted teacher user

- A teacher sees only classes/subjects resolved to that employee.
- A missing assignment does not grant access merely because the teacher is linked through teacher_class or the timetable.
- A direct URL to an unassigned sheet returns a structured 403 with a readable message.
- Management can inspect and repair; a restricted teacher cannot self-assign.

### 6.3 Assignment-status communication

Extend the resolver and response vocabulary to distinguish at least:

- RESOLVED
- MISSING
- AMBIGUOUS
- OUTSIDE_EFFECTIVE_DATE
- EMPLOYEE_INACTIVE
- SECTION_MISMATCH or QUALIFICATION_WARNING when applicable

Each status must include:

- stable code;
- localized user message;
- class and subject context;
- resolved teacher/assignment identity when available;
- effective date range when relevant;
- repair target metadata.

Do not collapse every code into the same frontend string. The frontend can provide a friendly fallback, but the backend code/message remains authoritative.

## 7. Grade-entry backend implementation

### 7.1 DTO changes

In AcademicDtos, add compatible fields rather than removing the existing top-level teacher fields.

Add a TeacherAssignmentReadinessView containing:

- status and code;
- teacherId and teacherName;
- assignmentId and assignmentVersion;
- source and role;
- effectiveFrom and effectiveTo;
- messageFr and messageEn;
- repairable boolean.

Add a GradeEntryCapabilitiesView containing:

- canView;
- canEditDraft;
- canSubmit;
- canReview.

Extend GradeEntryView with:

- teacherAssignment;
- capabilities;
- completionBlockers;
- submissionBlockers;
- warnings.

Keep blockers temporarily as a compatibility alias for completionBlockers until all clients are migrated.

Represent blockers as structured objects in the new fields:

- code;
- field;
- messageFr/messageEn;
- repairTarget;
- severity.

### 7.2 GradeEntryService changes

Split the current all-or-nothing assertion into three operations:

1. resolveGradeEntryContext:
   - validate period is a SEQUENCE;
   - validate class and curriculum subject;
   - resolve teacher once through TeachingAssignmentResolver;
   - apply teacher-scope authorization;
   - return context and readiness.
2. assertDraftEditAccess:
   - management/staff with academic write can save despite unresolved assignment;
   - restricted teachers require RESOLVED and matching employee ID.
3. assertSubmissionReady:
   - require RESOLVED assignment;
   - require valid completion;
   - require TEACHER_SUBMISSION window to be effectively open.

Specific method behavior:

- view:
  - management receives the roster even if teacher readiness is not RESOLVED;
  - restricted teacher behavior remains strict;
  - do not call assertResolvedSubject unconditionally.
- save:
  - do not call assertResolvedSubject for management draft storage;
  - still validate curriculum, enrollment, assessment, mark bounds, versions, and GRADE_ENTRY/CORRECTION policy;
  - create/update a packet with null teacher when unresolved;
  - set entered_by to the actual user;
  - return warnings and submission blockers.
- submit:
  - call assertSubmissionReady;
  - update the DRAFT/RETURNED packet with current teacher/assignment provenance;
  - fill teacher_id on the packet’s grades/comments while preserving entered_by;
  - then transition to SUBMITTED.

If an assignment changed after a packet was submitted or accepted, do not rewrite history. Return a clear stale-assignment conflict and require management to return/open a correction before adopting the new assignment.

### 7.3 Packet provenance migration

Create a Flyway migration after V77. Do not alter production manually.

Add nullable columns to academic_grade_packet:

- responsible_assignment_id;
- responsible_assignment_version;
- last_saved_by;
- last_saved_at.

Add foreign keys where safe and indexes needed by the packet lookup. Backfill last_saved_at from updated_at; do not invent assignment provenance for historical packets.

No teacher column needs to become nullable; it already is nullable.

### 7.4 Controller security

- Keep grade-sheet viewing behind academic read + staffOnly.
- Keep draft save behind academic write + staffOnly.
- Use the existing GRADE_SUBMIT action permission for SUBMIT and preserve reviewer-role checks for ACCEPT/RETURN.
- Do not create GRADE_SUBMIT_ON_BEHALF in this delivery.

## 8. Exact session-configuration reuse flow

### 8.1 Entry points

Provide both:

1. After creating a session, show:
   - Configure from standard template;
   - Reuse a previous session.
2. For an existing selected session, show Reuse a previous session beside the Academic configuration wizard title.

The action is available for:

- DRAFT target sessions;
- OPEN target sessions only when the preview proves the requested changes do not overwrite structures already used by academic facts.

CLOSED and ARCHIVED targets are read-only.

For the current live data, 2026-2027 is OPEN but has no reporting periods. It must be eligible for a non-destructive reuse preview.

### 8.2 Wizard steps

Use the existing wizard shell and visual language.

#### Step 1 — Source and scope

- Target session is read-only and prominent.
- Source session selector defaults to the chronologically previous session.
- Never allow source=target.
- Scope checkboxes:
  - Terms;
  - Result milestones S1-S6, T1-T3, Annual;
  - Dependency graph/weights;
  - Workflow-window policies.
- Show source completeness counts before Next.

#### Step 2 — Terms and result dates

- Rebase source local dates by the calendar-day delta between source.startDate and target.startDate.
- Convert window Instants through each source timezone to local date/time, shift by the same calendar-day delta, then convert in the target timezone. Do not shift raw epoch seconds.
- Do not clamp dates silently.
- A date outside the target session/term gets a red border and row-level message.
- Show original source value and proposed target value side by side.
- Allow every proposed target date to be edited.
- Session, term, and reporting-period dates remain required.

#### Step 3 — Dependencies

- Show parent code, child code, weight, optional flag, and action.
- Map source codes to proposed target periods.
- Highlight unknown/duplicate/cyclic dependencies.
- Preserve the configured formulas rather than hard-coding S1/S2 arithmetic in the copy service.

#### Step 4 — Workflow windows

For every applicable action display:

- Mode: Inherit / No time restriction / Use dates;
- Opens (optional);
- Closes (optional);
- a plain-language effective result.

Examples:

- “No time restriction.”
- “Unavailable before 07 Sep 2026 07:00; open afterward.”
- “Open immediately; closes 25 Oct 2026 18:00.”
- “Open from 07 Sep 2026 07:00 through 25 Oct 2026 18:00.”
- “Inherited from T1: no time restriction.”

GRADE_ENTRY and TEACHER_SUBMISSION cards are disabled and labelled Not applicable for computed result periods.

#### Step 5 — Editable diff

Group rows into Terms, Result milestones, Dependencies, and Window rules.

Every row has one status:

- CREATE
- KEEP
- UPDATE
- CONFLICT
- SKIP

Default merge mode is Fill missing and preserve target. An UPDATE of an existing value requires an explicit checkbox or row action. Nothing is deleted.

Show friendly counts. Keep the preview fingerprint hidden from the normal UI; it may appear only in collapsed support details.

#### Step 6 — Confirmation

- Show a human summary of creates/updates/keeps/skips/conflicts.
- Require an in-app confirmation modal and reason.
- Cancel performs no write.
- Apply is one transaction.
- On success, reload the selected target session and show the structured readiness dashboard.

## 9. Session-copy APIs and service

Create DTOs in the foundation/session package and a dedicated AcademicConfigurationCopyService.

### 9.1 Preview

POST /api/settings/academic-sessions/{targetSessionId}/configuration-copy/preview

Request shape:

    {
      "sourceSessionId": "uuid",
      "dateStrategy": "SHIFT_FROM_SESSION_START",
      "mergeMode": "FILL_MISSING",
      "scopes": {
        "terms": true,
        "reportingPeriods": true,
        "dependencies": true,
        "workflowWindows": true
      },
      "edits": []
    }

Preview response includes:

- source and target summaries;
- proposed term rows;
- proposed reporting-period rows;
- proposed dependency rows;
- proposed window-rule rows;
- row statuses and field-level differences;
- warnings and blockers;
- counts;
- previewFingerprint.

Preview is read-only and must be proven by an integration test that compares row counts before and after.

### 9.2 Apply

POST /api/settings/academic-sessions/{targetSessionId}/configuration-copy/apply

Headers:

- Idempotency-Key: required/generated by the frontend for the confirmation attempt.

Request includes:

- sourceSessionId;
- selected row decisions and edited proposed values;
- previewFingerprint;
- reason.

The server must:

1. Lock/re-read source and target versions.
2. Recompute the preview and reject stale proposals with CONFIGURATION_COPY_PREVIEW_STALE.
3. Validate target mutability and academic-fact guards.
4. Upsert terms in temporary-safe display order.
5. Upsert reporting periods and build source-code → target-ID maps.
6. Upsert dependencies using target IDs.
7. Upsert workflow-window rules.
8. Recompute the target structure fingerprint.
9. Record one audit event and copy-run summary.
10. Commit all changes together or roll back all.

### 9.3 Used-data guards

Before an existing target row can be structurally changed, inspect:

- academic_assessment;
- academic_grade;
- academic_grade_packet;
- bulletin_version;
- finalized attendance sessions whose dates would move out of range;
- published timetable versions;
- promotion batches/decisions.

Creating a missing row can remain allowed. Changing a used code/type/date is a blocker with a specific message. Do not return a generic database conflict.

## 10. Workflow-window persistence and policy

### 10.1 New normalized rule table

Add a Flyway migration creating academic_workflow_window_rule with:

- id;
- school_id;
- academic_session_id;
- academic_term_id nullable;
- reporting_period_id nullable;
- scope_type: SESSION, TERM, PERIOD;
- action: GRADE_ENTRY, TEACHER_SUBMISSION, REVIEW, VALIDATION, PUBLICATION, CORRECTION;
- mode: INHERIT, UNRESTRICTED, LIMITED;
- opens_at nullable;
- closes_at nullable;
- timezone;
- version;
- created_at/updated_at;
- uniqueness on school + scope identity + action;
- checks ensuring the scope foreign-key shape matches scope_type;
- checks ensuring INHERIT/UNRESTRICTED have no endpoints and LIMITED has at least one endpoint.

Keep the existing wide date columns for backward compatibility in this release. Do not drop them.

### 10.2 Backfill

Preserve intent while adopting the user’s new rule:

- Session scope:
  - any existing endpoint → LIMITED;
  - both endpoints null → UNRESTRICTED.
- Term scope:
  - any existing endpoint → LIMITED;
  - both endpoints null → INHERIT.
- Reporting-period scope:
  - any existing endpoint → LIMITED;
  - both endpoints null → INHERIT.
- Current session/term publication fields continue to seed both VALIDATION and PUBLICATION, preserving current alias behavior.
- REVIEW and CORRECTION at session level backfill to UNRESTRICTED because no legacy parent configuration exists.

Make the backfill tenant-safe and idempotent with ON CONFLICT DO NOTHING.

### 10.3 Policy resolution

Refactor AcademicWindowPolicyService:

1. Reject raw actions on computed periods as NOT_APPLICABLE.
2. Check active emergency override.
3. Resolve period rule.
4. If INHERIT, resolve term.
5. If still INHERIT, resolve session.
6. Evaluate:
   - UNRESTRICTED → open=true, state=UNRESTRICTED;
   - LIMITED opens-only → SCHEDULED before open, OPEN afterward;
   - LIMITED closes-only → OPEN until close, CLOSED afterward;
   - LIMITED both → current bounded behavior.

Enrich EffectiveWindowView with:

- configuredMode;
- effectiveMode;
- inheritedFromScope;
- plain state;
- nextTransition;
- blockers.

WINDOW_NOT_CONFIGURED should remain only as a compatibility/corrupt-data safeguard, not the normal result of blank dates.

### 10.4 Window editor UI

Update:

- session editor;
- term editor;
- reporting-period modal;
- wizard step 4;
- readiness cards.

The reporting-period modal must include TEACHER_SUBMISSION, which is currently absent.

Every field must have a visible border. Invalid fields receive a red border, red helper text, and focus on the first invalid field. Dates lose their mandatory asterisk. The mode itself is mandatory and explains the behavior.

## 11. Exact class-subject reuse flow

### 11.1 Entry

1. Open Paramètres.
2. Open Scolarité.
3. Open Matières par classe.
4. Select the target session.
5. Click Reuse a previous session beside the Session selector.

If a class is already selected, default scope to This class. Otherwise default to All matching classes.

### 11.2 Wizard

#### Step 1 — Source and classes

- Source session defaults to the previous chronological session.
- Scope:
  - This class;
  - Selected classes;
  - All matching classes.
- Show source/target counts by class.

#### Step 2 — What to reuse

Checkboxes:

- Subject groups and display settings;
- Assigned subjects;
- Class-specific coefficient;
- maximum score;
- “Mark/status required to submit” flag;
- subject acquisition threshold;
- subject rank visibility;
- subject remark requirement;
- responsible/homeroom teachers.

Teachers are independently optional and clearly labelled.

#### Step 3 — Editable preview

Group by class, then subject. Columns:

- Include;
- Class;
- Subject code and name;
- Group;
- Display order;
- Coefficient;
- Max score;
- Required-to-submit;
- Acquisition threshold;
- Remark required;
- Source teacher;
- Proposed target teacher;
- Result status.

Only subjects assigned in the source curriculum appear. Subject-catalog defaults are not substituted for copied class coefficients.

Teacher validation warnings appear inline. Missing teacher does not block copying the subject; it creates a visible readiness warning.

#### Step 4 — Confirm

- Default behavior: add missing and preserve target.
- Existing changed rows require explicit update selection.
- No target row is deleted.
- Require a reason and in-app confirmation.
- On success show:
  - groups created/kept/updated;
  - curriculum rows created/kept/updated/skipped;
  - teachers copied/skipped/warning;
  - direct links to unresolved teacher rows;
  - action to generate default sequence evaluations.

## 12. Curriculum-copy APIs and backend

Create CurriculumCopyService and DTOs under setup.

### 12.1 Preview

POST /api/setup/curriculum-copy/preview

Request:

    {
      "sourceSessionId": "uuid",
      "targetSessionId": "uuid",
      "classIds": ["uuid"],
      "allMatchingClasses": false,
      "includeGroups": true,
      "includeTeachers": true,
      "mergeMode": "FILL_MISSING",
      "rows": []
    }

Response includes:

- source/target summaries;
- class mappings;
- group mappings;
- editable curriculum rows;
- teacher proposals;
- statuses, warnings, blockers;
- counts;
- previewFingerprint.

### 12.2 Apply

POST /api/setup/curriculum-copy/apply

Use Idempotency-Key and the same stale-preview pattern as AssessmentDefaultsService.

Apply order:

1. Validate/lock target session.
2. Upsert selected groups by code using collision-safe temporary ordering.
3. Build source-group-code → target-group-ID map.
4. Upsert selected curriculum rows.
5. Rebase active_from/active_to into the target session.
6. Apply valid selected teacher assignments:
   - primary HOMEROOM once per class;
   - secondary RESPONSIBLE once per curriculum subject.
7. Record copy summary and audit.
8. Return fresh curriculum/readiness data.

Extend CurriculumSubjectView and CurriculumSubjectUpsert to expose activeFrom and activeTo so the preview is honest.

Do not call the existing single-row methods in a way that commits partial work or repeatedly reloads the whole curriculum. Extract validation/upsert helpers that participate in the surrounding transaction.

## 13. Copy-run audit

Add an academic_configuration_copy_run table, or an equivalently durable append-only summary, containing:

- id;
- school_id;
- source_session_id;
- target_session_id;
- scope: SESSION_STRUCTURE or CURRICULUM;
- preview_fingerprint;
- idempotency_key;
- status;
- created/updated/skipped/warning counts;
- actor_user_id;
- reason;
- created_at;
- result_summary JSONB.

Do not display the fingerprint in the normal success message. Return a friendly run/reference number; keep the hash in support details and audit data.

## 14. Structured readiness dashboard

Extend SessionReadinessView compatibly. Keep legacy phase/blockers/actions while adding sections:

- Structure;
- Workflow windows;
- Class curricula;
- Teacher assignments;
- Evaluations;
- Timetable;

Each section contains:

- status: READY, WARNING, BLOCKED, NOT_APPLICABLE;
- plain-language summary;
- counts;
- issue items with code, class/subject/period labels, and repair target.

Rules:

- Missing standard periods is BLOCKED.
- Invalid window rule is BLOCKED.
- UNRESTRICTED window is READY.
- Missing curriculum for a class with active enrollments is BLOCKED for grade/report workflows.
- Missing teacher is WARNING for draft preparation but BLOCKED for teacher submission/timetable publication.
- Missing default evaluations is WARNING with a direct Generate evaluations action, not a reason to hide the curriculum.

Avoid raw blocker strings such as TEACHER_WINDOW_NOT_CONFIGURED:S1 as the primary UI. Stable codes may remain in support details.

## 15. Frontend implementation map

### 15.1 Files to update

- frontend/src/app/features/academic/academic.ts
- frontend/src/app/features/academic/academic.api.ts
- frontend/src/app/features/settings/settings.ts
- frontend/src/app/features/settings/foundation-settings.ts
- frontend/src/app/core/foundation.api.ts
- frontend/src/app/features/setup/academic-setup.ts
- frontend/src/app/core/setup.api.ts

Create focused child components if the existing standalone files become harder to reason about:

- session-configuration-copy wizard;
- workflow-window-rule editor;
- curriculum-copy wizard;
- grade-entry readiness banner.

### 15.2 Routing

- SettingsComponent must read tab from query parameters; it currently always starts on academic.
- AcademicSetupComponent already reads subtab, sessionId, and classId; extend it with subjectId/subjectCode and returnUrl focus behavior.
- AcademicComponent already reads mode, classId, and periodId; extend it with subjectCode.
- Preserve all selected context when navigating to repair and back.

### 15.3 Form experience

Apply the user’s established UX requirement:

- visible field borders in view/edit/add/preview forms;
- mandatory fields have an asterisk and helper copy;
- on attempted continuation, each missing/invalid field gets a red border and message;
- focus/scroll to the first invalid field;
- destructive or transactional actions use in-app modals, never browser prompt/confirm;
- buttons communicate why disabled;
- no raw hash, UUID, SQL constraint, or backend exception as the main message.

## 16. Backend implementation map

Primary files:

- backend/src/main/java/com/bbc/sms/academic/GradeEntryService.java
- backend/src/main/java/com/bbc/sms/academic/dto/AcademicDtos.java
- backend/src/main/java/com/bbc/sms/timetable/TeachingAssignmentResolver.java
- backend/src/main/java/com/bbc/sms/academic/AcademicController.java
- backend/src/main/java/com/bbc/sms/foundation/session/AcademicSessionService.java
- backend/src/main/java/com/bbc/sms/foundation/session/AcademicSessionController.java
- backend/src/main/java/com/bbc/sms/foundation/session/AcademicWindowPolicyService.java
- backend/src/main/java/com/bbc/sms/foundation/session/SessionDtos.java
- backend/src/main/java/com/bbc/sms/setup/SetupService.java
- backend/src/main/java/com/bbc/sms/setup/SetupController.java
- backend/src/main/java/com/bbc/sms/setup/dto/SetupDtos.java
- backend/src/main/java/com/bbc/sms/platform/security/PermissionActions.java if GRADE_SUBMIT is wired explicitly

New focused services/entities:

- AcademicConfigurationCopyService
- CurriculumCopyService
- AcademicWorkflowWindowRule and repository/service
- copy DTO classes rather than extending already large DTO records indefinitely

All queries must include school_id and validate source/target belong to the same tenant.

## 17. Implementation order

### Phase 0 — Protect the baseline

1. Confirm branch is codex/report-card-fidelity and worktree is clean.
2. Read this document completely.
3. Inspect existing uncommitted work before editing.
4. Never reset or overwrite unrelated user changes.

### Phase 1 — Fix grade draft safety first

1. Add assignment-readiness DTOs.
2. Refactor GradeEntryService authorization/readiness.
3. Permit management draft load/save with unresolved assignment.
4. Keep restricted teacher scope strict.
5. Add packet provenance migration.
6. Add structured submission blockers.
7. Implement warning/repair UX and return route.
8. Add backend and frontend tests before moving on.

### Phase 2 — Window-rule semantics

1. Add normalized rule migration/backfill.
2. Implement repository/service and compatibility mapping.
3. Refactor policy resolution.
4. Update readiness.
5. Update all window editors, including teacher submission.
6. Test unrestricted and one-sided boundaries.

### Phase 3 — Session structure reuse

1. Implement preview DTO/service/API.
2. Implement date/time rebasing and row diff.
3. Implement used-data guards.
4. Implement transactional/idempotent apply and audit.
5. Integrate the existing six-step wizard.

### Phase 4 — Curriculum reuse

1. Implement preview mapping and teacher validation.
2. Implement transactional/idempotent apply.
3. Add the Class subjects wizard and result summary.
4. Add direct evaluation-generation action.

### Phase 5 — Readiness, regression, deployment

1. Add structured readiness sections.
2. Run all backend and frontend tests.
3. Build both applications.
4. Rebuild/redeploy the local Docker test stack on ports 8085/8084 without replacing the production-source database.
5. Execute the browser acceptance flow below.
6. Write an implementation report with commits, migrations, tests, live URLs, data created, and rollback notes.

## 18. Backend test matrix

### 18.1 Grade entry

1. Management view with missing secondary teacher returns roster + MISSING readiness.
2. Management save with missing teacher persists marks, comments, packet, entered_by, and null teacher.
3. Missing teacher blocks SUBMIT with RESPONSIBLE_ASSIGNMENT_MISSING and no workflow mutation.
4. Restricted teacher cannot view/save an unresolved or other-teacher subject.
5. After assignment repair, saved marks survive; submit attaches teacher/assignment provenance.
6. Ambiguous assignments return AMBIGUOUS with repair details.
7. Assignment outside the period effective date returns OUTSIDE_EFFECTIVE_DATE.
8. Primary uses HOMEROOM, not a subject assignment.
9. Optimistic packet/grade versions still reject concurrent edits.
10. ACCEPTED/LOCKED packets are not silently reassigned.

### 18.2 Window policy

For every applicable action:

1. UNRESTRICTED is open.
2. LIMITED opens-only is scheduled before opening and open afterward.
3. LIMITED closes-only is open before closing and closed afterward.
4. LIMITED with both boundaries observes both.
5. Invalid order is rejected with field-level error.
6. Period INHERIT resolves term, then session.
7. Emergency override wins.
8. Computed-period raw actions stay NOT_APPLICABLE.
9. Migration preserves existing configured windows.
10. Existing blank session windows become explicit UNRESTRICTED.
11. Timezone conversion and exact boundary instants are deterministic.

### 18.3 Session reuse

1. Preview writes zero rows.
2. Source/target same session is rejected.
3. Rebased dates are correct and preserve local wall-clock times.
4. Out-of-range date is a row conflict, never silently clamped.
5. Dependency IDs are target IDs mapped by code.
6. Fill-missing does not overwrite existing target values.
7. Explicit selected update changes only selected fields.
8. Used academic facts block unsafe structural changes.
9. Stale fingerprint is rejected.
10. Same idempotency key/payload returns same result.
11. Same key/different payload is rejected.
12. Any row failure rolls back the whole apply.
13. Audit/copy run is recorded.

### 18.4 Curriculum reuse

1. Preview writes zero rows.
2. Class-specific coefficients are copied, not subject defaults.
3. Groups map by code to target IDs.
4. Existing target rows are preserved by default.
5. No target rows are deleted.
6. Inactive/missing/incompatible teacher is warned/skipped.
7. Primary copies one HOMEROOM; secondary copies RESPONSIBLE per subject.
8. Effective dates are rebased and bounded by target session.
9. Grades/evaluations/attendance/timetable/snapshots are never copied.
10. Idempotent retry creates no duplicates.
11. Transaction rollback leaves no partial groups/curricula/teachers.

Use Testcontainers PostgreSQL integration tests for migration, transaction, constraints, and policy behavior. Add focused unit tests only where no database semantics are involved.

## 19. Frontend test matrix

1. Missing teacher renders a warning while roster remains editable for management.
2. Save remains enabled; submit is disabled with visible reason.
3. Successful draft save preserves values and shows the correct warning.
4. Save-and-repair navigates only after save succeeds.
5. Return URL restores class, period, and subject.
6. Restricted-teacher error remains an authorization state, not an editable roster.
7. Session reuse source/scope validation marks required fields.
8. Preview edits update the proposed payload and diff.
9. No write API is called during preview.
10. Window modes show/hide fields correctly and explain one-sided behavior.
11. Computed period disables raw actions.
12. Curriculum preview shows only source-assigned subjects and the class coefficient.
13. Apply confirmation requires reason.
14. Stale preview and row validation errors appear next to the affected rows.
15. Raw fingerprints remain hidden from the primary UI.

## 20. Live browser acceptance flow

Use the Docker test application at http://localhost:8085 with admin/admin.

### 20.1 Session reuse

1. Open Paramètres → Années & périodes.
2. Select 2026-2027.
3. Click Reuse a previous session.
4. Confirm 2025-2026 is preselected.
5. Select terms, result milestones, dependencies, and workflow windows.
6. Verify preview proposes T1-T3, S1-S6, T1_RESULT-T3_RESULT, and ANNUAL.
7. Edit at least one proposed date.
8. Set one action to No time restriction.
9. Configure one opens-only and one closes-only rule.
10. Apply with a reason.
11. Verify the session reloads, periods exist, dependencies point to target IDs, and readiness does not call unrestricted windows missing.

### 20.2 Curriculum reuse

1. Open Paramètres → Scolarité → Matières par classe.
2. Select target session 2026-2027.
3. Click Reuse a previous session.
4. Select source 2025-2026 and class 4eme A.
5. Preview with teachers enabled.
6. Verify the source’s seven subjects appear and each coefficient comes from the class relationship.
7. Keep one target row unchanged and explicitly update another to prove merge behavior.
8. Apply with a reason.
9. Verify target rows and teacher assignments in the normal Class subjects tab.
10. Verify timetable still derives the same locked teacher.

### 20.3 Grade-draft safety

Create or retain one secondary subject with no responsible teacher.

1. Open Académique → Saisie des notes.
2. Select 4eme A, S1, and the unassigned subject.
3. Verify roster opens with an amber warning.
4. Enter at least two marks and one status such as Absent.
5. Click Save without sending.
6. Reload and verify all values remain.
7. Verify Send to Management is disabled and explains the missing assignment.
8. Click Save draft and configure teacher.
9. Assign the teacher in the focused Class subjects row.
10. Return to the grade sheet and verify values remain.
11. Submit to management.
12. Verify packet status is SUBMITTED and provenance identifies the canonical assignment.

### 20.4 Regression

- Generate default evaluations for a target sequence and verify they remain visible.
- Open timetable and verify its teacher field remains derived/disabled.
- Preview a sequence report card and confirm the copied class coefficient is used.
- Verify primary class grade/timetable resolution still uses the homeroom teacher.

## 21. Deployment and migration safety

- Latest baseline production migration is V77; allocate new migrations sequentially after it.
- Use Flyway only. Never add/drop/change a production column by hand.
- Run migration tests against:
  - a clean database;
  - a database at V77 with current production-like rows;
  - the local production-simulation clone.
- Keep old workflow-window columns for compatibility; do not drop them in this delivery.
- Do not mutate immutable report-card snapshots or historical submitted/accepted packets during backfill.
- Take/identify a rollback database snapshot before deploying the local test stack.
- Rollback is application-image rollback plus database restore; Flyway migrations are forward-only.

## 22. Definition of done

The implementation is complete only when all statements are true:

- Management can open and save a grade draft without a responsible teacher.
- No typed mark is lost when assignment readiness changes.
- A restricted teacher gains no extra access.
- Submit is pre-emptively and clearly blocked until assignment is resolved.
- The repair action opens the exact Class subjects row and returns to the exact sheet.
- Previous terms/result structure/dependencies/windows can be previewed, edited, and reused.
- Previous class-subject configurations can be previewed, edited, and reused.
- Empty workflow dates have explicit no-restriction semantics; one-sided limits work.
- Teacher-submission windows are editable where applicable.
- Copy operations are non-destructive by default, transactional, audited, stale-safe, and idempotent.
- Readiness uses human explanations and direct repair actions.
- No raw fingerprint or constraint is shown as the primary message.
- All schema changes are Flyway migrations.
- Backend tests, frontend tests, builds, Docker health, and live browser acceptance pass.
- The implementation report states exactly what changed and where the user clicks to test it.

## 23. Non-goals

- Copying grades, evaluations, attendance, timetables, snapshots, enrollments, promotions, or generated documents between sessions.
- Automatically creating missing classes or subjects.
- Silently selecting a teacher.
- Allowing management submission on behalf of an unknown teacher.
- Removing the legacy window columns in this delivery.
- Changing the sequence → trimester → annual calculation graph beyond copying its configured dependencies.

## 24. Implementer handoff prompt

Read this file completely. Implement every Definition of done item on branch codex/report-card-fidelity in your assigned worktree. Start with grade-draft safety, then explicit window rules, session reuse, curriculum reuse, readiness, and live verification. Preserve unrelated changes. Use Flyway only for schema evolution. Do not push unless the user asks. Commit coherent phases, rebuild the local Docker stack on 8085/8084, test with admin/admin, and finish with a detailed implementation report and exact click paths.
