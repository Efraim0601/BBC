# Production-simulation database: 2026–2027 configuration and end-to-end runbook

## 1. Purpose and outcome

This document is the source-of-truth runbook for the **new, isolated production-simulation database** created for the school-management application.

It is intentionally different from the older deployments previously used during feature development:

- Frontend: `http://localhost:8110`
- Backend: `http://localhost:8111`
- PostgreSQL host port: `5542`
- Mailpit: `http://localhost:8135`
- Docker Compose project: `bbc-prod-simulation`
- Database volume: `bbc-prod-2026-db`
- Document volume: `bbc-prod-2026-documents`
- Database: `bbc_sms`
- Database user: `bbc`
- Bootstrap administrator: `admin / admin` (simulation only)

The database was created from an empty application database and configured with real school data supplied in `classe update.zip`. It is not a copy of the old `8082`, `8085`, or `8100` application databases.

The current database contains the following high-level data:

| Area | Current count | Meaning |
|---|---:|---|
| Students | 745 | 740 source rows plus five deliberate demonstration records |
| Classes | 43 | Maternelle, primary, and secondary French/English classes |
| Subjects | 42 | French and English subject catalogue |
| Class-subject assignments | 499 | Curriculum rows with class-specific coefficients and rules |
| Employees | 11 | Teachers, form teachers, accountant, and direction |
| Guardians | 607 | Imported guardians plus portal-enabled demonstration families |
| Enrollments | 745 | One current-session enrollment per imported/demo student |
| Assessment templates | 2,994 | One default assessment per assigned subject for S1–S6 |
| Timetable slots | 100 | Four representative classes, 25 slots each |

The latest Git branch was already pushed before this document was prepared. `git push` returned `Everything up-to-date`; no unrelated generated artifacts were staged.

## 2. Starting and identifying the correct environment

### 2.1 Start the isolated stack

Run the following from PowerShell. The compose files are deliberately explicit so that the database cannot be confused with an older local deployment.

```powershell
docker compose -p bbc-prod-simulation `
  -f "C:\Users\joe tech\.codex\worktrees\full-school-e2e\docker-compose.yml" `
  -f "C:\Users\joe tech\.codex\worktrees\full-school-e2e\docker-compose.full-e2e.yml" `
  -f "C:\Users\joe tech\bbcomplex\tmp\docker-compose.prod-simulation.yml" `
  up -d --build
```

The checked-in override is:

`C:\Users\joe tech\bbcomplex\tmp\docker-compose.prod-simulation.yml`

Verify the stack before using the UI:

```powershell
docker compose -p bbc-prod-simulation `
  -f "C:\Users\joe tech\.codex\worktrees\full-school-e2e\docker-compose.yml" `
  -f "C:\Users\joe tech\.codex\worktrees\full-school-e2e\docker-compose.full-e2e.yml" `
  -f "C:\Users\joe tech\bbcomplex\tmp\docker-compose.prod-simulation.yml" `
  ps
```

Expected services:

- `bbc-prod-simulation-backend-1`: healthy/up, published on `8111`
- `bbc-prod-simulation-db-1`: healthy/up, published on `5542`
- `bbc-prod-simulation-frontend-1`: up, published on `8110`
- `bbc-prod-simulation-mailpit-1`: healthy/up, published on `8135`

Then open `http://localhost:8110`, choose the correct parcours when the access-control screen asks for it, and sign in with the simulation administrator account.

### 2.2 Confirm the database boundary

```powershell
docker exec bbc-prod-simulation-db-1 psql -U bbc -d bbc_sms -c "select current_database();"
```

The result must be `bbc_sms`. Do not use a generic `docker compose up` command without the project name and override file; that can reconnect the browser to a different deployment.

## 3. Required configuration order

The configuration order matters because later modules depend on identifiers created earlier.

```text
School profile
    ↓
Academic session and reporting periods
    ↓
Parcours/sections and classes
    ↓
Subject catalogue
    ↓
Class-subject curriculum and coefficients
    ↓
Employees, roles, class links, and subject responsibility
    ↓
Students, guardians, and enrollments
    ↓
Assessment templates
    ↓
Finance catalogue, plans, installments, accounting periods
    ↓
Timetable periods, master timetable, and publication
    ↓
Attendance policies and daily/period roll calls
    ↓
Grades, trimester calculations, bulletins, payments, and permissions
```

Do not create grades before the student has an enrollment, do not generate charges before class fee plans exist, and do not take secondary attendance before at least one timetable period is published for the selected class/date.

## 4. School profile configuration

### UI flow

1. Sign in as `admin`.
2. Open **Settings**.
3. Select the **General** tab.
4. Enter the institution identity and contact information.
5. Save.
6. Refresh the page and verify that the values persist.
7. Open a generated receipt or bulletin later; the same school identity should be used in the document header.

### Values configured in the new database

| Field | Value |
|---|---|
| School name | École publique de Biyem-Assi |
| Motto | Paix-Travail-Patrie |
| City | Yaoundé |
| Country | Cameroun |
| Address | Biyem-Assi, Yaoundé |
| Phone | +237 698765432 |
| Email | contact@ecole-biyem-assi.cm |
| Currency | FCFA/XAF |
| Authority | Ministère de l'éducation de base |
| Opening time | 07:30 |
| Closing time | 17:00 |
| Locale | French (`fr`) |
| Timezone | Africa/Douala |

### Acceptance criteria

- Accented characters such as `É`, `é`, and `è` render correctly after a refresh.
- The value is stored as UTF-8, not as `??` or a replacement character.
- Receipt, bulletin, and timetable documents use the configured name and contact details.

## 5. Academic session and reporting structure

### 5.1 Create the current session

