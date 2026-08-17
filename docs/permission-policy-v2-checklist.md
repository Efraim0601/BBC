# Permission Policy V2 implementation checklist

Governing specification: [PERMISSION_POLICY_V2_IMPLEMENTATION_PLAN.md](../PERMISSION_POLICY_V2_IMPLEMENTATION_PLAN.md)

This checklist is kept with the implementation branch. `[x]` means implemented and verified in source/tests; `[~]` records an external verification limitation with concrete evidence.

## Plan sections

- [x] §1 Purpose and governance: the approved plan is copied into the repository and remains the governing specification.
- [x] §2 Findings and freeze: endpoint/action inventory is generated as `docs/ppv2-inventory.json` and `docs/ppv2-inventory-latest.json`.
  - Evidence: 533 endpoints, 506 explicit-action guards, 0 module-only guards, 0 unprotected endpoints.
- [x] §3 Policy model: feature visibility, exact action, resource scope, invariant checks, deny-wins precedence, user override precedence, dated rules, and fail-closed parcours are implemented in `AuthorizationPolicyService` and the scope resolvers.
- [x] §4 Database: Flyway-only V118–V123 policy migrations add the action catalogue, localized metadata, role/user rules, templates and durable template rules, rollout/compatibility state, audit/versioning, invalidation triggers, guardian actions, finance expense actions, setup/settings actions, and self-payslip access.
- [x] §5 Action catalogue: student/family, academic, attendance, timetable, settings, finance, parent, guardian-directory, expense, and payroll actions are represented in the stable Java catalogue and localized SQL catalogue. `PermissionActionControllerContractTest` rejects unknown controller actions.
- [x] §6 Backend:
  - [x] Central action + scope + domain-invariant evaluator, actual employee ownership, all active roles, parent multi-role identity, safe-mode fail-closed behavior, scope compatibility, and policy versioning.
  - [x] Academic student/roster/grade/report-card/assessment/packet/bulletin enforcement, including titulaire any-subject edit delegation and historical-session dates.
  - [x] Student/enrollment/guardian query filtering before DTO construction, minimized teacher/finance DTOs, exact mutation actions, and cross-class transfer protection.
  - [x] Attendance daily titulaire and exact secondary published occurrence resolution; query-before-DTO board, analytics, classes, legacy-path restrictions, and exact operation actions.
  - [x] Timetable own-schedule default, exact occurrence/substitution binding, export guards, separate substitution view/manage actions, and explicit management override path.
  - [x] Finance payer projection, school/enrollment binding, precise expense/document/report actions, and request/approve/reverse/close separation-of-duty checks.
  - [x] Settings/setup/role/permission/school/session/curriculum/assignment/calendar/discipline/mail guards and service-level central policy checks.
  - [x] Parent linked-child + effective guardian feature checks, any-child evaluation, document-category checks, and no staff endpoint access for parent-only accounts.
- [x] §7 Frontend: staged Access Control workspace with role/user selection, grouped localized actions, inherit/allow/deny, configured scopes, dated/permanent exceptions, reason/high-risk confirmation, optimistic versioning, effective preview, additions/removals, affected users, preserved exceptions, audit view, templates, Settings navigation, and Staff access drawer. Guards fail closed while capabilities load and contextual capabilities are not treated as global denial.
- [x] §8 API: `@policy` facade is present for context-free SCHOOL/NONE checks; contextual actions use grant-potential/capability descriptors and resource APIs; mutation DTO validation and self-lockout/last-active administrator protections are enforced.
- [x] §9 Rollout: fresh-school bootstrap creates SAFE_DEFAULT enforcement and least-privilege rules; existing schools retain visible compatibility/legacy authority; all schema changes are Flyway migrations.
- [x] §10 Tests:
  - [x] Unit precedence/scope/expiry/safe-mode/attendance/academic/guardian tests.
  - [x] Repository/service/controller negative tests for personas and cross-class, cross-subject, wrong-period, wrong-occurrence, primary-period, secondary-daily, substitute, finance, parent-feature, and unassigned-teacher cases.
  - [x] Fresh-container and production-like migration coverage.
  - [x] Frontend source and CI tests; inventory generator UTF-8 test.
  - [x] Full backend result: 39 Surefire classes, 120 tests, 0 failures, 0 errors, 0 skipped; Maven exit code 0.
- [x] §11 Acceptance evidence: backend compile/focused suite, full suite, frontend build/tests, compose validation, live authenticated Access Control preview, health endpoint, and migration version 123 were verified.
- [x] §12 Implementation order: inventory → migrations/catalogue → central engine → student/academic → attendance/timetable → finance/settings/parent → staged UX → hardening and verification.

## Verification notes and bounded limitations

- [x] Docker Compose configuration validates with `docker compose -f docker-compose.yml -f docker-compose.acceptance.yml config`.
- [~] A backend image build was bounded at 60 seconds and stopped with exit code 1 while silent at `RUN mvn -q dependency:go-offline`; local Maven compile and tests pass, so this is a Docker dependency-fetch/network limitation rather than an application failure.
- [~] The reused local database was intentionally not repaired or manually edited: startup correctly rejected its pre-existing V77 checksum mismatch. A new isolated PostgreSQL 16 acceptance database migrated cleanly through V123, the backend health endpoint returned HTTP 200, the authenticated browser workspace loaded without post-login console errors, and the isolated verification container was removed afterward.
