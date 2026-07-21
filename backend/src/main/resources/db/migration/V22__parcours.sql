-- ============================================================================
--  V20 — Parcours dimension (Maternelle) + per-user parcours access
--        Adds the "maternelle" level alongside primary/secondary, and a table
--        that restricts which parcours (level + subsystem) a login may see.
--        An EMPTY restriction set means the user sees ALL parcours (admins).
-- ============================================================================

-- ---- Allow the new "maternelle" level on sections & classes ----------------
ALTER TABLE section      DROP CONSTRAINT IF EXISTS section_level_check;
ALTER TABLE section      ADD  CONSTRAINT section_level_check
    CHECK (level IN ('maternelle','primary','secondary'));

ALTER TABLE school_class DROP CONSTRAINT IF EXISTS school_class_level_check;
ALTER TABLE school_class ADD  CONSTRAINT school_class_level_check
    CHECK (level IN ('maternelle','primary','secondary'));

-- ---- Per-user parcours access ----------------------------------------------
-- A user with NO rows here may access every parcours (no restriction).
-- A user WITH rows is limited to exactly those (level, subsystem) pairs.
CREATE TABLE app_user_parcours (
    user_id     UUID    NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    level       VARCHAR(12) NOT NULL CHECK (level IN ('maternelle','primary','secondary')),
    subsystem   CHAR(2)     NOT NULL CHECK (subsystem IN ('FR','EN')),
    PRIMARY KEY (user_id, level, subsystem)
);
CREATE INDEX idx_user_parcours_user ON app_user_parcours (user_id);
