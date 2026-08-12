# Empty-database end-to-end test report

Date: 2026-08-12
Branch: `codex/settings-readiness-fix`
Isolated application: `http://localhost:8086` (admin/admin)

## What was exercised

The test used a fresh PostgreSQL volume in the isolated Docker project `bbcomplex-e2e`. Configuration and data were created through the visible application UI, not by inserting test fixtures directly.

- Sessions: `2026-2027` and `2027-2028`.
- Classes: `6eme A`, `5eme A`, `4eme A`.
- Subjects: Mathematics, French, English, History, and Life and Earth Sciences.
- Teacher: Nadia Martin, responsible for all five subjects in `6eme A`.
- Students: 10 active students enrolled in `6eme A` for `2026-2027`.
- Family workflow: one parent account was created during registration; subsequent students used **Search existing parent**.
- Class-subject curriculum: all five subjects assigned to `6eme A`; Mathematics coefficient 2, remaining subjects coefficient 1; curriculum published/locked.

## Reproducible UI flow

### Session and reporting structure

1. Go to **Settings → Années & périodes**.
2. Create/open `2026-2027`, set it current, and use `2026-09-01` to `2027-07-31`.
3. Create T1, T2, and T3 with the school-year date ranges.
4. Run **Academic configuration wizard**:
   - S1 + S2 → T1 result;
   - S3 + S4 → T2 result;
   - S5 + S6 → T3 result;
   - T1 + T2 + T3 → annual result.
5. Use 0.5/0.5 sequence dependencies and equal annual trimester weights. Leave trimester access windows unrestricted for this test. Readiness becomes **READY**.

### Classes, subjects, teacher, and students

1. In **Settings → Academics**, create/select the Secondaire francophone section.
2. Create the five subjects and Nadia Martin in **Staff**.
3. Open **Settings → Academics → Matières par classe**, select `6eme A`, assign the five existing subjects, set the class coefficients, choose Nadia as responsible teacher, save, then publish/lock the curriculum.
4. In **Students → Nouvel élève**, create the first student with parent credentials; register nine more and use **Search existing parent** for the existing family where applicable.

### Evaluations and grades

1. Open **Academic → Evaluations**, select `6eme A` and each sequence, and use the default-template action.
2. Confirm the template contains only the five subjects assigned to the class, one evaluation per subject.
3. Open **Academic → Grade entry**. For every subject in S1 through S6, enter ten student grades, choose **Save without sending** or **Send to Management**, then complete the reviewer acceptance flow.
4. Open **Academic → Report cards** and verify S1–S6, then the calculated trimester results and annual result.
5. Publish each period through its lifecycle controls after validation.

Observed data: 30 grade packets and 300 academic grades (10 students × 5 subjects × 6 sequences), with no orphan grade rows. The database contains 10 `PUBLISHED` bulletins for each of S1, S2, S3, S4, S5, S6, T1, T2, T3, and ANNUAL (100 total) and 100 issued documents. T1/T2/T3 are calculated from their sequence pairs; annual is calculated from the three trimester results.

### Next session and promotion

1. In **Settings → Années & périodes**, create `2027-2028` and use **Reuse a previous session** to copy terms, reporting milestones, dependencies, and trimester access limits with shifted dates.
2. Assign the same five subjects to `5eme A` under **Settings → Academics → Matières par classe**, set coefficients/responsible teacher, and publish the curriculum.
3. Open **Journey → Promotions → 1. Règles & parcours**.
4. Choose source `2026-2027` and target `2027-2028`; configure `6eme A → 5eme A`, `5eme A → 4eme A`, and mark `4eme A` terminal. Publish/freeze the graph.
5. Publish the rule set: automatic promotion from 10/20, review/repeat below 8/20, and final average required. Manual **Décider** controls remain available for administrator override.
6. Open **2. Révision & validation → Prévisualiser les décisions**. All ten students displayed their published annual average (13.94–14.06/20), recommendation **Promouvoir**, and target `5eme A`.
7. Click **Enregistrer le lot de révision → Valider le lot → Confirmer le transfert**, supplying the validation reason.

The UI confirmed the next-session registrations were created. Database evidence: one `COMMITTED` batch and ten `PLANNED` target enrollments in `5eme A` for `2027-2028`; source enrollments remain `ACTIVE` until the target session becomes effective. The UI was then switched to `2027-2028` and showed it as **Courante**, and finally restored to `2026-2027` as **Courante**. This verifies the planned-enrollment behavior across a session switch without prematurely closing the source enrollment.

## Attendance regression verified

At `http://localhost:8085/presence`, choosing date `2026-09-22` and secondary class `4eme A` previously returned HTTP 500 from the attendance session-options request when no timetable configuration existed. The corrected app now shows:

> No published period exists for this class and date. Create and publish a timetable slot before taking attendance.

The page no longer shows **Erreur interne du serveur**. The user must go to **Timetable**, create and publish the class period, then return to **Attendance → Roll call**. Primary/Daily classes continue to use one daily roll call; secondary/Period classes require a published timetable period.

## Code changes verified

- First class-subject assignment creates an empty draft curriculum with session dates when no published source exists.
- Grade packets are persisted before grade rows; existing grade rows are reused, preventing orphan grades.
- Current-session hand-off clears the old current row before saving the new one, avoiding the partial-unique-index conflict.
- A validated bulletin remains publishable after reload.
- Bulletin idempotency keys no longer include the free-form reason, avoiding header-length rejection.
- Promotion rule-set publication is exposed and uses the backend row version correctly.
- Attendance handles a missing timetable configuration as an actionable validation response instead of HTTP 500.

## Verification commands

From the backend directory:

```powershell
mvn -q -Dtest=SharedFoundationIntegrationTest test
```

Result: 14 tests, 0 failures, 0 errors. Docker build/runtime verification was also completed for the isolated frontend/backend stack.

The local-only compose overlay `docker-compose.e2e-empty.yml` is intentionally untracked and is not part of the commit; it exists only to keep the empty test database isolated from the normal development database.
