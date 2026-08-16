# Permission Policy V2 — detailed implementation plan

## 1. Purpose

Replace the current coarse role × module permission matrix with a configurable, enforceable policy system that answers four separate questions for every request:

1. **Who is the actor?** School, account, employee/guardian identity, role profile and explicit user overrides.
2. **What may the actor do?** A precise action such as `STUDENT_PROFILE_VIEW`, `GRADE_EDIT`, `ATTENDANCE_MARK`, or `TIMETABLE_PUBLISH`.
3. **On which data?** All school data, selected parcours, assigned classes, titulaire classes, assigned class-subject pairs, own records, or linked children.
4. **Under which school rules?** Academic session, effective date, active enrollment, level model (maternelle/primary/secondary), publication state and workflow state.

The target rule is:

> **Allow = action grant AND data-scope match AND domain invariant.**

A broad module grant must never be enough by itself. The frontend may use the same policy result to simplify the interface, but the backend remains authoritative.

## 2. Findings from the current application

### 2.1 What already exists and should be retained

- `permission_grant`: role × module → `none/read/write`.
- `permission_action_grant`: role × action → allowed/denied, with fallback to the module grant.
- `app_user_parcours`: per-account level + subsystem restrictions.
- `AcademicAccessPolicyService`: strong session/date/class/subject/student enforcement for teacher academic access.
- `academic_access_delegation`: dated academic exceptions for a teacher.
- `TeacherScopeService`: class/student compatibility filter used by students, attendance, discipline, timetable and other services.
- `GuardianAccessService` and `student_guardian.receives_*`: per-child parent portal scope.
- Many finance endpoints already use precise action codes instead of a single `finance:write` check.
- The latest teacher-access branch already supports the correct academic base behavior: assigned subject teachers edit only their class-subject grades; a secondary titulaire gets class-wide read access by default; explicit dated delegation can expand academic rights.

### 2.2 Gaps that must be addressed

- Settings exposes only the wide module matrix. Action grants and data scopes are invisible and not administrable.
- `PermissionService` grants actions to a role only; there is no reusable per-user exception model.
- One account has one `role_code`, although an employee can have multiple employee roles. The effective login policy therefore does not cleanly compose multiple responsibilities.
- Several student operations share `students:write`; profile edits, registration, guardian links, transfers, withdrawals, import and deletion cannot be configured independently.
- Attendance action grants are precise, but teacher data enforcement is mainly class-wide. Secondary attendance must also require the teacher to own the timetable occurrence/class-subject-period being called.
- `timetable:read` exposes endpoints and UI tabs for master views, rooms, substitutions, availability and exports. Teachers should receive only their personal published schedule by default.
- `settings:write` is too broad. A principal who can edit one setting can currently inherit session, structure, roles, permissions and mail operations through fallback.
- An empty `app_user_parcours` list means unrestricted access. That is acceptable for explicitly global roles, but unsafe as an accidental fallback for a teacher with no valid assignment.
- Some frontend components use module permission fallback when action data has not loaded. This can temporarily expose controls that will later be rejected.
- The permission editor applies each matrix-cell click immediately, with no impact preview, reason, staged review, conflict warning, or effective-permission explanation.

## 3. Policy model

### 3.1 Keep three layers, but make their responsibilities explicit

#### Layer A — Feature/module visibility

`permission_grant` remains a coarse navigation and compatibility gate. It answers only: “May this profile enter this product area?” It must not authorize a business operation.

Examples:

- Teacher: Students `read`, Academic `read`, Attendance `read`, Timetable `read`.
- Finance officer: Finance `read`, Students `read` only when needed to identify a payer.
- Parent: Parent portal `read`; no staff modules.

#### Layer B — Action capability

Every backend command and sensitive query receives a stable action code. Action decisions become tri-state:

- `ALLOW`
- `DENY`
- `INHERIT`

An explicit deny must beat an allow inherited from a role. This is necessary to support “this principal normally has a management profile, but cannot manage sessions.”

