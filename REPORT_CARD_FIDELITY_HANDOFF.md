# School report-card fidelity handoff

Branch: `codex/report-card-fidelity` (started at verified `f846f319d9fdc230a5a0043372881aaf95b2e0ce`). The worktree contains only intended source/migration/docs changes; generated PDFs, ZIPs, PNGs and Docker scratch data remain under ignored `tmp/`.

## What is delivered

- Four operational secondary families: `FR_TERM`, `FR_ANNUAL`, `EN_TERM`, `EN_ANNUAL`. Published template and branding IDs/checksums are frozen into every bulletin snapshot.
- Versioned secondary competency models and marks with manual entry and CSV import in Settings → Academic setup → Secondary competencies, plus API routes listed below.
- Canonical session roster, curriculum coefficient, teacher/homeroom, guardian/profile-photo, attendance, conduct and council evidence. A snapshot is append-only for its printable evidence.
- Deterministic A4 PDF rendering with wrapped competency evidence, pagination, accents, photo/no-photo state, QR checksum, signatures/stamp placeholders, term/annual tables and class statistics.
- Batch ZIP companion artifacts and manifest; parent visibility remains published-only.

## Screens and routes

Frontend: `http://localhost:8082/settings` → Academic setup tabs **Templates / branding** and **Secondary competencies**. The template tab shows family/product/locale/version/status/checksum, a visual live sample, branding assets/history and a reason modal for copy/publish. The competency tab supports manual descriptions, model publishing, and CSV marks (`studentId,competencyCode,mark,valueStatus`).

Backend health: `http://localhost:8083/actuator/health`.

| Capability | Route |
| --- | --- |
| Design/branding register | `GET /api/settings/document-design` |
| Secondary models | `GET /api/academic/secondary-competencies?reportingPeriodId=...&classId=...&subjectId=...&locale=...` |
| Create/copy/publish model | `POST /api/academic/secondary-competencies/models`, `POST /models/{id}/copy?reason=...`, `POST /models/{id}/publish` |
| Manual/import marks | `PUT /api/academic/secondary-competencies/marks`, `POST /api/academic/secondary-competencies/marks/import` |
| Preview/calculate lifecycle | `GET/POST /api/academic/students/{studentId}/bulletin-snapshots/preview`, `POST /api/academic/students/{studentId}/bulletin-snapshots`, `POST /api/academic/bulletin-snapshots/{id}/validate`, `POST /api/academic/bulletin-snapshots/{id}/publish` |
| PDF/official document | `GET /api/academic/bulletin-snapshots/{id}/pdf`, `POST /api/academic/bulletin-snapshots/{id}/document` |
| Public QR verification | `GET /api/public/report-card-verification/{snapshotId}?checksum=...` |
| Batch package | `POST /api/academic/classes/{classId}/bulletin-batch?reportingPeriodId=...&locale=...`; async jobs use `/api/academic/bulletin-batch-jobs` |
| Parent published-only bulletin | `GET /api/parent/children/{studentId}/bulletins?reportingPeriodId=...` |

## Demo selections and credentials

The additive demo cohort is in archived session `2025-2026` (the current session remains untouched). Use principal `principal / password` for staff and `parent1 / password` for parent access.

| Variant | Demo student | Published snapshot used for QA |
| --- | --- | --- |
| FR term | Cédric FOTSO (`cccccccc-0000-0000-0000-000000000001`, 4ème) | `97ee090e-3ad1-4a74-9f6a-09e93a04e3fe` |
| FR annual | Cédric FOTSO (4ème) | `27a7fc24-faa4-4989-9d2d-ca0d047ad20a` |
| EN term | Boris ONDOUA (`cccccccc-0000-0000-0000-000000000005`, Form 5) | `fd969bd5-0d00-4de5-b521-e6d0a63a71db` |
| EN annual | Boris ONDOUA (Form 5) | `cb06da6d-886a-4b95-a5f3-51183ada1888` |

