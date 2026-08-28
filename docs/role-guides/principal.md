# Principal guide

*Lead only the assigned parcours without administering permissions.*

**Scope:** Only the school levels and language sections assigned by the administrator.

The principal supervises students, teaching, and operations inside one or more assigned parcours. Server controls deny every class outside that scope.

## What this role can do

- **Students:** View, search, and export students in scope; no registration or bulk import.
- **Academic:** View grades and results, review packets, view council input, and validate/publish report cards; do not edit teachers’ raw grades.
- **Attendance and discipline:** View attendance rosters/analytics and manage discipline in scope; attendance marking remains with the responsible teacher.
- **Timetable:** View, prepare, publish, reopen, and export timetables inside the allowed parcours.
- **Finance:** View overview, student accounts, consolidated receipts, treasury accounts, and movements; the main finance screen is read-only.
- **Oversight:** View journey, health, documents, promotions, resources, supplies, alerts, dashboards, reports, and visible settings.

## Daily procedures

### Choose an assigned parcours

Route: `/parcours`

1. After sign-in, choose one offered school level, then Francophone or English.
2. Check the header badge before opening a class.
3. To switch responsibility, click the badge and choose another assigned parcours.

> **Remember:** If no parcours is assigned, contact the administrator; do not try to bypass the selector.

### Monitor students in scope

Route: `/students`

1. Use the class filter; only authorized classes should appear.
2. Search by name or matricule, open the record, and view family, documents, health, and journey according to permissions.
3. Export the list to Excel or PDF when needed.

### Review grades and report cards

Route: `/academic`

1. Choose a class and academic period.
2. In Grade entry, review sheet status and blockers; do not edit grades on behalf of the teacher.
3. Use Class overview and Master sheet to review results and anomalies.
4. Open a student report card, validate/publish when complete, then generate the official PDF.

### Review attendance and council data

Route: `/presence`

1. In Roll call, choose date, class, and—at Secondary—the published period.
2. Review rosters and statuses; use Analytics for trends, absences, and lateness.
3. In Academic → Attendance & council, verify the sequence date range and totals from finalized calls.

### Manage discipline and coursebook

Route: `/discipline`

1. Create and follow incidents only for students in scope.
2. Use summon, close, and notification actions according to school procedure.
3. In Coursebook, view or complete the authorized entries for the parcours.

### Publish the parcours timetable

Route: `/timetable`

1. Choose an authorized class and review teachers, subjects, rooms, and conflicts.
2. Publish or reopen only after management validation.
3. Then verify teacher schedules and Secondary attendance occurrences.

### Review finance and oversight

Route: `/finance`

1. Review finance indicators and histories without creating payments or movements.
2. In Student accounts, filter by class, open the student, and prepare a consolidated receipt if needed.
3. Use Dashboards, Alerts, and Reports for parcours oversight.

## Boundaries

- No access to Access and responsibilities; only an administrator changes roles and permissions.
- No class outside assigned parcours, including direct URLs.
- No student registration/import and no editing teachers’ raw grades.
- Finance remains an oversight view; collections and movements belong to the accountant.

## Quick verification

- [ ] The student selector contains only classes in the active parcours.
- [ ] A URL targeting an out-of-scope class is denied by the server.
- [ ] New student and import are unavailable.
- [ ] Access and responsibilities redirects to the home page.
- [ ] Finance shows read-only and Treasury does not allow a movement.

## Confirmed gaps in the tested build

- Staff is advertised but currently redirects: HR_VIEW is missing from the local principal profile.
- A principal with no assignment still sees all three levels in the selector, then remains blocked. The screen should show an empty state instructing the user to contact an administrator.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