#### Layer C — Resource/data scope

An action grant is evaluated against a scope expression:

- `SCHOOL_ALL`
- `PARCOURS_ALLOWED`
- `ASSIGNED_CLASSES`
- `TITULAIRE_CLASSES`
- `ASSIGNED_CLASS_SUBJECTS`
- `TIMETABLE_OCCURRENCES_ASSIGNED`
- `LINKED_CHILDREN`
- `SELF`
- explicit `CLASS_SET`, `SUBJECT_SET`, `CLASS_SUBJECT_SET`, or `PARCOURS_SET`

Scopes are intersected with tenant, active session/date and the domain’s non-overridable invariants.

### 3.2 Role profiles, account overrides and assignments

Use this precedence for an effective decision:

1. Hard domain invariant or tenant mismatch → deny.
2. Explicit account-level deny → deny.
3. Explicit account-level allow + matching scope → allow.
4. Combined active role-profile decisions; any explicit deny wins, otherwise an allow with matching scope wins.
5. No matching decision → deny.

Assignments are not ordinary permission rows. They are authoritative data relationships:

- primary/maternelle titulaire assignment;
- secondary responsible class-subject assignment;
- timetable occurrence ownership;
- guardian-child relationship.

Permission profiles decide what an actor may do **within** those relationships. They do not manufacture a teaching assignment.

### 3.3 Default profiles

#### Teacher — shared defaults

- See only allowed parcours inferred from current dated assignments.
- See student directory/profile only for actively enrolled students in an assigned or titulaire class.
- Student profile is read-only; no create, update, deactivate, parent-link changes, import, enrollment, transfer or withdrawal.
- See own published weekly timetable only.
- Cannot view master timetable, other teachers’ schedules, rooms/resources administration, conflicts, drift, substitutions administration or timetable versions unless explicitly granted.

#### Maternelle/Primary teacher

- `GRADE_VIEW`, `GRADE_EDIT`, `GRADE_SUBMIT` only for the class-subjects resolved to that teacher under the homeroom model.
- Because the valid model uses the class titulaire for all assigned curriculum subjects, the titular teacher normally gets all subjects for that class—not all primary students or all primary classes.
- Daily attendance roster, mark and finalize for titulaire classes only.
- No period-based attendance configuration.

#### Secondary subject teacher

- Student profile view for students in classes where the teacher has at least one active responsible class-subject assignment.
- Grade view/edit/submit only for the exact class-subject pairs assigned to the teacher.
- Attendance roster/mark/finalize only for published timetable occurrences where the teacher is the resolved responsible teacher or approved substitute.
- No class-wide grades and no other subject’s marks.

#### Titulaire / form teacher

- Inherits ordinary subject-teacher rights for subjects actually assigned to the person.
- Class-wide grade and report-card **view** for titulaire classes.
- No class-wide grade edit by default.
- Optional delegated `GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS` scope, granted with dates and reason. This expands edit rights only inside titulaire classes and only to curriculum subjects assigned to those classes.
- Student profile remains read-only unless a separate profile-management action is granted.

#### Finance officer / bursar / cashier / accountant

- Global school/parcours reach for finance operations by default, because a payer may belong to any parcours.
- Minimal student lookup scope required for payment: identity, matricule, class, guardian payer and balance. Do not return medical, discipline or unrestricted academic details.
- Actions split by duty: overview, fee configuration, charge generation, collection, reversal/refund, documents, ledger, close/reopen, payroll and reports.
- Separation-of-duties constraints prevent the same policy profile from both requesting and approving sensitive reversals/refunds unless the school explicitly enables a documented override.

#### Principal

- Default profile is operational oversight, not universal superuser.
- Suggested default allows dashboards, scoped reports, class results/report-card oversight, attendance analytics and audit viewing.
- Default denies: session management, class create/delete, curriculum/subject management, permission administration, role administration, mail configuration, timetable publication/override, ledger close/reopen and destructive student actions.
- These can be added through named permission bundles or explicit account overrides.
- Maintain one emergency owner account outside ordinary principal defaults with permission-admin capability; prevent removal of the last active permission administrator.

