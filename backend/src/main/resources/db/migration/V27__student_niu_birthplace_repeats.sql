-- ============================================================================
--  V27 — Enrich the student record with the fields carried by the official BBC
--        class lists (the per-class ".xls" registers the school actually keeps):
--          • niu        — the state "Numéro d'Identifiant Unique" printed on
--                         every register. NOT unique in the source data (two
--                         pupils occasionally share a NIU), so it is a plain
--                         informational column, NOT the primary matricule.
--          • birthplace — "Lieu de naissance", needed on report cards.
--          • repeats    — "Redouble" (OUI/NON): is the pupil repeating the year.
--        These make the raw registers importable as-is (see StudentService
--        bulk import) instead of forcing staff to retype every column.
-- ============================================================================

ALTER TABLE student ADD COLUMN IF NOT EXISTS niu        VARCHAR(24);
ALTER TABLE student ADD COLUMN IF NOT EXISTS birthplace VARCHAR(120);
ALTER TABLE student ADD COLUMN IF NOT EXISTS repeats    BOOLEAN NOT NULL DEFAULT false;

-- A NIU is not unique, but we still look pupils up by it on re-import to avoid
-- creating duplicates, so index it (per tenant).
CREATE INDEX IF NOT EXISTS ix_student_niu ON student (school_id, niu);