1. Go to **Settings → Sessions & terms**.
2. Click **New session**.
3. Enter code `2026-2027` and label `Année scolaire 2026-2027`.
4. Set the dates to **2026-09-01 through 2027-07-31**.
5. Set the timezone to `Africa/Douala`.
6. Mark it as the current session.
7. Save and confirm that its status is **Open**.
8. Confirm that `2025-2026` remains historical and is not current.

Current session identifier:

`2c69a619-6215-4e65-af23-b6520f90b54f`

### 5.2 Apply the reporting-period template

The intended academic model is six sequences grouped into three trimester-result periods:

| Milestone | Inputs | Result |
|---|---|---|
| Sequence 1 | First sequence assessments | S1 result |
| Sequence 2 | Second sequence assessments | S2 result |
| Trimester 1 | S1 + S2 | T1_RESULT |
| Sequence 3 | Third sequence assessments | S3 result |
| Sequence 4 | Fourth sequence assessments | S4 result |
| Trimester 2 | S3 + S4 | T2_RESULT |
| Sequence 5 | Fifth sequence assessments | S5 result |
| Sequence 6 | Sixth sequence assessments | S6 result |
| Trimester 3 | S5 + S6 | T3_RESULT |
| Annual | T1 + T2 + T3 | ANNUAL |

From **Settings → Sessions & terms**:

1. Select `2026-2027`.
2. Open the reporting-period/configuration area.
3. Preview the standard configuration.
4. Verify that the preview contains S1 through S6, T1_RESULT through T3_RESULT, and ANNUAL.
5. Apply it only after reviewing the preview.
6. Keep trimester windows optional. If a school does not enter a window date, the period is not time-blocked.
7. Open each period and verify that the trimester result identifies its sequence dependencies rather than behaving as an unrelated independent period.

The applied preview fingerprint is:

`ccd1642b08e1c728b7aef9b8010a33d073ba4d09d80edee4b426712e6fe7ad56`

Configured period IDs:

| Code | ID |
|---|---|
| S1 | `9ed7d66e-f50c-4c68-8db7-caafbe8c15a3` |
| S2 | `83d52322-b6ef-41bc-8743-43c406163006` |
| T1_RESULT | `f3aeac7a-6a06-4eb3-b859-3a10ba010636` |
| S3 | `c404a0b6-9475-45f0-9adb-04d543b09df8` |
| S4 | `0e8d11ad-b1e3-48e0-96e7-0eac52aa3fea` |
| T2_RESULT | `8a4aae97-c5ac-4d3d-a5a0-e932ef61a54f` |
| S5 | `ec50562b-29df-41bd-8d45-5dbada42ba8e` |
| S6 | `44a27119-82f1-4e71-aa5a-c373fd55ef78` |
| T3_RESULT | `c07bfd4c-50e5-4e78-b22f-585469ad961d` |
| ANNUAL | `0adfa72f-b9ad-4fb4-837f-a7b7d0d8812e` |

### 5.3 Reporting calculation rule

The expected calculation is:

```text
subject S1 = the published/accepted S1 packet for that subject
subject S2 = the published/accepted S2 packet for that subject
subject T1 = average(S1, S2), respecting the class-subject coefficient
subject S3 = the published/accepted S3 packet
subject S4 = the published/accepted S4 packet
subject T2 = average(S3, S4)
subject S5 = the published/accepted S5 packet
subject S6 = the published/accepted S6 packet
subject T3 = average(S5, S6)
annual subject result = average(T1, T2, T3)
```

The result must not be manually entered as if it were an eighth unrelated examination. If one child sequence is incomplete, the UI must explain whether the trimester is pending, incomplete, or calculated according to the configured missing-value rule.

## 6. Sections, parcours, and classes

### 6.1 Sections created

| Code | Label | Educational level | Language |
|---|---|---|---|
| mat-fr | Maternelle FR | Maternelle | French |
| mat-en | Maternelle EN | Maternelle | English |
| pri-fr | Primaire FR | Primary | French |
| pri-en | Primary EN | Primary | English |
| sec-fr | Secondaire FR | Secondary | French |
| sec-en | Secondary EN | Secondary | English |

### 6.2 Classes created

Maternelle FR:

- `1ère année`
- `2ème année A`
- `2ème année B`

Maternelle EN:

- `Nursery 1`
- `Nursery 2`

Primary FR:

- `SIL B`, `SIL C`
- `CP A`, `CP B`, `CP C`
- `CE1 A`, `CE1 B`, `CE1 C`
- `CE2 A`, `CE2 B`, `CE2 C`
- `CM1 A`, `CM1 B`
- `CM2 A`, `CM2 B`

Primary EN:

- `Class 1`, `Class 2`, `Class 3`, `Class 4`, `Class 5`, `Class 6`

Secondary FR:

- `6ème A`, `6ème B`
- `5ème A`, `5ème B`
- `4ème A`, `4ème B`
- `3ème A`
- `2nde A`
- `1ère A`
- `Tle A`

Secondary EN:

- `Form 1`, `Form 2`, `Form 3`, `Form 4`, `Form 5`
- `Lower Sixth`, `Upper Sixth`

### 6.3 Class setup acceptance checks

For every class, verify:

- the class belongs to exactly one section/parcours;
- the class has the correct educational level;
- the class appears in the correct language view;
- a student can only be enrolled in a class from the intended session;
- an English class does not accidentally receive the French subject template;
- a secondary teacher assignment is subject-specific, not a primary-style homeroom assignment.

## 7. Subject catalogue and class-specific curriculum

### 7.1 Subject catalogue

The new database contains French and English subjects. Accented labels were inserted as UTF-8 and verified in the database/UI. The most important French subjects are:

`LANGAGE`, `PRE_MATH`, `MOTRICITE`, `EVEIL`, `FRANCAIS`, `MATHEMATIQUES`, `ANGLAIS`, `SCIENCES`, `SVT`, `HISTOIRE`, `GEOGRAPHIE`, `EDHC`, `TECHNOLOGIE`, `ARTS`, `MUSIQUE`, `EPS`, `PHYSIQUE`, `CHIMIE`, `INFORMATIQUE`, `PHILOSOPHIE`, `LITTERATURE`, `ESF`, `TRAVAIL_MANUEL`, `ORIENTATION`.