#### Parent/guardian

- Portal-only role.
- Data scope is always linked children with an active/effective guardian relationship.
- Per-child `receives_academic`, `receives_attendance`, `receives_finance`, `receives_discipline`, `receives_health` and `portal_access` remain hard filters.
- Parent profile configuration controls product features such as view bulletin, view attendance, view invoices/receipts, submit suggestion and download documents; it cannot grant access to unlinked children or staff endpoints.

## 4. Database changes

Implement through versioned Flyway migrations only. Never edit production rows or columns manually.

### 4.1 Action catalogue

Create `permission_action`:

- `code` primary key
- `module`
- `group_code` (Students, Academic, Attendance, Timetable, Finance, Settings, Parent, etc.)
- `label_fr`, `label_en`, `description_fr`, `description_en`
- `risk_level` (`LOW`, `MEDIUM`, `HIGH`, `CRITICAL`)
- `scope_type` (`NONE`, `STUDENT`, `CLASS`, `CLASS_SUBJECT`, `TIMETABLE_OCCURRENCE`, `PARCOURS`, `SCHOOL`, `SELF`, `CHILD`)
- `default_read_action` boolean
- `active`, `display_order`

Move the Java-only `PermissionActions.CATALOG` into this database-backed catalogue or generate both from one checked-in source. The backend must fail startup/test if a controller references an unknown action.

### 4.2 Role/profile action rules

Replace boolean-only semantics with `permission_role_action`:

- `school_id`, `role_code`, `action_code`
- `effect` (`ALLOW`, `DENY`, `INHERIT`)
- `scope_mode`
- optional `scope_payload JSONB`
- optional `effective_from`, `effective_to`
- `reason`, `version`, audit columns
- unique effective rule per school/role/action/scope key

Keep `permission_action_grant` during transition, backfill `true → ALLOW`, and read both until the cutover migration is verified.

### 4.3 Account-specific overrides

Create `permission_user_action` with the same effect/scope/date fields, keyed by `app_user.id`. Use it for exceptional authority, not routine teaching assignments.

Rules:

- expiry is mandatory for high-risk temporary grants unless marked permanent with a reason;
- account override UI must show the base role value and final effective value;
- disabling a user immediately invalidates all effective permissions.

### 4.4 Multiple active role profiles

The current login uses one `app_user.role_code` while employees can have several roles. Introduce `app_user_role`:

- `school_id`, `user_id`, `role_code`
- `is_primary`
- `effective_from`, `effective_to`
- `assigned_by`, `reason`

Backfill the current `app_user.role_code` as primary. During compatibility, keep the column synchronized; later treat it as display/default only. Effective permissions combine all active account roles using deny precedence.

### 4.5 Parcours policy

Add an explicit account scope mode so “no rows” is no longer ambiguous:

- `GLOBAL`: all school parcours (finance staff or explicitly global leadership).
- `EXPLICIT`: only rows in `app_user_parcours`.
- `ASSIGNMENT_DERIVED`: derive from active teaching/titulaire assignments.
- `CHILD_DERIVED`: parent’s linked children.
- `NONE`: no educational data until configured.

Backfill:

- teacher/form_teacher → `ASSIGNMENT_DERIVED`;
- parent → `CHILD_DERIVED`;
- accountant/bursar/cashier finance profiles → `GLOBAL`;
- existing users with explicit rows → `EXPLICIT`;
- administrators with intentionally unrestricted access → `GLOBAL`.

Fail closed: a teacher with no active assignment gets zero educational parcours and a useful readiness message, never FR+EN or all levels.

### 4.6 Audit and policy versioning

Every policy mutation records before/after, actor, target role/user, reason, correlation ID and timestamp. Add a monotonically increasing `school_permission_version`; include it in tokens or `/me/capabilities` responses so permission edits force an effective-policy refresh instead of waiting for token expiry.

