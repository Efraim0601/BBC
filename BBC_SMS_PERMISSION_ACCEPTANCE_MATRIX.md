# BBC SMS permission acceptance matrix

All results below target the isolated API on `8101` or UI on `8100`. `403` means a clean policy denial with no resource payload; `200`/`201` means the intended positive authority was usable.

| Persona | Action/scope | API result | UI result / disposition |
|---|---|---:|---|
| Bootstrap admin | Fresh section setup | 201 | Settings section creation usable; P0-01 fixed narrowly |
| Bootstrap admin | Safe inherited-scope template preview | 200, `changes=0` | 8100 preview succeeds; P0-02 fixed |
| Assigned teacher | Finance V2 plan read | 403 | No finance module authority |
| Assigned teacher | Confidential health read | 403 `POLICY_RULE_MISSING` | No confidential health disclosure |
| Accountant | Finance report/receivables | 200 | Finance reporting usable; academic grades denied |
| Accountant | Student grade read | 403 | No academic marks disclosure |
| Direction/principal | Dashboard source reads | students 200 (28), attendance 200, finance summary 200, payments 200 | `/dashboard` on 8100 renders 28 students, 15 FR/13 EN, attendance, finance, distribution, alerts |
| Direction/principal | Official document generation | 403 | Narrow bootstrap fixture exception only; ordinary principal remains denied |
| Parent FAM-A | Linked child academic/finance/school-life reads | 200 where relationship permission allows | Exactly 3 linked children; Amina School life renders attendance, discipline, health, event, correspondence |
| Parent FAM-A | Unrelated child bulletin/grade/message acknowledgement | 403 (`POLICY_RULE_MISSING` for ack) | No unrelated-child data leak |
| Parent FAM-B | Invitation accept, login, forgot/reset | 200 for supported lifecycle calls | Mailpit captured redacted invitation/reset evidence; no token stored in docs |
| Parent message owner | Acknowledge retained notice and replay | 200 / 200, `acknowledged=true` | Parent UI reflects acknowledged notice |
| Ordinary principal | Academic legacy workflow authority | V144 seeds exactly review/validate/publish | Grade edit, curriculum edit, teaching-assignment management remain absent |
| Nursery/Primary teacher | Coursebook create/delete in assigned class | 201 / 204 | Disposable Gate 14 write probe cleaned up |
| Registrar | Student profile create | 201 | Disposable row deactivated through supported temporary bootstrap cleanup authority; registrar deactivation remains denied |
| Parent FAM-A | Suggestion create/read-back | 201 / 200 | Disposable suggestion retained because no delete endpoint exists |

## Boundary rules

- `StudentService`, `EnrollmentService`, `GuardianService`, `TimetableService`, and `TimetableVersionService` enforce contextual actions after resource resolution. The contract test now asserts that boundary rather than requiring unsafe context-free controller checks.
- Parent message acknowledgement is a V2 role action with linked-child scope; V142 does not grant broad parent message management.
- V143 grants principal read-only student, attendance, and finance dashboard source reads. It does not grant collection, fee activation, curriculum mutation, or academic editing.
- V144 aligns fresh bootstrap and upgrades for exactly three legacy academic workflow compatibility grants.

## Matrix status

Core positive/negative persona coverage is green. The bounded policy-cache apply/expiry probe is recorded as PASS in the Gate 14 evidence. The exhaustive final matrix remains IN PROGRESS for a second tenant/cross-tenant fixture, the full reference-plan per-persona read/write plus console/network inventory, and the complete golden-journey matrix. Clean-session route/read checks and the isolated parent UI check are recorded; they are not a substitute for that remaining coverage. The nurse case is explicitly deferred/out of scope for this acceptance continuation; no nurse-specific API/UI row, route change, or focused test is included. The remaining items are release-readiness work, not silently assumed PASS.

## Superseding Gate 14 UI and frontend-regression evidence - 2026-08-15

Fresh explicit browser sessions added a measured boundary slice: Bursar
Finance Plans was usable while `/students/new` and `/parent` redirected to
`/apps`; Cashier Collections was usable while both Finance Plans creation
controls were disabled and the same forbidden routes redirected; FAM-A Parent
rendered exactly three linked children while direct `/students`, `/staff`, and
`/finance` routes redirected. Every checked route had zero browser diagnostics;
the tab was explicitly logged out and finalized. Exact details are in
`qa/e2e-runs/2026-08-14-full-school/final/gate14-ui-boundary-slice-20260815.md`.

The access-control workspace now has focused frontend coverage for safe-template
preview, high-risk reason/confirmation gating, and stale-policy error recovery:
`access-control-workspace-scope-ui.spec.ts` passed `3/3`, and the complete
frontend suite passed `23/23` files / `51/51` tests. These additions narrow the
gap but do not convert the exhaustive Gate 14 matrix to PASS. Nurse remains
deferred and excluded.

## Superseding clean-session route and normal-fixture UI evidence - 2026-08-16

The route-index artifact
`qa/e2e-runs/2026-08-14-full-school/final/gate14-clean-route-index-20260816.md`
records all 35 Section 31 routes for ten non-nurse personas using explicit
logout/login and context selection. Each row completed without browser
warning/error diagnostics; settled FAM-A route results confirmed the expected
`/apps` redirects after an early timing-only blank frame. The normal-fixture
read-only UI timing artifact
`qa/e2e-runs/2026-08-14-full-school/final/performance-ui-readonly-20260816.md`
adds bounded p50/p95/p99 values for six representative routes.

These artifacts strengthen the measured UI boundary but do not convert the
exhaustive Gate 14 action/resource/state/mobile/network/golden matrix or the
realistic-scale UI/performance requirement to PASS. Nurse remains explicitly
deferred and excluded.
