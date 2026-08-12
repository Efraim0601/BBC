# BAY-36 PDF fidelity gate

`ReportCardPdfServiceTest` is the deterministic contract fixture for the twelve
standard combinations (French/English × term/annual × Nursery/Primary/Secondary).
It verifies embedded-font text extraction, French accents, layout selection,
wrapping, PDF page geometry, QR-bearing output, frozen-snapshot-only rendering,
and byte-for-byte repeatability.

Pixel goldens are intentionally not checked in yet because the repository has
no stable Poppler/rendering baseline across its supported Windows and Linux
workers. The remaining visual gate is a reviewed PDF-to-PNG comparison for a
long multi-page remarks fixture with both present and missing photo/logo/stamp
assets. Any golden suite added later must keep the snapshot fixture and the
renderer's fixed PDF metadata/document ID unchanged.