### 4.7 Default-policy rollout must not silently remove current authority

The application currently seeds broad principal/module/action rights in bootstrap and several historical migrations. Changing the default principal profile therefore needs two distinct policies:

- **New schools:** receive the new least-privilege role templates immediately.
- **Existing schools:** first receive a generated “Legacy principal compatibility” profile reproducing their current effective access. An administrator then reviews a preview and explicitly adopts the safer “Principal oversight” template. Do not silently remove production authority during a Flyway migration.

Apply the same compatibility strategy to custom roles and existing action overrides. The migration report must list every user whose effective rights would change before enforcement is enabled.

## 5. Action catalogue to implement

### Students/family

- `STUDENT_DIRECTORY_VIEW`
- `STUDENT_PROFILE_VIEW`
- `STUDENT_PROFILE_CREATE`
- `STUDENT_PROFILE_EDIT`
- `STUDENT_PROFILE_DEACTIVATE`
- `STUDENT_PHOTO_VIEW`, `STUDENT_PHOTO_MANAGE`
- `STUDENT_IMPORT`
- `GUARDIAN_VIEW`, `GUARDIAN_LINK_MANAGE`, `GUARDIAN_ACCOUNT_MANAGE`
- `ENROLLMENT_VIEW`, `ENROLLMENT_CREATE`, `ENROLLMENT_TRANSFER`, `ENROLLMENT_WITHDRAW`
- `STUDENT_DOCUMENT_VIEW`, `STUDENT_DOCUMENT_GENERATE`, `STUDENT_DOCUMENT_REVOKE`

This split is mandatory to express “teacher may read assigned students but can never update or transfer them.”

### Academic

- Existing capabilities remain the domain vocabulary: roster, assessment view/manage, subject-grade view/edit/submit, class-results view, class-report-card view, packet review, validation, publication and council inputs.
- Add explicit `GRADE_EDIT_ANY_SUBJECT_IN_TITULAIRE_CLASS` as a delegable high-risk action/scope rather than overloading ordinary subject edit.
- Keep class-subject assignment and academic access delegation as dated authoritative inputs.

### Attendance

- `ATTENDANCE_ROSTER_VIEW`
- `ATTENDANCE_MARK`
- `ATTENDANCE_FINALIZE`
- `ATTENDANCE_REOPEN`
- `ATTENDANCE_ANALYTICS_VIEW`
- `ATTENDANCE_POLICY_MANAGE`
- `ATTENDANCE_RECONCILE`

Attach level-aware scopes and an occurrence resolver to each operational request.

### Timetable

- `TIMETABLE_MY_SCHEDULE_VIEW`
- `TIMETABLE_CLASS_SCHEDULE_VIEW`
- `TIMETABLE_MASTER_VIEW`
- `TIMETABLE_TEACHER_SCHEDULE_VIEW_ALL`
- `TIMETABLE_ROOM_VIEW`, `TIMETABLE_RESOURCE_VIEW`
- `TIMETABLE_DRAFT`, `TIMETABLE_PUBLISH`, `TIMETABLE_REOPEN`, `TIMETABLE_ARCHIVE`
- `TIMETABLE_SUBSTITUTION_VIEW`, `TIMETABLE_SUBSTITUTION_MANAGE`
- `TIMETABLE_EXPORT`, `TIMETABLE_OVERRIDE`

Teacher default is only `TIMETABLE_MY_SCHEDULE_VIEW`.

### Settings/structure

- `SCHOOL_PROFILE_VIEW`, `SCHOOL_PROFILE_MANAGE`
- `SESSION_VIEW`, `SESSION_MANAGE`
- `ACADEMIC_STRUCTURE_VIEW`, `CLASS_MANAGE`, `SUBJECT_MANAGE`, `CURRICULUM_MANAGE`, `TEACHING_ASSIGNMENT_MANAGE`
- `CALENDAR_VIEW`, `CALENDAR_MANAGE`
- `DISCIPLINE_CATALOG_MANAGE`
- `MAIL_CONFIG_MANAGE`
- `ROLE_VIEW`, `ROLE_MANAGE`
- `PERMISSION_VIEW`, `PERMISSION_MANAGE`
- `AUDIT_VIEW`