The English catalogue includes:

`LANGUAGE`, `MATHEMATICS`, `ENGLISH`, `FRENCH`, `SCIENCE`, `BIOLOGY`, `HISTORY`, `GEOGRAPHY`, `CITIZENSHIP`, `COMPUTER`, `PHYSICS`, `CHEMISTRY`, `PHYSICAL_EDUCATION`, `ART`, `MUSIC`, `MANUAL_WORK`, `LITERATURE`, `PHILOSOPHY`.

### 7.2 Class-subject assignment rule

The place to configure a subject for a class is the class curriculum/class-subject screen, not an Excel-only import. The relationship is the authoritative record for:

- whether the subject is offered to that class;
- the class-specific coefficient;
- maximum mark, normally 20;
- pass threshold, normally 10;
- whether it contributes to ranking;
- whether a teacher remark is enabled;
- whether it is mandatory for the class curriculum;
- the responsible teacher in secondary;
- the assigned class teacher/homeroom responsibility in primary/maternelle.

The subject-level coefficient is only the default. The report card must use the coefficient from the **class-subject relationship**. For example, `MATHEMATIQUES` in `6ème A` is configured with coefficient `4`, max `20`, pass threshold `10`, ranking enabled, and remark disabled in the current data set.

### 7.3 Curriculum configured

There are 499 curriculum rows:

| Level/language | Main default curriculum |
|---|---|
| Maternelle FR | LANGAGE, PRE_MATH, MOTRICITE, EVEIL; coefficient 1 |
| Maternelle EN | LANGUAGE, MATHEMATICS coefficient 3, PHYSICAL_EDUCATION, ART |
| Primary FR | FRANCAIS 2, MATHEMATIQUES 5, ANGLAIS 1, SCIENCES 1, HISTOIRE 1, GEOGRAPHIE 1, EDHC 1, TECHNOLOGIE 1, ARTS 2, MUSIQUE 1, EPS 1 |
| Primary EN | ENGLISH 3, MATHEMATICS 5, FRENCH 2, SCIENCE 2, HISTORY 1, GEOGRAPHY 1, CITIZENSHIP 1, COMPUTER 2, PHYSICAL_EDUCATION 2, ART 1 |
| Secondary FR | FRANCAIS 6, ANGLAIS 3, MATHEMATIQUES 4, SVT 5, PHYSIQUE 3, CHIMIE 2, INFORMATIQUE 2, HISTOIRE 2, GEOGRAPHIE 2, EDHC 2, PHILOSOPHIE 2, LITTERATURE 2, EPS 2, ESF 1, TRAVAIL_MANUEL 1 |
| Secondary EN | ENGLISH 3, FRENCH 3, MATHEMATICS 4, BIOLOGY 5, PHYSICS 3, CHEMISTRY 2, COMPUTER 2, HISTORY 1, GEOGRAPHY 1, CITIZENSHIP 2, PHYSICAL_EDUCATION 2, LITERATURE 2, PHILOSOPHY 2, MANUAL_WORK 1 |

### 7.4 “Mandatory” and pass mark semantics

The row-level `mandatory` flag is a curriculum/configuration flag. It is not automatically the same as “the student fails the whole class if the subject mark is below 10.” The school-level progression rule must use the total weighted result unless the school explicitly configures a separate required-subject rule.

The current intended promotion interpretation is:

```text
weighted total = Σ(subject result × class-subject coefficient)
weighted average = weighted total / Σ(coefficients)
promotion pass mark = configured total/average threshold
```

Do not implement a per-subject hard fail merely because a curriculum row is marked mandatory. If the administration later wants a required-subject barrier, it must be an explicit, separately named progression rule with its own explanation in the promotion decision.

## 8. Employees, roles, and responsibility assignment

### 8.1 Staff created

| Staff member | Intended role | Primary responsibility |
|---|---|---|
| MBAH Junior | Primary teacher/form teacher | CE1 A and other assigned primary classes |
| Jeanne Dongmo | Primary teacher/form teacher | CE1 B and other assigned primary classes |
| Paul Ngono | Primary teacher/form teacher | CP A and other assigned primary classes |
| TENEKU DONAL | Secondary teacher | Mathematics/physics/chemistry/EPS responsibility in configured secondary classes |
| NGOUNOU Fabrice | Secondary teacher | French/English/history/geography/philosophy/literature responsibility in configured secondary classes |
| TAGNE Joel | Secondary teacher | Remaining configured secondary subjects |
| FOTSO Bernard | Accountant | Finance and payment processing |
| NSONA Mireille | Direction/principal | Direction-level operations |
| Aline Ndom | Maternelle teacher/form teacher | Maternelle classes |
| Grace Forchu | Primary EN teacher/form teacher | Primary English classes |
| John Nji | Secondary EN teacher/form teacher | Secondary English classes and subject responsibilities |

### 8.2 Primary and maternelle rule

For primary/maternelle, the class teacher is the default responsible teacher for the class. The class teacher can see and enter all configured subjects for the assigned class, subject to the final permission policy.

The new database configured homeroom assignments for all 26 non-secondary classes. The representative links include:

- MBAH Junior: CE1 A, CE1 C, CM1 A, CM2 A, SIL B
- Jeanne Dongmo: CE1 B, CE2 A, CM1 B, CM2 B, SIL C
- Paul Ngono: the remaining French primary classes
- Aline Ndom: all configured maternelle classes
- Grace Forchu: all configured primary English classes

### 8.3 Secondary rule

Secondary classes use subject-specific responsibility. A teacher can be assigned mathematics in a class without becoming responsible for that class’s French or history marks.

