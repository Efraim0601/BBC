# BAY-8 implementation report

## Delivery

- Branch: `feature/BAY-8-student-parent-registration-family-linking`
- Base: the completed BAY-7 branch at `db454ba`
- Runtime: Docker deployment on `http://localhost:8082`, API on `http://localhost:8083`
- Existing school data and Docker volumes were preserved.

## What changed

### BAY-21 — Integrated student and parent registration

- Added a five-step registration wizard: student identity, current class, family, portal access, and review.
- One backend transaction creates the student, current-session enrollment, guardians, family relationships, and requested portal accounts/invitations.
- Supported access modes are immediate account creation, secure email invitation, and no portal account.
- Validation failures roll the whole registration back; responses never expose password hashes or plaintext credentials.

### BAY-22 — Existing-parent search and sibling linking

- Added authorized guardian search by normalized email, normalized phone, or name.
- Search results expose masked contact information, account state, and linked-child count, without exposing other children's identities.
- Registration and the student profile can select an existing guardian, creating another relationship instead of another account.
- Duplicate links are rejected, ambiguous email identities require explicit selection, and an admin-only merge operation safely reassigns links with an audit event.

### BAY-23 — Multiple guardians and relationship permissions

- Added first-class `guardian` and `student_guardian` records with relationship type, legal guardian, lives-with, emergency order, pickup, financial responsibility, portal access, notification permissions, effective dates, notes, and optimistic versioning.
- Migrated existing parent-account links and legacy father/mother/guardian contact blocks without discarding the compatibility `parent_student` mapping.
- Added a Family section on the dedicated student page for adding/searching adults, editing permissions, resending invitations, and ending a relationship.
- Ending a relationship uses an application modal with a consequence message and mandatory reason. Cancel performs no action; no native browser prompt is used.
- Parent academic and finance reads now pass through centralized active-relationship and per-feature permission checks.

### BAY-24 — Parent account security and lifecycle

- Added normalized/verified email, last login, failed attempts, lockout, forced-password-change, and credential-version state to parent identities.
- Invitations and password resets use random, expiring, single-use tokens; only SHA-256 token hashes are stored.
- Added public invitation acceptance and reset screens at `/parent-invite` and `/parent-reset`.
- Forgot-password returns a generic response to prevent account discovery.
- Five failed logins trigger a 15-minute lock; a successful login clears the counter.
- Invitation resend has a one-minute cooldown. Ending the last active child relationship deactivates the orphan account; accounts with another active child stay enabled.

### BAY-25 — Family-aware, retry-safe import

- Added persisted import jobs and rows with external row keys, dry-run diagnostics, explicit commit, per-row outcomes, and retry protection.
- Import commit reuses the same transactional student-registration service as the manual wizard.
- Added CSV and Excel file selection, downloadable template, editable parsed preview, dry-run summary, explicit commit, and downloadable result report.
- Exported error/report cells beginning with spreadsheet formula characters are neutralized.
- Preview does not create students. A repeated commit cannot duplicate already committed rows.

### Requested student-navigation correction

- Clicking a student now routes to `/students/:id`.
- The profile, enrollment history, documents, edit action, and family controls live on that dedicated page.
- The student list no longer opens a detail block at the bottom of the list.

## Database changes

- `V39__family_identity_and_registration.sql` creates guardian identity, relationship, invitation/reset token, and import-job structures and extends parent authentication state.
- `V40__backfill_legacy_guardian_contacts.sql` converts legacy father, mother, and guardian contact blocks into first-class family relationships.
- Both migrations are forward-only and Flyway reports schema version 40.

## Verification performed

- Backend production image compiled successfully with Java 21.
- Full backend integration suite passed against a fresh PostgreSQL 16 Testcontainer, including all 40 Flyway migrations.
- Family integration coverage verifies transaction rollback, current-session enrollment, sibling/account reuse, masked search, dry-run non-mutation, retry safety, and token hashing.
- Angular production build passed.
- Angular CI tests passed, including registration, guardian-search, and dry-run/commit API contract tests.
- Docker frontend, backend, and PostgreSQL containers are running; the backend health endpoint reports `UP`.
- Live browser acceptance verified:
  - list-row click opens a dedicated student route;
  - migrated family data appears on the profile;
  - relationship termination opens the custom modal;
  - confirmation is disabled without a reason;
  - Cancel closes the modal and preserves the relationship;
  - registration advances through student, class, and family steps;
  - searching `NGONO` displays the existing guardian and selecting it populates the family entry;
  - no test student was submitted and no real family relationship was ended.

## Screens to test

Use `admin / admin` at `http://localhost:8082`.

1. **Élèves** — `/students`
   - Click any row and confirm the browser navigates to `/students/{id}` instead of expanding the list.
2. **Nouvel élève** — `/students/new`
   - Complete the five steps; in Family, search an existing parent by name/email/phone, or enter a new adult.
   - In Access, choose Invitation, Create now, or No portal.
3. **Student details** — `/students/{id}`
   - Review Profile, Enrollment history, Documents, and Family.
   - Use Add family member to search/link or create a guardian.
   - Edit permission checkboxes and test Resend invitation where an email/account exists.
   - Open End relationship and test Cancel; only confirm against disposable data.
4. **Family import** — `/students/import-family`
   - Download the template or select a CSV/XLS/XLSX file, choose the class, preview, inspect row errors, download the report, then commit disposable rows.
5. **Parent invitation** — `/parent-invite?token=...`
   - Open from an invitation email and choose a compliant password.
6. **Parent password reset** — `/parent-reset?token=...`
   - Open from a reset email and choose a compliant password.

## Operational notes

- Email delivery uses the configured mail adapter. In environments without SMTP, token creation and audit still occur but delivery is logged as unavailable.
- Portal accounts created through an invitation remain pending until the one-time link is accepted.
- For manual destructive testing, use a disposable guardian/student relationship because confirmation intentionally takes effect immediately.