This prevents `settings:write` from making a principal an implicit system owner.

### Finance

Retain the existing detailed finance actions. Group them in the UI into Fees, Charges, Collections, Corrections, Documents, Accounting, Payroll and Reports. Add policy constraints for request/approve separation and use a finance-specific student summary DTO.

### Parent

- `PARENT_CHILD_SUMMARY_VIEW`
- `PARENT_ACADEMIC_VIEW`
- `PARENT_ATTENDANCE_VIEW`
- `PARENT_FINANCE_VIEW`
- `PARENT_DISCIPLINE_VIEW`
- `PARENT_HEALTH_VIEW`
- `PARENT_DOCUMENT_DOWNLOAD`
- `PARENT_SUGGESTION_SUBMIT`

Every action still requires `GuardianAccessService` for the requested child and feature.

## 6. Backend implementation

### 6.1 Central policy engine

Create `AuthorizationPolicyService` with a single contract:

```text
decide(actionCode, resourceContext) -> PolicyDecision
require(actionCode, resourceContext) -> PolicyDecision or coded 403
```

`ResourceContext` contains only identifiers and dates: school, session, effective date, parcours, class, subject, student, timetable occurrence, document and owner employee as applicable.

`PolicyDecision` returns:

- allowed/denied;
- stable denial code;
- human message FR/EN;
- winning rule source (role, user override, assignment, titulaire, guardian relationship);
- matched scope;
- policy version;
- optional repair hint.

Do not put security logic into controllers or Angular. Domain adapters resolve resource facts; the central engine combines actions/scopes/overrides.

### 6.2 Domain scope resolvers

Implement resolvers behind the policy engine:

- `AcademicScopeResolver`: adapt and preserve `AcademicAccessPolicyService` behavior.
- `StudentScopeResolver`: active enrollment by session/date; never rely solely on legacy `student.class_id`.
- `AttendanceScopeResolver`:
  - primary/maternelle DAILY → active titulaire assignment for class/date;
  - secondary PERIOD → published timetable occurrence + resolved responsible teacher or approved substitute;
  - titulaire visibility may be separately allowed, but marking another subject teacher’s period is denied by default.
- `TimetableScopeResolver`: own published schedule versus class/master/administrative resources.
- `FinanceScopeResolver`: school-wide finance identity/balance view, with field minimization.
- `GuardianScopeResolver`: linked child + relationship feature flags + effective dates.
- `ParcoursScopeResolver`: explicit mode described above.

### 6.3 Endpoint conversion

Convert controllers operation-by-operation. Do not leave a sensitive mutation protected only by module level.

Priority order:

1. Students, enrollment and guardian endpoints.
2. Academic endpoints not yet routed through `AcademicAccessPolicyService`.
3. Attendance roster/session/analytics endpoints.
4. Timetable read and write endpoints.
5. Settings/setup/session endpoints.
6. Finance and payroll consistency audit.
7. Parent portal endpoints.

Each list endpoint must filter in the query/service before DTO construction. Each direct-ID endpoint must call `require`; returning 404 instead of 403 for out-of-scope student/resource IDs may be used to reduce enumeration, while logs keep the precise internal denial code.

### 6.4 Student-specific enforcement

- Replace student controller guards with precise action codes.
- `StudentService.list/get` use active-enrollment scope for teachers.
- Create a `StudentTeacherView` DTO containing educationally necessary fields only. Do not expose guardian credentials, finance details, health or sensitive internal fields through the generic teacher view.
- Student profile edit never follows from profile view.
- Transfer calls require `ENROLLMENT_TRANSFER`; ordinary `STUDENT_PROFILE_EDIT` cannot move a student.
- Remove class movement from the generic profile update DTO; class changes must go exclusively through enrollment transfer with effective date, reason, version and audit.
- Hide and reject registration/import/family-link operations for teacher defaults.