The new database assigned 248 secondary class-subject responsibility rows with 248 responsible teachers. A sample from `6ème A`:

- FRANCAIS → NGOUNOU Fabrice
- ANGLAIS → NGOUNOU Fabrice
- MATHEMATIQUES → TENEKU DONAL

The timetable teacher field is locked to this class-subject responsibility. Attempting to schedule another teacher for the same class and subject returns a validation error explaining the responsible teacher.

### 8.4 Credentials

The source CSV contains no parent email addresses. Therefore, source guardians were created with `NO_PORTAL`; they do not have invented credentials.

For staff, the application’s reset flow is the source of truth:

1. Sign in as an administrator.
2. Open the employee detail screen.
3. Click **Reset credentials**.
4. Confirm the action in the application modal.
5. Read the generated email in Mailpit at `http://localhost:8135`.
6. Use the username and temporary password from that message to test login.
7. Change the password before production use.

The reset endpoint is:

```text
POST /api/staff/{employeeId}/reset-credentials
```

The API intentionally does not return the password; it sends it through the configured mail channel.

## 9. Student and family import

### 9.1 Source file

Source archive:

`C:\Users\joe tech\Music\classe update.zip`

The extracted archive contains 19 UTF-8-BOM CSV files. Each file has 17 comma-separated columns:

```text
nom,prenom,sexe,date_naissance,lieu_naissance,niu,redouble,
pere_nom,pere_telephone,pere_email,
mere_nom,mere_telephone,mere_email,
tuteur_nom,tuteur_lien,tuteur_telephone,tuteur_email
```

### 9.2 Source validation results

- 740 source rows.
- No duplicate source key detected.
- Sex distribution: 373 male / 367 female.
- One source row marked as repeating.
- 653 father names and 575 father phone values.
- No parent email values in the supplied source.
- No mother or tutor names in the supplied source.
- 87 rows had no guardian name at all.

This matters because a guardian account cannot be safely generated without an email and password. The application must never create a fake account with an invented email merely to make the count look complete.

### 9.3 Class-name mapping

The import maps source file/class labels to the new class records:

| Source label | Target class |
|---|---|
| `1ER_ANNEE` | `1ère année` |
| `2EME_ANNEE_A` | `2ème année A` |
| `2EME_ANNEE_B` | `2ème année B` |
| `CLASS_6` | `6ème A` |
| `SIL_B` / `SIL_C` | `SIL B` / `SIL C` |
| `CE1_A`, `CE1_B`, `CE1_C` | matching CE1 class |
| `CE2_A`, `CE2_B`, `CE2_C` | matching CE2 class |
| `CM1_A`, `CM1_B` | matching CM1 class |
| `CM2_A`, `CM2_B` | matching CM2 class |
| `CP_A`, `CP_B`, `CP_C` | matching CP class |

Two source rows had no first name:

- CM1 A / last name HAPSATOU
- CM1 B / last name MAÏRAMOU

They were imported as explicit review records with the first name `Prénom à compléter`, rather than silently discarded. These records must be corrected by an administrator before official documents are printed.

### 9.4 Import flow

The normal UI flow is:

1. Open **Students → Import family** at `/students/import-family`.
2. Upload the CSV/Excel file.
3. Confirm the delimiter and column mapping.
4. Preview invalid dates, duplicate records, missing names, and guardian warnings.
5. Correct source data where possible.
6. Choose whether each guardian gets a portal account.
7. Submit the import.
8. Download or record the import report.
9. Open a created student and verify enrollment, guardian relation, and class.

The API operation used for one family is:

```text
POST /api/student-registrations
```

with a `student` object and a `guardians` array. It is preferable to one giant opaque SQL import because it preserves validation, idempotency, guardian relationships, and audit events.

### 9.5 Demonstration family records

Because the source has no emails, two portal-enabled demonstration families were added to test the real parent workflow.

Family A:

- Portal username: `parent.demo@biyem-assi.cm`
- Demonstration password: `DemoParent2026!`
- Amina Démonstration → CE1 A
- Boris Démonstration → 6ème A
- Both students share the same guardian record.

Family B tests three parcours:

- Portal username: `parent.parcours@biyem-assi.cm`
- Demonstration password: `ParcoursDemo2026!`
- Chloe → Class 1
- Daniel → Form 1
- Eva → Nursery 1
- All three share the same guardian record.

These are test credentials only. They must be replaced or disabled before a real production go-live.

## 10. Default assessment generation

### 10.1 Purpose

Assessment generation creates the editable one-assessment-per-subject template for each sequence. It is not the same thing as entering marks. It creates the packet structure that grade entry later fills.

### 10.2 UI flow

1. Open **Academic → Evaluations**.
2. Select a class, for example `6ème A`.
3. Select a sequence, for example `S1`.
4. Click **Generate default**.
5. Review the friendly preview screen.
6. Confirm that only subjects assigned to the selected class appear.
7. Confirm that each row shows subject name and code.
8. Confirm that the preview says exactly what will be created.
9. Apply the generation.
10. Return to the evaluation list and filter by the same class and sequence to see the generated rows.

The API equivalents are:

```text
POST /api/academic/assessment-defaults/preview
POST /api/academic/assessment-defaults/apply
```

The request contains `academicSessionId`, `classId`, and `mode=ALL_SEQUENCES`; the apply request also carries the preview fingerprint and an idempotency key.

### 10.3 Actual result

The template was applied for all 43 classes:

- 2,994 assessment rows created.
- One default assessment per assigned subject for S1 through S6.
- `6ème A` preview contained 90 rows (15 subjects × 6 sequences).

Running the same generation again returns zero rows to update because the operation is idempotent. That is expected; the user should be directed to the existing evaluation list rather than seeing a confusing empty result.

## 11. Finance and accounting configuration

### 11.1 Accounting prerequisites

Before charging students:

