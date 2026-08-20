# BAY-7 — Shared Foundation Implementation Report

**Date:** 2026-08-06  
**Branch:** `feature/BAY-7-shared-foundation-sessions-enrollment-security-documents`  
**Implementation commits:** `7fdf5bb` and `30c010b`  
**Deployment tested at:** `http://localhost:8082` (API on `http://localhost:8083`)

## 1. Executive summary

The complete BAY-7 epic and its six child stories were implemented as the shared foundation for academic sessions, historical student enrollment, expected teaching sessions, immutable audit records, idempotent commands, official server-side documents, and permission-based access.

The implementation is deployed in the local Docker stack and has been verified through backend integration tests, Angular tests and production build, Docker image builds, direct API checks, and browser acceptance testing with the administrator account.

The follow-up fixes requested after acceptance testing are also included:

- Native browser prompts were replaced by application modals for session state changes, term deletion, and calendar generation.
- Canceling a modal performs no request and leaves the record unchanged.
- Calendar preview and generation now explain their behavior, counts, and technical source hash.
- Student transfer validates dates in the UI and returns a precise backend conflict message.
- Migrated enrollment dates are repaired so they remain inside their academic-session date range.

## 2. Delivered Linear scope

| Ticket | Delivered result |
| --- | --- |
| BAY-15 | Managed academic sessions, terms, publication windows, lifecycle states, current-session selection, validation, audit reasons, and historical read-only behavior. |
| BAY-16 | Session-aware student enrollments, class history, transfer and withdrawal commands, roster query, compatibility projection, migration/backfill, and detailed conflict feedback. |
| BAY-17 | School calendar configuration, expected-session preview/generation, deterministic source hash, holiday-aware calculation, and safe regeneration rules. |
| BAY-18 | Append-only audit events, redaction, reason capture, request correlation, idempotency records, and retry-safe official-document generation. |
| BAY-19 | Server-side PDF generation, templates, immutable document metadata/numbers/hashes, storage abstraction, authorized viewing, revocation, and public limited verification. |
| BAY-20 | Explicit action permissions, tenant-scoped endpoints, backend Testcontainers coverage, Angular tests, production builds, and CI workflow. |

## 3. Backend implementation

### 3.1 Academic sessions and terms

New APIs under `/api/settings/academic-sessions` support listing the sessions, resolving the current session, creating/updating sessions, lifecycle transitions, and creating/updating/deleting terms.

The model includes session code and label, start/end dates, status (`DRAFT`, `OPEN`, `CLOSED`, `ARCHIVED`), current-session designation, optimistic versioning, terms, and publication windows. School-scoped constraints prevent duplicate/current-session ambiguity and invalid date ranges. Existing academic-year data is migrated and `SchoolProfileService` reads the new source.

Lifecycle actions require an explicit reason. The service records the transition in the audit trail and enforces the state rules. Closed and archived sessions remain available for history but are protected from operational edits.

### 3.2 Session-aware enrollment and class history

The `student_enrollment` model records student, academic session, class, status, effective dates, source, previous enrollment, and optimistic version. The active placement is no longer represented only by mutable fields on the student.

APIs under `/api/enrollments` provide:

- Student enrollment history.
- Session/class roster resolution.
- Enrollment in a session.
- Effective-dated class transfer.
- Effective-dated withdrawal.

Transfers close the prior placement and create the destination placement transactionally while keeping legacy student class fields synchronized as a compatibility projection. Tenant, session, class, version, duplicate-active-enrollment, and effective-date constraints are checked server-side.

Migration V38 repairs migrated records whose enrollment date originally fell outside the migrated session dates. The live database contains three repaired migrated enrollments dated `2026-07-31` in session `2025-09-01` through `2026-07-31`.

### 3.3 School calendar and expected sessions

APIs under `/api/settings/calendar` support calendar-day reads/updates, dry-run generation, real generation, and expected-session queries by date range and class.

Preview computes the expected teaching days and class-session rows without changing the database. Real generation synchronizes the expected-session records used as the future attendance denominator. It excludes non-teaching/holiday dates, updates matching rows, and removes only obsolete future rows; it does not rewrite finalized historical attendance.

