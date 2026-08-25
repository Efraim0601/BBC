# Production permission alignment

Date: 2026-08-25

Source: local production-data rehearsal on ports 8130/8131/5553

Purpose: reproduce tested role behavior in production without copying QA accounts or broad one-user overrides.

## Safe deployment rule

Use role-level Flyway migrations for product behavior. Do not copy `permission_user_action` rows from the local `admin` test account and do not copy any `qa.*` account. After deployment, run `docs/production_permission_preflight_2026-08-25.sql` and repair only reported gaps.

## Automatic production change

Migration `V174__primary_homeroom_council_inputs.sql` grants the Primary/Kindergarten `teacher` role these two actions with scope **Homeroom classes / `TITULAIRE_CLASSES`**:

- **Class council input — view** (`ACADEMIC_COUNCIL_INPUT_VIEW`)
- **Edit class council input** (`ACADEMIC_COUNCIL_INPUT_EDIT`)

It deliberately does not grant Secondary council editing. It also updates the `primary_teacher` template so a school created after the migration gets the same behavior.

## Access Control settings to preserve

### Administrator

- Role: `administrator`.
- User scope: `GLOBAL`.
- Owns Access Control.
- Do not copy the 89 local `admin` user overrides. V168 makes the Administrator role authoritative.

### Principal

- Role: `principal`, never `administrator`.
- User scope: `EXPLICIT`.
- Assign only the Nursery, Primary, or Secondary FR/EN cycles that the Principal manages.
- **Human resources — view** (`HR_VIEW`): **Allowed**, **Authorized pathways / `PARCOURS_ALLOWED`**.
- **Manage human resources** (`HR_MANAGE`): **Allowed**, **Authorized pathways / `PARCOURS_ALLOWED`**.
- Academic and student oversight actions must use `PARCOURS_ALLOWED`, not `SCHOOL_ALL`.
- **Permissions — view**, **Manage permissions**, and **Manage roles** must remain explicit **Denied** rules. A Principal never receives Access Control.

Local real-account assignments to reproduce if production does not already contain them:

- `gleina.blamsia`: Secondary FR + EN.
- `hamadou.tissia`: Primary FR + EN.
- `haoua.koulou.bouquet`: Kindergarten/Nursery FR + EN.
- `salam2`: Secondary FR + EN.

### Primary / Kindergarten teacher (`teacher`)

- User scope: `ASSIGNMENT_DERIVED`.
- Student directory/profile and academic roster: `ASSIGNED_CLASSES`.
- Assessment view/manage: `ASSIGNED_CLASSES`.
- Subject grade view/edit/submit: `ASSIGNED_CLASS_SUBJECTS`.
- Edit any subject in the homeroom class: `TITULAIRE_CLASSES`.
- Attendance roster/mark/finalize: `TITULAIRE_CLASSES`.
- Report-card validate/generate: `TITULAIRE_CLASSES`.
- Council input view/edit: `TITULAIRE_CLASSES` (V174).
- Coursebook view/manage: `TITULAIRE_CLASSES`.
- Timetable: own schedule only.
- Guardian-directory administration remains denied.

### Secondary teacher (`secondary_teacher`)

- User scope: `ASSIGNMENT_DERIVED`.
- Student/class visibility: only classes justified by a responsible subject or active HOMEROOM assignment.
- Subject grade view/edit/submit: `ASSIGNED_CLASS_SUBJECTS`.
- Attendance mark/finalize: `TIMETABLE_OCCURRENCES_ASSIGNED`.
- A Secondary Titulaire additionally gets class-wide results/report-card review, council-input **view**, attendance-roster view, attendance reopen, report-card validation/generation for `TITULAIRE_CLASSES`.
- Council-input **edit must remain ungranted**.
- A Secondary Titulaire must not edit another teacher's subject merely because they oversee the class.

### Accountant

- User scope: `GLOBAL`.
- Finance permissions include treasury accounts/movements, student financial accounts, consolidated receipts, payments, expenses and payroll according to the configured role.
- Local testing also granted broad setup rights: student import, enrollment/guardian management, class/subject/timetable/document configuration and additional payroll/refund actions. These are business-policy choices, not safe automatic defaults. Confirm each one before reproducing it in production.

## Manual role edits found in the local audit trail

Nine Access Control saves were made during testing:

1. Accountant: academic assessment and roster read, school-wide.
2. Principal: broad timetable operations, school-wide.
3. Primary Teacher: assessment View/Manage, assigned classes.
4. Primary Teacher: any-subject grade edit, homeroom classes.
5. Principal: academic and student actions narrowed to authorized pathways.
6. Principal: alerts, coursebook, dashboard, discipline, events, messages, and selected finance/payroll/refund/report actions changed to authorized pathways.
7. Principal: Human Resources View/Manage enabled for authorized pathways.
8. Accountant: enrollment, guardian and student-import authority enabled school-wide.
9. Accountant: additional school-setup, document, payroll, refund, timetable and teaching-configuration authority enabled school-wide.

Items 3, 4, 5 and 7 are part of the tested teacher/Principal model. Items 1, 2, 6, 8 and 9 are broader operational choices and must be reviewed rather than copied wholesale.

## Data not to promote

- QA accounts: `qa.primary.fr`, `qa.primary.en`, `qa.sec.subject`, `qa.sec.other`, `qa.sec.titulaire`, `qa.timetable.fr`, `qa.timetable.en`.
- The deterministic teacher-audit fixture and generated QA marks/packets.
- The 89 `permission_user_action` overrides on username `admin`.
- QA passwords or any local-only credentials.

## Current data anomaly

`aimaka.robert` is an active Primary teacher but locally has `parcours_scope_mode = 'NONE'`. If production matches, edit/resave the staff teaching assignments or set that account to `ASSIGNMENT_DERIVED`. Otherwise assignment-based class access remains blocked.

## Verification completed locally

- Primary teacher `qa.primary.fr`: CE1 A council inputs load and are editable.
- Secondary Titulaire `qa.sec.titulaire`: 6ème A council inputs load read-only; fields and Save/Submit are disabled.
- Secondary subject teacher `qa.sec.subject`: only Grade entry is available; Council and Report-card tabs are absent.
- Production preflight: zero missing expected role rules and zero Secondary council-edit grants.
- Backend regression: 227 tests passed, 0 failed.

## Post-deployment checklist

1. Confirm Flyway applied through V174.
2. Run `docs/production_permission_preflight_2026-08-25.sql` read-only.
3. Correct only reported gaps; do not replace whole role policies blindly.
4. Confirm Principal cycle assignments and Access Control denial.
5. Confirm every teacher uses the role matching the employee cycle and `ASSIGNMENT_DERIVED`.
6. Log out and back in after permission changes so the capability snapshot refreshes.
7. Smoke-test one Primary homeroom teacher, one Secondary subject teacher, one Secondary Titulaire, one Principal and one Accountant.