### 6.5 Academic enforcement

- Keep responsible class-subject assignment as the default edit source.
- Keep titulaire class-wide result/report-card view.
- Add a dated, audited titulaire “any subject grade edit” delegation that the policy engine resolves after validating class titularity.
- A direct request for another subject remains 403 even if the student is visible through another taught subject.
- Ensure grade packet, comments, competencies, imports, batch jobs and PDFs resolve the same scope; no alternate route may bypass it.

### 6.6 Attendance enforcement

- Add `academicSessionId`, `occurrenceDate`, `classId`, `periodKey`, `subjectCode` and timetable occurrence/version to the attendance resource context.
- On secondary roster creation/open/mark/finalize, resolve the published slot and teacher. Reject a mismatched class, subject, teacher, date or period with a precise code.
- Approved substitution grants attendance rights only for the substituted occurrence/date.
- Primary/maternelle enforce exactly one DAILY session and the dated titulaire.
- Reopen remains a management-only action by default.
- Analytics scope follows allowed classes/occurrences; cross-school and out-of-parcours rows are filtered at query level.

### 6.7 Timetable enforcement

- `/teachers/me` requires only `TIMETABLE_MY_SCHEDULE_VIEW` and derives the employee from the authenticated account.
- Teachers must not receive the arbitrary teacher picker, master view, class builder, rooms, substitutions or version administration by default.
- `/teachers/{teacherId}`, master, conflict, room/resource, drift and export endpoints each require their dedicated actions.
- Every timetable mutation requires a write action. Preserve canonical class-subject teacher locking and conflict validation as domain invariants.

### 6.8 Settings and principal defaults

- Replace wide `settings:write` guards with precise settings actions.
- Protect permission/role administration with `PERMISSION_MANAGE`/`ROLE_MANAGE` and require a reason.
- Prevent self-lockout and deletion/denial of the last active permission administrator.
- For critical changes, require current password re-authentication or a short-lived elevated token if the application’s authentication architecture supports it.

### 6.9 Cache and token behavior

- Keep permission checks live or cache by `(school, user, policyVersion)` for a short period.
- Increment policy version on role, action, scope, assignment or user-override changes.
- `/api/auth/me` or `/api/me/capabilities` returns the current version, navigation capabilities and allowed parcours; Angular refreshes when stale.

## 7. Frontend and user experience

### 7.1 Replace the spreadsheet-like permission editor

Keep the module matrix only as an “Overview” tab. Add a dedicated **Access control** workspace:

1. Select a role profile or individual account.
2. Show a role summary card: purpose, active users, parcours mode and warning count.
3. Show capability groups as understandable cards: Students, Academic, Attendance, Timetable, Finance, Settings, Parent.
4. Each capability row has `Inherited / Allowed / Denied`, scope selector, effective dates where relevant and an explanation.
5. A sticky “Effective access preview” summarizes the result in plain language.
6. Save all staged changes together after review; require a reason for high-risk changes.

Do not make every click persist immediately.

### 7.2 Role-specific templates

Provide safe templates:

- Primary/Maternelle teacher
- Secondary subject teacher
- Titulaire/form teacher
- Finance collector/cashier
- Accountant
- Bursar/finance manager
- Principal oversight
- Parent portal
- Custom from blank

Applying a template opens a preview showing additions, removals, affected users and warnings. It never silently overwrites explicit user exceptions.

### 7.3 Employee/account drawer

From Staff → employee detail, add **Access & responsibilities**:

- linked login status;
- active permission profiles;
- derived parcours;
- teaching/titulaire assignments (read-only links to Academic setup);
- explicit account exceptions;
- expiry and reason;
- “Preview as this user” permission summary (server-evaluated, no impersonation);
- readiness blockers, e.g. teacher account has no employee link or no assignment.

### 7.4 Role-specific product shells

