# Timetable & Scheduling Epic — Implementation and Operating Guide

## Delivery summary

The Timetable & Scheduling epic is implemented on branch `feature/BAY-9-attendance-roster-level-models-analytics` and deployed to the local Docker stack at `http://localhost:8082`.

The implementation covers:

- Strict backend prevention of teacher and room double-booking.
- Session-aware timetables tied to the active academic year.
- Database-configured bell periods instead of hardcoded UI times.
- Primary `HOMEROOM` scheduling with one required class teacher.
- Secondary `DEPARTMENTAL` scheduling with class and subject qualification checks.
- Teacher-to-class and teacher-to-subject assignment from the timetable screen.
- Draft, publish, lock, and reason-based reopen lifecycle.
- Published timetable integration with Secondary period attendance.
- Personalized teacher schedules generated from the master timetable.
- Clear validation borders, mandatory-field messages, and precise backend errors.

## Screens and complete administrator flow

### 1. Create teachers and subjects

1. Open **Personnel** to create or edit teaching employees.
2. Give each teacher the correct educational level: Primary or Secondary.
3. Open **Académique → Structure → Matières** to create any missing subjects.
4. Open **Académique → Structure → Classes** if classes are missing.

Teacher level is enforced: a Secondary teacher cannot be scheduled in a Primary class and vice versa.

### 2. Configure the school bell periods

1. Open **Emploi du temps**.
2. Select **Périodes horaires**.
3. Edit the label, start time, or end time for P1–P9.
4. Click **Enregistrer** on the modified period.

Rules:

- End time must be after start time.
- Two active periods cannot overlap.
- These database values drive the class and teacher grids; they are not hardcoded in the Angular component.

### 3. Configure a Primary class

1. Open **Emploi du temps → Planning des classes**.
2. Select a Primary class, for example **Class 1**.
3. The screen displays the applied model: **Titulaire (appel quotidien)**.
4. Select the class's **Enseignant titulaire** and save.
5. Click an empty grid cell.
6. Select the subject and room. The teacher is automatically preselected as the homeroom teacher.
7. Save each course.
8. Click **Publier et verrouiller**, then confirm in the application modal.

Primary rule: every timetable course for that class must use the configured homeroom teacher. The backend rejects a different teacher even if a custom request bypasses the UI.

Primary attendance remains one daily roll call. The timetable is still useful for classroom planning, but it does not create subject-by-subject attendance.

### 4. Configure a Secondary class

1. Open **Emploi du temps → Planning des classes**.
2. Select a Secondary class, for example **1ère**.
3. The screen displays **Départemental (appel par période)**.
4. In **Affecter un enseignant à cette classe et à ses matières**:
   - select a teacher;
   - check the subjects they are qualified to teach;
   - click **Enregistrer l'affectation**.
5. Repeat for the other departmental teachers.
6. Click a grid cell and select its subject, assigned teacher, and room.
7. Save the course.
8. Publish the completed schedule.

Secondary rules:

- The teacher must belong to the Secondary level.
- The teacher must be assigned to the selected class.
- The teacher must be qualified for the selected subject.
- The same teacher cannot occupy two classes in the same period.
- The same room cannot be used by two classes in the same period.
- There is no force-save bypass for conflicts.

### 5. Publish and reopen

**Draft** schedules can be edited. **Published** schedules are locked and become the source for Secondary attendance.

To modify a published schedule:

1. Select the class.
2. Click **Rouvrir avec motif**.
3. Enter a mandatory reason in the application modal.
4. Make the changes.
5. Publish the new version again.

Publish and reopen actions use optimistic versions to prevent two administrators from silently overwriting each other's changes. The actions are also written to the audit trail.

### 6. View a teacher's personalized schedule

1. Open **Emploi du temps → Planning des enseignants**.
2. Select a teacher.
3. The app generates their weekly timetable from all published class schedules.
4. Each cell shows the class, subject, room, and configured period.
5. Use **Imprimer** for a printable view.

Teachers can retrieve their own published schedule through the authenticated `/api/timetable/teachers/me` endpoint. The local demo account below verifies this role-scoped flow.

### 7. Use the schedule for attendance

1. Publish a Secondary class schedule.
2. Open **Présence → Liste d'appel**.
3. Select a school date and the Secondary class.
4. The **Période / matière** selector displays only periods from the published timetable for that weekday.
5. Select a period, mark the roster, save, and finalize.

A draft or reopened timetable does not feed new Secondary attendance periods until it is published again.

## Local demo data

The live Docker database contains intentionally labelled demo employees:

| Code | Teacher | Level | Assignment |
|---|---|---|---|
| `DEMO-PRI` | Claire Tchana | Primary | Class 1 homeroom teacher |
| `DEMO-MATH` | Paul Nkomo | Secondary | Mathematics, 1ère and 2nde |
| `DEMO-LANG` | Amina Bello | Secondary | French and English, 1ère and 2nde |
| `DEMO-SCI` | Daniel Etoa | Secondary | Physics-Chemistry and Biology, 1ère |

Published demo schedules:

- **Class 1:** three homeroom courses using Claire Tchana.
- **1ère:** six departmental courses across Mathematics, French, English, Physics-Chemistry, and Biology.

Teacher portal test account:

- Username: `teacher.demo`
- Password: `admin`
- Linked employee: Paul Nkomo

This account is for the local development environment only.

## Backend implementation

Migration `V44__timetable_planning_workflow.sql` adds:

- `timetable_period` for configurable bell periods.
- `timetable_class_config` for academic session, class model, homeroom teacher, publication state, and optimistic version.
- `academic_session_id` on timetable slots.
- A unique database guard against teacher double-booking.
- A unique database guard against room double-booking.
- Teacher-schedule lookup indexes.

The service layer also performs friendly pre-save checks so users receive names of the conflicting teacher, class, subject, or room instead of a generic database-constraint message. Database indexes remain the final race-condition guard.

Main APIs:

- `GET /api/timetable/classes`
- `GET/PUT /api/timetable/periods`
- `GET /api/timetable?className=...`
- `PUT /api/timetable/slot`
- `DELETE /api/timetable?...`
- `PUT /api/timetable/classes/{classId}/config`
- `PUT /api/timetable/classes/{classId}/teachers/{teacherId}`
- `POST /api/timetable/classes/{classId}/publish`
- `POST /api/timetable/classes/{classId}/reopen`
- `GET /api/timetable/teachers/me`
- `GET /api/timetable/teachers/{teacherId}`

## Verification completed

- Backend Java 21 Docker package build passed.
- Frontend Angular production build passed.
- Frontend automated suite passed: 3 files, 5 tests.
- Flyway migration V44 applied successfully.
- Both Docker services restarted and backend health returned HTTP 200.
- Published **1ère** schedule rendered with six mock courses.
- Published **Class 1** homeroom schedule contains one teacher for all courses.
- A conflicting attempt to schedule Paul Nkomo in 2nde while he teaches 1ère was rejected with: `Enseignant indisponible : il assure déjà MATH en 1ère sur cette période.`
- Teacher account `teacher.demo` retrieved its personalized published schedule with two Mathematics periods.
- Attendance displayed the published Tuesday periods for 1ère: P1 SVT, P2 MATH, P3 EN.
- Bell-period screen rendered database values such as P1 `07:30–08:25`.

## Important operational distinction

The timetable controls *when and by whom courses occur*. Attendance controls *whether students attended those courses*.

- Primary: timetable courses may be hourly, but attendance is one daily roll call.
- Secondary: every published subject period becomes an available period-specific roll call.