The live run published S1/S2 before T1, S3/S4 before T2, S5/S6 before T3, then T1/T2/T3 before annual. A correction was used to regenerate the QA annuals after fixing dependency-code mapping; prior snapshots remain superseded and immutable.

## Verification artifacts (ignored QA output)

| Artifact | Path | SHA-256 |
| --- | --- | --- |
| FR term PDF (2 pages) | [tmp/qa/final-fr-term.pdf](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\tmp\qa\final-fr-term.pdf>) | `8E7569BB860387B526211BD7EDEA1DCEB797C3103EEBEEC8052516C28FA4CB3C` |
| FR annual PDF (1 page) | [tmp/qa/final-fr-annual.pdf](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\tmp\qa\final-fr-annual.pdf>) | `23655EEB2ED83156FADFFF541C5A9DB7F6A9D61A9D2CC78C1294FBBCDA67014A` |
| EN term PDF (2 pages) | [tmp/qa/final-en-term.pdf](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\tmp\qa\final-en-term.pdf>) | `EF95156212A1BA55170E26A8C7CA34E9984B538E6784FACFFADE6C5E523DACF2` |
| EN annual PDF (1 page) | [tmp/qa/final-en-annual.pdf](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\tmp\qa\final-en-annual.pdf>) | `E847086CBEEA9978D0F6FD1361A932D3EF831765E0B0C8A76D9CE8EE4251AE2B` |
| FR term batch ZIP | [tmp/qa/final-fr-term-batch.zip](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\tmp\qa\final-fr-term-batch.zip>) | `2EC722C1AB9A410203D6B70E5745A8A8E531489245E7905ADFC6FFA88230C636` |

The batch manifest includes one published bulletin, an eligible honor certificate, `class-statistics.pdf`, `pv-register.pdf`, filenames, snapshot/hash/status and companion checksums. The issued FR annual document used for document QA was `REPORTCA-20260810042109-0AF267`, official PDF SHA-256 `5e92a2dce6a26ea836dfa3bc28a4cc1b5c64c8fbfebae0d71a0b6b4b821b1b89`. The QR payload verifies the immutable snapshot hash `98fb3020684650ea8970bec2e4d47084f7173248fef29618d21c23fd7c39f7c7`; the public endpoint returned `valid=true` without authentication.

## Tests and deployment

- Backend: `mvn -q "-Dnet.bytebuddy.experimental=true" test` — 23 test cases across six suites (including the secondary A4 renderer smoke test), 0 failures/errors; Testcontainers PostgreSQL; empty-schema migration validation passed through production V76.
- Backend compile: `mvn -q -DskipTests compile` passed.
- Frontend: `npm run build` passed; only the pre-existing `staff.ts` optional-chain warning remains.
- Docker: `docker compose -f docker-compose.yml -f docker-compose.demo.yml -f docker-compose.local.yml build` and `up -d` passed. Backend `8083` health and frontend `8082` returned 200; demo Flyway is at V80 (V76 schema + V77–V80 additive demo data).
- Live UI smoke: `/settings` rendered the four published secondary family cards and live previews; the Secondary competencies screen loaded the archived 2025–2026 cohort, a 4ème/MATH model, two competency descriptions and the manual/import controls. Live API smoke also covered the nullable all-student marks query, QR verification, parent `PUBLISHED` visibility and the ZIP manifest companions.

## Remaining limitations

- The Settings preview is a guided sample/registry rather than a free-form drag-and-drop editor; the renderer remains deterministic and the published config/version is consumed by snapshots.
- The public QR endpoint verifies snapshot identity/checksum and issued state; it intentionally does not expose marks, guardians or unrelated student records.
- The reference PDFs contain source mojibake and malformed values. Renderer output normalizes text and uses explicit dashes/empty states, but historical source defects are not rewritten.

See the field-by-field mapping in [REPORT_CARD_FIDELITY_REFERENCE_MATRIX.md](<C:\Users\joe tech\.codex\worktrees\4f3d\bbcomplex\REPORT_CARD_FIDELITY_REFERENCE_MATRIX.md>).