The product should render only relevant screens:

- Teacher: My classes, My students, Grade entry, Attendance, My timetable, Messages.
- Finance: Finance workspace plus minimal payer/student search; no academic/attendance settings.
- Parent: parent portal only.
- Principal: oversight dashboard and explicitly granted operational screens.

Navigation is driven by effective capabilities—not only module level. Deep links still rely on backend enforcement and show a clear “You do not have this permission” page with a repair/contact hint.

### 7.5 Screen-specific behavior

- Students: teacher gets view-only cards/details; edit, add, import, guardian administration and enrollment transfer controls are absent.
- Academic: class and subject selectors contain only server-returned allowed options. Titulaire class overview is clearly labelled “Read-only” unless delegated edit exists.
- Attendance: secondary teacher sees only their published periods/subjects; primary teacher sees DAILY classes. Explain why an expected roster is absent.
- Timetable: teacher lands directly on “My weekly schedule”; no empty admin tabs or inaccessible dropdowns.
- Settings: permission editor uses search, filters, collapsible groups, changed-only mode, risk badges, plain-language descriptions and a final confirmation summary.

### 7.6 Never use optimistic permission fallback

Action-aware components must use a loading state until capability data is available. Do not use `action ?? moduleWrite` for a high-risk control. If capabilities fail to load, fail closed and show a retry message.

## 8. API design

Suggested endpoints:

- `GET /api/access/catalog`
- `GET /api/access/roles/{roleCode}`
- `PUT /api/access/roles/{roleCode}` — staged batch + version + reason
- `GET /api/access/users/{userId}`
- `PUT /api/access/users/{userId}/overrides`
- `GET /api/access/users/{userId}/effective?sessionId=&date=`
- `POST /api/access/preview` — returns before/after effective access and blockers
- `GET /api/access/me/capabilities?sessionId=&date=`
- `GET /api/access/audit`

Responses should group capabilities by product area and return localized labels, effective effect, source, scope summary, expiry and denial/repair messages.

Use optimistic locking (`version`) on profile and user-policy updates. A stale edit returns 409 with a readable diff, not a generic conflict.

## 9. Migration and rollout plan

### Phase 0 — Policy inventory and freeze

- Generate a machine-readable inventory of every controller endpoint and current guard.
- Map every endpoint to one target action and resource scope.
- CI fails when a protected controller lacks a mapped action or references an unknown action.
- Capture current role/module/action/parcours state for every tenant before migration.

### Phase 1 — Policy engine in shadow mode

- Add catalogue, role rules, user overrides, explicit parcours modes and audit tables.
- Backfill existing behavior without changing authorization outcomes.
- Run the new engine alongside the old one; log decision differences with no user impact.
- Resolve all unexplained differences before enforcement.

### Phase 2 — Student and academic enforcement

- Split student/enrollment/guardian actions.
- Remove class transfer from generic student update.
- Reuse the existing academic resolver in the central engine.
- Deliver teacher and titulaire UX first because these are the highest-risk cross-student/cross-subject paths.

### Phase 3 — Attendance and timetable enforcement

- Add occurrence-aware attendance checks.
- Restrict teachers to personal timetable API/UI.
- Preserve management views for explicitly authorized profiles.

### Phase 4 — Finance, settings and parent profiles

- Surface existing finance actions in the policy editor and add separation-of-duty validation.
- Split settings/setup action guards and change principal defaults.
- Surface parent capability templates while preserving guardian-child feature flags.

### Phase 5 — Account overrides and advanced UX

- Add individual account exceptions, expiry, preview and impact analysis.
- Add policy diff, audit view, copy profile and safe template workflows.
- Retire boolean-only `permission_action_grant` after production verification.

### Phase 6 — Hardening

- Remove module fallback from all sensitive actions.
- Make explicit parcours mode mandatory for every account.
- Security regression suite and penetration-style ID enumeration tests.
- Operational documentation, administrator training and rollback drill.

## 10. Test plan

### 10.1 Required persona matrix