The displayed source hash is a technical trace identifier, not a password or database identifier. It is the first 32 hexadecimal characters of a SHA-256 digest calculated from the academic-session identity/version, teaching-weekday configuration, and holiday/calendar inputs. Identical source configuration produces the same hash; a changed configuration produces a different hash.

The validated preview result `4560 séances attendues · 240 jours · 19 classes` means `240 teaching days × 19 classes = 4560 expected-session rows`. The UI now states this formula and distinguishes simulation from database synchronization.

### 3.4 Audit and idempotency

Critical changes write an immutable `audit_event` containing school, actor, action, aggregate, before/after information, reason, request/correlation metadata, and timestamp. Sensitive fields are redacted. Audit history is available through `/api/audit/{aggregateType}/{aggregateId}` to authorized users.

`idempotency_key` records protect retryable commands against duplicate execution and detect reuse of the same key with a different payload. The official-document generation client sends `Idempotency-Key`, so a network retry or double-click returns the prior result rather than issuing a duplicate document.

### 3.5 Official documents

The new `/api/official-documents` API provides template listing, document listing by business aggregate, server-side generation, PDF content, revocation, and limited validity verification by document number.

Generated documents have an immutable number, template/version context, SHA-256 content hash, storage key, issue/revocation metadata, and visibility. PDFs are rendered on the server and served with `application/pdf` and `no-store`; storage is behind an abstraction currently backed by local persistent storage. Existing student uploads remain separate from official generated artifacts.

### 3.6 Authorization and tenancy

The implementation adds stable action permissions for session, enrollment, calendar, audit, documents, guardian links, attendance, grades, bulletins, promotion, finance, payroll, timetable, and confidential health access. New endpoints enforce both action permission and school/tenant scope. Staff-only enrollment operations also enforce staff context.

## 4. Frontend implementation

### 4.1 Settings → Années & périodes

Administrators can view/create/edit sessions, identify the current session, manage terms and publication dates, and move a session through its lifecycle. Open, close, and archive actions use an application modal that shows the consequence and requires a reason stored in the audit log.

Cancel closes the modal without calling the backend. Closed/archived sessions remain readable and their mutation controls are disabled.

### 4.2 Settings → Calendrier

The selected academic session exposes teaching-day/calendar configuration plus preview and generation actions. Preview is explicitly labeled as a simulation and explains its counts. Generation opens a confirmation modal explaining that it synchronizes expected attendance sessions and how holidays, historical data, and future obsolete rows are handled.

The source hash is hidden under technical details with a plain-language explanation. Archived/closed sessions allow preview for diagnosis but disable calendar saving and real generation.

### 4.3 Élèves → student detail → Parcours / inscription

The student panel displays the current placement and session-linked class history. Authorized staff can transfer or withdraw an enrollment with effective date and reason. The transfer form constrains the date to the active enrollment/session boundaries and shows an inline explanation before submission.

The panel also exposes generated official documents and a permission-gated audit drawer. Document actions include generation, PDF opening/downloading, and revocation where authorized.

### 4.4 Shared academic context

A shared Angular academic-context service resolves the current session and makes session selection available to feature modules. Historical selection is retained as read-only context so downstream attendance, academic, journey, finance, reports, timetable, and parent-portal work can consume one canonical session model.

## 5. Corrections made after user acceptance feedback

### Native prompt cancellation

The former browser `prompt()` flow could continue after cancellation. It has been removed. Session state transitions, term deletion, and calendar generation now use controlled application modals. Buttons remain disabled until required input is present, and cancellation does not send an HTTP request.

### Calendar hash and generation communication

The unexplained value such as `c3e57377732601c056b12a2c0c39f150` is now labeled as a technical source hash and moved into a details section. Preview identifies itself as non-mutating. Real generation describes exactly which records are created/updated and what is preserved.

### Transfer conflict precision

The generic constraint error has been replaced for known enrollment conflicts. For example, the live negative test now returns HTTP 400 with:

> La date effective du transfert (2026-07-30) ne peut pas précéder la date d’inscription active (2026-07-31)

