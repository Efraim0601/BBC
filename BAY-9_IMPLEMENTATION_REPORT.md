# BAY-9 Attendance Epic — Implementation Report

## Delivered scope

This branch implements BAY-26 through BAY-32 as one attendance workflow.

### BAY-26 — Level-specific policies

- Nursery and Primary are enforced as one `DAILY` roll call per teaching day.
- Secondary is enforced as `PERIOD`, derived from the class timetable's subject slots.
- Administrators can configure lateness thresholds, chronic-absence thresholds, and mandatory absence reasons.
- Session generation uses the managed academic session and school calendar. If a new session has no weekday configuration yet, Monday–Friday is used until administrators customize it.

### BAY-27/BAY-28 — Teacher rosters

- The roster is built from active, session-aware student enrollments—not only students already marked.
- Teachers can mark Present, Absent, Late, or Excused, add a reason and note, and use “All present”.
- Secondary teachers choose a published timetable period and see the associated subject.
- Teacher class scope is enforced by the backend.

### BAY-29 — Save/finalize/reopen/audit

- Bulk saves use optimistic session versions; stale writes return HTTP 409 with a precise reload instruction.
- Finalization is blocked while any student remains unmarked.
- Finalized rosters are locked.
- Reopening requires a reason and records actor, time, action, and reason.
- The UI uses an application modal; no browser `prompt()` or `confirm()` is used.

### BAY-30 — Device reconciliation

- Fingerprint scans remain immutable evidence in the legacy attendance record.
- Administrators can inspect scans separately and associate an unreconciled scan with the currently opened roll-call session.
- The resulting mark records the fingerprint source and device record reference.

### BAY-31 — Analytics

- Student and aggregate analytics include expected, present, late, absent, excused, and unmarked counts.
- Attendance percentage is `(present + late) / expected`.
- Generated unmarked roster rows remain in the denominator, preventing missing roll calls from falsely improving percentages.
- Filters support date range and class; teacher scope is applied server-side.

### BAY-32 — Alerts and guardian notifications

- Chronic absence scanning creates or updates deduplicated operational alerts.
- Finalizing Absent/Late marks creates same-day alerts.
- Guardian relationships with `receives_attendance` are used to create retry-safe EMAIL/SMS/IN_APP notification outbox rows.
- Notification rows expose `PENDING`, `SENT`, `FAILED`, and `CANCELLED` lifecycle states and delivery-attempt metadata.

## User interface

Open **Presence** from the left navigation. The screen now contains:

1. **Roll call / Liste d'appel** — date, class, secondary period, complete roster, save/finalize/reopen, and audit history.
2. **Analytics / Analyses** — date range, optional class, attendance KPIs, student drill-down table, and chronic-alert generation.
3. **Devices & reconciliation / Lecteurs & rapprochement** — reader health and scan-to-session reconciliation (administrators).
4. **Settings / Configuration** — level policies and expected-session preview/generation (administrators).

All editable controls have visible borders and focus states. Mandatory fields are marked with `*`; attempted submission highlights every missing required reason in red and shows a field-level message.

## Authorization

- Principal and Prefect: roster, mark, finalize, reopen, analytics, policy management, and reconciliation.
- Teacher and Form teacher: scoped roster, mark, finalize, and analytics.
- Policy management, generation, reconciliation, and alert creation remain administrative.
- Parent accounts cannot access staff attendance APIs.

## Verification performed

- Angular tests: **5/5 passed**.
- Angular production build: passed.
- Java 21 Docker production compile and test-source compile: passed.
- Flyway migrations V41–V43 applied successfully to the live Docker database.
- Live primary lifecycle: roster loaded, save passed, stale save returned **409**, finalize passed, reopen passed with reason, and audit history contained all three actions.
- Live analytics: one present out of one expected produced **100.00%**.
- Missing-roll-call denominator: one generated unmarked row produced **0.00%**.
- Generation: preview reported **10 expected / 0 writes**; generation reported **10 synchronized**.
- Secondary model: a temporary Wednesday timetable slot produced **P1 / MATH**.
- Required-reason UX: blank absence reason showed a red field, field message, and page error without sending the save request.
- Generation UX: in-app explanation modal opened; Cancel closed it with no browser dialog and no write.
- Notification outbox: one finalized absence queued **2 notifications** (EMAIL and SMS), both PENDING.
- All temporary sessions, expected sessions, marks, events, notifications, alert, and timetable slot used by live testing were removed afterward.

The isolated Testcontainers runtime did not complete inside a nested Docker test container before the execution limit, so behavioral verification was completed against the deployed Java 21 application and database. The integration test source is included and compiles in the Docker build.
