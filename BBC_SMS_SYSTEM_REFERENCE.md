# BBC SMS system reference — exercised candidate

This is the operator-facing system map for the isolated full-school run. It describes the candidate that was actually exercised; it is not a claim that unrelated legacy stacks are equivalent.

## Runtime and trust boundaries

```text
8100 Angular UI
      │ authenticated API calls + X-Parcours when the selected workspace requires it
8101 Spring Boot API
      │ tenant derived from authenticated principal; V2 action + resource scope
private PostgreSQL 16 (bbc-full-e2e-db-1)
      ├── Flyway schema history v144
      ├── append-only audit / Journey / accounting records
      └── documents volume
8125 Mailpit
      └── local SMTP capture for invitation, reset, credential, and notification evidence
```

## Module map and dependencies

| Module | Primary source data | Downstream consumers | Acceptance evidence |
|---|---|---|---|
| Setup/foundation | school, calendar, mail, catalogs | all modules | `01-foundation`, `gate-ledger` gates 1–2 |
| Sessions/academic structure | sessions, terms, reporting periods, result products | curriculum, assessments, grades, promotion | `02-sessions`, `03-structure` |
| Sections/classes/curriculum | sections, classes, subjects, coefficients, curricula | timetable, teacher scope, assessments | `04-academic-setup` |
| Staff/HR | employees, accounts, roles, teacher/class links | timetable, attendance, grades, payroll | `05-staff` |
| Students/families | students, enrollments, guardians, relationships | every child-scoped module, parent portal | `05-students-families`, `10-parent-portal` |
| Timetable | versions, periods, slots, teacher/room resources | attendance occurrences, teacher schedule | `06-timetable` |
| Attendance | sessions, rosters, marks, devices, alerts | analytics, reports, parent school life | `07-attendance`, `11-daily-operations` |
| Academic results | packets, grades, conduct, snapshots, publications | report cards, documents, promotion | `08-academic-results`, `12-promotion` |
| Finance | plans, charges, collections, invoices, receipts, journals, payroll | parent finance, reports, reconciliation | `09-finance` |
| Daily operations | discipline, coursebook, health, documents, events, messages, ClassKit, alerts | parent/management views | `11-daily-operations`, `10-parent-portal` |
| Access control | legacy grants, V2 role/user rules, templates, audit | every controller/service boundary | `13-permission-sweep`, V124–V144 contracts |

## Cross-module event chain

1. A school/session/class/student is created under the authenticated tenant.
2. Class subjects and coefficient overrides feed assessment defaults and timetable assignment validation.
3. Published timetable occurrences feed attendance eligibility; attendance and conduct feed report-card snapshots.
4. Published annual snapshots feed promotion preview; override/commit creates one target enrollment and Journey transition.
5. Fee plans target current enrollments; charges/collections create one balanced accounting event and parent-linked finance read models.
6. Parent views are relationship-filtered after the parent action is allowed; health and message acknowledgement add relationship-level checks.
7. Access changes update policy version and are audited; contextual actions are enforced in services where resource scope is available.

## Operational invariants

- Tenant comes from the authenticated principal, not an arbitrary client school header.
- Resource-scoped actions are checked after resolving the student, class, session, relationship, version, or substitution.
- Posted journal lines balance; current DB read-back returned zero unbalanced posted entries.
- V144 adds only three principal legacy compatibility grants; it does not broaden ordinary role access.
- The UI is evidence for usability and route visibility; API/service policy and read-only DB reconciliation are authoritative for scope and accounting.

See [`FULL_SCHOOL_LIFECYCLE_E2E_TEST_REPORT.md`](FULL_SCHOOL_LIFECYCLE_E2E_TEST_REPORT.md) for the final disposition and [`gate-ledger.md`](qa/e2e-runs/2026-08-14-full-school/gate-ledger.md) for exact gate status.

The section-29 module pages, field/lifecycle/security summaries, and explicit
documentation gaps are in
[`BBC_SMS_SYSTEM_REFERENCE_MODULE_PAGES.md`](BBC_SMS_SYSTEM_REFERENCE_MODULE_PAGES.md).

## Current release-readiness addendum - 2026-08-15

The current isolated candidate read-back is UI `8100=200`, API health
`{"status":"UP"}`, Flyway `144|true`, backend `177/177` green, and frontend
`23` test files / `51` tests green. The latest fresh Bursar/Cashier/FAM-A UI
boundary slice and permission-policy frontend regression are recorded in the
Gate 14 evidence and ledger. Gate 14 remains `IN PROGRESS` for its exhaustive
persona/read-write/resource-scope/network/console/golden/state matrix; the
production-like V77 upgrade remains non-destructively `BLOCKED`; performance is
measured partial; and nurse is explicitly excluded.