1. Open **Finance → Accounting**.
2. Confirm the chart of accounts exists.
3. Confirm an open accounting period covers the charge/payment date.
4. Confirm each payment channel is mapped to an active, postable asset account.
5. Confirm the finance readiness panel shows all component checks as ready.

The session has monthly periods from September 2026 through July 2027 and a pre-opening period for August 2026:

- `FY26-PREOPEN`: 2026-08-01 through 2026-08-31
- Monthly periods generated by the FY26 period command, through 2027-07-31

### 11.2 Chart of accounts

The important accounts are:

| Code | Account | Purpose |
|---|---|---|
| 1000 | Caisse | Cash payments |
| 1010 | Banque | Bank transfer payments |
| 1020 | Orange clearing | Orange Money payments |
| 1030 | MoMo clearing | Mobile-money clearing; currently not mapped successfully |
| 1040 | Card clearing | Card/MPGS payments |
| 1100 | Student receivable | Outstanding student charges |
| 2100 | Student credits | Overpayments/credits |
| 2200 | Payroll payable | Staff salary liability |
| 4000 | Tuition revenue | Tuition income |
| 4010 | Registration revenue | Registration income |
| 4090 | Other revenue | Other income |
| 6000 | Salary expense | Payroll expense |

### 11.3 Fee types and installments

Active fee types:

| Code | Label | Default amount |
|---|---|---:|
| REGISTRATION | Registration | 25,000 XAF |
| TUITION | Tuition | 180,000 XAF |
| BOOKS | Books | 30,000 XAF |
| TRANSPORT | Transport | 60,000 XAF |
| EXAM | Examination | 15,000 XAF |

Installment template:

- Code: `TUITION_3_TRANCHES_2026`
- 30% at session start
- 30% at +90 days
- 40% at +180 days

### 11.4 Fee plans

Active plans cover:

- Maternelle FR: 205,000 XAF
- Maternelle EN: 215,000 XAF
- Primary FR: 235,000 XAF
- Primary EN: 245,000 XAF
- Secondary FR: 275,000 XAF
- Secondary EN: 285,000 XAF
- CE1 A class-specific override: 220,000 XAF

The class-specific CE1 A override demonstrates the intended precedence:

```text
class-specific plan > parcours/section plan > global default
```

### 11.5 Charge generation flow

1. Open **Finance → Fee plans**.
2. Create or confirm fee types.
3. Create the installment template.
4. Attach fee-plan lines to a section or class.
5. Open **Finance → Charges**.
6. Select the current session and target scope.
7. Preview the number of enrollments, total amount, and blocked rows.
8. Confirm generation.
9. Open the generated job and inspect completed, blocked, and failed counts.
10. Open a student’s ledger and verify each charge/installment.

Actual generation jobs completed with zero blocked and zero failed rows:

| Scope | Enrollments | Generated charges | Total |
|---|---:|---:|---:|
| Primary FR | 635 | 1,270 | 129,500,000 XAF |
| Maternelle FR | 82 | 164 | 14,350,000 XAF |
| Maternelle EN | 1 | 2 | 185,000 XAF |
| Primary EN | 1 | 2 | 215,000 XAF |
| Secondary FR | 25 | 50 | 6,125,000 XAF |
| Secondary EN | 1 | 2 | 255,000 XAF |

The primary FR job is the main real-data generation; the other jobs exercise the section-specific and smaller-scope cases.

### 11.6 Payment and receipt test cases

Family A / Amina:

- Proposed registration: 25,000 XAF
- Partial tuition allocation: 24,500 XAF
- Total payment: 49,500 XAF
- Channel: Orange Money (`OM`)
- Reference: `OM-PROD-AMINA-001`
- Receipt: `RCT/2026/000001`
- Status: posted, allocated, receipt issued
- Remaining outstanding: 140,500 XAF

Family A / Boris:

- Total outstanding before payment: 245,000 XAF
- Payment: 245,000 XAF
- Channel: bank transfer
- Reference: `BANK-PROD-BORIS-001`
- Receipt: `RCT/2026/000002`
- Status: posted, fully allocated, receipt issued
- Remaining outstanding: 0 XAF

The payment test verifies both partial installment payment and full payment. It also verifies that a journal entry and receipt document are created.

### 11.7 Finance gap to resolve before production

The legacy endpoint:

```text
GET /api/finance/students/{studentId}/statement
```

returns an empty legacy statement for these V2 charges. The authoritative V2 endpoints show the correct balances:

```text
/api/finance/v2/charges/accounts/{enrollmentId}
/api/finance/v2/collections
```

The Finance UI must be checked to ensure it uses V2 data. If it still calls the legacy statement endpoint, it will display zero while the ledger contains real charges. This is a blocking UI/API consistency issue, even though the V2 records and accounting postings are correct.

There is also a readiness presentation defect in the target V2 source: the chart-of-accounts `ReadinessCheck` arguments are passed in the wrong order, so the component rows appear ready while the aggregate `ready` flag remains false. The source location is:

`C:\Users\joe tech\.codex\worktrees\full-school-e2e\backend\src\main\java\com\bbc\sms\finance\accounting\FinanceReadinessService.java`

The constructor call must pass the detail before the status. This should be fixed and rebuilt before treating finance readiness as production-safe.

The `MOMO` channel is not currently mapped because the endpoint rejected the selected account as not active/postable. It must either be mapped to a valid asset/clearing account or disabled from the payment-channel selector.

## 12. Timetable configuration

### 12.1 Periods

The following timetable periods are configured:

| Period | Time |
|---|---|
| P1 | 07:30–08:15 |
| P2 | 08:20–09:05 |
| P3 | 09:10–09:55 |
| P4 | 10:15–11:00 |
| P5 | 11:05–11:50 |
| P6 | 12:00–12:45 |
| P7 | 14:00–14:45 |
| P8 | 14:50–15:35 |

### 12.2 Master timetable flow

