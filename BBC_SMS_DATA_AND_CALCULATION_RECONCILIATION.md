# BBC SMS data and calculation reconciliation

## Academic results and promotion

- Assessment-default generation created 24 nursery S1–S6 packets; all 24 ended `ACCEPTED` after teacher submit and management review.
- Annual conduct for the three promotion fixtures ended `APPROVED`.
- Published annual snapshots: Mvondo Aïcha average 15, Nanga Amina average 16, Talla Marius average 17.
- Promotion preview returned three candidates with published graph/rules and no blockers.
- Automated decisions were `PROMOTE` for all three; a valid, reasoned manual override changed only Marius to `REPEAT`.
- Commit returned `COMMITTED`; identical replay returned `COMMITTED` with no duplicates. Three target enrollments became `ACTIVE`; three source enrollments remained `COMPLETED` with source/target links.
- Exact snapshot, batch, decision, enrollment, register, and hash identifiers are in [`12-promotion/evidence.md`](qa/e2e-runs/2026-08-14-full-school/12-promotion/evidence.md).

## Finance reconciliation

- Gate 10 evidence covers versioned fee plans, class overrides, idempotent charges, partial/full collection, cashier close, waiver/reversal/refund, invoices/receipts/documents, payroll/payslips, reports, and parent-linked finance reads.
- The live candidate read-only reconciliation returned zero unbalanced posted journal entries.
- The finance reporting integration test passed 4/4 in the final full suite.
- No duplicate legacy finance posting was introduced by the V137–V139 authority changes; details are in [`09-finance/evidence.md`](qa/e2e-runs/2026-08-14-full-school/09-finance/evidence.md).

## Operational counts and integrity probes

At the final V144 live read-back:

| Probe | Result |
|---|---:|
| Active enrollments | 29 |
| Timetable slots | 920 |
| Unbalanced posted journal entries | 0 |
| Principal legacy V144 grants | 3 |
| Principal V143 dashboard V2 grants | 6 |
| Principal oversight template rules | 6 |
| Direction dashboard student API | 28 |
| Direction attendance-board records | 1 |
| Direction payments list | 0 |

The active-enrollment and slot counts are live fixture totals, not universal product limits. The invariant checks that matter for release are uniqueness/balance/scope checks recorded in the gate evidence; no manual database correction was used during the V144 deployment.

## Parent read reconciliation

For Amina (`86494486-c54a-4391-89b0-94a500a377bd`) under FAM-A:

- attendance: HTTP 200, total 0;
- discipline: HTTP 200, count 0;
- parent-safe health: HTTP 200, one visit (`2026-09-18`, minor headache; treatment is parent-safe);
- events: HTTP 200, one whole-school event on `2026-09-20`;
- correspondence: HTTP 200, two notices;
- linked notice acknowledgement: HTTP 200, replay HTTP 200, `acknowledged=true`;
- unrelated Biya message acknowledgement: HTTP 403, `POLICY_RULE_MISSING`.

The 8100 Amina School life screen showed the same health/event/correspondence values and exactly three linked child choices, with no unrelated child.

## Independent calculation workbook — 2026-08-15

The formula-driven workbook
`outputs/019ffffe-5397-7a32-842e-2134a9e52c2a/BBC_SMS_CALCULATION_RECONCILIATION.xlsx`
is the independent calculation artifact for the measured scope. It contains
Summary, Academic, Finance, Operations, Checks, and Sources sheets. Academic
averages, promotion counts, override count, commit/replay, finance control
totals, parent-safe statuses, and V144 operational counts are represented as
observed-versus-expected checks with formula-derived differences and statuses.
The exported workbook contains six sheets, all control statuses are `PASS`,
and the final formula-error scan matched zero cells. The workbook does not
claim the remaining Gate 14, visual/mobile, performance, or V77 requirements.
