-- ============================================================================
--  V29 — Widen section.label / school_class.name so realistic labels no longer
--        hit PostgreSQL "value too long" (surfaced as an opaque integrity error).
-- ============================================================================

ALTER TABLE section      ALTER COLUMN label TYPE VARCHAR(120);
ALTER TABLE school_class ALTER COLUMN name  TYPE VARCHAR(80);