1. Open **Timetable**.
2. Select the current session.
3. Configure period times.
4. Create a draft timetable version with effective dates 2026-09-01 through 2027-07-31.
5. Add class slots.
6. For primary/maternelle, confirm the teacher is the class teacher and the field is not editable.
7. For secondary, select a class-subject pair; the teacher should be filled from the class-subject responsibility and locked.
8. Leave the room blank unless the room exists in the room catalogue; an unknown room blocks publication.
9. Run conflict validation.
10. Publish the version.
11. Open each teacher’s personal schedule.

Current version:

- ID: `46bcd68b-80cc-4505-a184-b1fb91fe7b06`
- Version: 1
- Effective: 2026-09-01 through 2027-07-31
- Status: PUBLISHED
- Slots: 100
- Classes represented: CE1 A, 6ème A, Class 1, Nursery 1

The 100 slots were intentionally limited to four representative classes. The other 39 classes are configured and enrolled where applicable but do not yet have a populated weekly master timetable.

### 12.3 Timetable tests completed

- Empty conflict report after publishing the valid version.
- Wrong teacher for secondary `MATHEMATIQUES` rejected with a message naming the responsible teacher.
- Attempt to place the same teacher in two classes at the same time rejected with `TIMETABLE_TEACHER_CONFLICT`.
- Teacher schedules generated for MBAH Junior, TENEKU DONAL, NGOUNOU Fabrice, and TAGNE Joel.
- Asking for the admin’s personal teacher schedule correctly returns that the administrator is not linked to an employee.

## 13. Attendance configuration and tests

### 13.1 Rules

| Level | Attendance model | Late threshold | Chronic threshold |
|---|---|---:|---:|
| Maternelle | DAILY | 15 minutes | 15% |
| Primary | DAILY | 15 minutes | 15% |
| Secondary | PERIOD | 10 minutes | 20% |

Absence reason is required.

### 13.2 Daily primary roll call

1. Open **Attendance** at `/presence`.
2. Select date `2026-09-02`.
3. Select `CE1 A`.
4. Confirm the UI offers a daily roll call rather than a timetable subject period.
5. Mark one student absent with reason `Fièvre signalée par la famille`.
6. Mark a second student late by 12 minutes.
7. Mark all remaining students present.
8. Save, finalize, and reopen the session to verify the final status.
9. Open **Analytics** and verify the totals.

Actual result for CE1 A:

- Expected: 45
- Present: 43
- Late: 1
- Absent: 1
- Unmarked: 0
- Attendance percentage: 97.78%
- Session status: FINALIZED

### 13.3 Period secondary roll call

1. Select `6ème A` on the same date.
2. Select a published period, for example P1.
3. Confirm the UI requires a published timetable period.
4. Mark the same status pattern and finalize.
5. Verify that the record is associated with the class, period, and subject rather than being a whole-day record.

Actual result for the selected P1 session:

- Subject: PHILOSOPHIE
- Expected: 25
- Present: 23
- Late: 1
- Absent: 1
- Unmarked: 0
- Attendance percentage: 96%
- Session status: FINALIZED

If the UI says “No published period exists,” the fix is not to bypass attendance validation. First confirm the selected date, class, current session, timetable version effective date, and published slot.

## 14. Academic grade entry and report-card flow

### 14.1 Teacher packet flow

1. Reset a teacher account through **Staff** and retrieve the email from Mailpit.
2. Sign out of the administrator account.
3. Sign in as MBAH Junior.
4. Open **Academic → Grade entry**.
5. Select `CE1 A`, `S1`, and one assigned subject.
6. Confirm that the teacher is the responsible teacher and the class roster is visible.
7. Enter marks for every student in the packet.
8. Save as draft.
9. Confirm the packet status remains editable.
10. Submit/send to management.
11. Sign in as an authorized reviewer.
12. Open the reviewer queue, inspect the packet, and accept it.
13. Repeat for every subject in S1 and S2.
14. Generate or calculate the T1 result.
15. Open the report card and verify that S1, S2, and the calculated trimester result are shown.

The canonical view endpoint is:

```text
GET /api/academic/grade-entry?reportingPeriodId={periodId}&classId={classId}&subjectCode={subjectCode}
```

The canonical save endpoint is:

```text
POST /api/academic/grade-entry/save
```

The canonical workflow endpoint is:

```text
POST /api/academic/grade-entry/workflow
```

Direct legacy grade routes are intentionally disabled with `CANONICAL_GRADE_PACKET_REQUIRED`.

### 14.2 Required S1–S6 test matrix

The full acceptance run should use at least one representative student in CE1 A and one in 6ème A:

| Phase | Data to enter | Expected result |
|---|---|---|
| S1 | All assigned subjects | S1 values visible in report preview |
| S2 | All assigned subjects | S2 values visible; T1 becomes calculable |
| T1 | No direct manual marks | Result calculated from S1 + S2 |
| S3 | All assigned subjects | S3 values visible |
| S4 | All assigned subjects | S4 values visible; T2 becomes calculable |
| T2 | No direct manual marks | Result calculated from S3 + S4 |
| S5 | All assigned subjects | S5 values visible |
| S6 | All assigned subjects | S6 values visible; T3 becomes calculable |
| T3 | No direct manual marks | Result calculated from S5 + S6 |
| Annual | No direct manual marks | Annual result calculated from T1, T2, T3 |

### 14.3 Report-card acceptance criteria

The report card must:

- show the student’s name, matricule, class, session, and profile photo if present;
- use the class-subject coefficient, not the subject default coefficient;
- show each sequence result;
- show trimester result as a calculation from its child sequences;
- show annual result separately when the annual milestone is selected;
- show teacher remarks beside the relevant subject when enabled;
- show subject totals, weighted average, rank, and appreciation according to the class configuration;
- display attendance and conduct sections using actual attendance data;
- use the configured school profile and language;
- be printable/downloadable without `??` encoding artifacts.