Seed at least:

- primary titulaire A and primary titulaire B;
- secondary French teacher and Math teacher sharing one class;
- secondary titulaire who teaches one subject;
- approved substitute for one occurrence;
- principal default and principal with temporary session grant;
- finance collector, refund approver, accountant and bursar;
- parent linked to one of two siblings with different feature flags;
- teacher account with missing employee link and teacher with no assignment.

Include FR and EN classes in all three levels.

### 10.2 Negative tests are mandatory

- Teacher cannot enumerate another class’s students by list filter or direct ID.
- Teacher cannot update a visible student, photo, guardian, enrollment or class.
- French teacher cannot read/write Math marks even for a student visible through French.
- Titulaire sees all class marks but cannot edit another subject without delegation.
- Delegated titulaire edit works only for the specified class, dates and session and stops immediately on expiry/revocation.
- Secondary teacher cannot mark attendance for another subject/period or a day without their published slot.
- Substitute can mark only the approved occurrence.
- Primary teacher cannot create period attendance; secondary teacher cannot create DAILY attendance.
- Teacher can read `/teachers/me` but receives 403/404 from master/admin timetable endpoints.
- Finance user can find and charge a student in every parcours but cannot view marks, health or discipline.
- Principal default cannot manage sessions/classes/subjects/permissions; explicit grant enables exactly the granted action.
- Parent cannot access an unlinked child or a feature whose relationship flag is false.
- Empty teacher assignment returns no parcours/data, never all parcours.
- Direct backend calls remain denied even when the frontend control is manually re-enabled.

### 10.3 Automated levels

- Unit tests for precedence, tri-state inheritance, scope intersection and expiry.
- Repository/query tests proving filtered lists do not load unauthorized rows.
- Controller integration tests for every action code.
- Flyway tests upgrading a production-like database and a fresh empty database.
- Browser tests per persona covering navigation, selectors, read-only states, errors and mobile layouts.
- Concurrency tests for policy version conflicts and assignment changes during grade/attendance saves.
- Audit tests verifying before/after, reason and actor for critical grants.

## 11. Acceptance criteria

The work is complete only when all of the following are true:

- No sensitive endpoint relies solely on module `read/write`.
- Every action in the UI has the same backend action code and effective policy source.
- Teachers see only actively assigned/titulaire classes and students.
- Subject teachers can edit only their assigned class-subject grades.
- Titulaires have class-wide result visibility and no implicit class-wide edit.
- Secondary attendance is occurrence/subject/teacher scoped; primary/maternelle is titulaire/daily scoped.
- Teachers have a personal timetable view and no timetable mutation or administration access by default.
- Finance staff operate across all students only through finance/minimal-payer views.
- Principal defaults exclude session, structure and permission management.
- Parent access remains linked-child and feature-flag scoped.
- Parcours enforcement fails closed for unassigned teachers.
- Settings provides understandable role templates, action-level configuration, scope selection, individual overrides, preview, audit and safe staged saving.
- Upgrades preserve existing data through Flyway and produce a reviewed before/after policy report.

## 12. Recommended implementation order for another agent/team

1. Build the endpoint/action inventory and target catalogue.
2. Add new schema and backfill migrations.
3. Build central policy engine, precedence tests and effective-access API.
4. Adapt existing academic policy into the engine without changing behavior.
5. Split student/enrollment/guardian actions and DTOs.
6. Implement attendance occurrence resolver.
7. Split timetable personal/admin actions and simplify teacher UI.
8. Split settings/setup actions and install safe principal defaults.
9. Surface existing finance actions and duty-conflict validation.
10. Build the new Access control UI and staff-account drawer.
11. Add parent profile presentation over existing guardian feature flags.
12. Run shadow comparison, fix deltas, enable enforcement by module, then remove legacy fallback.

This order minimizes regressions: it first establishes one decision engine, then migrates the highest-risk student/academic/attendance/timetable paths, and only afterward changes administrator-facing configuration and defaults.
