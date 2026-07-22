-- ============================================================================
--  V28 — Per-class subject coefficients.
--        The Cameroonian système gives each subject a coefficient that depends
--        on the CLASS (6e..Tle / FORM 1..U6), not a single school-wide value —
--        see the official MINESEC arrêté 239/23 (francophone premier cycle) and
--        the school's anglophone coefficient table (FORM 1→U6).
--
--        `subject.coef` stays as the default/fallback. This table overrides it
--        for a given (subject, class) pair; the weighted bulletin average uses
--        the class-specific coefficient when present, else the subject default.
-- ============================================================================

CREATE TABLE subject_class_coef (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    school_id   UUID NOT NULL REFERENCES school(id),
    subject_id  UUID NOT NULL REFERENCES subject(id) ON DELETE CASCADE,
    class_id    UUID NOT NULL REFERENCES school_class(id) ON DELETE CASCADE,
    coef        INT  NOT NULL DEFAULT 1,
    UNIQUE (school_id, subject_id, class_id)
);

CREATE INDEX ix_scc_class ON subject_class_coef (school_id, class_id);
