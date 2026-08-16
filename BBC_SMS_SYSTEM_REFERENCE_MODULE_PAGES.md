# BBC SMS module reference pages - isolated candidate

This companion reference expands the compact system map in
`BBC_SMS_SYSTEM_REFERENCE.md` into one operator page per required feature
area. It describes only the candidate exercised on the isolated
`bbc-full-e2e` stack (UI 8100, API 8101, PostgreSQL 16, Mailpit 8125).

The evidence links are authoritative for exact IDs, HTTP responses, policy
versions, and database read-backs. Route-specific UI captures and common
empty/filled/validation/success states are now committed under
`qa/e2e-runs/2026-08-14-full-school/final/screenshots/`; the index and remaining
visual gaps are in `final/screenshot-pack.md`. Denied/locked/mobile coverage is
not claimed PASS by implication.

## Shared operating rules

- Tenant is derived from the authenticated principal. A client school header
  is not a tenant-switch mechanism.
- V2 action and resource scope are authoritative at the API/service boundary;
  a visible UI control is not permission evidence by itself.
- Use one browser tab, explicit login, parcours selection where required, and
  explicit logout before the next persona. Earlier shared-localStorage
  concurrent-tab observations are invalidated.
- Read-only DB reconciliation is used only for material state, accounting,
  audit, migration, and cleanup checks. No production-like migration repair,
  checksum edit, or manual acceptance-DB mutation was used.
- The nurse route/API case is deferred and out of scope for this run. The
  Health page below documents parent-safe health behavior only and does not
  add nurse-specific route evidence.

## 1. Authentication, parcours selection, and shell

1. **Purpose/owner:** authenticate staff/parent users and establish the shell workspace; owner: platform/authentication.
2. **Actors/actions/scopes:** admin, Direction, registrar, teachers, finance personas, parent; login/session and parcours-selection actions.
3. **Prerequisites:** seeded account, accepted invitation/reset where applicable, isolated Mailpit credential reference.
4. **Navigation FR/EN:** `/login` (Connexion/Login), `/parcours` (Parcours/Workspace), `/apps` (Applications/Apps).
5. **Field dictionary:** username/email; password; selected parcours; selected session/class context; required credentials are validated before submit.
6. **State/lifecycle:** `logged out -> credentials submitted -> authenticated -> parcours selected -> shell ready -> logged out`.
7. **Normal workflow:** open Login, authenticate, choose the allowed workspace, then open the required module from Apps.
8. **Alternate/edge workflows:** invitation acceptance, parent invite/reset, expired token, invalid credentials, missing parcours, and explicit logout before persona switching.
9. **Data changes:** session/token state and audit/authentication events; no domain record is created by ordinary login.
10. **Upstream dependencies:** app user, role/V2 policy, parcours catalog, Mailpit for invitation/reset.
11. **Downstream consumers:** every UI route and API authorization decision consumes the authenticated principal and selected context.
12. **Errors/correction:** invalid credentials or missing context stays on the login/context flow with a human-readable error; reauthenticate and select the required parcours.
13. **Audit/concurrency/idempotency:** token/session handling is per browser context; explicit logout prevents shared-localStorage contamination; repeated login does not create domain duplicates.
14. **Notifications/jobs/documents:** invitation, reset, and credential messages are captured in Mailpit when the flow produces them.
15. **Reports/metrics:** authentication success/failure and current-user/capability responses support the permission matrix.
16. **Security/privacy:** credentials and bearer tokens stay in memory and are not written to evidence; parent scope is relationship-filtered.
17. **Screenshots:** route-specific screen plus common empty/filled/validation/success captures are in `final/screenshots/`; denied/locked/mobile states remain explicitly open in `final/screenshot-pack.md`.
18. **Tests/status:** Gate 0/1, Gate 5/6 credential evidence, Gate 14 clean-session method; exercised PASS, exhaustive UI screenshot coverage open.

## 2. Settings: school, calendar, discipline, mail, and roles

1. **Purpose/owner:** configure tenant school profile, calendar, discipline catalog, mail, and role foundations; owner: administration/settings.
2. **Actors/actions/scopes:** bootstrap admin for setup; ordinary personas receive only their configured settings reads/actions.
3. **Prerequisites:** fresh bootstrap school, V124-V125 setup/session authorities, Mailpit endpoint.
4. **Navigation FR/EN:** `/settings` (Paramètres/Settings), settings tabs for school, calendar, discipline, mail, and roles.
5. **Field dictionary:** school name/code/contact; holiday date/label; discipline code/label/category; SMTP host/port/from; role/action/effect/scope.
6. **State/lifecycle:** `empty bootstrap -> draft edit -> validated -> saved -> read-back`; policy `draft -> preview -> apply -> versioned`.
7. **Normal workflow:** open Settings, choose a tab, enter validated values, save, reload, and confirm the value in UI/API/DB evidence.
8. **Alternate/edge workflows:** overlapping holiday, invalid date, missing required profile value, inherited-scope safe-template preview, stale policy version, and cancel-without-write.
9. **Data changes:** school/profile, calendar days, discipline catalog, mail configuration, and policy-role/user rows.
10. **Upstream dependencies:** bootstrap migration and authenticated tenant; calendar drives attendance and reporting windows.
11. **Downstream consumers:** attendance expected sessions, reports, notifications, authorization, and all module labels/context.
12. **Errors/correction:** validation names the field and correction; policy conflicts require a fresh version; do not repair by direct SQL.
13. **Audit/concurrency/idempotency:** policy versions advance once per apply; stale apply returns a conflict; preview is non-mutating; setup writes use supported API idempotency where present.
14. **Notifications/jobs/documents:** mail configuration feeds invitation/reset/notification delivery; no raw SMTP secret is recorded.
15. **Reports/metrics:** calendar holiday counts, discipline catalog counts, policy version, and mail health are recorded in Gate 2 evidence.
16. **Security/privacy:** no ordinary role template was broadened for bootstrap fixes; V2 scope and role boundaries remain enforced.
17. **Screenshots:** `screenshots/route-settings.png` plus common state captures are committed; Settings denied/locked/mobile states remain open in `final/screenshot-pack.md`.
18. **Tests/status:** P0-01/P0-02, V124/V125, Permission Policy V2 regression, and Gate 2 are PASS; screenshot completion remains open.

## 3. Permission Policy V2 and access control