At the time this document was written, assessment templates were created and grade-entry views were verified, but a complete S1–S6 teacher-login/packet-submission run still needs to be completed on this fresh database. It is therefore listed as a critical remaining acceptance test rather than falsely marked complete.

## 15. Permission and role acceptance tests

Permissions must be checked with real logins, not only with administrator screens.

### Teacher: primary form teacher

Using MBAH Junior:

- Can see CE1 A students.
- Can edit marks for CE1 A assigned subjects.
- Can see and manage the class’s daily attendance.
- Cannot see students from unrelated classes.
- Cannot transfer a student.
- Cannot change session/class/course configuration.
- Cannot edit the master timetable.
- Can see only the teacher’s own published schedule.

### Teacher: secondary subject teacher

Using TENEKU DONAL:

- Can see the students in classes where he is assigned a subject.
- Can edit marks only for the assigned subject/class relationship.
- Cannot see another subject’s marks for the same student.
- Can take attendance only for the permitted class/period scope.
- Cannot edit another teacher’s subject packet.
- Cannot transfer students.
- Cannot edit timetable slots.
- Can see his own published weekly schedule.

### Form teacher visibility

The class titulaire/form teacher has broader read access for the assigned class:

- can see all subject results for that class;
- cannot edit other teachers’ marks by default;
- may be granted explicit edit permission for all class subjects;
- still cannot modify unrelated classes.

### Accountant

Using FOTSO Bernard:

- can view and manage fees, charges, installments, collections, receipts, and accounting links for all students;
- cannot edit academic marks;
- cannot manage attendance;
- cannot transfer students;
- cannot alter sessions, class structure, or curriculum unless explicitly granted.

### Direction/principal

Using NSONA Mireille:

- can access direction-level operational views;
- must not automatically receive session/class/course configuration access;
- can be explicitly granted those settings later by an administrator;
- must not inherit finance or academic edit access merely from being a principal.

### Parent

Using the demonstration parent:

- can see only linked children;
- can see published bulletins and permitted attendance/finance information;
- cannot see another family’s children;
- cannot edit marks, attendance, enrollment, class transfers, or timetable;
- sibling links work across CE1 A and 6ème A.

## 16. Full click-through test order

This is the recommended human test sequence on `http://localhost:8110`.

### Phase A: administrator setup

1. Sign in as `admin/admin`.
2. Settings → General → verify school identity.
3. Settings → Sessions & terms → verify `2026-2027` is current/open.
4. Settings → Sessions & terms → verify reporting periods and dependencies.
5. Settings → Academics/sections → verify six parcours.
6. Settings → Classes → open one class from each level/language.
7. Settings → Subjects → verify accented names.
8. Class curriculum/class subjects → verify class-specific coefficients.
9. Staff → create/open each test employee.
10. Assign classes and subject responsibilities.
11. Settings → Mail → verify Mailpit configuration.

### Phase B: student and family setup

1. Students → Import family.
2. Upload one source file and preview it.
3. Verify date parsing and delimiter.
4. Verify missing guardian email warning.
5. Import or continue the full set.
6. Open a student detail page.
7. Verify current enrollment, class, matricule, guardian relation, and history.
8. Open the demonstration parent account and verify both linked children.

### Phase C: evaluations and grades

1. Academic → Evaluations.
2. Select CE1 A + S1.
3. Generate default preview.
4. Confirm only CE1 A subjects appear.
5. Apply generation.
6. Repeat for S2.
7. Reset MBAH credentials and log in as MBAH.
8. Academic → Grade entry.
9. Enter/save/submit S1 packets.
10. Review/accept packets using an authorized reviewer.
11. Repeat S2.
12. Open report card and verify calculated T1.

### Phase D: finance

1. Log in as accountant.
2. Finance → Fee types → verify registration, tuition, books, transport, exam.
3. Finance → Plans → verify section plans and CE1 A override.
4. Finance → Installments → verify three tranches.
5. Finance → Charges → preview and generate one small scope.
6. Open Amina’s ledger.
7. Post partial payment.
8. Print/download receipt.
9. Open Boris’s ledger.
10. Post full payment.
11. Verify zero outstanding.
12. Open accounting journal and confirm balanced debit/credit entries.

### Phase E: timetable and attendance

1. Log in as administrator.
2. Timetable → periods.
3. Timetable → create/open draft version.
4. Add a primary slot and verify homeroom lock.
5. Add a secondary slot and verify subject-teacher lock.
6. Attempt a duplicate teacher/time assignment and verify it is rejected.
7. Publish.
8. Log in as a teacher and open personal schedule.
9. Attendance → CE1 A → daily roll call.
10. Mark absent, late, and present.
11. Finalize and inspect analytics.
12. Attendance → 6ème A → published period.
13. Mark and finalize period attendance.
14. Inspect analytics.

### Phase F: permission denial checks

1. As secondary teacher, open a different subject’s grade packet; expect denial or no data.
2. As secondary teacher, open another class; expect denial or no data.
3. As teacher, attempt student transfer; expect permission denial.
4. As teacher, attempt timetable edit; expect permission denial.
5. As accountant, attempt grade edit; expect permission denial.
6. As parent, attempt to open an unrelated student; expect no data.
7. As principal, attempt session/class/course settings; expect no access unless explicitly granted.

## 17. Actual validation results

### Passed

- New isolated stack starts on the dedicated ports.
- Backend health is `UP`.
- New database has no dependency on old database volumes.
- School profile persisted with correct UTF-8 accents.
- Current academic session and reporting-period dependencies created.
- Six sections and 43 classes created.
- 42 subjects and 499 curriculum rows created.
- 248 secondary subject responsibilities configured.
- 26 primary/maternelle homeroom assignments configured.
- 740 source rows imported, with two explicit incomplete-name review records.
- Five demonstration students/family cases added.
- 2,994 idempotent assessment templates generated.
- Finance fee plans and installment template created.
- Charges generated with zero blocked/failed rows across tested scopes.
- A partial payment and a full payment posted with receipts and journal entries.
- Timetable version published with 100 valid slots.
- Wrong-teacher and double-booking timetable attempts rejected.
- Daily primary attendance finalized and analyzed.
- Period secondary attendance finalized and analyzed.

