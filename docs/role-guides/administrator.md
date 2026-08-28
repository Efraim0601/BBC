# Administrator guide

*Configure the school, manage access, and supervise every operation.*

**Scope:** Whole school for Administrator; assigned Kindergarten, Primary, or Secondary level for section administrators.

The administrator owns configuration and access governance. The global administrator covers every parcours. A section administrator follows the same procedures, but only inside the assigned school level.

## What this role can do

- **Access and responsibilities:** Create users, assign roles and parcours, configure role rules, and review the audit trail.
- **School structure:** Manage sessions, terms/sequences, sections, classes, subjects, class-subject assignments, and bilingual groups.
- **Students and families:** Register, import, edit, export, and manage guardian-child links and portal access.
- **Staff:** Create and edit employees, assign roles, and manage departments, leave, and multiple document categories.
- **Teaching and learning:** Supervise attendance, grades, councils, report cards, coursebooks, promotions, and timetables.
- **Finance and oversight:** Access payments, accounts, expenses, payroll, accounting, dashboards, alerts, and reports.

## Daily procedures

### Sign in and choose the correct scope

Route: `/parcours`

1. Sign in, then choose All parcours for a whole-school operation.
2. Choose a school level and language section before a class-specific operation.
3. Always verify the parcours badge in the header before changing data.

### Assign a role and its parcours

Route: `/access-control`

1. Open Settings → Access and responsibilities, then the Users tab.
2. Select the user, primary role, and only the parcours that are required.
3. Preview the changes, enter a clear reason, then save.
4. Sign out and test the affected account in every allowed and denied scenario.

> **Remember:** Prefer role rules. User-specific exceptions should remain rare, dated, and audited.

### Configure an academic year

Route: `/settings`

1. In Sessions and terms, create or verify the current academic year.
2. Define sequences, trimester results, the annual result, and their date ranges.
3. Verify the calendar, grade-entry windows, and report-card templates before opening grade entry.

### Configure classes, subjects, and bilingual groups

Route: `/settings`

1. Create classes in the correct school level and language section.
2. Create subjects, then assign them to classes with coefficients and responsible teachers.
3. In Link bilingual classes, pair the FR and EN classes that share the same pupils.
4. Keep separate teachers per class: the group shares the roster, not automatically the teacher or grades.

### Register a student or import a family

Route: `/students/new`

1. Enter identity details; on mobile, the date can be typed as DD/MM/YYYY with automatic slash insertion.
2. Choose the entry class. For a linked class, the backend attaches the student to the shared cohort.
3. Add one or more guardians. Email remains optional until portal access is enabled.
4. For a batch, use Import, download the template, preview every row, then confirm.

### Create an employee and attach documents

Route: `/staff/create`

1. Create the employee from Staff → New employee and assign the role and school level.
2. Add as many documents as required, each with its category: CV, diploma, identity, certificate, or other.
3. After creation, open /staff/{id}; each document can be previewed and downloaded.

### Publish a timetable

Route: `/timetable`

1. Choose the class and verify its model: daily homeroom or period-based teaching.
2. Assign subject, teacher, and room to each slot; resolve every reported conflict.
3. For linked bilingual classes, respect each section’s time block and separate teachers.
4. Publish only after preview. Secondary attendance depends on published occurrences.

### Supervise grades, councils, and report cards

Route: `/academic`

1. Review submitted sheets, return incomplete ones, and accept compliant sheets.
2. Verify finalized attendance, council data, and calculated results.
3. Validate the report card, generate the official PDF, and use batch generation when the class is ready.

### Run finance operations

Route: `/finance`

1. Use Payments to collect money and issue a receipt; always choose the account actually credited.
2. Use Accounts and movements for deposits, withdrawals, transfers, and reconciliation.
3. Use Student accounts for balances, full history, and consolidated receipts.
4. Review expenses, fees, payroll, accounting, and reports before any close.

## Boundaries

- Never share an administrator account or use this role for another role’s daily work.
- Do not grant Access and responsibilities to Principals, Teachers, Accountants, or Parents.
- Every critical action needs a reason and must be performed in the correct parcours.
- Avoid deleting history: prefer archive, void, reopen, or an audited reversing operation.

## Quick verification

- [ ] A teacher sees only assigned classes and subjects.
- [ ] A principal sees only assigned parcours and cannot open Access and responsibilities.
- [ ] The accountant covers all finance parcours without permission administration.
- [ ] A parent sees only linked children and guardian-enabled sections.
- [ ] Links, PDF/Excel exports, receipts, report cards, and documents open from their correct URL.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