1. **Purpose/owner:** manage action/effect/scope rules while preserving least privilege; owner: platform security.
2. **Actors/actions/scopes:** bootstrap admin for supported policy workspace; role/user rules with `ALLOW`, `DENY`, `INHERIT` and school/class/parcours/resource scope.
3. **Prerequisites:** V118-V123 policy schema, known role/user, current policy version, selected resource context.
4. **Navigation FR/EN:** `/access-control` (Contrôle d'accès/Access control); supported API `/access/roles/{role}` and policy decision routes.
5. **Field dictionary:** actionCode; effect; scopeMode; scopePayload; effectiveFrom/effectiveTo; permanent; reason; expectedPolicyVersion; confirmation.
6. **State/lifecycle:** `read -> preview -> confirm -> apply -> cache decision -> expiry/restore -> audit read-back`.
7. **Normal workflow:** inspect role/user, preview a narrow change, confirm if high-risk, apply with current version, test decision, restore temporary test authority.
8. **Alternate/edge workflows:** inherited null scope, stale version 409, expired rule, resource mismatch, ordinary-role denial, and cross-child denial.
9. **Data changes:** permission role/user action rows, policy version/audit rows, and cache/decision state.
10. **Upstream dependencies:** authenticated principal, action catalog, resource resolver, school/role/user records.
11. **Downstream consumers:** every controller/service guard, UI route guard, and filtered read/write response.
12. **Errors/correction:** `POLICY_VERSION_CONFLICT` means reread and retry; `POLICY_RULE_MISSING`, `FORBIDDEN`, or scope mismatch is a real denial to preserve; `CONTEXT_REQUIRED` selects UI context without bypassing API policy.
13. **Audit/concurrency/idempotency:** policy version is optimistic concurrency; cache apply/expiry probe was green; temporary rules were restored and DB-reconciled.
14. **Notifications/jobs/documents:** policy audit/decision records; no business document is created by a policy preview.
15. **Reports/metrics:** policy version, decision code, allow/deny counts, and matrix totals are in Gate 14 evidence.
16. **Security/privacy:** ordinary roles are not widened; resource checks occur after resolving student/class/relationship/session resources.
17. **Screenshots:** `screenshots/route-access-control.png` plus common state captures are committed; denied/locked/mobile states remain open.
18. **Tests/status:** P0-02, policy cache apply/expiry, 19/19 positive and 18/18 forbidden API matrix, and focused security contracts PASS; exhaustive matrix remains IN PROGRESS.

## 4. Sessions, terms, reporting milestones, optional windows, and reuse

1. **Purpose/owner:** establish the academic-year timeline and reusable reporting structure; owner: academic administration.
2. **Actors/actions/scopes:** bootstrap admin/Direction session and configuration actions; teacher/parent access is read-scoped.
3. **Prerequisites:** school profile, non-overlapping date ranges, session authority, standard products/dependencies.
4. **Navigation FR/EN:** Settings session/term workspace (Années & périodes/Sessions & terms) and academic setup wizard.
5. **Field dictionary:** code/name; start/end date; status/current flag; term code/dates; reporting product; sequence; optional window; dependency.
6. **State/lifecycle:** `DRAFT -> OPEN/CURRENT -> CLOSED -> ARCHIVED`; configuration `preview -> apply -> read-back -> repeat/idempotent`.
7. **Normal workflow:** create three sessions, mark one current/open, configure T1-T3/products, preview standard structure, apply, reload, and verify counts.
8. **Alternate/edge workflows:** second-current rejection, overlapping dates, T2/T3 window guard, unresolved blockers, date-shift boundary, cancel, and repeated preview.
9. **Data changes:** academic session, term, reporting period, dependency, workflow-window, and copy-run rows.
10. **Upstream dependencies:** school calendar and section/class catalog.
11. **Downstream consumers:** enrollment defaults, curriculum, assessment, attendance, report cards, finance, and promotion.
12. **Errors/correction:** date/window message names the conflicting period and corrective screen; a reuse boundary error was corrected through the supported session API.
13. **Audit/concurrency/idempotency:** reuse preview is non-mutating; repeated apply keeps existing rows; session state changes require reason and audit.
14. **Notifications/jobs/documents:** reuse copy run and audit metadata; no document is generated at this stage.
15. **Reports/metrics:** session/term counts, product/dependency counts, optional-window status, and copy fingerprint are recorded.
16. **Security/privacy:** only scoped administrators configure; parent/teacher reads cannot alter session structure.
17. **Screenshots:** `screenshots/route-journey.png` and `screenshots/route-settings.png` provide route captures; session-specific denied/locked/mobile states remain open.
18. **Tests/status:** Gate 3 PASS for exercised lifecycle, copy idempotency, window guards, and fresh 8100 read-back; exhaustive route imagery open.

## 5. Sections, classes, subjects, class subjects, evaluations, and designs

1. **Purpose/owner:** define the bilingual Nursery/Primary/Secondary academic structure and default assessments; owner: academic administration.
2. **Actors/actions/scopes:** bootstrap admin/registrar/academic setup authorities; teachers consume assigned scope.
3. **Prerequisites:** current session, six canonical sections, class levels/subsystems, subject catalogue, coefficients.
4. **Navigation FR/EN:** academic setup (Structure académique/Academic structure) and evaluations (Évaluations/Evaluations).
5. **Field dictionary:** section code/label/subsystem; class name/level; subject code/name; coefficient; group; curriculum row; assessment sequence/max/weight/required.
6. **State/lifecycle:** `catalog -> section/class -> subject/coefficient -> curriculum -> assessment defaults -> existing/read-only repeat`.
7. **Normal workflow:** create/read six sections and nine classes, configure subjects/coefficients/curricula, preview/apply assessment defaults, reload and read back.
8. **Alternate/edge workflows:** empty control classes, invalid code/duplicate, UTF-8 labels, wrong subsystem, repeated S1, all-six-sequence generation, and missing homeroom readiness.
9. **Data changes:** sections, classes, subjects, coefficients, groups, curriculum, and assessment rows.
10. **Upstream dependencies:** sessions/terms and section catalogue.
11. **Downstream consumers:** staff assignments, timetable, attendance, grades, report cards, and promotion.
12. **Errors/correction:** UTF-8 verifier and readiness messages identify the row/context; contextual UI guard only selects resource context and does not bypass API authorization.
13. **Audit/concurrency/idempotency:** assessment repeat returned existing rows rather than duplicates; setup mutations are scoped and read back.
14. **Notifications/jobs/documents:** assessment-default generation is a bounded job/preview; downstream reports consume immutable assessment definitions.
15. **Reports/metrics:** 6 sections, 9 classes, 30 subjects, 47 coefficients, 4 groups, 7 populated curricula, 36 assessment defaults.
16. **Security/privacy:** teacher sees only assigned academic scope; unrelated subsystem/class data is denied or filtered.
17. **Screenshots:** `screenshots/route-settings.png` provides the setup screen and common states are committed; academic-setup denied/locked/mobile states remain open.
18. **Tests/status:** Gate 4 PASS, UTF-8 verifier PASS, assessment idempotency PASS, and V126/V127 contracts PASS.

## 6. Staff, HR, accounts, academic responsibilities, and delegations

1. **Purpose/owner:** manage employees, accounts, departments, assignments, leave, and HR/payroll inputs; owner: HR/administration.
2. **Actors/actions/scopes:** HR bootstrap fixture exception only for test setup; Direction/HR/accountant actions remain narrow; teachers consume assignments.
3. **Prerequisites:** departments, employee form, role catalog, mail, current academic classes/subjects.
4. **Navigation FR/EN:** `/staff` (Personnel/Staff), responsibilities/delegations inside Staff/academic setup.
5. **Field dictionary:** employee code/name/email/type/sex/department/level; account username/role; class/subject responsibility; leave dates/status; payroll salary/hours.
6. **State/lifecycle:** `draft employee -> saved -> account invited/active -> responsibility assigned -> leave approved -> payroll eligible`.
7. **Normal workflow:** create employee, add account, assign class/subject, reload directory, verify Mailpit credential evidence, then test scope.
8. **Alternate/edge workflows:** incomplete form, duplicate email/code, invalid department, assignment mismatch, leave approval, import/payroll controls, and denied ordinary role.
9. **Data changes:** employee, app user/role, teacher-class/subject, staff application, leave, payroll component rows.
10. **Upstream dependencies:** school, departments, classes/subjects, permission policy, Mailpit.
11. **Downstream consumers:** timetable inheritance, teacher attendance/grades, HR/payroll, route visibility.
12. **Errors/correction:** form errors identify field; authorization denials remain 403; bootstrap HR fixture authority is test-specific and restored/recorded.
13. **Audit/concurrency/idempotency:** account/assignment changes are versioned/audited; repeated fixture reads are idempotent.
14. **Notifications/jobs/documents:** invitation/reset/credential email via Mailpit; payroll jobs consume employee data.
15. **Reports/metrics:** 15 credentialed accounts plus disposable EMP-016, four departments, teacher links/homerooms/secondary assignments, and payroll totals.
16. **Security/privacy:** no password/token in evidence; HR authority was applied only as test-specific bootstrap exception and ordinary roles were not broadened.
17. **Screenshots:** `screenshots/route-staff.png` plus common states are committed; Staff denied/locked/mobile states remain open.
18. **Tests/status:** Gate 5 PASS for exercised API/UI/HR lifecycle; Gate 14 persona write/network inventory remains open.

## 7. Students, enrollments, families, imports, profiles, and documents

1. **Purpose/owner:** register students, enroll them, link guardians/families, import safely, and maintain profiles/documents; owner: registrar.
2. **Actors/actions/scopes:** registrar manages directory/enrollment; teachers have assigned roster scope; parents have linked-child scope.
3. **Prerequisites:** current session/classes, guardian identity model, family roles, registrar account, UTF-8 input.
4. **Navigation FR/EN:** `/students` (Élèves/Students), `/students/new`, `/students/import-family`, `/students/:id`.
5. **Field dictionary:** first/last name; NIU/matricule; sex; DOB/birthplace; class; guardian identity/contact/relation; photo; import row/error.
6. **State/lifecycle:** `draft registration -> validated -> enrolled -> active roster -> profile/document update -> deactivated/completed`.
7. **Normal workflow:** register, link/deduplicate family, upload photo, preview/commit family import, reload roster/profile, and read parent relationship.
8. **Alternate/edge workflows:** duplicate key row, invalid row, same-name identity, shared family, new-job replay versus same-job retry, unauthorized student update, and inactive cleanup.
9. **Data changes:** student, enrollment, guardian, parent/student links, import job/row, photo/document rows.
10. **Upstream dependencies:** classes/session, guardian/account policy, Mailpit for invitations.
11. **Downstream consumers:** timetable/attendance, grades, finance, report cards, promotion, parent portal.
12. **Errors/correction:** preview marks row-level errors and corrective action; scope denial remains `ENROLLMENT_SCOPE_MISMATCH`/403; do not use direct DB cleanup in acceptance.
13. **Audit/concurrency/idempotency:** same import job retry created zero duplicates; student writes and cleanup were read back; parent links are relationship-scoped.
14. **Notifications/jobs/documents:** family import job, invitation/reset, profile photo versions, student documents.
15. **Reports/metrics:** 24 stable manual students, nine roster counts, family dedup/read-back, photo save, and import outcomes.
16. **Security/privacy:** parent sees only linked children; teachers see assigned scope; PII is synthetic and secrets excluded.
17. **Screenshots:** `route-students.png`, `route-students-new.png`, `route-students-import-family.png`, `state-empty-filtered-list.png`, `state-form-filled.png`, and `state-validation-error-student.png` are committed; denied/locked/mobile states remain open.
18. **Tests/status:** Gate 6 PASS; latest Gate 14 registrar create/deny/admin-supported cleanup is PASS for the exercised surface.

## 8. Timetable

1. **Purpose/owner:** create, validate, publish, reopen, and view class/teacher schedules; owner: academic operations.
2. **Actors/actions/scopes:** timetable manager/admin; teachers read own published schedule; class-subject responsibility controls inherited teacher.
3. **Prerequisites:** classes/curricula, responsible teachers, rooms, bell periods P1-P8, current session.
4. **Navigation FR/EN:** `/timetable` (Emploi du temps/Timetable), class schedules, teacher schedules, bell periods.
5. **Field dictionary:** day; period; start/end; class; subject; inherited teacher; room; version/status/reason; export format.
6. **State/lifecycle:** `draft -> validated -> published -> read-only -> reopened with reason -> republished`; stale update -> 409.
7. **Normal workflow:** configure periods, fill class grid, resolve teacher/room, publish, open teacher view, export, and read back version.
8. **Alternate/edge workflows:** teacher conflict, room conflict, wrong responsible teacher, invalid level, unassigned subject, incomplete publish, stale version, cancel reopen.
9. **Data changes:** timetable version/config/slots, room availability, assignment audit, substitutions where applicable.
10. **Upstream dependencies:** class subjects, teacher assignments, rooms, calendar/session.
11. **Downstream consumers:** attendance expected occurrences and teacher schedule/parent operational views.
12. **Errors/correction:** stable conflict names teacher/room/class/period and no slot is persisted; stale update requires refresh.
13. **Audit/concurrency/idempotency:** publish/reopen has version/reason audit; TT-007 stale write returns 409; exports reflect published version.
14. **Notifications/jobs/documents:** CSV/ICS/XLSX/PDF exports; no hidden slot mutation on read.
15. **Reports/metrics:** 230 published V4 slots, zero steady-state conflicts, eight valid periods, four exports 200.
16. **Security/privacy:** teacher endpoint is own view; class/manager selectors are not exposed to teacher scope.
17. **Screenshots:** `screenshots/route-timetable.png` is committed; conflict/denied/locked/mobile-specific states remain open.
18. **Tests/status:** Gate 7 PASS, TT-001-TT-007 exercised, including stale reopen/publish guards.

## 9. Attendance

1. **Purpose/owner:** generate expected sessions, run roll calls, finalize marks, analyze attendance, alert, and reconcile devices; owner: school operations.
2. **Actors/actions/scopes:** admin/attendance manager, form teacher assigned roster, signed device, parent linked-child read.
3. **Prerequisites:** calendar/holidays, timetable, current enrollments, attendance policy, device registration.
4. **Navigation FR/EN:** `/presence` (Présence/Attendance), Devices/Reconciliation, analytics/alerts.
5. **Field dictionary:** date; period/session; class; student; mark; source; reason; device ID/signature; finalized/version; threshold.
6. **State/lifecycle:** `expected preview -> generated -> open -> marks saved -> finalized -> analytics/alerts -> device reconciliation`.
7. **Normal workflow:** preview/generate, open roster, save/finalize marks, reload analytics, scan device, reconcile, and inspect alerts.
8. **Alternate/edge workflows:** holiday exclusion, stale save, duplicate scan, invalid signature, cross-student scan, teacher out-of-scope roster, threshold dedup.
9. **Data changes:** attendance sessions/records/events/notifications, device heartbeat/reconciliation, alert rows.
10. **Upstream dependencies:** calendar, timetable, enrollment, attendance policy, device.
11. **Downstream consumers:** analytics, parent School life, report-card attendance/conduct, alerts.
12. **Errors/correction:** stale version 409, cross-student 400, duplicate 409, device reconciliation instructions; do not retry blindly.
13. **Audit/concurrency/idempotency:** finalized marks immutable/guarded; repeated alert scan deduplicates; signed duplicate scan is stable.
14. **Notifications/jobs/documents:** notification/outbox and alert rows; device audit event `DEVICE_RECONCILED`.
15. **Reports/metrics:** holiday/generation counts, daily/period roster, analytics, alert counts, device status.
16. **Security/privacy:** form teacher sees assigned roster; parent sees linked child; device requests require signature/registered device.
17. **Screenshots:** `screenshots/route-presence.png` is committed; attendance error/denied/locked/mobile-specific states remain open.
18. **Tests/status:** Gate 8 PASS for exercised lifecycle, holiday regression, device/replay, analytics, alerts, and notifications.

## 10. Academic grades, results, report cards, PV, council, and batches

1. **Purpose/owner:** capture scoped grades, accept packets, calculate results, review conduct, publish snapshots, and generate documents; owner: academic administration.
2. **Actors/actions/scopes:** assigned teachers enter/review; Direction reviews/publishes; parent reads linked published results.
3. **Prerequisites:** sessions/products, curricula/assessments, teacher assignment, attendance/conduct, academic workflow windows.
4. **Navigation FR/EN:** `/academic` (Scolarité/Academics), report cards, PV, council, batch documents.
5. **Field dictionary:** packet; sequence/product; student; subject; score/max; coefficient; comment; conduct decision; snapshot/version/status; document locale.
6. **State/lifecycle:** `draft grades -> submitted -> accepted packet -> computed snapshot -> reviewed -> validated -> published -> document/PV/batch`.
7. **Normal workflow:** enter S1-S6, accept packets, compute annual, approve conduct, Direction review/validate/publish, generate PDF/PV/ZIP, verify publicly/parent.
8. **Alternate/edge workflows:** scheduled-window denial, incomplete dependency, correction/version, batch blocked row, retry failed rows, public verification, linked-parent scope.
9. **Data changes:** grades/packets/transitions, snapshots/versions, conduct, documents/batch jobs/items, publication audit.
10. **Upstream dependencies:** students/enrollments, curriculum/assessment, attendance, conduct, reporting windows.
11. **Downstream consumers:** parent portal, official documents, promotion, reports, public verification.
12. **Errors/correction:** workflow blockers name missing dependency/corrective screen; document authorization is narrow bootstrap/test fixture only.
13. **Audit/concurrency/idempotency:** packet acceptance/version and immutable snapshots; batch progress/row outcomes; repeated generation does not duplicate.
14. **Notifications/jobs/documents:** PDF/PV/ZIP/official document jobs, public verification, batch archive.
15. **Reports/metrics:** 24 S1-S6 packets accepted, annual snapshots averages 15/16/17, PV/PDF/ZIP/public verification evidence.
16. **Security/privacy:** teacher scope and Direction review are separate; parents only see linked published results; no ordinary-role document generation broadening.
17. **Screenshots:** `screenshots/route-academic.png` plus common states are committed; academic denied/locked/mobile states remain open.
18. **Tests/status:** Gate 9 PASS for exercised chain; batch/restart and exhaustive Gate 14 inventory remain distinct.

## 11. Finance catalogue, plans, charges, collections, documents, accounting, payroll, and reports

1. **Purpose/owner:** configure fees, generate charges, collect/reconcile payments, issue documents, run payroll, and report; owner: finance.
2. **Actors/actions/scopes:** accountant reporting/ledger, econome plan/payroll approval, cashier/collector collection, parent linked finance reads.
3. **Prerequisites:** fee types/plans, current enrollment, accounting period, payment channels, cashier session, payroll components.
4. **Navigation FR/EN:** `/finance` (Finance/Finance), fee types, plans, charges, collections, documents, payroll, accounting, reports.
5. **Field dictionary:** fee code/amount/scope; plan/effective dates; charge date/status; enrollment; channel; amount/reference/idempotency; invoice/receipt; period/journal; payroll component/payslip.
6. **State/lifecycle:** `catalog -> plan approved -> charge preview/generate -> cashier open -> quote -> post -> receipt/invoice -> ledger/reconcile -> close/payroll`.
7. **Normal workflow:** configure plan, preview/generate idempotently, open cashier, quote/post collection, issue receipt/invoice, close/reconcile, prepare/review/pay payroll.
8. **Alternate/edge workflows:** partial/full payment, overpayment, waiver, reversal/refund, legacy deny 403, document batch blocked row, self-vs-other payslip, future session lookup.
9. **Data changes:** fee plans/lines, charges/installments, payments/allocations, invoices/receipts/documents, journals/ledger, payroll runs/payslips.
10. **Upstream dependencies:** session/enrollment, fee catalogue, accounting periods, policy roles, employee payroll data.
11. **Downstream consumers:** parent finance, dashboards/reports/exports, accounting reconciliation, payroll documents.
12. **Errors/correction:** preview/quote use non-throwing accounting-period lookup; denied legacy actions are clean 403; effective-date/period blocker names corrective screen.
13. **Audit/concurrency/idempotency:** source-event/idempotency keys, balanced journals, cashier close, versioned plans, immutable documents, ownership checks.
14. **Notifications/jobs/documents:** invoice/receipt/PDF/batch jobs, payslips, reports/CSV exports, payment provider callbacks where configured.
15. **Reports/metrics:** billed/collected/waived/outstanding, debit=credit, reconciliation, payroll gross/net, seven report tabs and CSV exports.
16. **Security/privacy:** narrow accountant/collector/econome authorities; parent relationship scope; self-accountant payslip rule; no teacher/parent finance broadening.
17. **Screenshots:** finance route screenshots under `screenshots/route-finance*.png` plus common states are committed; finance denied/locked/mobile states remain open.
18. **Tests/status:** Gate 10 PASS for exercised lifecycle; preview/quote P1 closed; performance mutation finance path remains partial due disposable fixture preconditions.

## 12. Parent portal

1. **Purpose/owner:** provide relationship-filtered family access to child school life, academics, finance, and communication; owner: parent services.
2. **Actors/actions/scopes:** FAM-A/B/C/D/E/F parent/guardian roles, linked-child relationship and parent-safe health/message actions.
3. **Prerequisites:** guardian account, parent/student link, invitation/reset flow, published child data.
4. **Navigation FR/EN:** `/parent` (Portail parent/Parent portal), child selector, School life, academic, finance, resources.
5. **Field dictionary:** parent identity; child link; selected child; attendance/discipline/health/event/message; acknowledgement; invoice/receipt.
6. **State/lifecycle:** `invited/pending -> accepted/active -> select linked child -> read school life -> acknowledge message -> relationship ended`.
7. **Normal workflow:** sign in, select one of linked children, inspect safe surfaces, acknowledge message, replay acknowledgement, switch child, log out.
8. **Alternate/edge workflows:** unrelated child, ended relationship, missing share, invitation/reset token, duplicate ack replay, future-session account lookup.
9. **Data changes:** guardian/account/link, suggestions, message acknowledgements, parent-safe read models; ordinary reads do not mutate child data.
10. **Upstream dependencies:** students/enrollments, published academic/finance/attendance/events/messages, relationship policy.
11. **Downstream consumers:** family communications, dashboard/notifications, parent finance/results.
12. **Errors/correction:** unrelated child returns 403 policy code; replay is idempotent; reauthenticate/reselect child if context is missing.
13. **Audit/concurrency/idempotency:** acknowledgement replay 200 without duplication; relationship ending removes scope; parent suggestion has no delete endpoint and is labelled fixture data.
14. **Notifications/jobs/documents:** invitation/reset messages, parent suggestions, published report/finance documents.
15. **Reports/metrics:** FAM-A exactly three linked children; School life sections and linked finance/academic counts.
16. **Security/privacy:** relationship and child scope enforced after resource resolution; no directory or unrelated-child leakage.
17. **Screenshots:** `screenshots/route-parent.png` and `state-admin-parent-boundary.png` are committed; linked-parent denied/locked/mobile states remain open.
18. **Tests/status:** Gate 11 PASS for exercised linked scope; Gate 14 exhaustive/cross-tenant remains open.

## 13. Journey and promotions

1. **Purpose/owner:** preview, override, commit, and reconcile annual progression; owner: Direction/academic leadership.
2. **Actors/actions/scopes:** Direction reviews/overrides/commits; teachers supply results; parent sees resulting linked enrollment where allowed.
3. **Prerequisites:** accepted packets, published annual snapshot, progression rules/graph, source/target sessions/classes.
4. **Navigation FR/EN:** `/journey` (Parcours/Journey), `/journey/promotions` (Promotions/Promotions).
5. **Field dictionary:** batch/session/class; candidate; automated decision; manual decision/reason; target class/session; commit/replay status; register hash.
6. **State/lifecycle:** `draft batch -> preview -> override -> commit -> committed/replay-blocked -> activated target enrollment`.
7. **Normal workflow:** preview candidates, review automated decisions, record manual repeat override, commit, replay same commit, reconcile register and enrollments.
8. **Alternate/edge workflows:** stale override 409, target enrollment exists, batch not draft, missing snapshot, incomplete configuration, guessed route 404 versus authoritative preview route.
9. **Data changes:** promotion batch/decision/history/register, source completion, target enrollment, Journey transition event.
10. **Upstream dependencies:** academic snapshots, progression graph/rules, sessions/classes/enrollments.
11. **Downstream consumers:** next-session rosters, parent context, reports/audit.
12. **Errors/correction:** use `/journey/progression/batches/{id}/commit/preview`; stale/replay codes identify refresh or already-committed state.
13. **Audit/concurrency/idempotency:** register SHA-256, one transition per decision, identical commit replay is blocked/committed without duplicate target.
14. **Notifications/jobs/documents:** promotion register and journey events; no hidden target enrollment mutation on preview.
15. **Reports/metrics:** three-candidate preview, one manual REPEAT, commit/replay, three source-target reconciliations.
16. **Security/privacy:** Direction scope and batch state guard; ordinary roles cannot commit.
17. **Screenshots:** `screenshots/route-journey.png` and `screenshots/route-journey-promotions.png` are committed; Journey denied/locked/mobile states remain open.
18. **Tests/status:** Gate 13 PASS for exercised promotion chain; Gate 14 golden journey inventory remains open.

## 14. Discipline

1. **Purpose/owner:** maintain discipline catalogue and record/review incidents; owner: school administration.
2. **Actors/actions/scopes:** Direction/admin manage catalogue/incidents; teachers/parents receive permitted reads.
3. **Prerequisites:** students/enrollments, discipline catalogue, role policy.
4. **Navigation FR/EN:** `/discipline` (Discipline/Discipline) and parent School life discipline section.
5. **Field dictionary:** code/label/category; student; date; incident; severity/status; action/comment; review actor/time.
6. **State/lifecycle:** `catalog -> incident draft -> recorded -> reviewed/resolved -> parent-safe read where allowed`.
7. **Normal workflow:** select student, record incident, validate/review, reload, verify permitted parent-safe display.
8. **Alternate/edge workflows:** invalid student, missing category, unauthorized teacher/parent read, ended relationship, duplicate/retry.
9. **Data changes:** discipline catalog and incident/audit rows.
10. **Upstream dependencies:** student identity and policy.
11. **Downstream consumers:** report-card conduct and parent School life.
12. **Errors/correction:** field validation and policy denial are explicit; select the correct student/class context.
13. **Audit/concurrency/idempotency:** actor/time/status are retained; duplicate submission is not silently repeated.
14. **Notifications/jobs/documents:** conduct feeds report-card snapshot; no notification claim unless evidence records one.
15. **Reports/metrics:** discipline catalogue count and linked-child read-back.
16. **Security/privacy:** incidents are confidential and parent-safe filtering applies.
17. **Screenshots:** `screenshots/route-discipline.png` plus common states are committed; Discipline denied/locked/mobile states remain open.
18. **Tests/status:** Gate 2/11/12 exercised PASS; screenshot/complete persona inventory open.

## 15. Coursebook / ClassKit

1. **Purpose/owner:** let a scoped teacher publish class learning content and let permitted users read it; owner: academic operations.
2. **Actors/actions/scopes:** primary/teacher class scope for create/delete; parent-safe/management reads as configured.
3. **Prerequisites:** class/subject, teacher assignment, current session, teacher policy.
4. **Navigation FR/EN:** `/coursebook` and `/classkit` (Cahier de textes/ClassKit).
5. **Field dictionary:** className; subjectCode; entryDate; content; homework; dueDate; publication/status.
6. **State/lifecycle:** `draft entry -> saved/published -> visible to scope -> deleted/retained according to API`.
7. **Normal workflow:** select class/subject, enter content/homework/date, save, reload, read as allowed persona.
8. **Alternate/edge workflows:** missing subject, unassigned class, wrong teacher, parent unrelated child, duplicate/replay, cleanup delete.
9. **Data changes:** coursebook entry/class resource rows and audit.
10. **Upstream dependencies:** class curriculum and teacher assignment.
11. **Downstream consumers:** parent School life/ClassKit, daily operations/dashboard.
12. **Errors/correction:** resource scope mismatch 403; select assigned class/subject and retry.
13. **Audit/concurrency/idempotency:** write/read/delete HTTP results are explicit; no duplicate on replay claim beyond tested endpoint.
14. **Notifications/jobs/documents:** class resource publication may feed parent view; no Mailpit claim without evidence.
15. **Reports/metrics:** entry count and visible class/subject rows.
16. **Security/privacy:** teacher cannot write outside assigned class; parent sees only linked safe resource.
17. **Screenshots:** `screenshots/route-coursebook.png` plus common states are committed; Coursebook denied/locked/mobile states remain open.
18. **Tests/status:** Gate 12 and latest Gate 14 teacher write `201/204` PASS for exercised entry.

## 16. Health (parent-safe scope only)

1. **Purpose/owner:** record health information and expose only permitted parent-safe visits; owner: school health administration.
2. **Actors/actions/scopes:** health-authorized staff/admin; parent linked-child safe read; nurse-specific persona/route is excluded from this run.
3. **Prerequisites:** student/enrollment, health policy, parent relationship/share.
4. **Navigation FR/EN:** `/health` (Santé/Health) and parent School life health section.
5. **Field dictionary:** student; visit date; category/type; safe summary; confidential details; actor/status.
6. **State/lifecycle:** `visit draft -> recorded -> parent-safe projection -> read/audit`; confidential source remains protected.
7. **Normal workflow:** authorized staff records/read a visit; parent selects linked child and sees only safe projection.
8. **Alternate/edge workflows:** teacher without health authority, unrelated child, ended relationship, missing share, invalid student.
9. **Data changes:** health record/visit and audit rows; parent read is non-mutating.
10. **Upstream dependencies:** student identity, relationship, health policy.
11. **Downstream consumers:** parent School life and permitted reports.
12. **Errors/correction:** 403 policy denial is expected for non-health staff; use authorized health workflow, not UI bypass.
13. **Audit/concurrency/idempotency:** health access is audited and relationship filtered; no nurse route fix is included.
14. **Notifications/jobs/documents:** no notification/document claim beyond recorded evidence.
15. **Reports/metrics:** parent-safe visit count/read-back for linked Amina.
16. **Security/privacy:** confidential health details are not exposed to teachers/parents without exact scope.
17. **Screenshots:** `screenshots/route-health-parent-safe.png` plus common states are committed; health denied/locked/mobile states remain open and nurse evidence remains excluded.
18. **Tests/status:** Gate 11/12 parent-safe health PASS; nurse P1 is deferred/out of scope and not a release acceptance change.

## 17. Documents and orientation

1. **Purpose/owner:** generate/serve official documents and record orientation decisions; owner: academic administration/documents.
2. **Actors/actions/scopes:** bootstrap fixture authority/Direction as explicitly configured; parent/public verification reads where allowed.
3. **Prerequisites:** published snapshot/template/branding, document sequence, orientation context.
4. **Navigation FR/EN:** `/documents` (Documents/Documents), report-card document/PV/public verification surfaces.
5. **Field dictionary:** template/locale; branding version; aggregate ID; document number/status; hash; verification token; orientation decision/reason.
6. **State/lifecycle:** `template -> generate -> issued -> validate/publish -> public/parent verify -> correction/version`.
7. **Normal workflow:** choose published result, generate document, download PDF/PV/ZIP, verify public hash/status, read parent document.
8. **Alternate/edge workflows:** denied Direction generation, missing recipient, blocked batch row, correction, invalid verification, wrong locale.
9. **Data changes:** generated document, sequence, branding, batch job/item, orientation decision and audit.
10. **Upstream dependencies:** academic snapshots, finance aggregate, templates/branding.
11. **Downstream consumers:** parent portal, reports, promotion where orientation feeds it.
12. **Errors/correction:** missing dependency gives corrective path; document generation authority is narrow and not ordinary-role access.
13. **Audit/concurrency/idempotency:** document number/hash/version and batch row outcomes are durable; repeat does not duplicate issued aggregate.
14. **Notifications/jobs/documents:** PDF, PV, ZIP, official document, verification endpoints/jobs.
15. **Reports/metrics:** document IDs/numbers, PDF byte/signature checks, batch issued/blocked counts.
16. **Security/privacy:** public verification exposes only intended document facts; raw PII/secrets excluded.
17. **Screenshots:** `screenshots/route-documents.png` plus common states are committed; document denied/locked/mobile states remain open.
18. **Tests/status:** Gate 9 document/PV/public verification PASS; exhaustive UI screenshot coverage open.

## 18. Events

1. **Purpose/owner:** publish school events visible to permitted families/operations; owner: school operations.
2. **Actors/actions/scopes:** admin/Direction create/manage; parent linked-family read.
3. **Prerequisites:** school/session, audience policy, date/calendar.
4. **Navigation FR/EN:** `/events` (Événements/Events), parent School life events.
5. **Field dictionary:** title; description; date/time; audience/class; location; status; visibility.
6. **State/lifecycle:** `draft -> published -> visible to audience -> archived`.
7. **Normal workflow:** create event, validate audience/date, publish, reload management and parent-safe read.
8. **Alternate/edge workflows:** invalid date, unrelated child/audience, cancel, duplicate/replay, archived event.
9. **Data changes:** school event rows and audit.
10. **Upstream dependencies:** calendar, classes, parent relationships.
11. **Downstream consumers:** parent School life, dashboard/operations.
12. **Errors/correction:** validation or 403 scope denial; correct audience/date and retry through API/UI.
13. **Audit/concurrency/idempotency:** publish state and actor are retained; replay behavior is endpoint-specific and must be checked by evidence.
14. **Notifications/jobs/documents:** notification only when configured/evidenced; event is not a document.
15. **Reports/metrics:** event count/date/audience read-back.
16. **Security/privacy:** event audience filtering prevents unrelated-family visibility.
17. **Screenshots:** `screenshots/route-events.png` plus common states are committed; Events denied/locked/mobile states remain open.
18. **Tests/status:** Gate 11/12 event API/UI read-back PASS for exercised linked-family scope.

## 19. Correspondence and messages

1. **Purpose/owner:** send/read school correspondence and support parent acknowledgement; owner: school communications.
2. **Actors/actions/scopes:** Direction/admin send; parent linked-child read/ack; unrelated-child ack denied.
3. **Prerequisites:** recipient/relationship, message category, published audience.
4. **Navigation FR/EN:** `/messages` (Messages/Messages), parent School life correspondence.
5. **Field dictionary:** category; subject/message; recipient/audience; createdAt; read/ack status; idempotency/replay.
6. **State/lifecycle:** `draft -> sent -> delivered/read -> acknowledged -> replay-safe`.
7. **Normal workflow:** create/send message, parent reads, acknowledges, repeats same ack, verify audit/read-back.
8. **Alternate/edge workflows:** unrelated child, ended relationship, missing period/context, duplicate ack, invalid recipient.
9. **Data changes:** correspondence and parent acknowledgement/event rows.
10. **Upstream dependencies:** parent relationship, user/account, communication policy.
11. **Downstream consumers:** parent portal, notifications, audit.
12. **Errors/correction:** unrelated ack 403 `POLICY_RULE_MISSING`; use linked child and refresh context.
13. **Audit/concurrency/idempotency:** linked ack and exact replay 200 without duplicate; scope decision after child resolution.
14. **Notifications/jobs/documents:** notification/outbox where configured; no raw mailbox bodies in report.
15. **Reports/metrics:** message counts, ack status, replay result.
16. **Security/privacy:** recipient and child relationship filtering; no unrelated family data.
17. **Screenshots:** `screenshots/route-messages.png` plus common states are committed; Messages denied/locked/mobile states remain open.
18. **Tests/status:** Gate 11/12 and V142 ack regression PASS for exercised scope.

## 20. Supplies and books / ClassKit resources

1. **Purpose/owner:** manage school resource/book items and scoped class resource publication; owner: operations/academic administration.
2. **Actors/actions/scopes:** operations/admin manage inventory/publication; teacher/class and parent/linked-child read according to policy.
3. **Prerequisites:** item/catalog, class/resource, user role, school context.
4. **Navigation FR/EN:** `/classkit` (Ressources/ClassKit); inventory/resource tabs where exposed.
5. **Field dictionary:** item/book code/title; quantity/status; class; publication; borrower/recipient; due date; resource metadata.
6. **State/lifecycle:** `catalog -> stock/resource created -> assigned/published -> read/returned/archived`.
7. **Normal workflow:** create/read a resource, assign/publish to class, verify teacher/parent permitted read.
8. **Alternate/edge workflows:** unavailable item, wrong class, duplicate assignment, unauthorized persona, return/cancel.
9. **Data changes:** resource/item/publication and audit rows where the feature is enabled.
10. **Upstream dependencies:** school/classes/subjects and access policy.
11. **Downstream consumers:** ClassKit, parent School life, daily operations.
12. **Errors/correction:** scope/availability error identifies class/item; select valid resource or request authorized action.
13. **Audit/concurrency/idempotency:** publication/assignment state should be read back; no unverified duplicate claim.
14. **Notifications/jobs/documents:** resource publication may notify; no document/notification claim without evidence.
15. **Reports/metrics:** resource/item counts and publication status when exposed.
16. **Security/privacy:** class/resource scope prevents cross-class publication/read.
17. **Screenshots:** `screenshots/route-classkit.png` plus common states are committed; Supplies/books denied/locked/mobile states remain open.
18. **Tests/status:** Gate 12 ClassKit/resource lifecycle exercised; detailed inventory screenshot/API expansion remains open.

## 21. Dashboard, alerts, and reports

1. **Purpose/owner:** compose management summaries, alerts, and operational reports; owner: Direction/operations.
2. **Actors/actions/scopes:** Direction/admin dashboard; accountant finance reports; parent/teacher scoped dashboards where exposed.
3. **Prerequisites:** students, attendance, finance, alerts, permissions, current session.
4. **Navigation FR/EN:** `/dashboard` (Tableau de bord/Dashboard), `/alerts`, `/reports`.
5. **Field dictionary:** student count; language/subsystem; attendance percent/late/absent; finance summary; distribution; alert severity/status; report filters/export.
6. **State/lifecycle:** `load -> loading -> source API aggregation -> populated/empty/error -> refresh`; report `filter -> query -> export`.
7. **Normal workflow:** open dashboard, verify source requests, inspect cards/alerts, open reports, export where authorized.
8. **Alternate/edge workflows:** empty state, source API 404 guess versus composed route, unauthorized report, stale refresh, export error.
9. **Data changes:** dashboard reads only; alerts may be created/updated by attendance thresholds; reports are read/export jobs.
10. **Upstream dependencies:** students, attendance, finance, alerts, capabilities.
11. **Downstream consumers:** Direction decisions, operations monitoring, parent-safe summaries.
12. **Errors/correction:** use actual composed `/dashboard` route; do not treat guessed `/api/dashboard` 404 as UI failure; report filter errors identify correction.
13. **Audit/concurrency/idempotency:** alert scan deduplicates; dashboard read is non-mutating; exports are fresh-authenticated.
14. **Notifications/jobs/documents:** alerts, CSV/PDF reports where exposed.
15. **Reports/metrics:** 28 students, 15 FR/13 EN, 100% presence, level distribution 12/12/4, finance/attendance source 200.
16. **Security/privacy:** source APIs and report tabs use persona scope; no cross-tenant summary is claimed.
17. **Screenshots:** `screenshots/route-dashboard.png`, `route-alerts.png`, and `route-reports.png` plus common states are committed; dashboard denied/locked/mobile states remain open.
18. **Tests/status:** Gate 12 and V143 dashboard PASS; full Gate 14 persona/network inventory remains open.

## 22. Operations, migrations, backup, monitoring, and troubleshooting

1. **Purpose/owner:** run the isolated stack, verify release/migration safety, monitor health, and diagnose failures; owner: release/DB/QA.
2. **Actors/actions/scopes:** release operator/QA; no application persona can repair Flyway or mutate production backup state.
3. **Prerequisites:** candidate branch, Compose project/volume, Docker, fresh or approved backup, Mailpit, evidence ledger.
4. **Navigation FR/EN:** operationally `/actuator/health`, Compose/Docker, Flyway history, evidence/ledger files; no business route.
5. **Field dictionary:** branch/SHA; image digest; port; volume; health body; Flyway rank/version/checksum; backup lineage; test command; correlation ID.
6. **State/lifecycle:** `inspect -> build/package -> fresh migrate -> health -> exercise -> collect evidence -> cleanup disposable resources`; upgrade `restore -> validate -> forward migrate -> verify/rollback` only with approved fixture.
7. **Normal workflow:** preserve dirty primary, use candidate worktree, build jar/image, start private Compose, verify health/Flyway, run gates, record exact evidence, cleanup only named disposable resources.
8. **Alternate/edge workflows:** stale 8096 stack, Testcontainers startup, Docker cache/context, Mailpit DNS fixture noise, checksum mismatch, bounded benchmark timeout, shared browser storage.
9. **Data changes:** candidate migrations via Flyway only; disposable performance DB restored from `_bbc-perf.dump`; no manual acceptance/prod mutation.
10. **Upstream dependencies:** source branch/build context, Docker, approved backup strategy, database lineage.
11. **Downstream consumers:** every gate, report, defect register, release decision.
12. **Errors/correction:** V77 applied `-2113849035` versus resolved `-95452447` is a real lineage blocker; do not repair/checksum-edit; benchmark group timeout is recorded as partial; Mailpit DNS warnings are fixture noise.
13. **Audit/concurrency/idempotency:** Flyway validates/applies 111 migrations through V144; performance stack used separate DB/network and was removed; acceptance 8100/8101 untouched.
14. **Notifications/jobs/documents:** Docker logs, Maven/Surefire, Flyway history, health, benchmark report, and gate ledger are produced.
15. **Reports/metrics:** final Maven 177/177, frontend 23 test files / 51 tests, health 8101 200, Flyway `111|144|true`, git diff check 0, performance read/preview percentiles and bounded mutation results.
16. **Security/privacy:** no secrets in evidence; no unsafe restore/repair/manual SQL; nurse out-of-scope preserved.
17. **Screenshots:** `screenshots/route-apps.png`, `route-settings.png`, and runtime evidence are committed; operational denied/locked/mobile states remain open.
18. **Tests/status:** fresh install/build/Maven/health/Flyway PASS; production-like upgrade BLOCKED by exact checksum lineage; performance partial; Gate 14 IN PROGRESS.

## Reference disposition

This reference is intentionally explicit about remaining gaps. The candidate
has no open P0/P1 after V144 and the final backend suite is green, but the
plan's full definition of done is not claimed while screenshot states,
exhaustive Gate 14 cross-tenant/persona coverage, and an approved
candidate-compatible production backup/lineage strategy remain outstanding.

## State and responsive evidence addendum - 2026-08-15T18:24:48+01:00

The reference pack now indexes a bounded nine-persona route/read/redirect
slice and responsive screenshots for Primary attendance/grade entry, Cashier
collection, and Parent portal at `390x844`. These representative routes had
no horizontal overflow and no captured console warnings/errors. The full
module-by-module six-state, mobile, loading/error, and exhaustive persona
matrix remains explicitly `IN PROGRESS`; see
`qa/e2e-runs/2026-08-14-full-school/final/module-state-capture-matrix.md` and
`qa/e2e-runs/2026-08-14-full-school/final/ui-persona-slice-20260815.md`.

## Permission-policy and finance/parent UI addendum - 2026-08-15

The component-level access-control regression now passes `3/3` and the complete
frontend suite passes `23` files / `51` tests. The fresh finance/parent UI slice
records Bursar positive Finance Plans, Cashier positive Collections with locked
plan controls, and FAM-A linked-child success with direct staff-route denial;
all checked routes had zero diagnostics. These are measured additions, not a
claim that the exhaustive Gate 14 state/mobile/network matrix is complete.