### Not yet a final production sign-off

- Complete teacher-login grade packet workflow for S1–S6.
- Confirm trimester and annual bulletin calculations after all packets are accepted.
- Confirm official bulletin PDF generation from the fresh database.
- Verify all staff credentials through Mailpit and login with each role.
- Verify the V2 finance UI does not use the stale empty legacy statement endpoint.
- Fix or explicitly waive the finance readiness aggregate flag bug.
- Map or disable the MOMO payment channel.
- Populate weekly timetable slots for the remaining 39 classes.
- Add real profile photos if the school requires them on bulletins.
- Correct the two imported missing first names.
- Confirm all permission denials using real non-admin accounts, not only policy inspection.

## 18. Known implementation/data issues

1. **Finance legacy statement mismatch:** V2 charges exist and are balanced, but the old student statement endpoint returns zero. The UI must be migrated to the V2 ledger source.
2. **Finance readiness aggregate flag:** component statuses are ready but the aggregate flag is false due to a constructor argument order bug in `FinanceReadinessService`.
3. **MOMO payment mapping:** the current attempted clearing account was rejected as not active/postable. Do not expose MOMO as selectable until it maps to a valid asset account.
4. **Secondary titulaire:** the current class-teacher endpoint rejects secondary homeroom assignments by design. Secondary responsibility is per subject. If the school wants a secondary class adviser, implement it as a separate read/coordination role rather than replacing subject responsibility.
5. **Setup class-teacher endpoint:** one setup endpoint returned HTTP 500 because its SQL result alias did not match the mapper’s expected `active` column. Class links were configured through the working employee-class endpoint instead. This endpoint should be fixed before relying on it in the UI.
6. **Source guardian portals:** the source data has no email addresses. Imported guardians are therefore `NO_PORTAL`; the demonstration parent accounts are intentionally synthetic test data.
7. **Timetable coverage:** only four representative classes have populated weekly slots. A real school needs one published timetable version covering all active classes before period attendance can be used everywhere.
8. **Default bootstrap password:** `admin/admin` is acceptable only for local simulation. It must be changed or disabled for production.
9. **Photo coverage:** no actual student photos were supplied for the imported rows. Report-card profile-photo behavior is not fully validated with the real roster.

## 19. Remaining work, in priority order

### P0 — must pass before production acceptance

1. Fix the V2 finance readiness aggregate result.
2. Resolve the V2/legacy finance statement UI mismatch.
3. Complete real staff credential reset and login tests.
4. Complete S1 and S2 grade packet entry/submit/review for a representative class.
5. Verify T1 calculation and report card.
6. Repeat one representative path for T2, T3, and annual calculation.
7. Verify teacher/parent/accountant permission denials with real accounts.
8. Replace default admin credentials.

### P1 — required for operational rollout

1. Populate and publish timetables for all active classes.
2. Decide and configure the valid MOMO payment channel.
3. Correct missing source first names and review all import warnings.
4. Upload student profile photos where bulletins require them.
5. Confirm receipt/invoice/payroll document templates and print quality.
6. Configure payroll employees, pay periods, payslips, and accounting postings.
7. Verify backup/restore and database migration procedures against this isolated volume.

### P2 — can follow the first production release

1. Add more nuanced parent notification preferences.
2. Add richer class-level attendance dashboards.
3. Complete all remaining promotion edge cases and manual override reasons.
4. Add additional secondary-language and mixed-parcours permission scenarios.

## 20. Handoff rules

Any agent continuing this work must:

- use the `bbc-prod-simulation` Compose project;
- use frontend port `8110` and backend port `8111`;
- never assume `8082`, `8085`, or `8100` points at this database;
- preserve the `bbc-prod-2026-db` volume;
- query the fresh database before creating duplicate demo data;
- preserve existing administrator policy overrides when adding a new permission rule;
- treat `academic_curriculum_subject` as authoritative for class-specific coefficients;
- treat V2 finance charges/accounts as authoritative until the UI migration is complete;
- record every test result in this document or in a linked test log;
- never invent guardian credentials for source rows that have no email.

## 21. Final go-live checklist

The simulation database is ready for a controlled human walkthrough, but not yet a final production sign-off. Sign off only when every item below is checked:

- [ ] Correct school identity and UTF-8 display everywhere.
- [ ] Current session open and dates verified.
- [ ] Reporting periods and sequence dependencies verified.
- [ ] All active classes and class-subject assignments reviewed.
- [ ] Class-specific coefficients reviewed.
- [ ] Teacher class and subject responsibility reviewed.
- [ ] Student import exceptions resolved.
- [ ] Guardian portal cases tested.
- [ ] Staff credentials reset and tested.
- [ ] Complete S1–S6 grade workflow tested.
- [ ] T1, T2, T3, and annual results calculated from child periods.
- [ ] Bulletin PDF and profile-photo behavior verified.
- [ ] Fee types, plans, installments, and charges verified.
- [ ] Partial and full payments verified.
- [ ] Receipts, invoices, journals, and balances verified.
- [ ] Finance readiness aggregate fixed and green.
- [ ] MOMO channel mapped or hidden.
- [ ] Timetable published for every active class.
- [ ] Teacher timetable views verified.
- [ ] Primary daily attendance verified.
- [ ] Secondary period attendance verified.
- [ ] Attendance analytics verified.
- [ ] Teacher, accountant, principal, and parent permission denials verified.
- [ ] Backup/restore tested.
- [ ] Default passwords removed.
