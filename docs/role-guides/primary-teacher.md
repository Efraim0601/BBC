# Primary / Kindergarten teacher guide

*Work only with the assigned homeroom class, including a linked bilingual cohort.*

**Scope:** Active homeroom class(es) in the assigned level and language section.

In Kindergarten and Primary, access comes from the homeroom assignment, not from timetable appearances. The teacher handles every subject in that class and shares the attendance roster with the linked bilingual class.

## What this role can do

- **Students:** View and export only students in the homeroom class.
- **Grades:** Enter all subjects for the homeroom class, save, submit, and review results.
- **Attendance:** Take the cohort’s daily attendance, save, and finalize.
- **Council and report cards:** Complete council input, validate the homeroom report card, and generate the official PDF.
- **Coursebook and communication:** Manage the class coursebook and use Correspondence/Resources when available.
- **Timetable:** View the personal timetable; no school-wide publishing.

## Daily procedures

### Verify the homeroom assignment

Route: `/students`

1. After sign-in, choose the offered school level and language section.
2. Open Students: the filter should contain only the homeroom class.
3. If the list is empty, ask the administrator to verify the class homeroom teacher and effective dates.

### Enter and submit grades

Route: `/academic`

1. Open Grade entry and choose class, sequence, and subject.
2. Enter a mark on the displayed scale, or choose Absent/Exempt when applicable.
3. Save the draft; resolve every blocker before sending it to management.
4. An Accepted and locked sheet cannot be edited; contact management for return or reopening.

### Take daily attendance

Route: `/presence`

1. Choose the date and the displayed class or bilingual cohort.
2. Use All present, then correct absences, lateness, or excused statuses.
3. The reason is optional, including for an absence or excused status; add it only when the information is known and useful.
4. Save to keep a draft, then Finalize once the roster is verified.

### Understand a linked bilingual class

Route: `/presence`

1. The same pupil appears in both linked classes because enrollment belongs to one shared cohort.
2. Attendance is shared: for example CE1 A (FR) · Class 3 A (EN) shows one roster.
3. Grades, subjects, teachers, and report cards remain separate by class and language section.
4. Never create two student records to represent the two programs.

### Complete attendance and council input

Route: `/academic`

1. Open Attendance & council and choose the class and sequence.
2. Verify the date range; only finalized calls inside it feed the totals.
3. Unjustified absence is calculated automatically. Use manual correction only when finalized calls are inaccurate.
4. Complete work, conduct, decision, and observation, then save or submit.

### Validate and generate a report card

Route: `/academic`

1. In Report card, choose class, period, and student.
2. Check grades, average, rank, attendance, council decision, and student details.
3. Create the draft if needed, validate the report card, then click Generate official PDF.
4. For a bilingual cohort, repeat in each class to produce the two separate report cards.

### Maintain the coursebook

Route: `/coursebook`

1. Choose the homeroom class, then create the lesson or homework entry.
2. Enter date, subject, content, and any due date.
3. Review before publishing: this information may be visible to families.

## Boundaries

- No access to other classes, finance, staff, settings, or permissions.
- The homeroom assignment must be active on the working date.
- Bilingual attendance is shared, but a grade entered in the FR class does not become an EN grade.
- Do not change an accepted sheet or finalized attendance without the reopening process.

## Quick verification

- [ ] Students shows only the homeroom class and exact roster.
- [ ] All subjects for the homeroom class are available in grade entry.
- [ ] The linked FR and EN classes show the same matricules in attendance.
- [ ] Attendance can be saved, finalized, and then becomes locked.
- [ ] A validated report card can generate an official PDF.

---

Verified against the local application on 28 August 2026 (build `1c89f5b`).