Malformed requests and duplicate active enrollment constraints are also mapped to clearer user-facing errors. The UI performs equivalent date validation before submission.

## 6. Verification evidence

| Check | Result |
| --- | --- |
| Backend Testcontainers integration suite | 4 tests, 0 failures, 0 errors; fresh PostgreSQL migration through V38 |
| Angular CI test command | 2 spec files, 2 tests passed |
| Angular production build | Passed |
| Backend production Docker build | Passed |
| Frontend production Docker build | Passed |
| Runtime health | Backend actuator reports `UP` |
| Browser acceptance | Passed on `http://localhost:8082`; no console errors |
| Session modal cancel | Verified status remained `OPEN` after cancellation |
| Calendar preview | Verified 4560 rows = 240 teaching days × 19 classes; no mutation |
| Archived-session controls | Save/generate disabled; preview remains available |
| Transfer invalid-date API | HTTP 400 with the precise effective-date explanation |

The destructive path of performing a real student transfer was deliberately not executed during acceptance testing, because it would permanently alter school history. Validation was verified through the UI and a negative API request.

## 7. How to test in the application

Sign in at `http://localhost:8082` with `admin` / `admin`.

1. Open **Paramètres → Années & périodes**.
   - Inspect current, archived, and draft session cards.
   - Use a lifecycle action and verify the custom confirmation modal explains the impact.
   - Verify the action cannot be confirmed without a reason.
   - Press Cancel and confirm the status does not change.
   - Open a session to add/edit terms and publication windows.
2. Open **Paramètres → Calendrier**.
   - Select an open session.
   - Click **Prévisualiser la génération** and verify that it says no data is modified, displays the count formula, and explains the source hash under details.
   - Click **Générer les séances**, read the synchronization impact modal, and cancel if you do not want to change expected-session data.
   - Select an archived session and verify save/generation controls are unavailable while preview remains available.
3. Open **Élèves**, select a student, and locate the **Parcours / inscription** panel.
   - Review the session/class history timeline.
   - Click **Transférer** and test a date before the active enrollment date; the form must explain the exact allowed boundary and prevent submission.
   - Use a valid destination class/date/reason only if an actual historical transfer is intended.
4. In the same student panel, inspect **Documents officiels**.
   - Generate an available document, open its PDF, retry with the same command, and confirm no duplicate is issued.
   - Revoke only a disposable test document because revocation is audited.
5. Expand **Journal d’audit** on the student panel to inspect authorized enrollment/document activity.

## 8. Deployment and database state

The Docker deployment consists of the frontend on port 8082, backend on port 8083, and PostgreSQL on port 5434. Flyway migrations V37 and V38 are applied. At the end of acceptance testing, `Session 2026-2027` is current/open and `2025-2026` is archived.

Operational note: no real promotion/transfer was run. Existing migrated students are still associated with the archived 2025-2026 session. Before daily work begins in 2026-2027, administrators should create the students’ new-session enrollments through the forthcoming Journey/promotion workflow or an approved enrollment operation. This is expected operational data work, not a failed migration.

## 9. Known non-blocking items

- The frontend build reports pre-existing warnings for an optional chain in the staff feature and CommonJS dependencies (`stompjs`, `sockjs-client`).
- The package audit reports 24 dependency advisories (2 low, 4 moderate, 17 high, 1 critical). No automatic major-version remediation was applied because that can introduce unrelated regressions; dependency remediation should be handled in a dedicated security ticket.
- BAY-7 provides shared primitives and initial consuming screens. Feature-specific workflows in the downstream attendance, Journey, finance, academic, and timetable epics must use these APIs instead of introducing parallel session, enrollment, document, or audit models.

## 10. Delivery references

- `7fdf5bb` — `feat(BAY-7): implement shared academic foundation`
- `30c010b` — `fix(BAY-7): clarify lifecycle and transfer workflows`
- Main migration: `backend/src/main/resources/db/migration/V37__shared_foundation.sql`
- Repair migration: `backend/src/main/resources/db/migration/V38__repair_enrollment_dates.sql`
- CI workflow: `.github/workflows/ci.yml`

