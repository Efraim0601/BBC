-- ============================================================================
--  V22 — Subjects per subsystem (Francophone / Anglophone)
--        A subject (matière) now belongs to a subsystem so the FR and EN
--        sections keep DISTINCT subject + coefficient lists (as in the official
--        "MATIERE EXCEL" master list). Legacy subjects keep subsystem = NULL,
--        meaning "commune aux deux sous-systèmes" (shown in both).
-- ============================================================================

ALTER TABLE subject ADD COLUMN IF NOT EXISTS subsystem CHAR(2);

ALTER TABLE subject
    ADD CONSTRAINT subject_subsystem_chk
    CHECK (subsystem IS NULL OR subsystem IN ('FR', 'EN'));

-- The old (school_id, code) uniqueness is too strict now that FR & EN may both
-- define e.g. MATH. Replace it with a subsystem-aware unique index (NULL-safe
-- via COALESCE so a "commune" subject stays unique within the school).
ALTER TABLE subject DROP CONSTRAINT IF EXISTS subject_school_id_code_key;

CREATE UNIQUE INDEX IF NOT EXISTS ux_subject_code
    ON subject (school_id, COALESCE(subsystem, ''), code);
