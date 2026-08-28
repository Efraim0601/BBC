# Prefect guide

*Oversee school life across all parcours without administering teaching or access.*

**Scope:** All parcours, limited to attendance, discipline, and school-life follow-up.

The Prefect monitors attendance, incidents, sanctions, alerts, and school-life communication across the school. The role can read the student context needed for follow-up, but does not enter grades, decide promotions, or administer finance, staff, or permissions.

## What this role can do

- **Student directory:** Search and view students across all parcours to identify class, family, and follow-up context; no registration or import.
- **Attendance:** Review rosters and analytics, handle anomalies, reconcile data, and reopen/correct with a reason when policy permits.
- **Discipline:** Create and follow incidents, sanctions, summonses, and guardian notifications.
- **School life:** Review journey, non-confidential health information, relevant documents, and correspondence as needed for follow-up.
- **Alerts and reports:** Review attendance/discipline indicators, handle authorized alerts, and produce operational follow-up.
- **Resources:** View published resources and lists; do not administer school catalogues.

## Daily procedures

### Verify school-wide scope

Route: `/students`

1. After sign-in, verify that the header says All parcours.
2. Open Students: the filter should offer every class without New student or Import controls.
3. Search by name or matricule and verify the class before any intervention.
4. If classes are missing, stop the workflow and report a role-profile defect to the permission administrator.

### Oversee attendance

Route: `/presence`

1. Choose date and class, then open the relevant daily roster or published period.
2. Review absences, lateness, excused statuses, finalization, and any check-in anomalies.
3. A correction or reopening must include a precise reason and preserve the original attendance trace.
4. The responsible teacher owns the initial roll call; intervene only under the school procedure.

### Record a discipline incident

Route: `/discipline`

1. Click New incident, then choose the exact class and student.
2. Enter the date, type, factual description, and any sanction.
3. Save, review the history, then send the approved summons or notification.
4. Do not copy confidential medical information into the description.

### Handle alerts

Route: `/alerts`

1. Filter Attendance or Discipline first; finance and grades remain outside the mandate.
2. Open the alert, verify the student and source facts, then acknowledge it.
3. Record the follow-up action and close only when the situation has actually been handled.

### Review a student's school life

Route: `/journey`

1. Filter by class, choose the student, and confirm identity.
2. Review only the journey, attendance, discipline, correspondence, and relevant non-confidential information.
3. Send any permanent-record correction to an authorized administrator.

### Produce operational follow-up

Route: `/reports`

1. Choose the attendance or discipline report and the exact period.
2. Verify parcours, class, and filters before export.
3. Share the report only with authorized recipients and under the required confidentiality.

## Boundaries

- No grade entry, report-card validation, or promotion decision.
- No timetable publishing or school-structure changes.
- No access to Finance, Staff, Settings, or Access and responsibilities.
- Confidential medical and financial data remain out of scope.
- Never erase an incident or finalized attendance; use a traceable correction, closure, or reopening.

## Quick verification

- [ ] All classes appear in Students and Attendance, while registration/import is absent.
- [ ] An incident can be saved and found in history with its author.
- [ ] Attendance/discipline alerts contain real data and can be followed up.
- [ ] Academic access is limited to necessary oversight; grades, report cards, and promotions are not editable.
- [ ] Finance, Staff, Settings, and Permissions URLs are denied.

## Confirmed gaps in the tested build

- The local Prefect profile advertises Students, Attendance, Discipline, Journey, Health, Documents, and Correspondence, but Students redirects and the other screens receive no classes. Legacy modules and Permission Policy V2 actions must be aligned before use.
- Timetable shows an authorization error and only an empty teacher schedule even though the module is advertised as write-enabled. Remove the link or replace it with an explicitly authorized oversight view.
- Promotion currently exposes every class and editable decision controls to the Prefect. This high-risk access is outside the mandate and must be denied in both UI and server.
- Dashboard, Alerts, and Reports open but remain at zero or say data is unavailable; their read actions must be aligned with the school-life scope.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
