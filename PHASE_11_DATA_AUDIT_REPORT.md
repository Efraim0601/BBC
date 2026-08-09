# Phase 11 read-only data audit

Captured from the deployed local stack on 2026-08-09 after Flyway V74. The queries were read-only; no ambiguous rows were remediated automatically. The V72 teacher workload, qualification, and subject-qualification policy tables are present and empty in the demo data; no live policy rows were created during verification. V73 versioned report-design/branding tables and V74 append-only profile-photo assets are also present.

| Audit | Result | Interpretation |
| --- | ---: | --- |
| Latest Flyway schema | V74 | All additive migrations V59–V74 are applied. |
| Missing primary homeroom assignments | 0 | No current curriculum row is missing its dated primary authority. |
| Overlapping active secondary RESPONSIBLE assignments | 0 | The V65 effective-date invariant is clean. |
| Duplicate subject groups | 0 | No duplicate normalized group code was found. |
| Curriculum/legacy coefficient differences | 0 | Current curriculum coefficients agree with legacy defaults where both exist. |
| Grades without reporting-period/assessment scope | 0 | No unscopeable grade rows were found. |
| Published bulletin snapshots with trace | 1 | New trace-enabled snapshot coverage is present; 60 historical snapshots predate the trace schema and remain unchanged. |
| Published progression graphs with no edges | 0 | The live published graph is structurally populated. |
| Duplicate DRAFT promotion batches | 1 group | Two pre-existing demo batches named `Promotion Session 2026-2027` remain an admin repair item. They were not deleted or merged. |
| Future ACTIVE `PROMOTION` enrollments | 4 | Pre-existing legacy/demo rows target 2027-2028. New commit logic creates `PLANNED`; these historical rows remain an admin repair item. |
| Committed batches without a register | 2 | Both are pre-V69 committed batches; newly committed batches generate the V69 register. |
| Versioned report-card templates | 12 seeded reference rows | V73 provides FR/EN, primary/secondary, sequence/term/annual reference designs without rewriting older templates. |
| Published branding baselines | 1 FR row | V73 seeds a published branding version from the current school profile; snapshot evidence stores its immutable id/hash. |
| Append-only profile-photo assets | Seeded from current photos | V74 preserves exact bytes and SHA-256 metadata for future snapshot/PDF rendering. |

The ambiguous legacy/demo findings are intentionally preserved for operator review. The implementation adds the audit/register and prevents the same immediate-ACTIVE transition for new promotion commits; it does not silently rewrite historical enrollment or batch state.
